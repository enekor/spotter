package com.n3k0chan.spotter.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.ai.GroqClient
import com.n3k0chan.spotter.ai.Prompts
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutUiState(
    val loading: Boolean = true,
    val workout: WorkoutWithSets? = null,
    val orderedExerciseIds: List<Long> = emptyList(),
    val notes: String = "",
    val rpe: Int? = null,
    val suggestion: String? = null,
    val suggestionForExerciseId: Long? = null,
    val suggestionLoading: Boolean = false,
    val finishedSummary: String? = null,
    val finishedLoading: Boolean = false,
)

class WorkoutViewModel(private val workoutId: Long) : ViewModel() {

    private val workouts = ServiceLocator.workouts
    private val exercises = ServiceLocator.exercises
    private val templates = ServiceLocator.templates
    private val settings = ServiceLocator.settings

    val exerciseCatalog: StateFlow<List<Exercise>> = exercises.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    private val _state = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val w = workouts.get(workoutId)
        if (w == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        // Reordenado: primero los ejercicios añadidos en este entreno (orden de aparición),
        // si proviene de una plantilla y no hay sets aún, exponemos los ejercicios de la plantilla.
        val orderedFromSets = w.sets.map { it.set.exerciseId }.distinct()
        val orderedFromTemplate = w.workout.templateId?.let { tplId ->
            templates.get(tplId)?.items?.sortedBy { it.templateExercise.orderIndex }?.map { it.exercise.id }
        }.orEmpty()
        val combined = (orderedFromSets + orderedFromTemplate).distinct()

        _state.update {
            it.copy(
                loading = false,
                workout = w,
                orderedExerciseIds = combined,
                notes = w.workout.notes.orEmpty(),
                rpe = w.workout.rpe,
            )
        }
    }

    fun addExerciseToSession(exerciseId: Long) {
        _state.update {
            if (it.orderedExerciseIds.contains(exerciseId)) it
            else it.copy(orderedExerciseIds = it.orderedExerciseIds + exerciseId)
        }
    }

    fun removeExerciseFromSession(exerciseId: Long) {
        viewModelScope.launch {
            // Quita el ejercicio del orden y borra sus sets de hoy
            val current = _state.value.workout ?: return@launch
            current.sets.filter { it.set.exerciseId == exerciseId }
                .forEach { workouts.deleteSet(it.set.id) }
            reload()
            _state.update { it.copy(orderedExerciseIds = it.orderedExerciseIds - exerciseId) }
        }
    }

    fun addSet(exerciseId: Long, weightKg: Double, reps: Int, restSeconds: Int?) {
        viewModelScope.launch {
            val current = _state.value.workout ?: return@launch
            val nextSetNumber = current.sets.count { it.set.exerciseId == exerciseId } + 1
            val orderIndex = _state.value.orderedExerciseIds.indexOf(exerciseId).coerceAtLeast(0)
            workouts.addSet(
                WorkoutSet(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    orderIndex = orderIndex,
                    setNumber = nextSetNumber,
                    weightKg = weightKg,
                    reps = reps,
                    restSeconds = restSeconds,
                ),
            )
            reload()
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            workouts.deleteSet(setId)
            reload()
        }
    }

    fun updateSet(updated: WorkoutSet) {
        viewModelScope.launch {
            workouts.updateSet(updated)
            reload()
        }
    }

    fun setNotes(value: String) {
        _state.update { it.copy(notes = value) }
    }

    fun setRpe(value: Int?) {
        _state.update { it.copy(rpe = value) }
    }

    fun fetchSuggestion(exerciseId: Long, exerciseName: String) {
        val cfg = settings.state.value
        if (!cfg.hasApiKey) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    suggestionLoading = true,
                    suggestionForExerciseId = exerciseId,
                    suggestion = null,
                )
            }
            val recent = workouts.recentSetsFor(exerciseId, limit = 10)
            val current = _state.value.workout?.sets
                ?.filter { it.set.exerciseId == exerciseId }
                ?.map { it.set }
                .orEmpty()
            runCatching {
                GroqClient.chat(
                    apiKey = cfg.groqApiKey,
                    model = cfg.groqModel,
                    messages = Prompts.nextSetSuggestion(exerciseName, recent, current),
                    temperature = 0.4,
                )
            }.onSuccess { res ->
                _state.update { it.copy(suggestion = res.trim(), suggestionLoading = false) }
            }.onFailure {
                _state.update {
                    it.copy(
                        suggestion = null,
                        suggestionLoading = false,
                        suggestionForExerciseId = null,
                    )
                }
            }
        }
    }

    fun clearSuggestion() {
        _state.update {
            it.copy(suggestion = null, suggestionLoading = false, suggestionForExerciseId = null)
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            workouts.finish(workoutId, s.rpe, s.notes.takeIf { it.isNotBlank() })
            // Resumen IA opcional
            val cfg = settings.state.value
            if (cfg.hasApiKey) {
                _state.update { it.copy(finishedLoading = true) }
                val full = workouts.get(workoutId)
                if (full != null) {
                    val previous = full.sets.flatMap { sw ->
                        workouts.recentSetsFor(sw.exercise.id, 10)
                    }.take(20)
                    runCatching {
                        GroqClient.chat(
                            apiKey = cfg.groqApiKey,
                            model = cfg.groqModel,
                            messages = Prompts.postSessionSummary(full, previous),
                            temperature = 0.5,
                        )
                    }.onSuccess { res ->
                        _state.update { it.copy(finishedSummary = res.trim(), finishedLoading = false) }
                    }.onFailure {
                        _state.update { it.copy(finishedLoading = false) }
                    }
                } else {
                    _state.update { it.copy(finishedLoading = false) }
                }
            }
            // Backup automático en background si está enlazada Drive
            if (cfg.isDriveLinked && cfg.autoBackupAfterWorkout) {
                runCatching { ServiceLocator.driveBackup.backupNow() }
                // Errores silenciosos: el usuario los ve en Ajustes con la marca de tiempo
            }
            onDone()
        }
    }

    private fun MutableStateFlow<WorkoutUiState>.update(block: (WorkoutUiState) -> WorkoutUiState) {
        value = block(value)
    }

    companion object {
        fun factory(workoutId: Long) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WorkoutViewModel(workoutId) as T
            }
        }
    }
}
