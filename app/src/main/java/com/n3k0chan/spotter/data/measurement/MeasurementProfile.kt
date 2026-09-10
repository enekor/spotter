package com.n3k0chan.spotter.data.measurement

import com.n3k0chan.spotter.data.db.entities.WorkoutSet

/**
 * Cada ejercicio tiene un perfil de medida que define qué campos
 * tienen sentido al registrar una serie. La UI y los formatters se
 * adaptan al perfil del ejercicio.
 *
 * Si añades un perfil nuevo, recuerda:
 * - Añadir su entrada en [MeasurementProfile]
 * - Asignárselo a los seeds relevantes en SeedExercises
 * - Considerar si necesita un nuevo campo en [WorkoutSet]
 */
enum class MeasurementProfile(
    val display: String,
    val description: String,
    val fields: List<MeasurementField>,
) {
    /** Press banca, sentadilla, curl, etc. (lo más común). */
    WeightReps(
        display = "Peso × Repeticiones",
        description = "Peso libre y máquinas de musculación",
        fields = listOf(MeasurementField.Weight, MeasurementField.Reps),
    ),

    /** Dominadas con peso corporal, flexiones, abdominales. */
    Reps(
        display = "Solo repeticiones",
        description = "Peso corporal: dominadas, flexiones, abdominales…",
        fields = listOf(MeasurementField.Reps),
    ),

    /** Plancha, isométricos, sostener una postura. */
    Duration(
        display = "Solo duración",
        description = "Plancha, isométricos, mountain climber…",
        fields = listOf(MeasurementField.Duration),
    ),

    /** Sprints al aire libre, intervalos de carrera. */
    DistanceTime(
        display = "Distancia + Tiempo",
        description = "Correr al aire libre, sprints",
        fields = listOf(MeasurementField.Distance, MeasurementField.Duration),
    ),

    /**
     * Cardio en máquina: cinta, elíptica, bici, remo.
     * Permite registrar tiempo, distancia y nivel/resistencia.
     */
    CardioMachine(
        display = "Cardio en máquina",
        description = "Cinta, elíptica, bicicleta, remo",
        fields = listOf(MeasurementField.Duration, MeasurementField.Distance, MeasurementField.Resistance),
    ),

    /** Cinta de correr con inclinación específica. */
    TreadmillIncline(
        display = "Cinta con inclinación",
        description = "Cinta indicando velocidad e inclinación",
        fields = listOf(MeasurementField.Duration, MeasurementField.Distance, MeasurementField.Incline),
    ),

    /** Farmer's walk, sostener mancuernas, etc. */
    WeightDuration(
        display = "Peso × Duración",
        description = "Farmer's walk, sostener carga",
        fields = listOf(MeasurementField.Weight, MeasurementField.Duration),
    );

    companion object {
        /** Default seguro. Útil al deserializar perfiles desconocidos. */
        val Default = WeightReps

        fun fromNameOrDefault(value: String?): MeasurementProfile =
            entries.firstOrNull { it.name == value } ?: Default
    }
}

/** Tipos de campo que pueden formar parte de un perfil. */
enum class MeasurementField {
    /** Peso en kg. */
    Weight,
    /** Repeticiones. */
    Reps,
    /** Duración en segundos. */
    Duration,
    /** Distancia en metros (la UI permite pasar km). */
    Distance,
    /** Nivel/resistencia de máquina (1-20 típicamente). */
    Resistance,
    /** Inclinación en porcentaje (cinta de correr). */
    Incline,
}

/**
 * Formatea una serie según el perfil del ejercicio. Devuelve una cadena
 * compacta tipo "80kg × 8" o "5,2km en 25:30".
 */
fun WorkoutSet.formatForProfile(profile: MeasurementProfile): String {
    val parts = mutableListOf<String>()
    if (MeasurementField.Weight in profile.fields) weightKg?.let { parts += "${formatKg(it)}kg" }
    if (MeasurementField.Reps in profile.fields) reps?.let { parts += "${it} reps" }
    if (MeasurementField.Distance in profile.fields) distanceMeters?.let { parts += formatDistance(it) }
    if (MeasurementField.Duration in profile.fields) durationSeconds?.let { parts += formatDurationMmSs(it) }
    if (MeasurementField.Resistance in profile.fields) resistanceLevel?.let { parts += "nivel ${it}" }
    if (MeasurementField.Incline in profile.fields) inclinePercent?.let { parts += "${formatPct(it)}% incl." }
    return parts.joinToString(" · ").ifEmpty { "—" }
}

/** Versión muy corta para listados densos: "80×8" / "5,2km/25:30" / "1:30". */
fun WorkoutSet.formatShort(profile: MeasurementProfile): String = when (profile) {
    MeasurementProfile.WeightReps -> "${weightKg?.let { formatKg(it) } ?: "—"}×${reps ?: "—"}"
    MeasurementProfile.Reps -> "${reps ?: "—"} reps"
    MeasurementProfile.Duration -> formatDurationMmSs(durationSeconds ?: 0)
    MeasurementProfile.DistanceTime ->
        "${distanceMeters?.let { formatDistance(it) } ?: "—"}/${formatDurationMmSs(durationSeconds ?: 0)}"
    MeasurementProfile.CardioMachine -> {
        val t = durationSeconds?.let { formatDurationMmSs(it) }
        val d = distanceMeters?.let { formatDistance(it) }
        val r = resistanceLevel?.let { "n${it}" }
        listOfNotNull(t, d, r).joinToString("·").ifEmpty { "—" }
    }
    MeasurementProfile.TreadmillIncline -> {
        val t = durationSeconds?.let { formatDurationMmSs(it) }
        val d = distanceMeters?.let { formatDistance(it) }
        val i = inclinePercent?.let { "${formatPct(it)}%" }
        listOfNotNull(t, d, i).joinToString("·").ifEmpty { "—" }
    }
    MeasurementProfile.WeightDuration ->
        "${weightKg?.let { formatKg(it) } ?: "—"}kg/${formatDurationMmSs(durationSeconds ?: 0)}"
}

private fun formatKg(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "%.2fkm".format(meters / 1000).replace('.', ',')
    else "${meters.toInt()}m"

private fun formatDurationMmSs(s: Int): String {
    if (s <= 0) return "0:00"
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}

private fun formatPct(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
