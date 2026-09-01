package com.n3k0chan.spotter.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.ai.GroqClient
import com.n3k0chan.spotter.ai.Prompts
import com.n3k0chan.spotter.data.db.entities.WeightLog
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class WeightViewModel : ViewModel() {
    private val repo = ServiceLocator.weights
    private val settings = ServiceLocator.settings

    val logs: StateFlow<List<WeightLog>> = repo.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logsAsc: StateFlow<List<WeightLog>> = repo.getAllLogsAsc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary.asStateFlow()

    private val _isLoadingAi = MutableStateFlow(false)
    val isLoadingAi: StateFlow<Boolean> = _isLoadingAi.asStateFlow()

    fun addLog(weightKg: Float, dateMs: Long, notes: String?) {
        viewModelScope.launch {
            repo.insert(WeightLog(weightKg = weightKg, dateMs = dateMs, notes = notes))
        }
    }

    fun updateLog(log: WeightLog, weightKg: Float, dateMs: Long, notes: String?) {
        viewModelScope.launch {
            repo.update(log.copy(weightKg = weightKg, dateMs = dateMs, notes = notes))
        }
    }

    fun deleteLog(log: WeightLog) {
        viewModelScope.launch {
            repo.delete(log)
        }
    }

    fun analyzeProgress() {
        viewModelScope.launch {
            val currentLogs = logsAsc.value
            if (currentLogs.isEmpty()) return@launch

            _isLoadingAi.value = true
            try {
                val apiKey = settings.state.first().groqApiKey
                val model = settings.state.first().groqModel
                if (apiKey.isBlank()) {
                    _aiSummary.value = "Configura tu API Key de Groq en los ajustes para usar la IA."
                    return@launch
                }
                val messages = Prompts.weightAnalysis(currentLogs)
                val response = GroqClient.chat(
                    apiKey = apiKey,
                    model = model,
                    messages = messages,
                )
                _aiSummary.value = response
            } catch (e: Exception) {
                _aiSummary.value = "Error al analizar: ${e.message}"
            } finally {
                _isLoadingAi.value = false
            }
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WeightViewModel() as T
            }
        }
    }
}
