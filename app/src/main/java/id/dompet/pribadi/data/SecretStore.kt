package id.dompet.pribadi.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only AES-GCM ciphertext in app preferences; the key never leaves Android Keystore. */
class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("discord_secret_ciphertext", Context.MODE_PRIVATE)
    private val alias = "dompet_discord_webhook_aes"

    fun getWebhook(): String? {
        val encoded = preferences.getString(KEY_WEBHOOK, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = 12 // AES-GCM standard nonce size used by Android Keystore.
            if (combined.size <= ivSize) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, combined.copyOfRange(0, ivSize)))
            String(cipher.doFinal(combined.copyOfRange(ivSize, combined.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun setWebhook(url: String?) {
        if (url.isNullOrBlank()) {
            preferences.edit().remove(KEY_WEBHOOK).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(url.trim().toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        preferences.edit().putString(KEY_WEBHOOK, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    fun hasWebhook(): Boolean = !getWebhook().isNullOrBlank()

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private companion object { const val KEY_WEBHOOK = "webhook_ciphertext" }
}
