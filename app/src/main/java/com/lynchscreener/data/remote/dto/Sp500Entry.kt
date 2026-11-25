package com.lynchscreener.data.remote.dto

import kotlinx.serialization.Serializable

/** One entry of the bundled `sp500_universe.json` (assets). */
@Serializable
data class Sp500Entry(
    val ticker: String,
    val name: String? = null,
    val cik: String? = null,
    val sector: String? = null,
)
