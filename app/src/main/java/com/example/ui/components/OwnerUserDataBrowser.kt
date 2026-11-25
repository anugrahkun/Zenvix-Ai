package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.AiProject
import com.example.model.AuthEvent
import com.example.model.AuthEventType
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.ExtractedArtifact
import com.example.model.SessionStatus
import com.example.model.UserApp
import com.example.model.UserProfile
import com.example.ui.theme.LoraFontFamily
import com.example.viewmodel.UiState
import java.text.DateFormat
import java.util.Date

@Composable
fun OwnerUsersDirectorySection(
    usersState: UiState<List<UserProfile>>,
    selectedUserId: String?,
    selectedChatId: String?,
    userAppsState: UiState<List<UserApp>>,
    userChatsState: UiState<List<ChatThread>>,
    userProjectsState: UiState<List<AiProject>>,
    userMessagesState: UiState<List<ChatMessage>>,
    onSelectUser: (String?) -> Unit,
    onSelectChat: (String?) -> Unit,
    onPreviewUserApp: (ExtractedArtifact) -> Unit,
    modifier: Modifier = Modifier
) {
    val users = (usersState as? UiState.Success)?.data ?: emptyList()
    val selectedUser = remember(users, selectedUserId) {
        users.firstOrNull { it.userId == selectedUserId }
    }

    if (selectedUser != null) {
        BackHandler {
            if (selectedChatId != null) {
                onSelectChat(null)
            } else {
                onSelectUser(null)
            }
        }

        OwnerSelectedUserInspector(
            user = selectedUser,
            selectedChatId = selectedChatId,
            userAppsState = userAppsState,
            userChatsState = userChatsState,
            userProjectsState = userProjectsState,
            userMessagesState = userMessagesState,
            onBackToUsers = { onSelectUser(null) },
            onSelectChat = onSelectChat,
            onPreviewUserApp = onPreviewUserApp,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (usersState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = usersState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            is UiState.Success -> {
                val totalChats = users.sumOf { it.chatCount }
                val totalApps = users.sumOf { it.appCount }
                val activeCount = users.count { it.resolvedSessionStatus() == SessionStatus.SIGNED_IN }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.People,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${users.size} Registered ${if (users.size == 1) "User" else "Users"} ($activeCount Signed In)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "$totalChats total chats • $totalApps created apps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (users.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "As users sign in with Google and use Zenvix AI, their profiles, created apps, chats, and projects appear here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    users.forEachIndexed { index, profile ->
                        StaggeredReveal(index = index) {
                            OwnerUserProfileRowCard(
                                profile = profile,
                                onClick = { onSelectUser(profile.userId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerUserProfileRowCard(
    profile: UserProfile,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .springPressScale(interaction, pressedScale = 0.98f)
            .testTag("owner_user_card_${profile.userId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profile.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = profile.photoUrl,
                    contentDescription = profile.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (profile.displayName.firstOrNull() ?: 'Z').uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val sessionStatus = profile.resolvedSessionStatus()
                val isSignedIn = sessionStatus == SessionStatus.SIGNED_IN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSignedIn) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = sessionStatus.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSignedIn) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = profile.email.ifBlank { profile.userId },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatBadge("${profile.appCount} Apps")
                    StatBadge("${profile.chatCount} Chats")
                    StatBadge(profile.lastModelId)
                }
            }
        }
    }
}

@Composable
private fun OwnerSelectedUserInspector(
    user: UserProfile,
    selectedChatId: String?,
    userAppsState: UiState<List<UserApp>>,
    userChatsState: UiState<List<ChatThread>>,
    userProjectsState: UiState<List<AiProject>>,
    userMessagesState: UiState<List<ChatMessage>>,
    onBackToUsers: () -> Unit,
    onSelectChat: (String?) -> Unit,
    onPreviewUserApp: (ExtractedArtifact) -> Unit,
    modifier: Modifier = Modifier
) {
    var detailTab by remember(user.userId) { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (selectedChatId != null) onSelectChat(null) else onBackToUsers()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = LoraFontFamily),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email.ifBlank { user.userId },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (selectedChatId != null) {
            Text(
                text = "Conversation Messages",
                style = MaterialTheme.typography.titleSmall
            )
            when (userMessagesState) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                is UiState.Error -> Text(userMessagesState.message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    val msgs = userMessagesState.data
                    if (msgs.isEmpty()) {
                        Text(
                            text = "No messages in this thread.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        msgs.forEach { msg ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (msg.role == "user") {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (msg.role == "user") "User" else "AI (${msg.modelId ?: "cloud"})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 10,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = detailTab == 0,
                onClick = { detailTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
                Text("Apps")
            }
            SegmentedButton(
                selected = detailTab == 1,
                onClick = { detailTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
                Text("Chats")
            }
            SegmentedButton(
                selected = detailTab == 2,
                onClick = { detailTab = 2 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
                Text("Projects")
            }
        }

        when (detailTab) {
            0 -> {
                when (userAppsState) {
                    is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    is UiState.Error -> Text(userAppsState.message, color = MaterialTheme.colorScheme.error)
                    is UiState.Success -> {
                        if (userAppsState.data.isEmpty()) {
                            EmptyDataNote("This user hasn't created any apps in Your App yet.")
                        } else {
                            userAppsState.data.forEach { app ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Apps,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(app.name, style = MaterialTheme.typography.titleSmall)
                                            }
                                            StatBadge("v${app.version} • ${app.modelId}")
                                        }
                                        if (app.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = app.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                onPreviewUserApp(
                                                    ExtractedArtifact(
                                                        title = "${app.name} (v${app.version})",
                                                        language = "html",
                                                        content = app.htmlCode
                                                    )
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Preview App")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                when (userChatsState) {
                    is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    is UiState.Error -> Text(userChatsState.message, color = MaterialTheme.colorScheme.error)
                    is UiState.Success -> {
                        if (userChatsState.data.isEmpty()) {
                            EmptyDataNote("This user has no cloud chats yet.")
                        } else {
                            userChatsState.data.forEach { chat ->
                                Surface(
                                    onClick = { onSelectChat(chat.id) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(chat.title, style = MaterialTheme.typography.titleSmall)
                                            if (chat.lastMessagePreview.isNotBlank()) {
                                                Text(
                                                    text = chat.lastMessagePreview,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        StatBadge("${chat.messageCount} msgs")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                when (userProjectsState) {
                    is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    is UiState.Error -> Text(userProjectsState.message, color = MaterialTheme.colorScheme.error)
                    is UiState.Success -> {
                        if (userProjectsState.data.isEmpty()) {
                            EmptyDataNote("This user hasn't created any Projects yet.")
                        } else {
                            userProjectsState.data.forEach { project ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.FolderSpecial,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(project.name, style = MaterialTheme.typography.titleSmall)
                                        }
                                        if (project.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = project.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
fun OwnerAuthActivitySection(
    eventsState: UiState<List<AuthEvent>>,
    onDeleteEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (eventsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = eventsState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            is UiState.Success -> {
                val events = eventsState.data
                if (events.isEmpty()) {
                    EmptyDataNote("No Login or Logout activity recorded yet. Real-time entries appear whenever any user signs in or out with Google.")
                } else {
                    events.forEachIndexed { index, event ->
                        StaggeredReveal(index = index) {
                            AuthEventRowCard(
                                event = event,
                                onDelete = { onDeleteEvent(event.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthEventRowCard(
    event: AuthEvent,
    onDelete: () -> Unit
) {
    val eventType = event.resolvedEventType()
    val isLogin = eventType == AuthEventType.LOGIN
    val accentColor = if (isLogin) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val formattedTime = remember(event.createdAt) {
        val date: Date? = event.createdAt?.toDate()
        if (date != null) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
        } else {
            "Just now"
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("owner_auth_event_${event.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLogin) Icons.AutoMirrored.Rounded.Login else Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = eventType.label,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.displayName.ifBlank { "Zenvix User" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = eventType.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = event.email.ifBlank { event.userId },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$formattedTime • ${event.deviceInfo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete activity entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyDataNote(text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
