package com.n3k0chan.spotter.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.n3k0chan.spotter.data.db.entities.profile
import com.n3k0chan.spotter.data.measurement.MeasurementField
import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import com.n3k0chan.spotter.data.measurement.formatForProfile
import com.n3k0chan.spotter.data.repository.SetInput
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
                        onAddSet = { input -> vm.addSet(exerciseId, input) },
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
        com.n3k0chan.spotter.ui.components.ExercisePickerSheet(
            catalog = catalog,
            alreadyAdded = state.orderedExerciseIds.toSet(),
            onPick = {
                vm.addExerciseToSession(it.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    if (showFinishDialog) {
        val isDriveLinked = remember {
            ServiceLocator.settings.state.value.isDriveLinked
        }
        FinishWorkoutDialog(
            initialNotes = state.notes,
            initialRpe = state.rpe,
            showBackupSwitch = isDriveLinked,
            onConfirm = { rpe, notes, backup ->
                vm.setRpe(rpe)
                vm.setNotes(notes)
                vm.finish(backupAfterFinish = backup, onDone = onFinished)
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
    onAddSet: (com.n3k0chan.spotter.data.repository.SetInput) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onRequestSuggestion: () -> Unit,
    onClearSuggestion: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = SpotterTheme.colors
    val ctx = LocalContext.current
    val profile = exercise.profile
    val fields = profile.fields

    // Estado del formulario inline (cerrado por defecto), un campo por cada métrica posible
    var formOpen by remember { mutableStateOf(false) }
    var weightStr by remember { mutableStateOf("") }
    var repsStr by remember { mutableStateOf("") }
    var durHStr by remember { mutableStateOf("") }
    var durMStr by remember { mutableStateOf("") }
    var durSStr by remember { mutableStateOf("") }
    var distanceStr by remember { mutableStateOf("") }
    var resistanceStr by remember { mutableStateOf("") }
    var inclineStr by remember { mutableStateOf("") }
    var restStr by remember { mutableStateOf(defaultRest.toString()) }

    // Estado del menú kebab
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    // Cargamos el mejor set histórico una vez por ejercicio y lo usamos para
    // prellenar el form la primera vez que se abre.
    var bestPrefilled by remember(exercise.id) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(exercise.id, formOpen) {
        if (formOpen && !bestPrefilled && sets.isEmpty()) {
            val best = ServiceLocator.workouts.bestSetFor(exercise.id, profile)
            if (best != null) {
                best.weightKg?.let { weightStr = if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
                best.reps?.let { repsStr = it.toString() }
                best.durationSeconds?.let { total ->
                    val h = total / 3600
                    val m = (total % 3600) / 60
                    val s = total % 60
                    if (h > 0) durHStr = h.toString()
                    if (m > 0 || h > 0) durMStr = m.toString()
                    if (s > 0) durSStr = s.toString()
                }
                best.distanceMeters?.let { distanceStr = if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
                best.resistanceLevel?.let { resistanceStr = it.toString() }
                best.inclinePercent?.let { inclineStr = if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
                best.restSeconds?.let { restStr = it.toString() }
            }
            bestPrefilled = true
        }
    }

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

            // ── Sets ya completados (formato según perfil)
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
                        Text(
                            text = s.formatForProfile(profile),
                            style = SpotterText.numS,
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
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

            // ── Footer: o botón "+" o formulario dinámico (campos según perfil)
            if (formOpen) {
                MeasurementFormFields(
                    fields = fields,
                    weightStr = weightStr, onWeightChange = { weightStr = sanitizeDecimal(it) },
                    repsStr = repsStr, onRepsChange = { repsStr = it.filter(Char::isDigit) },
                    durHStr = durHStr, onDurHChange = { durHStr = it.filter(Char::isDigit).take(2) },
                    durMStr = durMStr, onDurMChange = { durMStr = it.filter(Char::isDigit).take(2) },
                    durSStr = durSStr, onDurSChange = { durSStr = it.filter(Char::isDigit).take(2) },
                    distanceStr = distanceStr, onDistanceChange = { distanceStr = sanitizeDecimal(it) },
                    resistanceStr = resistanceStr, onResistanceChange = { resistanceStr = it.filter(Char::isDigit) },
                    inclineStr = inclineStr, onInclineChange = { inclineStr = sanitizeDecimal(it) },
                    restStr = restStr, onRestChange = { restStr = it.filter(Char::isDigit) },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotterButton(
                        text = "Hecho · serie ${sets.size + 1}",
                        leading = Icons.Filled.Check,
                        variant = if (active) SpotterButtonVariant.Filled else SpotterButtonVariant.Tonal,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val input = buildSetInput(
                                fields = fields,
                                weightStr = weightStr, repsStr = repsStr,
                                durHStr = durHStr, durMStr = durMStr, durSStr = durSStr,
                                distanceStr = distanceStr,
                                resistanceStr = resistanceStr, inclineStr = inclineStr,
                                restStr = restStr,
                            )
                            if (input != null) {
                                onAddSet(input)
                                if (input.restSeconds != null && input.restSeconds > 0) {
                                    RestTimerService.start(ctx, input.restSeconds, preWarning, vibrate)
                                }
                                // Conservamos peso/duración/etc., vaciamos solo lo que cambia entre series
                                repsStr = ""
                                if (profile == MeasurementProfile.Duration) {
                                    durHStr = ""; durMStr = ""; durSStr = ""
                                }
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
private fun FinishWorkoutDialog(
    initialNotes: String,
    initialRpe: Int?,
    showBackupSwitch: Boolean,
    onConfirm: (Int?, String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val c = SpotterTheme.colors
    var notes by remember { mutableStateOf(initialNotes) }
    var rpe by remember { mutableStateOf(initialRpe) }
    var backupToDrive by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Terminar entreno", style = SpotterText.title2) },
        confirmButton = {
            TextButton(onClick = { onConfirm(rpe, notes, backupToDrive && showBackupSwitch) }) {
                Text("Guardar", color = c.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar", color = c.textMuted) }
        },
        text = {
            Column {
                Text("RPE (1-10)", style = SpotterText.smallMd, color = c.textMuted)
                Spacer(Modifier.height(6.dp))
                RpeDropdown(selected = rpe, onSelect = { rpe = it })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showBackupSwitch) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = c.border, thickness = 1.dp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Subir copia a Drive", style = SpotterText.bodyMd, color = c.text)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Guardar copia de seguridad",
                                style = SpotterText.small,
                                color = c.textMuted,
                            )
                        }
                        Switch(
                            checked = backupToDrive,
                            onCheckedChange = { backupToDrive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = c.primary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = c.borderStrong,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun RpeDropdown(selected: Int?, onSelect: (Int?) -> Unit) {
    val c = SpotterTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val rpeLabel: (Int) -> String = { v ->
        val tag = when (v) {
            in 1..3 -> "muy fácil"
            in 4..5 -> "fácil"
            6 -> "moderado"
            7 -> "exigente"
            8 -> "duro"
            9 -> "muy duro"
            10 -> "máximo"
            else -> ""
        }
        "RPE $v · $tag"
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceMuted)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected?.let(rpeLabel) ?: "Sin valorar",
                style = SpotterText.bodyMd,
                color = if (selected == null) c.textMuted else c.text,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = c.textFaint,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Sin valorar", color = c.textMuted) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            (1..10).forEach { v ->
                DropdownMenuItem(
                    text = { Text(rpeLabel(v)) },
                    onClick = {
                        onSelect(v)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

/* ─── Form dinámico según perfil ─── */

@Composable
private fun MeasurementFormFields(
    fields: List<MeasurementField>,
    weightStr: String, onWeightChange: (String) -> Unit,
    repsStr: String, onRepsChange: (String) -> Unit,
    durHStr: String, onDurHChange: (String) -> Unit,
    durMStr: String, onDurMChange: (String) -> Unit,
    durSStr: String, onDurSChange: (String) -> Unit,
    distanceStr: String, onDistanceChange: (String) -> Unit,
    resistanceStr: String, onResistanceChange: (String) -> Unit,
    inclineStr: String, onInclineChange: (String) -> Unit,
    restStr: String, onRestChange: (String) -> Unit,
) {
    val hasDuration = MeasurementField.Duration in fields

    val items = buildList<@Composable RowScope.() -> Unit> {
        if (MeasurementField.Weight in fields) add {
            NumericField(
                label = "Peso", suffix = "kg",
                value = weightStr, onValueChange = onWeightChange,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        if (MeasurementField.Reps in fields) add {
            NumericField(
                label = "Reps", suffix = null,
                value = repsStr, onValueChange = onRepsChange,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        if (MeasurementField.Distance in fields) add {
            NumericField(
                label = "Distancia", suffix = "m",
                value = distanceStr, onValueChange = onDistanceChange,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        if (MeasurementField.Resistance in fields) add {
            NumericField(
                label = "Nivel", suffix = null,
                value = resistanceStr, onValueChange = onResistanceChange,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        if (MeasurementField.Incline in fields) add {
            NumericField(
                label = "Inclin.", suffix = "%",
                value = inclineStr, onValueChange = onInclineChange,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        add {
            NumericField(
                label = "Desc.", suffix = "s",
                value = restStr, onValueChange = onRestChange,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (hasDuration) {
        DurationField(
            hStr = durHStr, onHChange = onDurHChange,
            mStr = durMStr, onMChange = onDurMChange,
            sStr = durSStr, onSChange = onDurSChange,
        )
        Spacer(Modifier.height(8.dp))
    }

    val perRow = if (items.size <= 3) items.size else (items.size + 1) / 2
    items.chunked(perRow).forEachIndexed { rowIdx, row ->
        if (rowIdx > 0) Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { it() }
        }
    }
}

@Composable
private fun DurationField(
    hStr: String, onHChange: (String) -> Unit,
    mStr: String, onMChange: (String) -> Unit,
    sStr: String, onSChange: (String) -> Unit,
) {
    val c = SpotterTheme.colors
    Column {
        Text("Tiempo", style = SpotterText.small.copy(fontSize = 11.sp), color = c.textMuted)
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DurationSegment(value = hStr, onValueChange = onHChange, hint = "0", label = "h", modifier = Modifier.weight(1f))
            Text(":", style = SpotterText.numM, color = c.textFaint)
            DurationSegment(value = mStr, onValueChange = onMChange, hint = "00", label = "m", modifier = Modifier.weight(1f))
            Text(":", style = SpotterText.numM, color = c.textFaint)
            DurationSegment(value = sStr, onValueChange = onSChange, hint = "00", label = "s", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DurationSegment(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = SpotterTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, color = c.textFaint, style = SpotterText.numS) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        suffix = { Text(label, style = SpotterText.small, color = c.textMuted) },
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
        modifier = modifier,
    )
}

private fun buildSetInput(
    fields: List<MeasurementField>,
    weightStr: String,
    repsStr: String,
    durHStr: String,
    durMStr: String,
    durSStr: String,
    distanceStr: String,
    resistanceStr: String,
    inclineStr: String,
    restStr: String,
): SetInput? {
    val weight = if (MeasurementField.Weight in fields) weightStr.replace(',', '.').toDoubleOrNull() else null
    val reps = if (MeasurementField.Reps in fields) repsStr.toIntOrNull() else null
    val duration = if (MeasurementField.Duration in fields) {
        val total = (durHStr.toIntOrNull() ?: 0) * 3600 +
            (durMStr.toIntOrNull() ?: 0) * 60 +
            (durSStr.toIntOrNull() ?: 0)
        if (total > 0) total else null
    } else null
    val distance = if (MeasurementField.Distance in fields) distanceStr.replace(',', '.').toDoubleOrNull() else null
    val resistance = if (MeasurementField.Resistance in fields) resistanceStr.toIntOrNull() else null
    val incline = if (MeasurementField.Incline in fields) inclineStr.replace(',', '.').toDoubleOrNull() else null

    // Validación: al menos un campo del perfil debe estar relleno.
    val anyFilled = listOf(weight, reps, duration, distance, resistance, incline).any { it != null }
    if (!anyFilled) return null

    return SetInput(
        weightKg = weight,
        reps = reps,
        durationSeconds = duration,
        distanceMeters = distance,
        resistanceLevel = resistance,
        inclinePercent = incline,
        restSeconds = restStr.toIntOrNull(),
    )
}

private fun sanitizeDecimal(input: String): String =
    input.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
