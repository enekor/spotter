package com.n3k0chan.spotter.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long? = null,
    val title: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val rpe: Int? = null,
    val notes: String? = null,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val restSeconds: Int? = null,
    val timeUnderTensionSeconds: Int? = null,
    val rpe: Int? = null,
    val completedAt: Long = System.currentTimeMillis(),
)
