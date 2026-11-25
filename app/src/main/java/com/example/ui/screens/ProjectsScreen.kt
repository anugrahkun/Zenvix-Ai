package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AiProject
import com.example.model.StylePreset
import com.example.ui.components.AmbientAuroraBackground
import com.example.ui.components.FloatingSparkField
import com.example.ui.components.StaggeredReveal
import com.example.ui.components.springPressScale
import com.example.ui.theme.LoraFontFamily
import com.example.viewmodel.UiState

private val PROJECT_ACCENTS = listOf(
    Color(0xFFC85A32),
    Color(0xFF2E7D5B),
    Color(0xFF3B6FD4),
    Color(0xFF8E4EC6),
    Color(0xFFB7791F),
    Color(0xFF0D7C8C)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    projectsState: UiState<List<AiProject>>,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onStartChatInProject: (AiProject) -> Unit,
    onSaveProject: (
        existingId: String?,
        name: String,
        description: String,
        systemPrompt: String,
        stylePreset: StylePreset,
        accentIndex: Long
    ) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingProject by remember { mutableStateOf<AiProject?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Projects",
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
                            editingProject = null
                            showCreateDialog = true
                        },
                        modifier = Modifier.testTag("create_project_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create project")
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
            AmbientAuroraBackground(intensity = 0.44f, isActive = true)
            FloatingSparkField(alphaMultiplier = 0.42f)

            when (projectsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = projectsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val projects = projectsState.data
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (projects.isEmpty()) {
                            item {
                                ProjectsEmptyState(
                                    onCreateCustom = {
                                        editingProject = null
                                        showCreateDialog = true
                                    },
                                    onQuickTemplate = { name, desc, prompt, style, accent ->
                                        onSaveProject(null, name, desc, prompt, style, accent)
                                    }
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = "Custom cloud workspaces with persistent instructions & style.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            itemsIndexed(projects, key = { _, project -> project.id }) { index, project ->
                                Box(modifier = Modifier.animateItem()) {
                                    StaggeredReveal(index = index) {
                                        ProjectCard(
                                            project = project,
                                            onStartChat = { onStartChatInProject(project) },
                                            onEdit = {
                                                editingProject = project
                                                showCreateDialog = true
                                            },
                                            onDelete = { onDeleteProject(project.id) }
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

    if (showCreateDialog) {
        ProjectEditorDialog(
            initial = editingProject,
            onDismiss = { showCreateDialog = false },
            onSave = { name, desc, prompt, style, accent ->
                onSaveProject(editingProject?.id, name, desc, prompt, style, accent)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun ProjectsEmptyState(
    onCreateCustom: () -> Unit,
    onQuickTemplate: (String, String, String, StylePreset, Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderSpecial,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Create AI Projects",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = LoraFontFamily)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Give Zenvix AI tailored instructions, domain rules, and a default writing style that sync across devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCreateCustom,
                    modifier = Modifier.testTag("empty_create_project_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Custom Project")
                }
            }
        }

        Text(
            text = "Quick Starter Templates",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        StarterTemplateCard(
            title = "Android & Kotlin Architect",
            description = "Clean Architecture, Jetpack Compose, M3 & Coroutines expert.",
            accent = PROJECT_ACCENTS[0],
            onUse = {
                onQuickTemplate(
                    "Android & Kotlin Architect",
                    "Clean Architecture, Jetpack Compose, Material 3 & Coroutines expert.",
                    "You are a principal Android engineer. Write clean, idiomatic Kotlin with Jetpack Compose, Material 3, StateFlow, and explicit edge-case handling.",
                    StylePreset.CODE_ARCHITECT,
                    0L
                )
            }
        )
        StarterTemplateCard(
            title = "Bilingual Hinglish & English Partner",
            description = "Natural Hindi-English explanations, translations & crisp copy.",
            accent = PROJECT_ACCENTS[1],
            onUse = {
                onQuickTemplate(
                    "Hinglish & English Partner",
                    "Natural Hindi-English explanations, translations & crisp copy.",
                    "Communicate warmly and clearly. Support seamless Hinglish, Hindi, and polished English with practical everyday examples.",
                    StylePreset.BALANCED,
                    1L
                )
            }
        )
        StarterTemplateCard(
            title = "Web & Artifact Studio",
            description = "Build interactive HTML/CSS/JS widgets and SVG graphics.",
            accent = PROJECT_ACCENTS[2],
            onUse = {
                onQuickTemplate(
                    "Web & Artifact Studio",
                    "Build interactive HTML/CSS/JS widgets and SVG graphics.",
                    "Always produce self-contained, responsive HTML/CSS/JS or SVG deliverables inside <artifact> tags so they can be live-previewed immediately.",
                    StylePreset.CODE_ARCHITECT,
                    2L
                )
            }
        )
    }
}

@Composable
private fun StarterTemplateCard(
    title: String,
    description: String,
    accent: Color,
    onUse: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
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
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(onClick = onUse) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: AiProject,
    onStartChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = PROJECT_ACCENTS[project.accentIndex.toInt().coerceIn(0, PROJECT_ACCENTS.lastIndex)]
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onStartChat,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .springPressScale(interactionSource, pressedScale = 0.98f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = project.resolvedStyle().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (project.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (project.systemPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Instructions: ${project.systemPrompt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onStartChat) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Chat")
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit project")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete project",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectEditorDialog(
    initial: AiProject?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, StylePreset, Long) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var systemPrompt by remember(initial) { mutableStateOf(initial?.systemPrompt.orEmpty()) }
    var style by remember(initial) { mutableStateOf(initial?.resolvedStyle() ?: StylePreset.BALANCED) }
    var accentIndex by remember(initial) { mutableLongStateOf(initial?.accentIndex ?: 0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "New Project" else "Edit Project",
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("project_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 240) description = it },
                    label = { Text("Short description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { if (it.length <= 4000) systemPrompt = it },
                    label = { Text("System instructions") },
                    placeholder = { Text("e.g., Always explain in Hinglish with clean code examples...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("project_prompt_input")
                )
                Text("Default Style", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StylePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = style == preset,
                            onClick = { style = preset },
                            label = { Text(preset.label) }
                        )
                    }
                }
                Text("Accent Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PROJECT_ACCENTS.forEachIndexed { index, color ->
                        val selected = accentIndex == index.toLong()
                        val dotScale by animateFloatAsState(
                            targetValue = if (selected) 1.18f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "project_color_scale"
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
                onClick = { onSave(name.trim(), description.trim(), systemPrompt.trim(), style, accentIndex) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_project_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
