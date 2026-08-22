package com.ns.findyourintroverfriend.util

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Manages the app-wide theme preference.
 * Three modes:
 *   SYSTEM  — follows device dark/light setting (default)
 *   DARK    — always dark
 *   LIGHT   — always light
 *
 * Persists to SharedPreferences so the choice survives app restarts.
 */
object VeilThemeManager {

    enum class ThemeMode { SYSTEM, DARK, LIGHT }

    private const val PREFS_NAME = "veil_prefs"
    private const val KEY_THEME  = "theme_mode"

    /** Current user-chosen mode — backed by Compose state so UI recomposes automatically */
    var themeMode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /**
     * Resolved isDark value pre-computed during init() from the Android Configuration.
     * Safe to read from file-level mutableStateOf initializers (before Compose starts).
     */
    var initialResolvedDark: Boolean = false
        private set

    /** Load the saved preference on startup (call from MainActivity.onCreate) */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeMode = when (prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)) {
            ThemeMode.DARK.name  -> ThemeMode.DARK
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            else                 -> ThemeMode.SYSTEM
        }
        // Pre-compute the effective dark state so file-level state properties
        // (e.g. _feedDark in IntrovertFeedScreen) initialize to the right value
        // immediately, without waiting for the first SideEffect pass.
        val nightFlags = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        initialResolvedDark = isDark(isSystemDark)
    }

    /** Called from Profile page toggle */
    fun setTheme(context: Context, mode: ThemeMode) {
        themeMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_THEME, mode.name)
        }
    }

    /**
     * Resolves the effective isDark flag given the current system dark state.
     * @param isSystemDark  true if the device is currently in dark mode
     */
    fun isDark(isSystemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemDark
    }
}

/** CompositionLocal that carries the resolved isDark boolean through the Compose tree */
val LocalVeilIsDark = compositionLocalOf { false }
