package com.n3k0chan.spotter.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.timer.RestTimerService
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    workoutId: Long,
    onFinished: () -> Unit,
    onOpenChat: () -> Unit,
    vm: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(workoutId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val catalog by vm.exerciseCatalog.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.workout?.workout?.title ?: "Entreno") },
                actions = {
                    IconButton(onClick = onOpenChat) {
                        Icon(Icons.Filled.Chat, contentDescription = "Asistente")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  " + stringResourceCompat(R.string.workout_add_exercise))
                }
                Button(
                    onClick = { showFinishDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("  " + stringResourceCompat(R.string.workout_finish))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            RestTimerBar(modifier = Modifier.padding(vertical = 8.dp))

            if (state.orderedExerciseIds.isEmpty()) {
                Text(
                    "Añade el primer ejercicio para empezar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                ) {
                    items(state.orderedExerciseIds, key = { it }) { exerciseId ->
                        val exercise = catalog.firstOrNull { it.id == exerciseId }
                        if (exercise != null) {
                            ExerciseSection(
                                exercise = exercise,
                                workoutSets = state.workout?.sets
                                    ?.filter { it.set.exerciseId == exerciseId }
                                    ?.map { it.set }
                                    .orEmpty(),
                                defaultRestSeconds = ServiceLocator.settings.state.value.defaultRestSeconds,
                                preWarning = ServiceLocator.settings.state.value.preWarning,
                                vibrate = ServiceLocator.settings.state.value.vibrate,
                                onAddSet = { weight, reps, rest ->
                                    vm.addSet(exerciseId, weight, reps, rest)
                                },
                                onDeleteSet = { vm.deleteSet(it) },
                                onRequestSuggestion = { vm.fetchSuggestion(exerciseId, exercise.name) },
                                suggestion = state.suggestion?.takeIf {
                                    state.suggestionForExerciseId == exerciseId && !state.suggestionLoading
                                },
                                suggestionLoading = state.suggestionLoading &&
                                    state.suggestionForExerciseId == exerciseId,
                                onClearSuggestion = { vm.clearSuggestion() },
                                onRemove = { vm.removeExerciseFromSession(exerciseId) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            catalog = catalog,
            already = state.orderedExerciseIds.toSet(),
            onPick = {
                vm.addExerciseToSession(it.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showFinishDialog) {
        FinishWorkoutDialog(
            initialNotes = state.notes,
            initialRpe = state.rpe,
            onConfirm = { rpe, notes ->
                vm.setRpe(rpe)
                vm.setNotes(notes)
                vm.finish(onFinished)
                showFinishDialog = false
            },
            onCancel = { showFinishDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseSection(
    exercise: Exercise,
    workoutSets: List<com.n3k0chan.spotter.data.db.entities.WorkoutSet>,
    defaultRestSeconds: Int,
    preWarning: Boolean,
    vibrate: Boolean,
    onAddSet: (weight: Double, reps: Int, rest: Int?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onRequestSuggestion: () -> Unit,
    suggestion: String?,
    suggestionLoading: Boolean,
    onClearSuggestion: () -> Unit,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    var weightStr by remember { mutableStateOf("") }
    var repsStr by remember { mutableStateOf("") }
    var restStr by remember { mutableStateOf(defaultRestSeconds.toString()) }
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Quitar del entreno") },
                        onClick = { menuOpen = false; onRemove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Sugerencia IA") },
                        onClick = { menuOpen = false; onRequestSuggestion() },
                    )
                }
            }
            if (exercise.muscleGroup != null) {
                Text(
                    exercise.muscleGroup,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (suggestionLoading) {
                Text("Pensando…", style = MaterialTheme.typography.labelLarge)
            } else if (!suggestion.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Sugerencia: $suggestion",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = onClearSuggestion) { Text("OK") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            workoutSets.forEachIndexed { idx, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Serie ${idx + 1}",
                        modifier = Modifier.width(76.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${formatWeight(s.weightKg)} kg × ${s.reps}",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteSet(s.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                }
            }
            if (workoutSets.isNotEmpty()) Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = weightStr,
                    onValueChange = { weightStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = repsStr,
                    onValueChange = { repsStr = it.filter(Char::isDigit) },
                    label = { Text("reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = restStr,
                    onValueChange = { restStr = it.filter(Char::isDigit) },
                    label = { Text("rest s") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val w = weightStr.replace(',', '.').toDoubleOrNull()
                        val r = repsStr.toIntOrNull()
                        val rest = restStr.toIntOrNull()
                        if (w != null && r != null) {
                            onAddSet(w, r, rest)
                            if (rest != null && rest > 0) {
                                RestTimerService.start(ctx, rest, preWarning, vibrate)
                            }
                            // Limpia reps pero deja peso/rest para la siguiente serie
                            repsStr = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResourceCompat(R.string.workout_done)) }

                if (restStr.toIntOrNull() != null && (restStr.toIntOrNull() ?: 0) > 0) {
                    OutlinedTextButton(onClick = {
                        val rest = restStr.toIntOrNull() ?: defaultRestSeconds
                        RestTimerService.start(ctx, rest, preWarning, vibrate)
                    }) { Text(stringResourceCompat(R.string.workout_start_rest)) }
                }
            }
        }
    }
}

@Composable
private fun OutlinedTextButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick) { content() }
}

@Composable
private fun ExercisePickerDialog(
    catalog: List<Exercise>,
    already: Set<Long>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newMuscle by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val name = newName.trim()
                if (name.isNotBlank()) {
                    scope.launch {
                        val id = ServiceLocator.exercises.create(name, newMuscle.takeIf { it.isNotBlank() })
                        ServiceLocator.exercises.get(id)?.let(onPick)
                    }
                }
            }) { Text("Crear y añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResourceCompat(R.string.common_cancel)) } },
        title = { Text(stringResourceCompat(R.string.workout_add_exercise)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val filtered = catalog.filter {
                    query.isBlank() || it.name.contains(query, ignoreCase = true)
                }
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(filtered, key = { it.id }) { ex ->
                        FilterChip(
                            selected = ex.id in already,
                            onClick = { onPick(ex) },
                            label = { Text(ex.name + (ex.muscleGroup?.let { " · $it" } ?: "")) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Crear nuevo ejercicio", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResourceCompat(R.string.exercise_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newMuscle,
                    onValueChange = { newMuscle = it },
                    label = { Text(stringResourceCompat(R.string.exercise_muscle)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun FinishWorkoutDialog(
    initialNotes: String,
    initialRpe: Int?,
    onConfirm: (rpe: Int?, notes: String) -> Unit,
    onCancel: () -> Unit,
) {
    var notes by remember { mutableStateOf(initialNotes) }
    var rpe by remember { mutableStateOf(initialRpe) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResourceCompat(R.string.workout_finish)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(rpe, notes) }) { Text(stringResourceCompat(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResourceCompat(R.string.common_cancel)) }
        },
        text = {
            Column {
                Text("RPE (1-10)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..10).forEach { value ->
                        FilterChip(
                            selected = rpe == value,
                            onClick = { rpe = if (rpe == value) null else value },
                            label = { Text(value.toString()) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResourceCompat(R.string.workout_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
    )
}

@Composable
private fun stringResourceCompat(@androidx.annotation.StringRes id: Int): String =
    androidx.compose.ui.res.stringResource(id)

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
