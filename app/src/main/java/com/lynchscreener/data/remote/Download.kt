package com.lynchscreener.data.remote

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streams a large response to [target] on [Dispatchers.IO] without holding the
 * whole body in memory. Used for the CVM DFP packages (~40 MB each).
 */
suspend fun OkHttpClient.downloadTo(
    url: String,
    target: File,
    headers: Map<String, String> = emptyMap(),
) = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(url).get()
    headers.forEach { (key, value) -> builder.header(key, value) }

    newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        val body = response.body ?: throw IOException("Empty body for $url")
        target.outputStream().use { out -> body.byteStream().copyTo(out) }
    }
}
