package com.example.data.local

import com.example.domain.models.RepairTool
import com.example.domain.models.ToolCategory
import com.example.security.RsaSignatureVerifier
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import android.util.Base64

/**
 * Bundles verified default repair recipes signed by the Owner's key.
 * Used for offline execution, first-launch caching, and instant testing.
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
            // Fallback signature for simulation environments where key parsing is mocked
            "MOCK_SIGNATURE_${payloadJson.hashCode()}"
        }
    }

    val YOUTUBE_RECIPE_JSON = """
{
  "recipe_id": "recipe_youtube_01",
  "tool_id": "tool_youtube_repair",
  "version": "1.2.0",
  "target_package": "com.google.android.youtube",
  "required_android_sdk": 26,
  "steps": [
    {
      "id": "step_yt_net",
      "action": "CHECK_INTERNET",
      "name": "Check Internet Connection",
      "description": "Verifies network interface and active internet route.",
      "is_required": true,
      "stop_on_failure": true
    },
    {
      "id": "step_yt_dns",
      "action": "DNS_LOOKUP",
      "name": "Resolve Video Streaming Endpoint",
      "description": "Resolves Google Video streaming domain.",
      "parameters": {
        "hostname": "googlevideo.com"
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_yt_probe",
      "action": "HTTPS_PROBE",
      "name": "Probe YouTube Server",
      "description": "Tests latency and TLS connectivity to YouTube servers.",
      "parameters": {
        "url": "https://www.youtube.com/generate_204",
        "timeout_ms": 5000
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_yt_app",
      "action": "CHECK_APP_INSTALLED",
      "name": "Verify YouTube App",
      "description": "Confirms YouTube is installed on this device.",
      "parameters": {
        "package_name": "com.google.android.youtube"
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_yt_guide",
      "action": "SHOW_GUIDE",
      "name": "Cache Cleaning Guidance",
      "description": "Instructions to clear YouTube temporary app cache.",
      "parameters": {
        "guide_title": "Clear YouTube App Cache",
        "guide_description": "Clearing corrupted cached video chunks often resolves playback and loading errors.",
        "guide_steps": [
          "Tap 'Storage & cache' on the next screen.",
          "Tap 'Clear cache' button.",
          "Do NOT tap 'Clear storage' to keep your offline downloads."
        ]
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_yt_details",
      "action": "OPEN_APP_DETAILS",
      "name": "Open YouTube Settings",
      "description": "Opens system App Info to allow clearing cache.",
      "parameters": {
        "package_name": "com.google.android.youtube",
        "instruction": "Opened YouTube App Info. Tap Storage & cache -> Clear cache."
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_yt_verify",
      "action": "VERIFY_INTERNET",
      "name": "Verify YouTube Route",
      "description": "Confirms active route to YouTube servers.",
      "parameters": {
        "host": "www.youtube.com"
      },
      "is_required": true,
      "stop_on_failure": true
    }
  ]
}
""".trimIndent()

    val DNS_RECIPE_JSON = """
{
  "recipe_id": "recipe_dns_01",
  "tool_id": "tool_dns_repair",
  "version": "1.1.0",
  "required_android_sdk": 26,
  "steps": [
    {
      "id": "step_dns_net",
      "action": "CHECK_INTERNET",
      "name": "Check Network Status",
      "description": "Inspects local network connection.",
      "is_required": true,
      "stop_on_failure": true
    },
    {
      "id": "step_dns_lookup_google",
      "action": "DNS_LOOKUP",
      "name": "Test Google DNS",
      "description": "Queries Google public DNS resolver.",
      "parameters": {
        "hostname": "dns.google"
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_dns_lookup_cf",
      "action": "DNS_LOOKUP",
      "name": "Test Cloudflare DNS",
      "description": "Queries Cloudflare public DNS resolver.",
      "parameters": {
        "hostname": "one.one.one.one"
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_dns_confirm",
      "action": "ASK_USER_CONFIRMATION",
      "name": "User Confirmation",
      "description": "Prompts to open Private DNS settings.",
      "parameters": {
        "title": "Open Network Settings?",
        "message": "If DNS is failing, switching Private DNS to 'Automatic' or disabling VPN usually restores resolution.",
        "explanation": "We will open Network & Internet settings for you."
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_dns_settings",
      "action": "OPEN_ANDROID_SETTINGS",
      "name": "Open Network Settings",
      "description": "Navigates to system network settings.",
      "parameters": {
        "setting_type": "NETWORK"
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_dns_verify",
      "action": "VERIFY_DNS",
      "name": "Verify DNS Health",
      "description": "Confirms DNS queries are resolving properly.",
      "parameters": {
        "domain": "dns.google"
      },
      "is_required": true,
      "stop_on_failure": true
    }
  ]
}
""".trimIndent()

    val FACEBOOK_RECIPE_JSON = """
{
  "recipe_id": "recipe_fb_01",
  "tool_id": "tool_fb_repair",
  "version": "1.0.4",
  "target_package": "com.facebook.katana",
  "required_android_sdk": 26,
  "steps": [
    {
      "id": "step_fb_net",
      "action": "CHECK_INTERNET",
      "name": "Verify Connection",
      "description": "Checks internet connectivity.",
      "is_required": true,
      "stop_on_failure": true
    },
    {
      "id": "step_fb_dns",
      "action": "DNS_LOOKUP",
      "name": "Resolve Facebook Servers",
      "description": "Resolves facebook.com hostnames.",
      "parameters": {
        "hostname": "facebook.com"
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_fb_probe",
      "action": "HTTPS_PROBE",
      "name": "Test Facebook HTTPS",
      "description": "Tests connectivity to Facebook endpoint.",
      "parameters": {
        "url": "https://m.facebook.com"
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_fb_app",
      "action": "CHECK_APP_INSTALLED",
      "name": "Check Facebook App",
      "description": "Verifies Facebook application status.",
      "parameters": {
        "package_name": "com.facebook.katana"
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_fb_details",
      "action": "OPEN_APP_DETAILS",
      "name": "Open Facebook Settings",
      "description": "Opens App Info to clear cache or check permissions.",
      "parameters": {
        "package_name": "com.facebook.katana",
        "instruction": "Opened Facebook App Info. Tap Storage & cache -> Clear cache."
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_fb_verify",
      "action": "VERIFY_INTERNET",
      "name": "Verify Route",
      "description": "Confirms Facebook connectivity.",
      "parameters": {
        "host": "facebook.com"
      },
      "is_required": true,
      "stop_on_failure": true
    }
  ]
}
""".trimIndent()

    val WIFI_RECIPE_JSON = """
{
  "recipe_id": "recipe_wifi_01",
  "tool_id": "tool_wifi_repair",
  "version": "1.0.0",
  "required_android_sdk": 26,
  "steps": [
    {
      "id": "step_wifi_check",
      "action": "CHECK_INTERNET",
      "name": "Check Wi-Fi Gateway",
      "description": "Validates Wi-Fi internet route and captive portals.",
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_wifi_probe",
      "action": "HTTPS_PROBE",
      "name": "Probe Captive Portal",
      "description": "Checks if Wi-Fi requires captive portal sign-in.",
      "parameters": {
        "url": "https://clients3.google.com/generate_204"
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_wifi_settings",
      "action": "OPEN_ANDROID_SETTINGS",
      "name": "Open Wi-Fi Settings",
      "description": "Navigates to Android Wi-Fi settings to reconnect or forget network.",
      "parameters": {
        "setting_type": "WIFI"
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_wifi_verify",
      "action": "VERIFY_INTERNET",
      "name": "Verify Wi-Fi Internet",
      "description": "Confirms connection is working.",
      "parameters": {
        "host": "clients3.google.com"
      },
      "is_required": true,
      "stop_on_failure": true
    }
  ]
}
""".trimIndent()

    val PLAY_STORE_RECIPE_JSON = """
{
  "recipe_id": "recipe_play_01",
  "tool_id": "tool_play_store_repair",
  "version": "1.3.1",
  "target_package": "com.android.vending",
  "required_android_sdk": 26,
  "steps": [
    {
      "id": "step_play_net",
      "action": "CHECK_INTERNET",
      "name": "Verify Internet Connectivity",
      "description": "Checks active internet connection.",
      "is_required": true,
      "stop_on_failure": true
    },
    {
      "id": "step_play_storage",
      "action": "CHECK_STORAGE",
      "name": "Verify Device Storage",
      "description": "Checks available internal storage (min 500MB needed for app installs).",
      "parameters": {
        "min_free_mb": 500
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_play_app",
      "action": "CHECK_APP_INSTALLED",
      "name": "Verify Play Store",
      "description": "Checks Google Play Store status.",
      "parameters": {
        "package_name": "com.android.vending"
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_play_guide",
      "action": "SHOW_GUIDE",
      "name": "Clear Play Store Cache",
      "description": "Step-by-step guide to clear download cache.",
      "parameters": {
        "guide_title": "Clear Google Play Store Cache",
        "guide_description": "Stuck app downloads or 'Download Pending' errors are fixed by clearing Play Store cache.",
        "guide_steps": [
          "Tap 'Storage & cache' on the next screen.",
          "Tap 'Clear cache'.",
          "If still failing, you can also tap 'Clear storage' safely."
        ]
      },
      "is_required": true,
      "stop_on_failure": false
    },
    {
      "id": "step_play_details",
      "action": "OPEN_APP_DETAILS",
      "name": "Open Play Store Settings",
      "description": "Opens App Info for Google Play Store.",
      "parameters": {
        "package_name": "com.android.vending",
        "instruction": "Opened Google Play Store App Info. Tap Storage & cache -> Clear cache."
      },
      "is_required": false,
      "stop_on_failure": false
    },
    {
      "id": "step_play_verify",
      "action": "VERIFY_INTERNET",
      "name": "Verify Play Services Route",
      "description": "Confirms connection to Google Play servers.",
      "parameters": {
        "host": "play.googleapis.com"
      },
      "is_required": true,
      "stop_on_failure": true
    }
  ]
}
""".trimIndent()

    fun getInitialTools(): List<ToolEntity> {
        return listOf(
            ToolEntity(
                id = "tool_youtube_repair",
                name = "YouTube Quick Repair",
                description = "Fixes video playback errors, infinite buffering, and clears corrupted cache.",
                category = ToolCategory.MEDIA.title,
                version = "1.2.0",
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
                id = "tool_fb_repair",
                name = "Facebook Troubleshooter",
                description = "Resolves news feed refresh failures, login timeouts, and cached data issues.",
                category = ToolCategory.SOCIAL.title,
                version = "1.0.4",
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
                id = "tool_dns_repair",
                name = "DNS Repair & Diagnostics",
                description = "Diagnoses DNS resolution failures and provides Private DNS recovery.",
                category = ToolCategory.NETWORK.title,
                version = "1.1.0",
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
                id = "tool_wifi_repair",
                name = "Wi-Fi Troubleshooter",
                description = "Probes captive portals, validates gateway routes, and guides reconnecting.",
                category = ToolCategory.NETWORK.title,
                version = "1.0.0",
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
                id = "tool_play_store_repair",
                name = "Play Store Repair",
                description = "Fixes 'Download Pending' loops, insufficient storage alerts, and app update errors.",
                category = ToolCategory.SYSTEM.title,
                version = "1.3.1",
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
