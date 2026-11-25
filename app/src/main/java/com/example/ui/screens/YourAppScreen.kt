package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.UserApp
import com.example.model.ZenvixModelOption
import com.example.model.ZenvixModelSpec
import com.example.ui.components.APP_ACCENT_COLORS
import com.example.ui.components.AmbientAuroraBackground
import com.example.ui.components.AnimatedGradientBorderBrush
import com.example.ui.components.AppRunnerAndEditorView
import com.example.ui.components.CreateAppDialog
import com.example.ui.components.FloatingSparkField
import com.example.ui.components.StaggeredReveal
import com.example.ui.components.TypingWaveDots
import com.example.ui.components.ZenvixLivingOrb
import com.example.ui.components.springPressScale
import com.example.ui.theme.LoraFontFamily
import com.example.viewmodel.AppBuilderState
import com.example.viewmodel.UiState

private data class QuickAppIdea(
    val name: String,
    val subtitle: String,
    val prompt: String,
    val accentIndex: Long
)

private val QUICK_APP_IDEAS = listOf(
    QuickAppIdea(
        name = "Expense Tracker Pro",
        subtitle = "Daily budget, categories & animated spending chart",
        prompt = "Build a sleek mobile Expense Tracker app with add expense/income, category chips, animated summary cards, daily chart, and localStorage persistence.",
        accentIndex = 0L
    ),
    QuickAppIdea(
        name = "Focus & Habit Studio",
        subtitle = "Pomodoro timer, streak tracker & glowing progress ring",
        prompt = "Build a dynamic Pomodoro Focus Timer & Daily Habit Tracker app with animated circular SVG progress ring, session sound beep, and localStorage streaks.",
        accentIndex = 1L
    ),
    QuickAppIdea(
        name = "Neon Smart Calculator",
        subtitle = "Scientific calculator + currency & unit quick converter",
        prompt = "Build a tactile dark-glass Smart Calculator with history tape, scientific buttons, and an instant unit converter tab.",
        accentIndex = 2L
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YourAppScreen(
    userAppsState: UiState<List<UserApp>>,
    builderState: AppBuilderState,
    selectedModel: ZenvixModelSpec,
    externalModels: List<ZenvixModelSpec>,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onCreateOrUpdateApp: (
        existingApp: UserApp?,
        name: String,
        description: String,
        prompt: String,
        accentIndex: Long,
        onComplete: (String) -> Unit
    ) -> Unit,
    onSaveManualCode: (app: UserApp, newName: String, newDesc: String, newHtml: String) -> Unit,
    onDeleteApp: (String) -> Unit,
    onDismissError: () -> Unit,
    onReportBug: (String) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeAppId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var prefillName by remember { mutableStateOf("") }
    var prefillPrompt by remember { mutableStateOf("") }
    var prefillAccent by remember { mutableLongStateOf(0L) }

    val apps = (userAppsState as? UiState.Success)?.data ?: emptyList()
    val openedApp = remember(apps, activeAppId) { apps.firstOrNull { it.id == activeAppId } }

    AnimatedContent(
        targetState = openedApp,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
        label = "your_app_list_or_runner"
    ) { currentApp ->
        if (currentApp != null) {
            AppRunnerAndEditorView(
                app = currentApp,
                selectedModel = selectedModel,
                builderState = builderState,
                onBack = { activeAppId = null },
                onApplyAiChange = { changePrompt ->
                    onCreateOrUpdateApp(
                        currentApp,
                        currentApp.name,
                        currentApp.description,
                        changePrompt,
                        currentApp.accentIndex
                    ) { updatedId ->
                        activeAppId = updatedId
                    }
                },
                onSaveManualCode = { newName, newDesc, newHtml ->
                    onSaveManualCode(currentApp, newName, newDesc, newHtml)
                },
                onNotify = onNotify,
                modifier = modifier
            )
        } else {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = "Your App",
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = LoraFontFamily)
                            )
                        },
                        navigationIcon = {
                            if (showDrawerButton) {
                                IconButton(onClick = onOpenDrawer) {
                                    Icon(Icons.Rounded.Menu, contentDescription = "Open navigation drawer")
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    prefillName = ""
                                    prefillPrompt = ""
                                    prefillAccent = 0L
                                    showCreateDialog = true
                                },
                                modifier = Modifier.testTag("topbar_create_app_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Create App")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
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
                        intensity = if (builderState.isBuilding) 1.0f else 0.72f,
                        isActive = true
                    )
                    FloatingSparkField(alphaMultiplier = 0.85f)

                    Column(
                        modifier = Modifier
                            .widthIn(max = 780.dp)
                            .fillMaxSize()
                    ) {
                        AnimatedVisibility(visible = builderState.isBuilding) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                border = BorderStroke(1.3.dp, AnimatedGradientBorderBrush()),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ZenvixLivingOrb(isActive = true, size = 34.dp) {
                                        Icon(
                                            imageVector = Icons.Rounded.RocketLaunch,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = builderState.statusText.ifBlank { "Building your app…" },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = if (builderState.streamingChars > 0) {
                                                "Generating live code (${builderState.streamingChars} chars)…"
                                            } else {
                                                "Designing layout, CSS animations & interactive logic…"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    TypingWaveDots(dotSize = 5.dp)
                                }
                            }
                        }

                        AnimatedVisibility(visible = builderState.error != null) {
                            builderState.error?.let { err ->
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

                        when (userAppsState) {
                            is UiState.Loading -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            is UiState.Error -> {
                                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = userAppsState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            is UiState.Success -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (apps.isEmpty()) {
                                        item {
                                            YourAppEmptyState(
                                                selectedModel = selectedModel,
                                                isBuilding = builderState.isBuilding,
                                                onCreateCustom = {
                                                    prefillName = ""
                                                    prefillPrompt = ""
                                                    prefillAccent = 0L
                                                    showCreateDialog = true
                                                },
                                                onPickIdea = { idea ->
                                                    prefillName = idea.name
                                                    prefillPrompt = idea.prompt
                                                    prefillAccent = idea.accentIndex
                                                    showCreateDialog = true
                                                }
                                            )
                                        }
                                    } else {
                                        item {
                                            YourAppStudioBanner(
                                                appCount = apps.size,
                                                selectedModel = selectedModel,
                                                isBuilding = builderState.isBuilding,
                                                onCreateApp = {
                                                    prefillName = ""
                                                    prefillPrompt = ""
                                                    prefillAccent = 0L
                                                    showCreateDialog = true
                                                }
                                            )
                                        }

                                        itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                                            val modelBadge = ZenvixModelOption.resolveSpec(app.modelId, externalModels).displayName
                                            Box(modifier = Modifier.animateItem()) {
                                                StaggeredReveal(index = index) {
                                                    UserAppCard(
                                                        app = app,
                                                        modelLabel = modelBadge,
                                                        onOpen = { activeAppId = app.id },
                                                        onDelete = { onDeleteApp(app.id) }
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
        }
    }

    if (showCreateDialog) {
        CreateAppDialog(
            selectedModel = selectedModel,
            initialName = prefillName,
            initialPrompt = prefillPrompt,
            initialAccent = prefillAccent,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, prompt, accent ->
                showCreateDialog = false
                onCreateOrUpdateApp(null, name, desc, prompt, accent) { newAppId ->
                    activeAppId = newAppId
                }
            }
        )
    }
}

@Composable
private fun YourAppEmptyState(
    selectedModel: ZenvixModelSpec,
    isBuilding: Boolean,
    onCreateCustom: () -> Unit,
    onPickIdea: (QuickAppIdea) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StaggeredReveal(index = 0) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                border = BorderStroke(1.3.dp, AnimatedGradientBorderBrush()),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ZenvixLivingOrb(isActive = true, size = 70.dp) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Apps Created Yet",
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = LoraFontFamily),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap Create App to build your own interactive app with ${selectedModel.displayName}. Once created, you can run it live and make AI changes anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    val btnInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = onCreateCustom,
                        enabled = !isBuilding,
                        interactionSource = btnInteraction,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .springPressScale(btnInteraction, pressedScale = 0.95f)
                            .testTag("empty_create_app_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create App", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        StaggeredReveal(index = 1) {
            Text(
                text = "Quick Start App Ideas",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        QUICK_APP_IDEAS.forEachIndexed { idx, idea ->
            StaggeredReveal(index = idx + 2) {
                val accent = APP_ACCENT_COLORS[idea.accentIndex.toInt().coerceIn(0, APP_ACCENT_COLORS.lastIndex)]
                val cardInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { if (!isBuilding) onPickIdea(idea) },
                    interactionSource = cardInteraction,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPressScale(cardInteraction, pressedScale = 0.98f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(idea.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = idea.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { onPickIdea(idea) },
                            enabled = !isBuilding
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YourAppStudioBanner(
    appCount: Int,
    selectedModel: ZenvixModelSpec,
    isBuilding: Boolean,
    onCreateApp: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        border = BorderStroke(1.2.dp, AnimatedGradientBorderBrush()),
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ZenvixLivingOrb(isActive = false, size = 44.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "$appCount Created ${if (appCount == 1) "App" else "Apps"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = LoraFontFamily)
                    )
                    Text(
                        text = "Open any app to run or modify with ${selectedModel.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onCreateApp,
                enabled = !isBuilding,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("header_create_app_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create App")
            }
        }
    }
}

@Composable
private fun UserAppCard(
    app: UserApp,
    modelLabel: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = APP_ACCENT_COLORS[app.accentIndex.toInt().coerceIn(0, APP_ACCENT_COLORS.lastIndex)]
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onOpen,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .springPressScale(interactionSource, pressedScale = 0.98f)
            .testTag("user_app_card_${app.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "v${app.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = modelLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (app.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = app.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (app.lastPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Last update: ${app.lastPrompt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onOpen) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open & Modify")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpen) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = "Make changes with AI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete app",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
