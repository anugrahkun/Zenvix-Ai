package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhoneEnabled
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.ContactCandidate
import com.example.core.DeviceActionStatus
import com.example.data.LiveVoicePhase
import com.example.data.LiveVoiceUiState
import com.example.ui.theme.LoraFontFamily
import kotlin.math.sin

private val LIVE_VOICE_OPTIONS = listOf("AOEDE", "PUCK", "FENRIR", "KORE", "CHARON")
private val SIRI_QUICK_COMMANDS = listOf(
    "Torch on",
    "Torch off",
    "Battery",
    "Open WhatsApp",
    "Wi-Fi settings",
    "Time kya hua hai"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZenvixLiveVoiceSheet(
    state: LiveVoiceUiState,
    onDismiss: () -> Unit,
    onStartOrReconnect: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onToggleOfflineMode: () -> Unit,
    onSubmitOfflineCommand: (String) -> Unit,
    onSelectDisambiguatedContact: (ContactCandidate) -> Unit,
    onPermissionResult: (permission: String, granted: Boolean) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var quickCommandText by remember { mutableStateOf("") }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPerm = granted
        if (granted) {
            onStartOrReconnect()
        }
    }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionResult(Manifest.permission.READ_CONTACTS, granted)
    }

    val callPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionResult(Manifest.permission.CALL_PHONE, granted)
    }

    LaunchedEffect(state.isVisible, hasAudioPerm) {
        if (state.isVisible && hasAudioPerm && state.phase == LiveVoicePhase.IDLE && !state.isOfflineMode) {
            onStartOrReconnect()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.transcripts.size, state.actionEvents.size) {
        val total = state.transcripts.size
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AmbientAuroraBackground(
                intensity = if (state.phase == LiveVoicePhase.SPEAKING || state.phase == LiveVoicePhase.LISTENING) 0.9f else 0.45f,
                isActive = state.phase != LiveVoicePhase.IDLE
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isOfflineMode) "Zenvix Offline Siri" else "Zenvix Live Voice",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = LoraFontFamily)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.isOfflineMode) Icons.Rounded.WifiOff else Icons.Rounded.Language,
                                contentDescription = null,
                                tint = if (state.isOfflineMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (state.isOfflineMode) {
                                    "Offline Siri Phone Control • Calls, Apps, Torch, Alarm"
                                } else {
                                    "Auto Multi-Language • Hindi, English, Hinglish & 70+"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    FilterChip(
                        selected = state.isOfflineMode,
                        onClick = onToggleOfflineMode,
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.isOfflineMode) Icons.Rounded.WifiOff else Icons.Rounded.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (state.isOfflineMode) "Offline Siri" else "Gemini Live",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("live_voice_mode_toggle_chip")
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("live_voice_close_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close live voice")
                    }
                }

                // Voice selector chips (online Gemini Live voices)
                AnimatedVisibility(visible = !state.isOfflineMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LIVE_VOICE_OPTIONS.forEach { voice ->
                            FilterChip(
                                selected = state.selectedVoice == voice,
                                onClick = { onSelectVoice(voice) },
                                label = {
                                    Text(
                                        text = voice.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }

                // Siri quick command chips (especially handy offline)
                AnimatedVisibility(visible = state.isOfflineMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SIRI_QUICK_COMMANDS.forEach { cmd ->
                            AssistChip(
                                onClick = { onSubmitOfflineCommand(cmd) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when {
                                            cmd.contains("Torch") -> Icons.Rounded.FlashlightOn
                                            cmd.contains("Battery") -> Icons.Rounded.BatteryChargingFull
                                            cmd.contains("Wi-Fi") -> Icons.Rounded.Wifi
                                            else -> Icons.Rounded.PhoneEnabled
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(text = cmd, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                }

                // Central living voice orb & visualizer
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val activeEnergy = maxOf(state.micLevel, state.speakerLevel)
                        val orbScale by animateFloatAsState(
                            targetValue = 1f + activeEnergy * 0.16f,
                            animationSpec = spring(stiffness = 380f),
                            label = "voice_orb_energy"
                        )
                        ZenvixLivingOrb(
                            isActive = state.phase == LiveVoicePhase.LISTENING ||
                                state.phase == LiveVoicePhase.SPEAKING ||
                                state.phase == LiveVoicePhase.EXECUTING_ACTION,
                            size = 78.dp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = orbScale
                                scaleY = orbScale
                            }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_zenvix_mark_fg),
                                contentDescription = "Zenvix Live orb",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }

                        LiveAudioBars(
                            energy = activeEnergy,
                            isActive = state.phase == LiveVoicePhase.LISTENING || state.phase == LiveVoicePhase.SPEAKING,
                            isSpeaking = state.phase == LiveVoicePhase.SPEAKING
                        )

                        Text(
                            text = when {
                                !hasAudioPerm -> "Microphone access needed to talk"
                                state.isOfflineMode && state.phase == LiveVoicePhase.LISTENING ->
                                    if (state.isMuted) "Offline Siri mic muted" else "Listening Offline (Siri Mode)…"
                                state.isOfflineMode && state.phase == LiveVoicePhase.SPEAKING ->
                                    "Offline Siri speaking…"
                                state.isOfflineMode && state.phase == LiveVoicePhase.IDLE ->
                                    state.statusNotice ?: "Offline Siri ready"
                                state.phase == LiveVoicePhase.CONNECTING -> "Connecting Gemini Live audio…"
                                state.phase == LiveVoicePhase.SPEAKING -> "Zenvix is speaking (speak anytime to interrupt)"
                                state.phase == LiveVoicePhase.EXECUTING_ACTION -> "Running device action…"
                                state.phase == LiveVoicePhase.THINKING -> "Thinking…"
                                state.phase == LiveVoicePhase.LISTENING -> if (state.isMuted) "Mic muted" else "Listening in any language…"
                                state.phase == LiveVoicePhase.ERROR -> state.errorMessage ?: "Connection paused"
                                else -> "Tap Start to talk"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (state.phase == LiveVoicePhase.ERROR) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )

                        if (!hasAudioPerm) {
                            Button(
                                onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier.testTag("live_voice_grant_mic_button")
                            ) {
                                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enable Microphone")
                            }
                        }
                    }
                }

                // Offline Siri / live command text bar
                if (state.isOfflineMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quickCommandText,
                            onValueChange = { quickCommandText = it },
                            placeholder = { Text("Type Siri command (e.g. Torch on, Call Mom)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val cmd = quickCommandText.trim()
                                    if (cmd.isNotEmpty()) {
                                        onSubmitOfflineCommand(cmd)
                                        quickCommandText = ""
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("offline_siri_command_input")
                        )
                        IconButton(
                            onClick = {
                                val cmd = quickCommandText.trim()
                                if (cmd.isNotEmpty()) {
                                    onSubmitOfflineCommand(cmd)
                                    quickCommandText = ""
                                }
                            },
                            modifier = Modifier.testTag("offline_siri_command_send")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Run Siri command")
                        }
                    }
                }

                // Runtime permission helper when Contacts or Direct Call permission is needed
                AnimatedVisibility(visible = state.pendingPermissionRequest != null) {
                    val neededPerm = state.pendingPermissionRequest
                    if (neededPerm != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (neededPerm == Manifest.permission.READ_CONTACTS) {
                                            "Contacts Permission Needed"
                                        } else {
                                            "Enable Direct Calling (Optional)"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = if (neededPerm == Manifest.permission.READ_CONTACTS) {
                                            "Allow Contacts so Zenvix can call saved contacts like Mom or Rahul."
                                        } else {
                                            "Dialer opened safely. Grant Phone permission for one-step calling."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (neededPerm == Manifest.permission.READ_CONTACTS) {
                                            contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                                        } else {
                                            callPermLauncher.launch(Manifest.permission.CALL_PHONE)
                                        }
                                    }
                                ) {
                                    Text("Allow")
                                }
                            }
                        }
                    }
                }

                // Contact disambiguation picker when multiple contacts match
                AnimatedVisibility(visible = state.pendingContactMatches.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Multiple contacts matched — tap to call:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            state.pendingContactMatches.forEach { candidate ->
                                OutlinedButton(
                                    onClick = { onSelectDisambiguatedContact(candidate) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${candidate.displayName} • ${candidate.phoneNumber}")
                                }
                            }
                        }
                    }
                }

                // Latest executed action badge
                state.actionEvents.lastOrNull()?.let { latest ->
                    val success = latest.outcome.executed
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (success) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (latest.outcome.status) {
                                    DeviceActionStatus.SUCCESS -> Icons.Rounded.CheckCircle
                                    DeviceActionStatus.OPENED_DIALER_PREFILLED -> Icons.Rounded.PhoneEnabled
                                    else -> Icons.Rounded.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = latest.outcome.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live transcripts feed
                if (state.transcripts.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.transcripts, key = { it.id }) { line ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (line.isUser) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = if (line.isUser) "You:" else "Zenvix:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (line.isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(52.dp)
                                    )
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onToggleMute,
                        enabled = hasAudioPerm && state.phase != LiveVoicePhase.IDLE,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("live_voice_mute_button")
                    ) {
                        Icon(
                            imageVector = if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isMuted) "Unmute" else "Mute")
                    }

                    if (state.phase == LiveVoicePhase.SPEAKING) {
                        OutlinedButton(
                            onClick = onInterrupt,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("live_voice_interrupt_button")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Interrupt")
                        }
                    } else if (state.phase == LiveVoicePhase.ERROR || state.phase == LiveVoicePhase.IDLE) {
                        Button(
                            onClick = {
                                if (hasAudioPerm) {
                                    onStartOrReconnect()
                                } else {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("live_voice_reconnect_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Voice")
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("live_voice_end_button")
                        ) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("End Voice")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAudioBars(
    energy: Float,
    isActive: Boolean,
    isSpeaking: Boolean
) {
    val transition = rememberInfiniteTransition(label = "live_bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "live_bars_phase"
    )
    val barColor = if (isSpeaking) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(28.dp)
    ) {
        for (i in 0 until 7) {
            val wave = if (isActive) {
                (0.28f + 0.45f * energy + 0.27f * ((sin(phase + i * 0.85f) + 1f) * 0.5f)).coerceIn(0.2f, 1f)
            } else {
                0.22f
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((6 + 22 * wave).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = if (isActive) 0.88f else 0.35f))
            )
        }
    }
}
