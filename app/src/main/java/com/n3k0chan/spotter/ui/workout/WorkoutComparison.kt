package com.n3k0chan.spotter.ui.workout

import kotlinx.serialization.Serializable

/**
 * Resultado de la comparación programática de un entreno. Se persiste como JSON
 * en Workout.aiSummaryJson (columna reutilizada, sin migración) y se muestra en
 * el diálogo post-entreno y en el detalle del historial. Las claves JSON
 * (summary/exercises/name/markdown) se mantienen para leer resúmenes antiguos.
 */
@Serializable
data class WorkoutComparison(
    val summary: String,
    val exercises: List<ComparisonExercise> = emptyList(),
)

@Serializable
data class ComparisonExercise(
    val name: String,
    /** Texto plano (líneas separadas por \n); el diálogo lo pinta como Text. */
    val markdown: String,
)
