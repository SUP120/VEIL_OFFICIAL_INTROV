package com.ns.findyourintroverfriend.game.drawguess

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Palette ───────────────────────────────────────────────────────────────────
private val GBg      = Color(0xFFF7F5F0)
private val GCard    = Color(0xFFFFFFFF)
private val GBorder  = Color(0xFFEDE8DF)
private val GAccent  = Color(0xFF7C6AE8)
private val GSub     = Color(0xFF8A8480)
private val GMuted   = Color(0xFFBBB5AC)
private val GPrimary = Color(0xFF1C1916)
private val GPill    = Color(0xFFF0EBE3)
private val GGreen   = Color(0xFF2E9E6A)
private val GRed     = Color(0xFFD94F4F)
private val GGold    = Color(0xFFE89820)

private val DRAW_COLORS = listOf(
    Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFE53935),
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFFB300),
    Color(0xFFFF6F00), Color(0xFF8E24AA)
)

private val EMOJI_REACTIONS = listOf("😂", "❤️", "😭", "😮", "🔥", "👏")

private data class FloatingReaction(val emoji: String, val id: Long)

// ── Local pen stroke ───────────────────────────────────────────────────────────
private data class LocalPen(val pts: List<Offset>, val colorHex: String, val width: Float, val eraser: Boolean)

@Composable
fun DrawGuessGameScreen(
    roomId   : String,
    matchId  : String,
    vm       : DrawGuessViewModel = viewModel(),
    onBack   : () -> Unit,
    onGameEnd: (roomId: String, matchId: String) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val room  = state.room
    val scope = rememberCoroutineScope()

    LaunchedEffect(roomId) { if (state.myUid.isEmpty()) vm.init(roomId) }
    LaunchedEffect(room?.status) { if (room?.status == "finished") onGameEnd(roomId, matchId) }

    if (state.loading || room == null) {
        Box(Modifier.fillMaxSize().background(GBg), Alignment.Center) {
            CircularProgressIndicator(color = GAccent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        return
    }

    val isDrawer    = state.myUid == room.drawerUid
    val isHost      = state.myUid == room.hostUid
    val isLastRound = room.currentRound >= room.maxRounds

    // Keyboard height for canvas shrink animation
    val imeInsets    = WindowInsets.ime
    val density      = LocalDensity.current
    val imeHeightDp  by remember {
        derivedStateOf { with(density) { imeInsets.getBottom(density).toDp() } }
    }
    val canvasWeight by animateFloatAsState(
        targetValue  = if (!isDrawer && imeHeightDp > 100.dp) 0.45f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label        = "canvasWeight"
    )

    // Floating emoji reactions state
    val floatingReactions = remember { mutableStateListOf<FloatingReaction>() }

    // Close guess toast
    var showCloseBanner by remember { mutableStateOf(false) }
    LaunchedEffect(state.guessResult) {
        if (state.guessResult == "close") {
            showCloseBanner = true
            delay(2000)
            showCloseBanner = false
        }
    }

    // ── Rules popup — show once at round 1 picking phase ─────────────────────
    var rulesShown by remember { mutableStateOf(false) }
    if (!rulesShown && room.currentRound == 1 && room.roundPhase == "picking") {
        DrawGuessRulesPopup(
            rounds   = room.maxRounds,
            category = room.category,
            onStart  = { rulesShown = true }
        )
        return
    }

    // ── Word-picking modal ────────────────────────────────────────────────────
    if (isDrawer && room.roundPhase == "picking" && state.wordChoices.isNotEmpty()) {
        WordPickModal(
            choices  = state.wordChoices,
            category = room.category,
            onPick   = { vm.pickWord(it) }
        )
        return
    }

    // ── Round result overlay ──────────────────────────────────────────────────
    if (room.roundPhase == "reveal") {
        val drawerAlias  = if (room.drawerUid == room.hostUid) room.hostAlias else room.guestAlias
        val guesserAlias = if (room.drawerUid == room.hostUid) room.guestAlias else room.hostAlias
        DrawGuessRoundResultScreen(
            round            = room.currentRound,
            maxRounds        = room.maxRounds,
            correctWord      = room.currentWordMasked.replace(" ", "").replace("_", "?"),
            guessedCorrectly = room.guessedCorrectly,
            drawerAlias      = drawerAlias,
            guesserAlias     = guesserAlias,
            guesserTime      = room.guesserGuessAt,
            drawerStareMs    = room.guesserGuessAt,
            hostUid          = room.hostUid,
            myUid            = state.myUid,
            isLastRound      = isLastRound,
            onNext           = { if (isLastRound) vm.leaveRoom() else vm.nextRound() }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(GBg).systemBarsPadding()) {

        // ── Header with scores ────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(GCard)
                .border(BorderStroke(0.5.dp, GBorder))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = { vm.leaveRoom(); onBack() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.ArrowBack, null, tint = GPrimary, modifier = Modifier.size(18.dp))
            }
            // Round chip
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(GPill)
                .padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("Round ${room.currentRound}/${room.maxRounds}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GPrimary)
            }
            Spacer(Modifier.weight(1f))
            TimerChip(seconds = state.timerSecondsLeft)
            Spacer(Modifier.weight(1f))
            // Scores
            val scores = room.scores
            Text(
                scores.entries.joinToString("  ·  ") { (uid, pts) ->
                    val alias = if (uid == room.hostUid) room.hostAlias else room.guestAlias
                    "$alias $pts"
                },
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GSub,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // ── Role banner ───────────────────────────────────────────────────────
        val drawerAlias = if (room.drawerUid == room.hostUid) room.hostAlias else room.guestAlias
        Row(
            Modifier.fillMaxWidth()
                .background(if (isDrawer) GAccent.copy(0.08f) else GGold.copy(0.08f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (isDrawer) "🎨" else "💭", fontSize = 18.sp)
            Text(
                if (isDrawer) "Your turn to draw!" else "$drawerAlias is drawing…",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (isDrawer) GAccent else GGold
            )
            Spacer(Modifier.weight(1f))
            Text("• ${room.category.replaceFirstChar { it.uppercase() }}",
                fontSize = 11.sp, color = GSub)
        }

        // ── Canvas ────────────────────────────────────────────────────────────
        var penColorHex by remember { mutableStateOf("#000000") }
        var penWidth    by remember { mutableStateOf(8f) }
        var isEraser    by remember { mutableStateOf(false) }
        val currentPts  = remember { mutableStateListOf<Offset>() }

        Box(
            Modifier.fillMaxWidth().weight(canvasWeight)
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GCard)
                .border(1.dp, GBorder, RoundedCornerShape(16.dp))
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
                    .background(Color.White)
                    .then(
                        if (isDrawer) Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { currentPts.clear(); currentPts.add(it) },
                                onDrag      = { change, _ -> currentPts.add(change.position) },
                                onDragEnd   = {
                                    if (currentPts.size > 1) {
                                        vm.sendStroke(DrawStroke(
                                            points   = currentPts.map { mapOf("x" to it.x, "y" to it.y) },
                                            color    = penColorHex,
                                            width    = penWidth,
                                            eraser   = isEraser,
                                            strokeAt = System.currentTimeMillis()
                                        ))
                                    }
                                    currentPts.clear()
                                }
                            )
                        } else Modifier
                    )
            ) {
                drawRect(Color.White)
                state.strokes.forEach { stroke ->
                    if (stroke.points.size < 2) return@forEach
                    val pts = stroke.points.map { Offset(it["x"] ?: 0f, it["y"] ?: 0f) }
                    val c   = if (stroke.eraser) Color.White
                    else try { Color(android.graphics.Color.parseColor(stroke.color)) }
                         catch (_: Exception) { Color.Black }
                    val path = Path().apply {
                        pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                    }
                    drawPath(path, color = c, style = Stroke(width = stroke.width,
                        cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (isDrawer && currentPts.size > 1) {
                    val c = if (isEraser) Color.White
                    else try { Color(android.graphics.Color.parseColor(penColorHex)) }
                         catch (_: Exception) { Color.Black }
                    val path = Path().apply {
                        currentPts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                    }
                    drawPath(path, color = c, style = Stroke(width = penWidth,
                        cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            // Guesser overlay when picking
            if (!isDrawer && room.roundPhase == "picking") {
                Box(Modifier.fillMaxSize().background(Color.White.copy(0.85f)), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = GAccent, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        Text("$drawerAlias is picking a word…", fontSize = 14.sp,
                            color = GSub, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Floating emoji reactions ──────────────────────────────────────
            floatingReactions.forEach { reaction ->
                key(reaction.id) { FloatingEmoji(emoji = reaction.emoji) }
            }

            // ── "So close!" toast ────────────────────────────────────────────
            if (showCloseBanner) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GGold)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("🔥 So close! Try again!",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // ── Word mask bar ─────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().background(GCard)
                .border(BorderStroke(0.5.dp, GBorder))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val mask = state.hintMasked.ifBlank { room.currentWordMasked }
            Text(
                text = if (isDrawer && room.roundPhase == "drawing")
                    "Drawing: ${mask.replace("_", "●")}" else mask.ifBlank { "_ _ _ _ _" },
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                color = if (isDrawer) GAccent else GPrimary,
                letterSpacing = 4.sp, textAlign = TextAlign.Center
            )
        }

        // ── Drawer live guess feed — ALWAYS shown so drawer knows where to look ──
        if (isDrawer && room.roundPhase == "drawing") {
            val guesses       = state.chatMessages.filter { it.isGuess }
            val latestGuesses = guesses.takeLast(4)

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(GCard)
                    .border(BorderStroke(0.5.dp, GBorder))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("👀", fontSize = 13.sp)
                    Text(
                        "Their guesses:",
                        fontSize      = 11.sp,
                        color         = GSub,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    if (guesses.isEmpty()) {
                        Text(
                            "— waiting for first guess…",
                            fontSize = 11.sp,
                            color    = GMuted
                        )
                    }
                }
                latestGuesses.forEach { msg ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("❌", fontSize = 12.sp)
                        Text(
                            msg.text,
                            fontSize   = 13.sp,
                            color      = GPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }


        // ── Drawer tools ──────────────────────────────────────────────────────
        if (isDrawer && room.roundPhase == "drawing") {
            Column(
                Modifier.fillMaxWidth().background(GCard)
                    .border(BorderStroke(0.5.dp, GBorder))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DRAW_COLORS.forEach { c ->
                        val hex = "#%02X%02X%02X".format(
                            (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                        val sel = penColorHex == hex && !isEraser
                        Box(
                            Modifier.size(if (sel) 32.dp else 26.dp).clip(CircleShape)
                                .background(if (c == Color.White) Color(0xFFEEEEEE) else c)
                                .then(if (sel) Modifier.border(2.5.dp, GAccent, CircleShape) else Modifier)
                                .clickable { penColorHex = hex; isEraser = false }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (isEraser) GRed.copy(0.15f) else GPill)
                            .border(1.dp, if (isEraser) GRed else GBorder, RoundedCornerShape(8.dp))
                            .clickable { isEraser = !isEraser }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.AutoFixNormal, null,
                                tint = if (isEraser) GRed else GSub, modifier = Modifier.size(14.dp))
                            Text("Erase", fontSize = 11.sp,
                                color = if (isEraser) GRed else GSub, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(4f to "S", 8f to "M", 14f to "L").forEach { (w, label) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (penWidth == w && !isEraser) GPrimary else GPill)
                                .clickable { penWidth = w; isEraser = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (penWidth == w && !isEraser) Color.White else GSub)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.undoLastStroke() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Undo, null, tint = GSub, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { vm.clearCanvas() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = GRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ── Guesser input + emoji reactions ───────────────────────────────────
        if (!isDrawer && room.roundPhase == "drawing") {
            var guessText by remember { mutableStateOf("") }
            val listState = rememberLazyListState()
            val guesses   = state.chatMessages.filter { it.isGuess }

            LaunchedEffect(guesses.size) {
                if (guesses.isNotEmpty()) listState.animateScrollToItem(guesses.lastIndex)
            }

            // Recent wrong guesses
            if (guesses.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = true
                ) {
                    items(guesses.reversed()) { msg ->
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(GPill)
                                .padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text("❌ ${msg.text}", fontSize = 12.sp, color = GRed)
                            }
                        }
                    }
                }
            }

            // Emoji reactions row
            Row(
                Modifier.fillMaxWidth().background(GCard)
                    .border(BorderStroke(0.5.dp, GBorder))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EMOJI_REACTIONS.forEach { emoji ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .background(GPill)
                            .clickable {
                                val id = System.currentTimeMillis()
                                floatingReactions.add(FloatingReaction(emoji, id))
                                scope.launch {
                                    delay(1500)
                                    floatingReactions.removeAll { it.id == id }
                                }
                                vm.sendChat("$emoji")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 18.sp)
                    }
                }
            }

            // Guess input
            Row(
                Modifier.fillMaxWidth().background(GCard)
                    .border(BorderStroke(0.5.dp, GBorder))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                        .background(GPill).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = guessText,
                        onValueChange = { guessText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = GPrimary),
                        decorationBox = { inner ->
                            if (guessText.isEmpty()) Text("Type your guess…", fontSize = 14.sp, color = GMuted)
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (guessText.isNotBlank()) GGreen else GPill)
                        .clickable(enabled = guessText.isNotBlank()) {
                            vm.sendGuess(guessText); guessText = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Send, null,
                        tint = if (guessText.isNotBlank()) Color.White else GMuted,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Floating emoji animation ──────────────────────────────────────────────────
@Composable
private fun FloatingEmoji(emoji: String) {
    val offsetY = remember { Animatable(0f) }
    val alpha   = remember { Animatable(1f) }
    val scale   = remember { Animatable(0.5f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        launch { offsetY.animateTo(-200f, tween(1400, easing = FastOutSlowInEasing)) }
        launch { delay(800); alpha.animateTo(0f, tween(600)) }
    }

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            emoji,
            fontSize = 36.sp,
            modifier = Modifier
                .offset(y = offsetY.value.dp)
                .scale(scale.value)
                .graphicsLayer(alpha = alpha.value)
        )
    }
}

// ── Word pick modal ───────────────────────────────────────────────────────────
@Composable
private fun WordPickModal(choices: List<String>, category: String, onPick: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)), Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(24.dp))
                .background(GCard).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎨", fontSize = 36.sp)
            Text("Pick a feeling to draw", fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold, color = GPrimary)
            Text("Category: ${category.replaceFirstChar { it.uppercase() }}",
                fontSize = 12.sp, color = GSub)
            HorizontalDivider(color = GBorder)
            choices.forEach { word ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(GAccent.copy(0.08f))
                        .border(1.5.dp, GAccent.copy(0.3f), RoundedCornerShape(14.dp))
                        .clickable { onPick(word) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(word.replaceFirstChar { it.uppercase() }, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, color = GPrimary)
                }
            }
        }
    }
}

// ── Timer chip ────────────────────────────────────────────────────────────────
@Composable
private fun TimerChip(seconds: Int) {
    val color = when {
        seconds > 40 -> GGreen
        seconds > 15 -> GGold
        else         -> GRed
    }
    val inf   = rememberInfiniteTransition(label = "tc")
    val pulse by inf.animateFloat(
        0.7f, 1f,
        infiniteRepeatable(tween(500), RepeatMode.Reverse), "tp"
    )
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(color.copy(if (seconds <= 10) pulse * 0.2f else 0.1f))
            .border(1.dp, color.copy(0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text("⏱ %02d:%02d".format(seconds / 60, seconds % 60),
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
