package com.jarvis.assistant.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.audio.AudioPlayer
import com.jarvis.assistant.audio.AudioRecorder
import com.jarvis.assistant.audio.SingingSessionManager
import com.jarvis.assistant.audio.VoiceActivityDetector
import com.jarvis.assistant.data.model.ChatTurn
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.data.repository.ChatRepository
import com.jarvis.assistant.network.GeminiLiveWebSocket
import com.jarvis.assistant.util.DurationParser
import com.jarvis.assistant.util.PromptGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences: AppPreferences = (application as JarvisApp).preferences
    private val chatRepository: ChatRepository = (application as JarvisApp).chatRepository

    private val _isSessionOn = MutableStateFlow(false)
    val isSessionOn: StateFlow<Boolean> = _isSessionOn.asStateFlow()

    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _isMicMuted = MutableStateFlow(preferences.isMicMuted)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _connectionStatus = MutableStateFlow("READY")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _batteryLevel = MutableStateFlow("0%")
    val batteryLevel: StateFlow<String> = _batteryLevel.asStateFlow()

    private val _ramUsage = MutableStateFlow("0%")
    val ramUsage: StateFlow<String> = _ramUsage.asStateFlow()

    private val _liveTime = MutableStateFlow("")
    val liveTime: StateFlow<String> = _liveTime.asStateFlow()

    private val _singingProgress = MutableStateFlow<String?>(null)
    val singingProgress: StateFlow<String?> = _singingProgress.asStateFlow()

    private val _personalityName = MutableStateFlow(preferences.personality)
    val personalityName: StateFlow<String> = _personalityName.asStateFlow()

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var liveWebSocket: GeminiLiveWebSocket? = null
    private val vad = VoiceActivityDetector()
    private val singingManager = SingingSessionManager()

    private var timeClockJob: Job? = null
    private val currentTurnUserText = StringBuilder()
    private val currentTurnAssistantText = StringBuilder()
    private var currentTurnEmotion = "NEUTRAL"
    private var currentTurnEmotionConfidence: Double? = null
    private var isCurrentTurnInterrupted = false
    private var lastEndOfSpeechTime = 0L
    private var speechStartInPlaybackTime = 0L
    private val latencyHistory = mutableListOf<Long>()

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        startTimeClock()
    }

    private fun startTimeClock() {
        timeClockJob = viewModelScope.launch(Dispatchers.Default) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            while (isActive) {
                _liveTime.value = sdf.format(Date())
                _ramUsage.value = "${getRamUsagePercentage()}%"
                _batteryLevel.value = "${getBatteryPercentage()}%"
                
                // Update singing progress
                if (singingManager.isActive()) {
                    val elapsed = singingManager.elapsedMs()
                    val total = singingManager.targetDurationMs()
                    val elapsedSec = (elapsed / 1000) % 60
                    val elapsedMin = (elapsed / 1000) / 60
                    _singingProgress.value = String.format(Locale.getDefault(), "• %02d:%02d", elapsedMin, elapsedSec)
                } else {
                    _singingProgress.value = null
                }
                
                delay(500) // Faster update for smooth progress
            }
        }
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val intent = getApplication<Application>().registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun getRamUsagePercentage(): Int {
        return try {
            val activityManager = getApplication<Application>().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val usedMem = memoryInfo.totalMem - memoryInfo.availMem
            ((usedMem.toDouble() / memoryInfo.totalMem.toDouble()) * 100).toInt()
        } catch (e: Exception) {
            0
        }
    }

    fun refreshSettings() {
        _personalityName.value = preferences.personality
        _isMicMuted.value = preferences.isMicMuted
        audioRecorder?.setMuted(preferences.isMicMuted)
    }

    fun toggleSession() {
        if (_isSessionOn.value) {
            stopSession()
        } else {
            startSession()
        }
    }

    private fun startSession() {
        val apiKey = preferences.apiKey.trim()
        if (apiKey.isBlank()) {
            viewModelScope.launch {
                _eventFlow.emit("Please configure your Gemini API Key in Settings first.")
            }
            return
        }

        _isSessionOn.value = true
        _conversationState.value = ConversationState.IDLE
        currentTurnAssistantText.clear()

        // 1. Output Player (24kHz Mono Output)
        audioPlayer = AudioPlayer(
            onPlaybackStateChanged = { isPlaying ->
                if (isPlaying) {
                    if (_conversationState.value != ConversationState.SINGING) {
                        _conversationState.value = ConversationState.SPEAKING
                    }
                } else {
                    // Only finalize and go IDLE if NOT in a singing session
                    if (!singingManager.isActive()) {
                        finalizeTurn()
                        if (_isSessionOn.value) {
                            _conversationState.value = ConversationState.IDLE
                        }
                    }
                }
            },
            onPlaybackAmplitude = { amp ->
                if (_conversationState.value == ConversationState.SPEAKING) {
                    _audioLevel.value = amp
                }
            }
        ).apply { start() }

        // 2. Gemini Live WebSocket Connection
        val isSinging = singingManager.isActive()
        val systemPrompt = PromptGenerator.generateSystemPrompt(
            personality = preferences.personality,
            userName = preferences.userName,
            isSingingSession = isSinging,
            requestedDurationMs = if (isSinging) singingManager.targetDurationMs() else 0L
        )

        liveWebSocket = GeminiLiveWebSocket(
            apiKey = apiKey,
            model = preferences.aiModel,
            voiceName = preferences.voice,
            systemPrompt = systemPrompt,
            listener = object : GeminiLiveWebSocket.Listener {
                override fun onConnectionStateChanged(status: String) {
                    _connectionStatus.value = status
                    if (status == "LIVE" && _conversationState.value == ConversationState.IDLE) {
                        _conversationState.value = ConversationState.LISTENING
                    }
                }

                override fun onAudioDataReceived(pcmData: ByteArray) {
                    if (singingManager.isActive()) {
                        _conversationState.value = ConversationState.SINGING
                    } else if (_conversationState.value != ConversationState.SINGING) {
                        _conversationState.value = ConversationState.SPEAKING
                    }
                    audioPlayer?.enqueueAudio(pcmData)
                }

                override fun onUserTextReceived(text: String) {
                    currentTurnUserText.append(text)
                    checkSingingIntent(text)
                }

                override fun onFirstAudioByteReceived() {
                    if (lastEndOfSpeechTime > 0) {
                        val latency = System.currentTimeMillis() - lastEndOfSpeechTime
                        addLatencyRecord(latency)
                        lastEndOfSpeechTime = 0
                    }
                }

                override fun onAssistantTextReceived(textChunk: String) {
                    currentTurnAssistantText.append(textChunk)
                    // Initial detection in case user text hasn't arrived yet
                    if (_conversationState.value != ConversationState.SINGING) {
                        if (textChunk.contains("sing", ignoreCase = true) || textChunk.contains("গান", ignoreCase = true)) {
                            startSingingSession(currentTurnUserText.toString())
                        }
                    }
                }

                override fun onEmotionDetected(emotion: String, confidence: Double?) {
                    currentTurnEmotion = emotion
                    currentTurnEmotionConfidence = confidence
                    android.util.Log.d(TAG, "[EMOTION] emotion=$emotion confidence=$confidence")
                }

                override fun onInterrupted() {
                    isCurrentTurnInterrupted = true
                    if (singingManager.isActive()) {
                        android.util.Log.i(TAG, "[SINGING] Session cancelled via interruption")
                        singingManager.cancel()
                    }
                    finalizeTurn() // Save partial meaningful turn
                    audioPlayer?.flush()
                    currentTurnAssistantText.clear()
                    currentTurnUserText.clear()
                    _conversationState.value = ConversationState.LISTENING
                }

                override fun onTurnCompleted() {
                    // Phase 7: Continuation Strategy
                    if (singingManager.isActive()) {
                        if (singingManager.shouldStop()) {
                            android.util.Log.i(TAG, "[SINGING] Duration reached. Ending gracefully.")
                            singingManager.stop()
                            _conversationState.value = ConversationState.IDLE
                        } else {
                            requestSingingContinuation()
                        }
                    } else if (_conversationState.value == ConversationState.SINGING) {
                        _conversationState.value = ConversationState.SPEAKING
                    }
                }

                override fun onError(message: String) {
                    viewModelScope.launch {
                        _eventFlow.emit(message)
                    }
                }
            }
        ).apply {
            connect(viewModelScope)
        }

        // 3. Audio Recorder (16kHz Mono Input)
        audioRecorder = AudioRecorder { chunk, amplitude ->
            if (_isSessionOn.value) {
                liveWebSocket?.sendAudioChunk(chunk)

                val vadResult = vad.processChunk(amplitude)
                
                when (vadResult) {
                    VoiceActivityDetector.VadResult.SPEECH_CONTINUING -> {
                        if (_conversationState.value == ConversationState.SPEAKING || _conversationState.value == ConversationState.SINGING) {
                            // BARGE-IN logic: Check for sustained speech to avoid accidental triggers
                            if (speechStartInPlaybackTime == 0L) {
                                speechStartInPlaybackTime = System.currentTimeMillis()
                            }
                            
                            val speechInPlaybackDuration = System.currentTimeMillis() - speechStartInPlaybackTime
                            if (speechInPlaybackDuration > 300L) { // Require ~300ms of speech before interrupting
                                android.util.Log.i(TAG, "[BARGE_IN] Confirmed user speaking during playback")
                                if (singingManager.isActive()) {
                                    android.util.Log.i(TAG, "[SINGING] Session cancelled via barge-in")
                                    singingManager.cancel()
                                }
                                isCurrentTurnInterrupted = true
                                audioPlayer?.flush()
                                _conversationState.value = ConversationState.LISTENING
                                speechStartInPlaybackTime = 0L
                            }
                        } else if (_conversationState.value != ConversationState.THINKING) {
                            _conversationState.value = ConversationState.LISTENING
                            speechStartInPlaybackTime = 0L
                        }
                        _audioLevel.value = amplitude
                    }
                    VoiceActivityDetector.VadResult.SPEECH_FINISHED -> {
                        speechStartInPlaybackTime = 0L
                        _conversationState.value = ConversationState.THINKING
                        lastEndOfSpeechTime = System.currentTimeMillis()
                        android.util.Log.d(TAG, "[VAD] End-of-Speech detected. Triggering AI...")
                    }
                    VoiceActivityDetector.VadResult.SHORT_PAUSE -> {
                        // User paused but hasn't finished yet. Just keep level.
                        _audioLevel.value = amplitude
                    }
                    else -> {
                        speechStartInPlaybackTime = 0L
                        _audioLevel.value = amplitude
                    }
                }
            }
        }.apply {
            setMuted(preferences.isMicMuted)
            start(viewModelScope)
        }
    }

    private fun checkSingingIntent(text: String) {
        val lower = text.lowercase()
        if (lower.contains("sing") || lower.contains("গান গাও") || lower.contains("গান করো") || lower.contains("গুনগুন")) {
            if (_conversationState.value != ConversationState.SINGING) {
                startSingingSession(text)
            }
        } else if (lower.contains("stop") || lower.contains("থামো") || lower.contains("বন্ধ করো")) {
            if (singingManager.isActive()) {
                android.util.Log.i(TAG, "[SINGING] Stop command received")
                singingManager.cancel()
                audioPlayer?.flush()
                _conversationState.value = ConversationState.IDLE
            }
        }
    }

    private fun startSingingSession(userRequest: String) {
        val durationMs = DurationParser.parse(userRequest)
        val sessionId = singingManager.start(durationMs)
        _conversationState.value = ConversationState.SINGING
        android.util.Log.i(TAG, "[SINGING] Started session $sessionId for ${durationMs / 1000}s")
    }

    private fun requestSingingContinuation() {
        if (!singingManager.isActive()) return
        
        val remaining = singingManager.remainingMs()
        android.util.Log.i(TAG, "[SINGING] Turn complete. Session active. Remaining: ${remaining / 1000}s. Sending continuation prompt...")
        
        viewModelScope.launch {
            delay(500) // Small buffer to ensure seamless hand-off
            if (!singingManager.isActive()) return@launch
            
            val prompt = if (remaining < 15000L) {
                // Phase 11: Graceful Ending
                "Target duration reached. Finish the current musical phrase naturally now and end the performance gracefully. Thank you."
            } else {
                PromptGenerator.generateContinuationPrompt()
            }
            
            liveWebSocket?.sendText(prompt)
        }
    }

    private fun finalizeTurn() {
        val userText = currentTurnUserText.toString().trim()
        val assistantText = currentTurnAssistantText.toString().trim()

        // Only save if meaningful (at least 2 characters or not empty)
        if (assistantText.length > 1 || userText.length > 1) {
            chatRepository.addTurn(
                ChatTurn(
                    userTranscript = userText.ifEmpty { "Spoken query" },
                    jarvisResponse = assistantText.ifEmpty { "..." },
                    emotion = currentTurnEmotion,
                    emotionConfidence = currentTurnEmotionConfidence,
                    mode = if (_conversationState.value == ConversationState.SINGING) "SINGING" else "NORMAL",
                    interrupted = isCurrentTurnInterrupted
                )
            )
        }
        
        // Reset turn state
        currentTurnAssistantText.clear()
        currentTurnUserText.clear()
        currentTurnEmotion = "NEUTRAL"
        currentTurnEmotionConfidence = null
        isCurrentTurnInterrupted = false
        vad.reset()
    }

    private fun stopSession() {
        _isSessionOn.value = false
        finalizeTurn()
        lastEndOfSpeechTime = 0L

        audioRecorder?.stop()
        audioRecorder = null

        audioPlayer?.flush()
        audioPlayer?.release()
        audioPlayer = null

        liveWebSocket?.disconnect()
        liveWebSocket = null

        _conversationState.value = ConversationState.IDLE
        _connectionStatus.value = "READY"
        _audioLevel.value = 0f
    }

    fun toggleMicMute() {
        val newMuted = !_isMicMuted.value
        _isMicMuted.value = newMuted
        preferences.isMicMuted = newMuted
        audioRecorder?.setMuted(newMuted)
        viewModelScope.launch {
            _eventFlow.emit(if (newMuted) "Microphone Muted" else "Microphone Unmuted")
        }
    }

    private fun addLatencyRecord(latency: Long) {
        latencyHistory.add(latency)
        if (latencyHistory.size > 20) {
            latencyHistory.removeAt(0)
        }

        val avg = latencyHistory.average().toInt()
        val sorted = latencyHistory.sorted()
        
        // P95 N/A if less than 5 samples
        val p95Str = if (latencyHistory.size >= 5) {
            val p95Index = (sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)
            "${sorted[p95Index]}ms"
        } else {
            "N/A"
        }

        android.util.Log.i(TAG, "[LATENCY] Turn Latency: ${latency}ms | Avg(20): ${avg}ms | P95(20): $p95Str")
    }

    override fun onCleared() {
        super.onCleared()
        timeClockJob?.cancel()
        stopSession()
    }
}
