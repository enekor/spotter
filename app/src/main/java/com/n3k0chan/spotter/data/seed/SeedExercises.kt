package com.n3k0chan.spotter.data.seed

import com.n3k0chan.spotter.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.first

/**
 * Catálogo de ejercicios básicos que se siembran la primera vez que la app
 * se abre con la base vacía. El usuario puede borrarlos o añadir los suyos.
 *
 * Grupos canónicos (los reconoce [com.n3k0chan.spotter.ui.components.MuscleGroup]):
 * Cardio · Abdomen · Brazo · Pecho · Espalda · Pierna
 */
object SeedExercises {

    private data class Seed(val name: String, val group: String)

    private val all = listOf(
        // ── Cardio
        Seed("Cinta de correr", "Cardio"),
        Seed("Bicicleta estática", "Cardio"),
        Seed("Elíptica", "Cardio"),
        Seed("Remo (cardio)", "Cardio"),
        Seed("Sprints", "Cardio"),
        Seed("Saltar a la comba", "Cardio"),

        // ── Abdomen
        Seed("Crunch", "Abdomen"),
        Seed("Plancha", "Abdomen"),
        Seed("Plancha lateral", "Abdomen"),
        Seed("Elevación de piernas", "Abdomen"),
        Seed("Bicicleta abdominal", "Abdomen"),
        Seed("Mountain climber", "Abdomen"),
        Seed("Rueda abdominal", "Abdomen"),

        // ── Brazo (bíceps + tríceps)
        Seed("Curl con barra", "Brazo"),
        Seed("Curl con mancuernas", "Brazo"),
        Seed("Curl martillo", "Brazo"),
        Seed("Curl barra Z", "Brazo"),
        Seed("Extensión de tríceps en polea", "Brazo"),
        Seed("Press francés", "Brazo"),
        Seed("Fondos en banco", "Brazo"),
        Seed("Patada de tríceps", "Brazo"),

        // ── Pecho
        Seed("Press banca", "Pecho"),
        Seed("Press inclinado con mancuernas", "Pecho"),
        Seed("Press declinado", "Pecho"),
        Seed("Aperturas en polea", "Pecho"),
        Seed("Aperturas con mancuernas", "Pecho"),
        Seed("Fondos en paralelas", "Pecho"),
        Seed("Flexiones", "Pecho"),

        // ── Espalda
        Seed("Dominadas", "Espalda"),
        Seed("Jalón al pecho", "Espalda"),
        Seed("Remo con barra", "Espalda"),
        Seed("Remo con mancuerna", "Espalda"),
        Seed("Remo en polea baja", "Espalda"),
        Seed("Peso muerto", "Espalda"),
        Seed("Pull-over", "Espalda"),

        // ── Pierna
        Seed("Sentadilla", "Pierna"),
        Seed("Sentadilla búlgara", "Pierna"),
        Seed("Prensa", "Pierna"),
        Seed("Zancadas", "Pierna"),
        Seed("Peso muerto rumano", "Pierna"),
        Seed("Curl femoral", "Pierna"),
        Seed("Extensión de cuádriceps", "Pierna"),
        Seed("Elevación de gemelos", "Pierna"),
        Seed("Hip thrust", "Pierna"),
    )

    /** Crea los ejercicios solo si no existe ninguno todavía. Idempotente. */
    suspend fun seedIfEmpty(repo: ExerciseRepository): Int {
        val existing = runCatching { repo.observeAll().first() }.getOrDefault(emptyList())
        if (existing.isNotEmpty()) return 0
        var inserted = 0
        all.forEach { (name, group) ->
            runCatching { repo.create(name, group) }.onSuccess { inserted++ }
        }
        return inserted
    }
}
