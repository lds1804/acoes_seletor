package com.lynchscreener.worker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fine-grained progress of the heaviest download in the app: the CVM DFP
 * packages (one ZIP of ~60–80 MB per fiscal year).
 *
 * [SyncEventLog] only records discrete events ("baixando…", "concluído"), which
 * leaves the user staring at a stalled console for many minutes. This object
 * exposes the live byte counters so the sync screen can show a real transfer
 * bar and the remaining package count.
 */
object SyncProgress {

    data class State(
        /** Human-readable step, e.g. "DFP 2025 · baixando pacote". */
        val label: String = "",
        val bytesDone: Long = 0L,
        /** -1 when the server did not send Content-Length. */
        val bytesTotal: Long = -1L,
        val active: Boolean = false,
        /** DFP packages still to process after the current one. */
        val pendingPackages: Int = 0,
    ) {
        /** 0..1, or null when the total size is unknown. */
        val fraction: Float?
            get() = if (bytesTotal > 0L) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else null

        val megabytesDone: Long get() = bytesDone / 1_048_576L
        val megabytesTotal: Long get() = if (bytesTotal > 0L) bytesTotal / 1_048_576L else -1L
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Starts a new phase (download or parsing) and resets the byte counters. */
    fun begin(label: String, pendingPackages: Int = 0) {
        _state.value = State(
            label = label,
            active = true,
            pendingPackages = pendingPackages,
        )
    }

    /** Byte-level update from the streaming download. */
    fun onBytes(done: Long, total: Long) {
        val current = _state.value
        _state.value = current.copy(
            bytesDone = done,
            bytesTotal = if (total > 0L) total else current.bytesTotal,
        )
    }

    /** Clears the indicator when the heavy work is over (or the worker dies). */
    fun finish() {
        _state.value = State()
    }
}
