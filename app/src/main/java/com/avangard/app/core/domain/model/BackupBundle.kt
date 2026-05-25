package com.avangard.app.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Wire format for SAF JSON backup. Versioned so future migrations can refuse
 * unsupported snapshots gracefully. Stays plain — no Compose-friendly state,
 * no derived properties — entities are flat for the serializer.
 */
@Serializable
data class BackupBundle(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val dailySessions: List<BackupDailySession>,
    val focusSessions: List<BackupFocusSession>,
    val habitLogs: List<BackupHabitLog>,
    val chronometer: ChronometerBackup? = null,
) {
    companion object {
        /** v2 adds the optional chronometer block; v1 snapshots restore with null. */
        const val SCHEMA_VERSION = 2
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
    }
}

@Serializable
data class ChronometerBackup(
    val birthdayEpochDay: Long? = null,
    val lifeExpectancyYears: Int = 80,
    val ignitionEnabled: Boolean = true,
    val ignitionHour: Int = 6,
    val ignitionMinute: Int = 0,
)

@Serializable
data class BackupDailySession(
    val dateEpoch: Long,
    val mvdActive: Int = 0,
    val coreStatus: Int = 0,
    val corePrompt: String? = null,
    val coreAuthorizedAt: Long? = null,
    val coreDefectKind: Int? = null,
    val infra02Status: Int = 0,
    val infra03Status: Int = 0,
    val infra04Status: Int = 0,
    val infra05Status: Int = 0,
    val eveningClosed: Int = 0,
    val eveningClosedAt: Long? = null,
    val virtRationality: Int? = null,
    val virtIndependence: Int? = null,
    val virtHonesty: Int? = null,
    val virtJustice: Int? = null,
    val bottleneckForNextWeek: String? = null,
    val journalEntry: String? = null,
)

@Serializable
data class BackupFocusSession(
    val id: Long,
    val dateEpoch: Long,
    val habitCode: String,
    val startedAt: Long,
    val endedAt: Long? = null,
)

@Serializable
data class BackupHabitLog(
    val dateEpoch: Long,
    val habitCode: String,
    val completedAt: Long,
)
