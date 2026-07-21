package com.n3k0chan.spotter.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.db.entities.WorkoutSetWithExercise
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import com.n3k0chan.spotter.data.db.entities.profile
import com.n3k0chan.spotter.data.health.WorkoutHealthMetrics
import com.n3k0chan.spotter.data.measurement.formatShort
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.MuscleGroupAvatar
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterChip
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import com.n3k0chan.spotter.ui.workout.AiSummaryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.time.Instant
import java.util.Date

data class WorkoutDetailState(
    val loading: Boolean = true,
    val workout: WorkoutWithSets? = null,
    val healthMetrics: WorkoutHealthMetrics? = null,
)

class WorkoutDetailViewModel(private val workoutId: Long) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutDetailState())
    val state: StateFlow<WorkoutDetailState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val w = ServiceLocator.workouts.get(workoutId)
            if (w == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            _state.update { it.copy(loading = false, workout = w) }

            val finished = w.workout.finishedAt ?: return@launch
            val hc = ServiceLocator.healthConnect
            if (!hc.isAvailable()) return@launch
            val hasPerms = runCatching { hc.hasAllPermissions() }.getOrDefault(false)
            if (!hasPerms) return@launch

            val metrics = runCatching {
                hc.readMetricsForTimeRange(
                    Instant.ofEpochMilli(w.workout.startedAt),
                    Instant.ofEpochMilli(finished),
                )
            }.getOrNull()
            _state.update { it.copy(healthMetrics = metrics) }
        }
    }

    companion object {
        fun factory(workoutId: Long) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WorkoutDetailViewModel(workoutId) as T
            }
        }
    }
}

@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onBack: () -> Unit,
) {
    val vm: WorkoutDetailViewModel = viewModel(factory = WorkoutDetailViewModel.factory(workoutId))
    val state by vm.state.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = state.workout?.workout?.title ?: "Detalle",
                leading = {
                    SpotterIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = c.primary)
            }
            state.workout == null -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Entreno no encontrado", style = SpotterText.body, color = c.textMuted)
            }
            else -> DetailContent(
                modifier = Modifier.padding(padding),
                w = state.workout!!,
                metrics = state.healthMetrics,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    modifier: Modifier = Modifier,
    w: WorkoutWithSets,
    metrics: WorkoutHealthMetrics?,
) {
    val c = SpotterTheme.colors
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val exercises = remember(w) {
        w.sets
            .groupBy { it.exercise }
            .entries
            .sortedBy { (_, sets) -> sets.minOf { it.set.orderIndex } }
            .map { (exercise, sets) -> exercise to sets }
    }
    val durationMin = w.workout.finishedAt
        ?.let { (it - w.workout.startedAt) / 60_000 }
        ?.toInt()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SpotterCard {
                Column {
                    Text(
                        df.format(Date(w.workout.startedAt)),
                        style = SpotterText.body,
                        color = c.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (durationMin != null) SpotterChip("$durationMin min")
                        if (w.workout.rpe != null) SpotterChip("RPE ${w.workout.rpe}", leading = Icons.Filled.Warning)
                        SpotterChip("${exercises.size} ejercicios")
                        SpotterChip("${w.sets.size} series")
                    }
                }
            }
        }
        
        if (w.workout.aiSummaryJson != null) {
            item {
                val summary = remember(w.workout.aiSummaryJson) {
                    runCatching {
                        Json { ignoreUnknownKeys = true }.decodeFromString<AiSummaryResponse>(w.workout.aiSummaryJson)
                    }.getOrNull()
                }
                
                if (summary != null) {
                    SpotterCard(
                        background = c.primarySoft,
                        border = c.primarySoft,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = c.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("RESUMEN IA", style = SpotterText.caps, color = c.primarySoftText)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(summary.summary, style = SpotterText.body, color = c.text)
                            
                            if (summary.exercises.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                val pagerState = rememberPagerState(pageCount = { summary.exercises.size })
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxWidth()
                                ) { page ->
                                    val ex = summary.exercises[page]
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(c.surface)
                                            .padding(12.dp)
                                    ) {
                                        Text(ex.name, style = SpotterText.smallMd, color = c.primary)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            ex.markdown,
                                            style = SpotterText.small,
                                            color = c.text
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    repeat(summary.exercises.size) { iteration ->
                                        val color = if (pagerState.currentPage == iteration) c.primary else c.primarySoftText.copy(alpha = 0.3f)
                                        Box(
                                            modifier = Modifier
                                                .padding(2.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(color)
                                                .size(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (metrics != null) {
            item {
                Text("Datos del reloj", style = SpotterText.title3, color = c.text)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HealthMetricCard(
                        icon = Icons.Filled.LocalFireDepartment,
                        iconTint = Color(0xFFEF4444),
                        label = "Calorías",
                        value = metrics.calories?.let { "%.0f".format(it) } ?: "—",
                        unit = "kcal",
                        modifier = Modifier.weight(1f),
                    )
                    HealthMetricCard(
                        icon = Icons.Filled.Favorite,
                        iconTint = Color(0xFFEC4899),
                        label = "FC media",
                        value = metrics.heartRateAvg?.toString() ?: "—",
                        unit = "bpm",
                        detail = if (metrics.heartRateMin != null && metrics.heartRateMax != null)
                            "${metrics.heartRateMin}–${metrics.heartRateMax} bpm" else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HealthMetricCard(
                        icon = Icons.Filled.Route,
                        iconTint = Color(0xFF3B82F6),
                        label = "Distancia",
                        value = metrics.distanceMeters?.let {
                            if (it >= 1000) "%.2f".format(it / 1000) else "%.0f".format(it)
                        } ?: "—",
                        unit = if ((metrics.distanceMeters ?: 0.0) >= 1000) "km" else "m",
                        modifier = Modifier.weight(1f),
                    )
                    HealthMetricCard(
                        icon = Icons.Filled.DirectionsWalk,
                        iconTint = SpotterTheme.colors.primary,
                        label = "Pasos",
                        value = metrics.steps?.toString() ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (!w.workout.notes.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceMuted)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Notes,
                        contentDescription = null,
                        tint = c.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(w.workout.notes, style = SpotterText.small, color = c.textMuted)
                }
            }
        }

        if (exercises.isNotEmpty()) {
            item {
                Text("Ejercicios", style = SpotterText.title3, color = c.text)
            }
            items(exercises) { (exercise, sets) ->
                DetailExerciseCard(exercise = exercise, sets = sets)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailExerciseCard(
    exercise: Exercise,
    sets: List<WorkoutSetWithExercise>,
) {
    val c = SpotterTheme.colors
    val profile = exercise.profile
    SpotterCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            MuscleGroupAvatar(
                rawGroup = exercise.muscleGroup,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        exercise.name,
                        style = SpotterText.title3,
                        color = c.text,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${sets.size} series",
                        style = SpotterText.small,
                        color = c.textFaint,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    sets.forEachIndexed { idx, ws ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.primarySoft)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "${idx + 1}. ${ws.set.formatShort(profile)}",
                                style = SpotterText.numS,
                                color = c.primarySoftText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthMetricCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    unit: String? = null,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = SpotterTheme.colors
    SpotterCard(modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(label, style = SpotterText.smallMd, color = c.textMuted)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = SpotterText.numM, color = c.text)
                if (unit != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        style = SpotterText.small,
                        color = c.textMuted,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = SpotterText.small, color = c.textFaint)
            }
        }
    }
}
