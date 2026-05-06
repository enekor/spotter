package com.n3k0chan.spotter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.ai.GroqClient
import com.n3k0chan.spotter.ai.Prompts
import com.n3k0chan.spotter.data.db.entities.TemplateWithExercises
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.motivation.MotivationalMessages
import com.n3k0chan.spotter.util.StreakCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val workouts = ServiceLocator.workouts
    private val templates = ServiceLocator.templates
    private val settings = ServiceLocator.settings

    val templatesList: StateFlow<List<TemplateWithExercises>> = templates.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    val streak: StateFlow<Int> = workouts.observeFinishedStartTimes()
        .map { StreakCalculator.current(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _greeting = MutableStateFlow<String?>(null)
    val greeting: StateFlow<String?> = _greeting.asStateFlow()

    init {
        viewModelScope.launch { loadGreeting() }
    }

    private suspend fun loadGreeting() {
        val days = runCatching { workouts.observeFinishedStartTimes().first() }.getOrDefault(emptyList())
        val streakNow = StreakCalculator.current(days)
        val cfg = settings.state.value
        if (!cfg.hasApiKey) {
            _greeting.value = MotivationalMessages.forHomeScreen(streakNow)
            return
        }
        val daysSince = StreakCalculator.daysSinceLast(days)
        runCatching {
            GroqClient.chat(
                apiKey = cfg.groqApiKey,
                model = cfg.groqModel,
                messages = Prompts.welcomeMessage(streakNow, daysSince),
                temperature = 0.8,
            )
        }.onSuccess { raw ->
            val cleaned = raw.trim().trim('"', '“', '”')
            _greeting.value = cleaned.ifBlank { MotivationalMessages.forHomeScreen(streakNow) }
        }.onFailure {
            _greeting.value = MotivationalMessages.forHomeScreen(streakNow)
        }
    }

    fun startFreeWorkout(onCreated: (Long) -> Unit) {
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
                return HomeViewModel() as T
            }
        }
    }
}
