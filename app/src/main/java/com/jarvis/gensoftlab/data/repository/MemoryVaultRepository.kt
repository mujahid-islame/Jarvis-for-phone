package com.jarvis.gensoftlab.data.repository

import com.jarvis.gensoftlab.data.model.MemoryVaultEntry
import com.jarvis.gensoftlab.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemoryVaultRepository(private val preferences: AppPreferences) {
    private val _entries = MutableStateFlow(preferences.loadMemoryVault())
    val entries: StateFlow<List<MemoryVaultEntry>> = _entries.asStateFlow()

    fun upsert(key: String, value: String) {
        val normalizedKey = key.trim().ifBlank { "unknown" }
        val normalizedValue = value.trim().take(200)
        val updated = (entries.value.filterNot { it.key == normalizedKey } + MemoryVaultEntry(normalizedKey, normalizedValue))
            .takeLast(30)
        _entries.value = updated
        preferences.saveMemoryVault(updated)
    }

    fun rememberFact(key: String, value: String) {
        upsert(key, value)
    }

    fun readValue(key: String): String? = entries.value.firstOrNull { it.key == key.trim() }?.value

    fun snapshot(): Map<String, String> = entries.value.associate { it.key to it.value }

    fun clear() {
        _entries.value = emptyList()
        preferences.saveMemoryVault(emptyList())
    }
}
