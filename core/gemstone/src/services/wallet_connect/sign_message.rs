use std::str::from_utf8;
use std::sync::{Arc, Mutex, PoisonError};

use gem_derivation::default_derivation_path;
use gem_encoding::decode_base58;
use gem_evm::eip712::hash_typed_data;
use gem_evm::message::eip191_hash_message;
use gem_evm::signer::format_ethereum_message_signature;
use gem_evm::siwe::SiweMessage;
use gem_solana::signer::{format_solana_message_signature, validate_solana_offchain_message};
use gem_tron::address::TronAddress;
use gem_tron::signer::format_tron_message_signature;
use gem_wallet_connect::SignDigestType;
use primitives::{
    Account, ApplicationMetadata, Asset, BlockExplorerLink, Chain, ChainType, OptionStringExt,
    Wallet, WalletId, decode_hex,
};

use crate::application;
use crate::keystore::{GemKeystore, decode_password, keystore_id_for_wallet};
use crate::message::payload::MessagePayloadPreview;
use crate::message::sign_type::{MessageType, SignMessage};
use crate::message::signer::MessageSigner;
use crate::models::copy::address_copy;
use crate::models::list::{GemListRow, GemListRowTitle};
use crate::services::assets::rules::asset_text;
use crate::services::confirm::GemSimulationValue;
use crate::services::error::GemServiceError;
use crate::services::explorer::GemExplorerService;
use crate::services::localization::GemLocalizedText;
use crate::services::name::GemNameService;
use crate::services::simulation::{address_requests, named_payload_rows, payload_fields, warning_rows};
use crate::services::wallet::GemKeystorePassword;
use crate::services::wallet::model::wallet_row;
use crate::services::wallet_connect::model::GemWalletConnectMessageRequest;
use crate::services::wallet_preferences::GemWalletPreferencesService;
use crate::ur::{
    EthDataType, SolSignType, TronDataType, UrDecoder, UrReceiveOutcome,
    decode_signature_ur, encode_eth_sign_request, encode_sol_sign_request,
    encode_tron_sign_request, extract_eip712_chain_id, generate_request_id,
    parse_master_fingerprint,
};

const QR_MESSAGE_SIGN_ORIGIN: &str = "Gem Wallet";

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum GemQrMessageSignOutcome {
    Progress { received: u32, expected: u32 },
    Signed { signature: String },
}

enum QrMessageKind {
    EvmPersonal {
        request_id: [u8; 16],
        data: Vec<u8>,
        sender_address: String,
    },
    EvmTypedData {
        request_id: [u8; 16],
        typed_data_json: String,
        sender_address: String,
    },
    TronPersonal {
        request_id: [u8; 16],
        data: Vec<u8>,
        sender_address: String,
    },
    SolanaPersonal {
        request_id: [u8; 16],
        raw_message: Vec<u8>,
        sender_address: String,
    },
}

struct QrMessageSignState {
    kind: QrMessageKind,
    decoder: UrDecoder,
}

#[derive(uniffi::Object)]
pub struct GemQrMessageSignSession {
    ur_frames: Vec<String>,
    state: Mutex<QrMessageSignState>,
}

#[uniffi::export]
impl GemQrMessageSignSession {
    pub fn ur_frames(&self) -> Vec<String> {
        self.ur_frames.clone()
    }

    pub fn receive_frame(&self, payload: String) -> Result<GemQrMessageSignOutcome, GemServiceError> {
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let trimmed = payload.trim();
        let outcome = state
            .decoder
            .receive(trimmed)
            .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;

        let UrReceiveOutcome::Complete { ur_type, cbor } = outcome else {
            let UrReceiveOutcome::Progress { received, expected } = outcome else {
                return Err(GemServiceError::InvalidInput {
                    msg: "unexpected UR decoder state".to_string(),
                });
            };
            return Ok(GemQrMessageSignOutcome::Progress {
                received: received as u32,
                expected: expected as u32,
            });
        };

        let decoded = decode_signature_ur(&ur_type, &cbor)
            .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;

        let signature = match &state.kind {
            QrMessageKind::EvmPersonal {
                request_id,
                data,
                sender_address,
            } => {
                verify_message_response(&decoded.ur_type, "eth-signature", decoded.request_id, *request_id)?;
                let digest = eip191_hash_message(data);
                format_ethereum_message_signature(&digest, &decoded.signature_bytes, sender_address)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?
            }
            QrMessageKind::EvmTypedData {
                request_id,
                typed_data_json,
                sender_address,
            } => {
                verify_message_response(&decoded.ur_type, "eth-signature", decoded.request_id, *request_id)?;
                let digest = hash_typed_data(typed_data_json)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                format_ethereum_message_signature(&digest, &decoded.signature_bytes, sender_address)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?
            }
            QrMessageKind::TronPersonal {
                request_id,
                data,
                sender_address,
            } => {
                verify_message_response(&decoded.ur_type, "tron-signature", decoded.request_id, *request_id)?;
                format_tron_message_signature(data, sender_address, &decoded.signature_bytes)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?
            }
            QrMessageKind::SolanaPersonal {
                request_id,
                raw_message,
                sender_address,
            } => {
                verify_message_response(&decoded.ur_type, "sol-signature", decoded.request_id, *request_id)?;
                format_solana_message_signature(raw_message, sender_address, &decoded.signature_bytes)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?
            }
        };

        state.decoder = UrDecoder::new();
        Ok(GemQrMessageSignOutcome::Signed { signature })
    }
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct GemSignMessagePreview {
    pub title: GemLocalizedText,
    pub text: String,
    pub primary_fields: Vec<GemListRow>,
    pub secondary_fields: Vec<GemListRow>,
    pub has_critical_warning: bool,
    pub header: Option<GemSimulationValue>,
    pub rows: Vec<GemListRow>,
    pub warnings: Vec<GemListRow>,
}

#[derive(uniffi::Object)]
pub struct GemSignMessageService {
    names: Arc<GemNameService>,
    explorer: Arc<GemExplorerService>,
    keystore: Arc<GemKeystore>,
    password: Arc<dyn GemKeystorePassword>,
    wallet_preferences: Arc<GemWalletPreferencesService>,
}

#[uniffi::export]
impl GemSignMessageService {
    #[uniffi::constructor]
    pub fn new(
        names: Arc<GemNameService>,
        explorer: Arc<GemExplorerService>,
        keystore: Arc<GemKeystore>,
        password: Arc<dyn GemKeystorePassword>,
        wallet_preferences: Arc<GemWalletPreferencesService>,
    ) -> Self {
        Self {
            names,
            explorer,
            keystore,
            password,
            wallet_preferences,
        }
    }

    pub fn requires_qr_sign(&self, wallet_id: WalletId) -> bool {
        self.wallet_preferences.is_qr_signer(wallet_id)
    }

    pub fn prepare_qr_sign(
        &self,
        wallet: Wallet,
        message: SignMessage,
    ) -> Result<Arc<GemQrMessageSignSession>, GemServiceError> {
        if !self.wallet_preferences.is_qr_signer(wallet.id.clone()) {
            return Err(GemServiceError::InvalidInput {
                msg: "wallet is not a QR cold wallet".to_string(),
            });
        }
        let master_fingerprint = self
            .wallet_preferences
            .qr_signer_xfp(wallet.id.clone())
            .as_deref()
            .map(parse_master_fingerprint)
            .transpose()
            .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
        let account = wallet
            .account(message.chain)
            .cloned()
            .ok_or_else(|| GemServiceError::NotFound {
                msg: format!("wallet has no {} account", message.chain),
            })?;
        let derivation_path = if account.derivation_path.trim().is_empty() {
            default_derivation_path(message.chain).to_string()
        } else {
            account.derivation_path.trim().to_string()
        };
        let request_id = generate_request_id()
            .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;

        let (ur_frames, kind) = match message.sign_type {
            SignDigestType::Eip191 | SignDigestType::Siwe => {
                ensure_evm_qr_chain(message.chain)?;
                if let Ok(raw) = from_utf8(&message.data)
                    && let Some(siwe) = SiweMessage::try_parse(raw)
                    && !siwe.address.eq_ignore_ascii_case(&account.address)
                {
                    return Err(GemServiceError::InvalidInput {
                        msg: "SIWE address does not match signing account".to_string(),
                    });
                }
                let address_bytes = decode_evm_address(&account.address)?;
                let chain_id = message.chain.network_id_value().filter(|&id| id > 0);
                let frames = encode_eth_sign_request(
                    request_id,
                    &message.data,
                    EthDataType::PersonalMessage,
                    chain_id,
                    &derivation_path,
                    master_fingerprint,
                    Some(address_bytes),
                    QR_MESSAGE_SIGN_ORIGIN,
                )
                .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                (
                    frames,
                    QrMessageKind::EvmPersonal {
                        request_id,
                        data: message.data,
                        sender_address: account.address,
                    },
                )
            }
            SignDigestType::Eip712 => {
                ensure_evm_qr_chain(message.chain)?;
                let typed_data_json = from_utf8(&message.data)
                    .map_err(|_| GemServiceError::InvalidInput {
                        msg: "EIP-712 message must be valid UTF-8".to_string(),
                    })?
                    .to_string();
                if typed_data_json.trim().is_empty() {
                    return Err(GemServiceError::InvalidInput {
                        msg: "Empty EIP712 message string".to_string(),
                    });
                }
                hash_typed_data(&typed_data_json)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                let address_bytes = decode_evm_address(&account.address)?;
                let chain_id = extract_eip712_chain_id(&typed_data_json)
                    .or_else(|| message.chain.network_id_value().filter(|&id| id > 0));
                let frames = encode_eth_sign_request(
                    request_id,
                    typed_data_json.as_bytes(),
                    EthDataType::TypedData,
                    chain_id,
                    &derivation_path,
                    master_fingerprint,
                    Some(address_bytes),
                    QR_MESSAGE_SIGN_ORIGIN,
                )
                .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                (
                    frames,
                    QrMessageKind::EvmTypedData {
                        request_id,
                        typed_data_json,
                        sender_address: account.address,
                    },
                )
            }
            SignDigestType::TronPersonal => {
                if message.chain != Chain::Tron {
                    return Err(GemServiceError::InvalidInput {
                        msg: format!("Tron personal message is not supported on {}", message.chain),
                    });
                }
                let owner = TronAddress::parse(&account.address)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                let frames = encode_tron_sign_request(
                    request_id,
                    &message.data,
                    TronDataType::PersonalMessage,
                    &derivation_path,
                    master_fingerprint,
                    Some(owner.bytes()),
                    QR_MESSAGE_SIGN_ORIGIN,
                )
                .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                (
                    frames,
                    QrMessageKind::TronPersonal {
                        request_id,
                        data: message.data,
                        sender_address: account.address,
                    },
                )
            }
            SignDigestType::Base58 => {
                if message.chain != Chain::Solana {
                    return Err(GemServiceError::InvalidInput {
                        msg: format!("Base58 sign message is not supported for {:?}", message.chain),
                    });
                }
                let raw_message = decode_base58(&message.data)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                let sender = validate_solana_offchain_message(&raw_message, &account.address)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                let frames = encode_sol_sign_request(
                    request_id,
                    &raw_message,
                    &derivation_path,
                    master_fingerprint,
                    Some(*sender.as_bytes()),
                    QR_MESSAGE_SIGN_ORIGIN,
                    SolSignType::Message,
                )
                .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                (
                    frames,
                    QrMessageKind::SolanaPersonal {
                        request_id,
                        raw_message,
                        sender_address: account.address,
                    },
                )
            }
            SignDigestType::SuiPersonal | SignDigestType::TonPersonal => {
                return Err(GemServiceError::InvalidInput {
                    msg: format!("QR cold wallet message signing is not supported for {}", message.chain),
                });
            }
        };

        Ok(Arc::new(GemQrMessageSignSession {
            ur_frames,
            state: Mutex::new(QrMessageSignState {
                kind,
                decoder: UrDecoder::new(),
            }),
        }))
    }

    pub async fn sign(&self, wallet_id: WalletId, message: SignMessage) -> Result<String, GemServiceError> {
        if self.wallet_preferences.is_qr_signer(wallet_id.clone()) {
            return Err(GemServiceError::InvalidInput {
                msg: "QR cold wallet requires QR message signing".to_string(),
            });
        }
        let password = decode_password(&self.password.get_password(false)?);
        Ok(MessageSigner::new(message).sign_with_keystore(self.keystore.clone(), keystore_id_for_wallet(wallet_id.id()), password)?)
    }

    pub fn preview(&self, request: GemWalletConnectMessageRequest) -> GemSignMessagePreview {
        let GemWalletConnectMessageRequest {
            chain,
            wallet,
            account,
            session,
            simulation,
            message,
            assets,
            ..
        } = request;
        let signer = MessageSigner::new(message);
        let has_critical_warning = simulation.has_critical_warning();
        let warnings = warning_rows(&simulation.warnings);
        let header = GemSimulationValue::from_simulation(&simulation, &assets);
        let fields = payload_fields(simulation.payload, header.is_some());
        let address_url = |chain, address| self.explorer.get_address_url(chain, address);
        let payload = signer.payload_preview(fields, address_url).ok().flatten();
        GemSignMessagePreview {
            title: payload.as_ref().map(|preview| preview.message_type).unwrap_or(MessageType::Text).title(),
            text: signer.plain_preview(),
            primary_fields: payload.as_ref().map(|preview| preview.primary.clone()).unwrap_or_default(),
            secondary_fields: payload.map(|preview| preview.secondary).unwrap_or_default(),
            has_critical_warning,
            rows: review_rows(chain, &wallet, &account, &session.metadata, header.is_some(), address_url),
            warnings,
            header,
        }
    }

    pub fn payload_preview(&self, message: SignMessage) -> Option<MessagePayloadPreview> {
        MessageSigner::new(message).payload_preview(Vec::new(), |chain, address| self.explorer.get_address_url(chain, address)).ok().flatten()
    }

    pub async fn with_address_names(&self, chain: Chain, preview: GemSignMessagePreview) -> GemSignMessagePreview {
        let requests = [address_requests(&preview.primary_fields, chain), address_requests(&preview.secondary_fields, chain)].concat();
        if requests.is_empty() {
            return preview;
        }
        let names = self.names.get_address_names(requests).await.unwrap_or_default();
        GemSignMessagePreview {
            primary_fields: named_payload_rows(preview.primary_fields, &names),
            secondary_fields: named_payload_rows(preview.secondary_fields, &names),
            ..preview
        }
    }
}

fn review_rows(chain: Chain, wallet: &Wallet, account: &Account, metadata: &ApplicationMetadata, shows_app: bool, address_url: impl Fn(Chain, String) -> BlockExplorerLink) -> Vec<GemListRow> {
    let app = shows_app.then(|| GemListRow::app(metadata.short_name(), application::icon_url(metadata), Some(metadata.url.clone()).non_empty()));
    let sender = GemListRow::wallet(wallet_row(wallet.clone()), address_copy(chain, account.address.clone()), address_url(chain, account.address.clone()));
    app.into_iter().chain([sender, network_row(chain)]).collect()
}

pub(crate) fn network_row(chain: Chain) -> GemListRow {
    GemListRow::Network {
        title: GemListRowTitle::Network,
        chain,
        name: asset_text(&Asset::from_chain(chain)).network_name,
    }
}

fn ensure_evm_qr_chain(chain: Chain) -> Result<(), GemServiceError> {
    if (chain.chain_type() == ChainType::Ethereum && chain != Chain::Tempo)
        || chain.chain_type() == ChainType::HyperCore
    {
        Ok(())
    } else {
        Err(GemServiceError::InvalidInput {
            msg: format!("QR EVM message signing is not supported for {chain}"),
        })
    }
}

fn decode_evm_address(address: &str) -> Result<[u8; 20], GemServiceError> {
    let bytes = decode_hex(address)
        .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
    bytes.try_into().map_err(|_| GemServiceError::InvalidInput {
        msg: "EVM sender address must be 20 bytes".to_string(),
    })
}

fn verify_message_response(
    actual_type: &str,
    expected_type: &str,
    response_request_id: Option<[u8; 16]>,
    expected_request_id: [u8; 16],
) -> Result<(), GemServiceError> {
    if actual_type != expected_type {
        return Err(GemServiceError::InvalidInput {
            msg: format!("expected {expected_type} QR response, got {actual_type}"),
        });
    }
    if let Some(actual_id) = response_request_id
        && actual_id != expected_request_id
    {
        return Err(GemServiceError::InvalidInput {
            msg: "QR signature response belongs to a different request".to_string(),
        });
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use futures::executor::block_on;
    use gem_derivation::derive_account_from_private_key;
    use gem_encoding::encode_base58;
    use gem_evm::testkit::eip712_mock::mock_eip712_json;
    use gem_tron::signer::tron_hash_message;
    use primitives::testkit::temp_dir::TempDir;
    use primitives::WalletType;
    use signer::{Ed25519KeyPair, Signer};
    use zeroize::Zeroizing;

    use super::*;
    use crate::api::GemDeviceApiClient;
    use crate::models::list::GemRowMenuItem;
    use crate::services::preferences::{GemPreferencesService, testkit::MemoryPreferencesStore};
    use crate::services::wallet::testkit::{MemoryAddressStore, MemoryKeystorePassword};
    use crate::services::wallet_preferences::testkit::MemoryWalletPreferencesStore;
    use crate::testkit::TestAlienProvider;
    use crate::ur::cbor::{CborValue, decode_cbor, encode_cbor};
    use crate::ur::UrEncoder;

    const TEST_KEY: [u8; 32] = [0x55; 32];
    const OTHER_KEY: [u8; 32] = [0x66; 32];

    fn build_service() -> (GemSignMessageService, Arc<GemWalletPreferencesService>, TempDir) {
        let dir = TempDir::new().unwrap();
        let keystore = GemKeystore::new(dir.path().to_string_lossy().to_string()).unwrap();
        let provider = Arc::new(TestAlienProvider::with_status(503));
        let names = Arc::new(GemNameService::new(
            Arc::new(GemDeviceApiClient::mock(provider)),
            Arc::new(MemoryAddressStore::default()),
        ));
        let preferences = Arc::new(GemPreferencesService::new(Arc::new(MemoryPreferencesStore::default())));
        let explorer = Arc::new(GemExplorerService::new(preferences));
        let password = Arc::new(MemoryKeystorePassword::default());
        let wallet_preferences = Arc::new(GemWalletPreferencesService::new(Arc::new(
            MemoryWalletPreferencesStore::default(),
        )));
        let service = GemSignMessageService::new(
            names,
            explorer,
            keystore,
            password,
            wallet_preferences.clone(),
        );
        (service, wallet_preferences, dir)
    }

    fn sign_ur_response(ur_type: &str, request_id: &[u8], signature: &[u8]) -> String {
        let cbor = encode_cbor(&CborValue::Map(vec![
            (
                CborValue::Unsigned(1),
                CborValue::Tag(37, Box::new(CborValue::Bytes(request_id.to_vec()))),
            ),
            (CborValue::Unsigned(2), CborValue::Bytes(signature.to_vec())),
        ]));
        UrEncoder::encode(ur_type, &cbor, cbor.len()).remove(0)
    }

    fn request_cbor(frames: &[String]) -> Vec<u8> {
        let mut decoder = UrDecoder::new();
        for frame in frames {
            if let UrReceiveOutcome::Complete { cbor, .. } = decoder.receive(frame).unwrap() {
                return cbor;
            }
        }
        panic!("UR frames did not complete");
    }

    #[test]
    fn test_qr_message_signing_matches_hot_signer_across_evm_tron_and_solana() {
        let (service, wallet_preferences, _dir) = build_service();
        let eth_acc = derive_account_from_private_key(&TEST_KEY, Chain::Ethereum).unwrap();
        let tron_acc = derive_account_from_private_key(&TEST_KEY, Chain::Tron).unwrap();
        let sol_acc = derive_account_from_private_key(&TEST_KEY, Chain::Solana).unwrap();
        let wallet = Wallet {
            id: WalletId::Multicoin("qr_msg".to_string()),
            ..Wallet::mock_with_accounts(vec![eth_acc, tron_acc, sol_acc])
        };
        wallet_preferences
            .set_qr_signer_xfp(wallet.id.clone(), Some("73c5da0a".to_string()))
            .unwrap();
        assert!(service.requires_qr_sign(wallet.id.clone()));

        let eip191_msg = SignMessage {
            chain: Chain::Ethereum,
            sign_type: SignDigestType::Eip191,
            data: b"Hello QR Cold Wallet".to_vec(),
        };
        assert!(block_on(service.sign(wallet.id.clone(), eip191_msg.clone())).is_err());

        let eip191_session = service.prepare_qr_sign(wallet.clone(), eip191_msg.clone()).unwrap();
        let root = decode_cbor(&request_cbor(&eip191_session.ur_frames())).unwrap();
        let req_id = root.map_get_uint(1).unwrap().as_bytes().unwrap();
        let wrong_sig = Signer::sign_ethereum_digest(&eip191_hash_message(&eip191_msg.data), &OTHER_KEY).unwrap();
        assert!(eip191_session.receive_frame(sign_ur_response("eth-signature", req_id, &wrong_sig)).is_err());

        let good_sig = Signer::sign_ethereum_digest(&eip191_hash_message(&eip191_msg.data), &TEST_KEY).unwrap();
        let expected_eip191 = MessageSigner::new(eip191_msg).sign(Zeroizing::new(TEST_KEY.to_vec())).unwrap();
        assert_eq!(
            eip191_session.receive_frame(sign_ur_response("eth-signature", req_id, &good_sig)).unwrap(),
            GemQrMessageSignOutcome::Signed {
                signature: expected_eip191,
            }
        );

        let typed_json = mock_eip712_json(1);
        let eip712_msg = SignMessage {
            chain: Chain::Ethereum,
            sign_type: SignDigestType::Eip712,
            data: typed_json.as_bytes().to_vec(),
        };
        let eip712_session = service.prepare_qr_sign(wallet.clone(), eip712_msg.clone()).unwrap();
        let root = decode_cbor(&request_cbor(&eip712_session.ur_frames())).unwrap();
        let req_id = root.map_get_uint(1).unwrap().as_bytes().unwrap();
        let eip712_sig = Signer::sign_ethereum_digest(&hash_typed_data(&typed_json).unwrap(), &TEST_KEY).unwrap();
        let expected_eip712 = MessageSigner::new(eip712_msg).sign(Zeroizing::new(TEST_KEY.to_vec())).unwrap();
        assert_eq!(
            eip712_session.receive_frame(sign_ur_response("eth-signature", req_id, &eip712_sig)).unwrap(),
            GemQrMessageSignOutcome::Signed {
                signature: expected_eip712,
            }
        );

        let tron_msg = SignMessage {
            chain: Chain::Tron,
            sign_type: SignDigestType::TronPersonal,
            data: b"Tron QR personal message".to_vec(),
        };
        let tron_session = service.prepare_qr_sign(wallet.clone(), tron_msg.clone()).unwrap();
        let root = decode_cbor(&request_cbor(&tron_session.ur_frames())).unwrap();
        let req_id = root.map_get_uint(1).unwrap().as_bytes().unwrap();
        let mut tron_raw_sig = Signer::sign_ethereum_digest(&tron_hash_message(&tron_msg.data), &TEST_KEY).unwrap();
        tron_raw_sig[64] -= 27;
        let expected_tron = MessageSigner::new(tron_msg).sign(Zeroizing::new(TEST_KEY.to_vec())).unwrap();
        assert_eq!(
            tron_session.receive_frame(sign_ur_response("tron-signature", req_id, &tron_raw_sig)).unwrap(),
            GemQrMessageSignOutcome::Signed {
                signature: expected_tron,
            }
        );

        let sol_raw = b"Solana off-chain sign message";
        let sol_msg = SignMessage {
            chain: Chain::Solana,
            sign_type: SignDigestType::Base58,
            data: encode_base58(sol_raw).into_bytes(),
        };
        let sol_session = service.prepare_qr_sign(wallet, sol_msg.clone()).unwrap();
        let root = decode_cbor(&request_cbor(&sol_session.ur_frames())).unwrap();
        let req_id = root.map_get_uint(1).unwrap().as_bytes().unwrap();
        let sol_sig = Ed25519KeyPair::from_private_key(&TEST_KEY).unwrap().sign(sol_raw);
        let expected_sol = MessageSigner::new(sol_msg).sign(Zeroizing::new(TEST_KEY.to_vec())).unwrap();
        assert_eq!(
            sol_session.receive_frame(sign_ur_response("sol-signature", req_id, &sol_sig)).unwrap(),
            GemQrMessageSignOutcome::Signed {
                signature: expected_sol,
            }
        );
    }

    #[test]
    fn test_review_rows_name_the_app_only_beside_a_header() {
        let wallet = Wallet::mock_with_type(WalletType::Multicoin, &[Chain::Ethereum]);
        let link = |chain: Chain, address: String| BlockExplorerLink {
            name: "Etherscan".to_string(),
            link: format!("https://etherscan.io/address/{address}?{chain}"),
        };

        let account = wallet.account(Chain::Ethereum).unwrap().clone();
        let with_header = review_rows(Chain::Ethereum, &wallet, &account, &ApplicationMetadata::mock(), true, link);
        assert!(matches!(&with_header[0], GemListRow::App { name, menu, .. } if name == "Test Dapp" && matches!(menu.as_slice(), [GemRowMenuItem::Open { url, .. }] if url == "https://example.com")));
        assert!(matches!(&with_header[1], GemListRow::Wallet { menu, .. } if matches!(menu.first(), Some(GemRowMenuItem::Copy { copy }) if copy.value == account.address)));
        assert!(matches!(&with_header[2], GemListRow::Network { chain: Chain::Ethereum, .. }));

        let plain = review_rows(Chain::Ethereum, &wallet, &account, &ApplicationMetadata::mock(), false, link);
        assert_eq!(plain.len(), 2, "the app header already names the app");
        assert!(matches!(plain[0], GemListRow::Wallet { .. }));
    }
}
