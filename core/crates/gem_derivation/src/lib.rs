mod cardano;
mod error;
mod mnemonic;
mod private_key;
mod public_key;
mod wallet_id;

pub use error::AccountDerivationError;
pub use mnemonic::{derive_accounts_from_mnemonic, derive_private_key_from_mnemonic};
pub use private_key::{ImportedPrivateKeyAccount, default_derivation_path, derive_account_from_private_key, derive_account_from_private_key_value, import_account_from_private_key};
pub use public_key::{
    derive_account_from_extended_public_key, derive_account_from_public_key, derive_bitcoin_account_from_extended_public_key, evm_compatible_chains,
};
pub use wallet_id::derive_wallet_id_from_account;

pub(crate) fn read_array<const N: usize>(bytes: &[u8], offset: usize) -> [u8; N] {
    let mut array = [0u8; N];
    array.copy_from_slice(&bytes[offset..offset + N]);
    array
}
