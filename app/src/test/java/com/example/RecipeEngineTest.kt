package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DefaultToolCatalog
import com.example.domain.actions.ActionRegistry
import com.example.domain.engine.RecipeExecutionEngine
import com.example.domain.models.ActionType
import com.example.domain.models.RepairRunResult
import com.example.domain.models.RepairTool
import com.example.domain.models.ToolCategory
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeEngineTest {

    private lateinit var context: Context
    private lateinit var actionRegistry: ActionRegistry
    private lateinit var engine: RecipeExecutionEngine
    private lateinit var testKeyPair: KeyPair

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        actionRegistry = ActionRegistry(context)
        engine = RecipeExecutionEngine(actionRegistry)
        testKeyPair = RsaSignatureVerifier.generateTestKeyPair()
    }

    @Test
    fun `test all 13 action types are recognized`() {
        val expectedActions = listOf(
            "CHECK_INTERNET",
            "DNS_LOOKUP",
            "HTTPS_PROBE",
            "CHECK_APP_INSTALLED",
            "CHECK_STORAGE",
            "OPEN_APP",
            "OPEN_APP_DETAILS",
            "OPEN_ANDROID_SETTINGS",
            "SHOW_GUIDE",
            "ASK_USER_CONFIRMATION",
            "REQUEST_OWN_APP_PERMISSION",
            "VERIFY_INTERNET",
            "VERIFY_DNS"
        )

        assertEquals(13, ActionType.entries.size)
        expectedActions.forEach { identifier ->
            val action = ActionType.fromIdentifier(identifier)
            assertNotNull("Action $identifier must be registered", action)
        }
    }

    @Test
    fun `test unknown action is strictly rejected`() {
        val maliciousRecipeJson = """
        {
          "recipe_id": "malicious_01",
          "tool_id": "tool_exploit",
          "version": "1.0.0",
          "steps": [
            {
              "id": "bad_step",
              "action": "RUN_SHELL_COMMAND",
              "name": "Execute Shell Script",
              "parameters": { "command": "rm -rf /" }
            }
          ]
        }
        """.trimIndent()

        val result = engine.parseRecipeJson(maliciousRecipeJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("RUN_SHELL_COMMAND") == true)
    }

    @Test
    fun `test recipe parsing succeeds for valid schema`() {
        val validJson = DefaultToolCatalog.YOUTUBE_RECIPE_JSON
        val result = engine.parseRecipeJson(validJson)

        assertTrue(result.isSuccess)
        val recipe = result.getOrThrow()
        assertEquals("tool_youtube_repair", recipe.toolId)
        assertTrue(recipe.steps.isNotEmpty())
        assertEquals(ActionType.CHECK_INTERNET, recipe.steps[0].action)
    }

    @Test
    fun `test RSA signature verification with generated keypair`() {
        val payload = """{"tool_id":"dns_tool","version":"1.0"}"""
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val signature = RsaSignatureVerifier.signForTesting(payloadBytes, testKeyPair.private)

        // Valid signature passes
        val isValid = RsaSignatureVerifier.verifySignature(payloadBytes, signature, testKeyPair.public)
        assertTrue("Signature must verify with matching public key", isValid)

        // Tampered payload fails
        val tamperedBytes = """{"tool_id":"dns_tool","version":"2.0"}""".toByteArray(Charsets.UTF_8)
        val isTamperedValid = RsaSignatureVerifier.verifySignature(tamperedBytes, signature, testKeyPair.public)
        assertFalse("Tampered payload must fail signature verification", isTamperedValid)
    }

    @Test
    fun `test invalid signature causes engine rejection`() = runBlocking {
        val tool = RepairTool(
            id = "test_tool",
            name = "Test Tool",
            description = "Test Description",
            category = ToolCategory.GENERAL,
            version = "1.0.0",
            problemDescription = "Testing"
        )

        val report = engine.executeRecipe(
            tool = tool,
            rawSignedPayload = DefaultToolCatalog.DNS_RECIPE_JSON,
            signatureBase64 = "INVALID_SIGNATURE_STRING_HERE",
            userEmail = "user@test.com"
        )

        assertEquals(RepairRunResult.FAILED, report.result)
        assertTrue(report.finalDiagnosis.contains("Security Error", ignoreCase = true))
    }

    @Test
    fun `test default bundled tools catalog contains verified tools`() {
        val tools = DefaultToolCatalog.getInitialTools()
        assertTrue(tools.size >= 5)

        val toolIds = tools.map { it.id }
        assertTrue(toolIds.contains("tool_youtube_repair"))
        assertTrue(toolIds.contains("tool_dns_repair"))
        assertTrue(toolIds.contains("tool_fb_repair"))
        assertTrue(toolIds.contains("tool_wifi_repair"))
        assertTrue(toolIds.contains("tool_play_store_repair"))
    }
}
