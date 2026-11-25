package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AiProject
import com.example.model.AuthEvent
import com.example.model.BugCategory
import com.example.model.BugReport
import com.example.model.BugStatus
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.ExtractedArtifact
import com.example.model.UserApp
import com.example.model.UserProfile
import com.example.model.ZENVIX_OWNER_EMAIL
import com.example.ui.components.AmbientAuroraBackground
import com.example.ui.components.AnimatedGradientBorderBrush
import com.example.ui.components.FloatingSparkField
import com.example.ui.components.OwnerAuthActivitySection
import com.example.ui.components.OwnerUsersDirectorySection
import com.example.ui.components.StaggeredReveal
import com.example.ui.components.ZenvixLivingOrb
import com.example.ui.components.springPressScale
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LoraFontFamily
import com.example.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportAndOwnerScreen(
    isOwnerAdmin: Boolean,
    isSubmittingBug: Boolean,
    diagnosticsPreview: String,
    prefillBugError: String?,
    myBugReportsState: UiState<List<BugReport>>,
    ownerBugInboxState: UiState<List<BugReport>>,
    allUsersState: UiState<List<UserProfile>>,
    authEventsState: UiState<List<AuthEvent>>,
    selectedOwnerUserId: String?,
    selectedOwnerChatId: String?,
    selectedUserAppsState: UiState<List<UserApp>>,
    selectedUserChatsState: UiState<List<ChatThread>>,
    selectedUserProjectsState: UiState<List<AiProject>>,
    selectedUserMessagesState: UiState<List<ChatMessage>>,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onSelectOwnerUser: (String?) -> Unit,
    onSelectOwnerChat: (String?) -> Unit,
    onPreviewUserApp: (ExtractedArtifact) -> Unit,
    onDeleteAuthEvent: (String) -> Unit,
    onSubmitBugReport: (BugCategory, String, String, String?, () -> Unit) -> Unit,
    onOwnerUpdateBugStatus: (String, BugStatus, String) -> Unit,
    onDeleteBugReport: (String) -> Unit,
    onClearPrefillError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(isOwnerAdmin) { mutableIntStateOf(if (isOwnerAdmin) 0 else 3) }
    var showReportDialog by remember(prefillBugError) { mutableStateOf(!prefillBugError.isNullOrBlank()) }
    var triagingReport by remember { mutableStateOf<BugReport?>(null) }

    val activeBugState = if (isOwnerAdmin && selectedTab == 2) ownerBugInboxState else myBugReportsState

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isOwnerAdmin) "Owner Dashboard" else "Bug Report & Safety",
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
                        onClick = { showReportDialog = true },
                        modifier = Modifier.testTag("topbar_report_bug_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Submit bug report")
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
            AmbientAuroraBackground(intensity = 0.56f, isActive = true)
            FloatingSparkField(alphaMultiplier = 0.52f)

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StaggeredReveal(index = 0) {
                        CloudSafetyBannerCard(isOwnerAdmin = isOwnerAdmin)
                    }
                }

                item {
                    StaggeredReveal(index = 1) {
                        val reportInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { showReportDialog = true },
                            interactionSource = reportInteraction,
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.3.dp, AnimatedGradientBorderBrush()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPressScale(reportInteraction, pressedScale = 0.98f)
                                .testTag("open_bug_report_dialog_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ZenvixLivingOrb(isActive = true, size = 46.dp) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.BugReport,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Report Bug or Request Fix",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Automatically attaches model & device logs and delivers directly to Zenvix AI owner.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (isOwnerAdmin) {
                    val userCount = (allUsersState as? UiState.Success)?.data?.size ?: 0
                    val eventCount = (authEventsState as? UiState.Success)?.data?.size ?: 0
                    val inboxCount = (ownerBugInboxState as? UiState.Success)?.data?.size ?: 0
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                leadingIcon = { Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Users ($userCount)") },
                                modifier = Modifier.testTag("owner_tab_users")
                            )
                            FilterChip(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Login / Logout ($eventCount)") },
                                modifier = Modifier.testTag("owner_tab_auth_events")
                            )
                            FilterChip(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                leadingIcon = { Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Bug Inbox ($inboxCount)") },
                                modifier = Modifier.testTag("owner_tab_bug_inbox")
                            )
                            FilterChip(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                leadingIcon = { Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("My Bugs") },
                                modifier = Modifier.testTag("owner_tab_my_bugs")
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "Your Submitted Reports",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isOwnerAdmin && selectedTab == 0) {
                    item {
                        OwnerUsersDirectorySection(
                            usersState = allUsersState,
                            selectedUserId = selectedOwnerUserId,
                            selectedChatId = selectedOwnerChatId,
                            userAppsState = selectedUserAppsState,
                            userChatsState = selectedUserChatsState,
                            userProjectsState = selectedUserProjectsState,
                            userMessagesState = selectedUserMessagesState,
                            onSelectUser = onSelectOwnerUser,
                            onSelectChat = onSelectOwnerChat,
                            onPreviewUserApp = onPreviewUserApp
                        )
                    }
                } else if (isOwnerAdmin && selectedTab == 1) {
                    item {
                        OwnerAuthActivitySection(
                            eventsState = authEventsState,
                            onDeleteEvent = onDeleteAuthEvent
                        )
                    }
                } else {
                    when (activeBugState) {
                        is UiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        is UiState.Error -> {
                            item {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = activeBugState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                        is UiState.Success -> {
                            val reports = activeBugState.data
                            if (reports.isEmpty()) {
                                item {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (isOwnerAdmin && selectedTab == 2) {
                                                    "No user bug reports yet"
                                                } else {
                                                    "No bug reports submitted yet"
                                                },
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (isOwnerAdmin && selectedTab == 2) {
                                                    "When any signed-in user reports an issue, their description & diagnostics appear here."
                                                } else {
                                                    "If anything breaks or needs a fix, tap Report Bug above to notify the owner."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                itemsIndexed(reports, key = { _, r -> r.id }) { index, report ->
                                    Box(modifier = Modifier.animateItem()) {
                                        StaggeredReveal(index = index) {
                                            BugReportCard(
                                                report = report,
                                                showReporterDetails = isOwnerAdmin && selectedTab == 2,
                                                canTriage = isOwnerAdmin && selectedTab == 2,
                                                onTriage = { triagingReport = report },
                                                onDelete = { onDeleteBugReport(report.id) }
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

    if (showReportDialog) {
        ReportBugDialog(
            diagnosticsPreview = diagnosticsPreview,
            initialErrorSnippet = prefillBugError,
            isSubmitting = isSubmittingBug,
            onDismiss = {
                showReportDialog = false
                onClearPrefillError()
            },
            onSubmit = { category, title, description ->
                onSubmitBugReport(category, title, description, prefillBugError) {
                    showReportDialog = false
                    onClearPrefillError()
                }
            }
        )
    }

    triagingReport?.let { report ->
        OwnerTriageDialog(
            report = report,
            onDismiss = { triagingReport = null },
            onSave = { status, note ->
                onOwnerUpdateBugStatus(report.id, status, note)
                triagingReport = null
            }
        )
    }
}

@Composable
private fun CloudSafetyBannerCard(isOwnerAdmin: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOwnerAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isOwnerAdmin) {
                        "Owner Mode Active ($ZENVIX_OWNER_EMAIL)"
                    } else {
                        "Cloud Data Privacy & Bug Protection"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isOwnerAdmin) {
                        "Every Google user's Login/Logout activity, email, profile, chats, created apps & bug reports sync to your Owner Dashboard. Google handles passwords directly and never exposes them."
                    } else {
                        "Google protects your email & password (Zenvix never sees or stores passwords). Your chats & apps stay private in your cloud account, and API keys stay encrypted only on your device."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BugReportCard(
    report: BugReport,
    showReporterDetails: Boolean,
    canTriage: Boolean,
    onTriage: () -> Unit,
    onDelete: () -> Unit
) {
    val status = report.resolvedStatus()
    val statusColor = when (status) {
        BugStatus.OPEN -> MaterialTheme.colorScheme.error
        BugStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        BugStatus.FIXED -> MaterialTheme.colorScheme.tertiary
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = report.resolvedCategory().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = report.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = report.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showReporterDetails) {
                Text(
                    text = "Reporter: ${report.reporterName} (${report.reporterEmail.ifBlank { report.userId }})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (report.diagnostics.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = report.diagnostics,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (report.ownerNote.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Owner Fix Note: ${report.ownerNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (canTriage) {
                    TextButton(onClick = onTriage) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Update Status")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete report",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportBugDialog(
    diagnosticsPreview: String,
    initialErrorSnippet: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (BugCategory, String, String) -> Unit
) {
    var category by remember { mutableStateOf(BugCategory.AI_CHAT) }
    var title by remember(initialErrorSnippet) {
        mutableStateOf(
            if (!initialErrorSnippet.isNullOrBlank()) "Error: ${initialErrorSnippet.take(60)}" else ""
        )
    }
    var description by remember(initialErrorSnippet) {
        mutableStateOf(
            if (!initialErrorSnippet.isNullOrBlank()) "Observed issue: $initialErrorSnippet\n\nSteps to reproduce:\n" else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Report Bug to Owner",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = LoraFontFamily)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BugCategory.entries.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("Issue summary") },
                    placeholder = { Text("e.g., Streaming stops on Opus 5.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bug_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 4000) description = it },
                    label = { Text("What happened?") },
                    placeholder = { Text("Describe the bug or fix you need...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("bug_description_input")
                )
                Text(
                    text = "Auto-attached Diagnostics:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diagnosticsPreview,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(category, title.trim(), description.trim()) },
                enabled = title.trim().isNotEmpty() && description.trim().isNotEmpty() && !isSubmitting,
                modifier = Modifier.testTag("submit_bug_report_button")
            ) {
                Text(if (isSubmitting) "Sending…" else "Send to Owner")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OwnerTriageDialog(
    report: BugReport,
    onDismiss: () -> Unit,
    onSave: (BugStatus, String) -> Unit
) {
    var status by remember(report) { mutableStateOf(report.resolvedStatus()) }
    var ownerNote by remember(report) { mutableStateOf(report.ownerNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Owner Bug Fix Update",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = LoraFontFamily)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(report.title, style = MaterialTheme.typography.titleSmall)
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BugStatus.entries.forEach { item ->
                        FilterChip(
                            selected = status == item,
                            onClick = { status = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = ownerNote,
                    onValueChange = { if (it.length <= 1000) ownerNote = it },
                    label = { Text("Fix note for user") },
                    placeholder = { Text("e.g., Bug fixed in latest cloud update.") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(status, ownerNote) }) {
                Text("Save Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
