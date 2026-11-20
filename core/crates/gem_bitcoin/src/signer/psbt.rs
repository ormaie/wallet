use std::str::FromStr;

use bitcoin::{
    PublicKey, Script, Transaction, TxOut, Witness,
    bip32::{ChildNumber, DerivationPath, Fingerprint, Xpub},
    blockdata::script::{Builder, Instruction},
    consensus::encode::serialize,
    ecdsa::Signature as BitcoinEcdsaSignature,
    psbt::{Psbt, PsbtSighashType},
    secp256k1::{Message, PublicKey as Secp256k1PublicKey, Secp256k1, VerifyOnly},
    sighash::{EcdsaSighashType, SighashCache},
};
use gem_encoding::{Base58Alphabet, decode_base58_check, decode_base64};
use primitives::{BitcoinChain, SignerError, SignerInput, hex};

use crate::signer::{
    address::{UnlockingScript, public_key_hash, script_for_public_key_hash},
    planner::{PlanInput, SpendPlan, UtxoPlanner},
    transaction::{build_unsigned_transaction, signature_push_bytes, validate_chain_input_types, validate_plan_amounts, validate_public_key},
};

const XPUB_VERSION: u32 = 0x0488_b21e;
const EXTENDED_KEY_PAYLOAD_LEN: usize = 78;
const ACCOUNT_DEPTH: usize = 3;

pub fn build_transfer_psbt(
    chain: BitcoinChain,
    input: &SignerInput,
    master_fingerprint: [u8; 4],
    derivation_path: &str,
    extended_public_key: Option<&str>,
) -> Result<Vec<u8>, SignerError> {
    if !matches!(chain, BitcoinChain::Bitcoin) {
        return SignerError::invalid_input_err("PSBT QR signing is only supported for Bitcoin");
    }

    let plan = UtxoPlanner::transfer(chain, input)?;
    validate_chain_input_types(chain, &plan)?;
    validate_plan_amounts(chain, &plan)?;

    let parsed_path = parse_derivation_path(derivation_path)?;
    let (leaf_pubkey, account_xpub) = resolve_sender_public_key(extended_public_key, &parsed_path)?;
    let bitcoin_pubkey = PublicKey::new(leaf_pubkey);
    validate_public_key(chain, &plan, &bitcoin_pubkey)?;

    let unsigned_tx = build_unsigned_transaction(&plan);
    let mut psbt = Psbt::from_unsigned_tx(unsigned_tx).map_err(SignerError::from_display)?;
    let fingerprint = Fingerprint::from(master_fingerprint);

    if let Some((xpub, account_path)) = account_xpub {
        psbt.xpub.insert(xpub, (fingerprint, account_path));
    }

    let sender_script = plan.inputs.first().map(|item| item.script_pubkey.clone());
    for (psbt_input, plan_input) in psbt.inputs.iter_mut().zip(&plan.inputs) {
        psbt_input.witness_utxo = Some(TxOut {
            value: plan_input.value,
            script_pubkey: plan_input.script_pubkey.clone(),
        });
        psbt_input.sighash_type = Some(PsbtSighashType::from(EcdsaSighashType::All));
        psbt_input.bip32_derivation.insert(leaf_pubkey, (fingerprint, parsed_path.clone()));
    }

    if let Some(sender_script) = sender_script {
        for (psbt_output, plan_output) in psbt.outputs.iter_mut().zip(&plan.outputs) {
            if plan_output.script_pubkey == sender_script {
                psbt_output.bip32_derivation.insert(leaf_pubkey, (fingerprint, parsed_path.clone()));
            }
        }
    }

    Ok(psbt.serialize())
}

pub fn decode_psbt_base64(encoded: &str) -> Result<Vec<u8>, SignerError> {
    let raw = decode_base64(encoded.trim()).map_err(SignerError::from_display)?;
    let psbt = Psbt::deserialize(&raw).map_err(SignerError::from_display)?;
    Ok(psbt.serialize())
}

pub fn extract_signed_psbt_transaction(chain: BitcoinChain, input: &SignerInput, psbt_bytes: &[u8]) -> Result<String, SignerError> {
    if !matches!(chain, BitcoinChain::Bitcoin) {
        return SignerError::invalid_input_err("PSBT QR signing is only supported for Bitcoin");
    }

    let plan = UtxoPlanner::transfer(chain, input)?;
    validate_chain_input_types(chain, &plan)?;
    validate_plan_amounts(chain, &plan)?;
    let expected_unsigned = build_unsigned_transaction(&plan);

    let mut psbt = Psbt::deserialize(psbt_bytes).map_err(SignerError::from_display)?;
    if psbt.unsigned_tx != expected_unsigned {
        return Err(SignerError::signing_error("signed PSBT transaction does not match unsigned transaction"));
    }
    if psbt.inputs.len() != plan.inputs.len() {
        return Err(SignerError::signing_error("signed PSBT input count mismatch"));
    }

    finalize_psbt_inputs(&mut psbt, &plan)?;
    let transaction = psbt.extract_tx_unchecked_fee_rate();
    Ok(hex::encode(serialize(&transaction)))
}

fn finalize_psbt_inputs(psbt: &mut Psbt, plan: &SpendPlan) -> Result<(), SignerError> {
    let secp = Secp256k1::verification_only();
    let mut sighash_cache = SighashCache::new(&psbt.unsigned_tx);

    for (index, (psbt_input, plan_input)) in psbt.inputs.iter_mut().zip(&plan.inputs).enumerate() {
        match (
            psbt_input.final_script_witness.as_ref().filter(|witness| !witness.is_empty()),
            psbt_input.final_script_sig.as_ref().filter(|script| !script.is_empty()),
        ) {
            (Some(witness), None) => {
                let (pubkey, signature) = match plan_input.unlocking_script {
                    UnlockingScript::P2wpkh => extract_finalized_p2wpkh(witness)?,
                    UnlockingScript::P2pkh => return Err(SignerError::signing_error("PSBT P2PKH input must not carry SegWit witness")),
                };
                verify_input_signature(&secp, &mut sighash_cache, index, plan_input, &pubkey, &signature)?;
                continue;
            }
            (None, Some(script_sig)) => {
                let (pubkey, signature) = match plan_input.unlocking_script {
                    UnlockingScript::P2pkh => extract_finalized_p2pkh(script_sig)?,
                    UnlockingScript::P2wpkh => return Err(SignerError::signing_error("PSBT P2WPKH input must not carry scriptSig")),
                };
                verify_input_signature(&secp, &mut sighash_cache, index, plan_input, &pubkey, &signature)?;
                continue;
            }
            (Some(_), Some(_)) => {
                return Err(SignerError::signing_error("PSBT input must not carry both scriptSig and witness"));
            }
            (None, None) => {}
        }

        let mut partial_sigs = psbt_input.partial_sigs.iter();
        let (Some((&pubkey, &signature)), None) = (partial_sigs.next(), partial_sigs.next()) else {
            return Err(SignerError::signing_error("signed PSBT must provide exactly one input signature"));
        };

        verify_input_signature(&secp, &mut sighash_cache, index, plan_input, &pubkey, &signature)?;

        match plan_input.unlocking_script {
            UnlockingScript::P2wpkh => {
                let mut witness = Witness::default();
                witness.push(signature.to_vec());
                witness.push(pubkey.to_bytes());
                psbt_input.final_script_witness = Some(witness);
                psbt_input.partial_sigs.clear();
            }
            UnlockingScript::P2pkh => {
                let script_sig = Builder::new().push_slice(signature_push_bytes(signature.to_vec())?).push_key(&pubkey).into_script();
                psbt_input.final_script_sig = Some(script_sig);
                psbt_input.partial_sigs.clear();
            }
        }
    }

    Ok(())
}

fn extract_finalized_p2wpkh(witness: &Witness) -> Result<(PublicKey, BitcoinEcdsaSignature), SignerError> {
    let mut items = witness.iter();
    let (Some(sig_bytes), Some(pubkey_bytes), None) = (items.next(), items.next(), items.next()) else {
        return Err(SignerError::signing_error("PSBT P2WPKH witness must contain signature and public key"));
    };
    let signature = BitcoinEcdsaSignature::from_slice(sig_bytes).map_err(|_| SignerError::signing_error("invalid PSBT P2WPKH signature encoding"))?;
    let pubkey = PublicKey::from_slice(pubkey_bytes).map_err(|_| SignerError::signing_error("invalid PSBT P2WPKH public key"))?;
    Ok((pubkey, signature))
}

fn extract_finalized_p2pkh(script_sig: &Script) -> Result<(PublicKey, BitcoinEcdsaSignature), SignerError> {
    let mut instructions = script_sig.instructions();
    let Some(Ok(Instruction::PushBytes(sig_push))) = instructions.next() else {
        return Err(SignerError::signing_error("PSBT P2PKH scriptSig missing signature"));
    };
    let Some(Ok(Instruction::PushBytes(pubkey_push))) = instructions.next() else {
        return Err(SignerError::signing_error("PSBT P2PKH scriptSig missing public key"));
    };
    if instructions.next().is_some() {
        return Err(SignerError::signing_error("PSBT P2PKH scriptSig has unexpected trailing instructions"));
    }
    let signature = BitcoinEcdsaSignature::from_slice(sig_push.as_bytes()).map_err(|_| SignerError::signing_error("invalid PSBT P2PKH signature encoding"))?;
    let pubkey = PublicKey::from_slice(pubkey_push.as_bytes()).map_err(|_| SignerError::signing_error("invalid PSBT P2PKH public key"))?;
    Ok((pubkey, signature))
}

fn verify_input_signature(
    secp: &Secp256k1<VerifyOnly>,
    sighash_cache: &mut SighashCache<&Transaction>,
    index: usize,
    plan_input: &PlanInput,
    pubkey: &PublicKey,
    signature: &BitcoinEcdsaSignature,
) -> Result<(), SignerError> {
    if signature.sighash_type != EcdsaSighashType::All {
        return Err(SignerError::signing_error("PSBT signature must use SIGHASH_ALL"));
    }
    let expected_script = script_for_public_key_hash(plan_input.unlocking_script, public_key_hash(&pubkey.to_bytes()));
    if expected_script != plan_input.script_pubkey {
        return Err(SignerError::signing_error("PSBT signature public key does not match input script"));
    }
    match plan_input.unlocking_script {
        UnlockingScript::P2wpkh => {
            if !pubkey.compressed {
                return Err(SignerError::signing_error("PSBT P2WPKH public key must be compressed"));
            }
            let sighash = sighash_cache
                .p2wpkh_signature_hash(index, &plan_input.script_pubkey, plan_input.value, signature.sighash_type)
                .map_err(|error| SignerError::signing_error(error.to_string()))?;
            secp.verify_ecdsa(&Message::from(sighash), &signature.signature, &pubkey.inner)
                .map_err(|_| SignerError::signing_error("invalid PSBT P2WPKH signature"))
        }
        UnlockingScript::P2pkh => {
            let sighash = sighash_cache
                .legacy_signature_hash(index, &plan_input.script_pubkey, signature.sighash_type.to_u32())
                .map_err(|error| SignerError::signing_error(error.to_string()))?;
            secp.verify_ecdsa(&Message::from(sighash), &signature.signature, &pubkey.inner)
                .map_err(|_| SignerError::signing_error("invalid PSBT P2PKH signature"))
        }
    }
}

fn parse_derivation_path(path: &str) -> Result<DerivationPath, SignerError> {
    let trimmed = path.trim();
    if trimmed.is_empty() {
        return DerivationPath::from_str("m/84'/0'/0'/0/0").map_err(SignerError::from_display);
    }
    let normalized = if trimmed.starts_with("m/") || trimmed == "m" {
        trimmed.replace(['h', 'H'], "'")
    } else {
        format!("m/{}", trimmed.replace(['h', 'H'], "'"))
    };
    DerivationPath::from_str(&normalized).map_err(SignerError::from_display)
}

fn resolve_sender_public_key(
    extended_public_key: Option<&str>,
    full_path: &DerivationPath,
) -> Result<(Secp256k1PublicKey, Option<(Xpub, DerivationPath)>), SignerError> {
    let raw = extended_public_key
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| SignerError::invalid_input("Bitcoin PSBT requires sender extended public key"))?;

    if let Ok(bytes) = hex::decode(raw) {
        let pubkey = Secp256k1PublicKey::from_slice(&bytes).map_err(SignerError::from_display)?;
        return Ok((pubkey, None));
    }

    let mut payload = decode_base58_check(raw, Base58Alphabet::Bitcoin).map_err(SignerError::from_display)?;
    if payload.len() != EXTENDED_KEY_PAYLOAD_LEN {
        return SignerError::invalid_input_err("invalid Bitcoin extended public key length");
    }
    payload[..4].copy_from_slice(&XPUB_VERSION.to_be_bytes());
    let xpub = Xpub::decode(&payload).map_err(SignerError::from_display)?;
    let depth = usize::from(xpub.depth);

    let secp = Secp256k1::verification_only();
    let derived = if full_path.len() > depth {
        let child_steps = &full_path[depth..];
        xpub.derive_pub(&secp, &child_steps).map_err(SignerError::from_display)?
    } else if depth == ACCOUNT_DEPTH {
        let steps = [
            ChildNumber::from_normal_idx(0).map_err(SignerError::from_display)?,
            ChildNumber::from_normal_idx(0).map_err(SignerError::from_display)?,
        ];
        xpub.derive_pub(&secp, &steps).map_err(SignerError::from_display)?
    } else {
        xpub
    };

    let account_path = if full_path.len() >= depth {
        DerivationPath::from(&full_path[..depth])
    } else {
        DerivationPath::from_str("m/84'/0'/0'").map_err(SignerError::from_display)?
    };

    Ok((derived.public_key, Some((xpub, account_path))))
}

#[cfg(test)]
mod tests {
    use bitcoin::secp256k1::SecretKey;

    use super::*;
    use crate::signer::BitcoinChainSigner;
    use crate::testkit::signer_mock::{TEST_PRIVATE_KEY, mock_p2wpkh_transfer_input};
    use primitives::ChainSigner;

    #[test]
    fn test_psbt_roundtrip_matches_direct_p2wpkh_signer() {
        let input = mock_p2wpkh_transfer_input();
        let secp = Secp256k1::new();
        let secret_key = SecretKey::from_slice(&TEST_PRIVATE_KEY).unwrap();
        let pubkey = Secp256k1PublicKey::from_secret_key(&secp, &secret_key);
        let pubkey_hex = hex::encode(pubkey.serialize());

        let psbt_bytes = build_transfer_psbt(BitcoinChain::Bitcoin, &input, [0x73, 0xc5, 0xda, 0x0a], "m/84'/0'/0'/0/0", Some(&pubkey_hex)).unwrap();
        let mut psbt = Psbt::deserialize(&psbt_bytes).unwrap();
        assert_eq!(psbt.inputs.len(), 1);
        assert!(psbt.inputs[0].witness_utxo.is_some());
        assert_eq!(psbt.inputs[0].bip32_derivation.len(), 1);

        let mut cache = SighashCache::new(&psbt.unsigned_tx);
        let utxo = psbt.inputs[0].witness_utxo.as_ref().unwrap();
        let sighash = cache.p2wpkh_signature_hash(0, &utxo.script_pubkey, utxo.value, EcdsaSighashType::All).unwrap();
        let sig = secp.sign_ecdsa(&Message::from(sighash), &secret_key);
        let bitcoin_sig = BitcoinEcdsaSignature {
            signature: sig,
            sighash_type: EcdsaSighashType::All,
        };
        psbt.inputs[0].partial_sigs.insert(PublicKey::new(pubkey), bitcoin_sig);

        let extracted = extract_signed_psbt_transaction(BitcoinChain::Bitcoin, &input, &psbt.serialize()).unwrap();
        let direct = BitcoinChainSigner::new(BitcoinChain::Bitcoin).sign_transfer(&input, &TEST_PRIVATE_KEY).unwrap();
        assert_eq!(extracted, direct);

        let mut finalized_psbt = Psbt::deserialize(&psbt_bytes).unwrap();
        let mut witness = Witness::default();
        witness.push(bitcoin_sig.to_vec());
        witness.push(PublicKey::new(pubkey).to_bytes());
        finalized_psbt.inputs[0].final_script_witness = Some(witness);
        let extracted_finalized = extract_signed_psbt_transaction(BitcoinChain::Bitcoin, &input, &finalized_psbt.serialize()).unwrap();
        assert_eq!(extracted_finalized, direct);
    }

    #[test]
    fn test_extract_signed_psbt_rejects_wrong_key_sighash_and_invalid_final_witness() {
        let input = mock_p2wpkh_transfer_input();
        let secp = Secp256k1::new();
        let secret_key = SecretKey::from_slice(&TEST_PRIVATE_KEY).unwrap();
        let pubkey = Secp256k1PublicKey::from_secret_key(&secp, &secret_key);
        let pubkey_hex = hex::encode(pubkey.serialize());
        let psbt_bytes = build_transfer_psbt(BitcoinChain::Bitcoin, &input, [0x73, 0xc5, 0xda, 0x0a], "m/84'/0'/0'/0/0", Some(&pubkey_hex)).unwrap();

        let wrong_secret = SecretKey::from_slice(&[2u8; 32]).unwrap();
        let wrong_pubkey = Secp256k1PublicKey::from_secret_key(&secp, &wrong_secret);
        let mut wrong_key_psbt = Psbt::deserialize(&psbt_bytes).unwrap();
        let utxo = wrong_key_psbt.inputs[0].witness_utxo.as_ref().unwrap().clone();
        let wrong_sighash = SighashCache::new(&wrong_key_psbt.unsigned_tx)
            .p2wpkh_signature_hash(0, &utxo.script_pubkey, utxo.value, EcdsaSighashType::All)
            .unwrap();
        let wrong_sig = BitcoinEcdsaSignature {
            signature: secp.sign_ecdsa(&Message::from(wrong_sighash), &wrong_secret),
            sighash_type: EcdsaSighashType::All,
        };
        wrong_key_psbt.inputs[0].partial_sigs.insert(PublicKey::new(wrong_pubkey), wrong_sig);
        assert_eq!(
            extract_signed_psbt_transaction(BitcoinChain::Bitcoin, &input, &wrong_key_psbt.serialize()),
            Err(SignerError::signing_error("PSBT signature public key does not match input script"))
        );

        let mut wrong_finalized_psbt = Psbt::deserialize(&psbt_bytes).unwrap();
        let mut bad_witness = Witness::default();
        bad_witness.push(wrong_sig.to_vec());
        bad_witness.push(PublicKey::new(wrong_pubkey).to_bytes());
        wrong_finalized_psbt.inputs[0].final_script_witness = Some(bad_witness);
        assert_eq!(
            extract_signed_psbt_transaction(BitcoinChain::Bitcoin, &input, &wrong_finalized_psbt.serialize()),
            Err(SignerError::signing_error("PSBT signature public key does not match input script"))
        );

        let mut non_all_psbt = Psbt::deserialize(&psbt_bytes).unwrap();
        let non_all_sighash = SighashCache::new(&non_all_psbt.unsigned_tx)
            .p2wpkh_signature_hash(0, &utxo.script_pubkey, utxo.value, EcdsaSighashType::None)
            .unwrap();
        non_all_psbt.inputs[0].partial_sigs.insert(
            PublicKey::new(pubkey),
            BitcoinEcdsaSignature {
                signature: secp.sign_ecdsa(&Message::from(non_all_sighash), &secret_key),
                sighash_type: EcdsaSighashType::None,
            },
        );
        assert_eq!(
            extract_signed_psbt_transaction(BitcoinChain::Bitcoin, &input, &non_all_psbt.serialize()),
            Err(SignerError::signing_error("PSBT signature must use SIGHASH_ALL"))
        );
    }
}
