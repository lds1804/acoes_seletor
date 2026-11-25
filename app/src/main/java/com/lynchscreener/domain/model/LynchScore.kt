package com.lynchscreener.domain.model

data class LynchScore(
    val totalScore: Int,
    val category: LynchCategory,
    val pegy: Double?,
    val netCashToMarketCap: Double?,
    val components: List<ScoreComponent>,
    val disqualifiers: List<String>,
) {
    val isDisqualified: Boolean get() = disqualifiers.isNotEmpty()
}
