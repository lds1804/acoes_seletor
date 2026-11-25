package com.lynchscreener.data.remote.dto

/** A single price snapshot from Yahoo or Stooq. */
data class QuotePrice(
    val price: Double,
    val currency: String? = null,
    val timestampSec: Long? = null,
)
