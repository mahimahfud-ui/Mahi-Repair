package com.example

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.MainUiState
import com.example.ui.screens.AuthScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun otpInputKeepsOnlySixAsciiDigitsAndSubmitsTheCleanCode() {
        var verifiedCode = ""

        composeRule.setContent {
            AuthScreen(
                state = MainUiState(otpSentForEmail = "invited@example.com"),
                onRequestOtp = {},
                onVerifyOtp = { verifiedCode = it }
            )
        }

        composeRule.onNodeWithTag("otp_input").performTextInput("12a34567")
        val otpText = composeRule.onNodeWithTag("otp_input").fetchSemanticsNode()
        assertEquals(AnnotatedString("123456"), otpText.config[SemanticsProperties.EditableText])
        composeRule.onNodeWithTag("verify_otp_button").performScrollTo().assertIsEnabled().performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("123456", verifiedCode) }
    }

    @Test
    fun resendIsDisabledWhileAnAuthRequestIsInFlight() {
        composeRule.setContent {
            AuthScreen(
                state = MainUiState(
                    otpSentForEmail = "invited@example.com",
                    isAuthLoading = true
                ),
                onRequestOtp = {},
                onVerifyOtp = {}
            )
        }

        composeRule.onNodeWithTag("resend_otp_button").assertIsNotEnabled()
    }

    @Test
    fun invitedEmailIsTrimmedBeforeRequestingAnAccessCode() {
        var requestedEmail = ""

        composeRule.setContent {
            AuthScreen(
                state = MainUiState(),
                onRequestOtp = { requestedEmail = it },
                onVerifyOtp = {}
            )
        }

        composeRule.onNodeWithTag("email_input").performTextInput(" invited@example.com ")
        composeRule.onNodeWithTag("send_otp_button").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("invited@example.com", requestedEmail) }
    }
}
