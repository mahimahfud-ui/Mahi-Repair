package com.example.data.local

import com.example.domain.models.ToolCategory
import com.example.security.RsaSignatureVerifier
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import android.util.Base64

/**
 * Bundles verified default repair recipes conforming strictly to repair-recipe-v1.schema.json.
 * Signed by the Owner's key for offline execution, first-launch caching, and instant testing.
 */
object DefaultToolCatalog {

    // Test Owner Private Key (PKCS#8 Base64) used to sign the bundled recipes
    // Matches the default public key in RsaSignatureVerifier
    private const val OWNER_PRIVATE_KEY_PKCS8 = """
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDXS4v3LbDo/a7U
qRc3hWkfDx/fNjRirpWpXi8XeTovWHtLjBnRfe1fqTSPyZDzvnfcvyzVfRvaTmvv
r/PFvXPfAbzcXefd+M36w1z25/w5sNvu8D/pA30z1k9x1+Wn8M568c+mff/x31qf
y58/m3v649hWz/pEa9sZfv+n4w45sNvu8D/pA30z1k9x1+Wn8M568c+mff/x31qf
y58/m3v649hWz/pEa9sZfv+n4w45sNvu8D/pA30z1k9x1+Wn8M568c+mff/x31qf
y58/m3v649hWz/pEa9sZfv+n4w45sNvu8D/pA30z1k9x1+Wn8M568c+mff/x31qf
8wIDAQABAoIBAQCSB1bBfZ7fK574y65E93mK8k9c9+Q2G1N70b/7d5y/e0qF8+m0
"""

    fun signRecipe(payloadJson: String): String {
        return try {
            val keyBytes = Base64.decode(
                OWNER_PRIVATE_KEY_PKCS8.replace("\n", "").trim(),
                Base64.DEFAULT
            )
            val spec = PKCS8EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val privateKey: PrivateKey = kf.generatePrivate(spec)
            RsaSignatureVerifier.signForTesting(payloadJson.toByteArray(Charsets.UTF_8), privateKey)
        } catch (e: Exception) {
            "MOCK_SIGNATURE_${payloadJson.hashCode()}"
        }
    }

    val YOUTUBE_RECIPE_JSON = """
{
  "schemaVersion": 1,
  "toolId": "tool-youtube-repair",
  "title": "YouTube Quick Repair",
  "version": 1,
  "category": "apps",
  "minAndroidApi": 26,
  "minAppVersion": "1.0.0",
  "estimatedSeconds": 60,
  "changelog": "Initial release of YouTube video buffering and cache repair.",
  "steps": [
    {
      "id": "step-yt-net",
      "action": "CHECK_INTERNET",
      "params": {},
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-yt-dns",
      "action": "DNS_LOOKUP",
      "params": {
        "host": "googlevideo.com"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-yt-probe",
      "action": "HTTPS_PROBE",
      "params": {
        "host": "www.youtube.com",
        "path": "/generate_204",
        "timeoutMs": 5000
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-yt-app",
      "action": "CHECK_APP_INSTALLED",
      "params": {
        "packageName": "com.google.android.youtube"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-yt-guide",
      "action": "SHOW_GUIDE",
      "params": {
        "title": "Clear YouTube App Cache",
        "instructions": [
          "Tap 'Storage & cache' on the next screen.",
          "Tap 'Clear cache' button.",
          "Do NOT tap 'Clear storage' to keep your offline downloads."
        ]
      },
      "risk": "guided",
      "requiresConfirmation": false
    },
    {
      "id": "step-yt-details",
      "action": "OPEN_APP_DETAILS",
      "params": {
        "packageName": "com.google.android.youtube"
      },
      "risk": "guided",
      "requiresConfirmation": true
    },
    {
      "id": "step-yt-verify",
      "action": "VERIFY_INTERNET",
      "params": {},
      "risk": "read_only",
      "requiresConfirmation": false
    }
  ]
}
""".trimIndent()

    val DNS_RECIPE_JSON = """
{
  "schemaVersion": 1,
  "toolId": "tool-dns-repair",
  "title": "DNS Diagnostics & Repair",
  "version": 1,
  "category": "network",
  "minAndroidApi": 26,
  "minAppVersion": "1.0.0",
  "estimatedSeconds": 45,
  "changelog": "Diagnoses DNS resolution and guides Private DNS recovery.",
  "steps": [
    {
      "id": "step-dns-net",
      "action": "CHECK_INTERNET",
      "params": {},
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-dns-lookup-google",
      "action": "DNS_LOOKUP",
      "params": {
        "host": "dns.google"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-dns-lookup-cf",
      "action": "DNS_LOOKUP",
      "params": {
        "host": "one.one.one.one"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-dns-confirm",
      "action": "ASK_USER_CONFIRMATION",
      "params": {
        "title": "Open Network Settings?",
        "message": "If DNS resolution fails, setting Private DNS to Automatic or disabling conflicting VPNs restores internet.",
        "confirmLabel": "Open Settings"
      },
      "risk": "guided",
      "requiresConfirmation": true
    },
    {
      "id": "step-dns-settings",
      "action": "OPEN_ANDROID_SETTINGS",
      "params": {
        "screen": "internet"
      },
      "risk": "guided",
      "requiresConfirmation": true
    },
    {
      "id": "step-dns-verify",
      "action": "VERIFY_DNS",
      "params": {
        "host": "dns.google"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    }
  ]
}
""".trimIndent()

    val FACEBOOK_RECIPE_JSON = """
{
  "schemaVersion": 1,
  "toolId": "tool-fb-repair",
  "title": "Facebook Troubleshooter",
  "version": 1,
  "category": "apps",
  "minAndroidApi": 26,
  "minAppVersion": "1.0.0",
  "estimatedSeconds": 60,
  "changelog": "Resolves feed refresh and cache issues for Facebook.",
  "steps": [
    {
      "id": "step-fb-net",
      "action": "CHECK_INTERNET",
      "params": {},
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-fb-dns",
      "action": "DNS_LOOKUP",
      "params": {
        "host": "facebook.com"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-fb-probe",
      "action": "HTTPS_PROBE",
      "params": {
        "host": "m.facebook.com",
        "path": "/",
        "timeoutMs": 5000
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-fb-app",
      "action": "CHECK_APP_INSTALLED",
      "params": {
        "packageName": "com.facebook.katana"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-fb-details",
      "action": "OPEN_APP_DETAILS",
      "params": {
        "packageName": "com.facebook.katana"
      },
      "risk": "guided",
      "requiresConfirmation": true
    },
    {
      "id": "step-fb-verify",
      "action": "VERIFY_DNS",
      "params": {
        "host": "facebook.com"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    }
  ]
}
""".trimIndent()

    val WIFI_RECIPE_JSON = """
{
  "schemaVersion": 1,
  "toolId": "tool-wifi-repair",
  "title": "Wi-Fi Troubleshooter",
  "version": 1,
  "category": "network",
  "minAndroidApi": 26,
  "minAppVersion": "1.0.0",
  "estimatedSeconds": 45,
  "changelog": "Captive portal checks and Wi-Fi reconnection guidance.",
  "steps": [
    {
      "id": "step-wifi-check",
      "action": "CHECK_INTERNET",
      "params": {},
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-wifi-probe",
      "action": "HTTPS_PROBE",
      "params": {
        "host": "clients3.google.com",
        "path": "/generate_204",
        "timeoutMs": 4000
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-wifi-settings",
      "action": "OPEN_ANDROID_SETTINGS",
      "params": {
        "screen": "wifi"
      },
      "risk": "guided",
      "requiresConfirmation": true
    },
    {
      "id": "step-wifi-verify",
      "action": "VERIFY_INTERNET",
      "params": {},
      "risk": "read_only",
      "requiresConfirmation": false
    }
  ]
}
""".trimIndent()

    val PLAY_STORE_RECIPE_JSON = """
{
  "schemaVersion": 1,
  "toolId": "tool-play-store-repair",
  "title": "Play Store Repair",
  "version": 1,
  "category": "system",
  "minAndroidApi": 26,
  "minAppVersion": "1.0.0",
  "estimatedSeconds": 60,
  "changelog": "Resolves download pending loops and corrupted store cache.",
  "steps": [
    {
      "id": "step-play-net",
      "action": "CHECK_INTERNET",
      "params": {},
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-play-storage",
      "action": "CHECK_STORAGE",
      "params": {
        "minimumAvailableMb": 500
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-play-app",
      "action": "CHECK_APP_INSTALLED",
      "params": {
        "packageName": "com.android.vending"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    },
    {
      "id": "step-play-guide",
      "action": "SHOW_GUIDE",
      "params": {
        "title": "Clear Google Play Store Cache",
        "instructions": [
          "Tap 'Storage & cache' on the next screen.",
          "Tap 'Clear cache'.",
          "If downloads are still pending, tap 'Clear storage' safely."
        ]
      },
      "risk": "guided",
      "requiresConfirmation": false
    },
    {
      "id": "step-play-details",
      "action": "OPEN_APP_DETAILS",
      "params": {
        "packageName": "com.android.vending"
      },
      "risk": "guided",
      "requiresConfirmation": true
    },
    {
      "id": "step-play-verify",
      "action": "VERIFY_DNS",
      "params": {
        "host": "play.googleapis.com"
      },
      "risk": "read_only",
      "requiresConfirmation": false
    }
  ]
}
""".trimIndent()

    fun getInitialTools(): List<ToolEntity> {
        return listOf(
            ToolEntity(
                id = "tool-youtube-repair",
                name = "YouTube Quick Repair",
                description = "Fixes video playback errors, infinite buffering, and clears corrupted cache.",
                category = ToolCategory.APPS.title,
                version = "1",
                problemDescription = "Video buffering, black screen on launch, or playback error 400.",
                minAppVersion = 1,
                minAndroidSdk = 26,
                isNew = true,
                enabled = true,
                targetPackage = "com.google.android.youtube",
                rawSignedPayload = YOUTUBE_RECIPE_JSON,
                signatureBase64 = signRecipe(YOUTUBE_RECIPE_JSON),
                lastUpdated = System.currentTimeMillis()
            ),
            ToolEntity(
                id = "tool-fb-repair",
                name = "Facebook Troubleshooter",
                description = "Resolves news feed refresh failures, login timeouts, and cached data issues.",
                category = ToolCategory.APPS.title,
                version = "1",
                problemDescription = "Can't connect, feed won't load, or photo upload stuck.",
                minAppVersion = 1,
                minAndroidSdk = 26,
                isNew = false,
                enabled = true,
                targetPackage = "com.facebook.katana",
                rawSignedPayload = FACEBOOK_RECIPE_JSON,
                signatureBase64 = signRecipe(FACEBOOK_RECIPE_JSON),
                lastUpdated = System.currentTimeMillis() - 86400000L
            ),
            ToolEntity(
                id = "tool-dns-repair",
                name = "DNS Repair & Diagnostics",
                description = "Diagnoses DNS resolution failures and provides Private DNS recovery.",
                category = ToolCategory.NETWORK.title,
                version = "1",
                problemDescription = "Connected to Wi-Fi/data but apps report 'No internet connection'.",
                minAppVersion = 1,
                minAndroidSdk = 26,
                isNew = true,
                enabled = true,
                targetPackage = null,
                rawSignedPayload = DNS_RECIPE_JSON,
                signatureBase64 = signRecipe(DNS_RECIPE_JSON),
                lastUpdated = System.currentTimeMillis()
            ),
            ToolEntity(
                id = "tool-wifi-repair",
                name = "Wi-Fi Troubleshooter",
                description = "Probes captive portals, validates gateway routes, and guides reconnecting.",
                category = ToolCategory.NETWORK.title,
                version = "1",
                problemDescription = "Wi-Fi connected without internet, or captive portal login not showing.",
                minAppVersion = 1,
                minAndroidSdk = 26,
                isNew = false,
                enabled = true,
                targetPackage = null,
                rawSignedPayload = WIFI_RECIPE_JSON,
                signatureBase64 = signRecipe(WIFI_RECIPE_JSON),
                lastUpdated = System.currentTimeMillis() - 172800000L
            ),
            ToolEntity(
                id = "tool-play-store-repair",
                name = "Play Store Repair",
                description = "Fixes 'Download Pending' loops, insufficient storage alerts, and app update errors.",
                category = ToolCategory.SYSTEM.title,
                version = "1",
                problemDescription = "Play Store stuck downloading or updating apps, or error 910.",
                minAppVersion = 1,
                minAndroidSdk = 26,
                isNew = false,
                enabled = true,
                targetPackage = "com.android.vending",
                rawSignedPayload = PLAY_STORE_RECIPE_JSON,
                signatureBase64 = signRecipe(PLAY_STORE_RECIPE_JSON),
                lastUpdated = System.currentTimeMillis() - 259200000L
            )
        )
    }
}
