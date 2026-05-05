package com.n3k0chan.spotter.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.db.entities.Template
import com.n3k0chan.spotter.data.db.entities.TemplateExercise
import com.n3k0chan.spotter.di.ServiceLocator
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
                if (it.exerciseId == exerciseId) {
                    it.copy(
                        targetSets = sets ?: it.targetSets,
                        targetReps = reps ?: it.targetReps,
                        defaultRestSeconds = rest ?: it.defaultRestSeconds,
                    )
                } else it
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: Long?,
    onBack: () -> Unit,
    vm: TemplateEditorViewModel = viewModel(factory = TemplateEditorViewModel.factory(templateId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (templateId == null) "Nueva plantilla" else "Editar plantilla") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::setName,
                label = { Text(stringResourceCompat(R.string.template_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("Ejercicios", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(state.items, key = { it.exerciseId }) { item ->
                    val exercise = catalog.firstOrNull { it.id == item.exerciseId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    exercise?.name ?: "(borrado)",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { vm.removeItem(item.exerciseId) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField(
                                    label = "Sets",
                                    value = item.targetSets,
                                    onChange = { vm.updateItem(item.exerciseId, sets = it) },
                                    modifier = Modifier.weight(1f),
                                )
                                NumberField(
                                    label = "Reps",
                                    value = item.targetReps,
                                    onChange = { vm.updateItem(item.exerciseId, reps = it) },
                                    modifier = Modifier.weight(1f),
                                )
                                NumberField(
                                    label = "Rest s",
                                    value = item.defaultRestSeconds,
                                    onChange = { vm.updateItem(item.exerciseId, rest = it) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  " + stringResourceCompat(R.string.template_add_exercise))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.save(onBack) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResourceCompat(R.string.template_save)) }
                if (templateId != null) {
                    OutlinedButton(
                        onClick = { vm.delete(onBack) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResourceCompat(R.string.template_delete)) }
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
private fun NumberField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { text = value.toString() }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter(Char::isDigit)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun TemplatePickerDialog(
    catalog: List<Exercise>,
    already: Set<Long>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
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
            }) { Text("Crear y añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResourceCompat(R.string.common_cancel)) } },
        title = { Text(stringResourceCompat(R.string.template_add_exercise)) },
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
                Spacer(Modifier.height(8.dp))
                Text("Crear nuevo", style = MaterialTheme.typography.labelLarge)
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
private fun stringResourceCompat(@androidx.annotation.StringRes id: Int): String =
    androidx.compose.ui.res.stringResource(id)
