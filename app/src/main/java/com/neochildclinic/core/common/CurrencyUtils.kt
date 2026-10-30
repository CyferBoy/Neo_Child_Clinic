package com.neochildclinic.core.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Currency display formatter implementing Indian compact notation according to
 * the Warm Clinical System v2 presentation specification:
 *
 * - Below ₹1,00,000: Indian digit grouping, no unnecessary decimals (e.g., ₹999, ₹22,000, ₹86,300).
 * - ₹1,00,000 to below ₹1,00,00,000: ₹<value> L (e.g., ₹1 L, ₹1.25 L, ₹1.5 L, ₹10 L).
 * - ₹1,00,00,000 and above: ₹<value> Cr (e.g., ₹1 Cr, ₹1.5 Cr).
 *
 * Rules:
 * - Exactly "L" and "Cr" with a single preceding space.
 * - Up to two decimal places, rounded half-up, trailing zeros trimmed.
 * - Presentation-only formatting. Underlying data values remain unchanged.
 */
object CurrencyUtils {

    fun formatInr(value: Double): String {
        if (!value.isFinite()) return "—"
        val sign = if (value < 0) "−" else ""
        val absValue = abs(value)

        return when {
            absValue >= 10_000_000.0 -> {
                val cr = round((absValue / 10_000_000.0) * 100.0) / 100.0
                val formatted = String.format(Locale.US, "%.2f", cr).trimEnd('0').trimEnd('.')
                "$sign₹$formatted Cr"
            }
            absValue >= 100_000.0 -> {
                val lakh = round((absValue / 100_000.0) * 100.0) / 100.0
                val formatted = String.format(Locale.US, "%.2f", lakh).trimEnd('0').trimEnd('.')
                "$sign₹$formatted L"
            }
            else -> {
                val symbols = DecimalFormatSymbols(Locale.US).apply {
                    groupingSeparator = ','
                    decimalSeparator = '.'
                }
                val df = if (absValue % 1.0 == 0.0) {
                    DecimalFormat("##,##,##0", symbols)
                } else {
                    DecimalFormat("##,##,##0.##", symbols)
                }
                "$sign₹${df.format(absValue)}"
            }
        }
    }

    fun formatInr(value: Long): String = formatInr(value.toDouble())

    fun formatInr(value: Int): String = formatInr(value.toDouble())
}
