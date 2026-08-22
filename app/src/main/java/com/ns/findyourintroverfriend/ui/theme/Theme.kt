package com.ns.findyourintroverfriend.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.VeilThemeManager

// ── Dark colour scheme (existing) ─────────────────────────────────────────────
private val VeilDarkColorScheme = darkColorScheme(
    primary              = VeilPrimary,
    onPrimary            = VeilTextPrimary,
    primaryContainer     = VeilSurfaceHigh,
    onPrimaryContainer   = VeilTextPrimary,
    secondary            = VeilPrimaryLight,
    onSecondary          = VeilBg,
    secondaryContainer   = VeilSurface,
    onSecondaryContainer = VeilTextSecondary,
    tertiary             = VeilAccentSand,
    onTertiary           = VeilBg,
    background           = VeilBg,
    onBackground         = VeilTextPrimary,
    surface              = VeilSurface,
    onSurface            = VeilTextPrimary,
    surfaceVariant       = VeilSurfaceAlt,
    onSurfaceVariant     = VeilTextSecondary,
    outline              = VeilPrimaryPale.copy(alpha = 0.4f),
    outlineVariant       = VeilDivider,
    error                = VeilError,
    onError              = VeilTextPrimary,
)

// ── Light colour scheme ────────────────────────────────────────────────────────
private val VeilLightColorScheme = lightColorScheme(
    primary              = VeilPrimary,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFE8F4FF),
    onPrimaryContainer   = Color(0xFF003D6B),
    secondary            = Color(0xFF5A6070),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFE4E8F0),
    onSecondaryContainer = Color(0xFF22252E),
    tertiary             = Color(0xFF7C6F4A),
    onTertiary           = Color.White,
    background           = Color(0xFFF5F6F8),
    onBackground         = Color(0xFF16171A),
    surface              = Color(0xFFFFFFFF),
    onSurface            = Color(0xFF16171A),
    surfaceVariant       = Color(0xFFF0F1F5),
    onSurfaceVariant     = Color(0xFF5A6070),
    outline              = Color(0xFFD0D0DA),
    outlineVariant       = Color(0xFFE8E8EE),
    error                = VeilError,
    onError              = Color.White,
)

@Composable
fun VeilTheme(content: @Composable () -> Unit) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark       = VeilThemeManager.isDark(isSystemDark)
    val colorScheme  = if (isDark) VeilDarkColorScheme else VeilLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalVeilIsDark provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = VeilTypography,
            content     = content
        )
    }
}

// ── Keep old name as alias so nothing breaks ───────────────────────────────────
@Composable
fun WhereIsMyIntroverFriendTheme(content: @Composable () -> Unit) = VeilTheme(content)