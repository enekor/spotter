package com.n3k0chan.spotter.ui.workout

import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import com.n3k0chan.spotter.data.measurement.formatShort

/** Entrada por ejercicio para el comparador. */
data class ExerciseComparisonInput(
    val name: String,
    val profile: MeasurementProfile,
    /** Series registradas hoy para este ejercicio. */
    val todaySets: List<WorkoutSet>,
    /** Series de entrenos anteriores (excluye hoy), más reciente primero. */
    val historySets: List<WorkoutSet>,
)

/** Comparación 100% local del entreno frente al histórico. Sin red, sin LLM. */
object WorkoutComparator {

    fun compare(inputs: List<ExerciseComparisonInput>): WorkoutComparison {
        val exercises = inputs.mapNotNull { input ->
            if (input.todaySets.isEmpty()) null
            else ComparisonExercise(input.name, verdictFor(input))
        }
        val totalSets = inputs.sumOf { it.todaySets.size }
        val prCount = inputs.count { isPr(it) }
        val summary = buildString {
            append("${exercises.size} ejercicios · $totalSets series")
            if (prCount > 0) append(" · $prCount PR${if (prCount > 1) "s" else ""}")
        }
        return WorkoutComparison(summary = summary, exercises = exercises)
    }

    private fun verdictFor(input: ExerciseComparisonInput): String {
        val profile = input.profile
        val todayVol = volumeOf(input.todaySets, profile)
        val lines = mutableListOf<String>()
        lines += "Hoy: ${input.todaySets.size} series · ${formatVolume(todayVol, profile)}"

        val histBest = bestSet(input.historySets, profile)
        val todayBest = bestSet(input.todaySets, profile)
        if (histBest == null || todayBest == null) {
            lines += "Sin datos previos para comparar"
            return lines.joinToString("\n")
        }

        lines += if (isPr(input)) {
            "Nuevo PR: ${todayBest.formatShort(profile)} (antes ${histBest.formatShort(profile)})"
        } else {
            "Mejor hoy: ${todayBest.formatShort(profile)} · PR: ${histBest.formatShort(profile)}"
        }

        val lastVol = volumeOf(lastSession(input.historySets), profile)
        if (lastVol > 0) {
            val pct = ((todayVol - lastVol) / lastVol * 100).toInt()
            val sign = if (pct >= 0) "+" else ""
            lines += "Volumen $sign$pct% vs última sesión"
        }
        return lines.joinToString("\n")
    }

    fun isPr(input: ExerciseComparisonInput): Boolean {
        val profile = input.profile
        val histBest = bestSet(input.historySets, profile) ?: return false
        val todayBest = bestSet(input.todaySets, profile) ?: return false
        return metric(todayBest, profile) > metric(histBest, profile)
    }

    fun bestSet(sets: List<WorkoutSet>, profile: MeasurementProfile): WorkoutSet? =
        sets.maxByOrNull { metric(it, profile) }

    fun volumeOf(sets: List<WorkoutSet>, profile: MeasurementProfile): Double = when (profile) {
        MeasurementProfile.WeightReps -> sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        MeasurementProfile.Reps -> sets.sumOf { (it.reps ?: 0).toDouble() }
        MeasurementProfile.Duration, MeasurementProfile.WeightDuration ->
            sets.sumOf { (it.durationSeconds ?: 0).toDouble() }
        MeasurementProfile.DistanceTime,
        MeasurementProfile.CardioMachine,
        MeasurementProfile.TreadmillIncline -> sets.sumOf { it.distanceMeters ?: 0.0 }
    }

    /** Métrica principal comparable de una serie según el perfil. */
    private fun metric(s: WorkoutSet, profile: MeasurementProfile): Double = when (profile) {
        // Prioriza peso; a igualdad, más reps.
        MeasurementProfile.WeightReps -> (s.weightKg ?: 0.0) * 1000 + (s.reps ?: 0)
        MeasurementProfile.Reps -> (s.reps ?: 0).toDouble()
        MeasurementProfile.Duration, MeasurementProfile.WeightDuration ->
            (s.durationSeconds ?: 0).toDouble()
        MeasurementProfile.DistanceTime,
        MeasurementProfile.CardioMachine,
        MeasurementProfile.TreadmillIncline -> s.distanceMeters ?: 0.0
    }

    /** Series de la sesión histórica más reciente (primer workoutId de la lista). */
    private fun lastSession(historySets: List<WorkoutSet>): List<WorkoutSet> {
        val firstWorkoutId = historySets.firstOrNull()?.workoutId ?: return emptyList()
        return historySets.filter { it.workoutId == firstWorkoutId }
    }

    private fun formatVolume(volume: Double, profile: MeasurementProfile): String = when (profile) {
        MeasurementProfile.WeightReps -> "${volume.toInt()} kg·rep"
        MeasurementProfile.Reps -> "${volume.toInt()} reps"
        MeasurementProfile.Duration, MeasurementProfile.WeightDuration -> {
            val s = volume.toInt(); "%d:%02d min".format(s / 60, s % 60)
        }
        MeasurementProfile.DistanceTime,
        MeasurementProfile.CardioMachine,
        MeasurementProfile.TreadmillIncline ->
            if (volume >= 1000) "%.2f km".format(volume / 1000) else "${volume.toInt()} m"
    }
}
