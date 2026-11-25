package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientAuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0.65f,
    isActive: Boolean = false
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val transition = rememberInfiniteTransition(label = "aurora_bg")
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_orbit"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_pulse"
    )

    val activeMultiplier by animateFloatAsState(
        targetValue = if (isActive) 1.45f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "aurora_active_mult"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val factor = (intensity * activeMultiplier).coerceIn(0f, 1.5f)

        val orb1Center = Offset(
            x = w * (0.24f + 0.14f * cos(orbitAngle)),
            y = h * (0.18f + 0.08f * sin(orbitAngle))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.12f * factor),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = (w * 0.68f) * pulse
            ),
            radius = (w * 0.68f) * pulse,
            center = orb1Center
        )

        val orb2Center = Offset(
            x = w * (0.78f - 0.14f * sin(orbitAngle)),
            y = h * (0.42f + 0.10f * cos(orbitAngle * 0.8f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondary.copy(alpha = 0.09f * factor),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = (w * 0.58f)
            ),
            radius = w * 0.58f,
            center = orb2Center
        )

        val orb3Center = Offset(
            x = w * (0.50f + 0.16f * cos(orbitAngle + 2.1f)),
            y = h * (0.82f - 0.06f * sin(orbitAngle + 1.1f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    tertiary.copy(alpha = 0.07f * factor),
                    Color.Transparent
                ),
                center = orb3Center,
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = orb3Center
        )
    }
}

@Composable
fun ZenvixLivingOrb(
    isActive: Boolean,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val transition = rememberInfiniteTransition(label = "living_orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 2200 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val breathScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (isActive) 1.07f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 950 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breath"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = breathScale
                    scaleY = breathScale
                }
        ) {
            val strokePx = if (isActive) 2.5.dp.toPx() else 1.6.dp.toPx()
            rotate(rotation) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primary.copy(alpha = if (isActive) 0.95f else 0.45f),
                            secondary.copy(alpha = if (isActive) 0.75f else 0.30f),
                            tertiary.copy(alpha = if (isActive) 0.55f else 0.12f),
                            Color.Transparent,
                            primary.copy(alpha = if (isActive) 0.95f else 0.45f)
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(size - 8.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun TypingWaveDots(
    color: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "typing_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "typing_phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 3) {
            val wave = sin(phase - i * 0.9f)
            val scale = 0.75f + 0.35f * ((wave + 1f) * 0.5f)
            val alpha = 0.40f + 0.60f * ((wave + 1f) * 0.5f)
            Canvas(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationY = -wave * 2.5.dp.toPx()
                    }
            ) {
                drawCircle(color = color.copy(alpha = alpha))
            }
        }
    }
}

@Composable
fun rememberShimmerSweepProgress(enabled: Boolean = true, durationMillis: Int = 2200): Float {
    if (!enabled) return -1f
    val transition = rememberInfiniteTransition(label = "shimmer_sweep")
    val progress by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    return progress
}

@Composable
fun StaggeredReveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val offsetPx = with(LocalDensity.current) { 18.dp.toPx() }

    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 8)) * 55L)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * offsetPx
        }
    ) {
        content()
    }
}

@Composable
fun MessageBubbleEntrance(
    messageKey: Any,
    fromEnd: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember(messageKey) { Animatable(0f) }
    val yOffsetPx = with(LocalDensity.current) { 12.dp.toPx() }
    val xOffsetPx = with(LocalDensity.current) { (if (fromEnd) 14.dp else (-10).dp).toPx() }

    LaunchedEffect(messageKey) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.84f,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * yOffsetPx
            translationX = (1f - progress.value) * xOffsetPx
        }
    ) {
        content()
    }
}

@Composable
fun Modifier.springPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.965f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun FloatingSparkField(
    modifier: Modifier = Modifier,
    alphaMultiplier: Float = 1.0f
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val transition = rememberInfiniteTransition(label = "spark_field")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spark_phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val seeds = listOf(
            Triple(0.14f, 0.22f, 2.6f),
            Triple(0.32f, 0.72f, 2.0f),
            Triple(0.55f, 0.36f, 3.0f),
            Triple(0.76f, 0.18f, 2.2f),
            Triple(0.86f, 0.64f, 2.8f),
            Triple(0.22f, 0.88f, 2.4f),
            Triple(0.66f, 0.84f, 2.1f),
            Triple(0.45f, 0.12f, 1.9f)
        )
        seeds.forEachIndexed { idx, (xBase, yBase, radiusDp) ->
            val progress = (phase + idx * 0.125f) % 1f
            val driftY = (yBase - progress * 0.18f).let { if (it < 0f) it + 1f else it }
            val swayX = xBase + 0.03f * sin((progress * 2f * PI + idx).toFloat())
            val twinkle = (0.35f + 0.65f * sin(progress * PI).toFloat()) * alphaMultiplier
            val dotColor = when (idx % 3) {
                0 -> primary
                1 -> secondary
                else -> tertiary
            }
            drawCircle(
                color = dotColor.copy(alpha = 0.22f * twinkle),
                radius = radiusDp.dp.toPx(),
                center = Offset(w * swayX, h * driftY)
            )
        }
    }
}

@Composable
fun AnimatedGradientBorderBrush(
    accent: Color = MaterialTheme.colorScheme.primary,
    secondary: Color = MaterialTheme.colorScheme.secondary,
    durationMillis: Int = 3400
): Brush {
    val progress = rememberShimmerSweepProgress(enabled = true, durationMillis = durationMillis)
    return Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = 0.28f),
            secondary.copy(alpha = 0.85f),
            accent.copy(alpha = 0.90f),
            accent.copy(alpha = 0.25f)
        ),
        start = Offset(progress * 500f, 0f),
        end = Offset((progress + 0.55f) * 500f, 320f)
    )
}

