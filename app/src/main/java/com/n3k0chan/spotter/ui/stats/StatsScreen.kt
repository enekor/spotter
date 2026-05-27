package com.n3k0chan.spotter.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import androidx.compose.material3.Icon
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import com.n3k0chan.spotter.util.StreakCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class ExerciseProgressData(
    val exerciseId: Long,
    val exerciseName: String,
    val points: List<Float>,
    val latestValue: String,
    val unit: String,
)

class StatsViewModel : ViewModel() {
    private val workouts = ServiceLocator.workouts
    private val exercises = ServiceLocator.exercises

    val totalSessions: StateFlow<Int> = workouts.observeFinishedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val thisWeekSessions: StateFlow<Int> = run {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        workouts.observeFinishedCountSince(monday)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val currentStreak: StateFlow<Int> = workouts.observeFinishedStartTimes()
        .map { StreakCalculator.current(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val longestStreak: StateFlow<Int> = workouts.observeFinishedStartTimes()
        .map { StreakCalculator.longest(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val weeksStreak: StateFlow<Int> = workouts.observeFinishedStartTimes()
        .map { StreakCalculator.currentWeeks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val workoutDates: StateFlow<Set<LocalDate>> = workouts.observeFinishedStartTimes()
        .map { timestamps ->
            timestamps.map { ms ->
                java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _exerciseProgress = MutableStateFlow<List<ExerciseProgressData>>(emptyList())
    val exerciseProgress: StateFlow<List<ExerciseProgressData>> = _exerciseProgress.asStateFlow()

    init {
        viewModelScope.launch { loadExerciseProgress() }
    }

    private suspend fun loadExerciseProgress() {
        val sets = workouts.getTopExerciseSets(5)
        if (sets.isEmpty()) return

        val grouped = sets.groupBy { it.exerciseId }
        val result = grouped.mapNotNull { (exId, exSets) ->
            val exercise = exercises.get(exId) ?: return@mapNotNull null

            val byWorkout = exSets.groupBy { it.workoutId }
            val sessionMaxes = byWorkout.values.mapNotNull { sessionSets ->
                val maxWeight = sessionSets.mapNotNull { it.weightKg }.maxOrNull()
                if (maxWeight != null && maxWeight > 0) return@mapNotNull maxWeight
                val maxReps = sessionSets.mapNotNull { it.reps }.maxOrNull()
                if (maxReps != null && maxReps > 0) return@mapNotNull maxReps.toDouble()
                val maxDist = sessionSets.mapNotNull { it.distanceMeters }.maxOrNull()
                if (maxDist != null && maxDist > 0) return@mapNotNull maxDist
                null
            }
            if (sessionMaxes.size < 2) return@mapNotNull null

            val hasWeight = exSets.any { (it.weightKg ?: 0.0) > 0 }
            val unit = if (hasWeight) "kg" else "reps"
            val latest = sessionMaxes.last()
            val latestStr = if (latest % 1.0 == 0.0) latest.toInt().toString() else "%.1f".format(latest)

            ExerciseProgressData(
                exerciseId = exId,
                exerciseName = exercise.name,
                points = sessionMaxes.map { it.toFloat() },
                latestValue = latestStr,
                unit = unit,
            )
        }.sortedByDescending { it.points.size }

        _exerciseProgress.value = result
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StatsViewModel() as T
            }
        }
    }
}

@Composable
fun StatsScreen(
    onOpenSettings: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val total by vm.totalSessions.collectAsStateWithLifecycle()
    val week by vm.thisWeekSessions.collectAsStateWithLifecycle()
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val longest by vm.longestStreak.collectAsStateWithLifecycle()
    val weeksStreak by vm.weeksStreak.collectAsStateWithLifecycle()
    val workoutDates by vm.workoutDates.collectAsStateWithLifecycle()
    val exerciseProgress by vm.exerciseProgress.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = "Stats",
                trailing = {
                    Row {
                        SpotterIconButton(Icons.AutoMirrored.Filled.Chat, onClick = onOpenChat)
                        SpotterIconButton(Icons.Filled.Settings, onClick = onOpenSettings)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Sesiones totales", "$total", null, modifier = Modifier.weight(1f))
                    StatTile("Esta semana", "$week", "objetivo 4", modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Racha actual", "$streak", "días", modifier = Modifier.weight(1f))
                    StatTile("Racha semanal", "$weeksStreak", if (weeksStreak == 1) "semana" else "semanas", modifier = Modifier.weight(1f))
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                WorkoutCalendar(workoutDates = workoutDates)
            }
            if (exerciseProgress.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("PROGRESO POR EJERCICIO", style = SpotterText.caps, color = c.textMuted,
                        modifier = Modifier.padding(start = 4.dp))
                }
                items(exerciseProgress.size) { idx ->
                    val data = exerciseProgress[idx]
                    ExerciseSparklineCard(data)
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                HealthConnectCard(onClick = onOpenHealth)
            }
        }
    }
}

@Composable
private fun WorkoutCalendar(workoutDates: Set<LocalDate>) {
    val c = SpotterTheme.colors
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()
    val isCurrentMonth = currentMonth == YearMonth.now()

    val monthLabel = currentMonth.month.getDisplayName(TextStyle.FULL, Locale("es"))
        .replaceFirstChar { it.uppercase() } + " ${currentMonth.year}"

    SpotterCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpotterIconButton(
                    icon = Icons.Filled.ChevronLeft,
                    onClick = { currentMonth = currentMonth.minusMonths(1) },
                )
                Text(monthLabel, style = SpotterText.title3, color = c.text)
                if (isCurrentMonth) {
                    Box(Modifier.size(40.dp))
                } else {
                    SpotterIconButton(
                        icon = Icons.Filled.ChevronRight,
                        onClick = { currentMonth = currentMonth.plusMonths(1) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                dayLabels.forEach { label ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = SpotterText.caps, color = c.textFaint)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val firstDay = currentMonth.atDay(1)
            val startOffset = (firstDay.dayOfWeek.value - 1) // Mon=0
            val daysInMonth = currentMonth.lengthOfMonth()

            val cells = mutableListOf<LocalDate?>()
            repeat(startOffset) { cells.add(null) }
            for (d in 1..daysInMonth) cells.add(currentMonth.atDay(d))
            while (cells.size % 7 != 0) cells.add(null)

            val rows = cells.chunked(7)
            rows.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    week.forEach { date ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                val hasWorkout = date in workoutDates
                                val isToday = date == today
                                val isFuture = date.isAfter(today)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                hasWorkout -> c.primary
                                                isToday -> c.primarySoft
                                                else -> c.bg
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${date.dayOfMonth}",
                                        style = SpotterText.smallMd,
                                        color = when {
                                            hasWorkout -> c.onPrimary
                                            isFuture -> c.textFaint
                                            isToday -> c.primary
                                            else -> c.text
                                        },
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val monthWorkouts = workoutDates.count {
                it.year == currentMonth.year && it.monthValue == currentMonth.monthValue
            }
            if (monthWorkouts > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "$monthWorkouts sesiones este mes",
                    style = SpotterText.small,
                    color = c.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    val c = SpotterTheme.colors
    SpotterCard(modifier = modifier, padding = 16.dp) {
        Column {
            Text(label, style = SpotterText.caps, color = c.textMuted)
            Spacer(Modifier.height(12.dp))
            Text(value, style = SpotterText.numL, color = c.text)
            if (sub != null) {
                Spacer(Modifier.height(4.dp))
                Text(sub, style = SpotterText.small, color = c.textFaint)
            }
        }
    }
}

@Composable
private fun HealthConnectCard(onClick: () -> Unit) {
    val c = SpotterTheme.colors
    SpotterCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Health Connect", style = SpotterText.bodyMd, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Pasos, calorías, sueño y más desde tus dispositivos",
                    style = SpotterText.small,
                    color = c.textMuted,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = c.textFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ExerciseSparklineCard(data: ExerciseProgressData) {
    val c = SpotterTheme.colors
    SpotterCard(padding = 16.dp) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(data.exerciseName, style = SpotterText.bodyMd, color = c.text)
                    Spacer(Modifier.height(2.dp))
                    Text("${data.points.size} sesiones", style = SpotterText.small, color = c.textMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${data.latestValue} ${data.unit}", style = SpotterText.numM, color = c.primary)
                    val firstVal = data.points.first()
                    val lastVal = data.points.last()
                    if (firstVal > 0f) {
                        val pct = ((lastVal - firstVal) / firstVal * 100).toInt()
                        val label = if (pct >= 0) "+$pct%" else "$pct%"
                        Text(label, style = SpotterText.small, color = if (pct >= 0) c.success else c.danger)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Sparkline(points = data.points, modifier = Modifier.fillMaxWidth().height(64.dp))
        }
    }
}

@Composable
fun Sparkline(points: List<Float>, modifier: Modifier = Modifier.fillMaxWidth().height(80.dp)) {
    val c = SpotterTheme.colors
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val pad = 4.dp.toPx()
        val w = size.width
        val h = size.height
        val min = points.min()
        val max = points.max()
        val span = (max - min).coerceAtLeast(0.001f)
        val xs = points.indices.map { i -> pad + i.toFloat() / (points.size - 1) * (w - pad * 2) }
        val ys = points.map { p -> pad + (1f - (p - min) / span) * (h - pad * 2) }

        val fill = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until points.size) lineTo(xs[i], ys[i])
            lineTo(xs.last(), h)
            lineTo(xs.first(), h)
            close()
        }
        drawPath(path = fill, color = c.chartFill)

        val line = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until points.size) lineTo(xs[i], ys[i])
        }
        drawPath(
            path = line,
            color = c.primary,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(color = c.primary, radius = 3.dp.toPx(), center = Offset(xs.last(), ys.last()))
    }
}
