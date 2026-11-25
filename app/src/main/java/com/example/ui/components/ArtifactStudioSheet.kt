package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.configureZenvixWebView
import com.example.model.ExtractedArtifact
import com.example.ui.theme.CodeSurfaceDark
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactStudioBottomSheet(
    artifact: ExtractedArtifact,
    onDismiss: () -> Unit,
    onSaveToCloud: (ExtractedArtifact) -> Unit,
    onNotify: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        ArtifactStudioContent(
            artifact = artifact,
            onDismiss = onDismiss,
            onSaveToCloud = onSaveToCloud,
            onNotify = onNotify,
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp)
                .navigationBarsPadding()
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArtifactStudioContent(
    artifact: ExtractedArtifact,
    onDismiss: () -> Unit,
    onSaveToCloud: (ExtractedArtifact) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val canPreview = remember(artifact) { artifact.supportsLivePreview() }
    var showPreview by remember(artifact) { mutableStateOf(canPreview) }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifact.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artifact.language.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(artifact.content))
                        onNotify("Copied artifact code")
                    },
                    modifier = Modifier.testTag("artifact_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy artifact",
                        modifier = Modifier.size(18.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = { onSaveToCloud(artifact) },
                    modifier = Modifier.testTag("artifact_save_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = "Save to cloud library",
                        modifier = Modifier.size(18.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, artifact.title)
                            putExtra(Intent.EXTRA_TEXT, artifact.content)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share ${artifact.title}"))
                    },
                    modifier = Modifier.testTag("artifact_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share artifact",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("artifact_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close artifact studio"
                    )
                }
            }
        }

        if (canPreview) {
            Spacer(modifier = Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = showPreview,
                    onClick = { showPreview = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                ) {
                    Text("Live Preview")
                }
                SegmentedButton(
                    selected = !showPreview,
                    onClick = { showPreview = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                ) {
                    Text("Source Code")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = showPreview && canPreview,
            transitionSpec = {
                val direction = if (targetState) -1 else 1
                (slideInHorizontally(tween(240)) { it / 6 * direction } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 6 * direction } + fadeOut(tween(180)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "artifact_preview_source_switch"
        ) { isPreviewMode ->
            if (isPreviewMode) {
                val htmlPayload = remember(artifact) { buildPreviewHtml(artifact) }
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
                                    enableJavaScript = artifact.language.equals("html", ignoreCase = true),
                                    enableDomStorage = true,
                                    onOutcome = { outcome -> onNotify(outcome.message) }
                                )
                                loadDataWithBaseURL("https://zenvix.local/", htmlPayload, "text/html", "UTF-8", null)
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL("https://zenvix.local/", htmlPayload, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.medium,
                    color = CodeSurfaceDark,
                    border = BorderStroke(1.dp, Color(0xFF2E2924))
                ) {
                    val lines = remember(artifact.content) { artifact.content.lines() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        Row {
                            Column(modifier = Modifier.padding(end = 14.dp)) {
                                lines.forEachIndexed { idx, _ ->
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 12.sp,
                                            lineHeight = 20.sp
                                        ),
                                        color = Color(0xFF7D756D)
                                    )
                                }
                            }
                            Column {
                                lines.forEach { line ->
                                    Text(
                                        text = line.ifEmpty { " " },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 12.5.sp,
                                            lineHeight = 20.sp
                                        ),
                                        color = Color(0xFFF5F2EC)
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

private fun buildPreviewHtml(artifact: ExtractedArtifact): String {
    return when (artifact.language.lowercase()) {
        "svg" -> """
            <!DOCTYPE html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"/>
            <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#faf8f5;padding:16px;box-sizing:border-box;}svg{max-width:100%;height:auto;}</style>
            </head><body>${artifact.content}</body></html>
        """.trimIndent()
        "markdown", "md" -> {
            val escaped = artifact.content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            """
            <!DOCTYPE html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"/>
            <style>body{font-family:-apple-system,system-ui,sans-serif;line-height:1.6;color:#1c1917;background:#faf8f5;padding:20px;margin:0;}pre{white-space:pre-wrap;font-family:inherit;}</style>
            </head><body><pre>$escaped</pre></body></html>
            """.trimIndent()
        }
        else -> {
            val trimmed = artifact.content.trim()
            if (trimmed.contains("<html", ignoreCase = true)) {
                trimmed
            } else {
                """
                <!DOCTYPE html>
                <html><head><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="font-family:system-ui,sans-serif;margin:16px;">$trimmed</body></html>
                """.trimIndent()
            }
        }
    }
}
