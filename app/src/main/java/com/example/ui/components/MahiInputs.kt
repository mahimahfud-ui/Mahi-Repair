package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MahiRadius
import com.example.ui.theme.MahiSpacing
import com.example.ui.theme.MahiTheme
import com.example.ui.theme.mahiFocusRing

/**
 * File 10: MahiInputs.kt
 * Premium inputs: MahiTextField and MahiOtpInput.
 */
@Composable
fun MahiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> MahiTheme.colors.crimson
            isFocused -> MahiTheme.colors.crimsonGlow
            else -> MahiTheme.colors.border
        },
        animationSpec = tween(durationMillis = 200),
        label = "inputBorder"
    )

    val surfaceColor by animateColorAsState(
        targetValue = if (isFocused) MahiTheme.colors.surfaceElevated else MahiTheme.colors.surface,
        animationSpec = tween(durationMillis = 200),
        label = "inputSurface"
    )

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = MahiTheme.colors.textSecondary
                ),
                modifier = Modifier.padding(bottom = MahiSpacing.xs)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .mahiFocusRing(
                    focused = isFocused,
                    color = if (isError) MahiTheme.colors.crimson else MahiTheme.colors.crimsonGlow,
                    shape = RoundedCornerShape(MahiRadius.md)
                )
                .background(surfaceColor, RoundedCornerShape(MahiRadius.md))
                .border(1.dp, borderColor, RoundedCornerShape(MahiRadius.md))
                .padding(horizontal = MahiSpacing.md),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.padding(end = MahiSpacing.sm)) {
                        leadingIcon()
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = MahiTheme.colors.textTertiary
                            )
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mahi_text_field"),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MahiTheme.colors.textPrimary
                        ),
                        singleLine = singleLine,
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        enabled = enabled,
                        cursorBrush = SolidColor(MahiTheme.colors.crimson),
                        interactionSource = interactionSource
                    )
                }

                if (trailingIcon != null) {
                    Box(modifier = Modifier.padding(start = MahiSpacing.sm)) {
                        trailingIcon()
                    }
                }
            }
        }

        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = MahiTheme.colors.crimson
                ),
                modifier = Modifier.padding(top = MahiSpacing.xs, start = MahiSpacing.xs)
            )
        }
    }
}

@Composable
fun MahiOtpInput(
    otp: String,
    onOtpChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    onComplete: (String) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // Hidden actual text field captures keyboard events
    Box(modifier = modifier) {
        BasicTextField(
            value = otp,
            onValueChange = { next ->
                val filtered = next.filter { it.isDigit() }.take(length)
                onOtpChange(filtered)
                if (filtered.length == length) onComplete(filtered)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .testTag("otp_hidden_input")
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(MahiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until length) {
                val digit = otp.getOrNull(i)
                val isCurrent = i == otp.length
                val isFilled = digit != null

                val cellBorderColor by animateColorAsState(
                    targetValue = when {
                        isError -> MahiTheme.colors.crimson
                        isCurrent -> MahiTheme.colors.crimson
                        isFilled -> MahiTheme.colors.borderFocus
                        else -> MahiTheme.colors.border
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "otpBorder_$i"
                )

                val cellBgColor by animateColorAsState(
                    targetValue = if (isCurrent) MahiTheme.colors.surfaceElevated else MahiTheme.colors.surface,
                    animationSpec = tween(durationMillis = 200),
                    label = "otpBg_$i"
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(cellBgColor, RoundedCornerShape(MahiRadius.md))
                        .border(1.dp, cellBorderColor, RoundedCornerShape(MahiRadius.md))
                        .clickable {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (digit != null) {
                        Text(
                            text = digit.toString(),
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MahiTheme.colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                        )
                    } else if (isCurrent) {
                        // Pulsing cursor
                        val infinite = rememberInfiniteTransition(label = "cursor")
                        val caretAlpha by infinite.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "caretAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(20.dp)
                                .alpha(caretAlpha)
                                .background(MahiTheme.colors.crimson)
                        )
                    }
                }
            }
        }
    }
}
