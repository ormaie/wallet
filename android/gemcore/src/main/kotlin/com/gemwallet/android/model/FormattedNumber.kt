package com.gemwallet.android.model

import android.icu.math.BigDecimal.ROUND_DOWN
import android.icu.math.BigDecimal.ROUND_HALF_EVEN
import android.icu.math.BigDecimal.ROUND_UP
import android.icu.text.CompactDecimalFormat
import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import android.icu.util.Currency
import uniffi.gemstone.GemFormattedNumber
import uniffi.gemstone.GemNumberDisplay
import uniffi.gemstone.GemNumberNotation
import uniffi.gemstone.GemNumberRounding
import uniffi.gemstone.GemNumberUnit
import uniffi.gemstone.GemPrecision
import uniffi.gemstone.GemTransactionRowValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private data class NumberFormatKey(val locale: Locale, val currencyCode: String?, val withSign: Boolean)

private data class PercentFormatKey(val locale: Locale, val showsSign: Boolean)

private data class CompactFormatKey(val locale: Locale, val currencyCode: String?)

private class ThreadFormatCache {
    val numberFormats = HashMap<NumberFormatKey, DecimalFormat>()
    val percentFormats = HashMap<PercentFormatKey, DecimalFormat>()
    val compactFormats = HashMap<CompactFormatKey, CompactDecimalFormat>()
}

private val threadFormatCache = ThreadLocal.withInitial(::ThreadFormatCache)

fun GemFormattedNumber.text(locale: Locale = Locale.getDefault()): String = when (notation) {
    GemNumberNotation.PARENTHESISED -> "(${body(locale)})"
    GemNumberNotation.PLAIN, GemNumberNotation.SIGNED -> body(locale)
}

private val GemFormattedNumber.showsSign: Boolean
    get() = notation == GemNumberNotation.SIGNED

private val GemFormattedNumber.numberRounding: Int
    get() = when (rounding) {
        GemNumberRounding.TO_NEAREST -> ROUND_HALF_EVEN
        GemNumberRounding.TOWARD_ZERO -> ROUND_DOWN
        GemNumberRounding.AWAY_FROM_ZERO -> ROUND_UP
    }

private fun GemFormattedNumber.body(locale: Locale): String = when (val display = display) {
    is GemNumberDisplay.Number -> when (unit) {
        is GemNumberUnit.Percent -> percentText(BigDecimal.valueOf(value), display.precision, showsSign, numberRounding, locale)
        is GemNumberUnit.Currency, is GemNumberUnit.Symbol, GemNumberUnit.Plain, GemNumberUnit.Multiplier -> appendSymbol(numberText(decimalValue, display.precision, locale))
    }

    is GemNumberDisplay.Abbreviated -> appendSymbol(abbreviatedText(BigDecimal.valueOf(value), locale))

    is GemNumberDisplay.BelowThreshold -> appendSymbol(
        "$signText<${numberText(BigDecimal.valueOf(display.threshold), GemPrecision.Fraction(display.places, display.places), locale, withSign = false)}",
    )
}

private val GemFormattedNumber.decimalValue: BigDecimal
    get() = exact?.let { BigDecimal(it).let { magnitude -> if (value < 0) magnitude.negate() else magnitude } } ?: BigDecimal.valueOf(value)

private val GemFormattedNumber.signText: String
    get() = when {
        !showsSign -> ""
        value < 0 -> "-"
        else -> "+"
    }

private val GemFormattedNumber.currencyCode: String?
    get() = (unit as? GemNumberUnit.Currency)?.code

private fun percentText(value: BigDecimal, precision: GemPrecision, showsSign: Boolean, rounding: Int, locale: Locale): String {
    val formatter = threadFormatCache.get()!!.percentFormats.getOrPut(PercentFormatKey(locale, showsSign)) {
        percentFormat(locale, showsSign)
    }
    when (precision) {
        is GemPrecision.Fraction -> {
            formatter.minimumFractionDigits = precision.min
            formatter.maximumFractionDigits = precision.max
        }

        is GemPrecision.Significant -> {
            formatter.minimumFractionDigits = 0
            formatter.maximumFractionDigits = Int.MAX_VALUE
        }
    }
    formatter.roundingMode = rounding
    val amount = value.movePointLeft(2)
    return formatter.format(
        when (precision) {
            is GemPrecision.Fraction -> amount
            is GemPrecision.Significant -> amount.rounded(precision, RoundingMode.valueOf(rounding))
        },
    )
}

private fun GemFormattedNumber.appendSymbol(text: String): String = when (val unit = unit) {
    is GemNumberUnit.Symbol -> "$text ${unit.symbol}"
    GemNumberUnit.Multiplier -> "${text}x"
    is GemNumberUnit.Currency, GemNumberUnit.Percent, GemNumberUnit.Plain -> text
}

private fun GemFormattedNumber.numberText(value: BigDecimal, precision: GemPrecision, locale: Locale, withSign: Boolean = showsSign): String {
    val formatter = numberFormat(locale, withSign)
    formatter.roundingMode = numberRounding
    return formatter.format(value, precision)
}

private fun GemFormattedNumber.abbreviatedText(value: BigDecimal, locale: Locale): String {
    val formatter = threadFormatCache.get()!!.compactFormats.getOrPut(CompactFormatKey(locale, currencyCode)) {
        CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).apply {
            setSignificantDigitsUsed(false)
            minimumFractionDigits = 0
            maximumFractionDigits = 2
            currencyCode?.let { currency = Currency.getInstance(it) }
        }
    }
    formatter.roundingMode = numberRounding
    return when {
        showsSign -> "$signText${formatter.format(value.abs())}"
        else -> formatter.format(value)
    }
}

private fun GemFormattedNumber.numberFormat(locale: Locale, withSign: Boolean): DecimalFormat = threadFormatCache.get()!!.numberFormats.getOrPut(NumberFormatKey(locale, currencyCode, withSign)) {
    ((currencyCode?.let { code -> NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(code) } } ?: NumberFormat.getInstance(locale)) as DecimalFormat).apply {
        if (withSign) {
            alwaysShowSign()
        }
    }
}

private fun percentFormat(locale: Locale, showsSign: Boolean): DecimalFormat = (NumberFormat.getPercentInstance(locale) as DecimalFormat).apply {
    if (showsSign) {
        alwaysShowSign()
    } else {
        negativePrefix = negativePrefix.replace(decimalFormatSymbols.minusSignString, "")
    }
}

private fun DecimalFormat.alwaysShowSign() {
    val pattern = toPattern()
    val negative = pattern.substringAfter(';', "-$pattern")
    applyPattern("${negative.replace('-', '+')};$negative")
}

fun GemTransactionRowValue.text(): String? = when (this) {
    GemTransactionRowValue.None -> null
    is GemTransactionRowValue.AssetSymbol -> asset.symbol
    is GemTransactionRowValue.Number -> number.text()
}
