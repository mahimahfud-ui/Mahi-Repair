package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import com.example.ui.theme.MahiSpacing
import com.example.ui.theme.MahiTheme
import com.example.ui.theme.rememberReducedMotion

/**
 * File 13: MahiSplashScreen.kt
 * Hero cinematic loading experience with animated monogram and orbit ring.
 */
@Composable
fun MahiSplashScreen(
    modifier: Modifier = Modifier,
    tagline: String = "PRECISION ANDROID REPAIR",
    onReady: (() -> Unit)? = null
) {
    val reduced = rememberReducedMotion()

    val pathProgress = remember { Animatable(if (reduced) 1f else 0f) }
    val glowAlpha = remember { Animatable(if (reduced) 1f else 0f) }
    val textAlpha = remember { Animatable(if (reduced) 1f else 0f) }
    val textScale = remember { Animatable(if (reduced) 1f else 0.92f) }

    val infinite = rememberInfiniteTransition(label = "splashOrbit")
    val orbitAngle by if (!reduced) {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbitAngle"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    LaunchedEffect(Unit) {
        if (!reduced) {
            // Step 1: Draw the M monogram path
            pathProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
            // Step 2: Bloom the crimson glow
            glowAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
            // Step 3: Reveal wordmark
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
            textScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        onReady?.invoke()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MahiTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        MahiAmbientBackground(content = {})

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Monogram container
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Radial crimson flare
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .alpha(glowAlpha.value * 0.45f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(MahiTheme.colors.crimson, Color.Transparent)
                            )
                        )
                )

                // Monogram canvas: draws an architectural "M"
                val crimson = MahiTheme.colors.crimson
                val crimsonLight = MahiTheme.colors.crimsonLight
                val border = MahiTheme.colors.border

                Canvas(modifier = Modifier.size(120.dp)) {
                    val w = size.width
                    val h = size.height

                    // Orbit ring
                    drawCircle(
                        color = border,
                        radius = w * 0.48f,
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), orbitAngle)
                        )
                    )

                    // Architectural "M" path
                    // Left leg -> peak 1 -> valley -> peak 2 -> right leg
                    val mPath = Path().apply {
                        moveTo(w * 0.22f, h * 0.78f)
                        lineTo(w * 0.22f, h * 0.24f)
                        lineTo(w * 0.50f, h * 0.54f)
                        lineTo(w * 0.78f, h * 0.24f)
                        lineTo(w * 0.78f, h * 0.78f)
                    }

                    // Stroke effect to animate path drawing
                    val pathMeasure = android.graphics.PathMeasure()
                    val androidPath = android.graphics.Path()
                    mPath.asAndroidPath()
                    pathMeasure.setPath(mPath.asAndroidPath(), false)
                    val length = pathMeasure.length

                    val partial = android.graphics.Path()
                    pathMeasure.getSegment(0f, length * pathProgress.value, partial, true)

                    drawPath(
                        path = partial.asComposePath(),
                        brush = Brush.linearGradient(
                            colors = listOf(crimsonLight, crimson),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        style = Stroke(
                            width = 5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Accent center diamond
                    if (pathProgress.value > 0.6f) {
                        val diamondAlpha = ((pathProgress.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        drawCircle(
                            color = crimsonLight.copy(alpha = diamondAlpha),
                            radius = 3.5.dp.toPx(),
                            center = Offset(w * 0.50f, h * 0.54f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MahiSpacing.xl))

            // Brand typography
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .scale(textScale.value)
            ) {
                Text(
                    text = "MAHI REPAIR",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        color = MahiTheme.colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(MahiSpacing.xs))

                Text(
                    text = tagline,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = MahiTheme.colors.textSecondary
                    )
                )
            }
        }
    }
}
