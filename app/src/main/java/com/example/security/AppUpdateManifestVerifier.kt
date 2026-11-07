package com.example.security

import com.example.data.remote.dto.AppUpdateManifestData
import org.json.JSONObject
import java.util.UUID

data class VerifiedNativeApk(
    val assetId: String,
    val sha256: String,
    val sizeBytes: Long,
    val contentType: String,
    val packageName: String,
    val versionCode: Long,
    val signingCertificateSha256: String
)

data class VerifiedAppUpdateRelease(
    val releaseId: String,
    val channel: String,
    val releaseNumber: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val minSupportedAppVersionCode: Long,
    val mandatory: Boolean,
    val releaseNotes: String,
    val contentPackageCount: Int,
    val nativeApk: VerifiedNativeApk?
) {
    fun isNativeUpdateAvailable(installedVersionCode: Long): Boolean =
        nativeApk != null && nativeApk.versionCode > installedVersionCode

    fun requiresUpdate(installedVersionCode: Long): Boolean =
        mandatory && installedVersionCode < minSupportedAppVersionCode
}

/**
 * Verifies the exact canonical manifest bytes before parsing. It accepts only the
 * checked-in v1 release contract and a native APK for this exact Android package.
 * Dynamic scripts, arbitrary URLs and unrecognized manifest properties are rejected.
 */
object AppUpdateManifestVerifier {
    private val sha256Pattern = Regex("^[a-f0-9]{64}$")
    private val assetIdPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private val apkPathPattern = Regex("^channels/(stable|beta)/native/[0-9a-f-]{36}\\.apk$")

    private val manifestKeys = setOf(
        "schemaVersion",
        "releaseId",
        "channel",
        "releaseNumber",
        "appVersionCode",
        "appVersionName",
        "minSupportedAppVersionCode",
        "mandatory",
        "releaseNotes",
        "remoteConfig",
        "packages",
        "nativeApk",
        "publishedAt"
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
        require(envelope.channel == "stable" || envelope.channel == "beta") {
            "Unsupported app-update channel."
        }
        val envelopeReleaseId = UUID.fromString(envelope.releaseId).toString()
        require(RsaSignatureVerifier.verify(envelope.manifestPayload, envelope.signature, publicKeyPem)) {
            "App-update manifest signature verification failed."
        }

        // The signed bytes are verified before any manifest JSON is parsed.
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
        require(appVersionCode > 0L && minSupportedVersionCode > 0L) {
            "Invalid Android app version code in update manifest."
        }
        require(minSupportedVersionCode <= appVersionCode) {
            "The minimum supported app version exceeds this release version."
        }

        val appVersionName = string(manifest, "appVersionName")
        require(appVersionName.length <= 40 && appVersionName.matches(Regex("^[0-9A-Za-z.+-]+$"))) {
            "Invalid Android app version name in update manifest."
        }
        val mandatory = boolean(manifest, "mandatory")
        val releaseNotes = string(manifest, "releaseNotes")
        require(releaseNotes.length <= 4000) { "App-update release notes are too long." }
        require(manifest.optJSONObject("remoteConfig") != null) {
            "The app-update remote configuration is missing or invalid."
        }

        val packages = manifest.optJSONArray("packages")
            ?: throw IllegalArgumentException("The app-update package list is invalid.")
        require(packages.length() <= 20) { "The app-update package list exceeds the permitted limit." }

        val nativeApk = when (val value = manifest.opt("nativeApk")) {
            null, JSONObject.NULL -> null
            is JSONObject -> verifyNativeApk(value, channel, expectedPackageName, appVersionCode)
            else -> throw IllegalArgumentException("The native APK descriptor is invalid.")
        }

        val publishedAt = string(manifest, "publishedAt")
        require(publishedAt.isNotBlank() && publishedAt.length <= 40) {
            "The app-update publication timestamp is invalid."
        }

        return VerifiedAppUpdateRelease(
            releaseId = releaseId,
            channel = channel,
            releaseNumber = releaseNumber,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            minSupportedAppVersionCode = minSupportedVersionCode,
            mandatory = mandatory,
            releaseNotes = releaseNotes,
            contentPackageCount = packages.length(),
            nativeApk = nativeApk
        )
    }

    private fun verifyNativeApk(
        apk: JSONObject,
        channel: String,
        expectedPackageName: String,
        releaseVersionCode: Long
    ): VerifiedNativeApk {
        requireOnlyKeys(
            apk,
            setOf(
                "id",
                "storagePath",
                "sha256",
                "sizeBytes",
                "contentType",
                "packageName",
                "versionCode",
                "signingCertificateSha256"
            )
        )

        val assetId = string(apk, "id")
        require(assetId.length <= 80 && assetIdPattern.matches(assetId)) {
            "Invalid native APK asset ID."
        }
        val storagePath = string(apk, "storagePath")
        require(apkPathPattern.matches(storagePath) && storagePath.startsWith("channels/$channel/native/")) {
            "The native APK storage path does not match the signed release channel."
        }

        val sha256 = string(apk, "sha256")
        require(sha256Pattern.matches(sha256)) { "Invalid native APK SHA-256 digest." }
        val signingCertificate = string(apk, "signingCertificateSha256")
        require(sha256Pattern.matches(signingCertificate)) {
            "Invalid native APK signing-certificate digest."
        }

        val sizeBytes = integer(apk, "sizeBytes")
        require(sizeBytes in 1L..MAX_APK_SIZE_BYTES) { "The native APK size exceeds the permitted limit." }
        val contentType = string(apk, "contentType")
        require(contentType == APK_CONTENT_TYPE) { "Unsupported native APK content type." }

        val packageName = string(apk, "packageName")
        require(packageName == expectedPackageName) {
            "This update is not published for the installed Mahi Repair application ID."
        }
        val versionCode = integer(apk, "versionCode")
        require(versionCode == releaseVersionCode && versionCode > 0L) {
            "The native APK version does not match the signed release version."
        }

        return VerifiedNativeApk(
            assetId = assetId,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            contentType = contentType,
            packageName = packageName,
            versionCode = versionCode,
            signingCertificateSha256 = signingCertificate
        )
    }

    private fun requireOnlyKeys(json: JSONObject, allowed: Set<String>) {
        val keys = json.keys()
        while (keys.hasNext()) {
            require(keys.next() in allowed) { "The signed app-update manifest contains an unsupported field." }
        }
    }

    private fun string(json: JSONObject, key: String): String {
        val value = json.opt(key)
        require(value is String) { "Invalid $key in app-update manifest." }
        return value
    }

    private fun integer(json: JSONObject, key: String): Long {
        val value = json.opt(key) as? Number
            ?: throw IllegalArgumentException("Invalid $key in app-update manifest.")
        val numeric = value.toDouble()
        require(numeric.isFinite() && numeric % 1.0 == 0.0 && numeric >= 0.0 && numeric <= Long.MAX_VALUE.toDouble()) {
            "Invalid $key in app-update manifest."
        }
        return value.toLong()
    }

    private fun boolean(json: JSONObject, key: String): Boolean {
        val value = json.opt(key)
        require(value is Boolean) { "Invalid $key in app-update manifest." }
        return value
    }

    private fun Long.toIntExact(label: String): Int {
        require(this in 1L..Int.MAX_VALUE.toLong()) { "Invalid $label in app-update manifest." }
        return toInt()
    }

    private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
    private const val MAX_APK_SIZE_BYTES = 150L * 1024L * 1024L
}
