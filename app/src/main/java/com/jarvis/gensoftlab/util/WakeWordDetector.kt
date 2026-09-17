package com.jarvis.gensoftlab.util

object WakeWordDetector {
    private val wakeWords = listOf(
        "hey jarvis",
        "jarvis",
        "ok jarvis",
        "hello jarvis",
        "hey jervis",
        "jarvis please",
        "jarvis,",
        "হ্যালো জারভিস",
        "hello jarvis",
        "hey jarvis",
        "জারভিস",
        "জারভিস দাও",
        "জারভিস শুনছ",
        "হেই জারভিস",
        "হ্যালো জারভিস,",
        "জারভিস,"
    )

    fun detect(text: String): Boolean {
        val normalized = text.lowercase().trim()
        if (normalized.isBlank()) return false

        return wakeWords.any { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }
}
