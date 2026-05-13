package com.ns.findyourintroverfriend.founders

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
//  Palette — 2 rich colours only: deep dark background + warm gold
// ─────────────────────────────────────────────────────────────────────────────

private val FBg      = Color(0xFF09080E)        // deep dark
private val FCard    = Color(0xFF13111C)        // card surface
private val FBorder  = Color(0xFF242033)        // subtle border
private val FGold    = Color(0xFFD4A853)        // warm rich gold — primary accent
private val FGoldSft = Color(0x22D4A853)        // gold tint fills
private val FGoldMid = Color(0xFFB8912A)        // deeper gold for gradient end
private val FText    = Color(0xFFF2EED8)        // warm cream text
private val FMuted   = Color(0xFF7A7660)        // muted warm grey

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

private data class Founder(val name: String, val badge: String, val number: Int)

private val FOUNDERS = listOf(
    Founder("Jenny Joe",  "First Believer",  1),
    Founder("Jinny",      "Early Seed",      2),
    Founder("Sanchita",   "Night Bloom",     3),
    Founder("Shanmuk",    "The Spark",       4),
    Founder("Smritiii",   "Stardust",        5),
)

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FoundersScreen(onBack: () -> Unit = {}) {
    val scroll     = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val inf        = rememberInfiniteTransition(label = "founders")

    // Orbiting egg — X and Y offsets trace a small circle
    val orbitAngle by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Restart), "orbit"
    )
    val eggX = 18f * cos(orbitAngle)
    val eggY = 10f * sin(orbitAngle)   // flattened ellipse — looks natural on screen

    // Egg happy scale bounce
    val eggScale by inf.animateFloat(
        0.90f, 1.10f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), "escale"
    )

    // Gold glow pulse
    val glow by inf.animateFloat(
        0.30f, 0.90f,
        infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FBg)
    ) {
        // Subtle gold radial glow behind the egg area
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopCenter)
                .offset(y = 40.dp)
                .background(
                    Brush.radialGradient(
                        listOf(FGold.copy(0.08f * glow), Color.Transparent),
                        radius = 400f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Rounded.ArrowBack, contentDescription = "Back",
                    tint     = FMuted,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onBack() }
                        .padding(8.dp)
                        .align(Alignment.CenterStart)
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Orbiting Happy Star Mascot ────────────────────────────────────
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Faint orbit track ring
                Canvas(modifier = Modifier.size(120.dp)) {
                    val cx = size.width / 2f; val cy = size.height / 2f
                    drawOval(
                        color   = FGold.copy(0.10f),
                        topLeft = androidx.compose.ui.geometry.Offset(cx - 46f, cy - 22f),
                        size    = androidx.compose.ui.geometry.Size(92f, 44f),
                        style   = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f)
                    )
                }
                // Star mascot — Canvas drawn, orbiting
                Canvas(
                    modifier = Modifier
                        .size(72.dp)
                        .offset(x = eggX.dp, y = eggY.dp)
                        .scale(eggScale)
                ) {
                    val cx  = size.width / 2f
                    val cy  = size.height / 2f + 4f   // nudge slightly down for visual balance
                    val outerR = size.minDimension * 0.46f
                    val innerR = outerR * 0.42f
                    val numPts = 5
                    val startAngle = (-Math.PI / 2).toFloat()

                    // ── Build star path ─────────────────────────────────────
                    val starPath = androidx.compose.ui.graphics.Path()
                    for (i in 0 until numPts * 2) {
                        val angle = startAngle + i * (Math.PI / numPts).toFloat()
                        val r     = if (i % 2 == 0) outerR else innerR
                        val px    = cx + r * cos(angle)
                        val py    = cy + r * sin(angle)
                        if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
                    }
                    starPath.close()

                    // Glow halo
                    drawCircle(
                        brush  = Brush.radialGradient(
                            listOf(FGold.copy(0.35f * glow), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            radius = outerR * 1.5f
                        ),
                        radius = outerR * 1.5f,
                        center = androidx.compose.ui.geometry.Offset(cx, cy)
                    )

                    // Star body — gold gradient fill
                    drawPath(
                        path  = starPath,
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFE680), FGold, FGoldMid),
                            center = androidx.compose.ui.geometry.Offset(cx - outerR * 0.1f, cy - outerR * 0.2f),
                            radius = outerR * 1.2f
                        )
                    )
                    // Star outline
                    drawPath(
                        path  = starPath,
                        color = FGoldMid.copy(0.60f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f)
                    )

                    // ── Happy face ───────────────────────────────────────────
                    val faceR = outerR * 0.36f

                    // Eyes
                    val eyeY  = cy - faceR * 0.22f
                    val eyeOff = faceR * 0.36f
                    drawCircle(Color(0xFF3A2800), radius = faceR * 0.10f,
                        center = androidx.compose.ui.geometry.Offset(cx - eyeOff, eyeY))
                    drawCircle(Color(0xFF3A2800), radius = faceR * 0.10f,
                        center = androidx.compose.ui.geometry.Offset(cx + eyeOff, eyeY))

                    // Rosy cheeks
                    drawCircle(Color(0xFFFF8FAB).copy(0.40f), radius = faceR * 0.22f,
                        center = androidx.compose.ui.geometry.Offset(cx - eyeOff * 1.2f, cy + faceR * 0.10f))
                    drawCircle(Color(0xFFFF8FAB).copy(0.40f), radius = faceR * 0.22f,
                        center = androidx.compose.ui.geometry.Offset(cx + eyeOff * 1.2f, cy + faceR * 0.10f))

                    // Smile arc
                    drawArc(
                        color       = Color(0xFF3A2800),
                        startAngle  = 10f,
                        sweepAngle  = 160f,
                        useCenter   = false,
                        topLeft     = androidx.compose.ui.geometry.Offset(cx - faceR * 0.45f, cy - faceR * 0.05f),
                        size        = androidx.compose.ui.geometry.Size(faceR * 0.90f, faceR * 0.55f),
                        style       = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.2f,
                            cap   = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Title ─────────────────────────────────────────────────────────
            Text(
                "The Founding Circle",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = FGold,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // Small subtitle pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(FGoldSft)
                    .border(0.6.dp, FGold.copy(0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    "First 5 souls who believed in Veil",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FGold.copy(0.75f),
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    )
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Thank-you card ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(FCard)
                    .border(
                        0.8.dp,
                        Brush.verticalGradient(listOf(FGold.copy(0.35f), FGold.copy(0.08f))),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("✦", fontSize = 16.sp, color = FGold.copy(glow))
                Text(
                    "Before Veil had its first match, before the very first Day 1 began — " +
                    "these five wonderful people stepped forward and believed in what this " +
                    "little app could become.\n\n" +
                    "They didn't wait for proof. They brought the magic first.\n\n" +
                    "To you, the Founding Circle — from the bottom of our hearts: " +
                    "thank you. Veil exists because you believed. ❤️",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color      = FText.copy(0.80f),
                        fontSize   = 13.sp,
                        lineHeight = 21.sp,
                        textAlign  = TextAlign.Center
                    ),
                    textAlign = TextAlign.Center
                )
                Text("✦", fontSize = 16.sp, color = FGold.copy(glow))
            }

            Spacer(Modifier.height(28.dp))

            // ── Section divider ───────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.padding(horizontal = 24.dp)
            ) {
                Box(Modifier.weight(1f).height(0.5.dp).background(
                    Brush.horizontalGradient(listOf(Color.Transparent, FBorder))
                ))
                Text(
                    "FOUNDING MEMBERS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FMuted, fontSize = 9.sp, letterSpacing = 2.5.sp
                    )
                )
                Box(Modifier.weight(1f).height(0.5.dp).background(
                    Brush.horizontalGradient(listOf(FBorder, Color.Transparent))
                ))
            }

            Spacer(Modifier.height(14.dp))

            // ── Member rows ───────────────────────────────────────────────────
            FOUNDERS.forEachIndexed { index, founder ->
                FounderRow(founder = founder, glow = glow)
                if (index < FOUNDERS.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                            .height(0.5.dp)
                            .background(FBorder)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── "Want your name here?" block ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(FCard)
                    .border(0.6.dp, FBorder, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Want your name here?",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color      = FText,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp
                    )
                )
                Text(
                    "Share Veil with someone who deserves to find their circle. " +
                    "Every connection you spark brings you closer to this list. ✨",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color     = FMuted,
                        fontSize  = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                // Instagram handles
                Text(
                    "Follow us on Instagram",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FMuted.copy(0.60f),
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    InstagramChip(
                        handle  = "@veil.bonds",
                        url     = "https://instagram.com/veil.bonds",
                        glow    = glow
                    )
                    InstagramChip(
                        handle  = "@tenex.galaxy",
                        url     = "https://instagram.com/tenex.galaxy",
                        glow    = glow
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Founder row — leaderboard style
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FounderRow(founder: Founder, glow: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Rank number
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(FGoldSft)
                .border(0.8.dp, FGold.copy(0.30f * glow), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "#${founder.number}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = FGold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            )
        }

        // Name + badge
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                founder.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color      = FText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
            )
            Text(
                founder.badge,
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = FMuted,
                    fontSize = 10.sp
                )
            )
        }

        // Gold star
        Text("★", fontSize = 14.sp, color = FGold.copy(0.60f + 0.40f * glow))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Instagram chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InstagramChip(handle: String, url: String, glow: Float) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FGoldSft)
            .border(0.6.dp, FGold.copy(0.25f + 0.15f * glow), RoundedCornerShape(10.dp))
            .clickable {
                runCatching { uriHandler.openUri(url) }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            handle,
            style = MaterialTheme.typography.labelSmall.copy(
                color      = FGold,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 11.sp
            )
        )
    }
}
