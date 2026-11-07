package com.example.security

import com.example.data.remote.dto.AppUpdateManifestData
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateManifestVerifierTest {
    companion object {
        private lateinit var keyPair: KeyPair
        private lateinit var publicKeyPem: String
        const val PACKAGE_NAME = "com.aistudio.mahirepair.qrzntk"

        @JvmStatic
        @BeforeClass
        fun createSigningKey() {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(3072)
            keyPair = generator.generateKeyPair()
            publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.public.encoded) +
                "\n-----END PUBLIC KEY-----"
        }
    }

    @Test
    fun verifiesSignedManifestAndBindsNativeApkToInstalledPackage() {
        val envelope = signedEnvelope()
        val release = AppUpdateManifestVerifier.verify(
            envelope = envelope,
            publicKeyPem = publicKeyPem,
            expectedPackageName = PACKAGE_NAME,
            installedVersionCode = 1L
        )

        assertEquals("1.2.0", release.appVersionName)
        assertEquals(12L, release.appVersionCode)
        assertTrue(release.isNativeUpdateAvailable(1L))
        assertFalse(release.isNativeUpdateAvailable(12L))
        assertEquals("android-apk", release.nativeApk?.assetId)
    }

    @Test
    fun rejectsManifestSignedByAnUnknownKey() {
        val envelope = signedEnvelope(signatureKey = generateKeyPair())
        val error = runCatching {
            AppUpdateManifestVerifier.verify(
                envelope = envelope,
                publicKeyPem = publicKeyPem,
                expectedPackageName = PACKAGE_NAME,
                installedVersionCode = 1L
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsApkForAnotherPackageEvenWhenManifestSignatureIsValid() {
        val envelope = signedEnvelope(packageName = "com.example.other")
        val error = runCatching {
            AppUpdateManifestVerifier.verify(
                envelope = envelope,
                publicKeyPem = publicKeyPem,
                expectedPackageName = PACKAGE_NAME,
                installedVersionCode = 1L
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsManifestEnvelopeWithMismatchedReleaseNumber() {
        val signed = signedEnvelope()
        val envelope = signed.copy(releaseNumber = signed.releaseNumber + 1)
        val error = runCatching {
            AppUpdateManifestVerifier.verify(
                envelope = envelope,
                publicKeyPem = publicKeyPem,
                expectedPackageName = PACKAGE_NAME,
                installedVersionCode = 1L
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun signedEnvelope(
        signatureKey: KeyPair = keyPair,
        packageName: String = PACKAGE_NAME
    ): AppUpdateManifestData {
        val releaseId = UUID.randomUUID().toString()
        val manifestPayload = """
            {
              "schemaVersion": 1,
              "releaseId": "$releaseId",
              "channel": "stable",
              "releaseNumber": 7,
              "appVersionCode": 12,
              "appVersionName": "1.2.0",
              "minSupportedAppVersionCode": 2,
              "mandatory": false,
              "releaseNotes": "Security and stability improvements.",
              "remoteConfig": {
                "featureFlags": {},
                "repairTools": {},
                "ui": {},
                "toolMetadata": {},
                "compatibility": { "minAndroidApi": 26, "maxAndroidApi": 100 }
              },
              "packages": [],
              "nativeApk": {
                "id": "android-apk",
                "storagePath": "channels/stable/native/123e4567-e89b-12d3-a456-426614174000.apk",
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "sizeBytes": 1048576,
                "contentType": "application/vnd.android.package-archive",
                "packageName": "$packageName",
                "versionCode": 12,
                "signingCertificateSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
              },
              "publishedAt": "2026-11-07T12:00:00.000Z"
            }
        """.trimIndent()

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(signatureKey.private)
        signer.update(manifestPayload.toByteArray(Charsets.UTF_8))
        val signatureBase64 = android.util.Base64.encodeToString(signer.sign(), android.util.Base64.NO_WRAP)

        return AppUpdateManifestData(
            releaseId = releaseId,
            channel = "stable",
            releaseNumber = 7,
            manifestPayload = manifestPayload,
            signature = signatureBase64,
            signatureAlgorithm = "RSA-SHA256"
        )
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(3072)
        return generator.generateKeyPair()
    }

}
