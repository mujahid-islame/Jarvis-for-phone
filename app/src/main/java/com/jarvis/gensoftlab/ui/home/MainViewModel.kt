package com.jarvis.gensoftlab.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.gensoftlab.JarvisApp
import com.jarvis.gensoftlab.audio.AudioPlayer
import com.jarvis.gensoftlab.audio.AudioRecorder
import com.jarvis.gensoftlab.audio.SingingSessionManager
import com.jarvis.gensoftlab.audio.VoiceActivityDetector
import com.jarvis.gensoftlab.data.model.ChatTurn
import com.jarvis.gensoftlab.data.model.ConversationState
import com.jarvis.gensoftlab.data.model.GeminiConstants
import com.jarvis.gensoftlab.data.preferences.AppPreferences
import com.jarvis.gensoftlab.data.repository.AssistantMemoryRepository
import com.jarvis.gensoftlab.data.repository.ChatRepository
import com.jarvis.gensoftlab.data.repository.MemoryVaultRepository
import com.jarvis.gensoftlab.network.GeminiLiveWebSocket
import com.jarvis.gensoftlab.util.DeviceContextSnapshotBuilder
import com.jarvis.gensoftlab.util.AssistantCommandParser
import com.jarvis.gensoftlab.util.AssistantCommandType
import com.jarvis.gensoftlab.util.AssistantToolExecutor
import com.jarvis.gensoftlab.util.AssistantToolResult
import com.jarvis.gensoftlab.util.GeminiToolCallRouter
import com.jarvis.gensoftlab.util.DurationParser
import com.jarvis.gensoftlab.util.PhonePlannerContract
import com.jarvis.gensoftlab.util.PhoneAgent
import com.jarvis.gensoftlab.util.PhonePlanner
import com.jarvis.gensoftlab.util.PlannerValidation
import com.jarvis.gensoftlab.util.PhoneTaskRouter
import com.jarvis.gensoftlab.util.PromptGenerator
import com.jarvis.gensoftlab.util.WakeWordDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
    private val assistantMemoryRepository: AssistantMemoryRepository = (application as JarvisApp).assistantMemoryRepository
    private val memoryVaultRepository: MemoryVaultRepository = (application as JarvisApp).memoryVaultRepository

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

    private val _currentEmotion = MutableStateFlow("NEUTRAL")
    val currentEmotion: StateFlow<String> = _currentEmotion.asStateFlow()

    private val _singingProgress = MutableStateFlow<String?>(null)
    val singingProgress: StateFlow<String?> = _singingProgress.asStateFlow()

    private val _personalityName = MutableStateFlow(preferences.personality)
    val personalityName: StateFlow<String> = _personalityName.asStateFlow()

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var liveWebSocket: GeminiLiveWebSocket? = null
    private val geminiToolCallRouter = GeminiToolCallRouter { context, name, arguments ->
        AssistantToolExecutor.executeFunction(context, name, arguments)
    }
    private val vad = VoiceActivityDetector()
    private val singingManager = SingingSessionManager()

    private var timeClockJob: Job? = null
    private var pendingCommandJob: Job? = null
    private val currentTurnUserText = StringBuilder()
    private val currentTurnAssistantText = StringBuilder()
    private var handledCommandType: AssistantCommandType? = null
    private var activePhoneGoal: com.jarvis.gensoftlab.util.TaskGoal? = null
    private var activePhoneTaskJob: Job? = null
    private var plannerDecisionChannel: Channel<PlannerValidation>? = null
    private var pendingPlannerCallId: String? = null
    private var pendingPlannerCallName: String? = null
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
        refreshDeviceMemorySnapshot()
    }

    private fun refreshDeviceMemorySnapshot() {
        val snapshot = DeviceContextSnapshotBuilder.build(getApplication())
        memoryVaultRepository.upsert("device_context", snapshot.toMemorySummary())
        memoryVaultRepository.upsert("battery", "${snapshot.batteryPercent}%")
        memoryVaultRepository.upsert("charging", if (snapshot.isCharging) "charging" else "discharging")
        memoryVaultRepository.upsert("current_app", snapshot.currentApp)
        memoryVaultRepository.upsert("time", snapshot.timeText)
        memoryVaultRepository.upsert("date", snapshot.dateText)
        memoryVaultRepository.upsert("network", snapshot.connectionType)
        memoryVaultRepository.upsert("brightness", "${snapshot.brightnessPercent}%")
        memoryVaultRepository.upsert("volume", "${snapshot.volumePercent}%")
    }

    private fun buildRuntimeMemoryContext(): String {
        val chatMemory = chatRepository.buildMemoryContext()
        val vaultMap = memoryVaultRepository.snapshot()
        val vaultEntries = vaultMap
            .filterKeys { it.isNotBlank() }
            .toSortedMap()
            .map { (key, value) -> "$key: $value" }
            .joinToString(" | ")
        val deviceSnapshot = DeviceContextSnapshotBuilder.build(getApplication())
        val deviceFacts = "device_context: ${deviceSnapshot.toMemorySummary()}"
        return buildList {
            if (chatMemory.isNotBlank()) add(chatMemory)
            if (vaultEntries.isNotBlank()) add(vaultEntries)
            if (deviceFacts.isNotBlank()) add(deviceFacts)
        }.joinToString("\n")
    }

    fun toggleSession() {
        if (_isSessionOn.value) {
            stopSession()
        } else {
            startSession()
        }
    }

    fun ensureSessionStarted() {
        if (!_isSessionOn.value) {
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
        handledCommandType = null
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
        refreshDeviceMemorySnapshot()
        val isSinging = singingManager.isActive()
        val memoryContext = buildRuntimeMemoryContext()
        val systemPrompt = PromptGenerator.generateSystemPrompt(
            personality = preferences.personality,
            userName = preferences.userName,
            isSingingSession = isSinging,
            requestedDurationMs = if (isSinging) singingManager.targetDurationMs() else 0L,
            memoryContext = memoryContext
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
                    if (WakeWordDetector.detect(text)) {
                        _conversationState.value = ConversationState.LISTENING
                    }

                    val transcript = currentTurnUserText.toString().trim()
                    val routed = PhoneTaskRouter.route(transcript)
                    if (routed.route == com.jarvis.gensoftlab.util.RequestRoute.CANCEL_TASK) {
                        activePhoneTaskJob?.cancel()
                        plannerDecisionChannel?.close()
                        activePhoneTaskJob = null
                        plannerDecisionChannel = null
                        activePhoneGoal = null
                        currentTurnAssistantText.append("ঠিক আছে, চলমান phone task বন্ধ করেছি।")
                        liveWebSocket?.sendText("User cancelled the active phone task. Confirm cancellation briefly in Bangla.")
                        return
                    }
                    if (routed.route == com.jarvis.gensoftlab.util.RequestRoute.PHONE_TASK && routed.goal != null) {
                        if (activePhoneTaskJob?.isActive == true) return
                        if (!PhoneTaskRouter.isConfident(routed.goal)) {
                            liveWebSocket?.sendText("The phone task goal is ambiguous. Ask the user for the minimum clarification needed before acting.")
                            return
                        }
                        activePhoneTaskJob?.cancel()
                        plannerDecisionChannel?.close()
                        val goal = routed.goal
                        activePhoneGoal = routed.goal
                        plannerDecisionChannel = Channel(capacity = 1)
                        activePhoneTaskJob = viewModelScope.launch {
                            val channel = plannerDecisionChannel ?: return@launch
                            val agent = PhoneAgent(getApplication())
                            val result = agent.run(
                                goal = goal,
                                planner = PhonePlanner { context, uiSnapshot ->
                                    liveWebSocket?.sendText(buildPhoneTaskPlanningInstruction(goal, context, uiSnapshot))
                                    channel.receive()
                                },
                                onActionResult = { decision, actionResult ->
                                    val callId = pendingPlannerCallId
                                    val name = pendingPlannerCallName ?: decision.action.orEmpty()
                                    if (!callId.isNullOrBlank()) {
                                        liveWebSocket?.sendToolResponse(
                                            callId = callId,
                                            name = name,
                                            success = actionResult.success,
                                            message = actionResult.message
                                        )
                                        pendingPlannerCallId = null
                                        pendingPlannerCallName = null
                                    }
                                }
                            )
                            currentTurnAssistantText.append(result.message)
                            liveWebSocket?.sendText("Phone task result: ${result.message}. Respond naturally in Bangla without exposing internal state.")
                            activePhoneGoal = null
                            plannerDecisionChannel = null
                            activePhoneTaskJob = null
                        }
                        return
                    }
                    val command = AssistantCommandParser.parse(transcript)
                    if (command != null &&
                        command.type != handledCommandType &&
                        hasCommandPayload(command, transcript)
                    ) {
                        handledCommandType = command.type
                        if (isActionCommand(command.type)) {
                            pendingCommandJob?.cancel()
                            pendingCommandJob = viewModelScope.launch {
                                delay(650)
                                val latestTranscript = currentTurnUserText.toString().trim()
                                val latestCommand = AssistantCommandParser.parse(latestTranscript)
                                if (latestCommand == null ||
                                    latestCommand.type != command.type ||
                                    !hasCommandPayload(latestCommand, latestTranscript)
                                ) {
                                    handledCommandType = null
                                    return@launch
                                }

                                persistVoiceCommand(latestCommand, latestTranscript)
                                val response = if (isToolCommand(latestCommand.type)) {
                                    val result = AssistantToolExecutor.executeResult(getApplication(), latestCommand)
                                    if (result.success) {
                                        "TOOL_RESULT_SUCCESS: ${result.message}\nTell the user briefly what was completed."
                                    } else {
                                        "TOOL_RESULT_FAILURE: ${result.message}\nDo not claim this action was completed. Explain the failure briefly."
                                    }
                                } else {
                                    AssistantCommandParser.buildAssistantResponse(latestCommand)
                                }
                                currentTurnAssistantText.append(response)
                                liveWebSocket?.sendText(response)
                            }
                        } else {
                            persistVoiceCommand(command, transcript)
                            val response = AssistantCommandParser.buildAssistantResponse(command)
                            currentTurnAssistantText.append(response)
                            liveWebSocket?.sendText(response)
                        }
                        return
                    }

                    if (!handleModeSwitchCommand(text)) {
                        checkSingingIntent(text)
                    }
                }

                override fun onToolCall(callId: String, name: String, arguments: Map<String, Any?>) {
                    viewModelScope.launch {
                        val validation = PhonePlannerContract.validateFunctionCall(name, arguments)
                        if (activePhoneTaskJob?.isActive == true && plannerDecisionChannel != null) {
                            if (!validation.valid) {
                                liveWebSocket?.sendToolResponse(
                                    callId = callId,
                                    name = name,
                                    success = false,
                                    message = validation.message
                                )
                            } else {
                                pendingPlannerCallId = callId
                                pendingPlannerCallName = name
                            }
                            plannerDecisionChannel?.send(validation)
                            return@launch
                        }
                        geminiToolCallRouter.route(
                            context = getApplication(),
                            callId = callId,
                            name = name,
                            arguments = arguments
                        ) { response ->
                            liveWebSocket?.sendToolResponse(
                                callId = response.callId,
                                name = response.name,
                                success = response.success,
                                message = response.message
                            )
                        }
                    }
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
                    _currentEmotion.value = emotion
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
                    handledCommandType = null
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
        
        // Dynamic Steering: Inject the full Singing Engine instructions mid-session
        val steeringPrompt = PromptGenerator.generateSystemPrompt(
            personality = preferences.personality,
            userName = preferences.userName,
            isSingingSession = true,
            requestedDurationMs = durationMs
        )
        liveWebSocket?.sendText("INSTRUCTION: Activate the [SINGING ENGINE] defined in my system prompt now. Use the provided lyrics and structure. Begin the performance immediately.\n\n$steeringPrompt")
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

    private fun handleModeSwitchCommand(text: String): Boolean {
        val lower = text.lowercase()
        val newMode = when {
            lower.contains("switch to assistant") || lower.contains("assistant mode") -> GeminiConstants.PERSONALITY_ASSISTANT
            lower.contains("switch to girlfriend") || lower.contains("girlfriend mode") -> GeminiConstants.PERSONALITY_GIRLFRIEND
            lower.contains("switch to personal ai") || lower.contains("personal ai mode") -> GeminiConstants.PERSONALITY_PERSONAL_AI
            else -> null
        }

        if (newMode != null && newMode != preferences.personality) {
            preferences.personality = newMode
            _personalityName.value = newMode
            refreshSession()
            return true
        }
        return false
    }

    private fun refreshSession() {
        if (_isSessionOn.value) {
            viewModelScope.launch {
                _eventFlow.emit("Switching to ${preferences.personality}...")
                _connectionStatus.value = "CONNECTING..."
                
                // Stop existing session gracefully but firmly
                stopSession()
                
                // Allow a small gap for resources to free up
                delay(300)
                
                if (isActive) {
                    startSession()
                }
            }
        }
    }

    private fun persistVoiceCommand(command: com.jarvis.gensoftlab.util.AssistantCommand, text: String) {
        when (command.type) {
            AssistantCommandType.REMINDER -> {
                val title = "রিমাইন্ডার"
                val reminderText = text.replace(Regex("(?i)(reminder|মনে রাখো|মনে রাখবেন|রিমাইন্ডার)"), "").trim()
                if (reminderText.isNotBlank()) {
                    assistantMemoryRepository.addReminder(
                        title = title,
                        message = reminderText,
                        scheduledAt = System.currentTimeMillis() + 60 * 60 * 1000L
                    )
                }
            }
            AssistantCommandType.NOTE -> {
                val noteText = text.replace(Regex("(?i)(note|নোট|নোট রাখো|write note|save note|memory|মেমরি|মেমরি রাখো|remember this|মনে রাখো)"), "").trim()
                if (noteText.isNotBlank()) {
                    assistantMemoryRepository.addNote("নোট", noteText)
                    JarvisApp.instance.memoryVaultRepository.rememberFact("memory_note", noteText)
                }
            }
            AssistantCommandType.TIMER -> {
                val minutes = extractMinutes(text)
                val durationMs = if (minutes > 0) minutes * 60L * 1000L else 5L * 60L * 1000L
                assistantMemoryRepository.addReminder(
                    title = "টাইমার",
                    message = text,
                    scheduledAt = System.currentTimeMillis() + durationMs
                )
            }
            AssistantCommandType.ALARM -> {
                assistantMemoryRepository.addReminder(
                    title = "অ্যালার্ম",
                    message = text,
                    scheduledAt = System.currentTimeMillis() + 60 * 60 * 1000L
                )
            }
            else -> Unit
        }
    }

    private fun isToolCommand(type: AssistantCommandType): Boolean {
        return type == AssistantCommandType.WEB_SEARCH ||
            type == AssistantCommandType.YOUTUBE_SEARCH ||
            type == AssistantCommandType.PHONE_NAVIGATION ||
            type == AssistantCommandType.WEATHER ||
            type == AssistantCommandType.NEWS ||
            type == AssistantCommandType.DEVICE_CONTROL ||
            type == AssistantCommandType.APP_AUTOMATION ||
            type == AssistantCommandType.NOTE
    }

    private fun isActionCommand(type: AssistantCommandType): Boolean {
        return isToolCommand(type) ||
            type == AssistantCommandType.NOTE ||
            type == AssistantCommandType.REMINDER ||
            type == AssistantCommandType.TIMER ||
            type == AssistantCommandType.ALARM
    }

    private fun buildPhoneTaskInstruction(goal: com.jarvis.gensoftlab.util.TaskGoal): String {
        return """
PHONE_TASK_START
Goal objective: ${goal.objective}
Target app: ${goal.targetApp ?: "unknown"}
Constraints: ${goal.constraints.joinToString("; ").ifBlank { "none" }}
Success criteria: ${goal.successCriteria.joinToString("; ").ifBlank { "observe and verify the user's goal" }}

Act as the JARVIS phone-task planner. Use only registered structured tools.
Choose exactly one next action. Observe the current screen before acting, validate the action,
execute it, inspect the result, and re-plan. Never claim completion without evidence.
If the target is ambiguous, ask for clarification. If the user cancels, stop immediately.
PHONE_TASK_END
""".trimIndent()
    }

    private fun buildPhoneTaskPlanningInstruction(
        goal: com.jarvis.gensoftlab.util.TaskGoal,
        context: com.jarvis.gensoftlab.util.TaskContext,
        uiSnapshot: com.jarvis.gensoftlab.accessibility.UiSnapshot?
    ): String {
        val elements = uiSnapshot?.nodes?.take(80)?.joinToString(" | ") { element ->
            listOfNotNull(element.text, element.contentDescription).joinToString("/") +
                "[idless role=${element.role},click=${element.clickable},edit=${element.editable},scroll=${element.scrollable},bounds=${element.bounds.flattenToString()}]"
        }.orEmpty().take(6000)
        return """
PHONE_TASK_NEXT_ACTION
Goal: ${goal.objective}
Target app: ${goal.targetApp ?: "unknown"}
Success criteria: ${goal.successCriteria.joinToString("; ")}
Current app: ${context.currentApp ?: uiSnapshot?.packageName ?: "unknown"}
Current screen fingerprint: ${context.currentScreenFingerprint ?: "none"}
Completed steps: ${context.completedSteps}
Planner calls remaining: ${(20 - context.plannerCalls).coerceAtLeast(0)}
Last action: ${context.lastDecision?.action ?: "none"}
Last result: ${context.lastActionResult?.message ?: "none"}
Visible elements: $elements

Return exactly one structured tool call for the next safe action, or a completion/clarification status.
Observe before acting. Do not use stale elements, coordinates, or unlisted tools. Verify after one action.
PHONE_TASK_NEXT_ACTION_END
""".trimIndent()
    }

    private fun hasCommandPayload(
        command: com.jarvis.gensoftlab.util.AssistantCommand,
        transcript: String
    ): Boolean {
        return when (command.type) {
            AssistantCommandType.WEB_SEARCH -> transcript.replace(
                Regex("(?i)^(search for|search|google|look up|ওয়েবে খুঁজে দেখ|ওয়েবে খুঁজে দেখ|সার্চ কর)"),
                ""
            ).trim().length > 1
            AssistantCommandType.YOUTUBE_SEARCH -> transcript.replace(
                Regex("(?i)(hey jarvis|ok jarvis|jarvis|জারভিস|search youtube for|youtube search|search on youtube|search in youtube|youtube এ সার্চ কর|youtube এ খুঁজে দেখ|ইউটিউবে সার্চ করো?|ইউটিউবে খুঁজে দেখো?)"),
                ""
            ).replace(Regex("(?i)^(for|on|in)\\s+"), "").trim().length > 2
            AssistantCommandType.APP_AUTOMATION -> transcript
                .replace(Regex("(?i)\\b(please|can you|could you|open|launch|start)\\b"), "")
                .replace(Regex("(খোলো|খুলে দাও|চালু কর|দয়া করে|দাও)"), "")
                .trim().length > 2
            AssistantCommandType.NOTE -> transcript.replace(
                Regex("(?i)(note|নোট|নোট রাখো|write note|save note)"),
                ""
            ).trim().isNotBlank()
            AssistantCommandType.REMINDER -> transcript.replace(
                Regex("(?i)(reminder|remember|মনে রাখো|মনে রাখবেন|রিমাইন্ডার|কাজ মনে রাখো)"),
                ""
            ).trim().isNotBlank()
            else -> true
        }
    }

    private fun extractMinutes(text: String): Long {
        val match = Regex("(\\d+)").find(text)
        return match?.value?.toLongOrNull() ?: 0L
    }

    private fun finalizeTurn() {
        pendingCommandJob?.cancel()
        pendingCommandJob = null
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
        handledCommandType = null
        currentTurnEmotion = "NEUTRAL"
        _currentEmotion.value = "NEUTRAL"
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
