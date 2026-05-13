package com.ns.findyourintroverfriend.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  VEIL — "Bonds in Bloom"  Rich Introvert Friendship Palette
//  Philosophy: Warm, cozy, safe — premium without being cold.
//  Day: Soft lavender-rose dawn.  Night: Deep indigo velvet.
// ═══════════════════════════════════════════════════════════════════════════════

// ── Day Mode Backgrounds ──────────────────────────────────────────────────────
val DayBg          = Color(0xFFF5F0FF)   // Soft lavender mist
val DayBgAlt       = Color(0xFFEDE6FA)   // Slightly deeper lavender
val DaySurface     = Color(0xFFFFFFFF)   // Pure white card surface
val DaySurfaceAlt  = Color(0xFFF9F5FF)   // Off-white tinted surface
val DaySurfaceHigh = Color(0xFFF0E9FF)   // Elevated card

// ── Night Mode Backgrounds ────────────────────────────────────────────────────
val NightBg        = Color(0xFF12082A)   // Deep indigo void
val NightBgAlt     = Color(0xFF1A0D38)   // Slightly lifted indigo
val NightSurface   = Color(0xFF221444)   // Card surface — dark violet
val NightSurfaceAlt= Color(0xFF2C1A58)   // Elevated card
val NightSurfaceHigh= Color(0xFF361F6C)  // Highest surface

// ── Primary — Bloom Violet ────────────────────────────────────────────────────
val BloomPrimary    = Color(0xFF7C3AED)   // Vivid violet — hero
val BloomPrimaryMid = Color(0xFF8B5CF6)   // Mid violet
val BloomPrimaryLight= Color(0xFFA78BFA)  // Soft violet
val BloomPrimaryPale = Color(0xFFDDD6FE)  // Pale violet (day chips)

// ── Accent — Sunrise Rose ─────────────────────────────────────────────────────
val BloomRose       = Color(0xFFFF6B9D)   // Rose — warmth, connection
val BloomRoseLight  = Color(0xFFFFB3CF)   // Light rose
val BloomGold       = Color(0xFFFFBB55)   // Warm gold — ceremony, day 3
val BloomGoldLight  = Color(0xFFFFE0A3)   // Soft gold
val BloomMint       = Color(0xFF34D399)   // Mint — trust, unlock
val BloomMintLight  = Color(0xFFA7F3D0)   // Light mint
val BloomPeriwinkle = Color(0xFF818CF8)   // Periwinkle — calm day 2
val BloomSky        = Color(0xFF38BDF8)   // Sky blue — explore

// ── Text — Day ───────────────────────────────────────────────────────────────
val DayTextPrimary   = Color(0xFF1E1035)  // Deep violet-black
val DayTextSecondary = Color(0xFF5B4A7A)  // Medium violet
val DayTextTertiary  = Color(0xFF9280B3)  // Muted violet
val DayTextDisabled  = Color(0xFFBFAFD8)  // Disabled

// ── Text — Night ──────────────────────────────────────────────────────────────
val NightTextPrimary   = Color(0xFFF0ECFF)  // Near-white with violet warmth
val NightTextSecondary = Color(0xFFCBBEF5)  // Muted lavender
val NightTextTertiary  = Color(0xFF9580C8)  // Dim violet
val NightTextDisabled  = Color(0xFF5A447A)  // Disabled

// ── Status ────────────────────────────────────────────────────────────────────
val StatusSuccess   = Color(0xFF22C55E)
val StatusWarning   = Color(0xFFF59E0B)
val StatusError     = Color(0xFFEF4444)
val StatusInfo      = Color(0xFF3B82F6)

// ── Dividers / Overlays ───────────────────────────────────────────────────────
val DayDivider     = Color(0x18000000)
val NightDivider   = Color(0x1AFFFFFF)
val ModalOverlay   = Color(0x90000000)

// ── Day Gradients ─────────────────────────────────────────────────────────────
val DayGradTop     = Color(0xFFEDE6FA)
val DayGradMid     = Color(0xFFF5F0FF)
val DayGradBottom  = Color(0xFFFFEEF5)

// ── Night Gradients ───────────────────────────────────────────────────────────
val NightGradTop   = Color(0xFF0E0620)
val NightGradMid   = Color(0xFF180A36)
val NightGradBottom= Color(0xFF220D48)

// ── Legacy Veil names (keep for backward compat in unchanged screens) ─────────
val VeilBg           = NightBg
val VeilBgAlt        = NightBgAlt
val VeilSurface      = NightSurface
val VeilSurfaceAlt   = NightSurfaceAlt
val VeilSurfaceHigh  = NightSurfaceHigh
val VeilPrimary      = BloomPrimary
val VeilPrimaryMid   = BloomPrimaryMid
val VeilPrimaryLight = BloomPrimaryLight
val VeilPrimaryPale  = BloomPrimaryPale
val VeilAccentSand   = BloomGoldLight
val VeilAccentGold   = BloomGold
val VeilAccentGlow   = BloomPeriwinkle
val VeilAccentMint   = BloomMint
val VeilTextPrimary  = NightTextPrimary
val VeilTextSecondary= NightTextSecondary
val VeilTextTertiary = NightTextTertiary
val VeilTextDisabled = NightTextDisabled
val VeilSuccess      = StatusSuccess
val VeilWarning      = StatusWarning
val VeilError        = StatusError
val VeilDivider      = NightDivider
val VeilOverlay      = ModalOverlay
val VeilRipple       = BloomPeriwinkle.copy(alpha = 0.1f)
val VeilGradStart    = NightGradTop
val VeilGradMid      = NightGradMid
val VeilGradEnd      = NightGradBottom

// ── Material3 placeholders ────────────────────────────────────────────────────
val Purple80         = Color(0xFFD0BCFF)
val PurpleGrey80     = Color(0xFFCCC2DC)
val Pink80           = Color(0xFFEFB8C8)
val Purple40         = Color(0xFF6650A4)
val PurpleGrey40     = Color(0xFF625B71)
val Pink40           = Color(0xFF7D5260)