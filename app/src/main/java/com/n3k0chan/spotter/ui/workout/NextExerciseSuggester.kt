package com.n3k0chan.spotter.ui.workout

import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.ui.components.MuscleGroup

/**
 * Elige el siguiente ejercicio a sugerir en una sesión activa:
 * - Solo con ≥2 ejercicios en la sesión.
 * - Grupo objetivo = grupo muscular predominante de la sesión (empate → grupo
 *   del último ejercicio añadido).
 * - Candidato = el más entrenado (trainingCounts) de ese grupo que no esté ya
 *   en la sesión. Sin candidatos → null.
 */
object NextExerciseSuggester {

    fun suggest(
        sessionExercises: List<Exercise>,
        catalog: List<Exercise>,
        trainingCounts: Map<Long, Int>,
    ): Long? {
        if (sessionExercises.size < 2) return null
        val target = predominantGroup(sessionExercises) ?: return null
        val sessionIds = sessionExercises.map { it.id }.toSet()
        val candidates = catalog.filter {
            it.id !in sessionIds && MuscleGroup.from(it.muscleGroup) == target
        }
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { trainingCounts[it.id] ?: 0 }?.id
    }

    private fun predominantGroup(sessionExercises: List<Exercise>): MuscleGroup? {
        if (sessionExercises.isEmpty()) return null
        val groups = sessionExercises.map { MuscleGroup.from(it.muscleGroup) }
        val counts = groups.groupingBy { it }.eachCount()
        val max = counts.values.max()
        val top = counts.filterValues { it == max }.keys
        return if (top.size == 1) top.first() else groups.last()
    }
}
