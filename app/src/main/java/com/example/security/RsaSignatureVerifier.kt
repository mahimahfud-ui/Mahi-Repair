package com.example.security

import android.util.Base64
import com.example.BuildConfig
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAKey
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies RSA-SHA256 digital signatures on remote repair recipes.
 * Only recipes signed by the Owner's private key will be executed.
 */
object RsaSignatureVerifier {

    private const val ALGORITHM = "SHA256withRSA"
    private const val KEY_ALGORITHM = "RSA"

    /**
     * Parses an RSA public key from a PEM or Base64-encoded X.509 string.
     */
    fun parsePublicKey(publicKeyStr: String): PublicKey {
        val cleanKey = publicKeyStr
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()

        require(cleanKey.isNotBlank()) { "Recipe verification key is not configured" }
        val decoded = Base64.decode(cleanKey, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val publicKey = keyFactory.generatePublic(keySpec)
        require((publicKey as? RSAKey)?.modulus?.bitLength() == 3072) {
            "The repair signing key must be RSA-3072"
        }
        return publicKey
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
        publicKeyPem: String = BuildConfig.RECIPE_SIGNING_PUBLIC_KEY_PEM
    ): Boolean {
        return try {
            val key = parsePublicKey(publicKeyPem)
            verifySignature(signedPayload.toByteArray(StandardCharsets.UTF_8), signatureBase64, key)
        } catch (e: Exception) {
            false
        }
    }

}
