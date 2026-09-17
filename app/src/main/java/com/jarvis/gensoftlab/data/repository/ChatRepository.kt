package com.jarvis.gensoftlab.data.repository

import com.jarvis.gensoftlab.data.model.ChatTurn
import com.jarvis.gensoftlab.data.model.GeminiConstants
import com.jarvis.gensoftlab.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatRepository(private val preferences: AppPreferences) {

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    init {
        _turns.value = preferences.loadChatHistory()
    }

    @Synchronized
    fun buildMemoryContext(limit: Int = 20): String {
        if (limit <= 0) return ""

        val recentTurns = _turns.value.takeLast(limit)
        if (recentTurns.isEmpty()) return ""

        val memoryEntries = recentTurns.mapNotNull { turn ->
            val userText = turn.userTranscript.trim()
            val assistantText = turn.jarvisResponse.trim()
            when {
                userText.isEmpty() -> null
                assistantText.isEmpty() -> "User asked: ${sanitizeForMemory(userText)}"
                else -> "User asked: ${sanitizeForMemory(userText)}. Assistant replied: ${sanitizeForMemory(assistantText)}"
            }
        }

        val compact = memoryEntries
            .distinct()
            .takeLast(8)
            .joinToString(" | ")
            .trim()

        return if (compact.isNotBlank()) {
            "USER MEMORY\n- $compact"
        } else {
            ""
        }
    }

    private fun sanitizeForMemory(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\[EMOTION:[^\\]]+\\]"), "")
            .trim()
            .take(220)
    }

    @Synchronized
    fun addTurn(turn: ChatTurn) {
        val currentList = _turns.value.toMutableList()

        // Deduplication check
        if (currentList.isNotEmpty()) {
            val last = currentList.last()
            if (last.userTranscript == turn.userTranscript && last.jarvisResponse == turn.jarvisResponse) {
                return
            }
        }

        currentList.add(turn)
        if (currentList.size > 100) {
            currentList.removeAt(0)
        }

        _turns.value = currentList
        preferences.saveChatHistory(currentList)
    }

    @Synchronized
    fun getModeSpecificHistory(mode: String): List<ChatTurn> {
        val all = _turns.value
        return when (mode) {
            GeminiConstants.PERSONALITY_ASSISTANT -> all.filter { it.mode == "NORMAL" || it.mode == "ASSISTANT" }
            GeminiConstants.PERSONALITY_GIRLFRIEND -> all.filter { it.mode == "SINGING" || it.mode == "GIRLFRIEND" }
            GeminiConstants.PERSONALITY_PERSONAL_AI -> all
            else -> all
        }
    }

    @Synchronized
    fun clearHistory() {
        _turns.value = emptyList()
        preferences.clearChatHistory()
    }
}
