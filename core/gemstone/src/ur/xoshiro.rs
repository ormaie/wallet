use std::collections::BTreeSet;

use gem_hash::sha2::sha256;

const STATE_WORDS: usize = 4;
const U64_MAX_PLUS_ONE: f64 = (u64::MAX as f64) + 1.0;

pub struct Xoshiro256 {
    state: [u64; STATE_WORDS],
}

impl Xoshiro256 {
    pub fn from_seed(seed: &[u8]) -> Self {
        let digest = sha256(seed);
        let mut state = [0u64; STATE_WORDS];
        for (index, slot) in state.iter_mut().enumerate() {
            let offset = index * 8;
            *slot = u64::from_be_bytes([
                digest[offset],
                digest[offset + 1],
                digest[offset + 2],
                digest[offset + 3],
                digest[offset + 4],
                digest[offset + 5],
                digest[offset + 6],
                digest[offset + 7],
            ]);
        }
        Self { state }
    }

    pub fn from_crc32(checksum: u32) -> Self {
        Self::from_seed(&checksum.to_be_bytes())
    }

    pub fn next_u64(&mut self) -> u64 {
        let result = self.state[1].wrapping_mul(5).rotate_left(7).wrapping_mul(9);
        let shifted = self.state[1] << 17;

        self.state[2] ^= self.state[0];
        self.state[3] ^= self.state[1];
        self.state[1] ^= self.state[2];
        self.state[0] ^= self.state[3];
        self.state[2] ^= shifted;
        self.state[3] = self.state[3].rotate_left(45);

        result
    }

    pub fn next_double(&mut self) -> f64 {
        (self.next_u64() as f64) / U64_MAX_PLUS_ONE
    }

    pub fn next_int(&mut self, low: usize, count: usize) -> usize {
        ((self.next_double() * (count as f64)) as usize) + low
    }

    #[cfg(test)]
    pub fn next_bytes(&mut self, len: usize) -> Vec<u8> {
        (0..len).map(|_| self.next_int(0, 256) as u8).collect()
    }
}

pub struct RandomSampler {
    probs: Vec<f64>,
    aliases: Vec<usize>,
}

impl RandomSampler {
    pub fn new(weights: &[f64]) -> Self {
        let count = weights.len();
        let sum: f64 = weights.iter().sum();
        let mut scaled: Vec<f64> = weights.iter().map(|weight| weight * (count as f64) / sum).collect();

        let mut small = Vec::new();
        let mut large = Vec::new();
        for index in (0..count).rev() {
            if scaled[index] < 1.0 {
                small.push(index);
            } else {
                large.push(index);
            }
        }

        let mut probs = vec![0.0; count];
        let mut aliases = vec![0usize; count];
        while let (Some(less), Some(greater)) = (small.pop(), large.pop()) {
            probs[less] = scaled[less];
            aliases[less] = greater;
            scaled[greater] += scaled[less] - 1.0;
            if scaled[greater] < 1.0 {
                small.push(greater);
            } else {
                large.push(greater);
            }
        }
        while let Some(greater) = large.pop() {
            probs[greater] = 1.0;
        }
        while let Some(less) = small.pop() {
            probs[less] = 1.0;
        }

        Self { probs, aliases }
    }

    pub fn next(&self, rng: &mut Xoshiro256) -> usize {
        let r1 = rng.next_double();
        let r2 = rng.next_double();
        let index = ((self.probs.len() as f64) * r1) as usize;
        if r2 < self.probs[index] {
            index
        } else {
            self.aliases[index]
        }
    }
}

pub struct DegreeChooser {
    sampler: RandomSampler,
}

impl DegreeChooser {
    pub fn new(seq_len: usize) -> Self {
        let probabilities: Vec<f64> = (1..=seq_len).map(|degree| 1.0 / (degree as f64)).collect();
        Self {
            sampler: RandomSampler::new(&probabilities),
        }
    }

    pub fn choose_degree(&self, rng: &mut Xoshiro256) -> usize {
        self.sampler.next(rng) + 1
    }
}

pub fn shuffled<T: Clone>(items: &[T], rng: &mut Xoshiro256, count: usize) -> Vec<T> {
    let mut remaining = items.to_vec();
    let mut result = Vec::with_capacity(remaining.len());
    while result.len() < count && !remaining.is_empty() {
        let index = rng.next_int(0, remaining.len());
        result.push(remaining.remove(index));
    }
    result
}

pub struct FragmentChooser {
    degree_chooser: DegreeChooser,
    indexes: Vec<usize>,
    checksum: u32,
}

impl FragmentChooser {
    pub fn new(seq_len: usize, checksum: u32) -> Self {
        Self {
            degree_chooser: DegreeChooser::new(seq_len),
            indexes: (0..seq_len).collect(),
            checksum,
        }
    }

    pub fn choose_fragments(&self, seq_num: u32) -> BTreeSet<usize> {
        if seq_num == 0 {
            return BTreeSet::new();
        }
        if (seq_num as usize) <= self.indexes.len() {
            let mut single = BTreeSet::new();
            single.insert((seq_num as usize) - 1);
            return single;
        }

        let mut seed = [0u8; 8];
        seed[..4].copy_from_slice(&seq_num.to_be_bytes());
        seed[4..].copy_from_slice(&self.checksum.to_be_bytes());
        let mut rng = Xoshiro256::from_seed(&seed);
        let degree = self.degree_chooser.choose_degree(&mut rng);
        shuffled(&self.indexes, &mut rng, degree).into_iter().collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ur::crc32::crc32;
    use crate::ur::fountain::FountainEncoder;

    #[test]
    fn test_xoshiro_vectors() {
        let mut rng1 = Xoshiro256::from_seed(b"Wolf");
        let numbers1: Vec<u64> = (0..20).map(|_| rng1.next_u64() % 100).collect();
        assert_eq!(numbers1, vec![42, 81, 85, 8, 82, 84, 76, 73, 70, 88, 2, 74, 40, 48, 77, 54, 88, 7, 5, 88]);

        let mut rng2 = Xoshiro256::from_crc32(crc32(b"Wolf"));
        let numbers2: Vec<u64> = (0..20).map(|_| rng2.next_u64() % 100).collect();
        assert_eq!(numbers2, vec![88, 44, 94, 74, 0, 99, 7, 77, 68, 35, 47, 78, 19, 21, 50, 15, 42, 36, 91, 11]);

        let mut rng3 = Xoshiro256::from_seed(b"Wolf");
        let numbers3: Vec<usize> = (0..20).map(|_| rng3.next_int(1, 10)).collect();
        assert_eq!(numbers3, vec![6, 5, 8, 4, 10, 5, 7, 10, 4, 9, 10, 9, 7, 7, 1, 1, 2, 9, 9, 2]);
    }

    #[test]
    fn test_shuffle_and_fragment_chooser_vectors() {
        let values: Vec<usize> = (1..=10).collect();
        let mut rng = Xoshiro256::from_seed(b"Wolf");
        assert_eq!(shuffled(&values, &mut rng, 10), vec![6, 4, 9, 3, 10, 5, 7, 8, 1, 2]);

        let mut msg_rng = Xoshiro256::from_seed(b"Wolf");
        let message = msg_rng.next_bytes(1024);
        let checksum = crc32(&message);
        let fragment_len = FountainEncoder::find_nominal_fragment_length(message.len(), 10, 100);
        let fragments = FountainEncoder::partition_message(&message, fragment_len);
        let chooser = FragmentChooser::new(fragments.len(), checksum);

        let chosen_12: Vec<usize> = chooser.choose_fragments(12).into_iter().collect();
        let chosen_13: Vec<usize> = chooser.choose_fragments(13).into_iter().collect();
        let chosen_14: Vec<usize> = chooser.choose_fragments(14).into_iter().collect();
        let chosen_15: Vec<usize> = chooser.choose_fragments(15).into_iter().collect();
        assert_eq!(chosen_12, vec![9]);
        assert_eq!(chosen_13, vec![2, 5, 6, 8, 9, 10]);
        assert_eq!(chosen_14, vec![8]);
        assert_eq!(chosen_15, vec![1, 5]);
    }
}
