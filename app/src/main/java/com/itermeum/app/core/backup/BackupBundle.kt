package com.itermeum.app.core.backup

import com.itermeum.app.core.database.entity.CatechesisNoteEntity
import com.itermeum.app.core.database.entity.DayPlanEntity
import com.itermeum.app.core.database.entity.ExamenEntity
import com.itermeum.app.core.database.entity.FormationEventEntity
import com.itermeum.app.core.database.entity.FreezeLedgerEntity
import com.itermeum.app.core.database.entity.JaculatoriaLogEntity
import com.itermeum.app.core.database.entity.MemorizationItemEntity
import com.itermeum.app.core.database.entity.MilestoneEntity
import com.itermeum.app.core.database.entity.MonthlySummaryViewEntity
import com.itermeum.app.core.database.entity.MorningStateEntity
import com.itermeum.app.core.database.entity.MortificationLogEntity
import com.itermeum.app.core.database.entity.NormCompletionEntity
import com.itermeum.app.core.database.entity.NormEntity
import com.itermeum.app.core.database.entity.OutreachLogEntity
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.core.database.entity.ProjectEntity
import com.itermeum.app.core.database.entity.QuarterGoalEntity
import com.itermeum.app.core.database.entity.QuarterIncomeEntity
import com.itermeum.app.core.database.entity.ReadingLogEntity
import com.itermeum.app.core.database.entity.RosaryProgressEntity
import com.itermeum.app.core.database.entity.ScriptSessionEntity
import com.itermeum.app.core.database.entity.StreakStateEntity
import com.itermeum.app.core.database.entity.TransitionRitualEntity
import com.itermeum.app.core.database.entity.TravelPeriodEntity
import com.itermeum.app.core.database.entity.VanityCheckEntity
import com.itermeum.app.core.database.entity.WeeklyReviewEntity
import com.itermeum.app.core.database.entity.WorkTaskEntity
import kotlinx.serialization.Serializable

/** Section 8 backup v1. `*Enc` fields are decrypted in memory before export and re-encrypted on import. */
@Serializable
data class BackupBundle(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long,
    val appVersion: String,
    val contentPackVersion: Int,
    val schemaVersion: Int = 1,
    val tables: Tables,
) {
    @Serializable
    data class Tables(
        val profile: ProfileEntity?,
        val norm: List<NormEntity>,
        val normCompletion: List<NormCompletionEntity>,
        val dayPlan: List<DayPlanEntity>,
        val workTask: List<WorkTaskEntity>,
        val outreachLog: List<OutreachLogEntity>,
        val scriptSession: List<ScriptSessionEntity>,
        val transitionRitual: List<TransitionRitualEntity>,
        val examen: List<ExamenEntity>,
        val prayerUserText: Map<String, String>,
        val memorization: List<MemorizationItemEntity>,
        val jaculatoriaLog: List<JaculatoriaLogEntity>,
        val readingLog: List<ReadingLogEntity>,
        val rosaryProgress: List<RosaryProgressEntity>,
        val formationEvent: List<FormationEventEntity>,
        val catechesisNote: List<CatechesisNoteEntity>,
        val streakState: List<StreakStateEntity>,
        val freezeLedger: List<FreezeLedgerEntity>,
        val milestone: List<MilestoneEntity>,
        val project: List<ProjectEntity>,
        val quarterGoal: List<QuarterGoalEntity>,
        val quarterIncome: List<QuarterIncomeEntity>,
        val vanityCheck: List<VanityCheckEntity>,
        val weeklyReview: List<WeeklyReviewEntity>,
        val mortificationLog: List<MortificationLogEntity>,
        val travelPeriod: List<TravelPeriodEntity>,
        val morningState: List<MorningStateEntity>,
        val monthlySummaryView: List<MonthlySummaryViewEntity>,
    )

    companion object {
        const val FORMAT = "itermeum-backup"
    }
}
