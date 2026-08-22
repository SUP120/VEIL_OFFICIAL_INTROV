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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val RBg      = Color(0xFFF7F5F0)
private val RCard    = Color(0xFFFFFFFF)
private val RBorder  = Color(0xFFEDE8DF)
private val RAccent  = Color(0xFF7C6AE8)
private val RSub     = Color(0xFF8A8480)
private val RMuted   = Color(0xFFBBB5AC)
private val RPrimary = Color(0xFF1C1916)
private val RPill    = Color(0xFFF0EBE3)
private val RGreen   = Color(0xFF2E9E6A)
private val RGold    = Color(0xFFE89820)

@Composable
fun DrawGuessResultScreen(
    roomId  : String,
    matchId : String,
    vm      : DrawGuessViewModel = viewModel(),
    onBack  : () -> Unit,
    onPlayAgain: (roomId: String, matchId: String) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val room  = state.room

    LaunchedEffect(roomId) { if (state.myUid.isEmpty()) vm.init(roomId) }

    if (room == null) {
        Box(Modifier.fillMaxSize().background(RBg), Alignment.Center) {
            CircularProgressIndicator(color = RAccent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        return
    }

    val scores = room.scores
    val sortedPlayers = scores.entries.sortedByDescending { it.value }
    val winnerId = sortedPlayers.firstOrNull()?.key ?: ""
    val isDraw   = sortedPlayers.size >= 2 && sortedPlayers[0].value == sortedPlayers[1].value
    val iWon     = winnerId == state.myUid && !isDraw

    // Confetti animation
    val inf  = rememberInfiniteTransition(label = "conf")
    val confX by inf.animateFloat(-20f, 20f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "cx")

    Column(
        Modifier.fillMaxSize().background(RBg).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(RCard)
                .border(BorderStroke(0.5.dp, RBorder))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.leaveRoom(); onBack() }) {
                Icon(Icons.Rounded.ArrowBack, null, tint = RPrimary, modifier = Modifier.size(20.dp))
            }
            Text("Draw & Guess · Results", fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold, color = RPrimary,
                modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(32.dp))

        // Trophy
        Text(
            text = when {
                isDraw -> "🤝"
                iWon   -> "🏆"
                else   -> "🎨"
            },
            fontSize = 72.sp,
            modifier = Modifier.offset(x = if (iWon) confX.dp else 0.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = when {
                isDraw -> "It's a Draw!"
                iWon   -> "You Won! 🎉"
                else   -> "${
                    if (winnerId == room.hostUid) room.hostAlias else room.guestAlias
                } Wins!"
            },
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                isDraw -> RGold
                iWon   -> RGreen
                else   -> RPrimary
            },
            textAlign = TextAlign.Center
        )

        Text("${room.maxRounds} rounds · ${room.category.replaceFirstChar { it.uppercase() }}",
            fontSize = 13.sp, color = RSub, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(28.dp))

        // Score cards
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sortedPlayers.forEachIndexed { idx, (uid, pts) ->
                val alias   = if (uid == room.hostUid) room.hostAlias else room.guestAlias
                val isWinner = idx == 0 && !isDraw
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isWinner) RGreen.copy(0.08f) else RCard)
                        .border(1.5.dp,
                            if (isWinner) RGreen.copy(0.4f) else RBorder,
                            RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Rank circle
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (isWinner) RGreen.copy(0.15f) else RPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isWinner) "🥇" else "🥈", fontSize = 22.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(alias, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = RPrimary)
                        Text(if (uid == state.myUid) "You" else "Opponent",
                            fontSize = 11.sp, color = RSub)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$pts", fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isWinner) RGreen else RPrimary)
                        Text("points", fontSize = 10.sp, color = RMuted)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Scoring legend
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RPill)
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SCORING", fontSize = 9.sp, color = RMuted,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                listOf(
                    "Guess in 0–20s" to "+3 pts",
                    "Guess in 20–50s" to "+2 pts",
                    "Guess in 50–80s" to "+1 pt",
                    "Drawer (if guessed)" to "+1 pt"
                ).forEach { (label, pts) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 11.sp, color = RSub)
                        Text(pts, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RPrimary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        val isHost = room.hostUid == state.myUid
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isHost) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(RAccent)
                        .clickable { vm.playAgain(); onPlayAgain(roomId, matchId) }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Replay, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Play Again", fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(RPill)
                    .border(1.dp, RBorder, RoundedCornerShape(14.dp))
                    .clickable { vm.leaveRoom(); onBack() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Chat, null, tint = RSub, modifier = Modifier.size(18.dp))
                    Text("Back to Chat", fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = RSub)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
