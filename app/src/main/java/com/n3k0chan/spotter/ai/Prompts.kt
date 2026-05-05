package com.n3k0chan.spotter.ai

import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets

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
        - Cuando sugieras pesos, basa tu razonamiento en los datos del historial proporcionados.
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
        workout.sets.groupBy { it.exercise.name }.forEach { (name, sets) ->
            sb.append("- $name: ")
            sb.append(sets.joinToString(", ") { "${it.set.weightKg}kg×${it.set.reps}" })
            sb.append("\n")
        }
        if (previousSimilar.isNotEmpty()) {
            sb.append("\nReferencia de sesiones anteriores (mismo entreno):\n")
            previousSimilar.take(8).forEach {
                sb.append("- ${it.weightKg}kg×${it.reps}\n")
            }
        }
        return listOf(
            GroqMessage("system", systemTrainer),
            GroqMessage(
                role = "user",
                content = sb.toString() + "\nDame un resumen en 2-3 frases: progreso vs anteriores, " +
                    "qué destacar y un punto a vigilar la próxima vez. Sin cursiladas.",
            ),
        )
    }

    fun nextSetSuggestion(
        exerciseName: String,
        recentHistory: List<WorkoutSet>,
        currentSets: List<WorkoutSet>,
    ): List<GroqMessage> {
        val sb = StringBuilder()
        sb.append("Ejercicio: $exerciseName\n")
        sb.append("Series ya hechas hoy: ")
        sb.append(if (currentSets.isEmpty()) "ninguna" else currentSets.joinToString(", ") { "${it.weightKg}kg×${it.reps}" })
        sb.append("\nHistorial reciente del ejercicio (más reciente primero):\n")
        recentHistory.take(8).forEach { sb.append("- ${it.weightKg}kg×${it.reps}\n") }
        return listOf(
            GroqMessage("system", systemTrainer),
            GroqMessage(
                role = "user",
                content = sb.toString() + "\nSugiere peso y reps para la SIGUIENTE serie. " +
                    "Responde EXACTAMENTE en formato: <peso>kg x <reps> · <razón breve>. Nada más.",
            ),
        )
    }

    fun chatTurn(history: List<GroqMessage>, userInput: String): List<GroqMessage> =
        buildList {
            add(GroqMessage("system", systemTrainer))
            addAll(history)
            add(GroqMessage("user", userInput))
        }
}
