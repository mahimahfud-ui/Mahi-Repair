package com.example.data.remote

import com.example.BuildConfig
import com.example.domain.intelligence.DynamicTelemetry
import com.example.domain.intelligence.FieldAvailability
import com.example.domain.intelligence.HardwareProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Uploads consented hardware and health telemetry through the authenticated
 * Supabase register-device Edge Function. No legacy staging API is used.
 */
class DeviceIntelligenceApiClient(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/'),
    private val publishableKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank() || deviceId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("A valid session and registered device are required.")
            )
        }
        if (!supabaseUrl.startsWith("https://") ||
            supabaseUrl.contains("placeholder.supabase.co") ||
            publishableKey.isBlank() ||
            publishableKey.contains("placeholder")
        ) {
            return@withContext Result.failure(
                IllegalStateException("Supabase device-profile service is not configured.")
            )
        }

        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("device_label", deviceLabel.take(60))
                put("app_version", appVersion)
                put("android_version", androidVersion)
                put("android_api", androidApi)
                put("hardware_profile", hardwareProfile.toJson())
                put("telemetry", telemetry.toJson())
                put("field_availability", JSONObject().apply {
                    fieldAvailability.forEach { (key, value) -> put(key, value.name) }
                })
            }

            val request = Request.Builder()
                .url("$supabaseUrl/functions/v1/register-device")
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Device profile sync failed (HTTP " + response.code + ").")
                    )
                }

                val envelope = runCatching { JSONObject(responseBody) }.getOrNull()
                    ?: return@withContext Result.failure(
                        IOException("Device profile service returned an invalid response.")
                    )
                val data = envelope.optJSONObject("data")
                    ?: return@withContext Result.failure(
                        IOException("Device profile service did not confirm registration.")
                    )
                if (data.optString("device_id") != deviceId ||
                    !data.optBoolean("registered") ||
                    !data.optBoolean("profile_updated")
                ) {
                    return@withContext Result.failure(
                        IOException("Server did not confirm this device profile update.")
                    )
                }
                Result.success(Unit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(IOException("Device profile could not be synchronized with Supabase.", error))
        }
    }

    private fun HardwareProfile.toJson() = JSONObject().apply {
        put("manufacturer", manufacturer)
        put("brand", brand)
        put("model", model)
        commercialModelName?.let { put("commercialModelName", it) }
        modelIdentifier?.let { put("modelIdentifier", it) }
        put("productName", productName)
        put("deviceCodename", deviceCodename)
        put("hardware", hardware)
        put("socManufacturer", socManufacturer)
        put("socModel", socModel)
        put("cpuAbis", JSONArray(cpuAbis))
        put("processorArchitecture", processorArchitecture)
        put("cpuCoreCount", cpuCoreCount)
        put("androidRelease", androidRelease)
        put("androidSdk", androidSdk)
        sdkMinorVersion?.let { put("sdkMinorVersion", it) }
        put("securityPatchDate", securityPatchDate)
        put("buildDisplayInfo", buildDisplayInfo)
        put("screenWidthPx", screenWidthPx)
        put("screenHeightPx", screenHeightPx)
        put("densityDpi", densityDpi)
        put("refreshRateHz", refreshRateHz.toDouble())
        put("supportedFeatures", JSONArray(supportedFeatures))
        put("availableSensors", JSONArray().apply {
            availableSensors.forEach { sensor ->
                put(JSONObject()
                    .put("type", sensor.type)
                    .put("name", sensor.name)
                    .put("vendor", sensor.vendor))
            }
        })
        put("cameraAvailable", cameraAvailable)
        put("profileConsentVersion", profileConsentVersion)
        put("collectedAt", collectedAt)
    }

    private fun DynamicTelemetry.toJson() = JSONObject().apply {
        put("totalRamBytes", totalRamBytes)
        put("availableRamBytes", availableRamBytes)
        put("isLowMemory", isLowMemory)
        put("internalStorageTotalBytes", internalStorageTotalBytes)
        put("internalStorageAvailableBytes", internalStorageAvailableBytes)
        put("storagePressure", storagePressure)
        put("appSpecificStorageBytes", appSpecificStorageBytes)
        put("batteryLevel", batteryLevel)
        put("chargingStatus", chargingStatus)
        put("powerSource", powerSource)
        batteryTemperature?.let { put("batteryTemperature", it.toDouble()) }
        put("batteryHealth", batteryHealth)
        batteryCapacityEstimateMah?.let { put("batteryCapacityEstimateMah", it) }
        put("batterySaverActive", batterySaverActive)
        put("thermalStatus", thermalStatus)
        put("networkConnectivity", networkConnectivity)
        put("networkTransport", networkTransport)
        put("internetValidated", internetValidated)
        dnsResolutionLatencyMs?.let { put("dnsResolutionLatencyMs", it) }
        httpsProbeLatencyMs?.let { put("httpsProbeLatencyMs", it) }
        connectionFailureDetails?.let { put("connectionFailureDetails", it.take(500)) }
        put("grantedPermissions", JSONArray(grantedPermissions))
        put("detectedRepairApps", JSONArray().apply {
            detectedRepairApps.forEach { app ->
                val item = JSONObject()
                    .put("packageName", app.packageName)
                    .put("installed", app.installed)
                app.versionName?.let { item.put("versionName", it) }
                put(item)
            }
        })
        put("lastTelemetryUpdatedAt", lastTelemetryUpdatedAt)
    }
}
