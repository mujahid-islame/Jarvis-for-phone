package com.jarvis.gensoftlab.tools

data class ToolResult(
    val success: Boolean,
    val tool: String,
    val actionId: String? = null,
    val taskId: String? = null,
    val generationId: String? = null,
    val error: String? = null,
    val message: String? = null,
    val data: Any? = null,
    val capability: String? = null,
    val status: String? = null
)
