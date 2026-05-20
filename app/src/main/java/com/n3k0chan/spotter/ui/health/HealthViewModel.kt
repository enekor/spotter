package com.n3k0chan.spotter.ui.health

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.ai.GroqClient
import com.n3k0chan.spotter.ai.Prompts
import com.n3k0chan.spotter.data.health.DaySummary
import com.n3k0chan.spotter.data.health.ExerciseSession
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SourceInfo(
    val packageName: String,
    val sessionCount: Int,
)

data class ImportPreview(
    val sessions: List<ExerciseSession>,
    val sources: List<SourceInfo>,
    val selectedSources: Set<String>,
)

data class HealthUiState(
    val available: Boolean = true,
    val hasPermissions: Boolean = false,
    val loading: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val summary: DaySummary? = null,
    val error: String? = null,
    val importing: Boolean = false,
    val importResult: String? = null,
    val scanningImport: Boolean = false,
    val importPreview: ImportPreview? = null,
)

class HealthViewModel : ViewModel() {

    private val hc = ServiceLocator.healthConnect

    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    init {
        checkAvailabilityAndPermissions()
    }

    private fun checkAvailabilityAndPermissions() {
        if (!hc.isAvailable()) {
            _state.update { it.copy(available = false) }
            return
        }
        viewModelScope.launch {
            val has = hc.hasAllPermissions()
            _state.update { it.copy(hasPermissions = has) }
            if (has) load()
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        val has = hc.permissions.all { it in granted }
        _state.update { it.copy(hasPermissions = has) }
        if (has) load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val summary = hc.readDaySummary(_state.value.date)
                _state.update { it.copy(loading = false, summary = summary) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.localizedMessage ?: "Error desconocido") }
            }
        }
    }

    fun previousDay() {
        _state.update { it.copy(date = it.date.minusDays(1)) }
        load()
    }

    fun nextDay() {
        val next = _state.value.date.plusDays(1)
        if (next.isAfter(LocalDate.now())) return
        _state.update { it.copy(date = next) }
        load()
    }

    fun scanForImport(startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            _state.update { it.copy(scanningImport = true, importPreview = null) }
            try {
                val allSessions = hc.readExerciseSessions(startDate, endDate)
                    .filter { it.title != null || it.type != 0 }
                val sources = allSessions
                    .groupBy { it.sourcePackage ?: "desconocido" }
                    .map { (pkg, list) -> SourceInfo(pkg, list.size) }
                    .sortedByDescending { it.sessionCount }
                _state.update {
                    it.copy(
                        scanningImport = false,
                        importPreview = ImportPreview(
                            sessions = allSessions,
                            sources = sources,
                            selectedSources = sources.map { s -> s.packageName }.toSet(),
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        scanningImport = false,
                        importResult = "Error al escanear: ${e.localizedMessage}",
                    )
                }
            }
        }
    }

    fun toggleImportSource(packageName: String) {
        _state.update { st ->
            val preview = st.importPreview ?: return@update st
            val newSelected = if (packageName in preview.selectedSources)
                preview.selectedSources - packageName
            else preview.selectedSources + packageName
            st.copy(importPreview = preview.copy(selectedSources = newSelected))
        }
    }

    fun dismissImportPreview() {
        _state.update { it.copy(importPreview = null) }
    }

    fun confirmImport() {
        val preview = _state.value.importPreview ?: return
        viewModelScope.launch {
            _state.update { it.copy(importing = true, importPreview = null) }
            try {
                val workoutsRepo = ServiceLocator.workouts
                val settings = ServiceLocator.settings.state.value
                val filtered = preview.sessions.filter {
                    (it.sourcePackage ?: "desconocido") in preview.selectedSources
                }

                val aiResults = processWithAi(filtered, settings.groqApiKey, settings.groqModel)

                var imported = 0
                for ((i, session) in filtered.withIndex()) {
                    val ai = aiResults.getOrNull(i)
                    if (ai?.skip == true) continue

                    val startMs = session.startTime.toEpochMilli()
                    val existing = workoutsRepo.getWorkoutsInRange(
                        startMs - 5 * 60_000,
                        startMs + 5 * 60_000,
                    )
                    if (existing.isNotEmpty()) continue

                    val title = ai?.title ?: session.title ?: exerciseTypeName(session.type)
                    val durationMin = java.time.Duration.between(session.startTime, session.endTime).toMinutes()
                    val notesParts = mutableListOf("Importado desde Health Connect")
                    ai?.label?.takeIf { it.isNotBlank() }?.let { notesParts += it }
                    notesParts += "Duración: ${durationMin}min"
                    session.calories?.let { notesParts += "Calorías: %.0f kcal".format(it) }
                    session.heartRateAvg?.let { notesParts += "FC media: $it bpm" }
                    session.distance?.let { d ->
                        notesParts += if (d >= 1000) "Distancia: %.2f km".format(d / 1000)
                        else "Distancia: %.0f m".format(d)
                    }
                    session.sourcePackage?.let { pkg ->
                        notesParts += "Fuente: ${appLabel(pkg)}"
                    }

                    workoutsRepo.importFromHealthConnect(
                        title = title,
                        startedAt = startMs,
                        finishedAt = session.endTime.toEpochMilli(),
                        notes = notesParts.joinToString(" · "),
                    )
                    imported++
                }
                _state.update {
                    it.copy(
                        importing = false,
                        importResult = if (imported > 0) "$imported sesiones importadas"
                        else "No se encontraron sesiones nuevas para importar",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(importing = false, importResult = "Error: ${e.localizedMessage}")
                }
            }
        }
    }

    @Serializable
    private data class AiSessionResult(
        val title: String? = null,
        val label: String? = null,
        val skip: Boolean = false,
    )

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun processWithAi(
        sessions: List<ExerciseSession>,
        apiKey: String,
        model: String,
    ): List<AiSessionResult> {
        if (apiKey.isBlank() || sessions.isEmpty()) return emptyList()
        return try {
            val zone = ZoneId.systemDefault()
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            val sessionsJson = buildString {
                append("[")
                sessions.forEachIndexed { idx, s ->
                    if (idx > 0) append(",")
                    val start = s.startTime.atZone(zone)
                    val durationMin = java.time.Duration.between(s.startTime, s.endTime).toMinutes()
                    append("{")
                    append("\"date\":\"${start.format(dateFmt)}\",")
                    append("\"time\":\"${start.format(timeFmt)}\",")
                    append("\"title\":${s.title?.let { "\"$it\"" } ?: "null"},")
                    append("\"type\":\"${exerciseTypeName(s.type)}\",")
                    append("\"durationMin\":$durationMin,")
                    append("\"calories\":${s.calories?.let { "%.0f".format(it) } ?: "null"},")
                    append("\"heartRateAvg\":${s.heartRateAvg ?: "null"},")
                    append("\"distanceM\":${s.distance?.let { "%.0f".format(it) } ?: "null"}")
                    append("}")
                }
                append("]")
            }

            val messages = Prompts.healthConnectImport(sessionsJson)
            val response = GroqClient.chat(apiKey, model, messages, temperature = 0.3)

            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            lenientJson.decodeFromString<List<AiSessionResult>>(cleaned)
        } catch (e: Exception) {
            Log.w("HealthVM", "AI processing failed, importing all: ${e.message}")
            sessions.map { AiSessionResult(skip = false) }
        }
    }

    fun consumeImportResult() {
        _state.update { it.copy(importResult = null) }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HealthViewModel() as T
            }
        }
    }
}

internal fun appLabel(packageName: String): String {
    val known = mapOf(
        "com.google.android.apps.fitness" to "Google Fit",
        "com.samsung.shealth" to "Samsung Health",
        "com.samsung.android.wear.shealth" to "Samsung Health (reloj)",
        "com.huawei.health" to "Huawei Health",
        "com.xiaomi.wearable" to "Mi Fitness",
        "com.xiaomi.hm.health" to "Zepp Life",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.strava" to "Strava",
        "com.polar.beat" to "Polar Beat",
        "com.polar.flow" to "Polar Flow",
        "com.withings.wiscale2" to "Withings",
        "com.sec.android.app.shealth" to "Samsung Health",
    )
    return known[packageName] ?: packageName.substringAfterLast('.')
        .replaceFirstChar { it.uppercase() }
}
