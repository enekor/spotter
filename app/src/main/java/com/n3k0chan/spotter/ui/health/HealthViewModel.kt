package com.n3k0chan.spotter.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.health.HealthConnectRepository
import com.n3k0chan.spotter.health.HealthSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HealthUiState(
    val availability: HealthConnectRepository.Availability = HealthConnectRepository.Availability.Unsupported,
    val hasPermissions: Boolean = false,
    val snapshot: HealthSnapshot = HealthSnapshot(),
    val loading: Boolean = false,
)

class HealthViewModel : ViewModel() {

    private val repo: HealthConnectRepository = ServiceLocator.health

    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    val readPermissions: Set<String> get() = repo.readPermissions

    init {
        refreshAvailability()
    }

    fun refreshAvailability() {
        viewModelScope.launch {
            val avail = repo.availability()
            _state.value = _state.value.copy(availability = avail)
            if (avail == HealthConnectRepository.Availability.Available) {
                val granted = repo.hasAllPermissions()
                _state.value = _state.value.copy(hasPermissions = granted)
                if (granted) loadSnapshot()
            }
        }
    }

    fun onPermissionsResult() {
        viewModelScope.launch {
            val granted = repo.hasAllPermissions()
            _state.value = _state.value.copy(hasPermissions = granted)
            if (granted) loadSnapshot()
        }
    }

    fun loadSnapshot() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val snap = runCatching { repo.readToday() }.getOrDefault(HealthSnapshot())
            _state.value = _state.value.copy(snapshot = snap, loading = false)
        }
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
