package com.avangard.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.avangard.app.feature.dashboard.DashboardScreen
import com.avangard.app.feature.report.evening.EveningReportScreen
import com.avangard.app.feature.report.morning.MorningReportScreen

@Composable
fun AvangardNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = NavRoute.Dashboard.route) {
        composable(NavRoute.Dashboard.route) {
            DashboardScreen(
                onOpenMorningReport = { navController.navigate(NavRoute.MorningReport.route) },
                onOpenEveningReport = { navController.navigate(NavRoute.EveningReport.route) },
            )
        }
        composable(NavRoute.MorningReport.route) {
            MorningReportScreen(onCompleted = { navController.popBackStack() })
        }
        composable(NavRoute.EveningReport.route) {
            EveningReportScreen(onCompleted = { navController.popBackStack() })
        }
    }
}
