use primitives::hex;
use primitives::{SignerError, SignerInput, TransferDataOutputType};

use crate::transaction::wallet_connect::{WalletConnectRequest, WalletConnectTransaction};

pub(crate) struct WalletConnectPayload {
    transaction: WalletConnectTransaction,
    output_type: TransferDataOutputType,
}

impl WalletConnectPayload {
    pub(crate) fn parse(input: &SignerInput) -> Result<Self, SignerError> {
        let extra = input.get_data_extra().map_err(SignerError::invalid_input)?;
        let data = extra.data.as_ref().ok_or_else(|| SignerError::invalid_input("Missing transaction data"))?;
        let payload: WalletConnectRequest = serde_json::from_slice(data)?;

        Ok(Self {
            transaction: payload.transaction,
            output_type: extra.output_type.clone(),
        })
    }

    pub(crate) fn transaction_hash(&self) -> Result<[u8; 32], SignerError> {
        self.transaction.validate().map(|(hash, _)| hash)
    }

    pub(crate) fn validated_raw_data(&self) -> Result<([u8; 32], Vec<u8>), SignerError> {
        let (transaction_hash, decoded) = self.transaction.validate()?;
        let encoded = match &self.transaction.raw_data_hex {
            Some(raw_data) => raw_data.clone(),
            None => decoded.encode()?,
        };
        Ok((transaction_hash, encoded))
    }

    pub(crate) fn into_output(self, transaction_hash: [u8; 32], signature_hex: String) -> Result<String, SignerError> {
        self.encode_output(transaction_hash, signature_hex)
    }

    pub(crate) fn encode_output(&self, transaction_hash: [u8; 32], signature_hex: String) -> Result<String, SignerError> {
        match self.output_type {
            TransferDataOutputType::Signature => Ok(signature_hex),
            TransferDataOutputType::EncodedTransaction => self.transaction.clone().into_signed_json(hex::encode(transaction_hash), signature_hex),
        }
    }
}

impl WalletConnectTransaction {
    fn into_signed_json(mut self, transaction_id: String, signature_hex: String) -> Result<String, SignerError> {
        if self.raw_data_hex.is_none() {
            let (_, raw_data) = self.validate()?;
            self.raw_data_hex = Some(raw_data.encode()?);
        }
        self.signature = Some(vec![signature_hex]);
        self.transaction_id = Some(transaction_id);
        serde_json::to_string(&self).map_err(Into::into)
    }
}
