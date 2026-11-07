package com.example.data.local

import com.example.domain.models.RepairTool
import com.example.domain.models.ToolCategory

/**
 * Bundles verified default repair recipes signed by the Owner's key.
 * Used for offline execution, first-launch caching, and instant testing.
 * Contains only detached signatures verified by the public key.
 * Zero private key material exists in this application.
 */
object DefaultToolCatalog {

    const val YOUTUBE_SIGNATURE = "TlcEmgmxYY/+y5kyKzQq+016GuOs8vBOprXAH4xnJHScypGSt7KRfVbSXogzwfTI9OSZ+m9wUqIjvDCvs9LY9UicIpljsrPZcO+HAa5AHv+YHhEPXWq7ws0UE1bh2nKLieDWQ2XOC0/vCYx4hO7IsjsA/qkthlLg0NVQ4LSzx2rVxxfUs9MaHGcP5JcWCWjEYbbwt8plwvwlGCqOl16skXnakK8OCq3gaE47HEf5VtzaEl03WACOS5wiNCLGX4b186V/h4VekRCrA7l+q2BBwVakWmlXF9RcjyQtyf4j1m6RcoZbx4oY+V/VQtXvbbxEMuUk3XQygafdy9RC0A+/Gw=="
    const val FACEBOOK_SIGNATURE = "rQ9jtgSDyek7RrSGcYhL0M5s/pQ/xRd4CbEXD2ojJrtk+UYcoDnY4vv9HcmGJnO/h36zjB0ib+aORwIc32HQG4EkcT59+FrTnOQNkcSv7XejbKZQCdzOHZXYI68kxGon1HBdMf0r0tyL5ItFXWRvXOOQIa25SaHd33fjRCBQpnXQG2+LuBS1v9Qsd84+yFz0eFRJfjQzO466eaPipx4/kjjDPQt54LaKaaEnpxjbN4G77iffKLH3rWV5P9VFWF0tJWzUQR1LFotDxMxlJnM7FvnuGqzqXChM47Pa2RzGBON9HCVcatzDge0C1mEHxzh7zILepkuLKh8aj0mnlZsRag=="
    const val DNS_SIGNATURE = "TuQLk6VXOVlxFX1f42bkGYX4q6wmiujVIdG1Nt/VuSGOoHz6MR8KyEUSsPwsby6WW0F+o63K3jHaM6dpzbf99EtclicY0QerKG5/ssXHmlW+mSlzWng0Rcvjxu7Pm4qVC7eeiWZjDWxraOhAd8rD6CfpFAVj5rJlWh5mC3NnXT2Gku+kuML4YmtmAyXIBzXkr2LyqoC4l0lgQ+yI6BZVuaawd0H8jDmZkEY0J1q4HH8cGFFRpJbmpC82mKanPkFrKK1z5tRN/6OL0XHFFtkifbVR0uYQMTVw2MFxHpkExPn7fEiU0JtsUyURc21BR20tEZVANXzdABwOebQzi4dlhA=="
    const val WIFI_SIGNATURE = "sweasuPcPeWN0EX29Q4iDo3unAWtjhGEfjrN7TFjRSAIh4eqUbIyL5i4LskNdYhMjCP14ClmXt4RrD3af9X0pGv0f3TUgrwahIbcAkIbJU85LJ8Cfnj3lk88oGH2JvhQd/HnR74N0H0S5JOMPhJnmXHLnchyFQvLnM6Z5gJHap+AQlEiZ5eH+uZLDDY1mDMGhrwz+Uwby7Iibd/CpSXJncKmL76QS2XyPJBzBvHzX3cvhjJT74CjR0GqeWPenY0Rv+LBnG16pFh1hBO58r0Z9G2d6imDB8yyZa9fMWJBuwlrM2MfLpmJGZcmASexuykJDERNZpN9WNOTcL9ZYgSCAg=="
    const val PLAY_STORE_SIGNATURE = "hnxu/ZzyEE+fSR44VuxDAUm2ZCGmmKGPPVOxXipqGyAkvQ4xrLqnL/SYhNIrn+CIU7b/kaqe4QUbSPy1S71F7Fz0lcTTvYD0AM63zDMgvXlTJGKGfOLfi9BIO9PFeMabmWggHatiULr6fM1zhfoXLOil/mloAUzWjR3FTP7XLd8UD5i3DtMZ38DeKmBT6Ls2AzGXLxdqhI1ujpk+TQ7JO9eIXmBCZ+5xKGsqA65JGms9g5SgE/ro+s42yp7FkyEZFiQ8iwWrJUqLxiuh3QI5dNNoDvbNq9kW6JF6aP9n411QEI4/0G65egfUkwQD6D98Drjbki5CfnRZ4dBcHk7sRw=="

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
                signatureBase64 = YOUTUBE_SIGNATURE,
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
                signatureBase64 = FACEBOOK_SIGNATURE,
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
                signatureBase64 = DNS_SIGNATURE,
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
                signatureBase64 = WIFI_SIGNATURE,
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
                signatureBase64 = PLAY_STORE_SIGNATURE,
                lastUpdated = System.currentTimeMillis() - 259200000L
            )
        )
    }
}
