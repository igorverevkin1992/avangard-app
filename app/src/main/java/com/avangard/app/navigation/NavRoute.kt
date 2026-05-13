package com.avangard.app.navigation

sealed interface NavRoute {
    val route: String

    data object Habits : NavRoute { override val route = "habits" }
}
