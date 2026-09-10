package com.n3k0chan.spotter.data.repository

import com.n3k0chan.spotter.data.db.dao.ExerciseDao
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: ExerciseDao) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    suspend fun get(id: Long) = dao.getById(id)

    suspend fun create(
        name: String,
        muscleGroup: String?,
        profile: MeasurementProfile = MeasurementProfile.Default,
        isUserCreated: Boolean = true,
    ): Long = dao.insert(
        Exercise(
            name = name.trim(),
            muscleGroup = muscleGroup?.trim()?.takeIf { it.isNotEmpty() },
            measurementProfile = profile.name,
            isUserCreated = isUserCreated,
        ),
    )

    suspend fun update(exercise: Exercise) = dao.update(exercise)
    suspend fun delete(id: Long) = dao.delete(id)
}
