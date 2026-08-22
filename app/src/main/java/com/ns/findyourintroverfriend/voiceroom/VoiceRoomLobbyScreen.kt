package com.ns.findyourintroverfriend.voiceroom

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.util.LocalVeilIsDark

// ─────────────────────────────────────────────────────────────────────────────
//  Whisper — theme-aware local design tokens
//  Light: white bg / dark text. Dark: near-black bg / off-white text.
// ─────────────────────────────────────────────────────────────────────────────

private var _wDark by mutableStateOf(true) // synced in root composable via SideEffect

// Peaceful Serenity — warm parchment · dusty mauve · sage green · muted terracotta
private val WBg          get() = if (_wDark) Color(0xFF0F0F14) else Color(0xFFFBF8F4)  // warm parchment
private val WCard        get() = if (_wDark) Color(0xFF17171E) else Color(0xFFFFFFFF)
private val WCardAlt     get() = if (_wDark) Color(0xFF1E1E26) else Color(0xFFF4F0EB)
private val WBorder      get() = if (_wDark) Color(0xFF2C2C34) else Color(0xFFE8E3DC)
private val WBorderFocus get() = if (_wDark) Color(0xFF3C3C48) else Color(0xFFCEC9C2)
private val WAccent             = Color(0xFFA07C8C)   // dusty mauve
private val WAccentBg    get() = if (_wDark) Color(0xFF251520) else Color(0xFFF7EFF3)
private val WAccentText         = Color(0xFF9A6E7E)
private val WGreen              = Color(0xFF7A9E87)   // sage green
private val WGreenBg     get() = if (_wDark) Color(0xFF0E1E13) else Color(0xFFEFF5F1)
private val WRed                = Color(0xFFBF6B6B)   // muted terracotta
private val WRedBg       get() = if (_wDark) Color(0xFF1E0F0F) else Color(0xFFF5EEED)
private val WTextPrimary   get() = if (_wDark) Color(0xFFE0DDD8) else Color(0xFF2C2825)
private val WTextSecondary get() = if (_wDark) Color(0xFF918E8A) else Color(0xFF7A7470)
private val WTextTertiary  get() = if (_wDark) Color(0xFF5A5855) else Color(0xFF9E9A96)
private val WTextDisabled  get() = if (_wDark) Color(0xFF3A3835) else Color(0xFFC4BFBA)

// ─────────────────────────────────────────────────────────────────────────────
//  Whisper Lobby Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VoiceRoomLobbyScreen(
    vm         : VoiceRoomViewModel = viewModel(),
    onEnterRoom: (roomId: String) -> Unit = {},
    onBack     : () -> Unit = {},
) {
    val state        by vm.lobbyState.collectAsStateWithLifecycle()
    val connectState by vm.connectState.collectAsStateWithLifecycle()
    val context    = LocalContext.current
    val haptic     = LocalHapticFeedback.current
    val myUid      = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    // Sync dark/light mode
    val veilIsDark = LocalVeilIsDark.current
    SideEffect { _wDark = veilIsDark }

    var showCreate by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connectState) {
        when (val s = connectState) {
            is VoiceConnectState.Ready  -> { vm.resetConnectState(); onEnterRoom(s.roomId) }
            is VoiceConnectState.Failed -> { errorMsg = s.message; vm.resetConnectState() }
            else -> Unit
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (!granted) errorMsg = "Microphone permission is required to use Whisper."
    }
    fun withMicPermission(action: () -> Unit) {
        if (hasMicPermission) action() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val isWorking  = connectState is VoiceConnectState.Working
    val workingMsg = (connectState as? VoiceConnectState.Working)?.msg ?: "Connecting…"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WBg)
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ── Header ──────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = WTextSecondary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(6.dp))
                // Whisper icon
                Image(
                    painter            = painterResource(id = R.drawable.whispericon),
                    contentDescription = "Whisper",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Whisper",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = WTextPrimary,
                        )
                        WLiveBadge()
                    }
                    Text(
                        "Live voice rooms",
                        fontSize = 11.sp,
                        color    = WTextTertiary,
                    )
                }
                // Create button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(WAccent)
                        .clickable(enabled = !isWorking) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            withMicPermission { showCreate = true }
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("Create", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider(color = WBorder)

            // ── Content ──────────────────────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color       = WAccent,
                            strokeWidth = 1.5.dp,
                            modifier    = Modifier.size(26.dp),
                        )
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "Error", color = WTextTertiary, fontSize = 14.sp)
                    }
                }
                state.rooms.isEmpty() -> {
                    WEmptyState(onCreate = { withMicPermission { showCreate = true } })
                }
                else -> {
                    // Stats strip
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(WGreen))
                        Text(
                            "${state.rooms.size} room${if (state.rooms.size != 1) "s" else ""} live",
                            fontSize = 11.sp, color = WTextTertiary,
                        )
                        val totalPeople = state.rooms.sumOf { it.participantCount }
                        if (totalPeople > 0) {
                            Text("·", color = WTextDisabled, fontSize = 11.sp)
                            Text(
                                "$totalPeople ${if (totalPeople == 1) "person" else "people"} talking",
                                fontSize = 11.sp, color = WTextTertiary,
                            )
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.rooms, key = { it.roomId }) { room ->
                            WRoomCard(
                                room      = room,
                                myUid     = myUid,
                                isWorking = isWorking,
                                onJoin    = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    withMicPermission { vm.joinExistingRoom(room.roomId) }
                                },
                                onRequest = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    withMicPermission { vm.requestToJoin(room.roomId) }
                                },
                                onEnter   = { onEnterRoom(room.roomId) },
                            )
                        }
                    }
                }
            }
        }

        // Connecting overlay
        if (isWorking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.7f))
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick           = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(color = WAccentText, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    Text(workingMsg, color = WTextSecondary, fontSize = 13.sp)
                }
            }
        }

        // Error snackbar
        errorMsg?.let { msg ->
            Snackbar(
                modifier       = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = WCard,
                contentColor   = WTextPrimary,
                action = {
                    TextButton(onClick = { errorMsg = null }) {
                        Text("OK", color = WAccentText)
                    }
                }
            ) { Text(msg, fontSize = 12.sp) }
        }
    }

    if (showCreate) {
        WCreateSheet(
            onDismiss = { showCreate = false },
            onCreate  = { topic, desc, lang, tags, maxP, locked ->
                showCreate = false
                withMicPermission {
                    vm.createAndJoin(
                        topic           = topic,
                        description     = desc,
                        language        = lang,
                        tags            = tags,
                        maxParticipants = maxP,
                        isLocked        = locked,
                    )
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LIVE badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WLiveBadge() {
    val inf = rememberInfiniteTransition(label = "wb")
    val a   by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), "wa")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(WGreenBg)
            .border(0.5.dp, WGreen.copy(0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(WGreen.copy(a)))
        Text("LIVE", color = WGreen, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Room Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WRoomCard(
    room     : VoiceRoom,
    myUid    : String,
    isWorking: Boolean,
    onJoin   : () -> Unit,
    onRequest: () -> Unit,
    onEnter  : () -> Unit,
) {
    val inf    = rememberInfiniteTransition(label = "dot_${room.roomId}")
    val dotA   by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), "da")

    val alreadyIn    = myUid in room.participantUids
    val canJoin      = !room.isFull && !alreadyIn
    val hasRequested = room.joinRequests.any { it.uid == myUid }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WCard)
            .border(1.dp, WBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = when {
                alreadyIn -> onEnter
                canJoin && !room.isLocked -> onJoin
                canJoin && room.isLocked && !hasRequested -> onRequest
                else -> ({})
            })
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Top row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Host emoji
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(WCardAlt)
                        .border(1.dp, WBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(room.hostEmoji, fontSize = 18.sp) }

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            room.topic.ifBlank { "Late night thoughts" },
                            color      = WTextPrimary,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false),
                        )
                        if (room.isLocked) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(WAccentBg)
                                    .border(0.5.dp, WAccent.copy(0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(Icons.Rounded.Lock, null, tint = WAccentText, modifier = Modifier.size(8.dp))
                                    Text("Private", color = WAccentText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("by ${room.hostAlias}", color = WTextTertiary, fontSize = 11.sp)
                        if (room.language.isNotBlank() && room.language != "Any") {
                            Text("·", color = WTextDisabled, fontSize = 11.sp)
                            Text(room.language, color = WTextTertiary, fontSize = 11.sp)
                        }
                        Text("·", color = WTextDisabled, fontSize = 11.sp)
                        Text(room.ageLabel(), color = WTextDisabled, fontSize = 10.sp)
                    }
                }

                // Time chip
                val remSecs = room.remainingSecs().coerceAtLeast(0L)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WCardAlt)
                        .border(0.5.dp, WBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "%02d:%02d".format(remSecs / 60, remSecs % 60),
                        color      = WTextSecondary,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Description
            if (room.description.isNotBlank()) {
                Text(
                    room.description,
                    color    = WTextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Bottom row
            HorizontalDivider(color = WBorder, thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(WGreen.copy(dotA)))
                Text(
                    "${room.participantCount}/${room.maxParticipants} in room",
                    color = WTextTertiary, fontSize = 11.sp,
                )
                if (room.tags.isNotEmpty()) {
                    Text("·", color = WTextDisabled, fontSize = 11.sp)
                    Text(
                        room.tags.take(2).joinToString(" · "),
                        color    = WTextDisabled,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))

                // Action
                when {
                    alreadyIn -> WPill("Enter →", WAccentBg, WAccentText, border = WAccent.copy(0.3f), onClick = onEnter)
                    hasRequested -> WPill("Requested ⏳", WCardAlt, WTextTertiary)
                    canJoin && !room.isLocked -> WPill(
                        text    = if (isWorking) "…" else "Join",
                        bg      = WAccent,
                        textCol = Color.White,
                        onClick = onJoin,
                    )
                    canJoin && room.isLocked -> WPill(
                        text    = "Request",
                        bg      = WCardAlt,
                        textCol = WTextSecondary,
                        border  = WBorder,
                        onClick = onRequest,
                    )
                    else -> WPill("Full", WCardAlt, WTextDisabled)
                }
            }
        }
    }
}

@Composable
private fun WPill(
    text   : String,
    bg     : Color,
    textCol: Color,
    border : Color? = null,
    onClick: (() -> Unit)? = null,
) {
    var mod = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(bg)
    if (border != null) mod = mod.border(0.5.dp, border, RoundedCornerShape(8.dp))
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    mod = mod.padding(horizontal = 12.dp, vertical = 5.dp)
    Box(mod) {
        Text(text, color = textCol, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty State
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WEmptyState(onCreate: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "ew")
    val sc  by inf.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), "ews")
    Column(
        Modifier.fillMaxSize().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Animated mic icon instead of emoji bubble
        Box(
            Modifier
                .size(80.dp)
                .scale(sc)
                .clip(CircleShape)
                .background(WAccentBg)
                .border(1.dp, WAccent.copy(0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = null,
                tint = WAccent,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "No rooms yet",
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            color      = WTextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Be the first to open a Whisper room.\nOthers can join and talk with you.",
            fontSize  = 13.sp,
            color     = WTextTertiary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(WAccent)
                .clickable(onClick = onCreate)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Text("Start a Whisper Room", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Create Room Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WCreateSheet(
    onDismiss: () -> Unit,
    onCreate : (topic: String, description: String, language: String, tags: List<String>, maxParticipants: Int, isLocked: Boolean) -> Unit,
) {
    var topic          by remember { mutableStateOf("") }
    var description    by remember { mutableStateOf("") }
    var language       by remember { mutableStateOf("Any") }
    var selectedTags   by remember { mutableStateOf(setOf<String>()) }
    var maxP           by remember { mutableIntStateOf(6) }
    var isLocked       by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = WBg,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 3.dp).clip(CircleShape).background(WBorder))
            }
        }
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "New Whisper Room",
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = WTextPrimary,
            )

            // Topic
            OutlinedTextField(
                value         = topic,
                onValueChange = { if (it.length <= 60) topic = it },
                placeholder   = { Text("e.g. late night thoughts", color = WTextDisabled, fontSize = 13.sp) },
                label         = { Text("Room Topic *", color = WTextTertiary, fontSize = 11.sp) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                colors        = wFieldColors(),
                trailingIcon  = {
                    Text("${topic.length}/60", color = WTextDisabled, fontSize = 10.sp,
                        modifier = Modifier.padding(end = 8.dp))
                }
            )

            // Description
            OutlinedTextField(
                value         = description,
                onValueChange = { if (it.length <= 120) description = it },
                placeholder   = { Text("What will you talk about? (optional)", color = WTextDisabled, fontSize = 13.sp) },
                label         = { Text("Description", color = WTextTertiary, fontSize = 11.sp) },
                minLines      = 2,
                maxLines      = 3,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                colors        = wFieldColors(),
            )

            // Public / Private
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WCard)
                    .border(1.dp, WBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            null,
                            tint     = if (isLocked) WAccentText else WTextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            if (isLocked) "Private Room" else "Public Room",
                            fontWeight = FontWeight.SemiBold,
                            color      = WTextPrimary,
                            fontSize   = 13.sp,
                        )
                    }
                    Text(
                        if (isLocked) "You approve who joins" else "Anyone can join instantly",
                        color = WTextTertiary, fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = isLocked,
                    onCheckedChange = { isLocked = it; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = WAccent,
                        uncheckedThumbColor = WTextDisabled,
                        uncheckedTrackColor = WCardAlt,
                    )
                )
            }

            // Language
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WCard)
                    .border(1.dp, WBorder, RoundedCornerShape(12.dp))
                    .clickable { showLangPicker = !showLangPicker }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Language", fontSize = 11.sp, color = WTextTertiary)
                    Text(language, fontSize = 13.sp, color = WTextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Icon(
                    if (showLangPicker) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null, tint = WTextTertiary, modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = showLangPicker) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WHISPER_LANGUAGES.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { lang ->
                                val sel = lang == language
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) WAccentBg else WCard)
                                        .border(0.5.dp, if (sel) WAccent.copy(0.5f) else WBorder, RoundedCornerShape(8.dp))
                                        .clickable { language = lang; showLangPicker = false }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(lang, fontSize = 11.sp, color = if (sel) WAccentText else WTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }

            // Mood tags
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Mood (up to 3)", fontSize = 11.sp, color = WTextTertiary)
                WHISPER_TAGS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { tag ->
                            val sel = tag in selectedTags
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) WAccentBg else WCard)
                                    .border(0.5.dp, if (sel) WAccent.copy(0.5f) else WBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedTags = if (sel) selectedTags - tag
                                        else if (selectedTags.size < 3) selectedTags + tag
                                        else selectedTags
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(tag, fontSize = 11.sp, color = if (sel) WAccentText else WTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // Max participants
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Max participants", fontSize = 11.sp, color = WTextTertiary)
                    Text("$maxP people", fontSize = 12.sp, color = WTextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value         = maxP.toFloat(),
                    onValueChange = { maxP = it.toInt() },
                    valueRange    = 2f..6f,
                    steps         = 3,
                    colors = SliderDefaults.colors(
                        thumbColor         = WAccent,
                        activeTrackColor   = WAccent,
                        inactiveTrackColor = WCardAlt,
                    )
                )
            }

            // Free tier note
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(WCard)
                    .border(0.5.dp, WGreen.copy(0.25f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, tint = WGreen, modifier = Modifier.size(16.dp))
                    Column {
                        Text("15 min free · extend with cookies", fontSize = 12.sp, color = WGreen, fontWeight = FontWeight.SemiBold)
                        Text("5 cookies = +15 min (up to 3×)", fontSize = 11.sp, color = WTextTertiary)
                    }
                }
            }

            // Go Live
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (topic.isNotBlank()) WAccent else WCard)
                    .border(0.5.dp, if (topic.isNotBlank()) Color.Transparent else WBorder, RoundedCornerShape(14.dp))
                    .clickable(enabled = topic.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCreate(topic.trim(), description.trim(), language, selectedTags.toList(), maxP, isLocked)
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Rounded.Mic, null,
                        tint     = if (topic.isNotBlank()) Color.White else WTextDisabled,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        "Go Live",
                        color      = if (topic.isNotBlank()) Color.White else WTextDisabled,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun wFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = WAccent.copy(0.7f),
    unfocusedBorderColor    = WBorder,
    focusedContainerColor   = WCard,
    unfocusedContainerColor = WCard,
    cursorColor             = WAccentText,
    focusedTextColor        = WTextPrimary,
    unfocusedTextColor      = WTextPrimary,
    focusedLabelColor       = WAccentText,
    unfocusedLabelColor     = WTextTertiary,
)
