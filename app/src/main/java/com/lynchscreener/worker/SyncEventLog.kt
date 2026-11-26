package com.lynchscreener.worker

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory ring buffer of the last 20 sync events (triggers + network errors),
 * so the sync screen can show a live debug console without plugging the phone
 * into a PC.
 *
 * Every event is also mirrored to Logcat under [TAG], because this buffer dies
 * with the process — when a worker misbehaves the console is gone, and
 * `adb logcat -s lynch_sync` is the only way to see what happened.
 */
object SyncEventLog {

    /** Logcat tag; keep in sync with the WORKER_TAG used in adb commands. */
    private const val TAG = "lynch_sync"

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    private val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val stamp = time.format(Date())
        _events.value = (listOf("[$stamp] $message") + _events.value).take(20)
        android.util.Log.i(TAG, message)
    }
}
