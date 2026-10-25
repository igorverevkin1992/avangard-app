package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
import com.itermeum.app.core.database.entity.PrayerEntity
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

/** Whole-table reads, wipes and inserts for backup v1 (section 5.17). */
@Dao
interface BackupDao {
    @Query("SELECT * FROM norm") suspend fun norms(): List<NormEntity>
    @Query("SELECT * FROM norm_completion") suspend fun completions(): List<NormCompletionEntity>
    @Query("SELECT * FROM day_plan") suspend fun dayPlans(): List<DayPlanEntity>
    @Query("SELECT * FROM work_task") suspend fun tasks(): List<WorkTaskEntity>
    @Query("SELECT * FROM outreach_log") suspend fun outreach(): List<OutreachLogEntity>
    @Query("SELECT * FROM script_session") suspend fun scripts(): List<ScriptSessionEntity>
    @Query("SELECT * FROM transition_ritual") suspend fun rituals(): List<TransitionRitualEntity>
    @Query("SELECT * FROM examen") suspend fun examens(): List<ExamenEntity>
    @Query("SELECT * FROM prayer WHERE userEditedText IS NOT NULL") suspend fun editedPrayers(): List<PrayerEntity>
    @Query("SELECT * FROM memorization_item") suspend fun memorization(): List<MemorizationItemEntity>
    @Query("SELECT * FROM jaculatoria_log") suspend fun jaculatoriaLogs(): List<JaculatoriaLogEntity>
    @Query("SELECT * FROM reading_log") suspend fun readingLogs(): List<ReadingLogEntity>
    @Query("SELECT * FROM rosary_progress") suspend fun rosary(): List<RosaryProgressEntity>
    @Query("SELECT * FROM formation_event") suspend fun formation(): List<FormationEventEntity>
    @Query("SELECT * FROM catechesis_note") suspend fun notes(): List<CatechesisNoteEntity>
    @Query("SELECT * FROM streak_state") suspend fun streaks(): List<StreakStateEntity>
    @Query("SELECT * FROM freeze_ledger") suspend fun freezes(): List<FreezeLedgerEntity>
    @Query("SELECT * FROM milestone") suspend fun milestones(): List<MilestoneEntity>
    @Query("SELECT * FROM project") suspend fun projects(): List<ProjectEntity>
    @Query("SELECT * FROM quarter_goal") suspend fun goals(): List<QuarterGoalEntity>
    @Query("SELECT * FROM quarter_income") suspend fun income(): List<QuarterIncomeEntity>
    @Query("SELECT * FROM vanity_check") suspend fun vanity(): List<VanityCheckEntity>
    @Query("SELECT * FROM weekly_review") suspend fun reviews(): List<WeeklyReviewEntity>
    @Query("SELECT * FROM mortification_log") suspend fun mortifications(): List<MortificationLogEntity>
    @Query("SELECT * FROM travel_period") suspend fun travel(): List<TravelPeriodEntity>
    @Query("SELECT * FROM morning_state") suspend fun mornings(): List<MorningStateEntity>
    @Query("SELECT * FROM monthly_summary_view") suspend fun monthViews(): List<MonthlySummaryViewEntity>

    @Query("DELETE FROM norm_completion") suspend fun wipeCompletions()
    @Query("DELETE FROM day_plan") suspend fun wipeDayPlans()
    @Query("DELETE FROM work_task") suspend fun wipeTasks()
    @Query("DELETE FROM outreach_log") suspend fun wipeOutreach()
    @Query("DELETE FROM script_session") suspend fun wipeScripts()
    @Query("DELETE FROM transition_ritual") suspend fun wipeRituals()
    @Query("DELETE FROM examen") suspend fun wipeExamens()
    @Query("UPDATE prayer SET userEditedText = NULL") suspend fun wipePrayerEdits()
    @Query("DELETE FROM memorization_item") suspend fun wipeMemorization()
    @Query("DELETE FROM jaculatoria_log") suspend fun wipeJaculatoriaLogs()
    @Query("DELETE FROM reading_log") suspend fun wipeReadingLogs()
    @Query("DELETE FROM rosary_progress") suspend fun wipeRosary()
    @Query("DELETE FROM formation_event") suspend fun wipeFormation()
    @Query("DELETE FROM catechesis_note") suspend fun wipeNotes()
    @Query("DELETE FROM streak_state") suspend fun wipeStreaks()
    @Query("DELETE FROM freeze_ledger") suspend fun wipeFreezes()
    @Query("DELETE FROM milestone") suspend fun wipeMilestones()
    @Query("DELETE FROM project") suspend fun wipeProjects()
    @Query("DELETE FROM quarter_goal") suspend fun wipeGoals()
    @Query("DELETE FROM quarter_income") suspend fun wipeIncome()
    @Query("DELETE FROM vanity_check") suspend fun wipeVanity()
    @Query("DELETE FROM weekly_review") suspend fun wipeReviews()
    @Query("DELETE FROM mortification_log") suspend fun wipeMortifications()
    @Query("DELETE FROM travel_period") suspend fun wipeTravel()
    @Query("DELETE FROM morning_state") suspend fun wipeMornings()
    @Query("DELETE FROM monthly_summary_view") suspend fun wipeMonthViews()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNorms(x: List<NormEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCompletions(x: List<NormCompletionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDayPlans(x: List<DayPlanEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTasks(x: List<WorkTaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOutreach(x: List<OutreachLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertScripts(x: List<ScriptSessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRituals(x: List<TransitionRitualEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExamens(x: List<ExamenEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMemorization(x: List<MemorizationItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertJaculatoriaLogs(x: List<JaculatoriaLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReadingLogs(x: List<ReadingLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRosary(x: List<RosaryProgressEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFormation(x: List<FormationEventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNotes(x: List<CatechesisNoteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStreaks(x: List<StreakStateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFreezes(x: List<FreezeLedgerEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMilestones(x: List<MilestoneEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProjects(x: List<ProjectEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGoals(x: List<QuarterGoalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIncome(x: List<QuarterIncomeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVanity(x: List<VanityCheckEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReviews(x: List<WeeklyReviewEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMortifications(x: List<MortificationLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTravel(x: List<TravelPeriodEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMornings(x: List<MorningStateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMonthViews(x: List<MonthlySummaryViewEntity>)
    @Query("UPDATE prayer SET userEditedText = :text WHERE id = :id") suspend fun setPrayerEdit(id: String, text: String)
}
