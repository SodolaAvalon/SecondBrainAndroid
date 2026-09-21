package com.lifeos.secondbrain.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small AES-GCM secret store backed by Android Keystore. Plaintext secrets never enter DataStore. */
class SecureSecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure-secrets", Context.MODE_PRIVATE)
    private val alias = "second-brain-secrets-v1"

    fun put(key: String, value: String) {
        if (value.isBlank()) {
            remove(key)
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString(key, encoded).apply()
    }

    fun get(key: String): String? = runCatching {
        val encoded = prefs.getString(key, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        if (bytes.size <= 12) return null
        val iv = bytes.copyOfRange(0, 12)
        val ciphertext = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }.getOrNull()

    fun has(key: String): Boolean = get(key)?.isNotBlank() == true

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Random SQLCipher passphrase, itself encrypted by the Android Keystore key. */
    fun databasePassphrase(): String {
        get(DATABASE_PASSPHRASE)?.takeIf { it.isNotBlank() }?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val generated = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)
        put(DATABASE_PASSPHRASE, generated)
        return generated
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val AI_API_KEY = "ai_api_key"
        const val PROXY_PASSWORD = "proxy_password"
        private const val DATABASE_PASSPHRASE = "database_passphrase"
    }
}
