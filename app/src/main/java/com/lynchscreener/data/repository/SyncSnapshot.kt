package com.lynchscreener.data.repository

/** Progress counters shown on the sync screen. */
data class SyncSnapshot(
    val brTotal: Int,
    val brWithFundamentals: Int,
    val brWithPrice: Int,
    val usTotal: Int,
    val usWithFundamentals: Int,
    val usWithPrice: Int,
)
