package com.example.quantumsafechat

import android.util.Base64
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec


object QuantumSafeCrypto {

    private val random = SecureRandom()

    private val protocolVersion =
        "QuantumSafeChat-v1".toByteArray(StandardCharsets.UTF_8)


    // ============================================================
    // Base64
    // Android-compatible implementation
    // ============================================================

    private fun encodeBase64(data: ByteArray): String {
        return Base64.encodeToString(
            data,
            Base64.NO_WRAP
        )
    }

    private fun decodeBase64(data: String): ByteArray {
        return Base64.decode(
            data,
            Base64.DEFAULT
        )
    }


    // ============================================================
    // ML-KEM-768
    // ============================================================

    data class KemKeyPair(
        val publicKey: String,
        val privateKey: MLKEMPrivateKeyParameters
    )

    data class EncapsulationResult(
        val ciphertext: String,
        val sharedSecret: ByteArray
    )


    fun generateKeyPair(): KemKeyPair {

        val generator =
            MLKEMKeyPairGenerator()

        generator.init(
            MLKEMKeyGenerationParameters(
                random,
                MLKEMParameters.ml_kem_768
            )
        )

        val keyPair =
            generator.generateKeyPair()

        val publicKey =
            keyPair.public as MLKEMPublicKeyParameters

        val privateKey =
            keyPair.private as MLKEMPrivateKeyParameters

        val rawPublicKey =
            publicKey.getEncoded()

        return KemKeyPair(
            publicKey =
                encodeBase64(rawPublicKey),

            privateKey =
                privateKey
        )
    }


    fun encapsulate(
        publicKeyBase64: String
    ): EncapsulationResult {

        val rawPublicKey =
            decodeBase64(publicKeyBase64)

        val publicKey =
            MLKEMPublicKeyParameters(
                MLKEMParameters.ml_kem_768,
                rawPublicKey
            )

        val generator =
            MLKEMGenerator(random)

        val result =
            generator.generateEncapsulated(
                publicKey
            )

        val ciphertext =
            encodeBase64(
                result.encapsulation
            )

        val sharedSecret =
            result.secret

        result.destroy()

        return EncapsulationResult(
            ciphertext = ciphertext,
            sharedSecret = sharedSecret
        )
    }


    fun decapsulate(
        privateKey: MLKEMPrivateKeyParameters,
        ciphertextBase64: String
    ): ByteArray {

        val ciphertext =
            decodeBase64(ciphertextBase64)

        val extractor =
            MLKEMExtractor(privateKey)

        return extractor.extractSecret(
            ciphertext
        )
    }


    // ============================================================
    // HKDF-SHA256
    // Compatible with Python cryptography HKDF
    // ============================================================

    fun deriveSessionKey(
        sharedSecret: ByteArray,
        initiator: String,
        responder: String
    ): ByteArray {

        val info =
            protocolVersion +
                    "|session|".toByteArray(StandardCharsets.UTF_8) +
                    initiator.toByteArray(StandardCharsets.UTF_8) +
                    "|".toByteArray(StandardCharsets.UTF_8) +
                    responder.toByteArray(StandardCharsets.UTF_8)

        return hkdfSha256(
            inputKeyMaterial = sharedSecret,
            salt = null,
            info = info,
            length = 32
        )
    }


    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray?,
        info: ByteArray,
        length: Int
    ): ByteArray {

        val actualSalt =
            salt ?: ByteArray(32)

        val mac =
            Mac.getInstance("HmacSHA256")

        mac.init(
            SecretKeySpec(
                actualSalt,
                "HmacSHA256"
            )
        )

        // HKDF-Extract
        val prk =
            mac.doFinal(
                inputKeyMaterial
            )

        val output =
            ByteArray(length)

        var previous =
            ByteArray(0)

        var offset = 0

        var counter = 1

        // HKDF-Expand
        while (offset < length) {

            mac.init(
                SecretKeySpec(
                    prk,
                    "HmacSHA256"
                )
            )

            previous =
                mac.doFinal(
                    previous +
                            info +
                            byteArrayOf(
                                counter.toByte()
                            )
                )

            val copyLength =
                minOf(
                    previous.size,
                    length - offset
                )

            System.arraycopy(
                previous,
                0,
                output,
                offset,
                copyLength
            )

            offset += copyLength
            counter++
        }

        return output
    }


    // ============================================================
    // HMAC-SHA256
    // ============================================================

    fun createConfirmationTag(
        sharedSecret: ByteArray,
        initiator: String,
        responder: String
    ): String {

        val context =
            protocolVersion +
                    "|confirm|".toByteArray(StandardCharsets.UTF_8) +
                    initiator.toByteArray(StandardCharsets.UTF_8) +
                    "|".toByteArray(StandardCharsets.UTF_8) +
                    responder.toByteArray(StandardCharsets.UTF_8)

        val mac =
            Mac.getInstance(
                "HmacSHA256"
            )

        mac.init(
            SecretKeySpec(
                sharedSecret,
                "HmacSHA256"
            )
        )

        return mac
            .doFinal(context)
            .joinToString("") {
                "%02x".format(it)
            }
    }


    fun verifyConfirmationTag(
        sharedSecret: ByteArray,
        initiator: String,
        responder: String,
        receivedTag: String
    ): Boolean {

        val expectedTag =
            createConfirmationTag(
                sharedSecret,
                initiator,
                responder
            )

        return MessageDigest.isEqual(
            expectedTag.toByteArray(
                StandardCharsets.UTF_8
            ),
            receivedTag.toByteArray(
                StandardCharsets.UTF_8
            )
        )
    }


    // ============================================================
    // AES-256-GCM
    // ============================================================

    data class EncryptionResult(
        val nonce: String,
        val ciphertext: String
    )


    fun encryptMessage(
        sessionKey: ByteArray,
        message: String,
        sender: String,
        recipient: String,
        messageId: String
    ): EncryptionResult {

        require(sessionKey.size == 32) {
            "AES-256 requires a 32-byte key"
        }

        val nonce =
            ByteArray(12)

        random.nextBytes(nonce)

        val associatedData =
            buildAssociatedData(
                sender,
                recipient,
                messageId
            )

        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(
                sessionKey,
                "AES"
            ),
            GCMParameterSpec(
                128,
                nonce
            )
        )

        cipher.updateAAD(
            associatedData
        )

        val ciphertext =
            cipher.doFinal(
                message.toByteArray(
                    StandardCharsets.UTF_8
                )
            )

        return EncryptionResult(
            nonce =
                encodeBase64(nonce),

            ciphertext =
                encodeBase64(ciphertext)
        )
    }


    fun decryptMessage(
        sessionKey: ByteArray,
        nonceBase64: String,
        ciphertextBase64: String,
        sender: String,
        recipient: String,
        messageId: String
    ): String {

        require(sessionKey.size == 32) {
            "AES-256 requires a 32-byte key"
        }

        val nonce =
            decodeBase64(nonceBase64)

        val ciphertext =
            decodeBase64(ciphertextBase64)

        val associatedData =
            buildAssociatedData(
                sender,
                recipient,
                messageId
            )

        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(
                sessionKey,
                "AES"
            ),
            GCMParameterSpec(
                128,
                nonce
            )
        )

        cipher.updateAAD(
            associatedData
        )

        val plaintext =
            cipher.doFinal(
                ciphertext
            )

        return String(
            plaintext,
            StandardCharsets.UTF_8
        )
    }


    // ============================================================
    // AES-GCM Associated Data
    // ============================================================

    private fun buildAssociatedData(
        sender: String,
        recipient: String,
        messageId: String
    ): ByteArray {

        return protocolVersion +
                "|".toByteArray(StandardCharsets.UTF_8) +
                sender.toByteArray(StandardCharsets.UTF_8) +
                "|".toByteArray(StandardCharsets.UTF_8) +
                recipient.toByteArray(StandardCharsets.UTF_8) +
                "|".toByteArray(StandardCharsets.UTF_8) +
                messageId.toByteArray(StandardCharsets.UTF_8)
    }
}