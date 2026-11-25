package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AiProject
import com.example.model.ChatMessage
import com.example.model.CustomApiSettings
import com.example.model.ExtractedArtifact
import com.example.model.PendingAttachment
import com.example.model.StylePreset
import com.example.model.ZenvixModelOption
import com.example.model.ZenvixModelSpec
import com.example.ui.components.AmbientAuroraBackground
import com.example.ui.components.AnimatedGradientBorderBrush
import com.example.ui.components.ArtifactCard
import com.example.ui.components.ChatComposerDock
import com.example.ui.components.FloatingSparkField
import com.example.ui.components.MarkdownFormattedText
import com.example.ui.components.MessageBubbleEntrance
import com.example.ui.components.StaggeredReveal
import com.example.ui.components.ThoughtProcessCard
import com.example.ui.components.TypingWaveDots
import com.example.ui.components.ZenvixLivingOrb
import com.example.ui.components.springPressScale
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LoraFontFamily
import com.example.viewmodel.StreamingMessageState
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkspaceUiControls
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userFirstName: String,
    controls: WorkspaceUiControls,
    apiSettings: CustomApiSettings,
    messagesState: UiState<List<ChatMessage>>,
    streamingState: StreamingMessageState,
    activeProject: AiProject?,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenYourApp: () -> Unit,
    onSelectModel: (ZenvixModelSpec) -> Unit,
    onOpenApiSettings: () -> Unit,
    onSelectStyle: (StylePreset) -> Unit,
    onToggleThinking: () -> Unit,
    onSetAttachment: (PendingAttachment) -> Unit,
    onClearAttachment: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onRegenerateLast: () -> Unit,
    onOpenArtifact: (ExtractedArtifact) -> Unit,
    onSaveArtifact: (ExtractedArtifact) -> Unit,
    onDismissError: () -> Unit,
    onReportBug: (String) -> Unit,
    onOpenLiveVoice: () -> Unit = {},
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var draftPrompt by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }

    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(messages.size, streamingState.visibleText.length, streamingState.isGenerating) {
        val total = messages.size + if (streamingState.isGenerating) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val pillInteraction = remember { MutableInteractionSource() }
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (showModelSheet) 180f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "model_pill_chevron"
                    )
                    Surface(
                        onClick = { showModelSheet = true },
                        interactionSource = pillInteraction,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (controls.selectedModel.isExternalApi) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .springPressScale(pillInteraction, pressedScale = 0.95f)
                            .testTag("model_selector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedContent(
                                targetState = controls.selectedModel.displayName,
                                transitionSpec = {
                                    (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(200))) togetherWith
                                        (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "model_name_morph"
                            ) { modelName ->
                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Switch cloud AI model",
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = chevronRotation },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open sidebar")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenYourApp,
                        modifier = Modifier.testTag("topbar_your_app_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = "Your App",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenApiSettings,
                        modifier = Modifier.testTag("topbar_api_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = "API Key & Custom Models",
                            tint = if (apiSettings.hasConfiguredKey()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.testTag("topbar_new_chat_button")
                    ) {
                        Icon(Icons.Rounded.AddComment, contentDescription = "Start new chat")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            ChatComposerDock(
                draftText = draftPrompt,
                onDraftChange = { draftPrompt = it },
                isGenerating = streamingState.isGenerating,
                thinkingEnabled = controls.thinkingEnabled,
                selectedStyle = controls.selectedStyle,
                pendingAttachment = controls.pendingAttachment,
                onToggleThinking = onToggleThinking,
                onSelectStyle = onSelectStyle,
                onSetAttachment = onSetAttachment,
                onClearAttachment = onClearAttachment,
                onSend = {
                    val toSend = draftPrompt
                    draftPrompt = ""
                    onSendPrompt(toSend)
                },
                onStop = onStopStreaming,
                onOpenLiveVoice = onOpenLiveVoice,
                onNotify = onNotify
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            AmbientAuroraBackground(
                intensity = if (streamingState.isGenerating) 0.88f else 0.48f,
                isActive = true
            )
            FloatingSparkField(
                alphaMultiplier = if (streamingState.isGenerating) 0.90f else 0.45f
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize()
            ) {
                if (activeProject != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Project: ${activeProject.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = controls.aiError != null) {
                    controls.aiError?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onReportBug(err) }) {
                                    Text("Report Bug")
                                }
                                TextButton(onClick = onDismissError) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                when (messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = messagesState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is UiState.Success -> {
                        if (messages.isEmpty() && !streamingState.isGenerating) {
                            ChatEmptyWelcome(
                                userFirstName = userFirstName,
                                activeProject = activeProject,
                                onOpenYourApp = onOpenYourApp,
                                onStarterClick = { starterPrompt ->
                                    onSendPrompt(starterPrompt)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    Box(modifier = Modifier.animateItem()) {
                                        MessageBubbleEntrance(
                                            messageKey = msg.id,
                                            fromEnd = msg.role == "user"
                                        ) {
                                            if (msg.role == "user") {
                                                UserMessageBubble(message = msg)
                                            } else {
                                                val isLastModel = messages.lastOrNull { it.role == "model" }?.id == msg.id
                                                ModelMessageBlock(
                                                    message = msg,
                                                    externalModels = apiSettings.customModels,
                                                    isSpeaking = speakingMessageId == msg.id,
                                                    showRegenerate = isLastModel && !streamingState.isGenerating,
                                                    onOpenArtifact = onOpenArtifact,
                                                    onSaveArtifact = onSaveArtifact,
                                                    onToggleSpeak = {
                                                        if (speakingMessageId == msg.id) {
                                                            tts.stop()
                                                            speakingMessageId = null
                                                        } else {
                                                            speakingMessageId = msg.id
                                                            tts.speak(
                                                                msg.content,
                                                                TextToSpeech.QUEUE_FLUSH,
                                                                null,
                                                                msg.id
                                                            )
                                                        }
                                                    },
                                                    onRegenerate = onRegenerateLast,
                                                    onNotify = onNotify
                                                )
                                            }
                                        }
                                    }
                                }

                                if (streamingState.isGenerating) {
                                    item(key = "streaming_reply") {
                                        Box(modifier = Modifier.animateItem()) {
                                            StreamingModelBlock(
                                                state = streamingState,
                                                externalModels = apiSettings.customModels,
                                                onOpenArtifact = onOpenArtifact,
                                                onNotify = onNotify
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select AI Model",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = LoraFontFamily)
                )
                Text(
                    text = "Free Built-in Cloud AI (zero setup) or your own API key for Opus 5.5, Febal 5.1 & Sonnet 5.5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Free Built-in Cloud AI (No API Key)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                ZenvixModelOption.builtInSpecs().forEach { spec ->
                    ModelSpecOptionCard(
                        spec = spec,
                        selected = controls.selectedModel.id == spec.id,
                        onClick = {
                            onSelectModel(spec)
                            showModelSheet = false
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "External API Models (BYOK)",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (apiSettings.hasConfiguredKey()) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                            }
                        ) {
                            Text(
                                text = if (apiSettings.hasConfiguredKey()) {
                                    "Key Saved • ${apiSettings.provider.label}"
                                } else {
                                    "API Key Required"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (apiSettings.hasConfiguredKey()) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showModelSheet = false
                            onOpenApiSettings()
                        },
                        modifier = Modifier.testTag("model_sheet_configure_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configure Key")
                    }
                }

                apiSettings.customModels.forEach { spec ->
                    ModelSpecOptionCard(
                        spec = spec,
                        selected = controls.selectedModel.id == spec.id,
                        showModelIdTag = true,
                        onClick = {
                            onSelectModel(spec)
                            showModelSheet = false
                            if (!apiSettings.hasConfiguredKey()) {
                                onOpenApiSettings()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelSpecOptionCard(
    spec: ZenvixModelSpec,
    selected: Boolean,
    showModelIdTag: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(220),
        label = "model_card_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(220),
        label = "model_card_border"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(if (selected) 1.4.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .springPressScale(interactionSource, pressedScale = 0.975f)
            .testTag("model_option_${spec.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = spec.displayName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            spec.isFreeBuiltIn -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                            spec.isExternalApi -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = spec.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                spec.isFreeBuiltIn -> MaterialTheme.colorScheme.tertiary
                                spec.isExternalApi -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (showModelIdTag) "${spec.subtitle} • ${spec.id}" else spec.subtitle,
                    style = if (showModelIdTag) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ChatEmptyWelcome(
    userFirstName: String,
    activeProject: AiProject?,
    onOpenYourApp: () -> Unit,
    onStarterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredReveal(index = 0) {
            ZenvixLivingOrb(isActive = true, size = 72.dp) {
                Image(
                    painter = painterResource(id = R.drawable.ic_zenvix_mark_fg),
                    contentDescription = "Zenvix AI mark",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        StaggeredReveal(index = 1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (activeProject != null) {
                        activeProject.name
                    } else {
                        "How can I help, $userFirstName?"
                    },
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = LoraFontFamily),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = activeProject?.description?.takeIf { it.isNotBlank() }
                        ?: "Ask questions, write code, create custom apps in Your App, or attach photos and docs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StaggeredReveal(index = 2) {
                val yourAppInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onOpenYourApp,
                    interactionSource = yourAppInteraction,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.25.dp, AnimatedGradientBorderBrush()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPressScale(yourAppInteraction, pressedScale = 0.975f)
                        .testTag("welcome_your_app_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your App — Create & Customize Apps",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Open created apps, or tap Create App and modify anytime with AI.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            StaggeredReveal(index = 3) {
                StarterPromptCard(
                    icon = Icons.Rounded.Code,
                    title = "Build an Interactive Artifact",
                    prompt = "Create a sleek interactive Pomodoro focus timer in a single HTML file with start, pause, reset, and session counter."
                ) { onStarterClick(it) }
            }

            StaggeredReveal(index = 4) {
                StarterPromptCard(
                    icon = Icons.Rounded.Psychology,
                    title = "Deep Reasoning & Strategy",
                    prompt = "Compare clean MVVM vs MVI architecture for a real-time Android AI app with pros, cons, and a decision checklist."
                ) { onStarterClick(it) }
            }

            StaggeredReveal(index = 5) {
                StarterPromptCard(
                    icon = Icons.Rounded.AttachFile,
                    title = "Bilingual Hinglish + English",
                    prompt = "Explain how cloud AI streaming works in simple Hinglish with a real-world analogy and 3 practical tips."
                ) { onStarterClick(it) }
            }
        }
    }
}

@Composable
private fun StarterPromptCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    prompt: String,
    onClick: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = { onClick(prompt) },
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .springPressScale(interactionSource, pressedScale = 0.975f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (!message.attachmentName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.attachmentName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ModelMessageBlock(
    message: ChatMessage,
    externalModels: List<ZenvixModelSpec>,
    isSpeaking: Boolean,
    showRegenerate: Boolean,
    onOpenArtifact: (ExtractedArtifact) -> Unit,
    onSaveArtifact: (ExtractedArtifact) -> Unit,
    onToggleSpeak: () -> Unit,
    onRegenerate: () -> Unit,
    onNotify: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val artifact = remember(message) { message.toArtifactOrNull() }
    val modelLabel = remember(message.modelId, externalModels) {
        ZenvixModelOption.resolveSpec(message.modelId, externalModels).displayName
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_zenvix_mark_fg),
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = modelLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!message.thinkingSummary.isNullOrBlank()) {
            ThoughtProcessCard(thinkingText = message.thinkingSummary, isStreaming = false)
        }

        if (message.content.isNotBlank()) {
            MarkdownFormattedText(
                markdown = message.content,
                onCopyCode = { onNotify("Code copied") }
            )
        }

        if (artifact != null) {
            ArtifactCard(
                artifact = artifact,
                onOpen = { onOpenArtifact(artifact) }
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(message.content))
                    onNotify("Response copied")
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy response",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = onToggleSpeak,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (isSpeaking) "Stop reading aloud" else "Read aloud",
                    tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (artifact != null) {
                IconButton(
                    onClick = { onSaveArtifact(artifact) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = "Save artifact to cloud",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (showRegenerate) {
                IconButton(
                    onClick = onRegenerate,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Regenerate response",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingModelBlock(
    state: StreamingMessageState,
    externalModels: List<ZenvixModelSpec>,
    onOpenArtifact: (ExtractedArtifact) -> Unit,
    onNotify: (String) -> Unit
) {
    val activeModelName = remember(state.modelId, externalModels) {
        ZenvixModelOption.resolveSpec(state.modelId, externalModels).displayName
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZenvixLivingOrb(isActive = true, size = 28.dp) {
                Image(
                    painter = painterResource(id = R.drawable.ic_zenvix_mark_fg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$activeModelName is responding",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            TypingWaveDots(color = MaterialTheme.colorScheme.primary, dotSize = 5.dp)
        }

        if (state.thinkingText.isNotBlank()) {
            ThoughtProcessCard(thinkingText = state.thinkingText, isStreaming = true)
        }

        if (state.visibleText.isNotBlank()) {
            MarkdownFormattedText(
                markdown = state.visibleText,
                onCopyCode = { onNotify("Code copied") }
            )
        }

        state.artifact?.let { artifact ->
            ArtifactCard(
                artifact = artifact,
                onOpen = { onOpenArtifact(artifact) }
            )
        }
    }
}
