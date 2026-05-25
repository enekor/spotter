package com.n3k0chan.spotter.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.n3k0chan.spotter.data.health.WorkoutHealthMetrics
import com.n3k0chan.spotter.data.measurement.formatShort
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.*
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.util.*

class HistoryViewModel : ViewModel() {
    private val workoutsRepo = ServiceLocator.workouts
    private val healthConnect = ServiceLocator.healthConnect

    val list: StateFlow<List<WorkoutWithSets>> = workoutsRepo.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun consumedToast() { _toast.value = null }

    fun syncRecentWithHC() {
        viewModelScope.launch {
            _syncing.value = true
            val updated = syncWorkoutsWithHC(
                list.value.filter { w ->
                    val fiveDaysAgo = System.currentTimeMillis() - 5L * 24 * 60 * 60 * 1000
                    w.workout.startedAt >= fiveDaysAgo
                }
            )
            _syncing.value = false
            _toast.value = if (updated > 0) "$updated entrenos sincronizados con Health Connect"
            else "No se encontraron datos nuevos"
        }
    }

    private suspend fun syncWorkoutsWithHC(workouts: List<WorkoutWithSets>): Int {
        if (!healthConnect.isAvailable()) return 0
        val hasPerms = runCatching { healthConnect.hasAllPermissions() }.getOrDefault(false)
        if (!hasPerms) return 0

        var count = 0
        for (w in workouts) {
            val finished = w.workout.finishedAt ?: continue
            val metrics = runCatching {
                healthConnect.readMetricsForTimeRange(
                    Instant.ofEpochMilli(w.workout.startedAt),
                    Instant.ofEpochMilli(finished),
                )
            }.getOrNull() ?: continue

            val changed = w.workout.calories != metrics.calories
                    || w.workout.heartRateAvg != metrics.heartRateAvg
                    || w.workout.steps != metrics.steps
                    || w.workout.distanceMeters != metrics.distanceMeters
            if (!changed && w.workout.calories != null) continue

            workoutsRepo.update(
                w.workout.copy(
                    calories = metrics.calories,
                    heartRateAvg = metrics.heartRateAvg,
                    heartRateMin = metrics.heartRateMin,
                    heartRateMax = metrics.heartRateMax,
                    distanceMeters = metrics.distanceMeters,
                    steps = metrics.steps,
                )
            )
            count++
        }
        return count
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.let {
            if (id in it) it - id else it + id
        }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun selectAll() {
        _selectedIds.value = list.value.map { it.workout.id }.toSet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { workoutsRepo.delete(it) }
            _selectedIds.value = emptySet()
            _toast.value = "${ids.size} entrenos eliminados"
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { workoutsRepo.delete(id) }
    }

    fun adjustStartTime(workoutId: Long, newStartedAt: Long) {
        viewModelScope.launch {
            val full = workoutsRepo.get(workoutId) ?: return@launch
            val finishedAt = full.workout.finishedAt ?: System.currentTimeMillis()

            var metrics: WorkoutHealthMetrics? = null
            if (healthConnect.isAvailable()) {
                val hasPerms = runCatching { healthConnect.hasAllPermissions() }.getOrDefault(false)
                if (hasPerms) {
                    metrics = runCatching {
                        healthConnect.readMetricsForTimeRange(
                            Instant.ofEpochMilli(newStartedAt),
                            Instant.ofEpochMilli(finishedAt)
                        )
                    }.getOrNull()
                }
            }

            workoutsRepo.update(
                full.workout.copy(
                    startedAt = newStartedAt,
                    calories = metrics?.calories,
                    heartRateAvg = metrics?.heartRateAvg,
                    heartRateMin = metrics?.heartRateMin,
                    heartRateMax = metrics?.heartRateMax,
                    distanceMeters = metrics?.distanceMeters,
                    steps = metrics?.steps
                )
            )
            _toast.value = "Hora de inicio ajustada y datos de salud actualizados"
        }
    }

    fun calculateEstimatedStart(w: WorkoutWithSets): Long {
        if (w.sets.isEmpty()) return w.workout.startedAt
        val totalActiveSeconds = w.sets.sumOf { (it.set.durationSeconds ?: 0).toLong() }
        val totalRestSeconds = w.sets.sumOf { (it.set.restSeconds ?: 0).toLong() }
        val totalMs = (totalActiveSeconds + totalRestSeconds) * 1000
        val end = w.workout.finishedAt ?: System.currentTimeMillis()
        return end - totalMs
    }

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
fun HistoryScreen(
    onWorkoutClick: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val list by vm.list.collectAsStateWithLifecycle()
    val selectedIds by vm.selectedIds.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val isSelecting = selectedIds.isNotEmpty()
    var confirmBulkDelete by remember { mutableStateOf(false) }

    var workoutToAdjust by remember { mutableStateOf<WorkoutWithSets?>(null) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            if (isSelecting) {
                SpotterTopBar(
                    title = "${selectedIds.size} seleccionados",
                    leading = {
                        SpotterIconButton(
                            icon = Icons.Filled.Close,
                            onClick = { vm.clearSelection() },
                        )
                    },
                    trailing = {
                        Row {
                            SpotterIconButton(
                                icon = Icons.Filled.SelectAll,
                                onClick = { vm.selectAll() },
                            )
                            SpotterIconButton(
                                icon = Icons.Filled.Delete,
                                tone = IconButtonTone.Danger,
                                onClick = { confirmBulkDelete = true },
                            )
                        }
                    },
                )
            } else {
                SpotterTopBar(
                    title = "Historial",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (syncing) {
                                CircularProgressIndicator(
                                    color = c.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                SpotterIconButton(
                                    Icons.Filled.Sync,
                                    onClick = { vm.syncRecentWithHC() },
                                    contentDescription = "Sincronizar HC",
                                )
                            }
                            SpotterIconButton(Icons.AutoMirrored.Filled.Chat, onClick = onOpenChat)
                            SpotterIconButton(Icons.Filled.Settings, onClick = onOpenSettings)
                        }
                    },
                )
            }
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
                    val selected = w.workout.id in selectedIds
                    SessionCard(
                        w = w,
                        df = df,
                        isSelecting = isSelecting,
                        isSelected = selected,
                        onClick = {
                            if (isSelecting) vm.toggleSelection(w.workout.id)
                            else onWorkoutClick(w.workout.id)
                        },
                        onLongClick = { vm.toggleSelection(w.workout.id) },
                        onDelete = { vm.delete(w.workout.id) },
                        onSaveAsTemplate = { name -> vm.saveAsTemplate(w, name) },
                        onAdjustStart = { workoutToAdjust = w }
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

    if (workoutToAdjust != null) {
        val originalStart = workoutToAdjust!!.workout.startedAt
        val estimatedStart = vm.calculateEstimatedStart(workoutToAdjust!!)
        val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
        var selectedStartAt by remember { mutableStateOf(minOf(originalStart, estimatedStart)) }

        AlertDialog(
            onDismissRequest = { workoutToAdjust = null },
            title = { Text("Ajustar hora de inicio", style = SpotterText.title2) },
            text = {
                Column {
                    Text("Selecciona la hora de inicio correcta para recalcular métricas:", style = SpotterText.body, color = c.textMuted)
                    Spacer(Modifier.height(16.dp))
                    StartTimeOption(
                        label = "Registrada",
                        time = timeFmt.format(Date(originalStart)),
                        selected = selectedStartAt == originalStart,
                        onClick = { selectedStartAt = originalStart }
                    )
                    Spacer(Modifier.height(8.dp))
                    StartTimeOption(
                        label = "Estimada",
                        time = timeFmt.format(Date(estimatedStart)),
                        selected = selectedStartAt == estimatedStart,
                        onClick = { selectedStartAt = estimatedStart }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.adjustStartTime(workoutToAdjust!!.workout.id, selectedStartAt)
                    workoutToAdjust = null
                }) { Text("Guardar", color = c.primary) }
            },
            dismissButton = {
                TextButton(onClick = { workoutToAdjust = null }) { Text("Cancelar", color = c.textMuted) }
            }
        )
    }

    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text("Borrar entrenos", style = SpotterText.title2) },
            text = {
                Text(
                    "Se eliminarán ${selectedIds.size} entrenos junto con todas sus series. Esta acción no se puede deshacer.",
                    style = SpotterText.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulkDelete = false
                    vm.deleteSelected()
                }) { Text("Borrar ${selectedIds.size}", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) {
                    Text("Cancelar", color = c.textMuted)
                }
            },
        )
    }
}

private const val COLLAPSED_EXERCISE_COUNT = 3

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    w: WorkoutWithSets,
    df: DateFormat,
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit,
    onSaveAsTemplate: (String) -> Unit,
    onAdjustStart: () -> Unit
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

    val cardModifier = if (isSelected) {
        Modifier
            .border(2.dp, c.primary, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }

    SpotterCard(modifier = cardModifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelecting) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle
                        else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) c.primary else c.textFaint,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(w.workout.title, style = SpotterText.title3, color = c.text)
                    Text(
                        df.format(Date(w.workout.startedAt)),
                        style = SpotterText.small,
                        color = c.textMuted,
                    )
                }
                if (!isSelecting) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Opciones", tint = c.textFaint)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ajustar hora de inicio") },
                                onClick = {
                                    menuOpen = false
                                    onAdjustStart()
                                }
                            )
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
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (durationMin != null) SpotterChip("$durationMin min")
                if (w.workout.rpe != null) SpotterChip("RPE ${w.workout.rpe}", leading = Icons.Filled.Warning)
                SpotterChip("${exercises.size} ejercicios")
                
                // Mostrar métricas persistidas si existen
                w.workout.calories?.let {
                    SpotterChip("%.0f kcal".format(it), leading = Icons.Filled.LocalFireDepartment)
                }
                w.workout.heartRateAvg?.let {
                    SpotterChip("$it bpm", leading = Icons.Filled.Favorite)
                }
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
                    OutlinedTextField(
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

@Composable
private fun StartTimeOption(
    label: String,
    time: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = SpotterTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primarySoft else c.surfaceMuted)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = c.primary)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = SpotterText.small, color = if (selected) c.primary else c.textMuted)
            Text(time, style = SpotterText.bodyMd, color = c.text)
        }
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
