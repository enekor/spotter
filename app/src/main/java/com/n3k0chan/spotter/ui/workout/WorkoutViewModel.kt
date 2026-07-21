package com.n3k0chan.spotter.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.ai.GroqClient
import com.n3k0chan.spotter.ai.Prompts
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.db.entities.TemplateExercise
import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import com.n3k0chan.spotter.data.health.WorkoutHealthMetrics
import com.n3k0chan.spotter.data.repository.SetInput
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AiSummaryResponse(
    val summary: String,
    val exercises: List<AiSummaryExercise> = emptyList()
)

@Serializable
data class AiSummaryExercise(
    val name: String,
    val markdown: String
)

data class WorkoutUiState(
    val loading: Boolean = true,
    val workout: WorkoutWithSets? = null,
    val orderedExerciseIds: List<Long> = emptyList(),
    val templateTargets: Map<Long, TemplateExercise> = emptyMap(),
    val notes: String = "",
    val rpe: Int? = null,
    val suggestion: String? = null,
    val suggestionForExerciseId: Long? = null,
    val suggestionLoading: Boolean = false,
    val finishedSummary: AiSummaryResponse? = null,
    val finishedLoading: Boolean = false,
    val showPostFinish: Boolean = false,
)

class WorkoutViewModel(private val workoutId: Long) : ViewModel() {

    private val workouts = ServiceLocator.workouts
    private val exercises = ServiceLocator.exercises
    private val templates = ServiceLocator.templates
    private val settings = ServiceLocator.settings
    private val healthConnect = ServiceLocator.healthConnect

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
        val orderedFromSets = w.sets.map { it.set.exerciseId }.distinct()
        val tpl = w.workout.templateId?.let { tplId -> templates.get(tplId) }
        val orderedFromTemplate = tpl?.items
            ?.sortedBy { it.templateExercise.orderIndex }
            ?.map { it.exercise.id }
            .orEmpty()
        val combined = (orderedFromSets + orderedFromTemplate).distinct()

        val targets = tpl?.items?.associate { it.exercise.id to it.templateExercise }.orEmpty()

        _state.update {
            it.copy(
                loading = false,
                workout = w,
                orderedExerciseIds = combined,
                templateTargets = targets,
                notes = w.workout.notes.orEmpty(),
                rpe = w.workout.rpe,
            )
        }
    }

    fun calculateEstimatedStart(): Long {
        val w = _state.value.workout ?: return System.currentTimeMillis()
        if (w.sets.isEmpty()) return w.workout.startedAt
        
        // Sumamos duraciones de series y descansos
        val totalActiveSeconds = w.sets.sumOf { (it.set.durationSeconds ?: 0).toLong() }
        val totalRestSeconds = w.sets.sumOf { (it.set.restSeconds ?: 0).toLong() }
        
        // Estimación: tiempo total = suma de todo
        val totalMs = (totalActiveSeconds + totalRestSeconds) * 1000
        return System.currentTimeMillis() - totalMs
    }

    fun addExerciseToSession(exerciseId: Long) {
        _state.update {
            if (it.orderedExerciseIds.contains(exerciseId)) it
            else it.copy(orderedExerciseIds = it.orderedExerciseIds + exerciseId)
        }
    }

    fun removeExerciseFromSession(exerciseId: Long) {
        viewModelScope.launch {
            val current = _state.value.workout ?: return@launch
            current.sets.filter { it.set.exerciseId == exerciseId }
                .forEach { workouts.deleteSet(it.set.id) }
            reload()
            _state.update { it.copy(orderedExerciseIds = it.orderedExerciseIds - exerciseId) }
        }
    }

    fun addSet(exerciseId: Long, input: SetInput) {
        viewModelScope.launch {
            val current = _state.value.workout ?: return@launch
            val nextSetNumber = current.sets.count { it.set.exerciseId == exerciseId } + 1
            val orderIndex = _state.value.orderedExerciseIds.indexOf(exerciseId).coerceAtLeast(0)
            workouts.addSet(
                workoutId = workoutId,
                exerciseId = exerciseId,
                orderIndex = orderIndex,
                setNumber = nextSetNumber,
                input = input,
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
            val exercise = ServiceLocator.exercises.get(exerciseId)
            val profile = exercise?.let {
                com.n3k0chan.spotter.data.measurement.MeasurementProfile.fromNameOrDefault(it.measurementProfile)
            } ?: com.n3k0chan.spotter.data.measurement.MeasurementProfile.Default
            val recent = workouts.recentSetsFor(exerciseId, limit = 10)
            val current = _state.value.workout?.sets
                ?.filter { it.set.exerciseId == exerciseId }
                ?.map { it.set }
                .orEmpty()
            runCatching {
                GroqClient.chat(
                    apiKey = cfg.groqApiKey,
                    model = cfg.groqModel,
                    messages = Prompts.nextSetSuggestion(exerciseName, profile, recent, current),
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

    fun finish(chosenStartedAt: Long, backupAfterFinish: Boolean, onDone: () -> Unit) {
        val cfg = settings.state.value
        val hasAi = cfg.hasApiKey

        if (!hasAi) {
            viewModelScope.launch {
                persistWorkout(chosenStartedAt, backupAfterFinish)
                onDone()
            }
            return
        }

        viewModelScope.launch {
            persistWorkout(chosenStartedAt, backupAfterFinish)
            _state.update { it.copy(showPostFinish = true, finishedLoading = true) }

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
                        responseFormat = "json_object"
                    )
                }.onSuccess { res ->
                    val parsed = runCatching {
                        Json { ignoreUnknownKeys = true }.decodeFromString<AiSummaryResponse>(res)
                    }.getOrNull()
                    
                    if (parsed != null) {
                        // Guardar en BD para poder verlo despues
                        val currentWorkout = workouts.get(workoutId)?.workout
                        if (currentWorkout != null) {
                            workouts.update(currentWorkout.copy(aiSummaryJson = res))
                        }
                        
                        _state.update { it.copy(finishedSummary = parsed, finishedLoading = false) }
                    } else {
                        _state.update { it.copy(finishedLoading = false) }
                    }
                }.onFailure {
                    _state.update { it.copy(finishedLoading = false) }
                }
            } else {
                _state.update { it.copy(finishedLoading = false) }
            }
        }
    }

    private suspend fun persistWorkout(chosenStartedAt: Long, backupAfterFinish: Boolean) {
        val s = _state.value
        val now = System.currentTimeMillis()

        var metrics: WorkoutHealthMetrics? = null
        if (healthConnect.isAvailable()) {
            val hasPerms = runCatching { healthConnect.hasAllPermissions() }.getOrDefault(false)
            if (hasPerms) {
                metrics = runCatching {
                    healthConnect.readMetricsForTimeRange(
                        Instant.ofEpochMilli(chosenStartedAt),
                        Instant.ofEpochMilli(now)
                    )
                }.getOrNull()
            }
        }

        workouts.finish(
            workoutId = workoutId,
            rpe = s.rpe,
            notes = s.notes.takeIf { it.isNotBlank() },
            startedAt = chosenStartedAt,
            calories = metrics?.calories,
            heartRateAvg = metrics?.heartRateAvg,
            heartRateMin = metrics?.heartRateMin,
            heartRateMax = metrics?.heartRateMax,
            distanceMeters = metrics?.distanceMeters,
            steps = metrics?.steps
        )

        val cfg = settings.state.value
        if (backupAfterFinish && cfg.isDriveLinked) {
            runCatching { ServiceLocator.driveBackup.backupNow() }
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
