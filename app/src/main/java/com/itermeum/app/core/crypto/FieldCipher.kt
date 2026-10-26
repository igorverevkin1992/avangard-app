package com.itermeum.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Section 8 invariant I6: `*Enc` columns are AES-GCM with a key that never
 * leaves the Android Keystore. Format: base64(iv[12] + ciphertext + tag).
 * The Keystore is unavailable in JVM tests, so [FieldCipher] is an
 * interface with a plain-text test double.
 */
interface FieldCipher {
    fun encrypt(plain: String): String
    fun decrypt(cipherText: String): String
}

@Singleton
class KeystoreFieldCipher @Inject constructor() : FieldCipher {

    private val key: SecretKey by lazy { loadOrCreate() }

    /** HyperOS Keystore occasionally throws under load; one retry before giving up. */
    private inline fun <T> retryOnce(block: () -> T): T = try { block() } catch (e: java.security.GeneralSecurityException) { block() } catch (e: java.security.ProviderException) { block() }

    override fun encrypt(plain: String): String = retryOnce { encryptOnce(plain) }

    override fun decrypt(cipherText: String): String = retryOnce { decryptOnce(cipherText) }

    private fun encryptOnce(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + out, Base64.NO_WRAP)
    }

    private fun decryptOnce(cipherText: String): String {
        val all = Base64.decode(cipherText, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, IV_BYTES)
        val body = all.copyOfRange(IV_BYTES, all.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun loadOrCreate(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "itermeum_fields_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/** Pure-JVM AES-GCM with an in-memory key: used by tests and the backup pipeline. */
class InMemoryFieldCipher(private val key: SecretKey) : FieldCipher {
    override fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String {
        val all = Base64.decode(cipherText, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }
}
