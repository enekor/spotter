package com.n3k0chan.spotter.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.util.StreakCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class StatsViewModel : ViewModel() {

    private val workouts = ServiceLocator.workouts

    val totalSessions: StateFlow<Int> = workouts.observeFinishedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val thisWeekSessions: StateFlow<Int> = run {
        val monday = LocalDate.now()
            .with(java.time.DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val total by vm.totalSessions.collectAsStateWithLifecycle()
    val week by vm.thisWeekSessions.collectAsStateWithLifecycle()
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val longest by vm.longestStreak.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.stats_total_sessions), total.toString(), Modifier.weight(1f))
                StatCard(stringResource(R.string.stats_this_week), week.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.stats_current_streak), "$streak d", Modifier.weight(1f))
                StatCard(stringResource(R.string.stats_longest_streak), "$longest d", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        }
    }
}
