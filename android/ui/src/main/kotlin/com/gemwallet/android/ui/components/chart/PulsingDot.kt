package com.gemwallet.android.ui.components.chart

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gemwallet.android.ui.theme.space8
import kotlinx.coroutines.delay

private object PulseMetrics {
    const val DURATION_MS = 1800L
    const val DURATION_NS = DURATION_MS * 1_000_000L
    const val FRAME_INTERVAL_MS = 33L
    const val RING2_PHASE_OFFSET = 0.4f / 1.8f
    const val RING1_MAX_SCALE = 3.0f
    const val RING2_MAX_SCALE = 2.5f
    const val CONTAINER_SCALE = 5
    val ringStroke = 1.5.dp
}

private object PulseAlpha {
    const val RING_STROKE = 0.40f
    const val RING_OPACITY = 0.80f
}

internal data class PulseRingFrame(
    val scale: Float,
    val alpha: Float,
)

internal fun pulseProgressFromElapsedNanos(elapsedNanos: Long): Float {
    if (elapsedNanos <= 0L) return 0f
    return (elapsedNanos % PulseMetrics.DURATION_NS).toFloat() / PulseMetrics.DURATION_NS.toFloat()
}

internal fun computePulseRingFrame(progress: Float, maxScale: Float): PulseRingFrame {
    val clamped = progress.coerceIn(0f, 1f)
    val eased = easeOutQuad(clamped)
    return PulseRingFrame(
        scale = 1f + (maxScale - 1f) * eased,
        alpha = PulseAlpha.RING_STROKE * PulseAlpha.RING_OPACITY * (1f - eased),
    )
}

@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, dotSize: Dp = space8) {
    val containerSize = dotSize * PulseMetrics.CONTAINER_SCALE
    val lifecycleOwner = LocalLifecycleOwner.current
    val progressState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val startNanos = withFrameNanos { it }
            progressState.floatValue = 0f
            while (true) {
                delay(PulseMetrics.FRAME_INTERVAL_MS)
                val frameNanos = withFrameNanos { it }
                progressState.floatValue = pulseProgressFromElapsedNanos(frameNanos - startNanos)
            }
        }
    }

    Spacer(
        modifier = modifier
            .size(containerSize)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val dotRadius = dotSize.toPx() / 2f
                val ringStroke = Stroke(width = PulseMetrics.ringStroke.toPx())
                val dotBrush = Brush.radialGradient(
                    colors = listOf(Color.White, color),
                    center = center,
                    radius = dotRadius,
                )
                onDrawBehind {
                    val progress = progressState.floatValue
                    val ring1 = computePulseRingFrame(progress, PulseMetrics.RING1_MAX_SCALE)
                    if (ring1.alpha > 0f) {
                        drawCircle(
                            color = color.copy(alpha = ring1.alpha),
                            radius = dotRadius * ring1.scale,
                            center = center,
                            style = ringStroke,
                        )
                    }
                    val ring2 = computePulseRingFrame((progress + PulseMetrics.RING2_PHASE_OFFSET) % 1f, PulseMetrics.RING2_MAX_SCALE)
                    if (ring2.alpha > 0f) {
                        drawCircle(
                            color = color.copy(alpha = ring2.alpha),
                            radius = dotRadius * ring2.scale,
                            center = center,
                            style = ringStroke,
                        )
                    }
                    drawCircle(
                        brush = dotBrush,
                        radius = dotRadius,
                        center = center,
                    )
                }
            },
    )
}

private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)
