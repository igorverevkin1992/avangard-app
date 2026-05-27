package com.avangard.app.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.avangard.app.R
import com.avangard.app.ui.theme.IsaColors

private data class TopLevelTab(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
)

private val tabs = listOf(
    TopLevelTab(NavRoute.OperatorPulpit.route, R.string.nav_tab_pulpit, R.drawable.ic_nav_pulpit),
    TopLevelTab(NavRoute.Library.route, R.string.nav_tab_library, R.drawable.ic_nav_library),
    TopLevelTab(NavRoute.SundayAudit.route, R.string.nav_tab_audit, R.drawable.ic_nav_audit),
    TopLevelTab(NavRoute.HistoryGrid.route, R.string.nav_tab_history, R.drawable.ic_nav_history),
)

/**
 * True when the current destination is a tier-1 surface that owns the
 * bottom-nav strip. Modal routes (auth, evening close, sabotage, settings,
 * earned-pride, future library detail pages) hide the bar so the modal
 * uses the full viewport.
 */
@Composable
fun shouldShowBottomNav(navController: NavHostController): Boolean {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: return true
    return tabs.any { it.route == route }
}

@Composable
fun AvangardNavigationBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    NavigationBar(
        modifier = modifier,
        containerColor = IsaColors.Carbon,
        contentColor = IsaColors.LiveMetal,
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute == tab.route) return@NavigationBarItem
                    navController.navigate(tab.route) {
                        // Pop to the start so tab switching doesn't grow the
                        // back stack unboundedly; preserve scroll/selection
                        // state of each tab via saveState/restoreState.
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    // Material3 already wires the label to the item's a11y
                    // name, but TalkBack reads contentDescription on the icon
                    // first when focus lands on it directly via swipe — keep
                    // it populated so the label and icon describe the same
                    // thing instead of "unlabeled".
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = stringResource(tab.labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IsaColors.Approve,
                    selectedTextColor = IsaColors.Approve,
                    unselectedIconColor = IsaColors.Lattice,
                    unselectedTextColor = IsaColors.Lattice,
                    indicatorColor = IsaColors.Graphite,
                ),
            )
        }
    }
}
