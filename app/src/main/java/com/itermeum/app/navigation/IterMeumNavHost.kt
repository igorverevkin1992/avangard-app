package com.itermeum.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.itermeum.app.feature.about.AboutScreen
import com.itermeum.app.feature.onboarding.OnboardingScreen
import com.itermeum.app.feature.path.CatechesisScreen
import com.itermeum.app.feature.path.ChronicleScreen
import com.itermeum.app.feature.path.MilestoneScreen
import com.itermeum.app.feature.path.MonthSummaryScreen
import com.itermeum.app.feature.path.PathScreen
import com.itermeum.app.feature.path.QuarterScreen
import com.itermeum.app.feature.path.WeeklyReviewScreen
import com.itermeum.app.feature.examen.ExamenScreen
import com.itermeum.app.feature.prayer.AngelusScreen
import com.itermeum.app.feature.prayer.CalendarScreen
import com.itermeum.app.feature.prayer.GospelScreen
import com.itermeum.app.feature.prayer.JaculatoriaScreen
import com.itermeum.app.feature.prayer.MassScreen
import com.itermeum.app.feature.prayer.MentalPrayerScreen
import com.itermeum.app.feature.prayer.PrayerBookScreen
import com.itermeum.app.feature.prayer.PrayerItemScreen
import com.itermeum.app.feature.prayer.PrayerScreen
import com.itermeum.app.feature.prayer.RosaryScreen
import com.itermeum.app.feature.prayer.SrsScreen
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
        composable(NavRoute.Prayer.route) {
            PrayerScreen(
                onBook = { navController.navigate(NavRoute.PrayerBook.route) },
                onRosary = { navController.navigate(NavRoute.PrayerRosary.route) },
                onJaculatory = { navController.navigate(NavRoute.PrayerJaculatory.of(null)) },
                onGospel = { navController.navigate(NavRoute.PrayerGospel.route) },
                onSrs = { navController.navigate(NavRoute.PrayerSrs.route) },
                onCalendar = { navController.navigate("prayer/calendar") },
                onMental = { navController.navigate(NavRoute.PrayerMental.route) },
                onExamen = { navController.navigate(NavRoute.Examen.route) },
                onAngelus = { navController.navigate(NavRoute.PrayerAngelus.route) },
                onMass = { navController.navigate(NavRoute.PrayerMass.route) },
            )
        }
        composable(NavRoute.PrayerBook.route) { PrayerBookScreen(onBack = { navController.popBackStack() }, onOpen = { navController.navigate(NavRoute.PrayerItem.of(it)) }) }
        composable(NavRoute.PrayerItem.route) { entry -> PrayerItemScreen(id = entry.arguments?.getString("id") ?: "", onBack = { navController.popBackStack() }) }
        composable(NavRoute.PrayerRosary.route) { RosaryScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.PrayerJaculatory.route, arguments = listOf(navArgument("situation") { nullable = true; defaultValue = null })) { entry ->
            JaculatoriaScreen(initialSituation = entry.arguments?.getString("situation"), onBack = { navController.popBackStack() })
        }
        composable(NavRoute.PrayerGospel.route) { GospelScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.PrayerSrs.route) { SrsScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.PrayerCalendar.route, arguments = listOf(navArgument("ym") { nullable = true; defaultValue = null })) { entry ->
            CalendarScreen(yearMonth = entry.arguments?.getString("ym"), onBack = { navController.popBackStack() })
        }
        composable(NavRoute.PrayerMental.route) { MentalPrayerScreen(onBack = { navController.popBackStack() }, gospelTitle = null) }
        composable(NavRoute.PrayerAngelus.route) { AngelusScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.PrayerMass.route) { MassScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.Examen.route) { ExamenScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.Path.route) {
            PathScreen(
                onCatechesis = { navController.navigate(NavRoute.PathCatechesis.of(it)) },
                onReview = { navController.navigate(NavRoute.PathReview.route) },
                onMonth = { navController.navigate(NavRoute.PathMonth.route) },
                onQuarter = { navController.navigate(NavRoute.PathQuarter.route) },
                onChronicle = { navController.navigate(NavRoute.PathChronicle.route) },
            )
        }
        composable(NavRoute.PathCatechesis.route, arguments = listOf(navArgument("noteId") { nullable = true; defaultValue = null })) { entry ->
            CatechesisScreen(noteId = entry.arguments?.getString("noteId")?.toLongOrNull(), onBack = { navController.popBackStack() })
        }
        composable(NavRoute.PathReview.route) { WeeklyReviewScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.PathMonth.route) { MonthSummaryScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.PathQuarter.route) { QuarterScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.PathChronicle.route) { ChronicleScreen(onBack = { navController.popBackStack() }) }
        composable(NavRoute.Milestone.route) { entry -> MilestoneScreen(id = entry.arguments?.getString("id") ?: "", onDone = { navController.popBackStack() }) }
        composable(NavRoute.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(NavRoute.About.route) },
            )
        }
        composable(NavRoute.About.route) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}
