use std::str;

use ::signer::{Ed25519KeyPair, verify_ed25519_signature};
use gem_encoding::{encode_base58, encode_base64};
use primitives::{Chain, ChainSigner, DateTime, SignerError, SignerInput, TransactionInputType, TransferDataOutputType, swap::SwapQuoteDataType};

use super::{instructions, sign_message as sign_solana_message, swap, transaction};
use crate::{Instruction, Pubkey, SignatureBytes, VersionedTransaction, VersionedTransactionExt, decode_transaction, siws::SiwsMessage, transaction::is_transaction_bytes};

#[derive(Default)]
pub struct SolanaChainSigner;

#[derive(Debug, Clone)]
pub struct UnsignedSolanaTransaction {
    transaction: VersionedTransaction,
    sender: Pubkey,
    wallet_slots: Vec<usize>,
    output_type: TransferDataOutputType,
}

impl UnsignedSolanaTransaction {
    pub fn sender_bytes(&self) -> [u8; 32] {
        *self.sender.as_bytes()
    }

    pub fn message_bytes(&self) -> Result<Vec<u8>, SignerError> {
        self.transaction.serialize_message().map_err(SignerError::from_display)
    }

    pub fn attach_signature(mut self, signature_bytes: &[u8]) -> Result<String, SignerError> {
        let message = self.message_bytes()?;
        verify_ed25519_signature(self.sender.as_bytes(), &message, signature_bytes)?;
        let raw_sig: [u8; 64] = signature_bytes
            .try_into()
            .map_err(|_| SignerError::invalid_input("invalid Solana signature length"))?;
        let signature = SignatureBytes::new(raw_sig);
        for (index, slot) in self.transaction.signatures_mut().iter_mut().enumerate() {
            if self.wallet_slots.contains(&index) {
                *slot = signature;
            }
        }
        match self.output_type {
            TransferDataOutputType::Signature => Ok(encode_base58(signature.as_bytes())),
            TransferDataOutputType::EncodedTransaction => {
                let bytes = self.transaction.serialize().map_err(|e| SignerError::signing_error(format!("serialize transaction: {e}")))?;
                Ok(encode_base64(&bytes))
            }
        }
    }
}

pub fn build_unsigned_solana_transaction(input: &SignerInput) -> Result<UnsignedSolanaTransaction, SignerError> {
    let sender = Pubkey::from_base58(&input.sender_address).map_err(SignerError::from_display)?;
    match &input.input_type {
        TransactionInputType::Transfer { asset } => {
            let ix = if asset.id.is_token() {
                instructions::token_transfer(input, sender)?
            } else {
                instructions::native_transfer(input, sender)?
            };
            build_single_signer_unsigned(input, sender, &ix)
        }
        TransactionInputType::TransferNft { .. } => build_single_signer_unsigned(input, sender, &instructions::nft_transfer(input, sender)?),
        TransactionInputType::Stake { .. } => build_single_signer_unsigned(input, sender, &instructions::stake(input, sender)?),
        TransactionInputType::Swap { swap_data, .. } => match swap_data.data.data_type {
            SwapQuoteDataType::Transfer => SignerError::invalid_input_err("unsupported Solana transfer swap"),
            SwapQuoteDataType::Contract => {
                let transaction = swap::prepare_swap_transaction(input)?;
                build_multi_slot_unsigned(transaction, sender, TransferDataOutputType::EncodedTransaction)
            }
        },
        TransactionInputType::Generic { .. } | TransactionInputType::Payment { .. } => {
            let extra = input.input_type.get_generic_data().map_err(SignerError::invalid_input)?;
            let data = extra.data_as_str().map_err(SignerError::invalid_input)?;
            let mut transaction = decode_transaction(data).map_err(SignerError::invalid_input)?;
            if let TransactionInputType::Payment { .. } = input.input_type
                && !transaction.uses_durable_nonce()
            {
                *transaction.recent_blockhash_mut() = transaction::block_hash(input)?;
            }
            build_multi_slot_unsigned(transaction, sender, extra.output_type.clone())
        }
        TransactionInputType::Deposit { .. }
        | TransactionInputType::Withdrawal { .. }
        | TransactionInputType::TokenApprove { .. }
        | TransactionInputType::Account { .. }
        | TransactionInputType::Perpetual { .. }
        | TransactionInputType::Earn { .. } => SignerError::invalid_input_err("unsupported Solana transaction input type"),
    }
}

pub fn validate_solana_offchain_message(message: &[u8], expected_sender: &str) -> Result<Pubkey, SignerError> {
    let sender = Pubkey::from_base58(expected_sender).map_err(SignerError::from_display)?;
    if is_transaction_bytes(message) {
        return Err(SignerError::invalid_input(SIGN_MESSAGE_PAYLOAD_REJECTION));
    }
    if let Ok(raw) = str::from_utf8(message)
        && let Some(siws) = SiwsMessage::parse(raw).map_err(SignerError::invalid_input)?
    {
        siws.validate(Chain::Solana, DateTime::now()).map_err(SignerError::invalid_input)?;
        if siws.address != expected_sender {
            return SignerError::invalid_input_err("SIWS account mismatch");
        }
    }
    Ok(sender)
}

pub fn format_solana_message_signature(message: &[u8], expected_sender: &str, signature_bytes: &[u8]) -> Result<String, SignerError> {
    let sender = validate_solana_offchain_message(message, expected_sender)?;
    verify_ed25519_signature(sender.as_bytes(), message, signature_bytes)?;
    Ok(encode_base58(signature_bytes))
}

fn build_single_signer_unsigned(input: &SignerInput, sender: Pubkey, instructions: &[Instruction]) -> Result<UnsignedSolanaTransaction, SignerError> {
    let transaction = transaction::build_legacy_transaction(sender, transaction::block_hash(input)?, instructions)?;
    if transaction.num_required_signatures() != 1 {
        return Err(SignerError::invalid_input("Solana transaction requires more than one signer"));
    }
    Ok(UnsignedSolanaTransaction {
        transaction,
        sender,
        wallet_slots: vec![0],
        output_type: TransferDataOutputType::EncodedTransaction,
    })
}

fn build_multi_slot_unsigned(
    transaction: VersionedTransaction,
    sender: Pubkey,
    output_type: TransferDataOutputType,
) -> Result<UnsignedSolanaTransaction, SignerError> {
    let wallet_slots = transaction.signer_slots(&sender);
    if wallet_slots.is_empty() {
        return Err(SignerError::invalid_input("wallet account is not a required signer of the Solana transaction"));
    }
    if wallet_slots.iter().any(|index| transaction.signatures().get(*index) != Some(&SignatureBytes::default())) {
        return Err(SignerError::invalid_input("Solana transaction already contains the wallet signature"));
    }
    Ok(UnsignedSolanaTransaction {
        transaction,
        sender,
        wallet_slots,
        output_type,
    })
}

const SIGN_MESSAGE_PAYLOAD_REJECTION: &str = "Serialized Solana transaction or transaction message received in signMessage request; use signTransaction instead";

impl ChainSigner for SolanaChainSigner {
    fn sign_transfer(&self, input: &SignerInput, private_key: &[u8]) -> Result<String, SignerError> {
        let sender = Pubkey::from_base58(&input.sender_address).map_err(SignerError::from_display)?;
        transaction::sign_single_signer_instructions(input, private_key, sender, instructions::native_transfer(input, sender)?)
    }

    fn sign_token_transfer(&self, input: &SignerInput, private_key: &[u8]) -> Result<String, SignerError> {
        let sender = Pubkey::from_base58(&input.sender_address).map_err(SignerError::from_display)?;
        transaction::sign_single_signer_instructions(input, private_key, sender, instructions::token_transfer(input, sender)?)
    }

    fn sign_nft_transfer(&self, input: &SignerInput, private_key: &[u8]) -> Result<String, SignerError> {
        let sender = Pubkey::from_base58(&input.sender_address).map_err(SignerError::from_display)?;
        transaction::sign_single_signer_instructions(input, private_key, sender, instructions::nft_transfer(input, sender)?)
    }

    fn sign_swap(&self, input: &SignerInput, private_key: &[u8]) -> Result<Vec<String>, SignerError> {
        swap::sign(input, private_key)
    }

    fn sign_stake(&self, input: &SignerInput, private_key: &[u8]) -> Result<Vec<String>, SignerError> {
        let sender = Pubkey::from_base58(&input.sender_address).map_err(SignerError::from_display)?;
        Ok(vec![transaction::sign_single_signer_instructions(input, private_key, sender, instructions::stake(input, sender)?)?])
    }

    fn sign_message(&self, message: &[u8], private_key: &[u8]) -> Result<String, SignerError> {
        if is_transaction_bytes(message) {
            return Err(SignerError::invalid_input(SIGN_MESSAGE_PAYLOAD_REJECTION));
        }
        if let Ok(raw) = str::from_utf8(message)
            && let Some(siws) = SiwsMessage::parse(raw).map_err(SignerError::invalid_input)?
        {
            siws.validate(Chain::Solana, DateTime::now()).map_err(SignerError::invalid_input)?;
            let public_key = Ed25519KeyPair::from_private_key(private_key)?.public_key_bytes;
            if siws.address != encode_base58(public_key) {
                return SignerError::invalid_input_err("SIWS account mismatch");
            }
        }
        let signature = sign_solana_message(private_key, message).map_err(|e| SignerError::signing_error(format!("sign: {e}")))?;
        Ok(encode_base58(signature.as_bytes()))
    }

    fn sign_data(&self, input: &SignerInput, private_key: &[u8]) -> Result<String, SignerError> {
        let extra = input.input_type.get_generic_data().map_err(SignerError::invalid_input)?;
        let data = extra.data_as_str().map_err(SignerError::invalid_input)?;
        let mut transaction = decode_transaction(data).map_err(SignerError::invalid_input)?;

        if let TransactionInputType::Payment { .. } = input.input_type
            && !transaction.uses_durable_nonce()
        {
            *transaction.recent_blockhash_mut() = transaction::block_hash(input)?;
        }

        let signature = transaction::sign_transaction(&mut transaction, private_key)?;

        match extra.output_type {
            TransferDataOutputType::Signature => Ok(encode_base58(signature.as_bytes())),
            TransferDataOutputType::EncodedTransaction => {
                let bytes = transaction.serialize().map_err(|e| SignerError::signing_error(format!("serialize transaction: {e}")))?;
                Ok(encode_base64(&bytes))
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::signer::testkit::SINGLE_SIG_TX;
    use crate::testkit::{mock_durable_nonce_transaction, mock_legacy_transaction, mock_v1_transaction};
    use crate::{SignatureBytes, VersionedTransaction};
    use gem_encoding::decode_base58;
    use gem_encoding::decode_base64;
    use primitives::testkit::signer_mock::{TEST_PRIVATE_KEY, TEST_PRIVATE_KEY_SOLANA_ADDRESS};
    use primitives::{Chain, ChainSigner, SignerInput, TransactionLoadInput, TransactionLoadMetadata, TransferDataOutputType};

    fn signed_blockhash(transaction: VersionedTransaction, payment: bool) -> [u8; 32] {
        let encoded = encode_base64(&transaction.serialize().unwrap());
        let mut input = if payment {
            TransactionLoadInput::mock_sign_data_payment(Chain::Solana, &encoded, TransferDataOutputType::EncodedTransaction)
        } else {
            TransactionLoadInput::mock_sign_data(Chain::Solana, &encoded, TransferDataOutputType::EncodedTransaction)
        };
        input.metadata = TransactionLoadMetadata::mock_solana(&encode_base58([4; 32]));
        let fee = input.default_fee();

        let result = SolanaChainSigner.sign_data(&SignerInput::new(input, fee), &TEST_PRIVATE_KEY).unwrap();

        *VersionedTransaction::deserialize_with_version(&decode_base64(&result).unwrap()).unwrap().recent_blockhash()
    }

    #[test]
    fn test_sign_data_blockhash() {
        let mut latest_blockhash = mock_legacy_transaction();
        *latest_blockhash.recent_blockhash_mut() = [7; 32];
        latest_blockhash.add_signature(SignatureBytes::new([0; 64]));

        let mut wallet_connect = mock_legacy_transaction();
        *wallet_connect.recent_blockhash_mut() = [0; 32];
        wallet_connect.add_signature(SignatureBytes::new([0; 64]));

        assert_eq!(signed_blockhash(latest_blockhash, true), [4; 32]);
        assert_eq!(signed_blockhash(mock_durable_nonce_transaction([7; 32]), true), [7; 32]);
        assert_eq!(signed_blockhash(wallet_connect, false), [0; 32]);
    }

    #[test]
    fn test_sign_data_fills_the_wallet_signer_slot() {
        let transaction = mock_v1_transaction(2, 1);
        let message_bytes = transaction.serialize_message().unwrap();
        let transaction_config = *transaction.transaction_config().unwrap();
        let fee_payer_signature = transaction.signatures()[0];
        let encoded = encode_base64(&transaction.serialize().unwrap());
        let input = TransactionLoadInput::mock_sign_data(Chain::Solana, &encoded, TransferDataOutputType::EncodedTransaction);
        let fee = input.default_fee();

        let result = SolanaChainSigner.sign_data(&SignerInput::new(input, fee), &TEST_PRIVATE_KEY).unwrap();

        let signed = VersionedTransaction::deserialize_with_version(&decode_base64(&result).unwrap()).unwrap();
        let expected_signature = sign_solana_message(&TEST_PRIVATE_KEY, &message_bytes).unwrap();
        assert_eq!(signed.serialize_message().unwrap(), message_bytes);
        assert_eq!(*signed.transaction_config().unwrap(), transaction_config);
        assert_eq!(signed.signatures(), &[fee_payer_signature, expected_signature]);

        let signature_input = TransactionLoadInput::mock_sign_data(Chain::Solana, &encoded, TransferDataOutputType::Signature);
        let fee = signature_input.default_fee();

        let signature = SolanaChainSigner.sign_data(&SignerInput::new(signature_input, fee), &TEST_PRIVATE_KEY).unwrap();

        assert_eq!(signature, encode_base58(expected_signature.as_bytes()));
    }

    #[test]
    fn test_sign_data_rejects_transactions_the_wallet_must_not_sign() {
        let input = TransactionLoadInput::mock_sign_data(Chain::Solana, SINGLE_SIG_TX, TransferDataOutputType::EncodedTransaction);
        let fee = input.default_fee();

        assert_eq!(
            SolanaChainSigner.sign_data(&SignerInput::new(input, fee), &TEST_PRIVATE_KEY).unwrap_err().to_string(),
            "Invalid input: wallet account is not a required signer of the Solana transaction"
        );

        let mut transaction = mock_v1_transaction(1, 0);
        transaction.signatures_mut()[0] = SignatureBytes::new([9; 64]);
        let encoded = encode_base64(&transaction.serialize().unwrap());
        let input = TransactionLoadInput::mock_sign_data(Chain::Solana, &encoded, TransferDataOutputType::EncodedTransaction);
        let fee = input.default_fee();

        assert_eq!(
            SolanaChainSigner.sign_data(&SignerInput::new(input, fee), &TEST_PRIVATE_KEY).unwrap_err().to_string(),
            "Invalid input: Solana transaction already contains the wallet signature"
        );
    }

    #[test]
    fn test_sign_message() {
        let result = SolanaChainSigner.sign_message(b"hello", &TEST_PRIVATE_KEY).unwrap();

        assert_eq!(decode_base58(result).unwrap().len(), 64);
    }

    #[test]
    fn test_sign_message_siws() {
        let message = include_str!("../../testdata/siws_sign_in.txt");
        let signature = SolanaChainSigner.sign_message(message.as_bytes(), &TEST_PRIVATE_KEY).unwrap();
        assert_eq!(signature, encode_base58(sign_solana_message(&TEST_PRIVATE_KEY, message.as_bytes()).unwrap().as_bytes()));
        assert_eq!(SolanaChainSigner.sign_message(message.as_bytes(), &[2; 32]).unwrap_err().to_string(), "Invalid input: SIWS account mismatch");
        let expired_message = include_str!("../../testdata/siws_complete.txt");
        assert_eq!(
            SolanaChainSigner.sign_message(expired_message.as_bytes(), &TEST_PRIVATE_KEY).unwrap_err().to_string(),
            "Invalid input: SIWS message expired or invalid expiration"
        );
        assert_eq!(decode_base58(SolanaChainSigner.sign_message(&[255, 254, 253], &TEST_PRIVATE_KEY).unwrap()).unwrap().len(), 64);
    }

    #[test]
    fn test_sign_message_rejects_transaction_payloads() {
        let bytes = decode_base64(SINGLE_SIG_TX).unwrap();
        let result = SolanaChainSigner.sign_message(&bytes, &TEST_PRIVATE_KEY);

        assert_eq!(result.unwrap_err().to_string(), format!("Invalid input: {SIGN_MESSAGE_PAYLOAD_REJECTION}"));

        let transaction = VersionedTransaction::deserialize_with_version(&bytes).unwrap();
        let message = transaction.serialize_message().unwrap();
        let result = SolanaChainSigner.sign_message(&message, &TEST_PRIVATE_KEY);

        assert_eq!(result.unwrap_err().to_string(), format!("Invalid input: {SIGN_MESSAGE_PAYLOAD_REJECTION}"));

        let message = mock_legacy_transaction().serialize_message().unwrap();
        let result = SolanaChainSigner.sign_message(&message, &TEST_PRIVATE_KEY);

        assert_eq!(result.unwrap_err().to_string(), format!("Invalid input: {SIGN_MESSAGE_PAYLOAD_REJECTION}"));

        let transaction = mock_v1_transaction(1, 0);
        let full_transaction = transaction.serialize().unwrap();
        let message = transaction.serialize_message().unwrap();

        assert_eq!(
            SolanaChainSigner.sign_message(&full_transaction, &TEST_PRIVATE_KEY).unwrap_err().to_string(),
            format!("Invalid input: {SIGN_MESSAGE_PAYLOAD_REJECTION}")
        );
        assert_eq!(SolanaChainSigner.sign_message(&message, &TEST_PRIVATE_KEY).unwrap_err().to_string(), format!("Invalid input: {SIGN_MESSAGE_PAYLOAD_REJECTION}"));
    }

    #[test]
    fn test_qr_unsigned_solana_transaction_roundtrip() {
        let transaction = mock_v1_transaction(2, 1);
        let encoded = encode_base64(&transaction.serialize().unwrap());
        let input = SignerInput::mock_sign_data(Chain::Solana, TEST_PRIVATE_KEY_SOLANA_ADDRESS, &encoded, TransferDataOutputType::EncodedTransaction);
        let expected = SolanaChainSigner.sign_data(&input, &TEST_PRIVATE_KEY).unwrap();

        let unsigned = build_unsigned_solana_transaction(&input).unwrap();
        let sig = sign_solana_message(&TEST_PRIVATE_KEY, &unsigned.message_bytes().unwrap()).unwrap();
        let assembled = unsigned.attach_signature(sig.as_bytes()).unwrap();
        assert_eq!(assembled, expected);
    }
}
