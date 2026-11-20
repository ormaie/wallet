use std::sync::{Mutex, PoisonError};

use gem_derivation::derive_wallet_id_from_account;
use primitives::{Account, Chain, ChainType, WalletId, WalletType};

use crate::services::error::GemServiceError;
use crate::ur::{
    UrDecoder, UrReceiveOutcome, decode_pairing_ur, merge_unique_accounts,
};

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GemQrPairingPreview {
    pub is_complete: bool,
    pub received_parts: u32,
    pub expected_parts: u32,
    pub wallet_type: WalletType,
    pub master_fingerprint: Option<String>,
    pub device: Option<String>,
    pub chains: Vec<Chain>,
    pub accounts: Vec<Account>,
}

struct PairingSessionState {
    decoder: UrDecoder,
    master_fingerprint: Option<String>,
    device: Option<String>,
    accounts: Vec<Account>,
}

#[derive(uniffi::Object)]
pub struct GemQrPairingSession {
    state: Mutex<PairingSessionState>,
}

impl Default for GemQrPairingSession {
    fn default() -> Self {
        Self::new()
    }
}

impl GemQrPairingSession {
    pub fn new() -> Self {
        Self {
            state: Mutex::new(PairingSessionState {
                decoder: UrDecoder::new(),
                master_fingerprint: None,
                device: None,
                accounts: Vec::new(),
            }),
        }
    }

    pub(super) fn snapshot(&self) -> (Option<String>, Vec<Account>) {
        let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        (state.master_fingerprint.clone(), ordered_accounts(&state.accounts))
    }
}

#[uniffi::export]
impl GemQrPairingSession {
    pub fn receive_frame(&self, payload: String) -> Result<GemQrPairingPreview, GemServiceError> {
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let outcome = state
            .decoder
            .receive(&payload)
            .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;

        match outcome {
            UrReceiveOutcome::Progress { received, expected } => {
                let accounts = ordered_accounts(&state.accounts);
                let wallet_type = preview_wallet_type(&accounts);
                Ok(GemQrPairingPreview {
                    is_complete: false,
                    received_parts: received as u32,
                    expected_parts: expected as u32,
                    wallet_type,
                    master_fingerprint: state.master_fingerprint.clone(),
                    device: state.device.clone(),
                    chains: summary_chains(&accounts),
                    accounts,
                })
            }
            UrReceiveOutcome::Complete { ur_type, cbor } => {
                state.decoder = UrDecoder::new();
                let parsed = decode_pairing_ur(&ur_type, &cbor)
                    .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
                if let (Some(existing), Some(incoming)) = (&state.master_fingerprint, &parsed.master_fingerprint)
                    && !existing.eq_ignore_ascii_case(incoming)
                {
                    return Err(GemServiceError::InvalidInput {
                        msg: format!("QR code belongs to a different wallet ({incoming} vs {existing})"),
                    });
                }
                if state.master_fingerprint.is_none() {
                    state.master_fingerprint = parsed.master_fingerprint;
                }
                if state.device.is_none() {
                    state.device = parsed.device;
                }
                merge_unique_accounts(&mut state.accounts, parsed.accounts);

                let accounts = ordered_accounts(&state.accounts);
                let wallet_type = preview_wallet_type(&accounts);
                let chains = summary_chains(&accounts);
                Ok(GemQrPairingPreview {
                    is_complete: true,
                    received_parts: 1,
                    expected_parts: 1,
                    wallet_type,
                    master_fingerprint: state.master_fingerprint.clone(),
                    device: state.device.clone(),
                    chains,
                    accounts,
                })
            }
        }
    }
}

pub(super) fn resolve_qr_wallet_identity(accounts: &[Account]) -> Result<(WalletId, WalletType), GemServiceError> {
    match accounts {
        [] => Err(GemServiceError::InvalidInput {
            msg: "no wallet accounts scanned".to_string(),
        }),
        [single] => {
            let wallet_id = derive_wallet_id_from_account(single, WalletType::Single)
                .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
            Ok((wallet_id, WalletType::Single))
        }
        many => {
            let anchor = many
                .iter()
                .find(|account| account.chain == Chain::Ethereum)
                .unwrap_or(&many[0]);
            let wallet_id = derive_wallet_id_from_account(anchor, WalletType::Multicoin)
                .map_err(|error| GemServiceError::InvalidInput { msg: error.to_string() })?;
            Ok((wallet_id, WalletType::Multicoin))
        }
    }
}

pub(super) fn ordered_accounts(accounts: &[Account]) -> Vec<Account> {
    Chain::all()
        .into_iter()
        .filter_map(|chain| accounts.iter().find(|account| account.chain == chain).cloned())
        .collect()
}

fn preview_wallet_type(accounts: &[Account]) -> WalletType {
    match accounts.len() {
        1 => WalletType::Single,
        _ => WalletType::Multicoin,
    }
}

fn summary_chains(accounts: &[Account]) -> Vec<Chain> {
    let mut chains = Vec::new();
    let has_evm = accounts
        .iter()
        .any(|account| matches!(account.chain.chain_type(), ChainType::Ethereum | ChainType::HyperCore));
    for chain in Chain::all() {
        match chain.chain_type() {
            ChainType::Ethereum => {
                if chain == Chain::Ethereum && has_evm {
                    chains.push(Chain::Ethereum);
                }
            }
            ChainType::HyperCore => {}
            _ => {
                if accounts.iter().any(|account| account.chain == chain) {
                    chains.push(chain);
                }
            }
        }
    }
    chains
}
