package com.n3k0chan.spotter.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado compartido del temporizador. El servicio actualiza estos valores y la UI los observa.
 * Se mantiene como singleton para que la UI sobreviva a recreaciones de la activity sin perder el estado.
 */
object RestTimerController {

    data class State(
        val isRunning: Boolean = false,
        val totalSeconds: Int = 0,
        val remainingSeconds: Int = 0,
        val finishedAt: Long? = null,
    ) {
        val progress: Float
            get() = if (totalSeconds == 0) 0f else (totalSeconds - remainingSeconds).toFloat() / totalSeconds
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    internal fun update(transform: (State) -> State) {
        _state.value = transform(_state.value)
    }
}
