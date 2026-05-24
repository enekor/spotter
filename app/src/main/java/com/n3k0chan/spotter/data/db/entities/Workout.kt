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

    // Métricas de salud (Health Connect) persistidas
    val calories: Double? = null,
    val heartRateAvg: Long? = null,
    val heartRateMin: Long? = null,
    val heartRateMax: Long? = null,
    val distanceMeters: Double? = null,
    val steps: Long? = null,
)

/**
 * Una serie/registro de un ejercicio dentro de un entreno.
 * Todos los campos métricos son nullables porque cada perfil de
 * medida solo usa los relevantes (peso×reps, distancia+tiempo, etc.).
 * Ver [com.n3k0chan.spotter.data.measurement.MeasurementProfile].
 */
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
    // Campos métricos (todos opcionales, según el perfil del ejercicio)
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val resistanceLevel: Int? = null,
    val inclinePercent: Double? = null,
    // Comunes
    val restSeconds: Int? = null,
    val timeUnderTensionSeconds: Int? = null,
    val rpe: Int? = null,
    val completedAt: Long = System.currentTimeMillis(),
)
