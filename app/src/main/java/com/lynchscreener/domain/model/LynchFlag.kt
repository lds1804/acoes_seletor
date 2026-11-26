package com.lynchscreener.domain.model

/**
 * Risk / data-quality alerts attached to a score.
 *
 * They are persisted alongside the score so the ranking card and the exported
 * Markdown can say *why* a number looks suspicious — a 15% dividend yield or a
 * 45% ROE is usually a warning, not a prize.
 */
enum class LynchFlag(val code: String, val label: String) {
    /** Net income fell more than 15% versus the previous period. */
    PROFIT_DROP("PROFIT_DROP", "Lucro em queda"),

    /** 5-year earnings CAGR is negative. */
    NEGATIVE_GROWTH("NEGATIVE_GROWTH", "Crescimento negativo"),

    /** At least one year in the window reported a loss. */
    INCONSISTENT_EARNINGS("INCONSISTENT_EARNINGS", "Prejuízo na série"),

    /** Dividend yield above the atypical threshold (one-off payout / falling price). */
    ATYPICAL_DY("ATYPICAL_DY", "DY atípico"),

    /** ROE above the outlier threshold — often artificially thin equity. */
    HIGH_ROE("HIGH_ROE", "ROE atípico"),

    /** Non-positive equity. */
    THIN_EQUITY("THIN_EQUITY", "PL negativo");

    companion object {
        /** Decodes the comma-separated codes stored in `fundamentals.alertFlags`. */
        fun fromCodes(codes: String?): List<LynchFlag> {
            if (codes.isNullOrBlank()) return emptyList()
            val wanted = codes.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return entries.filter { it.code in wanted }
        }

        /** Encodes for storage; empty list becomes null. */
        fun toCodes(flags: List<LynchFlag>): String? =
            if (flags.isEmpty()) null else flags.joinToString(",") { it.code }
    }
}
