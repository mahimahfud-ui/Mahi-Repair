package com.example.data.remote

import com.example.data.remote.dto.RegisterDeviceRequest
import com.example.domain.intelligence.DynamicTelemetry
import com.example.domain.intelligence.FieldAvailability
import com.example.domain.intelligence.HardwareProfile
import com.example.domain.intelligence.SensorInfo
import java.io.IOException

/**
 * Sends the current device profile through the authenticated Supabase
 * register-device Edge Function. No legacy Express API or alternate data
 * store is used.
 */
class DeviceIntelligenceApiClient(
    private val supabaseClient: SupabaseClient = SupabaseClient()
) {
    suspend fun upload(
        accessToken: String,
        deviceId: String,
        deviceLabel: String,
        appVersion: String,
        androidVersion: String,
        androidApi: Int,
        hardwareProfile: HardwareProfile,
        telemetry: DynamicTelemetry,
        fieldAvailability: Map<String, FieldAvailability>
    ): Result<Unit> {
        if (accessToken.isBlank() || deviceId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("A valid session and registered device are required.")
            )
        }

        return try {
            val request = RegisterDeviceRequest(
                deviceId = deviceId,
                deviceLabel = deviceLabel.trim().take(60),
                appVersion = appVersion,
                androidVersion = androidVersion,
                androidApi = androidApi,
                hardwareProfile = hardwareProfile.toApiMap(),
                telemetry = telemetry.toApiMap(),
                fieldAvailability = fieldAvailability.mapValues { (_, value) -> value.name }
            )
            supabaseClient.registerDevice(request, accessToken).map { registeredId ->
                if (registeredId != deviceId) {
                    throw IOException("Supabase returned a different device identity.")
                }
                Unit
            }
        } catch (error: Exception) {
            Result.failure(IOException("Device profile sync could not be completed.", error))
        }
    }
}

private fun HardwareProfile.toApiMap(): Map<String, Any?> = mapOf(
    "manufacturer" to manufacturer,
    "brand" to brand,
    "model" to model,
    "commercialModelName" to commercialModelName,
    "modelIdentifier" to modelIdentifier,
    "productName" to productName,
    "deviceCodename" to deviceCodename,
    "hardware" to hardware,
    "socManufacturer" to socManufacturer,
    "socModel" to socModel,
    "cpuAbis" to cpuAbis,
    "processorArchitecture" to processorArchitecture,
    "cpuCoreCount" to cpuCoreCount,
    "androidRelease" to androidRelease,
    "androidSdk" to androidSdk,
    "sdkMinorVersion" to sdkMinorVersion,
    "securityPatchDate" to securityPatchDate,
    "buildDisplayInfo" to buildDisplayInfo,
    "screenWidthPx" to screenWidthPx,
    "screenHeightPx" to screenHeightPx,
    "densityDpi" to densityDpi,
    "refreshRateHz" to refreshRateHz,
    "supportedFeatures" to supportedFeatures,
    "availableSensors" to availableSensors.map(SensorInfo::toApiMap),
    "cameraAvailable" to cameraAvailable,
    "profileConsentVersion" to profileConsentVersion,
    "collectedAt" to collectedAt
)

private fun SensorInfo.toApiMap(): Map<String, Any?> = mapOf(
    "type" to type,
    "name" to name,
    "vendor" to vendor
)

private fun DynamicTelemetry.toApiMap(): Map<String, Any?> = mapOf(
    "totalRamBytes" to totalRamBytes,
    "availableRamBytes" to availableRamBytes,
    "isLowMemory" to isLowMemory,
    "internalStorageTotalBytes" to internalStorageTotalBytes,
    "internalStorageAvailableBytes" to internalStorageAvailableBytes,
    "storagePressure" to storagePressure,
    "appSpecificStorageBytes" to appSpecificStorageBytes,
    "batteryLevel" to batteryLevel,
    "chargingStatus" to chargingStatus,
    "powerSource" to powerSource,
    "batteryTemperature" to batteryTemperature,
    "batteryHealth" to batteryHealth,
    "batteryCapacityEstimateMah" to batteryCapacityEstimateMah,
    "batterySaverActive" to batterySaverActive,
    "thermalStatus" to thermalStatus,
    "networkConnectivity" to networkConnectivity,
    "networkTransport" to networkTransport,
    "internetValidated" to internetValidated,
    "dnsResolutionLatencyMs" to dnsResolutionLatencyMs,
    "httpsProbeLatencyMs" to httpsProbeLatencyMs,
    "connectionFailureDetails" to connectionFailureDetails?.take(500),
    "grantedPermissions" to grantedPermissions,
    "detectedRepairApps" to detectedRepairApps.map { app ->
        mapOf(
            "packageName" to app.packageName,
            "installed" to app.installed,
            "versionName" to app.versionName
        )
    },
    "lastTelemetryUpdatedAt" to lastTelemetryUpdatedAt
)
