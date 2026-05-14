package com.avangard.app.navigation

sealed interface NavRoute {
    val route: String

    data object OperatorPulpit : NavRoute { override val route = "pulpit" }
    data object AuthorisationModal : NavRoute { override val route = "pulpit/authorise" }
    data object EarnedPride : NavRoute { override val route = "pulpit/earned-pride" }
    data object Sabotage : NavRoute { override val route = "sabotage" }
    data object EveningClose : NavRoute { override val route = "closing" }
    data object SundayAudit : NavRoute { override val route = "audit" }
    data object HistoryGrid : NavRoute { override val route = "history" }
    data object Settings : NavRoute { override val route = "settings" }
}
