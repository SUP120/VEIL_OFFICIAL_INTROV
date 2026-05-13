package com.ns.findyourintroverfriend.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

fun vibrate(ctx: Context, pattern: LongArray) {
    val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
    else @Suppress("DEPRECATION") vib.vibrate(pattern, -1)
}

// ── Warm white palette (matches app) ─────────────────────────────────────────
private val GBg         = Color(0xFFF7F5F0)
private val GCard       = Color(0xFFFFFFFF)
private val GBorder     = Color(0xFFEDE8DF)
private val GAccent     = Color(0xFF7C6AE8)
private val GAccentSoft = Color(0xFFEFEBFF)
private val GGold       = Color(0xFFE89820)
private val GGoldSoft   = Color(0xFFFFF3DC)
private val GPrimary    = Color(0xFF1C1916)
private val GSub        = Color(0xFF8A8480)
private val GMuted      = Color(0xFFBBB5AC)
private val GGreen      = Color(0xFF2E9E6A)
private val GGreenSoft  = Color(0xFFE6F5EE)
private val GRed        = Color(0xFFD94F4F)
private val GRedSoft    = Color(0xFFFFECEC)
private val GPill       = Color(0xFFF0EBE3)

// ── Waiting Room ─────────────────────────────────────────────────────────────
@Composable
fun GameWaitingRoom(vm: GameRoomViewModel, state: GameRoomUiState, room: GameRoom) {
    val isHost   = room.hostUid == state.myUid
    val canStart = room.playerCount >= 2 && isHost
    val ctx      = LocalContext.current
    val inf      = rememberInfiniteTransition(label = "wr")
    val pulse by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), "p")
    val shimX by inf.animateFloat(-400f, 1200f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), "sx")
    var notifSent by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    val chatMsgs  = state.chatMessages
    val listState = rememberLazyListState()
    LaunchedEffect(chatMsgs.size) {
        if (chatMsgs.isNotEmpty()) listState.animateScrollToItem(chatMsgs.size - 1)
    }

    Column(Modifier.fillMaxSize().background(GBg).systemBarsPadding().imePadding()) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .background(GCard)
                .drawBehind {
                    drawLine(GBorder, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(
                    room.name,
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = GPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${room.maxRounds} rounds  \u00B7  Guess the Country  \u00B7  ${room.playerCount}/2",
                    fontSize = 11.sp, color = GSub
                )
            }
            if (isHost && room.playerCount < 2) {
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (notifSent) GGreenSoft else GGoldSoft)
                        .clickable(enabled = !notifSent) {
                            notifSent = true
                            vibrate(ctx, longArrayOf(0, 40, 60, 40))
                            Firebase.firestore.collection("gameInvites").add(
                                mapOf("roomId" to room.id, "hostAlias" to room.hostAlias,
                                    "sentAt" to System.currentTimeMillis()))
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (notifSent) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications,
                            null, tint = if (notifSent) GGreen else GGold, modifier = Modifier.size(14.dp)
                        )
                        Text(
                            if (notifSent) "Sent" else "Invite",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (notifSent) GGreen else GGold
                        )
                    }
                }
            }
        }

        // ── VS strip ─────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player 1
            val p1 = room.players.entries.toList().getOrNull(0)
            PlayerAvatar(
                alias  = p1?.value?.alias ?: "",
                isHost = p1?.key == room.hostUid,
                isMe   = p1?.key == state.myUid,
                empty  = p1 == null,
                pulse  = pulse,
                modifier = Modifier.weight(1f)
            )

            // VS badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text("\uD83C\uDF0D", fontSize = 26.sp)
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(GPill)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("VS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = GPrimary, letterSpacing = 2.sp)
                }
            }

            // Player 2
            val p2 = room.players.entries.toList().getOrNull(1)
            PlayerAvatar(
                alias  = p2?.value?.alias ?: "",
                isHost = p2?.key == room.hostUid,
                isMe   = p2?.key == state.myUid,
                empty  = p2 == null,
                pulse  = pulse,
                modifier = Modifier.weight(1f)
            )
        }

        // Divider with label
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f).height(0.8.dp).background(GBorder))
            Text("\uD83D\uDCAC  chat", fontSize = 10.sp, color = GSub,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
            Box(Modifier.weight(1f).height(0.8.dp).background(GBorder))
        }

        Spacer(Modifier.height(6.dp))

        // ── Chat ─────────────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            if (chatMsgs.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("\uD83D\uDC4B", fontSize = 28.sp)
                            Text("Say hi before the game starts!", fontSize = 13.sp, color = GMuted)
                        }
                    }
                }
            }
            items(chatMsgs) { msg ->
                val isMe = msg.uid == state.myUid
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                        modifier = Modifier.fillMaxWidth(0.78f)
                    ) {
                        if (!isMe) {
                            Text(msg.alias, fontSize = 10.sp, color = GSub,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                        }
                        Box(
                            Modifier.clip(
                                RoundedCornerShape(
                                    topStart = if (isMe) 14.dp else 4.dp,
                                    topEnd   = if (isMe) 4.dp else 14.dp,
                                    bottomStart = 14.dp, bottomEnd = 14.dp
                                )
                            )
                            .background(if (isMe) GAccent else GCard)
                            .border(if (isMe) 0.dp else 1.dp, GBorder,
                                RoundedCornerShape(
                                    topStart = if (isMe) 14.dp else 4.dp,
                                    topEnd   = if (isMe) 4.dp else 14.dp,
                                    bottomStart = 14.dp, bottomEnd = 14.dp
                                ))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(msg.text, fontSize = 13.sp,
                                color = if (isMe) Color.White else GPrimary)
                        }
                    }
                }
            }
        }

        // ── Chat input ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GCard)
                .border(1.dp, GBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = chatInput,
                onValueChange = { if (it.length <= 200) chatInput = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = GPrimary),
                decorationBox = { inner ->
                    if (chatInput.isEmpty()) Text("Type a message\u2026", fontSize = 14.sp, color = GMuted)
                    inner()
                }
            )
            androidx.compose.animation.AnimatedVisibility(chatInput.isNotBlank()) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(GAccent)
                        .clickable { vm.sendChat(chatInput); chatInput = "" },
                    Alignment.Center
                ) {
                    Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ── Start / waiting button ────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (canStart) GAccent else if (isHost) GBorder else GPill)
                .drawWithContent {
                    drawContent()
                    if (canStart) drawRect(
                        Brush.linearGradient(
                            listOf(Color(0x007C6AE8), Color(0x44FFFFFF), Color(0x007C6AE8)),
                            Offset(shimX, 0f), Offset(shimX + 160f, size.height)
                        )
                    )
                }
                .clickable(enabled = canStart) {
                    vibrate(ctx, longArrayOf(0, 50, 50, 100))
                    vm.startGame()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isHost) Text("\u23F3", fontSize = 16.sp, modifier = Modifier.alpha(pulse))
                Text(
                    when {
                        canStart -> "Start Game \uD83D\uDE80"
                        isHost   -> "\u23F3  Waiting for opponent\u2026"
                        else     -> "\u23F3  Waiting for host\u2026"
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = when {
                        canStart -> Color.White
                        isHost   -> GSub
                        else     -> GSub
                    }
                )
            }
        }

        TextButton(
            onClick = { vm.leaveRoom() },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
        ) {
            Text("Leave", fontSize = 12.sp, color = GRed)
        }
    }
}

// ── Player avatar widget ──────────────────────────────────────────────────────
@Composable
private fun PlayerAvatar(
    alias   : String,
    isHost  : Boolean,
    isMe    : Boolean,
    empty   : Boolean,
    pulse   : Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(64.dp)
                .clip(CircleShape)
                .background(if (empty) GPill.copy(pulse) else if (isMe) GAccentSoft else GPill)
                .border(2.dp, if (isMe) GAccent else GBorder, CircleShape)
                .alpha(if (empty) pulse else 1f),
            contentAlignment = Alignment.Center
        ) {
            if (empty) {
                Text("?", fontSize = 22.sp, color = GSub)
            } else {
                Text(
                    alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (isMe) GAccent else GPrimary
                )
            }
        }
        if (!empty) {
            Text(
                alias, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = GPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (isHost) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(GGoldSoft)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("HOST", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = GGold, letterSpacing = 0.5.sp)
                }
            }
        } else {
            Text("Waiting\u2026", fontSize = 12.sp, color = GMuted.copy(pulse))
        }
    }
}


// ── Game Screen ───────────────────────────────────────────────────────────────
@Composable
fun GameRoomScreen(vm: GameRoomViewModel, state: GameRoomUiState, room: GameRoom) {
    val isHost    = room.hostUid == state.myUid
    val maxSec    = if (room.roundPhase == "hint") 10 else if (room.roundPhase == "reveal") 3 else 10
    var countdown by remember { mutableIntStateOf(maxSec) }

    // Phase timer
    LaunchedEffect(room.roundPhase, room.currentRound) {
        val max = when (room.roundPhase) { "hint" -> 10; "reveal" -> 3; else -> 10 }
        countdown = max
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
        if (isHost) {
            when (room.roundPhase) {
                "emoji"  -> if (room.roundWinner.isEmpty()) vm.advanceToHint()
                "hint"   -> if (room.roundWinner.isEmpty()) vm.advanceToReveal()
                "reveal" -> vm.startRound(room.currentRound + 1)
            }
        }
    }

    // When someone wins → host auto-advances after 3s
    LaunchedEffect(room.roundWinner) {
        if (room.roundWinner.isNotEmpty() && room.roundPhase == "reveal" && isHost) {
            delay(3000L)
            vm.startRound(room.currentRound + 1)
        }
    }

    val mySelection = state.mySelection
    val revealed    = room.roundPhase == "reveal"
    val locked      = revealed || mySelection.isNotEmpty()

    Column(Modifier.fillMaxSize().background(GBg)) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().background(GCard)
            .border(bottom = 1.dp, color = GBorder)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(room.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Round ${room.currentRound} of ${room.maxRounds}", fontSize = 11.sp, color = GSub)
            }
            room.players.values.take(2).forEach { p ->
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(GAccentSoft)
                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("${p.alias.take(7)}: ${p.score}",
                        fontSize = 11.sp, color = GAccent, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── TV Screen ─────────────────────────────────────────────────────────
        TvScreen(room = room, countdown = countdown, maxSec = maxSec)

        // ── 3 Option Buttons ──────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (room.options.isNotEmpty()) {
                room.options.forEach { option ->
                    OptionButton(
                        label      = option.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                        rawValue   = option,
                        answer     = room.currentAnswer,
                        myPick     = mySelection,
                        revealed   = revealed,
                        locked     = locked,
                        onTap      = { vm.selectOption(option) }
                    )
                }
            } else {
                // Options not loaded yet
                repeat(3) {
                    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(GPill).border(1.dp, GBorder, RoundedCornerShape(14.dp)))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Status strip at bottom ────────────────────────────────────────────
        AnimatedVisibility(visible = revealed, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth()
                .background(if (room.roundWinner.isNotEmpty()) GGreenSoft else GRedSoft)
                .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(
                    if (room.roundWinner.isNotEmpty())
                        "🎉 ${room.roundWinnerAlias} got it first!"
                    else
                        "⏰ Nobody answered in time!",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (room.roundWinner.isNotEmpty()) GGreen else GRed
                )
            }
        }
    }
}

// ── Option button ─────────────────────────────────────────────────────────────
@Composable
private fun OptionButton(
    label    : String,
    rawValue : String,
    answer   : String,
    myPick   : String,
    revealed : Boolean,
    locked   : Boolean,
    onTap    : () -> Unit
) {
    val isCorrect = rawValue.equals(answer, ignoreCase = true)
    val iMyPick   = myPick.equals(rawValue, ignoreCase = true)

    // Determine state
    val state = when {
        revealed && isCorrect          -> "correct"
        revealed && iMyPick && !isCorrect -> "wrong"
        iMyPick && !revealed           -> "picked"
        else                           -> "idle"
    }

    val bgColor = when (state) {
        "correct" -> GGreen
        "wrong"   -> GRed
        "picked"  -> GAccent
        else      -> GCard
    }
    val textColor = when (state) {
        "correct", "wrong", "picked" -> Color.White
        else                          -> GPrimary
    }
    val borderColor = when (state) {
        "correct" -> GGreen
        "wrong"   -> GRed
        "picked"  -> GAccent
        else      -> GBorder
    }

    val ctx = LocalContext.current
    // Bounce animation when tapped
    val scale = remember { Animatable(1f) }
    LaunchedEffect(iMyPick) {
        if (iMyPick) { scale.animateTo(0.95f, tween(80)); scale.animateTo(1f, tween(120)) }
    }
    // Vibrate on selection
    LaunchedEffect(iMyPick) {
        if (iMyPick) vibrate(ctx, longArrayOf(0, 30))
    }
    // Vibrate on reveal
    LaunchedEffect(state) {
        if (revealed) when (state) {
            "correct" -> vibrate(ctx, longArrayOf(0, 80, 40, 80, 40, 120))
            "wrong"   -> vibrate(ctx, longArrayOf(0, 200))
        }
    }

    Row(
        Modifier.fillMaxWidth().scale(scale.value)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = !locked) { onTap() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor, modifier = Modifier.weight(1f))
        when (state) {
            "correct" -> Text("✅", fontSize = 18.sp)
            "wrong"   -> Text("❌", fontSize = 18.sp)
            "picked"  -> Text("⏳", fontSize = 18.sp)
            else      -> {}
        }
    }
}

// ── TV Screen ─────────────────────────────────────────────────────────────────
@Composable
private fun TvScreen(room: GameRoom, countdown: Int, maxSec: Int = 10) {
    val phase    = room.roundPhase
    val revealed = phase == "reveal"
    val won      = room.roundWinner.isNotEmpty()

    val borderColor = when {
        revealed && won  -> GGreen
        revealed && !won -> GRed
        phase == "hint"  -> GGold
        else             -> GAccent
    }
    val timerColor = when {
        countdown <= 3 -> GRed
        countdown <= 6 -> GGold
        else           -> GGreen
    }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .background(GCard)
            .border(2.dp, borderColor.copy(0.45f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {

            // Phase pill
            Box(Modifier.clip(RoundedCornerShape(20.dp))
                .background(when (phase) {
                    "emoji" -> GAccentSoft; "hint" -> GGoldSoft
                    "reveal" -> if (won) GGreenSoft else GRedSoft
                    else -> GPill
                }).padding(horizontal = 14.dp, vertical = 5.dp)) {
                Text(
                    when (phase) {
                        "emoji"  -> "🌍  EMOJI CLUE"
                        "hint"   -> "💡  HINT"
                        "reveal" -> if (won) "✅  Correct!" else "⏰  Time's Up!"
                        else     -> ""
                    },
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    color = when (phase) { "emoji" -> GAccent; "hint" -> GGold; else -> if (won) GGreen else GRed }
                )
            }

            AnimatedContent(targetState = phase,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "tv") { p ->
                when (p) {
                    "emoji" -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(room.currentEmoji, fontSize = 36.sp, letterSpacing = 5.sp, textAlign = TextAlign.Center)
                        Text("What country is this?", fontSize = 11.sp, color = GSub)
                    }
                    "hint" -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(room.currentEmoji, fontSize = 20.sp, letterSpacing = 3.sp)
                        Text(room.currentHint, fontSize = 13.sp, color = GPrimary,
                            textAlign = TextAlign.Center, lineHeight = 18.sp)
                    }
                    "reveal" -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            room.currentAnswer.split(" ")
                                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = GPrimary
                        )
                        if (won) Text("🎉 ${room.roundWinnerAlias} answered first!",
                            fontSize = 12.sp, color = GGold, fontWeight = FontWeight.Bold)
                        else Text("Next round starting…", fontSize = 11.sp, color = GSub)
                    }
                    else -> {}
                }
            }

            // Timer bar
            if (!revealed) {
                val progress by animateFloatAsState(countdown.toFloat() / maxSec.toFloat(),
                    tween(950), label = "bar")
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.fillMaxWidth(0.8f).height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(GPill)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress)
                            .clip(RoundedCornerShape(3.dp)).background(timerColor))
                    }
                    Text("$countdown s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = timerColor)
                }
            }
        }

        // Corner dots
        Box(Modifier.matchParentSize()) {
            listOf(Alignment.TopStart, Alignment.TopEnd,
                   Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
                Box(Modifier.size(10.dp).align(a).padding(3.dp).clip(CircleShape).background(GBorder))
            }
        }
    }
}

// ── Modifier extension: single-side border ────────────────────────────────────────────
private fun Modifier.border(bottom: Dp = 0.dp, top: Dp = 0.dp, color: Color): Modifier =
    this.drawBehind {
        if (bottom > 0.dp) drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), bottom.toPx())
        if (top    > 0.dp) drawLine(color, Offset(0f, 0f),          Offset(size.width, 0f),           top.toPx())
    }

@Composable
fun GameResultScreen(vm: GameRoomViewModel, state: GameRoomUiState, room: GameRoom) {
    val sorted   = room.players.entries.sortedByDescending { it.value.score }
    val isWinner = sorted.firstOrNull()?.key == state.myUid
    val ctx      = LocalContext.current
    val scaleAnim = remember { Animatable(0.7f) }
    var chatInput by remember { mutableStateOf("") }
    val chatMsgs  = state.chatMessages
    val listState = rememberLazyListState()
    LaunchedEffect(chatMsgs.size) {
        if (chatMsgs.isNotEmpty()) listState.animateScrollToItem(chatMsgs.size - 1)
    }
    LaunchedEffect(Unit) {
        scaleAnim.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        if (isWinner) vibrate(ctx, longArrayOf(0,80,40,80,40,200,80,300))
        else vibrate(ctx, longArrayOf(0, 100))
    }
    val confettiColors = listOf(GAccent, GGold, GGreen, Color(0xFFE84393))
    val particles = remember { if (isWinner) List(35) { Triple(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat() * 0.5f, confettiColors.random()) } else emptyList() }
    val confettiAnim by rememberInfiniteTransition(label = "conf").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), "ca")

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(GBg).systemBarsPadding()) {
            // Score header
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isWinner) "\uD83C\uDFC6" else "\uD83C\uDFAE", fontSize = 52.sp,
                    modifier = Modifier.scale(scaleAnim.value))
                Text(if (isWinner) "You won!" else "Game over", fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold, color = GPrimary)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GCard)
                    .border(1.dp, GBorder, RoundedCornerShape(16.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sorted.forEachIndexed { i, (_, info) ->
                        val top = i == 0
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (top) GGoldSoft else GPill)
                            .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (top) "\uD83E\uDD47" else "\uD83E\uDD48", fontSize = 18.sp)
                            Column {
                                Text(info.alias, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GPrimary, maxLines = 1)
                                Text("${info.score} pts", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                                    color = if (top) GGold else GPrimary)
                            }
                        }
                    }
                }
            }
            Text("  Chat", fontSize = 10.sp, color = GSub, fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(4.dp))
            // Post-game chat
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)) {
                if (chatMsgs.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), Alignment.Center) {
                            Text("Discuss the game \uD83D\uDCAC", fontSize = 12.sp, color = GMuted)
                        }
                    }
                }
                items(chatMsgs) { msg ->
                    val isMe = msg.uid == state.myUid
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
                        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                            if (!isMe) Text(msg.alias, fontSize = 10.sp, color = GSub, modifier = Modifier.padding(horizontal = 4.dp))
                            Box(Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (isMe) GAccent else GCard)
                                .border(if (isMe) 0.dp else 1.dp, GBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(msg.text, fontSize = 13.sp, color = if (isMe) Color.White else GPrimary)
                            }
                        }
                    }
                }
            }
            // Chat input
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp)).background(GCard)
                .border(1.dp, GBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.text.BasicTextField(
                    value = chatInput, onValueChange = { chatInput = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = GPrimary),
                    decorationBox = { inner ->
                        if (chatInput.isEmpty()) Text("Say something\u2026", fontSize = 14.sp, color = GMuted)
                        inner()
                    }
                )
                if (chatInput.isNotBlank()) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(GAccent).clickable {
                        vm.sendChat(chatInput); chatInput = ""
                    }, Alignment.Center) {
                        Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
            // Action buttons
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (room.hostUid == state.myUid) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(GAccentSoft)
                        .border(1.dp, GAccent.copy(0.3f), RoundedCornerShape(12.dp))
                        .clickable {
                            vm.createRoom(room.name, "", room.maxRounds, room.gameType)
                        }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                        Text("Play Again", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GAccent)
                    }
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(GPill)
                    .clickable { vm.leaveRoom() }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                    Text("Leave", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GSub)
                }
            }
        }
        // Confetti
        if (isWinner) {
            Canvas(Modifier.fillMaxSize()) {
                particles.forEach { (xFrac, yStart, color) ->
                    val y = (yStart + confettiAnim) % 1f
                    val wobble = kotlin.math.sin((confettiAnim + xFrac) * Math.PI * 6).toFloat() * 14f
                    drawCircle(color = color.copy(0.8f), radius = 5f + xFrac * 4f,
                        center = Offset(xFrac * size.width + wobble, y * size.height))
                }
            }
        }
    }
}
