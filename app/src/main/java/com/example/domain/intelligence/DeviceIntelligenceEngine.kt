package com.example.domain.intelligence

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Field-level availability classification according to safety and privacy guidelines.
 */
enum class FieldAvailability {
    OBSERVED,
    ESTIMATED,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    UNSUPPORTED
}

data class SensorInfo(
    val type: Int,
    val name: String,
    val vendor: String
)

data class DetectedAppInfo(
    val packageName: String,
    val installed: Boolean,
    val versionName: String? = null
)

data class HardwareProfile(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val commercialModelName: String?,
    val modelIdentifier: String?,
    val productName: String,
    val deviceCodename: String,
    val hardware: String,
    val socManufacturer: String,
    val socModel: String,
    val cpuAbis: List<String>,
    val processorArchitecture: String,
    val cpuCoreCount: Int,
    val androidRelease: String,
    val androidSdk: Int,
    val sdkMinorVersion: String?,
    val securityPatchDate: String,
    val buildDisplayInfo: String,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val supportedFeatures: List<String>,
    val availableSensors: List<SensorInfo>,
    val cameraAvailable: Boolean,
    val profileConsentVersion: Int = 1,
    val collectedAt: String
)

data class DynamicTelemetry(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowMemory: Boolean,
    val internalStorageTotalBytes: Long,
    val internalStorageAvailableBytes: Long,
    val storagePressure: String, // NORMAL, MODERATE, LOW, CRITICAL
    val appSpecificStorageBytes: Long,
    val batteryLevel: Int,
    val chargingStatus: String, // CHARGING, DISCHARGING, NOT_CHARGING, FULL, UNKNOWN
    val powerSource: String, // AC, USB, WIRELESS, BATTERY, UNKNOWN
    val batteryTemperature: Float?,
    val batteryHealth: String, // GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, UNSPECIFIED_FAILURE, COLD, UNKNOWN
    val batteryCapacityEstimateMah: Int?,
    val batterySaverActive: Boolean,
    val thermalStatus: String, // NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN
    val networkConnectivity: String, // CONNECTED, DISCONNECTED, CAPTIVE_PORTAL
    val networkTransport: String, // WIFI, CELLULAR, ETHERNET, BLUETOOTH, VPN, NONE
    val internetValidated: Boolean,
    val dnsResolutionLatencyMs: Long?,
    val httpsProbeLatencyMs: Long?,
    val connectionFailureDetails: String?,
    val grantedPermissions: List<String>,
    val detectedRepairApps: List<DetectedAppInfo>,
    val lastTelemetryUpdatedAt: String
)

data class DeviceProfile(
    val appInstallationId: String,
    val hardwareProfile: HardwareProfile,
    val telemetry: DynamicTelemetry,
    val fieldAvailability: Map<String, FieldAvailability>,
    val profileVersion: Int = 1,
    val appVersion: String = "1.2.0"
)

/**
 * Real Device Intelligence Engine for Android
 * Collects hardware attributes, memory, storage, battery, thermal status,
 * display metrics, and diagnostic latency using only official Android APIs.
 *
 * Invariant: Never invents commercial names or battery health estimates.
 * Invariant: Never accesses private data, SMS, contacts, or camera feeds.
 */
class DeviceIntelligenceEngine(private val context: Context) {

    /**
     * Collects static hardware profile information.
     */
    fun collectHardwareProfile(): Pair<HardwareProfile, Map<String, FieldAvailability>> {
        val now = java.time.Instant.now().toString()
        val availMap = mutableMapOf<String, FieldAvailability>()

        val manufacturer = Build.MANUFACTURER.ifBlank { "UNKNOWN" }
        val brand = Build.BRAND.ifBlank { "UNKNOWN" }
        val model = Build.MODEL.ifBlank { "UNKNOWN" }
        val product = Build.PRODUCT.ifBlank { "UNKNOWN" }
        val deviceCodename = Build.DEVICE.ifBlank { "UNKNOWN" }
        val hardware = Build.HARDWARE.ifBlank { "UNKNOWN" }

        // SoC manufacturer & model (available on API 31+)
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            availMap["socManufacturer"] = FieldAvailability.OBSERVED
            Build.SOC_MANUFACTURER.ifBlank { "UNKNOWN" }
        } else {
            availMap["socManufacturer"] = FieldAvailability.UNSUPPORTED
            "UNKNOWN"
        }

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            availMap["socModel"] = FieldAvailability.OBSERVED
            Build.SOC_MODEL.ifBlank { "UNKNOWN" }
        } else {
            availMap["socModel"] = FieldAvailability.UNSUPPORTED
            "UNKNOWN"
        }

        val cpuAbis = Build.SUPPORTED_ABIS.toList().ifEmpty { listOf("UNKNOWN") }
        val processorArch = System.getProperty("os.arch") ?: "UNKNOWN"
        val cpuCoreCount = Runtime.getRuntime().availableProcessors()

        val androidRelease = Build.VERSION.RELEASE.ifBlank { "UNKNOWN" }
        val sdkInt = Build.VERSION.SDK_INT
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH.ifBlank { "UNKNOWN" }
        } else {
            "UNKNOWN"
        }
        val buildDisplay = Build.DISPLAY.ifBlank { "UNKNOWN" }

        // Display metrics
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val displayMetrics = DisplayMetrics()
        var refreshRate = 60f

        if (windowManager != null) {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay?.let { disp ->
                disp.getRealMetrics(displayMetrics)
                refreshRate = disp.refreshRate
            }
        } else {
            val dm = context.resources.displayMetrics
            displayMetrics.widthPixels = dm.widthPixels
            displayMetrics.heightPixels = dm.heightPixels
            displayMetrics.densityDpi = dm.densityDpi
        }

        // Hardware system features inspection (no hardware activation)
        val pm = context.packageManager
        val features = mutableListOf<String>()
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) features.add("android.hardware.camera")
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) features.add("android.hardware.camera.autofocus")
        if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI)) features.add("android.hardware.wifi")
        if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) features.add("android.hardware.bluetooth")
        if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) features.add("android.hardware.nfc")
        if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) features.add("android.hardware.biometrics")
        if (pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) features.add("android.hardware.sensor.accelerometer")
        if (pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) features.add("android.hardware.sensor.gyroscope")

        // Sensor inventory inspection (presence inspection only, no sensor listeners active)
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensorList = sensorManager?.getSensorList(Sensor.TYPE_ALL)?.map {
            SensorInfo(it.type, it.name, it.vendor)
        } ?: emptyList()

        val cameraAvailable = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        val profile = HardwareProfile(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            commercialModelName = model, // Report observed model without fabrication
            modelIdentifier = Build.MODEL.ifBlank { "UNKNOWN" },
            productName = product,
            deviceCodename = deviceCodename,
            hardware = hardware,
            socManufacturer = socManufacturer,
            socModel = socModel,
            cpuAbis = cpuAbis,
            processorArchitecture = processorArch,
            cpuCoreCount = cpuCoreCount,
            androidRelease = androidRelease,
            androidSdk = sdkInt,
            sdkMinorVersion = null,
            securityPatchDate = securityPatch,
            buildDisplayInfo = buildDisplay,
            screenWidthPx = displayMetrics.widthPixels,
            screenHeightPx = displayMetrics.heightPixels,
            densityDpi = displayMetrics.densityDpi,
            refreshRateHz = refreshRate,
            supportedFeatures = features,
            availableSensors = sensorList,
            cameraAvailable = cameraAvailable,
            profileConsentVersion = 1,
            collectedAt = now
        )

        return Pair(profile, availMap)
    }

    /**
     * Collects dynamic telemetry including memory, storage, battery, thermal, and network.
     */
    suspend fun collectDynamicTelemetry(): DynamicTelemetry = withContext(Dispatchers.IO) {
        val now = java.time.Instant.now().toString()

        // 1. RAM Memory
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val isLowMem = memInfo.lowMemory

        // 2. Internal Storage
        val dataDir = Environment.getDataDirectory()
        val statFs = StatFs(dataDir.path)
        val totalStorage = statFs.totalBytes
        val availStorage = statFs.availableBytes

        val storagePressure = when {
            availStorage < 500L * 1024 * 1024 -> "CRITICAL" // < 500MB
            availStorage < 2L * 1024 * 1024 * 1024 -> "LOW" // < 2GB
            availStorage < 5L * 1024 * 1024 * 1024 -> "MODERATE" // < 5GB
            else -> "NORMAL"
        }

        // App-specific private storage footprint
        val appSpecificStorage = calculateDirectorySize(context.filesDir) + calculateDirectorySize(context.cacheDir)

        // 3. Battery & Power
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (rawLevel >= 0 && scale > 0) ((rawLevel * 100) / scale) else 100

        val rawStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargingStatus = when (rawStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }

        val rawPlugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val powerSource = when (rawPlugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            0 -> "BATTERY"
            else -> "UNKNOWN"
        }

        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryTemp = if (rawTemp > 0) (rawTemp / 10.0f) else null

        val rawHealth = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val batteryHealth = when (rawHealth) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            else -> "UNKNOWN"
        }

        // Power Manager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batterySaver = powerManager?.isPowerSaveMode ?: false

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NONE"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "UNKNOWN"
            }
        } else {
            "NONE"
        }

        // 4. Network Connectivity & Transport
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = connManager?.activeNetwork
        val netCaps = activeNet?.let { connManager.getNetworkCapabilities(it) }

        val isConnected = netCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = netCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        val networkConnectivity = when {
            isValidated -> "CONNECTED"
            isConnected -> "CAPTIVE_PORTAL"
            else -> "DISCONNECTED"
        }

        val networkTransport = when {
            netCaps == null -> "NONE"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "NONE"
        }

        // Diagnostic Latency Probes (non-invasive DNS and HTTPS probe)
        var dnsLatency: Long? = null
        var httpsLatency: Long? = null
        var connFailure: String? = null

        if (isConnected) {
            try {
                val startDns = System.currentTimeMillis()
                InetAddress.getByName("connectivitycheck.gstatic.com")
                dnsLatency = System.currentTimeMillis() - startDns
            } catch (e: Exception) {
                connFailure = "DNS resolution error: ${e.message}"
            }

            try {
                val startHttps = System.currentTimeMillis()
                val url = URL("https://connectivitycheck.gstatic.com/generate_204")
                val conn = url.openConnection() as HttpsURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.instanceFollowRedirects = false
                val code = conn.responseCode
                if (code == 204 || code in 200..299) {
                    httpsLatency = System.currentTimeMillis() - startHttps
                }
                conn.disconnect()
            } catch (e: Exception) {
                if (connFailure == null) connFailure = "HTTPS probe error: ${e.message}"
            }
        }

        // 5. Granted Permissions Check
        val pm = context.packageManager
        val myPackage = context.packageName
        val declaredPermissions = listOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                android.Manifest.permission.POST_NOTIFICATIONS else "POST_NOTIFICATIONS",
            android.Manifest.permission.CAMERA
        )
        val grantedPermissions = declaredPermissions.filter { perm ->
            pm.checkPermission(perm, myPackage) == PackageManager.PERMISSION_GRANTED
        }

        // 6. Relevant Repair Target Apps (Scoped to explicit queries)
        val repairTargetPackages = listOf(
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.android.vending",
            "com.google.android.gms"
        )
        val detectedApps = repairTargetPackages.map { pkg ->
            try {
                val info = pm.getPackageInfo(pkg, 0)
                DetectedAppInfo(pkg, installed = true, versionName = info.versionName)
            } catch (_: PackageManager.NameNotFoundException) {
                DetectedAppInfo(pkg, installed = false)
            }
        }

        DynamicTelemetry(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            isLowMemory = isLowMem,
            internalStorageTotalBytes = totalStorage,
            internalStorageAvailableBytes = availStorage,
            storagePressure = storagePressure,
            appSpecificStorageBytes = appSpecificStorage,
            batteryLevel = batteryPct,
            chargingStatus = chargingStatus,
            powerSource = powerSource,
            batteryTemperature = batteryTemp,
            batteryHealth = batteryHealth,
            batteryCapacityEstimateMah = null, // Invariant: do not fabricate capacity
            batterySaverActive = batterySaver,
            thermalStatus = thermalStatus,
            networkConnectivity = networkConnectivity,
            networkTransport = networkTransport,
            internetValidated = isValidated,
            dnsResolutionLatencyMs = dnsLatency,
            httpsProbeLatencyMs = httpsLatency,
            connectionFailureDetails = connFailure,
            grantedPermissions = grantedPermissions,
            detectedRepairApps = detectedApps,
            lastTelemetryUpdatedAt = now
        )
    }

    private fun calculateDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return total
    }
}
