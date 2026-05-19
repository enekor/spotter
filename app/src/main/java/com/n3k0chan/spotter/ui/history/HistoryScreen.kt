package com.n3k0chan.spotter.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.n3k0chan.spotter.data.measurement.formatShort
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.IconButtonTone
import com.n3k0chan.spotter.ui.components.MuscleGroupAvatar
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterChip
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class HistoryViewModel : ViewModel() {
    val list: StateFlow<List<WorkoutWithSets>> = ServiceLocator.workouts.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    private val _toast = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun consumedToast() { _toast.value = null }

    fun delete(id: Long) {
        viewModelScope.launch { ServiceLocator.workouts.delete(id) }
    }

    /**
     * Crea una plantilla a partir de una sesión existente. Toma los ejercicios
     * usados (en su orden de aparición) y genera una plantilla con valores
     * razonables: sets = cuántos hizo en la sesión, reps/rest tomados del
     * primer set del ejercicio.
     */
    fun saveAsTemplate(session: WorkoutWithSets, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val byExercise = session.sets
                .sortedBy { it.set.orderIndex }
                .groupBy { it.set.exerciseId }
            val items = byExercise.entries.mapIndexed { idx, (exId, sets) ->
                val first = sets.first().set
                com.n3k0chan.spotter.data.db.entities.TemplateExercise(
                    templateId = 0,
                    exerciseId = exId,
                    orderIndex = idx,
                    targetSets = sets.size.coerceAtLeast(1),
                    targetReps = first.reps ?: 8,
                    defaultRestSeconds = first.restSeconds ?: 90,
                )
            }
            ServiceLocator.templates.create(name.trim(), items)
            _toast.value = "Plantilla \"${name.trim()}\" creada"
        }
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
    val toast by vm.toast.collectAsStateWithLifecycle()
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
                        onSaveAsTemplate = { name -> vm.saveAsTemplate(w, name) },
                    )
                }
            }
        }
        toast?.let {
            AlertDialog(
                onDismissRequest = { vm.consumedToast() },
                confirmButton = {
                    TextButton(onClick = { vm.consumedToast() }) { Text("OK", color = c.primary) }
                },
                text = { Text(it, style = SpotterText.body) },
            )
        }
    }
}

private const val COLLAPSED_EXERCISE_COUNT = 3

@Composable
private fun SessionCard(
    w: WorkoutWithSets,
    df: DateFormat,
    onDelete: () -> Unit,
    onSaveAsTemplate: (String) -> Unit,
) {
    val c = SpotterTheme.colors
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
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saveTemplate by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val needsExpansion = exercises.size > COLLAPSED_EXERCISE_COUNT

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
                            text = { Text("Convertir en plantilla") },
                            onClick = {
                                menuOpen = false
                                saveTemplate = true
                            },
                        )
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
                SpotterChip("${exercises.size} ejercicios")
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = c.border, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))

            val visibleExercises = if (needsExpansion && !expanded) {
                exercises.take(COLLAPSED_EXERCISE_COUNT)
            } else {
                exercises
            }
            visibleExercises.forEachIndexed { idx, (exercise, sets) ->
                ExerciseRow(exercise = exercise, sets = sets)
                if (idx < visibleExercises.lastIndex) Spacer(Modifier.height(6.dp))
            }

            if (needsExpansion) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        exercises.drop(COLLAPSED_EXERCISE_COUNT).forEachIndexed { idx, (exercise, sets) ->
                            if (idx == 0) Spacer(Modifier.height(6.dp))
                            ExerciseRow(exercise = exercise, sets = sets)
                            if (idx < exercises.size - COLLAPSED_EXERCISE_COUNT - 1) {
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (expanded) "Ver menos"
                        else "Ver ${exercises.size - COLLAPSED_EXERCISE_COUNT} ejercicios más",
                        style = SpotterText.smallMd,
                        color = c.primary,
                    )
                }
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

    if (saveTemplate) {
        var name by remember { mutableStateOf(w.workout.title) }
        AlertDialog(
            onDismissRequest = { saveTemplate = false },
            title = { Text("Crear plantilla", style = SpotterText.title2) },
            text = {
                Column {
                    Text(
                        "Toma los ${w.sets.groupBy { it.exercise.name }.size} ejercicios de esta sesión y crea una plantilla reutilizable.",
                        style = SpotterText.body,
                        color = c.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        saveTemplate = false
                        onSaveAsTemplate(name)
                    },
                ) { Text("Crear", color = c.primary) }
            },
            dismissButton = {
                TextButton(onClick = { saveTemplate = false }) { Text("Cancelar", color = c.textMuted) }
            },
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseRow(
    exercise: Exercise,
    sets: List<WorkoutSetWithExercise>,
) {
    val c = SpotterTheme.colors
    val profile = exercise.profile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceMuted)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MuscleGroupAvatar(
            rawGroup = exercise.muscleGroup,
            size = 32.dp,
            iconSize = 16.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.name,
                    style = SpotterText.smallMd,
                    color = c.text,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${sets.size}s",
                    style = SpotterText.small,
                    color = c.textFaint,
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sets.forEach { ws ->
                    SetBadge(text = ws.set.formatShort(profile))
                }
            }
        }
    }
}

@Composable
private fun SetBadge(text: String) {
    val c = SpotterTheme.colors
    Text(
        text = text,
        style = SpotterText.numS,
        color = c.primarySoftText,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.primarySoft)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

