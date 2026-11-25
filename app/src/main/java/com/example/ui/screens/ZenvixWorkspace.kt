package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.R
import com.example.core.signOut
import com.example.model.BugStatus
import com.example.model.ChatThread
import com.example.ui.components.ApiSettingsBottomSheet
import com.example.ui.components.ArtifactStudioBottomSheet
import com.example.ui.components.ArtifactStudioContent
import com.example.ui.components.ZenvixLiveVoiceSheet
import com.example.ui.theme.LoraFontFamily
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkspaceViewModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

const val ROUTE_CHAT = "chat"
const val ROUTE_YOUR_APP = "your_app"
const val ROUTE_PROJECTS = "projects"
const val ROUTE_ARTIFACTS = "artifacts"
const val ROUTE_SUPPORT_OWNER = "support_owner"

@Composable
fun ZenvixWorkspace(
    user: FirebaseUser,
    viewModel: WorkspaceViewModel,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val apiSettings by viewModel.apiSettings.collectAsStateWithLifecycle()
    val apiTestState by viewModel.apiTestState.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val liveVoiceState by viewModel.liveVoiceState.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val userAppsState by viewModel.userAppsState.collectAsStateWithLifecycle()
    val appBuilderState by viewModel.appBuilderState.collectAsStateWithLifecycle()
    val artifactsState by viewModel.artifactsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val myBugReportsState by viewModel.myBugReportsState.collectAsStateWithLifecycle()
    val ownerBugInboxState by viewModel.ownerBugInboxState.collectAsStateWithLifecycle()
    val allUsersForOwnerState by viewModel.allUsersForOwnerState.collectAsStateWithLifecycle()
    val authEventsForOwnerState by viewModel.authEventsForOwnerState.collectAsStateWithLifecycle()
    val selectedOwnerUserId by viewModel.selectedOwnerUserId.collectAsStateWithLifecycle()
    val selectedOwnerChatId by viewModel.selectedOwnerChatId.collectAsStateWithLifecycle()
    val selectedOwnerUserAppsState by viewModel.selectedOwnerUserAppsState.collectAsStateWithLifecycle()
    val selectedOwnerUserChatsState by viewModel.selectedOwnerUserChatsState.collectAsStateWithLifecycle()
    val selectedOwnerUserProjectsState by viewModel.selectedOwnerUserProjectsState.collectAsStateWithLifecycle()
    val selectedOwnerUserMessagesState by viewModel.selectedOwnerUserMessagesState.collectAsStateWithLifecycle()
    val isSubmittingBug by viewModel.isSubmittingBug.collectAsStateWithLifecycle()
    var prefillBugError by remember { mutableStateOf<String?>(null) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_CHAT

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(controls.statusBanner) {
        val msg = controls.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val projects = (projectsState as? UiState.Success)?.data ?: emptyList()
    val activeProject = remember(projects, controls.activeProjectId) {
        projects.firstOrNull { it.id == controls.activeProjectId }
    }
    val userFirstName = remember(user.displayName) {
        user.displayName?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() } ?: "there"
    }

    val navigateTo: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(ROUTE_CHAT) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        scope.launch { drawerState.close() }
    }

    val userAppsCount = (userAppsState as? UiState.Success)?.data?.size ?: 0
    val openOwnerBugsCount = remember(ownerBugInboxState) {
        (ownerBugInboxState as? UiState.Success)?.data?.count { it.resolvedStatus() != BugStatus.FIXED } ?: 0
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp
        val showSideArtifactPane = isExpanded && controls.openedArtifact != null

        val drawerBody: @Composable () -> Unit = {
            WorkspaceDrawerContent(
                user = user,
                currentRoute = currentRoute,
                hasConfiguredApiKey = apiSettings.hasConfiguredKey(),
                userAppsCount = userAppsCount,
                isOwnerAdmin = viewModel.isOwnerAdmin,
                openOwnerBugsCount = openOwnerBugsCount,
                chatsState = chatsState,
                activeChatId = controls.activeChatId,
                searchQuery = controls.searchQuery,
                onSearchChange = viewModel::updateSearchQuery,
                onNewChat = {
                    viewModel.startNewChat()
                    navigateTo(ROUTE_CHAT)
                },
                onSelectRoute = navigateTo,
                onOpenApiSettings = {
                    scope.launch { drawerState.close() }
                    viewModel.openApiSettings()
                },
                onSelectChat = { chat ->
                    viewModel.selectChat(chat)
                    navigateTo(ROUTE_CHAT)
                },
                onTogglePin = viewModel::togglePinChat,
                onDeleteChat = viewModel::deleteChat,
                onSignOut = {
                    scope.launch {
                        viewModel.recordSignOutBeforeAuthClear()
                        signOut(credentialManager, scope, onSignOutComplete = onSignedOut)
                    }
                }
            )
        }

        val workspaceBody: @Composable () -> Unit = {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_CHAT,
                        enterTransition = {
                            slideInHorizontally(tween(260)) { it / 10 } + fadeIn(tween(240))
                        },
                        exitTransition = {
                            slideOutHorizontally(tween(220)) { -it / 12 } + fadeOut(tween(180))
                        },
                        popEnterTransition = {
                            slideInHorizontally(tween(260)) { -it / 10 } + fadeIn(tween(240))
                        },
                        popExitTransition = {
                            slideOutHorizontally(tween(220)) { it / 12 } + fadeOut(tween(180))
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(ROUTE_CHAT) {
                            ChatScreen(
                                userFirstName = userFirstName,
                                controls = controls,
                                apiSettings = apiSettings,
                                messagesState = messagesState,
                                streamingState = streaming,
                                activeProject = activeProject,
                                showDrawerButton = !isExpanded,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onNewChat = { viewModel.startNewChat() },
                                onOpenYourApp = { navigateTo(ROUTE_YOUR_APP) },
                                onSelectModel = viewModel::setModel,
                                onOpenApiSettings = viewModel::openApiSettings,
                                onSelectStyle = viewModel::setStyle,
                                onToggleThinking = viewModel::toggleThinking,
                                onSetAttachment = viewModel::setPendingAttachment,
                                onClearAttachment = viewModel::clearPendingAttachment,
                                onSendPrompt = viewModel::sendMessage,
                                onStopStreaming = viewModel::stopStreaming,
                                onRegenerateLast = viewModel::regenerateLastResponse,
                                onOpenArtifact = viewModel::openArtifact,
                                onSaveArtifact = viewModel::saveArtifactToLibrary,
                                onDismissError = viewModel::clearAiError,
                                onReportBug = { err ->
                                    prefillBugError = err
                                    navigateTo(ROUTE_SUPPORT_OWNER)
                                },
                                onOpenLiveVoice = viewModel::openLiveVoice,
                                onNotify = viewModel::showStatusBanner
                            )
                        }
                        composable(ROUTE_YOUR_APP) {
                            YourAppScreen(
                                userAppsState = userAppsState,
                                builderState = appBuilderState,
                                selectedModel = controls.selectedModel,
                                externalModels = apiSettings.customModels,
                                showDrawerButton = !isExpanded,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onCreateOrUpdateApp = viewModel::createOrUpdateUserApp,
                                onSaveManualCode = viewModel::saveUserAppCodeDirect,
                                onDeleteApp = viewModel::deleteUserApp,
                                onDismissError = viewModel::clearAppBuilderError,
                                onReportBug = { err ->
                                    prefillBugError = err
                                    navigateTo(ROUTE_SUPPORT_OWNER)
                                },
                                onNotify = viewModel::showStatusBanner
                            )
                        }
                        composable(ROUTE_PROJECTS) {
                            ProjectsScreen(
                                projectsState = projectsState,
                                showDrawerButton = !isExpanded,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onStartChatInProject = { project ->
                                    viewModel.startNewChat(project = project)
                                    navigateTo(ROUTE_CHAT)
                                },
                                onSaveProject = viewModel::saveProject,
                                onDeleteProject = viewModel::deleteProject
                            )
                        }
                        composable(ROUTE_ARTIFACTS) {
                            ArtifactsLibraryScreen(
                                artifactsState = artifactsState,
                                showDrawerButton = !isExpanded,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenArtifact = viewModel::openArtifact,
                                onDeleteArtifact = viewModel::deleteSavedArtifact,
                                onNotify = viewModel::showStatusBanner
                            )
                        }
                        composable(ROUTE_SUPPORT_OWNER) {
                            SupportAndOwnerScreen(
                                isOwnerAdmin = viewModel.isOwnerAdmin,
                                isSubmittingBug = isSubmittingBug,
                                diagnosticsPreview = viewModel.buildDeviceDiagnostics(prefillBugError),
                                prefillBugError = prefillBugError,
                                myBugReportsState = myBugReportsState,
                                ownerBugInboxState = ownerBugInboxState,
                                allUsersState = allUsersForOwnerState,
                                authEventsState = authEventsForOwnerState,
                                selectedOwnerUserId = selectedOwnerUserId,
                                selectedOwnerChatId = selectedOwnerChatId,
                                selectedUserAppsState = selectedOwnerUserAppsState,
                                selectedUserChatsState = selectedOwnerUserChatsState,
                                selectedUserProjectsState = selectedOwnerUserProjectsState,
                                selectedUserMessagesState = selectedOwnerUserMessagesState,
                                showDrawerButton = !isExpanded,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onSelectOwnerUser = viewModel::selectOwnerUserForInspection,
                                onSelectOwnerChat = viewModel::selectOwnerChatForInspection,
                                onPreviewUserApp = viewModel::openArtifact,
                                onDeleteAuthEvent = viewModel::deleteAuthEventAsOwner,
                                onSubmitBugReport = viewModel::submitBugReport,
                                onOwnerUpdateBugStatus = viewModel::updateBugStatusAsOwner,
                                onDeleteBugReport = viewModel::deleteBugReport,
                                onClearPrefillError = { prefillBugError = null }
                            )
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .safeDrawingPadding()
                            .padding(top = 56.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showSideArtifactPane,
                    enter = expandHorizontally(tween(260)) + fadeIn(tween(240)),
                    exit = shrinkHorizontally(tween(220)) + fadeOut(tween(180))
                ) {
                    controls.openedArtifact?.let { artifact ->
                        Surface(
                            modifier = Modifier
                                .width(420.dp)
                                .fillMaxHeight()
                                .safeDrawingPadding(),
                            tonalElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            ArtifactStudioContent(
                                artifact = artifact,
                                onDismiss = viewModel::closeArtifact,
                                onSaveToCloud = viewModel::saveArtifactToLibrary,
                                onNotify = viewModel::showStatusBanner,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(300.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        drawerBody()
                    }
                },
                content = workspaceBody
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(304.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        drawerBody()
                    }
                },
                content = workspaceBody
            )

            controls.openedArtifact?.let { artifact ->
                ArtifactStudioBottomSheet(
                    artifact = artifact,
                    onDismiss = viewModel::closeArtifact,
                    onSaveToCloud = viewModel::saveArtifactToLibrary,
                    onNotify = viewModel::showStatusBanner
                )
            }
        }

        if (controls.showApiSettings) {
            ApiSettingsBottomSheet(
                initialSettings = apiSettings,
                testState = apiTestState,
                onTestConnection = viewModel::testApiConnection,
                onSave = viewModel::saveApiSettings,
                onClearKey = viewModel::clearApiKey,
                onDismiss = viewModel::closeApiSettings
            )
        }

        if (liveVoiceState.isVisible) {
            ZenvixLiveVoiceSheet(
                state = liveVoiceState,
                onDismiss = viewModel::closeLiveVoice,
                onStartOrReconnect = viewModel::startLiveVoiceSession,
                onToggleMute = viewModel::toggleLiveVoiceMute,
                onInterrupt = viewModel::interruptLiveVoice,
                onSelectVoice = viewModel::selectLiveVoice,
                onToggleOfflineMode = viewModel::toggleOfflineSiriMode,
                onSubmitOfflineCommand = viewModel::submitLiveCommandText,
                onSelectDisambiguatedContact = viewModel::selectLiveDisambiguatedContact,
                onPermissionResult = viewModel::onLiveVoicePermissionResult
            )
        }
    }
}

@Composable
private fun WorkspaceDrawerContent(
    user: FirebaseUser,
    currentRoute: String,
    hasConfiguredApiKey: Boolean,
    userAppsCount: Int,
    isOwnerAdmin: Boolean,
    openOwnerBugsCount: Int,
    chatsState: UiState<List<ChatThread>>,
    activeChatId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectRoute: (String) -> Unit,
    onOpenApiSettings: () -> Unit,
    onSelectChat: (ChatThread) -> Unit,
    onTogglePin: (ChatThread) -> Unit,
    onDeleteChat: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_zenvix_mark_fg),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Zenvix AI",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = LoraFontFamily)
                )
                Text(
                    text = "Cloud Workspace",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onNewChat,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sidebar_new_chat_button")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(10.dp))

        NavigationDrawerItem(
            label = { Text("Chats") },
            selected = currentRoute == ROUTE_CHAT,
            onClick = { onSelectRoute(ROUTE_CHAT) },
            icon = { Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null) },
            modifier = Modifier.testTag("nav_chats_item")
        )
        NavigationDrawerItem(
            label = { Text("Your App") },
            selected = currentRoute == ROUTE_YOUR_APP,
            onClick = { onSelectRoute(ROUTE_YOUR_APP) },
            icon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
            badge = {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (userAppsCount > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                    }
                ) {
                    Text(
                        text = if (userAppsCount > 0) "$userAppsCount" else "Create",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (userAppsCount > 0) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            },
            modifier = Modifier.testTag("nav_your_app_item")
        )
        NavigationDrawerItem(
            label = { Text("Projects") },
            selected = currentRoute == ROUTE_PROJECTS,
            onClick = { onSelectRoute(ROUTE_PROJECTS) },
            icon = { Icon(Icons.Rounded.FolderSpecial, contentDescription = null) },
            modifier = Modifier.testTag("nav_projects_item")
        )
        NavigationDrawerItem(
            label = { Text("Artifacts Studio") },
            selected = currentRoute == ROUTE_ARTIFACTS,
            onClick = { onSelectRoute(ROUTE_ARTIFACTS) },
            icon = { Icon(Icons.Rounded.Code, contentDescription = null) },
            modifier = Modifier.testTag("nav_artifacts_item")
        )
        NavigationDrawerItem(
            label = {
                Text(if (isOwnerAdmin) "Owner Dashboard" else "Bug Report & Safety")
            },
            selected = currentRoute == ROUTE_SUPPORT_OWNER,
            onClick = { onSelectRoute(ROUTE_SUPPORT_OWNER) },
            icon = {
                Icon(
                    imageVector = if (isOwnerAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.BugReport,
                    contentDescription = null
                )
            },
            badge = {
                if (isOwnerAdmin && openOwnerBugsCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "$openOwnerBugsCount Open",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            modifier = Modifier.testTag("nav_support_owner_item")
        )

        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            onClick = onOpenApiSettings,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("drawer_api_settings_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (hasConfiguredApiKey) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "API Key & Models",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (hasConfiguredApiKey) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Text(
                    text = if (hasConfiguredApiKey) "Key Active" else "Add Key",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasConfiguredApiKey) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search chats…", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (chatsState) {
                is UiState.Loading -> {
                    item {
                        Text(
                            text = "Syncing cloud chats…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = chatsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val q = searchQuery.trim().lowercase()
                    val filtered = chatsState.data.filter {
                        q.isEmpty() || it.title.lowercase().contains(q) || it.lastMessagePreview.lowercase().contains(q)
                    }
                    val pinned = filtered.filter { it.pinned }
                    val recent = filtered.filterNot { it.pinned }

                    if (pinned.isNotEmpty()) {
                        item {
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        items(pinned, key = { "pin_${it.id}" }) { chat ->
                            DrawerChatRow(
                                chat = chat,
                                isActive = chat.id == activeChatId && currentRoute == ROUTE_CHAT,
                                onClick = { onSelectChat(chat) },
                                onTogglePin = { onTogglePin(chat) },
                                onDelete = { onDeleteChat(chat.id) }
                            )
                        }
                    }

                    if (recent.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        items(recent, key = { it.id }) { chat ->
                            DrawerChatRow(
                                chat = chat,
                                isActive = chat.id == activeChatId && currentRoute == ROUTE_CHAT,
                                onClick = { onSelectChat(chat) },
                                onTogglePin = { onTogglePin(chat) },
                                onDelete = { onDeleteChat(chat.id) }
                            )
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = if (q.isBlank()) "No conversations yet." else "No chats match \"$searchQuery\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (user.photoUrl != null) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = "Account avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (user.displayName?.firstOrNull() ?: 'Z').uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName ?: "Zenvix User",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Sign out",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerChatRow(
    chat: ChatThread,
    isActive: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chat.lastMessagePreview.isNotBlank()) {
                    Text(
                        text = chat.lastMessagePreview,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = if (chat.pinned) "Unpin chat" else "Pin chat",
                    tint = if (chat.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
