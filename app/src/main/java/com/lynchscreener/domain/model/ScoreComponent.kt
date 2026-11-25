package com.lynchscreener.domain.model

import kotlinx.serialization.Serializable

/**
 * One slice of the Peter Lynch score. Persisted as JSON in
 * [com.lynchscreener.data.local.entity.FundamentalsEntity.breakdownJson]
 * so the "Raio-X" screen can explain exactly where points were won/lost.
 */
@Serializable
data class ScoreComponent(
    val key: String,
    val label: String,
    val points: Double,
    val maxPoints: Double,
    val rationale: String,
)
