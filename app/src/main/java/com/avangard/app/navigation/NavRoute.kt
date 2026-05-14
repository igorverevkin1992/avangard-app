package com.avangard.app.navigation

sealed interface NavRoute {
    val route: String

    data object OperatorPulpit : NavRoute { override val route = "pulpit" }
    data object AuthorisationModal : NavRoute { override val route = "pulpit/authorise" }
    data object EarnedPride : NavRoute { override val route = "pulpit/earned-pride" }
    data object Sabotage : NavRoute { override val route = "sabotage" }
}
