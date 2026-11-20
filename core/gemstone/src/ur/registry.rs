use gem_crypto::random;
use gem_derivation::{
    derive_account_from_extended_public_key, derive_account_from_public_key, evm_compatible_chains,
};
use gem_encoding::decode_base58;
use primitives::{Account, Chain};
use serde_json::Value;

use super::cbor::{CborValue, decode_cbor, encode_cbor};
use super::error::UrError;
use super::fountain::{DEFAULT_MAX_FRAGMENT_LEN, UrEncoder};

const TAG_UUID: u64 = 37;
const TAG_CRYPTO_HDKEY: u64 = 303;
const TAG_CRYPTO_KEYPATH: u64 = 304;
const TAG_CRYPTO_OUTPUT: u64 = 308;
const TAG_SCRIPT_SH: u64 = 400;
const TAG_SCRIPT_WSH: u64 = 401;
const TAG_SCRIPT_PKH: u64 = 403;
const TAG_SCRIPT_WPKH: u64 = 404;
const TAG_SCRIPT_TR: u64 = 409;

const COIN_TYPE_BTC: u32 = 0;
const COIN_TYPE_ETH: u32 = 60;
const COIN_TYPE_TRX: u32 = 195;
const COIN_TYPE_SOL: u32 = 501;
const BIP84_PURPOSE: u32 = 84;

const BITTOOL_WALLET_PROTOCOL: &str = "bittool-signer-wallet";
const KEYSTONE_STANDARD_NOTE: &str = "account.standard";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EthDataType {
    LegacyTransaction = 1,
    TypedData = 2,
    PersonalMessage = 3,
    TypedTransaction = 4,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SolSignType {
    Transaction = 1,
    Message = 2,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TronDataType {
    Transaction = 1,
    PersonalMessage = 2,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct KeyPathComponent {
    pub index: u32,
    pub hardened: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ParsedPairingPayload {
    pub master_fingerprint: Option<String>,
    pub device: Option<String>,
    pub accounts: Vec<Account>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DecodedUrSignature {
    pub ur_type: String,
    pub request_id: Option<[u8; 16]>,
    pub signature_bytes: Vec<u8>,
}

struct ParsedKeyPath {
    path: String,
    components: Vec<Option<KeyPathComponent>>,
    source_fingerprint: Option<u32>,
    depth: Option<u32>,
}

enum DecodedHdKey {
    Accounts {
        master_fingerprint: Option<u32>,
        accounts: Vec<Account>,
    },
    NonBip84Bitcoin,
    UnsupportedCoinType(u32),
}

pub fn generate_request_id() -> Result<[u8; 16], UrError> {
    let mut uuid = random::bytes::<16>().map_err(|err| UrError::InvalidPayload(err.to_string()))?;
    uuid[6] = (uuid[6] & 0x0f) | 0x40;
    uuid[8] = (uuid[8] & 0x3f) | 0x80;
    Ok(uuid)
}

pub fn parse_master_fingerprint(xfp: &str) -> Result<u32, UrError> {
    let trimmed = xfp.trim().trim_start_matches("0x").trim_start_matches("0X");
    if trimmed.len() != 8 || !trimmed.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err(UrError::InvalidPayload(format!("invalid master fingerprint: {xfp}")));
    }
    u32::from_str_radix(trimmed, 16).map_err(|_| UrError::InvalidPayload(format!("invalid master fingerprint: {xfp}")))
}

pub fn format_master_fingerprint(xfp: u32) -> String {
    format!("{xfp:08x}")
}

pub fn parse_bip32_keypath(path: &str) -> Result<Vec<KeyPathComponent>, UrError> {
    let trimmed = path.trim().trim_matches('/');
    let without_prefix = trimmed
        .strip_prefix("m/")
        .or_else(|| trimmed.strip_prefix("M/"))
        .unwrap_or(trimmed);
    if without_prefix.is_empty() || without_prefix == "m" || without_prefix == "M" {
        return Err(UrError::InvalidPayload("empty derivation path".to_string()));
    }

    without_prefix
        .split('/')
        .filter(|part| !part.is_empty())
        .map(|part| {
            let (digits, hardened) = if let Some(stripped) = part.strip_suffix(['\'', 'h', 'H']) {
                (stripped, true)
            } else {
                (part, false)
            };
            let index: u32 = digits
                .parse()
                .map_err(|_| UrError::InvalidPayload(format!("invalid derivation path step: {part}")))?;
            if index >= 0x8000_0000 {
                return Err(UrError::InvalidPayload(format!("derivation step out of range: {part}")));
            }
            Ok(KeyPathComponent { index, hardened })
        })
        .collect()
}

pub fn extract_eip712_chain_id(typed_data_json: &str) -> Option<u64> {
    let parsed: Value = serde_json::from_slice(typed_data_json.as_bytes()).ok()?;
    let chain_id = parsed.get("domain")?.get("chainId")?;
    let parsed_id = match chain_id {
        Value::Number(num) => num.as_u64(),
        Value::String(text) => {
            let trimmed = text.trim();
            if let Some(hex_str) = trimmed.strip_prefix("0x").or_else(|| trimmed.strip_prefix("0X")) {
                u64::from_str_radix(hex_str, 16).ok()
            } else {
                trimmed.parse::<u64>().ok()
            }
        }
        _ => None,
    };
    parsed_id.filter(|id| *id > 0)
}

pub fn encode_eth_sign_request(
    request_id: [u8; 16],
    sign_data: &[u8],
    data_type: EthDataType,
    chain_id: Option<u64>,
    derivation_path: &str,
    master_fingerprint: Option<u32>,
    address: Option<[u8; 20]>,
    origin: &str,
) -> Result<Vec<String>, UrError> {
    let keypath = encode_keypath_tag(derivation_path, master_fingerprint)?;
    let mut entries = vec![
        (CborValue::Unsigned(1), encode_uuid_tag(request_id)),
        (CborValue::Unsigned(2), CborValue::Bytes(sign_data.to_vec())),
        (CborValue::Unsigned(3), CborValue::Unsigned(data_type as u64)),
    ];
    if let Some(id) = chain_id.filter(|id| *id > 0) {
        entries.push((CborValue::Unsigned(4), CborValue::Unsigned(id)));
    }
    entries.push((CborValue::Unsigned(5), keypath));
    if let Some(addr) = address {
        entries.push((CborValue::Unsigned(6), CborValue::Bytes(addr.to_vec())));
    }
    if !origin.is_empty() {
        entries.push((CborValue::Unsigned(7), CborValue::Text(origin.to_string())));
    }
    let cbor = encode_cbor(&CborValue::Map(entries));
    Ok(UrEncoder::encode("eth-sign-request", &cbor, DEFAULT_MAX_FRAGMENT_LEN))
}

pub fn encode_sol_sign_request(
    request_id: [u8; 16],
    sign_data: &[u8],
    derivation_path: &str,
    master_fingerprint: Option<u32>,
    address: Option<[u8; 32]>,
    origin: &str,
    sign_type: SolSignType,
) -> Result<Vec<String>, UrError> {
    let keypath = encode_keypath_tag(derivation_path, master_fingerprint)?;
    let mut entries = vec![
        (CborValue::Unsigned(1), encode_uuid_tag(request_id)),
        (CborValue::Unsigned(2), CborValue::Bytes(sign_data.to_vec())),
        (CborValue::Unsigned(3), keypath),
    ];
    if let Some(addr) = address {
        entries.push((CborValue::Unsigned(4), CborValue::Bytes(addr.to_vec())));
    }
    if !origin.is_empty() {
        entries.push((CborValue::Unsigned(5), CborValue::Text(origin.to_string())));
    }
    entries.push((CborValue::Unsigned(6), CborValue::Unsigned(sign_type as u64)));
    let cbor = encode_cbor(&CborValue::Map(entries));
    Ok(UrEncoder::encode("sol-sign-request", &cbor, DEFAULT_MAX_FRAGMENT_LEN))
}

pub fn encode_tron_sign_request(
    request_id: [u8; 16],
    sign_data: &[u8],
    data_type: TronDataType,
    derivation_path: &str,
    master_fingerprint: Option<u32>,
    address: Option<[u8; 21]>,
    origin: &str,
) -> Result<Vec<String>, UrError> {
    let keypath = encode_keypath_tag(derivation_path, master_fingerprint)?;
    let mut entries = vec![
        (CborValue::Unsigned(1), encode_uuid_tag(request_id)),
        (CborValue::Unsigned(2), CborValue::Bytes(sign_data.to_vec())),
        (CborValue::Unsigned(3), CborValue::Unsigned(data_type as u64)),
        (CborValue::Unsigned(4), keypath),
    ];
    if let Some(addr) = address {
        entries.push((CborValue::Unsigned(5), CborValue::Bytes(addr.to_vec())));
    }
    if !origin.is_empty() {
        entries.push((CborValue::Unsigned(6), CborValue::Text(origin.to_string())));
    }
    let cbor = encode_cbor(&CborValue::Map(entries));
    Ok(UrEncoder::encode("tron-sign-request", &cbor, DEFAULT_MAX_FRAGMENT_LEN))
}

pub fn encode_crypto_psbt(psbt_bytes: &[u8]) -> Vec<String> {
    let cbor = encode_cbor(&CborValue::Bytes(psbt_bytes.to_vec()));
    UrEncoder::encode("crypto-psbt", &cbor, DEFAULT_MAX_FRAGMENT_LEN)
}

pub fn decode_signature_ur(ur_type: &str, cbor_bytes: &[u8]) -> Result<DecodedUrSignature, UrError> {
    let normalized = ur_type.to_ascii_lowercase();
    match normalized.as_str() {
        "eth-signature" | "sol-signature" | "tron-signature" => {
            let root = decode_cbor(cbor_bytes)?;
            let request_id = match root.map_get_uint(1) {
                Some(id_value) => {
                    let bytes = id_value
                        .as_bytes()
                        .ok_or_else(|| UrError::InvalidPayload("signature request_id must be bytes".to_string()))?;
                    let array: [u8; 16] = bytes
                        .try_into()
                        .map_err(|_| UrError::InvalidPayload("signature request_id must be 16 bytes".to_string()))?;
                    Some(array)
                }
                None => None,
            };
            let signature_bytes = root
                .map_get_uint(2)
                .and_then(CborValue::as_bytes)
                .ok_or_else(|| UrError::InvalidPayload("missing signature bytes in UR response".to_string()))?
                .to_vec();
            Ok(DecodedUrSignature {
                ur_type: normalized,
                request_id,
                signature_bytes,
            })
        }
        "crypto-psbt" => {
            let root = decode_cbor(cbor_bytes)?;
            let psbt_bytes = root
                .as_bytes()
                .ok_or_else(|| UrError::InvalidPayload("crypto-psbt must contain byte string".to_string()))?
                .to_vec();
            Ok(DecodedUrSignature {
                ur_type: normalized,
                request_id: None,
                signature_bytes: psbt_bytes,
            })
        }
        _ => Err(UrError::UnsupportedType(normalized)),
    }
}

pub fn decode_pairing_ur(ur_type: &str, cbor_bytes: &[u8]) -> Result<ParsedPairingPayload, UrError> {
    let normalized = ur_type.to_ascii_lowercase();
    match normalized.as_str() {
        "crypto-hdkey" => {
            let root = decode_cbor(cbor_bytes)?;
            let (xfp, accounts) = require_hdkey_accounts(decode_single_hdkey(&root)?)?;
            Ok(ParsedPairingPayload {
                master_fingerprint: xfp.map(format_master_fingerprint),
                device: None,
                accounts,
            })
        }
        "crypto-account" => {
            let root = decode_cbor(cbor_bytes)?;
            decode_crypto_account(&root)
        }
        "crypto-multi-accounts" => {
            let root = decode_cbor(cbor_bytes)?;
            decode_crypto_multi_accounts(&root)
        }
        "bytes" => {
            let root = decode_cbor(cbor_bytes)?;
            let inner = root
                .as_bytes()
                .ok_or_else(|| UrError::InvalidPayload("ur:bytes must contain byte string".to_string()))?;
            decode_bittool_wallet_bytes(inner)
        }
        _ => Err(UrError::UnsupportedType(normalized)),
    }
}

fn encode_uuid_tag(uuid: [u8; 16]) -> CborValue {
    CborValue::Tag(TAG_UUID, Box::new(CborValue::Bytes(uuid.to_vec())))
}

fn encode_keypath_tag(derivation_path: &str, master_fingerprint: Option<u32>) -> Result<CborValue, UrError> {
    let components = parse_bip32_keypath(derivation_path)?;
    let mut encoded_components = Vec::with_capacity(components.len() * 2);
    for comp in components {
        encoded_components.push(CborValue::Unsigned(comp.index as u64));
        encoded_components.push(CborValue::Bool(comp.hardened));
    }
    let mut entries = vec![(CborValue::Unsigned(1), CborValue::Array(encoded_components))];
    if let Some(xfp) = master_fingerprint {
        entries.push((CborValue::Unsigned(2), CborValue::Unsigned(xfp as u64)));
    }
    Ok(CborValue::Tag(TAG_CRYPTO_KEYPATH, Box::new(CborValue::Map(entries))))
}

fn decode_crypto_account(root: &CborValue) -> Result<ParsedPairingPayload, UrError> {
    let xfp = root
        .map_get_uint(1)
        .and_then(CborValue::as_uint)
        .and_then(|val| u32::try_from(val).ok())
        .ok_or_else(|| UrError::InvalidPayload("crypto-account missing master fingerprint".to_string()))?;
    let descriptors = root
        .map_get_uint(2)
        .and_then(CborValue::as_array)
        .ok_or_else(|| UrError::InvalidPayload("crypto-account missing output descriptors".to_string()))?;

    let mut accounts = Vec::new();
    let mut saw_non_wpkh_script = false;

    for descriptor in descriptors {
        let current = match descriptor.as_tag() {
            Some((TAG_CRYPTO_OUTPUT, inner)) => inner,
            _ => descriptor,
        };
        match current.as_tag() {
            Some((TAG_SCRIPT_WPKH, hdkey_node)) => {
                let (_, derived) = require_hdkey_accounts(decode_single_hdkey(hdkey_node)?)?;
                merge_unique_accounts(&mut accounts, derived);
            }
            Some((TAG_SCRIPT_SH | TAG_SCRIPT_WSH | TAG_SCRIPT_PKH | TAG_SCRIPT_TR, _)) => {
                saw_non_wpkh_script = true;
            }
            Some((TAG_CRYPTO_HDKEY, _)) | None => match decode_single_hdkey(current)? {
                DecodedHdKey::Accounts { accounts: derived, .. } => merge_unique_accounts(&mut accounts, derived),
                DecodedHdKey::NonBip84Bitcoin => saw_non_wpkh_script = true,
                DecodedHdKey::UnsupportedCoinType(_) => {}
            },
            Some((other_tag, _)) => {
                return Err(UrError::InvalidPayload(format!("unsupported descriptor script tag: {other_tag}")));
            }
        }
    }

    if accounts.is_empty() {
        if saw_non_wpkh_script {
            return Err(UrError::InvalidPayload("Bitcoin cold wallet requires Native SegWit (BIP84) account".to_string()));
        }
        return Err(UrError::InvalidPayload("crypto-account contained no supported accounts".to_string()));
    }

    Ok(ParsedPairingPayload {
        master_fingerprint: Some(format_master_fingerprint(xfp)),
        device: None,
        accounts,
    })
}

fn decode_crypto_multi_accounts(root: &CborValue) -> Result<ParsedPairingPayload, UrError> {
    let xfp = root
        .map_get_uint(1)
        .and_then(CborValue::as_uint)
        .and_then(|val| u32::try_from(val).ok())
        .ok_or_else(|| UrError::InvalidPayload("crypto-multi-accounts missing master fingerprint".to_string()))?;
    let keys = root
        .map_get_uint(2)
        .and_then(CborValue::as_array)
        .ok_or_else(|| UrError::InvalidPayload("crypto-multi-accounts missing keys array".to_string()))?;
    let device = root.map_get_uint(3).and_then(CborValue::as_str).map(ToOwned::to_owned);

    let mut accounts = Vec::new();
    let mut standard_chains = Vec::new();
    let mut saw_non_bip84_btc = false;
    for item in keys {
        let is_standard = item.map_get_uint(10).and_then(CborValue::as_str) == Some(KEYSTONE_STANDARD_NOTE);
        match decode_single_hdkey(item)? {
            DecodedHdKey::Accounts { accounts: derived, .. } => merge_multi_accounts(&mut accounts, &mut standard_chains, derived, is_standard),
            DecodedHdKey::NonBip84Bitcoin => saw_non_bip84_btc = true,
            DecodedHdKey::UnsupportedCoinType(_) => {}
        }
    }
    if accounts.is_empty() {
        if saw_non_bip84_btc {
            return Err(UrError::InvalidPayload("Bitcoin cold wallet requires Native SegWit (BIP84) account".to_string()));
        }
        return Err(UrError::InvalidPayload("crypto-multi-accounts contained no supported chains".to_string()));
    }

    Ok(ParsedPairingPayload {
        master_fingerprint: Some(format_master_fingerprint(xfp)),
        device,
        accounts,
    })
}

fn require_hdkey_accounts(decoded: DecodedHdKey) -> Result<(Option<u32>, Vec<Account>), UrError> {
    match decoded {
        DecodedHdKey::Accounts { master_fingerprint, accounts } => Ok((master_fingerprint, accounts)),
        DecodedHdKey::NonBip84Bitcoin => Err(UrError::InvalidPayload("Bitcoin requires Native SegWit (BIP84 m/84'/0'/0') account".to_string())),
        DecodedHdKey::UnsupportedCoinType(coin_type) => Err(UrError::InvalidPayload(format!("unsupported SLIP-44 coin type: {coin_type}"))),
    }
}

fn decode_single_hdkey(node: &CborValue) -> Result<DecodedHdKey, UrError> {
    if node.map_get_uint(1).and_then(CborValue::as_bool) == Some(true) {
        return Err(UrError::InvalidPayload("master hdkey without coin derivation is not supported".to_string()));
    }
    if node.map_get_uint(2).and_then(CborValue::as_bool) == Some(true) {
        return Err(UrError::InvalidPayload("private hdkey is not allowed in cold wallet pairing".to_string()));
    }

    let key_data = node
        .map_get_uint(3)
        .and_then(CborValue::as_bytes)
        .ok_or_else(|| UrError::InvalidPayload("crypto-hdkey missing key_data".to_string()))?;
    let chain_code = node.map_get_uint(4).and_then(CborValue::as_bytes);

    let (coin_info_type, coin_info_network) = match node.map_get_uint(5) {
        Some(use_info) => (
            use_info.map_get_uint(1).and_then(CborValue::as_uint).and_then(|val| u32::try_from(val).ok()),
            use_info.map_get_uint(2).and_then(CborValue::as_uint),
        ),
        None => (None, None),
    };
    if let Some(network) = coin_info_network
        && network != 0
    {
        return Err(UrError::InvalidPayload("testnet crypto-hdkey is not supported".to_string()));
    }

    let origin = node.map_get_uint(6).map(parse_cbor_keypath).transpose()?;
    let children = node.map_get_uint(7).map(parse_cbor_keypath).transpose()?;

    let inferred_xfp = infer_master_fingerprint(origin.as_ref(), node.map_get_uint(8).and_then(CborValue::as_uint));
    let origin_coin_type = origin
        .as_ref()
        .and_then(|kp| kp.components.get(1))
        .and_then(|comp| *comp)
        .map(|comp| comp.index);
    let origin_purpose = origin
        .as_ref()
        .and_then(|kp| kp.components.first())
        .and_then(|comp| *comp)
        .map(|comp| comp.index);

    let coin_type = match origin_coin_type.or(coin_info_type) {
        Some(value) => value,
        None if key_data.len() == 32 || (key_data.len() == 33 && key_data[0] == 0x00) => COIN_TYPE_SOL,
        None => COIN_TYPE_ETH,
    };

    let origin_path = origin.as_ref().map(|kp| kp.path.as_str()).unwrap_or("");
    let child_path = children.as_ref().map(|kp| kp.path.as_str());

    let accounts = match coin_type {
        COIN_TYPE_BTC => {
            if let Some(purpose) = origin_purpose
                && purpose != BIP84_PURPOSE
            {
                return Ok(DecodedHdKey::NonBip84Bitcoin);
            }
            let effective_origin = if origin_path.is_empty() { "m/84'/0'/0'" } else { origin_path };
            let btc = derive_account_from_public_key(Chain::Bitcoin, key_data, chain_code, effective_origin, child_path).map_err(|err| UrError::InvalidPayload(err.to_string()))?;
            vec![btc]
        }
        COIN_TYPE_ETH => {
            let effective_origin = if origin_path.is_empty() { "m/44'/60'/0'" } else { origin_path };
            let base = derive_account_from_public_key(Chain::Ethereum, key_data, chain_code, effective_origin, child_path).map_err(|err| UrError::InvalidPayload(err.to_string()))?;
            expand_evm_accounts(&base)
        }
        COIN_TYPE_TRX => {
            let effective_origin = if origin_path.is_empty() { "m/44'/195'/0'" } else { origin_path };
            let tron = derive_account_from_public_key(Chain::Tron, key_data, chain_code, effective_origin, child_path).map_err(|err| UrError::InvalidPayload(err.to_string()))?;
            vec![tron]
        }
        COIN_TYPE_SOL => {
            let effective_origin = if origin_path.is_empty() { "m/44'/501'/0'" } else { origin_path };
            let sol = derive_account_from_public_key(Chain::Solana, key_data, None, effective_origin, None).map_err(|err| UrError::InvalidPayload(err.to_string()))?;
            vec![sol]
        }
        other => return Ok(DecodedHdKey::UnsupportedCoinType(other)),
    };

    Ok(DecodedHdKey::Accounts {
        master_fingerprint: inferred_xfp,
        accounts,
    })
}

fn infer_master_fingerprint(origin: Option<&ParsedKeyPath>, parent_fingerprint: Option<u64>) -> Option<u32> {
    if let Some(kp) = origin
        && let Some(source_xfp) = kp.source_fingerprint
    {
        let comp_len = kp.components.len() as u32;
        if kp.depth.is_none_or(|depth| depth == comp_len) {
            return Some(source_xfp);
        }
    }
    if origin.is_some_and(|kp| kp.components.len() == 1) {
        return parent_fingerprint.and_then(|val| u32::try_from(val).ok());
    }
    None
}

fn parse_cbor_keypath(value: &CborValue) -> Result<ParsedKeyPath, UrError> {
    let raw_components = value
        .map_get_uint(1)
        .and_then(CborValue::as_array)
        .ok_or_else(|| UrError::InvalidPayload("crypto-keypath missing components".to_string()))?;
    if !raw_components.len().is_multiple_of(2) {
        return Err(UrError::InvalidPayload("crypto-keypath components must be pairs".to_string()));
    }

    let mut segments = Vec::with_capacity(raw_components.len() / 2);
    let mut parsed_components = Vec::with_capacity(raw_components.len() / 2);
    for pair in raw_components.as_chunks::<2>().0 {
        let hardened = pair[1]
            .as_bool()
            .ok_or_else(|| UrError::InvalidPayload("crypto-keypath hardened flag must be bool".to_string()))?;
        if let Some(index_u64) = pair[0].as_uint() {
            let index = u32::try_from(index_u64).map_err(|_| UrError::InvalidPayload("keypath index overflow".to_string()))?;
            let suffix = if hardened { "'" } else { "" };
            segments.push(format!("{index}{suffix}"));
            parsed_components.push(Some(KeyPathComponent { index, hardened }));
        } else if pair[0].as_array().is_some_and(<[CborValue]>::is_empty) {
            segments.push("*".to_string());
            parsed_components.push(None);
        } else {
            return Err(UrError::InvalidPayload("unsupported crypto-keypath component".to_string()));
        }
    }

    let source_fingerprint = value
        .map_get_uint(2)
        .and_then(CborValue::as_uint)
        .and_then(|val| u32::try_from(val).ok());
    let depth = value
        .map_get_uint(3)
        .and_then(CborValue::as_uint)
        .and_then(|val| u32::try_from(val).ok());

    Ok(ParsedKeyPath {
        path: segments.join("/"),
        components: parsed_components,
        source_fingerprint,
        depth,
    })
}

fn decode_bittool_wallet_bytes(inner: &[u8]) -> Result<ParsedPairingPayload, UrError> {
    if let Ok(cbor_doc) = decode_cbor(inner)
        && cbor_doc.as_map().is_some()
    {
        return decode_bittool_wallet_cbor(&cbor_doc);
    }
    if let Ok(json_doc) = serde_json::from_slice::<Value>(inner) {
        return decode_bittool_wallet_json(&json_doc);
    }
    Err(UrError::InvalidPayload("unsupported ur:bytes wallet pairing format".to_string()))
}

fn decode_bittool_wallet_cbor(doc: &CborValue) -> Result<ParsedPairingPayload, UrError> {
    let protocol = doc
        .map_get_str("protocol")
        .and_then(CborValue::as_str)
        .ok_or_else(|| UrError::InvalidPayload("missing protocol in ur:bytes pairing".to_string()))?;
    if protocol != BITTOOL_WALLET_PROTOCOL {
        return Err(UrError::InvalidPayload(format!("unsupported ur:bytes protocol: {protocol}")));
    }
    if let Some(network) = doc.map_get_str("network").and_then(CborValue::as_str)
        && network != "mainnet"
    {
        return Err(UrError::InvalidPayload(format!("unsupported pairing network: {network}")));
    }

    let xfp_raw = doc
        .map_get_str("masterFingerprint")
        .and_then(CborValue::as_str)
        .ok_or_else(|| UrError::InvalidPayload("missing masterFingerprint in bittool-signer-wallet".to_string()))?;
    let xfp = parse_master_fingerprint(xfp_raw)?;

    let entries = doc
        .map_get_str("accounts")
        .and_then(CborValue::as_array)
        .ok_or_else(|| UrError::InvalidPayload("missing accounts in bittool-signer-wallet".to_string()))?;

    let mut accounts = Vec::new();
    for entry in entries {
        let Some(chain) = entry.map_get_str("chain").and_then(CborValue::as_str) else {
            continue;
        };
        match chain {
            "btc" => {
                let script = entry.map_get_str("script").and_then(CborValue::as_str).unwrap_or("p2wpkh");
                if script != "p2wpkh" {
                    continue;
                }
                let xpub = entry
                    .map_get_str("xpub")
                    .and_then(CborValue::as_str)
                    .ok_or_else(|| UrError::InvalidPayload("missing btc xpub".to_string()))?;
                let path = entry.map_get_str("accountPath").and_then(CborValue::as_str);
                let btc = derive_account_from_extended_public_key(Chain::Bitcoin, xpub, path, None)
                    .map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                merge_unique_accounts(&mut accounts, vec![btc]);
            }
            "evm" => {
                let xpub = entry
                    .map_get_str("xpub")
                    .and_then(CborValue::as_str)
                    .ok_or_else(|| UrError::InvalidPayload("missing evm xpub".to_string()))?;
                let path = entry.map_get_str("accountPath").and_then(CborValue::as_str);
                let child = entry.map_get_str("derive").and_then(CborValue::as_str);
                let base = derive_account_from_extended_public_key(Chain::Ethereum, xpub, path, child)
                    .map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                merge_unique_accounts(&mut accounts, expand_evm_accounts(&base));
            }
            "tron" => {
                let xpub = entry
                    .map_get_str("xpub")
                    .and_then(CborValue::as_str)
                    .ok_or_else(|| UrError::InvalidPayload("missing tron xpub".to_string()))?;
                let path = entry.map_get_str("accountPath").and_then(CborValue::as_str);
                let child = entry.map_get_str("derive").and_then(CborValue::as_str);
                let tron = derive_account_from_extended_public_key(Chain::Tron, xpub, path, child)
                    .map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                merge_unique_accounts(&mut accounts, vec![tron]);
            }
            "sol" => {
                if let Some(sol_list) = entry.map_get_str("accounts").and_then(CborValue::as_array)
                    && let Some(chosen) = sol_list
                        .iter()
                        .find(|item| item.map_get_str("profile").and_then(CborValue::as_str) == Some("trust"))
                        .or_else(|| {
                            sol_list
                                .iter()
                                .find(|item| item.map_get_str("profile").and_then(CborValue::as_str) == Some("standard"))
                        })
                        .or_else(|| sol_list.first())
                {
                    let address = chosen
                        .map_get_str("address")
                        .and_then(CborValue::as_str)
                        .ok_or_else(|| UrError::InvalidPayload("missing solana address".to_string()))?;
                    let path = chosen.map_get_str("path").and_then(CborValue::as_str).unwrap_or("m/44'/501'/0'");
                    let pubkey = decode_base58(address).map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                    let sol = derive_account_from_public_key(Chain::Solana, &pubkey, None, path, None)
                        .map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                    merge_unique_accounts(&mut accounts, vec![sol]);
                }
            }
            _ => {}
        }
    }

    if accounts.is_empty() {
        return Err(UrError::InvalidPayload("bittool-signer-wallet contained no supported accounts".to_string()));
    }

    Ok(ParsedPairingPayload {
        master_fingerprint: Some(format_master_fingerprint(xfp)),
        device: Some("BitTool Signer".to_string()),
        accounts,
    })
}

fn decode_bittool_wallet_json(doc: &Value) -> Result<ParsedPairingPayload, UrError> {
    let protocol = doc
        .get("protocol")
        .and_then(Value::as_str)
        .ok_or_else(|| UrError::InvalidPayload("missing protocol in JSON pairing".to_string()))?;
    if protocol != BITTOOL_WALLET_PROTOCOL {
        return Err(UrError::InvalidPayload(format!("unsupported JSON pairing protocol: {protocol}")));
    }
    if let Some(network) = doc.get("network").and_then(Value::as_str)
        && network != "mainnet"
    {
        return Err(UrError::InvalidPayload(format!("unsupported pairing network: {network}")));
    }
    let xfp_raw = doc
        .get("masterFingerprint")
        .and_then(Value::as_str)
        .ok_or_else(|| UrError::InvalidPayload("missing masterFingerprint".to_string()))?;
    let xfp = parse_master_fingerprint(xfp_raw)?;
    let entries = doc
        .get("accounts")
        .and_then(Value::as_array)
        .ok_or_else(|| UrError::InvalidPayload("missing accounts array".to_string()))?;

    let mut accounts = Vec::new();
    for entry in entries {
        let Some(chain) = entry.get("chain").and_then(Value::as_str) else {
            continue;
        };
        match chain {
            "btc" => {
                if entry.get("script").and_then(Value::as_str).unwrap_or("p2wpkh") != "p2wpkh" {
                    continue;
                }
                let xpub = entry
                    .get("xpub")
                    .and_then(Value::as_str)
                    .ok_or_else(|| UrError::InvalidPayload("missing btc xpub".to_string()))?;
                let path = entry.get("accountPath").and_then(Value::as_str);
                let btc = derive_account_from_extended_public_key(Chain::Bitcoin, xpub, path, None)
                    .map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                merge_unique_accounts(&mut accounts, vec![btc]);
            }
            "evm" => {
                let xpub = entry
                    .get("xpub")
                    .and_then(Value::as_str)
                    .ok_or_else(|| UrError::InvalidPayload("missing evm xpub".to_string()))?;
                let path = entry.get("accountPath").and_then(Value::as_str);
                let child = entry.get("derive").and_then(Value::as_str);
                let base = derive_account_from_extended_public_key(Chain::Ethereum, xpub, path, child)
                    .map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                merge_unique_accounts(&mut accounts, expand_evm_accounts(&base));
            }
            "tron" => {
                let xpub = entry
                    .get("xpub")
                    .and_then(Value::as_str)
                    .ok_or_else(|| UrError::InvalidPayload("missing tron xpub".to_string()))?;
                let path = entry.get("accountPath").and_then(Value::as_str);
                let child = entry.get("derive").and_then(Value::as_str);
                let tron = derive_account_from_extended_public_key(Chain::Tron, xpub, path, child)
                    .map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                merge_unique_accounts(&mut accounts, vec![tron]);
            }
            "sol" => {
                if let Some(sol_list) = entry.get("accounts").and_then(Value::as_array)
                    && let Some(chosen) = sol_list
                        .iter()
                        .find(|item| item.get("profile").and_then(Value::as_str) == Some("trust"))
                        .or_else(|| sol_list.iter().find(|item| item.get("profile").and_then(Value::as_str) == Some("standard")))
                        .or_else(|| sol_list.first())
                {
                    let address = chosen
                        .get("address")
                        .and_then(Value::as_str)
                        .ok_or_else(|| UrError::InvalidPayload("missing solana address".to_string()))?;
                    let path = chosen.get("path").and_then(Value::as_str).unwrap_or("m/44'/501'/0'");
                    let pubkey = decode_base58(address).map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                    let sol = derive_account_from_public_key(Chain::Solana, &pubkey, None, path, None)
                        .map_err(|err| UrError::InvalidPayload(err.to_string()))?;
                    merge_unique_accounts(&mut accounts, vec![sol]);
                }
            }
            _ => {}
        }
    }

    if accounts.is_empty() {
        return Err(UrError::InvalidPayload("JSON wallet pairing contained no supported accounts".to_string()));
    }

    Ok(ParsedPairingPayload {
        master_fingerprint: Some(format_master_fingerprint(xfp)),
        device: Some("BitTool Signer".to_string()),
        accounts,
    })
}

fn expand_evm_accounts(ethereum_account: &Account) -> Vec<Account> {
    evm_compatible_chains()
        .into_iter()
        .map(|chain| Account {
            chain,
            address: ethereum_account.address.clone(),
            derivation_path: ethereum_account.derivation_path.clone(),
            extended_public_key: ethereum_account.extended_public_key.clone(),
        })
        .collect()
}

pub fn merge_unique_accounts(target: &mut Vec<Account>, incoming: Vec<Account>) {
    for account in incoming {
        if let Some(existing) = target.iter_mut().find(|item| item.chain == account.chain) {
            *existing = account;
        } else {
            target.push(account);
        }
    }
}

fn merge_multi_accounts(target: &mut Vec<Account>, standard_chains: &mut Vec<Chain>, incoming: Vec<Account>, is_standard: bool) {
    for account in incoming {
        let chain = account.chain;
        if let Some(existing) = target.iter_mut().find(|item| item.chain == chain) {
            if is_standard && !standard_chains.contains(&chain) {
                *existing = account;
                standard_chains.push(chain);
            }
        } else {
            if is_standard {
                standard_chains.push(chain);
            }
            target.push(account);
        }
    }
}

#[cfg(test)]
mod tests {
    use primitives::hex;

    use super::*;
    use crate::ur::fountain::{UrDecoder, UrReceiveOutcome};

    fn decode_frames<S: AsRef<str>>(frames: &[S]) -> (String, Vec<u8>) {
        let mut decoder = UrDecoder::new();
        for frame in frames {
            if let UrReceiveOutcome::Complete { ur_type, cbor } = decoder.receive(frame.as_ref()).unwrap() {
                return (ur_type, cbor);
            }
        }
        panic!("UR frames did not complete");
    }

    #[test]
    fn test_bittool_signing_vectors_roundtrip() {
        let request_id: [u8; 16] = hex::decode("8f235e99e6e84044a51d51ec3ec94202").unwrap().try_into().unwrap();
        let eip1559_bytes = hex::decode("02e80107843b9aca008506fc23ac0082520894000000000000000000000000000000000000dead0180c0").unwrap();
        let evm_addr: [u8; 20] = hex::decode("9858effd232b4033e47d90003d41ec34ecaeda94").unwrap().try_into().unwrap();

        let frames = encode_eth_sign_request(
            request_id,
            &eip1559_bytes,
            EthDataType::TypedTransaction,
            Some(1),
            "m/44'/60'/0'/0/0",
            Some(0x73c5_da0a),
            Some(evm_addr),
            "BitTool Wallet public interoperability fixture",
        )
        .unwrap();
        assert_eq!(
            decode_frames(&frames),
            decode_frames(&[
                "UR:ETH-SIGN-REQUEST/OSADTPDAGDMYCNHYNLVAVSFZFYONCAGYWPFMSOFWAOAOHDDRAOVSADATLRFRNYSGAELPAMZTCNPSAELFGMAYMWAEAEAEAEAEAEAEAEAEAEAEAEAEAEAEAEAEAEUEPMADLARTAXAAAAADAHTAADDYOEADLECSDWYKCSFNYKAEYKAEWKAEWKAOCYJKSKTNBKAMGHMKHDWSZCCNDNFZEOVEKIMHAEFSFPWPEEWPPLTNMWATKSDMFWINJYGHJLJLJZCXHGHSJZJZIHJYCXJOKPIDJZINIACXINJTJYIHJPJLJOIHJPHSIDINJZINJYKKCXIYINKSJYKPJPIHLAETHGLA"
            ])
        );

        let mut decoder = UrDecoder::new();
        let eth_sig_frame = "UR:ETH-SIGNATURE/OEADTPDAGDMYCNHYNLVAVSFZFYONCAGYWPFMSOFWAOAOHDFPLUZOESDEBTVEKGVYBKTAIELRFSDAEEFRMYRNPFPROTJKUYNBJNSTLKKTAALOLOUTHDKPIAWMLTWMTLTPRLLRSWCTNYLKPSFZYKVAFZVACWYKSNKEMSATSWYKJSLEDYAAAEGRKPLUZE";
        let UrReceiveOutcome::Complete { ur_type, cbor } = decoder.receive(eth_sig_frame).unwrap() else {
            panic!("expected complete eth-signature");
        };
        let decoded_eth_sig = decode_signature_ur(&ur_type, &cbor).unwrap();
        assert_eq!(decoded_eth_sig.request_id, Some(request_id));
        assert_eq!(
            hex::encode(decoded_eth_sig.signature_bytes),
            "8bfb39280de47be10ad964843d25343b8fbeb0b2a373dba06dc78c77048888dd587563eb87ebd5d8b784c61f9a8cac40f5e640e61bf5cd7c9707c6f5718a300400"
        );

        let tron_req_id: [u8; 16] = hex::decode("8f235e99e6e84044a51d51ec3ec94207").unwrap().try_into().unwrap();
        let tron_msg = hex::decode("426974546f6f6c2057616c6c6574202b20426974546f6f6c205369676e65723a207075626c696320696e7465726f7065726162696c69747920746573742e").unwrap();
        let tron_addr: [u8; 21] = hex::decode("41c8599111f29c1e1e061265b4af93ea1f274ad78a").unwrap().try_into().unwrap();
        let tron_frames = encode_tron_sign_request(
            tron_req_id,
            &tron_msg,
            TronDataType::PersonalMessage,
            "m/44'/195'/0'/0/0",
            Some(0x73c5_da0a),
            Some(tron_addr),
            "BitTool Wallet public fixture",
        )
        .unwrap();
        assert_eq!(
            decode_frames(&tron_frames),
            decode_frames(&[
                "UR:TRON-SIGN-REQUEST/OLADTPDAGDMYCNHYNLVAVSFZFYONCAGYWPFMSOFWATAOHDFMFWINJYGHJLJLJZCXHGHSJZJZIHJYCXDNCXFWINJYGHJLJLJZCXGUINIOJTIHJPFTCXJOKPIDJZINIACXINJTJYIHJPJLJOIHJPHSIDINJZINJYKKCXJYIHJKJYDMAXAOAATAADDYOEADLECSDWYKCSSRYKAEYKAEWKAEWKAOCYJKSKTNBKAHGOFPSPHKMEBYWZNSCKCKAMBGIHQZPEMUWDCTDIGETSLEAMKSCAFWINJYGHJLJLJZCXHGHSJZJZIHJYCXJOKPIDJZINIACXIYINKSJYKPJPIHHPRKPFSN"
            ])
        );

        let sol_req_id: [u8; 16] = hex::decode("8f235e99e6e84044a51d51ec3ec94205").unwrap().try_into().unwrap();
        let sol_addr: [u8; 32] = hex::decode("f036276246a75b9de3349ed42b15e232f6518fc20f5fcd4f1d64e81f9bd258f7").unwrap().try_into().unwrap();
        let sol_frames = encode_sol_sign_request(
            sol_req_id,
            &tron_msg,
            "m/44'/501'/0'/0'",
            Some(0x73c5_da0a),
            Some(sol_addr),
            "BitTool Wallet public fixture",
            SolSignType::Message,
        )
        .unwrap();
        assert_eq!(
            decode_frames(&sol_frames),
            decode_frames(&[
                "UR:SOL-SIGN-REQUEST/OLADTPDAGDMYCNHYNLVAVSFZFYONCAGYWPFMSOFWAHAOHDFMFWINJYGHJLJLJZCXHGHSJZJZIHJYCXDNCXFWINJYGHJLJLJZCXGUINIOJTIHJPFTCXJOKPIDJZINIACXINJTJYIHJPJLJOIHJPHSIDINJZINJYKKCXJYIHJKJYDMAXTAADDYOEADLOCSDWYKCFADYKYKAEYKAEYKAOCYJKSKTNBKAAHDCXWTENDIIDFGOSHPNTVLEENNTYDNBZVOEYYNGYMYSABSHESNGWCAIEVSCTNDTDHDYLAHKSCAFWINJYGHJLJLJZCXHGHSJZJZIHJYCXJOKPIDJZINIACXIYINKSJYKPJPIHAMAORNRSLGJT"
            ])
        );
    }

    #[test]
    fn test_decode_multipart_psbt_and_multi_accounts() {
        let mut decoder = UrDecoder::new();
        let psbt_frames = [
            "UR:CRYPTO-PSBT/1-3/LPADAXCFADLBCYNLRPSECXHDLAHKADKEJOJKIDJYZMADAEGMAOAEAEAEADWLBSWTPKCNUTLOFSDRVDOYSGFNCFPKFZCMLSVLNSYKJPCEWKSPNEYKTDRLPMGESWAEAEAEAEAEZMZMZMZMADMHHEADAEAEAEAEAECMAEBBRTTORFTBSRTESGLKKPUOHYSWDMRNGOEOBAYTBEVOAEAEAEAEAEADAEGMAOAEAEAEADADADADADADADADADADADADADADADADADADADADADADADADADADADTEMOSEEM",
            "UR:CRYPTO-PSBT/2-3/LPAOAXCFADLBCYNLRPSECXHDLAADADADADADADAEAEAEAEAEZMZMZMZMADNBLNADAEAEAEAEAECMAEBBRTTORFTBSRTESGLKKPUOHYSWDMRNGOEOBAYTBEVOAEAEAEAEADADCTNBLNADAEAEAEAEAECMAEBBRTTORFTBSRTESGLKKPUOHYSWDMRNGOEOBAYTBEVOCPAOAXDYTLGWTIUTFWBKJTHELGENDKYKWFFDDWPLECBSKKTLWTKPFRYKRNWSNSDPMEPEFNFDDYFEAOCLAEMSCAWEBYQDBT",
            "UR:CRYPTO-PSBT/3-3/LPAXAXCFADLBCYNLRPSECXHDLAAADNOSLAUTDLDYSSNYVSNNSEPFTBCFOTSEBGISMSJOGWSABNTBLKGTOTWDYTAOCXKEJTLTUYUEIELTQDDNCXWETLYAPYOENLTISNDRGHGARHCYHYLAFWJKCXFZNLVSONADCPAMAXDYTLGWTIUTFWBKJTHELGENDKYKWFFDDWPLECBSKKTLWTKPFRYKRNWSNSDPMEPEFNCSJKSKTNBKGHAEAELAAEAEAELAAEAEAELAAEAEAEAEAEAEAEAEAEAEAEPLIEEHCY",
        ];
        assert_eq!(decoder.receive(psbt_frames[0]).unwrap(), UrReceiveOutcome::Progress { received: 1, expected: 3 });
        assert_eq!(decoder.receive(psbt_frames[1]).unwrap(), UrReceiveOutcome::Progress { received: 2, expected: 3 });
        let UrReceiveOutcome::Complete { ur_type, cbor } = decoder.receive(psbt_frames[2]).unwrap() else {
            panic!("expected complete psbt");
        };
        let sig = decode_signature_ur(&ur_type, &cbor).unwrap();
        assert_eq!(&sig.signature_bytes[..5], b"psbt\xff");

        let multi_accounts_cbor = hex::decode(
            "a40119cab90282d9012fa603582102c0a1ffb8c1bc1ddabbedf7ff3dfd8fb9be26fb4d8fb2cfb7ca7efaa6a1acbc840458209f6d73ad88eb3fc81bbd98be3a68ddbc6ce363fb04a336966ba7fa84601b62a706d90130a201861854f500f500f50219cab9081a953b82d6096b425443204163636f756e740a706163636f756e742e7374616e64617264d9012fa60358210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817980458203466f8b37b16a64bc5db2a1d32cd038e7be40be3b07dd3c538b87309e4ff41aa06d90130a20186182cf5183cf500f50219cab9081a953b82d6096b455448204163636f756e740a736163636f756e742e6c65646765725f6c697665036b4b657973746f6e6550726f04483030303030303030",
        )
        .unwrap();
        let parsed = decode_pairing_ur("crypto-multi-accounts", &multi_accounts_cbor).unwrap();
        let expected_btc = Account {
            chain: Chain::Bitcoin,
            address: "bc1q0frrlnmztr7wjt5gf883u503ptkj97jga0ycpw".to_string(),
            derivation_path: "m/84'/0'/0'/0/0".to_string(),
            extended_public_key: Some("zpub6qKJA3qD7NEXiK47BFJp6F75uyLe8t9xBkyoiXByiRmPft9FamSjcDeireiqh4J78eM3TTuEut3rFMDQUuZopzTupLWie5qWjhFkUyE7ai5".to_string()),
        };
        let expected_eth = Account {
            chain: Chain::Ethereum,
            address: "0x2e6647805E740945C8c63e01d048DFF1633cAb00".to_string(),
            derivation_path: "m/44'/60'/0'/0/0".to_string(),
            extended_public_key: Some("0413a7f47dce54d206f8aec805ecb240ebee63187db25a5b65ce93b0c9e3ee3e4c4a5fbe0b25bfb1ad30471fef50c20135c74cbdf856f809fb74a806763d935bb6".to_string()),
        };
        let mut expected_accounts = vec![expected_btc];
        expected_accounts.extend(expand_evm_accounts(&expected_eth));
        assert_eq!(
            parsed,
            ParsedPairingPayload {
                master_fingerprint: Some("0000cab9".to_string()),
                device: Some("KeystonePro".to_string()),
                accounts: expected_accounts,
            }
        );
    }

    #[test]
    fn test_decode_pairing_rejects_malformed_and_private_keys_while_skipping_unsupported() {
        let malformed_multi_accounts = hex::decode(
            "a40119cab90282d9012fa603582102c0a1ffb8c1bc1ddabbedf7ff3dfd8fb9be26fb4d8fb2cfb7ca7efaa6a1acbc840458209f6d73ad88eb3fc81bbd98be3a68ddbc6ce363fb04a336966ba7fa84601b62a706d90130a201861854f500f500f50219cab9081a953b82d6096b425443204163636f756e740a706163636f756e742e7374616e64617264d9012fa6035821034f5bb8ba6fbde03d9fb8ad83fb90b2a840bfef3699ac2cc8acbd76eb91e2ff420458203466f8b37b16a64bc5db2a1d32cd038e7be40be3b07dd3c538b87309e4ff41aa06d90130a20186182cf5183cf500f50219cab9081a953b82d6096b455448204163636f756e740a736163636f756e742e6c65646765725f6c697665036b4b657973746f6e6550726f04483030303030303030",
        )
        .unwrap();
        assert!(decode_pairing_ur("crypto-multi-accounts", &malformed_multi_accounts).is_err());

        let pubkey_g = hex::decode("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798").unwrap();
        let pubkey_2g = hex::decode("02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5").unwrap();
        let invalid_pubkey = hex::decode("034f5bb8ba6fbde03d9fb8ad83fb90b2a840bfef3699ac2cc8acbd76eb91e2ff42").unwrap();
        let btc_pubkey = hex::decode("02c0a1ffb8c1bc1ddabbedf7ff3dfd8fb9be26fb4d8fb2cfb7ca7efaa6a1acbc84").unwrap();
        let btc_chain_code = hex::decode("9f6d73ad88eb3fc81bbd98be3a68ddbc6ce363fb04a336966ba7fa84601b62a7").unwrap();

        let private_hdkey = CborValue::Tag(
            TAG_CRYPTO_HDKEY,
            Box::new(CborValue::Map(vec![
                (CborValue::Unsigned(2), CborValue::Bool(true)),
                (CborValue::Unsigned(3), CborValue::Bytes(pubkey_g.clone())),
                (CborValue::Unsigned(6), encode_keypath_tag("m/44'/60'/0'/0/0", Some(0x73c5_da0a)).unwrap()),
            ])),
        );
        let private_multi = encode_cbor(&CborValue::Map(vec![
            (CborValue::Unsigned(1), CborValue::Unsigned(0x73c5_da0a)),
            (CborValue::Unsigned(2), CborValue::Array(vec![private_hdkey])),
        ]));
        assert_eq!(
            decode_pairing_ur("crypto-multi-accounts", &private_multi),
            Err(UrError::InvalidPayload("private hdkey is not allowed in cold wallet pairing".to_string()))
        );

        let malformed_account = encode_cbor(&CborValue::Map(vec![
            (CborValue::Unsigned(1), CborValue::Unsigned(0x73c5_da0a)),
            (
                CborValue::Unsigned(2),
                CborValue::Array(vec![CborValue::Tag(
                    TAG_CRYPTO_HDKEY,
                    Box::new(CborValue::Map(vec![
                        (CborValue::Unsigned(3), CborValue::Bytes(invalid_pubkey)),
                        (CborValue::Unsigned(6), encode_keypath_tag("m/44'/60'/0'/0/0", Some(0x73c5_da0a)).unwrap()),
                    ])),
                )]),
            ),
        ]));
        assert!(decode_pairing_ur("crypto-account", &malformed_account).is_err());

        let btc_bip84_key = CborValue::Tag(
            TAG_CRYPTO_HDKEY,
            Box::new(CborValue::Map(vec![
                (CborValue::Unsigned(3), CborValue::Bytes(btc_pubkey.clone())),
                (CborValue::Unsigned(4), CborValue::Bytes(btc_chain_code.clone())),
                (CborValue::Unsigned(6), encode_keypath_tag("m/84'/0'/0'", Some(0x73c5_da0a)).unwrap()),
            ])),
        );
        let btc_bip49_key = CborValue::Tag(
            TAG_CRYPTO_HDKEY,
            Box::new(CborValue::Map(vec![
                (CborValue::Unsigned(3), CborValue::Bytes(btc_pubkey)),
                (CborValue::Unsigned(4), CborValue::Bytes(btc_chain_code)),
                (CborValue::Unsigned(6), encode_keypath_tag("m/49'/0'/0'", Some(0x73c5_da0a)).unwrap()),
            ])),
        );
        let cosmos_key = CborValue::Tag(
            TAG_CRYPTO_HDKEY,
            Box::new(CborValue::Map(vec![
                (CborValue::Unsigned(3), CborValue::Bytes(pubkey_g.clone())),
                (CborValue::Unsigned(6), encode_keypath_tag("m/44'/118'/0'", Some(0x73c5_da0a)).unwrap()),
            ])),
        );
        let skipped_multi = encode_cbor(&CborValue::Map(vec![
            (CborValue::Unsigned(1), CborValue::Unsigned(0x73c5_da0a)),
            (CborValue::Unsigned(2), CborValue::Array(vec![btc_bip49_key, cosmos_key, btc_bip84_key])),
        ]));
        assert_eq!(
            decode_pairing_ur("crypto-multi-accounts", &skipped_multi).unwrap(),
            ParsedPairingPayload {
                master_fingerprint: Some("73c5da0a".to_string()),
                device: None,
                accounts: vec![Account {
                    chain: Chain::Bitcoin,
                    address: "bc1q0frrlnmztr7wjt5gf883u503ptkj97jga0ycpw".to_string(),
                    derivation_path: "m/84'/0'/0'/0/0".to_string(),
                    extended_public_key: Some("zpub6qKJA3qD7NEXiK47BFJp6F75uyLe8t9xBkyoiXByiRmPft9FamSjcDeireiqh4J78eM3TTuEut3rFMDQUuZopzTupLWie5qWjhFkUyE7ai5".to_string()),
                }],
            }
        );

        let eth_ledger_live = CborValue::Tag(
            TAG_CRYPTO_HDKEY,
            Box::new(CborValue::Map(vec![
                (CborValue::Unsigned(3), CborValue::Bytes(pubkey_g)),
                (CborValue::Unsigned(6), encode_keypath_tag("m/44'/60'/0'/0/0", Some(0x73c5_da0a)).unwrap()),
                (CborValue::Unsigned(10), CborValue::Text("account.ledger_live".to_string())),
            ])),
        );
        let eth_standard = CborValue::Tag(
            TAG_CRYPTO_HDKEY,
            Box::new(CborValue::Map(vec![
                (CborValue::Unsigned(3), CborValue::Bytes(pubkey_2g)),
                (CborValue::Unsigned(6), encode_keypath_tag("m/44'/60'/0'/0/0", Some(0x73c5_da0a)).unwrap()),
                (CborValue::Unsigned(10), CborValue::Text("account.standard".to_string())),
            ])),
        );
        let ledger_first = encode_cbor(&CborValue::Map(vec![
            (CborValue::Unsigned(1), CborValue::Unsigned(0x73c5_da0a)),
            (CborValue::Unsigned(2), CborValue::Array(vec![eth_ledger_live.clone(), eth_standard.clone()])),
        ]));
        let standard_first = encode_cbor(&CborValue::Map(vec![
            (CborValue::Unsigned(1), CborValue::Unsigned(0x73c5_da0a)),
            (CborValue::Unsigned(2), CborValue::Array(vec![eth_standard, eth_ledger_live])),
        ]));
        let parsed_ledger_first = decode_pairing_ur("crypto-multi-accounts", &ledger_first).unwrap();
        let parsed_standard_first = decode_pairing_ur("crypto-multi-accounts", &standard_first).unwrap();
        assert_eq!(parsed_ledger_first, parsed_standard_first);
        assert_eq!(
            parsed_standard_first.accounts.iter().find(|account| account.chain == Chain::Ethereum).unwrap().address,
            "0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF"
        );
    }
}
