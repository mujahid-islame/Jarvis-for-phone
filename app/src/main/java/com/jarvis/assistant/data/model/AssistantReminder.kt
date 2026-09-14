package com.jarvis.assistant.data.model

import java.util.UUID

data class AssistantReminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val scheduledAt: Long,
    val createdAt: Long = System.currentTimeMillis()
)
