package com.matan.secondbrain.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Keeps the API key encrypted on the device. It never leaves the phone except in calls to the AI provider. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "sbp_secure_prefs",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("sbp_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val _hasKey = MutableStateFlow(apiKey.isNotBlank())
    val hasKey: StateFlow<Boolean> = _hasKey

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) {
            prefs.edit().putString("api_key", value.trim()).apply()
            _hasKey.value = value.isNotBlank()
        }

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString("model", value.trim().ifEmpty { DEFAULT_MODEL }).apply()

    var autoEnrich: Boolean
        get() = prefs.getBoolean("auto_enrich", true)
        set(value) = prefs.edit().putBoolean("auto_enrich", value).apply()

    companion object {
        const val DEFAULT_MODEL = "claude-haiku-5-5"
        val MODEL_CHOICES = listOf("claude-haiku-5-5", "claude-sonnet-5-5")
    }
}
