package com.n3k0chan.spotter.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.data.health.DaySummary
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HealthUiState(
    val available: Boolean = true,
    val hasPermissions: Boolean = false,
    val loading: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val summary: DaySummary? = null,
    val error: String? = null,
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

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HealthViewModel() as T
            }
        }
    }
}
