package com.ns.findyourintroverfriend.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

// ── Colors ─────────────────────────────────────────────────────────────────
private val SBg      = Color(0xFF0D0D0D)
private val SCard    = Color(0xFF1A1A1A)
private val SAccent  = Color(0xFF7C6AF7)   // soft purple
private val SGreen   = Color(0xFF22C55E)
private val SRed     = Color(0xFFFF6B6B)
private val SText    = Color(0xFFFFFFFF)
private val SSubtext = Color(0xFF9CA3AF)

@Composable
fun SyncGameScreen(
    matchId      : String,
    partnerAlias : String = "Partner",
    onBack       : () -> Unit,
    vm           : SyncGameViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(matchId) { vm.startGame(matchId, partnerAlias) }

    // Auto-advance after reveal
    LaunchedEffect(state.phase, state.round) {
        if (state.phase == SyncPhase.REVEALING && !state.gameOver) {
            delay(2800)
            vm.nextRound()
        }
    }

    Box(Modifier.fillMaxSize().background(SBg).systemBarsPadding()) {
        when {
            state.loading -> SyncLoading()
            state.gameOver -> SyncEndScreen(state, onBack, onPlayAgain = { vm.resetGame() })
            else -> SyncGameContent(state, onBack, vm)
        }
    }
}

// ── Main game content ───────────────────────────────────────────────────────
@Composable
private fun SyncGameContent(
    state  : SyncGameState,
    onBack : () -> Unit,
    vm     : SyncGameViewModel
) {
    val question = SYNC_QUESTIONS.getOrNull(state.round)
        ?: SYNC_QUESTIONS.last()

    Column(Modifier.fillMaxSize()) {

        // ── Header ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, null, tint = SSubtext, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sync", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SText)
                Text(
                    "Round ${state.round + 1} of ${state.totalRounds}",
                    fontSize = 11.sp, color = SSubtext
                )
            }
            // Score badge
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(SAccent.copy(0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "⚡ ${state.score}",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SAccent
                )
            }
        }

        // ── Progress bar ────────────────────────────────────────────────────
        val progress by animateFloatAsState(
            targetValue   = (state.round.toFloat() / state.totalRounds),
            animationSpec = tween(600),
            label         = "progress"
        )
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .height(4.dp).clip(RoundedCornerShape(2.dp)).background(SCard)
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SAccent)
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── Question card ───────────────────────────────────────────────────
        AnimatedContent(
            targetState   = state.round,
            transitionSpec = { slideInVertically { it } + fadeIn() togetherWith
                               slideOutVertically { -it } + fadeOut() },
            label          = "question"
        ) { round ->
            val q = SYNC_QUESTIONS.getOrNull(round) ?: SYNC_QUESTIONS.last()
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(24.dp)).background(SCard)
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    q.text,
                    fontSize    = 22.sp,
                    fontWeight  = FontWeight.Bold,
                    color       = SText,
                    textAlign   = TextAlign.Center,
                    lineHeight  = 32.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Phase: CHOOSING or WAITING_PARTNER ──────────────────────────────
        AnimatedContent(
            targetState   = state.phase,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label          = "phase"
        ) { phase ->
            when (phase) {
                SyncPhase.CHOOSING -> {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        question.options.forEach { option ->
                            SyncOptionButton(text = option, onClick = { vm.submitAnswer(option) })
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                SyncPhase.WAITING_PARTNER -> {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val dots = rememberInfiniteTransition(label = "dots")
                            val alpha by dots.animateFloat(0.3f, 1f,
                                infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
                            Text("⏳", fontSize = 36.sp, modifier = Modifier.alpha(alpha))
                            Text("Waiting for ${state.partnerAlias}…",
                                fontSize = 14.sp, color = SSubtext, textAlign = TextAlign.Center)
                            Text("Your answer is locked in ✓",
                                fontSize = 12.sp, color = SGreen)
                        }
                    }
                }
                SyncPhase.REVEALING -> {
                    SyncRevealPanel(state)
                }
                SyncPhase.NEXT_ROUND -> {
                    Box(Modifier.fillMaxWidth().padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Next question →", fontSize = 14.sp, color = SSubtext)
                    }
                }
            }
        }
    }
}

// ── Option button ───────────────────────────────────────────────────────────
@Composable
private fun SyncOptionButton(text: String, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "sc"
    )

    Box(
        Modifier.fillMaxWidth().scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(SCard)
            .border(1.5.dp, SAccent.copy(0.35f), RoundedCornerShape(16.dp))
            .clickable {
                pressed = true
                onClick()
            }
            .padding(vertical = 18.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = SText, textAlign = TextAlign.Center)
    }

    LaunchedEffect(pressed) {
        if (pressed) { delay(150); pressed = false }
    }
}

// ── Reveal panel ────────────────────────────────────────────────────────────
@Composable
private fun SyncRevealPanel(state: SyncGameState) {
    val synced = state.synced
    val bgColor = if (synced) SGreen else SRed

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sync / No-sync badge
        val scale = remember { Animatable(0.5f) }
        LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) }
        Box(
            Modifier.scale(scale.value).clip(RoundedCornerShape(20.dp))
                .background(bgColor.copy(0.18f))
                .border(1.5.dp, bgColor.copy(0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                if (synced) "⚡ SYNC! +1 point" else "❌ No match",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = bgColor
            )
        }

        // Both answers side by side
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnswerCard(label = "You", answer = if (state.isPlayerA) state.revealA else state.revealB,
                modifier = Modifier.weight(1f), highlight = synced)
            AnswerCard(label = state.partnerAlias, answer = if (state.isPlayerA) state.revealB else state.revealA,
                modifier = Modifier.weight(1f), highlight = synced)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AnswerCard(
    label     : String,
    answer    : String?,
    modifier  : Modifier = Modifier,
    highlight : Boolean
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (highlight) SGreen.copy(0.12f) else SCard)
            .border(1.dp, if (highlight) SGreen.copy(0.4f) else Color.White.copy(0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontSize = 11.sp, color = SSubtext, fontWeight = FontWeight.Medium)
        Text(
            answer ?: "…",
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (highlight) SGreen else SText,
            textAlign  = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

// ── End screen ──────────────────────────────────────────────────────────────
@Composable
private fun SyncEndScreen(
    state      : SyncGameState,
    onBack     : () -> Unit,
    onPlayAgain: () -> Unit
) {
    val pct = (state.score * 100) / state.totalRounds.coerceAtLeast(1)
    val emoji = when {
        pct >= 80 -> "🔥"
        pct >= 60 -> "💫"
        pct >= 40 -> "🌙"
        else      -> "🌊"
    }
    val message = when {
        pct >= 80 -> "Twin souls! You think alike."
        pct >= 60 -> "Great connection! You vibe well."
        pct >= 40 -> "Interesting differences. That's what makes it fun."
        else      -> "Opposites attract! You're unique together."
    }

    val scale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 64.sp, modifier = Modifier.scale(scale.value))
        Spacer(Modifier.height(16.dp))
        Text("Sync Complete!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SText)
        Spacer(Modifier.height(8.dp))

        // Score ring
        Box(
            Modifier.size(140.dp).clip(CircleShape)
                .background(SAccent.copy(0.15f))
                .border(3.dp, SAccent.copy(0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$pct%", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = SAccent)
                Text("Synced", fontSize = 11.sp, color = SSubtext)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "${state.score} / ${state.totalRounds} matched",
            fontSize = 15.sp, color = SSubtext
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            fontSize = 16.sp, color = SText, textAlign = TextAlign.Center, lineHeight = 24.sp
        )

        Spacer(Modifier.height(40.dp))

        // Play again
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(SAccent).clickable(onClick = onPlayAgain)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Play Again ⚡", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(SCard).clickable(onClick = onBack)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Back", fontSize = 15.sp, color = SSubtext)
        }
    }
}

// ── Loading ─────────────────────────────────────────────────────────────────
@Composable
private fun SyncLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val inf = rememberInfiniteTransition(label = "load")
            val alpha by inf.animateFloat(0.3f, 1f,
                infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
            Text("⚡", fontSize = 48.sp, modifier = Modifier.alpha(alpha))
            Text("Setting up Sync…", fontSize = 15.sp, color = SSubtext)
        }
    }
}
