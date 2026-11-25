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
 */
object SyncEventLog {

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    private val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val stamp = time.format(Date())
        _events.value = (listOf("[$stamp] $message") + _events.value).take(20)
    }
}
