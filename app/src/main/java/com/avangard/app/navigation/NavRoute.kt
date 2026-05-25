package com.avangard.app.navigation

sealed interface NavRoute {
    val route: String

    data object SignIn : NavRoute { override val route = "auth/signin" }
    data object Restoring : NavRoute { override val route = "auth/restoring" }
    data object OperatorPulpit : NavRoute { override val route = "pulpit" }
    data object AuthorisationModal : NavRoute { override val route = "pulpit/authorise" }
    data object EarnedPride : NavRoute { override val route = "pulpit/earned-pride" }
    data object Sabotage : NavRoute { override val route = "sabotage" }
    data object EveningClose : NavRoute { override val route = "closing" }
    data object SundayAudit : NavRoute { override val route = "audit" }
    data object HistoryGrid : NavRoute { override val route = "history" }
    data object Settings : NavRoute { override val route = "settings" }
    data object Library : NavRoute { override val route = "library" }
    data object Chronometer : NavRoute { override val route = "chronometer" }

    /** Library → list of quotes filtered by a single VirtueTag.
     *  Argument: VirtueTag.name. */
    data object VirtueQuotes : NavRoute {
        override val route = "library/virtue/{virtue}"
        const val ARG_VIRTUE = "virtue"
        fun create(virtue: String): String = "library/virtue/$virtue"
    }

    /** Library → single quote detail by id. */
    data object QuoteDetail : NavRoute {
        override val route = "library/quote/{id}"
        const val ARG_ID = "id"
        fun create(id: Int): String = "library/quote/$id"
    }
}
