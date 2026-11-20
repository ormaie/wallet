use super::jdenticon::{GemJdenticon, GemJdenticonColor, GemJdenticonLayer, GemJdenticonPoint, GemJdenticonSubpath, append_group};

const ICON_SIZE: f64 = 200.0;
const BORDER_WIDTH: f64 = 3.0;
const GRID_DIMENSION: usize = 8;
const HALF_GRID_DIMENSION: usize = GRID_DIMENSION / 2;
const CELL_SIZE: f64 = ICON_SIZE / GRID_DIMENSION as f64;
const HUE_DEGREES: f64 = 360.0;
const SATURATION_BASE: f64 = 40.0;
const SATURATION_SPAN: f64 = 60.0;
const LIGHTNESS_SCALE: f64 = 25.0;
const PERCENT_DIVISOR: f64 = 100.0;
const PIXEL_DENSITY: f64 = 2.3;
const PRNG_DIVISOR: f64 = (1u32 << 31) as f64;
const HIGH_LIGHTNESS_THRESHOLD: f64 = 0.78;
const LOW_LIGHTNESS_THRESHOLD: f64 = 0.18;
const EXTREME_BORDER_SHIFT: f64 = 0.16;
const DEFAULT_BORDER_SHIFT: f64 = 0.10;
const MIN_BORDER_LIGHTNESS: f64 = 0.08;

pub(crate) fn generate(address: &str) -> GemJdenticon {
    let seed = normalized_seed(address);
    let mut prng = Xorshift::new(&seed);

    let foreground = prng.next_hsl().rgb();
    let background_hsl = prng.next_hsl();
    let background = background_hsl.rgb();
    let border = background_hsl.border_rgb();
    let spot = prng.next_hsl().rgb();

    let grid = build_grid(&mut prng);
    let mut layers: Vec<GemJdenticonLayer> = Vec::with_capacity(2);
    append_group(&mut layers, foreground, build_row_runs(&grid, 1));
    append_group(&mut layers, spot, build_row_runs(&grid, 2));

    GemJdenticon {
        size: ICON_SIZE,
        border_width: BORDER_WIDTH,
        background,
        border,
        layers,
    }
}

fn normalized_seed(address: &str) -> String {
    if address.starts_with("0x") || address.starts_with("0X") {
        address.to_ascii_lowercase()
    } else {
        address.to_string()
    }
}

fn build_grid(prng: &mut Xorshift) -> [[u8; GRID_DIMENSION]; GRID_DIMENSION] {
    let mut grid = [[0u8; GRID_DIMENSION]; GRID_DIMENSION];
    for row in &mut grid {
        let mut left = [0u8; HALF_GRID_DIMENSION];
        for slot in &mut left {
            let raw = (prng.next_f64() * PIXEL_DENSITY).floor() as u8;
            *slot = match raw {
                0 => 0,
                1 => 1,
                _ => 2,
            };
        }
        for index in 0..HALF_GRID_DIMENSION {
            row[index] = left[index];
            row[GRID_DIMENSION - 1 - index] = left[index];
        }
    }
    grid
}

fn build_row_runs(grid: &[[u8; GRID_DIMENSION]; GRID_DIMENSION], target: u8) -> Vec<GemJdenticonSubpath> {
    let mut subpaths = Vec::new();
    for (row_index, row) in grid.iter().enumerate() {
        let mut col = 0;
        while col < GRID_DIMENSION {
            if row[col] == target {
                let start = col;
                while col < GRID_DIMENSION && row[col] == target {
                    col += 1;
                }
                let left = start as f64 * CELL_SIZE;
                let right = col as f64 * CELL_SIZE;
                let top = row_index as f64 * CELL_SIZE;
                let bottom = top + CELL_SIZE;
                subpaths.push(GemJdenticonSubpath::Polygon {
                    points: vec![
                        GemJdenticonPoint::new(left, top),
                        GemJdenticonPoint::new(right, top),
                        GemJdenticonPoint::new(right, bottom),
                        GemJdenticonPoint::new(left, bottom),
                    ],
                });
            } else {
                col += 1;
            }
        }
    }
    subpaths
}

#[derive(Clone, Copy)]
struct HslColor {
    hue_degrees: f64,
    saturation: f64,
    lightness: f64,
}

impl HslColor {
    fn rgb(self) -> GemJdenticonColor {
        hsl_to_rgb(self.hue_degrees, self.saturation, self.lightness)
    }

    fn border_rgb(self) -> GemJdenticonColor {
        let adjusted = if self.lightness >= HIGH_LIGHTNESS_THRESHOLD {
            self.lightness - EXTREME_BORDER_SHIFT
        } else if self.lightness <= LOW_LIGHTNESS_THRESHOLD {
            self.lightness + EXTREME_BORDER_SHIFT
        } else {
            (self.lightness - DEFAULT_BORDER_SHIFT).max(MIN_BORDER_LIGHTNESS)
        };
        hsl_to_rgb(self.hue_degrees, self.saturation, adjusted)
    }
}

fn hsl_to_rgb(hue_degrees: f64, saturation: f64, lightness: f64) -> GemJdenticonColor {
    let hue = hue_degrees.rem_euclid(HUE_DEGREES) / HUE_DEGREES;
    let sat = saturation.clamp(0.0, 1.0);
    let light = lightness.clamp(0.0, 1.0);
    if sat == 0.0 {
        let gray = round_channel(light);
        return GemJdenticonColor::new(gray, gray, gray);
    }
    let q = if light < 0.5 { light * (1.0 + sat) } else { light + sat - light * sat };
    let p = 2.0 * light - q;
    GemJdenticonColor::new(
        hue_to_channel(p, q, hue + 1.0 / 3.0),
        hue_to_channel(p, q, hue),
        hue_to_channel(p, q, hue - 1.0 / 3.0),
    )
}

fn hue_to_channel(p: f64, q: f64, t: f64) -> u8 {
    let wrapped = if t < 0.0 {
        t + 1.0
    } else if t > 1.0 {
        t - 1.0
    } else {
        t
    };
    let channel = if wrapped < 1.0 / 6.0 {
        p + (q - p) * 6.0 * wrapped
    } else if wrapped < 0.5 {
        q
    } else if wrapped < 2.0 / 3.0 {
        p + (q - p) * (2.0 / 3.0 - wrapped) * 6.0
    } else {
        p
    };
    round_channel(channel)
}

fn round_channel(channel: f64) -> u8 {
    ((channel * 255.0).round() as i32).clamp(0, 255) as u8
}

struct Xorshift {
    state: [i32; 4],
}

impl Xorshift {
    fn new(seed: &str) -> Self {
        let mut state = [0i32; 4];
        for (index, code_unit) in seed.encode_utf16().enumerate() {
            let slot = index % 4;
            state[slot] = state[slot].wrapping_shl(5).wrapping_sub(state[slot]).wrapping_add(i32::from(code_unit));
        }
        Self { state }
    }

    fn next_f64(&mut self) -> f64 {
        let t = self.state[0] ^ self.state[0].wrapping_shl(11);
        self.state[0] = self.state[1];
        self.state[1] = self.state[2];
        self.state[2] = self.state[3];
        self.state[3] = self.state[3] ^ (self.state[3] >> 19) ^ t ^ (t >> 8);
        (self.state[3] as u32) as f64 / PRNG_DIVISOR
    }

    fn next_hsl(&mut self) -> HslColor {
        let hue_degrees = (self.next_f64() * HUE_DEGREES).floor();
        let saturation = (self.next_f64() * SATURATION_SPAN + SATURATION_BASE) / PERCENT_DIVISOR;
        let lightness_sum = self.next_f64() + self.next_f64() + self.next_f64() + self.next_f64();
        let lightness = (lightness_sum * LIGHTNESS_SCALE) / PERCENT_DIVISOR;
        HslColor {
            hue_degrees,
            saturation,
            lightness,
        }
    }
}

#[cfg(test)]
mod tests {
    use primitives::{Account, Chain, Wallet, WalletId, WalletSource, WalletType};

    use super::*;
    use crate::services::avatar::jdenticon;
    use crate::services::wallet::model::wallet_row;

    #[test]
    fn test_generate_matches_ethereum_blockies_and_normalizes_evm_case() {
        let checksummed = generate("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045");
        let lowercased = generate("0xd8da6bf26964af9d7eed9e03e53415d37aa96045");

        assert_eq!(checksummed, lowercased);
        assert_eq!(checksummed.size, 200.0);
        assert_eq!(checksummed.border_width, 3.0);
        assert_eq!(checksummed.background, GemJdenticonColor::new(86, 30, 63));
        assert_eq!(checksummed.border, GemJdenticonColor::new(49, 17, 35));
        assert_eq!(checksummed.layers.len(), 2);
        assert_eq!(checksummed.layers[0].color, GemJdenticonColor::new(1, 1, 251));
        assert_eq!(checksummed.layers[0].subpaths.len(), 12);
        assert_eq!(checksummed.layers[1].color, GemJdenticonColor::new(55, 250, 169));
        assert_eq!(checksummed.layers[1].subpaths.len(), 6);
        assert_eq!(
            checksummed.layers[0].subpaths[0],
            GemJdenticonSubpath::Polygon {
                points: vec![
                    GemJdenticonPoint::new(25.0, 0.0),
                    GemJdenticonPoint::new(175.0, 0.0),
                    GemJdenticonPoint::new(175.0, 25.0),
                    GemJdenticonPoint::new(25.0, 25.0),
                ],
            }
        );
    }

    #[test]
    fn test_generate_preserves_case_for_non_hex_addresses() {
        let original = generate("7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV");
        let altered = generate("7ecdhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV");

        assert_ne!(original, altered);
    }

    #[test]
    fn test_wallet_row_identicons_orders_jdenticon_before_blockies_across_wallet_types() {
        let address = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
        let expected = vec![jdenticon::generate(address), generate(address)];
        let wallet_for = |id: WalletId, wallet_type: WalletType| Wallet {
            id,
            external_id: None,
            name: "Wallet".to_string(),
            index: 1,
            wallet_type,
            accounts: Account::mock_chains(&[Chain::Ethereum], address),
            is_pinned: false,
            image_url: None,
            source: WalletSource::Import,
        };

        let multicoin = wallet_row(wallet_for(WalletId::Multicoin(address.to_string()), WalletType::Multicoin));
        let single = wallet_row(wallet_for(WalletId::Single(Chain::Ethereum, address.to_string()), WalletType::Single));
        let private_key = wallet_row(wallet_for(WalletId::PrivateKey(Chain::Ethereum, address.to_string()), WalletType::PrivateKey));
        let view_only = wallet_row(wallet_for(WalletId::View(Chain::Ethereum, address.to_string()), WalletType::View));

        assert_eq!(multicoin.identicons(), expected);
        assert_eq!(single.identicons(), expected);
        assert_eq!(private_key.identicons(), expected);
        assert_eq!(view_only.identicons(), expected);
    }
}
