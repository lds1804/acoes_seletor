package com.lynchscreener.ui.sync

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.data.repository.SyncSettings
import com.lynchscreener.data.repository.SyncSnapshot
import com.lynchscreener.worker.SyncEventLog
import com.lynchscreener.worker.SyncMonitor
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

    val uiState: StateFlow<SyncUiState> =
        combine(wifiOnly, chargingOnly, snapshot, syncMonitor.status, logLines) { w, c, s, status, log ->
            SyncUiState(wifiOnly = w, chargingOnly = c, snapshot = s, syncStatus = status, logLines = log)
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
