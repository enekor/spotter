@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.n3k0chan.spotter.ui.chat

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.ai.GroqClient
import com.n3k0chan.spotter.ai.GroqMessage
import com.n3k0chan.spotter.ai.Prompts
import com.n3k0chan.spotter.data.db.entities.WorkoutWithSets
import com.n3k0chan.spotter.data.db.entities.profile
import com.n3k0chan.spotter.data.measurement.formatShort
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.IconButtonTone
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatTurn(val role: String, val content: String)

class ChatViewModel : ViewModel() {

    private val settings = ServiceLocator.settings
    private val workouts = ServiceLocator.workouts
    private val exercisesRepo = ServiceLocator.exercises

    val catalog: StateFlow<List<com.n3k0chan.spotter.data.db.entities.Exercise>> =
        exercisesRepo.observeAll().stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    private val _messages = MutableStateFlow<List<ChatTurn>>(emptyList())
    val messages: StateFlow<List<ChatTurn>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Cuando está activo, en el SIGUIENTE envío adjuntamos el historial de entrenos. */
    private val _attachHistory = MutableStateFlow(false)
    val attachHistory: StateFlow<Boolean> = _attachHistory.asStateFlow()

    /**
     * Cuando está activo, en el SIGUIENTE envío adjuntamos el catálogo de ejercicios
     * (nombre + grupo + tipo de medida, SIN series). Útil para preguntar qué
     * ejercicio del catálogo encaja con una máquina del gimnasio.
     */
    private val _attachCatalog = MutableStateFlow(false)
    val attachCatalog: StateFlow<Boolean> = _attachCatalog.asStateFlow()

    fun hasKey(): Boolean = settings.state.value.hasApiKey

    fun toggleAttachHistory() { _attachHistory.value = !_attachHistory.value }

    fun toggleAttachCatalog() { _attachCatalog.value = !_attachCatalog.value }

    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _sending.value) return
        val cfg = settings.state.value
        if (!cfg.hasApiKey) return

        _messages.value = _messages.value + ChatTurn("user", text)
        _sending.value = true
        val attachHistoryNow = _attachHistory.value
        val attachCatalogNow = _attachCatalog.value
        // Toggles one-shot: se desactivan tras el envío
        if (attachHistoryNow) _attachHistory.value = false
        if (attachCatalogNow) _attachCatalog.value = false

        viewModelScope.launch {
            val history = _messages.value.dropLast(1).map { GroqMessage(it.role, it.content) }
            val parts = mutableListOf(text)
            if (attachHistoryNow) {
                runCatching { buildHistoryContext() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { parts += it }
            }
            if (attachCatalogNow) {
                runCatching { buildCatalogContext() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { parts += it }
            }
            val finalUserText = parts.joinToString("\n\n")
            runCatching {
                GroqClient.chat(
                    apiKey = cfg.groqApiKey,
                    model = cfg.groqModel,
                    messages = Prompts.chatTurn(history, finalUserText),
                    temperature = 0.6,
                )
            }.onSuccess { reply ->
                _messages.value = _messages.value + ChatTurn("assistant", reply.trim())
            }.onFailure {
                _messages.value = _messages.value + ChatTurn(
                    "assistant",
                    "Error contactando con Groq: ${it.message ?: it::class.simpleName}",
                )
            }
            _sending.value = false
        }
    }

    /**
     * Construye un bloque con el catálogo entero de ejercicios disponibles
     * (nombre + grupo + tipo de medida). Sirve para preguntar al chat qué
     * ejercicio encaja con una máquina o aparato del gimnasio. NO incluye
     * historial ni series — solo metadata del catálogo.
     */
    private suspend fun buildCatalogContext(): String {
        val all = catalog.value.takeIf { it.isNotEmpty() }
            ?: runCatching { exercisesRepo.observeAll().first() }.getOrNull().orEmpty()
        if (all.isEmpty()) return ""
        return buildString {
            appendLine("[Catálogo de ejercicios disponibles · ${all.size} ejercicios]")
            // Agrupar por grupo muscular para que el asistente pueda navegar mejor
            all.groupBy { it.muscleGroup ?: "Otro" }
                .toSortedMap()
                .forEach { (group, list) ->
                    appendLine("· $group:")
                    list.sortedBy { it.name.lowercase() }.forEach { ex ->
                        val profile = com.n3k0chan.spotter.data.measurement.MeasurementProfile
                            .fromNameOrDefault(ex.measurementProfile)
                        appendLine("  - ${ex.name} (${profile.display})")
                    }
                }
        }.trimEnd()
    }

    /**
     * Construye un resumen de las sesiones terminadas dentro de la ventana
     * configurada en Ajustes (semana / mes / año / todo) para inyectar como
     * contexto al asistente.
     */
    private suspend fun buildHistoryContext(): String {
        val window = settings.state.value.chatHistoryWindow
        val cutoff = window.cutoffMillis()
        val sessions = workouts.observeAll().first()
            .filter { it.workout.finishedAt != null }
            .filter { cutoff == null || it.workout.startedAt >= cutoff }
        if (sessions.isEmpty()) return ""
        val df = DateFormat.getDateInstance(DateFormat.SHORT)
        return buildString {
            appendLine("[Historial · ${window.display} · ${sessions.size} sesiones, más reciente primero]")
            sessions.forEach { w -> appendSession(w, df) }
        }.trimEnd()
    }

    private fun StringBuilder.appendSession(w: WorkoutWithSets, df: DateFormat) {
        val durationMin = w.workout.finishedAt
            ?.let { (it - w.workout.startedAt) / 60_000 }
            ?.toInt()
        append("- ").append(df.format(Date(w.workout.startedAt)))
        append(" · ").append(w.workout.title)
        if (durationMin != null) append(" (${durationMin} min)")
        if (w.workout.rpe != null) append(" · RPE ${w.workout.rpe}")
        appendLine()
        val byExercise = w.sets.groupBy { it.exercise.name }
        byExercise.entries.take(8).forEach { (name, sets) ->
            val profile = sets.firstOrNull()?.exercise?.profile ?: return@forEach
            append("  ").append(name).append(": ")
            append(sets.joinToString(", ") { it.set.formatShort(profile) })
            appendLine()
        }
        if (!w.workout.notes.isNullOrBlank()) {
            appendLine("  notas: ${w.workout.notes}")
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel() as T
            }
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val attachHistory by vm.attachHistory.collectAsStateWithLifecycle()
    val attachCatalog by vm.attachCatalog.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val groqModel = ServiceLocator.settings.state.value.groqModel

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = "Asistente",
                subtitle = "Groq · $groqModel",
                leading = { SpotterIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack) },
                trailing = { SpotterIconButton(Icons.Filled.MoreVert, tone = IconButtonTone.Muted) },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface),
            ) {
                HorizontalDivider(color = c.border, thickness = 1.dp)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AttachHistoryChip(
                            selected = attachHistory,
                            onClick = { vm.toggleAttachHistory() },
                        )
                        AttachCatalogChip(
                            selected = attachCatalog,
                            onClick = { vm.toggleAttachCatalog() },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Pregunta lo que quieras…", color = c.textFaint) },
                            modifier = Modifier.weight(1f),
                            enabled = vm.hasKey() && !sending,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = c.surfaceMuted,
                                unfocusedContainerColor = c.surfaceMuted,
                                disabledContainerColor = c.surfaceMuted,
                                focusedBorderColor = c.borderStrong,
                                unfocusedBorderColor = c.border,
                                disabledBorderColor = c.border,
                                cursorColor = c.primary,
                                focusedTextColor = c.text,
                                unfocusedTextColor = c.text,
                            ),
                            shape = RoundedCornerShape(24.dp),
                            textStyle = SpotterText.body,
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (vm.hasKey() && !sending && input.isNotBlank()) c.primary else c.surfaceVariant)
                                .clickable(
                                    enabled = vm.hasKey() && !sending && input.isNotBlank(),
                                    onClick = { vm.send(input); input = "" },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if (vm.hasKey() && !sending && input.isNotBlank()) c.onPrimary else c.textFaint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (!vm.hasKey()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            ) {
                SpotterCard(
                    background = c.surfaceMuted,
                    border = c.border,
                    padding = 16.dp,
                ) {
                    Text(
                        "Configura tu API key de Groq en Ajustes para usar el asistente.",
                        style = SpotterText.body,
                        color = c.text,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                items(messages) { msg -> Bubble(msg) }
                if (sending) item { Bubble(ChatTurn("assistant", "…")) }
            }
        }
    }

}

@Composable
private fun Bubble(turn: ChatTurn) {
    val c = SpotterTheme.colors
    val isUser = turn.role == "user"
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    ),
                )
                .background(if (isUser) c.primary else c.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                turn.content,
                style = SpotterText.body,
                color = if (isUser) c.onPrimary else c.text,
            )
        }
    }
}

@Composable
private fun AttachCatalogChip(selected: Boolean, onClick: () -> Unit) {
    val c = SpotterTheme.colors
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.primarySoft else c.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                id = com.n3k0chan.spotter.R.drawable.ic_muscle_other,
            ),
            contentDescription = null,
            tint = if (selected) c.primarySoftText else c.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Mis ejercicios",
            style = SpotterText.smallMd,
            color = if (selected) c.primarySoftText else c.textMuted,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) c.primary else c.borderStrong),
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(12.dp)
                    .align(if (selected) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(androidx.compose.ui.graphics.Color.White),
            )
        }
    }
}

@Composable
private fun AttachHistoryChip(selected: Boolean, onClick: () -> Unit) {
    val c = SpotterTheme.colors
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.primarySoft else c.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.n3k0chan.spotter.R.drawable.ic_muscle_other),
            contentDescription = null,
            tint = if (selected) c.primarySoftText else c.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Compartir historial de entrenamiento",
            style = SpotterText.smallMd,
            color = if (selected) c.primarySoftText else c.textMuted,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) c.primary else c.borderStrong),
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(12.dp)
                    .align(if (selected) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(androidx.compose.ui.graphics.Color.White),
            )
        }
    }
}
