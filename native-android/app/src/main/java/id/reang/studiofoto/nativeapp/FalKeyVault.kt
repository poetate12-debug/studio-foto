package id.reang.studiofoto.nativeapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the fal.ai key encrypted with an AES-GCM key held by Android Keystore.
 * The raw API key is never written to preferences or project data.
 */
object FalKeyVault {
    private const val KEY_ALIAS = "reang_fal_api_key_v1"
    private const val PREFS = "reang_secure_settings"
    private const val CIPHER = "AES/GCM/NoPadding"

    fun load(context: Context): String {
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ivText = prefs.getString("fal_iv", null) ?: return ""
            val payloadText = prefs.getString("fal_payload", null) ?: return ""
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(payloadText, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun save(context: Context, apiKey: String) {
        val clean = apiKey.trim()
        require(clean.isEmpty() || clean.length >= 12) { "API key terlalu pendek." }
        if (clean.isEmpty()) {
            clear(context)
            return
        }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("fal_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("fal_payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("fal_iv").remove("fal_payload").apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
