package com.itermeum.app.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.itermeum.app.R

private data class Tab(val route: NavRoute, val labelRes: Int, val iconRes: Int)

private val tabs = listOf(
    Tab(NavRoute.Today, R.string.nav_today, R.drawable.ic_tab_today),
    Tab(NavRoute.Work, R.string.nav_work, R.drawable.ic_tab_work),
    Tab(NavRoute.Prayer, R.string.nav_prayer, R.drawable.ic_tab_prayer),
    Tab(NavRoute.Path, R.string.nav_path, R.drawable.ic_tab_path),
)

@Composable
fun shouldShowBottomNav(navController: NavHostController): Boolean {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: return true
    return tabs.any { it.route.route == route } || (route.startsWith("work/") || route.startsWith("prayer/") || route.startsWith("path/")) && route != "ritual" && !route.startsWith("milestone/")
}

@Composable
fun IterMeumNavigationBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val scheme = MaterialTheme.colorScheme
    val goldDim = scheme.outlineVariant
    NavigationBar(
        modifier = modifier.drawBehind { drawLine(goldDim, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f) },
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) return@NavigationBarItem
                    navController.navigate(tab.route.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = stringResource(tab.labelRes),
                    )
                },
                label = { Text(stringResource(tab.labelRes).uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = scheme.tertiary,
                    selectedTextColor = scheme.onSurface,
                    indicatorColor = scheme.surfaceVariant,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                ),
            )
        }
    }
}
