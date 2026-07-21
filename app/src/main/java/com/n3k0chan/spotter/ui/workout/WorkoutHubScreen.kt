package com.n3k0chan.spotter.ui.workout

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.TemplateExerciseWithExercise
import com.n3k0chan.spotter.data.db.entities.TemplateWithExercises
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.SpotterButton
import com.n3k0chan.spotter.ui.components.SpotterButtonVariant
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutHubViewModel : ViewModel() {
    private val templates = ServiceLocator.templates
    private val workouts = ServiceLocator.workouts

    val templatesList: StateFlow<List<TemplateWithExercises>> = templates.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    fun startFree(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = workouts.start(title = "Entreno libre", templateId = null)
            onCreated(id)
        }
    }

    fun startFromTemplate(templateId: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val tpl = templates.get(templateId) ?: return@launch
            val id = workouts.start(title = tpl.template.name, templateId = templateId)
            onCreated(id)
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WorkoutHubViewModel() as T
            }
        }
    }
}

@Composable
fun WorkoutHubScreen(
    onStartFreeWorkout: (Long) -> Unit,
    onStartFromTemplate: (Long) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenExercises: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    vm: WorkoutHubViewModel = viewModel(factory = WorkoutHubViewModel.Factory),
) {
    val templates by vm.templatesList.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = "Entrenar",
                trailing = {
                    Row {
                        SpotterIconButton(Icons.AutoMirrored.Filled.Chat, onClick = onOpenChat)
                        SpotterIconButton(Icons.Filled.Settings, onClick = onOpenSettings)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SpotterButton(
                    text = "Entreno libre",
                    leading = Icons.Filled.PlayArrow,
                    full = true,
                    onClick = { vm.startFree(onStartFreeWorkout) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotterButton(
                        text = "Plantillas",
                        variant = SpotterButtonVariant.Outlined,
                        onClick = onOpenTemplates,
                        modifier = Modifier.weight(1f),
                    )
                    SpotterButton(
                        text = "Ejercicios",
                        variant = SpotterButtonVariant.Outlined,
                        onClick = onOpenExercises,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "PLANTILLAS",
                    style = SpotterText.caps,
                    color = c.textMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
                )
            }
            if (templates.isEmpty()) {
                item {
                    Text(
                        "Aún no tienes plantillas. Crea una para reutilizar tus rutinas.",
                        style = SpotterText.body,
                        color = c.textMuted,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            } else {
                items(templates, key = { it.template.id }) { tpl ->
                    TemplateRowCard(
                        name = tpl.template.name,
                        exercises = tpl.items,
                        onClickStart = { vm.startFromTemplate(tpl.template.id, onStartFromTemplate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateRowCard(
    name: String,
    exercises: List<TemplateExerciseWithExercise>,
    onClickStart: () -> Unit
) {
    val c = SpotterTheme.colors
    var expanded by androidx.compose.runtime.mutableStateOf(false)

    SpotterCard(onClick = { expanded = !expanded }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = c.textMuted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = SpotterText.title3, color = c.text)
                    Spacer(Modifier.size(2.dp))
                    Text("${exercises.size} ejercicios", style = SpotterText.small, color = c.textMuted)
                }
                SpotterButton(
                    text = "Iniciar",
                    variant = SpotterButtonVariant.Filled,
                    onClick = onClickStart,
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    exercises.forEach { item ->
                        Text(
                            text = "• ${item.exercise.name} (${item.templateExercise.targetSets} series)",
                            style = SpotterText.body,
                            color = c.textMuted
                        )
                    }
                }
            }
        }
    }
}
