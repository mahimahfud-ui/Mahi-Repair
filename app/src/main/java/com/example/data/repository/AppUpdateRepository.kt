package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.remote.SupabaseClient
import com.example.security.VerifiedAppUpdateRelease
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Authenticated Android client for the Owner-published, RSA-signed app-update release.
 * No update is trusted until its exact manifest bytes and the downloaded APK digest,
 * package ID, version and signing certificate have all been checked.
 */
class AppUpdateRepository(
    context: Context,
    private val supabaseClient: SupabaseClient,
    private val toolRepository: ToolRepository
) {
    private val appContext = context.applicationContext
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun checkForUpdate(): Result<VerifiedAppUpdateRelease?> {
        return try {
            if (!supabaseClient.isConfigured()) {
                throw IllegalStateException("The repair service is not configured.")
            }

            val identity = toolRepository.getRealtimeIdentity().getOrElse { cause ->
                throw IllegalStateException("Sign in and register this device before checking for updates.", cause)
            }
            val response = supabaseClient.getAppUpdateManifest(
                deviceId = identity.deviceId,
                channel = "stable",
                authToken = identity.accessToken
            ).getOrThrow()

            if (response == null) return Result.success(null)

            val verified = com.example.security.AppUpdateManifestVerifier.verify(
                envelope = response,
                publicKeyPem = BuildConfig.RECIPE_SIGNING_PUBLIC_KEY_PEM,
                expectedPackageName = BuildConfig.APPLICATION_ID,
                installedVersionCode = BuildConfig.VERSION_CODE.toLong()
            )
            verified.nativeApk?.let { apk ->
                require(installedSigningCertificateSha256() == apk.signingCertificateSha256) {
                    "The update is not signed with the currently installed Mahi Repair certificate."
                }
            }
            Result.success(verified)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Downloads only the APK asset bound to a previously verified manifest. The app
     * verifies the private signed URL origin, exact byte count, SHA-256, package ID,
     * version and Owner-published signing certificate before launching Android's
     * package installer. Android retains the final user confirmation step.
     */
    suspend fun downloadAndPromptInstall(release: VerifiedAppUpdateRelease): Result<String> =
        withContext(Dispatchers.IO) {
            var downloadedFile: File? = null
            try {
                val apk = release.nativeApk
                    ?: throw IllegalStateException("This release does not contain an Android APK.")
                require(release.isNativeUpdateAvailable(BuildConfig.VERSION_CODE.toLong())) {
                    "No newer Android app version is available in this release."
                }
                require(apk.packageName == BuildConfig.APPLICATION_ID) {
                    "This update is not for the installed Mahi Repair application."
                }

                val identity = toolRepository.getRealtimeIdentity().getOrElse { cause ->
                    throw IllegalStateException("Your device could not be revalidated for this update.", cause)
                }
                val asset = supabaseClient.getAppUpdateAssetUrl(
                    deviceId = identity.deviceId,
                    releaseId = release.releaseId,
                    assetId = apk.assetId,
                    authToken = identity.accessToken
                ).getOrThrow()

                require(asset.assetId == apk.assetId) { "The server returned a different update asset." }
                require(asset.sha256 == apk.sha256 && asset.sizeBytes == apk.sizeBytes) {
                    "The update asset metadata does not match the signed manifest."
                }
                require(asset.contentType == apk.contentType && asset.contentType == APK_CONTENT_TYPE) {
                    "The update asset content type does not match the signed manifest."
                }
                require(asset.expiresInSeconds in 1..90) {
                    "The update download authorization has an invalid lifetime."
                }

                val baseUrl = BuildConfig.SUPABASE_URL.toHttpUrlOrNull()
                    ?: throw IllegalStateException("The Supabase project URL is not configured.")
                val signedUrl = asset.signedUrl.toHttpUrlOrNull()
                    ?: throw IllegalStateException("The update download URL is invalid.")
                require(signedUrl.isHttps && signedUrl.host == baseUrl.host && signedUrl.port == baseUrl.port) {
                    "The update download URL does not belong to the configured Supabase project."
                }

                val updateDirectory = File(appContext.cacheDir, "app-updates")
                require(updateDirectory.mkdirs() || updateDirectory.isDirectory) {
                    "The private update cache could not be created."
                }
                val outputFile = File(updateDirectory, "${release.releaseId}.apk")
                downloadedFile = outputFile
                downloadAndVerifySha256(
                    signedUrl = signedUrl.toString(),
                    outputFile = outputFile,
                    expectedSize = apk.sizeBytes,
                    expectedSha256 = apk.sha256
                )

                verifyApkMetadata(
                    apkFile = outputFile,
                    expectedPackageName = apk.packageName,
                    expectedVersionCode = apk.versionCode,
                    expectedVersionName = release.appVersionName,
                    expectedCertificateSha256 = apk.signingCertificateSha256
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !appContext.packageManager.canRequestPackageInstalls()
                ) {
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(permissionIntent)
                    return@withContext Result.success(
                        "Enable Install unknown apps for Mahi Repair, then return and tap Install update again."
                    )
                }

                val contentUri = FileProvider.getUriForFile(
                    appContext,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    outputFile
                )
                val installerIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, APK_CONTENT_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(installerIntent)
                Result.success("Android's installer opened. Review the update and confirm installation on your device.")
            } catch (error: Exception) {
                downloadedFile?.delete()
                Result.failure(error)
            }
        }

    private fun downloadAndVerifySha256(
        signedUrl: String,
        outputFile: File,
        expectedSize: Long,
        expectedSha256: String
    ) {
        val request = Request.Builder().url(signedUrl).get().build()
        val response = downloadClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("The update download failed (HTTP ${it.code}).")
            }
            val body = it.body ?: throw IllegalStateException("The update download was empty.")
            val declaredLength = body.contentLength()
            if (declaredLength >= 0L && declaredLength != expectedSize) {
                throw IllegalStateException("The update download size does not match its signed manifest.")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            body.byteStream().use { input ->
                outputFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        require(totalBytes <= expectedSize && totalBytes <= MAX_APK_SIZE_BYTES) {
                            "The update download exceeded its signed size limit."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }

            require(totalBytes == expectedSize) { "The downloaded APK is incomplete." }
            val actualDigest = digest.digest().joinToString("") { byte ->
                "%02x".format(Locale.US, byte.toInt() and 0xff)
            }
            require(MessageDigest.isEqual(
                actualDigest.toByteArray(Charsets.US_ASCII),
                expectedSha256.toByteArray(Charsets.US_ASCII)
            )) { "The downloaded APK failed SHA-256 verification." }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedSigningCertificateSha256(): String {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = appContext.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, flags)
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }
        require(signers.size == 1) {
            "The installed Mahi Repair signing identity is not a single-certificate release."
        }
        return certificateSha256(signers.single())
    }

    private fun certificateSha256(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte ->
                "%02x".format(Locale.US, byte.toInt() and 0xff)
            }

    @Suppress("DEPRECATION")
    private fun verifyApkMetadata(
        apkFile: File,
        expectedPackageName: String,
        expectedVersionCode: Long,
        expectedVersionName: String,
        expectedCertificateSha256: String
    ) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val packageInfo: PackageInfo = appContext.packageManager
            .getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: throw IllegalStateException("Android could not inspect the downloaded APK.")

        require(packageInfo.packageName == expectedPackageName) {
            "The downloaded APK application ID does not match Mahi Repair."
        }
        require(packageInfo.versionName == expectedVersionName) {
            "The downloaded APK version name does not match its signed manifest."
        }
        val actualVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        require(actualVersionCode == expectedVersionCode && actualVersionCode > BuildConfig.VERSION_CODE.toLong()) {
            "The downloaded APK version code is invalid or is not newer than this installation."
        }

        val signerCertificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }
        require(signerCertificates.isNotEmpty()) { "The downloaded APK has no verifiable signing certificate." }
        val actualCertificateDigests = signerCertificates.map(::certificateSha256)
        require(
            actualCertificateDigests.size == 1 &&
                actualCertificateDigests.single() == expectedCertificateSha256
        ) {
            "The downloaded APK signing certificate does not match the Owner-published single-signer release."
        }
    }

    private companion object {
        const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
        const val MAX_APK_SIZE_BYTES = 150L * 1024L * 1024L
    }
}
