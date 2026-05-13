package com.ns.findyourintroverfriend.game

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.ui.theme.*
import com.ns.findyourintroverfriend.ui.rememberPulseAnimation

// ─────────────────────────────────────────────────────────────────────────────
//  Tic-Tac-Toe Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TicTacToeScreen(
    gameId : String,
    vm     : TicTacToeViewModel = viewModel(),
    onBack : () -> Unit
) {
    LaunchedEffect(gameId) { vm.init(gameId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF060A10), Color(0xFF0B1525), Color(0xFF101F3A))
                )
            )
    ) {
        // Ambient glow effects
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopStart)
                .offset((-80).dp, (-80).dp)
                .background(Brush.radialGradient(listOf(VeilPrimary.copy(0.08f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomEnd)
                .offset(80.dp, 80.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF5BC8F5).copy(0.06f), Color.Transparent)))
        )
        
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val pulse = rememberPulseAnimation(0.9f, 1.1f, 1000)
                    Text(
                        "⚡",
                        fontSize = 48.sp,
                        modifier = Modifier.scale(pulse)
                    )
                    Text(
                        "Loading game...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = VeilTextSecondary
                        )
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Top bar ───────────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(VeilSurface)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = VeilTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Tic-Tac-Toe",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = VeilTextSecondary, letterSpacing = 1.sp
                            )
                        )
                        Text(
                            "vs ${state.opponentEmoji} ${state.opponentAlias}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = VeilTextDisabled, fontSize = 10.sp
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(38.dp)) // balance
                }

                Spacer(Modifier.height(8.dp))

                // ── Turn / status banner ──────────────────────────────────────
                val bannerText = when {
                    state.gameOver && state.winner == "draw"    -> "It's a draw  〜"
                    state.gameOver && state.winner == state.myUid -> "You won  ✦"
                    state.gameOver                              -> "${state.opponentAlias} won"
                    state.currentTurn == state.myUid            -> "Your turn  (${state.mySymbol})"
                    else                                        -> "${state.opponentAlias}'s turn"
                }

                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.85f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "bannerPulse"
                )
                val isMyTurn = !state.gameOver && state.currentTurn == state.myUid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (state.gameOver && state.winner == state.myUid)
                                Brush.horizontalGradient(listOf(VeilPrimary.copy(.4f), VeilPrimaryMid.copy(.4f)))
                            else Brush.horizontalGradient(listOf(Color.White.copy(.04f), Color.White.copy(.04f)))
                        )
                        .border(
                            1.dp,
                            if (isMyTurn) VeilAccentGlow.copy(pulse * .5f)
                            else Color.White.copy(.08f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        bannerText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isMyTurn) VeilAccentGlow else VeilTextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Game board ────────────────────────────────────────────────
                TttBoard(
                    board     = state.board,
                    mySymbol  = state.mySymbol,
                    isMyTurn  = isMyTurn,
                    gameOver  = state.gameOver,
                    onCellClick = { index ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.makeMove(index)
                    }
                )

                // ── Post-game message ─────────────────────────────────────────
                if (state.gameOver) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when {
                            state.winner == "draw"        -> "A balanced mind meets its match."
                            state.winner == state.myUid   -> "Your instincts are sharp. +1 bond point."
                            else                          -> "They outplayed you — still +1 bond point."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = VeilTextTertiary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    )

                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VeilSurface)
                            .border(0.8.dp, VeilDivider, RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBack()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("← Back", style = MaterialTheme.typography.labelMedium.copy(color = VeilTextSecondary))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Board composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TttBoard(
    board       : Board,
    mySymbol    : String,
    isMyTurn    : Boolean,
    gameOver    : Boolean,
    onCellClick : (Int) -> Unit
) {
    // 3×3 grid — square cells
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..2) {
                    val index = row * 3 + col
                    TttCell(
                        symbol    = board[index],
                        mySymbol  = mySymbol,
                        enabled   = isMyTurn && board[index] == null && !gameOver,
                        onClick   = { onCellClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TttCell(
    symbol   : String?,
    mySymbol : String,
    enabled  : Boolean,
    onClick  : () -> Unit
) {
    var justPlaced by remember { mutableStateOf(false) }
    
    LaunchedEffect(symbol) {
        if (symbol != null) {
            justPlaced = true
            kotlinx.coroutines.delay(600)
            justPlaced = false
        }
    }
    
    val scale by animateFloatAsState(
        targetValue   = if (justPlaced) 1.15f else if (symbol != null) 1f else if (enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label         = "cellScale"
    )
    
    val size = 96.dp
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when (symbol) {
                    "X"  -> Brush.radialGradient(listOf(VeilPrimary.copy(.35f), VeilSurface))
                    "O"  -> Brush.radialGradient(listOf(Color(0xFF1A3040).copy(.5f), VeilSurface))
                    else -> Brush.radialGradient(listOf(VeilSurface, VeilSurface))
                }
            )
            .border(
                1.2.dp,
                when (symbol) {
                    "X"  -> VeilAccentGlow.copy(.5f)
                    "O"  -> Color(0xFF5BC8F5).copy(.45f)
                    else -> if (enabled) Color.White.copy(.12f) else Color.White.copy(.06f)
                },
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (symbol != null) {
            val symbolAlpha by animateFloatAsState(
                targetValue = if (justPlaced) 1f else 0.95f,
                animationSpec = tween(300),
                label = "symbolAlpha"
            )
            Text(
                text  = symbol,
                style = MaterialTheme.typography.displaySmall.copy(
                    color      = if (symbol == "X") VeilAccentGlow.copy(symbolAlpha) else Color(0xFF5BC8F5).copy(symbolAlpha),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 38.sp
                )
            )
        } else if (enabled) {
            // Subtle hint dot with pulse
            val hintPulse = rememberPulseAnimation(0.6f, 1f, 1200)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(hintPulse)
                    .clip(CircleShape)
                    .background(Color.White.copy(.08f))
            )
        }
    }
}
