package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.GoogleSignInButton
import com.example.ui.components.AmbientAuroraBackground
import com.example.ui.components.FloatingSparkField
import com.example.ui.components.StaggeredReveal
import com.example.ui.components.ZenvixLivingOrb
import com.example.ui.components.rememberShimmerSweepProgress

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sheenProgress = rememberShimmerSweepProgress(enabled = true, durationMillis = 3600)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        AmbientAuroraBackground(intensity = 0.85f, isActive = true)
        FloatingSparkField(alphaMultiplier = 0.7f)

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StaggeredReveal(index = 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .clip(MaterialTheme.shapes.large)
                            .drawWithContent {
                                drawContent()
                                val xStart = size.width * (sheenProgress - 0.25f)
                                val xEnd = size.width * (sheenProgress + 0.15f)
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.08f),
                                            Color.Transparent
                                        ),
                                        start = Offset(xStart, 0f),
                                        end = Offset(xEnd, size.height)
                                    )
                                )
                            }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_zenvix_hero),
                            contentDescription = "Zenvix AI abstract hero artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.15f),
                                            Color(0xFF151311).copy(alpha = 0.78f)
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ZenvixLivingOrb(isActive = false, size = 54.dp) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_zenvix_mark_fg),
                                    contentDescription = "Zenvix AI emblem",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Zenvix AI",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color(0xFFF5F2EC)
                                )
                                Text(
                                    text = "Cloud & BYOK AI Workspace",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFE9B48F)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                StaggeredReveal(index = 1) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Think clearly, build faster, sync everywhere.",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Free built-in Cloud AI plus custom API key support for Opus 5.5, Febal 5.1 & Sonnet 5.5.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StaggeredReveal(index = 2) {
                        FeatureRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Free Cloud AI + Custom API Models",
                            subtitle = "Stream Zenvix Sonnet 3.8 free or connect Opus 5.5, Febal 5.1 & Sonnet 5.5"
                        )
                    }
                    StaggeredReveal(index = 3) {
                        FeatureRow(
                            icon = Icons.Rounded.Code,
                            title = "Interactive Artifacts Studio",
                            subtitle = "Generate and live-preview HTML, SVG, Kotlin, Python & Docs"
                        )
                    }
                    StaggeredReveal(index = 4) {
                        FeatureRow(
                            icon = Icons.Rounded.DocumentScanner,
                            title = "Vision & File Intelligence",
                            subtitle = "Analyze photos, screenshots, PDFs, and code files seamlessly"
                        )
                    }
                    StaggeredReveal(index = 5) {
                        FeatureRow(
                            icon = Icons.Rounded.CloudSync,
                            title = "Cloud Projects & History Sync",
                            subtitle = "Custom system prompts, pinned chats, and saved artifacts"
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                GoogleSignInButton(
                    onAuthSuccess = {
                        errorMessage = null
                        onAuthSuccess()
                    },
                    onAuthError = { msg -> errorMessage = msg },
                    label = "Sign in with Google"
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Direct 1-Tap Google Login • Passwords Stay Protected by Google",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
