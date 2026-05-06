package com.n3k0chan.spotter.data.seed

import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import com.n3k0chan.spotter.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.first

/**
 * Catálogo de ejercicios básicos que se siembran la primera vez que la app
 * se abre con la base vacía. Cada uno lleva su [MeasurementProfile] para que
 * el formulario de series muestre los campos correctos.
 */
object SeedExercises {

    private data class Seed(val name: String, val group: String, val profile: MeasurementProfile)

    private val all = listOf(
        // ── Cardio
        Seed("Cinta de correr", "Cardio", MeasurementProfile.TreadmillIncline),
        Seed("Bicicleta estática", "Cardio", MeasurementProfile.CardioMachine),
        Seed("Elíptica", "Cardio", MeasurementProfile.CardioMachine),
        Seed("Remo (cardio)", "Cardio", MeasurementProfile.CardioMachine),
        Seed("Sprints", "Cardio", MeasurementProfile.DistanceTime),
        Seed("Saltar a la comba", "Cardio", MeasurementProfile.Duration),

        // ── Abdomen
        Seed("Crunch", "Abdomen", MeasurementProfile.Reps),
        Seed("Plancha", "Abdomen", MeasurementProfile.Duration),
        Seed("Plancha lateral", "Abdomen", MeasurementProfile.Duration),
        Seed("Elevación de piernas", "Abdomen", MeasurementProfile.Reps),
        Seed("Bicicleta abdominal", "Abdomen", MeasurementProfile.Reps),
        Seed("Mountain climber", "Abdomen", MeasurementProfile.Duration),
        Seed("Rueda abdominal", "Abdomen", MeasurementProfile.Reps),

        // ── Brazo
        Seed("Curl con barra", "Brazo", MeasurementProfile.WeightReps),
        Seed("Curl con mancuernas", "Brazo", MeasurementProfile.WeightReps),
        Seed("Curl martillo", "Brazo", MeasurementProfile.WeightReps),
        Seed("Curl barra Z", "Brazo", MeasurementProfile.WeightReps),
        Seed("Extensión de tríceps en polea", "Brazo", MeasurementProfile.WeightReps),
        Seed("Press francés", "Brazo", MeasurementProfile.WeightReps),
        Seed("Fondos en banco", "Brazo", MeasurementProfile.Reps),
        Seed("Patada de tríceps", "Brazo", MeasurementProfile.WeightReps),

        // ── Pecho
        Seed("Press banca", "Pecho", MeasurementProfile.WeightReps),
        Seed("Press inclinado con mancuernas", "Pecho", MeasurementProfile.WeightReps),
        Seed("Press declinado", "Pecho", MeasurementProfile.WeightReps),
        Seed("Aperturas en polea", "Pecho", MeasurementProfile.WeightReps),
        Seed("Aperturas con mancuernas", "Pecho", MeasurementProfile.WeightReps),
        Seed("Fondos en paralelas", "Pecho", MeasurementProfile.Reps),
        Seed("Flexiones", "Pecho", MeasurementProfile.Reps),

        // ── Espalda
        Seed("Dominadas", "Espalda", MeasurementProfile.Reps),
        Seed("Jalón al pecho", "Espalda", MeasurementProfile.WeightReps),
        Seed("Remo con barra", "Espalda", MeasurementProfile.WeightReps),
        Seed("Remo con mancuerna", "Espalda", MeasurementProfile.WeightReps),
        Seed("Remo en polea baja", "Espalda", MeasurementProfile.WeightReps),
        Seed("Peso muerto", "Espalda", MeasurementProfile.WeightReps),
        Seed("Pull-over", "Espalda", MeasurementProfile.WeightReps),

        // ── Pierna
        Seed("Sentadilla", "Pierna", MeasurementProfile.WeightReps),
        Seed("Sentadilla búlgara", "Pierna", MeasurementProfile.WeightReps),
        Seed("Prensa", "Pierna", MeasurementProfile.WeightReps),
        Seed("Zancadas", "Pierna", MeasurementProfile.WeightReps),
        Seed("Peso muerto rumano", "Pierna", MeasurementProfile.WeightReps),
        Seed("Curl femoral", "Pierna", MeasurementProfile.WeightReps),
        Seed("Extensión de cuádriceps", "Pierna", MeasurementProfile.WeightReps),
        Seed("Elevación de gemelos", "Pierna", MeasurementProfile.WeightReps),
        Seed("Hip thrust", "Pierna", MeasurementProfile.WeightReps),
    )

    /** Crea los ejercicios solo si no existe ninguno todavía. Idempotente. */
    suspend fun seedIfEmpty(repo: ExerciseRepository): Int {
        val existing = runCatching { repo.observeAll().first() }.getOrDefault(emptyList())
        if (existing.isNotEmpty()) return 0
        var inserted = 0
        all.forEach { seed ->
            runCatching { repo.create(seed.name, seed.group, seed.profile) }
                .onSuccess { inserted++ }
        }
        return inserted
    }
}
