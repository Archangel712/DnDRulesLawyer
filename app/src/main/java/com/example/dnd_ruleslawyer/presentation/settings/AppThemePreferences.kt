package com.example.dnd_ruleslawyer.presentation.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppThemePreferences {
    private const val PREFERENCES_NAME = "app_settings"
    private const val KEY_DARK_MODE = "dark_mode"

    fun isDarkModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()

        applyTheme(enabled)
    }

    fun applySavedTheme(context: Context) {
        applyTheme(isDarkModeEnabled(context))
    }

    private fun applyTheme(enabled: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
