package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.itermeum.app.core.database.entity.ProjectEntity
import com.itermeum.app.core.database.entity.QuarterGoalEntity
import com.itermeum.app.core.database.entity.QuarterIncomeEntity
import com.itermeum.app.core.database.entity.VanityCheckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project WHERE id = :id")
    fun observe(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("SELECT * FROM quarter_goal WHERE quarter = :quarter")
    fun observeGoals(quarter: String): Flow<List<QuarterGoalEntity>>

    @Query("SELECT * FROM quarter_goal WHERE quarter = :quarter")
    suspend fun goals(quarter: String): List<QuarterGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: QuarterGoalEntity): Long

    @Query("DELETE FROM quarter_goal WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM quarter_income ORDER BY quarter")
    fun observeIncome(): Flow<List<QuarterIncomeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIncome(income: QuarterIncomeEntity)

    @Query("SELECT * FROM vanity_check WHERE quarter = :quarter")
    suspend fun vanity(quarter: String): VanityCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVanity(check: VanityCheckEntity)
}
