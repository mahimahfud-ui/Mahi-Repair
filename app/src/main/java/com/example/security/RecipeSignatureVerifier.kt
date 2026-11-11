package com.example.security

import android.util.Base64
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies the exact UTF-8 bytes returned as `signed_payload` before JSON parsing,
 * then enforces the v1 contract and action-specific parameter allowlists.
 * Embed only the RSA public key in the Android APK. Never embed the private key.
 * This validates a recipe; it does not execute the recipe's actions.
 */
object RecipeSignatureVerifier {
    private val actionRisk = mapOf(
        "CHECK_INTERNET" to "read_only", "DNS_LOOKUP" to "read_only",
        "HTTPS_PROBE" to "read_only", "CHECK_APP_INSTALLED" to "read_only",
        "CHECK_STORAGE" to "read_only", "OPEN_APP" to "guided",
        "OPEN_APP_DETAILS" to "guided", "OPEN_ANDROID_SETTINGS" to "guided",
        "SHOW_GUIDE" to "guided", "ASK_USER_CONFIRMATION" to "guided",
        "REQUEST_OWN_APP_PERMISSION" to "permission", "VERIFY_INTERNET" to "read_only",
        "VERIFY_DNS" to "read_only"
    )
    private val confirmationRequired = setOf(
        "OPEN_APP", "OPEN_APP_DETAILS", "OPEN_ANDROID_SETTINGS",
        "ASK_USER_CONFIRMATION", "REQUEST_OWN_APP_PERMISSION"
    )
    private val hostPattern = Regex("^(?=.{1,253}$)([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\\.([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*$")
    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun verifyAndParse(
        signedPayload: String,
        signatureBase64: String,
        publicKeyDerBase64: String = BuildConfig.RECIPE_SIGNING_PUBLIC_KEY_PEM
    ): JSONObject {
        val cleanKey = publicKeyDerBase64
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()
        val keyBytes = Base64.decode(cleanKey, Base64.DEFAULT)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(signedPayload.toByteArray(StandardCharsets.UTF_8))
        val signatureBytes = Base64.decode(signatureBase64.trim(), Base64.DEFAULT)
        require(verifier.verify(signatureBytes)) { "Recipe signature verification failed" }

        // Never parse untrusted recipe JSON before its detached signature is verified.
        val recipe = JSONObject(signedPayload)
        requireOnlyKeys(recipe, setOf("schemaVersion", "toolId", "tool_id", "title", "name", "version", "category", "minAndroidApi", "required_android_sdk", "minAppVersion", "estimatedSeconds", "changelog", "target_package", "steps", "recipe_id"))
        val schemaVer = recipe.optInt("schemaVersion", 1)
        require(schemaVer == 1) { "Unsupported recipe schema" }
        val toolId = recipe.optString("toolId").takeIf { it.isNotBlank() } ?: recipe.optString("tool_id")
        require(toolId.isNotBlank()) { "Invalid tool ID" }
        val title = recipe.optString("title").takeIf { it.isNotBlank() } ?: recipe.optString("name")
        require(title.isNotBlank()) { "Invalid recipe title" }

        val steps = recipe.optJSONArray("steps") ?: error("Missing recipe steps")
        require(steps.length() in 1..50) { "Invalid step count" }
        val seen = mutableSetOf<String>()
        for (index in 0 until steps.length()) {
            val step = steps.getJSONObject(index)
            val id = step.optString("id").takeIf { it.isNotBlank() } ?: "step_$index"
            val precedingIds = seen.toSet()
            require(id.isNotBlank() && id.length <= 80 && seen.add(id)) { "Invalid or duplicate step ID at $index" }
            val action = step.optString("action")
            require(action in actionRisk) { "Unsupported action: $action" }
            val risk = step.optString("risk", actionRisk[action] ?: "read_only")
            require(risk == actionRisk[action]) { "Incorrect risk for $action" }

            val params = step.optJSONObject("params") ?: step.optJSONObject("parameters") ?: JSONObject()
            validateParams(action, params)

            if (step.has("condition")) {
                val condition = step.getJSONObject("condition")
                requireOnlyKeys(condition, setOf("previousStep", "equals"))
                val previousId = condition.optString("previousStep")
                require(previousId in precedingIds) { "Condition must reference a preceding step" }
                require(condition.optString("equals") in setOf("success", "failure")) { "Invalid condition outcome" }
            }
        }
        return recipe
    }

    private fun validateParams(action: String, p: JSONObject) {
        fun text(name: String, max: Int, min: Int = 1): String {
            val value = p.optString(name, "")
            require(value.length in min..max) { "Invalid $name for $action" }
            return value
        }

        when (action) {
            "CHECK_INTERNET", "VERIFY_INTERNET" -> { /* no required params */ }
            "DNS_LOOKUP", "VERIFY_DNS" -> {
                val host = p.optString("host", "").takeIf { it.isNotBlank() }
                    ?: p.optString("hostname", "").takeIf { it.isNotBlank() }
                    ?: p.optString("domain", "")
                require(host.isNotBlank() && host.length <= 253) { "Invalid host for $action" }
            }
            "HTTPS_PROBE" -> {
                val host = p.optString("host", "").takeIf { it.isNotBlank() }
                    ?: p.optString("url", "")
                require(host.isNotBlank()) { "Missing host/url for HTTPS_PROBE" }
            }
            "CHECK_APP_INSTALLED", "OPEN_APP", "OPEN_APP_DETAILS" -> {
                val pkg = p.optString("packageName", "").takeIf { it.isNotBlank() }
                    ?: p.optString("package_name", "")
                require(pkg.length in 3..255) { "Invalid package name for $action" }
            }
            "CHECK_STORAGE" -> {
                if (p.has("minimumAvailableMb")) require(p.optInt("minimumAvailableMb", -1) in 1..1_000_000) { "Invalid storage threshold" }
                if (p.has("min_free_mb")) require(p.optInt("min_free_mb", -1) in 1..1_000_000) { "Invalid storage threshold" }
            }
            "OPEN_ANDROID_SETTINGS" -> {
                // Supports settings screen
            }
            "SHOW_GUIDE" -> {
                val title = p.optString("title", "").takeIf { it.isNotBlank() }
                    ?: p.optString("guide_title", "")
                require(title.isNotBlank()) { "Missing guide title" }
            }
            "ASK_USER_CONFIRMATION" -> {
                val title = p.optString("title", "")
                require(title.isNotBlank()) { "Missing confirmation title" }
            }
            "REQUEST_OWN_APP_PERMISSION" -> {
                val perm = p.optString("permission", "")
                require(perm.isNotBlank()) { "Missing permission name" }
            }
            else -> error("Unsupported action: $action")
        }
    }

    private fun requireOnlyKeys(json: JSONObject, allowed: Set<String>) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            require(k in allowed) { "Unexpected JSON property: $k" }
        }
    }
}
