package com.n3k0chan.spotter.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.ai.GroqClient
import com.n3k0chan.spotter.ai.GroqMessage
import com.n3k0chan.spotter.ai.Prompts
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.health.HealthConnectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatTurn(val role: String, val content: String)

class ChatViewModel : ViewModel() {

    private val settings = ServiceLocator.settings
    private val health = ServiceLocator.health

    private val _messages = MutableStateFlow<List<ChatTurn>>(emptyList())
    val messages: StateFlow<List<ChatTurn>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Cuando está activo, en el SIGUIENTE envío adjuntamos los datos de Health Connect. */
    private val _attachHealth = MutableStateFlow(false)
    val attachHealth: StateFlow<Boolean> = _attachHealth.asStateFlow()

    fun hasKey(): Boolean = settings.state.value.hasApiKey

    fun toggleAttachHealth() {
        _attachHealth.value = !_attachHealth.value
    }

    /** True solo si Health Connect está disponible y permisos concedidos. */
    suspend fun canAttachHealth(): Boolean =
        health.availability() == HealthConnectRepository.Availability.Available &&
            health.hasAllPermissions()

    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _sending.value) return
        val cfg = settings.state.value
        if (!cfg.hasApiKey) return

        _messages.value = _messages.value + ChatTurn("user", text)
        _sending.value = true
        val attach = _attachHealth.value
        // El toggle es one-shot: se desactiva después de enviar
        if (attach) _attachHealth.value = false

        viewModelScope.launch {
            val history = _messages.value.dropLast(1).map { GroqMessage(it.role, it.content) }
            val finalUserText = if (attach && canAttachHealth()) {
                val snap = runCatching { health.readToday() }.getOrNull()
                if (snap != null && !snap.isEmpty) {
                    "$text\n\n[Datos de Health Connect de hoy]\n${snap.toPromptContext()}"
                } else text
            } else text
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

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel() as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(8.dp)) {
                val attachHealth by vm.attachHealth.collectAsStateWithLifecycle()
                var canAttach by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { canAttach = vm.canAttachHealth() }
                if (canAttach) {
                    androidx.compose.material3.FilterChip(
                        selected = attachHealth,
                        onClick = { vm.toggleAttachHealth() },
                        label = {
                            Text(
                                if (attachHealth) "Adjuntar datos de Health Connect (sí)"
                                else "Adjuntar datos de Health Connect",
                            )
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.chat_hint)) },
                        modifier = Modifier.weight(1f),
                        enabled = vm.hasKey() && !sending,
                    )
                    IconButton(
                        onClick = {
                            vm.send(input)
                            input = ""
                        },
                        enabled = vm.hasKey() && !sending && input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp),
        ) {
            if (!vm.hasKey()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        stringResource(R.string.chat_no_key),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(messages) { msg -> Bubble(msg) }
                if (sending) {
                    item { Bubble(ChatTurn("assistant", "…")) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(turn: ChatTurn) {
    val isUser = turn.role == "user"
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val container =
        if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Card(
            colors = CardDefaults.cardColors(containerColor = container),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                turn.content,
                modifier = Modifier.padding(12.dp),
                color = content,
            )
        }
    }
}
