package ru.ffl24.zakaz.settings

import android.content.Context
import ru.ffl24.zakaz.BuildConfig

object AppSettingsStore {
    private const val PREFS_NAME = "driver_app_settings"
    private const val KEY_API_BASE_URL = "api_base_url"
    private const val KEY_DRIVER_ID = "driver_id"
    const val DEFAULT_DRIVER_ID = 97

    fun getApiBaseUrl(context: Context): String {
        val savedUrl = preferences(context).getString(KEY_API_BASE_URL, null)
        return normalizeBaseUrl(savedUrl ?: BuildConfig.DRIVER_API_BASE_URL)
    }

    fun getDriverId(context: Context): Int {
        return preferences(context).getInt(KEY_DRIVER_ID, DEFAULT_DRIVER_ID)
    }

    fun saveSettings(
        context: Context,
        apiBaseUrl: String,
        driverId: Int
    ) {
        preferences(context).edit()
            .putString(KEY_API_BASE_URL, normalizeBaseUrl(apiBaseUrl))
            .putInt(KEY_DRIVER_ID, driverId)
            .apply()
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
