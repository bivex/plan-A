package com.cryptex.android.crypto

import org.bouncycastle.crypto.generators.SCrypt
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptexEngine {

    companion object {
        private const val SALT_SIZE = 32
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        
        // Scrypt parameters matching Python version
        private const val SCRYPT_N = 16384
        private const val SCRYPT_R = 8
        private const val SCRYPT_P = 1
        private const val KEY_LENGTH = 32
    }

    fun decrypt(encryptedData: ByteArray, password: String): ByteArray {
        if (encryptedData.size < SALT_SIZE + NONCE_SIZE + TAG_SIZE) {
            throw IllegalArgumentException("Invalid encrypted file: too small")
        }

        val buffer = ByteBuffer.wrap(encryptedData)

        // 1. Extract Salt
        val salt = ByteArray(SALT_SIZE)
        buffer.get(salt)

        // 2. Extract Nonce
        val nonce = ByteArray(NONCE_SIZE)
        buffer.get(nonce)

        // 3. Extract Tag
        val tag = ByteArray(TAG_SIZE)
        buffer.get(tag)

        // 4. Extract Ciphertext
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        // 5. Derive Key using Bouncy Castle scrypt
        val derivedKey = SCrypt.generate(
            password.toByteArray(Charsets.UTF_8),
            salt,
            SCRYPT_N,
            SCRYPT_R,
            SCRYPT_P,
            KEY_LENGTH
        )

        // 6. Decrypt using AES-GCM
        val cipherInput = ciphertext + tag
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_SIZE * 8, nonce)
        val keySpec = SecretKeySpec(derivedKey, "AES")
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
        val decryptedPadded = cipher.doFinal(cipherInput)
        
        // 7. Unpad
        if (decryptedPadded.size < 8) {
            throw IllegalArgumentException("Decrypted data too short for padding header")
        }
        
        val lengthBuffer = ByteBuffer.wrap(decryptedPadded)
        val originalLength = lengthBuffer.long // Read 8 bytes as Long (Big Endian)
        
        if (originalLength < 0 || originalLength > decryptedPadded.size - 8) {
            throw IllegalArgumentException("Invalid length in padding header: $originalLength")
        }
        
        val result = ByteArray(originalLength.toInt())
        lengthBuffer.position(8)
        lengthBuffer.get(result)
        return result
    }
}
