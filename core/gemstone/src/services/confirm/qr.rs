use std::str::from_utf8;
use std::sync::{Arc, Mutex, PoisonError};

use gem_bitcoin::signer::{build_transfer_psbt, decode_psbt_base64, extract_signed_psbt_transaction};
use gem_derivation::default_derivation_path;
use gem_evm::eip712::hash_typed_data;
use gem_evm::signer::{
    Eip1559Transaction, attach_typed_data_signature, build_unsigned_eip1559_transactions,
};
use gem_hypercore::signer::{
    HyperCoreSignStep, build_hypercore_sign_steps, complete_hypercore_user_step,
};
use gem_solana::signer::{UnsignedSolanaTransaction, build_unsigned_solana_transaction};
use gem_tron::signer::{UnsignedTronTransaction, build_unsigned_tron_transactions};
use primitives::swap::SwapQuoteDataType;
use primitives::{
    BitcoinChain, Chain, ChainType, SignerError, SignerInput, TransactionInputType, TransactionType,
    TransferDataOutputType, decode_hex, hex,
};
use swapper::Quote;
use swapper::permit2_data::PermitSingle;

use super::error::sign_error;
use super::{
    GemConfirmData, GemConfirmError, GemConfirmInput, GemConfirmTransferService, GemSubmitResult,
    SendInput,
};
use crate::models::transaction::GemSignedTransaction;
use crate::signer::{
    expected_transaction_types, payment_approval_signer_input, swap_transfer_signer_input,
};
use crate::ur::{
    EthDataType, SolSignType, TronDataType, UrDecoder, UrReceiveOutcome,
    decode_signature_ur, encode_crypto_psbt, encode_eth_sign_request, encode_sol_sign_request,
    encode_tron_sign_request, extract_eip712_chain_id, generate_request_id,
    parse_master_fingerprint,
};

const QR_SIGN_ORIGIN: &str = "Gem Wallet";
const PSBT_MAGIC_HEX: &str = "70736274ff";
const PSBT_BASE64_PREFIX: &str = "cHNidP";

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GemQrSignStep {
    pub step_index: u32,
    pub total_steps: u32,
    pub ur_type: String,
    pub ur_frames: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum GemQrSignOutcome {
    Progress { received: u32, expected: u32 },
    NextStep { step: GemQrSignStep },
    ReadyToSubmit,
}

#[allow(clippy::large_enum_variant)]
enum PendingQrTask {
    Permit2 {
        request_id: [u8; 16],
        typed_data_json: String,
        sender_address: String,
        quote: Quote,
        permit_single: PermitSingle,
        ur_frames: Vec<String>,
    },
    EvmTransaction {
        request_id: [u8; 16],
        transaction: Eip1559Transaction,
        sender_address: String,
        transaction_type: TransactionType,
        sequence: Option<u64>,
        ur_frames: Vec<String>,
    },
    EvmTypedData {
        request_id: [u8; 16],
        typed_data_json: String,
        sender_address: String,
        transaction_type: TransactionType,
        ur_frames: Vec<String>,
    },
    BitcoinPsbt {
        signer_input: SignerInput,
        transaction_type: TransactionType,
        sequence: Option<u64>,
        ur_frames: Vec<String>,
    },
    SolanaTransaction {
        request_id: [u8; 16],
        transaction: UnsignedSolanaTransaction,
        transaction_type: TransactionType,
        sequence: Option<u64>,
        ur_frames: Vec<String>,
    },
    TronTransaction {
        request_id: [u8; 16],
        transaction: UnsignedTronTransaction,
        transaction_type: TransactionType,
        sequence: Option<u64>,
        ur_frames: Vec<String>,
    },
    HyperCoreTypedData {
        request_id: [u8; 16],
        typed_data_json: String,
        action_json: String,
        timestamp: u64,
        sender_address: String,
        transaction_type: TransactionType,
        sequence: Option<u64>,
        ur_frames: Vec<String>,
    },
}

impl PendingQrTask {
    fn ur_type(&self) -> &'static str {
        match self {
            Self::Permit2 { .. }
            | Self::EvmTransaction { .. }
            | Self::EvmTypedData { .. }
            | Self::HyperCoreTypedData { .. } => "eth-sign-request",
            Self::BitcoinPsbt { .. } => "crypto-psbt",
            Self::SolanaTransaction { .. } => "sol-sign-request",
            Self::TronTransaction { .. } => "tron-sign-request",
        }
    }

    fn ur_frames(&self) -> &[String] {
        match self {
            Self::Permit2 { ur_frames, .. }
            | Self::EvmTransaction { ur_frames, .. }
            | Self::EvmTypedData { ur_frames, .. }
            | Self::BitcoinPsbt { ur_frames, .. }
            | Self::SolanaTransaction { ur_frames, .. }
            | Self::TronTransaction { ur_frames, .. }
            | Self::HyperCoreTypedData { ur_frames, .. } => ur_frames,
        }
    }
}

#[allow(clippy::large_enum_variant)]
enum QrSlot {
    Ready(GemSignedTransaction),
    Pending(PendingQrTask),
}

struct QrSignState {
    input: SendInput,
    master_fingerprint: Option<u32>,
    slots: Vec<QrSlot>,
    completed_qr_steps: u32,
    total_qr_steps: u32,
    decoder: UrDecoder,
}

impl QrSignState {
    fn current_step(&self) -> Option<GemQrSignStep> {
        let pending = self.slots.iter().find_map(|slot| match slot {
            QrSlot::Pending(task) => Some(task),
            QrSlot::Ready(_) => None,
        })?;
        Some(GemQrSignStep {
            step_index: self.completed_qr_steps + 1,
            total_steps: self.total_qr_steps.max(self.completed_qr_steps + 1),
            ur_type: pending.ur_type().to_string(),
            ur_frames: pending.ur_frames().to_vec(),
        })
    }

    fn all_ready(&self) -> Option<Vec<GemSignedTransaction>> {
        let mut signed = Vec::with_capacity(self.slots.len());
        for slot in &self.slots {
            match slot {
                QrSlot::Ready(item) => signed.push(item.clone()),
                QrSlot::Pending(_) => return None,
            }
        }
        Some(signed)
    }
}

#[allow(clippy::large_enum_variant)]
enum StepResolution {
    Progress { received: u32, expected: u32 },
    SignedTransaction(GemSignedTransaction),
    SignedPermit2 {
        quote: Quote,
        permit_single: PermitSingle,
        signature: Vec<u8>,
    },
}

#[derive(uniffi::Object)]
pub struct GemQrSignSession {
    service: Arc<GemConfirmTransferService>,
    state: Mutex<QrSignState>,
}

impl GemQrSignSession {
    pub(super) fn prepare(
        service: Arc<GemConfirmTransferService>,
        input: SendInput,
    ) -> Result<Arc<Self>, GemConfirmError> {
        let chain = input.confirm.input.transfer.input_type.get_asset().chain();
        let master_fingerprint = service
            .qr_signer_xfp(&input.wallet.id)
            .as_deref()
            .map(parse_master_fingerprint)
            .transpose()
            .map_err(|error| qr_sign_error(chain, error.to_string()))?;

        let transfer = &input.confirm.input.transfer;
        if let (Some(quote), Some(approval)) = (
            &input.swap_quote,
            transfer
                .input_type
                .get_swap_data()
                .ok()
                .and_then(|swap| swap.data.permit2.clone()),
        ) {
            if chain.chain_type() != ChainType::Ethereum || chain == Chain::Tempo {
                return Err(qr_sign_error(chain, format!("Permit2 QR signing is not supported on {chain}")));
            }
            let (permit_single, message) = service.swap.permit2_signing_data(quote, &approval)?;
            let typed_data_json = from_utf8(&message.data)
                .map_err(|_| qr_sign_error(chain, "invalid Permit2 EIP-712 UTF-8".to_string()))?
                .to_string();
            hash_typed_data(&typed_data_json)
                .map_err(|error| sign_error(&transfer.input_type, error.into()))?;
            let account = &input.confirm.input.from;
            let derivation_path = effective_derivation_path(chain, &account.derivation_path);
            let address_bytes = decode_evm_address(&account.address, chain)?;
            let request_id = generate_request_id().map_err(|error| qr_sign_error(chain, error.to_string()))?;
            let chain_id = extract_eip712_chain_id(&typed_data_json)
                .or_else(|| input.confirm.metadata.get_chain_id().ok().and_then(|id| id.parse::<u64>().ok()));
            let ur_frames = encode_eth_sign_request(
                request_id,
                typed_data_json.as_bytes(),
                EthDataType::TypedData,
                chain_id,
                &derivation_path,
                master_fingerprint,
                Some(address_bytes),
                QR_SIGN_ORIGIN,
            )
            .map_err(|error| qr_sign_error(chain, error.to_string()))?;

            let slot = QrSlot::Pending(PendingQrTask::Permit2 {
                request_id,
                typed_data_json,
                sender_address: account.address.clone(),
                quote: quote.clone(),
                permit_single,
                ur_frames,
            });

            return Ok(Arc::new(Self {
                service,
                state: Mutex::new(QrSignState {
                    input,
                    master_fingerprint,
                    slots: vec![slot],
                    completed_qr_steps: 0,
                    total_qr_steps: 2,
                    decoder: UrDecoder::new(),
                }),
            }));
        }

        let slots = build_qr_slots(&input, master_fingerprint)?;
        let pending_count = slots
            .iter()
            .filter(|slot| matches!(slot, QrSlot::Pending(_)))
            .count() as u32;
        if pending_count == 0 {
            return Err(qr_sign_error(chain, "transaction requires no QR signature".to_string()));
        }

        Ok(Arc::new(Self {
            service,
            state: Mutex::new(QrSignState {
                input,
                master_fingerprint,
                slots,
                completed_qr_steps: 0,
                total_qr_steps: pending_count,
                decoder: UrDecoder::new(),
            }),
        }))
    }
}

#[uniffi::export]
impl GemQrSignSession {
    pub fn current_step(&self) -> Option<GemQrSignStep> {
        self.state
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .current_step()
    }

    pub async fn receive_frame(&self, payload: String) -> Result<GemQrSignOutcome, GemConfirmError> {
        let permit_rebuild = {
            let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
            let chain = state.input.confirm.input.transfer.input_type.get_asset().chain();
            let input_type = state.input.confirm.input.transfer.input_type.clone();
            let Some(pending_index) = state
                .slots
                .iter()
                .position(|slot| matches!(slot, QrSlot::Pending(_)))
            else {
                return Ok(GemQrSignOutcome::ReadyToSubmit);
            };

            let resolution = {
                let state = &mut *state;
                let QrSlot::Pending(task) = &state.slots[pending_index] else {
                    return Ok(GemQrSignOutcome::ReadyToSubmit);
                };
                resolve_incoming_frame(&mut state.decoder, task, &input_type, chain, &payload)?
            };

            match resolution {
                StepResolution::Progress { received, expected } => {
                    return Ok(GemQrSignOutcome::Progress { received, expected });
                }
                StepResolution::SignedTransaction(signed) => {
                    state.decoder = UrDecoder::new();
                    state.slots[pending_index] = QrSlot::Ready(signed);
                    state.completed_qr_steps += 1;
                    return match state.current_step() {
                        Some(step) => Ok(GemQrSignOutcome::NextStep { step }),
                        None => Ok(GemQrSignOutcome::ReadyToSubmit),
                    };
                }
                StepResolution::SignedPermit2 {
                    quote,
                    permit_single,
                    signature,
                } => {
                    state.decoder = UrDecoder::new();
                    (
                        state.input.clone(),
                        state.master_fingerprint,
                        quote,
                        permit_single,
                        signature,
                    )
                }
            }
        };

        let (current_input, master_fingerprint, quote, permit_single, signature) = permit_rebuild;
        let rebuilt_transfer = Box::pin(self.service.swap.build_transfer_with_permit_signature(
            &current_input.wallet,
            &quote,
            &current_input.confirm.input.transfer,
            permit_single,
            signature,
        ))
        .await?;

        let updated_input = SendInput {
            confirm: GemConfirmData {
                input: GemConfirmInput {
                    transfer: rebuilt_transfer,
                    ..current_input.confirm.input
                },
                ..current_input.confirm
            },
            swap_quote: None,
            ..current_input
        };
        let new_slots = build_qr_slots(&updated_input, master_fingerprint)?;
        let new_pending = new_slots
            .iter()
            .filter(|slot| matches!(slot, QrSlot::Pending(_)))
            .count() as u32;

        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        state.input = updated_input;
        state.slots = new_slots;
        state.completed_qr_steps += 1;
        state.total_qr_steps = state.completed_qr_steps + new_pending;
        match state.current_step() {
            Some(step) => Ok(GemQrSignOutcome::NextStep { step }),
            None => Ok(GemQrSignOutcome::ReadyToSubmit),
        }
    }

    pub async fn submit(&self) -> Result<GemSubmitResult, GemConfirmError> {
        let (input, signed) = {
            let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
            let chain = state.input.confirm.input.transfer.input_type.get_asset().chain();
            let signed = state
                .all_ready()
                .ok_or_else(|| qr_sign_error(chain, "QR signing is not complete".to_string()))?;
            (state.input.clone(), signed)
        };
        Box::pin(self.service.finish_submit(input, signed)).await
    }
}

fn resolve_incoming_frame(
    decoder: &mut UrDecoder,
    task: &PendingQrTask,
    input_type: &TransactionInputType,
    chain: Chain,
    payload: &str,
) -> Result<StepResolution, GemConfirmError> {
    let trimmed = payload.trim();
    if let PendingQrTask::BitcoinPsbt {
        signer_input,
        transaction_type,
        sequence,
        ..
    } = task
    {
        if trimmed.starts_with(PSBT_BASE64_PREFIX) {
            let psbt_bytes = decode_psbt_base64(trimmed)
                .map_err(|error| sign_error(input_type, error.into()))?;
            let raw_tx = extract_signed_psbt_transaction(BitcoinChain::Bitcoin, signer_input, &psbt_bytes)
                .map_err(|error| sign_error(input_type, error.into()))?;
            return Ok(StepResolution::SignedTransaction(GemSignedTransaction {
                data: raw_tx,
                transaction_type: transaction_type.clone(),
                sequence: *sequence,
            }));
        }
        if trimmed.len() >= PSBT_MAGIC_HEX.len()
            && trimmed[..PSBT_MAGIC_HEX.len()].eq_ignore_ascii_case(PSBT_MAGIC_HEX)
        {
            let psbt_bytes = hex::decode(trimmed)
                .map_err(|error| qr_sign_error(chain, format!("invalid hex PSBT: {error}")))?;
            let raw_tx = extract_signed_psbt_transaction(BitcoinChain::Bitcoin, signer_input, &psbt_bytes)
                .map_err(|error| sign_error(input_type, error.into()))?;
            return Ok(StepResolution::SignedTransaction(GemSignedTransaction {
                data: raw_tx,
                transaction_type: transaction_type.clone(),
                sequence: *sequence,
            }));
        }
    }

    let outcome = decoder
        .receive(trimmed)
        .map_err(|error| qr_sign_error(chain, error.to_string()))?;

    let UrReceiveOutcome::Complete { ur_type, cbor } = outcome else {
        let UrReceiveOutcome::Progress { received, expected } = outcome else {
            return Err(qr_sign_error(chain, "unexpected UR decoder state".to_string()));
        };
        return Ok(StepResolution::Progress {
            received: received as u32,
            expected: expected as u32,
        });
    };

    let decoded = decode_signature_ur(&ur_type, &cbor)
        .map_err(|error| qr_sign_error(chain, error.to_string()))?;

    match task {
        PendingQrTask::Permit2 {
            request_id,
            typed_data_json,
            sender_address,
            quote,
            permit_single,
            ..
        } => {
            verify_ur_response(&decoded.ur_type, "eth-signature", decoded.request_id, *request_id, chain)?;
            let signature_hex = attach_typed_data_signature(
                typed_data_json.as_bytes(),
                &decoded.signature_bytes,
                sender_address,
            )
            .map_err(|error| sign_error(input_type, error.into()))?;
            let signature = decode_hex(&signature_hex)
                .map_err(|error| qr_sign_error(chain, error.to_string()))?;
            Ok(StepResolution::SignedPermit2 {
                quote: quote.clone(),
                permit_single: permit_single.clone(),
                signature,
            })
        }
        PendingQrTask::EvmTransaction {
            request_id,
            transaction,
            sender_address,
            transaction_type,
            sequence,
            ..
        } => {
            verify_ur_response(&decoded.ur_type, "eth-signature", decoded.request_id, *request_id, chain)?;
            let raw = transaction
                .attach_signature(&decoded.signature_bytes, sender_address)
                .map_err(|error| sign_error(input_type, error.into()))?;
            Ok(StepResolution::SignedTransaction(GemSignedTransaction {
                data: hex::encode(raw),
                transaction_type: transaction_type.clone(),
                sequence: *sequence,
            }))
        }
        PendingQrTask::EvmTypedData {
            request_id,
            typed_data_json,
            sender_address,
            transaction_type,
            ..
        } => {
            verify_ur_response(&decoded.ur_type, "eth-signature", decoded.request_id, *request_id, chain)?;
            let data = attach_typed_data_signature(
                typed_data_json.as_bytes(),
                &decoded.signature_bytes,
                sender_address,
            )
            .map_err(|error| sign_error(input_type, error.into()))?;
            Ok(StepResolution::SignedTransaction(GemSignedTransaction {
                data,
                transaction_type: transaction_type.clone(),
                sequence: None,
            }))
        }
        PendingQrTask::BitcoinPsbt {
            signer_input,
            transaction_type,
            sequence,
            ..
        } => {
            if decoded.ur_type != "crypto-psbt" {
                return Err(qr_sign_error(
                    chain,
                    format!("expected crypto-psbt QR response, got {}", decoded.ur_type),
                ));
            }
            let data = extract_signed_psbt_transaction(
                BitcoinChain::Bitcoin,
                signer_input,
                &decoded.signature_bytes,
            )
            .map_err(|error| sign_error(input_type, error.into()))?;
            Ok(StepResolution::SignedTransaction(GemSignedTransaction {
                data,
                transaction_type: transaction_type.clone(),
                sequence: *sequence,
            }))
        }
        PendingQrTask::SolanaTransaction {
            request_id,
            transaction,
            transaction_type,
            sequence,
            ..
        } => {
            verify_ur_response(&decoded.ur_type, "sol-signature", decoded.request_id, *request_id, chain)?;
            let data = transaction
                .clone()
                .attach_signature(&decoded.signature_bytes)
                .map_err(|error| sign_error(input_type, error.into()))?;
            Ok(StepResolution::SignedTransaction(GemSignedTransaction {
                data,
                transaction_type: transaction_type.clone(),
                sequence: *sequence,
            }))
        }
        PendingQrTask::TronTransaction {
            request_id,
            transaction,
            transaction_type,
            sequence,
            ..
        } => {
            verify_ur_response(&decoded.ur_type, "tron-signature", decoded.request_id, *request_id, chain)?;
            let data = transaction
                .attach_signature(&decoded.signature_bytes)
                .map_err(|error| sign_error(input_type, error.into()))?;
            Ok(StepResolution::SignedTransaction(GemSignedTransaction {
                data,
                transaction_type: transaction_type.clone(),
                sequence: *sequence,
            }))
        }
        PendingQrTask::HyperCoreTypedData {
            request_id,
            typed_data_json,
            action_json,
            timestamp,
            sender_address,
            transaction_type,
            sequence,
            ..
        } => {
            verify_ur_response(&decoded.ur_type, "eth-signature", decoded.request_id, *request_id, chain)?;
            let data = complete_hypercore_user_step(
                typed_data_json,
                action_json,
                *timestamp,
                &decoded.signature_bytes,
                sender_address,
            )
            .map_err(|error| sign_error(input_type, error.into()))?;
            Ok(StepResolution::SignedTransaction(GemSignedTransaction {
                data,
                transaction_type: transaction_type.clone(),
                sequence: *sequence,
            }))
        }
    }
}

fn verify_ur_response(
    actual_type: &str,
    expected_type: &str,
    response_request_id: Option<[u8; 16]>,
    expected_request_id: [u8; 16],
    chain: Chain,
) -> Result<(), GemConfirmError> {
    if actual_type != expected_type {
        return Err(qr_sign_error(
            chain,
            format!("expected {expected_type} QR response, got {actual_type}"),
        ));
    }
    if let Some(actual_id) = response_request_id
        && actual_id != expected_request_id
    {
        return Err(qr_sign_error(
            chain,
            "QR signature response belongs to a different request".to_string(),
        ));
    }
    Ok(())
}

fn build_qr_slots(
    send_input: &SendInput,
    master_fingerprint: Option<u32>,
) -> Result<Vec<QrSlot>, GemConfirmError> {
    let raw_signer_input = send_input.signer_input()?;
    let original_input_type = &send_input.confirm.input.transfer.input_type;
    let chain = original_input_type.get_asset().chain();
    let account = &send_input.confirm.input.from;
    let derivation_path = effective_derivation_path(chain, &account.derivation_path);

    let (effective_input, forced_single_type) = match &raw_signer_input.input.input_type {
        TransactionInputType::Swap {
            from_asset,
            swap_data,
            ..
        } if swap_data.data.data_type == SwapQuoteDataType::Transfer => (
            swap_transfer_signer_input(&raw_signer_input, from_asset, swap_data),
            Some(TransactionType::Swap),
        ),
        _ => (raw_signer_input, None),
    };

    match chain.chain_type() {
        ChainType::Ethereum if chain != Chain::Tempo => build_evm_slots(
            chain,
            original_input_type,
            &effective_input,
            forced_single_type,
            &account.address,
            &derivation_path,
            master_fingerprint,
        ),
        ChainType::Bitcoin if chain == Chain::Bitcoin => build_bitcoin_slots(
            chain,
            original_input_type,
            &effective_input,
            forced_single_type,
            &derivation_path,
            account.extended_public_key.as_deref(),
            master_fingerprint,
        ),
        ChainType::Solana if chain == Chain::Solana => build_solana_slots(
            chain,
            original_input_type,
            &effective_input,
            forced_single_type,
            &derivation_path,
            master_fingerprint,
        ),
        ChainType::Tron if chain == Chain::Tron => build_tron_slots(
            chain,
            original_input_type,
            &effective_input,
            forced_single_type,
            &derivation_path,
            master_fingerprint,
        ),
        ChainType::HyperCore if chain == Chain::HyperCore => build_hypercore_slots(
            chain,
            original_input_type,
            &effective_input,
            &account.address,
            &derivation_path,
            master_fingerprint,
        ),
        _ => Err(qr_sign_error(
            chain,
            format!("QR cold wallet signing is not supported for {chain}"),
        )),
    }
}

fn build_evm_slots(
    chain: Chain,
    original_input_type: &TransactionInputType,
    signer_input: &SignerInput,
    forced_single_type: Option<TransactionType>,
    sender_address: &str,
    derivation_path: &str,
    master_fingerprint: Option<u32>,
) -> Result<Vec<QrSlot>, GemConfirmError> {
    let address_bytes = decode_evm_address(sender_address, chain)?;

    if let TransactionInputType::Payment { asset, extra, .. } = &signer_input.input.input_type
        && extra.output_type == TransferDataOutputType::Signature
    {
        let mut slots = Vec::new();
        if let Some(approval) = &extra.approval {
            let approve_input = payment_approval_signer_input(signer_input, asset, approval);
            let mut approve_txs = build_unsigned_eip1559_transactions(&approve_input)
                .map_err(|error| sign_error(original_input_type, error.into()))?;
            let approve_tx = approve_txs
                .pop()
                .ok_or_else(|| qr_sign_error(chain, "missing payment approval transaction".to_string()))?;
            let request_id = generate_request_id().map_err(|error| qr_sign_error(chain, error.to_string()))?;
            let ur_frames = encode_eth_sign_request(
                request_id,
                &approve_tx.encoded_unsigned(),
                EthDataType::TypedTransaction,
                Some(approve_tx.chain_id),
                derivation_path,
                master_fingerprint,
                Some(address_bytes),
                QR_SIGN_ORIGIN,
            )
            .map_err(|error| qr_sign_error(chain, error.to_string()))?;
            slots.push(QrSlot::Pending(PendingQrTask::EvmTransaction {
                request_id,
                transaction: approve_tx,
                sender_address: sender_address.to_string(),
                transaction_type: TransactionType::TokenApproval,
                sequence: approve_input.input.metadata.get_sequence().ok(),
                ur_frames,
            }));
        }

        let raw_typed_data = extra.data.clone().unwrap_or_default();
        let typed_data_json = from_utf8(&raw_typed_data)
            .map_err(|_| qr_sign_error(chain, "payment typed data must be valid UTF-8".to_string()))?
            .to_string();
        hash_typed_data(&typed_data_json)
            .map_err(|error| sign_error(original_input_type, error.into()))?;
        let request_id = generate_request_id().map_err(|error| qr_sign_error(chain, error.to_string()))?;
        let chain_id = extract_eip712_chain_id(&typed_data_json)
            .or_else(|| signer_input.input.metadata.get_chain_id().ok().and_then(|id| id.parse::<u64>().ok()));
        let ur_frames = encode_eth_sign_request(
            request_id,
            typed_data_json.as_bytes(),
            EthDataType::TypedData,
            chain_id,
            derivation_path,
            master_fingerprint,
            Some(address_bytes),
            QR_SIGN_ORIGIN,
        )
        .map_err(|error| qr_sign_error(chain, error.to_string()))?;
        slots.push(QrSlot::Pending(PendingQrTask::EvmTypedData {
            request_id,
            typed_data_json,
            sender_address: sender_address.to_string(),
            transaction_type: signer_input.input_type.transaction_type(),
            ur_frames,
        }));
        return Ok(slots);
    }

    let transactions = build_unsigned_eip1559_transactions(signer_input)
        .map_err(|error| sign_error(original_input_type, error.into()))?;
    if transactions.is_empty() {
        return Err(qr_sign_error(chain, "signer returned no transactions".to_string()));
    }
    let tx_types = match forced_single_type {
        Some(single_type) => vec![single_type; transactions.len()],
        None => expected_transaction_types(chain, signer_input, transactions.len())
            .map_err(|error| sign_error(original_input_type, error.into()))?,
    };
    let base_sequence = signer_input.input.metadata.get_sequence().ok();

    transactions
        .into_iter()
        .zip(tx_types)
        .enumerate()
        .map(|(position, (transaction, transaction_type))| {
            let request_id = generate_request_id().map_err(|error| qr_sign_error(chain, error.to_string()))?;
            let ur_frames = encode_eth_sign_request(
                request_id,
                &transaction.encoded_unsigned(),
                EthDataType::TypedTransaction,
                Some(transaction.chain_id),
                derivation_path,
                master_fingerprint,
                Some(address_bytes),
                QR_SIGN_ORIGIN,
            )
            .map_err(|error| qr_sign_error(chain, error.to_string()))?;
            Ok(QrSlot::Pending(PendingQrTask::EvmTransaction {
                request_id,
                transaction,
                sender_address: sender_address.to_string(),
                transaction_type,
                sequence: base_sequence.map(|seq| seq + position as u64),
                ur_frames,
            }))
        })
        .collect()
}

fn build_bitcoin_slots(
    chain: Chain,
    original_input_type: &TransactionInputType,
    signer_input: &SignerInput,
    forced_single_type: Option<TransactionType>,
    derivation_path: &str,
    extended_public_key: Option<&str>,
    master_fingerprint: Option<u32>,
) -> Result<Vec<QrSlot>, GemConfirmError> {
    if !matches!(signer_input.input_type, TransactionInputType::Transfer { .. }) {
        return Err(qr_sign_error(chain, "unsupported Bitcoin transaction type for PSBT signing".to_string()));
    }
    let psbt_bytes = build_transfer_psbt(
        BitcoinChain::Bitcoin,
        signer_input,
        master_fingerprint.unwrap_or(0).to_be_bytes(),
        derivation_path,
        extended_public_key,
    )
    .map_err(|error| sign_error(original_input_type, error.into()))?;
    let ur_frames = encode_crypto_psbt(&psbt_bytes);
    let transaction_type = forced_single_type.unwrap_or_else(|| signer_input.input_type.transaction_type());
    Ok(vec![QrSlot::Pending(PendingQrTask::BitcoinPsbt {
        signer_input: signer_input.clone(),
        transaction_type,
        sequence: signer_input.input.metadata.get_sequence().ok(),
        ur_frames,
    })])
}

fn build_solana_slots(
    chain: Chain,
    original_input_type: &TransactionInputType,
    signer_input: &SignerInput,
    forced_single_type: Option<TransactionType>,
    derivation_path: &str,
    master_fingerprint: Option<u32>,
) -> Result<Vec<QrSlot>, GemConfirmError> {
    let unsigned = build_unsigned_solana_transaction(signer_input)
        .map_err(|error| sign_error(original_input_type, error.into()))?;
    let message_bytes = unsigned
        .message_bytes()
        .map_err(|error| sign_error(original_input_type, error.into()))?;
    let request_id = generate_request_id().map_err(|error| qr_sign_error(chain, error.to_string()))?;
    let ur_frames = encode_sol_sign_request(
        request_id,
        &message_bytes,
        derivation_path,
        master_fingerprint,
        Some(unsigned.sender_bytes()),
        QR_SIGN_ORIGIN,
        SolSignType::Transaction,
    )
    .map_err(|error| qr_sign_error(chain, error.to_string()))?;
    let transaction_type = forced_single_type.unwrap_or_else(|| signer_input.input_type.transaction_type());
    Ok(vec![QrSlot::Pending(PendingQrTask::SolanaTransaction {
        request_id,
        transaction: unsigned,
        transaction_type,
        sequence: signer_input.input.metadata.get_sequence().ok(),
        ur_frames,
    })])
}

fn build_tron_slots(
    chain: Chain,
    original_input_type: &TransactionInputType,
    signer_input: &SignerInput,
    forced_single_type: Option<TransactionType>,
    derivation_path: &str,
    master_fingerprint: Option<u32>,
) -> Result<Vec<QrSlot>, GemConfirmError> {
    let transactions = build_unsigned_tron_transactions(signer_input)
        .map_err(|error| sign_error(original_input_type, error.into()))?;
    if transactions.is_empty() {
        return Err(qr_sign_error(chain, "signer returned no transactions".to_string()));
    }
    let tx_types = match forced_single_type {
        Some(single_type) => vec![single_type; transactions.len()],
        None => expected_transaction_types(chain, signer_input, transactions.len())
            .map_err(|error| sign_error(original_input_type, error.into()))?,
    };
    let base_sequence = signer_input.input.metadata.get_sequence().ok();

    transactions
        .into_iter()
        .zip(tx_types)
        .enumerate()
        .map(|(position, (transaction, transaction_type))| {
            let request_id = generate_request_id().map_err(|error| qr_sign_error(chain, error.to_string()))?;
            let ur_frames = encode_tron_sign_request(
                request_id,
                transaction.raw_data_bytes(),
                TronDataType::Transaction,
                derivation_path,
                master_fingerprint,
                Some(transaction.owner_bytes()),
                QR_SIGN_ORIGIN,
            )
            .map_err(|error| qr_sign_error(chain, error.to_string()))?;
            Ok(QrSlot::Pending(PendingQrTask::TronTransaction {
                request_id,
                transaction,
                transaction_type,
                sequence: base_sequence.map(|seq| seq + position as u64),
                ur_frames,
            }))
        })
        .collect()
}

fn build_hypercore_slots(
    chain: Chain,
    original_input_type: &TransactionInputType,
    signer_input: &SignerInput,
    sender_address: &str,
    derivation_path: &str,
    master_fingerprint: Option<u32>,
) -> Result<Vec<QrSlot>, GemConfirmError> {
    let address_bytes = decode_evm_address(sender_address, chain)?;
    let steps = build_hypercore_sign_steps(signer_input)
        .map_err(|error| sign_error(original_input_type, error.into()))?;
    if steps.is_empty() {
        return Err(qr_sign_error(chain, "signer returned no HyperCore steps".to_string()));
    }
    let tx_types = expected_transaction_types(chain, signer_input, steps.len())
        .map_err(|error| sign_error(original_input_type, error.into()))?;
    let base_sequence = signer_input.input.metadata.get_sequence().ok();

    steps
        .into_iter()
        .zip(tx_types)
        .enumerate()
        .map(|(position, (step, transaction_type))| {
            let sequence = base_sequence.map(|seq| seq + position as u64);
            match step {
                HyperCoreSignStep::Ready(data) => Ok(QrSlot::Ready(GemSignedTransaction {
                    data,
                    transaction_type,
                    sequence,
                })),
                HyperCoreSignStep::RequiresUserTypedData {
                    typed_data_json,
                    action_json,
                    timestamp,
                } => {
                    hash_typed_data(&typed_data_json)
                        .map_err(|error| sign_error(original_input_type, error.into()))?;
                    let request_id = generate_request_id()
                        .map_err(|error| qr_sign_error(chain, error.to_string()))?;
                    let chain_id = extract_eip712_chain_id(&typed_data_json);
                    let ur_frames = encode_eth_sign_request(
                        request_id,
                        typed_data_json.as_bytes(),
                        EthDataType::TypedData,
                        chain_id,
                        derivation_path,
                        master_fingerprint,
                        Some(address_bytes),
                        QR_SIGN_ORIGIN,
                    )
                    .map_err(|error| qr_sign_error(chain, error.to_string()))?;
                    Ok(QrSlot::Pending(PendingQrTask::HyperCoreTypedData {
                        request_id,
                        typed_data_json,
                        action_json,
                        timestamp,
                        sender_address: sender_address.to_string(),
                        transaction_type,
                        sequence,
                        ur_frames,
                    }))
                }
            }
        })
        .collect()
}

fn effective_derivation_path(chain: Chain, account_path: &str) -> String {
    let trimmed = account_path.trim();
    if trimmed.is_empty() {
        default_derivation_path(chain).to_string()
    } else {
        trimmed.to_string()
    }
}

fn decode_evm_address(address: &str, chain: Chain) -> Result<[u8; 20], GemConfirmError> {
    let bytes = decode_hex(address)
        .map_err(|error| qr_sign_error(chain, format!("invalid EVM sender address: {error}")))?;
    bytes
        .try_into()
        .map_err(|_| qr_sign_error(chain, "EVM sender address must be 20 bytes".to_string()))
}

fn qr_sign_error(chain: Chain, msg: String) -> GemConfirmError {
    GemConfirmError::Sign {
        error: SignerError::SigningError(msg.clone()),
        chain,
        msg,
    }
}

pub(super) fn input_requires_qr_sign(input: &SendInput) -> bool {
    let chain = input.confirm.input.transfer.input_type.get_asset().chain();
    if chain != Chain::HyperCore {
        return true;
    }
    let Ok(signer_input) = input.signer_input() else {
        return true;
    };
    let Ok(steps) = build_hypercore_sign_steps(&signer_input) else {
        return true;
    };
    steps
        .iter()
        .any(|step| matches!(step, HyperCoreSignStep::RequiresUserTypedData { .. }))
}

#[cfg(test)]
mod tests {
    use std::str::from_utf8;
    use std::sync::Arc;
    use std::sync::atomic::Ordering;

    use futures::executor::block_on;
    use gem_bitcoin::testkit::signer_mock::{
        TEST_PRIVATE_KEY as BTC_TEST_PRIVATE_KEY, mock_p2wpkh_transfer_input,
        sign_p2wpkh_psbt_for_test,
    };
    use gem_derivation::derive_account_from_private_key;
    use gem_evm::eip712::hash_typed_data;
    use gem_hash::keccak::keccak256;
    use gem_hash::sha2::sha256;
    use num_bigint::BigInt;
    use primitives::swap::Permit2ApprovalData;
    use primitives::transaction_load_metadata::AgentPrivateKey;
    use primitives::{
        Account, Asset, AssetId, Chain, GasPriceType, HyperliquidOrder, PerpetualConfirmData,
        PerpetualDirection, PerpetualType, TransactionFee, TransactionInputType,
        TransactionLoadMetadata, Wallet, WalletId, hex,
    };
    use signer::{Ed25519KeyPair, SignatureScheme, Signer, secp256k1_public_key};
    use serde_json::{Value, from_str};
    use swapper::testkit::MockSwapper;
    use swapper::{FetchQuoteData, Quote, SwapperProvider};

    use super::*;
    use crate::services::confirm::testkit::{
        ConfirmTestkit, TEST_TRON_RECIPIENT, broadcast_failed, mock_swap_transfer,
    };
    use crate::services::swap::GemSwapService;
    use crate::services::swap::rules as swap_rules;
    use crate::services::transfer::{GemRecipient, GemTransferData};
    use crate::services::wallet::GemKeystoreAuthentication;
    use crate::ur::cbor::{CborValue, decode_cbor, encode_cbor};
    use crate::ur::{UrDecoder, UrEncoder, UrReceiveOutcome};

    const COLD_KEY: [u8; 32] = [0x22; 32];
    const WRONG_KEY: [u8; 32] = [0x33; 32];
    const AGENT_KEY_HEX: &str = "4444444444444444444444444444444444444444444444444444444444444444";

    fn decode_ur_frames(frames: &[String]) -> (String, CborValue) {
        let mut decoder = UrDecoder::new();
        for frame in frames {
            if let UrReceiveOutcome::Complete { ur_type, cbor } = decoder.receive(frame).unwrap() {
                return (ur_type, decode_cbor(&cbor).unwrap());
            }
        }
        panic!("UR frames did not complete");
    }

    fn build_signature_ur(ur_type: &str, request_id: &[u8], signature: &[u8]) -> String {
        let cbor = encode_cbor(&CborValue::Map(vec![
            (
                CborValue::Unsigned(1),
                CborValue::Tag(37, Box::new(CborValue::Bytes(request_id.to_vec()))),
            ),
            (CborValue::Unsigned(2), CborValue::Bytes(signature.to_vec())),
        ]));
        UrEncoder::encode(ur_type, &cbor, cbor.len()).remove(0)
    }

    fn sign_eth_request_frames(frames: &[String], private_key: &[u8], override_id: Option<[u8; 16]>) -> String {
        let (ur_type, root) = decode_ur_frames(frames);
        assert_eq!(ur_type, "eth-sign-request");
        let req_id = override_id
            .map(Vec::from)
            .unwrap_or_else(|| root.map_get_uint(1).unwrap().as_bytes().unwrap().to_vec());
        let sign_data = root.map_get_uint(2).unwrap().as_bytes().unwrap();
        let data_type = root.map_get_uint(3).unwrap().as_uint().unwrap();
        let digest = match data_type {
            2 => hash_typed_data(from_utf8(sign_data).unwrap()).unwrap(),
            4 => keccak256(sign_data),
            other => panic!("unexpected eth data_type: {other}"),
        };
        let sig = Signer::sign_ethereum_digest(&digest, private_key).unwrap();
        build_signature_ur("eth-signature", &req_id, &sig)
    }

    fn qr_wallet_for(chain: Chain, private_key: &[u8]) -> Wallet {
        let account = derive_account_from_private_key(private_key, chain).unwrap();
        Wallet {
            id: WalletId::Multicoin(format!("qr_{chain}")),
            ..Wallet::mock_with_accounts(vec![account])
        }
    }

    #[test]
    fn test_evm_qr_transfer_roundtrip_and_rejects_wrong_request_or_signer() {
        block_on(async {
            let wallet = qr_wallet_for(Chain::Ethereum, &COLD_KEY);
            let testkit = ConfirmTestkit::new(wallet.clone(), wallet.clone());
            testkit
                .wallet_preferences
                .set_qr_signer_xfp(wallet.id.clone(), Some("73c5da0a".to_string()))
                .unwrap();

            let recipient = wallet.accounts[0].address.clone();
            let transfer = GemTransferData {
                recipient: GemRecipient::address(recipient),
                value: BigInt::from(1_000_000_000_000_000u64),
                ..GemTransferData::mock(TransactionInputType::Transfer {
                    asset: Asset::mock_eth(),
                })
            };
            let confirmation = testkit.service.clone().confirmation(wallet.clone(), transfer.clone(), None);
            let view = confirmation.view_state(confirmation.screen());
            assert_eq!(view.button.icon, GemKeystoreAuthentication::None);
            assert_eq!(testkit.passwords.authentication_reads.load(Ordering::SeqCst), 0);
            assert!(confirmation.requires_qr_sign());

            let input = SendInput::mock_signed_by(wallet, transfer, None);
            let session = GemQrSignSession::prepare(testkit.service.clone(), input).unwrap();
            let step = session.current_step().unwrap();
            assert_eq!(step.step_index, 1);
            assert_eq!(step.total_steps, 1);
            assert_eq!(step.ur_type, "eth-sign-request");

            let wrong_req = sign_eth_request_frames(&step.ur_frames, &COLD_KEY, Some([0x99; 16]));
            assert!(Box::pin(session.receive_frame(wrong_req)).await.is_err());

            let wrong_signer = sign_eth_request_frames(&step.ur_frames, &WRONG_KEY, None);
            assert!(Box::pin(session.receive_frame(wrong_signer)).await.is_err());

            let valid_sig = sign_eth_request_frames(&step.ur_frames, &COLD_KEY, None);
            let outcome = Box::pin(session.receive_frame(valid_sig)).await.unwrap();
            assert_eq!(outcome, GemQrSignOutcome::ReadyToSubmit);
            assert_eq!(session.current_step(), None);

            let err = Box::pin(session.submit()).await.unwrap_err();
            assert!(broadcast_failed(&err), "broadcast should be reached: {err:?}");
            assert!(testkit.passwords.create_requests.lock().unwrap().is_empty());
        });
    }

    #[test]
    fn test_evm_permit2_two_step_qr_swap_roundtrip() {
        block_on(async {
            let swapper = MockSwapper::new(SwapperProvider::UniswapV3, |request| {
                Ok(Quote::mock_with_request(request))
            });
            let builds = swapper.builds();
            let wallet = qr_wallet_for(Chain::Ethereum, &COLD_KEY);
            let testkit = ConfirmTestkit::with_swap(
                wallet.clone(),
                Arc::new(GemSwapService::mock_with_swappers(vec![Box::new(swapper)])),
            );
            testkit
                .wallet_preferences
                .set_qr_signer_xfp(wallet.id.clone(), Some("unknown".to_string()))
                .unwrap();

            let transfer = mock_swap_transfer(&wallet.accounts[0].address, Some(Permit2ApprovalData::mock()));
            let quote = Quote::mock_with_request(
                &swap_rules::requote_request(&wallet, &transfer, &transfer.value)
                    .unwrap()
                    .1,
            );
            let input = SendInput::mock_signed_by(wallet, transfer, Some(quote));
            let session = GemQrSignSession::prepare(testkit.service.clone(), input).unwrap();

            let step1 = session.current_step().unwrap();
            assert_eq!(step1.step_index, 1);
            assert_eq!(step1.total_steps, 2);
            let (_, step1_cbor) = decode_ur_frames(&step1.ur_frames);
            assert_eq!(
                step1_cbor.map_get_uint(5).unwrap().unwrap_tags().map_get_uint(2),
                None,
                "unknown xfp must omit keypath source_fingerprint"
            );

            let sig1 = sign_eth_request_frames(&step1.ur_frames, &COLD_KEY, None);
            let GemQrSignOutcome::NextStep { step: step2 } =
                Box::pin(session.receive_frame(sig1)).await.unwrap()
            else {
                panic!("expected NextStep after Permit2 signature");
            };
            assert_eq!(step2.step_index, 2);
            assert_eq!(step2.total_steps, 2);
            assert!(matches!(builds.lock().unwrap().as_slice(), [FetchQuoteData::Permit2(_)]));

            let sig2 = sign_eth_request_frames(&step2.ur_frames, &COLD_KEY, None);
            assert_eq!(
                Box::pin(session.receive_frame(sig2)).await.unwrap(),
                GemQrSignOutcome::ReadyToSubmit
            );
            let err = Box::pin(session.submit()).await.unwrap_err();
            assert!(broadcast_failed(&err));
        });
    }

    #[test]
    fn test_bitcoin_psbt_ur_and_base64_qr_roundtrip() {
        block_on(async {
            let p2wpkh_input = mock_p2wpkh_transfer_input();
            let pubkey_hex = hex::encode(secp256k1_public_key(&BTC_TEST_PRIVATE_KEY).unwrap());
            let mut btc_account = Account::mock(Chain::Bitcoin, &p2wpkh_input.input.sender_address);
            btc_account.derivation_path = "m/84'/0'/0'/0/0".to_string();
            btc_account.extended_public_key = Some(pubkey_hex);
            let wallet = Wallet {
                id: WalletId::Multicoin("qr_btc".to_string()),
                ..Wallet::mock_with_accounts(vec![btc_account.clone()])
            };
            let testkit = ConfirmTestkit::new(wallet.clone(), wallet.clone());
            testkit
                .wallet_preferences
                .set_qr_signer_xfp(wallet.id.clone(), Some("73c5da0a".to_string()))
                .unwrap();

            let transfer = GemTransferData {
                use_max_amount: false,
                recipient: GemRecipient::address(p2wpkh_input.input.destination_address.clone()),
                value: BigInt::from(10_000u64),
                ..GemTransferData::mock(TransactionInputType::Transfer {
                    asset: Asset::mock_btc(),
                })
            };
            let send_input = SendInput {
                wallet,
                confirm: GemConfirmData {
                    input: GemConfirmInput {
                        from: btc_account,
                        transfer: transfer.clone(),
                    },
                    fee: TransactionFee {
                        gas_price_type: GasPriceType::regular(10),
                        fee_asset: AssetId::from_chain(Chain::Bitcoin),
                        ..TransactionFee::mock_with_fee(10)
                    },
                    metadata: p2wpkh_input.input.metadata.clone(),
                    ..GemConfirmData::mock(Chain::Bitcoin, transfer.input_type)
                },
                value: BigInt::from(10_000u64),
                network_fee: BigInt::from(10u64),
                simulation: None,
                swap_quote: None,
            };

            let session_ur = GemQrSignSession::prepare(testkit.service.clone(), send_input.clone()).unwrap();
            let step = session_ur.current_step().unwrap();
            assert_eq!(step.ur_type, "crypto-psbt");
            let (_, psbt_cbor) = decode_ur_frames(&step.ur_frames);
            let unsigned_psbt = psbt_cbor.as_bytes().unwrap();
            let (signed_psbt_bytes, signed_psbt_b64) =
                sign_p2wpkh_psbt_for_test(unsigned_psbt, &BTC_TEST_PRIVATE_KEY);

            let ur_response_frames = encode_crypto_psbt(&signed_psbt_bytes);
            let mut last_outcome = None;
            for frame in ur_response_frames {
                last_outcome = Some(Box::pin(session_ur.receive_frame(frame)).await.unwrap());
            }
            assert_eq!(last_outcome, Some(GemQrSignOutcome::ReadyToSubmit));
            let err = Box::pin(session_ur.submit()).await.unwrap_err();
            assert!(broadcast_failed(&err));

            let session_b64 = GemQrSignSession::prepare(testkit.service.clone(), send_input).unwrap();
            assert_eq!(
                Box::pin(session_b64.receive_frame(signed_psbt_b64)).await.unwrap(),
                GemQrSignOutcome::ReadyToSubmit
            );
        });
    }

    #[test]
    fn test_solana_and_tron_qr_roundtrip() {
        block_on(async {
            let sol_wallet = qr_wallet_for(Chain::Solana, &COLD_KEY);
            let sol_testkit = ConfirmTestkit::new(sol_wallet.clone(), sol_wallet.clone());
            sol_testkit
                .wallet_preferences
                .set_qr_signer_xfp(sol_wallet.id.clone(), Some("73c5da0a".to_string()))
                .unwrap();
            let sol_recipient = sol_wallet.accounts[0].address.clone();
            let sol_transfer = GemTransferData {
                use_max_amount: false,
                recipient: GemRecipient::address(sol_recipient),
                value: BigInt::from(100_000u64),
                ..GemTransferData::mock(TransactionInputType::Transfer {
                    asset: Asset::mock_sol(),
                })
            };
            let sol_input = SendInput {
                wallet: sol_wallet.clone(),
                confirm: GemConfirmData {
                    input: GemConfirmInput {
                        from: sol_wallet.accounts[0].clone(),
                        transfer: sol_transfer.clone(),
                    },
                    metadata: TransactionLoadMetadata::mock_solana("11111111111111111111111111111111"),
                    ..GemConfirmData::mock(Chain::Solana, sol_transfer.input_type)
                },
                value: BigInt::from(100_000u64),
                network_fee: BigInt::from(5_000u64),
                simulation: None,
                swap_quote: None,
            };
            let sol_session = GemQrSignSession::prepare(sol_testkit.service.clone(), sol_input).unwrap();
            let sol_step = sol_session.current_step().unwrap();
            let (sol_ur_type, sol_cbor) = decode_ur_frames(&sol_step.ur_frames);
            assert_eq!(sol_ur_type, "sol-sign-request");
            let sol_req_id = sol_cbor.map_get_uint(1).unwrap().as_bytes().unwrap();
            let sol_msg = sol_cbor.map_get_uint(2).unwrap().as_bytes().unwrap();
            let sol_sig = Ed25519KeyPair::from_private_key(&COLD_KEY).unwrap().sign(sol_msg);
            let sol_resp = build_signature_ur("sol-signature", sol_req_id, &sol_sig);
            assert_eq!(
                Box::pin(sol_session.receive_frame(sol_resp)).await.unwrap(),
                GemQrSignOutcome::ReadyToSubmit
            );
            assert!(broadcast_failed(&Box::pin(sol_session.submit()).await.unwrap_err()));

            let tron_wallet = qr_wallet_for(Chain::Tron, &COLD_KEY);
            let tron_testkit = ConfirmTestkit::new(tron_wallet.clone(), tron_wallet.clone());
            tron_testkit
                .wallet_preferences
                .set_qr_signer_xfp(tron_wallet.id.clone(), Some("73c5da0a".to_string()))
                .unwrap();
            let tron_transfer = GemTransferData {
                use_max_amount: false,
                recipient: GemRecipient::address(TEST_TRON_RECIPIENT.to_string()),
                value: BigInt::from(1_000_000u64),
                ..GemTransferData::mock(TransactionInputType::Transfer {
                    asset: Asset::from_chain(Chain::Tron),
                })
            };
            let tron_input = SendInput {
                wallet: tron_wallet.clone(),
                confirm: GemConfirmData {
                    input: GemConfirmInput {
                        from: tron_wallet.accounts[0].clone(),
                        transfer: tron_transfer.clone(),
                    },
                    metadata: TransactionLoadMetadata::mock_tron(),
                    ..GemConfirmData::mock(Chain::Tron, tron_transfer.input_type)
                },
                value: BigInt::from(1_000_000u64),
                network_fee: BigInt::from(0u64),
                simulation: None,
                swap_quote: None,
            };
            let tron_session = GemQrSignSession::prepare(tron_testkit.service.clone(), tron_input).unwrap();
            let tron_step = tron_session.current_step().unwrap();
            let (tron_ur_type, tron_cbor) = decode_ur_frames(&tron_step.ur_frames);
            assert_eq!(tron_ur_type, "tron-sign-request");
            let tron_req_id = tron_cbor.map_get_uint(1).unwrap().as_bytes().unwrap();
            let tron_raw = tron_cbor.map_get_uint(2).unwrap().as_bytes().unwrap();
            let tron_digest = sha256(tron_raw);

            let bad_tron_sig = Signer::sign_digest(SignatureScheme::Secp256k1, &tron_digest, &WRONG_KEY).unwrap();
            let bad_tron_resp = build_signature_ur("tron-signature", tron_req_id, &bad_tron_sig);
            assert!(Box::pin(tron_session.receive_frame(bad_tron_resp)).await.is_err());

            let good_tron_sig = Signer::sign_digest(SignatureScheme::Secp256k1, &tron_digest, &COLD_KEY).unwrap();
            let good_tron_resp = build_signature_ur("tron-signature", tron_req_id, &good_tron_sig);
            assert_eq!(
                Box::pin(tron_session.receive_frame(good_tron_resp)).await.unwrap(),
                GemQrSignOutcome::ReadyToSubmit
            );
            assert!(broadcast_failed(&Box::pin(tron_session.submit()).await.unwrap_err()));
        });
    }

    #[test]
    fn test_hypercore_ready_only_skips_qr_and_approve_agent_preserves_order() {
        block_on(async {
            let wallet = qr_wallet_for(Chain::HyperCore, &COLD_KEY);
            let agent_addr = derive_account_from_private_key(&hex::decode(AGENT_KEY_HEX).unwrap(), Chain::Ethereum)
                .unwrap()
                .address;
            let testkit = ConfirmTestkit::new(wallet.clone(), wallet.clone());
            testkit
                .wallet_preferences
                .set_qr_signer_xfp(wallet.id.clone(), Some("73c5da0a".to_string()))
                .unwrap();

            let perp_type = TransactionInputType::Perpetual {
                asset: Asset::from_chain(Chain::HyperCore),
                perpetual_type: PerpetualType::Open {
                    data: PerpetualConfirmData::mock(PerpetualDirection::Long, 0, None, None),
                },
            };
            let build_input = |approve_agent_required: bool| {
                let transfer = GemTransferData {
                    use_max_amount: false,
                    value: BigInt::from(100_000_000u64),
                    ..GemTransferData::mock(perp_type.clone())
                };
                SendInput {
                    wallet: wallet.clone(),
                    confirm: GemConfirmData {
                        input: GemConfirmInput {
                            from: wallet.accounts[0].clone(),
                            transfer: transfer.clone(),
                        },
                        metadata: TransactionLoadMetadata::Hyperliquid {
                            order: Some(HyperliquidOrder {
                                approve_agent_required,
                                agent_address: agent_addr.clone(),
                                agent_private_key: Arc::new(AgentPrivateKey::new(AGENT_KEY_HEX.to_string())),
                                ..HyperliquidOrder::mock()
                            }),
                        },
                        ..GemConfirmData::mock(Chain::HyperCore, transfer.input_type)
                    },
                    value: BigInt::from(100_000_000u64),
                    network_fee: BigInt::from(0u64),
                    simulation: None,
                    swap_quote: None,
                }
            };

            let ready_only = build_input(false);
            assert!(!input_requires_qr_sign(&ready_only));
            let err = Box::pin(testkit.service.submit(ready_only)).await.unwrap_err();
            assert!(broadcast_failed(&err));
            assert!(testkit.passwords.create_requests.lock().unwrap().is_empty());

            let needs_approval = build_input(true);
            assert!(input_requires_qr_sign(&needs_approval));
            let session = GemQrSignSession::prepare(testkit.service.clone(), needs_approval).unwrap();
            let step = session.current_step().unwrap();
            assert_eq!(step.step_index, 1);
            assert_eq!(step.total_steps, 1);
            let sig = sign_eth_request_frames(&step.ur_frames, &COLD_KEY, None);
            assert_eq!(
                Box::pin(session.receive_frame(sig)).await.unwrap(),
                GemQrSignOutcome::ReadyToSubmit
            );
            let ready_txs = session.state.lock().unwrap().all_ready().unwrap();
            let action_types: Vec<String> = ready_txs
                .iter()
                .map(|tx| {
                    let parsed: Value = from_str(&tx.data).unwrap();
                    parsed["action"]["type"].as_str().unwrap().to_string()
                })
                .collect();
            assert_eq!(action_types, vec!["approveAgent", "updateLeverage", "order"]);
            assert_eq!(
                ready_txs
                    .iter()
                    .map(|tx| tx.transaction_type.clone())
                    .collect::<Vec<_>>(),
                vec![TransactionType::PerpetualOpenPosition; 3]
            );
        });
    }
}
