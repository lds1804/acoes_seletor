package com.lynchscreener.domain.model

/** A [RankedCompany] with its overall and within-sector positions computed. */
data class RankingItem(
    val company: RankedCompany,
    val overallRank: Int,
    val sectorRank: Int,
)
