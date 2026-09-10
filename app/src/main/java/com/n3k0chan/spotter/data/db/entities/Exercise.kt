package com.n3k0chan.spotter.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.n3k0chan.spotter.data.measurement.MeasurementProfile

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String? = null,
    /** Perfil de medida del ejercicio (nombre del enum MeasurementProfile). */
    val measurementProfile: String = MeasurementProfile.Default.name,
    /** true si lo creó el usuario; false para los seeds del catálogo base. */
    val isUserCreated: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Devuelve el [MeasurementProfile] resuelto del ejercicio. */
val Exercise.profile: MeasurementProfile
    get() = MeasurementProfile.fromNameOrDefault(measurementProfile)
