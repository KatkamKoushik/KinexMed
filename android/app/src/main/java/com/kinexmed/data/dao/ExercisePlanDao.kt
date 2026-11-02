package com.kinexmed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kinexmed.data.entity.ExercisePlanEntity
import com.kinexmed.data.entity.PlanExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for exercise plans and their prescribed exercises.
 */
@Dao
interface ExercisePlanDao {

    @Query("SELECT * FROM exercise_plans ORDER BY createdAt DESC")
    fun getAllPlansFlow(): Flow<List<ExercisePlanEntity>>

    @Query("SELECT * FROM exercise_plans ORDER BY createdAt DESC")
    suspend fun getAllPlans(): List<ExercisePlanEntity>

    @Query("SELECT * FROM exercise_plans WHERE isActive = 1 LIMIT 1")
    fun getActivePlanFlow(): Flow<ExercisePlanEntity?>

    @Query("SELECT * FROM exercise_plans WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlan(): ExercisePlanEntity?

    @Query("SELECT * FROM exercise_plans WHERE id = :planId LIMIT 1")
    suspend fun getPlanById(planId: String): ExercisePlanEntity?

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC")
    fun getPlanExercisesFlow(planId: String): Flow<List<PlanExerciseEntity>>

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getPlanExercises(planId: String): List<PlanExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: ExercisePlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanExercises(exercises: List<PlanExerciseEntity>)

    @Update
    suspend fun updatePlan(plan: ExercisePlanEntity)

    @Query("UPDATE exercise_plans SET isActive = 0")
    suspend fun deactivateAllPlans()

    @Transaction
    suspend fun setActivePlan(planId: String) {
        deactivateAllPlans()
        setActivePlanById(planId)
    }

    @Query("UPDATE exercise_plans SET isActive = 1 WHERE id = :planId")
    suspend fun setActivePlanById(planId: String)

    @Query("DELETE FROM plan_exercises WHERE planId = :planId")
    suspend fun deletePlanExercises(planId: String)

    @Query("DELETE FROM exercise_plans WHERE id = :planId")
    suspend fun deletePlan(planId: String)
}
