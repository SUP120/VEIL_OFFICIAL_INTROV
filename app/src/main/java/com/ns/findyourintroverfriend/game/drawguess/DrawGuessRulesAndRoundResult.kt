package com.ns.findyourintroverfriend.game.drawguess

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.unit.*

private val RuBg      = Color(0xFFF7F5F0)
private val RuCard    = Color(0xFFFFFFFF)
private val RuBorder  = Color(0xFFEDE8DF)
private val RuAccent  = Color(0xFF7C6AE8)
private val RuPrimary = Color(0xFF1C1916)
private val RuSub     = Color(0xFF8A8480)
private val RuPill    = Color(0xFFF0EBE3)
private val RuGold    = Color(0xFFE89820)
private val RuGreen   = Color(0xFF2E9E6A)

// ── Rules Popup ───────────────────────────────────────────────────────────────
@Composable
fun DrawGuessRulesPopup(
    rounds   : Int,
    category : String,
    onStart  : () -> Unit
) {
    val scale = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .scale(scale.value)
                .clip(RoundedCornerShape(24.dp))
                .background(RuCard)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🎨", fontSize = 48.sp)
            Text(
                "Draw The Feeling",
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = RuPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                "${rounds} rounds · ${category.replaceFirstChar { it.uppercase() }}",
                fontSize = 13.sp, color = RuSub
            )

            HorizontalDivider(color = RuBorder)

            // Rules
            val rules = listOf(
                "✏️" to "Draw emotions & feelings — no letters!",
                "💭" to "Guess correctly to earn points",
                "⚡" to "Guess fast (under 15s) = bonus points",
                "😂" to "React with emojis during drawing",
                "🔁" to "Players swap drawer & guesser each round"
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rules.forEach { (emoji, text) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 18.sp)
                        Text(text, fontSize = 13.sp, color = RuPrimary)
                    }
                }
            }

            HorizontalDivider(color = RuBorder)

            // Scoring
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RuPill)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("SCORING", fontSize = 10.sp, color = RuSub,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                listOf(
                    "Correct guess" to "+10 pts",
                    "Under 15 seconds" to "+5 bonus",
                    "Drawer (if guessed)" to "+2 pts",
                    "Emoji reaction received" to "+2 pts"
                ).forEach { (label, pts) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 12.sp, color = RuSub)
                        Text(pts, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RuAccent)
                    }
                }
            }

            // Start button
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(RuAccent, Color(0xFF9F5CF0))))
                    .clickable(onClick = onStart)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Let's Draw! 🚀",
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

// ── Round Result Screen ───────────────────────────────────────────────────────
@Composable
fun DrawGuessRoundResultScreen(
    round          : Int,
    maxRounds      : Int,
    correctWord    : String,
    guessedCorrectly: Boolean,
    drawerAlias    : String,
    guesserAlias   : String,
    guesserTime    : Long,      // ms taken to guess
    drawerStareMs  : Long,      // fun stat: how long guesser looked before guessing
    hostUid        : String,
    myUid          : String,
    isLastRound    : Boolean,
    onNext         : () -> Unit  // only host can call
) {
    val emoji = if (guessedCorrectly) "✅" else "⏰"
    val title = if (guessedCorrectly) "Correct!" else "Time's up!"
    val guessedSec = (guesserTime / 1000).toInt()
    val isHost = myUid == hostUid

    // Pop-in animation
    val popScale = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        popScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .scale(popScale.value)
                .clip(RoundedCornerShape(24.dp))
                .background(RuCard)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Round badge
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(RuPill)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Round $round / $maxRounds",
                    fontSize = 11.sp, color = RuSub, fontWeight = FontWeight.Bold)
            }

            Text(emoji, fontSize = 52.sp)
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = RuPrimary)

            // Correct word reveal
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (guessedCorrectly) RuGreen.copy(0.08f) else Color(0xFFFFEEEE))
                    .border(1.dp,
                        if (guessedCorrectly) RuGreen.copy(0.3f) else Color(0xFFFFCCCC),
                        RoundedCornerShape(14.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("The word was", fontSize = 11.sp, color = RuSub)
                    Text(
                        correctWord.replaceFirstChar { it.uppercase() },
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (guessedCorrectly) RuGreen else Color(0xFFD94F4F)
                    )
                }
            }

            // Funny stats
            if (guessedCorrectly && guesserTime > 0) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RuPill)
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FUN STATS", fontSize = 9.sp, color = RuSub,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$guesserAlias guessed in", fontSize = 12.sp, color = RuSub)
                            Text(
                                "${guessedSec}s ⚡",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (guessedSec < 15) RuGold else RuPrimary
                            )
                        }
                        if (guessedSec < 15) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Speed bonus", fontSize = 12.sp, color = RuSub)
                                Text("+5 pts 🔥", fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, color = RuGold)
                            }
                        }
                    }
                }
            }

            // Points earned
            if (guessedCorrectly) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RuGreen.copy(0.08f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (guessedSec < 15) "+15 pts total 🎉" else "+10 pts 🎉",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RuGreen
                    )
                }
            }

            HorizontalDivider(color = RuBorder)

            // Next round / Results button (host only)
            if (isHost) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RuAccent)
                        .clickable(onClick = onNext)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isLastRound) "See Final Results 🏆" else "Next Round →",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = RuAccent, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
                    Text("Waiting for ${if (myUid == hostUid) "you" else drawerAlias} to continue…",
                        fontSize = 12.sp, color = RuSub)
                }
            }
        }
    }
}
