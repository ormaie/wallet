use gem_encoding::encode_hex;

const ICON_SIZE: f64 = 200.0;
const BORDER_WIDTH: f64 = 4.0;
const PADDING_RATIO: f64 = 0.17;
const GRID_DIMENSION: usize = 4;
const MIN_VALID_HASH_LEN: usize = 11;
const HUE_HEX_LEN: usize = 7;
const HUE_DIVISOR: f64 = 0xfff_ffff as f64;
const COLOR_SATURATION: f64 = 0.55;
const GRAYSCALE_SATURATION: f64 = 0.0;
const COLOR_LIGHTNESS_MIN: f64 = 0.38;
const COLOR_LIGHTNESS_MAX: f64 = 0.70;
const GRAYSCALE_LIGHTNESS_MIN: f64 = 0.30;
const GRAYSCALE_LIGHTNESS_MAX: f64 = 0.68;
const BACKGROUND_SATURATION: f64 = 0.50;
const BACKGROUND_LIGHTNESS: f64 = 0.925;
const BORDER_SATURATION: f64 = 0.42;
const BORDER_LIGHTNESS: f64 = 0.78;
const CENTER_SHAPE_COUNT: usize = 14;
const OUTER_SHAPE_COUNT: usize = 4;
const LIGHTNESS_CORRECTORS: [f64; 7] = [0.55, 0.5, 0.5, 0.46, 0.6, 0.55, 0.55];
const SIDE_POSITIONS: [(usize, usize); 8] = [(1, 0), (2, 0), (2, 3), (1, 3), (0, 1), (3, 1), (3, 2), (0, 2)];
const CORNER_POSITIONS: [(usize, usize); 4] = [(0, 0), (3, 0), (3, 3), (0, 3)];
const CENTER_POSITIONS: [(usize, usize); 4] = [(1, 1), (2, 1), (2, 2), (1, 2)];

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct GemJdenticonColor {
    pub red: u8,
    pub green: u8,
    pub blue: u8,
}

impl GemJdenticonColor {
    pub const fn new(red: u8, green: u8, blue: u8) -> Self {
        Self { red, green, blue }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct GemJdenticonPoint {
    pub x: f64,
    pub y: f64,
}

impl GemJdenticonPoint {
    pub const fn new(x: f64, y: f64) -> Self {
        Self { x, y }
    }
}

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum GemJdenticonSubpath {
    Polygon { points: Vec<GemJdenticonPoint> },
    Oval { x: f64, y: f64, diameter: f64 },
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GemJdenticonLayer {
    pub color: GemJdenticonColor,
    pub subpaths: Vec<GemJdenticonSubpath>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GemJdenticon {
    pub size: f64,
    pub border_width: f64,
    pub background: GemJdenticonColor,
    pub border: GemJdenticonColor,
    pub layers: Vec<GemJdenticonLayer>,
}

pub(crate) fn generate(value: &str) -> GemJdenticon {
    let hash = compute_hash(value);
    let hue_start = hash.len().saturating_sub(HUE_HEX_LEN);
    let hue = parse_hex(&hash, hue_start, HUE_HEX_LEN) as f64 / HUE_DIVISOR;
    let background = corrected_hsl(hue, BACKGROUND_SATURATION, BACKGROUND_LIGHTNESS);
    let border = corrected_hsl(hue, BORDER_SATURATION, BORDER_LIGHTNESS);
    let palette = color_theme(hue);
    let selected = selected_color_indices(&hash, palette.len());

    let padding = (ICON_SIZE * PADDING_RATIO).trunc();
    let inner_size = ICON_SIZE - padding * 2.0;
    let cell = (inner_size / GRID_DIMENSION as f64).trunc();
    let origin = padding + ((inner_size - cell * GRID_DIMENSION as f64) / 2.0).trunc();

    let mut layers: Vec<GemJdenticonLayer> = Vec::with_capacity(3);
    append_group(&mut layers, palette[selected[0]], render_group(&hash, ShapeKind::Outer, 2, Some(3), &SIDE_POSITIONS, origin, cell));
    append_group(&mut layers, palette[selected[1]], render_group(&hash, ShapeKind::Outer, 4, Some(5), &CORNER_POSITIONS, origin, cell));
    append_group(&mut layers, palette[selected[2]], render_group(&hash, ShapeKind::Center, 1, None, &CENTER_POSITIONS, origin, cell));

    GemJdenticon {
        size: ICON_SIZE,
        border_width: BORDER_WIDTH,
        background,
        border,
        layers,
    }
}

pub(super) fn append_group(layers: &mut Vec<GemJdenticonLayer>, color: GemJdenticonColor, subpaths: Vec<GemJdenticonSubpath>) {
    if subpaths.is_empty() {
        return;
    }
    if let Some(existing) = layers.iter_mut().find(|layer| layer.color == color) {
        existing.subpaths.extend(subpaths);
    } else {
        layers.push(GemJdenticonLayer { color, subpaths });
    }
}

#[derive(Clone, Copy)]
enum ShapeKind {
    Outer,
    Center,
}

fn render_group(hash: &str, kind: ShapeKind, index_position: usize, rotation_position: Option<usize>, positions: &[(usize, usize)], origin: f64, cell: f64) -> Vec<GemJdenticonSubpath> {
    let rotation = rotation_position.map_or(0, |position| parse_hex(hash, position, 1));
    let shape_count = match kind {
        ShapeKind::Outer => OUTER_SHAPE_COUNT,
        ShapeKind::Center => CENTER_SHAPE_COUNT,
    };
    let shape_index = parse_hex(hash, index_position, 1) % shape_count;
    let mut graphics = Graphics::default();

    for (cell_index, &(column, row)) in positions.iter().enumerate() {
        graphics.transform = Transform {
            x: origin + column as f64 * cell,
            y: origin + row as f64 * cell,
            size: cell,
            rotation: (rotation + cell_index) % 4,
        };
        match kind {
            ShapeKind::Outer => render_outer_shape(shape_index, &mut graphics, cell),
            ShapeKind::Center => render_center_shape(shape_index, &mut graphics, cell, cell_index),
        }
    }

    graphics.subpaths
}

fn selected_color_indices(hash: &str, palette_len: usize) -> [usize; 3] {
    let mut selected = [usize::MAX; 3];
    for (slot, offset) in (0..3).zip(8..11) {
        let mut candidate = parse_hex(hash, offset, 1) % palette_len;
        let selected_prefix = &selected[..slot];
        if is_conflicting_pair(candidate, selected_prefix, &[0, 4]) || is_conflicting_pair(candidate, selected_prefix, &[2, 3]) {
            candidate = 1;
        }
        selected[slot] = candidate;
    }
    selected
}

fn is_conflicting_pair(candidate: usize, chosen: &[usize], pair: &[usize]) -> bool {
    pair.contains(&candidate) && pair.iter().any(|value| chosen.contains(value))
}

fn color_theme(hue: f64) -> [GemJdenticonColor; 5] {
    let color_lightness = |factor: f64| COLOR_LIGHTNESS_MIN + factor * (COLOR_LIGHTNESS_MAX - COLOR_LIGHTNESS_MIN);
    let gray_lightness = |factor: f64| GRAYSCALE_LIGHTNESS_MIN + factor * (GRAYSCALE_LIGHTNESS_MAX - GRAYSCALE_LIGHTNESS_MIN);
    [
        corrected_hsl(hue, GRAYSCALE_SATURATION, gray_lightness(0.0)),
        corrected_hsl(hue, COLOR_SATURATION, color_lightness(0.5)),
        corrected_hsl(hue, GRAYSCALE_SATURATION, gray_lightness(1.0)),
        corrected_hsl(hue, COLOR_SATURATION, color_lightness(1.0)),
        corrected_hsl(hue, COLOR_SATURATION, color_lightness(0.0)),
    ]
}

fn corrected_hsl(hue: f64, saturation: f64, lightness: f64) -> GemJdenticonColor {
    let corrector_index = (hue * 6.0 + 0.5) as usize;
    let corrector = LIGHTNESS_CORRECTORS.get(corrector_index).copied().unwrap_or(0.55);
    let adjusted = if lightness < 0.5 { lightness * corrector * 2.0 } else { corrector + (lightness - 0.5) * (1.0 - corrector) * 2.0 };
    hsl_color(hue, saturation, adjusted)
}

fn hsl_color(hue: f64, saturation: f64, lightness: f64) -> GemJdenticonColor {
    if saturation == 0.0 {
        let channel = clamp_channel(lightness * 255.0);
        return GemJdenticonColor::new(channel, channel, channel);
    }
    let m2 = if lightness <= 0.5 { lightness * (saturation + 1.0) } else { lightness + saturation - lightness * saturation };
    let m1 = lightness * 2.0 - m2;
    GemJdenticonColor::new(
        hue_to_channel(m1, m2, hue * 6.0 + 2.0),
        hue_to_channel(m1, m2, hue * 6.0),
        hue_to_channel(m1, m2, hue * 6.0 - 2.0),
    )
}

fn hue_to_channel(m1: f64, m2: f64, hue: f64) -> u8 {
    let wrapped = if hue < 0.0 {
        hue + 6.0
    } else if hue > 6.0 {
        hue - 6.0
    } else {
        hue
    };
    let channel = if wrapped < 1.0 {
        m1 + (m2 - m1) * wrapped
    } else if wrapped < 3.0 {
        m2
    } else if wrapped < 4.0 {
        m1 + (m2 - m1) * (4.0 - wrapped)
    } else {
        m1
    };
    clamp_channel(255.0 * channel)
}

fn clamp_channel(value: f64) -> u8 {
    (value as i32).clamp(0, 255) as u8
}

fn parse_hex(hash: &str, start: usize, len: usize) -> usize {
    hash.get(start..start + len).and_then(|slice| usize::from_str_radix(slice, 16).ok()).unwrap_or(0)
}

fn compute_hash(value: &str) -> String {
    if value.len() >= MIN_VALID_HASH_LEN && value.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return value.to_ascii_lowercase();
    }
    sha1_hex(value.as_bytes())
}

fn sha1_hex(data: &[u8]) -> String {
    let mut h0: u32 = 0x6745_2301;
    let mut h1: u32 = 0xefcd_ab89;
    let mut h2: u32 = 0x98ba_dcfe;
    let mut h3: u32 = 0x1032_5476;
    let mut h4: u32 = 0xc3d2_e1f0;

    let bit_len = (data.len() as u64).wrapping_mul(8);
    let pad_zeros = (55usize.wrapping_sub(data.len())) % 64;
    let mut padded = Vec::with_capacity(data.len() + 1 + pad_zeros + 8);
    padded.extend_from_slice(data);
    padded.push(0x80);
    padded.resize(padded.len() + pad_zeros, 0);
    padded.extend_from_slice(&bit_len.to_be_bytes());

    for chunk in padded.as_chunks::<64>().0 {
        let mut words = [0u32; 80];
        for (slot, word_bytes) in words.iter_mut().take(16).zip(chunk.as_chunks::<4>().0) {
            *slot = u32::from_be_bytes(*word_bytes);
        }
        for index in 16..80 {
            words[index] = (words[index - 3] ^ words[index - 8] ^ words[index - 14] ^ words[index - 16]).rotate_left(1);
        }

        let mut a = h0;
        let mut b = h1;
        let mut c = h2;
        let mut d = h3;
        let mut e = h4;

        for (index, &word) in words.iter().enumerate() {
            let (f, k) = match index {
                0..=19 => ((b & c) | ((!b) & d), 0x5a82_7999),
                20..=39 => (b ^ c ^ d, 0x6ed9_eba1),
                40..=59 => ((b & c) | (b & d) | (c & d), 0x8f1b_bcdc),
                _ => (b ^ c ^ d, 0xca62_c1d6),
            };
            let next = a.rotate_left(5).wrapping_add(f).wrapping_add(e).wrapping_add(k).wrapping_add(word);
            e = d;
            d = c;
            c = b.rotate_left(30);
            b = a;
            a = next;
        }

        h0 = h0.wrapping_add(a);
        h1 = h1.wrapping_add(b);
        h2 = h2.wrapping_add(c);
        h3 = h3.wrapping_add(d);
        h4 = h4.wrapping_add(e);
    }

    let mut digest = [0u8; 20];
    for (slot, word) in digest.as_chunks_mut::<4>().0.iter_mut().zip([h0, h1, h2, h3, h4]) {
        *slot = word.to_be_bytes();
    }
    encode_hex(digest)
}

#[derive(Default, Clone, Copy)]
struct Transform {
    x: f64,
    y: f64,
    size: f64,
    rotation: usize,
}

impl Transform {
    fn point(self, x: f64, y: f64, width: f64, height: f64) -> GemJdenticonPoint {
        let right = self.x + self.size;
        let bottom = self.y + self.size;
        match self.rotation {
            1 => GemJdenticonPoint::new(right - y - height, self.y + x),
            2 => GemJdenticonPoint::new(right - x - width, bottom - y - height),
            3 => GemJdenticonPoint::new(self.x + y, bottom - x - width),
            _ => GemJdenticonPoint::new(self.x + x, self.y + y),
        }
    }
}

#[derive(Default)]
struct Graphics {
    subpaths: Vec<GemJdenticonSubpath>,
    transform: Transform,
}

impl Graphics {
    fn add_polygon(&mut self, coordinates: &[(f64, f64)], invert: bool) {
        let points = if invert {
            coordinates.iter().rev().map(|&(x, y)| self.transform.point(x, y, 0.0, 0.0)).collect()
        } else {
            coordinates.iter().map(|&(x, y)| self.transform.point(x, y, 0.0, 0.0)).collect()
        };
        self.subpaths.push(GemJdenticonSubpath::Polygon { points });
    }

    fn add_circle(&mut self, x: f64, y: f64, diameter: f64) {
        let origin = self.transform.point(x, y, diameter, diameter);
        self.subpaths.push(GemJdenticonSubpath::Oval { x: origin.x, y: origin.y, diameter });
    }

    fn add_rectangle(&mut self, x: f64, y: f64, width: f64, height: f64, invert: bool) {
        self.add_polygon(&[(x, y), (x + width, y), (x + width, y + height), (x, y + height)], invert);
    }

    fn add_triangle(&mut self, x: f64, y: f64, width: f64, height: f64, rotation: usize, invert: bool) {
        let skipped = rotation % 4;
        let corners = [(x + width, y), (x + width, y + height), (x, y + height), (x, y)];
        let points: Vec<(f64, f64)> = corners.into_iter().enumerate().filter_map(|(index, point)| (index != skipped).then_some(point)).collect();
        self.add_polygon(&points, invert);
    }

    fn add_rhombus(&mut self, x: f64, y: f64, width: f64, height: f64, invert: bool) {
        self.add_polygon(&[(x + width / 2.0, y), (x + width, y + height / 2.0), (x + width / 2.0, y + height), (x, y + height / 2.0)], invert);
    }
}

fn render_outer_shape(shape_index: usize, graphics: &mut Graphics, cell: f64) {
    match shape_index {
        0 => graphics.add_triangle(0.0, 0.0, cell, cell, 0, false),
        1 => graphics.add_triangle(0.0, cell / 2.0, cell, cell / 2.0, 0, false),
        2 => graphics.add_rhombus(0.0, 0.0, cell, cell, false),
        _ => {
            let margin = cell / 6.0;
            graphics.add_circle(margin, margin, cell - 2.0 * margin);
        }
    }
}

fn render_center_shape(shape_index: usize, graphics: &mut Graphics, cell: f64, cell_index: usize) {
    match shape_index {
        0 => {
            let cut = cell * 0.42;
            graphics.add_polygon(&[(0.0, 0.0), (cell, 0.0), (cell, cell - cut * 2.0), (cell - cut, cell), (0.0, cell)], false);
        }
        1 => {
            let width = (cell * 0.5).trunc();
            let height = (cell * 0.8).trunc();
            graphics.add_triangle(cell - width, 0.0, width, height, 2, false);
        }
        2 => {
            let inset = (cell / 3.0).trunc();
            graphics.add_rectangle(inset, inset, cell - inset, cell - inset, false);
        }
        3 => {
            let raw_inner = cell * 0.1;
            let inner = if raw_inner > 1.0 {
                raw_inner.trunc()
            } else if raw_inner > 0.5 {
                1.0
            } else {
                raw_inner
            };
            let outer = if cell < 6.0 {
                1.0
            } else if cell < 8.0 {
                2.0
            } else {
                (cell * 0.25).trunc()
            };
            graphics.add_rectangle(outer, outer, cell - inner - outer, cell - inner - outer, false);
        }
        4 => {
            let margin = (cell * 0.15).trunc();
            let diameter = (cell * 0.5).trunc();
            graphics.add_circle(cell - diameter - margin, cell - diameter - margin, diameter);
        }
        5 => {
            let inner = cell * 0.1;
            let outer = inner * 4.0;
            graphics.add_rectangle(0.0, 0.0, cell, cell, false);
            graphics.add_polygon(&[(outer, outer), (cell - inner, outer), (outer + (cell - outer - inner) / 2.0, cell - inner)], true);
        }
        6 => {
            graphics.add_polygon(&[(0.0, 0.0), (cell, 0.0), (cell, cell * 0.7), (cell * 0.4, cell * 0.4), (cell * 0.7, cell), (0.0, cell)], false);
        }
        7 | 11 => {
            let half = cell / 2.0;
            graphics.add_triangle(half, half, half, half, 3, false);
        }
        8 => {
            let half = cell / 2.0;
            graphics.add_rectangle(0.0, 0.0, cell, half, false);
            graphics.add_rectangle(0.0, half, half, half, false);
            graphics.add_triangle(half, half, half, half, 1, false);
        }
        9 => {
            let raw_inner = cell * 0.14;
            let inner = if cell < 8.0 { raw_inner } else { raw_inner.trunc() };
            let outer = if cell < 4.0 {
                1.0
            } else if cell < 6.0 {
                2.0
            } else {
                (cell * 0.35).trunc()
            };
            let span = cell - outer - inner;
            graphics.add_rectangle(0.0, 0.0, cell, cell, false);
            graphics.add_rectangle(outer, outer, span, span, true);
        }
        10 => {
            let inner = cell * 0.12;
            let outer = inner * 3.0;
            graphics.add_rectangle(0.0, 0.0, cell, cell, false);
            graphics.add_circle(outer, outer, cell - inner - outer);
        }
        12 => {
            let margin = cell * 0.25;
            let span = cell - margin;
            graphics.add_rectangle(0.0, 0.0, cell, cell, false);
            graphics.add_rhombus(margin, margin, span, span, true);
        }
        _ => {
            if cell_index == 0 {
                graphics.add_circle(cell * 0.4, cell * 0.4, cell * 1.2);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sha1_hex_matches_standard_vectors_across_padding_boundaries() {
        assert_eq!(sha1_hex(b""), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assert_eq!(sha1_hex(b"abc"), "a9993e364706816aba3e25717850c26c9cd0d89d");
        assert_eq!(sha1_hex(&[b'x'; 55]), "cef734ba81a024479e09eb5a75b6ddae62e6abf1");
        assert_eq!(sha1_hex(b"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"), "84983e441c3bd26ebaae4aa1f95129e5e54670f1");
        assert_eq!(sha1_hex(&[b'x'; 64]), "bb2fa3ee7afb9f54c6dfb5d021f14b1ffe40c163");
    }

    #[test]
    fn test_generate_renders_ethereum_address_with_border_and_high_contrast_palette() {
        let icon = generate("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045");

        assert_eq!(icon.size, 200.0);
        assert_eq!(icon.border_width, 4.0);
        assert_eq!(icon.background, GemJdenticonColor::new(224, 244, 241));
        assert_eq!(icon.border, GemJdenticonColor::new(168, 219, 212));
        assert_eq!(icon.layers.len(), 2);
        assert_eq!(icon.layers[0].color, GemJdenticonColor::new(40, 138, 123));
        assert_eq!(icon.layers[0].subpaths.len(), 8);
        assert_eq!(icon.layers[1].color, GemJdenticonColor::new(58, 197, 177));
        assert_eq!(icon.layers[1].subpaths.len(), 5);
        assert_eq!(icon.layers[1].subpaths[4], GemJdenticonSubpath::Oval { x: 80.2, y: 80.2, diameter: 39.6 });
    }

    #[test]
    fn test_generate_accepts_hex_hashes_case_insensitively() {
        let lower = generate("abcdef0123456789");
        let upper = generate("ABCDEF0123456789");

        assert_eq!(lower, upper);
        assert_eq!(
            lower.layers.iter().map(|layer| layer.color).collect::<Vec<_>>(),
            vec![
                GemJdenticonColor::new(173, 173, 173),
                GemJdenticonColor::new(172, 202, 73),
                GemJdenticonColor::new(126, 150, 43),
            ]
        );
    }

    #[test]
    fn test_generate_deduplicates_conflicting_palette_slots_and_emits_center_holes() {
        let icon = generate("0a0000000000");

        assert_eq!(icon.layers.len(), 2);
        assert_eq!(icon.layers[0].color, GemJdenticonColor::new(84, 84, 84));
        assert_eq!(icon.layers[1].color, GemJdenticonColor::new(207, 91, 91));
        assert_eq!(icon.layers[1].subpaths.len(), 12);
    }
}
