use super::crc32::crc32;
use super::error::UrError;

const CHECKSUM_BYTE_LEN: usize = 4;

const WORDS: [&str; 256] = [
    "able", "acid", "also", "apex", "aqua", "arch", "atom", "aunt",
    "away", "axis", "back", "bald", "barn", "belt", "beta", "bias",
    "blue", "body", "brag", "brew", "bulb", "buzz", "calm", "cash",
    "cats", "chef", "city", "claw", "code", "cola", "cook", "cost",
    "crux", "curl", "cusp", "cyan", "dark", "data", "days", "deli",
    "dice", "diet", "door", "down", "draw", "drop", "drum", "dull",
    "duty", "each", "easy", "echo", "edge", "epic", "even", "exam",
    "exit", "eyes", "fact", "fair", "fern", "figs", "film", "fish",
    "fizz", "flap", "flew", "flux", "foxy", "free", "frog", "fuel",
    "fund", "gala", "game", "gear", "gems", "gift", "girl", "glow",
    "good", "gray", "grim", "guru", "gush", "gyro", "half", "hang",
    "hard", "hawk", "heat", "help", "high", "hill", "holy", "hope",
    "horn", "huts", "iced", "idea", "idle", "inch", "inky", "into",
    "iris", "iron", "item", "jade", "jazz", "join", "jolt", "jowl",
    "judo", "jugs", "jump", "junk", "jury", "keep", "keno", "kept",
    "keys", "kick", "kiln", "king", "kite", "kiwi", "knob", "lamb",
    "lava", "lazy", "leaf", "legs", "liar", "limp", "lion", "list",
    "logo", "loud", "love", "luau", "luck", "lung", "main", "many",
    "math", "maze", "memo", "menu", "meow", "mild", "mint", "miss",
    "monk", "nail", "navy", "need", "news", "next", "noon", "note",
    "numb", "obey", "oboe", "omit", "onyx", "open", "oval", "owls",
    "paid", "part", "peck", "play", "plus", "poem", "pool", "pose",
    "puff", "puma", "purr", "quad", "quiz", "race", "ramp", "real",
    "redo", "rich", "road", "rock", "roof", "ruby", "ruin", "runs",
    "rust", "safe", "saga", "scar", "sets", "silk", "skew", "slot",
    "soap", "solo", "song", "stub", "surf", "swan", "taco", "task",
    "taxi", "tent", "tied", "time", "tiny", "toil", "tomb", "toys",
    "trip", "tuna", "twin", "ugly", "undo", "unit", "urge", "user",
    "vast", "very", "veto", "vial", "vibe", "view", "visa", "void",
    "vows", "wall", "wand", "warm", "wasp", "wave", "waxy", "webs",
    "what", "when", "whiz", "wolf", "work", "yank", "yawn", "yell",
    "yoga", "yurt", "zaps", "zero", "zest", "zinc", "zone", "zoom",
];

pub fn encode_minimal_upper(payload: &[u8]) -> String {
    let checksum = crc32(payload).to_be_bytes();
    let mut output = String::with_capacity((payload.len() + CHECKSUM_BYTE_LEN) * 2);
    for byte in payload.iter().chain(checksum.iter()) {
        let word = WORDS[*byte as usize].as_bytes();
        output.push((word[0] as char).to_ascii_uppercase());
        output.push((word[3] as char).to_ascii_uppercase());
    }
    output
}

pub fn decode_bytewords(encoded: &str) -> Result<Vec<u8>, UrError> {
    let trimmed = encoded.trim();
    if trimmed.is_empty() {
        return Err(UrError::InvalidFormat("empty bytewords payload".to_string()));
    }

    let raw_bytes = if trimmed.contains('-') || trimmed.contains(' ') {
        decode_separated_words(trimmed)?
    } else {
        decode_minimal_words(trimmed)?
    };

    if raw_bytes.len() < CHECKSUM_BYTE_LEN {
        return Err(UrError::InvalidFormat("bytewords shorter than checksum".to_string()));
    }
    let split_index = raw_bytes.len() - CHECKSUM_BYTE_LEN;
    let payload = &raw_bytes[..split_index];
    let expected_checksum = u32::from_be_bytes([
        raw_bytes[split_index],
        raw_bytes[split_index + 1],
        raw_bytes[split_index + 2],
        raw_bytes[split_index + 3],
    ]);
    if crc32(payload) != expected_checksum {
        return Err(UrError::InvalidChecksum);
    }
    Ok(payload.to_vec())
}

fn decode_minimal_words(encoded: &str) -> Result<Vec<u8>, UrError> {
    let bytes = encoded.as_bytes();
    if !bytes.len().is_multiple_of(2) {
        return Err(UrError::InvalidFormat("minimal bytewords length must be even".to_string()));
    }
    let mut output = Vec::with_capacity(bytes.len() / 2);
    for pair in bytes.as_chunks::<2>().0 {
        let first = pair[0].to_ascii_lowercase();
        let last = pair[1].to_ascii_lowercase();
        let value = lookup_minimal(first, last).ok_or_else(|| UrError::InvalidFormat("unknown minimal byteword".to_string()))?;
        output.push(value);
    }
    Ok(output)
}

fn decode_separated_words(encoded: &str) -> Result<Vec<u8>, UrError> {
    let mut output = Vec::new();
    for token in encoded.split(['-', ' ']).filter(|part| !part.is_empty()) {
        let lower = token.to_ascii_lowercase();
        let index = WORDS
            .binary_search(&lower.as_str())
            .map_err(|_| UrError::InvalidFormat("unknown full byteword".to_string()))?;
        output.push(index as u8);
    }
    Ok(output)
}

fn lookup_minimal(first: u8, last: u8) -> Option<u8> {
    WORDS.iter().position(|word| {
        let bytes = word.as_bytes();
        bytes[0] == first && bytes[3] == last
    }).map(|index| index as u8)
}

#[cfg(test)]
mod tests {
    use primitives::hex;

    use super::*;

    #[test]
    fn test_bcr_2020_012_vectors() {
        let body = hex::decode("d99d6ca20150c7098580125e2ab0981253468b2dbc5202c11947da").unwrap();
        let minimal = encode_minimal_upper(&body);
        assert_eq!(minimal.to_ascii_lowercase(), "tantjzoeadgdstaslplabghydrpfmkbggufgludprfgmaosecffltnsoaawkbd");
        assert_eq!(decode_bytewords(&minimal).unwrap(), body);

        let uri_words = "tuna-next-jazz-oboe-acid-good-slot-axis-limp-lava-brag-holy-door-puff-monk-brag-guru-frog-luau-drop-roof-grim-also-safe-chef-fuel-twin-solo-aqua-work-bald";
        assert_eq!(decode_bytewords(uri_words).unwrap(), body);

        let seed = hex::decode("c7098580125e2ab0981253468b2dbc52").unwrap();
        assert_eq!(encode_minimal_upper(&seed).to_ascii_lowercase(), "staslplabghydrpfmkbggufgludprfgmzepsbtwd");
        assert_eq!(decode_bytewords("staslplabghydrpfmkbggufgludprfgmzepsbtzm"), Err(UrError::InvalidChecksum));
    }
}
