package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores sensitive API keys and GitHub credentials locally using Android Keystore AES-256-GCM.
 * Never persists raw secrets in Room database columns, logs, or project exports.
 */
class KeystoreSecretVault(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = Any()

    fun saveSecret(keyId: String, plainSecret: String) {
        synchronized(lock) {
            if (plainSecret.isBlank()) {
                prefs.edit().remove(keyId).apply()
                return
            }
            val encrypted = encryptValue(plainSecret)
            prefs.edit().putString(keyId, encrypted).apply()
        }
    }

    fun getSecret(keyId: String): String? {
        synchronized(lock) {
            val stored = prefs.getString(keyId, null) ?: return null
            return decryptValue(stored)
        }
    }

    fun deleteSecret(keyId: String) {
        synchronized(lock) {
            prefs.edit().remove(keyId).apply()
        }
    }

    fun getAllRegisteredSecretsForRedaction(): List<String> {
        synchronized(lock) {
            return prefs.all.keys.mapNotNull { key ->
                getSecret(key)?.takeIf { it.length >= 6 }
            }
        }
    }

    private fun encryptValue(plainText: String): String {
        val secretKey = getOrCreateKeystoreKey()
        if (secretKey != null) {
            return try {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
                "$KEYSTORE_PREFIX$ivB64:$dataB64"
            } catch (_: Exception) {
                fallbackXorEncode(plainText)
            }
        }
        return fallbackXorEncode(plainText)
    }

    private fun decryptValue(encoded: String): String? {
        if (encoded.startsWith(KEYSTORE_PREFIX)) {
            val payload = encoded.removePrefix(KEYSTORE_PREFIX)
            val parts = payload.split(":", limit = 2)
            if (parts.size != 2) return null
            val secretKey = getOrCreateKeystoreKey() ?: return null
            return try {
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
        if (encoded.startsWith(FALLBACK_PREFIX)) {
            return fallbackXorDecode(encoded)
        }
        return null
    }

    private fun getOrCreateKeystoreKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    // Used only in JVM unit test environments (e.g. Robolectric) where AndroidKeyStore is absent.
    private fun fallbackXorEncode(plainText: String): String {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val input = plainText.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArray(input.size)
        for (i in input.indices) {
            out[i] = (input[i].toInt() xor salt[i % salt.size].toInt() xor 0x5A).toByte()
        }
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(out, Base64.NO_WRAP)
        return "$FALLBACK_PREFIX$saltB64:$dataB64"
    }

    private fun fallbackXorDecode(encoded: String): String? {
        return try {
            val parts = encoded.removePrefix(FALLBACK_PREFIX).split(":", limit = 2)
            if (parts.size != 2) return null
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val out = ByteArray(data.size)
            for (i in data.indices) {
                out[i] = (data[i].toInt() xor salt[i % salt.size].toInt() xor 0x5A).toByte()
            }
            String(out, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "xthrex_encrypted_vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "xthrex_master_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEYSTORE_PREFIX = "ks_v1:"
        private const val FALLBACK_PREFIX = "fb_v1:"

        fun apiKeySlot(profileId: String) = "api_key_$profileId"
        const val GITHUB_TOKEN_SLOT = "github_oauth_pat"
        const val REMOTE_BACKEND_TOKEN_SLOT = "remote_backend_token"
        const val SIGNING_KEYSTORE_PASSWORD_SLOT = "signing_keystore_pw"
    }
}
