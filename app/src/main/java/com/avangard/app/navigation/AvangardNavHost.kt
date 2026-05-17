package com.avangard.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.avangard.app.feature.audit.SundayAuditScreen
import com.avangard.app.feature.closing.EveningCloseScreen
import com.avangard.app.feature.habits.HabitTrackerScreen
import com.avangard.app.feature.library.LibraryPlaceholderScreen
import com.avangard.app.feature.locked.HistoryGateViewModel
import com.avangard.app.feature.locked.WeekdayLockScreen
import com.avangard.app.feature.pulpit.AuthorisationModalScreen
import com.avangard.app.feature.pulpit.EarnedPrideScreen
import com.avangard.app.feature.pulpit.OperatorPulpitScreen
import com.avangard.app.feature.sabotage.SabotageProtocolScreen
import com.avangard.app.feature.settings.SettingsScreen

@Composable
fun AvangardNavHost(
    navController: NavHostController,
    startDestination: String = NavRoute.OperatorPulpit.route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(NavRoute.OperatorPulpit.route) {
            OperatorPulpitScreen(
                onOpenAuthorisation = { navController.navigate(NavRoute.AuthorisationModal.route) },
                onOpenSabotage = { navController.navigate(NavRoute.Sabotage.route) },
                onOpenEveningClose = { navController.navigate(NavRoute.EveningClose.route) },
                onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
            )
        }
        composable(NavRoute.Settings.route) {
            SettingsScreen(onReturn = { navController.popBackStack() })
        }
        composable(NavRoute.AuthorisationModal.route) {
            AuthorisationModalScreen(
                onAuthorised = {
                    navController.navigate(NavRoute.EarnedPride.route) {
                        popUpTo(NavRoute.AuthorisationModal.route) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(NavRoute.EarnedPride.route) {
            EarnedPrideScreen(
                onAutoDismiss = {
                    navController.popBackStack(NavRoute.OperatorPulpit.route, inclusive = false)
                },
            )
        }
        composable(NavRoute.Sabotage.route) {
            SabotageProtocolScreen(onClose = { navController.popBackStack() })
        }
        composable(NavRoute.EveningClose.route) {
            EveningCloseScreen(
                onClosed = {
                    if (!navController.popBackStack()) {
                        navController.navigate(NavRoute.OperatorPulpit.route) {
                            popUpTo(NavRoute.EveningClose.route) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(NavRoute.SundayAudit.route) {
            HistoryGate(onLockedReturn = { navController.popBackStack() }) {
                SundayAuditScreen(
                    onOpenHistory = { navController.navigate(NavRoute.HistoryGrid.route) },
                    onOpenPulpit = { navController.navigate(NavRoute.OperatorPulpit.route) },
                )
            }
        }
        composable(NavRoute.HistoryGrid.route) {
            HistoryGate(onLockedReturn = { navController.popBackStack() }) {
                HabitTrackerScreen()
            }
        }
        composable(NavRoute.Library.route) {
            // Real Library screen lands in v3.6 commit 2; for now a stable
            // route placeholder so the bottom-nav tab has somewhere to land.
            LibraryPlaceholderScreen()
        }
    }
}

@Composable
private fun HistoryGate(
    onLockedReturn: () -> Unit,
    content: @Composable () -> Unit,
) {
    val viewModel = hiltViewModel<HistoryGateViewModel>()
    if (viewModel.isUnlocked()) content() else WeekdayLockScreen(onReturn = onLockedReturn)
}
