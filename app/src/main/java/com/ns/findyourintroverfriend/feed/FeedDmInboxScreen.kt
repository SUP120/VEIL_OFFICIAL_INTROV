package com.ns.findyourintroverfriend.feed

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ns.findyourintroverfriend.swipe.MatchesViewModel
import com.ns.findyourintroverfriend.swipe.SwipeMatch
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens ──────────────────────────────────────────────────────────
private val FBg        = Color(0xFFFFFFFF)
private val FBlack     = Color(0xFF1A1A1A)
private val FGray      = Color(0xFF8E8E8E)
private val FDivider   = Color(0xFFEFEFEF)
private val FBlue      = Color(0xFF0095F6)
private val FLightGray = Color(0xFFF2F2F2)
private val FGreen     = Color(0xFF22C55E)

private val fAvatarColors = listOf(
    Color(0xFFE3F2FD), Color(0xFFFCE4EC), Color(0xFFF3E5F5),
    Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFE0F2F1),
)
private fun fAvatarColor(uid: String) =
    fAvatarColors[kotlin.math.abs(uid.hashCode()) % fAvatarColors.size]

private fun fAgo(ms: Long): String {
    if (ms == 0L) return ""
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000      -> "now"
        d < 3_600_000   -> "${d / 60_000}m"
        d < 86_400_000  -> "${d / 3_600_000}h"
        d < 604_800_000 -> "${d / 86_400_000}d"
        else            -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

// ── Screen ─────────────────────────────────────────────────────────────────
@Composable
fun FeedDmInboxScreen(
    vm           : MatchesViewModel       = viewModel(),
    approachVm   : FeedApproachViewModel  = viewModel(),
    onBack       : () -> Unit             = {},
    onOpenChat   : (String) -> Unit       = {},
    onOpenProfile: (uid: String) -> Unit  = {}
) {
    // Friend matches only (source == "friend") from the shared MatchesViewModel
    val friendMatches  by vm.friendMatches.collectAsStateWithLifecycle()
    val loading        by vm.loading.collectAsStateWithLifecycle()
    val lastMessages   by vm.lastMessages.collectAsStateWithLifecycle()
    val unreadMatchIds by vm.unreadMatchIds.collectAsStateWithLifecycle()
    val incoming       by approachVm.incoming.collectAsStateWithLifecycle()

    val myUid = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(FBg).statusBarsPadding()) {

        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBackIosNew, null, tint = FBlack, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Friends", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FBlack, letterSpacing = (-0.3).sp)
                Text("Introvert Feed connections", fontSize = 11.sp, color = FGray)
            }
        }

        // ── Tabs: Messages | Requests ──────────────────────────────────────
        val reqLabel = if (incoming.isEmpty()) "Requests" else "Requests (${incoming.size})"
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = FBg,
            contentColor     = FBlack,
            indicator        = { tp ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tp[selectedTab]), height = 2.dp, color = FBlack
                )
            },
            divider = {}
        ) {
            listOf("Messages", reqLabel).forEachIndexed { i, label ->
                Tab(
                    selected = selectedTab == i,
                    onClick  = { selectedTab = i },
                    text = {
                        Text(
                            label, fontSize = 14.sp,
                            fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == i) FBlack else FGray
                        )
                    }
                )
            }
        }
        HorizontalDivider(color = FDivider, thickness = 0.8.dp)

        // ── Content ────────────────────────────────────────────────────────
        when (selectedTab) {
            1    -> FRequestsTab(incoming, onOpenProfile, approachVm)
            else -> FMessagesTab(
                matches        = friendMatches,
                loading        = loading,
                lastMessages   = lastMessages,
                unreadMatchIds = unreadMatchIds,
                myUid          = myUid,
                onOpenChat     = { id -> vm.markMatchRead(id); onOpenChat(id) },
                onDelete       = { id -> vm.deleteMatch(id) },
                onBlock        = { m ->
                    val other = if (m.userA == myUid) m.userB else m.userA
                    vm.blockUser(other, m.matchId)
                }
            )
        }
    }
}

// ── Messages tab ────────────────────────────────────────────────────────────
@Composable
private fun FMessagesTab(
    matches        : List<SwipeMatch>,
    loading        : Boolean,
    lastMessages   : Map<String, Pair<String, Long>>,
    unreadMatchIds : Set<String>,
    myUid          : String,
    onOpenChat     : (String) -> Unit,
    onDelete       : (String) -> Unit,
    onBlock        : (SwipeMatch) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val sorted = remember(matches, query, lastMessages, unreadMatchIds) {
        val filtered = if (query.isBlank()) matches
        else matches.filter { m ->
            val alias = if (m.userA == myUid) m.aliasB else m.aliasA
            alias.contains(query.trim(), ignoreCase = true)
        }
        filtered.sortedWith(
            compareByDescending<SwipeMatch> { it.matchId in unreadMatchIds }
                .thenByDescending { lastMessages[it.matchId]?.second ?: it.matchedAt }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Search bar
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FLightGray)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Search, null, tint = FGray, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = FBlack),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search friends…", fontSize = 14.sp, color = FGray)
                    inner()
                },
                modifier = Modifier.weight(1f)
            )
            if (query.isNotEmpty()) {
                Icon(Icons.Rounded.Close, null, tint = FGray,
                    modifier = Modifier.size(16.dp).clickable { query = "" })
            }
        }

        when {
            loading -> FSkeletonList()
            sorted.isEmpty() && query.isNotBlank() -> FEmptyState(
                Icons.Rounded.Search, "No results", "No friends match \"$query\""
            )
            sorted.isEmpty() -> FEmptyState(
                Icons.Rounded.PeopleOutline, "No friends yet",
                "Accept a friendship request\nand they'll appear here."
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(sorted, key = { it.matchId }) { m ->
                    FDmRow(
                        match    = m,
                        myUid    = myUid,
                        lastMsg  = lastMessages[m.matchId],
                        isUnread = m.matchId in unreadMatchIds,
                        onClick  = { onOpenChat(m.matchId) },
                        onDelete = { onDelete(m.matchId) },
                        onBlock  = { onBlock(m) }
                    )
                    HorizontalDivider(color = FDivider, thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 84.dp))
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

// ── Single row ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FDmRow(
    match    : SwipeMatch,
    myUid    : String,
    lastMsg  : Pair<String, Long>?,
    isUnread : Boolean,
    onClick  : () -> Unit,
    onDelete : () -> Unit,
    onBlock  : () -> Unit
) {
    val isA      = match.userA == myUid
    val alias    = (if (isA) match.aliasB else match.aliasA).ifBlank { "Friend" }
    val photo    = if (isA) match.photoB else match.photoA
    val otherUid = if (isA) match.userB  else match.userA
    var showMenu by remember { mutableStateOf(false) }
    var pressed  by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (pressed) Color(0xFFF5F5F5) else FBg, label = "bg"
    )

    Box {
        Row(
            Modifier.fillMaxWidth().background(bg)
                .combinedClickable(onClick = { pressed = true; onClick() }, onLongClick = { showMenu = true })
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box {
                Box(
                    Modifier.size(54.dp).clip(CircleShape)
                        .then(if (isUnread) Modifier.border(2.dp, FBlue, CircleShape) else Modifier)
                        .background(fAvatarColor(otherUid)),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo.isNotBlank()) {
                        AsyncImage(model = photo, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        Text(alias.first().uppercaseChar().toString(),
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                            color = FBlack.copy(0.55f))
                    }
                }
                if (isUnread) {
                    Box(Modifier.size(13.dp).align(Alignment.BottomEnd)
                        .clip(CircleShape).background(Color.White).padding(2.dp)) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(FBlue))
                    }
                }
            }
            // Text
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(alias, fontSize = 15.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        color = FBlack, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Text(fAgo(lastMsg?.second ?: match.matchedAt), fontSize = 11.sp,
                        color = if (isUnread) FBlue else FGray,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(lastMsg?.first ?: "Say hello 👋", fontSize = 13.sp,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isUnread) FBlack else FGray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (isUnread) Box(Modifier.size(8.dp).clip(CircleShape).background(FBlue))
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).widthIn(min = 180.dp)) {
            DropdownMenuItem(
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFE53935), modifier = Modifier.size(17.dp))
                        Text("Delete chat", fontSize = 14.sp, color = Color(0xFFE53935))
                    }
                }, onClick = { showMenu = false; onDelete() })
            HorizontalDivider(color = FDivider)
            DropdownMenuItem(
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Block, null, tint = Color(0xFFE53935), modifier = Modifier.size(17.dp))
                        Text("Block $alias", fontSize = 14.sp, color = Color(0xFFE53935))
                    }
                }, onClick = { showMenu = false; onBlock() })
        }
    }
    LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(100); pressed = false } }
}

// ── Requests tab ────────────────────────────────────────────────────────────
@Composable
private fun FRequestsTab(
    incoming     : List<FriendApproach>,
    onOpenProfile: (String) -> Unit,
    approachVm   : FeedApproachViewModel
) {
    if (incoming.isEmpty()) {
        FEmptyState(Icons.Rounded.Handshake, "No requests yet",
            "When someone sends you a friendship request,\nit shows here.")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(incoming, key = { it.docId }) { req ->
                ApproachRequestRow(
                    request   = req,
                    onView    = { onOpenProfile(req.fromUid) },
                    onAccept  = { approachVm.acceptRequest(req) },
                    onDismiss = { approachVm.dismissRequest(req.docId) }
                )
                HorizontalDivider(color = FDivider, thickness = 0.5.dp)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── Approach Request Row ────────────────────────────────────────────────────
@Composable
private fun ApproachRequestRow(
    request  : FriendApproach,
    onView   : () -> Unit,
    onAccept : () -> Unit,
    onDismiss: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onView)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(fAvatarColor(request.fromUid)),
                contentAlignment = Alignment.Center) {
                if (request.fromPhotoUrl.isNotBlank()) {
                    AsyncImage(model = request.fromPhotoUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(request.fromAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = FBlack.copy(0.6f))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(request.fromAlias.ifBlank { "Anonymous" }, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = FBlack,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Wants to be friends · ${fAgo(request.timestamp)}",
                    fontSize = 12.sp, color = FGray, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(FGreen).clickable(onClick = onAccept).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center) {
                Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(FLightGray).clickable(onClick = onDismiss).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center) {
                Text("Decline", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FGray)
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────────────
@Composable
private fun FEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(32.dp)) {
            Box(Modifier.size(68.dp).clip(CircleShape).background(FLightGray), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = FGray, modifier = Modifier.size(32.dp))
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FBlack)
            Text(message, fontSize = 13.sp, color = FGray, textAlign = TextAlign.Center, lineHeight = 19.sp)
        }
    }
}

// ── Skeleton ────────────────────────────────────────────────────────────────
@Composable
private fun FSkeletonList() {
    val inf   = rememberInfiniteTransition(label = "sk")
    val alpha by inf.animateFloat(0.3f, 0.7f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    LazyColumn {
        items(7) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(FDivider.copy(alpha)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(120.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(FDivider.copy(alpha)))
                    Box(Modifier.width(80.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(FDivider.copy(alpha * 0.7f)))
                }
            }
        }
    }
}
