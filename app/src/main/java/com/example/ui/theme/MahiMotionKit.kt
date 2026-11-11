package com.example.ui.theme

import android.graphics.Matrix
import android.graphics.SweepGradient
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True when the user disabled animations system-wide (Developer options / accessibility
 * "Remove animations"). Every effect in this file degrades to a static state in that case.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** Springy press feedback: shrinks slightly while pressed, overshoots on release. */
fun Modifier.mahiPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "mahiPressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Skeleton shimmer: a soft titanium highlight sweeping across the surface. */
fun Modifier.mahiShimmer(
    shape: Shape = RoundedCornerShape(MahiRadius.small),
): Modifier = composed {
    val reduced = rememberReducedMotion()
    val colors = MahiTheme.colors
    val transition = rememberInfiniteTransition(label = "mahiShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "shimmerProgress",
    )
    this
        .clip(shape)
        .background(colors.surface)
        .drawBehind {
            if (!reduced) {
                val w = size.width
                val x = -w + progress * 3f * w
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, colors.surfaceElevated, Color.Transparent),
                        start = Offset(x, 0f),
                        end = Offset(x + w, size.height * 0.3f),
                    ),
                )
            }
        }
}

/** Staggered entrance: fade + rise + slight scale. Use `index` to cascade list items. */
fun Modifier.mahiEntrance(
    index: Int = 0,
    delayPerItemMs: Int = 60,
    offsetY: Dp = 18.dp,
): Modifier = composed {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = MahiMotion.EMPHASIZED_MS + 120,
                    delayMillis = index.coerceIn(0, 12) * delayPerItemMs,
                    easing = MahiMotion.Emphasized,
                ),
            )
        }
    }
    val offsetPx = with(LocalDensity.current) { offsetY.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * offsetPx
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}

/**
 * Real 3D tilt: the surface rotates toward the finger using rotationX/rotationY with
 * a camera distance for perspective, then springs back on release. Gestures are observed
 * without consuming them, so clicks on the same element keep working.
 */
fun Modifier.mahiTilt3D(
    enabled: Boolean = true,
    maxDegrees: Float = 9f,
): Modifier = composed {
    val reduced = rememberReducedMotion()
    val active = enabled && !reduced
    var targetX by remember { mutableFloatStateOf(0f) }
    var targetY by remember { mutableFloatStateOf(0f) }
    val rotX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "tiltX",
    )
    val rotY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "tiltY",
    )
    val density = LocalDensity.current.density

    this
        .graphicsLayer {
            rotationX = rotX
            rotationY = rotY
            cameraDistance = 14f * density
        }
        .pointerInput(active) {
            if (!active) return@pointerInput
            fun update(p: Offset) {
                val w = size.width.coerceAtLeast(1)
                val h = size.height.coerceAtLeast(1)
                targetY = ((p.x / w) - 0.5f) * 2f * maxDegrees
                targetX = -((p.y / h) - 0.5f) * 2f * maxDegrees
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                update(down.position)
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.pressed) update(change.position)
                } while (event.changes.any { it.pressed })
                targetX = 0f
                targetY = 0f
            }
        }
}

/**
 * Animated titanium border: a champagne-to-cyan highlight orbits the outline.
 * Use sparingly, on one hero element per screen.
 */
fun Modifier.mahiGlowBorder(
    shape: Shape,
    width: Dp = 1.dp,
    colors: List<Color>? = null,
    periodMs: Int = 7000,
): Modifier = composed {
    val reduced = rememberReducedMotion()
    val mc = MahiTheme.colors
    val palette = colors ?: listOf(mc.border, mc.champagne, mc.cyan, mc.border)
    val transition = rememberInfiniteTransition(label = "mahiGlowBorder")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "glowAngle",
    )
    this.drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val colorInts = (palette + palette.first()).map { it.toArgb() }.toIntArray()
        val a = if (reduced) 0f else angle
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val gradient = SweepGradient(cx, cy, colorInts, null)
                val matrix = Matrix()
                matrix.postRotate(a, cx, cy)
                gradient.setLocalMatrix(matrix)
                return gradient
            }
        }
        drawOutline(outline, brush, style = Stroke(width.toPx()))
    }
}

/** Horizontal error shake. Increment `trigger` to play it. 0 means never played. */
fun Modifier.mahiShake(
    trigger: Int,
    amplitude: Dp = 10.dp,
): Modifier = composed {
    val reduced = rememberReducedMotion()
    val offset = remember { Animatable(0f) }
    val amp = with(LocalDensity.current) { amplitude.toPx() }
    LaunchedEffect(trigger) {
        if (trigger > 0 && !reduced) {
            for (f in listOf(-1f, 1f, -0.6f, 0.6f, -0.25f, 0f)) {
                offset.animateTo(f * amp, tween(55, easing = LinearEasing))
            }
        }
    }
    graphicsLayer { translationX = offset.value }
}

/** Animates an integer from 0 to [target]. Re-runs when [target] changes. */
@Composable
fun rememberCountUp(target: Int, durationMs: Int = 900): Int {
    val reduced = rememberReducedMotion()
    val anim = remember { Animatable(if (reduced) target.toFloat() else 0f) }
    LaunchedEffect(target) {
        if (reduced) {
            anim.snapTo(target.toFloat())
        } else {
            anim.animateTo(target.toFloat(), tween(durationMs, easing = MahiMotion.Emphasized))
        }
    }
    return anim.value.toInt()
}
