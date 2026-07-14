package com.n3k0chan.spotter.ai

import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import com.n3k0chan.spotter.data.db.entities.profile
import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import com.n3k0chan.spotter.data.measurement.formatShort

object Prompts {

    /**
     * System prompt común. Tono directo, sin cursiladas.
     * Spanglish-ready: el usuario habla español pero permitimos términos técnicos en inglés.
     */
    val systemTrainer = """
        Eres Spotter, un asistente de gimnasio práctico. Hablas español, breve y directo.
        Reglas:
        - Sin frases cursis, sin emojis innecesarios, sin "tú puedes".
        - Si no estás seguro, dilo en una línea.
        - Cuando sugieras pesos/tiempos, basa tu razonamiento en los datos del historial proporcionados.
        - Los ejercicios pueden medirse de varias formas: peso×reps, solo reps, duración, distancia+tiempo, cardio en máquina (tiempo+distancia+resistencia), etc. Adáptate a lo que veas en los datos.
        - No reemplazas a un médico. Si el usuario menciona dolor agudo, recomienda parar y consultar.
        - Respuestas muy cortas por defecto (1-3 frases) salvo que se pida detalle.
    """.trimIndent()

    fun welcomeMessage(streakDays: Int, lastWorkoutAgoDays: Int?): List<GroqMessage> {
        val context = buildString {
            append("Datos del usuario al abrir la app:\n")
            append("- Racha actual: $streakDays días\n")
            append("- Días desde el último entreno: ${lastWorkoutAgoDays ?: "nunca"}\n")
        }
        return listOf(
            GroqMessage("system", systemTrainer),
            GroqMessage(
                role = "user",
                content = "$context\nDame UNA frase corta (max 12 palabras) para la pantalla de inicio. " +
                    "Sin signos de exclamación, sin clichés tipo 'tú puedes'. Tono seco y motivante.",
            ),
        )
    }

    fun postSessionSummary(workout: WorkoutWithSets, previousSimilar: List<WorkoutSet>): List<GroqMessage> {
        val sb = StringBuilder()
        sb.append("Sesión recién terminada (\"${workout.workout.title}\"):\n")
        workout.sets.groupBy { it.exercise }.forEach { (exercise, sets) ->
            val profile = exercise.profile ?: return@forEach
            sb.append("- ${exercise.name} [${profile.display}]: ")
            sb.append(sets.joinToString(", ") { it.set.formatShort(profile) })
            sb.append("\n")
        }
        if (previousSimilar.isNotEmpty()) {
            sb.append("\nReferencia de series en sesiones anteriores (para comparar PRs, volumen total, etc):\n")
            previousSimilar.take(20).forEach {
                sb.append("- ").append(formatRawSet(it)).append("\n")
            }
        }
        return listOf(
            GroqMessage("system", systemTrainer + "\nDebes responder ÚNICAMENTE con un objeto JSON válido, sin markdown ni explicaciones adicionales fuera del JSON."),
            GroqMessage(
                role = "user",
                content = sb.toString() + "\nCompara el total de cada ejercicio (volumen = peso x reps, o tiempo x distancia, etc) con los datos anteriores. " +
                    "Desglosa puntos fuertes y débiles frente a entrenamientos anteriores (sobre todo entre la mejor serie y la más parecida hecha hoy). " +
                    "Ten en cuenta el nombre y tipo del ejercicio (ej. si es Just Dance, valora el esfuerzo aeróbico). " +
                    "Responde EXACTAMENTE con este JSON:\n" +
                    "{\n" +
                    "  \"summary\": \"Descripción pequeña general del entrenamiento (2-3 frases)\",\n" +
                    "  \"exercises\": [\n" +
                    "    {\n" +
                    "      \"name\": \"Nombre del ejercicio\",\n" +
                    "      \"markdown\": \"Tu veredicto detallado para este ejercicio en formato Markdown\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}"
            ),
        )
    }

    fun nextSetSuggestion(
        exerciseName: String,
        profile: MeasurementProfile,
        recentHistory: List<WorkoutSet>,
        currentSets: List<WorkoutSet>,
    ): List<GroqMessage> {
        val sb = StringBuilder()
        sb.append("Ejercicio: $exerciseName\n")
        sb.append("Tipo de medida: ").append(profile.display).append("\n")
        sb.append("Series ya hechas hoy: ")
        sb.append(
            if (currentSets.isEmpty()) "ninguna"
            else currentSets.joinToString(", ") { it.formatShort(profile) },
        )
        sb.append("\nHistorial reciente del ejercicio (más reciente primero):\n")
        recentHistory.take(8).forEach { sb.append("- ").append(it.formatShort(profile)).append("\n") }

        // Adaptamos el formato esperado a la naturaleza del ejercicio
        val expectedFormat = when (profile) {
            MeasurementProfile.WeightReps -> "<peso>kg x <reps> · <razón breve>"
            MeasurementProfile.Reps -> "<reps> reps · <razón breve>"
            MeasurementProfile.Duration -> "<segundos>s · <razón breve>"
            MeasurementProfile.DistanceTime -> "<distancia>m en <tiempo> · <razón breve>"
            MeasurementProfile.CardioMachine -> "<tiempo> a nivel <n> (~<distancia>m) · <razón breve>"
            MeasurementProfile.TreadmillIncline -> "<tiempo> a <inclinación>% (~<distancia>m) · <razón breve>"
            MeasurementProfile.WeightDuration -> "<peso>kg durante <tiempo> · <razón breve>"
        }
        return listOf(
            GroqMessage("system", systemTrainer),
            GroqMessage(
                role = "user",
                content = sb.toString() + "\nSugiere los valores para la SIGUIENTE serie. " +
                    "Responde EXACTAMENTE en formato: $expectedFormat. Nada más.",
            ),
        )
    }

    fun healthConnectImport(sessionsJson: String): List<GroqMessage> = listOf(
        GroqMessage(
            "system",
            """
            Eres un asistente que procesa datos de sesiones de ejercicio de Health Connect.
            Recibes un JSON con sesiones crudas y debes devolver un JSON con la misma cantidad de elementos.
            Para cada sesión:
            - "title": un nombre descriptivo en español (ej: "Carrera 5K", "Fuerza tren superior", "Caminata"). Si ya tiene título bueno, mantenlo.
            - "label": una etiqueta corta para mostrar como chip (ej: "Cardio", "Fuerza", "HIIT", "Flexibilidad", "Caminar"). Máximo 12 caracteres.
            - "skip": true si la sesión NO parece ejercicio real (ej: tipo desconocido sin datos, duraciones de <2min). false si se debe importar.
            Responde SOLO con un JSON array, sin markdown, sin explicaciones. Ejemplo:
            [{"title":"Carrera matutina","label":"Cardio","skip":false},{"title":"Desconocido","label":"","skip":true}]
            """.trimIndent(),
        ),
        GroqMessage("user", sessionsJson),
    )

    fun chatTurn(history: List<GroqMessage>, userInput: String): List<GroqMessage> =
        buildList {
            add(GroqMessage("system", systemTrainer))
            addAll(history)
            add(GroqMessage("user", userInput))
        }

    /**
     * Formato fallback cuando solo tenemos un WorkoutSet pelado (sin acceso al
     * exercise/profile). Mostramos los campos no nulos con un símbolo.
     */
    private fun formatRawSet(s: WorkoutSet): String {
        val parts = mutableListOf<String>()
        s.weightKg?.let { parts += "${if (it % 1.0 == 0.0) it.toInt() else "%.1f".format(it)}kg" }
        s.reps?.let { parts += "${it} reps" }
        s.distanceMeters?.let {
            parts += if (it >= 1000) "%.2fkm".format(it / 1000) else "${it.toInt()}m"
        }
        s.durationSeconds?.let { parts += "%d:%02d".format(it / 60, it % 60) }
        s.resistanceLevel?.let { parts += "n${it}" }
        s.inclinePercent?.let { parts += "${if (it % 1.0 == 0.0) it.toInt() else "%.1f".format(it)}%" }
        return parts.joinToString(" · ").ifEmpty { "—" }
    }
}
