package com.n3k0chan.spotter.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object StreakCalculator {

    /**
     * Devuelve la racha actual de días con al menos un entreno terminado, contando hasta hoy.
     * Si hoy no hay entreno pero ayer sí, la racha de ayer sigue activa (no se rompe hasta saltarse 2 días).
     * Esto evita "perder" la racha por entrenar a las 23:00 ayer y abrir la app a las 8:00 de hoy.
     */
    fun current(startTimes: List<Long>, today: LocalDate = LocalDate.now()): Int {
        if (startTimes.isEmpty()) return 0
        val days = startTimes.toLocalDates().toSortedSet().reversed()
        var streak = 0
        var cursor = today
        val mostRecent = days.first()
        // Tolerancia: si la última sesión fue ayer, empezamos por ayer.
        if (ChronoUnit.DAYS.between(mostRecent, today) > 1) return 0
        if (mostRecent == today.minusDays(1)) cursor = today.minusDays(1)

        for (day in days) {
            when {
                day == cursor -> {
                    streak += 1
                    cursor = cursor.minusDays(1)
                }
                day.isBefore(cursor) -> break
                // day > cursor (entrenos del mismo día) -> ignora duplicados
            }
        }
        return streak
    }

    fun longest(startTimes: List<Long>): Int {
        if (startTimes.isEmpty()) return 0
        val days = startTimes.toLocalDates().toSortedSet().toList()
        var best = 1
        var cur = 1
        for (i in 1 until days.size) {
            cur = if (days[i] == days[i - 1].plusDays(1)) cur + 1 else 1
            if (cur > best) best = cur
        }
        return best
    }

    fun daysSinceLast(startTimes: List<Long>, today: LocalDate = LocalDate.now()): Int? {
        val last = startTimes.toLocalDates().maxOrNull() ?: return null
        return ChronoUnit.DAYS.between(last, today).toInt()
    }

    private fun List<Long>.toLocalDates(): List<LocalDate> =
        map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
}
