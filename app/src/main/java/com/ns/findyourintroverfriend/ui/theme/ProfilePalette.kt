package com.ns.findyourintroverfriend.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ns.findyourintroverfriend.util.LocalVeilIsDark

/** Shared colors for the profile and the People directory. */
@Immutable
internal data class ProfilePalette(
    val background: Color,
    val card: Color,
    val text: Color,
    val secondaryText: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val tintedSurface: Color
)

private val lightProfilePalette = ProfilePalette(
    background = Color(0xFFFAF5F2),
    card = Color.White,
    text = Color(0xFF2E232B),
    secondaryText = Color(0xFF685C66),
    border = Color(0xFFE4D7DF),
    accent = Color(0xFF8A466A),
    onAccent = Color.White,
    tintedSurface = Color(0xFFF1E4EC)
)

private val darkProfilePalette = ProfilePalette(
    background = Color(0xFF131017),
    card = Color(0xFF1D1D28),
    text = Color(0xFFF5EAF1),
    secondaryText = Color(0xFFBFB0BD),
    border = Color(0xFF493543),
    accent = Color(0xFFE8A9C9),
    onAccent = Color(0xFF2B1423),
    tintedSurface = Color(0xFF2A202A)
)

internal val currentProfilePalette: ProfilePalette
    @Composable get() = if (LocalVeilIsDark.current) darkProfilePalette else lightProfilePalette
