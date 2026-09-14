package com.jarvis.assistant.data.model

data class MemoryVaultEntry(
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis()
)
