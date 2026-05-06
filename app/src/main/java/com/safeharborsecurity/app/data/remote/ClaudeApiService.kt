package com.safeharborsecurity.app.data.remote

import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import com.safeharborsecurity.app.data.remote.model.ClaudeResponse
import retrofit2.Response
import retrofit2.http.*

interface ClaudeApiService {

    @POST("v1/messages")
    @Headers(
        "anthropic-version: 2023-06-01",
        "content-type: application/json"
    )
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Body request: ClaudeRequest
    ): Response<ClaudeResponse>
}
