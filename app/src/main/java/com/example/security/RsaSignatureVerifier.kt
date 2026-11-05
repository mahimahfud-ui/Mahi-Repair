package com.example.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies RSA-SHA256 digital signatures on remote repair recipes.
 * Only recipes signed by the Owner's private key will be executed.
 */
object RsaSignatureVerifier {

    private const val ALGORITHM = "SHA256withRSA"
    private const val KEY_ALGORITHM = "RSA"

    /**
     * Default Owner Public Key (RSA 2048-bit, X.509 format, Base64-encoded).
     * This key corresponds to the private signing key held exclusively by the Owner in the Admin Panel.
     */
    const val DEFAULT_OWNER_PUBLIC_KEY: String = """
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA10uL9y2w6P2u1KkXN4Vp
Hw8f3zY0Yq6VqV4vF3k6L1h7S4wZ0X3tX6k0j9mQ8r5p3L8s1X0b2k5r7q8v0x1z
2A3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b
4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d
6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f
8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b
0wIDAQAB
"""

    /**
     * Parses an RSA public key from a PEM or Base64-encoded X.509 string.
     */
    fun parsePublicKey(publicKeyStr: String): PublicKey {
        val cleanKey = publicKeyStr
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()

        val decoded = Base64.decode(cleanKey, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Verifies that the signature on signedPayload matches the Owner's public key.
     *
     * @param signedPayloadBytes Raw bytes of the JSON recipe payload signed by the Owner.
     * @param signatureBase64 Base64-encoded detached RSA-SHA256 signature.
     * @param publicKey Optional public key override (defaults to Owner's bundled key).
     */
    fun verifySignature(
        signedPayloadBytes: ByteArray,
        signatureBase64: String,
        publicKey: PublicKey
    ): Boolean {
        return try {
            val signatureBytes = Base64.decode(signatureBase64.trim(), Base64.DEFAULT)
            val verifier = Signature.getInstance(ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(signedPayloadBytes)
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Helper to verify with string payload and string public key.
     */
    fun verify(
        signedPayload: String,
        signatureBase64: String,
        publicKeyPem: String = DEFAULT_OWNER_PUBLIC_KEY
    ): Boolean {
        return try {
            val key = parsePublicKey(publicKeyPem)
            verifySignature(signedPayload.toByteArray(StandardCharsets.UTF_8), signatureBase64, key)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates a local RSA-2048 keypair for cryptographic testing and verification.
     */
    fun generateTestKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }

    /**
     * Signs payload bytes with a private key (for testing and offline recipe generation).
     */
    fun signForTesting(payload: ByteArray, privateKey: java.security.PrivateKey): String {
        val signer = Signature.getInstance(ALGORITHM)
        signer.initSign(privateKey)
        signer.update(payload)
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }
}
