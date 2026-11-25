package com.lynchscreener.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lynchscreener.domain.Ranking
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.RankingItem
import com.lynchscreener.domain.model.SectorStats
import com.lynchscreener.ui.components.IndicatorValue
import com.lynchscreener.ui.components.PulsingDot
import com.lynchscreener.ui.components.SegmentedPill
import com.lynchscreener.ui.components.StockRankCard
import com.lynchscreener.ui.components.sectorIcon
import com.lynchscreener.ui.components.shimmerEffect
import com.lynchscreener.ui.format.Formatters
import com.lynchscreener.ui.ranking.RankingViewModel
import com.lynchscreener.ui.ranking.SortBy
import com.lynchscreener.ui.ranking.ViewMode
import com.lynchscreener.ui.theme.LocalLynchColors
import com.lynchscreener.worker.SyncScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    viewModel: RankingViewModel,
    onCompanyClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val colors = LocalLynchColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var isRefreshing by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedSector by remember(state.market) { mutableStateOf<String?>(null) }
    val displayItems = if (selectedSector == null) state.items
    else state.items.filter { (it.company.sector ?: Ranking.OUTROS) == selectedSector }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        // Header
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lynch Screener", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                PulsingDot(
                    color = if (syncStatus.isSyncing) colors.accentPrimary else colors.borderSubtle,
                )
            }
            Spacer(Modifier.height(12.dp))

            SegmentedPill(
                options = listOf("Brasil · B3", "EUA · S&P 500"),
                selectedIndex = if (state.market == Market.BR) 0 else 1,
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setMarket(if (it == 0) Market.BR else Market.US)
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Buscar ticker ou empresa…", color = colors.textMuted) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Rounded.Close, null, tint = colors.textMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.accentPrimary,
                        unfocusedBorderColor = colors.borderSubtle,
                        cursorColor = colors.accentPrimary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                    modifier = Modifier.weight(1f).height(44.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFilters = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .background(colors.surfaceElevated),
                    ) {
                        Icon(Icons.Rounded.Tune, null, tint = colors.textPrimary)
                    }
                    if (state.filters.hasActiveFilters) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .background(colors.accentPrimary, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            SegmentedPill(
                options = listOf("Ranking geral", "Por setor"),
                selectedIndex = if (state.viewMode == ViewMode.GENERAL) 0 else 1,
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setViewMode(if (it == 0) ViewMode.GENERAL else ViewMode.BY_SECTOR)
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Sector chips
        SectorChips(state, selectedSector, onSelect = { selectedSector = it })

        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                SyncScheduler.triggerNow(context)
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedContent(
                targetState = state.isLoading,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "loading",
            ) { loading ->
                when {
                    loading -> SkeletonList()
                    state.items.isEmpty() -> EmptyState()
                    state.viewMode == ViewMode.GENERAL -> GeneralList(displayItems, state.market, onCompanyClick)
                    else -> SectorGroupedList(displayItems, state.market, state.sectorStats, onCompanyClick)
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(state = state, viewModel = viewModel, onDismiss = { showFilters = false })
    }
}

@Composable
private fun SectorChips(state: com.lynchscreener.ui.ranking.RankingUiState, selected: String?, onSelect: (String?) -> Unit) {
    val colors = LocalLynchColors.current
    val sectors = state.items.map { it.company.sector ?: Ranking.OUTROS }.distinct().sorted()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Todos") },
            )
        }
        items(sectors) { sector ->
            FilterChip(
                selected = selected == sector,
                onClick = { onSelect(if (selected == sector) null else sector) },
                label = { Text(sector) },
                leadingIcon = {
                    Icon(sectorIcon(sector), null, Modifier.size(14.dp), tint = colors.textSecondary)
                },
            )
        }
    }
}

@Composable
private fun SkeletonList() {
    val colors = LocalLynchColors.current
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(6) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .shimmerEffect()
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp)),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = LocalLynchColors.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nenhuma ação ainda", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Toque em Sincronizar para buscar os dados do Fundamentus e da SEC.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun GeneralList(state: com.lynchscreener.ui.ranking.RankingUiState, onCompanyClick: (String) -> Unit) {
    val market = state.mitems: List<RankingItem>, market: Market, onCompanyClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.company.ticker }) { item ->
            StockRankCard(item = item, market = market, onClick = { onCompanyClick(item.company.ticker) })
        }
    }
}

@Composable
private fun SectorGroupedList(
    items: List<RankingItem>,
    market: Market,
    sectorStats: Map<String, SectorStats>,
    onCompanyClick: (String) -> Unit,
) {
    val groups = items.groupBy { it.company.sector ?: Ranking.OUTROS }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEach { (sector, itemsInSector) ->
            val isExpanded = expanded[sector] ?: true
            item(key = "header_$sector") {
                SectorHeader(sector, itemsInSector, 
                }
            }
            if (isExpanded) {
                items(itemsInSector, key = { "item_${it.company.ticker}" }) { item ->
                    StockRankCard(item = item, market = market, onClick = { onCompanyClick(item.company.ticker) })
                }
            }
        }
    }
}

@Composable
private fun SectorHeader(
    sector: String,
    items: List<RankingItem>,
    stats: SectorStats?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalLynchColors.current
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(sectorIcon(sector), null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(sector, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("${items.size}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Spacer(Modifier.weight(1f))
        if (stats != null) {
            Text(
                "P/L méd. ${Formatters.ratio(stats.avgPe)} · ROE méd. ${Formatters.percent(stats.avgRoe)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: com.lynchscreener.ui.ranking.RankingUiState,
    viewModel: RankingViewModel,
    onDismiss: () -> Unit,
) {
    val colors = LocalLynchColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Ordenar por", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SortBy.entries.forEach { option ->
                val selected = state.sortBy == option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setSortBy(option); onDismiss() }
                        .padding(vertical = 10.dp),
                ) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium, color = if (selected) colors.accentPrimary else colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    if (selected) Text("✓", color = colors.accentPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Filtros rápidos", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FilterSwitch("Apenas caixa líquido", state.filters.onlyNetCash, viewModel::setOnlyNetCash)
            FilterSwitch("PEGY < 1,0", state.filters.pegyBelowOne, viewModel::setPegyBelowOne)
            FilterSwitch("Ocultar P/L negativo", state.filters.hideNegativePe, viewModel::setHideNegativePe)
        }
    }
}

@Composable
private fun FilterSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalLynchColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
