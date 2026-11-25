package com.lynchscreener.data.remote.parser

/**
 * Defensive Brazilian-number parsing.
 *
 * Fundamentus uses comma as decimal separator and dot as thousands separator
 * (e.g. "1.234,56" == 1234.56, "1.548.000" == 1548000). Percentages come as
 * "5,6%".
 */
object BrNumber {

    /** "1.234,56" -> 1234.56 ; "1.548.000" -> 1548000.0 ; "-12,3" -> -12.3. */
    fun toDouble(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        val hadPercent = s.endsWith("%")
        s = s.removeSuffix("%").trim()
        val hasComma = s.contains(',')
        s = s.replace(".", "").replace(',', '.')
        s = s.replace("R$", "").replace("US$", "").trim()
        val value = s.toDoubleOrNull() ?: return null
        return if (hadPercent) value / 100.0 else value
    }

    /** "5,6%" -> 0.056 ; "15" -> 0.15 (assumes percent input). */
    fun toFraction(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim().removeSuffix("%").trim()
        s = s.replace(".", "").replace(',', '.')
        return s.toDoubleOrNull()?.div(100.0)
    }
}
