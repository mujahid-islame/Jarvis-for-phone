package com.jarvis.gensoftlab.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser

data class GeminiToolResponse(
    val callId: String,
    val name: String,
    val success: Boolean,
    val message: String,
    val actionId: String?,
    val taskId: String?,
    val generationId: String?,
    val data: Any? = null
)

class GeminiToolCallRouter(
    private val executor: suspend (Context, String, Map<String, Any?>) -> AssistantToolResult
) {
    private val gson = Gson()

    suspend fun route(
        context: Context,
        callId: String,
        name: String,
        arguments: Map<String, Any?>,
        sendResponse: (GeminiToolResponse) -> Unit
    ) {
        val validation = PhonePlannerContract.validateFunctionCall(name, arguments)
        if (!validation.valid) {
            sendResponse(
                GeminiToolResponse(callId, name, false, validation.message, arguments["action_id"]?.toString(),
                    arguments["task_id"]?.toString(), arguments["generation_id"]?.toString())
            )
            return
        }

        val result = executor(context, name, arguments + ("action_id" to callId))
        val parsed = runCatching { JsonParser.parseString(result.message).asJsonObject }.getOrNull()
        sendResponse(
            GeminiToolResponse(
                callId = callId,
                name = name,
                success = result.success,
                message = result.message,
                actionId = parsed?.get("actionId")?.asString ?: callId,
                taskId = parsed?.get("taskId")?.asString ?: arguments["task_id"]?.toString(),
                generationId = parsed?.get("generationId")?.asString ?: arguments["generation_id"]?.toString(),
                data = parsed?.get("data")
            )
        )
    }

    fun serialize(response: GeminiToolResponse): String = gson.toJson(response)
}
