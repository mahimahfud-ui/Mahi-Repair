package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.domain.actions.ActionRegistry
import com.example.domain.engine.ExecutionEvent
import com.example.domain.engine.PermissionExecutionResult
import com.example.domain.engine.RecipeExecutionEngine
import com.example.domain.models.RepairRunResult
import com.example.domain.models.RepairTool
import com.example.domain.models.StepStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
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
class PermissionExecutionFlowTest {
    private lateinit var engine: RecipeExecutionEngine
    private lateinit var keyPair: KeyPair
    private lateinit var publicKeyPem: String

    private val recipe = """{
      "schemaVersion": 1,
      "toolId": "permission-test",
      "title": "Permission Test",
      "version": 1,
      "category": "apps",
      "minAndroidApi": 23,
      "minAppVersion": "1.0.0",
      "estimatedSeconds": 30,
      "steps": [{
        "id": "camera-permission",
        "action": "REQUEST_OWN_APP_PERMISSION",
        "params": {"permission": "CAMERA"},
        "risk": "permission",
        "requiresConfirmation": true
      }]
    }"""

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        engine = RecipeExecutionEngine(ActionRegistry(context))
        keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP) +
            "\n-----END PUBLIC KEY-----"
    }

    @Test
    fun permissionStepCompletesOnlyAfterAndroidReportsGranted() = runBlocking {
        val execution = async { runWithPermissionResult(PermissionExecutionResult.GRANTED) }
        val report = execution.await()
        assertEquals(RepairRunResult.SUCCESS, report.result)
        assertEquals(StepStatus.COMPLETED, report.stepRecords.single().status)
    }

    @Test
    fun deniedPermissionFailsTheRequiredStepAndNeverReportsCompletion() = runBlocking {
        val report = runWithPermissionResult(PermissionExecutionResult.DENIED)
        assertEquals(RepairRunResult.FAILED, report.result)
        assertEquals(StepStatus.FAILED, report.stepRecords.single().status)
        assertTrue(report.stepRecords.single().message.contains("denied", ignoreCase = true))
    }

    @Test
    fun cancelledPermissionRequestCancelsTheRepair() = runBlocking {
        val report = runWithPermissionResult(PermissionExecutionResult.CANCELLED)
        assertEquals(RepairRunResult.CANCELLED, report.result)
        assertEquals(StepStatus.CANCELLED, report.stepRecords.single().status)
    }

    @Test
    fun permanentlyDeniedPermissionIsNotTreatedAsGranted() = runBlocking {
        val report = runWithPermissionResult(PermissionExecutionResult.PERMANENTLY_DENIED)
        assertEquals(RepairRunResult.FAILED, report.result)
        assertEquals(StepStatus.FAILED, report.stepRecords.single().status)
        assertTrue(report.stepRecords.single().message.contains("app settings", ignoreCase = true))
    }

    @Test
    fun repeatedResultCallbacksCannotTurnAnEarlierDenialIntoApproval() = runBlocking {
        val task = async { executeUntilPermissionPrompt() }
        withTimeout(3_000) {
            engine.engineState.first { it is ExecutionEvent.PromptUserAction }
        }
        engine.submitPermissionResult(PermissionExecutionResult.DENIED)
        engine.submitPermissionResult(PermissionExecutionResult.GRANTED)
        val report = task.await()
        assertEquals(RepairRunResult.FAILED, report.result)
        assertEquals(StepStatus.FAILED, report.stepRecords.single().status)
    }

    private suspend fun runWithPermissionResult(result: PermissionExecutionResult) =
        kotlinx.coroutines.coroutineScope {
            val task = async { executeUntilPermissionPrompt() }
            val report = task.await(result)
            report
        }

    private suspend fun kotlinx.coroutines.Deferred<com.example.domain.models.RepairRunReport>.await(
        permissionResult: PermissionExecutionResult
    ): com.example.domain.models.RepairRunReport {
        // executeUntilPermissionPrompt is launched as a deferred report and will emit
        // the permission prompt before waiting for the Android Activity Result callback.
        return this@PermissionExecutionFlowTest.awaitPromptThen(permissionResult, this)
    }

    private suspend fun awaitPromptThen(
        result: PermissionExecutionResult,
        task: kotlinx.coroutines.Deferred<com.example.domain.models.RepairRunReport>
    ): com.example.domain.models.RepairRunReport {
        withTimeout(3_000) {
            engine.engineState.first { it is ExecutionEvent.PromptUserAction }
        }
        engine.submitPermissionResult(result)
        return task.await()
    }

    private suspend fun executeUntilPermissionPrompt(): com.example.domain.models.RepairRunReport {
        // This suspends until the separate Activity Result callback is simulated below.
        return engine.executeRecipe(
            tool = RepairTool(
                id = "permission-test-id",
                name = "Permission Test",
                description = "",
                category = com.example.domain.models.ToolCategory.APPS,
                version = "1",
                problemDescription = ""
            ),
            rawSignedPayload = recipe,
            signatureBase64 = sign(recipe),
            userEmail = "permission-test@example.invalid",
            publicKeyPem = publicKeyPem
        )
    }

    private fun sign(payload: String): String {
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }
}
