use std::collections::{BTreeMap, BTreeSet, VecDeque};
use std::mem;

use gem_bitcoin::signer::decode_psbt_base64;
use primitives::decode_hex;

use super::bytewords::{decode_bytewords, encode_minimal_upper};
use super::cbor::{CborValue, decode_cbor, encode_cbor};
use super::crc32::crc32;
use super::error::UrError;
use super::xoshiro::FragmentChooser;

pub const DEFAULT_MAX_FRAGMENT_LEN: usize = 140;
const DEFAULT_MIN_FRAGMENT_LEN: usize = 10;
const MAX_FOUNTAIN_FRAGMENTS: usize = 4096;
const MAX_UR_MESSAGE_BYTES: usize = 512 * 1024;
const MAX_FRAGMENT_BYTES: usize = 16 * 1024;
const PSBT_MAGIC: &[u8; 5] = b"psbt\xff";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FountainPart {
    pub seq_num: u32,
    pub seq_len: usize,
    pub message_len: usize,
    pub checksum: u32,
    pub data: Vec<u8>,
}

impl FountainPart {
    pub fn to_cbor(&self) -> Vec<u8> {
        encode_cbor(&CborValue::Array(vec![
            CborValue::Unsigned(self.seq_num as u64),
            CborValue::Unsigned(self.seq_len as u64),
            CborValue::Unsigned(self.message_len as u64),
            CborValue::Unsigned(self.checksum as u64),
            CborValue::Bytes(self.data.clone()),
        ]))
    }

    pub fn from_cbor(bytes: &[u8]) -> Result<Self, UrError> {
        let value = decode_cbor(bytes)?;
        let items = value.as_array().ok_or_else(|| UrError::InvalidCbor("fountain part must be CBOR array".to_string()))?;
        if items.len() != 5 {
            return Err(UrError::InvalidCbor("fountain part array must have 5 elements".to_string()));
        }
        let seq_num = u32::try_from(items[0].as_uint().ok_or_else(|| UrError::InvalidCbor("invalid seq_num".to_string()))?)
            .map_err(|_| UrError::InvalidCbor("seq_num overflow".to_string()))?;
        let seq_len = usize::try_from(items[1].as_uint().ok_or_else(|| UrError::InvalidCbor("invalid seq_len".to_string()))?)
            .map_err(|_| UrError::InvalidCbor("seq_len overflow".to_string()))?;
        let message_len = usize::try_from(items[2].as_uint().ok_or_else(|| UrError::InvalidCbor("invalid message_len".to_string()))?)
            .map_err(|_| UrError::InvalidCbor("message_len overflow".to_string()))?;
        let checksum = u32::try_from(items[3].as_uint().ok_or_else(|| UrError::InvalidCbor("invalid checksum".to_string()))?)
            .map_err(|_| UrError::InvalidCbor("checksum overflow".to_string()))?;
        let data = items[4].as_bytes().ok_or_else(|| UrError::InvalidCbor("invalid part data".to_string()))?.to_vec();

        Ok(Self {
            seq_num,
            seq_len,
            message_len,
            checksum,
            data,
        })
    }
}

pub struct FountainEncoder {
    message_len: usize,
    fragment_len: usize,
    seq_num: u32,
    checksum: u32,
    fragments: Vec<Vec<u8>>,
    fragment_chooser: FragmentChooser,
}

impl FountainEncoder {
    pub fn new(message: &[u8], max_fragment_len: usize, first_seq_num: u32, min_fragment_len: usize) -> Self {
        let message_len = message.len();
        let checksum = crc32(message);
        let effective_min = min_fragment_len.max(1);
        let effective_max = max_fragment_len.max(effective_min);
        let fragment_len = Self::find_nominal_fragment_length(message_len.max(1), effective_min, effective_max);
        let fragments = Self::partition_message(message, fragment_len);
        let fragment_chooser = FragmentChooser::new(fragments.len(), checksum);
        Self {
            message_len,
            fragment_len,
            seq_num: first_seq_num,
            checksum,
            fragments,
            fragment_chooser,
        }
    }

    pub fn seq_len(&self) -> usize {
        self.fragments.len()
    }

    pub fn find_nominal_fragment_length(message_len: usize, min_fragment_len: usize, max_fragment_len: usize) -> usize {
        let max_fragment_count = (message_len / min_fragment_len).max(1);
        let mut fragment_len = message_len;
        for fragment_count in 1..=max_fragment_count {
            fragment_len = message_len.div_ceil(fragment_count);
            if fragment_len <= max_fragment_len {
                break;
            }
        }
        fragment_len
    }

    pub fn partition_message(message: &[u8], fragment_len: usize) -> Vec<Vec<u8>> {
        if message.is_empty() {
            return vec![vec![0u8; fragment_len.max(1)]];
        }
        let mut fragments = Vec::new();
        for chunk in message.chunks(fragment_len) {
            let mut fragment = chunk.to_vec();
            if fragment.len() < fragment_len {
                fragment.resize(fragment_len, 0);
            }
            fragments.push(fragment);
        }
        fragments
    }

    pub fn next_part(&mut self) -> FountainPart {
        self.seq_num = self.seq_num.wrapping_add(1);
        let indexes = self.fragment_chooser.choose_fragments(self.seq_num);
        let data = self.mix(&indexes);
        FountainPart {
            seq_num: self.seq_num,
            seq_len: self.fragments.len(),
            message_len: self.message_len,
            checksum: self.checksum,
            data,
        }
    }

    fn mix(&self, indexes: &BTreeSet<usize>) -> Vec<u8> {
        if indexes.len() == 1
            && let Some(only) = indexes.iter().next()
        {
            return self.fragments[*only].clone();
        }
        let mut mixed = vec![0u8; self.fragment_len];
        for index in indexes {
            xor_into(&mut mixed, &self.fragments[*index]);
        }
        mixed
    }
}

#[derive(Clone)]
struct DecoderPart {
    indexes: BTreeSet<usize>,
    data: Vec<u8>,
}

impl DecoderPart {
    fn is_simple(&self) -> bool {
        self.indexes.len() == 1
    }

    fn simple_index(&self) -> Option<usize> {
        self.indexes.iter().next().copied()
    }
}

#[derive(Default)]
pub struct FountainDecoder {
    expected_seq_len: Option<usize>,
    expected_fragment_len: Option<usize>,
    expected_message_len: Option<usize>,
    expected_checksum: Option<u32>,
    fragment_chooser: Option<FragmentChooser>,
    received_indexes: BTreeSet<usize>,
    simple_parts: BTreeMap<usize, Vec<u8>>,
    mixed_parts: BTreeMap<BTreeSet<usize>, DecoderPart>,
    queue: VecDeque<DecoderPart>,
    result: Option<Result<Vec<u8>, UrError>>,
}

impl FountainDecoder {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn received_count(&self) -> usize {
        self.received_indexes.len()
    }

    pub fn expected_count(&self) -> usize {
        self.expected_seq_len.unwrap_or(0)
    }

    pub fn receive_part(&mut self, encoder_part: FountainPart) -> Result<Option<Vec<u8>>, UrError> {
        if let Some(existing) = &self.result {
            return existing.clone().map(Some);
        }
        if !self.validate_part(&encoder_part)? {
            return Ok(None);
        }
        let chooser = self
            .fragment_chooser
            .get_or_insert_with(|| FragmentChooser::new(encoder_part.seq_len, encoder_part.checksum));
        let indexes = chooser.choose_fragments(encoder_part.seq_num);
        if indexes.is_empty() {
            return Ok(None);
        }
        self.queue.push_back(DecoderPart {
            indexes,
            data: encoder_part.data,
        });
        self.drain_queue();

        match &self.result {
            Some(Ok(bytes)) => Ok(Some(bytes.clone())),
            Some(Err(err)) => Err(err.clone()),
            None => Ok(None),
        }
    }

    fn validate_part(&mut self, part: &FountainPart) -> Result<bool, UrError> {
        if part.seq_num == 0
            || part.seq_len == 0
            || part.seq_len > MAX_FOUNTAIN_FRAGMENTS
            || part.message_len == 0
            || part.message_len > MAX_UR_MESSAGE_BYTES
            || part.data.is_empty()
            || part.data.len() > MAX_FRAGMENT_BYTES
            || part.seq_len.saturating_mul(part.data.len()) < part.message_len
        {
            return Err(UrError::InvalidFormat("invalid fountain part bounds".to_string()));
        }

        if let (Some(seq_len), Some(message_len), Some(checksum), Some(fragment_len)) = (
            self.expected_seq_len,
            self.expected_message_len,
            self.expected_checksum,
            self.expected_fragment_len,
        ) && (seq_len != part.seq_len
            || message_len != part.message_len
            || checksum != part.checksum
            || fragment_len != part.data.len())
        {
            *self = Self::new();
        }

        if self.expected_seq_len.is_none() {
            self.expected_seq_len = Some(part.seq_len);
            self.expected_message_len = Some(part.message_len);
            self.expected_checksum = Some(part.checksum);
            self.expected_fragment_len = Some(part.data.len());
        }
        Ok(true)
    }

    fn drain_queue(&mut self) {
        while self.result.is_none() {
            let Some(part) = self.queue.pop_front() else {
                break;
            };
            if part.is_simple() {
                self.record_simple_part(part);
            } else {
                self.record_mixed_part(part);
            }
        }
    }

    fn record_simple_part(&mut self, part: DecoderPart) {
        let Some(index) = part.simple_index() else {
            return;
        };
        if self.received_indexes.contains(&index) {
            return;
        }
        self.received_indexes.insert(index);
        self.simple_parts.insert(index, part.data.clone());

        if Some(self.received_indexes.len()) == self.expected_seq_len {
            let Some(message_len) = self.expected_message_len else {
                return;
            };
            let Some(expected_checksum) = self.expected_checksum else {
                return;
            };
            let mut joined = Vec::with_capacity(self.simple_parts.len() * part.data.len());
            for fragment in self.simple_parts.values() {
                joined.extend_from_slice(fragment);
            }
            joined.truncate(message_len);
            if crc32(&joined) == expected_checksum {
                self.result = Some(Ok(joined));
            } else {
                self.result = Some(Err(UrError::InvalidChecksum));
            }
        } else {
            self.reduce_mixed_by(&part);
        }
    }

    fn record_mixed_part(&mut self, mut candidate: DecoderPart) {
        if self.mixed_parts.contains_key(&candidate.indexes) {
            return;
        }
        for (&index, data) in &self.simple_parts {
            if candidate.indexes.contains(&index) && candidate.indexes.len() > 1 {
                candidate.indexes.remove(&index);
                xor_into(&mut candidate.data, data);
            }
        }
        for existing in self.mixed_parts.values() {
            candidate = reduce_part(candidate, existing);
        }

        if candidate.is_simple() {
            self.queue.push_back(candidate);
        } else {
            self.reduce_mixed_by(&candidate);
            self.mixed_parts.insert(candidate.indexes.clone(), candidate);
        }
    }

    fn reduce_mixed_by(&mut self, reducer: &DecoderPart) {
        let current_mixed: Vec<DecoderPart> = mem::take(&mut self.mixed_parts).into_values().collect();
        for item in current_mixed {
            let reduced = reduce_part(item, reducer);
            if reduced.is_simple() {
                self.queue.push_back(reduced);
            } else {
                self.mixed_parts.insert(reduced.indexes.clone(), reduced);
            }
        }
    }
}

fn reduce_part(mut target: DecoderPart, by: &DecoderPart) -> DecoderPart {
    if by.indexes.len() < target.indexes.len() && by.indexes.is_subset(&target.indexes) {
        for index in &by.indexes {
            target.indexes.remove(index);
        }
        xor_into(&mut target.data, &by.data);
    }
    target
}

fn xor_into(target: &mut [u8], source: &[u8]) {
    for (left, right) in target.iter_mut().zip(source.iter()) {
        *left ^= *right;
    }
}

pub struct UrEncoder;

impl UrEncoder {
    pub fn encode(ur_type: &str, cbor: &[u8], max_fragment_len: usize) -> Vec<String> {
        let upper_type = ur_type.to_ascii_uppercase();
        let limit = max_fragment_len.max(DEFAULT_MIN_FRAGMENT_LEN);
        if cbor.len() <= limit {
            return vec![format!("UR:{upper_type}/{}", encode_minimal_upper(cbor))];
        }

        let mut fountain = FountainEncoder::new(cbor, limit, 0, DEFAULT_MIN_FRAGMENT_LEN);
        let seq_len = fountain.seq_len();
        let total_frames = seq_len.saturating_mul(2);
        (0..total_frames)
            .map(|_| {
                let part = fountain.next_part();
                let part_cbor = part.to_cbor();
                format!("UR:{upper_type}/{}-{}/{}", part.seq_num, part.seq_len, encode_minimal_upper(&part_cbor))
            })
            .collect()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UrReceiveOutcome {
    Progress { received: usize, expected: usize },
    Complete { ur_type: String, cbor: Vec<u8> },
}

#[derive(Default)]
pub struct UrDecoder {
    active_type: Option<String>,
    fountain: FountainDecoder,
}

impl UrDecoder {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn reset(&mut self) {
        self.active_type = None;
        self.fountain = FountainDecoder::new();
    }

    pub fn receive(&mut self, payload: &str) -> Result<UrReceiveOutcome, UrError> {
        let trimmed = payload.trim();
        if trimmed.is_empty() {
            return Err(UrError::InvalidFormat("empty QR payload".to_string()));
        }

        let lines: Vec<&str> = trimmed.lines().map(str::trim).filter(|line| !line.is_empty()).collect();
        if lines.len() > 1 {
            let mut last_outcome = None;
            for line in lines {
                let outcome = self.receive_single(line)?;
                if matches!(outcome, UrReceiveOutcome::Complete { .. }) {
                    return Ok(outcome);
                }
                last_outcome = Some(outcome);
            }
            return last_outcome.ok_or_else(|| UrError::InvalidFormat("empty multi-line QR payload".to_string()));
        }

        self.receive_single(trimmed)
    }

    fn receive_single(&mut self, line: &str) -> Result<UrReceiveOutcome, UrError> {
        if line.len() >= 3 && line[..3].eq_ignore_ascii_case("ur:") {
            let body = &line[3..];
            let segments: Vec<&str> = body.split('/').collect();
            match segments.as_slice() {
                [raw_type, bytewords] => {
                    let ur_type = normalize_ur_type(raw_type)?;
                    if self.active_type.is_some() {
                        self.reset();
                    }
                    let cbor = decode_bytewords(bytewords)?;
                    Ok(UrReceiveOutcome::Complete { ur_type, cbor })
                }
                [raw_type, seq_header, bytewords] => {
                    let ur_type = normalize_ur_type(raw_type)?;
                    if self.active_type.as_deref() != Some(ur_type.as_str()) {
                        self.reset();
                        self.active_type = Some(ur_type.clone());
                    }
                    let (header_seq_num, header_seq_len) = parse_seq_header(seq_header)?;
                    let part_cbor = decode_bytewords(bytewords)?;
                    let part = FountainPart::from_cbor(&part_cbor)?;
                    if part.seq_num != header_seq_num || part.seq_len != header_seq_len {
                        return Err(UrError::InvalidFormat("UR sequence header mismatch".to_string()));
                    }
                    match self.fountain.receive_part(part)? {
                        Some(cbor) => {
                            self.reset();
                            Ok(UrReceiveOutcome::Complete { ur_type, cbor })
                        }
                        None => Ok(UrReceiveOutcome::Progress {
                            received: self.fountain.received_count(),
                            expected: self.fountain.expected_count(),
                        }),
                    }
                }
                _ => Err(UrError::InvalidFormat("expected ur:<type>/<payload> or ur:<type>/<seq>/<payload>".to_string())),
            }
        } else {
            let psbt_bytes = try_decode_raw_psbt(line)?;
            self.reset();
            Ok(UrReceiveOutcome::Complete {
                ur_type: "crypto-psbt".to_string(),
                cbor: encode_cbor(&CborValue::Bytes(psbt_bytes)),
            })
        }
    }
}

fn normalize_ur_type(raw: &str) -> Result<String, UrError> {
    let normalized = raw.trim().to_ascii_lowercase();
    if normalized.is_empty() || !normalized.bytes().all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-') {
        return Err(UrError::InvalidFormat(format!("invalid UR type: {raw}")));
    }
    Ok(normalized)
}

fn parse_seq_header(header: &str) -> Result<(u32, usize), UrError> {
    let (left, right) = header
        .split_once('-')
        .ok_or_else(|| UrError::InvalidFormat("expected <seqNum>-<seqLen>".to_string()))?;
    let seq_num: u32 = left.parse().map_err(|_| UrError::InvalidFormat("invalid seqNum".to_string()))?;
    let seq_len: usize = right.parse().map_err(|_| UrError::InvalidFormat("invalid seqLen".to_string()))?;
    if seq_num == 0 || seq_len == 0 {
        return Err(UrError::InvalidFormat("sequence numbers must be positive".to_string()));
    }
    Ok((seq_num, seq_len))
}

fn try_decode_raw_psbt(raw: &str) -> Result<Vec<u8>, UrError> {
    if let Ok(bytes) = decode_hex(raw)
        && bytes.starts_with(PSBT_MAGIC)
    {
        return Ok(bytes);
    }
    if let Ok(bytes) = decode_psbt_base64(raw)
        && bytes.starts_with(PSBT_MAGIC)
    {
        return Ok(bytes);
    }
    Err(UrError::InvalidFormat("unrecognized QR payload".to_string()))
}

#[cfg(test)]
mod tests {
    use primitives::hex;

    use super::*;
    use crate::ur::xoshiro::Xoshiro256;

    #[test]
    fn test_part_cbor_and_fountain_encoder_vectors() {
        let part = FountainPart {
            seq_num: 12,
            seq_len: 8,
            message_len: 100,
            checksum: 0x1234_5678,
            data: vec![1, 5, 3, 3, 5],
        };
        let encoded = part.to_cbor();
        assert_eq!(hex::encode(&encoded), "850c0818641a12345678450105030305");
        assert_eq!(FountainPart::from_cbor(&encoded).unwrap(), part);

        let mut rng = Xoshiro256::from_seed(b"Wolf");
        let message = rng.next_bytes(256);
        let mut encoder = FountainEncoder::new(&message, 30, 0, 10);
        let first_cbor = hex::encode(encoder.next_part().to_cbor());
        assert_eq!(first_cbor, "8501091901001a0167aa07581d916ec65cf77cadf55cd7f9cda1a1030026ddd42e905b77adc36e4f2d3c");
        for _ in 1..9 {
            encoder.next_part();
        }
        let tenth_cbor = hex::encode(encoder.next_part().to_cbor());
        assert_eq!(tenth_cbor, "850a091901001a0167aa07581d330f0f33a05eead4f331df229871bee733b50de71afd2e5a79f196de09");
    }

    #[test]
    fn test_rateless_fountain_decoder_recovers_large_message() {
        let mut rng = Xoshiro256::from_seed(b"Wolf");
        let message = rng.next_bytes(4096);
        let mut encoder = FountainEncoder::new(&message, 200, 100, 10);
        let mut decoder = FountainDecoder::new();
        let recovered = loop {
            let part = encoder.next_part();
            if let Some(done) = decoder.receive_part(part).unwrap() {
                break done;
            }
        };
        assert_eq!(recovered, message);
    }
}
