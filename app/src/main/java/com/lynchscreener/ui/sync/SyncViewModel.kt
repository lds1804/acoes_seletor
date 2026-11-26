package com.lynchscreener.ui.sync

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.data.repository.SyncSettings
import com.lynchscreener.data.repository.SyncSnapshot
import com.lynchscreener.worker.DeviceConditions
import com.lynchscreener.worker.SyncEventLog
import com.lynchscreener.worker.SyncMonitor
import com.lynchscreener.worker.SyncProgress
import com.lynchscreener.worker.SyncScheduler
import com.lynchscreener.worker.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SyncUiState(
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = true,
    val snapshot: SyncSnapshot? = null,
    val syncStatus: SyncStatus = SyncStatus(),
    val logLines: List<String> = emptyList(),
    /** Live byte progress of the heavy CVM download, if any. */
    val download: SyncProgress.State = SyncProgress.State(),
    /** Battery / network state, so a stalled queue can explain itself. */
    val conditions: DeviceConditions? = null,
)

class SyncViewModel(
    private val repository: AssetRepository,
    private val syncSettings: SyncSettings,
    private val syncMonitor: SyncMonitor,
    private val app: Application,
) : ViewModel() {

    private val wifiOnly = MutableStateFlow(syncSettings.wifiOnly)
    private val chargingOnly = MutableStateFlow(syncSettings.chargingOnly)
    private val snapshot = MutableStateFlow<SyncSnapshot?>(null)
    private val logLines = MutableStateFlow<List<String>>(emptyList())
    private val conditions = MutableStateFlow<DeviceConditions?>(null)

    val uiState: StateFlow<SyncUiState> =
        combine(
            combine(wifiOnly, chargingOnly, logLines) { w, c, log -> Triple(w, c, log) },
            combine(snapshot, syncMonitor.status, SyncProgress.state) { s, status, download ->
                Triple(s, status, download)
            },
            conditions,
        ) { (w, c, log), (s, status, download), cond ->
            SyncUiState(
                wifiOnly = w,
                chargingOnly = c,
                snapshot = s,
                syncStatus = status,
                logLines = log,
                download = download,
                conditions = cond,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    init {
        viewModelScope.launch {
            val errorsFlow = repository.observeErrors()
            combine(SyncEventLog.events, errorsFlow) { events, errs ->
                val errLines = errs.map { "${it.ticker}: ${it.lastError ?: "erro"}" }
                (events + errLines).distinct().take(30)
            }.collect { logLines.value = it }
        }
        viewModelScope.launch {
            while (isActive) {
                snapshot.value = runCatching { repository.syncSnapshot() }.getOrNull()
                // Poll the device state on the same tick, so the screen can say what
                // is blocking the queues instead of just "aguardando".
                conditions.value = runCatching { DeviceConditions.read(app) }.getOrNull()
                delay(2_000)
            }
        }
    }

    fun setWifiOnly(value: Boolean) {
        wifiOnly.value = value
        syncSettings.wifiOnly = value
        SyncScheduler.scheduleAll(app, syncSettings)
    }

    fun setChargingOnly(value: Boolean) {
        chargingOnly.value = value
        syncSettings.chargingOnly = value
        SyncScheduler.scheduleAll(app, syncSettings)
    }

    fun triggerNow() {
        SyncScheduler.triggerNow(app)
    }
}
