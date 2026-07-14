package com.n3k0chan.spotter.ai

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object GroqClient {
    private const val BASE_URL = "https://api.groq.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val service: GroqService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqService::class.java)
    }

    suspend fun chat(
        apiKey: String,
        model: String,
        messages: List<GroqMessage>,
        temperature: Double = 0.6,
        responseFormat: String? = null
    ): String {
        require(apiKey.isNotBlank()) { "Groq API key vacía" }
        val reqFormat = responseFormat?.let { GroqResponseFormat(type = it) }
        val res = service.chatCompletions(
            authHeader = "Bearer $apiKey",
            request = GroqRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                response_format = reqFormat
            ),
        )
        return res.firstContent.orEmpty()
    }
}
