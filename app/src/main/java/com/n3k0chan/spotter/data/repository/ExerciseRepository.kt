package com.n3k0chan.spotter.data.repository

import com.n3k0chan.spotter.data.db.dao.ExerciseDao
import com.n3k0chan.spotter.data.db.entities.Exercise
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: ExerciseDao) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    suspend fun get(id: Long) = dao.getById(id)
    suspend fun create(name: String, muscleGroup: String?): Long =
        dao.insert(Exercise(name = name.trim(), muscleGroup = muscleGroup?.trim()?.takeIf { it.isNotEmpty() }))
    suspend fun update(exercise: Exercise) = dao.update(exercise)
    suspend fun delete(id: Long) = dao.delete(id)
}
