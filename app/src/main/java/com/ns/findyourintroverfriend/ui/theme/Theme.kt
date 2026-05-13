package com.ns.findyourintroverfriend.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Veil always uses a dark colour scheme — day/night is handled by the island ──
private val VeilColorScheme = darkColorScheme(
    primary            = VeilPrimary,
    onPrimary          = VeilTextPrimary,
    primaryContainer   = VeilSurfaceHigh,
    onPrimaryContainer = VeilTextPrimary,

    secondary          = VeilPrimaryLight,
    onSecondary        = VeilBg,
    secondaryContainer = VeilSurface,
    onSecondaryContainer = VeilTextSecondary,

    tertiary           = VeilAccentSand,
    onTertiary         = VeilBg,

    background         = VeilBg,
    onBackground       = VeilTextPrimary,

    surface            = VeilSurface,
    onSurface          = VeilTextPrimary,
    surfaceVariant     = VeilSurfaceAlt,
    onSurfaceVariant   = VeilTextSecondary,

    outline            = VeilPrimaryPale.copy(alpha = 0.4f),
    outlineVariant     = VeilDivider,

    error              = VeilError,
    onError            = VeilTextPrimary,
)

@Composable
fun VeilTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor  = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = VeilColorScheme,
        typography  = VeilTypography,
        content     = content
    )
}

// ── Keep old name as alias so nothing breaks ──────────────────────────────────
@Composable
fun WhereIsMyIntroverFriendTheme(content: @Composable () -> Unit) = VeilTheme(content)