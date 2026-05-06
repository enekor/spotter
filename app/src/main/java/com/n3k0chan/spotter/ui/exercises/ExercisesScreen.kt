package com.n3k0chan.spotter.ui.exercises

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.IconButtonTone
import com.n3k0chan.spotter.ui.components.MuscleGroup
import com.n3k0chan.spotter.ui.components.MuscleGroupAvatar
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.templates.Fab
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExercisesViewModel : ViewModel() {
    val list: StateFlow<List<Exercise>> = ServiceLocator.exercises.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    fun create(name: String, muscle: String?) {
        viewModelScope.launch { ServiceLocator.exercises.create(name, muscle) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { ServiceLocator.exercises.delete(id) }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ExercisesViewModel() as T
            }
        }
    }
}

@Composable
fun ExercisesScreen(
    onBack: () -> Unit,
    vm: ExercisesViewModel = viewModel(factory = ExercisesViewModel.Factory),
) {
    val list by vm.list.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = "Ejercicios",
                leading = { SpotterIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack) },
                trailing = { SpotterIconButton(Icons.Filled.MoreVert, tone = IconButtonTone.Muted) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (list.isEmpty()) {
                Text(
                    "No tienes ejercicios todavía. Crea el primero.",
                    style = SpotterText.body,
                    color = c.textMuted,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                val sorted = list.sortedBy { it.name.lowercase() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                ) {
                    var lastLetter = '?'
                    sorted.forEach { ex ->
                        val letter = ex.name.firstOrNull()?.uppercaseChar() ?: '?'
                        if (letter != lastLetter) {
                            lastLetter = letter
                            item(key = "letter-$letter") {
                                Text(
                                    letter.toString(),
                                    style = SpotterText.caps,
                                    color = c.textMuted,
                                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
                                )
                            }
                        }
                        item(key = ex.id) {
                            ExerciseRow(ex = ex, onDelete = { vm.delete(ex.id) })
                            HorizontalDivider(color = c.border, thickness = 1.dp)
                        }
                    }
                }
            }
            Fab(onClick = { showCreate = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }

    if (showCreate) {
        var newName by remember { mutableStateOf("") }
        var newMuscle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nuevo ejercicio", style = SpotterText.title2) },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        vm.create(newName.trim(), newMuscle.takeIf { it.isNotBlank() })
                        showCreate = false
                    }
                }) { Text("Guardar", color = c.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancelar", color = c.textMuted) }
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
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
}

@Composable
private fun ExerciseRow(ex: Exercise, onDelete: () -> Unit) {
    val c = SpotterTheme.colors
    val group = MuscleGroup.from(ex.muscleGroup)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MuscleGroupAvatar(group = group, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(ex.name, style = SpotterText.bodyMd, color = c.text)
            if (ex.muscleGroup != null) {
                Spacer(Modifier.height(2.dp))
                Text(ex.muscleGroup, style = SpotterText.small, color = c.textMuted)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = c.textFaint, modifier = Modifier.size(18.dp))
        }
    }
}
