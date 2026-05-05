package com.n3k0chan.spotter.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.data.db.entities.TemplateWithExercises
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutHubViewModel : ViewModel() {
    private val templates = ServiceLocator.templates
    private val workouts = ServiceLocator.workouts

    val templates_: StateFlow<List<TemplateWithExercises>> = templates.observeAll().stateIn(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHubScreen(
    onStartFreeWorkout: (Long) -> Unit,
    onStartFromTemplate: (Long) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenExercises: () -> Unit,
    vm: WorkoutHubViewModel = viewModel(factory = WorkoutHubViewModel.Factory),
) {
    val templates by vm.templates_.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_workout)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Button(
                onClick = { vm.startFree(onStartFreeWorkout) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.home_start_free)) }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenTemplates,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Gestionar plantillas") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenExercises,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Gestionar ejercicios") }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.templates_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            if (templates.isEmpty()) {
                Text(
                    stringResource(R.string.templates_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates, key = { it.template.id }) { tpl ->
                        Card(
                            onClick = { vm.startFromTemplate(tpl.template.id, onStartFromTemplate) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(tpl.template.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${tpl.items.size} ejercicios",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
