package com.ns.findyourintroverfriend.groupchat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ── Theme tokens ──────────────────────────────────────────────────────────────
private var _gcDark by mutableStateOf(true)

private val GcBg          get() = if (_gcDark) Color(0xFF0D0D0F) else Color(0xFFF4F4F6)
private val GcCard        get() = if (_gcDark) Color(0xFF17171C) else Color(0xFFFFFFFF)
private val GcBorder      get() = if (_gcDark) Color(0xFF252530) else Color(0xFFE2E2E8)
private val GcPrimary     get() = if (_gcDark) Color(0xFFEEEEF4) else Color(0xFF0E0E14)
private val GcSub         get() = if (_gcDark) Color(0xFF8A8A96) else Color(0xFF6C6C78)
private val GcMuted       get() = if (_gcDark) Color(0xFF44444C) else Color(0xFFC0C0C8)
private val GcMyBubble    get() = if (_gcDark) Color(0xFF242430) else Color(0xFF1A1A1A)
private val GcTheirBubble get() = if (_gcDark) Color(0xFF1E1E26) else Color(0xFFEEEEF2)
private val GcMyText      get() = Color(0xFFE8E8F0)
private val GcTheirText   get() = if (_gcDark) Color(0xFFDDDDE8) else Color(0xFF0E0E14)
private val GcInputBg     get() = if (_gcDark) Color(0xFF17171C) else Color(0xFFFFFFFF)
private val GcAccent      get() = if (_gcDark) Color(0xFFEEEEF4) else Color(0xFF0E0E14)
private val GcReplyBar    get() = if (_gcDark) Color(0xFF13131A) else Color(0xFFF0F0F6)

// ── Draw helpers ──────────────────────────────────────────────────────────────
private fun Modifier.bottomBorder(w: Dp, c: Color) = drawBehind {
    drawLine(c, Offset(0f, size.height), Offset(size.width, size.height), w.toPx())
}
private fun Modifier.topBorder(w: Dp, c: Color) = drawBehind {
    drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), w.toPx())
}
private fun Modifier.startBorder(w: Dp, c: Color) = drawBehind {
    drawLine(c, Offset(0f, 0f), Offset(0f, size.height), w.toPx())
}

// ── Avatar palette (deterministic per-uid) ────────────────────────────────────
private val AVATAR_COLORS = listOf(
    Color(0xFF3D3D5C), Color(0xFF3D5C3D), Color(0xFF5C3D3D),
    Color(0xFF3D4D5C), Color(0xFF5C4D3D), Color(0xFF4D3D5C)
)
private fun avatarColor(uid: String) =
    AVATAR_COLORS[uid.hashCode().and(0x7FFFFFFF) % AVATAR_COLORS.size]

// ── Reply state ───────────────────────────────────────────────────────────────
data class ReplyDraft(
    val msgId : String,
    val alias : String,
    val text  : String
)

// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatRoomScreen(
    roomId        : String,
    onBack        : () -> Unit,
    onOpenProfile : (uid: String) -> Unit = {},
    vm            : GroupChatRoomViewModel = viewModel()
) {
    val dark = LocalVeilIsDark.current
    SideEffect { _gcDark = dark }

    LaunchedEffect(roomId) { vm.init(roomId) }

    val room          by vm.room.collectAsStateWithLifecycle()
    val messages      by vm.messages.collectAsStateWithLifecycle()
    val msgLoading    by vm.msgLoading.collectAsStateWithLifecycle()
    val sending       by vm.sending.collectAsStateWithLifecycle()
    val pendingUsers  by vm.pendingUsers.collectAsStateWithLifecycle()
    val inviteUsers   by vm.inviteUsers.collectAsStateWithLifecycle()
    val inviteLoading by vm.inviteLoading.collectAsStateWithLifecycle()
    val userProfiles  by vm.userProfiles.collectAsStateWithLifecycle()

    var draft         by remember { mutableStateOf("") }
    var replyDraft    by remember { mutableStateOf<ReplyDraft?>(null) }
    val keyboard      = LocalSoftwareKeyboardController.current
    val listState     = rememberLazyListState()

    var showInviteSheet  by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMemberList   by remember { mutableStateOf(false) }  // group info screen

    val isCreator = room?.createdBy == vm.myUid
    val isMember  = vm.myUid in (room?.memberUids  ?: emptyList())
    val isPending = vm.myUid in (room?.pendingUids ?: emptyList())

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(GcBg)
            .systemBarsPadding()
            .imePadding()          // ← keyboard slides content up
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(GcCard)
                .bottomBorder(0.6.dp, GcBorder)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.ArrowBack, null, tint = GcPrimary, modifier = Modifier.size(20.dp))
            }

            // Group avatar → opens members list
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GcBorder)
                    .clickable { showMemberList = true },
                contentAlignment = Alignment.Center
            ) { Text("💬", fontSize = 16.sp) }

            // Name + member count → also opens members list
            Column(
                Modifier.weight(1f).clickable { showMemberList = true },
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    room?.name ?: "Group",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = GcPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${room?.memberCount ?: 0} members · tap to view",
                    fontSize = 11.sp, color = GcSub
                )
            }

            if (isMember) {
                IconButton(
                    onClick  = { vm.loadInviteConnections(); showInviteSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, tint = GcSub, modifier = Modifier.size(18.dp))
                }
            }
            if (isCreator) {
                IconButton(
                    onClick  = { showMembersSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ManageAccounts, null, tint = GcSub, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── Pending join requests ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = isCreator && pendingUsers.isNotEmpty(),
            enter   = slideInVertically { -it } + fadeIn(),
            exit    = slideOutVertically { -it } + fadeOut()
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(if (_gcDark) Color(0xFF13131A) else Color(0xFFF8F8FC))
                    .bottomBorder(0.6.dp, GcBorder)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, tint = GcSub, modifier = Modifier.size(12.dp))
                    Text(
                        "${pendingUsers.size} join request${if (pendingUsers.size > 1) "s" else ""}",
                        fontSize = 11.sp, color = GcSub, fontWeight = FontWeight.SemiBold
                    )
                }
                pendingUsers.forEach { (uid, alias) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AvatarCircle(uid = uid, alias = alias, size = 28.dp, onClick = { onOpenProfile(uid) })
                        Text(alias, fontSize = 13.sp, color = GcPrimary, modifier = Modifier.weight(1f))
                        SmallBtn("Accept", GcPrimary, GcBg) { vm.approveJoin(uid) }
                        SmallBtn("Deny", GcBorder, GcSub) { vm.denyJoin(uid) }
                    }
                }
            }
        }

        // ── Access gate ───────────────────────────────────────────────────────
        if (!isMember && !isPending) {
            val r = room
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (r == null) {
                    CircularProgressIndicator(color = GcSub, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)
                    ) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape)
                                .background(GcCard).border(1.dp, GcBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("💬", fontSize = 30.sp) }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(r.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GcPrimary, textAlign = TextAlign.Center)
                            if (r.description.isNotBlank()) {
                                Text(r.description, fontSize = 13.sp, color = GcSub, textAlign = TextAlign.Center, lineHeight = 20.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Group, null, tint = GcMuted, modifier = Modifier.size(13.dp))
                                Text("${r.memberCount} members", fontSize = 12.sp, color = GcMuted)
                            }
                        }

                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(GcPrimary)
                                .clickable {
                                    if (r.joinMode == "auto") vm.joinRoom(roomId)
                                    else vm.requestJoin(roomId)
                                }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (r.joinMode == "auto") "Join Group" else "Request to Join",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GcBg
                            )
                        }
                    }
                }
            }
            return@Column
        }

        // ── Pending approval ──────────────────────────────────────────────────
        if (isPending && !isMember) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.HourglassTop, null, tint = GcMuted, modifier = Modifier.size(40.dp))
                    Text("Request Sent", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = GcPrimary)
                    Text("Waiting for the creator to approve.", fontSize = 13.sp, color = GcSub)
                }
            }
            return@Column
        }

        // ── Messages ──────────────────────────────────────────────────────────
        Box(Modifier.weight(1f)) {
            when {
                msgLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GcSub, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = GcMuted, modifier = Modifier.size(44.dp))
                        Text("No messages yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GcSub)
                        Text("Be the first to say something 👋", fontSize = 12.sp, color = GcMuted)
                    }
                }
                else -> LazyColumn(
                    state          = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                        val isMe       = msg.uid == vm.myUid
                        val profile    = userProfiles[msg.uid]
                        val photoUrl   = profile?.second ?: ""
                        val alias      = profile?.first  ?: msg.alias
                        val prevMsg    = messages.getOrNull(index - 1)
                        val nextMsg    = messages.getOrNull(index + 1)
                        val showHeader = !isMe && (prevMsg == null || prevMsg.uid != msg.uid)
                        val isLast     = nextMsg == null || nextMsg.uid != msg.uid
                        val isFirst    = prevMsg == null || prevMsg.uid != msg.uid
                        val topPad     = if (isFirst) 8.dp else 2.dp
                        val bottomPad  = if (isLast)  2.dp else 1.dp

                        SwipeableMessageItem(
                            msg           = msg,
                            isMe          = isMe,
                            alias         = alias,
                            photoUrl      = photoUrl,
                            showAvatar    = !isMe && isLast,
                            showName      = showHeader,
                            topPad        = topPad,
                            bottomPad     = bottomPad,
                            onAvatarClick = { onOpenProfile(msg.uid) },
                            onReply       = {
                                replyDraft = ReplyDraft(
                                    msgId = msg.id,
                                    alias = alias,
                                    text  = msg.text
                                )
                            }
                        )
                    }
                }
            }
        }

        // ── Reply preview bar ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = replyDraft != null,
            enter   = slideInVertically { it } + fadeIn(),
            exit    = slideOutVertically { it } + fadeOut()
        ) {
            val rd = replyDraft
            if (rd != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(GcReplyBar)
                        .topBorder(0.6.dp, GcBorder)
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Accent bar
                    Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(GcSub))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Replying to ${rd.alias}",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GcSub
                        )
                        Text(
                            rd.text,
                            fontSize = 12.sp, color = GcMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { replyDraft = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, null, tint = GcMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ── Input bar ─────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(GcCard)
                .topBorder(0.6.dp, GcBorder)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(GcInputBg)
                    .border(0.8.dp, GcBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value           = draft,
                    onValueChange   = { draft = it },
                    singleLine      = false,
                    maxLines        = 5,
                    textStyle       = TextStyle(fontSize = 14.sp, color = GcPrimary, lineHeight = 20.sp),
                    cursorBrush     = SolidColor(GcPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    decorationBox   = { inner ->
                        if (draft.isEmpty()) Text("Message…", fontSize = 14.sp, color = GcMuted)
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (draft.isNotBlank()) GcAccent else GcCard)
                    .border(0.8.dp, if (draft.isNotBlank()) GcAccent else GcBorder, CircleShape)
                    .clickable(enabled = draft.isNotBlank() && !sending) {
                        val t = draft.trim()
                        if (t.isNotBlank()) {
                            val rd = replyDraft
                            vm.send(
                                text         = t,
                                replyToId    = rd?.msgId ?: "",
                                replyToAlias = rd?.alias ?: "",
                                replyToText  = rd?.text  ?: ""
                            )
                            draft      = ""
                            replyDraft = null
                            keyboard?.hide()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    CircularProgressIndicator(color = GcSub, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        Icons.Rounded.ArrowUpward, null,
                        tint = if (draft.isNotBlank()) GcBg else GcMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = GcCard,
            title = { Text("Delete Room", color = GcPrimary, fontWeight = FontWeight.Bold) },
            text  = {
                Text("This will permanently delete \"${room?.name}\" and all its messages.", color = GcSub, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteRoom { onBack() }; showDeleteDialog = false }) {
                    Text("Delete", color = Color(0xFFD94F4F), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = GcSub) }
            }
        )
    }

    if (showInviteSheet) {
        GroupInviteSheet(
            roomId        = roomId,
            inviteUsers   = inviteUsers,
            isLoading     = inviteLoading,
            onSendInvites = { selected -> vm.sendInvites(roomId, selected) },
            onDismiss     = { showInviteSheet = false }
        )
    }

    if (showMembersSheet) {
        GroupMembersSheet(
            room           = room,
            myUid          = vm.myUid,
            userProfiles   = userProfiles,
            onRemoveMember = { uid -> vm.removeMember(uid) },
            onOpenProfile  = onOpenProfile,
            onDeleteRoom   = { showMembersSheet = false; showDeleteDialog = true },
            onDismiss      = { showMembersSheet = false }
        )
    }

    // ── Group member list (full screen panel) ─────────────────────────────────
    if (showMemberList) {
        GroupMemberListScreen(
            room         = room,
            myUid        = vm.myUid,
            userProfiles = userProfiles,
            onOpenProfile = onOpenProfile,
            onDismiss    = { showMemberList = false }
        )
    }
}

// ── Swipeable message item ────────────────────────────────────────────────────
@Composable
private fun SwipeableMessageItem(
    msg          : GroupMessage,
    isMe         : Boolean,
    alias        : String,
    photoUrl     : String,
    showAvatar   : Boolean,
    showName     : Boolean,
    topPad       : Dp,
    bottomPad    : Dp,
    onAvatarClick: () -> Unit,
    onReply      : () -> Unit
) {
    val swipeOffset     = remember { Animatable(0f) }
    val replyTriggered  = remember { mutableStateOf(false) }
    val scope           = rememberCoroutineScope()
    val REPLY_THRESHOLD = 60f

    LaunchedEffect(replyTriggered.value) {
        if (replyTriggered.value) {
            onReply()
            replyTriggered.value = false
            swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = topPad, bottom = bottomPad)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset.value > REPLY_THRESHOLD) {
                            replyTriggered.value = true
                        } else {
                            scope.launch {
                                swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val newVal = (swipeOffset.value + dragAmount).coerceIn(0f, REPLY_THRESHOLD + 20f)
                        scope.launch { swipeOffset.snapTo(newVal) }
                    }
                )
            }
    ) {
        // Reply icon — appears as message is dragged
        val iconAlpha = (swipeOffset.value / REPLY_THRESHOLD).coerceIn(0f, 1f)
        if (iconAlpha > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GcBorder.copy(alpha = iconAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Reply, null,
                    tint     = GcSub.copy(alpha = iconAlpha),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Message content, offset by drag
        Box(Modifier.offset { IntOffset(swipeOffset.value.roundToInt(), 0) }) {
            GroupMessageItem(
                msg           = msg,
                isMe          = isMe,
                alias         = alias,
                photoUrl      = photoUrl,
                showAvatar    = showAvatar,
                showName      = showName,
                onAvatarClick = onAvatarClick
            )
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────
@Composable
private fun GroupMessageItem(
    msg          : GroupMessage,
    isMe         : Boolean,
    alias        : String,
    photoUrl     : String,
    showAvatar   : Boolean,
    showName     : Boolean,
    onAvatarClick: () -> Unit
) {
    val timeStr = remember(msg.sentAt) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(msg.sentAt.toDate())
    }
    val hasReply = msg.replyToId.isNotBlank()

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom
    ) {
        if (!isMe) {
            Box(Modifier.size(36.dp)) {
                if (showAvatar) {
                    AvatarCircle(uid = msg.uid, alias = alias, photoUrl = photoUrl, size = 34.dp, onClick = onAvatarClick)
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 272.dp)
        ) {
            if (showName) {
                Text(
                    alias,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GcSub,
                    modifier = Modifier.padding(start = 10.dp, bottom = 3.dp)
                )
            }

            Column(
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart    = if (isMe) 18.dp else if (showName) 4.dp else 18.dp,
                            topEnd      = if (isMe) if (showName) 4.dp else 18.dp else 18.dp,
                            bottomStart = if (!isMe && showAvatar) 18.dp else if (!isMe) 4.dp else 18.dp,
                            bottomEnd   = if (isMe && showAvatar) 18.dp else if (isMe) 4.dp else 18.dp
                        )
                    )
                    .background(if (isMe) GcMyBubble else GcTheirBubble)
            ) {
                // Quoted reply block
                if (hasReply) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isMe) Color(0xFF1A1A22) else if (_gcDark) Color(0xFF16161E) else Color(0xFFE4E4EC))
                            .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.width(2.5.dp).fillMaxHeight().background(GcSub).clip(RoundedCornerShape(2.dp)))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                msg.replyToAlias,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GcSub
                            )
                            Text(
                                msg.replyToText,
                                fontSize = 11.sp, color = GcMuted, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // Message text
                Text(
                    msg.text,
                    fontSize = 14.sp,
                    color    = if (isMe) GcMyText else GcTheirText,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Text(
                timeStr,
                fontSize = 10.sp, color = GcMuted,
                modifier = Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp)
            )
        }

        if (isMe) Spacer(Modifier.width(4.dp))
    }
}

// ── Group Member List (full-screen overlay) ───────────────────────────────────
@Composable
private fun GroupMemberListScreen(
    room         : GroupRoom?,
    myUid        : String,
    userProfiles : Map<String, Pair<String, String>>,
    onOpenProfile: (String) -> Unit,
    onDismiss    : () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GcBg)
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(GcCard)
                    .bottomBorder(0.6.dp, GcBorder)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = GcPrimary, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(room?.name ?: "Group", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GcPrimary)
                    Text("${room?.memberCount ?: 0} members", fontSize = 12.sp, color = GcSub)
                }
            }

            // Group info card
            if (room?.description?.isNotBlank() == true) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GcCard)
                        .border(1.dp, GcBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = GcMuted, modifier = Modifier.size(13.dp))
                        Text("About", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GcMuted, letterSpacing = 0.5.sp)
                    }
                    Text(room.description, fontSize = 13.sp, color = GcSub, lineHeight = 19.sp)
                }
            }

            // Section label
            Text(
                "MEMBERS",
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GcMuted,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
            )

            LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val members = room?.memberUids ?: emptyList()
                // Creator first
                val creatorUid  = room?.createdBy ?: ""
                val sorted      = members.sortedByDescending { it == creatorUid }

                items(sorted) { uid ->
                    val isMe      = uid == myUid
                    val isCreator = uid == creatorUid
                    val profile   = userProfiles[uid]
                    val alias     = profile?.first  ?: if (isMe) "You" else "Member"
                    val photoUrl  = profile?.second ?: ""

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GcCard)
                            .border(1.dp, GcBorder, RoundedCornerShape(14.dp))
                            .clickable(enabled = !isMe) { onOpenProfile(uid) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AvatarCircle(
                            uid      = uid,
                            alias    = alias,
                            photoUrl = photoUrl,
                            size     = 44.dp,
                            onClick  = { if (!isMe) onOpenProfile(uid) }
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (isMe) "You" else alias,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GcPrimary
                            )
                            if (!isMe) {
                                Text("Tap to view profile", fontSize = 11.sp, color = GcMuted)
                            }
                        }
                        if (isCreator) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (_gcDark) Color(0xFF1E1E28) else Color(0xFFEEEEF4))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Admin", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = GcSub)
                            }
                        } else if (!isMe) {
                            Icon(Icons.Rounded.ChevronRight, null, tint = GcMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ── Avatar circle ─────────────────────────────────────────────────────────────
@Composable
private fun AvatarCircle(
    uid     : String,
    alias   : String,
    photoUrl: String = "",
    size    : Dp,
    onClick : () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(uid))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model              = photoUrl,
                contentDescription = alias,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            Text(
                alias.take(1).uppercase(),
                fontSize   = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFFD8D8E8)
            )
        }
    }
}

// ── Small button ──────────────────────────────────────────────────────────────
@Composable
private fun SmallBtn(label: String, bg: Color, textColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

// ── Invite bottom sheet ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInviteSheet(
    roomId        : String,
    inviteUsers   : List<GroupInviteUser>,
    isLoading     : Boolean,
    onSendInvites : (List<String>) -> Unit,
    onDismiss     : () -> Unit
) {
    var selected  by remember { mutableStateOf(setOf<String>()) }
    var query     by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allUsers = remember(inviteUsers) { inviteUsers.distinctBy { it.uid }.sortedBy { it.alias.lowercase() } }
    val filtered = remember(allUsers, query) {
        if (query.isBlank()) allUsers
        else allUsers.filter { it.alias.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = GcCard,
        dragHandle       = null,
        shape            = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 38.dp, height = 4.dp).clip(CircleShape).background(GcBorder))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Invite People", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GcPrimary)
                    Text(
                        when {
                            isLoading && allUsers.isEmpty() -> "Loading connections..."
                            selected.isEmpty()             -> "${allUsers.size} connection${if (allUsers.size != 1) "s" else ""}"
                            else                           -> "${selected.size} selected"
                        },
                        fontSize = 12.sp, color = GcSub
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, tint = GcSub, modifier = Modifier.size(20.dp))
                }
            }
            // Search bar
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (_gcDark) Color(0xFF13131A) else Color(0xFFF0F0F6))
                    .border(0.8.dp, GcBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Search, null, tint = GcMuted, modifier = Modifier.size(16.dp))
                BasicTextField(
                    value         = query,
                    onValueChange = { query = it },
                    singleLine    = true,
                    textStyle     = TextStyle(fontSize = 14.sp, color = GcPrimary),
                    cursorBrush   = SolidColor(GcPrimary),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search by name...", fontSize = 14.sp, color = GcMuted)
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotBlank()) {
                    Box(Modifier.size(18.dp).clip(CircleShape).clickable { query = "" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, null, tint = GcMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            HorizontalDivider(color = GcBorder, thickness = 0.5.dp)

            Box(Modifier.weight(1f)) {
                when {
                    isLoading && allUsers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = GcSub, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Finding your connections...", fontSize = 12.sp, color = GcMuted)
                        }
                    }
                    filtered.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.PeopleOutline, null, tint = GcMuted, modifier = Modifier.size(44.dp))
                            Text(
                                if (query.isNotBlank()) "No match for \"$query\"" else "No connections to invite yet",
                                fontSize = 13.sp, color = GcSub, textAlign = TextAlign.Center, lineHeight = 20.sp
                            )
                        }
                    }
                    else -> LazyColumn(
                        contentPadding      = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.uid }) { user ->
                            val isSel = user.uid in selected
                            val badgeText = if (user.source == "friend") "Feed Friend" else "Swipe Match"
                            val badgeBg = if (user.source == "friend")
                                if (_gcDark) Color(0xFF1A2A1A) else Color(0xFFEAF5EA)
                            else
                                if (_gcDark) Color(0xFF1A1A2A) else Color(0xFFEAEAF5)
                            val badgeText2Color = if (user.source == "friend")
                                if (_gcDark) Color(0xFF6EBF6E) else Color(0xFF3A8C3A)
                            else
                                if (_gcDark) Color(0xFF8A8AE8) else Color(0xFF4A4AAA)

                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSel) (if (_gcDark) Color(0xFF1E1E2C) else Color(0xFFE8E8F4))
                                        else Color.Transparent
                                    )
                                    .border(1.dp, if (isSel) GcSub.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(14.dp))
                                    .clickable { selected = if (isSel) selected - user.uid else selected + user.uid }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AvatarCircle(uid = user.uid, alias = user.alias, photoUrl = user.photoUrl, size = 44.dp, onClick = {})
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(user.alias, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GcPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Box(
                                        Modifier.clip(RoundedCornerShape(4.dp)).background(badgeBg).padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(badgeText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = badgeText2Color, letterSpacing = 0.3.sp)
                                    }
                                }
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape)
                                        .background(if (isSel) GcPrimary else Color.Transparent)
                                        .border(1.5.dp, if (isSel) GcPrimary else GcBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSel) Icon(Icons.Rounded.Check, null, tint = GcBg, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                enter   = slideInVertically { it } + fadeIn(),
                exit    = slideOutVertically { it } + fadeOut()
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick  = { onSendInvites(selected.toList()); onDismiss() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GcPrimary)
                    ) {
                        Icon(Icons.Rounded.Send, null, tint = GcBg, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send Invite to ${selected.size} ${if (selected.size == 1) "person" else "people"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GcBg)
                    }
                }
            }
        }
    }
}


// ── Members management sheet (creator) ───────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupMembersSheet(
    room           : GroupRoom?,
    myUid          : String,
    userProfiles   : Map<String, Pair<String, String>>,
    onRemoveMember : (String) -> Unit,
    onOpenProfile  : (String) -> Unit,
    onDeleteRoom   : () -> Unit,
    onDismiss      : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = GcCard,
        dragHandle       = null,
        shape            = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.65f).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 38.dp, height = 4.dp).clip(CircleShape).background(GcBorder))
            }
            Text("Manage Members", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GcPrimary)
            Text("${room?.memberCount ?: 0} members", fontSize = 12.sp, color = GcSub, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(room?.memberUids ?: emptyList()) { uid ->
                    val isMe      = uid == myUid
                    val isCreator = uid == room?.createdBy
                    val profile   = userProfiles[uid]
                    val alias     = profile?.first  ?: if (isMe) "You" else uid.take(8)
                    val photoUrl  = profile?.second ?: ""

                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (_gcDark) Color(0xFF13131A) else Color(0xFFF8F8FC))
                            .clickable { if (!isMe) onOpenProfile(uid) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AvatarCircle(uid = uid, alias = alias, photoUrl = photoUrl, size = 40.dp, onClick = { if (!isMe) onOpenProfile(uid) })
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isMe) "You" else alias, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GcPrimary)
                                if (isCreator) {
                                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(GcBorder).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("Admin", fontSize = 9.sp, color = GcSub, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }
                        if (!isMe) {
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFD94F4F).copy(0.4f), RoundedCornerShape(8.dp))
                                    .clickable { onRemoveMember(uid) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) { Text("Remove", fontSize = 11.sp, color = Color(0xFFD94F4F)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (room?.isSystem != true) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (_gcDark) Color(0xFF1A0909) else Color(0xFFFFF2F2))
                        .border(1.dp, Color(0xFFD94F4F).copy(0.3f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onDeleteRoom)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.DeleteForever, null, tint = Color(0xFFD94F4F), modifier = Modifier.size(18.dp))
                        Text("Delete Room", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD94F4F))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
