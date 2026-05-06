package com.n3k0chan.spotter.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import com.n3k0chan.spotter.util.StreakCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class StatsViewModel : ViewModel() {
    private val workouts = ServiceLocator.workouts

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
fun StatsScreen(vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val total by vm.totalSessions.collectAsStateWithLifecycle()
    val week by vm.thisWeekSessions.collectAsStateWithLifecycle()
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val longest by vm.longestStreak.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors

    Scaffold(
        containerColor = c.bg,
        topBar = { SpotterTopBar(title = "Stats") },
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
                    StatTile("Racha más larga", "$longest", "días", modifier = Modifier.weight(1f))
                }
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
