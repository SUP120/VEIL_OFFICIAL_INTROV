package com.ns.findyourintroverfriend.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ns.findyourintroverfriend.R

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward

private val SineEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

// ── Design tokens ─────────────────────────────────────────────────────────────
private val GBg       = Color(0xFFFFFFFF)
private val GBlack    = Color(0xFF111111)
private val GGray     = Color(0xFF8A8A8A)
private val GLight    = Color(0xFFF5F5F5)
private val GBorder   = Color(0xFFE8E8E8)
private val GLive     = Color(0xFF22C55E)   // green live dot

// Games that are planned (shown as teasers)
private val plannedGames = listOf(
    Triple("🎯", "Truth or Dare",     "Dare to be real"),
    Triple("🃏", "Would You Rather",  "Make the hard choices"),
    Triple("🔤", "Word Chain",        "Battle of words"),
    Triple("🎭", "Guess My Vibe",     "Can you read me?"),
    Triple("🧠", "Personality Duel",  "Who knows who better?"),
    Triple("🎲", "Spin the Question", "Luck meets depth"),
)

@Composable
fun GamesComingSoonScreen(
    modifier            : Modifier = Modifier,
    onOpenListeningRoom : () -> Unit = {}
) {
    val inf = rememberInfiniteTransition(label = "pulse")

    // Controller icon gentle float animation
    val floatAnim by inf.animateFloat(
        initialValue = -6f,
        targetValue  = 6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = SineEasing), RepeatMode.Reverse),
        label = "float"
    )

    // Glow pulse
    val glowAnim by inf.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.7f,
        animationSpec = infiniteRepeatable(tween(1400, easing = SineEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // ── Floating controller icon ──────────────────────────────────────────
        Box(contentAlignment = Alignment.Center) {
            // Glow ring behind icon
            Box(
                Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(GLight)
                    .alpha(glowAnim)
            )
            Box(
                Modifier
                    .offset(y = floatAnim.dp)
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(GLight)
                    .border(1.5.dp, GBorder, CircleShape)
                    .padding(20.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_nav_gamepad),
                    contentDescription = "Games",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Title ─────────────────────────────────────────────────────────────
        Text(
            "Games",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = GBlack,
            letterSpacing = (-1).sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Play together. Break the ice.\nReal fun, real connections.",
            fontSize = 14.sp,
            color = GGray,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(32.dp))

        // ── 🎵 LIVE: Shared Listening Room card ───────────────────────────────
        ListeningRoomCard(onClick = onOpenListeningRoom)

        Spacer(Modifier.height(28.dp))

        // ── Coming Soon badge ────────────────────────────────────────────────
        Box(
            Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(GBlack)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                "Coming Soon 🎮",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                letterSpacing = 0.3.sp
            )
        }

        Spacer(Modifier.height(36.dp))

        // ── Section heading ───────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HorizontalDivider(Modifier.weight(1f), color = GBorder)
            Text("Games we're building", fontSize = 11.sp, color = GGray, fontWeight = FontWeight.Medium)
            HorizontalDivider(Modifier.weight(1f), color = GBorder)
        }

        Spacer(Modifier.height(16.dp))

        // ── Game cards grid ───────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plannedGames.chunked(2).forEachIndexed { rowIdx, rowGames ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowGames.forEach { (emoji, name, desc) ->
                        GameTeaser(
                            emoji = emoji,
                            name  = name,
                            desc  = desc,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill empty slot if odd
                    if (rowGames.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        // ── Notify me section ──────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GLight)
                .border(1.dp, GBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔔", fontSize = 28.sp)
                Text(
                    "We'll notify you when games launch",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GBlack,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Make sure notifications are enabled for Veil",
                    fontSize = 11.sp,
                    color = GGray,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun GameTeaser(emoji: String, name: String, desc: String, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "gt")
    val scale by inf.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(2000 + (name.length * 80), easing = SineEasing), RepeatMode.Reverse
        ), label = "gtScale"
    )

    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GLight)
            .border(1.dp, GBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 28.sp, modifier = Modifier.scale(scale))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GBlack, lineHeight = 18.sp)
            Text(desc, fontSize = 11.sp, color = GGray, lineHeight = 16.sp)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(GBorder)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Soon", fontSize = 10.sp, color = GGray, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Listening Room — live feature card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ListeningRoomCard(onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha by inf.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "dotAlpha"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GBlack)
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Live badge
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(GLive.copy(alpha = dotAlpha))
                )
                Text(
                    "LIVE NOW",
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = GLive,
                    letterSpacing = 1.5.sp
                )
            }

            // Title row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🎵", fontSize = 32.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Shared Listening",
                            fontSize   = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color      = androidx.compose.ui.graphics.Color.White
                        )
                        Text(
                            "Listen to music together in sync",
                            fontSize = 12.sp,
                            color    = androidx.compose.ui.graphics.Color.White.copy(0.60f)
                        )
                    }
                }
                Icon(
                    Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint     = androidx.compose.ui.graphics.Color.White.copy(0.70f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Description pill
            Row(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(androidx.compose.ui.graphics.Color.White.copy(0.10f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("🔗", fontSize = 12.sp)
                Text(
                    "Paste a YouTube link · Both hear it in sync",
                    fontSize = 11.sp,
                    color    = androidx.compose.ui.graphics.Color.White.copy(0.65f)
                )
            }
        }
    }
}
