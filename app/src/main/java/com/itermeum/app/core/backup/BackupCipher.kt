package com.itermeum.app.core.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Section 5.17: `[magic "IMB1"][salt 16B][iv 12B][ciphertext+tag]`, key from
 * the password via PBKDF2-HMAC-SHA256 with 210 000 iterations, AES-256-GCM.
 */
object BackupCipher {
    private val MAGIC = "IMB1".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    class WrongFormat : IllegalArgumentException("not an itermeum backup")

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < MAGIC.size + SALT_BYTES + IV_BYTES + 16 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw WrongFormat()
        var o = MAGIC.size
        val salt = file.copyOfRange(o, o + SALT_BYTES); o += SALT_BYTES
        val iv = file.copyOfRange(o, o + IV_BYTES); o += IV_BYTES
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(file.copyOfRange(o, file.size))
    }

    fun isBackup(file: ByteArray): Boolean = file.size >= MAGIC.size && file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
