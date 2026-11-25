package com.example.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.ApiProvider
import com.example.model.CustomApiSettings
import com.example.model.ZenvixModelSpec
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiKeyVault(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): CustomApiSettings {
        val provider = ApiProvider.fromRaw(prefs.getString(KEY_PROVIDER, null))
        val baseUrl = prefs.getString(KEY_BASE_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: provider.defaultBaseUrl
        val encryptedKey = prefs.getString(KEY_API_KEY_ENC, null)
        val decryptedKey = if (!encryptedKey.isNullOrBlank()) decrypt(encryptedKey) else ""
        val customModels = parseModelsJson(prefs.getString(KEY_CUSTOM_MODELS, null))
        return CustomApiSettings(
            provider = provider,
            baseUrl = baseUrl,
            apiKey = decryptedKey,
            customModels = customModels
        )
    }

    fun saveSettings(settings: CustomApiSettings) {
        val cleanUrl = settings.baseUrl.trim().ifBlank { settings.provider.defaultBaseUrl }
        val cleanKey = settings.apiKey.trim()
        val encryptedKey = if (cleanKey.isNotEmpty()) encrypt(cleanKey) else ""
        val modelsJson = serializeModelsJson(settings.customModels)

        prefs.edit()
            .putString(KEY_PROVIDER, settings.provider.name)
            .putString(KEY_BASE_URL, cleanUrl)
            .putString(KEY_API_KEY_ENC, encryptedKey)
            .putString(KEY_CUSTOM_MODELS, modelsJson)
            .apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY_ENC).apply()
    }

    private fun parseModelsJson(raw: String?): List<ZenvixModelSpec> {
        if (raw.isNullOrBlank()) return defaultExternalModels()
        return try {
            val arr = JSONArray(raw)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("displayName").trim()
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        add(
                            ZenvixModelSpec(
                                id = id,
                                displayName = name,
                                badge = obj.optString("badge", "API").ifBlank { "API" },
                                subtitle = obj.optString("subtitle", "Custom external API model"),
                                isExternalApi = true,
                                isFreeBuiltIn = false
                            )
                        )
                    }
                }
            }
            list.ifEmpty { defaultExternalModels() }
        } catch (_: Exception) {
            defaultExternalModels()
        }
    }

    private fun serializeModelsJson(models: List<ZenvixModelSpec>): String {
        val arr = JSONArray()
        models.filter { it.isExternalApi }.forEach { model ->
            arr.put(
                JSONObject().apply {
                    put("id", model.id)
                    put("displayName", model.displayName)
                    put("badge", model.badge)
                    put("subtitle", model.subtitle)
                }
            )
        }
        return arr.toString()
    }

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + ciphertext.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
            "ks:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun decrypt(encoded: String): String {
        if (!encoded.startsWith("ks:")) return ""
        return try {
            val combined = Base64.decode(encoded.removePrefix("ks:"), Base64.NO_WRAP)
            if (combined.size < 14) return ""
            val ivLen = combined[0].toInt() and 0xFF
            if (combined.size <= 1 + ivLen) return ""
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val PREFS_NAME = "zenvix_api_vault"
        private const val KEY_PROVIDER = "api_provider"
        private const val KEY_BASE_URL = "api_base_url"
        private const val KEY_API_KEY_ENC = "api_key_enc"
        private const val KEY_CUSTOM_MODELS = "custom_models_json"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "zenvix_byok_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

        fun defaultExternalModels(): List<ZenvixModelSpec> = listOf(
            ZenvixModelSpec(
                id = "opus-5.5",
                displayName = "Opus 5.5",
                badge = "API • Flagship",
                subtitle = "Maximum reasoning, deep coding & complex tasks",
                isExternalApi = true,
                isFreeBuiltIn = false
            ),
            ZenvixModelSpec(
                id = "febal-5.1",
                displayName = "Febal 5.1",
                badge = "API • Pro",
                subtitle = "Fast creative synthesis & high-precision prose",
                isExternalApi = true,
                isFreeBuiltIn = false
            ),
            ZenvixModelSpec(
                id = "sonnet-5.5",
                displayName = "Sonnet 5.5",
                badge = "API • Balanced",
                subtitle = "Everyday coding, artifacts & sharp analysis",
                isExternalApi = true,
                isFreeBuiltIn = false
            )
        )
    }
}
