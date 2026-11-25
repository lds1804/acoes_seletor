package com.lynchscreener.data.remote

import com.lynchscreener.data.remote.HttpConfig.BROWSER_USER_AGENT
import com.lynchscreener.data.remote.HttpConfig.FUNDAMENTUS_DETALHES
import com.lynchscreener.data.remote.HttpConfig.FUNDAMENTUS_RESULTADO
import okhttp3.OkHttpClient

/** Fetches raw HTML from Fundamentus (B3). */
class FundamentusClient(private val client: OkHttpClient) {

    /** Full screening table (ticker, cotação, P/L, P/VP, DY, ROE, ROIC, ...). */
    suspend fun fetchResultTable(): String =
        client.get(FUNDAMENTUS_RESULTADO, mapOf("User-Agent" to BROWSER_USER_AGENT))

    /** Enrichment page: sector, subsector, market cap, equity, cash, net debt, net income. */
    suspend fun fetchDetails(ticker: String): String =
        client.get(FUNDAMENTUS_DETALHES + ticker, mapOf("User-Agent" to BROWSER_USER_AGENT))
}
