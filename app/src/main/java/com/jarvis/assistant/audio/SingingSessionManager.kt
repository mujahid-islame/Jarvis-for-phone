package com.jarvis.assistant.audio

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SingingSessionManager {
    private val isActive = AtomicBoolean(false)
    private val startTime = AtomicLong(0L)
    private val targetDuration = AtomicLong(0L)
    private var currentSessionId: String? = null

    fun start(durationMs: Long): String {
        val sessionId = UUID.randomUUID().toString()
        currentSessionId = sessionId
        targetDuration.set(durationMs)
        startTime.set(System.currentTimeMillis())
        isActive.set(true)
        return sessionId
    }

    fun stop() {
        isActive.set(false)
        startTime.set(0L)
        targetDuration.set(0L)
    }

    fun cancel() {
        stop()
        currentSessionId = null
    }

    fun isActive(): Boolean = isActive.get()

    fun elapsedMs(): Long {
        if (!isActive.get()) return 0L
        return System.currentTimeMillis() - startTime.get()
    }

    fun remainingMs(): Long {
        if (!isActive.get()) return 0L
        val remaining = targetDuration.get() - elapsedMs()
        return remaining.coerceAtLeast(0L)
    }

    fun targetDurationMs(): Long = targetDuration.get()

    fun shouldStop(): Boolean {
        return isActive.get() && elapsedMs() >= targetDuration.get()
    }

    fun isSessionValid(sessionId: String): Boolean {
        return currentSessionId == sessionId
    }

    fun getCurrentSessionId(): String? = currentSessionId
}
