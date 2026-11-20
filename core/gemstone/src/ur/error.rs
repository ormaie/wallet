use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

use crate::GemstoneError;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UrError {
    InvalidFormat(String),
    InvalidChecksum,
    InvalidCbor(String),
    UnsupportedType(String),
    InvalidPayload(String),
}

impl Display for UrError {
    fn fmt(&self, f: &mut Formatter<'_>) -> FmtResult {
        match self {
            Self::InvalidFormat(msg) => write!(f, "Invalid UR format: {msg}"),
            Self::InvalidChecksum => write!(f, "Invalid UR checksum"),
            Self::InvalidCbor(msg) => write!(f, "Invalid UR CBOR: {msg}"),
            Self::UnsupportedType(ur_type) => write!(f, "Unsupported UR type: {ur_type}"),
            Self::InvalidPayload(msg) => write!(f, "Invalid UR payload: {msg}"),
        }
    }
}

impl Error for UrError {}

impl From<UrError> for GemstoneError {
    fn from(error: UrError) -> Self {
        Self::AnyError { msg: error.to_string() }
    }
}
