package com.lynchscreener.data.remote.parser

import com.lynchscreener.data.remote.dto.QuotePrice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Parses the Yahoo Finance chart JSON (`/v8/finance/chart/{symbol}`). */
object YahooChartParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): QuotePrice? {
        val root = json.parseToJsonElement(raw).jsonObject
        val result = root["chart"]?.jsonObject?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
            ?: return null
        val meta = result["meta"]?.jsonObject ?: return null
        val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull ?: return null
        return QuotePrice(
            price = price,
            currency = meta["currency"]?.jsonPrimitive?.contentOrNull,
            timestampSec = meta["regularMarketTime"]?.jsonPrimitive?.longOrNull,
        )
    }
}
