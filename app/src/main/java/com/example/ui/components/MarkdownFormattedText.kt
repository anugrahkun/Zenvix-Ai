package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExtractedArtifact
import com.example.ui.theme.CodeSurfaceDark
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LoraFontFamily

private sealed interface MdSegment {
    data class TextBlock(val text: String) : MdSegment
    data class CodeBlock(val language: String, val code: String) : MdSegment
}

@Composable
fun ThoughtProcessCard(
    thinkingText: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember(isStreaming) { mutableStateOf(isStreaming) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thinking_chevron_rot"
    )
    val pulseTransition = rememberInfiniteTransition(label = "thinking_icon_pulse")
    val iconPulse by pulseTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_icon_scale"
    )
    val shimmerProgress = rememberShimmerSweepProgress(enabled = isStreaming, durationMillis = 1800)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = if (isStreaming) 1.3.dp else 1.dp,
            brush = if (isStreaming) {
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    ),
                    start = Offset(shimmerProgress * 400f, 0f),
                    end = Offset((shimmerProgress + 0.5f) * 400f, 120f)
                )
            } else {
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                if (isStreaming) {
                                    scaleX = iconPulse
                                    scaleY = iconPulse
                                }
                            }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isStreaming) "Thinking…" else "Thought process",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isStreaming) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TypingWaveDots(dotSize = 4.dp)
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse thought process" else "Expand thought process",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }

            AnimatedVisibility(
                visible = expanded && thinkingText.isNotBlank(),
                enter = expandVertically(spring(dampingRatio = 0.85f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = thinkingText,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ArtifactCard(
    artifact: ExtractedArtifact,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val sheen = rememberShimmerSweepProgress(enabled = true, durationMillis = 3200)
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onOpen,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .springPressScale(interactionSource, pressedScale = 0.975f)
            .testTag("artifact_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.40f)),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryColor.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            start = Offset(size.width * (sheen - 0.25f), 0f),
                            end = Offset(size.width * (sheen + 0.15f), size.height)
                        )
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val previewType = if (artifact.supportsLivePreview()) {
                        "${artifact.language.uppercase()} • Interactive Preview"
                    } else {
                        "${artifact.language.uppercase()} • ${artifact.content.lines().size} lines"
                    }
                    Text(
                        text = previewType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open artifact studio",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownFormattedText(
    markdown: String,
    modifier: Modifier = Modifier,
    onCopyCode: (String) -> Unit = {}
) {
    val segments = remember(markdown) { parseMarkdownSegments(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryColor = MaterialTheme.colorScheme.primary

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            segments.forEach { segment ->
                when (segment) {
                    is MdSegment.CodeBlock -> {
                        CodeBlockCard(
                            language = segment.language,
                            code = segment.code,
                            onCopy = { onCopyCode(segment.code) }
                        )
                    }
                    is MdSegment.TextBlock -> {
                        val lines = segment.text.lines()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            lines.forEach { rawLine ->
                                val line = rawLine.trimEnd()
                                when {
                                    line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                                    line.startsWith("### ") -> Text(
                                        text = formatInlineMarkdown(line.removePrefix("### "), codeBg, primaryColor),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = LoraFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    line.startsWith("## ") -> Text(
                                        text = formatInlineMarkdown(line.removePrefix("## "), codeBg, primaryColor),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = LoraFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    line.startsWith("# ") -> Text(
                                        text = formatInlineMarkdown(line.removePrefix("# "), codeBg, primaryColor),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = LoraFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    line.startsWith("- ") || line.startsWith("* ") -> {
                                        Row(modifier = Modifier.padding(start = 6.dp)) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.width(16.dp)
                                            )
                                            Text(
                                                text = formatInlineMarkdown(line.drop(2), codeBg, primaryColor),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    line.startsWith("> ") -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = formatInlineMarkdown(line.removePrefix("> "), codeBg, primaryColor),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    else -> Text(
                                        text = formatInlineMarkdown(line, codeBg, primaryColor),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
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

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    onCopy: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = CodeSurfaceDark,
        border = BorderStroke(1.dp, Color(0xFF2E2924)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1815))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = Color(0xFFD6CEC4)
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        onCopy()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFFD6CEC4),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    ),
                    color = Color(0xFFF3EFE9)
                )
            }
        }
    }
}

private fun parseMarkdownSegments(input: String): List<MdSegment> {
    val regex = Regex("""```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```""")
    val result = mutableListOf<MdSegment>()
    var lastIndex = 0
    for (match in regex.findAll(input)) {
        if (match.range.first > lastIndex) {
            val text = input.substring(lastIndex, match.range.first).trim()
            if (text.isNotEmpty()) result.add(MdSegment.TextBlock(text))
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        result.add(MdSegment.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < input.length) {
        val tail = input.substring(lastIndex).trim()
        if (tail.isNotEmpty()) result.add(MdSegment.TextBlock(tail))
    }
    return result
}

private fun formatInlineMarkdown(
    text: String,
    inlineCodeBg: Color,
    accentColor: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.5.sp,
                            background = inlineCodeBg,
                            color = accentColor
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
