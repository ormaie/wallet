use super::error::UrError;

const MAX_CBOR_DEPTH: usize = 16;
const MAX_CBOR_ITEMS: usize = 4096;
const INDEFINITE_BREAK: u8 = 0xff;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CborValue {
    Unsigned(u64),
    Negative(i128),
    Bytes(Vec<u8>),
    Text(String),
    Array(Vec<CborValue>),
    Map(Vec<(CborValue, CborValue)>),
    Tag(u64, Box<CborValue>),
    Bool(bool),
    Null,
}

impl CborValue {
    pub fn unwrap_tags(&self) -> &CborValue {
        let mut current = self;
        while let CborValue::Tag(_, inner) = current {
            current = inner;
        }
        current
    }

    pub fn as_tag(&self) -> Option<(u64, &CborValue)> {
        match self {
            CborValue::Tag(tag, inner) => Some((*tag, inner)),
            _ => None,
        }
    }

    pub fn as_uint(&self) -> Option<u64> {
        match self.unwrap_tags() {
            CborValue::Unsigned(value) => Some(*value),
            _ => None,
        }
    }

    pub fn as_bool(&self) -> Option<bool> {
        match self.unwrap_tags() {
            CborValue::Bool(value) => Some(*value),
            _ => None,
        }
    }

    pub fn as_bytes(&self) -> Option<&[u8]> {
        match self.unwrap_tags() {
            CborValue::Bytes(bytes) => Some(bytes),
            _ => None,
        }
    }

    pub fn as_str(&self) -> Option<&str> {
        match self.unwrap_tags() {
            CborValue::Text(text) => Some(text),
            _ => None,
        }
    }

    pub fn as_array(&self) -> Option<&[CborValue]> {
        match self.unwrap_tags() {
            CborValue::Array(items) => Some(items),
            _ => None,
        }
    }

    pub fn as_map(&self) -> Option<&[(CborValue, CborValue)]> {
        match self.unwrap_tags() {
            CborValue::Map(entries) => Some(entries),
            _ => None,
        }
    }

    pub fn map_get_uint(&self, target: u64) -> Option<&CborValue> {
        self.as_map()?.iter().find_map(|(key, value)| (key.as_uint() == Some(target)).then_some(value))
    }

    pub fn map_get_str(&self, target: &str) -> Option<&CborValue> {
        self.as_map()?.iter().find_map(|(key, value)| (key.as_str() == Some(target)).then_some(value))
    }
}

pub fn encode_cbor(value: &CborValue) -> Vec<u8> {
    let mut output = Vec::new();
    write_cbor_into(value, &mut output);
    output
}

pub fn decode_cbor(input: &[u8]) -> Result<CborValue, UrError> {
    let mut offset = 0usize;
    let value = read_cbor_item(input, &mut offset, 0)?;
    if offset != input.len() {
        return Err(UrError::InvalidCbor("trailing bytes after CBOR item".to_string()));
    }
    Ok(value)
}

fn write_cbor_into(value: &CborValue, output: &mut Vec<u8>) {
    match value {
        CborValue::Unsigned(number) => write_head(0, *number, output),
        CborValue::Negative(number) => {
            let encoded = (-1i128 - *number).max(0) as u64;
            write_head(1, encoded, output);
        }
        CborValue::Bytes(bytes) => {
            write_head(2, bytes.len() as u64, output);
            output.extend_from_slice(bytes);
        }
        CborValue::Text(text) => {
            write_head(3, text.len() as u64, output);
            output.extend_from_slice(text.as_bytes());
        }
        CborValue::Array(items) => {
            write_head(4, items.len() as u64, output);
            for item in items {
                write_cbor_into(item, output);
            }
        }
        CborValue::Map(entries) => {
            write_head(5, entries.len() as u64, output);
            for (key, val) in entries {
                write_cbor_into(key, output);
                write_cbor_into(val, output);
            }
        }
        CborValue::Tag(tag, inner) => {
            write_head(6, *tag, output);
            write_cbor_into(inner, output);
        }
        CborValue::Bool(false) => output.push((7 << 5) | 20),
        CborValue::Bool(true) => output.push((7 << 5) | 21),
        CborValue::Null => output.push((7 << 5) | 22),
    }
}

fn write_head(major: u8, argument: u64, output: &mut Vec<u8>) {
    let prefix = major << 5;
    if argument < 24 {
        output.push(prefix | (argument as u8));
    } else if argument <= u8::MAX as u64 {
        output.push(prefix | 24);
        output.push(argument as u8);
    } else if argument <= u16::MAX as u64 {
        output.push(prefix | 25);
        output.extend_from_slice(&(argument as u16).to_be_bytes());
    } else if argument <= u32::MAX as u64 {
        output.push(prefix | 26);
        output.extend_from_slice(&(argument as u32).to_be_bytes());
    } else {
        output.push(prefix | 27);
        output.extend_from_slice(&argument.to_be_bytes());
    }
}

fn read_cbor_item(input: &[u8], offset: &mut usize, depth: usize) -> Result<CborValue, UrError> {
    if depth > MAX_CBOR_DEPTH {
        return Err(UrError::InvalidCbor("CBOR nesting depth exceeded".to_string()));
    }
    let initial = read_byte(input, offset)?;
    let major = initial >> 5;
    let info = initial & 0x1f;

    match major {
        0 => Ok(CborValue::Unsigned(read_argument(input, offset, info)?)),
        1 => {
            let raw = read_argument(input, offset, info)?;
            Ok(CborValue::Negative(-1i128 - (raw as i128)))
        }
        2 => {
            if info == 31 {
                let mut combined = Vec::new();
                while peek_byte(input, *offset)? != INDEFINITE_BREAK {
                    let chunk = read_cbor_item(input, offset, depth + 1)?;
                    let CborValue::Bytes(bytes) = chunk else {
                        return Err(UrError::InvalidCbor("indefinite byte string chunk must be bytes".to_string()));
                    };
                    combined.extend_from_slice(&bytes);
                }
                *offset += 1;
                return Ok(CborValue::Bytes(combined));
            }
            let len = read_len(input, offset, info)?;
            let slice = read_slice(input, offset, len)?;
            Ok(CborValue::Bytes(slice.to_vec()))
        }
        3 => {
            if info == 31 {
                let mut combined = String::new();
                while peek_byte(input, *offset)? != INDEFINITE_BREAK {
                    let chunk = read_cbor_item(input, offset, depth + 1)?;
                    let CborValue::Text(text) = chunk else {
                        return Err(UrError::InvalidCbor("indefinite text string chunk must be text".to_string()));
                    };
                    combined.push_str(&text);
                }
                *offset += 1;
                return Ok(CborValue::Text(combined));
            }
            let len = read_len(input, offset, info)?;
            let slice = read_slice(input, offset, len)?;
            let text = String::from_utf8(slice.to_vec()).map_err(|_| UrError::InvalidCbor("invalid UTF-8 in CBOR text".to_string()))?;
            Ok(CborValue::Text(text))
        }
        4 => {
            if info == 31 {
                let mut items = Vec::new();
                while peek_byte(input, *offset)? != INDEFINITE_BREAK {
                    if items.len() >= MAX_CBOR_ITEMS {
                        return Err(UrError::InvalidCbor("CBOR array exceeds item limit".to_string()));
                    }
                    items.push(read_cbor_item(input, offset, depth + 1)?);
                }
                *offset += 1;
                return Ok(CborValue::Array(items));
            }
            let count = read_item_count(input, offset, info)?;
            let mut items = Vec::with_capacity(count);
            for _ in 0..count {
                items.push(read_cbor_item(input, offset, depth + 1)?);
            }
            Ok(CborValue::Array(items))
        }
        5 => {
            if info == 31 {
                let mut entries = Vec::new();
                while peek_byte(input, *offset)? != INDEFINITE_BREAK {
                    if entries.len() >= MAX_CBOR_ITEMS {
                        return Err(UrError::InvalidCbor("CBOR map exceeds item limit".to_string()));
                    }
                    let key = read_cbor_item(input, offset, depth + 1)?;
                    let value = read_cbor_item(input, offset, depth + 1)?;
                    entries.push((key, value));
                }
                *offset += 1;
                return Ok(CborValue::Map(entries));
            }
            let count = read_item_count(input, offset, info)?;
            let mut entries = Vec::with_capacity(count);
            for _ in 0..count {
                let key = read_cbor_item(input, offset, depth + 1)?;
                let value = read_cbor_item(input, offset, depth + 1)?;
                entries.push((key, value));
            }
            Ok(CborValue::Map(entries))
        }
        6 => {
            let tag = read_argument(input, offset, info)?;
            let inner = read_cbor_item(input, offset, depth + 1)?;
            Ok(CborValue::Tag(tag, Box::new(inner)))
        }
        7 => match info {
            20 => Ok(CborValue::Bool(false)),
            21 => Ok(CborValue::Bool(true)),
            22 | 23 => Ok(CborValue::Null),
            _ => Err(UrError::InvalidCbor(format!("unsupported CBOR simple/float info: {info}"))),
        },
        _ => Err(UrError::InvalidCbor("invalid CBOR major type".to_string())),
    }
}

fn read_argument(input: &[u8], offset: &mut usize, info: u8) -> Result<u64, UrError> {
    match info {
        0..=23 => Ok(info as u64),
        24 => Ok(read_byte(input, offset)? as u64),
        25 => {
            let bytes = read_slice(input, offset, 2)?;
            Ok(u16::from_be_bytes([bytes[0], bytes[1]]) as u64)
        }
        26 => {
            let bytes = read_slice(input, offset, 4)?;
            Ok(u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]) as u64)
        }
        27 => {
            let bytes = read_slice(input, offset, 8)?;
            Ok(u64::from_be_bytes([
                bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
            ]))
        }
        _ => Err(UrError::InvalidCbor(format!("invalid CBOR additional info: {info}"))),
    }
}

fn read_len(input: &[u8], offset: &mut usize, info: u8) -> Result<usize, UrError> {
    let raw = read_argument(input, offset, info)?;
    let len = usize::try_from(raw).map_err(|_| UrError::InvalidCbor("CBOR length overflow".to_string()))?;
    if len > input.len().saturating_sub(*offset) {
        return Err(UrError::InvalidCbor("CBOR length exceeds remaining input".to_string()));
    }
    Ok(len)
}

fn read_item_count(input: &[u8], offset: &mut usize, info: u8) -> Result<usize, UrError> {
    let len = read_len(input, offset, info)?;
    if len > MAX_CBOR_ITEMS {
        return Err(UrError::InvalidCbor("CBOR collection exceeds item limit".to_string()));
    }
    Ok(len)
}

fn peek_byte(input: &[u8], offset: usize) -> Result<u8, UrError> {
    input
        .get(offset)
        .copied()
        .ok_or_else(|| UrError::InvalidCbor("unexpected end of CBOR input".to_string()))
}

fn read_byte(input: &[u8], offset: &mut usize) -> Result<u8, UrError> {
    let byte = peek_byte(input, *offset)?;
    *offset += 1;
    Ok(byte)
}

fn read_slice<'a>(input: &'a [u8], offset: &mut usize, len: usize) -> Result<&'a [u8], UrError> {
    let end = offset.checked_add(len).ok_or_else(|| UrError::InvalidCbor("CBOR slice overflow".to_string()))?;
    let slice = input.get(*offset..end).ok_or_else(|| UrError::InvalidCbor("unexpected end of CBOR slice".to_string()))?;
    *offset = end;
    Ok(slice)
}
