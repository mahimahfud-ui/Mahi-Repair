package com.mahi.repair.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahi.repair.designsystem.theme.LocalReduceMotion
import com.mahi.repair.designsystem.theme.MahiMotion
import com.mahi.repair.designsystem.theme.MahiTheme

/**
 * Progress ring. Pass `progress = null` when the backend gives no measurable progress
 * (shows an indeterminate arc). Never pass an estimated value.
 */
@Composable
fun MahiProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp,
    strokeWidth: Dp = 6.dp,
    color: Color = MahiTheme.colors.accent,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = MahiTheme.colors
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(progress ?: 0f, tween(MahiMotion.Emphasized, easing = MahiMotion.Ease), label = "ringProgress")
    val spin: State<Float> =
        if (progress == null && !reduce) {
            rememberInfiniteTransition(label = "ringSpin").animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
                label = "ringRotation",
            )
        } else remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .size(diameter)
            .semantics {
                progressBarRangeInfo = if (progress != null) ProgressBarRangeInfo(progress, 0f..1f) else ProgressBarRangeInfo.Indeterminate
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val topLeft = Offset(sw / 2, sw / 2)
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(c.outlineStrong, 0f, 360f, false, topLeft, arc, style = Stroke(sw))
            if (progress == null) {
                drawArc(color, spin.value - 90f, if (reduce) 110f else 100f, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
            } else if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Small rotating arc for "this step is running". Static partial arc under reduced motion. */
@Composable
fun MahiSpinner(modifier: Modifier = Modifier, diameter: Dp = 20.dp, color: Color = MahiTheme.colors.accent) {
    val reduce = LocalReduceMotion.current
    val rotation: State<Float> =
        if (!reduce) {
            rememberInfiniteTransition(label = "spinner").animateFloat(
                0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinnerRotation",
            )
        } else remember { mutableFloatStateOf(0f) }
    val track = MahiTheme.colors.outlineStrong
    Canvas(modifier.size(diameter)) {
        val sw = 2.5.dp.toPx()
        val tl = Offset(sw / 2, sw / 2)
        val arc = Size(size.width - sw, size.height - sw)
        drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(sw))
        drawArc(color, rotation.value - 90f, 100f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Header for the execution screen: ring + tool name + live "current task" line. */
@Composable
fun RepairExecutionHeader(
    toolName: String,
    currentTask: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val c = MahiTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MahiProgressRing(progress, diameter = 112.dp) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.height(20.dp))
        Text(toolName, style = MahiTheme.typography.headline, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = currentTask,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260, easing = MahiMotion.Ease)) { it / 3 }) togetherWith fadeOut(tween(120))
            },
            label = "currentTask",
        ) { task ->
            Text(
                task,
                style = MahiTheme.typography.body,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

enum class StepStatus { Pending, Active, Done, Waiting, Failed, Skipped }

@Immutable
data class RepairStep(
    val id: String,
    val title: String,
    val status: StepStatus,
    val detail: String? = null,
)

/** Vertical timeline of execution steps, driven entirely by the [steps] you pass in. */
@Composable
fun RepairStepList(steps: List<RepairStep>, modifier: Modifier = Modifier) {
    val c = MahiTheme.colors
    val shape = MahiTheme.shapes.md
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.outline, shape)
            .padding(16.dp),
    ) {
        steps.forEachIndexed { index, step ->
            key(step.id) { StepRow(step, isLast = index == steps.lastIndex) }
        }
    }
}

@Composable
private fun StepRow(step: RepairStep, isLast: Boolean) {
    val c = MahiTheme.colors
    val line by animateColorAsState(if (step.status == StepStatus.Done) c.success.copy(alpha = 0.5f) else c.outline, tween(MahiMotion.Standard), label = "stepLine")
    val titleColor by animateColorAsState(
        when (step.status) {
            StepStatus.Active, StepStatus.Done -> c.textPrimary
            StepStatus.Failed -> c.error
            else -> c.textSecondary
        },
        tween(MahiMotion.Standard), label = "stepTitle",
    )
    val trailing = when (step.status) {
        StepStatus.Pending -> "Pending"
        StepStatus.Waiting -> "Waiting"
        StepStatus.Failed -> "Failed"
        StepStatus.Skipped -> "Skipped"
        else -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { stateDescription = step.status.name },
    ) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            StepIndicator(step.status)
            if (!isLast) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .clip(MahiTheme.shapes.pill)
                        .background(line),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 20.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    step.title,
                    style = if (step.status == StepStatus.Active) MahiTheme.typography.bodyStrong else MahiTheme.typography.body,
                    color = titleColor,
                    modifier = Modifier.weight(1f),
                )
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(trailing, style = MahiTheme.typography.caption, color = if (step.status == StepStatus.Failed) c.error else c.textSecondary)
                }
            }
            if (step.detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(step.detail, style = MahiTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun StepIndicator(status: StepStatus) {
    val c = MahiTheme.colors
    AnimatedContent(
        targetState = status,
        transitionSpec = { (fadeIn(tween(200)) + scaleIn(MahiMotion.pressSpring(), initialScale = 0.6f)) togetherWith fadeOut(tween(100)) },
        label = "stepIndicator",
    ) { s ->
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (s) {
                StepStatus.Pending -> Box(Modifier.size(18.dp).border(2.dp, c.outlineStrong, CircleShape))
                StepStatus.Active -> MahiSpinner(diameter = 22.dp)
                StepStatus.Done -> Box(Modifier.size(22.dp).clip(CircleShape).background(c.success), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = c.background, modifier = Modifier.size(14.dp))
                }
                StepStatus.Waiting -> Box(Modifier.size(22.dp).border(2.dp, c.warning, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.warning))
                }
                StepStatus.Failed -> Box(Modifier.size(22.dp).clip(CircleShape).background(c.error), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = c.background, modifier = Modifier.size(14.dp))
                }
                StepStatus.Skipped -> Box(Modifier.width(12.dp).height(2.dp).background(c.outlineStrong))
            }
        }
    }
}

/** Ring + check that draw themselves once. Static when reduced motion is on. */
@Composable
fun AnimatedCheckmark(
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp,
    color: Color = MahiTheme.colors.success,
) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.size(diameter).semantics { contentDescription = "Success" }) {
        val sw = size.minDimension * 0.06f
        val p = progress.value
        val measure = PathMeasure()

        val ring = Path().apply { addOval(Rect(center = center, radius = (size.minDimension - sw) / 2)) }
        measure.setPath(ring, false)
        val ringSeg = Path()
        measure.getSegment(0f, measure.length * (p / 0.6f).coerceIn(0f, 1f), ringSeg, true)
        drawPath(ringSeg, color, style = Stroke(sw, cap = StrokeCap.Round))

        val w = size.width
        val h = size.height
        val tick = Path().apply {
            moveTo(w * 0.30f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.65f)
            lineTo(w * 0.70f, h * 0.37f)
        }
        measure.setPath(tick, false)
        val tickSeg = Path()
        measure.getSegment(0f, measure.length * ((p - 0.5f) / 0.5f).coerceIn(0f, 1f), tickSeg, true)
        drawPath(tickSeg, color, style = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
