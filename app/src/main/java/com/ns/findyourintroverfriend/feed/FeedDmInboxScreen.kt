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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ns.findyourintroverfriend.swipe.MatchesViewModel
import com.ns.findyourintroverfriend.swipe.SwipeMatch
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.PresenceManager
import com.ns.findyourintroverfriend.util.VeilThemeManager
import java.text.SimpleDateFormat
import java.util.*

// ── Dark-mode state (synced via SideEffect in FeedDmInboxScreen) ────────────
private var _dmDark by androidx.compose.runtime.mutableStateOf(VeilThemeManager.initialResolvedDark)

// ── Design tokens ────────────────────────────────────────────────────────────
private val FBg        get() = if (_dmDark) Color(0xFF111118) else Color(0xFFFFFFFF)
private val FCard      get() = if (_dmDark) Color(0xFF1D1D28) else Color(0xFFFFFFFF)
private val FBlack     get() = if (_dmDark) Color(0xFFDFDFEA) else Color(0xFF1A1A1A)
private val FGray      get() = if (_dmDark) Color(0xFFAAAAAE) else Color(0xFF8E8E8E)
private val FDivider   get() = if (_dmDark) Color(0xFF282838) else Color(0xFFEFEFEF)
private val FBlue               = Color(0xFF0095F6)
private val FLightGray get() = if (_dmDark) Color(0xFF1E1E2C) else Color(0xFFF2F2F2)
private val FMuted     get() = if (_dmDark) Color(0xFF787888) else Color(0xFF999999)
private val FGreen              = Color(0xFF22C55E)
private val DVeil               = Color(0xFFE91E8C)
private val DPink      get() = if (_dmDark) Color(0xFF2A1020) else Color(0xFFFCE4EC)
private val DPinkDim   get() = if (_dmDark) Color(0xFF3A1830) else Color(0xFFF8BBD0)
private val DBorder    get() = if (_dmDark) Color(0xFF6B1840).copy(0.6f) else Color(0xFFF8BBD0)

private val fAvatarColorsDark = listOf(
    Color(0xFF1A2840), Color(0xFF2A1020), Color(0xFF1E1030),
    Color(0xFF102818), Color(0xFF281808), Color(0xFF102828),
)
private val fAvatarColorsLight = listOf(
    Color(0xFFE3F2FD), Color(0xFFFCE4EC), Color(0xFFF3E5F5),
    Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFE0F2F1),
)
private fun fAvatarColor(uid: String) =
    (if (_dmDark) fAvatarColorsDark else fAvatarColorsLight)[kotlin.math.abs(uid.hashCode()) % 6]

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
    val friendMatches  by vm.friendMatches.collectAsStateWithLifecycle()
    val swipeMatches   by vm.matches.collectAsStateWithLifecycle()
    val loading        by vm.loading.collectAsStateWithLifecycle()
    val lastMessages   by vm.lastMessages.collectAsStateWithLifecycle()
    val unreadMatchIds by vm.unreadMatchIds.collectAsStateWithLifecycle()
    val presenceMap    by vm.presenceMap.collectAsStateWithLifecycle()
    val incoming       by approachVm.incoming.collectAsStateWithLifecycle()

    val _veilDark = LocalVeilIsDark.current
    SideEffect { _dmDark = _veilDark }

    val context = LocalContext.current

    val myUid = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }
    var selectedTab   by remember { mutableIntStateOf(0) }
    var showSettings  by remember { mutableStateOf(false) }
    var showLastSeen  by remember { mutableStateOf(PresenceManager.showLastSeenPref(context)) }

    // Refresh own presence heartbeat while on this screen
    // (MainActivity.onStop handles going offline — no goOffline here
    //  to avoid writing lastSeen when user just navigates within the app)
    LaunchedEffect(Unit) {
        PresenceManager.goOnline(context)
    }

    // Periodically refresh presence (every 45 s)
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshPresence()
            kotlinx.coroutines.delay(45_000)
        }
    }

    val fUnread = friendMatches.count { it.matchId in unreadMatchIds }
    val vUnread = swipeMatches.count  { it.matchId in unreadMatchIds }

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
                Text("Messages", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = FBlack, letterSpacing = (-0.3).sp)
                Text("Friends · Crushed · Requests", fontSize = 11.sp, color = FGray)
            }
            // ── Settings gear ───────────────────────────────────────────────
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, null, tint = FGray, modifier = Modifier.size(22.dp))
            }
        }

        // ── Tabs: Friends | Veil Connections | Requests ────────────────────
        val reqLabel = if (incoming.isEmpty()) "Requests" else "Requests (${incoming.size})"
        val tabs = listOf(
            if (fUnread > 0) "Friends ($fUnread)" else "Friends",
            if (vUnread > 0) "Love Matches ($vUnread)"  else "Love Matches",
            reqLabel
        )
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = FBg,
            contentColor     = FBlack,
            indicator        = { tp ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tp[selectedTab]), height = 2.dp,
                    color = if (selectedTab == 1) DVeil else FBlack
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = selectedTab == i,
                    onClick  = { selectedTab = i },
                    text = {
                        Text(
                            label, fontSize = 13.sp,
                            fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                i == 1 && selectedTab == 1 -> DVeil
                                selectedTab == i            -> FBlack
                                else                        -> FGray
                            }
                        )
                    }
                )
            }
        }
        HorizontalDivider(
            color = if (selectedTab == 1) DPinkDim else FDivider,
            thickness = 0.8.dp
        )

        // ── Content ────────────────────────────────────────────────────────
        when (selectedTab) {
            2    -> FRequestsTab(incoming, onOpenProfile, approachVm)
            1    -> VeilConnectionsTab(
                matches        = swipeMatches,
                loading        = loading,
                myUid          = myUid,
                lastMessages   = lastMessages,
                unreadMatchIds = unreadMatchIds,
                presenceMap    = presenceMap,
                showLastSeen   = showLastSeen,
                onOpenChat     = { id -> vm.markMatchRead(id); onOpenChat(id) },
                onDelete       = { id -> vm.deleteMatch(id) },
                onBlock        = { m ->
                    val other = if (m.userA == myUid) m.userB else m.userA
                    vm.blockUser(other, m.matchId)
                }
            )
            else -> FMessagesTab(
                matches        = friendMatches,
                loading        = loading,
                lastMessages   = lastMessages,
                unreadMatchIds = unreadMatchIds,
                presenceMap    = presenceMap,
                showLastSeen   = showLastSeen,
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

    // ── Privacy Settings Dialog ─────────────────────────────────────────────
    if (showSettings) {
        Dialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(FCard)
                    .border(1.dp, FDivider, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Header
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Message Settings", fontSize = 17.sp,
                                fontWeight = FontWeight.Bold, color = FBlack)
                            Text("Privacy & visibility", fontSize = 12.sp, color = FGray)
                        }
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.Rounded.Close, null, tint = FGray, modifier = Modifier.size(20.dp))
                        }
                    }

                    HorizontalDivider(color = FDivider)

                    // Last Seen toggle
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show Last Seen", fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, color = FBlack)
                            Text(
                                if (showLastSeen)
                                    "Others can see when you were last active"
                                else
                                    "Your last seen is hidden from others",
                                fontSize = 12.sp, color = FGray, lineHeight = 17.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = showLastSeen,
                            onCheckedChange = { v ->
                                showLastSeen = v
                                PresenceManager.setShowLastSeen(context, v)
                                if (v) PresenceManager.goOnline(context)
                                else   PresenceManager.goOffline()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor  = Color.White,
                                checkedTrackColor  = FBlue,
                                uncheckedThumbColor = FGray,
                                uncheckedTrackColor = FDivider
                            )
                        )
                    }

                    // Info note
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FLightGray)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = FGray, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                        Text(
                            "If you turn this off, you also won't see others' last seen.",
                            fontSize = 12.sp, color = FGray, lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Presence line — only shows "last seen X" text (online dot is on avatar) ──
@Composable
private fun PresenceLine(
    otherUid    : String,
    presenceMap : Map<String, Pair<Boolean, Long>>,
    showLastSeen: Boolean,
    isLoveTheme : Boolean = false
) {
    if (!showLastSeen) return
    val p = presenceMap[otherUid] ?: return
    val (isOnline, lastSeenMs) = p
    // When online: green dot on avatar is enough — no text needed in the list
    if (isOnline) return
    val text = PresenceManager.formatPresence(false, lastSeenMs)
    if (text.isBlank()) return

    Text(
        text,
        fontSize   = 11.sp,
        color      = FGray,
        fontWeight = FontWeight.Normal
    )
}

// ── Veil Connections tab (dating-style rose theme) ───────────────────────────
@Composable
private fun VeilConnectionsTab(
    matches        : List<SwipeMatch>,
    loading        : Boolean,
    myUid          : String,
    lastMessages   : Map<String, Pair<String, Long>>,
    unreadMatchIds : Set<String>,
    presenceMap    : Map<String, Pair<Boolean, Long>>,
    showLastSeen   : Boolean,
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
    Column(Modifier.fillMaxSize().background(DPink)) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Search, null, tint = DVeil.copy(0.6f), modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = FBlack),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search Veil connections…", fontSize = 14.sp, color = FGray)
                    inner()
                },
                modifier = Modifier.weight(1f)
            )
            if (query.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = FGray,
                modifier = Modifier.size(16.dp).clickable { query = "" })
        }
        when {
            loading -> FSkeletonList()
            sorted.isEmpty() && query.isNotBlank() -> FEmptyState(Icons.Rounded.Search, "No results", "No Veil connections match \"$query\"")
            sorted.isEmpty() -> FEmptyState(Icons.Rounded.Favorite, "No connections yet", "Match with someone on Veil Swipe\nand they'll appear here 💘")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(sorted, key = { it.matchId }) { m ->
                    VeilDmRow(
                        match        = m, myUid = myUid,
                        lastMsg      = lastMessages[m.matchId],
                        isUnread     = m.matchId in unreadMatchIds,
                        presenceMap  = presenceMap,
                        showLastSeen = showLastSeen,
                        onClick      = { onOpenChat(m.matchId) },
                        onDelete     = { onDelete(m.matchId) },
                        onBlock      = { onBlock(m) }
                    )
                    HorizontalDivider(color = DPinkDim, thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 84.dp))
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VeilDmRow(
    match        : SwipeMatch,
    myUid        : String,
    lastMsg      : Pair<String, Long>?,
    isUnread     : Boolean,
    presenceMap  : Map<String, Pair<Boolean, Long>>,
    showLastSeen : Boolean,
    onClick      : () -> Unit,
    onDelete     : () -> Unit,
    onBlock      : () -> Unit
) {
    val isA      = match.userA == myUid
    val alias    = (if (isA) match.aliasB else match.aliasA).ifBlank { "Match" }
    val photo    = if (isA) match.photoB else match.photoA
    val otherUid = if (isA) match.userB  else match.userA
    val isOnline = presenceMap[otherUid]?.first == true
    var showMenu by remember { mutableStateOf(false) }
    var pressed  by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (pressed) DPinkDim else FBg, label = "vbg")

    Box(Modifier.background(FBg)) {
        Row(
            Modifier.fillMaxWidth().background(bg)
                .combinedClickable(onClick = { pressed = true; onClick() }, onLongClick = { showMenu = true })
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                Box(
                    Modifier.size(54.dp).clip(CircleShape)
                        .then(if (isUnread) Modifier.border(2.dp, DVeil, CircleShape) else Modifier)
                        .background(DPink),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo.isNotBlank()) {
                        AsyncImage(model = photo, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        Text(alias.first().uppercaseChar().toString(),
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                            color = DVeil.copy(0.7f))
                    }
                }
                // Online dot on avatar
                if (isOnline && showLastSeen) {
                    Box(
                        Modifier.size(14.dp).align(Alignment.BottomEnd)
                            .clip(CircleShape).background(FBg).padding(2.dp)
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(FGreen))
                    }
                } else if (isUnread) {
                    Box(Modifier.size(13.dp).align(Alignment.BottomEnd)
                        .clip(CircleShape).background(FBg).padding(2.dp)) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(DVeil))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(alias, fontSize = 15.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        color = FBlack, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Text(fAgo(lastMsg?.second ?: match.matchedAt), fontSize = 11.sp,
                        color = if (isUnread) DVeil else FGray,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal)
                }
                // Presence line
                PresenceLine(otherUid, presenceMap, showLastSeen, isLoveTheme = true)
                // Last message
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(lastMsg?.first ?: "Say hello 💘", fontSize = 13.sp,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isUnread) FBlack else FGray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    if (isUnread) Box(Modifier.size(8.dp).clip(CircleShape).background(DVeil))
                }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(FBg).widthIn(min = 180.dp)) {
            DropdownMenuItem(
                text = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFE53935), modifier = Modifier.size(17.dp))
                    Text("Delete chat", fontSize = 14.sp, color = Color(0xFFE53935)) } },
                onClick = { showMenu = false; onDelete() })
            HorizontalDivider(color = FDivider)
            DropdownMenuItem(
                text = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Block, null, tint = Color(0xFFE53935), modifier = Modifier.size(17.dp))
                    Text("Block $alias", fontSize = 14.sp, color = Color(0xFFE53935)) } },
                onClick = { showMenu = false; onBlock() })
        }
    }
    LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(100); pressed = false } }
}

// ── Messages tab ────────────────────────────────────────────────────────────
@Composable
private fun FMessagesTab(
    matches        : List<SwipeMatch>,
    loading        : Boolean,
    lastMessages   : Map<String, Pair<String, Long>>,
    unreadMatchIds : Set<String>,
    presenceMap    : Map<String, Pair<Boolean, Long>>,
    showLastSeen   : Boolean,
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
                        match        = m,
                        myUid        = myUid,
                        lastMsg      = lastMessages[m.matchId],
                        isUnread     = m.matchId in unreadMatchIds,
                        presenceMap  = presenceMap,
                        showLastSeen = showLastSeen,
                        onClick      = { onOpenChat(m.matchId) },
                        onDelete     = { onDelete(m.matchId) },
                        onBlock      = { onBlock(m) }
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
    match        : SwipeMatch,
    myUid        : String,
    lastMsg      : Pair<String, Long>?,
    isUnread     : Boolean,
    presenceMap  : Map<String, Pair<Boolean, Long>>,
    showLastSeen : Boolean,
    onClick      : () -> Unit,
    onDelete     : () -> Unit,
    onBlock      : () -> Unit
) {
    val isA      = match.userA == myUid
    val alias    = (if (isA) match.aliasB else match.aliasA).ifBlank { "Friend" }
    val photo    = if (isA) match.photoB else match.photoA
    val otherUid = if (isA) match.userB  else match.userA
    val isOnline = presenceMap[otherUid]?.first == true
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
            // Avatar with online dot
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
                if (isOnline && showLastSeen) {
                    Box(
                        Modifier.size(14.dp).align(Alignment.BottomEnd)
                            .clip(CircleShape).background(FBg).padding(2.dp)
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(FGreen))
                    }
                } else if (isUnread) {
                    Box(Modifier.size(13.dp).align(Alignment.BottomEnd)
                        .clip(CircleShape).background(FBg).padding(2.dp)) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(FBlue))
                    }
                }
            }
            // Text
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
                // Presence line
                PresenceLine(otherUid, presenceMap, showLastSeen)
                // Last message
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
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(FBg).widthIn(min = 180.dp)) {
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
