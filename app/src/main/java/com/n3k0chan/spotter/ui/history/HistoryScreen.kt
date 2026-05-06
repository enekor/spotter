package com.n3k0chan.spotter.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.IconButtonTone
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterChip
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
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

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val list by vm.list.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = "Historial",
                trailing = { SpotterIconButton(Icons.Filled.MoreVert, tone = IconButtonTone.Muted) },
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Text("Aún no has registrado entrenos.", style = SpotterText.body, color = c.textMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.workout.id }) { w -> SessionCard(w, df) }
            }
        }
    }
}

@Composable
private fun SessionCard(w: WorkoutWithSets, df: DateFormat) {
    val c = SpotterTheme.colors
    val byExercise = w.sets.groupBy { it.exercise.name }
    val durationMin = w.workout.finishedAt
        ?.let { (it - w.workout.startedAt) / 60_000 }
        ?.toInt()

    SpotterCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.workout.title, style = SpotterText.title3, color = c.text, modifier = Modifier.weight(1f))
                Text(
                    df.format(Date(w.workout.startedAt)),
                    style = SpotterText.small,
                    color = c.textMuted,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (durationMin != null) SpotterChip("$durationMin min")
                if (w.workout.rpe != null) SpotterChip("RPE ${w.workout.rpe}", leading = Icons.Filled.Warning)
                SpotterChip("${byExercise.size} ejercicios")
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = c.border, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            byExercise.entries.take(4).forEach { (name, sets) ->
                Text(
                    text = "· $name · " + sets.joinToString(", ") {
                        "${formatWeight(it.set.weightKg)}×${it.set.reps}"
                    },
                    style = SpotterText.small.copy(fontFamily = FontFamily.Monospace),
                    color = c.textMuted,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            if (byExercise.size > 4) {
                Text(
                    "+${byExercise.size - 4} más",
                    style = SpotterText.small,
                    color = c.textFaint,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            if (!w.workout.notes.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
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
    }
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
