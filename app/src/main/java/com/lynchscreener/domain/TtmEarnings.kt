package com.lynchscreener.domain

/**
 * Trailing-twelve-month earnings and the year-over-year change of that rolling
 * window.
 *
 * Why this exists: the app used to call the difference between two *closed fiscal
 * years* "YoY TTM", which is a different (and lagging) number. A company whose
 * last reported quarter collapsed still looked like it was growing, because the
 * comparison was 2025-vs-2024.
 *
 * The rule here is the one an analyst uses:
 *
 *     TTM(E)  = sum of the four isolated quarters ending at E
 *     YoY     = TTM(E) / TTM(E − 12 months) − 1
 *
 * Pure and dependency-free so it can be unit-tested without Android/Room.
 */
object TtmEarnings {

    /** Minimum quarters required for a real TTM comparison (4 current + 4 prior). */
    const val MIN_QUARTERS = 8

    /**
     * A quarter of reported net income.
     *
     * @param year fiscal year.
     * @param quarter 1..4.
     * @param value net income for the three-month period alone (not year-to-date).
     */
    data class Quarter(val year: Int, val quarter: Int, val value: Double) : Comparable<Quarter> {
        /** Absolute position, so ordering and shifting are plain arithmetic. */
        val index: Int get() = year * 4 + (quarter - 1)

        override fun compareTo(other: Quarter): Int = index.compareTo(other.index)

        override fun toString(): String = "${year}Q$quarter"
    }

    data class Result(
        /** Sum of the last four quarters, in BRL. */
        val ttmCurrent: Double?,
        /** Sum of the four quarters before those, in BRL. */
        val ttmPrevious: Double?,
        /** TTM variation as a fraction (0.18 == +18%). */
        val yoy: Double?,
        /** Last quarter used, e.g. "2026Q3". */
        val latestQuarter: String?,
        /** True when the comparison used isolated quarters instead of fiscal years. */
        val quarterly: Boolean,
        /** How many quarters were available. */
        val quartersUsed: Int,
    ) {
        companion object {
            val EMPTY = Result(null, null, null, null, false, 0)
        }
    }

    /**
     * Builds the TTM comparison.
     *
     * @param quarters isolated quarterly values (any order, duplicates allowed —
     *   the most recent filing wins).
     * @param annual net income per fiscal year, used only as a fallback when there
     *   are not enough quarters, and to fill a missing Q4.
     */
    fun compute(
        quarters: List<Quarter>,
        annual: Map<Int, Double>,
        currentYear: Int = java.time.LocalDate.now().year,
    ): Result {
        val byIndex = quarters
            .groupBy { it.index }
            .mapValues { (_, list) -> list.last().value }

        // A missing 4th quarter is derivable when both the fiscal year and the
        // first three quarters are known: Q4 = FY − (Q1+Q2+Q3).
        val filled = byIndex.toMutableMap()
        for ((year, fy) in annual) {
            val q4 = year * 4 + 3
            if (filled.containsKey(q4)) continue
            val q1 = filled[year * 4]
            val q2 = filled[year * 4 + 1]
            val q3 = filled[year * 4 + 2]
            if (q1 != null && q2 != null && q3 != null) {
                filled[q4] = fy - (q1 + q2 + q3)
            }
        }

        val ordered = filled.entries
            .sortedByDescending { it.key }
            .map { (index, value) -> Quarter(index / 4, index % 4 + 1, value) }

        if (ordered.size < MIN_QUARTERS) {
            return annualFallback(annual, currentYear, ordered.size)
        }

        // Only compare windows that are contiguous: a gap (missing filing) would
        // make the sum meaningless.
        val latest = ordered.first()
        val indexByQuarter = filled.keys
        val currentWindow = (0..3).map { latest.index - it }
        if (currentWindow.any { it !in indexByQuarter }) {
            return annualFallback(annual, currentYear, ordered.size)
        }
        val previousWindow = (4..7).map { latest.index - it }
        if (previousWindow.any { it !in indexByQuarter }) {
            return annualFallback(annual, currentYear, ordered.size)
        }

        val ttmCurrent = currentWindow.sumOf { filled.getValue(it) }
        val ttmPrevious = previousWindow.sumOf { filled.getValue(it) }
        val yoy = if (ttmPrevious > 0.0) ttmCurrent / ttmPrevious - 1.0 else null

        return Result(
            ttmCurrent = ttmCurrent,
            ttmPrevious = ttmPrevious,
            yoy = yoy,
            latestQuarter = latest.toString(),
            quarterly = true,
            quartersUsed = ordered.size,
        )
    }

    /**
     * Last resort: last closed fiscal year against the one before it.
     *
     * This is what the whole app used to do unconditionally. Kept because some
     * companies stop filing ITRs (delisting, closed capital) and showing nothing
     * would be worse than showing a lagging number — as long as the UI can tell
     * the two apart, which [Result.quarterly] does.
     */
    private fun annualFallback(
        annual: Map<Int, Double>,
        currentYear: Int,
        quartersUsed: Int,
    ): Result {
        val years = annual.keys.sorted()
        if (years.size < 2) return Result.EMPTY.copy(quartersUsed = quartersUsed)
        val lastYear = years.last()
        val previousYear = years[years.size - 2]
        val last = annual.getValue(lastYear)
        val previous = annual.getValue(previousYear)
        return Result(
            ttmCurrent = last,
            ttmPrevious = previous,
            yoy = if (previous > 0.0) last / previous - 1.0 else null,
            latestQuarter = "FY$lastYear",
            quarterly = false,
            quartersUsed = quartersUsed,
        )
    }
}
