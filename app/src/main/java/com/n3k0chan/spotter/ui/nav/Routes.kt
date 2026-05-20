package com.n3k0chan.spotter.ui.nav

import androidx.annotation.StringRes
import com.n3k0chan.spotter.R

enum class TopLevelRoute(val route: String, @StringRes val labelRes: Int) {
    Home("home", R.string.nav_home),
    Workout("workout_root", R.string.nav_workout),
    History("history", R.string.nav_history),
    Stats("stats", R.string.nav_stats),
}

object Routes {
    const val Home = "home"
    const val WorkoutRoot = "workout_root"

    // Detail / sub-screens
    const val Templates = "templates"
    fun templateEditor(id: Long?) = "template_editor?id=${id ?: -1L}"
    const val TemplateEditorPattern = "template_editor?id={id}"

    const val Exercises = "exercises"

    fun workoutSession(workoutId: Long) = "workout/$workoutId"
    const val WorkoutSessionPattern = "workout/{workoutId}"

    const val Chat = "chat"

    const val History = "history"
    const val Stats = "stats"
    const val Settings = "settings"
    const val Health = "health"

    fun workoutDetail(workoutId: Long) = "workout_detail/$workoutId"
    const val WorkoutDetailPattern = "workout_detail/{workoutId}"
}
