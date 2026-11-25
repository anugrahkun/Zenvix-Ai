package com.example.viewmodel

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.core.ApiKeyVault
import com.example.core.ContactCandidate
import com.example.core.DeviceActionBridge
import com.example.data.LiveVoiceUiState
import com.example.data.ZenvixAiService
import com.example.data.ZenvixFirestoreRepository
import com.example.data.ZenvixLiveVoiceManager
import com.example.model.AiProject
import com.example.model.AuthEvent
import com.example.model.AuthEventType
import com.example.model.BugCategory
import com.example.model.BugReport
import com.example.model.BugStatus
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.CustomApiSettings
import com.example.model.ExtractedArtifact
import com.example.model.PendingAttachment
import com.example.model.SavedArtifact
import com.example.model.SessionStatus
import com.example.model.StylePreset
import com.example.model.UserApp
import com.example.model.UserProfile
import com.example.model.ZenvixModelOption
import com.example.model.ZenvixModelSpec
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class StreamingMessageState(
    val isGenerating: Boolean = false,
    val thinkingText: String = "",
    val visibleText: String = "",
    val artifact: ExtractedArtifact? = null,
    val modelId: String = ZenvixModelOption.SONNET_FLASH.modelId
)

data class ApiConnectionTestState(
    val isTesting: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false
)

data class AppBuilderState(
    val isBuilding: Boolean = false,
    val statusText: String = "",
    val streamingChars: Int = 0,
    val error: String? = null
)

data class WorkspaceUiControls(
    val activeChatId: String? = null,
    val activeProjectId: String? = null,
    val selectedModel: ZenvixModelSpec = ZenvixModelOption.SONNET_FLASH.toSpec(),
    val selectedStyle: StylePreset = StylePreset.BALANCED,
    val thinkingEnabled: Boolean = false,
    val searchQuery: String = "",
    val pendingAttachment: PendingAttachment? = null,
    val openedArtifact: ExtractedArtifact? = null,
    val showApiSettings: Boolean = false,
    val aiError: String? = null,
    val statusBanner: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModel(
    private val repository: ZenvixFirestoreRepository,
    private val aiService: ZenvixAiService,
    private val apiKeyVault: ApiKeyVault,
    private val currentUserId: String,
    private val currentUserName: String = "Zenvix User",
    private val currentUserEmail: String = "",
    private val currentUserPhotoUrl: String = "",
    val isOwnerAdmin: Boolean = false,
    private val liveVoiceManager: ZenvixLiveVoiceManager? = null,
    private val deviceActionBridge: DeviceActionBridge? = null
) : ViewModel() {

    val liveVoiceState: StateFlow<LiveVoiceUiState> =
        liveVoiceManager?.state ?: MutableStateFlow(LiveVoiceUiState()).asStateFlow()

    private val _apiSettings = MutableStateFlow(apiKeyVault.loadSettings())
    val apiSettings: StateFlow<CustomApiSettings> = _apiSettings.asStateFlow()

    private val _apiTestState = MutableStateFlow(ApiConnectionTestState())
    val apiTestState: StateFlow<ApiConnectionTestState> = _apiTestState.asStateFlow()

    private val _controls = MutableStateFlow(WorkspaceUiControls())
    val controls: StateFlow<WorkspaceUiControls> = _controls.asStateFlow()

    private val _streaming = MutableStateFlow(StreamingMessageState())
    val streaming: StateFlow<StreamingMessageState> = _streaming.asStateFlow()

    private var activeGenerationJob: Job? = null

    val chatsState: StateFlow<UiState<List<ChatThread>>> = repository.observeChats(currentUserId)
        .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chats", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load cloud conversations."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val projectsState: StateFlow<UiState<List<AiProject>>> = repository.observeProjects(currentUserId)
        .map<List<AiProject>, UiState<List<AiProject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing projects", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load projects."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _appBuilderState = MutableStateFlow(AppBuilderState())
    val appBuilderState: StateFlow<AppBuilderState> = _appBuilderState.asStateFlow()

    private var activeAppBuildJob: Job? = null

    val userAppsState: StateFlow<UiState<List<UserApp>>> = repository.observeUserApps(currentUserId)
        .map<List<UserApp>, UiState<List<UserApp>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user apps", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load your apps."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val artifactsState: StateFlow<UiState<List<SavedArtifact>>> = repository.observeArtifacts(currentUserId)
        .map<List<SavedArtifact>, UiState<List<SavedArtifact>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing artifacts", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load saved artifacts."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _isSubmittingBug = MutableStateFlow(false)
    val isSubmittingBug: StateFlow<Boolean> = _isSubmittingBug.asStateFlow()

    val myBugReportsState: StateFlow<UiState<List<BugReport>>> = repository.observeMyBugReports(currentUserId)
        .map<List<BugReport>, UiState<List<BugReport>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing my bug reports", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load your submitted bug reports."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val ownerBugInboxState: StateFlow<UiState<List<BugReport>>> = if (isOwnerAdmin) {
        repository.observeAllBugReportsForOwner()
            .map<List<BugReport>, UiState<List<BugReport>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing owner bug inbox", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load owner bug inbox."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )
    } else {
        MutableStateFlow(UiState.Success(emptyList()))
    }

    val allUsersForOwnerState: StateFlow<UiState<List<UserProfile>>> = if (isOwnerAdmin) {
        repository.observeAllUserProfilesForOwner()
            .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing user profiles", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load user directory."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )
    } else {
        MutableStateFlow(UiState.Success(emptyList()))
    }

    val authEventsForOwnerState: StateFlow<UiState<List<AuthEvent>>> = if (isOwnerAdmin) {
        repository.observeAuthEventsForOwner()
            .map<List<AuthEvent>, UiState<List<AuthEvent>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing auth events", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load login/logout activity."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )
    } else {
        MutableStateFlow(UiState.Success(emptyList()))
    }

    private val _selectedOwnerUserId = MutableStateFlow<String?>(null)
    val selectedOwnerUserId: StateFlow<String?> = _selectedOwnerUserId.asStateFlow()

    private val _selectedOwnerChatId = MutableStateFlow<String?>(null)
    val selectedOwnerChatId: StateFlow<String?> = _selectedOwnerChatId.asStateFlow()

    val selectedOwnerUserAppsState: StateFlow<UiState<List<UserApp>>> = _selectedOwnerUserId
        .flatMapLatest { targetUid ->
            if (!isOwnerAdmin || targetUid.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeUserApps(targetUid)
                    .map<List<UserApp>, UiState<List<UserApp>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error(firestoreError?.message ?: error.message ?: "Failed to load user apps."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    val selectedOwnerUserChatsState: StateFlow<UiState<List<ChatThread>>> = _selectedOwnerUserId
        .flatMapLatest { targetUid ->
            if (!isOwnerAdmin || targetUid.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeChats(targetUid)
                    .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error(firestoreError?.message ?: error.message ?: "Failed to load user chats."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    val selectedOwnerUserProjectsState: StateFlow<UiState<List<AiProject>>> = _selectedOwnerUserId
        .flatMapLatest { targetUid ->
            if (!isOwnerAdmin || targetUid.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeProjects(targetUid)
                    .map<List<AiProject>, UiState<List<AiProject>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error(firestoreError?.message ?: error.message ?: "Failed to load user projects."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    val selectedOwnerUserMessagesState: StateFlow<UiState<List<ChatMessage>>> = combine(
        _selectedOwnerUserId,
        _selectedOwnerChatId
    ) { targetUid, targetChatId -> targetUid to targetChatId }
        .flatMapLatest { (targetUid, targetChatId) ->
            if (!isOwnerAdmin || targetUid.isNullOrBlank() || targetChatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(targetUid, targetChatId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error(firestoreError?.message ?: error.message ?: "Failed to load chat messages."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    @Volatile
    private var isSigningOut: Boolean = false

    private fun compactDeviceSummary(): String =
        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}"
            .take(240)

    init {
        viewModelScope.launch {
            repository.recordAuthEvent(
                userId = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail,
                eventType = AuthEventType.LOGIN,
                deviceInfo = compactDeviceSummary()
            )
        }
        viewModelScope.launch {
            combine(
                chatsState,
                userAppsState,
                _controls.map { it.selectedModel.id }.distinctUntilChanged()
            ) { chats, apps, modelId ->
                val chatCount = (chats as? UiState.Success)?.data?.size?.toLong() ?: 0L
                val appCount = (apps as? UiState.Success)?.data?.size?.toLong() ?: 0L
                val loaded = chats is UiState.Success && apps is UiState.Success
                Triple(loaded, modelId, chatCount to appCount)
            }
                .distinctUntilChanged()
                .collect { (loaded, modelId, counts) ->
                    if (loaded && !isSigningOut) {
                        repository.upsertUserProfile(
                            userId = currentUserId,
                            displayName = currentUserName,
                            email = currentUserEmail,
                            photoUrl = currentUserPhotoUrl,
                            lastModelId = modelId,
                            chatCount = counts.first,
                            appCount = counts.second,
                            sessionStatus = SessionStatus.SIGNED_IN
                        )
                    }
                }
        }
    }

    suspend fun recordSignOutBeforeAuthClear() {
        isSigningOut = true
        withTimeoutOrNull(2500L) {
            val chatCount = (chatsState.value as? UiState.Success)?.data?.size?.toLong() ?: 0L
            val appCount = (userAppsState.value as? UiState.Success)?.data?.size?.toLong() ?: 0L
            val modelId = _controls.value.selectedModel.id
            repository.upsertUserProfile(
                userId = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail,
                photoUrl = currentUserPhotoUrl,
                lastModelId = modelId,
                chatCount = chatCount,
                appCount = appCount,
                sessionStatus = SessionStatus.SIGNED_OUT
            )
            repository.recordAuthEvent(
                userId = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail,
                eventType = AuthEventType.LOGOUT,
                deviceInfo = compactDeviceSummary()
            )
        }
    }

    fun deleteAuthEventAsOwner(eventId: String) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            repository.deleteAuthEventForOwner(eventId).onSuccess {
                _controls.update { it.copy(statusBanner = "Activity entry removed") }
            }.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to remove activity entry.") }
            }
        }
    }

    fun selectOwnerUserForInspection(userId: String?) {
        _selectedOwnerUserId.value = userId
        _selectedOwnerChatId.value = null
    }

    fun selectOwnerChatForInspection(chatId: String?) {
        _selectedOwnerChatId.value = chatId
    }

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _controls
        .map { it.activeChatId }
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf<UiState<List<ChatMessage>>>(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, chatId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to load messages."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    fun startNewChat(project: AiProject? = null) {
        activeGenerationJob?.cancel()
        _streaming.value = StreamingMessageState()
        _controls.update {
            it.copy(
                activeChatId = null,
                activeProjectId = project?.id,
                selectedStyle = project?.resolvedStyle() ?: it.selectedStyle,
                pendingAttachment = null,
                aiError = null
            )
        }
    }

    fun selectChat(chat: ChatThread) {
        activeGenerationJob?.cancel()
        _streaming.value = StreamingMessageState()
        val resolved = ZenvixModelOption.resolveSpec(
            modelId = chat.modelId,
            externalModels = _apiSettings.value.customModels
        )
        _controls.update {
            it.copy(
                activeChatId = chat.id,
                activeProjectId = chat.projectId,
                selectedModel = resolved,
                selectedStyle = chat.resolvedStyle(),
                thinkingEnabled = chat.thinkingEnabled,
                pendingAttachment = null,
                aiError = null
            )
        }
    }

    fun setModel(spec: ZenvixModelSpec) {
        if (spec.isExternalApi && !_apiSettings.value.hasConfiguredKey()) {
            _controls.update {
                it.copy(
                    selectedModel = spec,
                    showApiSettings = true,
                    statusBanner = "Add your API key to use ${spec.displayName}"
                )
            }
        } else {
            _controls.update { it.copy(selectedModel = spec, aiError = null) }
        }
        val chatId = _controls.value.activeChatId ?: return
        viewModelScope.launch {
            repository.updateChatMetadata(currentUserId, chatId, modelId = spec.id)
        }
    }

    fun openApiSettings() {
        _apiTestState.value = ApiConnectionTestState()
        _controls.update { it.copy(showApiSettings = true) }
    }

    fun closeApiSettings() {
        _apiTestState.value = ApiConnectionTestState()
        _controls.update { it.copy(showApiSettings = false) }
    }

    fun saveApiSettings(newSettings: CustomApiSettings) {
        apiKeyVault.saveSettings(newSettings)
        val reloaded = apiKeyVault.loadSettings()
        _apiSettings.value = reloaded
        // Refresh selectedModel in case a custom model ID was edited
        val currentSelected = _controls.value.selectedModel
        if (currentSelected.isExternalApi) {
            val updatedSpec = reloaded.customModels.firstOrNull {
                it.id == currentSelected.id || it.displayName == currentSelected.displayName
            } ?: currentSelected
            _controls.update { it.copy(selectedModel = updatedSpec) }
        }
        _controls.update {
            it.copy(
                showApiSettings = false,
                statusBanner = if (reloaded.hasConfiguredKey()) {
                    "API key encrypted & saved on device"
                } else {
                    "API settings updated"
                }
            )
        }
    }

    fun clearApiKey() {
        apiKeyVault.clearApiKey()
        _apiSettings.value = apiKeyVault.loadSettings()
        _apiTestState.value = ApiConnectionTestState()
        _controls.update { it.copy(statusBanner = "API key removed from device") }
    }

    fun testApiConnection(settings: CustomApiSettings, modelId: String) {
        viewModelScope.launch {
            _apiTestState.value = ApiConnectionTestState(isTesting = true)
            val result = aiService.testExternalConnection(settings, modelId)
            result.onSuccess { msg ->
                _apiTestState.value = ApiConnectionTestState(
                    isTesting = false,
                    message = msg,
                    isSuccess = true
                )
            }.onFailure { err ->
                _apiTestState.value = ApiConnectionTestState(
                    isTesting = false,
                    message = err.localizedMessage ?: "Connection failed",
                    isSuccess = false
                )
            }
        }
    }

    fun setStyle(style: StylePreset) {
        _controls.update { it.copy(selectedStyle = style) }
        val chatId = _controls.value.activeChatId ?: return
        viewModelScope.launch {
            repository.updateChatMetadata(currentUserId, chatId, stylePreset = style)
        }
    }

    fun toggleThinking() {
        val next = !_controls.value.thinkingEnabled
        _controls.update { it.copy(thinkingEnabled = next) }
        val chatId = _controls.value.activeChatId ?: return
        viewModelScope.launch {
            repository.updateChatMetadata(currentUserId, chatId, thinkingEnabled = next)
        }
    }

    fun updateSearchQuery(query: String) {
        _controls.update { it.copy(searchQuery = query) }
    }

    fun setPendingAttachment(attachment: PendingAttachment) {
        _controls.update { it.copy(pendingAttachment = attachment, aiError = null) }
    }

    fun clearPendingAttachment() {
        _controls.update { it.copy(pendingAttachment = null) }
    }

    fun openArtifact(artifact: ExtractedArtifact) {
        _controls.update { it.copy(openedArtifact = artifact) }
    }

    fun closeArtifact() {
        _controls.update { it.copy(openedArtifact = null) }
    }

    fun clearAiError() {
        _controls.update { it.copy(aiError = null) }
    }

    fun clearStatusBanner() {
        _controls.update { it.copy(statusBanner = null) }
    }

    fun showStatusBanner(message: String) {
        _controls.update { it.copy(statusBanner = message) }
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        val ctrl = _controls.value
        val attachment = ctrl.pendingAttachment
        if (prompt.isEmpty() && attachment == null) return
        if (_streaming.value.isGenerating) return

        val settingsSnapshot = _apiSettings.value
        if (ctrl.selectedModel.isExternalApi && !settingsSnapshot.hasConfiguredKey()) {
            _controls.update {
                it.copy(
                    showApiSettings = true,
                    aiError = "Please add your API key to use ${ctrl.selectedModel.displayName}, or switch to a Free Cloud model."
                )
            }
            return
        }

        val effectivePrompt = prompt.ifBlank {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> "Analyze this image in detail."
                is PendingAttachment.DocumentAttachment -> "Summarize and analyze this document."
                null -> ""
            }
        }

        if (deviceActionBridge != null && !deviceActionBridge.isDeviceOnline() && attachment == null) {
            _controls.update { it.copy(pendingAttachment = null, aiError = null) }
            val offlineOutcome = deviceActionBridge.executeOfflineSiriCommand(effectivePrompt)
            if (offlineOutcome != null) {
                _controls.update { it.copy(statusBanner = offlineOutcome.message) }
            } else {
                _controls.update {
                    it.copy(
                        aiError = "Offline mode: Siri controls like \"Open WhatsApp\", \"Torch on\", \"Call 9876543210\", \"Battery\", and \"Wi-Fi settings\" work offline; general AI chat needs internet."
                    )
                }
            }
            return
        }

        val currentMessages = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        val projects = (projectsState.value as? UiState.Success)?.data ?: emptyList()
        val activeProject = projects.firstOrNull { it.id == ctrl.activeProjectId }

        _controls.update { it.copy(pendingAttachment = null, aiError = null) }

        activeGenerationJob = viewModelScope.launch {
            var chatId = ctrl.activeChatId
            var existingCount = currentMessages.size.toLong()
            val isFirstTurn = chatId == null || currentMessages.isEmpty()

            if (chatId == null) {
                val initialTitle = effectivePrompt.replace("\n", " ").take(48).ifBlank { "New conversation" }
                val createRes = repository.createChat(
                    userId = currentUserId,
                    title = initialTitle,
                    projectId = ctrl.activeProjectId,
                    modelId = ctrl.selectedModel.id,
                    stylePreset = ctrl.selectedStyle,
                    thinkingEnabled = ctrl.thinkingEnabled,
                    firstPreview = effectivePrompt
                )
                createRes.onFailure { err ->
                    _controls.update {
                        it.copy(aiError = err.localizedMessage ?: "Failed to create cloud conversation.")
                    }
                    return@launch
                }
                chatId = createRes.getOrNull() ?: return@launch
                _controls.update { it.copy(activeChatId = chatId) }
                existingCount = 0L
            }

            val userMsgResult = repository.addMessage(
                userId = currentUserId,
                chatId = chatId,
                role = "user",
                content = effectivePrompt,
                attachmentName = attachment?.displayName
            )
            userMsgResult.onFailure { err ->
                _controls.update {
                    it.copy(aiError = err.localizedMessage ?: "Failed to save message.")
                }
                return@launch
            }

            _streaming.value = StreamingMessageState(
                isGenerating = true,
                modelId = ctrl.selectedModel.id
            )

            var latestThinking: String? = null
            var latestVisible = ""
            var latestArtifact: ExtractedArtifact? = null

            try {
                aiService.streamReply(
                    historyMessages = currentMessages,
                    userPrompt = effectivePrompt,
                    attachment = attachment,
                    modelSpec = ctrl.selectedModel,
                    customApiSettings = settingsSnapshot,
                    stylePreset = ctrl.selectedStyle,
                    thinkingEnabled = ctrl.thinkingEnabled,
                    activeProject = activeProject
                ).collect { parsed ->
                    latestThinking = parsed.thinkingSummary
                    latestVisible = parsed.visibleText
                    latestArtifact = parsed.artifact
                    _streaming.value = StreamingMessageState(
                        isGenerating = true,
                        thinkingText = parsed.thinkingSummary.orEmpty(),
                        visibleText = parsed.visibleText,
                        artifact = parsed.artifact,
                        modelId = ctrl.selectedModel.id
                    )
                }

                repository.addMessage(
                    userId = currentUserId,
                    chatId = chatId,
                    role = "model",
                    content = latestVisible,
                    thinkingSummary = latestThinking,
                    artifactTitle = latestArtifact?.title,
                    artifactLanguage = latestArtifact?.language,
                    artifactContent = latestArtifact?.content,
                    modelId = ctrl.selectedModel.id
                )

                val previewText = latestVisible.ifBlank { latestArtifact?.title ?: "Artifact generated" }
                repository.updateChatMetadata(
                    userId = currentUserId,
                    chatId = chatId,
                    lastMessagePreview = previewText,
                    messageCount = existingCount + 2L
                )

                if (isFirstTurn) {
                    launch {
                        val smartTitle = aiService.generateChatTitle(effectivePrompt)
                        repository.updateChatMetadata(currentUserId, chatId, title = smartTitle)
                    }
                }
            } catch (e: CancellationException) {
                if (latestVisible.isNotBlank() || latestArtifact != null) {
                    repository.addMessage(
                        userId = currentUserId,
                        chatId = chatId,
                        role = "model",
                        content = latestVisible.ifBlank { "(Generation stopped)" },
                        thinkingSummary = latestThinking,
                        artifactTitle = latestArtifact?.title,
                        artifactLanguage = latestArtifact?.language,
                        artifactContent = latestArtifact?.content,
                        modelId = ctrl.selectedModel.id
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                val friendly = formatAiError(e, ctrl.selectedModel)
                _controls.update { it.copy(aiError = friendly) }
            } finally {
                _streaming.value = StreamingMessageState()
            }
        }
    }

    fun stopStreaming() {
        activeGenerationJob?.cancel()
        _streaming.value = StreamingMessageState()
    }

    fun regenerateLastResponse() {
        val msgs = (messagesState.value as? UiState.Success)?.data ?: return
        val lastUserMsg = msgs.lastOrNull { it.role == "user" } ?: return
        sendMessage(lastUserMsg.content)
    }

    fun togglePinChat(chat: ChatThread) {
        viewModelScope.launch {
            val res = repository.updateChatMetadata(
                userId = currentUserId,
                chatId = chat.id,
                pinned = !chat.pinned
            )
            res.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Unable to update pin state.") }
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            if (_controls.value.activeChatId == chatId) {
                startNewChat()
            }
            val res = repository.deleteChat(currentUserId, chatId)
            res.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to delete chat.") }
            }
        }
    }

    fun saveProject(
        existingProjectId: String?,
        name: String,
        description: String,
        systemPrompt: String,
        stylePreset: StylePreset,
        accentIndex: Long
    ) {
        viewModelScope.launch {
            val res = repository.saveProject(
                userId = currentUserId,
                existingProjectId = existingProjectId,
                name = name,
                description = description,
                systemPrompt = systemPrompt,
                stylePreset = stylePreset,
                accentIndex = accentIndex
            )
            res.onSuccess {
                _controls.update { it.copy(statusBanner = "Project synced to cloud") }
            }.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to save project.") }
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            if (_controls.value.activeProjectId == projectId) {
                _controls.update { it.copy(activeProjectId = null) }
            }
            repository.deleteProject(currentUserId, projectId).onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to delete project.") }
            }
        }
    }

    fun saveArtifactToLibrary(artifact: ExtractedArtifact) {
        viewModelScope.launch {
            val res = repository.saveArtifact(
                userId = currentUserId,
                title = artifact.title,
                language = artifact.language,
                content = artifact.content,
                chatId = _controls.value.activeChatId
            )
            res.onSuccess {
                _controls.update { it.copy(statusBanner = "Saved \"${artifact.title}\" to Cloud Artifacts") }
            }.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to save artifact.") }
            }
        }
    }

    fun deleteSavedArtifact(artifactId: String) {
        viewModelScope.launch {
            repository.deleteArtifact(currentUserId, artifactId).onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to delete artifact.") }
            }
        }
    }

    fun clearAppBuilderError() {
        _appBuilderState.update { it.copy(error = null) }
    }

    fun createOrUpdateUserApp(
        existingApp: UserApp?,
        name: String,
        description: String,
        prompt: String,
        accentIndex: Long,
        onComplete: (String) -> Unit = {}
    ) {
        val cleanName = name.trim().ifBlank { existingApp?.name ?: "My Zenvix App" }.take(80)
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty() || _appBuilderState.value.isBuilding) return

        val selectedModel = _controls.value.selectedModel
        val settingsSnapshot = _apiSettings.value
        if (selectedModel.isExternalApi && !settingsSnapshot.hasConfiguredKey()) {
            _controls.update { it.copy(showApiSettings = true) }
            _appBuilderState.update {
                it.copy(error = "Add your API key to use ${selectedModel.displayName}, or switch to a Free Cloud model.")
            }
            return
        }

        activeAppBuildJob?.cancel()
        activeAppBuildJob = viewModelScope.launch {
            _appBuilderState.value = AppBuilderState(
                isBuilding = true,
                statusText = if (existingApp == null) {
                    "Creating \"$cleanName\" with ${selectedModel.displayName}…"
                } else {
                    "Applying changes to \"$cleanName\"…"
                }
            )
            var extractedHtml: String? = null
            var latestVisible = ""
            try {
                aiService.streamBuildOrUpdateApp(
                    appName = cleanName,
                    appPrompt = cleanPrompt,
                    existingHtmlCode = existingApp?.htmlCode,
                    modelSpec = selectedModel,
                    customApiSettings = settingsSnapshot
                ).collect { parsed ->
                    latestVisible = parsed.visibleText
                    val htmlCandidate = parsed.artifact?.content?.takeIf { it.isNotBlank() }
                    if (htmlCandidate != null) {
                        extractedHtml = htmlCandidate
                    }
                    _appBuilderState.update {
                        it.copy(streamingChars = (htmlCandidate?.length ?: latestVisible.length))
                    }
                }

                val finalHtml = extractedHtml?.takeIf { it.isNotBlank() }
                    ?: extractRawHtmlFallback(latestVisible)
                    ?: throw IllegalStateException("Could not extract runnable app HTML. Please try describing the app again.")

                val nextVersion = if (existingApp == null) 1L else (existingApp.version + 1L).coerceAtMost(10000L)
                val resolvedDesc = description.trim()
                    .ifBlank { existingApp?.description.orEmpty() }
                    .ifBlank { cleanPrompt.take(160) }
                    .take(300)

                val saveResult = repository.saveUserApp(
                    userId = currentUserId,
                    existingAppId = existingApp?.id,
                    name = cleanName,
                    description = resolvedDesc,
                    htmlCode = finalHtml,
                    lastPrompt = cleanPrompt,
                    modelId = selectedModel.id,
                    version = nextVersion,
                    accentIndex = accentIndex
                )
                saveResult.onSuccess { savedId ->
                    _controls.update {
                        it.copy(
                            statusBanner = if (existingApp == null) {
                                "\"$cleanName\" created in Your App!"
                            } else {
                                "\"$cleanName\" updated to v$nextVersion"
                            }
                        )
                    }
                    onComplete(savedId)
                }.onFailure { err ->
                    _appBuilderState.update {
                        it.copy(error = err.localizedMessage ?: "Failed to save app to cloud.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "App build failed", e)
                _appBuilderState.update {
                    it.copy(error = formatAiError(e, selectedModel))
                }
            } finally {
                _appBuilderState.update { it.copy(isBuilding = false, statusText = "") }
            }
        }
    }

    fun saveUserAppCodeDirect(
        app: UserApp,
        newName: String,
        newDescription: String,
        newHtmlCode: String
    ) {
        val trimmedCode = newHtmlCode.trim()
        if (trimmedCode.isEmpty()) return
        viewModelScope.launch {
            val nextVersion = (app.version + 1L).coerceAtMost(10000L)
            repository.saveUserApp(
                userId = currentUserId,
                existingAppId = app.id,
                name = newName.trim().ifBlank { app.name },
                description = newDescription.trim().ifBlank { app.description },
                htmlCode = trimmedCode,
                lastPrompt = "Manual code edit",
                modelId = app.modelId,
                version = nextVersion,
                accentIndex = app.accentIndex
            ).onSuccess {
                _controls.update { it.copy(statusBanner = "Saved manual changes (v$nextVersion)") }
            }.onFailure { err ->
                _appBuilderState.update {
                    it.copy(error = err.localizedMessage ?: "Unable to save app code.")
                }
            }
        }
    }

    fun deleteUserApp(appId: String) {
        viewModelScope.launch {
            repository.deleteUserApp(currentUserId, appId).onSuccess {
                _controls.update { it.copy(statusBanner = "App deleted from Your App") }
            }.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to delete app.") }
            }
        }
    }

    fun buildDeviceDiagnostics(extraError: String? = null): String {
        val model = _controls.value.selectedModel
        val provider = _apiSettings.value.provider.label
        val base = buildString {
            append("App=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ")
            append("Android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) | ")
            append("Device=${Build.MANUFACTURER} ${Build.MODEL} | ")
            append("Model=${model.id} | Provider=$provider")
            val err = extraError?.takeIf { it.isNotBlank() }
                ?: _controls.value.aiError
                ?: _appBuilderState.value.error
            if (!err.isNullOrBlank()) {
                append(" | Error=${err.take(400)}")
            }
        }
        return base.take(2000)
    }

    fun submitBugReport(
        category: BugCategory,
        title: String,
        description: String,
        extraErrorLog: String? = null,
        onComplete: () -> Unit = {}
    ) {
        val cleanTitle = title.trim()
        val cleanDesc = description.trim()
        if (cleanTitle.isEmpty() || cleanDesc.isEmpty() || _isSubmittingBug.value) return

        viewModelScope.launch {
            _isSubmittingBug.value = true
            val diagnostics = buildDeviceDiagnostics(extraErrorLog)
            repository.submitBugReport(
                userId = currentUserId,
                reporterName = currentUserName,
                reporterEmail = currentUserEmail,
                category = category,
                title = cleanTitle,
                description = cleanDesc,
                diagnostics = diagnostics
            ).onSuccess {
                _controls.update { it.copy(statusBanner = "Bug report sent safely to Zenvix AI Owner!") }
                onComplete()
            }.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Could not submit bug report.") }
            }
            _isSubmittingBug.value = false
        }
    }

    fun updateBugStatusAsOwner(
        reportId: String,
        status: BugStatus,
        ownerNote: String
    ) {
        if (!isOwnerAdmin) return
        viewModelScope.launch {
            repository.updateBugReportStatusForOwner(
                reportId = reportId,
                status = status,
                ownerNote = ownerNote
            ).onSuccess {
                _controls.update { it.copy(statusBanner = "Bug status updated to ${status.label}") }
            }.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to update bug status.") }
            }
        }
    }

    fun deleteBugReport(reportId: String) {
        viewModelScope.launch {
            repository.deleteBugReport(reportId).onSuccess {
                _controls.update { it.copy(statusBanner = "Bug report removed") }
            }.onFailure { err ->
                _controls.update { it.copy(aiError = err.localizedMessage ?: "Failed to delete bug report.") }
            }
        }
    }

    private fun extractRawHtmlFallback(raw: String): String? {
        val trimmed = raw.trim()
        val docIdx = trimmed.indexOf("<!DOCTYPE", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: trimmed.indexOf("<html", ignoreCase = true)
        if (docIdx >= 0) {
            val endIdx = trimmed.lastIndexOf("</html>", ignoreCase = true)
            return if (endIdx > docIdx) {
                trimmed.substring(docIdx, endIdx + 7)
            } else {
                trimmed.substring(docIdx)
            }
        }
        return null
    }

    private fun formatAiError(e: Exception, model: ZenvixModelSpec): String {
        val raw = e.localizedMessage ?: "Cloud AI request failed."
        return when {
            !model.isFreeBuiltIn && !model.isExternalApi && (raw.contains("billing", ignoreCase = true) ||
                raw.contains("403", ignoreCase = true) ||
                raw.contains("quota", ignoreCase = true)) ->
                "${model.displayName} requires a Firebase Blaze billing plan. Switch to Zenvix Sonnet 3.8 or Haiku 3.5 for free cloud usage."
            !model.isExternalApi && (raw.contains("403", ignoreCase = true) || raw.contains("AppCheck", ignoreCase = true)) ->
                "App Check attestation error (403). Please verify Firebase App Check configuration."
            else -> raw
        }
    }

    fun openLiveVoice() {
        liveVoiceManager?.openSheet(autoStartIfPermitted = true)
    }

    fun closeLiveVoice() {
        liveVoiceManager?.closeSheet()
    }

    fun startLiveVoiceSession() {
        liveVoiceManager?.startSession()
    }

    fun toggleLiveVoiceMute() {
        liveVoiceManager?.toggleMute()
    }

    fun interruptLiveVoice() {
        liveVoiceManager?.interruptPlayback()
    }

    fun selectLiveVoice(voiceName: String) {
        liveVoiceManager?.selectVoice(voiceName)
    }

    fun toggleOfflineSiriMode() {
        liveVoiceManager?.toggleOfflineSiriMode()
    }

    fun submitLiveCommandText(text: String) {
        liveVoiceManager?.submitCommandText(text)
    }

    fun selectLiveDisambiguatedContact(candidate: ContactCandidate) {
        liveVoiceManager?.onDisambiguatedContactSelected(candidate)
    }

    fun onLiveVoicePermissionResult(permission: String, granted: Boolean) {
        liveVoiceManager?.onPermissionResult(permission, granted)
    }

    override fun onCleared() {
        super.onCleared()
        liveVoiceManager?.release()
    }

    private companion object {
        const val TAG = "WorkspaceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
