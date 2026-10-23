package top.wkbin.taixu.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

internal val LocalClassicToolStatusDots = staticCompositionLocalOf { false }

@Composable
internal fun ToolStatusIndicator(
    icon: RuntimeIconName,
    color: Color,
    description: String?,
    running: Boolean = false,
) {
    val semantics = Modifier.semantics { description?.let { contentDescription = it } }
    if (running) {
        val infiniteTransition = rememberInfiniteTransition(label = "tool_running_transition")
        val orbitAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1350, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "tool_orbit_angle",
        )
        val rippleProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "tool_ripple_progress",
        )
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "tool_icon_scale",
        )

        if (LocalClassicToolStatusDots.current) {
            Box(
                modifier = Modifier.size(16.dp).then(semantics),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val maxRadius = size.minDimension / 2f
                    drawCircle(
                        color = color.copy(alpha = (1f - rippleProgress) * 0.45f),
                        radius = maxRadius * (0.45f + 0.55f * rippleProgress),
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                    drawCircle(
                        color = color,
                        radius = maxRadius * 0.42f * pulseScale,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.size(22.dp).then(semantics),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(22.dp)) {
                    val strokePx = 1.6.dp.toPx()
                    val maxRadius = (size.minDimension - strokePx) / 2f
                    // Expanding sonar halo
                    drawCircle(
                        color = color.copy(alpha = (1f - rippleProgress) * 0.32f),
                        radius = maxRadius * (0.65f + 0.35f * rippleProgress),
                    )
                    // Subtle base track ring
                    drawCircle(
                        color = color.copy(alpha = 0.18f),
                        radius = maxRadius * 0.92f,
                        style = Stroke(width = strokePx),
                    )
                    // Primary orbital arc
                    drawArc(
                        color = color,
                        startAngle = orbitAngle,
                        sweepAngle = 115f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    // Secondary opposite orbital arc
                    drawArc(
                        color = color.copy(alpha = 0.55f),
                        startAngle = (orbitAngle + 180f) % 360f,
                        sweepAngle = 65f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
                RuntimeIcon(
                    icon,
                    Modifier
                        .size(13.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        },
                    color,
                )
            }
        }
    } else {
        if (LocalClassicToolStatusDots.current) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
                    .then(semantics),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape)
                    .then(semantics),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(icon, Modifier.size(14.dp), color)
            }
        }
    }
}
