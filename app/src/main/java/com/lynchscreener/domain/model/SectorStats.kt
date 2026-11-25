package com.lynchscreener.domain.model

/** Aggregate metrics for a sector, used to compare a stock against its peers. */
data class SectorStats(
    val sector: String,
    val count: Int,
    val avgPe: Double?,
    val avgPb: Double?,
    val avgRoe: Double?,
    val medianPe: Double?,
    val medianPb: Double?,
    val medianRoe: Double?,
)
