package com.jarvis.gensoftlab.data.model

import java.util.UUID

data class AssistantNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
