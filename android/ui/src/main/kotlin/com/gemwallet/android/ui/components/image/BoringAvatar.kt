package com.gemwallet.android.ui.components.image

import androidx.compose.runtime.Immutable
import kotlin.math.abs

enum class BoringAvatarVariant(val label: String) {
    Bauhaus("Bauhaus"),
    Ring("Ring"),
    Beam("Beam"),
    Sunset("Sunset"),
    Marble("Marble"),
}

enum class BoringAvatarPalette(
    val primaryDot: Int,
    val secondaryDot: Int,
    val accentColor: Int,
    private val hexColors: IntArray,
) {
    Cobalt(
        primaryDot = 0xFF2D5BE6.toInt(),
        secondaryDot = 0xFF38BDF8.toInt(),
        accentColor = 0xFF2D5BE6.toInt(),
        hexColors = intArrayOf(0xFF2D5BE6.toInt(), 0xFF1E293B.toInt(), 0xFF38BDF8.toInt(), 0xFFFACC15.toInt(), 0xFFF8FAFC.toInt()),
    ),
    Bauhaus(
        primaryDot = 0xFF1D4ED8.toInt(),
        secondaryDot = 0xFFE11D48.toInt(),
        accentColor = 0xFFE11D48.toInt(),
        hexColors = intArrayOf(0xFF1D4ED8.toInt(), 0xFFE11D48.toInt(), 0xFFF59E0B.toInt(), 0xFF111827.toInt(), 0xFFFAF5E8.toInt()),
    ),
    Glacier(
        primaryDot = 0xFF0284C7.toInt(),
        secondaryDot = 0xFF2DD4BF.toInt(),
        accentColor = 0xFF0284C7.toInt(),
        hexColors = intArrayOf(0xFF0F172A.toInt(), 0xFF0284C7.toInt(), 0xFF2DD4BF.toInt(), 0xFFA5F3FC.toInt(), 0xFFF1F5F9.toInt()),
    ),
    Morandi(
        primaryDot = 0xFF4A6FA5.toInt(),
        secondaryDot = 0xFFE07A5F.toInt(),
        accentColor = 0xFF4A6FA5.toInt(),
        hexColors = intArrayOf(0xFF4A6FA5.toInt(), 0xFFE07A5F.toInt(), 0xFFF2CC8F.toInt(), 0xFF81B29A.toInt(), 0xFFF4F1DE.toInt()),
    ),
    Coral(
        primaryDot = 0xFFF43F5E.toInt(),
        secondaryDot = 0xFFFB923C.toInt(),
        accentColor = 0xFFF43F5E.toInt(),
        hexColors = intArrayOf(0xFF1E1B4B.toInt(), 0xFFF43F5E.toInt(), 0xFFFB923C.toInt(), 0xFFFDE047.toInt(), 0xFFFFF1F2.toInt()),
    ),
    Obsidian(
        primaryDot = 0xFF0F172A.toInt(),
        secondaryDot = 0xFFF59E0B.toInt(),
        accentColor = 0xFFD97706.toInt(),
        hexColors = intArrayOf(0xFF0F172A.toInt(), 0xFF334155.toInt(), 0xFFD97706.toInt(), 0xFFFCD34D.toInt(), 0xFFF8FAFC.toInt()),
    ),
    Cyber(
        primaryDot = 0xFF7C3AED.toInt(),
        secondaryDot = 0xFFEC4899.toInt(),
        accentColor = 0xFF7C3AED.toInt(),
        hexColors = intArrayOf(0xFF1E1B4B.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt(), 0xFF38BDF8.toInt(), 0xFFF5F3FF.toInt()),
    ),
    Emerald(
        primaryDot = 0xFF059669.toInt(),
        secondaryDot = 0xFFFDE047.toInt(),
        accentColor = 0xFF10B981.toInt(),
        hexColors = intArrayOf(0xFF064E3B.toInt(), 0xFF10B981.toInt(), 0xFF6EE7B7.toInt(), 0xFFFDE047.toInt(), 0xFFECFDF5.toInt()),
    ),
    ;

    val colorCount: Int
        get() = hexColors.size

    fun colorAt(index: Int): Int = hexColors[index]
}

enum class BoringPaletteMode {
    Mix,
    Cobalt,
    Bauhaus,
    Glacier,
    Morandi,
    Coral,
    Obsidian,
    Cyber,
    Emerald,
    ;

    fun paletteFor(seed: String, variantIndex: Int): BoringAvatarPalette = when (this) {
        Mix -> {
            val palettes = BoringAvatarPalette.entries
            val offset = Math.floorMod(boringHashCode(seed), palettes.size)
            palettes[(offset + variantIndex) % palettes.size]
        }

        Cobalt -> BoringAvatarPalette.Cobalt
        Bauhaus -> BoringAvatarPalette.Bauhaus
        Glacier -> BoringAvatarPalette.Glacier
        Morandi -> BoringAvatarPalette.Morandi
        Coral -> BoringAvatarPalette.Coral
        Obsidian -> BoringAvatarPalette.Obsidian
        Cyber -> BoringAvatarPalette.Cyber
        Emerald -> BoringAvatarPalette.Emerald
    }

    fun fixedPaletteOrNull(): BoringAvatarPalette? = when (this) {
        Mix -> null
        Cobalt -> BoringAvatarPalette.Cobalt
        Bauhaus -> BoringAvatarPalette.Bauhaus
        Glacier -> BoringAvatarPalette.Glacier
        Morandi -> BoringAvatarPalette.Morandi
        Coral -> BoringAvatarPalette.Coral
        Obsidian -> BoringAvatarPalette.Obsidian
        Cyber -> BoringAvatarPalette.Cyber
        Emerald -> BoringAvatarPalette.Emerald
    }

    companion object {
        fun fromPalette(palette: BoringAvatarPalette): BoringPaletteMode = when (palette) {
            BoringAvatarPalette.Cobalt -> Cobalt
            BoringAvatarPalette.Bauhaus -> Bauhaus
            BoringAvatarPalette.Glacier -> Glacier
            BoringAvatarPalette.Morandi -> Morandi
            BoringAvatarPalette.Coral -> Coral
            BoringAvatarPalette.Obsidian -> Obsidian
            BoringAvatarPalette.Cyber -> Cyber
            BoringAvatarPalette.Emerald -> Emerald
        }
    }
}

@Immutable
data class BoringAvatar(
    val seed: String,
    val variant: BoringAvatarVariant,
    val palette: BoringAvatarPalette,
    val paletteMode: BoringPaletteMode = BoringPaletteMode.fromPalette(palette),
)

fun boringHashCode(seed: String): Int {
    var hash = 0
    for (i in seed.indices) {
        hash = ((hash shl 5) - hash) + seed[i].code
    }
    return if (hash == Int.MIN_VALUE) 0 else abs(hash)
}
