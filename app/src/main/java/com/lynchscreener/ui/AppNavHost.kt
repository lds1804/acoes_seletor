package com.lynchscreener.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lynchscreener.di.AppContainer
import com.lynchscreener.ui.detail.DetailViewModel
import com.lynchscreener.ui.ranking.RankingViewModel
import com.lynchscreener.ui.screens.PortfolioScreen
import com.lynchscreener.ui.screens.RankingScreen
import com.lynchscreener.ui.screens.StockDetailScreen
import com.lynchscreener.ui.screens.SyncScreen
import com.lynchscreener.ui.sync.SyncViewModel
import com.lynchscreener.ui.theme.LocalLynchColors

private object Routes {
    const val RANKING = "ranking"
    const val SYNC = "sync"
    const val PORTFOLIO = "portfolio"
    const val DETAIL = "detail/{ticker}"

    fun detail(ticker: String) = "detail/$ticker"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val factory = remember(container) { AppViewModelFactory(container) }
    val colors = LocalLynchColors.current

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = listOf(
        BottomTab(Routes.RANKING, "Ranking", Icons.Rounded.Leaderboard),
        BottomTab(Routes.SYNC, "Sincronizar", Icons.Rounded.Sync),
        BottomTab(Routes.PORTFOLIO, "Carteira", Icons.Rounded.PieChart),
    )
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = colors.surfaceCard) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accentPrimary,
                                selectedTextColor = colors.textPrimary,
                                indicatorColor = colors.surfaceElevated,
                                unselectedIconColor = colors.textMuted,
                                unselectedTextColor = colors.textMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RANKING,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.RANKING) {
                val vm: RankingViewModel = viewModel(factory = factory)
                RankingScreen(
                    viewModel = vm,
                    onCompanyClick = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.SYNC) {
                val vm: SyncViewModel = viewModel(factory = factory)
                SyncScreen(viewModel = vm)
            }
            composable(Routes.PORTFOLIO) {
                PortfolioScreen()
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("ticker") { type = NavType.StringType }),
            ) {
                val vm: DetailViewModel = viewModel(factory = factory)
                StockDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
