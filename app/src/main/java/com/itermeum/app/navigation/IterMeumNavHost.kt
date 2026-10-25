package com.itermeum.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.itermeum.app.feature.about.AboutScreen
import com.itermeum.app.feature.onboarding.OnboardingScreen
import com.itermeum.app.feature.path.PathScreen
import com.itermeum.app.feature.prayer.PrayerScreen
import com.itermeum.app.feature.settings.SettingsScreen
import com.itermeum.app.feature.today.TodayScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.itermeum.app.feature.work.DayPlanScreen
import com.itermeum.app.feature.work.LastStoneScreen
import com.itermeum.app.feature.work.OutreachScreen
import com.itermeum.app.feature.work.ProjectDetailScreen
import com.itermeum.app.feature.work.ProjectsScreen
import com.itermeum.app.feature.work.StudioScreen
import com.itermeum.app.feature.work.TransitionRitualScreen
import com.itermeum.app.feature.work.WorkScreen

@Composable
fun IterMeumNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoute.Onboarding.route) {
            OnboardingScreen(onFinished = {
                navController.navigate(NavRoute.Today.route) { popUpTo(NavRoute.Onboarding.route) { inclusive = true } }
            })
        }
        composable(NavRoute.Today.route) {
            TodayScreen(onOpenSettings = { navController.navigate(NavRoute.Settings.route) })
        }
        composable(NavRoute.Work.route) {
            WorkScreen(
                onPlan = { navController.navigate(NavRoute.WorkPlan.route) },
                onOutreach = { navController.navigate(NavRoute.WorkOutreach.route) },
                onLastStone = { navController.navigate(NavRoute.WorkLastStone.route) },
                onProjects = { navController.navigate(NavRoute.WorkProjects.route) },
                onStudio = { navController.navigate(NavRoute.WorkStudio.route) },
                onRitual = { navController.navigate(NavRoute.Ritual.route) },
            )
        }
        composable(NavRoute.WorkPlan.route) { DayPlanScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.WorkOutreach.route) { OutreachScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.WorkLastStone.route) { LastStoneScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.WorkProjects.route) {
            ProjectsScreen(onBack = { navController.popBackStack() }, onOpen = { navController.navigate(NavRoute.WorkProject.of(it)) })
        }
        composable(NavRoute.WorkProject.route, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ProjectDetailScreen(id = entry.arguments?.getLong("id") ?: 0L, onBack = { navController.popBackStack() })
        }
        composable(NavRoute.WorkStudio.route) { StudioScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.Ritual.route) { TransitionRitualScreen(onDone = { navController.popBackStack() }) }
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
