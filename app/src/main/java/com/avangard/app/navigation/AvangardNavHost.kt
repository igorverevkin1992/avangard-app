package com.avangard.app.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.avangard.app.feature.audit.SundayAuditScreen
import com.avangard.app.feature.closing.EveningCloseScreen
import com.avangard.app.feature.habits.HabitTrackerScreen
import com.avangard.app.feature.locked.HistoryGateViewModel
import com.avangard.app.feature.locked.WeekdayLockScreen
import com.avangard.app.feature.pulpit.AuthorisationModalScreen
import com.avangard.app.feature.pulpit.EarnedPrideScreen
import com.avangard.app.feature.pulpit.OperatorPulpitScreen
import com.avangard.app.feature.sabotage.SabotageProtocolScreen
import com.avangard.app.feature.settings.SettingsScreen

@Composable
fun AvangardNavHost(startDestination: String = NavRoute.OperatorPulpit.route) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
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
            HistoryGate {
                SundayAuditScreen(
                    onOpenHistory = { navController.navigate(NavRoute.HistoryGrid.route) },
                )
            }
        }
        composable(NavRoute.HistoryGrid.route) {
            HistoryGate { HabitTrackerScreen() }
        }
        composable(NavRoute.WeekdayLock.route) {
            WeekdayLockScreen(onReturn = { navController.popBackStack() })
        }
    }
}

@Composable
private fun HistoryGate(content: @Composable () -> Unit) {
    val viewModel = hiltViewModel<HistoryGateViewModel>()
    if (viewModel.isUnlocked()) content() else WeekdayLockScreen(onReturn = { /* user uses system back */ })
}
