package com.avangard.app.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Operator-defined criteria for what counts as «СТАНДАРТ» vs «МИНИМУМ»
 * for a given habit. Free-form text — the app makes no attempt to parse
 * or enforce the criteria; the value is in writing them down so the
 * status buttons stop being ambiguous after a few weeks of use.
 *
 * Persisted as a Map<HabitCode, HabitStandard> JSON inside the user
 * preferences DataStore; round-trips through BackupBundle as an optional
 * top-level map.
 */
@Serializable
data class HabitStandard(
    val standard: String = "",
    val mvd: String = "",
) {
    val isEmpty: Boolean get() = standard.isBlank() && mvd.isBlank()
}
