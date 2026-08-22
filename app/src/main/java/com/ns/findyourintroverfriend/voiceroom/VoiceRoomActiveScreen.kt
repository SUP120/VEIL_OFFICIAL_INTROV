package com.ns.findyourintroverfriend.voiceroom

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.ns.findyourintroverfriend.util.LocalVeilIsDark

// ─────────────────────────────────────────────────────────────────────────────
//  Whisper Active Screen — theme-aware local design tokens
// ─────────────────────────────────────────────────────────────────────────────

private var _waDark by mutableStateOf(true) // synced from root composable

// Peaceful Serenity — warm parchment · dusty mauve · sage green · muted terracotta
private val WBg            get() = if (_waDark) Color(0xFF0F0F14) else Color(0xFFFBF8F4)  // warm parchment
private val WCard          get() = if (_waDark) Color(0xFF17171E) else Color(0xFFFFFFFF)  // card surface
private val WCardAlt       get() = if (_waDark) Color(0xFF1E1E26) else Color(0xFFF4F0EB)  // card alt
private val WCardHigh      get() = if (_waDark) Color(0xFF25252E) else Color(0xFFECE7DF)  // card highlight
private val WBorder        get() = if (_waDark) Color(0xFF2C2C34) else Color(0xFFE8E3DC)  // soft border
private val WAccent               = Color(0xFFA07C8C)   // dusty mauve
private val WAccentBg      get() = if (_waDark) Color(0xFF251520) else Color(0xFFF7EFF3)  // mauve tint
private val WAccentText           = Color(0xFF9A6E7E)   // mauve readable
private val WGreen                = Color(0xFF7A9E87)   // sage green
private val WGreenBg       get() = if (_waDark) Color(0xFF0E1E13) else Color(0xFFEFF5F1)  // sage tint
private val WRed                  = Color(0xFFBF6B6B)   // muted terracotta
private val WRedBg         get() = if (_waDark) Color(0xFF1E0F0F) else Color(0xFFF5EEED)  // terracotta tint
private val WGold                 = Color(0xFFBFA882)   // warm sand (cookie accent)
private val WGoldSoft      get() = if (_waDark) Color(0xFF1A1610) else Color(0xFFF7F3EC)  // gold tint
private val WTextPrimary   get() = if (_waDark) Color(0xFFE0DDD8) else Color(0xFF2C2825)  // warm near-black
private val WTextSecondary get() = if (_waDark) Color(0xFF918E8A) else Color(0xFF7A7470)  // warm stone
private val WTextTertiary  get() = if (_waDark) Color(0xFF5A5855) else Color(0xFF9E9A96)  // muted stone
private val WTextDisabled  get() = if (_waDark) Color(0xFF3A3835) else Color(0xFFC4BFBA)  // disabled warm

// ─────────────────────────────────────────────────────────────────────────────
//  Voice Room Active Screen — Whisper edition
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VoiceRoomActiveScreen(
    roomId : String,
    vm     : VoiceRoomViewModel = viewModel(),
    onBack : () -> Unit = {},
) {
    val state   by vm.activeState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val myUid   = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val isHost  = state.room?.hostUid == myUid
    val haptic  = LocalHapticFeedback.current

    // Sync dark/light mode
    val veilIsDark = LocalVeilIsDark.current
    SideEffect { _waDark = veilIsDark }

    // Connect LiveKit on first entry (skipped if PENDING_APPROVAL — wait for host approval)
    LaunchedEffect(roomId) {
        if (state.phase != RoomPhase.PENDING_APPROVAL) {
            vm.connectToLiveKit(roomId, context)
        }
    }

    // When host approves a locked room, phase flips PENDING_APPROVAL → CONNECTING
    var hasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        if (state.phase == RoomPhase.CONNECTING && !hasConnected) {
            hasConnected = true
            vm.connectToLiveKit(roomId, context)
        }
    }

    BackHandler { vm.leaveRoom(isHost) }

    LaunchedEffect(state.phase) {
        if (state.phase == RoomPhase.LEFT) onBack()
    }

    // Cookie extension dialog
    if (state.showExtendDialog && isHost) {
        WExtendDialog(
            cookieBalance = state.cookieBalance,
            roomExtCount  = state.room?.extensionCount ?: 0,
            isExtending   = state.isExtending,
            onExtend      = { vm.extendRoom(roomId) },
            onDismiss     = { vm.dismissExtendDialog(); vm.leaveRoom(isHost) },
        )
    }

    // Host panel
    var showHostPanel by remember { mutableStateOf(false) }
    if (showHostPanel && isHost) {
        WHostPanel(
            state     = state,
            myUid     = myUid,
            roomId    = roomId,
            vm        = vm,
            onDismiss = { showHostPanel = false },
        )
    }

    // Invite sheet state
    val inviteSheetOpen   by vm.inviteSheetOpen.collectAsStateWithLifecycle()
    val inviteSentSnack   by vm.inviteSentSnack.collectAsStateWithLifecycle()

    // Check if broadcast was already used for this room
    LaunchedEffect(roomId) { vm.checkBroadcastUsed(roomId) }

    if (inviteSheetOpen && isHost) {
        WhisperInviteSheet(
            roomId    = roomId,
            vm        = vm,
            onDismiss = { vm.closeInviteSheet() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WBg)
    ) {
        when (state.phase) {
            RoomPhase.CONNECTING       -> WConnectingOverlay("Joining Whisper room…")
            RoomPhase.PENDING_APPROVAL -> WPendingOverlay(onCancel = { vm.leaveRoom(false) })
            RoomPhase.REJECTED         -> WRejectedOverlay(onBack = onBack)
            RoomPhase.ERROR            -> WErrorOverlay(msg = state.error ?: "Connection failed", onBack = onBack)
            RoomPhase.EXPIRED          -> WExpiredOverlay(onBack = { vm.leaveRoom(isHost) })
            RoomPhase.LEFT             -> Unit
            RoomPhase.ACTIVE,
            RoomPhase.EXPIRING -> {
                WActiveContent(
                    state        = state,
                    myUid        = myUid,
                    isHost       = isHost,
                    haptic       = haptic,
                    roomId       = roomId,
                    onMute       = { vm.toggleMic() },
                    onLeave      = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.leaveRoom(isHost) },
                    onRaiseHand  = { if (state.hasRaisedHand) vm.lowerHand(roomId) else vm.raiseHand(roomId) },
                    onHostPanel  = { showHostPanel = true },
                    onLockToggle = { vm.setRoomLocked(roomId, !(state.room?.isLocked ?: false)) },
                    onInvite     = { vm.openInviteSheet() },
                    vm           = vm,
                )
            }
        }

        // Error / kicked snackbar
        state.error?.takeIf { it.isNotBlank() }?.let { errMsg ->
            Snackbar(
                modifier       = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = WCard,
                contentColor   = WTextPrimary,
                action = {
                    TextButton(onClick = onBack) {
                        Text("Leave", color = WRed, fontSize = 12.sp)
                    }
                }
            ) { Text(errMsg, fontSize = 12.sp) }
        }

        // Invite sent snackbar
        if (inviteSentSnack) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2500)
                vm.clearInviteSentSnack()
            }
            Snackbar(
                modifier       = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = WCard,
                contentColor   = WTextPrimary,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = WGreen, modifier = Modifier.size(16.dp))
                    Text("Invites sent!", fontSize = 13.sp, color = WTextPrimary)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Main active content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WActiveContent(
    state       : VoiceRoomActiveUiState,
    myUid       : String,
    isHost      : Boolean,
    haptic      : androidx.compose.ui.hapticfeedback.HapticFeedback,
    roomId      : String,
    onMute      : () -> Unit,
    onLeave     : () -> Unit,
    onRaiseHand : () -> Unit,
    onHostPanel : () -> Unit,
    onLockToggle: () -> Unit,
    onInvite    : () -> Unit,
    vm          : VoiceRoomViewModel,
) {
    val room = state.room ?: return

    Column(
        Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WLiveBadge()
            Spacer(Modifier.weight(1f))
            if (room.isLocked) {
                Icon(Icons.Rounded.Lock, null, tint = WTextTertiary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "${room.participantCount}/${room.maxParticipants}",
                color = WTextTertiary, fontSize = 12.sp,
            )
            // Invite button — host only
            if (isHost) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(WAccentBg)
                        .border(0.5.dp, WAccent.copy(0.4f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onInvite)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment      = Alignment.CenterVertically,
                        horizontalArrangement  = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = WAccent, modifier = Modifier.size(13.dp))
                        Text("Invite", fontSize = 11.sp, color = WAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Room header ──────────────────────────────────────────────────────
        Text(
            room.topic.ifBlank { "Whisper Room" },
            fontSize   = 19.sp,
            fontWeight = FontWeight.Bold,
            color      = WTextPrimary,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.padding(horizontal = 28.dp),
            lineHeight = 26.sp,
        )
        if (room.tags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                room.tags.joinToString(" · "),
                fontSize  = 11.sp,
                color     = WTextTertiary,
                textAlign = TextAlign.Center,
            )
        }
        if (room.description.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                room.description,
                fontSize  = 11.sp,
                color     = WTextDisabled,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 36.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Join requests panel (host only) ──────────────────────────────────
        if (isHost && room.joinRequests.isNotEmpty()) {
            WJoinRequestsBar(
                requests  = room.joinRequests,
                onApprove = { uid -> vm.approveJoinRequest(roomId, uid) },
                onReject  = { uid -> vm.rejectJoinRequest(roomId, uid) },
                modifier  = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
        }

        // ── Participant orbs ─────────────────────────────────────────────────
        WParticipantGrid(
            room           = room,
            activeSpeakers = state.activeSpeakers,
            myUid          = myUid,
            isMuted        = state.isMuted,
            aliases        = state.participantAliases,
            emojis         = state.participantEmojis,
            modifier       = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
        )

        // ── Raised hands queue (host only) ───────────────────────────────────
        if (isHost && room.raisedHands.isNotEmpty()) {
            WRaisedHandsBar(
                hands     = room.raisedHands,
                onPromote = { uid -> vm.promoteToSpeaker(roomId, uid) },
                modifier  = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Timer ────────────────────────────────────────────────────────────
        WTimerBar(
            remainingSecs  = state.remainingSecs,
            totalSecs      = WHISPER_FREE_MINS * 60L + (room.extensionCount * WHISPER_EXTENSION_MINS * 60L),
            extensionCount = room.extensionCount,
            modifier       = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )

        HorizontalDivider(color = WBorder, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        // ── Controls row 1: Raise hand | Mic | Leave ─────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            if (!isHost) {
                WHandButton(
                    raised  = state.hasRaisedHand,
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onRaiseHand() },
                )
            } else {
                Spacer(Modifier.size(54.dp))
            }
            WMicButton(isMuted = state.isMuted, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMute()
            })
            WLeaveButton(isHost = isHost, onClick = onLeave)
        }

        // ── Controls row 2: Host-only ─────────────────────────────────────
        if (isHost) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                WSmallBtn(
                    icon    = Icons.Rounded.AdminPanelSettings,
                    label   = "Manage",
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onHostPanel() },
                )
                WSmallBtn(
                    icon    = if (room.isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    label   = if (room.isLocked) "Unlock" else "Lock",
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLockToggle() },
                )
                if (room.canExtend) {
                    WSmallBtn(
                        icon    = Icons.Rounded.AddCircle,
                        label   = "+15m",
                        onClick = { vm.showExtendDialog() },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Join Requests bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WJoinRequestsBar(
    requests : List<JoinRequest>,
    onApprove: (String) -> Unit,
    onReject : (String) -> Unit,
    modifier : Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WCard)
            .border(1.dp, WAccent.copy(0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${requests.size} join request${if (requests.size != 1) "s" else ""} waiting",
                fontSize = 11.sp, color = WAccentText, fontWeight = FontWeight.SemiBold,
            )
            requests.take(3).forEach { req ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(req.emoji, fontSize = 15.sp)
                    Text(req.alias, color = WTextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(WGreenBg)
                            .border(0.5.dp, WGreen.copy(0.3f), RoundedCornerShape(7.dp))
                            .clickable { onApprove(req.uid) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("Let in", color = WGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(WRedBg)
                            .border(0.5.dp, WRed.copy(0.3f), RoundedCornerShape(7.dp))
                            .clickable { onReject(req.uid) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("Deny", color = WRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Raised Hands bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WRaisedHandsBar(
    hands    : List<RaisedHand>,
    onPromote: (String) -> Unit,
    modifier : Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WCard)
            .border(1.dp, WGreen.copy(0.2f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.PanTool, null, tint = WGreen, modifier = Modifier.size(12.dp))
                Text(
                    "Voice queue (${hands.size})",
                    fontSize = 11.sp, color = WGreen, fontWeight = FontWeight.SemiBold,
                )
            }
            hands.take(4).forEachIndexed { idx, hand ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${idx + 1}.", color = WTextDisabled, fontSize = 10.sp, modifier = Modifier.width(16.dp))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(WCardAlt)
                            .border(0.5.dp, WBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(hand.emoji, fontSize = 12.sp)
                    }
                    Text(hand.alias, color = WTextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(WGreenBg)
                            .border(0.5.dp, WGreen.copy(0.3f), RoundedCornerShape(7.dp))
                            .clickable { onPromote(hand.uid) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("Mic on", color = WGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Participant Orb Grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WParticipantGrid(
    room           : VoiceRoom,
    activeSpeakers : Set<String>,
    myUid          : String,
    isMuted        : Boolean,
    aliases        : Map<String, String>,
    emojis         : Map<String, String>,
    modifier       : Modifier = Modifier,
) {
    val inf      = rememberInfiniteTransition(label = "orb")
    val ringPulse by inf.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rp",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        val displayed = room.participantUids.take(6)
        val cols      = if (displayed.size <= 2) 2 else if (displayed.size <= 4) 2 else 3

        Column(
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            displayed.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { uid ->
                        val isSpeaking = activeSpeakers.any { it == uid }
                        val isMe       = uid == myUid
                        val isH        = uid == room.hostUid
                        val isSpeaker  = uid in room.speakerUids

                        val alias = if (isMe) "You" else aliases[uid]?.ifBlank { "Wanderer" } ?: "Wanderer"
                        val emoji = if (isH) room.hostEmoji.ifBlank { "🌙" } else emojis[uid]?.ifBlank { "🌙" } ?: "🌙"

                        WOrb(
                            emoji      = emoji,
                            label      = alias,
                            roleLabel  = when { isH -> "Host"; isSpeaker -> "Speaker"; else -> "" },
                            isSpeaking = isSpeaking && !(isMe && isMuted),
                            isHost     = isH,
                            ringPulse  = ringPulse,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WOrb(
    emoji     : String,
    label     : String,
    roleLabel : String,
    isSpeaking: Boolean,
    isHost    : Boolean,
    ringPulse : Float,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Speaking ring
            if (isSpeaking) {
                Box(
                    Modifier.size(80.dp).clip(CircleShape)
                        .background(WAccent.copy(ringPulse * 0.18f))
                )
            }
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(WCard)
                    .border(
                        width = if (isSpeaking) 1.5.dp else 1.dp,
                        color = when {
                            isSpeaking -> WAccent.copy(ringPulse)
                            isHost     -> WAccent.copy(0.5f)
                            else       -> WBorder
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Emoji shown in orb — keep as is, it's user-chosen identity
                Text(emoji, fontSize = 24.sp)
            }

            if (isHost) {
                Box(
                    Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 1.dp, y = (-1).dp)
                        .clip(CircleShape)
                        .background(WAccentBg)
                        .border(0.5.dp, WAccent.copy(0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = "Host",
                        tint = WAccent,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }
        Text(label, color = WTextSecondary, fontSize = 11.sp, fontWeight = if (isHost) FontWeight.SemiBold else FontWeight.Normal)
        if (roleLabel.isNotBlank()) {
            Text(roleLabel, color = WAccentText.copy(0.7f), fontSize = 9.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Timer bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WTimerBar(
    remainingSecs  : Long,
    totalSecs      : Long,
    extensionCount : Int,
    modifier       : Modifier = Modifier,
) {
    val progress  = if (totalSecs == 0L) 1f else (remainingSecs.toFloat() / totalSecs.toFloat()).coerceIn(0f, 1f)
    val animProg  by animateFloatAsState(progress, tween(700), label = "timer")
    val isUrgent  = remainingSecs in 1L..180L

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = WTextTertiary, modifier = Modifier.size(12.dp))
                Text("Room time", color = WTextTertiary, fontSize = 11.sp)
                if (extensionCount > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(5.dp)).background(WGreenBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) { Text("+${extensionCount}× extended", color = WGreen, fontSize = 9.sp) }
                }
            }
            Text(
                if (remainingSecs <= 0L) "Ending…" else "%02d:%02d left".format(remainingSecs / 60, remainingSecs % 60),
                color      = if (isUrgent) WRed else WTextSecondary,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(WCardAlt)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animProg)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (isUrgent) WRed else WAccent)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Control buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WMicButton(isMuted: Boolean, onClick: () -> Unit) {
    val inf   = rememberInfiniteTransition(label = "mic")
    val pulse by inf.animateFloat(1f, 1.06f, infiniteRepeatable(tween(800), RepeatMode.Reverse), "mp")

    Box(
        Modifier
            .size(68.dp)
            .scale(if (!isMuted) pulse else 1f)
            .clip(CircleShape)
            .background(if (!isMuted) WAccent else WCardAlt)
            .border(1.dp, if (!isMuted) WAccent.copy(0.5f) else WBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (!isMuted) Icons.Rounded.Mic else Icons.Rounded.MicOff,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            tint     = if (!isMuted) Color.White else WTextTertiary,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun WLeaveButton(isHost: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(WRed.copy(0.12f))
            .border(1.dp, WRed.copy(0.4f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Rounded.CallEnd, null, tint = WRed, modifier = Modifier.size(22.dp))
            Text(if (isHost) "End" else "Leave", color = WRed, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WHandButton(raised: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (raised) WGreenBg else WCardAlt)
            .border(1.dp, if (raised) WGreen.copy(0.5f) else WBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (raised) Icons.Rounded.PanTool else Icons.Rounded.BackHand,
            contentDescription = if (raised) "Lower hand" else "Raise hand",
            tint = if (raised) WGreen else WTextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun WSmallBtn(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    label  : String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(WCard).border(1.dp, WBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = WTextTertiary, modifier = Modifier.size(17.dp))
        }
        Text(label, color = WTextTertiary, fontSize = 9.sp, textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LIVE badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WLiveBadge() {
    val inf = rememberInfiniteTransition(label = "lb")
    val a   by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "la")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(WGreenBg)
            .border(0.5.dp, WGreen.copy(0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(WGreen.copy(a)))
        Text("LIVE", color = WGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Host Panel (bottom sheet)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WHostPanel(
    state    : VoiceRoomActiveUiState,
    myUid    : String,
    roomId   : String,
    vm       : VoiceRoomViewModel,
    onDismiss: () -> Unit,
) {
    val room = state.room ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = WCard,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 3.dp).clip(CircleShape).background(WTextDisabled))
            }
        }
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Manage Room", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WTextPrimary)
                Text(
                    "${room.participantCount} participants",
                    fontSize = 11.sp, color = WTextTertiary,
                )
            }

            HorizontalDivider(color = WBorder, modifier = Modifier.padding(vertical = 2.dp))

            room.participantUids.forEach { uid ->
                if (uid == myUid) return@forEach
                val alias    = state.participantAliases[uid]?.ifBlank { "Wanderer" } ?: "Wanderer"
                val emoji    = state.participantEmojis[uid]?.ifBlank { "🌙" } ?: "🌙"
                val hostMuted = uid in room.mutedByHost
                val isSpeaker = uid in room.speakerUids

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WCard)
                        .border(1.dp, WBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(emoji, fontSize = 18.sp)
                    Column(Modifier.weight(1f)) {
                        Text(alias, color = WTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                hostMuted -> "Muted by you"
                                isSpeaker -> "Speaker"
                                else      -> "Listener"
                            },
                            color = WTextTertiary, fontSize = 10.sp,
                        )
                    }
                    // Mute toggle
                    WHostAction(
                        label = if (hostMuted) "Unmute" else "Mute",
                        bg    = if (hostMuted) WGreenBg else WAccentBg,
                        fg    = if (hostMuted) WGreen else WAccentText,
                        onClick = {
                            if (hostMuted) vm.unmuteParticipant(roomId, uid)
                            else vm.muteParticipant(roomId, uid)
                        }
                    )
                    // Speaker toggle
                    WHostAction(
                        label = if (isSpeaker) "Demote" else "Speaker",
                        bg    = WCardAlt,
                        fg    = WTextSecondary,
                        onClick = {
                            if (isSpeaker) vm.demoteSpeaker(roomId, uid)
                            else vm.promoteToSpeaker(roomId, uid)
                        }
                    )
                    // Kick
                    WHostAction(
                        label = "Kick",
                        bg    = WRedBg,
                        fg    = WRed,
                        onClick = { vm.kickParticipant(roomId, uid) }
                    )
                }
            }

            if (room.participantUids.size <= 1) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No other participants yet", color = WTextTertiary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun WHostAction(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cookie Extension Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WExtendDialog(
    cookieBalance: Int,
    roomExtCount : Int,
    isExtending  : Boolean,
    onExtend     : () -> Unit,
    onDismiss    : () -> Unit,
) {
    val canAfford = cookieBalance >= WHISPER_EXTENSION_COOKIES
    val extLeft   = WHISPER_MAX_EXTENSIONS - roomExtCount

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(WCard)
                .border(1.dp, WBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val inf = rememberInfiniteTransition(label = "cd")
                val sc  by inf.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), "cds")
                Box(
                    Modifier.size(56.dp).scale(sc).clip(CircleShape)
                        .background(WGoldSoft)
                        .border(1.dp, WGold.copy(0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Cookie, null, tint = WGold, modifier = Modifier.size(28.dp))
                }

                Text("Room Ending Soon", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WTextPrimary)
                Text(
                    "Extend by ${WHISPER_EXTENSION_MINS} more minutes?\n$extLeft extension${if (extLeft != 1) "s" else ""} remaining.",
                    fontSize  = 12.sp,
                    color     = WTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                )

                // Balance row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(WCardAlt)
                        .border(0.5.dp, WBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Your cookies", color = WTextTertiary, fontSize = 12.sp)
                    Text(
                        "$cookieBalance cookies",
                        color      = if (canAfford) WGreen else WRed,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Cost", color = WTextTertiary, fontSize = 12.sp)
                    Text("$WHISPER_EXTENSION_COOKIES cookies", color = WTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Extend button
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canAfford) WAccent else WCardAlt)
                        .border(0.5.dp, if (canAfford) Color.Transparent else WBorder, RoundedCornerShape(12.dp))
                        .clickable(enabled = canAfford && !isExtending, onClick = onExtend)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isExtending) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 1.5.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            if (canAfford) "Extend +${WHISPER_EXTENSION_MINS} min" else "Not enough cookies",
                            color      = if (canAfford) Color.White else WTextDisabled,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("End Room", color = WTextTertiary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  State Overlays
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WConnectingOverlay(msg: String = "Joining room…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("🫧", fontSize = 44.sp)
            CircularProgressIndicator(color = WAccent, strokeWidth = 1.5.dp, modifier = Modifier.size(28.dp))
            Text(msg, color = WTextTertiary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun WPendingOverlay(onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(WCard)
                .border(1.dp, WBorder, RoundedCornerShape(22.dp))
                .padding(28.dp),
        ) {
            val inf = rememberInfiniteTransition(label = "pend")
            val sc  by inf.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), "ps")
            Box(
                Modifier.size(56.dp).scale(sc).clip(CircleShape)
                    .background(WAccentBg)
                    .border(1.dp, WAccent.copy(0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = WAccent, modifier = Modifier.size(24.dp))
            }
            Text("Waiting for approval", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WTextPrimary)
            Text(
                "The host will accept your request shortly.",
                fontSize = 12.sp, color = WTextSecondary, textAlign = TextAlign.Center, lineHeight = 19.sp,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WCardAlt)
                    .border(0.5.dp, WBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = WTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun WRejectedOverlay(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(WCard)
                .border(1.dp, WBorder, RoundedCornerShape(22.dp))
                .padding(28.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(WRedBg)
                    .border(1.dp, WRed.copy(0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PersonOff, null, tint = WRed, modifier = Modifier.size(26.dp))
            }
            Text("Request Declined", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WTextPrimary)
            Text(
                "The host didn't let you in this time.",
                fontSize = 12.sp, color = WTextSecondary, textAlign = TextAlign.Center,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WAccent)
                    .clickable(onClick = onBack)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Back to Rooms", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun WExpiredOverlay(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(WCard)
                .border(1.dp, WBorder, RoundedCornerShape(22.dp))
                .padding(28.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(WCardAlt)
                    .border(1.dp, WBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.HourglassEmpty, null, tint = WTextTertiary, modifier = Modifier.size(26.dp))
            }
            Text("Time's Up", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WTextPrimary)
            Text(
                "This Whisper room has ended.\nHope it was a good conversation.",
                fontSize = 12.sp, color = WTextSecondary, textAlign = TextAlign.Center, lineHeight = 19.sp,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WAccent)
                    .clickable(onClick = onBack)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Back to Rooms", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun WErrorOverlay(msg: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(36.dp),
        ) {
            Text("⚠️", fontSize = 38.sp)
            Text("Connection Failed", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WTextPrimary)
            Text(msg, fontSize = 12.sp, color = WTextTertiary, textAlign = TextAlign.Center, lineHeight = 18.sp)
            TextButton(onClick = onBack) {
                Text("← Back to Lobby", color = WAccentText, fontSize = 13.sp)
            }
        }
    }
}
