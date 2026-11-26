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
 *
 * [onProgress] is invoked with `(bytesWritten, totalBytes)` roughly every
 * 512 KB (and once at the end) so a UI can show a live transfer bar. The
 * callback runs on the IO dispatcher — keep it non-blocking.
 */
suspend fun OkHttpClient.downloadTo(
    url: String,
    target: File,
    headers: Map<String, String> = emptyMap(),
    onProgress: ((done: Long, total: Long) -> Unit)? = null,
) = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(url).get()
    headers.forEach { (key, value) -> builder.header(key, value) }

    newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        val body = response.body ?: throw IOException("Empty body for $url")
        // contentLength is -1 when the server omits Content-Length.
        val total = body.contentLength()
        target.outputStream().use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var done = 0L
                var lastReport = 0L
                onProgress?.invoke(0L, total)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    done += read
                    if (onProgress != null && done - lastReport >= PROGRESS_STEP_BYTES) {
                        lastReport = done
                        onProgress(done, total)
                    }
                }
                onProgress?.invoke(done, total)
            }
        }
    }
}

/** Granularity of the progress callbacks: 512 KB. */
private const val PROGRESS_STEP_BYTES = 512L * 1024L
