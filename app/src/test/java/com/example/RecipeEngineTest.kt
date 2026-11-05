package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.domain.actions.ActionRegistry
import com.example.domain.engine.RecipeExecutionEngine
import com.example.domain.models.ActionType
import com.example.domain.models.RepairRunResult
import com.example.domain.models.RepairTool
import com.example.domain.models.ToolCategory
import com.example.security.RecipeContractVerifier
import com.example.security.RsaSignatureVerifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeEngineTest {
    private lateinit var context: Context
    private lateinit var actionRegistry: ActionRegistry
    private lateinit var engine: RecipeExecutionEngine
    private lateinit var testKeyPair: KeyPair

    private val validRecipeJson = """
        {
          "schemaVersion": 1,
          "toolId": "demo-network-check",
          "title": "Demo network check",
          "version": 1,
          "category": "network",
          "minAndroidApi": 23,
          "minAppVersion": "1.0.0",
          "estimatedSeconds": 20,
          "steps": [
            {
              "id": "check-internet",
              "action": "CHECK_INTERNET",
              "params": {},
              "risk": "read_only",
              "requiresConfirmation": false
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        actionRegistry = ActionRegistry(context)
        engine = RecipeExecutionEngine(actionRegistry)
        testKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    @Test
    fun allThirteenSupportedActionsAreRegistered() {
        val expected = listOf(
            "CHECK_INTERNET", "DNS_LOOKUP", "HTTPS_PROBE", "CHECK_APP_INSTALLED",
            "CHECK_STORAGE", "OPEN_APP", "OPEN_APP_DETAILS", "OPEN_ANDROID_SETTINGS",
            "SHOW_GUIDE", "ASK_USER_CONFIRMATION", "REQUEST_OWN_APP_PERMISSION",
            "VERIFY_INTERNET", "VERIFY_DNS"
        )
        assertEquals(13, ActionType.entries.size)
        expected.forEach { assertNotNull(ActionType.fromIdentifier(it)) }
    }

    @Test
    fun unknownActionIsRejectedByCanonicalContract() {
        val malicious = validRecipeJson.replace(
            "\"CHECK_INTERNET\"",
            "\"RUN_SHELL_COMMAND\""
        ).replace("\"params\": {}", "\"params\": {\"command\": \"id\"}")
        val result = engine.parseRecipeJson(malicious)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("RUN_SHELL_COMMAND") == true)
    }

    @Test
    fun validCanonicalRecipeParses() {
        val result = engine.parseRecipeJson(validRecipeJson)
        assertTrue(result.isSuccess)
        assertEquals("demo-network-check", result.getOrThrow().toolId)
        assertEquals(ActionType.CHECK_INTERNET, result.getOrThrow().steps.first().action)
    }

    @Test
    fun conditionsCanReferenceOnlyPrecedingSteps() {
        val conditional = validRecipeJson.replace(
            """
              ]
            }
            """.trimIndent(),
            """
                ,
                {
                  "id": "verify-internet",
                  "action": "VERIFY_INTERNET",
                  "params": {},
                  "risk": "read_only",
                  "requiresConfirmation": false,
                  "condition": {
                    "previousStep": "check-internet",
                    "equals": "success"
                  }
                }
              ]
            }
            """.trimIndent()
        )
        val parsed = engine.parseRecipeJson(conditional)
        assertTrue(parsed.isSuccess)
        assertEquals("check-internet", parsed.getOrThrow().steps[1].condition?.previousStep)
    }

    @Test
    fun canonicalContractRejectsUnknownPropertiesAndMalformedActions() {
        val extraRootProperty = validRecipeJson.replace(
            "\"title\": \"Demo network check\"",
            "\"title\": \"Demo network check\", \"unexpected\": true"
        )
        assertTrue(runCatching { RecipeContractVerifier.validateSchema(extraRootProperty) }.isFailure)

        val extraStepProperty = validRecipeJson.replace(
            "\"params\": {}",
            "\"params\": {}, \"shell\": \"id\""
        )
        assertTrue(runCatching { RecipeContractVerifier.validateSchema(extraStepProperty) }.isFailure)
    }

    @Test
    fun rsaSignatureVerifiesOnlyForExactPayloadBytes() {
        val payload = validRecipeJson.toByteArray(Charsets.UTF_8)
        val signature = signForTesting(payload, testKeyPair)
        assertTrue(RsaSignatureVerifier.verifySignature(payload, signature, testKeyPair.public))

        val tampered = validRecipeJson.replace("Demo network check", "Tampered").toByteArray(Charsets.UTF_8)
        assertFalse(RsaSignatureVerifier.verifySignature(tampered, signature, testKeyPair.public))
    }

    @Test
    fun contractVerifierChecksSignatureBeforeAcceptingRecipe() {
        val signature = signForTesting(validRecipeJson.toByteArray(Charsets.UTF_8), testKeyPair)
        val publicPem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.encodeToString(testKeyPair.public.encoded, Base64.NO_WRAP) +
            "\n-----END PUBLIC KEY-----"
        assertEquals(
            "demo-network-check",
            RecipeContractVerifier.verifyAndParse(validRecipeJson, signature, publicPem).getString("toolId")
        )
        assertTrue(runCatching {
            RecipeContractVerifier.verifyAndParse(validRecipeJson.replace("Demo", "Altered"), signature, publicPem)
        }.isFailure)
    }

    @Test
    fun invalidSignatureStopsExecution() = runBlocking {
        val tool = RepairTool(
            id = "demo-network-check",
            name = "Demo network check",
            description = "Test",
            category = ToolCategory.GENERAL,
            version = "1",
            problemDescription = "Test"
        )
        val report = engine.executeRecipe(
            tool = tool,
            rawSignedPayload = validRecipeJson,
            signatureBase64 = "INVALID_SIGNATURE",
            userEmail = "user@test.com"
        )
        assertEquals(RepairRunResult.FAILED, report.result)
        assertTrue(report.finalDiagnosis.contains("Security Error", ignoreCase = true))
    }

    private fun signForTesting(payload: ByteArray, keyPair: KeyPair): String {
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(payload)
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }
}
