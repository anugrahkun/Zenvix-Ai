package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.ContactCandidate
import com.example.core.DeviceActionBridge
import com.example.core.DeviceActionOutcome
import com.example.core.DeviceActionStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.ContextWindowCompressionConfig
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveServerToolCallCancellation
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RealtimeInputConfig
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

enum class LiveVoicePhase {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    EXECUTING_ACTION,
    ERROR
}

data class LiveTranscriptLine(
    val id: String,
    val isUser: Boolean,
    val text: String
)

data class LiveActionEvent(
    val id: String,
    val outcome: DeviceActionOutcome
)

data class LiveVoiceUiState(
    val isVisible: Boolean = false,
    val phase: LiveVoicePhase = LiveVoicePhase.IDLE,
    val isMuted: Boolean = false,
    val isOfflineMode: Boolean = false,
    val offlineSiriOverride: Boolean = false,
    val micLevel: Float = 0f,
    val speakerLevel: Float = 0f,
    val selectedVoice: String = "AOEDE",
    val transcripts: List<LiveTranscriptLine> = emptyList(),
    val actionEvents: List<LiveActionEvent> = emptyList(),
    val pendingContactMatches: List<ContactCandidate> = emptyList(),
    val pendingPermissionRequest: String? = null,
    val lastActionHint: String? = null,
    val statusNotice: String? = null,
    val errorMessage: String? = null
)

@OptIn(PublicPreviewAPI::class)
class ZenvixLiveVoiceManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val actionBridge = DeviceActionBridge(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(LiveVoiceUiState())
    val state: StateFlow<LiveVoiceUiState> = _state.asStateFlow()

    private val audioPlaybackQueue = ConcurrentLinkedQueue<ByteArray>()
    private var liveSession: LiveSession? = null
    private var micJob: Job? = null
    private var receiveJob: Job? = null
    private var playbackJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var audioTrack: AudioTrack? = null
    private var sessionJob: Job? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var offlineTts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false
    @Volatile
    private var pendingOfflineUtterance: String? = null
    @Volatile
    private var offlineRestartPending = false

    private var activeUserTranscriptId: String? = null
    private var activeModelTranscriptId: String? = null
    private var lineCounter = 0
    private var pendingRetryContactQuery: String? = null

    fun openSheet(autoStartIfPermitted: Boolean = true) {
        val useOffline = _state.value.offlineSiriOverride || !actionBridge.isDeviceOnline()
        _state.update {
            it.copy(
                isVisible = true,
                isOfflineMode = useOffline,
                errorMessage = null
            )
        }
        if (autoStartIfPermitted &&
            _state.value.phase == LiveVoicePhase.IDLE &&
            hasPermission(Manifest.permission.RECORD_AUDIO)
        ) {
            startSession()
        }
    }

    fun closeSheet() {
        stopSession()
        _state.update {
            it.copy(
                isVisible = false,
                phase = LiveVoicePhase.IDLE,
                pendingContactMatches = emptyList(),
                pendingPermissionRequest = null,
                errorMessage = null
            )
        }
    }

    fun selectVoice(voiceName: String) {
        val wasActive = _state.value.phase != LiveVoicePhase.IDLE && _state.value.phase != LiveVoicePhase.ERROR
        _state.update { it.copy(selectedVoice = voiceName) }
        if (wasActive && !_state.value.isOfflineMode) {
            startSession()
        }
    }

    fun toggleOfflineSiriMode() {
        val nextOverride = !_state.value.offlineSiriOverride
        val useOffline = nextOverride || !actionBridge.isDeviceOnline()
        stopSession()
        _state.update {
            it.copy(
                offlineSiriOverride = nextOverride,
                isOfflineMode = useOffline,
                errorMessage = null,
                statusNotice = if (useOffline) {
                    "Offline Siri Mode active — control apps, calls, torch, alarm & device settings offline."
                } else {
                    "Switched to Gemini Live Cloud Voice."
                }
            )
        }
        if (_state.value.isVisible && hasPermission(Manifest.permission.RECORD_AUDIO)) {
            startSession()
        }
    }

    fun toggleMute() {
        val nextMuted = !_state.value.isMuted
        _state.update {
            it.copy(
                isMuted = nextMuted,
                micLevel = if (nextMuted) 0f else it.micLevel
            )
        }
        if (_state.value.isOfflineMode) {
            if (nextMuted) {
                stopOfflineSpeechRecognizer()
            } else if (_state.value.isVisible && hasPermission(Manifest.permission.RECORD_AUDIO)) {
                startOfflineSiriListening()
            }
        }
    }

    fun interruptPlayback() {
        flushAudioOutput()
        mainHandler.post {
            runCatching { offlineTts?.stop() }
        }
        if (_state.value.phase == LiveVoicePhase.SPEAKING) {
            _state.update { it.copy(phase = LiveVoicePhase.LISTENING, speakerLevel = 0f) }
        }
    }

    fun submitCommandText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val activeOnlineSession = liveSession
        if (activeOnlineSession != null && !_state.value.isOfflineMode) {
            sendTextTurn(clean)
        } else {
            interruptPlayback()
            activeUserTranscriptId = null
            appendOrUpdateTranscript(isUser = true, chunk = clean, isIncremental = false)
            activeUserTranscriptId = null
            scope.launch {
                handleOfflineSiriInput(clean)
            }
        }
    }

    fun sendTextTurn(message: String) {
        val clean = message.trim()
        val session = liveSession ?: return
        if (clean.isEmpty()) return
        flushAudioOutput()
        appendOrUpdateTranscript(isUser = true, chunk = clean, isIncremental = false)
        activeUserTranscriptId = null
        scope.launch {
            runCatching {
                session.send(clean)
            }.onFailure { err ->
                Log.w(TAG, "Failed sending text turn to LiveSession: ${err.message}")
            }
        }
    }

    fun onDisambiguatedContactSelected(candidate: ContactCandidate) {
        _state.update { it.copy(pendingContactMatches = emptyList()) }
        scope.launch {
            val outcome = withContext(Dispatchers.Main) {
                actionBridge.makeCall(
                    phoneNumber = candidate.phoneNumber,
                    displayLabel = candidate.displayName
                )
            }
            recordOutcome(outcome)
            val session = liveSession
            if (session != null && !_state.value.isOfflineMode) {
                runCatching {
                    session.send(
                        "User tapped to confirm calling ${candidate.displayName} (${candidate.phoneNumber}). " +
                            "Result: ${outcome.message}"
                    )
                }
            } else {
                appendOrUpdateTranscript(isUser = false, chunk = outcome.message, isIncremental = false)
                activeModelTranscriptId = null
                speakOfflineTts(outcome.message)
            }
        }
    }

    fun onPermissionResult(permission: String, granted: Boolean) {
        if (permission == Manifest.permission.READ_CONTACTS) {
            _state.update { it.copy(pendingPermissionRequest = null) }
            val pendingContact = pendingRetryContactQuery
            pendingRetryContactQuery = null
            if (granted && !pendingContact.isNullOrBlank()) {
                scope.launch {
                    val outcome = withContext(Dispatchers.Main) {
                        actionBridge.callContact(pendingContact)
                    }
                    recordOutcome(outcome)
                    val session = liveSession
                    if (session != null && !_state.value.isOfflineMode) {
                        runCatching {
                            session.send("Contacts permission granted. Lookup for $pendingContact: ${outcome.message}")
                        }
                    } else {
                        appendOrUpdateTranscript(isUser = false, chunk = outcome.message, isIncremental = false)
                        activeModelTranscriptId = null
                        speakOfflineTts(outcome.message)
                    }
                }
            } else if (!granted) {
                val deniedMsg = "Contacts permission was denied. Grant Contacts access to search saved names."
                val session = liveSession
                if (session != null && !_state.value.isOfflineMode) {
                    scope.launch {
                        runCatching {
                            session.send("User denied Contacts permission. Let them know you cannot search contacts without it.")
                        }
                    }
                } else {
                    appendOrUpdateTranscript(isUser = false, chunk = deniedMsg, isIncremental = false)
                    activeModelTranscriptId = null
                    speakOfflineTts(deniedMsg)
                }
            }
        } else if (permission == Manifest.permission.CALL_PHONE) {
            _state.update { it.copy(pendingPermissionRequest = null) }
            if (granted) {
                _state.update { it.copy(statusNotice = "Direct calling permission enabled.") }
            }
        }
    }

    fun startSession() {
        val useOffline = _state.value.offlineSiriOverride || !actionBridge.isDeviceOnline()
        if (!useOffline && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            _state.update {
                it.copy(
                    phase = LiveVoicePhase.ERROR,
                    isOfflineMode = false,
                    errorMessage = "Microphone permission is required for Zenvix Live Voice."
                )
            }
            return
        }

        stopSession()
        val chosenVoice = _state.value.selectedVoice

        if (useOffline) {
            _state.update {
                it.copy(
                    phase = if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        LiveVoicePhase.CONNECTING
                    } else {
                        LiveVoicePhase.IDLE
                    },
                    isOfflineMode = true,
                    isMuted = false,
                    micLevel = 0f,
                    speakerLevel = 0f,
                    pendingContactMatches = emptyList(),
                    pendingPermissionRequest = null,
                    errorMessage = null,
                    statusNotice = if (_state.value.offlineSiriOverride) {
                        "Offline Siri Mode ready — voice & quick phone controls active"
                    } else {
                        "No internet detected — running in Offline Siri Mode"
                    }
                )
            }
            ensureOfflineTtsInitialized()
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                startOfflineSiriListening()
            }
            return
        }

        _state.update {
            it.copy(
                phase = LiveVoicePhase.CONNECTING,
                isOfflineMode = false,
                isMuted = false,
                micLevel = 0f,
                speakerLevel = 0f,
                pendingContactMatches = emptyList(),
                pendingPermissionRequest = null,
                errorMessage = null,
                statusNotice = "Connecting Zenvix Live ($LIVE_MODEL_ID)…"
            )
        }

        sessionJob = scope.launch {
            try {
                val model = buildLiveModel(chosenVoice)
                val session = model.connect()
                liveSession = session

                initAudioTrack()
                startPlaybackLoop()
                startReceiveLoop(session)
                startMicStreamingLoop(session)

                _state.update {
                    it.copy(
                        phase = LiveVoicePhase.LISTENING,
                        statusNotice = "Listening automatically across Hindi, English, Hinglish & 70+ languages"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Gemini Live session", e)
                if (!actionBridge.isDeviceOnline()) {
                    _state.update {
                        it.copy(
                            isOfflineMode = true,
                            phase = LiveVoicePhase.CONNECTING,
                            errorMessage = null,
                            statusNotice = "Connection unavailable — switched to Offline Siri Mode"
                        )
                    }
                    ensureOfflineTtsInitialized()
                    if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        startOfflineSiriListening()
                    }
                    return@launch
                }
                val msg = e.localizedMessage ?: "Unable to connect Zenvix Live session."
                val friendly = if (msg.contains("403") || msg.contains("AppCheck", ignoreCase = true)) {
                    "App Check / backend verification error (403). Please retry or switch to Offline Siri."
                } else {
                    msg
                }
                _state.update {
                    it.copy(
                        phase = LiveVoicePhase.ERROR,
                        errorMessage = friendly,
                        statusNotice = null
                    )
                }
                cleanupAudioAndSession()
            }
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        micJob?.cancel()
        micJob = null
        receiveJob?.cancel()
        receiveJob = null
        playbackJob?.cancel()
        playbackJob = null
        offlineRestartPending = false
        cleanupAudioAndSession()
        stopOfflineSpeechRecognizer()
        mainHandler.post {
            runCatching { offlineTts?.stop() }
        }
        _state.update {
            it.copy(
                phase = LiveVoicePhase.IDLE,
                micLevel = 0f,
                speakerLevel = 0f,
                statusNotice = null
            )
        }
    }

    fun release() {
        stopSession()
        mainHandler.post {
            runCatching {
                offlineTts?.stop()
                offlineTts?.shutdown()
            }
            offlineTts = null
            ttsReady = false
        }
    }

    private fun ensureOfflineTtsInitialized() {
        mainHandler.post {
            if (offlineTts != null) return@post
            offlineTts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    runCatching {
                        offlineTts?.language = Locale.getDefault()
                        offlineTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _state.update {
                                    it.copy(phase = LiveVoicePhase.SPEAKING, speakerLevel = 0.65f)
                                }
                            }

                            override fun onDone(utteranceId: String?) {
                                onOfflineSpeechFinished()
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                onOfflineSpeechFinished()
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                onOfflineSpeechFinished()
                            }
                        })
                    }
                    val queued = pendingOfflineUtterance
                    pendingOfflineUtterance = null
                    if (!queued.isNullOrBlank()) {
                        speakOfflineTts(queued)
                    }
                } else {
                    ttsReady = false
                }
            }
        }
    }

    private fun onOfflineSpeechFinished() {
        _state.update { current ->
            val nextPhase = if (current.isVisible && current.isOfflineMode && !current.isMuted &&
                SpeechRecognizer.isRecognitionAvailable(appContext) &&
                hasPermission(Manifest.permission.RECORD_AUDIO)
            ) {
                LiveVoicePhase.LISTENING
            } else {
                LiveVoicePhase.IDLE
            }
            current.copy(phase = nextPhase, speakerLevel = 0f)
        }
        val current = _state.value
        if (current.isVisible && current.isOfflineMode && !current.isMuted &&
            hasPermission(Manifest.permission.RECORD_AUDIO)
        ) {
            scheduleOfflineRecognizerRestart(delayMs = 350L)
        }
    }

    private fun speakOfflineTts(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        ensureOfflineTtsInitialized()
        mainHandler.post {
            val engine = offlineTts
            if (engine != null && ttsReady) {
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "siri_utt_${System.currentTimeMillis()}")
                }
                engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, "siri_utt_${System.currentTimeMillis()}")
            } else {
                pendingOfflineUtterance = clean
            }
        }
    }

    private fun startOfflineSiriListening() {
        mainHandler.post {
            if (!_state.value.isVisible || !_state.value.isOfflineMode || _state.value.isMuted) return@post
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return@post
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.update {
                    it.copy(
                        phase = LiveVoicePhase.IDLE,
                        statusNotice = "On-device voice input is unavailable on this device — type or tap Siri commands below."
                    )
                }
                return@post
            }

            runCatching {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.update {
                            it.copy(
                                phase = LiveVoicePhase.LISTENING,
                                errorMessage = null,
                                statusNotice = "Listening Offline (Siri Mode) — try \"WhatsApp kholo\", \"Torch on\", \"Battery\"…"
                            )
                        }
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) {
                        if (!_state.value.isMuted) {
                            val normalized = (rmsdB.coerceIn(0f, 12f) / 12f)
                            _state.update { it.copy(micLevel = normalized) }
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _state.update { it.copy(micLevel = 0f, phase = LiveVoicePhase.THINKING) }
                    }

                    override fun onError(error: Int) {
                        _state.update { it.copy(micLevel = 0f) }
                        if (!_state.value.isVisible || !_state.value.isOfflineMode || _state.value.isMuted) {
                            return
                        }
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                _state.update {
                                    it.copy(
                                        phase = LiveVoicePhase.LISTENING,
                                        statusNotice = "Listening Offline — speak clearly or type a Siri command below"
                                    )
                                }
                                scheduleOfflineRecognizerRestart(delayMs = 400L)
                            }
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                _state.update {
                                    it.copy(
                                        phase = LiveVoicePhase.IDLE,
                                        statusNotice = "Offline speech recognizer busy/unavailable — use Siri quick bar below."
                                    )
                                }
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                _state.update {
                                    it.copy(
                                        phase = LiveVoicePhase.ERROR,
                                        errorMessage = "Microphone permission required for offline voice."
                                    )
                                }
                            }
                            else -> {
                                _state.update {
                                    it.copy(
                                        phase = LiveVoicePhase.IDLE,
                                        statusNotice = "Offline voice paused (code $error) — tap Start Voice or use the Siri command bar."
                                    )
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()?.trim().orEmpty()
                        _state.update { it.copy(micLevel = 0f) }
                        if (spoken.isBlank()) {
                            activeUserTranscriptId = null
                            if (_state.value.isVisible && _state.value.isOfflineMode && !_state.value.isMuted) {
                                scheduleOfflineRecognizerRestart(delayMs = 350L)
                            }
                            return
                        }
                        replaceCurrentUserTranscript(spoken)
                        activeUserTranscriptId = null
                        scope.launch {
                            handleOfflineSiriInput(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotEmpty()) {
                            replaceCurrentUserTranscript(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                recognizer.startListening(intent)
            }.onFailure { err ->
                Log.w(TAG, "Offline SpeechRecognizer failed to start", err)
                _state.update {
                    it.copy(
                        phase = LiveVoicePhase.IDLE,
                        statusNotice = "Offline voice unavailable — use the Siri quick bar below."
                    )
                }
            }
        }
    }

    private fun scheduleOfflineRecognizerRestart(delayMs: Long) {
        if (offlineRestartPending) return
        offlineRestartPending = true
        mainHandler.postDelayed({
            offlineRestartPending = false
            val s = _state.value
            if (s.isVisible && s.isOfflineMode && !s.isMuted &&
                s.phase != LiveVoicePhase.SPEAKING &&
                s.phase != LiveVoicePhase.EXECUTING_ACTION
            ) {
                startOfflineSiriListening()
            }
        }, delayMs)
    }

    private fun stopOfflineSpeechRecognizer() {
        offlineRestartPending = false
        mainHandler.post {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = null
        }
    }

    private suspend fun handleOfflineSiriInput(rawText: String) {
        _state.update { it.copy(phase = LiveVoicePhase.EXECUTING_ACTION) }
        val outcome = withContext(Dispatchers.Main) {
            actionBridge.executeOfflineSiriCommand(rawText)
        }
        if (outcome != null) {
            if (outcome.status == DeviceActionStatus.PERMISSION_REQUIRED &&
                outcome.missingPermission == Manifest.permission.READ_CONTACTS
            ) {
                pendingRetryContactQuery = outcome.targetLabel
            }
            recordOutcome(outcome)
            appendOrUpdateTranscript(isUser = false, chunk = outcome.message, isIncremental = false)
            activeModelTranscriptId = null
            speakOfflineTts(outcome.message)
        } else {
            val guidance = "You are in Offline Siri mode. Phone controls like WhatsApp, calls, " +
                "flashlight, volume, Wi-Fi/Bluetooth panels, alarm, timer, and battery work offline, " +
                "while general AI answers need an internet connection."
            appendOrUpdateTranscript(isUser = false, chunk = guidance, isIncremental = false)
            activeModelTranscriptId = null
            speakOfflineTts(guidance)
        }
    }

    private fun replaceCurrentUserTranscript(text: String) {
        if (text.isBlank()) return
        _state.update { current ->
            val updated = current.transcripts.toMutableList()
            val idx = activeUserTranscriptId?.let { target ->
                updated.indexOfFirst { it.id == target }
            } ?: -1
            if (idx >= 0) {
                updated[idx] = updated[idx].copy(text = text)
            } else {
                val newId = "tr_u_${++lineCounter}"
                activeUserTranscriptId = newId
                activeModelTranscriptId = null
                updated.add(LiveTranscriptLine(id = newId, isUser = true, text = text))
            }
            current.copy(transcripts = updated.takeLast(24))
        }
    }

    private fun cleanupAudioAndSession() {
        audioPlaybackQueue.clear()
        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching {
            audioRecord?.stop()
        }
        runCatching {
            audioRecord?.release()
        }
        audioRecord = null
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        }
        runCatching {
            audioTrack?.release()
        }
        audioTrack = null

        val sessionToClose = liveSession
        liveSession = null
        if (sessionToClose != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { sessionToClose.stopReceiving() }
                runCatching { sessionToClose.close() }
            }
        }
    }

    private fun buildLiveModel(voiceName: String) = Firebase.ai.liveModel(
        modelName = LIVE_MODEL_ID,
        generationConfig = liveGenerationConfig {
            responseModality = ResponseModality.AUDIO
            speechConfig = SpeechConfig(voice = Voice(voiceName))
            inputAudioTranscription = AudioTranscriptionConfig()
            outputAudioTranscription = AudioTranscriptionConfig()
            contextWindowCompression = ContextWindowCompressionConfig()
            realtimeInputConfig = RealtimeInputConfig.builder()
                .setActivityHandling(RealtimeInputConfig.ActivityHandling.INTERRUPT)
                .build()
        },
        tools = listOf(buildActionTool()),
        systemInstruction = content {
            text(
                """
                You are Zenvix AI (Zenvix Voice Assistant), a warm, intelligent, natural conversational partner created by Anugrah Das.
                Creator identity rule: Whenever anyone asks who created, built, or made you or Zenvix AI (in Hindi, Hinglish, English, or any language), ALWAYS clearly answer that Anugrah Das created Zenvix AI.
                Keep responses crisp, friendly, and conversational for real-time voice listening.

                CRITICAL MULTI-LANGUAGE RULES:
                1. Automatically detect whichever language the user speaks in each turn — including Hindi, English, Hinglish (natural Hindi + English mix), Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and any other supported language.
                2. Respond in Hindi when the user speaks Hindi. Respond in English when the user speaks English. Respond naturally in Hinglish when the user speaks Hinglish.
                3. If the user switches languages mid-conversation (e.g., "Hindi mein baat karo" -> Hindi, "Talk to me in English" -> English, "Hinglish mein baat karo" -> Hinglish), switch immediately without asking for manual language settings.

                CRITICAL DEVICE ACTION & TOOL CALLING RULES:
                1. You have 12 device control tools like Siri: openWhatsApp, openApp, openUrl, makeCall, callContact, setFlashlight, openSystemPanel, adjustVolume, setAlarm, setTimer, getBatteryStatus, getCurrentDateTime.
                2. NEVER verbally pretend or claim that you opened an app, toggled the flashlight, changed volume, set an alarm/timer, or placed/started a call unless you FIRST invoked the matching tool and inspected its returned JSON result!
                3. Map natural variations automatically:
                   - "WhatsApp kholo", "Open WhatsApp", "WhatsApp open karo", "WhatsApp chalao" -> call openWhatsApp().
                   - "Open YouTube", "Instagram kholo", "Open Chrome", "Open settings" -> call openApp(appName).
                   - "Call 9876543210", "9876543210 pe call lagao" -> call makeCall(phoneNumber).
                   - "Call Mom", "Mummy ko call karo", "Mom ko phone lagao", "Rahul ko call karo" -> call callContact(contactName).
                   - "Torch on karo", "Flashlight band karo" -> call setFlashlight(enabled).
                   - "Wifi settings kholo", "Bluetooth open karo", "Internet panel" -> call openSystemPanel(panelType).
                   - "Volume up", "Awaz kam karo", "Phone mute karo" -> call adjustVolume(mode).
                   - "7 baje ka alarm lagao" -> call setAlarm(hour, minute, label).
                   - "5 minute ka timer lagao" -> call setTimer(seconds, label).
                   - "Battery kitni hai", "Time kya hua hai", "Aaj ki tarikh kya hai" -> call getBatteryStatus() / getCurrentDateTime(wantDate).
                   - Opening a website/URL -> call openUrl(url).
                4. Inspect tool response truthfully:
                   - If status is "success" and executed is true -> confirm naturally in the user's current language.
                   - If status is "opened_dialer_prefilled" -> honestly explain that the phone dialer has been opened pre-filled with the number/contact (never claim direct calling started if only the dialer opened).
                   - If status is "ambiguous" -> read out the matched candidate contact names clearly and ask which person they want to call.
                   - If status is "not_found" -> clearly tell them the contact wasn't found and NEVER guess a phone number.
                   - If status is "not_installed" or "permission_required" -> politely explain what app is missing or what permission needs to be granted on screen.
                """.trimIndent()
            )
        }
    )

    private fun buildActionTool(): Tool {
        val openWhatsAppDecl = FunctionDeclaration(
            name = "openWhatsApp",
            description = "Opens WhatsApp on the user's Android device.",
            parameters = emptyMap()
        )
        val openAppDecl = FunctionDeclaration(
            name = "openApp",
            description = "Opens an installed Android app or device screen by name, such as YouTube, Instagram, Chrome, Settings, Camera, Maps, Gmail, or WhatsApp.",
            parameters = mapOf(
                "appName" to Schema.string("Human name of the app to open, e.g. 'YouTube', 'Instagram', 'Chrome', or 'Settings'.")
            )
        )
        val openUrlDecl = FunctionDeclaration(
            name = "openUrl",
            description = "Opens a safe http or https web URL in the Android browser.",
            parameters = mapOf(
                "url" to Schema.string("Full http or https URL to open, e.g. 'https://youtube.com'.")
            )
        )
        val makeCallDecl = FunctionDeclaration(
            name = "makeCall",
            description = "Calls or opens the dialer for a specific numeric phone number supplied by the user.",
            parameters = mapOf(
                "phoneNumber" to Schema.string("Numeric phone number with optional leading + country code.")
            )
        )
        val callContactDecl = FunctionDeclaration(
            name = "callContact",
            description = "Looks up a contact by name (such as Mom, Mummy, Dad, Rahul) in device contacts and initiates a call or reports multiple/no matches.",
            parameters = mapOf(
                "contactName" to Schema.string("Name or relationship label of the contact to search and call.")
            )
        )
        val setFlashlightDecl = FunctionDeclaration(
            name = "setFlashlight",
            description = "Turns the device camera flashlight (torch) ON or OFF.",
            parameters = mapOf(
                "enabled" to Schema.boolean("True to turn flashlight ON, false to turn flashlight OFF.")
            )
        )
        val openSystemPanelDecl = FunctionDeclaration(
            name = "openSystemPanel",
            description = "Opens quick Android system settings panel for wifi, internet, bluetooth, sound, display, battery, or location.",
            parameters = mapOf(
                "panelType" to Schema.string("One of: wifi, internet, bluetooth, sound, display, battery, location.")
            )
        )
        val adjustVolumeDecl = FunctionDeclaration(
            name = "adjustVolume",
            description = "Adjusts media volume on the phone.",
            parameters = mapOf(
                "mode" to Schema.string("One of: up, down, mute, unmute, max.")
            )
        )
        val setAlarmDecl = FunctionDeclaration(
            name = "setAlarm",
            description = "Opens the device Clock app to create an alarm for a specific 24-hour time.",
            parameters = mapOf(
                "hour" to Schema.integer("Hour in 24-hour format (0-23)."),
                "minute" to Schema.integer("Minute (0-59)."),
                "label" to Schema.string("Optional alarm title.")
            )
        )
        val setTimerDecl = FunctionDeclaration(
            name = "setTimer",
            description = "Starts a countdown timer in the device Clock app.",
            parameters = mapOf(
                "seconds" to Schema.integer("Duration in seconds."),
                "label" to Schema.string("Optional timer label.")
            )
        )
        val getBatteryStatusDecl = FunctionDeclaration(
            name = "getBatteryStatus",
            description = "Reads the device's real current battery percentage and charging state.",
            parameters = emptyMap()
        )
        val getCurrentDateTimeDecl = FunctionDeclaration(
            name = "getCurrentDateTime",
            description = "Reads the device's real current local time or today's date.",
            parameters = mapOf(
                "wantDate" to Schema.boolean("True to return today's date, false to return the current time.")
            )
        )
        return Tool.functionDeclarations(
            listOf(
                openWhatsAppDecl,
                openAppDecl,
                openUrlDecl,
                makeCallDecl,
                callContactDecl,
                setFlashlightDecl,
                openSystemPanelDecl,
                adjustVolumeDecl,
                setAlarmDecl,
                setTimerDecl,
                getBatteryStatusDecl,
                getCurrentDateTimeDecl
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun startMicStreamingLoop(session: LiveSession) {
        val minBuf = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        audioRecord = recorder

        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
            }
        }

        recorder.startRecording()

        micJob = scope.launch {
            val readBuffer = ByteArray(1600) // ~50ms chunks at 16kHz 16-bit mono
            while (isActive) {
                val readBytes = recorder.read(readBuffer, 0, readBuffer.size)
                if (readBytes > 0) {
                    if (!_state.value.isMuted) {
                        val rms = computeNormalizedPcmRms(readBuffer, readBytes)
                        _state.update { it.copy(micLevel = rms) }
                        val chunkCopy = readBuffer.copyOf(readBytes)
                        runCatching {
                            session.sendAudioRealtime(InlineData(chunkCopy, "audio/pcm;rate=$INPUT_SAMPLE_RATE"))
                        }.onFailure { err ->
                            Log.w(TAG, "sendAudioRealtime failed: ${err.message}")
                        }
                    } else {
                        _state.update { it.copy(micLevel = 0f) }
                    }
                } else {
                    delay(15L)
                }
            }
        }
    }

    private fun initAudioTrack() {
        val minBuf = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 3)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()
        audioTrack = track
    }

    private fun startPlaybackLoop() {
        playbackJob = scope.launch {
            while (isActive) {
                val chunk = audioPlaybackQueue.poll()
                if (chunk != null) {
                    val track = audioTrack
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        val rms = computeNormalizedPcmRms(chunk, chunk.size)
                        _state.update {
                            if (it.phase == LiveVoicePhase.LISTENING || it.phase == LiveVoicePhase.THINKING || it.phase == LiveVoicePhase.SPEAKING) {
                                it.copy(phase = LiveVoicePhase.SPEAKING, speakerLevel = rms)
                            } else {
                                it.copy(speakerLevel = rms)
                            }
                        }
                        track.write(chunk, 0, chunk.size)
                    }
                } else {
                    if (_state.value.speakerLevel > 0f) {
                        _state.update { it.copy(speakerLevel = 0f) }
                    }
                    delay(12L)
                }
            }
        }
    }

    private fun flushAudioOutput() {
        audioPlaybackQueue.clear()
        val track = audioTrack ?: return
        runCatching {
            track.pause()
            track.flush()
            track.play()
        }
    }

    private fun startReceiveLoop(session: LiveSession) {
        receiveJob = scope.launch {
            try {
                session.receive().collect { message ->
                    when (message) {
                        is LiveServerContent -> handleServerContent(message)
                        is LiveServerToolCall -> handleToolCall(session, message)
                        is LiveServerToolCallCancellation -> {
                            Log.d(TAG, "Cancelled tool calls: ${message.functionIds}")
                        }
                        is LiveServerGoAway -> {
                            _state.update {
                                it.copy(statusNotice = "Session limit approaching — restart voice anytime.")
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live receive stream error", e)
                _state.update {
                    it.copy(
                        phase = LiveVoicePhase.ERROR,
                        errorMessage = e.localizedMessage ?: "Voice connection interrupted.",
                        statusNotice = null
                    )
                }
            }
        }
    }

    private fun handleServerContent(contentMsg: LiveServerContent) {
        if (contentMsg.interrupted) {
            flushAudioOutput()
            activeModelTranscriptId = null
            _state.update {
                it.copy(
                    phase = LiveVoicePhase.LISTENING,
                    speakerLevel = 0f
                )
            }
        }

        val inputTranscript = contentMsg.inputTranscription?.text?.trim()
        if (!inputTranscript.isNullOrEmpty()) {
            appendOrUpdateTranscript(isUser = true, chunk = inputTranscript, isIncremental = true)
        }

        val outputTranscript = contentMsg.outputTranscription?.text?.trim()
        if (!outputTranscript.isNullOrEmpty()) {
            appendOrUpdateTranscript(isUser = false, chunk = outputTranscript, isIncremental = true)
        }

        contentMsg.content?.parts?.forEach { part ->
            if (part is InlineDataPart && part.mimeType.startsWith("audio/")) {
                audioPlaybackQueue.add(part.inlineData)
            }
        }

        if (contentMsg.turnComplete || contentMsg.generationComplete) {
            activeUserTranscriptId = null
            activeModelTranscriptId = null
            if (audioPlaybackQueue.isEmpty()) {
                _state.update { it.copy(phase = LiveVoicePhase.LISTENING, speakerLevel = 0f) }
            }
        }
    }

    private suspend fun handleToolCall(session: LiveSession, toolCall: LiveServerToolCall) {
        _state.update { it.copy(phase = LiveVoicePhase.EXECUTING_ACTION) }
        val responses = mutableListOf<FunctionResponsePart>()
        for (call in toolCall.functionCalls) {
            val outcome = executeFunctionCall(call)
            recordOutcome(outcome)
            responses.add(
                FunctionResponsePart(
                    name = call.name,
                    response = outcome.toSerializableJsonObject(),
                    id = call.id
                )
            )
        }
        runCatching {
            session.sendFunctionResponse(responses)
        }.onFailure { err ->
            Log.e(TAG, "Failed sending function response", err)
        }
        _state.update {
            if (it.phase == LiveVoicePhase.EXECUTING_ACTION) {
                it.copy(phase = LiveVoicePhase.THINKING)
            } else {
                it
            }
        }
    }

    private suspend fun executeFunctionCall(call: FunctionCallPart): DeviceActionOutcome {
        return when (call.name) {
            "openWhatsApp" -> withContext(Dispatchers.Main) {
                actionBridge.openWhatsApp()
            }
            "openApp" -> {
                val appName = call.args["appName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                withContext(Dispatchers.Main) {
                    actionBridge.openApp(appName)
                }
            }
            "openUrl" -> {
                val url = call.args["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                withContext(Dispatchers.Main) {
                    actionBridge.openUrl(url)
                }
            }
            "makeCall" -> {
                val phone = call.args["phoneNumber"]?.jsonPrimitive?.contentOrNull.orEmpty()
                withContext(Dispatchers.Main) {
                    actionBridge.makeCall(phone)
                }
            }
            "callContact" -> {
                val name = call.args["contactName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val outcome = withContext(Dispatchers.Main) {
                    actionBridge.callContact(name)
                }
                if (outcome.status == DeviceActionStatus.PERMISSION_REQUIRED &&
                    outcome.missingPermission == Manifest.permission.READ_CONTACTS
                ) {
                    pendingRetryContactQuery = name
                }
                outcome
            }
            "setFlashlight" -> {
                val enabled = call.args["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                withContext(Dispatchers.Main) {
                    actionBridge.setFlashlight(enabled)
                }
            }
            "openSystemPanel" -> {
                val panelType = call.args["panelType"]?.jsonPrimitive?.contentOrNull ?: "wifi"
                withContext(Dispatchers.Main) {
                    actionBridge.openSystemPanel(panelType)
                }
            }
            "adjustVolume" -> {
                val mode = call.args["mode"]?.jsonPrimitive?.contentOrNull ?: "up"
                withContext(Dispatchers.Main) {
                    actionBridge.adjustVolume(mode)
                }
            }
            "setAlarm" -> {
                val hour = call.args["hour"]?.jsonPrimitive?.intOrNull ?: 7
                val minute = call.args["minute"]?.jsonPrimitive?.intOrNull ?: 0
                val label = call.args["label"]?.jsonPrimitive?.contentOrNull
                withContext(Dispatchers.Main) {
                    actionBridge.setAlarm(hour, minute, label)
                }
            }
            "setTimer" -> {
                val seconds = call.args["seconds"]?.jsonPrimitive?.intOrNull ?: 60
                val label = call.args["label"]?.jsonPrimitive?.contentOrNull
                withContext(Dispatchers.Main) {
                    actionBridge.setTimer(seconds, label)
                }
            }
            "getBatteryStatus" -> withContext(Dispatchers.Main) {
                actionBridge.getBatteryStatus()
            }
            "getCurrentDateTime" -> {
                val wantDate = call.args["wantDate"]?.jsonPrimitive?.booleanOrNull ?: false
                withContext(Dispatchers.Main) {
                    actionBridge.getCurrentDateTime(wantDate)
                }
            }
            else -> DeviceActionOutcome(
                actionName = call.name,
                executed = false,
                status = DeviceActionStatus.FAILED,
                message = "Unknown or disallowed function: ${call.name}"
            )
        }
    }

    private fun recordOutcome(outcome: DeviceActionOutcome) {
        val eventId = "act_${System.currentTimeMillis()}_${++lineCounter}"
        _state.update { current ->
            val updatedEvents = (current.actionEvents + LiveActionEvent(eventId, outcome)).takeLast(12)
            current.copy(
                actionEvents = updatedEvents,
                pendingContactMatches = if (outcome.status == DeviceActionStatus.AMBIGUOUS) {
                    outcome.candidates
                } else {
                    emptyList()
                },
                pendingPermissionRequest = outcome.missingPermission,
                lastActionHint = outcome.message
            )
        }
    }

    private fun appendOrUpdateTranscript(isUser: Boolean, chunk: String, isIncremental: Boolean) {
        if (chunk.isBlank()) return
        _state.update { current ->
            val targetId = if (isUser) activeUserTranscriptId else activeModelTranscriptId
            val existingIdx = if (targetId != null) {
                current.transcripts.indexOfFirst { it.id == targetId }
            } else {
                -1
            }
            val updatedList = current.transcripts.toMutableList()
            if (isIncremental && existingIdx >= 0) {
                val prev = updatedList[existingIdx]
                val joined = if (prev.text.isBlank()) chunk else "${prev.text} $chunk"
                updatedList[existingIdx] = prev.copy(text = joined)
            } else {
                val newId = "tr_${if (isUser) "u" else "m"}_${++lineCounter}"
                if (isUser) {
                    activeUserTranscriptId = newId
                    activeModelTranscriptId = null
                } else {
                    activeModelTranscriptId = newId
                    activeUserTranscriptId = null
                }
                updatedList.add(LiveTranscriptLine(id = newId, isUser = isUser, text = chunk))
            }
            current.copy(transcripts = updatedList.takeLast(24))
        }
    }

    private fun DeviceActionOutcome.toSerializableJsonObject(): JsonObject {
        val map = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "action" to JsonPrimitive(actionName),
            "executed" to JsonPrimitive(executed),
            "status" to JsonPrimitive(status.name.lowercase()),
            "message" to JsonPrimitive(message)
        )
        if (!targetLabel.isNullOrBlank()) {
            map["target"] = JsonPrimitive(targetLabel)
        }
        if (!missingPermission.isNullOrBlank()) {
            map["missingPermission"] = JsonPrimitive(missingPermission)
        }
        if (candidates.isNotEmpty()) {
            map["candidates"] = JsonArray(
                candidates.map { c ->
                    JsonObject(
                        mapOf(
                            "name" to JsonPrimitive(c.displayName),
                            "phoneNumber" to JsonPrimitive(c.phoneNumber)
                        )
                    )
                }
            )
        }
        return JsonObject(map)
    }

    private fun computeNormalizedPcmRms(bytes: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSq = 0.0
        val sampleCount = length / 2
        var idx = 0
        while (idx + 1 < length) {
            val lo = bytes[idx].toInt() and 0xFF
            val hi = bytes[idx + 1].toInt()
            val sample = (hi shl 8) or lo
            val norm = sample / 32768.0
            sumSq += norm * norm
            idx += 2
        }
        val rms = sqrt(sumSq / sampleCount.coerceAtLeast(1)).toFloat()
        return (rms * 4.5f).coerceIn(0f, 1f)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "ZenvixLiveVoice"
        const val LIVE_MODEL_ID = "gemini-3.1-flash-live-preview"
        const val INPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_SAMPLE_RATE = 24_000
    }
}
