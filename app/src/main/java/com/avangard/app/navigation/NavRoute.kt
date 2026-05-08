package com.avangard.app.navigation

sealed interface NavRoute {
    val route: String

    data object Dashboard : NavRoute { override val route = "dashboard" }
    data object MorningReport : NavRoute { override val route = "report/morning" }
    data object EveningReport : NavRoute { override val route = "report/evening" }
    data object Analytics : NavRoute { override val route = "analytics" }
    data object Settings : NavRoute { override val route = "settings" }
}
