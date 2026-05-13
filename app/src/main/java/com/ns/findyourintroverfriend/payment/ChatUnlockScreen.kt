package com.ns.findyourintroverfriend.payment

// ─────────────────────────────────────────────────────────────────────────────
//  ChatUnlockScreen — Minimalist high-trust purchase screen
//
//  Design principles:
//   • Clean white background — no dark theme, no gradients, no purple
//   • Lottie "Flying Wallet" animation creates emotional delight without clutter
//   • Trust signals: secure payment badge, one-time price clarity, refund info
//   • Single clear CTA with the custom buy-button image
//   • Testimonial / social proof line increases conversion
//
//  TODO: Wire up Google Play Billing and replace the coming-soon state.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*

// ── Color palette (pure whites + charcoal — no purple, no dark BG) ───────────
private val BgWhite      = Color(0xFFFFFFFF)
private val SurfaceGrey  = Color(0xFFF7F7F8)
private val ChipBg       = Color(0xFFF2F2F7)
private val Charcoal     = Color(0xFF1C1C1E)
private val SubText      = Color(0xFF6C6C70)
private val TertiaryText = Color(0xFFA8A8B0)
private val Hairline     = Color(0xFFE5E5EA)
private val AccentGreen  = Color(0xFF34C759)    // trusted, iOS-green "safe" feel

@Composable
fun ChatUnlockScreen(
    onBack: () -> Unit = {},
) {
    // ── Rocket Lottie ─────────────────────────────────────────────────────────
    val lottieComposition by rememberLottieComposition(
        LottieCompositionSpec.Asset("rocket_success.lottie")
    )
    val lottieProgress by animateLottieCompositionAsState(
        composition = lottieComposition,
        iterations  = LottieConstants.IterateForever,
        speed       = 0.85f,
    )

    // ── Pulse animation for the CTA button ───────────────────────────────────
    val inf = rememberInfiniteTransition(label = "ctaPulse")
    val ctaScale by inf.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.025f,
        animationSpec = infiniteRepeatable(
            tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "ctaPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgWhite)
            .systemBarsPadding()
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint               = Charcoal,
                    )
                }
                Text(
                    "Extend Your Chat",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = Charcoal,
                        fontWeight = FontWeight.SemiBold,
                    )
                )
            }

            // ── Hairline divider ──────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Hairline)
            )

            Spacer(Modifier.height(24.dp))

            // ── Lottie flying wallet ──────────────────────────────────────────
            Box(
                modifier            = Modifier.size(180.dp),
                contentAlignment    = Alignment.Center
            ) {
                LottieAnimation(
                    composition = lottieComposition,
                    progress    = { lottieProgress },
                    modifier    = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Headline ──────────────────────────────────────────────────────
            Text(
                "Your 7 messages are up",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color      = Charcoal,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    fontSize   = 22.sp,
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Keep the conversation alive.\nUnlock 20 more messages — just once, forever.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color      = SubText,
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp,
                    fontSize   = 14.sp,
                ),
                modifier = Modifier.padding(horizontal = 36.dp)
            )

            Spacer(Modifier.height(28.dp))

            // ── Value card ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceGrey)
                    .border(0.8.dp, Hairline, RoundedCornerShape(20.dp))
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValueRow(
                        emoji = "💬",
                        title = "20 messages per session",
                        subtitle = "More than enough to know if you connect"
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
                    ValueRow(
                        emoji = "♾️",
                        title = "One-time — never expires",
                        subtitle = "Pay once, available across all your circles"
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
                    ValueRow(
                        emoji = "🔐",
                        title = "Secure via Google Play",
                        subtitle = "Your payment is protected by Google"
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Price row ─────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "₹79",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color      = Charcoal,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 36.sp,
                    )
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "one-time",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = SubText,
                            fontSize   = 12.sp,
                        )
                    )
                    Text(
                        "no subscription",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = TertiaryText,
                            fontSize = 10.sp,
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── CTA button — clean "Extend" ───────────────────────────────────
            Box(
                modifier          = Modifier
                    .scale(ctaScale)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable { /* TODO: wire Google Play Billing */ },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Extend",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 16.sp,
                    )
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Trust signals ─────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                TrustBadge("🔒 Secure")
                Spacer(Modifier.width(12.dp))
                TrustBadge("⚡ Instant")
                Spacer(Modifier.width(12.dp))
                TrustBadge("🙂 No Spam")
            }

            Spacer(Modifier.height(14.dp))

            // ── Disclaimer ────────────────────────────────────────────────────
            Text(
                "Coming soon via Google Play Billing.\nWe're finalizing a secure purchase experience.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = TertiaryText,
                    textAlign  = TextAlign.Center,
                    lineHeight = 16.sp,
                    fontSize   = 10.sp,
                ),
                modifier = Modifier.padding(horizontal = 40.dp)
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Value row helper ──────────────────────────────────────────────────────────
@Composable
private fun ValueRow(emoji: String, title: String, subtitle: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(ChipBg)
                .border(0.6.dp, Hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 17.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color      = Charcoal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                )
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = SubText,
                    fontSize = 11.sp,
                )
            )
        }
    }
}

// ── Trust badge helper ────────────────────────────────────────────────────────
@Composable
private fun TrustBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ChipBg)
            .border(0.6.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color      = SubText,
                fontWeight = FontWeight.Medium,
                fontSize   = 10.sp,
            )
        )
    }
}
