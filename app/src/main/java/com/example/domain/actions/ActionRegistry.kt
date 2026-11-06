package com.example.domain.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.domain.models.ActionType
import com.example.domain.models.RecipeStep
import com.example.domain.models.StepStatus
import com.example.security.RuntimePermissionContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

data class ActionResult(
    val status: StepStatus,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val requiresUserAction: Boolean = false,
    val userActionType: UserActionType? = null,
    val userActionPayload: Map<String, String> = emptyMap()
)

enum class UserActionType {
    CONFIRMATION,
    GUIDE,
    PERMISSION
}

/**
 * Registry of allowlisted native repair actions conforming to repair-recipe-v1.
 * Any unallowlisted action or unsafe script is strictly rejected.
 */
class ActionRegistry(private val context: Context) {

    private fun getStringParam(params: Map<String, Any?>, vararg keys: String): String? {
        for (k in keys) {
            val v = params[k]
            if (v is String && v.isNotBlank()) return v.trim()
        }
        return null
    }

    private fun getIntParam(params: Map<String, Any?>, vararg keys: String): Int? {
        for (k in keys) {
            val v = params[k]
            if (v is Number) return v.toInt()
            if (v is String) v.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun getLongParam(params: Map<String, Any?>, vararg keys: String): Long? {
        for (k in keys) {
            val v = params[k]
            if (v is Number) return v.toLong()
            if (v is String) v.toLongOrNull()?.let { return it }
        }
        return null
    }

    /**
     * Validates that the step contains a known allowlisted action
     * and that all required parameters are present and conform to constraints.
     */
    fun validateStep(step: RecipeStep): Result<Unit> {
        val action = step.action
        val p = step.params
        return when (action) {
            ActionType.CHECK_APP_INSTALLED,
            ActionType.OPEN_APP,
            ActionType.OPEN_APP_DETAILS -> {
                val pkg = getStringParam(p, "packageName", "package_name")
                if (pkg.isNullOrBlank()) {
                    Result.failure(IllegalArgumentException("Action ${action.identifier} requires non-empty 'packageName'"))
                } else {
                    Result.success(Unit)
                }
            }
            ActionType.DNS_LOOKUP,
            ActionType.VERIFY_DNS -> {
                val host = getStringParam(p, "host", "hostname", "domain")
                if (host.isNullOrBlank()) {
                    Result.failure(IllegalArgumentException("Action ${action.identifier} requires non-empty 'host'"))
                } else {
                    Result.success(Unit)
                }
            }
            ActionType.HTTPS_PROBE -> {
                val host = getStringParam(p, "host", "url")
                if (host.isNullOrBlank()) {
                    Result.failure(IllegalArgumentException("Action HTTPS_PROBE requires non-empty 'host'"))
                } else {
                    Result.success(Unit)
                }
            }
            ActionType.SHOW_GUIDE -> {
                val title = getStringParam(p, "title", "guide_title") ?: step.name
                if (title.isBlank()) {
                    Result.failure(IllegalArgumentException("Action SHOW_GUIDE requires 'title' or step name"))
                } else {
                    Result.success(Unit)
                }
            }
            ActionType.OPEN_ANDROID_SETTINGS -> {
                val screen = getStringParam(p, "screen", "setting_type")
                if (screen.isNullOrBlank()) {
                    Result.failure(IllegalArgumentException("Action OPEN_ANDROID_SETTINGS requires 'screen'"))
                } else {
                    Result.success(Unit)
                }
            }
            ActionType.ASK_USER_CONFIRMATION -> {
                val title = getStringParam(p, "title") ?: step.name
                val message = getStringParam(p, "message")
                if (title.isBlank() || message.isNullOrBlank()) {
                    Result.failure(IllegalArgumentException("Action ASK_USER_CONFIRMATION requires 'title' and 'message'"))
                } else {
                    Result.success(Unit)
                }
            }
            ActionType.REQUEST_OWN_APP_PERMISSION -> {
                val permission = getStringParam(p, "permission")
                if (permission.isNullOrBlank() || !RuntimePermissionContract.isAllowlisted(permission)) {
                    Result.failure(IllegalArgumentException("Action REQUEST_OWN_APP_PERMISSION contains an unsupported permission"))
                } else {
                    Result.success(Unit)
                }
            }
            else -> Result.success(Unit)
        }
    }

    /**
     * Executes the allowlisted native action in Kotlin.
     */
    suspend fun executeAction(step: RecipeStep): ActionResult = withContext(Dispatchers.IO) {
        try {
            when (step.action) {
                ActionType.CHECK_INTERNET -> executeCheckInternet()
                ActionType.DNS_LOOKUP -> executeDnsLookup(step.params)
                ActionType.HTTPS_PROBE -> executeHttpsProbe(step.params)
                ActionType.CHECK_APP_INSTALLED -> executeCheckAppInstalled(step.params)
                ActionType.CHECK_STORAGE -> executeCheckStorage(step.params)
                ActionType.OPEN_APP -> executeOpenApp(step.params)
                ActionType.OPEN_APP_DETAILS -> executeOpenAppDetails(step.params)
                ActionType.OPEN_ANDROID_SETTINGS -> executeOpenAndroidSettings(step.params)
                ActionType.SHOW_GUIDE -> executeShowGuide(step.params, step.name)
                ActionType.ASK_USER_CONFIRMATION -> executeAskConfirmation(step.params, step.name)
                ActionType.REQUEST_OWN_APP_PERMISSION -> executeRequestPermission(step.params)
                ActionType.VERIFY_INTERNET -> executeVerifyInternet(step.params)
                ActionType.VERIFY_DNS -> executeVerifyDns(step.params)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ActionResult(
                status = StepStatus.FAILED,
                message = "Execution error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                details = mapOf("exception" to (e.message ?: "Unknown error"))
            )
        }
    }

    // --- Action 1: CHECK_INTERNET ---
    private fun executeCheckInternet(): ActionResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ActionResult(StepStatus.FAILED, "ConnectivityManager unavailable")

        val activeNetwork = cm.activeNetwork
            ?: return ActionResult(
                StepStatus.FAILED,
                "No active network connection detected.",
                mapOf("connected" to "false")
            )

        val caps = cm.getNetworkCapabilities(activeNetwork)
            ?: return ActionResult(
                StepStatus.FAILED,
                "Unable to retrieve network capabilities.",
                mapOf("connected" to "false")
            )

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        val transportName = when {
            isWifi -> "Wi-Fi"
            isCellular -> "Cellular Data"
            isVpn -> "VPN"
            else -> "Other"
        }

        return if (hasInternet && isValidated) {
            ActionResult(
                status = StepStatus.COMPLETED,
                message = "Internet connection active and validated ($transportName).",
                details = mapOf(
                    "transport" to transportName,
                    "validated" to isValidated.toString(),
                    "hasInternet" to hasInternet.toString()
                )
            )
        } else if (hasInternet) {
            ActionResult(
                status = StepStatus.FAILED,
                message = "Connected to $transportName but connection is not validated (captive portal or no route).",
                details = mapOf("transport" to transportName, "validated" to "false")
            )
        } else {
            ActionResult(
                status = StepStatus.FAILED,
                message = "Connected network has no internet capability.",
                details = mapOf("transport" to transportName, "hasInternet" to "false")
            )
        }
    }

    // --- Action 2: DNS_LOOKUP ---
    private fun executeDnsLookup(params: Map<String, Any?>): ActionResult {
        val host = getStringParam(params, "host", "hostname") ?: "google.com"
        val startTime = System.currentTimeMillis()
        return try {
            val addresses = InetAddress.getAllByName(host)
            val duration = System.currentTimeMillis() - startTime
            val ipList = addresses.joinToString(", ") { it.hostAddress ?: "" }

            ActionResult(
                status = StepStatus.COMPLETED,
                message = "Resolved $host in ${duration}ms ($ipList)",
                details = mapOf(
                    "host" to host,
                    "ips" to ipList,
                    "durationMs" to duration.toString()
                )
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ActionResult(
                status = StepStatus.FAILED,
                message = "DNS resolution failed for $host (${e.localizedMessage})",
                details = mapOf(
                    "host" to host,
                    "durationMs" to duration.toString(),
                    "error" to (e.message ?: "Unknown DNS error")
                )
            )
        }
    }

    // --- Action 3: HTTPS_PROBE ---
    private fun executeHttpsProbe(params: Map<String, Any?>): ActionResult {
        val targetUrl = if (params.containsKey("url")) {
            params["url"] as? String ?: "https://www.google.com/generate_204"
        } else {
            val host = getStringParam(params, "host") ?: "www.google.com"
            val path = getStringParam(params, "path") ?: "/generate_204"
            "https://$host$path"
        }
        val timeoutMs = getIntParam(params, "timeoutMs", "timeout_ms") ?: 6000
        val startTime = System.currentTimeMillis()

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(targetUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MahiRepair-Probe/1.0")

            val responseCode = connection.responseCode
            val duration = System.currentTimeMillis() - startTime

            if (responseCode in 200..399 || responseCode == 204) {
                ActionResult(
                    status = StepStatus.COMPLETED,
                    message = "HTTPS probe succeeded: HTTP $responseCode in ${duration}ms",
                    details = mapOf(
                        "url" to targetUrl,
                        "responseCode" to responseCode.toString(),
                        "latencyMs" to duration.toString()
                    )
                )
            } else {
                ActionResult(
                    status = StepStatus.FAILED,
                    message = "HTTPS probe returned unexpected status: HTTP $responseCode",
                    details = mapOf(
                        "url" to targetUrl,
                        "responseCode" to responseCode.toString(),
                        "latencyMs" to duration.toString()
                    )
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ActionResult(
                status = StepStatus.FAILED,
                message = "HTTPS probe failed: ${e.localizedMessage}",
                details = mapOf(
                    "url" to targetUrl,
                    "latencyMs" to duration.toString(),
                    "error" to (e.message ?: "Network error")
                )
            )
        } finally {
            connection?.disconnect()
        }
    }

    // --- Action 4: CHECK_APP_INSTALLED ---
    private fun executeCheckAppInstalled(params: Map<String, Any?>): ActionResult {
        val packageName = getStringParam(params, "packageName", "package_name")
            ?: return ActionResult(StepStatus.FAILED, "Missing packageName parameter")

        return try {
            val pm = context.packageManager
            val packageInfo: PackageInfo = pm.getPackageInfo(packageName, 0)
            val appName = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
            val versionName = packageInfo.versionName ?: "unknown"

            ActionResult(
                status = StepStatus.COMPLETED,
                message = "$appName ($packageName) is installed (v$versionName).",
                details = mapOf(
                    "packageName" to packageName,
                    "appName" to appName,
                    "versionName" to versionName,
                    "installed" to "true"
                )
            )
        } catch (e: PackageManager.NameNotFoundException) {
            ActionResult(
                status = StepStatus.FAILED,
                message = "Application $packageName is not installed on this device.",
                details = mapOf("packageName" to packageName, "installed" to "false")
            )
        }
    }

    // --- Action 5: CHECK_STORAGE ---
    private fun executeCheckStorage(params: Map<String, Any?>): ActionResult {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBytes
            val bytesTotal = stat.totalBytes
            val freeMb = bytesAvailable / (1024 * 1024)
            val totalMb = bytesTotal / (1024 * 1024)
            val percentFree = ((bytesAvailable.toDouble() / bytesTotal.toDouble()) * 100).toInt()

            val minFreeMb = getLongParam(params, "minimumAvailableMb", "min_free_mb") ?: 500L

            if (freeMb < minFreeMb) {
                ActionResult(
                    status = StepStatus.FAILED,
                    message = "Low internal storage: ${freeMb}MB free (${percentFree}%), minimum needed: ${minFreeMb}MB.",
                    details = mapOf(
                        "freeMb" to freeMb.toString(),
                        "totalMb" to totalMb.toString(),
                        "percentFree" to "$percentFree%"
                    )
                )
            } else {
                ActionResult(
                    status = StepStatus.COMPLETED,
                    message = "Storage OK: ${freeMb}MB (${percentFree}%) available of ${totalMb}MB.",
                    details = mapOf(
                        "freeMb" to freeMb.toString(),
                        "totalMb" to totalMb.toString(),
                        "percentFree" to "$percentFree%"
                    )
                )
            }
        } catch (e: Exception) {
            ActionResult(
                status = StepStatus.FAILED,
                message = "Failed to query storage stats: ${e.localizedMessage}",
                details = mapOf("error" to (e.message ?: "StatFs error"))
            )
        }
    }

    // --- Action 6: OPEN_APP ---
    private fun executeOpenApp(params: Map<String, Any?>): ActionResult {
        val packageName = getStringParam(params, "packageName", "package_name")
            ?: return ActionResult(StepStatus.FAILED, "Missing packageName parameter")

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return ActionResult(
                StepStatus.FAILED,
                "No launchable Activity found for $packageName.",
                mapOf("packageName" to packageName)
            )

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)

        return ActionResult(
            status = StepStatus.COMPLETED,
            message = "Launched app $packageName.",
            details = mapOf("packageName" to packageName)
        )
    }

    // --- Action 7: OPEN_APP_DETAILS ---
    private fun executeOpenAppDetails(params: Map<String, Any?>): ActionResult {
        val packageName = getStringParam(params, "packageName", "package_name")
            ?: return ActionResult(StepStatus.FAILED, "Missing packageName parameter")

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val guideMessage = getStringParam(params, "instruction")
            ?: "Opened App Info for $packageName. Tap 'Storage & cache' -> 'Clear cache'."

        return ActionResult(
            status = StepStatus.COMPLETED,
            message = guideMessage,
            details = mapOf("packageName" to packageName)
        )
    }

    // --- Action 8: OPEN_ANDROID_SETTINGS ---
    private fun executeOpenAndroidSettings(params: Map<String, Any?>): ActionResult {
        val screen = (getStringParam(params, "screen", "setting_type") ?: "general").lowercase()
        val intent = when (screen) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "internet", "wireless", "network" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "app_notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            "date_time", "date" -> Intent(Settings.ACTION_DATE_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
        return ActionResult(
            status = StepStatus.COMPLETED,
            message = "Navigated to Android $screen settings.",
            details = mapOf("screen" to screen)
        )
    }

    // --- Action 9: SHOW_GUIDE ---
    private fun executeShowGuide(params: Map<String, Any?>, fallbackTitle: String): ActionResult {
        val title = getStringParam(params, "title", "guide_title") ?: fallbackTitle
        val description = getStringParam(params, "description", "guide_description") ?: ""
        val steps = (params["instructions"] as? List<*>)
            ?: (params["guide_steps"] as? List<*>)
            ?: emptyList<Any?>()
        val stepsList = steps.mapNotNull { it?.toString() }

        return ActionResult(
            status = StepStatus.WAITING_USER,
            message = "Displaying interactive troubleshooting guide.",
            requiresUserAction = true,
            userActionType = UserActionType.GUIDE,
            userActionPayload = mapOf(
                "title" to title,
                "description" to description,
                "steps" to stepsList.joinToString("|||")
            )
        )
    }

    // --- Action 10: ASK_USER_CONFIRMATION ---
    private fun executeAskConfirmation(params: Map<String, Any?>, fallbackTitle: String): ActionResult {
        val title = getStringParam(params, "title") ?: fallbackTitle
        val message = getStringParam(params, "message")
            ?: "Do you want to proceed with this diagnostic step?"
        val confirmLabel = getStringParam(params, "confirmLabel") ?: "Continue"

        return ActionResult(
            status = StepStatus.WAITING_USER,
            message = "Waiting for user consent.",
            requiresUserAction = true,
            userActionType = UserActionType.CONFIRMATION,
            userActionPayload = mapOf(
                "title" to title,
                "message" to message,
                "confirmLabel" to confirmLabel
            )
        )
    }

    // --- Action 11: REQUEST_OWN_APP_PERMISSION ---
    private fun executeRequestPermission(params: Map<String, Any?>): ActionResult {
        val permissionName = getStringParam(params, "permission")
            ?: return ActionResult(StepStatus.FAILED, "The requested app permission is missing.")
        val spec = RuntimePermissionContract.resolve(permissionName)
            ?: return ActionResult(StepStatus.FAILED, "This repair requested an unsupported Android permission.")
        val androidPermission = spec.platformPermission
            ?: return ActionResult(
                status = StepStatus.COMPLETED,
                message = "Permission $permissionName is not required on this Android version.",
                details = mapOf("permission" to permissionName, "granted" to "true", "notRequired" to "true")
            )

        val isGranted = ContextCompat.checkSelfPermission(context, androidPermission) == PackageManager.PERMISSION_GRANTED
        return if (isGranted) {
            ActionResult(
                status = StepStatus.COMPLETED,
                message = "Permission $permissionName is already granted.",
                details = mapOf("permission" to permissionName, "granted" to "true")
            )
        } else {
            ActionResult(
                status = StepStatus.WAITING_USER,
                message = "Android must confirm permission $permissionName before this repair can continue.",
                requiresUserAction = true,
                userActionType = UserActionType.PERMISSION,
                userActionPayload = mapOf(
                    "permission" to permissionName,
                    "androidPermission" to androidPermission
                )
            )
        }
    }

    // --- Action 12: VERIFY_INTERNET ---
    private fun executeVerifyInternet(params: Map<String, Any?>): ActionResult {
        val testHost = getStringParam(params, "host") ?: "clients3.google.com"
        val startTime = System.currentTimeMillis()
        return try {
            val addresses = InetAddress.getAllByName(testHost)
            if (addresses.isEmpty()) {
                return ActionResult(StepStatus.FAILED, "Post-repair verification: DNS resolution returned no addresses.")
            }

            val url = URL("https://$testHost/generate_204")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }
            val code = connection.responseCode
            connection.disconnect()

            val duration = System.currentTimeMillis() - startTime
            if (code in 200..399 || code == 204) {
                ActionResult(
                    status = StepStatus.COMPLETED,
                    message = "Post-repair verification confirmed active internet route (${duration}ms).",
                    details = mapOf("verified" to "true", "latencyMs" to duration.toString())
                )
            } else {
                ActionResult(
                    status = StepStatus.FAILED,
                    message = "Post-repair verification failed: HTTP $code received.",
                    details = mapOf("verified" to "false", "httpCode" to code.toString())
                )
            }
        } catch (e: Exception) {
            ActionResult(
                status = StepStatus.FAILED,
                message = "Post-repair verification failed: ${e.localizedMessage}",
                details = mapOf("verified" to "false", "error" to (e.message ?: "Verification failed"))
            )
        }
    }

    // --- Action 13: VERIFY_DNS ---
    private fun executeVerifyDns(params: Map<String, Any?>): ActionResult {
        val targetDomain = getStringParam(params, "host", "domain") ?: "dns.google"
        return try {
            val addresses = InetAddress.getAllByName(targetDomain)
            if (addresses.isNotEmpty()) {
                val ipSummary = addresses.joinToString { it.hostAddress ?: "" }
                ActionResult(
                    status = StepStatus.COMPLETED,
                    message = "DNS verification confirmed for $targetDomain ($ipSummary).",
                    details = mapOf("verified" to "true", "host" to targetDomain, "ips" to ipSummary)
                )
            } else {
                ActionResult(
                    status = StepStatus.FAILED,
                    message = "DNS verification failed: no IP resolved for $targetDomain.",
                    details = mapOf("verified" to "false", "host" to targetDomain)
                )
            }
        } catch (e: Exception) {
            ActionResult(
                status = StepStatus.FAILED,
                message = "DNS verification failed for $targetDomain: ${e.localizedMessage}",
                details = mapOf("verified" to "false", "host" to targetDomain, "error" to (e.message ?: ""))
            )
        }
    }
}
