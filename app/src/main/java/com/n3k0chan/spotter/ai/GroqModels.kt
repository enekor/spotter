package com.n3k0chan.spotter.ai

import kotlinx.serialization.Serializable

@Serializable
data class GroqMessage(
    val role: String,
    val content: String,
)

@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.6,
    val max_tokens: Int = 512,
    val stream: Boolean = false,
)

@Serializable
data class GroqChoice(val message: GroqMessage)

@Serializable
data class GroqResponse(val choices: List<GroqChoice>) {
    val firstContent: String? get() = choices.firstOrNull()?.message?.content
}
