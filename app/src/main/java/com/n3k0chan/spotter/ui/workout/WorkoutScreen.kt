package com.n3k0chan.spotter.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.timer.RestTimerController
import com.n3k0chan.spotter.timer.RestTimerService
import com.n3k0chan.spotter.ui.components.IconButtonTone
import com.n3k0chan.spotter.ui.components.MuscleGroup
import com.n3k0chan.spotter.ui.components.MuscleGroupAvatar
import com.n3k0chan.spotter.ui.components.SpotterButton
import com.n3k0chan.spotter.ui.components.SpotterButtonVariant
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.launch

@Composable
fun WorkoutScreen(
    workoutId: Long,
    onFinished: () -> Unit,
    onOpenChat: () -> Unit,
    vm: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(workoutId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val catalog by vm.exerciseCatalog.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors
    var showPicker by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            Column {
                SpotterTopBar(
                    title = state.workout?.workout?.title ?: "Entreno",
                    subtitle = "sesión activa",
                    leading = {
                        SpotterIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onFinished,
                        )
                    },
                    trailing = {
                        SpotterIconButton(
                            icon = Icons.Filled.AutoAwesome,
                            onClick = onOpenChat,
                            tone = IconButtonTone.Primary,
                            contentDescription = "Asistente",
                        )
                        SpotterIconButton(
                            icon = Icons.Filled.MoreVert,
                            tone = IconButtonTone.Muted,
                        )
                    },
                )
                RestTimerBar()
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface),
            ) {
                HorizontalDivider(color = c.border, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SpotterButton(
                        text = "Añadir",
                        leading = Icons.Filled.Add,
                        variant = SpotterButtonVariant.Outlined,
                        modifier = Modifier.weight(1f),
                        onClick = { showPicker = true },
                    )
                    SpotterButton(
                        text = "Terminar",
                        leading = Icons.Filled.Check,
                        modifier = Modifier.weight(1.4f),
                        onClick = { showFinishDialog = true },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        ) {
            if (state.orderedExerciseIds.isEmpty()) {
                item {
                    Text(
                        "Añade el primer ejercicio para empezar.",
                        style = SpotterText.body,
                        color = c.textMuted,
                    )
                }
            } else {
                items(state.orderedExerciseIds, key = { it }) { exerciseId ->
                    val exercise = catalog.firstOrNull { it.id == exerciseId } ?: return@items
                    val isLast = state.orderedExerciseIds.lastOrNull() == exerciseId
                    val sets = state.workout?.sets
                        ?.filter { it.set.exerciseId == exerciseId }
                        ?.map { it.set }
                        .orEmpty()
                    ExerciseCard(
                        exercise = exercise,
                        sets = sets,
                        active = isLast,
                        defaultRest = ServiceLocator.settings.state.value.defaultRestSeconds,
                        preWarning = ServiceLocator.settings.state.value.preWarning,
                        vibrate = ServiceLocator.settings.state.value.vibrate,
                        suggestion = state.suggestion?.takeIf {
                            state.suggestionForExerciseId == exerciseId && !state.suggestionLoading
                        },
                        suggestionLoading = state.suggestionLoading && state.suggestionForExerciseId == exerciseId,
                        onAddSet = { weight, reps, rest -> vm.addSet(exerciseId, weight, reps, rest) },
                        onDeleteSet = { vm.deleteSet(it) },
                        onRequestSuggestion = { vm.fetchSuggestion(exerciseId, exercise.name) },
                        onClearSuggestion = { vm.clearSuggestion() },
                        onRemove = { vm.removeExerciseFromSession(exerciseId) },
                    )
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

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    sets: List<com.n3k0chan.spotter.data.db.entities.WorkoutSet>,
    active: Boolean,
    defaultRest: Int,
    preWarning: Boolean,
    vibrate: Boolean,
    suggestion: String?,
    suggestionLoading: Boolean,
    onAddSet: (Double, Int, Int?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onRequestSuggestion: () -> Unit,
    onClearSuggestion: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = SpotterTheme.colors
    val ctx = LocalContext.current

    // Estado del formulario inline (cerrado por defecto)
    var formOpen by remember { mutableStateOf(false) }
    var weightStr by remember { mutableStateOf("") }
    var repsStr by remember { mutableStateOf("") }
    var restStr by remember { mutableStateOf(defaultRest.toString()) }

    // Estado del menú kebab
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val timerState by RestTimerController.state.collectAsStateWithLifecycle()
    val showRing = active && timerState.isRunning

    SpotterCard(
        radius = 16.dp,
        padding = 16.dp,
        border = if (active) c.primary else c.border,
    ) {
        Column {
            // ── Header: avatar + nombre + ring (si activo) + kebab
            Row(verticalAlignment = Alignment.Top) {
                MuscleGroupAvatar(
                    group = MuscleGroup.from(exercise.muscleGroup),
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, style = SpotterText.title3, color = c.text)
                    if (exercise.muscleGroup != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(exercise.muscleGroup, style = SpotterText.small, color = c.textMuted)
                    }
                }
                if (showRing) {
                    Spacer(Modifier.width(8.dp))
                    RestRing(
                        progress = timerState.progress.coerceIn(0f, 1f),
                        remainingSeconds = timerState.remainingSeconds,
                    )
                    Spacer(Modifier.width(8.dp))
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
                            text = { Text("Sugerencia IA") },
                            onClick = {
                                menuOpen = false
                                onRequestSuggestion()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Quitar del entreno", color = c.danger) },
                            onClick = {
                                menuOpen = false
                                confirmRemove = true
                            },
                        )
                    }
                }
            }

            // ── Sugerencia IA
            if (suggestionLoading) {
                Spacer(Modifier.height(8.dp))
                SuggestionCard(text = "Pensando…", onDismiss = null)
            } else if (!suggestion.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                SuggestionCard(text = suggestion, onDismiss = onClearSuggestion)
            }

            // ── Sets ya completados
            if (sets.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                sets.forEachIndexed { i, s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "SERIE ${i + 1}",
                            style = SpotterText.caps,
                            color = c.textMuted,
                            modifier = Modifier.width(64.dp),
                        )
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                            Text(formatWeight(s.weightKg), style = SpotterText.numS, color = c.text)
                            Spacer(Modifier.width(2.dp))
                            Text("kg", style = SpotterText.small, color = c.textMuted)
                            Text(" × ", style = SpotterText.small, color = c.textFaint)
                            Text("${s.reps}", style = SpotterText.numS, color = c.text)
                            Spacer(Modifier.width(2.dp))
                            Text("reps", style = SpotterText.small, color = c.textMuted)
                        }
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = c.success,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { onDeleteSet(s.id) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Borrar",
                                tint = c.textFaint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (i < sets.size - 1) HorizontalDivider(color = c.border, thickness = 1.dp)
                }
                HorizontalDivider(color = c.border, thickness = 1.dp)
            }

            Spacer(Modifier.height(12.dp))

            // ── Footer: o botón "+" o formulario expandido
            if (formOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericField(
                        label = "Peso",
                        suffix = "kg",
                        value = weightStr,
                        onValueChange = { weightStr = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    NumericField(
                        label = "Reps",
                        suffix = null,
                        value = repsStr,
                        onValueChange = { repsStr = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    NumericField(
                        label = "Desc.",
                        suffix = "s",
                        value = restStr,
                        onValueChange = { restStr = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotterButton(
                        text = "Hecho · serie ${sets.size + 1}",
                        leading = Icons.Filled.Check,
                        variant = if (active) SpotterButtonVariant.Filled else SpotterButtonVariant.Tonal,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val w = weightStr.replace(',', '.').toDoubleOrNull()
                            val r = repsStr.toIntOrNull()
                            val rest = restStr.toIntOrNull()
                            if (w != null && r != null) {
                                onAddSet(w, r, rest)
                                if (rest != null && rest > 0) {
                                    RestTimerService.start(ctx, rest, preWarning, vibrate)
                                }
                                // Mantén peso/desc, vacía reps para meter otra serie con un toque
                                repsStr = ""
                            }
                        },
                    )
                    SpotterButton(
                        text = "Cerrar",
                        variant = SpotterButtonVariant.Outlined,
                        onClick = { formOpen = false },
                    )
                }
            } else {
                SpotterButton(
                    text = if (sets.isEmpty()) "Añadir primera serie" else "Añadir serie",
                    leading = Icons.Filled.Add,
                    variant = SpotterButtonVariant.Tonal,
                    full = true,
                    onClick = { formOpen = true },
                )
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Quitar del entreno", style = SpotterText.title2) },
            text = {
                Text(
                    "¿Quitar \"${exercise.name}\" de esta sesión? Se borrarán también las series " +
                        "que hayas registrado para este ejercicio en este entreno.",
                    style = SpotterText.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text("Quitar", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancelar", color = c.textMuted) }
            },
        )
    }
}

@Composable
private fun SuggestionCard(text: String, onDismiss: (() -> Unit)?) {
    val c = SpotterTheme.colors
    SpotterCard(
        radius = 12.dp,
        padding = 12.dp,
        background = c.primarySoft,
        border = c.primarySoft,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("SUGERENCIA", style = SpotterText.caps, color = c.primarySoftText)
                Spacer(Modifier.height(4.dp))
                Text(
                    text,
                    style = SpotterText.small,
                    color = c.primarySoftText.copy(alpha = 0.85f),
                )
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("OK", color = c.primarySoftText, style = SpotterText.smallMd)
                }
            }
        }
    }
}

@Composable
private fun RestRing(progress: Float, remainingSeconds: Int) {
    val c = SpotterTheme.colors
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(56.dp)) {
            val stroke = 5.dp.toPx()
            val r = (size.minDimension - stroke) / 2
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = c.surfaceVariant,
                radius = r,
                center = center,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = c.primary,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            formatMmSs(remainingSeconds),
            style = SpotterText.numS.copy(fontSize = 13.sp),
            color = c.text,
        )
    }
}

@Composable
private fun NumericField(
    label: String,
    suffix: String?,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val c = SpotterTheme.colors
    Column(modifier = modifier) {
        Text(label, style = SpotterText.small.copy(fontSize = 11.sp), color = c.textMuted)
        Spacer(Modifier.height(2.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("—", color = c.textFaint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            suffix = suffix?.let { { Text(it, style = SpotterText.small, color = c.textMuted) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.surfaceMuted,
                unfocusedContainerColor = c.surfaceMuted,
                focusedBorderColor = c.borderStrong,
                unfocusedBorderColor = c.border,
                cursorColor = c.primary,
                focusedTextColor = c.text,
                unfocusedTextColor = c.text,
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = SpotterText.numS,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExercisePickerDialog(
    catalog: List<Exercise>,
    already: Set<Long>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SpotterTheme.colors
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
            }) { Text("Crear y añadir", color = c.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = c.textMuted) } },
        title = { Text("Añadir ejercicio", style = SpotterText.title2, color = c.text) },
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
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(filtered, key = { it.id }) { ex ->
                        PickerRow(
                            exercise = ex,
                            selected = ex.id in already,
                            onClick = { onPick(ex) },
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = c.border)
                Text("Crear nuevo", style = SpotterText.smallMd, color = c.textMuted)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newMuscle,
                    onValueChange = { newMuscle = it },
                    label = { Text("Grupo muscular") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun PickerRow(
    exercise: Exercise,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = SpotterTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MuscleGroupAvatar(group = MuscleGroup.from(exercise.muscleGroup), size = 32.dp, iconSize = 16.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(exercise.name, style = SpotterText.bodyMd, color = c.text)
            if (exercise.muscleGroup != null) {
                Text(exercise.muscleGroup, style = SpotterText.small, color = c.textMuted)
            }
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FinishWorkoutDialog(
    initialNotes: String,
    initialRpe: Int?,
    onConfirm: (Int?, String) -> Unit,
    onCancel: () -> Unit,
) {
    val c = SpotterTheme.colors
    var notes by remember { mutableStateOf(initialNotes) }
    var rpe by remember { mutableStateOf(initialRpe) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Terminar entreno", style = SpotterText.title2) },
        confirmButton = {
            TextButton(onClick = { onConfirm(rpe, notes) }) { Text("Guardar", color = c.primary) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar", color = c.textMuted) }
        },
        text = {
            Column {
                Text("RPE (1-10)", style = SpotterText.smallMd, color = c.textMuted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..10).forEach { v ->
                        FilterChip(
                            selected = rpe == v,
                            onClick = { rpe = if (rpe == v) null else v },
                            label = { Text(v.toString()) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
