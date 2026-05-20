package com.n3k0chan.spotter.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.n3k0chan.spotter.ui.chat.ChatScreen
import com.n3k0chan.spotter.ui.health.HealthScreen
import com.n3k0chan.spotter.ui.exercises.ExercisesScreen
import com.n3k0chan.spotter.ui.history.HistoryScreen
import com.n3k0chan.spotter.ui.history.WorkoutDetailScreen
import com.n3k0chan.spotter.ui.home.HomeScreen
import com.n3k0chan.spotter.ui.settings.SettingsScreen
import com.n3k0chan.spotter.ui.stats.StatsScreen
import com.n3k0chan.spotter.ui.templates.TemplateEditorScreen
import com.n3k0chan.spotter.ui.templates.TemplatesScreen
import com.n3k0chan.spotter.ui.workout.WorkoutHubScreen
import com.n3k0chan.spotter.ui.workout.WorkoutScreen

@Composable
fun SpotterNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier,
    ) {
        composable(Routes.Home) {
            HomeScreen(
                onStartFreeWorkout = { workoutId ->
                    navController.navigate(Routes.workoutSession(workoutId))
                },
                onPickTemplate = { navController.navigate(Routes.WorkoutRoot) },
                onOpenChat = { navController.navigate(Routes.Chat) },
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }

        composable(Routes.WorkoutRoot) {
            WorkoutHubScreen(
                onStartFreeWorkout = { id -> navController.navigate(Routes.workoutSession(id)) },
                onStartFromTemplate = { id -> navController.navigate(Routes.workoutSession(id)) },
                onOpenTemplates = { navController.navigate(Routes.Templates) },
                onOpenExercises = { navController.navigate(Routes.Exercises) },
            )
        }

        composable(Routes.Templates) {
            TemplatesScreen(
                onCreateNew = { navController.navigate(Routes.templateEditor(null)) },
                onEdit = { id -> navController.navigate(Routes.templateEditor(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TemplateEditorPattern,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: -1L
            TemplateEditorScreen(
                templateId = id.takeIf { it > 0 },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.Exercises) {
            ExercisesScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.WorkoutSessionPattern,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("workoutId") ?: 0L
            WorkoutScreen(
                workoutId = id,
                onFinished = { navController.popBackStack(Routes.Home, inclusive = false) },
                onOpenChat = { navController.navigate(Routes.Chat) },
            )
        }

        composable(Routes.Chat) {
            ChatScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.History) {
            HistoryScreen(
                onWorkoutClick = { workoutId ->
                    navController.navigate(Routes.workoutDetail(workoutId))
                },
            )
        }

        composable(
            route = Routes.WorkoutDetailPattern,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("workoutId") ?: 0L
            WorkoutDetailScreen(
                workoutId = id,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.Stats) {
            StatsScreen()
        }

        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenHealth = { navController.navigate(Routes.Health) },
            )
        }

        composable(Routes.Health) {
            HealthScreen(onBack = { navController.popBackStack() })
        }
    }
}
