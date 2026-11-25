package com.lynchscreener.domain.model

/**
 * One slice of the Peter Lynch score. Persisted as JSON in
 * [com.lynchscreener.data.local.entity.FundamentalsEntity.breakdownJson]
 * so the "Raio-X" screen can explain exactly where points were won/lost.
 */
data class ScoreComponent(
    val key: String,
    val label: String,
    val points: Double,
    val maxPoints: Double,
    val rationale: String,
)
