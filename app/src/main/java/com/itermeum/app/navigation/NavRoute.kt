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
    data object WorkPlan : NavRoute("work/plan")
    data object WorkOutreach : NavRoute("work/outreach")
    data object WorkLastStone : NavRoute("work/laststone")
    data object WorkProjects : NavRoute("work/projects")
    data object WorkProject : NavRoute("work/project/{id}") { fun of(id: Long) = "work/project/$id" }
    data object WorkStudio : NavRoute("work/studio")
    data object Ritual : NavRoute("ritual")
    data object PrayerBook : NavRoute("prayer/book")
    data object PrayerItem : NavRoute("prayer/item/{id}") { fun of(id: String) = "prayer/item/$id" }
    data object PrayerRosary : NavRoute("prayer/rosary")
    data object PrayerJaculatory : NavRoute("prayer/jaculatory?situation={situation}") { fun of(s: String?) = "prayer/jaculatory" + (s?.let { "?situation=$it" } ?: "") }
    data object PrayerGospel : NavRoute("prayer/gospel")
    data object PrayerSrs : NavRoute("prayer/srs")
    data object PrayerCalendar : NavRoute("prayer/calendar?ym={ym}")
    data object PrayerMental : NavRoute("prayer/mental")
    data object PrayerAngelus : NavRoute("prayer/angelus")
    data object PrayerMass : NavRoute("prayer/mass")
    data object Examen : NavRoute("examen")

    companion object {
        val tabs: List<NavRoute> = listOf(Today, Work, Prayer, Path)

        /** `itermeum://today` → "today". Unknown hosts → null (keep the current screen). */
        fun fromDeepLink(uri: Uri?): String? {
            if (uri == null || uri.scheme != SCHEME) return null
            return when (uri.host) {
                "today" -> Today.route
                "settings" -> Settings.route
                "work" -> Work.route
                "outreach" -> WorkOutreach.route
                "laststone" -> WorkLastStone.route
                "ritual" -> Ritual.route
                "prayer" -> Prayer.route
                "angelus" -> PrayerAngelus.route
                "rosary" -> PrayerRosary.route
                "examen" -> Examen.route
                "mental" -> PrayerMental.route
                "reading", "gospel" -> PrayerGospel.route
                "srs" -> PrayerSrs.route
                "path", "catechesis" -> Path.route
                else -> null
            }
        }

        const val SCHEME = "itermeum"
    }
}
