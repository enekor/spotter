package com.n3k0chan.spotter.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.n3k0chan.spotter.data.db.entities.profile
import com.n3k0chan.spotter.data.measurement.formatShort
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
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class HistoryViewModel : ViewModel() {
    val list: StateFlow<List<WorkoutWithSets>> = ServiceLocator.workouts.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    fun delete(id: Long) {
        viewModelScope.launch { ServiceLocator.workouts.delete(id) }
    }

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
                items(list, key = { it.workout.id }) { w ->
                    SessionCard(
                        w = w,
                        df = df,
                        onDelete = { vm.delete(w.workout.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(w: WorkoutWithSets, df: DateFormat, onDelete: () -> Unit) {
    val c = SpotterTheme.colors
    val byExercise = w.sets.groupBy { it.exercise.name }
    val durationMin = w.workout.finishedAt
        ?.let { (it - w.workout.startedAt) / 60_000 }
        ?.toInt()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    SpotterCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(w.workout.title, style = SpotterText.title3, color = c.text)
                    Text(
                        df.format(Date(w.workout.startedAt)),
                        style = SpotterText.small,
                        color = c.textMuted,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Opciones", tint = c.textFaint)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Borrar entreno", color = c.danger) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                }
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
                val profile = sets.firstOrNull()?.exercise?.profile
                Text(
                    text = "· $name · " + if (profile != null) {
                        sets.joinToString(", ") { it.set.formatShort(profile) }
                    } else "—",
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar entreno", style = SpotterText.title2) },
            text = {
                Text(
                    "Se eliminará el entreno \"${w.workout.title}\" del ${df.format(Date(w.workout.startedAt))} " +
                        "junto con todas sus series. Esta acción no se puede deshacer.",
                    style = SpotterText.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Borrar", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = c.textMuted) }
            },
        )
    }
}

