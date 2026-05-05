package com.n3k0chan.spotter.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.n3k0chan.spotter.data.db.entities.Workout
import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutWithSets>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutWithSets?

    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): Workout?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(set: WorkoutSet): Long

    @Update
    suspend fun updateSet(set: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("""
        SELECT * FROM workout_sets
        WHERE exerciseId = :exerciseId
        ORDER BY completedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentSetsForExercise(exerciseId: Long, limit: Int = 10): List<WorkoutSet>

    @Query("SELECT startedAt FROM workouts WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeFinishedStartTimes(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM workouts WHERE finishedAt IS NOT NULL")
    fun observeFinishedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM workouts WHERE finishedAt IS NOT NULL AND startedAt >= :since")
    fun observeFinishedCountSince(since: Long): Flow<Int>
}
