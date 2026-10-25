package com.itermeum.app.navigation

import android.net.Uri

/**
 * Section 9 routes. Top-level tabs are the four `Tab` entries; everything
 * else is reached from them or from a deep-link (`itermeum://…`).
 */
sealed class NavRoute(val route: String) {
    data object Today : NavRoute("today")
    data object Work : NavRoute("work")
    data object Prayer : NavRoute("prayer")
    data object Path : NavRoute("path")
    data object Settings : NavRoute("settings")
    data object About : NavRoute("about")
    data object Onboarding : NavRoute("onboarding")

    companion object {
        val tabs: List<NavRoute> = listOf(Today, Work, Prayer, Path)

        /** `itermeum://today` → "today". Unknown hosts → null (keep the current screen). */
        fun fromDeepLink(uri: Uri?): String? {
            if (uri == null || uri.scheme != SCHEME) return null
            return when (uri.host) {
                "today" -> Today.route
                "settings" -> Settings.route
                "work", "outreach", "laststone" -> Work.route
                "prayer", "angelus", "rosary", "examen" -> Prayer.route
                "path", "catechesis" -> Path.route
                else -> null
            }
        }

        const val SCHEME = "itermeum"
    }
}
