package com.n3k0chan.spotter.ai

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GroqService {
    @POST("openai/v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authHeader: String,
        @Body request: GroqRequest,
    ): GroqResponse
}
