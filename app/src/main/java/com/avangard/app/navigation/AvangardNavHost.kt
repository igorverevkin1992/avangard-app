package com.avangard.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.avangard.app.feature.analytics.AnalyticsScreen
import com.avangard.app.feature.checkpoint.MidDayCheckpointScreen
import com.avangard.app.feature.dashboard.DashboardScreen
import com.avangard.app.feature.habits.HabitTrackerScreen
import com.avangard.app.feature.report.evening.EveningReportScreen
import com.avangard.app.feature.report.morning.MorningReportScreen
import com.avangard.app.feature.settings.SettingsScreen

@Composable
fun AvangardNavHost(startDestination: String = NavRoute.Dashboard.route) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoute.Dashboard.route) {
            DashboardScreen(
                onOpenMorningReport = { navController.navigate(NavRoute.MorningReport.route) },
                onOpenEveningReport = { navController.navigate(NavRoute.EveningReport.route) },
                onOpenHabits = { navController.navigate(NavRoute.Habits.route) },
                onOpenAnalytics = { navController.navigate(NavRoute.Analytics.route) },
                onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
            )
        }
        composable(NavRoute.MorningReport.route) {
            MorningReportScreen(onCompleted = { navController.popBackStack() })
        }
        composable(NavRoute.MidDayCheckpoint.route) {
            MidDayCheckpointScreen(onCompleted = {
                if (!navController.popBackStack()) {
                    navController.navigate(NavRoute.Dashboard.route) {
                        popUpTo(NavRoute.MidDayCheckpoint.route) { inclusive = true }
                    }
                }
            })
        }
        composable(NavRoute.EveningReport.route) {
            EveningReportScreen(onCompleted = { navController.popBackStack() })
        }
        composable(NavRoute.Habits.route) { HabitTrackerScreen() }
        composable(NavRoute.Analytics.route) { AnalyticsScreen() }
        composable(NavRoute.Settings.route) { SettingsScreen() }
    }
}
