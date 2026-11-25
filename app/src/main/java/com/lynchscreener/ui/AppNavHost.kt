package com.lynchscreener.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.lynchscreener.ui.detail.DetailScreen
import com.lynchscreener.ui.detail.DetailViewModel
import com.lynchscreener.ui.ranking.RankingScreen
import com.lynchscreener.ui.ranking.RankingViewModel
import com.lynchscreener.ui.sync.SyncScreen
import com.lynchscreener.ui.sync.SyncViewModel

private object Routes {
    const val RANKING = "ranking"
    const val SYNC = "sync"
    const val DETAIL = "detail/{ticker}"

    fun detail(ticker: String) = "detail/$ticker"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val factory = remember(container) { AppViewModelFactory(container) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute == Routes.RANKING || currentRoute == Routes.SYNC

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.RANKING,
                        onClick = {
                            navController.navigate(Routes.RANKING) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) },
                        label = { Text("Ranking") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SYNC,
                        onClick = {
                            navController.navigate(Routes.SYNC) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        label = { Text("Sincronizar") },
                    )
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
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("ticker") { type = NavType.StringType }),
            ) {
                val vm: DetailViewModel = viewModel(factory = factory)
                DetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
