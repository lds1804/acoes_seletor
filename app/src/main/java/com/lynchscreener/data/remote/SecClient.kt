package com.lynchscreener.data.remote

import com.lynchscreener.data.remote.HttpConfig.SEC_COMPANY_FACTS
import com.lynchscreener.data.remote.HttpConfig.SEC_USER_AGENT
import okhttp3.OkHttpClient

/**
 * SEC EDGAR client. The User-Agent MUST identify the requestor with a real
 * contact (HTTP 403 otherwise), and we keep a conservative pace (~3 req/s)
 * enforced by the calling worker.
 */
class SecClient(private val client: OkHttpClient) {

    /** XBRL company facts for a 10-digit CIK (e.g. "0000320193"). */
    suspend fun fetchCompanyFacts(cik: String): String {
        require(cik.length == 10) { "CIK must be zero-padded to 10 digits, got '$cik'" }
        return client.get(
            SEC_COMPANY_FACTS.format(cik),
            mapOf(
                "User-Agent" to SEC_USER_AGENT,
                "Accept-Encoding" to "gzip, deflate",
            ),
        )
    }
}
