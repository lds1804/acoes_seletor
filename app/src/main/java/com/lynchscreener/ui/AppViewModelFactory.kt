package com.lynchscreener.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.lynchscreener.LynchScreenerApp
import com.lynchscreener.di.AppContainer
import com.lynchscreener.ui.detail.DetailViewModel
import com.lynchscreener.ui.ranking.RankingViewModel
import com.lynchscreener.ui.sync.SyncViewModel

/** Manual ViewModel factory reading from the [AppContainer] DI graph. */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LynchScreenerApp
        return when {
            modelClass.isAssignableFrom(RankingViewModel::class.java) ->
                RankingViewModel(container.repository, container.syncMonitor) as T

            modelClass.isAssignableFrom(DetailViewModel::class.java) ->
                DetailViewModel(container.repository, extras.createSavedStateHandle(), app) as T

            modelClass.isAssignableFrom(SyncViewModel::class.java) ->
                SyncViewModel(container.repository, container.syncSettings, container.syncMonitor, app) as T

            else -> throw IllegalArgumentException("ViewModel desconhecida: ${modelClass.name}")
        }
    }
}
