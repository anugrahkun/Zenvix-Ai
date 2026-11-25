package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PendingAttachment
import com.example.model.StylePreset
import com.example.utils.AttachmentUtils
import kotlinx.coroutines.launch

@Composable
fun ChatComposerDock(
    draftText: String,
    onDraftChange: (String) -> Unit,
    isGenerating: Boolean,
    thinkingEnabled: Boolean,
    selectedStyle: StylePreset,
    pendingAttachment: PendingAttachment?,
    onToggleThinking: () -> Unit,
    onSelectStyle: (StylePreset) -> Unit,
    onSetAttachment: (PendingAttachment) -> Unit,
    onClearAttachment: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenLiveVoice: () -> Unit = {},
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAttachMenu by remember { mutableStateOf(false) }
    var showStyleMenu by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                AttachmentUtils.loadImageAttachment(context, uri)
                    .onSuccess { onSetAttachment(it) }
                    .onFailure { onNotify(it.localizedMessage ?: "Unable to load image") }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                AttachmentUtils.loadDocumentAttachment(context, uri)
                    .onSuccess { onSetAttachment(it) }
                    .onFailure { onNotify(it.localizedMessage ?: "Unable to read file") }
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                val combined = if (draftText.isBlank()) spoken else "${draftText.trimEnd()} $spoken"
                onDraftChange(combined)
            }
        }
    }

    val canSend = draftText.isNotBlank() || pendingAttachment != null
    val sendInteractionSource = remember { MutableInteractionSource() }
    val borderColor by animateColorAsState(
        targetValue = when {
            isGenerating -> MaterialTheme.colorScheme.primary
            canSend -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(280),
        label = "composer_border_color"
    )
    val shimmerProgress = rememberShimmerSweepProgress(enabled = isGenerating, durationMillis = 1900)
    val borderBrush = if (isGenerating) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary
            )
        )
    } else {
        Brush.horizontalGradient(listOf(borderColor, borderColor))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(if (isGenerating || canSend) 1.4.dp else 1.dp, borderBrush),
            tonalElevation = if (canSend || isGenerating) 5.dp else 3.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                AnimatedVisibility(
                    visible = pendingAttachment != null,
                    enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    pendingAttachment?.let { att ->
                        AttachmentPreviewChip(
                            attachment = att,
                            onRemove = onClearAttachment,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                TextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    placeholder = {
                        Text(
                            text = "Ask Zenvix anything…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions.Default,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_prompt_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val attachIconRotation by animateFloatAsState(
                            targetValue = if (showAttachMenu) 45f else 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "attach_rotation"
                        )
                        Box {
                            IconButton(
                                onClick = { showAttachMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("attach_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Attach photo, file, or voice",
                                    tint = if (showAttachMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.graphicsLayer { rotationZ = attachIconRotation }
                                )
                            }
                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Photo or Screenshot") },
                                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                                    onClick = {
                                        showAttachMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("PDF, Code, or Document") },
                                    leadingIcon = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
                                    onClick = {
                                        showAttachMenu = false
                                        documentPickerLauncher.launch(
                                            arrayOf("application/pdf", "text/*", "application/json")
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Voice Dictation") },
                                    leadingIcon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
                                    onClick = {
                                        showAttachMenu = false
                                        try {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                )
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Zenvix AI")
                                            }
                                            speechLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            onNotify("Voice dictation is unavailable on this device.")
                                        }
                                    }
                                )
                            }
                        }

                        val thinkIconScale by animateFloatAsState(
                            targetValue = if (thinkingEnabled) 1.15f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "think_scale"
                        )
                        FilterChip(
                            selected = thinkingEnabled,
                            onClick = onToggleThinking,
                            label = {
                                Text(
                                    text = "Think",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer {
                                            scaleX = thinkIconScale
                                            scaleY = thinkIconScale
                                        }
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("thinking_toggle_chip")
                        )

                        Box {
                            FilterChip(
                                selected = selectedStyle != StylePreset.BALANCED,
                                onClick = { showStyleMenu = true },
                                label = {
                                    Text(
                                        text = selectedStyle.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                modifier = Modifier.testTag("style_selector_chip")
                            )
                            DropdownMenu(
                                expanded = showStyleMenu,
                                onDismissRequest = { showStyleMenu = false }
                            ) {
                                StylePreset.entries.forEach { preset ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(preset.label, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    preset.shortHint,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            showStyleMenu = false
                                            onSelectStyle(preset)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    val liveInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = onOpenLiveVoice,
                        interactionSource = liveInteraction,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(48.dp)
                            .springPressScale(liveInteraction, pressedScale = 0.92f)
                            .testTag("live_voice_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Open Zenvix Live Voice assistant",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    val sendBgColor by animateColorAsState(
                        targetValue = when {
                            isGenerating -> MaterialTheme.colorScheme.errorContainer
                            canSend -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = tween(220),
                        label = "send_bg_color"
                    )
                    val readyScale by animateFloatAsState(
                        targetValue = if (canSend || isGenerating) 1.04f else 0.96f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "send_ready_scale"
                    )
                    val genTransition = rememberInfiniteTransition(label = "gen_pulse")
                    val genPulse by genTransition.animateFloat(
                        initialValue = 0.94f,
                        targetValue = 1.06f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(750),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "gen_pulse_scale"
                    )
                    val activePulse = if (isGenerating) genPulse else readyScale

                    Surface(
                        onClick = { if (isGenerating) onStop() else if (canSend) onSend() },
                        enabled = isGenerating || canSend,
                        interactionSource = sendInteractionSource,
                        shape = CircleShape,
                        color = sendBgColor,
                        modifier = Modifier
                            .size(48.dp)
                            .springPressScale(sendInteractionSource, pressedScale = 0.90f)
                            .graphicsLayer {
                                scaleX = activePulse
                                scaleY = activePulse
                                if (isGenerating && shimmerProgress >= 0f) {
                                    alpha = 0.92f + 0.08f * (shimmerProgress.coerceIn(0f, 1f))
                                }
                            }
                            .testTag(if (isGenerating) "stop_button" else "send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = isGenerating,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.7f) + fadeIn(tween(150))) togetherWith
                                        (scaleOut(targetScale = 0.7f) + fadeOut(tween(120)))
                                },
                                label = "send_stop_icon"
                            ) { generating ->
                                Icon(
                                    imageVector = if (generating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = if (generating) "Stop generating" else "Send message",
                                    tint = when {
                                        generating -> MaterialTheme.colorScheme.onErrorContainer
                                        canSend -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewChip(
    attachment: PendingAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> {
                    Image(
                        bitmap = attachment.bitmap.asImageBitmap(),
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                is PendingAttachment.DocumentAttachment -> {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
