package com.lynchscreener.ui.sync

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lynchscreener.data.local.entity.AssetEntity
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.data.repository.SyncSettings
import com.lynchscreener.data.repository.SyncSnapshot
import com.lynchscreener.worker.SyncScheduler
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
    val errors: List<AssetEntity> = emptyList(),
)

class SyncViewModel(
    private val repository: AssetRepository,
    private val syncSettings: SyncSettings,
    private val app: Application,
) : ViewModel() {

    private val wifiOnly = MutableStateFlow(syncSettings.wifiOnly)
    private val chargingOnly = MutableStateFlow(syncSettings.chargingOnly)
    private val snapshot = MutableStateFlow<SyncSnapshot?>(null)
    private val errors = MutableStateFlow<List<AssetEntity>>(emptyList())

    val uiState: StateFlow<SyncUiState> =
        combine(wifiOnly, chargingOnly, snapshot, errors) { w, c, s, e ->
            SyncUiState(wifiOnly = w, chargingOnly = c, snapshot = s, errors = e)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    init {
        viewModelScope.launch {
            repository.observeErrors().collect { errors.value = it }
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
