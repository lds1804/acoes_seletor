package com.lynchscreener.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.domain.Ranking
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.RankingItem
import com.lynchscreener.domain.model.SectorStats
import com.lynchscreener.worker.SyncMonitor
import com.lynchscreener.worker.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class ViewMode { GENERAL, BY_SECTOR }

enum class SortBy(val label: String) {
    SCORE("Score Lynch"),
    PEGY("Menor PEGY"),
    PE("Menor P/L"),
    PB("Menor P/VP"),
    ROE("Maior ROE"),
    DY("Maior DY"),
}

data class RankingUiState(
    val market: Market = Market.BR,
    val viewMode: ViewMode = ViewMode.GENERAL,
    val query: String = "",
    val sortBy: SortBy = SortBy.SCORE,
    val isLoading: Boolean = true,
    val items: List<RankingItem> = emptyList(),
    val sectorStats: Map<String, SectorStats> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class RankingViewModel(
    private val repository: AssetRepository,
    private val syncMonitor: SyncMonitor,
) : ViewModel() {

    private val market = MutableStateFlow(Market.BR)
    private val viewMode = MutableStateFlow(ViewMode.GENERAL)
    private val query = MutableStateFlow("")
    private val sortBy = MutableStateFlow(SortBy.SCORE)

    private val companies = market.flatMapLatest { repository.observeRanked(it) }

    /** Live background sync status (running / enqueued tasks). */
    val syncStatus: StateFlow<SyncStatus> =
        syncMonitor.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    val uiState: StateFlow<RankingUiState> =
        combine(market, viewMode, query, sortBy, companies) { m, vm, q, s, comps ->
            val ranked = Ranking.rank(comps)
            val filtered = if (q.isBlank()) ranked else ranked.filter { item ->
                item.company.ticker.contains(q, ignoreCase = true) ||
                    (item.company.name?.contains(q, ignoreCase = true) == true)
            }
            RankingUiState(
                market = m,
                viewMode = vm,
                query = q,
                sortBy = s,
                isLoading = false,
                items = sortBy(filtered, s),
                sectorStats = Ranking.sectorStats(comps),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RankingUiState())

    fun setMarket(value: Market) {
        market.value = value
    }

    fun setViewMode(value: ViewMode) {
        viewMode.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSortBy(value: SortBy) {
        sortBy.value = value
    }

    private fun sortBy(list: List<RankingItem>, s: SortBy): List<RankingItem> = when (s) {
        SortBy.SCORE -> list.sortedBy { it.overallRank }
        SortBy.PEGY -> list.sortedBy { it.company.pegy ?: Double.MAX_VALUE }
        SortBy.PE -> list.sortedBy { it.company.pe ?: Double.MAX_VALUE }
        SortBy.PB -> list.sortedBy { it.company.pb ?: Double.MAX_VALUE }
        SortBy.ROE -> list.sortedByDescending { it.company.roe ?: Double.NEGATIVE_INFINITY }
        SortBy.DY -> list.sortedByDescending { it.company.dividendYield ?: Double.NEGATIVE_INFINITY }
    }
}
