package com.ns.findyourintroverfriend.game.drawguess

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val WBg      = Color(0xFFF7F5F0)
private val WCard    = Color(0xFFFFFFFF)
private val WBorder  = Color(0xFFEDE8DF)
private val WAccent  = Color(0xFF7C6AE8)
private val WSub     = Color(0xFF8A8480)
private val WMuted   = Color(0xFFBBB5AC)
private val WPrimary = Color(0xFF1C1916)
private val WPill    = Color(0xFFF0EBE3)
private val WGreen   = Color(0xFF2E9E6A)

@Composable
fun DrawGuessWaitingRoom(
    roomId  : String,
    matchId : String,
    vm      : DrawGuessViewModel = viewModel(),
    onBack  : () -> Unit,
    onGameStart: (roomId: String, matchId: String) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val room  = state.room

    // Navigate to game when playing starts
    LaunchedEffect(room?.status) {
        if (room?.status == "playing") onGameStart(roomId, matchId)
    }

    LaunchedEffect(roomId) {
        if (state.myUid.isEmpty()) vm.init(roomId)
    }

    // Bug fix: once the room loads, tell Firestore the guest is present.
    // markGuestJoined() is idempotent — safe to call every time room updates.
    LaunchedEffect(room?.id) {
        if (room != null) vm.markGuestJoined()
    }

    var chatInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty())
            listState.animateScrollToItem(state.chatMessages.lastIndex)
    }

    // Pulse animation for waiting dot
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), "p")

    Column(
        Modifier
            .fillMaxSize()
            .background(WBg)
            .systemBarsPadding()
            .imePadding()          // Bug 2 fix: push content up when keyboard opens
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(WCard)
                .border(BorderStroke(0.5.dp, WBorder))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { vm.leaveRoom(); onBack() }) {
                Icon(Icons.Rounded.ArrowBack, null, tint = WPrimary, modifier = Modifier.size(20.dp))
            }
            Text("🎨", fontSize = 22.sp)
            Column(Modifier.weight(1f)) {
                Text("Draw & Guess", fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold, color = WPrimary)
                if (room != null)
                    Text("${room.maxRounds} rounds · ${room.category.replaceFirstChar { it.uppercase() }}",
                        fontSize = 11.sp, color = WSub)
            }
        }

        // Player cards
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                room?.hostUid to room?.hostAlias,
                room?.guestUid to room?.guestAlias
            ).forEachIndexed { idx, (uid, alias) ->
                val label  = if (idx == 0) "Host" else "Guest"
                // Bug 1 fix: for private rooms, guest is pre-set in the doc but
                // "joined" only becomes true after they open the waiting room.
                val joined = when {
                    idx == 0 -> uid?.isNotBlank() == true      // host: always joined
                    room?.inviteType == "private" -> room.guestJoined // guest: must have actually opened the room
                    else     -> uid?.isNotBlank() == true      // public room: join = uid present
                }
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WCard)
                        .border(1.dp, if (joined) WAccent.copy(0.4f) else WBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (joined) WAccent.copy(0.12f) else WPill)
                            .alpha(if (joined) 1f else pulse),
                        contentAlignment = Alignment.Center
                    ) {
                        if (joined)
                            Text(alias?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WAccent)
                        else
                            Icon(Icons.Rounded.PersonOutline, null,
                                tint = WMuted, modifier = Modifier.size(22.dp))
                    }
                    Text(if (joined) (alias ?: "Player") else "Waiting...",
                        fontSize = 13.sp,
                        fontWeight = if (joined) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (joined) WPrimary else WMuted)
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (joined) WGreen.copy(0.12f) else WPill)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (joined) "✓ Ready" else label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (joined) WGreen else WMuted
                        )
                    }
                }
            }
        }

        // Room info strip
        if (room?.inviteType == "public" && room.name.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WCard)
                    .border(1.dp, WBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.MeetingRoom, null, tint = WSub, modifier = Modifier.size(16.dp))
                Text(room.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WPrimary)
                Spacer(Modifier.weight(1f))
                if (room.hasPassword)
                    Icon(Icons.Rounded.Lock, null, tint = WMuted, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        // Chat label
        Text("  Lobby Chat", fontSize = 10.sp, color = WSub,
            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))

        // Chat messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (state.chatMessages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), Alignment.Center) {
                        Text("Say hello before the game! 👋", fontSize = 13.sp, color = WMuted)
                    }
                }
            }
            items(state.chatMessages) { msg ->
                val mine = msg.uid == state.myUid
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        if (!mine) Text(msg.alias, fontSize = 10.sp, color = WSub,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (mine) 16.dp else 4.dp,
                                bottomEnd = if (mine) 4.dp else 16.dp
                            )).background(if (mine) WAccent else WCard)
                                .border(if (mine) 0.dp else 1.dp, WBorder,
                                    RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(msg.text, fontSize = 14.sp,
                                color = if (mine) Color.White else WPrimary)
                        }
                    }
                }
            }
        }

        // Chat input
        Row(
            Modifier.fillMaxWidth()
                .background(WCard)
                .border(BorderStroke(0.5.dp, WBorder))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(WPill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = WPrimary),
                    decorationBox = { inner ->
                        if (chatInput.isEmpty()) Text("Chat...", fontSize = 14.sp, color = WMuted)
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (chatInput.isNotBlank()) WAccent else WPill)
                    .clickable(enabled = chatInput.isNotBlank()) {
                        vm.sendChat(chatInput); chatInput = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Send, null,
                    tint = if (chatInput.isNotBlank()) Color.White else WMuted,
                    modifier = Modifier.size(16.dp))
            }
        }

        // Start Game section
        // Bug 1 fix: for private rooms, both players are ready only when the
        // guest has actually opened the waiting room (guestJoined = true).
        val bothReady = room?.hostUid?.isNotBlank() == true && when {
            room.inviteType == "private" -> room.guestJoined
            else                         -> room.guestUid.isNotBlank()
        }
        val isHost    = room?.hostUid == state.myUid

        if (isHost && bothReady) {
            // ✅ Both players present — show Start button
            Box(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WAccent)
                    .clickable { vm.startGame() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null,
                        tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Start Game 🚀", fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        } else if (isHost) {
            // ⏳ Host waiting for guest — animated waiting state, NO start button
            Box(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WPill)
                    .border(1.dp, WBorder, RoundedCornerShape(14.dp))
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(WAccent.copy(alpha = pulse)))
                    Text("Waiting for player to join…",
                        fontSize = 14.sp, color = WSub, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            // Guest view — waiting for host to start
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WPill)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Waiting for host to start…", fontSize = 14.sp,
                    color = WSub, fontWeight = FontWeight.Medium)
            }
        }
    }
}
