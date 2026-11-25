package com.lynchscreener.ui.ranking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lynchscreener.domain.Ranking
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.RankingItem
import com.lynchscreener.domain.model.SectorStats
import com.lynchscreener.ui.components.MetricTile
import com.lynchscreener.ui.components.PulsingDot
import com.lynchscreener.ui.components.ScoreRing
import com.lynchscreener.ui.components.SectorBadge
import com.lynchscreener.ui.components.SegmentedControl
import com.lynchscreener.ui.components.ShimmerBox
import com.lynchscreener.ui.format.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    viewModel: RankingViewModel,
    onCompanyClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lynch Screener", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        PulsingDot(
                            color = if (syncStatus.isSyncing) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SyncBanner(isSyncing = syncStatus.isSyncing, hasWork = syncStatus.hasWork)

            // Market selector
            Row(Modifier.padding(horizontal = 16.dp)) {
                SegmentedControl(
                    options = listOf("Brasil (B3)", "EUA (S&P 500)"),
                    selectedIndex = if (state.market == Market.BR) 0 else 1,
                    onSelect = { viewModel.setMarket(if (it == 0) Market.BR else Market.US) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Search + sort
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Buscar ticker ou empresa…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Limpar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SortMenu(current = state.sortBy, onSelect = viewModel::setSortBy)
            }

            Spacer(Modifier.height(10.dp))

            // View mode
            Row(Modifier.padding(horizontal = 16.dp)) {
                SegmentedControl(
                    options = listOf("Ranking geral", "Por setor"),
                    selectedIndex = if (state.viewMode == ViewMode.GENERAL) 0 else 1,
                    onSelect = { viewModel.setViewMode(if (it == 0) ViewMode.GENERAL else ViewMode.BY_SECTOR) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(10.dp))

            Crossfade(
                targetState = state.isLoading,
                animationSpec = tween(300),
                label = "loadingCrossfade",
            ) { loading ->
                when {
                    loading -> LoadingList()
                    state.items.isEmpty() -> EmptyState()
                    state.viewMode == ViewMode.GENERAL -> GeneralList(state, onCompanyClick)
                    else -> SectorView(state, onCompanyClick)
                }
            }
        }
    }
}

@Composable
private fun SyncBanner(isSyncing: Boolean, hasWork: Boolean) {
    AnimatedVisibility(
        visible = isSyncing || hasWork,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot(color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isSyncing) "Sincronizando em segundo plano…" else "Sincronização agendada…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LoadingList() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(6) {
            ShimmerBox(Modifier.fillMaxWidth().height(120.dp))
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Nenhuma ação ainda",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Toque em Sincronizar (aba inferior) para buscar os dados do Fundamentus e da SEC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GeneralList(state: RankingUiState, onCompanyClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.items, key = { it.company.ticker }) { item ->
            CompanyCard(
                item = item,
                market = state.market,
                rankText = "#${item.overallRank}",
                subtitle = item.company.sector ?: "—",
                onClick = { onCompanyClick(item.company.ticker) },
            )
        }
    }
}

@Composable
private fun SectorView(state: RankingUiState, onCompanyClick: (String) -> Unit) {
    var selectedSector by remember(state.market) { mutableStateOf<String?>(null) }

    val sectors = state.items
        .map { it.company.sector ?: Ranking.OUTROS }
        .distinct()
        .sorted()

    val filtered = if (selectedSector == null) state.items
    else state.items.filter { (it.company.sector ?: Ranking.OUTROS) == selectedSector }

    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedSector == null,
                    onClick = { selectedSector = null },
                    label = { Text("Todos") },
                )
            }
            items(sectors) { sector ->
                FilterChip(
                    selected = selectedSector == sector,
                    onClick = { selectedSector = sector },
                    label = { Text(sector) },
                )
            }
        }

        selectedSector?.let { sector ->
            val stats = state.sectorStats[sector]
            if (stats != null) {
                SectorSummaryBar(sector = sector, stats = stats)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered, key = { it.company.ticker }) { item ->
                CompanyCard(
                    item = item,
                    market = state.market,
                    rankText = "#${item.sectorRank} no setor",
                    subtitle = item.company.sector ?: "—",
                    onClick = { onCompanyClick(item.company.ticker) },
                )
            }
        }
    }
}

@Composable
private fun SectorSummaryBar(sector: String, stats: SectorStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetricTile("Média P/L", Formatters.ratio(stats.avgPe))
        MetricTile("Média P/VP", Formatters.ratio(stats.avgPb))
        MetricTile("Média ROE", Formatters.percent(stats.avgRoe))
        MetricTile("Empresas", stats.count.toString())
    }
}

@Composable
private fun CompanyCard(
    item: RankingItem,
    market: Market,
    rankText: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = item.company
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rankText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(72.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = c.ticker,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = c.name ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ScoreRing(score = c.lynchScore)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectorBadge(subtitle)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("P/L", Formatters.ratio(c.pe), modifier = Modifier.weight(1f))
                MetricTile("P/VP", Formatters.ratio(c.pb), modifier = Modifier.weight(1f))
                MetricTile("ROE", Formatters.percent(c.roe), modifier = Modifier.weight(1f))
                MetricTile("DY", Formatters.percent(c.dividendYield), modifier = Modifier.weight(1f))
                MetricTile(
                    "PEGY",
                    Formatters.ratio(c.pegy),
                    valueColor = pegColor(c.pegy),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SortMenu(current: SortBy, onSelect: (SortBy) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(current.label, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortBy.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun pegColor(pegy: Double?): Color =
    when {
        pegy == null -> MaterialTheme.colorScheme.onSurface
        pegy < 1.0 -> com.lynchscreener.ui.theme.ScoreGreat
        pegy < 1.5 -> com.lynchscreener.ui.theme.ScoreGood
        else -> com.lynchscreener.ui.theme.ScoreBad
    }
