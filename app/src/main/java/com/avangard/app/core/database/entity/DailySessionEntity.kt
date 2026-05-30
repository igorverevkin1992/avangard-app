package com.avangard.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per operational day (PK = start-of-day epoch ms).
 * Holds the entire daily ledger: MVD toggle, Core status, Infra statuses for
 * 02-05, evening close + virtues, and the Sunday audit bottleneck.
 */
@Entity(tableName = "daily_session")
data class DailySessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "date_epoch") val dateEpoch: Long,

    @ColumnInfo(name = "mvd_active") val mvdActive: Int = 0,

    /** 0 = Idle, 1 = Approved, 2 = Failed. */
    @ColumnInfo(name = "core_status") val coreStatus: Int = 0,
    @ColumnInfo(name = "core_prompt") val corePrompt: String? = null,
    @ColumnInfo(name = "core_authorized_at") val coreAuthorizedAt: Long? = null,
    /** 0 = Defect, 1 = Waste; null until evening close on a Failed day. */
    @ColumnInfo(name = "core_defect_kind") val coreDefectKind: Int? = null,
    /**
     * `CoreMode.name()` ("Standard" / "Mvd") for an Approved Core. NULL on Idle/Failed
     * days. Migrated from legacy `mvd_active` flag in MIGRATION_6_7.
     */
    @ColumnInfo(name = "core_mode") val coreMode: String? = null,

    /** Each: 0 = NotDone, 1 = Standard, 2 = MVD. */
    @ColumnInfo(name = "infra_02_status") val infra02Status: Int = 0,
    @ColumnInfo(name = "infra_03_status") val infra03Status: Int = 0,
    @ColumnInfo(name = "infra_04_status") val infra04Status: Int = 0,
    @ColumnInfo(name = "infra_05_status") val infra05Status: Int = 0,

    @ColumnInfo(name = "evening_closed") val eveningClosed: Int = 0,
    @ColumnInfo(name = "evening_closed_at") val eveningClosedAt: Long? = null,

    /** −1 / 0 / +1 per virtue; null until evening close. */
    @ColumnInfo(name = "virt_rationality") val virtRationality: Int? = null,
    @ColumnInfo(name = "virt_independence") val virtIndependence: Int? = null,
    @ColumnInfo(name = "virt_honesty") val virtHonesty: Int? = null,
    @ColumnInfo(name = "virt_justice") val virtJustice: Int? = null,

    /** Stored as Bottleneck.name() string; set only on Sunday audit close. */
    @ColumnInfo(name = "bottleneck_for_next_week") val bottleneckForNextWeek: String? = null,

    /**
     * Free-text day journal. Capped at 500 chars in the use-case layer
     * — the column itself is plain TEXT; old rows carry NULL until the
     * operator writes one.
     */
    @ColumnInfo(name = "journal_entry") val journalEntry: String? = null,

    /**
     * `BottleneckFollowup.name()` (Yes / Partial / No) recording the verdict
     * on the bottleneck set on last Sunday's audit. NULL until the next
     * Sunday closes. Lets the audit screen surface a one-row PDCA close.
     */
    @ColumnInfo(name = "bottleneck_followup") val bottleneckFollowup: String? = null,
)
