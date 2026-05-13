package com.avangard.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.avangard.app.feature.habits.HabitTrackerScreen

@Composable
fun AvangardNavHost(startDestination: String = NavRoute.Habits.route) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoute.Habits.route) { HabitTrackerScreen() }
    }
}
