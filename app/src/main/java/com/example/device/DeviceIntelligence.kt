package com.example.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.BuildConfig
import java.util.Locale

data class DeviceIntelligenceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val buildDisplay: String,
    val cpuAbis: List<String>,
    val totalRamBytes: Long?,
    val availableRamBytes: Long?,
    val totalStorageBytes: Long?,
    val availableStorageBytes: Long?,
    val batteryPercent: Int?,
    val displayWidthPx: Int?,
    val displayHeightPx: Int?,
    val densityDpi: Int?,
    val networkTransport: String,
    val networkValidated: Boolean,
    val appVersion: String,
    val hardwareCapabilities: List<String>
)

/**
 * Reads public Android platform diagnostics only. It never reads serial, IMEI,
 * MAC address, account identifiers, installed-app inventory, location, or personal content.
 * Unknown platform values remain null/Unavailable rather than being fabricated.
 */
object DeviceIntelligence {
    fun collect(context: Context): DeviceIntelligenceSnapshot {
        val appContext = context.applicationContext
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        val hasMemoryInfo = runCatching {
            activityManager?.getMemoryInfo(memoryInfo)
            activityManager != null
        }.getOrDefault(false)

        val storage = runCatching {
            StatFs(Environment.getDataDirectory().absolutePath)
        }.getOrNull()

        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryFromIntent = if (batteryLevel >= 0 && batteryScale > 0) {
            ((batteryLevel * 100f) / batteryScale).toInt().coerceIn(0, 100)
        } else {
            null
        }
        val batteryManager = appContext.getSystemService(BatteryManager::class.java)
        val batteryFromManager = runCatching {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()?.takeIf { it in 0..100 }

        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkCapabilities = runCatching {
            connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
        }.getOrNull()
        val networkTransport = when {
            networkCapabilities == null -> "Offline or unavailable"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Connected (transport not identified)"
        }

        val metrics = DisplayMetrics()
        val displayAvailable = runCatching {
            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val display = windowManager?.defaultDisplay
            if (display != null) display.getRealMetrics(metrics)
            display != null
        }.getOrDefault(false)

        val packageManager = appContext.packageManager
        val capabilities = listOf(
            "Camera" to PackageManager.FEATURE_CAMERA_ANY,
            "Bluetooth" to PackageManager.FEATURE_BLUETOOTH,
            "Bluetooth LE" to PackageManager.FEATURE_BLUETOOTH_LE,
            "Fingerprint hardware" to PackageManager.FEATURE_FINGERPRINT,
            "NFC" to PackageManager.FEATURE_NFC,
            "Telephony" to PackageManager.FEATURE_TELEPHONY,
            "Wi-Fi" to PackageManager.FEATURE_WIFI,
            "USB host" to PackageManager.FEATURE_USB_HOST
        ).filter { (_, feature) ->
            runCatching { packageManager.hasSystemFeature(feature) }.getOrDefault(false)
        }.map { it.first }

        val buildDisplay = listOf(Build.DISPLAY, Build.ID)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
            .ifBlank { "Unavailable" }

        return DeviceIntelligenceSnapshot(
            manufacturer = Build.MANUFACTURER.trim().ifBlank { "Unavailable" },
            model = Build.MODEL.trim().ifBlank { "Unavailable" },
            androidVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "Unavailable",
            sdkInt = Build.VERSION.SDK_INT,
            buildDisplay = buildDisplay,
            cpuAbis = Build.SUPPORTED_ABIS.filter { it.isNotBlank() },
            totalRamBytes = if (hasMemoryInfo) memoryInfo.totalMem.takeIf { it > 0L } else null,
            availableRamBytes = if (hasMemoryInfo) memoryInfo.availMem.takeIf { it >= 0L } else null,
            totalStorageBytes = storage?.totalBytes?.takeIf { it > 0L },
            availableStorageBytes = storage?.availableBytes?.takeIf { it >= 0L },
            batteryPercent = batteryFromIntent ?: batteryFromManager,
            displayWidthPx = if (displayAvailable) metrics.widthPixels.takeIf { it > 0 } else null,
            displayHeightPx = if (displayAvailable) metrics.heightPixels.takeIf { it > 0 } else null,
            densityDpi = if (displayAvailable) metrics.densityDpi.takeIf { it > 0 } else null,
            networkTransport = networkTransport,
            networkValidated = networkCapabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ) == true,
            appVersion = BuildConfig.VERSION_NAME,
            hardwareCapabilities = capabilities
        )
    }

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "Unavailable"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        do {
            value /= 1024.0
            unitIndex++
        } while (value >= 1024.0 && unitIndex < units.lastIndex)
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}