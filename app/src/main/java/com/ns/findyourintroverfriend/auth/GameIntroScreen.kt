package com.ns.findyourintroverfriend.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
//  Veil — Welcome Screen  (shown once, after first profile setup)
//  Clean white + black — matches the rest of the onboarding aesthetic
// ─────────────────────────────────────────────────────────────────────────────

private val WBg       = Color(0xFFFFFFFF)
private val WText     = Color(0xFF0A0A0A)
private val WTextSub  = Color(0xFF6B6B6B)
private val WTextHint = Color(0xFFABABAB)
private val WDivider  = Color(0xFFEAEAEA)
private val WInput    = Color(0xFFF5F5F5)
private val WBtnBg    = Color(0xFF0A0A0A)
private val WBtnText  = Color(0xFFFFFFFF)

@Composable
fun GameIntroScreen(onComplete: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    // Fade-in entrance
    val contentAlpha by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "fadeIn"
    )

    // Breathing scale on the logo mark
    val inf = rememberInfiniteTransition(label = "breath")
    val breathe by inf.animateFloat(
        0.92f, 1.04f,
        infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "breathe"
    )

    Surface(color = WBg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .alpha(contentAlpha)
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            // ── Logo mark ────────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .size(80.dp)
                    .scale(breathe)
                    .clip(CircleShape)
                    .background(WInput),
                contentAlignment = Alignment.Center
            ) {
                // Two overlapping circles — Veil's identity mark
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .offset(x = (-8).dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape    = CircleShape,
                        color    = Color.Transparent,
                        border   = ButtonDefaults.outlinedButtonBorder
                    ) {}
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── Wordmark ─────────────────────────────────────────────────────
            Text(
                "VEIL",
                fontSize      = 13.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 8.sp,
                color         = WText
            )

            Spacer(Modifier.height(16.dp))

            // ── Welcome headline ─────────────────────────────────────────────
            Text(
                "Welcome.",
                fontSize      = 36.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = WText,
                textAlign     = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "You're now part of something different.\nReal conversations. No performance.\nJust you.",
                fontSize   = 15.sp,
                color      = WTextSub,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(36.dp))

            // ── Feature pills ────────────────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier            = Modifier.fillMaxWidth()
            ) {
                WelcomePill("🌙", "Anonymous for the first 3 days")
                WelcomePill("💬", "One thoughtful question at a time")
                WelcomePill("🔒", "Your real name only reveals when you choose")
            }

            Spacer(Modifier.weight(1f))

            HorizontalDivider(color = WDivider, thickness = 1.dp)
            Spacer(Modifier.height(24.dp))

            // ── CTA ──────────────────────────────────────────────────────────
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WBtnBg),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    "Let's go  ✦",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = WBtnText
                )
            }

            Spacer(Modifier.height(14.dp))

            // Skip link
            Text(
                "Skip and explore →",
                fontSize  = 13.sp,
                color     = WTextHint,
                modifier  = Modifier.clickable { onComplete() }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Feature pill row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WelcomePill(emoji: String, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WInput)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(
            label,
            fontSize   = 14.sp,
            color      = Color(0xFF3A3A3A),
            fontWeight = FontWeight.Normal,
            lineHeight = 18.sp
        )
    }
}
