package com.n3k0chan.spotter.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.data.db.entities.WeightLog
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class WeightViewModel : ViewModel() {
    private val repo = ServiceLocator.weights

    val logs: StateFlow<List<WeightLog>> = repo.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logsAsc: StateFlow<List<WeightLog>> = repo.getAllLogsAsc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WeightViewModel() as T
            }
        }
    }
}
