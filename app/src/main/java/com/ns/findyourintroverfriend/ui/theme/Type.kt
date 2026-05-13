package com.ns.findyourintroverfriend.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.ns.findyourintroverfriend.R

// ── Google Fonts Provider ─────────────────────────────────────────────────────
val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

// ── Nunito — warm, rounded, friendly. Closest to Bunny Snaps aesthetic. ──────
private val Nunito = GoogleFont("Nunito")

val NunitoFamily = FontFamily(
    Font(googleFont = Nunito, fontProvider = googleFontProvider, weight = FontWeight.Light),
    Font(googleFont = Nunito, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = Nunito, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = Nunito, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = Nunito, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = Nunito, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold),
    Font(googleFont = Nunito, fontProvider = googleFontProvider, weight = FontWeight.Black),
)

// ── Veil Typography System ────────────────────────────────────────────────────
val VeilTypography = Typography(

    // Hero / Wordmark — "VEIL"
    displayLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Black,
        fontSize     = 56.sp,
        lineHeight   = 64.sp,
        letterSpacing = 6.sp,
        color        = VeilTextPrimary
    ),

    // Section hero titles
    displayMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.ExtraBold,
        fontSize     = 40.sp,
        lineHeight   = 48.sp,
        letterSpacing = 1.sp,
        color        = VeilTextPrimary
    ),

    // Screen titles
    headlineLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 30.sp,
        lineHeight   = 38.sp,
        letterSpacing = 0.sp,
        color        = VeilTextPrimary
    ),

    headlineMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 24.sp,
        lineHeight   = 32.sp,
        letterSpacing = 0.sp,
        color        = VeilTextPrimary
    ),

    headlineSmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 20.sp,
        lineHeight   = 28.sp,
        letterSpacing = 0.sp,
        color        = VeilTextPrimary
    ),

    // Card / step titles
    titleLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 18.sp,
        lineHeight   = 26.sp,
        letterSpacing = 0.sp,
        color        = VeilTextPrimary
    ),

    titleMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.15.sp,
        color        = VeilTextPrimary
    ),

    titleSmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp,
        color        = VeilTextPrimary
    ),

    // Body text
    bodyLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.5.sp,
        color        = VeilTextSecondary
    ),

    bodyMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.25.sp,
        color        = VeilTextSecondary
    ),

    bodySmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.4.sp,
        color        = VeilTextTertiary
    ),

    // Labels & buttons
    labelLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 15.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.5.sp,
        color        = VeilTextPrimary
    ),

    labelMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.8.sp,
        color        = VeilTextSecondary
    ),

    labelSmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 11.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.8.sp,
        color        = VeilTextTertiary
    )
)