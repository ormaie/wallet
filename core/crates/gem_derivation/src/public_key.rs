use std::fmt::Display;
use std::str::FromStr;

use bitcoin::bip32::{ChildNumber, DerivationPath, Xpub};
use bitcoin::secp256k1::{PublicKey, Secp256k1};
use gem_bitcoin::BitcoinAddress;
use gem_encoding::{Base58Alphabet, decode_base58_check, encode_base58_check};
use primitives::{Account, Address, BitcoinChain, Chain, ChainType, hex};

use crate::AccountDerivationError;
use crate::private_key::{address_from_public_key, default_derivation_path};

const XPUB_VERSION: u32 = 0x0488_b21e;
const ZPUB_VERSION: u32 = 0x04b2_4746;
const EXTENDED_KEY_PAYLOAD_LEN: usize = 78;
const CHAIN_CODE_LEN: usize = 32;
const ED25519_PUBLIC_KEY_LEN: usize = 32;
const SLIP10_ED25519_PREFIX: u8 = 0x00;

pub fn evm_compatible_chains() -> Vec<Chain> {
    Chain::all()
        .into_iter()
        .filter(|chain| matches!(chain.chain_type(), ChainType::Ethereum | ChainType::HyperCore))
        .collect()
}

pub fn derive_account_from_public_key(
    chain: Chain,
    public_key: &[u8],
    chain_code: Option<&[u8]>,
    origin_path: &str,
    child_path: Option<&str>,
) -> Result<Account, AccountDerivationError> {
    let normalized_origin = normalize_derivation_path(origin_path, chain)?;
    match chain.chain_type() {
        ChainType::Ethereum | ChainType::HyperCore | ChainType::Tron => {
            let (leaf_key, full_path) = derive_secp256k1_leaf(public_key, chain_code, &normalized_origin, child_path)?;
            let uncompressed = leaf_key.serialize_uncompressed();
            let address = address_from_public_key(&uncompressed, chain)?;
            Ok(Account {
                chain,
                address,
                derivation_path: full_path,
                extended_public_key: Some(hex::encode(uncompressed)),
            })
        }
        ChainType::Bitcoin => {
            if chain != Chain::Bitcoin {
                return Err(AccountDerivationError::unsupported_chain(chain));
            }
            derive_bitcoin_account_from_hd_public_key(public_key, chain_code, &normalized_origin, child_path)
        }
        ChainType::Solana => {
            let raw_ed25519 = normalize_ed25519_public_key(public_key)?;
            let address = address_from_public_key(&raw_ed25519, chain)?;
            Ok(Account {
                chain,
                address,
                derivation_path: normalized_origin,
                extended_public_key: Some(hex::encode(raw_ed25519)),
            })
        }
        ChainType::Aptos
        | ChainType::Sui
        | ChainType::Near
        | ChainType::Stellar
        | ChainType::Algorand
        | ChainType::Cosmos
        | ChainType::Ton
        | ChainType::Xrp
        | ChainType::Polkadot
        | ChainType::Cardano => Err(AccountDerivationError::unsupported_chain(chain)),
    }
}

pub fn derive_account_from_extended_public_key(
    chain: Chain,
    extended_public_key: &str,
    origin_path: Option<&str>,
    child_path: Option<&str>,
) -> Result<Account, AccountDerivationError> {
    match chain.chain_type() {
        ChainType::Bitcoin => {
            if chain != Chain::Bitcoin {
                return Err(AccountDerivationError::unsupported_chain(chain));
            }
            derive_bitcoin_account_from_extended_public_key(extended_public_key, origin_path)
        }
        ChainType::Ethereum | ChainType::HyperCore | ChainType::Tron => {
            let xpub = decode_xpub(extended_public_key)?;
            derive_account_from_public_key(
                chain,
                &xpub.public_key.serialize(),
                Some(xpub.chain_code.as_bytes()),
                origin_path.unwrap_or(""),
                child_path,
            )
        }
        ChainType::Solana
        | ChainType::Aptos
        | ChainType::Sui
        | ChainType::Near
        | ChainType::Stellar
        | ChainType::Algorand
        | ChainType::Cosmos
        | ChainType::Ton
        | ChainType::Xrp
        | ChainType::Polkadot
        | ChainType::Cardano => Err(AccountDerivationError::unsupported_chain(chain)),
    }
}

pub fn derive_bitcoin_account_from_extended_public_key(extended_public_key: &str, origin_path: Option<&str>) -> Result<Account, AccountDerivationError> {
    let xpub = decode_xpub(extended_public_key)?;
    let zpub = encode_extended_public_key(&xpub, ZPUB_VERSION);
    let secp = Secp256k1::verification_only();
    let child_steps = [ChildNumber::from_normal_idx(0).map_err(map_bip32_error)?, ChildNumber::from_normal_idx(0).map_err(map_bip32_error)?];
    let leaf = xpub.derive_pub(&secp, &child_steps).map_err(map_bip32_error)?;
    let address = BitcoinAddress::from_public_key(BitcoinChain::Bitcoin, &leaf.public_key.serialize())?.encode();
    let base_origin = match origin_path.map(str::trim).filter(|path| !path.is_empty()) {
        Some(path) => normalize_derivation_path(path, Chain::Bitcoin)?,
        None => "m/84'/0'/0'".to_string(),
    };
    let derivation_path = append_path_suffix(&base_origin, "0/0");
    Ok(Account {
        chain: Chain::Bitcoin,
        address,
        derivation_path,
        extended_public_key: Some(zpub),
    })
}

fn derive_bitcoin_account_from_hd_public_key(
    public_key: &[u8],
    chain_code: Option<&[u8]>,
    origin_path: &str,
    child_path: Option<&str>,
) -> Result<Account, AccountDerivationError> {
    let parsed_origin = DerivationPath::from_str(origin_path).map_err(map_bip32_error)?;
    let secp_pubkey = PublicKey::from_slice(public_key).map_err(map_bip32_error)?;
    let chain_code_bytes: [u8; CHAIN_CODE_LEN] = chain_code
        .ok_or_else(|| AccountDerivationError::invalid_input("Bitcoin HD public key requires chain code"))?
        .try_into()
        .map_err(|_| AccountDerivationError::invalid_input("invalid Bitcoin chain code length"))?;

    let depth = u8::try_from(parsed_origin.len()).map_err(|_| AccountDerivationError::invalid_input("derivation path too deep"))?;
    let last_child = parsed_origin.as_ref().last().copied().map(u32::from).unwrap_or(0);
    let account_xpub = build_xpub(depth, [0u8; 4], last_child, chain_code_bytes, secp_pubkey)?;
    let zpub = encode_extended_public_key(&account_xpub, ZPUB_VERSION);

    let relative = resolve_relative_child_path(parsed_origin.len(), true, child_path);
    let (leaf_xpub, full_path) = if relative.is_empty() {
        (account_xpub, origin_path.to_string())
    } else {
        let secp = Secp256k1::verification_only();
        let steps = parse_normal_child_steps(&relative)?;
        let derived = account_xpub.derive_pub(&secp, &steps).map_err(map_bip32_error)?;
        (derived, append_path_suffix(origin_path, &relative))
    };

    let address = BitcoinAddress::from_public_key(BitcoinChain::Bitcoin, &leaf_xpub.public_key.serialize())?.encode();
    Ok(Account {
        chain: Chain::Bitcoin,
        address,
        derivation_path: full_path,
        extended_public_key: Some(zpub),
    })
}

fn derive_secp256k1_leaf(
    public_key: &[u8],
    chain_code: Option<&[u8]>,
    origin_path: &str,
    child_path: Option<&str>,
) -> Result<(PublicKey, String), AccountDerivationError> {
    let secp_pubkey = PublicKey::from_slice(public_key).map_err(map_bip32_error)?;
    let parsed_origin = DerivationPath::from_str(origin_path).map_err(map_bip32_error)?;
    let relative = resolve_relative_child_path(parsed_origin.len(), chain_code.is_some(), child_path);
    if relative.is_empty() {
        return Ok((secp_pubkey, origin_path.to_string()));
    }

    let chain_code_bytes: [u8; CHAIN_CODE_LEN] = chain_code
        .ok_or_else(|| AccountDerivationError::invalid_input("child public key derivation requires chain code"))?
        .try_into()
        .map_err(|_| AccountDerivationError::invalid_input("invalid chain code length"))?;
    let depth = u8::try_from(parsed_origin.len()).map_err(|_| AccountDerivationError::invalid_input("derivation path too deep"))?;
    let last_child = parsed_origin.as_ref().last().copied().map(u32::from).unwrap_or(0);
    let xpub = build_xpub(depth, [0u8; 4], last_child, chain_code_bytes, secp_pubkey)?;
    let secp = Secp256k1::verification_only();
    let steps = parse_normal_child_steps(&relative)?;
    let derived = xpub.derive_pub(&secp, &steps).map_err(map_bip32_error)?;
    Ok((derived.public_key, append_path_suffix(origin_path, &relative)))
}

fn normalize_ed25519_public_key(public_key: &[u8]) -> Result<[u8; ED25519_PUBLIC_KEY_LEN], AccountDerivationError> {
    if public_key.len() == ED25519_PUBLIC_KEY_LEN + 1 && public_key[0] == SLIP10_ED25519_PREFIX {
        return public_key[1..].try_into().map_err(|_| AccountDerivationError::invalid_input("invalid ed25519 public key"));
    }
    public_key.try_into().map_err(|_| AccountDerivationError::invalid_input("invalid ed25519 public key"))
}

fn normalize_derivation_path(origin_path: &str, chain: Chain) -> Result<String, AccountDerivationError> {
    let trimmed = origin_path.trim().trim_end_matches('/');
    if trimmed.is_empty() {
        return Ok(default_derivation_path(chain).to_string());
    }
    let normalized = if trimmed.starts_with("m/") || trimmed == "m" {
        trimmed.replace(['h', 'H'], "'")
    } else if let Some(rest) = trimmed.strip_prefix("M/") {
        format!("m/{}", rest.replace(['h', 'H'], "'"))
    } else {
        format!("m/{}", trimmed.replace(['h', 'H'], "'"))
    };
    DerivationPath::from_str(&normalized).map_err(map_bip32_error)?;
    Ok(normalized)
}

fn resolve_relative_child_path(origin_depth: usize, has_chain_code: bool, explicit_child: Option<&str>) -> String {
    if let Some(child) = explicit_child.map(str::trim).filter(|value| !value.is_empty()) {
        return child
            .trim_start_matches("m/")
            .trim_matches('/')
            .split('/')
            .filter(|part| !part.is_empty())
            .map(|part| if part == "*" { "0" } else { part })
            .collect::<Vec<_>>()
            .join("/");
    }
    if !has_chain_code {
        return String::new();
    }
    match origin_depth {
        3 => "0/0".to_string(),
        4 => "0".to_string(),
        _ => String::new(),
    }
}

fn parse_normal_child_steps(relative_path: &str) -> Result<Vec<ChildNumber>, AccountDerivationError> {
    relative_path
        .split('/')
        .filter(|part| !part.is_empty())
        .map(|part| {
            let index: u32 = part
                .parse()
                .map_err(|_| AccountDerivationError::invalid_input(format!("invalid non-hardened child step: {part}")))?;
            ChildNumber::from_normal_idx(index).map_err(map_bip32_error)
        })
        .collect()
}

fn append_path_suffix(base: &str, suffix: &str) -> String {
    let base = base.trim_end_matches('/');
    let suffix = suffix.trim_matches('/');
    if suffix.is_empty() {
        base.to_string()
    } else {
        format!("{base}/{suffix}")
    }
}

fn build_xpub(
    depth: u8,
    parent_fingerprint: [u8; 4],
    child_number: u32,
    chain_code: [u8; CHAIN_CODE_LEN],
    public_key: PublicKey,
) -> Result<Xpub, AccountDerivationError> {
    let mut payload = [0u8; EXTENDED_KEY_PAYLOAD_LEN];
    payload[..4].copy_from_slice(&XPUB_VERSION.to_be_bytes());
    payload[4] = depth;
    payload[5..9].copy_from_slice(&parent_fingerprint);
    payload[9..13].copy_from_slice(&child_number.to_be_bytes());
    payload[13..45].copy_from_slice(&chain_code);
    payload[45..78].copy_from_slice(&public_key.serialize());
    Xpub::decode(&payload).map_err(map_bip32_error)
}

fn decode_xpub(extended_public_key: &str) -> Result<Xpub, AccountDerivationError> {
    let mut payload = decode_base58_check(extended_public_key.trim(), Base58Alphabet::Bitcoin)
        .map_err(|error| AccountDerivationError::invalid_input(format!("invalid extended public key: {error}")))?;
    if payload.len() != EXTENDED_KEY_PAYLOAD_LEN {
        return Err(AccountDerivationError::invalid_input("invalid extended public key length"));
    }
    payload[..4].copy_from_slice(&XPUB_VERSION.to_be_bytes());
    Xpub::decode(&payload).map_err(map_bip32_error)
}

fn encode_extended_public_key(xpub: &Xpub, version: u32) -> String {
    let mut payload = xpub.encode();
    payload[..4].copy_from_slice(&version.to_be_bytes());
    encode_base58_check(&payload, Base58Alphabet::Bitcoin)
}

fn map_bip32_error(error: impl Display) -> AccountDerivationError {
    AccountDerivationError::invalid_input(format!("invalid BIP32 public key derivation: {error}"))
}

#[cfg(test)]
mod tests {
    use bitcoin::Network;
    use bitcoin::bip32::Xpriv;
    use gem_keystore::Mnemonic;

    use super::*;
    use crate::derive_accounts_from_mnemonic;

    const TEST_PHRASE: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

    #[test]
    fn test_derive_evm_and_tron_from_account_xpub_matches_mnemonic() {
        let expected = derive_accounts_from_mnemonic(TEST_PHRASE, vec![Chain::Ethereum, Chain::Tron]).unwrap();
        let seed = Mnemonic::seed(TEST_PHRASE).unwrap();
        let secp = Secp256k1::new();
        let master = Xpriv::new_master(Network::Bitcoin, seed.as_slice()).unwrap();

        let eth_account_priv = master.derive_priv(&secp, &DerivationPath::from_str("m/44'/60'/0'").unwrap()).unwrap();
        let eth_account_pub = Xpub::from_priv(&secp, &eth_account_priv);
        let derived_eth = derive_account_from_public_key(
            Chain::Ethereum,
            &eth_account_pub.public_key.serialize(),
            Some(eth_account_pub.chain_code.as_bytes()),
            "m/44'/60'/0'",
            None,
        )
        .unwrap();
        assert_eq!(derived_eth, expected[0]);

        let tron_account_priv = master.derive_priv(&secp, &DerivationPath::from_str("m/44'/195'/0'").unwrap()).unwrap();
        let tron_account_pub = Xpub::from_priv(&secp, &tron_account_priv);
        let derived_tron = derive_account_from_public_key(
            Chain::Tron,
            &tron_account_pub.public_key.serialize(),
            Some(tron_account_pub.chain_code.as_bytes()),
            "m/44'/195'/0'",
            Some("0/*"),
        )
        .unwrap();
        assert_eq!(derived_tron, expected[1]);
    }

    #[test]
    fn test_derive_bitcoin_from_zpub_and_hd_pubkey_matches_mnemonic() {
        let expected = derive_accounts_from_mnemonic(TEST_PHRASE, vec![Chain::Bitcoin]).unwrap().remove(0);
        let zpub = expected.extended_public_key.as_deref().unwrap();

        let from_zpub = derive_bitcoin_account_from_extended_public_key(zpub, None).unwrap();
        assert_eq!(from_zpub, expected);

        let seed = Mnemonic::seed(TEST_PHRASE).unwrap();
        let secp = Secp256k1::new();
        let master = Xpriv::new_master(Network::Bitcoin, seed.as_slice()).unwrap();
        let btc_priv = master.derive_priv(&secp, &DerivationPath::from_str("m/84'/0'/0'").unwrap()).unwrap();
        let btc_pub = Xpub::from_priv(&secp, &btc_priv);
        let from_hd = derive_account_from_public_key(
            Chain::Bitcoin,
            &btc_pub.public_key.serialize(),
            Some(btc_pub.chain_code.as_bytes()),
            "m/84'/0'/0'",
            None,
        )
        .unwrap();
        assert_eq!(from_hd.address, expected.address);
        assert_eq!(from_hd.derivation_path, expected.derivation_path);
    }

    #[test]
    fn test_derive_solana_accepts_raw_and_slip10_prefixed_ed25519_keys() {
        let expected = derive_accounts_from_mnemonic(TEST_PHRASE, vec![Chain::Solana]).unwrap().remove(0);
        let raw = hex::decode(expected.extended_public_key.as_deref().unwrap()).unwrap();
        let mut prefixed = vec![0x00];
        prefixed.extend_from_slice(&raw);

        let from_raw = derive_account_from_public_key(Chain::Solana, &raw, None, "m/44'/501'/0'/0'", None).unwrap();
        let from_prefixed = derive_account_from_public_key(Chain::Solana, &prefixed, None, "44'/501'/0'", None).unwrap();

        assert_eq!(from_raw, expected);
        assert_eq!(from_prefixed.address, expected.address);
        assert_eq!(from_prefixed.derivation_path, "m/44'/501'/0'");
        assert_eq!(from_prefixed.extended_public_key, expected.extended_public_key);
    }
}
