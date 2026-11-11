package com.example.data

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val apiKey: String,
    val baseUrl: String,
    val modelName: String,
    val provider: String, // "9ROUTER" or "GEMINI_DIRECT"
    val defaultAspect: String // "9:16" or "ORIGINAL"
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auto_clipper_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_API_KEY = "sk-f3a0eacae7b8c282-rm4sr9-7c00c4e6"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:20128/v1"
        const val DEFAULT_MODEL = "AdheRouter"
        const val PROVIDER_9ROUTER = "9ROUTER"
        const val PROVIDER_GEMINI_DIRECT = "GEMINI_DIRECT"
        const val ASPECT_VERTICAL = "9:16"
        const val ASPECT_ORIGINAL = "ORIGINAL"

        // 9Router Combos & Vision Adapter models from user's setup
        val COMBOS_9ROUTER = listOf(
            "AdheRouter",
            "Bandel-Gacor",
            "zd/gpt-6-luna",
            "ag/claude-sonnet-4-6",
            "oc-prod/deepseek-v4.1-flash",
            "oc/mimo-v2.6-flash-free",
            "ag/gemini-3.8-flash-high"
        )

        val URL_PRESETS = listOf(
            "http://192.168.1.100:20128/v1" to "HP Fisik (IP LAN PC)",
            "http://10.0.2.2:20128/v1" to "Emulator (10.0.2.2)",
            "http://localhost:20128/v1" to "Localhost (:20128)",
            "https://openrouter.ai/api/v1" to "OpenRouter Cloud"
        )

        fun normalizeChatEndpoint(baseUrl: String): String {
            val clean = baseUrl.trim().removeSuffix("/")
            return when {
                clean.endsWith("/chat/completions") -> clean
                clean.endsWith("/v1") -> "$clean/chat/completions"
                else -> "$clean/v1/chat/completions"
            }
        }
    }

    fun getSettings(): AppSettings {
        var apiKey = prefs.getString("api_key", DEFAULT_API_KEY) ?: DEFAULT_API_KEY
        var baseUrl = prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        var modelName = prefs.getString("model_name", DEFAULT_MODEL) ?: DEFAULT_MODEL
        var provider = prefs.getString("provider", PROVIDER_9ROUTER) ?: PROVIDER_9ROUTER
        val defaultAspect = prefs.getString("default_aspect", ASPECT_VERTICAL) ?: ASPECT_VERTICAL

        // Auto-upgrade legacy defaults to 9Router combo AdheRouter
        if (modelName == "google/gemini-2.0-flash-001" || modelName.isBlank()) {
            modelName = DEFAULT_MODEL
        }
        if (baseUrl == "https://openrouter.ai/api/v1") {
            baseUrl = DEFAULT_BASE_URL
        }
        if (apiKey.isBlank()) {
            apiKey = DEFAULT_API_KEY
        }
        if (provider == "OPENROUTER") {
            provider = PROVIDER_9ROUTER
        }

        return AppSettings(
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelName = modelName,
            provider = provider,
            defaultAspect = defaultAspect
        )
    }

    fun saveSettings(
        apiKey: String,
        baseUrl: String,
        modelName: String,
        provider: String,
        defaultAspect: String
    ) {
        prefs.edit()
            .putString("api_key", apiKey.trim())
            .putString("base_url", baseUrl.trim().removeSuffix("/"))
            .putString("model_name", modelName.trim())
            .putString("provider", provider)
            .putString("default_aspect", defaultAspect)
            .apply()
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
