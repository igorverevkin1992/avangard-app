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
    /** Operator-defined criteria per habit code; v3+. */
    val habitStandards: Map<String, HabitStandard>? = null,
) {
    companion object {
        /**
         * v2 added the optional chronometer block; v1 snapshots restore with null.
         * v3 adds `coreMode` on each BackupDailySession (per-Core Standard/Mvd flag,
         * replacing the legacy day-wide `mvdActive`). v1/v2 snapshots restore with
         * coreMode = null and RoomBackupRepository.toEntity() backfills from
         * mvdActive — see MIGRATION_6_7 for the equivalent on-disk logic.
         */
        const val SCHEMA_VERSION = 3
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
    /**
     * Legacy day-wide MVD flag. Kept on the wire so v1/v2 snapshots survive
     * a round-trip, but the meaningful information is now [coreMode] on
     * Approved Cores. RoomBackupRepository.toEntity() back-fills coreMode
     * from mvdActive when [coreMode] is missing (v1/v2 snapshots).
     */
    val mvdActive: Int = 0,
    val coreStatus: Int = 0,
    val corePrompt: String? = null,
    val coreAuthorizedAt: Long? = null,
    val coreDefectKind: Int? = null,
    /** "Standard" / "Mvd" / null — null on legacy snapshots, back-filled on restore. */
    val coreMode: String? = null,
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
    /** Operator's verdict on last week's bottleneck (Yes/Partial/No); v3+. */
    val bottleneckFollowup: String? = null,
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
