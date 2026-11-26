package com.lynchscreener.domain.model

data class LynchScore(
    val totalScore: Int,
    val category: LynchCategory,
    val pegy: Double?,
    val netCashToMarketCap: Double?,
    val components: List<ScoreComponent>,
    val disqualifiers: List<String>,
    /**
     * Sum of the criteria before any ceiling/penalty was applied. Differs from
     * [totalScore] only when a cap kicked in, and the difference is exposed as
     * the `CEILING` component so the breakdown always reconciles.
     */
    val baseScore: Int = 0,
) {
    val isDisqualified: Boolean get() = disqualifiers.isNotEmpty()

    /** True when a ceiling lowered the score. */
    val isCapped: Boolean get() = totalScore < baseScore

    /** Risk / data-quality alerts (profit drop, atypical yield, thin equity…). */
    val flags: List<LynchFlag> = emptyList()
}
