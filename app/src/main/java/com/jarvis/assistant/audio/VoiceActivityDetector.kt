package com.jarvis.assistant.audio

import android.util.Log

class VoiceActivityDetector {
    companion object {
        private const val TAG = "VAD"
        private const val CALIBRATION_CHUNKS = 20
        private const val MIN_SPEECH_DURATION_MS = 1L // Super snappy initiation
        private const val SILENCE_TIMEOUT_MS = 200L // 200ms for ultra chot-pot response rate
        private const val INITIAL_NOISE_FLOOR = 0.15f
    }

    private var noiseFloor = INITIAL_NOISE_FLOOR
    private var calibrationCounter = 0
    private val noiseHistory = mutableListOf<Float>()

    private var isUserSpeaking = false
    private var speechStartTime = 0L
    private var lastSpeechDetectedTime = 0L

    fun processChunk(amplitude: Float): VadResult {
        // 1. Dynamic Noise Floor Calibration (Adapts to current environment background)
        if (calibrationCounter < CALIBRATION_CHUNKS) {
            noiseHistory.add(amplitude)
            calibrationCounter++
            if (calibrationCounter == CALIBRATION_CHUNKS) {
                val avg = noiseHistory.average().toFloat()
                noiseFloor = maxOf(avg * 1.3f, INITIAL_NOISE_FLOOR)
                Log.d(TAG, "[VAD] Dynamic Noise Floor Calibrated: $noiseFloor")
            }
            return VadResult.CALIBRATING
        }

        val currentTime = System.currentTimeMillis()
        val isCurrentFrameSpeech = amplitude > noiseFloor

        if (isCurrentFrameSpeech) {
            lastSpeechDetectedTime = currentTime
            if (!isUserSpeaking) {
                isUserSpeaking = true
                speechStartTime = currentTime
            }
            // Slowly track noise floor upward if background noise grows slightly
            noiseFloor = (noiseFloor * 0.995f) + (amplitude * 0.005f)
            return VadResult.SPEECH_CONTINUING
        } else {
            // Adapt noise floor downwards during pure silence chunks
            if (!isUserSpeaking) {
                noiseFloor = (noiseFloor * 0.98f) + (amplitude * 0.02f)
                noiseFloor = maxOf(noiseFloor, INITIAL_NOISE_FLOOR)
            }

            if (isUserSpeaking) {
                val silentDuration = currentTime - lastSpeechDetectedTime
                val speechDuration = lastSpeechDetectedTime - speechStartTime

                if (silentDuration > SILENCE_TIMEOUT_MS) {
                    isUserSpeaking = false
                    return if (speechDuration >= MIN_SPEECH_DURATION_MS) {
                        VadResult.SPEECH_FINISHED
                    } else {
                        VadResult.NOISE_IGNORE
                    }
                }
                return VadResult.SHORT_PAUSE
            }
        }

        return VadResult.SILENCE
    }

    fun reset() {
        isUserSpeaking = false
        speechStartTime = 0L
        lastSpeechDetectedTime = 0L
    }

    enum class VadResult {
        CALIBRATING,
        SPEECH_CONTINUING,
        SHORT_PAUSE,
        SPEECH_FINISHED,
        NOISE_IGNORE,
        SILENCE
    }
}
