package com.n3k0chan.spotter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.IconButtonTone
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatTurn(val role: String, val content: String)

class ChatViewModel : ViewModel() {

    private val settings = ServiceLocator.settings

    private val _messages = MutableStateFlow<List<ChatTurn>>(emptyList())
    val messages: StateFlow<List<ChatTurn>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun hasKey(): Boolean = settings.state.value.hasApiKey

    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _sending.value) return
        val cfg = settings.state.value
        if (!cfg.hasApiKey) return

        _messages.value = _messages.value + ChatTurn("user", text)
        _sending.value = true

        viewModelScope.launch {
            val history = _messages.value.dropLast(1).map { GroqMessage(it.role, it.content) }
            runCatching {
                GroqClient.chat(
                    apiKey = cfg.groqApiKey,
                    model = cfg.groqModel,
                    messages = Prompts.chatTurn(history, text),
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

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
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
