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

    /**
     * Devuelve el "mejor" set histórico del ejercicio según su perfil:
     * - Con peso: el de mayor peso (a igualdad, más reps/duración).
     * - Solo reps: el de más reps.
     * - Solo duración: el de mayor duración.
     * - Distancia+tiempo / cardio máquina: el de mayor distancia.
     * Sirve para prellenar el form al añadir una serie con tu PR como referencia.
     */
    suspend fun bestSetFor(
        exerciseId: Long,
        profile: com.n3k0chan.spotter.data.measurement.MeasurementProfile,
    ): WorkoutSet? {
        val all = dao.getRecentSetsForExercise(exerciseId, 500)
        if (all.isEmpty()) return null
        val fields = profile.fields
        return when {
            com.n3k0chan.spotter.data.measurement.MeasurementField.Weight in fields ->
                all.maxWithOrNull(compareBy({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }, { it.durationSeconds ?: 0 }))
            com.n3k0chan.spotter.data.measurement.MeasurementField.Distance in fields ->
                all.maxByOrNull { it.distanceMeters ?: 0.0 }
            com.n3k0chan.spotter.data.measurement.MeasurementField.Duration in fields ->
                all.maxByOrNull { it.durationSeconds ?: 0 }
            com.n3k0chan.spotter.data.measurement.MeasurementField.Reps in fields ->
                all.maxByOrNull { it.reps ?: 0 }
            else -> all.firstOrNull()
        }
    }

    suspend fun getWorkoutsInRange(startMillis: Long, endMillis: Long): List<Workout> =
        dao.getWorkoutsInRange(startMillis, endMillis)

    suspend fun importFromHealthConnect(
        title: String,
        startedAt: Long,
        finishedAt: Long,
        notes: String,
    ): Long = dao.insertWorkout(
        Workout(
            title = title,
            startedAt = startedAt,
            finishedAt = finishedAt,
            notes = notes,
        ),
    )

    fun observeFinishedStartTimes(): Flow<List<Long>> = dao.observeFinishedStartTimes()
    fun observeFinishedCount(): Flow<Int> = dao.observeFinishedCount()
    fun observeFinishedCountSince(sinceEpochMillis: Long): Flow<Int> =
        dao.observeFinishedCountSince(sinceEpochMillis)
}
