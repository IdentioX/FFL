package ru.ffl24.zakaz.settings

import android.content.Context
import ru.ffl24.zakaz.BuildConfig

object AppSettingsStore {
    private const val PREFS_NAME = "driver_app_settings"
    private const val KEY_API_BASE_URL = "api_base_url"
    private const val KEY_DRIVER_ID = "driver_id"
    private const val KEY_DARK_THEME_ENABLED = "dark_theme_enabled"
    const val DEFAULT_DRIVER_ID = 97

    fun getApiBaseUrl(context: Context): String {
        val savedUrl = preferences(context).getString(KEY_API_BASE_URL, null)
        return normalizeBaseUrl(savedUrl ?: BuildConfig.DRIVER_API_BASE_URL)
    }

    fun getDriverId(context: Context): Int {
        return preferences(context).getInt(KEY_DRIVER_ID, DEFAULT_DRIVER_ID)
    }

    fun isDarkThemeEnabled(context: Context): Boolean {
        return preferences(context).getBoolean(KEY_DARK_THEME_ENABLED, false)
    }

    fun saveSettings(
        context: Context,
        apiBaseUrl: String,
        driverId: Int,
        darkThemeEnabled: Boolean = isDarkThemeEnabled(context)
    ) {
        preferences(context).edit()
            .putString(KEY_API_BASE_URL, normalizeBaseUrl(apiBaseUrl))
            .putInt(KEY_DRIVER_ID, driverId)
            .putBoolean(KEY_DARK_THEME_ENABLED, darkThemeEnabled)
            .apply()
    }

    fun saveDarkThemeEnabled(
        context: Context,
        enabled: Boolean
    ) {
        preferences(context).edit()
            .putBoolean(KEY_DARK_THEME_ENABLED, enabled)
            .apply()
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
