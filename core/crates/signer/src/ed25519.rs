use ed25519_dalek::{Signature, Signer as DalekSigner, SigningKey, Verifier, VerifyingKey};
use zeroize::Zeroizing;

use primitives::SignerError;

pub const ED25519_KEY_TYPE: u8 = 0;

pub fn verify_ed25519_signature(public_key: &[u8], message: &[u8], signature: &[u8]) -> Result<(), SignerError> {
    let pubkey_bytes: [u8; ed25519_dalek::PUBLIC_KEY_LENGTH] = public_key.try_into().map_err(|_| SignerError::invalid_input("Invalid Ed25519 public key length"))?;
    let sig_bytes: [u8; ed25519_dalek::SIGNATURE_LENGTH] = signature.try_into().map_err(|_| SignerError::invalid_input("Invalid Ed25519 signature length"))?;
    let verifying_key = VerifyingKey::from_bytes(&pubkey_bytes).map_err(|_| SignerError::invalid_input("Invalid Ed25519 public key"))?;
    let dalek_signature = Signature::from_bytes(&sig_bytes);
    verifying_key
        .verify(message, &dalek_signature)
        .map_err(|_| SignerError::signing_error("Ed25519 signature verification failed"))
}

#[derive(Debug)]
pub struct Ed25519KeyPair {
    signing_key: SigningKey,
    pub public_key_bytes: [u8; ed25519_dalek::PUBLIC_KEY_LENGTH],
}

impl Ed25519KeyPair {
    pub fn from_private_key(private_key: &[u8]) -> Result<Self, SignerError> {
        let key_bytes: Zeroizing<[u8; ed25519_dalek::SECRET_KEY_LENGTH]> = Zeroizing::new(private_key.try_into().map_err(|_| SignerError::invalid_input("Invalid Ed25519 private key length"))?);
        let signing_key = SigningKey::from_bytes(&key_bytes);
        Ok(Self {
            public_key_bytes: signing_key.verifying_key().to_bytes(),
            signing_key,
        })
    }

    pub fn sign(&self, digest: &[u8]) -> [u8; ed25519_dalek::SIGNATURE_LENGTH] {
        self.signing_key.sign(digest).to_bytes()
    }
}
