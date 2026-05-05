package com.n3k0chan.spotter.motivation

import kotlin.random.Random

/**
 * Frases motivacionales de respaldo (cuando no hay API key o cuando falla la IA).
 * Tono seco, sin cursilería, sin "tú puedes". Mensajes cortos.
 */
object MotivationalMessages {

    private val noStreak = listOf(
        "Hoy se rompe la racha de no entrenar.",
        "Empezar es la parte fea. Hazlo.",
        "Sin entrenar ayer. Hoy toca volver.",
        "El primer set de la semana siempre cuesta más.",
        "Cero entrenos esta semana. Cambia eso.",
    )

    private val onStreak = listOf(
        "Día %d seguido. No bajes el listón.",
        "Llevas %d días. Sigue.",
        "%d días consecutivos. Repite hoy.",
        "Racha de %d. Hoy una serie más que ayer.",
    )

    private val midSession = listOf(
        "Una serie buena es mejor que dos a medias.",
        "Técnica primero, peso después.",
        "Si llegas con margen, sube 2,5 kg.",
        "Última repetición limpia o no la cuentes.",
        "Descansa lo que pone, no lo que te apetece.",
    )

    private val postSession = listOf(
        "Hecho. Mañana decide tu yo de hoy.",
        "Sesión registrada. Sin drama.",
        "Cerrado. Come y duerme.",
        "Fin. La consistencia es el truco.",
    )

    fun forHomeScreen(streakDays: Int): String = when {
        streakDays <= 0 -> noStreak.random()
        else -> onStreak.random().format(streakDays)
    }

    fun midWorkout(): String = midSession.random()
    fun afterWorkout(): String = postSession.random()

    /** Útil para tests deterministas. */
    fun forHomeScreen(streakDays: Int, random: Random): String = when {
        streakDays <= 0 -> noStreak[random.nextInt(noStreak.size)]
        else -> onStreak[random.nextInt(onStreak.size)].format(streakDays)
    }
}
