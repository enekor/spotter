package com.n3k0chan.spotter.data.db.entities

import androidx.room.Embedded
import androidx.room.Relation

data class TemplateExerciseWithExercise(
    @Embedded val templateExercise: TemplateExercise,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id",
    )
    val exercise: Exercise,
)

data class TemplateWithExercises(
    @Embedded val template: Template,
    @Relation(
        entity = TemplateExercise::class,
        parentColumn = "id",
        entityColumn = "templateId",
    )
    val items: List<TemplateExerciseWithExercise>,
)

data class WorkoutSetWithExercise(
    @Embedded val set: WorkoutSet,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id",
    )
    val exercise: Exercise,
)

data class WorkoutWithSets(
    @Embedded val workout: Workout,
    @Relation(
        entity = WorkoutSet::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val sets: List<WorkoutSetWithExercise>,
)
