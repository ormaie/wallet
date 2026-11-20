mod chain_signer;
mod instructions;
mod swap;
#[cfg(test)]
pub mod testkit;
mod transaction;

use crate::SignatureBytes;
use ::signer::Ed25519KeyPair;
use primitives::SignerError;

pub use chain_signer::{SolanaChainSigner, UnsignedSolanaTransaction, build_unsigned_solana_transaction, format_solana_message_signature, validate_solana_offchain_message};

fn sign_message(private_key: &[u8], message: &[u8]) -> Result<SignatureBytes, SignerError> {
    let signature = Ed25519KeyPair::from_private_key(private_key)?.sign(message);
    Ok(SignatureBytes::new(signature))
}
