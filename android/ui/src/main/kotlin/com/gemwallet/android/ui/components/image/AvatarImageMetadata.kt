package com.gemwallet.android.ui.components.image

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.runtime.Immutable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import kotlin.math.max

private const val METADATA_KEYWORD = "GemAvatar"
private const val PNG_HEADER_LENGTH = 8
private const val CHUNK_HEADER_LENGTH = 8
private const val CHUNK_CRC_LENGTH = 4
private const val CHUNK_OVERHEAD_LENGTH = CHUNK_HEADER_LENGTH + CHUNK_CRC_LENGTH
private const val DOMINANT_SAMPLE_TARGET_PX = 48
private const val OPAQUE_ALPHA_THRESHOLD = 200
private const val HUE_BINS = 24
private const val HUE_BIN_DEGREES = 15f
private const val MIN_CHROMA_SATURATION = 0.18f
private const val MIN_CHROMA_VALUE = 0.18f
private const val MAX_CHROMA_VALUE = 0.96f
private const val CLAMP_MIN_SATURATION = 0.45f
private const val CLAMP_MAX_SATURATION = 0.85f
private const val CLAMP_MIN_VALUE = 0.45f
private const val CLAMP_MAX_VALUE = 0.88f
private const val PREFIX_PRESET = "preset:"
private const val PREFIX_BORING = "boring:"

private val pngSignature = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)
private val chunkTypeText = byteArrayOf('t'.code.toByte(), 'E'.code.toByte(), 'X'.code.toByte(), 't'.code.toByte())
private val chunkTypeEnd = byteArrayOf('I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte())

@Immutable
sealed interface StoredAvatarSelection {
    data class Preset(val preset: AvatarPreset) : StoredAvatarSelection
    data class Boring(
        val variant: BoringAvatarVariant,
        val palette: BoringAvatarPalette,
        val paletteMode: BoringPaletteMode,
    ) : StoredAvatarSelection
}

@Immutable
data class StoredAvatarInfo(
    val selection: StoredAvatarSelection?,
    val dominantColor: Int?,
)

internal fun ByteArray.withAvatarSelectionMetadata(selection: StoredAvatarSelection): ByteArray {
    if (!hasPngSignature(this)) return this
    val iendOffset = findChunkOffset(this, chunkTypeEnd) ?: return this
    val payload = selection.encode()
    val data = "$METADATA_KEYWORD\u0000$payload".toByteArray(Charsets.ISO_8859_1)
    val crc = CRC32().apply {
        update(chunkTypeText)
        update(data)
    }.value.toInt()

    val out = ByteArrayOutputStream(size + data.size + CHUNK_OVERHEAD_LENGTH)
    out.write(this, 0, iendOffset)
    out.writeIntBe(data.size)
    out.write(chunkTypeText)
    out.write(data)
    out.writeIntBe(crc)
    out.write(this, iendOffset, size - iendOffset)
    return out.toByteArray()
}

internal fun readAvatarSelectionMetadata(bytes: ByteArray): StoredAvatarSelection? {
    if (!hasPngSignature(bytes)) return null
    val keywordPrefix = "$METADATA_KEYWORD\u0000".toByteArray(Charsets.ISO_8859_1)
    var offset = PNG_HEADER_LENGTH
    while (offset + CHUNK_OVERHEAD_LENGTH <= bytes.size) {
        val length = readIntBe(bytes, offset)
        if (length < 0) return null
        val dataStart = offset + CHUNK_HEADER_LENGTH
        val nextOffset = dataStart.toLong() + length.toLong() + CHUNK_CRC_LENGTH.toLong()
        if (nextOffset > bytes.size.toLong()) return null

        if (matchesBytes(bytes, offset + 4, chunkTypeText) &&
            length > keywordPrefix.size &&
            matchesBytes(bytes, dataStart, keywordPrefix)
        ) {
            val payload = String(
                bytes,
                dataStart + keywordPrefix.size,
                length - keywordPrefix.size,
                Charsets.ISO_8859_1,
            )
            return decodeAvatarSelection(payload)
        }
        if (matchesBytes(bytes, offset + 4, chunkTypeEnd)) return null
        offset = nextOffset.toInt()
    }
    return null
}

internal fun extractAvatarDominantColor(bytes: ByteArray): Int? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val maxSide = max(bounds.outWidth, bounds.outHeight)
    while (maxSide / (sampleSize * 2) >= DOMINANT_SAMPLE_TARGET_PX) {
        sampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val weightSum = FloatArray(HUE_BINS)
        val rSum = FloatArray(HUE_BINS)
        val gSum = FloatArray(HUE_BINS)
        val bSum = FloatArray(HUE_BINS)
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            if (Color.alpha(pixel) < OPAQUE_ALPHA_THRESHOLD) continue
            Color.colorToHSV(pixel, hsv)
            val sat = hsv[1]
            val value = hsv[2]
            if (sat < MIN_CHROMA_SATURATION || value < MIN_CHROMA_VALUE || value > MAX_CHROMA_VALUE) continue

            val bin = (hsv[0] / HUE_BIN_DEGREES).toInt().coerceIn(0, HUE_BINS - 1)
            val weight = sat * sat * (0.35f + 0.65f * value)
            weightSum[bin] += weight
            rSum[bin] += Color.red(pixel) * weight
            gSum[bin] += Color.green(pixel) * weight
            bSum[bin] += Color.blue(pixel) * weight
        }

        var bestBin = -1
        var bestWeight = 0f
        for (i in 0 until HUE_BINS) {
            if (weightSum[i] > bestWeight) {
                bestWeight = weightSum[i]
                bestBin = i
            }
        }
        if (bestBin < 0 || bestWeight <= 0f) return null

        val avgColor = Color.rgb(
            (rSum[bestBin] / bestWeight).toInt().coerceIn(0, 255),
            (gSum[bestBin] / bestWeight).toInt().coerceIn(0, 255),
            (bSum[bestBin] / bestWeight).toInt().coerceIn(0, 255),
        )
        Color.colorToHSV(avgColor, hsv)
        hsv[1] = hsv[1].coerceIn(CLAMP_MIN_SATURATION, CLAMP_MAX_SATURATION)
        hsv[2] = hsv[2].coerceIn(CLAMP_MIN_VALUE, CLAMP_MAX_VALUE)
        Color.HSVToColor(hsv)
    } finally {
        bitmap.recycle()
    }
}

fun inspectStoredAvatar(context: Context, imageName: String?): StoredAvatarInfo? {
    val name = imageName?.takeIf { it.isNotBlank() } ?: return null
    return inspectStoredAvatar(File(context.filesDir, name))
}

fun inspectStoredAvatar(file: File): StoredAvatarInfo {
    if (!file.isFile) return StoredAvatarInfo(selection = null, dominantColor = null)
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return StoredAvatarInfo(selection = null, dominantColor = null)
    val selection = readAvatarSelectionMetadata(bytes)
    val dominantColor = when (selection) {
        is StoredAvatarSelection.Boring -> selection.palette.accentColor
        is StoredAvatarSelection.Preset -> selection.preset.accentColor
        null -> extractAvatarDominantColor(bytes)
    }
    return StoredAvatarInfo(selection = selection, dominantColor = dominantColor)
}

private fun StoredAvatarSelection.encode(): String = when (this) {
    is StoredAvatarSelection.Preset -> "$PREFIX_PRESET${preset.name}"
    is StoredAvatarSelection.Boring -> "$PREFIX_BORING${variant.name}:${palette.name}:${paletteMode.name}"
}

private fun decodeAvatarSelection(payload: String): StoredAvatarSelection? = when {
    payload.startsWith(PREFIX_PRESET) -> {
        val name = payload.removePrefix(PREFIX_PRESET)
        AvatarPreset.entries.firstOrNull { it.name == name }?.let(StoredAvatarSelection::Preset)
    }

    payload.startsWith(PREFIX_BORING) -> {
        val parts = payload.removePrefix(PREFIX_BORING).split(":")
        val variant = parts.getOrNull(0)?.let { name -> BoringAvatarVariant.entries.firstOrNull { it.name == name } }
        val palette = parts.getOrNull(1)?.let { name -> BoringAvatarPalette.entries.firstOrNull { it.name == name } }
        if (variant != null && palette != null) {
            val mode = parts.getOrNull(2)?.let { name -> BoringPaletteMode.entries.firstOrNull { it.name == name } }
                ?: BoringPaletteMode.fromPalette(palette)
            StoredAvatarSelection.Boring(variant = variant, palette = palette, paletteMode = mode)
        } else {
            null
        }
    }

    else -> null
}

private fun hasPngSignature(bytes: ByteArray): Boolean = bytes.size >= PNG_HEADER_LENGTH && matchesBytes(bytes, 0, pngSignature)

private fun findChunkOffset(bytes: ByteArray, targetType: ByteArray): Int? {
    var offset = PNG_HEADER_LENGTH
    while (offset + CHUNK_OVERHEAD_LENGTH <= bytes.size) {
        val length = readIntBe(bytes, offset)
        if (length < 0) return null
        val nextOffset = offset.toLong() + CHUNK_OVERHEAD_LENGTH.toLong() + length.toLong()
        if (nextOffset > bytes.size.toLong()) return null
        if (matchesBytes(bytes, offset + 4, targetType)) return offset
        offset = nextOffset.toInt()
    }
    return null
}

private fun matchesBytes(source: ByteArray, offset: Int, expected: ByteArray): Boolean {
    if (offset + expected.size > source.size) return false
    for (i in expected.indices) {
        if (source[offset + i] != expected[i]) return false
    }
    return true
}

private fun readIntBe(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 24) or
    ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
    (bytes[offset + 3].toInt() and 0xFF)

private fun ByteArrayOutputStream.writeIntBe(value: Int) {
    write((value ushr 24) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}
