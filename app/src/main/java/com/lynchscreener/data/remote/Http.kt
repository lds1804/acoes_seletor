package com.lynchscreener.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Shared blocking GET helper that runs on [Dispatchers.IO] and surfaces
 * non-2xx responses as [IOException]. Kept in one place so every client
 * enforces the same timeout + error handling.
 */
suspend fun OkHttpClient.get(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.header(key, value) }

        newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            response.body?.string() ?: throw IOException("Empty body for $url")
        }
    }
