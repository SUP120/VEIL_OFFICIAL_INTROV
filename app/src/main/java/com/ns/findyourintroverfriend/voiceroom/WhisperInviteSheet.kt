package com.ns.findyourintroverfriend.voiceroom

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.util.LocalVeilIsDark

// ─────────────────────────────────────────────────────────────────────────────
//  Whisper Invite Sheet — host-only bottom sheet to invite connections
//  Two tabs:  0 = Feed Friends (source=friend)   1 = Swipe Matches (source=swipe)
//  Plus a one-time "Broadcast to all users" section at the bottom.
// ─────────────────────────────────────────────────────────────────────────────

private var _invDark by androidx.compose.runtime.mutableStateOf(true)

private val IvBg       get() = if (_invDark) Color(0xFF111115) else Color(0xFFFAFAFC)
private val IvCard     get() = if (_invDark) Color(0xFF1C1C24) else Color(0xFFFFFFFF)
private val IvBorder   get() = if (_invDark) Color(0x22FFFFFF) else Color(0x14000000)
private val IvPrimary  get() = if (_invDark) Color(0xFFF0F0F8) else Color(0xFF141420)
private val IvSecond   get() = if (_invDark) Color(0xFF888896) else Color(0xFF666676)
private val IvAccent           = Color(0xFF8B7FE0)
private val IvAccentBg get() = if (_invDark) Color(0x208B7FE0) else Color(0x148B7FE0)
private val IvGreen            = Color(0xFF2EB87A)
private val IvGreenBg  get() = if (_invDark) Color(0x162EB87A) else Color(0x102EB87A)
private val IvSelected get() = if (_invDark) Color(0x308B7FE0) else Color(0x1A8B7FE0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperInviteSheet(
    roomId       : String,
    vm           : VoiceRoomViewModel,
    onDismiss    : () -> Unit
) {
    // Sync theme
    val dark = LocalVeilIsDark.current
    SideEffect { _invDark = dark }

    val connections by vm.inviteConnections.collectAsStateWithLifecycle()
    val isLoading   by vm.inviteLoading.collectAsStateWithLifecycle()
    val broadcastUsed by vm.broadcastUsed.collectAsStateWithLifecycle()

    // Selected UIDs
    var selectedUids by remember { mutableStateOf(setOf<String>()) }
    // Active tab: 0=Feed Friends, 1=Swipe Matches
    var activeTab by remember { mutableIntStateOf(0) }

    val feedFriends  = remember(connections) { connections.filter { it.source == "friend" } }
    val swipeMatches = remember(connections) { connections.filter { it.source == "swipe"  } }
    val listToShow   = if (activeTab == 0) feedFriends else swipeMatches

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = IvBg,
        dragHandle        = null,
        shape             = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            // ── Handle bar ───────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(IvBorder)
                )
            }

            // ── Header ───────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Invite to Whisper",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = IvPrimary
                    )
                    if (selectedUids.isNotEmpty()) {
                        Text(
                            "${selectedUids.size} selected",
                            fontSize = 12.sp,
                            color    = IvAccent
                        )
                    } else {
                        Text(
                            "Select people to invite",
                            fontSize = 12.sp,
                            color    = IvSecond
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, tint = IvSecond, modifier = Modifier.size(20.dp))
                }
            }

            // ── Tab row ──────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InviteTab(
                    label    = "Feed Friends",
                    count    = feedFriends.size,
                    selected = activeTab == 0,
                    onClick  = { activeTab = 0 }
                )
                InviteTab(
                    label    = "Swipe Matches",
                    count    = swipeMatches.size,
                    selected = activeTab == 1,
                    onClick  = { activeTab = 1 }
                )
            }

            HorizontalDivider(
                color     = IvBorder,
                thickness = 0.5.dp,
                modifier  = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            // ── Connection list ───────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading && connections.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement   = Arrangement.Center,
                        horizontalAlignment   = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = IvAccent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Loading connections…", fontSize = 13.sp, color = IvSecond)
                    }
                } else if (listToShow.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 32.dp),
                        verticalArrangement   = Arrangement.Center,
                        horizontalAlignment   = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.PersonOff,
                            null,
                            tint     = IvBorder,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (activeTab == 0)
                                "No Feed friends yet. Connect with people in the Introvert Feed!"
                            else
                                "No Swipe matches yet. Start swiping to find matches!",
                            fontSize  = 13.sp,
                            color     = IvSecond,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding       = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement  = Arrangement.spacedBy(6.dp)
                    ) {
                        items(listToShow, key = { it.uid }) { user ->
                            val isSelected = user.uid in selectedUids
                            InviteUserRow(
                                user       = user,
                                isSelected = isSelected,
                                onClick    = {
                                    selectedUids = if (isSelected)
                                        selectedUids - user.uid
                                    else
                                        selectedUids + user.uid
                                }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }

            // ── Broadcast section ─────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                HorizontalDivider(color = IvBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))

                // Broadcast card
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (broadcastUsed) IvCard else IvGreenBg)
                        .border(
                            width  = 1.dp,
                            color  = if (broadcastUsed) IvBorder else IvGreen.copy(0.35f),
                            shape  = RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !broadcastUsed) {
                            vm.sendInvites(roomId, emptyList())
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (broadcastUsed) IvBorder.copy(0.3f) else IvGreen.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (broadcastUsed) Icons.Rounded.NotificationsOff
                                          else Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint     = if (broadcastUsed) IvSecond else IvGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (broadcastUsed) "Broadcast Sent" else "Notify All Veil Users",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (broadcastUsed) IvSecond else IvPrimary
                        )
                        Text(
                            if (broadcastUsed) "You already broadcast this room — one time only"
                            else "Ping every user on Veil once. They'll get a push notification.",
                            fontSize = 11.sp,
                            color    = IvSecond,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!broadcastUsed) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            null,
                            tint     = IvGreen.copy(0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Send selected invites button
                AnimatedVisibility(visible = selectedUids.isNotEmpty()) {
                    Button(
                        onClick  = { vm.sendInvites(roomId, selectedUids.toList()) },
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = IvAccent)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color    = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Send Invite to ${selectedUids.size} ${if (selectedUids.size == 1) "person" else "people"}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp,
                                color      = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.navigationBars))
            }
        }
    }
}

// ── Tab chip ──────────────────────────────────────────────────────────────────
@Composable
private fun InviteTab(
    label    : String,
    count    : Int,
    selected : Boolean,
    onClick  : () -> Unit
) {
    val bg     = if (selected) IvAccentBg else Color.Transparent
    val border = if (selected) IvAccent.copy(0.4f) else IvBorder
    val text   = if (selected) IvAccent else IvSecond

    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment      = Alignment.CenterVertically,
        horizontalArrangement  = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = text)
        if (count > 0) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) IvAccent else IvBorder)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    count.toString(),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (selected) Color.White else IvSecond
                )
            }
        }
    }
}

// ── Individual user row ───────────────────────────────────────────────────────
@Composable
private fun InviteUserRow(
    user       : VoiceRoomRepository.InvitableUser,
    isSelected : Boolean,
    onClick    : () -> Unit
) {
    val context = LocalContext.current
    val bg      = if (isSelected) IvSelected else IvCard
    val border  = if (isSelected) IvAccent.copy(0.4f) else IvBorder

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(IvBorder)
        ) {
            if (user.photoUrl.isNotBlank()) {
                AsyncImage(
                    model             = user.photoUrl,
                    contentDescription = user.alias,
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier.fillMaxSize()
                )
            } else if (user.avatarRes.isNotBlank()) {
                val resId = context.resources.getIdentifier(
                    user.avatarRes, "drawable", context.packageName
                )
                if (resId != 0) {
                    Image(
                        painter            = painterResource(id = resId),
                        contentDescription = user.alias,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            user.alias.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = IvAccent
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        user.alias.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = IvAccent
                    )
                }
            }
        }

        // Name + source
        Column(Modifier.weight(1f)) {
            Text(
                user.alias,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = IvPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                if (user.source == "friend") "Feed friend" else "Swipe match",
                fontSize = 11.sp,
                color    = IvSecond
            )
        }

        // Checkmark
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) IvAccent else IvBorder.copy(0.5f))
                .border(1.dp, if (isSelected) IvAccent else IvBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    null,
                    tint     = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
