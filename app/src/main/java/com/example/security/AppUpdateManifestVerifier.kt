package com.example.security

import com.example.data.remote.dto.AppUpdateManifestData
import org.json.JSONObject
import java.util.UUID

data class VerifiedNativeApk(
    val assetId: String, val sha256: String, val sizeBytes: Long, val contentType: String,
    val packageName: String, val versionCode: Long, val signingCertificateSha256: String
)
data class VerifiedContentEntry(val path: String, val sha256: String, val sizeBytes: Long)
data class VerifiedContentPackage(
    val assetId: String, val version: Int, val storagePath: String, val sha256: String,
    val sizeBytes: Long, val contentType: String, val entries: List<VerifiedContentEntry>
)
data class VerifiedToolMetadata(val title: String?, val description: String?, val category: String?)
data class VerifiedRemoteAnnouncement(val title: String, val body: String, val tone: String)
data class VerifiedRemoteConfig(
    val rawJson: String,
    val featureFlags: Map<String, Boolean>,
    val repairTools: Map<String, Boolean>,
    val toolMetadata: Map<String, VerifiedToolMetadata>,
    val announcement: VerifiedRemoteAnnouncement?,
    val minAndroidApi: Int,
    val maxAndroidApi: Int
) {
    val hasClientChanges: Boolean
        get() = featureFlags.isNotEmpty() || repairTools.isNotEmpty() || toolMetadata.isNotEmpty() || announcement != null
}
data class VerifiedAppUpdateRelease(
    val releaseId: String,
    val channel: String,
    val releaseNumber: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val minSupportedAppVersionCode: Long,
    val mandatory: Boolean,
    val releaseNotes: String,
    val remoteConfig: VerifiedRemoteConfig,
    val packages: List<VerifiedContentPackage>,
    val contentPackageCount: Int,
    val nativeApk: VerifiedNativeApk?
) {
    fun isNativeUpdateAvailable(installedVersionCode: Long): Boolean =
        nativeApk != null && nativeApk.versionCode > installedVersionCode
    fun requiresUpdate(installedVersionCode: Long): Boolean =
        mandatory && installedVersionCode < minSupportedAppVersionCode
    val hasDataUpdate: Boolean get() = packages.isNotEmpty() || remoteConfig.hasClientChanges
}

/** Verifies signed manifest bytes before parsing; only allowlisted, data-only resources are accepted. */
object AppUpdateManifestVerifier {
    private val sha256Pattern = Regex("^[a-f0-9]{64}$")
    private val assetIdPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private val slugPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private val flagPattern = Regex("^[a-z0-9_.-]{1,80}$")
    private val packagePathPattern = Regex("^channels/(stable|beta)/packages/[0-9a-f-]{36}\\.zip$")
    private val apkPathPattern = Regex("^channels/(stable|beta)/native/[0-9a-f-]{36}\\.apk$")
    private val contentPathPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]{0,239}$")
    private val manifestKeys = setOf(
        "schemaVersion", "releaseId", "channel", "releaseNumber", "appVersionCode",
        "appVersionName", "minSupportedAppVersionCode", "mandatory", "releaseNotes",
        "remoteConfig", "packages", "nativeApk", "publishedAt"
    )

    fun verify(
        envelope: AppUpdateManifestData,
        publicKeyPem: String,
        expectedPackageName: String,
        installedVersionCode: Long
    ): VerifiedAppUpdateRelease {
        require(installedVersionCode > 0L) { "The installed app version code is invalid." }
        require(envelope.signatureAlgorithm == RecipeContractVerifier.SIGNATURE_ALGORITHM) {
            "Unsupported app-update signature algorithm."
        }
        require(envelope.channel == "stable" || envelope.channel == "beta") { "Unsupported app-update channel." }
        val envelopeReleaseId = UUID.fromString(envelope.releaseId).toString()
        require(RsaSignatureVerifier.verify(envelope.manifestPayload, envelope.signature, publicKeyPem)) {
            "App-update manifest signature verification failed."
        }
        val manifest = JSONObject(envelope.manifestPayload)
        requireOnlyKeys(manifest, manifestKeys)
        require(integer(manifest, "schemaVersion") == 1L) { "Unsupported app-update manifest schema." }
        val releaseId = UUID.fromString(string(manifest, "releaseId")).toString()
        require(releaseId == envelopeReleaseId) { "Manifest release ID does not match the server envelope." }
        val channel = string(manifest, "channel")
        require(channel == envelope.channel) { "Manifest channel does not match the server envelope." }
        val releaseNumber = integer(manifest, "releaseNumber").toIntExact("release number")
        require(releaseNumber == envelope.releaseNumber && releaseNumber > 0) {
            "Manifest release number does not match the server envelope."
        }
        val appVersionCode = integer(manifest, "appVersionCode")
        val minSupportedVersionCode = integer(manifest, "minSupportedAppVersionCode")
        require(appVersionCode > 0L && minSupportedVersionCode > 0L && minSupportedVersionCode <= appVersionCode) {
            "Invalid app version range in update manifest."
        }
        val appVersionName = string(manifest, "appVersionName")
        require(appVersionName.length <= 40 && appVersionName.matches(Regex("^[0-9A-Za-z.+-]+$"))) {
            "Invalid Android app version name in update manifest."
        }
        val mandatory = boolean(manifest, "mandatory")
        val releaseNotes = string(manifest, "releaseNotes")
        require(releaseNotes.length <= 4000) { "App-update release notes are too long." }
        val remoteConfig = verifyRemoteConfig(
            manifest.optJSONObject("remoteConfig")
                ?: throw IllegalArgumentException("The app-update remote configuration is missing.")
        )
        val packageArray = manifest.optJSONArray("packages")
            ?: throw IllegalArgumentException("The app-update package list is invalid.")
        require(packageArray.length() <= 20) { "Too many app-update content packages." }
        val packages = (0 until packageArray.length()).map { index ->
            verifyContentPackage(packageArray.optJSONObject(index)
                ?: throw IllegalArgumentException("Invalid content package descriptor."), channel)
        }
        require(packages.map { it.assetId }.distinct().size == packages.size) {
            "Content package asset IDs must be unique."
        }
        val nativeApk = when (val value = manifest.opt("nativeApk")) {
            null, JSONObject.NULL -> null
            is JSONObject -> verifyNativeApk(value, channel, expectedPackageName, appVersionCode)
            else -> throw IllegalArgumentException("The native APK descriptor is invalid.")
        }
        require(nativeApk == null || packages.none { it.assetId == nativeApk.assetId }) {
            "Content package and APK asset IDs must be unique."
        }
        val publishedAt = string(manifest, "publishedAt")
        require(publishedAt.isNotBlank() && publishedAt.length <= 40) { "Invalid update publication timestamp." }
        return VerifiedAppUpdateRelease(
            releaseId, channel, releaseNumber, appVersionName, appVersionCode,
            minSupportedVersionCode, mandatory, releaseNotes, remoteConfig, packages, packages.size, nativeApk
        )
    }

    fun verifyRemoteConfig(remoteConfig: JSONObject): VerifiedRemoteConfig {
        requireOnlyKeys(remoteConfig, setOf("featureFlags", "repairTools", "ui", "toolMetadata", "compatibility"))
        val featureFlags = parseBooleanMap(remoteConfig.optJSONObject("featureFlags"), flagPattern, 100)
        val repairTools = parseBooleanMap(remoteConfig.optJSONObject("repairTools"), slugPattern, 200)
        val ui = remoteConfig.optJSONObject("ui") ?: throw IllegalArgumentException("Remote UI config is missing.")
        requireOnlyKeys(ui, setOf("announcement"))
        val announcement = when (val item = ui.opt("announcement")) {
            null, JSONObject.NULL -> null
            is JSONObject -> {
                requireOnlyKeys(item, setOf("title", "body", "tone"))
                val tone = item.optString("tone", "info")
                require(tone in setOf("info", "success", "warning")) { "Unsupported announcement tone." }
                VerifiedRemoteAnnouncement(
                    boundedString(item, "title", 1, 100),
                    boundedString(item, "body", 1, 500),
                    tone
                )
            }
            else -> throw IllegalArgumentException("Remote announcement must be an object or null.")
        }
        val metadataObject = remoteConfig.optJSONObject("toolMetadata")
            ?: throw IllegalArgumentException("Remote tool metadata is missing.")
        require(metadataObject.length() <= 200) { "Too many remote tool metadata entries." }
        val metadata = linkedMapOf<String, VerifiedToolMetadata>()
        val metadataKeys = metadataObject.keys()
        while (metadataKeys.hasNext()) {
            val slug = metadataKeys.next()
            require(slugPattern.matches(slug)) { "Invalid remote tool metadata identifier." }
            val item = metadataObject.optJSONObject(slug)
                ?: throw IllegalArgumentException("Remote tool metadata entry must be an object.")
            requireOnlyKeys(item, setOf("title", "description", "category"))
            metadata[slug] = VerifiedToolMetadata(
                optionalBoundedString(item, "title", 1, 100),
                optionalBoundedString(item, "description", 0, 500),
                optionalBoundedString(item, "category", 1, 60)
            )
        }
        val compatibility = remoteConfig.optJSONObject("compatibility")
            ?: throw IllegalArgumentException("Remote compatibility config is missing.")
        requireOnlyKeys(compatibility, setOf("minAndroidApi", "maxAndroidApi"))
        val minApi = integerWithDefault(compatibility, "minAndroidApi", 26).toIntExact("minimum Android API")
        val maxApi = integerWithDefault(compatibility, "maxAndroidApi", 100).toIntExact("maximum Android API")
        require(minApi in 26..100 && maxApi in 26..100 && minApi <= maxApi) {
            "Remote Android API compatibility range is invalid."
        }
        return VerifiedRemoteConfig(
            remoteConfig.toString(), featureFlags, repairTools, metadata, announcement, minApi, maxApi
        )
    }

    private fun parseBooleanMap(json: JSONObject?, pattern: Regex, maximum: Int): Map<String, Boolean> {
        val objectValue = json ?: throw IllegalArgumentException("Remote boolean configuration is missing.")
        require(objectValue.length() <= maximum) { "Remote boolean configuration contains too many entries." }
        val result = linkedMapOf<String, Boolean>()
        val keys = objectValue.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(pattern.matches(key)) { "Invalid remote configuration key." }
            val value = objectValue.opt(key)
            require(value is Boolean) { "Remote configuration values must be boolean." }
            result[key] = value
        }
        return result
    }

    private fun verifyContentPackage(pkg: JSONObject, channel: String): VerifiedContentPackage {
        requireOnlyKeys(pkg, setOf("id", "version", "storagePath", "sha256", "sizeBytes", "contentType", "entries"))
        val id = string(pkg, "id")
        require(id.length <= 80 && slugPattern.matches(id)) { "Invalid content package ID." }
        val version = integer(pkg, "version").toIntExact("content package version")
        val path = string(pkg, "storagePath")
        require(packagePathPattern.matches(path) && path.startsWith("channels/$channel/packages/")) {
            "Content package path does not match the release channel."
        }
        val sha = string(pkg, "sha256")
        require(sha256Pattern.matches(sha)) { "Invalid content package SHA-256." }
        val size = integer(pkg, "sizeBytes")
        require(size in 1L..MAX_CONTENT_PACKAGE_BYTES) { "Content package exceeds the size limit." }
        val mime = string(pkg, "contentType")
        require(mime == ZIP_CONTENT_TYPE) { "Unsupported content package MIME type." }
        val array = pkg.optJSONArray("entries") ?: throw IllegalArgumentException("Content package entries are missing.")
        require(array.length() in 1..200) { "Content package must have between 1 and 200 entries." }
        val entries = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: throw IllegalArgumentException("Invalid content entry.")
            requireOnlyKeys(item, setOf("path", "sha256", "sizeBytes"))
            val entryPath = string(item, "path")
            require(isSafeContentPath(entryPath)) { "Unsafe or unsupported content package path." }
            val digest = string(item, "sha256")
            require(sha256Pattern.matches(digest)) { "Invalid content entry SHA-256." }
            val entrySize = integer(item, "sizeBytes")
            require(entrySize in 1L..MAX_CONTENT_ENTRY_BYTES) { "Content entry exceeds the size limit." }
            VerifiedContentEntry(entryPath, digest, entrySize)
        }
        require(entries.map { it.path }.distinct().size == entries.size) { "Duplicate declared content paths." }
        require(entries.sumOf { it.sizeBytes } <= size * MAX_EXPANSION_RATIO) {
            "Expanded content package exceeds the permitted size ratio."
        }
        return VerifiedContentPackage(id, version, path, sha, size, mime, entries)
    }

    private fun verifyNativeApk(apk: JSONObject, channel: String, expectedPackageName: String, releaseVersionCode: Long): VerifiedNativeApk {
        requireOnlyKeys(apk, setOf(
            "id", "storagePath", "sha256", "sizeBytes", "contentType",
            "packageName", "versionCode", "signingCertificateSha256"
        ))
        val id = string(apk, "id")
        require(id.length <= 80 && assetIdPattern.matches(id)) { "Invalid native APK asset ID." }
        val path = string(apk, "storagePath")
        require(apkPathPattern.matches(path) && path.startsWith("channels/$channel/native/")) {
            "Native APK path does not match the release channel."
        }
        val sha = string(apk, "sha256")
        val certificate = string(apk, "signingCertificateSha256")
        require(sha256Pattern.matches(sha) && sha256Pattern.matches(certificate)) { "Invalid APK digest or certificate." }
        val size = integer(apk, "sizeBytes")
        require(size in 1L..MAX_APK_SIZE_BYTES) { "Native APK exceeds the size limit." }
        val mime = string(apk, "contentType")
        require(mime == APK_CONTENT_TYPE) { "Unsupported APK MIME type." }
        val packageName = string(apk, "packageName")
        require(packageName == expectedPackageName) { "APK is not for the installed Mahi Repair application ID." }
        val version = integer(apk, "versionCode")
        require(version == releaseVersionCode && version > 0L) { "APK versionCode does not match the signed release." }
        return VerifiedNativeApk(id, sha, size, mime, packageName, version, certificate)
    }

    private fun isSafeContentPath(path: String): Boolean {
        if (path.isBlank() || path.length > 240 || !contentPathPattern.matches(path) ||
            path.startsWith('/') || path.contains('\\')) return false
        if (path.split('/').any { it.isEmpty() || it == "." || it == ".." }) return false
        val lower = path.lowercase()
        return lower.endsWith(".json") || lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".webp")
    }
    private fun optionalBoundedString(json: JSONObject, key: String, min: Int, max: Int): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.opt(key) as? String ?: throw IllegalArgumentException("Invalid remote $key.")
        require(value.length in min..max) { "Remote $key is outside the permitted length." }
        return value
    }
    private fun boundedString(json: JSONObject, key: String, min: Int, max: Int): String =
        optionalBoundedString(json, key, min, max) ?: throw IllegalArgumentException("Missing remote $key.")
    private fun requireOnlyKeys(json: JSONObject, allowed: Set<String>) {
        val keys = json.keys()
        while (keys.hasNext()) require(keys.next() in allowed) {
            "The signed app-update manifest contains an unsupported field."
        }
    }
    private fun string(json: JSONObject, key: String): String {
        val value = json.opt(key)
        require(value is String) { "Invalid $key in app-update manifest." }
        return value
    }
    private fun integer(json: JSONObject, key: String): Long {
        val value = json.opt(key) as? Number ?: throw IllegalArgumentException("Invalid $key in app-update manifest.")
        val number = value.toDouble()
        require(number.isFinite() && number % 1.0 == 0.0 && number >= 0.0 && number <= Long.MAX_VALUE.toDouble()) {
            "Invalid $key in app-update manifest."
        }
        return value.toLong()
    }
    private fun integerWithDefault(json: JSONObject, key: String, default: Long): Long =
        if (json.has(key)) integer(json, key) else default
    private fun boolean(json: JSONObject, key: String): Boolean {
        val value = json.opt(key)
        require(value is Boolean) { "Invalid $key in app-update manifest." }
        return value
    }
    private fun Long.toIntExact(label: String): Int {
        require(this in 1L..Int.MAX_VALUE.toLong()) { "Invalid $label." }
        return toInt()
    }

    const val ZIP_CONTENT_TYPE = "application/zip"
    const val MAX_APK_SIZE_BYTES = 150L * 1024L * 1024L
    const val MAX_CONTENT_PACKAGE_BYTES = 50L * 1024L * 1024L
    const val MAX_CONTENT_ENTRY_BYTES = 20L * 1024L * 1024L
    const val MAX_EXPANSION_RATIO = 20L
    private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
}
