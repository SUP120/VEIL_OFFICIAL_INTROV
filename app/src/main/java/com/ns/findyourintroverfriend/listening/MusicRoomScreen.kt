package com.ns.findyourintroverfriend.listening

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// ── App palette (matches rest of app) ────────────────────────────────────────
private val RBg        = Color(0xFFF7F5F0)
private val RCard      = Color(0xFFFFFFFF)
private val RBorder    = Color(0xFFEDE8DF)
private val RAccent    = Color(0xFF7C6AE8)
private val RAccentSoft= Color(0xFFEFEBFF)
private val RPrimary   = Color(0xFF1C1916)
private val RSub       = Color(0xFF8A8480)
private val RMuted     = Color(0xFFBBB5AC)
private val RGreen     = Color(0xFF2E9E6A)
private val RGreenSoft = Color(0xFFE6F5EE)
private val RPill      = Color(0xFFF0EBE3)
private val RGold      = Color(0xFFE89820)

// ── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun MusicRoomRoot(vm: MusicRoomViewModel = viewModel(), onBack: () -> Unit = {}) {
    val roomState by vm.roomState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    when (val s = roomState) {
        is MusicRoomUiState.Idle,
        is MusicRoomUiState.Error   -> MusicLobbyScreen(vm, onBack)
        is MusicRoomUiState.Loading -> Box(Modifier.fillMaxSize().background(RBg), Alignment.Center) {
            CircularProgressIndicator(color = RAccent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }
        is MusicRoomUiState.Active  -> MusicChatRoom(vm, s, ctx, onBack)
    }
}

// ── LOBBY ─────────────────────────────────────────────────────────────────────
@Composable
private fun MusicLobbyScreen(vm: MusicRoomViewModel, onBack: () -> Unit) {
    val lobbyState by vm.lobby.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    val shimInf = rememberInfiniteTransition(label = "ls")
    val shimX by shimInf.animateFloat(
        -400f, 1200f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), "lsx"
    )

    Column(
        Modifier.fillMaxSize().background(RBg).systemBarsPadding()
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBackIosNew, null, tint = RPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("Music Room", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = RPrimary)
                Text("Listen together, vibe together", fontSize = 12.sp, color = RSub)
            }
            // Shimmer Create button
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(RAccent)
                    .drawWithContent {
                        drawContent()
                        val brush = Brush.linearGradient(
                            listOf(Color(0x007C6AE8), Color(0x60FFFFFF), Color(0x007C6AE8)),
                            start = Offset(shimX, 0f),
                            end   = Offset(shimX + 150f, size.height)
                        )
                        drawRect(brush = brush)
                    }
                    .clickable { showCreate = true }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text("Create", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        HorizontalDivider(color = RBorder, thickness = 1.dp)

        // ── Room list ────────────────────────────────────────────────────────
        when (val ls = lobbyState) {
            is LobbyState.Loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { LobbyCardSkeleton(shimX) }
            }
            is LobbyState.Loaded -> {
                if (ls.rooms.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🎵", fontSize = 44.sp)
                            Text("No open rooms", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = RPrimary)
                            Text("Create one — music starts right away", fontSize = 12.sp, color = RSub)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(RAccent)
                                .clickable { showCreate = true }.padding(horizontal = 20.dp, vertical = 9.dp)) {
                                Text("Create Room", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(ls.rooms) { room ->
                            LobbyRoomCard(room, vm.myUid, shimX) { vm.joinRoom(room.roomId) {} }
                        }
                        item { Spacer(Modifier.height(60.dp)) }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateRoomSheet(
            onDismiss = { showCreate = false },
            onConfirm = { max, priv -> showCreate = false; vm.createRoom(max, priv) {} }
        )
    }
}

@Composable
private fun LobbyRoomCard(room: MusicRoom, myUid: String, shimX: Float, onJoin: () -> Unit) {
    val isLive   = room.status == "playing"
    val isMember = myUid in room.members

    val inf = rememberInfiniteTransition(label = "dot")
    val da by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), "da")

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RCard)
            .border(1.dp, if (isLive) RAccent.copy(0.35f) else RBorder, RoundedCornerShape(14.dp))
            .clickable(enabled = !isMember && !room.isFull, onClick = onJoin)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Music emoji thumb
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(RAccentSoft), Alignment.Center) {
                Text("🎵", fontSize = 20.sp)
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isLive) Box(Modifier.size(6.dp).clip(CircleShape).background(RGreen.copy(da)))
                    Text(
                        if (isLive) "Playing now" else "Waiting to start",
                        fontSize = 10.sp, fontWeight = FontWeight.Medium,
                        color = if (isLive) RGreen else RSub
                    )
                }
                Text(
                    room.currentTrack?.title ?: "Lofi Vibes",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("by ${room.hostAlias} · ${room.members.size}/${room.maxMembers} people",
                    fontSize = 11.sp, color = RSub)
            }

            if (isMember) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(RAccentSoft)
                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("You're in", color = RAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            } else if (!room.isFull) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(RAccent)
                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("Join", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(Icons.Rounded.Lock, null, tint = RMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LobbyCardSkeleton(shimX: Float) {
    val brush = Brush.linearGradient(
        listOf(Color(0xFFEEEBE5), Color(0xFFF5F3EF), Color(0xFFEEEBE5)),
        Offset(shimX, 0f), Offset(shimX + 300f, 0f)
    )
    Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(14.dp)).background(brush))
}

// ── Create Room Sheet ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRoomSheet(onDismiss: () -> Unit, onConfirm: (Int, Boolean) -> Unit) {
    var maxPeople by remember { mutableIntStateOf(5) }
    var isPrivate by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RCard,
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp).width(32.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(RBorder))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("New Room", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = RPrimary)

            // Size
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Room size", fontSize = 12.sp, color = RSub)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 5).forEach { n ->
                        val sel = maxPeople == n
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) RAccent else RPill)
                                .clickable { maxPeople = n }.padding(vertical = 11.dp),
                            Alignment.Center
                        ) {
                            Text("$n", color = if (sel) Color.White else RSub,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            // Private toggle
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Private", fontSize = 14.sp, color = RPrimary, fontWeight = FontWeight.Medium)
                    Text("Only visible to invited users", fontSize = 11.sp, color = RSub)
                }
                Switch(checked = isPrivate, onCheckedChange = { isPrivate = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RAccent))
            }

            if (!isPrivate) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Notifications, null, tint = RAccent, modifier = Modifier.size(14.dp))
                    Text("All Veil users will get a notification", fontSize = 11.sp, color = RSub)
                }
            }

            Button(
                onClick = { onConfirm(maxPeople, isPrivate) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RAccent)
            ) {
                Text(if (!isPrivate) "Create & Notify All" else "Create Room",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ── MUSIC CHAT ROOM ───────────────────────────────────────────────────────────
// Music plays in background. UI = ambient banner + full chat.
@Composable
private fun MusicChatRoom(
    vm    : MusicRoomViewModel,
    state : MusicRoomUiState.Active,
    ctx   : Context,
    onBack: () -> Unit
) {
    val room     = state.room
    val amHost   = state.amHost
    val messages by vm.messages.collectAsStateWithLifecycle()
    val track    = room.currentTrack

    // Auto-start music for host on enter; guests sync to Firestore state
    LaunchedEffect(Unit) {
        if (amHost) vm.applyPlayback(ctx, room, 0L, play = true)
    }
    // Guests follow host state changes
    LaunchedEffect(room.trackIndex, room.isPlaying, room.updatedAt) {
        if (!amHost) vm.applyPlayback(ctx, room, room.positionMs, room.isPlaying)
    }
    DisposableEffect(Unit) { onDispose { vm.releasePlayer() } }

    // Waveform animation when playing
    val inf = rememberInfiniteTransition(label = "wave")
    val wavePhase by inf.animateFloat(
        0f, 32f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), "wp"
    )

    Column(Modifier.fillMaxSize().background(RBg).systemBarsPadding()) {

        // ── Ambient music banner ─────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .background(RCard)
                .border(width = 0.dp, color = Color.Transparent, shape = RoundedCornerShape(0.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = { vm.leaveRoom(ctx); onBack() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ArrowBackIosNew, null, tint = RPrimary, modifier = Modifier.size(16.dp))
                }

                // Live waveform bars (tiny) or music note
                if (room.isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(22.dp)
                    ) {
                        repeat(4) { i ->
                            val h = (4 + 8 * Math.abs(Math.sin((wavePhase + i * 2.5) * 0.4))).toFloat()
                            Box(
                                Modifier.width(3.dp).height(h.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(RAccent)
                            )
                        }
                    }
                } else {
                    Icon(Icons.Rounded.MusicNote, null, tint = RMuted, modifier = Modifier.size(18.dp))
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title ?: "Loading…",
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = RPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${room.members.size} listening · ${track?.artist ?: ""}",
                        fontSize = 11.sp, color = RSub
                    )
                }

                // Host controls: only skip button, music is always on
                if (amHost) {
                    IconButton(onClick = { vm.nextTrack(ctx) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.SkipNext, null, tint = RSub, modifier = Modifier.size(20.dp))
                    }
                }

                // Private indicator
                if (room.isPrivate) {
                    Icon(Icons.Rounded.Lock, null, tint = RGold, modifier = Modifier.size(14.dp))
                }
            }
        }

        HorizontalDivider(color = RBorder, thickness = 1.dp)

        // ── Chat fills all remaining space ───────────────────────────────────
        ChatSection(messages = messages, myUid = vm.myUid, onSend = { vm.sendMessage(it) })
    }
}

// ── Chat Section ──────────────────────────────────────────────────────────────
@Composable
private fun ChatSection(messages: List<ChatMessage>, myUid: String, onSend: (String) -> Unit) {
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    val focusMgr = LocalFocusManager.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxWidth().padding(top = 40.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🎵", fontSize = 32.sp)
                            Text("Music is on — say something!", fontSize = 13.sp, color = RSub)
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.uid == myUid
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    if (!isMe) {
                        Text(msg.alias, fontSize = 10.sp, color = RSub,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                    }
                    Box(
                        Modifier.clip(
                            RoundedCornerShape(
                                topStart = if (isMe) 16.dp else 4.dp,
                                topEnd = if (isMe) 4.dp else 16.dp,
                                bottomStart = 16.dp, bottomEnd = 16.dp
                            )
                        ).background(if (isMe) RAccent else RCard)
                            .border(if (!isMe) 1.dp else 0.dp, RBorder,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(msg.text, fontSize = 14.sp,
                            color = if (isMe) Color.White else RPrimary, lineHeight = 20.sp)
                    }
                }
            }
        }

        // Input
        HorizontalDivider(color = RBorder, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().background(RCard).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(RBg).border(1.dp, RBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (text.isEmpty()) Text("Say something…", fontSize = 14.sp, color = RMuted)
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = RPrimary),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (text.isNotBlank()) { onSend(text); text = "" }
                        focusMgr.clearFocus()
                    })
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (text.isNotBlank()) RAccent else RPill)
                    .clickable(enabled = text.isNotBlank()) {
                        onSend(text); text = ""; focusMgr.clearFocus()
                    },
                Alignment.Center
            ) {
                Icon(Icons.Rounded.Send, null,
                    tint = if (text.isNotBlank()) Color.White else RMuted,
                    modifier = Modifier.size(17.dp))
            }
        }
    }
}
