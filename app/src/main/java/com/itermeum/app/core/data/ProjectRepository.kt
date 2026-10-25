package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.crypto.FieldCipher
import com.itermeum.app.core.database.dao.ProjectDao
import com.itermeum.app.core.database.entity.GoalStatus
import com.itermeum.app.core.database.entity.ProjectEntity
import com.itermeum.app.core.database.entity.ProjectStage
import com.itermeum.app.core.database.entity.QuarterGoalEntity
import com.itermeum.app.core.database.entity.QuarterIncomeEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Section 5.16 projects-as-offerings and the studio map (quarter goals, income line). */
@Singleton
class ProjectRepository @Inject constructor(
    private val dao: ProjectDao,
    private val cipher: FieldCipher,
    private val clock: Clock,
) {
    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeAll()
    fun observeProject(id: Long): Flow<ProjectEntity?> = dao.observe(id)

    suspend fun create(title: String, market: com.itermeum.app.core.database.entity.Market, format: String, dedication: String): Long =
        dao.insert(ProjectEntity(title = title.trim(), market = market, format = format.trim(), stage = ProjectStage.CONCEPT, dedication = dedication.trim(), createdAt = clock.nowEpochMillis()))

    suspend fun update(project: ProjectEntity) = dao.update(project)

    fun ethics(project: ProjectEntity): List<Boolean> =
        project.ethicsJson.trim('[', ']').split(',').map { it.trim().toBoolean() }.let { if (it.size == 4) it else List(4) { false } }

    fun ethicsJson(values: List<Boolean>) = values.joinToString(",", "[", "]")

    /** Section 5.16: the checklist is mandatory at CONCEPT and SCRIPT — the stage cannot advance past SCRIPT without 4/4. */
    fun canAdvance(project: ProjectEntity, to: ProjectStage): Boolean {
        if (to.ordinal <= ProjectStage.SCRIPT.ordinal) return true
        return ethics(project).all { it }
    }

    fun reflection(project: ProjectEntity): String? = project.reflectionEnc?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
    suspend fun setReflection(project: ProjectEntity, text: String) = dao.update(project.copy(reflectionEnc = if (text.isBlank()) null else cipher.encrypt(text)))

    fun observeGoals(quarter: String): Flow<List<QuarterGoalEntity>> = dao.observeGoals(quarter)
    suspend fun goals(quarter: String) = dao.goals(quarter)
    suspend fun upsertGoal(goal: QuarterGoalEntity) = dao.upsertGoal(goal)
    suspend fun setGoalStatus(goal: QuarterGoalEntity, status: GoalStatus) = dao.upsertGoal(goal.copy(status = status))
    suspend fun deleteGoal(id: Long) = dao.deleteGoal(id)
    fun observeIncome(): Flow<List<QuarterIncomeEntity>> = dao.observeIncome()
    suspend fun setIncome(quarter: String, amountRub: Long) = dao.upsertIncome(QuarterIncomeEntity(quarter, amountRub))

    companion object {
        /** Section 5.15: personal income goal, ₽ per year. */
        const val YEARLY_GOAL_RUB = 50_000_000L

        fun quarterOf(date: LocalDate): String = "${date.year}-Q${(date.monthValue - 1) / 3 + 1}"
    }
}
