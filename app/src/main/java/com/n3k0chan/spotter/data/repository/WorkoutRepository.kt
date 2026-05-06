package com.n3k0chan.spotter.data.repository

import com.n3k0chan.spotter.data.db.dao.WorkoutDao
import com.n3k0chan.spotter.data.db.entities.Workout
import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import kotlinx.coroutines.flow.Flow

/**
 * Datos de entrada para registrar una serie. Solo los campos relevantes
 * según el perfil del ejercicio se rellenan; el resto van como null.
 */
data class SetInput(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val resistanceLevel: Int? = null,
    val inclinePercent: Double? = null,
    val restSeconds: Int? = null,
    val rpe: Int? = null,
)

class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeAll(): Flow<List<WorkoutWithSets>> = dao.observeAll()
    suspend fun get(id: Long): WorkoutWithSets? = dao.getById(id)
    suspend fun getActive(): Workout? = dao.getActive()

    suspend fun start(title: String, templateId: Long?): Long =
        dao.insertWorkout(
            Workout(
                title = title,
                templateId = templateId,
                startedAt = System.currentTimeMillis(),
            ),
        )

    suspend fun finish(workoutId: Long, rpe: Int?, notes: String?) {
        val current = dao.getById(workoutId)?.workout ?: return
        dao.updateWorkout(
            current.copy(
                finishedAt = System.currentTimeMillis(),
                rpe = rpe,
                notes = notes,
            ),
        )
    }

    suspend fun update(workout: Workout) = dao.updateWorkout(workout)

    /** Crea una serie a partir de un [SetInput] genérico. */
    suspend fun addSet(
        workoutId: Long,
        exerciseId: Long,
        orderIndex: Int,
        setNumber: Int,
        input: SetInput,
    ): Long = dao.insertSet(
        WorkoutSet(
            workoutId = workoutId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            setNumber = setNumber,
            weightKg = input.weightKg,
            reps = input.reps,
            durationSeconds = input.durationSeconds,
            distanceMeters = input.distanceMeters,
            resistanceLevel = input.resistanceLevel,
            inclinePercent = input.inclinePercent,
            restSeconds = input.restSeconds,
            rpe = input.rpe,
        ),
    )

    suspend fun updateSet(set: WorkoutSet) = dao.updateSet(set)
    suspend fun deleteSet(id: Long) = dao.deleteSet(id)
    suspend fun delete(id: Long) = dao.deleteWorkout(id)

    suspend fun recentSetsFor(exerciseId: Long, limit: Int = 10): List<WorkoutSet> =
        dao.getRecentSetsForExercise(exerciseId, limit)

    fun observeFinishedStartTimes(): Flow<List<Long>> = dao.observeFinishedStartTimes()
    fun observeFinishedCount(): Flow<Int> = dao.observeFinishedCount()
    fun observeFinishedCountSince(sinceEpochMillis: Long): Flow<Int> =
        dao.observeFinishedCountSince(sinceEpochMillis)
}
