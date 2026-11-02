package com.kinexmed.data.repository

import com.kinexmed.data.dao.ExercisePlanDao
import com.kinexmed.data.entity.ExercisePlanEntity
import com.kinexmed.data.entity.PlanExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository mediating access to exercise plans and their prescribed exercises.
 */
class PlanRepository(private val planDao: ExercisePlanDao) {

    val allPlans: Flow<List<ExercisePlanEntity>> = planDao.getAllPlansFlow()
    val activePlan: Flow<ExercisePlanEntity?> = planDao.getActivePlanFlow()

    fun getPlanExercisesFlow(planId: String): Flow<List<PlanExerciseEntity>> {
        return planDao.getPlanExercisesFlow(planId)
    }

    suspend fun getActivePlan(): ExercisePlanEntity? {
        return planDao.getActivePlan()
    }

    suspend fun getPlanById(planId: String): ExercisePlanEntity? {
        return planDao.getPlanById(planId)
    }

    suspend fun getPlanExercises(planId: String): List<PlanExerciseEntity> {
        return planDao.getPlanExercises(planId)
    }

    suspend fun savePlanWithExercises(
        plan: ExercisePlanEntity,
        exercises: List<PlanExerciseEntity>
    ) {
        planDao.insertPlan(plan)
        planDao.deletePlanExercises(plan.id)
        if (exercises.isNotEmpty()) {
            planDao.insertPlanExercises(exercises)
        }
        if (plan.isActive) {
            planDao.setActivePlan(plan.id)
        }
    }

    suspend fun setActivePlan(planId: String) {
        planDao.setActivePlan(planId)
    }

    suspend fun deletePlan(planId: String) {
        planDao.deletePlanExercises(planId)
        planDao.deletePlan(planId)
    }
}
