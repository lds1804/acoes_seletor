package com.lynchscreener.data.remote

import java.time.YearMonth

/**
 * Helpers to interpret the exercise periods disclosed by the CVM
 * (`DT_INI_EXERC` / `DT_FIM_EXERC`).
 *
 * Why this matters: in a DFP every account appears twice (current and previous
 * fiscal year), and in an ITR every account appears twice per quarter — once for
 * the isolated period (3 months) and once accumulated from January 1st (YTD).
 * Summing or picking the wrong row inflates the earnings-per-share denominator
 * (or the P/E), which is exactly the class of bug that produced P/L = 25.9 for a
 * company whose real P/L was ~7.
 *
 * Dates are always `yyyy-MM-dd` in the CVM open data.
 */
object CvmPeriod {

    /** `"2025-03-31"` -> 24305 (= 2025 * 12 + 2, a monotonically increasing month index). */
    fun monthIndex(date: String?): Int? {
        if (date.isNullOrBlank() || date.length < 7) return null
        val year = date.substring(0, 4).toIntOrNull() ?: return null
        val month = date.substring(5, 7).toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return year * 12 + (month - 1)
    }

    /** Length of the exercise period in months, or null when a date is missing. */
    fun monthsBetween(start: String?, end: String?): Int? {
        val from = monthIndex(start) ?: return null
        val to = monthIndex(end) ?: return null
        return to - from
    }

    /** Fiscal year of the period (taken from the end date). */
    fun fiscalYear(end: String?): Int? =
        if (end.isNullOrBlank() || end.length < 4) null else end.substring(0, 4).toIntOrNull()

    /**
     * True when the row covers a full fiscal year (10..14 months, tolerant to
     * 52/53-week fiscal calendars). Anything shorter is a partial period and must
     * never be compared against an annual market cap without accumulation.
     */
    fun isAnnual(start: String?, end: String?): Boolean =
        monthsBetween(start, end)?.let { it in 10..14 } ?: false

    /** True when the row covers the first quarter of the year (Jan 1st start). */
    fun isYearToDate(start: String?): Boolean = monthIndex(start)?.let { it % 12 == 0 } ?: false

    /**
     * Picks the year-to-date row among candidates with the same fiscal year.
     *
     * Used for ITR files, where 2T and 3T expose both the isolated quarter and
     * the accumulated figure. Ties are broken by the longest period.
     */
    fun <T> pickYearToDate(rows: List<T>, start: (T) -> String?, end: (T) -> String?): T? =
        rows.maxByOrNull { monthsBetween(start(it), end(it)) ?: -1 }

    /**
     * Compound annual growth rate between two values measured over [years]
     * intervals. Requires both endpoints to be positive — growth from a loss is
     * not a meaningful rate.
     */
    fun cagr(first: Double?, last: Double?, intervals: Int): Double? {
        if (first == null || last == null || intervals <= 0) return null
        if (first <= 0.0 || last <= 0.0) return null
        return Math.pow(last / first, 1.0 / intervals) - 1.0
    }

    /** Last period covered by [start]/[end] as a readable `YYYY-MM` label. */
    fun label(start: String?, end: String?): String {
        val months = monthsBetween(start, end) ?: return "?"
        val ym = monthIndex(end)?.let { YearMonth.of(it / 12, it % 12 + 1) }
        return "${months}m até ${ym ?: "?"}"
    }
}
