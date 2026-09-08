package com.n3k0chan.spotter.ui.workout

import com.n3k0chan.spotter.data.db.entities.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextExerciseSuggesterTest {

    private fun ex(id: Long, group: String?) = Exercise(id = id, name = "e$id", muscleGroup = group)

    @Test
    fun `returns null with fewer than two session exercises`() {
        val result = NextExerciseSuggester.suggest(
            sessionExercises = listOf(ex(1, "Pecho")),
            catalog = listOf(ex(1, "Pecho"), ex(2, "Pecho")),
            trainingCounts = emptyMap(),
        )
        assertNull(result)
    }

    @Test
    fun `suggests most trained from predominant group not already in session`() {
        val session = listOf(ex(1, "Pecho"), ex(2, "Pecho"))
        val catalog = session + listOf(ex(3, "Pecho"), ex(4, "Pecho"), ex(5, "Pierna"))
        val counts = mapOf(3L to 2, 4L to 9)
        assertEquals(4L, NextExerciseSuggester.suggest(session, catalog, counts))
    }

    @Test
    fun `returns null when no candidate remains in group`() {
        val session = listOf(ex(1, "Pecho"), ex(2, "Pecho"))
        val catalog = session + listOf(ex(5, "Pierna"))
        assertNull(NextExerciseSuggester.suggest(session, catalog, emptyMap()))
    }

    @Test
    fun `breaks group tie using last added exercise`() {
        val session = listOf(ex(1, "Pecho"), ex(2, "Pierna"))
        val catalog = session + listOf(ex(3, "Pecho"), ex(4, "Pierna"))
        assertEquals(4L, NextExerciseSuggester.suggest(session, catalog, emptyMap()))
    }
}
