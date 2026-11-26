package com.lynchscreener.data.remote.dto

/**
 * Historical earnings extracted from CVM DFP filings (DRE consolidado).
 *
 * [netIncomeByYear] maps fiscal year (YYYY) to net income in BRL (absolute,
 * already de-scaled from the "MIL" unit). [cagr5y] is the 5-year compound
 * annual growth rate as a fraction (0.15 == 15%). [consistent] is true when no
 * fiscal year in the window reported a loss (Peter Lynch's consistency rule).
 */
data class CvmEarnings(
    val netIncomeByYear: Map<Int, Double>,
    val cagr5y: Double?,
    val consistent: Boolean?,
    val latestYear: Int?,
) {
    val isEmpty: Boolean get() = netIncomeByYear.isEmpty()

    companion object {
        /** Fiscal years without fresh data are ignored for CAGR purposes. */
        private const val MAX_STALENESS_YEARS = 2

        /** Minimum number of fiscal years to compute a meaningful CAGR. */
        private const val MIN_YEARS = 4

        /**
         * Builds the aggregate from a raw year→net-income map.
         *
         * CAGR null when:
         *  - fewer than [MIN_YEARS] fiscal years are known;
         *  - the latest filing is older than [MAX_STALENESS_YEARS] (the company
         *    stopped reporting, e.g. delisted — a stale CAGR would be misleading);
         *  - either endpoint of the window is a loss (growth from a negative
         *    base is not meaningful), in which case the caller falls back to
         *    revenue growth.
         */
        fun from(raw: Map<Int, Double>, currentYear: Int = java.time.LocalDate.now().year): CvmEarnings {
            if (raw.isEmpty()) return CvmEarnings(emptyMap(), null, null, null)
            val sorted = raw.toSortedMap()
            val window = sorted.entries.toList().takeLast(6)
            val years = window.associate { it.key to it.value }
            val latestYear = sorted.keys.lastOrNull()
            val fresh = latestYear != null && latestYear >= currentYear - MAX_STALENESS_YEARS

            val first = window.firstOrNull()?.value
            val last = window.lastOrNull()?.value
            val intervals = window.size - 1
            val cagr = if (
                fresh && intervals >= MIN_YEARS - 1 &&
                first != null && last != null && first > 0.0 && last > 0.0
            ) {
                Math.pow(last / first, 1.0 / intervals) - 1.0
            } else {
                null
            }

            val consistent = if (window.size >= 2) window.all { it.value > 0.0 } else null
            return CvmEarnings(
                netIncomeByYear = years,
                cagr5y = cagr,
                consistent = consistent,
                latestYear = latestYear,
            )
        }
    }
}
