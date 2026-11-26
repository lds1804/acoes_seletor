package com.lynchscreener.ui.detail

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.domain.Ranking
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.RankedCompany
import com.lynchscreener.domain.model.ScoreComponent
import com.lynchscreener.domain.model.SectorStats
import com.lynchscreener.worker.SyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/** One fiscal year of reported net income (CVM DFP, B3 companies). */
data class EarningsYear(val year: Int, val value: Double)

data class DetailUiState(
    val company: RankedCompany? = null,
    val overallRank: Int? = null,
    val sectorRank: Int? = null,
    val sectorStats: SectorStats? = null,
    val components: List<ScoreComponent> = emptyList(),
    /** Net income per fiscal year, oldest first. Empty when not yet ingested. */
    val earningsHistory: List<EarningsYear> = emptyList(),
    /** True when the company reported the DFP and no losses in the window. */
    val earningsConsistent: Boolean? = null,
    /** `CONTROLLERS` or `CONSOLIDATED` — basis of the reported net income. */
    val earningsBasis: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val repository: AssetRepository,
    savedStateHandle: SavedStateHandle,
    private val app: Application,
) : ViewModel() {

    private val ticker: String = savedStateHandle.get<String>("ticker").orEmpty()
    private val json = Json { ignoreUnknownKeys = true }

    val uiState: StateFlow<DetailUiState> = repository.observeCompany(ticker)
        .flatMapLatest { company ->
            if (company == null) flowOf(DetailUiState())
            else repository.observeRanked(Market.fromCode(company.market))
                .map { list -> buildDetail(company, list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private fun buildDetail(company: RankedCompany, list: List<RankedCompany>): DetailUiState {
        val item = Ranking.rank(list).firstOrNull { it.company.ticker == company.ticker }
        val sector = company.sector ?: Ranking.OUTROS
        val stats = Ranking.sectorStats(list)[sector]
        val components = runCatching {
            company.breakdownJson?.let { json.decodeFromString<List<ScoreComponent>>(it) }
        }.getOrNull() ?: emptyList()

        val earnings = runCatching {
            company.earningsHistoryJson
                ?.let { json.decodeFromString<Map<String, Double>>(it) }
                ?.mapNotNull { (year, value) -> year.toIntOrNull()?.let { EarningsYear(it, value) } }
                ?.sortedBy { it.year }
        }.getOrNull().orEmpty()

        return DetailUiState(
            company = company,
            overallRank = item?.overallRank,
            sectorRank = item?.sectorRank,
            sectorStats = stats,
            components = components,
            earningsHistory = earnings,
            earningsConsistent = company.earningsConsistent,
            earningsBasis = company.earningsBasis,
        )
    }

    fun refreshNow() {
        val company = uiState.value.company ?: return
        SyncScheduler.triggerSingle(app, company.ticker, Market.fromCode(company.market))
    }
}
