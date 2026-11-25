package com.lynchscreener.data.remote

import com.lynchscreener.data.remote.dto.QuotePrice
import com.lynchscreener.data.remote.parser.StooqCsvParser
import com.lynchscreener.data.remote.parser.YahooChartParser
import com.lynchscreener.data.remote.HttpConfig.BROWSER_USER_AGENT
import com.lynchscreener.data.remote.HttpConfig.STOOQ_CSV
import com.lynchscreener.data.remote.HttpConfig.YAHOO_CHART
import com.lynchscreener.domain.model.Market
import okhttp3.OkHttpClient

/**
 * Daily quotes. Yahoo is the primary source (HTTPS, JSON, .SA suffix for B3);
 * Stooq CSV is the automatic fallback when Yahoo fails.
 */
class PriceClient(private val client: OkHttpClient) {

    suspend fun fetchPrice(ticker: String, market: Market): QuotePrice? =
        fetchYahoo(ticker, market) ?: fetchStooq(ticker, market)

    suspend fun fetchYahoo(ticker: String, market: Market): QuotePrice? = try {
        val symbol = yahooSymbol(ticker, market)
        val body = client.get("$YAHOO_CHART$symbol?interval=1d&range=5d", mapOf("User-Agent" to BROWSER_USER_AGENT))
        YahooChartParser.parse(body)
    } catch (_: Exception) {
        null
    }

    suspend fun fetchStooq(ticker: String, market: Market): QuotePrice? = try {
        val symbol = stooqSymbol(ticker, market)
        val body = client.get(STOOQ_CSV.format(symbol), mapOf("User-Agent" to BROWSER_USER_AGENT))
        StooqCsvParser.parse(body)
    } catch (_: Exception) {
        null
    }

    private fun yahooSymbol(ticker: String, market: Market): String =
        if (market == Market.US) ticker else "$ticker.SA"

    private fun stooqSymbol(ticker: String, market: Market): String =
        if (market == Market.US) "${ticker.lowercase()}.us" else ticker.lowercase()
}
