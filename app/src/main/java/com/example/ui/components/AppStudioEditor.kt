package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.configureZenvixWebView
import com.example.model.UserApp
import com.example.model.ZenvixModelSpec
import com.example.ui.theme.CodeSurfaceDark
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LoraFontFamily
import com.example.viewmodel.AppBuilderState

val APP_ACCENT_COLORS = listOf(
    Color(0xFFC85A32),
    Color(0xFF2E7D5B),
    Color(0xFF3B6FD4),
    Color(0xFF8E4EC6),
    Color(0xFFB7791F),
    Color(0xFF0D7C8C)
)

@Composable
fun CreateAppDialog(
    selectedModel: ZenvixModelSpec,
    initialName: String = "",
    initialPrompt: String = "",
    initialAccent: Long = 0L,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, prompt: String, accentIndex: Long) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember { mutableStateOf("") }
    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var accentIndex by remember(initialAccent) { mutableLongStateOf(initialAccent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Create New App",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = LoraFontFamily)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Building with ${selectedModel.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("App Name") },
                    placeholder = { Text("e.g., Smart Expense Tracker") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_app_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 240) description = it },
                    label = { Text("Short Tagline (optional)") },
                    placeholder = { Text("e.g., Daily budget tracker with charts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= 1000) prompt = it },
                    label = { Text("Describe your app features") },
                    placeholder = { Text("Design a modern interactive app with dark mode, animations, and local save...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_app_prompt_input")
                )
                Text("Theme Accent", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    APP_ACCENT_COLORS.forEachIndexed { index, color ->
                        val selected = accentIndex == index.toLong()
                        val dotScale by animateFloatAsState(
                            targetValue = if (selected) 1.2f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "app_accent_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = dotScale
                                    scaleY = dotScale
                                }
                                .clip(CircleShape)
                                .background(color)
                                .clickable { accentIndex = index.toLong() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name.trim().ifBlank { "My Zenvix App" },
                        description.trim(),
                        prompt.trim(),
                        accentIndex
                    )
                },
                enabled = prompt.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_create_app_button")
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create App")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRunnerAndEditorView(
    app: UserApp,
    selectedModel: ZenvixModelSpec,
    builderState: AppBuilderState,
    onBack: () -> Unit,
    onApplyAiChange: (changePrompt: String) -> Unit,
    onSaveManualCode: (newName: String, newDesc: String, newHtml: String) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showCodeTab by remember { mutableStateOf(false) }
    var changePrompt by remember { mutableStateOf("") }

    var editedName by remember(app.id, app.name) { mutableStateOf(app.name) }
    var editedDesc by remember(app.id, app.description) { mutableStateOf(app.description) }
    var editedHtml by remember(app.id, app.version, app.htmlCode) { mutableStateOf(app.htmlCode) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "v${app.version} • ${selectedModel.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("app_studio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Your App list"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(app.htmlCode))
                            onNotify("App HTML copied")
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy app source")
                    }
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, app.name)
                                putExtra(Intent.EXTRA_TEXT, app.htmlCode)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share ${app.name}"))
                        }
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share app code")
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
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            AmbientAuroraBackground(
                intensity = if (builderState.isBuilding) 0.9f else 0.45f,
                isActive = true
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !showCodeTab,
                        onClick = { showCodeTab = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Live App")
                    }
                    SegmentedButton(
                        selected = showCodeTab,
                        onClick = { showCodeTab = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Edit Code")
                    }
                }

                AnimatedVisibility(visible = builderState.isBuilding) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.2.dp, AnimatedGradientBorderBrush()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ZenvixLivingOrb(isActive = true, size = 28.dp) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = builderState.statusText.ifBlank { "Updating your app…" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (builderState.streamingChars > 0) {
                                    Text(
                                        text = "${builderState.streamingChars} chars generated",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            TypingWaveDots(dotSize = 5.dp)
                        }
                    }
                }

                AnimatedContent(
                    targetState = showCodeTab,
                    transitionSpec = {
                        val dir = if (targetState) 1 else -1
                        (slideInHorizontally(tween(240)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(180)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = "app_runner_code_switch"
                ) { isCodeMode ->
                    if (!isCodeMode) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            color = Color.White
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        configureZenvixWebView(
                                            webView = this,
                                            enableJavaScript = true,
                                            enableDomStorage = true,
                                            onOutcome = { outcome -> onNotify(outcome.message) }
                                        )
                                        loadDataWithBaseURL("https://zenvix.local/", app.htmlCode, "text/html", "UTF-8", null)
                                    }
                                },
                                update = { webView ->
                                    webView.loadDataWithBaseURL("https://zenvix.local/", app.htmlCode, "text/html", "UTF-8", null)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { if (it.length <= 80) editedName = it },
                                label = { Text("App Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editedDesc,
                                onValueChange = { if (it.length <= 300) editedDesc = it },
                                label = { Text("Description") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editedHtml,
                                onValueChange = { editedHtml = it },
                                label = { Text("HTML / CSS / JS Source") },
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                            )
                            Button(
                                onClick = { onSaveManualCode(editedName, editedDesc, editedHtml) },
                                enabled = editedHtml.isNotBlank() && !builderState.isBuilding,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Manual Changes")
                            }
                        }
                    }
                }

                // AI "Make Changes" Bar
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.2.dp, AnimatedGradientBorderBrush()),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = changePrompt,
                            onValueChange = { changePrompt = it },
                            placeholder = {
                                Text(
                                    text = "Ask AI to modify this app (e.g. add dark neon theme)…",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("modify_app_prompt_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val req = changePrompt.trim()
                                if (req.isNotEmpty()) {
                                    changePrompt = ""
                                    onApplyAiChange(req)
                                }
                            },
                            enabled = changePrompt.isNotBlank() && !builderState.isBuilding,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("apply_app_changes_button")
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Modify")
                        }
                    }
                }
            }
        }
    }
}
