package com.avangard.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.avangard.app.feature.pulpit.AuthorisationModalScreen
import com.avangard.app.feature.pulpit.EarnedPrideScreen
import com.avangard.app.feature.pulpit.OperatorPulpitScreen
import com.avangard.app.feature.sabotage.SabotageProtocolScreen

@Composable
fun AvangardNavHost(startDestination: String = NavRoute.OperatorPulpit.route) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoute.OperatorPulpit.route) {
            OperatorPulpitScreen(
                onOpenAuthorisation = { navController.navigate(NavRoute.AuthorisationModal.route) },
                onOpenSabotage = { navController.navigate(NavRoute.Sabotage.route) },
                // EveningClose lands in commit 5; until then close-shift is a no-op
                // intentionally — pressing the footer button does nothing.
                onOpenEveningClose = { /* commit 5 */ },
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
    }
}
