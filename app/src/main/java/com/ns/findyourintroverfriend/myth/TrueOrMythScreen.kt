package com.ns.findyourintroverfriend.myth

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
//  True or Myth — Coming Soon screen (warm ivory/amber theme)
// ─────────────────────────────────────────────────────────────────────────────

private val TmBg         = Color(0xFFFDF8F2)
private val TmBgMid      = Color(0xFFFFF4E8)
private val TmBgDeep     = Color(0xFFFEEDD8)
private val TmAccent     = Color(0xFFE8832A)
private val TmAccentSoft = Color(0xFFF5A654)
private val TmGlow       = Color(0xFFFFB96B)
private val TmText       = Color(0xFF2D1F0E)
private val TmTextSub    = Color(0xFF7A5C3A)
private val TmTextLight  = Color(0xFFB08A60)
private val TmCard       = Color(0xFFFFFFFF)
private val TmBorder     = Color(0xFFEDD9C0)

@Composable
fun TrueOrMythScreen(
    vm          : TrueOrMythViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack      : () -> Unit,
    onOpenSetup : () -> Unit = {}
) {
    val inf = rememberInfiniteTransition(label = "tmBg")
    val pulse by inf.animateFloat(
        initialValue  = 0.85f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "tmPulse"
    )
    val orbPulse by inf.animateFloat(
        initialValue  = 0.90f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "orbP"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(TmBg, TmBgMid, TmBgDeep))
            )
    ) {
        // Warm ambient glows
        Box(
            modifier = Modifier.size(320.dp).align(Alignment.TopEnd)
                .offset(80.dp, (-80).dp)
                .background(Brush.radialGradient(listOf(TmGlow.copy(0.18f), Color.Transparent)))
        )
        Box(
            modifier = Modifier.size(260.dp).align(Alignment.BottomStart)
                .offset((-60).dp, 80.dp)
                .background(Brush.radialGradient(listOf(TmAccentSoft.copy(0.12f), Color.Transparent)))
        )

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding()
        ) {
            // ── Top bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(TmCard)
                        .border(1.dp, TmBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint               = TmText,
                        modifier           = Modifier.size(18.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(0.8.dp, TmBorder.copy(0.5f), CircleShape)
                    ) {
                        androidx.compose.foundation.Image(
                            painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_true_myth),
                            contentDescription = null,
                            modifier     = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Text(
                        "True or Myth",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color         = TmText,
                            fontWeight    = FontWeight.ExtraBold,
                            fontSize      = 17.sp,
                            letterSpacing = 0.2.sp
                        )
                    )
                    Text(
                        "Day 3 activity",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = TmTextLight,
                            fontSize = 10.sp
                        )
                    )
                }

                Box(modifier = Modifier.size(42.dp)) // Spacer
            }

            // Warm divider
            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, TmBorder, TmAccentSoft.copy(0.50f), TmBorder, Color.Transparent)
                        )
                    )
            )

            // ── Coming Soon body ───────────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier            = Modifier.padding(horizontal = 36.dp)
                ) {
                    // Pulsing orb
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(orbPulse)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(TmAccent.copy(0.15f), TmAccentSoft.copy(0.06f), Color.Transparent)
                                )
                            )
                            .border(1.5.dp, TmAccentSoft.copy(0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔮", fontSize = 38.sp)
                    }

                    // Title
                    Text(
                        "Coming Soon",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color         = TmText,
                            textAlign     = TextAlign.Center,
                            fontWeight    = FontWeight.ExtraBold,
                            fontSize      = 30.sp,
                            letterSpacing = (-0.5).sp
                        )
                    )

                    // Subtitle
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "True or Myth will unlock here\nonce we're ready to reveal.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color      = TmTextSub,
                                textAlign  = TextAlign.Center,
                                lineHeight = 24.sp,
                                fontSize   = 15.sp
                            )
                        )
                        Text(
                            "It's going to be worth the wait ✦",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color    = TmAccent,
                                fontSize = 13.sp
                            )
                        )
                    }

                    // Teaser card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(TmCard)
                            .border(1.dp, TmBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "What to expect",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color         = TmTextLight,
                                    fontSize      = 9.sp,
                                    letterSpacing = 1.2.sp,
                                    fontWeight    = FontWeight.SemiBold
                                )
                            )
                            listOf(
                                "✦  Write your own truths and myths",
                                "✦  Your circle guesses which is which",
                                "✦  Reveal together — surprise each other"
                            ).forEach { item ->
                                Text(
                                    item,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color      = TmTextSub,
                                        lineHeight = 20.sp,
                                        fontSize   = 13.sp
                                    )
                                )
                            }
                        }
                    }

                    // Back button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.60f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TmCard)
                            .border(1.dp, TmBorder, RoundedCornerShape(16.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "← Back to island",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color      = TmTextSub,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
