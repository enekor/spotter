package com.n3k0chan.spotter.ui.workout

import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutComparatorTest {

    private fun wr(workoutId: Long, weight: Double, reps: Int) = WorkoutSet(
        workoutId = workoutId, exerciseId = 1, orderIndex = 0, setNumber = 1,
        weightKg = weight, reps = reps,
    )

    @Test
    fun `volume for weightReps is sum of weight times reps`() {
        val sets = listOf(wr(1, 100.0, 5), wr(1, 100.0, 5))
        assertEquals(1000.0, WorkoutComparator.volumeOf(sets, MeasurementProfile.WeightReps), 0.0)
    }

    @Test
    fun `detects PR when today beats history`() {
        val input = ExerciseComparisonInput(
            name = "Press", profile = MeasurementProfile.WeightReps,
            todaySets = listOf(wr(2, 105.0, 5)),
            historySets = listOf(wr(1, 100.0, 5)),
        )
        assertTrue(WorkoutComparator.isPr(input))
    }

    @Test
    fun `no PR when today equals history`() {
        val input = ExerciseComparisonInput(
            name = "Press", profile = MeasurementProfile.WeightReps,
            todaySets = listOf(wr(2, 100.0, 5)),
            historySets = listOf(wr(1, 100.0, 5)),
        )
        assertFalse(WorkoutComparator.isPr(input))
    }

    @Test
    fun `no previous data yields explanatory verdict`() {
        val comparison = WorkoutComparator.compare(
            listOf(
                ExerciseComparisonInput(
                    "Press", MeasurementProfile.WeightReps,
                    todaySets = listOf(wr(2, 100.0, 5)), historySets = emptyList(),
                ),
            ),
        )
        assertEquals(1, comparison.exercises.size)
        assertTrue(comparison.exercises[0].markdown.contains("Sin datos previos"))
    }
}
