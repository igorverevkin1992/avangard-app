package com.avangard.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.avangard.app.feature.audit.SundayAuditScreen
import com.avangard.app.feature.auth.RestoringScreen
import com.avangard.app.feature.auth.SignInScreen
import com.avangard.app.feature.chronometer.ChronometerScreen
import com.avangard.app.feature.closing.EveningCloseScreen
import com.avangard.app.feature.habits.HabitTrackerScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.avangard.app.feature.library.LibraryScreen
import com.avangard.app.feature.library.QuoteDetailScreen
import com.avangard.app.feature.library.VirtueQuotesScreen
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
        composable(NavRoute.SignIn.route) {
            SignInScreen(
                onSignedIn = {
                    // After login, jump to the restore overlay; it pulls the
                    // Drive snapshot and only then forwards to the pulpit.
                    // Sign-in is gone from the back stack — back-press from
                    // the pulpit closes the app instead of dumping the user
                    // back to the login wall.
                    navController.navigate(NavRoute.Restoring.route) {
                        popUpTo(NavRoute.SignIn.route) { inclusive = true }
                    }
                },
            )
        }
        composable(NavRoute.Restoring.route) {
            RestoringScreen(
                onDone = {
                    navController.navigate(NavRoute.OperatorPulpit.route) {
                        popUpTo(NavRoute.Restoring.route) { inclusive = true }
                    }
                },
            )
        }
        composable(NavRoute.OperatorPulpit.route) {
            OperatorPulpitScreen(
                onOpenAuthorisation = {
                    navController.navigate(NavRoute.AuthorisationModal.route)
                },
                onOpenSabotage = { navController.navigate(NavRoute.Sabotage.route) },
                onOpenEveningClose = { navController.navigate(NavRoute.EveningClose.route) },
                onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
                onOpenChronometer = { navController.navigate(NavRoute.Chronometer.route) },
                onOpenQuote = { id -> navController.navigate(NavRoute.QuoteDetail.create(id)) },
            )
        }
        composable(NavRoute.Settings.route) {
            SettingsScreen(onReturn = { navController.popBackStack() })
        }
        composable(NavRoute.Chronometer.route) {
            ChronometerScreen(
                onReturn = {
                    if (!navController.popBackStack()) {
                        navController.navigate(NavRoute.OperatorPulpit.route) {
                            popUpTo(NavRoute.Chronometer.route) { inclusive = true }
                        }
                    }
                },
                onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
            )
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
            // History grid (monthly habit mosaic) is reachable every day:
            // it's a read-only summary of past behaviour, not a Sunday
            // reflection ritual. Audit stays Sunday-only via HistoryGate.
            HabitTrackerScreen()
        }
        composable(NavRoute.Library.route) {
            LibraryScreen(
                onOpenVirtue = { virtue ->
                    navController.navigate(NavRoute.VirtueQuotes.create(virtue.name))
                },
                onOpenQuote = { id ->
                    navController.navigate(NavRoute.QuoteDetail.create(id))
                },
            )
        }
        composable(
            route = NavRoute.VirtueQuotes.route,
            arguments = listOf(
                navArgument(NavRoute.VirtueQuotes.ARG_VIRTUE) { type = NavType.StringType },
            ),
        ) {
            VirtueQuotesScreen(
                onBack = { navController.popBackStack() },
                onOpenQuote = { id ->
                    navController.navigate(NavRoute.QuoteDetail.create(id))
                },
            )
        }
        composable(
            route = NavRoute.QuoteDetail.route,
            arguments = listOf(
                navArgument(NavRoute.QuoteDetail.ARG_ID) { type = NavType.StringType },
            ),
        ) {
            QuoteDetailScreen(onBack = { navController.popBackStack() })
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
