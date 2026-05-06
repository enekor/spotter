package com.n3k0chan.spotter.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.db.entities.Template
import com.n3k0chan.spotter.data.db.entities.TemplateExercise
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.SpotterButton
import com.n3k0chan.spotter.ui.components.SpotterButtonVariant
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateEditorState(
    val templateId: Long? = null,
    val name: String = "",
    val items: List<TemplateExercise> = emptyList(),
)

class TemplateEditorViewModel(initialTemplateId: Long?) : ViewModel() {

    private val templates = ServiceLocator.templates
    private val exercises = ServiceLocator.exercises

    private val _state = MutableStateFlow(TemplateEditorState(templateId = initialTemplateId))
    val state: StateFlow<TemplateEditorState> = _state.asStateFlow()

    val catalog: StateFlow<List<Exercise>> = exercises.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (initialTemplateId != null) {
            viewModelScope.launch {
                templates.get(initialTemplateId)?.let { tpl ->
                    _state.value = TemplateEditorState(
                        templateId = tpl.template.id,
                        name = tpl.template.name,
                        items = tpl.items.sortedBy { it.templateExercise.orderIndex }
                            .map { it.templateExercise },
                    )
                }
            }
        }
    }

    fun setName(value: String) { _state.value = _state.value.copy(name = value) }

    fun addItem(exerciseId: Long) {
        val cur = _state.value
        if (cur.items.any { it.exerciseId == exerciseId }) return
        _state.value = cur.copy(
            items = cur.items + TemplateExercise(
                templateId = cur.templateId ?: 0,
                exerciseId = exerciseId,
                orderIndex = cur.items.size,
            ),
        )
    }

    fun removeItem(exerciseId: Long) {
        _state.value = _state.value.copy(items = _state.value.items.filterNot { it.exerciseId == exerciseId })
    }

    fun updateItem(exerciseId: Long, sets: Int? = null, reps: Int? = null, rest: Int? = null) {
        _state.value = _state.value.copy(
            items = _state.value.items.map {
                if (it.exerciseId == exerciseId) it.copy(
                    targetSets = sets ?: it.targetSets,
                    targetReps = reps ?: it.targetReps,
                    defaultRestSeconds = rest ?: it.defaultRestSeconds,
                ) else it
            },
        )
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            val id = if (s.templateId == null) {
                templates.create(s.name, s.items)
            } else {
                templates.rename(s.templateId, s.name)
                templates.replaceItems(s.templateId, s.items)
                s.templateId
            }
            _state.value = s.copy(templateId = id)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _state.value.templateId ?: return
        viewModelScope.launch {
            templates.delete(Template(id = id, name = _state.value.name))
            onDone()
        }
    }

    companion object {
        fun factory(id: Long?) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TemplateEditorViewModel(id) as T
            }
        }
    }
}

@Composable
fun TemplateEditorScreen(
    templateId: Long?,
    onBack: () -> Unit,
    vm: TemplateEditorViewModel = viewModel(factory = TemplateEditorViewModel.factory(templateId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = if (templateId == null) "Nueva plantilla" else "Editar plantilla",
                leading = { SpotterIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack) },
                trailing = {
                    SpotterButton(
                        text = "Guardar",
                        variant = SpotterButtonVariant.Text,
                        height = 36.dp,
                        onClick = { vm.save(onBack) },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Name card
            item {
                SpotterCard(padding = 16.dp) {
                    Column {
                        Text("NOMBRE", style = SpotterText.caps, color = c.textMuted)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = vm::setName,
                            placeholder = { Text("Push", style = SpotterText.title2, color = c.textFaint) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = c.surface,
                                unfocusedContainerColor = c.surface,
                                focusedBorderColor = c.borderStrong,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                cursorColor = c.primary,
                                focusedTextColor = c.text,
                                unfocusedTextColor = c.text,
                            ),
                            textStyle = SpotterText.title2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Text(
                    "EJERCICIOS · ${state.items.size}",
                    style = SpotterText.caps,
                    color = c.textMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(state.items, key = { it.exerciseId }) { item ->
                val exercise = catalog.firstOrNull { it.id == item.exerciseId }
                ItemCard(
                    name = exercise?.name ?: "(borrado)",
                    sets = item.targetSets,
                    reps = item.targetReps,
                    rest = item.defaultRestSeconds,
                    onSetsChange = { vm.updateItem(item.exerciseId, sets = it) },
                    onRepsChange = { vm.updateItem(item.exerciseId, reps = it) },
                    onRestChange = { vm.updateItem(item.exerciseId, rest = it) },
                    onRemove = { vm.removeItem(item.exerciseId) },
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                SpotterButton(
                    text = "Añadir ejercicio",
                    leading = Icons.Filled.Add,
                    variant = SpotterButtonVariant.Outlined,
                    full = true,
                    onClick = { showPicker = true },
                )
                if (templateId != null) {
                    Spacer(Modifier.height(8.dp))
                    SpotterButton(
                        text = "Eliminar plantilla",
                        leading = Icons.Filled.Delete,
                        variant = SpotterButtonVariant.Danger,
                        full = true,
                        onClick = { vm.delete(onBack) },
                    )
                }
            }
        }
    }

    if (showPicker) {
        TemplatePickerDialog(
            catalog = catalog,
            already = state.items.map { it.exerciseId }.toSet(),
            onPick = {
                vm.addItem(it.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun ItemCard(
    name: String,
    sets: Int,
    reps: Int,
    rest: Int,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val c = SpotterTheme.colors
    SpotterCard(padding = 14.dp, background = c.surface, border = c.border) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = null,
                    tint = c.textFaint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = SpotterText.bodyMd, color = c.text)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$sets×$reps · desc. ${rest}s",
                        style = SpotterText.small.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = c.textMuted,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Quitar", tint = c.textFaint, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("Sets", sets, onSetsChange, Modifier.weight(1f))
                IntField("Reps", reps, onRepsChange, Modifier.weight(1f))
                IntField("Rest s", rest, onRestChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SpotterTheme.colors
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { text = value.toString() }
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceMuted)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = SpotterText.small, color = c.textMuted)
        Spacer(Modifier.height(2.dp))
        BasicTextField(
            value = text,
            onValueChange = {
                text = it.filter(Char::isDigit)
                text.toIntOrNull()?.let(onChange)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = SpotterText.numS.copy(color = c.text),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
        )
    }
}

@Composable
private fun TemplatePickerDialog(
    catalog: List<Exercise>,
    already: Set<Long>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SpotterTheme.colors
    var query by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newMuscle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
        title = { Text("Añadir ejercicio", style = SpotterText.title2) },
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
                val filtered = catalog.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(filtered, key = { it.id }) { ex ->
                        FilterChip(
                            selected = ex.id in already,
                            onClick = { onPick(ex) },
                            label = { Text(ex.name + (ex.muscleGroup?.let { " · $it" } ?: "")) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
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
