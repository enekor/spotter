package com.n3k0chan.spotter.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

class HistoryViewModel : ViewModel() {
    val list: StateFlow<List<WorkoutWithSets>> = ServiceLocator.workouts.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel() as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val list by vm.list.collectAsStateWithLifecycle()
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (list.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.workout.id }) { w ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(w.workout.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = df.format(Date(w.workout.startedAt)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val grouped = w.sets.groupBy { it.exercise.name }
                                grouped.forEach { (name, sets) ->
                                    Text(
                                        "· $name: " + sets.joinToString(", ") {
                                            "${formatWeight(it.set.weightKg)}kg×${it.set.reps}"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                if (w.workout.rpe != null) {
                                    Text(
                                        "RPE ${w.workout.rpe}",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                if (!w.workout.notes.isNullOrBlank()) {
                                    Text(
                                        "“${w.workout.notes}”",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
