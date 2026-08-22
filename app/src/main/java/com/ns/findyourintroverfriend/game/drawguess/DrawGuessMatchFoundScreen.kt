package com.ns.findyourintroverfriend.game.drawguess

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

private val MBg     = Color(0xFFF7F5F0)
private val MCard   = Color(0xFFFFFFFF)
private val MAccent = Color(0xFF7C6AE8)
private val MRose   = Color(0xFFEC4899)
private val MPrimary= Color(0xFF1C1916)
private val MSub    = Color(0xFF8A8480)
private val MPill   = Color(0xFFF0EBE3)
private val MGold   = Color(0xFFE89820)

private val avatarColors = listOf(
    Color(0xFF7C6AE8), Color(0xFFEC4899), Color(0xFF0EA5E9),
    Color(0xFF10B981), Color(0xFFE89820), Color(0xFFEF4444)
)
private fun avatarBg(uid: String) =
    avatarColors[kotlin.math.abs(uid.hashCode()) % avatarColors.size]

@Composable
fun DrawGuessMatchFoundScreen(
    hostUid    : String,
    hostAlias  : String,
    guestUid   : String,
    guestAlias : String,
    category   : String,
    onReady    : () -> Unit   // called after countdown → navigate to game
) {
    var countdown by remember { mutableStateOf(3) }
    var phase     by remember { mutableStateOf("enter") }  // enter | countdown | go

    // Entrance → then countdown
    LaunchedEffect(Unit) {
        delay(700)
        phase = "countdown"
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        phase = "go"
        delay(500)
        onReady()
    }

    // Heartbeat animation for the ❤️
    val heartbeat = rememberInfiniteTransition(label = "hb")
    val heartScale by heartbeat.animateFloat(
        1f, 1.25f,
        infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "hs"
    )

    // Gentle glow behind avatars
    val glow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glow.animateFloat(
        0.15f, 0.35f,
        infiniteRepeatable(tween(1200), RepeatMode.Reverse), "ga"
    )

    Box(
        Modifier.fillMaxSize().background(MBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        ) {
            // Category pill
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(MAccent.copy(0.1f))
                    .border(1.dp, MAccent.copy(0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "✏️ ${category.replaceFirstChar { it.uppercase() }} · Draw The Feeling",
                    fontSize = 12.sp, color = MAccent, fontWeight = FontWeight.SemiBold
                )
            }

            // Avatars + heart row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Host avatar
                PlayerAvatar(uid = hostUid, alias = hostAlias, glowAlpha = glowAlpha)

                // Heart
                Text("❤️", fontSize = 32.sp,
                    modifier = Modifier.scale(heartScale))

                // Guest avatar
                PlayerAvatar(uid = guestUid, alias = guestAlias, glowAlpha = glowAlpha)
            }

            // "Drawing together" text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "You and $guestAlias are drawing together",
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = MPrimary, textAlign = TextAlign.Center
                )
                Text(
                    "No pressure. Just vibes. 🎨",
                    fontSize = 13.sp, color = MSub, textAlign = TextAlign.Center
                )
            }

            // Countdown / Go!
            when (phase) {
                "countdown" -> {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape)
                            .background(MAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$countdown",
                            fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
                "go" -> {
                    Text(
                        "🎨 LET'S GO!",
                        fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                        color = MAccent
                    )
                }
                else -> {
                    // Loading indicator during entrance
                    Box(
                        Modifier.size(80.dp).clip(CircleShape)
                            .background(MPill),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Fun banter chips
            if (phase == "countdown" || phase == "go") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Ready? 😅", "Let's see who draws worse 😭").forEach { msg ->
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(MCard)
                                .border(1.dp, Color(0xFFEDE8DF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(msg, fontSize = 12.sp, color = MSub)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerAvatar(uid: String, alias: String, glowAlpha: Float) {
    val color = avatarBg(uid)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            // Glow ring
            Box(Modifier.size(80.dp).clip(CircleShape).background(color.copy(glowAlpha)))
            // Avatar
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                )
            }
        }
        Text(alias, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MPrimary)
    }
}
