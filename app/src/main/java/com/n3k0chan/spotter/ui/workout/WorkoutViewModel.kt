package com.n3k0chan.spotter.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

import kotlinx.serialization.json.Json

data class WorkoutUiState(
    val loading: Boolean = true,
    val workout: WorkoutWithSets? = null,
    val orderedExerciseIds: List<Long> = emptyList(),
    val templateTargets: Map<Long, TemplateExercise> = emptyMap(),
    val notes: String = "",
    val rpe: Int? = null,
    val suggestedExerciseId: Long? = null,
    val suggestionDismissed: Boolean = false,
    val finishedComparison: WorkoutComparison? = null,
    val showPostFinish: Boolean = false,
)

class WorkoutViewModel(private val workoutId: Long) : ViewModel() {

    private val workouts = ServiceLocator.workouts
    private var trainingCounts: Map<Long, Int> = emptyMap()
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
        recomputeSuggestion()
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
        _state.update { it.copy(suggestionDismissed = false) }
        recomputeSuggestion()
    }

    fun removeExerciseFromSession(exerciseId: Long) {
        viewModelScope.launch {
            val current = _state.value.workout ?: return@launch
            current.sets.filter { it.set.exerciseId == exerciseId }
                .forEach { workouts.deleteSet(it.set.id) }
            reload()
            _state.update { it.copy(orderedExerciseIds = it.orderedExerciseIds - exerciseId) }
            _state.update { it.copy(suggestionDismissed = false) }
            recomputeSuggestion()
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

    fun dismissSuggestion() {
        _state.update { it.copy(suggestionDismissed = true, suggestedExerciseId = null) }
    }

    private fun recomputeSuggestion() {
        viewModelScope.launch {
            if (trainingCounts.isEmpty()) {
                trainingCounts = runCatching { workouts.exerciseTrainingCounts() }.getOrDefault(emptyMap())
            }
            val catalog = exerciseCatalog.value
            val sessionExercises = _state.value.orderedExerciseIds
                .mapNotNull { id -> catalog.firstOrNull { it.id == id } }
            val suggestedId = NextExerciseSuggester.suggest(sessionExercises, catalog, trainingCounts)
            _state.update { it.copy(suggestedExerciseId = suggestedId) }
        }
    }

    fun finish(chosenStartedAt: Long, backupAfterFinish: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            persistWorkout(chosenStartedAt, backupAfterFinish)

            val full = workouts.get(workoutId)
            if (full == null) {
                onDone()
                return@launch
            }

            val comparison = buildComparison(full)
            // Persistir para verlo luego en el detalle (columna reutilizada).
            workouts.get(workoutId)?.workout?.let { w ->
                runCatching {
                    val json = Json.encodeToString(WorkoutComparison.serializer(), comparison)
                    workouts.update(w.copy(aiSummaryJson = json))
                }
            }
            _state.update { it.copy(showPostFinish = true, finishedComparison = comparison) }
        }
    }

    private suspend fun buildComparison(full: WorkoutWithSets): WorkoutComparison {
        val byExercise = full.sets.groupBy { it.exercise.id }
        val inputs = byExercise.map { (exerciseId, setsWithEx) ->
            val exercise = setsWithEx.first().exercise
            val profile = com.n3k0chan.spotter.data.measurement.MeasurementProfile
                .fromNameOrDefault(exercise.measurementProfile)
            val todaySets = setsWithEx.map { it.set }
            val history = workouts.recentSetsFor(exerciseId, limit = 30)
                .filter { it.workoutId != workoutId }
            ExerciseComparisonInput(exercise.name, profile, todaySets, history)
        }
        return WorkoutComparator.compare(inputs)
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
