package com.n3k0chan.spotter.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import com.n3k0chan.spotter.data.db.entities.profile
import com.n3k0chan.spotter.data.measurement.formatShort
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.MuscleGroupAvatar
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class WorkoutDetailViewModel(private val workoutId: Long) : ViewModel() {
    private val _workout = MutableStateFlow<WorkoutWithSets?>(null)
    val workout: StateFlow<WorkoutWithSets?> = _workout

    init {
        viewModelScope.launch {
            _workout.value = ServiceLocator.workouts.get(workoutId)
        }
    }

    companion object {
        fun provideFactory(id: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WorkoutDetailViewModel(id) as T
            }
        }
    }
}

@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onBack: () -> Unit,
    vm: WorkoutDetailViewModel = viewModel(factory = WorkoutDetailViewModel.provideFactory(workoutId))
) {
    val workout by vm.workout.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = workout?.workout?.title ?: "Detalle",
                leading = { SpotterIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack) }
            )
        }
    ) { innerPadding ->
        workout?.let { w ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(df.format(Date(w.workout.startedAt)), style = SpotterText.body, color = c.textMuted)
                    if (!w.workout.notes.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(w.workout.notes, style = SpotterText.body, color = c.text)
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = c.border)
                }

                val exercises = w.sets.groupBy { it.exercise }
                items(exercises.toList()) { (exercise, sets) ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MuscleGroupAvatar(rawGroup = exercise.muscleGroup, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(exercise.name, style = SpotterText.title3, color = c.text)
                        }
                        Spacer(Modifier.height(8.dp))
                        sets.sortedBy { it.set.orderIndex }.forEach { s ->
                            Text(
                                text = "Serie ${s.set.orderIndex + 1}: ${s.set.formatShort(exercise.profile)}",
                                style = SpotterText.body,
                                color = c.text,
                                modifier = Modifier.padding(start = 44.dp, top = 2.dp, bottom = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp)
                }
            }
        }
    }
}
