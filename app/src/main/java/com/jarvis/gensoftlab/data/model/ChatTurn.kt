package com.jarvis.gensoftlab.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChatTurn(
    val id: String = UUID.randomUUID().toString(),
    val userTranscript: String,
    val jarvisResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: String = "NEUTRAL",
    val emotionConfidence: Double? = null,
    val mode: String = "NORMAL",
    val interrupted: Boolean = false
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
