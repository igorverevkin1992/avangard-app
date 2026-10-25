package com.itermeum.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.itermeum.app.feature.about.AboutScreen
import com.itermeum.app.feature.path.PathScreen
import com.itermeum.app.feature.prayer.PrayerScreen
import com.itermeum.app.feature.settings.SettingsScreen
import com.itermeum.app.feature.today.TodayScreen
import com.itermeum.app.feature.work.WorkScreen

@Composable
fun IterMeumNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = NavRoute.Today.route) {
        composable(NavRoute.Today.route) {
            TodayScreen(onOpenSettings = { navController.navigate(NavRoute.Settings.route) })
        }
        composable(NavRoute.Work.route) { WorkScreen() }
        composable(NavRoute.Prayer.route) { PrayerScreen() }
        composable(NavRoute.Path.route) { PathScreen() }
        composable(NavRoute.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(NavRoute.About.route) },
            )
        }
        composable(NavRoute.About.route) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}
