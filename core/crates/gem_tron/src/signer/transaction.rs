use gem_hash::sha2::sha256;
use primitives::hex::{self, encode_with_0x};
use primitives::{
    SignerError, SignerInput, StakeType, TransactionInputType, TransactionLoadMetadata, TransferDataOutputType, TronStakeData, decode_hex,
    swap::{SwapQuoteData, SwapQuoteDataType},
};
use signer::{SignatureScheme, Signer};

use super::builder::{SignedTransactionJson, TronRawData};
use super::message::tron_hash_message;
use super::wallet_connect::WalletConnectPayload;
use crate::address::TronAddress;
use crate::transaction::{TronContract, TronTriggerSmartContract, contract::TronResource};

const TRON_SIGNATURE_LEN: usize = 65;
const ETHEREUM_RECOVERY_OFFSET: u8 = 27;

enum UnsignedTronKind {
    Built(TronRawData),
    WalletConnect { payload: WalletConnectPayload, transaction_hash: [u8; 32] },
}

pub struct UnsignedTronTransaction {
    owner: TronAddress,
    raw_data_bytes: Vec<u8>,
    kind: UnsignedTronKind,
}

impl UnsignedTronTransaction {
    pub fn owner_bytes(&self) -> [u8; 21] {
        self.owner.bytes()
    }

    pub fn raw_data_bytes(&self) -> &[u8] {
        &self.raw_data_bytes
    }

    pub fn attach_signature(&self, signature_bytes: &[u8]) -> Result<String, SignerError> {
        let transaction_id = match &self.kind {
            UnsignedTronKind::Built(_) => sha256(&self.raw_data_bytes),
            UnsignedTronKind::WalletConnect { transaction_hash, .. } => *transaction_hash,
        };
        let normalized = verify_and_normalize_tron_signature(&transaction_id, &self.owner, signature_bytes)?;
        let signature_hex = hex::encode(normalized);

        match &self.kind {
            UnsignedTronKind::Built(raw_data) => serde_json::to_string(&SignedTransactionJson::new(raw_data.json(), &self.raw_data_bytes, &transaction_id, signature_hex)).map_err(Into::into),
            UnsignedTronKind::WalletConnect { payload, transaction_hash } => payload.encode_output(*transaction_hash, signature_hex),
        }
    }
}

pub fn build_unsigned_tron_transactions(input: &SignerInput) -> Result<Vec<UnsignedTronTransaction>, SignerError> {
    let owner = TronAddress::parse(&input.sender_address)?;
    match &input.input_type {
        TransactionInputType::Transfer { asset } => {
            let payload = if asset.id.is_native() {
                TronContract::Transfer {
                    owner,
                    to: TronAddress::parse(&input.destination_address)?,
                    amount: input.value_as_u64()?,
                }
                .into()
            } else {
                let call = TronTriggerSmartContract::token_transfer(owner, asset.id.get_token_id()?, &input.destination_address, &input.value)?;
                TronContract::TriggerSmart(call).into()
            };
            Ok(vec![build_unsigned_from_contract(input, owner, payload)?])
        }
        TransactionInputType::TokenApprove { approval_data, .. } => {
            let payload = TronContract::TriggerSmart(TronTriggerSmartContract::token_approval(owner, approval_data)?).into();
            Ok(vec![build_unsigned_from_contract(input, owner, payload)?])
        }
        TransactionInputType::Swap { swap_data, .. } => {
            let payloads = build_swap_payloads(input, owner, &swap_data.data)?;
            payloads.into_iter().map(|payload| build_unsigned_from_contract(input, owner, payload)).collect()
        }
        TransactionInputType::Stake { .. } => {
            let payloads = build_stake_payloads(input, owner)?;
            payloads.into_iter().map(|payload| build_unsigned_from_contract(input, owner, payload)).collect()
        }
        TransactionInputType::Generic { .. } => Ok(vec![build_unsigned_wallet_connect(input, owner)?]),
        TransactionInputType::Payment { extra, .. } => match extra.output_type {
            TransferDataOutputType::EncodedTransaction => Ok(vec![build_unsigned_wallet_connect(input, owner)?]),
            TransferDataOutputType::Signature => SignerError::invalid_input_err("Payment signature output is not supported"),
        },
        TransactionInputType::Deposit { .. }
        | TransactionInputType::Withdrawal { .. }
        | TransactionInputType::TransferNft { .. }
        | TransactionInputType::Account { .. }
        | TransactionInputType::Perpetual { .. }
        | TransactionInputType::Earn { .. } => SignerError::invalid_input_err("Unsupported Tron transaction input type"),
    }
}

pub fn format_tron_message_signature(message: &[u8], expected_sender: &str, signature_bytes: &[u8]) -> Result<String, SignerError> {
    let sender = TronAddress::parse(expected_sender)?;
    let digest = tron_hash_message(message);
    let mut signature = verify_and_normalize_tron_signature(&digest, &sender, signature_bytes)?;
    signature[TRON_SIGNATURE_LEN - 1] += ETHEREUM_RECOVERY_OFFSET;
    Ok(encode_with_0x(&signature))
}

fn build_unsigned_from_contract(input: &SignerInput, owner: TronAddress, payload: ContractPayload) -> Result<UnsignedTronTransaction, SignerError> {
    let fee_limit = contract_fee_limit(input, &payload.contract)?;
    let raw_data = TronRawData::from_input_with_data(input, payload.contract, fee_limit, payload.data)?;
    let raw_data_bytes = raw_data.encode();
    Ok(UnsignedTronTransaction {
        owner,
        raw_data_bytes,
        kind: UnsignedTronKind::Built(raw_data),
    })
}

fn build_unsigned_wallet_connect(input: &SignerInput, owner: TronAddress) -> Result<UnsignedTronTransaction, SignerError> {
    let payload = WalletConnectPayload::parse(input)?;
    let (transaction_hash, raw_data_bytes) = payload.validated_raw_data()?;
    Ok(UnsignedTronTransaction {
        owner,
        raw_data_bytes,
        kind: UnsignedTronKind::WalletConnect { payload, transaction_hash },
    })
}

fn verify_and_normalize_tron_signature(digest: &[u8; 32], expected_owner: &TronAddress, signature_bytes: &[u8]) -> Result<[u8; TRON_SIGNATURE_LEN], SignerError> {
    if signature_bytes.len() != TRON_SIGNATURE_LEN {
        return SignerError::invalid_input_err("Invalid Tron signature length");
    }
    let recovery = match signature_bytes[TRON_SIGNATURE_LEN - 1] {
        0 | 1 => signature_bytes[TRON_SIGNATURE_LEN - 1],
        27 | 28 => signature_bytes[TRON_SIGNATURE_LEN - 1] - ETHEREUM_RECOVERY_OFFSET,
        _ => return SignerError::invalid_input_err("Invalid Tron signature recovery id"),
    };
    let mut normalized = [0u8; TRON_SIGNATURE_LEN];
    normalized[..TRON_SIGNATURE_LEN - 1].copy_from_slice(&signature_bytes[..TRON_SIGNATURE_LEN - 1]);
    normalized[TRON_SIGNATURE_LEN - 1] = recovery;

    let recovered = Signer::recover_ethereum_address(digest, &normalized)?;
    let recovered_bytes = decode_hex(&recovered)?;
    if recovered_bytes != expected_owner.account_id() {
        return SignerError::invalid_input_err("Tron signature does not match sender address");
    }
    Ok(normalized)
}

struct ContractPayload {
    contract: TronContract,
    data: Option<Vec<u8>>,
}

impl From<TronContract> for ContractPayload {
    fn from(contract: TronContract) -> Self {
        Self { contract, data: None }
    }
}

pub(crate) fn sign_transfer(input: &SignerInput, private_key: &[u8]) -> Result<String, SignerError> {
    sign_contract_payload(input, private_key, |owner| {
        Ok(TronContract::Transfer {
            owner,
            to: TronAddress::parse(&input.destination_address)?,
            amount: input.value_as_u64()?,
        }
        .into())
    })
}

pub(crate) fn sign_token_transfer(input: &SignerInput, private_key: &[u8]) -> Result<String, SignerError> {
    sign_contract_payload(input, private_key, |owner| {
        let call = TronTriggerSmartContract::token_transfer(owner, input.input_type.get_asset().id.get_token_id()?, &input.destination_address, &input.value)?;
        Ok(TronContract::TriggerSmart(call).into())
    })
}

pub(crate) fn sign_token_approval(input: &SignerInput, private_key: &[u8]) -> Result<String, SignerError> {
    sign_contract_payload(input, private_key, |owner| {
        Ok(TronContract::TriggerSmart(TronTriggerSmartContract::token_approval(owner, input.input_type.get_approval_data()?)?).into())
    })
}

pub(crate) fn sign_swap(input: &SignerInput, private_key: &[u8]) -> Result<Vec<String>, SignerError> {
    let swap_data = &input.input_type.get_swap_data()?.data;
    sign_contract_payloads(input, private_key, |owner| build_swap_payloads(input, owner, swap_data))
}

fn build_swap_payloads(input: &SignerInput, owner: TronAddress, swap_data: &SwapQuoteData) -> Result<Vec<ContractPayload>, SignerError> {
    match swap_data.data_type {
        SwapQuoteDataType::Transfer => SignerError::invalid_input_err("unsupported Tron transfer swap"),
        SwapQuoteDataType::Contract => {
            let swap_payload = ContractPayload {
                contract: TronContract::contract_swap(owner, input.input_type.get_asset(), swap_data)?,
                data: swap_data.memo.as_deref().map(decode_hex).transpose()?,
            };
            match &swap_data.approval {
                Some(approval) => Ok(vec![TronContract::TriggerSmart(TronTriggerSmartContract::token_approval(owner, approval)?).into(), swap_payload]),
                None => Ok(vec![swap_payload]),
            }
        }
    }
}

pub(crate) fn sign_stake(input: &SignerInput, private_key: &[u8]) -> Result<Vec<String>, SignerError> {
    sign_contract_payloads(input, private_key, |owner| build_stake_payloads(input, owner))
}

fn build_stake_payloads(input: &SignerInput, owner: TronAddress) -> Result<Vec<ContractPayload>, SignerError> {
    let stake_type = input.input_type.get_stake_type().map_err(SignerError::invalid_input)?;
    let TransactionLoadMetadata::Tron { stake_data, .. } = &input.metadata else {
        return SignerError::invalid_input_err("Missing tron metadata");
    };

    let contracts = match stake_type {
        StakeType::Stake(_) | StakeType::Redelegate(_) => match stake_data {
            TronStakeData::Votes { votes } => vec![TronContract::vote_witness(owner, votes)?],
            TronStakeData::Unfreeze { unfreezes: _ } => return SignerError::invalid_input_err("Expected Tron vote stake data"),
        },
        StakeType::Unstake(_) => match stake_data {
            TronStakeData::Votes { votes } => vec![TronContract::vote_witness(owner, votes)?],
            TronStakeData::Unfreeze { unfreezes } => unfreezes
                .iter()
                .map(|unfreeze| TronContract::UnfreezeBalanceV2 {
                    owner,
                    unfreeze_balance: unfreeze.amount,
                    resource: TronResource::from(&unfreeze.resource),
                })
                .collect(),
        },
        StakeType::Rewards(_) => vec![TronContract::WithdrawBalance { owner }],
        StakeType::Withdraw(_) => vec![TronContract::WithdrawExpireUnfreeze { owner }],
        StakeType::Freeze(resource) => vec![TronContract::FreezeBalanceV2 {
            owner,
            frozen_balance: input.value_as_u64()?,
            resource: TronResource::from(resource),
        }],
        StakeType::Unfreeze(resource) => vec![TronContract::UnfreezeBalanceV2 {
            owner,
            unfreeze_balance: input.value_as_u64()?,
            resource: TronResource::from(resource),
        }],
    };

    Ok(contracts.into_iter().map(ContractPayload::from).collect())
}

pub(crate) fn sign_data(input: &SignerInput, private_key: &[u8]) -> Result<String, SignerError> {
    validate_sender(input, private_key)?;
    let payload = WalletConnectPayload::parse(input)?;
    let transaction_hash = payload.transaction_hash()?;
    let signature = sign_raw_hash(&transaction_hash, private_key)?;
    payload.into_output(transaction_hash, signature)
}

fn validate_sender(input: &SignerInput, private_key: &[u8]) -> Result<TronAddress, SignerError> {
    let sender = TronAddress::parse(&input.sender_address)?;
    if sender != TronAddress::from_private_key(private_key)? {
        return SignerError::invalid_input_err("Tron sender address does not match private key");
    }
    Ok(sender)
}

fn sign_contract_payload<F>(input: &SignerInput, private_key: &[u8], build_payload: F) -> Result<String, SignerError>
where
    F: FnOnce(TronAddress) -> Result<ContractPayload, SignerError>,
{
    let owner = validate_sender(input, private_key)?;
    let payload = build_payload(owner)?;
    sign_built_contract_payload(input, payload, private_key)
}

fn sign_contract_payloads<F>(input: &SignerInput, private_key: &[u8], build_payloads: F) -> Result<Vec<String>, SignerError>
where
    F: FnOnce(TronAddress) -> Result<Vec<ContractPayload>, SignerError>,
{
    let owner = validate_sender(input, private_key)?;
    build_payloads(owner)?.into_iter().map(|payload| sign_built_contract_payload(input, payload, private_key)).collect()
}

fn contract_fee_limit(input: &SignerInput, contract: &TronContract) -> Result<u64, SignerError> {
    match contract {
        TronContract::TriggerSmart(_) => input.fee.gas_limit(),
        _ => input.fee.fee(),
    }
}

fn sign_built_contract_payload(input: &SignerInput, payload: ContractPayload, private_key: &[u8]) -> Result<String, SignerError> {
    let fee_limit = contract_fee_limit(input, &payload.contract)?;
    let raw_data = TronRawData::from_input_with_data(input, payload.contract, fee_limit, payload.data)?;
    sign_raw_data(raw_data, private_key)
}

fn sign_raw_data(raw_data: TronRawData, private_key: &[u8]) -> Result<String, SignerError> {
    let raw_data_bytes = raw_data.encode();
    let transaction_id = sha256(&raw_data_bytes);
    let signature = sign_raw_hash(&transaction_id, private_key)?;

    serde_json::to_string(&SignedTransactionJson::new(raw_data.json(), &raw_data_bytes, &transaction_id, signature)).map_err(Into::into)
}

fn sign_raw_hash(hash: &[u8], private_key: &[u8]) -> Result<String, SignerError> {
    Ok(hex::encode(Signer::sign_digest(SignatureScheme::Secp256k1, hash, private_key)?))
}
