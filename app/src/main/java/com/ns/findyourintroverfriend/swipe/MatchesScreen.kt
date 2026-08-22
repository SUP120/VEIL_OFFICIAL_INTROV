package com.ns.findyourintroverfriend.swipe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.*
import java.text.SimpleDateFormat
import java.util.*
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.VeilThemeManager

// -- Dark-mode state -------------------------------------------------------
private var _matchDark by mutableStateOf(VeilThemeManager.initialResolvedDark)

// -- Design tokens (theme-aware) -------------------------------------------
private val MBg         get() = if (_matchDark) Color(0xFF111118) else Color(0xFFFFFFFF)
private val MCard       get() = if (_matchDark) Color(0xFF1D1D28) else Color(0xFFFFFFFF)
private val MBorder     get() = if (_matchDark) Color(0xFF272730) else Color(0xFFDBDBDB)
private val MPrimary    get() = if (_matchDark) Color(0xFFDFDFEA) else Color(0xFF262626)
private val MSecondary  get() = if (_matchDark) Color(0xFFAAAAB8) else Color(0xFF8E8E8E)
private val MMuted      get() = if (_matchDark) Color(0xFF787888) else Color(0xFFC7C7C7)
private val MAccent            = Color(0xFF0095F6)
private val MAccentSoft get() = if (_matchDark) Color(0xFF0A1E35) else Color(0xFFE7F3FF)
private val MGreen             = Color(0xFF00BA34)
private val MRed               = Color(0xFFED4956)
private val MRedSoft    get() = if (_matchDark) Color(0xFF2D0A0F) else Color(0xFFFFF0F1)

// Avatar colors - subtle, professional pastels
private val avatarColorsLight = listOf(
    Color(0xFFE3F2FD), Color(0xFFFCE4EC), Color(0xFFF3E5F5),
    Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFE0F2F1)
)
private val avatarColorsDark = listOf(
    Color(0xFF0A1520), Color(0xFF1F0A14), Color(0xFF140A1F),
    Color(0xFF0A1A0F), Color(0xFF1A1207), Color(0xFF0A1A18)
)
private val avatarColors get() = if (_matchDark) avatarColorsDark else avatarColorsLight

private fun avatarColor(uid: String): Color =
    avatarColors[kotlin.math.abs(uid.hashCode()) % avatarColors.size]

private fun mAgo(ms: Long): String {
    if (ms == 0L) return "now"
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000      -> "now"
        d < 3_600_000   -> "${d / 60_000}m"
        d < 86_400_000  -> "${d / 3_600_000}h"
        d < 604_800_000 -> "${d / 86_400_000}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

// ── Screen Entry ──────────────────────────────────────────────────────────────
@Composable
fun MatchesScreen(
    vm          : MatchesViewModel = viewModel(),
    onBack      : () -> Unit = {},
    onOpenMatch : (String) -> Unit = {}
) {
    val matches         by vm.matches.collectAsStateWithLifecycle()
    val compliments     by vm.compliments.collectAsStateWithLifecycle()
    val sentCompliments by vm.sentCompliments.collectAsStateWithLifecycle()
    val pending         by vm.pendingLikes.collectAsStateWithLifecycle()
    val loading         by vm.loading.collectAsStateWithLifecycle()
    val lastMessages    by vm.lastMessages.collectAsStateWithLifecycle()
    val unreadMatchIds  by vm.unreadMatchIds.collectAsStateWithLifecycle()

    val swipeVm: SwipeViewModel = viewModel()
    val myProfile by swipeVm.myProfile.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }

    // Mark all unread compliments as read when the user opens the Compliments tab.
    // Key is ONLY (tab), NOT (tab, compliments) — including compliments in the key
    // created a feedback loop: each Firestore write triggered the snapshot listener
    // which updated the compliments list, which re-triggered this effect, causing
    // rapid recompositions and animation glitches ("flickering NEW badge").
    LaunchedEffect(tab) {
        if (tab == 2) vm.markAllComplimentsRead()
    }

    val _veilIsDark = LocalVeilIsDark.current
    SideEffect { _matchDark = _veilIsDark }

    Column(Modifier.fillMaxSize().background(MBg).systemBarsPadding()) {
        // ── Header (Instagram style) ──────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(MBg)
                .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.ArrowBackIosNew, null, tint = MPrimary,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Matches",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MPrimary,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    "Your swipe connections",
                    fontSize = 11.sp,
                    color = MSecondary
                )
            }
        }

        Divider(color = MBorder, thickness = 0.5.dp)

        // ── Tab Row (Clean, minimal) ──────────────────────────────────────────
        MTabRow(
            tab         = tab,
            pendingCount= pending.size,
            matchCount  = matches.size,
            unreadCount = vm.unreadComplimentsCount,
            onSelect    = { tab = it }
        )

        Divider(color = MBorder, thickness = 0.5.dp)

        // ── Content ───────────────────────────────────────────────────────────
        AnimatedContent(
            targetState = tab,
            label       = "tabContent",
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            }
        ) { t ->
            when (t) {
                0 -> MPendingTab(
                    list      = pending,
                    loading   = loading,
                    onSmash   = { p -> vm.smashPendingLike(p, myProfile) { onOpenMatch(it) } },
                    onPass    = { p -> vm.passPendingLike(p) }
                )
                1 -> MMatchedTab(
                    matches       = matches,
                    loading       = loading,
                    lastMessages  = lastMessages,
                    unreadMatchIds= unreadMatchIds,
                    onOpenMatch   = { id -> vm.markMatchRead(id); onOpenMatch(id) }
                )
                2 -> MComplimentsTab(
                    received    = compliments,
                    sent        = sentCompliments,
                    loading     = loading,
                    myProfile   = myProfile,
                    onAccept    = { c -> vm.acceptCompliment(c, myProfile, onOpenMatch) },
                    onReject    = { c -> vm.rejectCompliment(c.fromUid) }
                )
            }
        }
    }
}

// ── Tab Row (Professional, animated) ──────────────────────────────────────────
@Composable
private fun MTabRow(
    tab: Int, pendingCount: Int, matchCount: Int,
    unreadCount: Int, onSelect: (Int) -> Unit
) {
    val items = listOf(
        Triple("Pending", pendingCount, Icons.Rounded.FavoriteBorder),
        Triple("Matches", matchCount, Icons.Rounded.Favorite),
        Triple("Compliments", unreadCount, Icons.Rounded.StarBorder)
    )
    
    Row(
        Modifier.fillMaxWidth().background(MBg)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items.forEachIndexed { i, (label, badge, icon) ->
            val sel = i == tab
            val textColor by animateColorAsState(
                if (sel) MPrimary else MSecondary,
                label = "textColor$i"
            )
            val underlineWidth by animateDpAsState(
                if (sel) 40.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "underline$i"
            )
            
            Box(
                Modifier.weight(1f)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor,
                            letterSpacing = (-0.2).sp
                        )
                        // Professional badge (not a dot!)
                        if (badge > 0) {
                            Box(
                                Modifier
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) MAccent else if (_matchDark) Color(0xFF272730) else Color(0xFFE8E8E8))
                                    .padding(horizontal = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$badge",
                                    fontSize = 11.sp,
                                    color = if (sel) Color.White else MSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    // Animated underline indicator
                    Box(
                        Modifier.width(underlineWidth).height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MPrimary)
                    )
                }
            }
        }
    }
}

// ── Pending Tab ───────────────────────────────────────────────────────────────
@Composable
private fun MPendingTab(
    list    : List<PendingLike>,
    loading : Boolean,
    onSmash : (PendingLike) -> Unit,
    onPass  : (PendingLike) -> Unit
) {
    when {
        loading      -> MShimmer()
        list.isEmpty() -> MEmpty(
            Icons.Rounded.FavoriteBorder,
            "No pending likes",
            "When someone likes you,\nthey'll appear here"
        )
        else -> LazyColumn(
            Modifier.fillMaxSize().background(MBg),
            contentPadding = PaddingValues(0.dp)
        ) {
            items(list, key = { it.fromUid }) { p ->
                MPendingCard(p, onSmash = { onSmash(p) }, onPass = { onPass(p) })
                Divider(color = MBorder, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun MPendingCard(p: PendingLike, onSmash: () -> Unit, onPass: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(MBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onSmash()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar with subtle shadow
        Box(
            Modifier.shadow(
                elevation = 2.dp,
                shape = CircleShape,
                clip = false
            )
        ) {
            MAvatar(p.fromAlias, p.fromPhotoUrl, avatarColor(p.fromUid), 60.dp, p.fromAvatar)
        }
        
        // Info
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    p.fromAlias.ifBlank { "Someone" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.3).sp
                )
                // "LIKED YOU" badge
                Box(
                    Modifier
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (_matchDark) Color(0xFF2D0A0F) else Color(0xFFFFEBEE))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "LIKED YOU",
                        fontSize = 9.sp,
                        color = MRed,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }
            Text(
                "${mAgo(p.at)} ago",
                fontSize = 13.sp,
                color = MSecondary
            )
        }
        
        // Action buttons with better styling
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Pass button
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5))
                    .clickable { onPass() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    null,
                    tint = MSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            // Like back button with gradient
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0095F6), Color(0xFF00C6FF))
                        )
                    )
                    .clickable { onSmash() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Matched Tab (Instagram DM style) ──────────────────────────────────────────
@Composable
private fun MMatchedTab(
    matches        : List<SwipeMatch>,
    loading        : Boolean,
    lastMessages   : Map<String, Pair<String, Long>>,
    unreadMatchIds : Set<String>,
    onOpenMatch    : (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(matches, searchQuery) {
        if (searchQuery.isBlank()) matches
        else matches.filter { m ->
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val alias = if (m.userA == uid) m.aliasB else m.aliasA
            alias.contains(searchQuery.trim(), ignoreCase = true)
        }
    }
    // Sort: unread first, then by last message time, then matchedAt
    val sorted = remember(filtered, lastMessages, unreadMatchIds) {
        filtered.sortedWith(compareByDescending<SwipeMatch> { it.matchId in unreadMatchIds }
            .thenByDescending { lastMessages[it.matchId]?.second ?: it.matchedAt })
    }

    Column(Modifier.fillMaxSize().background(MBg)) {
        // ── Search bar ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (_matchDark) Color(0xFF1E1E26) else Color(0xFFF2F2F2))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Search, null, tint = MSecondary, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp, color = MPrimary
                ),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) Text("Search connections…", fontSize = 14.sp, color = MSecondary)
                    inner()
                },
                modifier = Modifier.weight(1f)
            )
            if (searchQuery.isNotEmpty()) {
                Icon(Icons.Rounded.Close, null, tint = MSecondary,
                    modifier = Modifier.size(16.dp).clickable { searchQuery = "" })
            }
        }

        when {
            loading -> MShimmer()
            sorted.isEmpty() && searchQuery.isNotBlank() -> MEmpty(
                Icons.Rounded.Search, "No results", "No connections match \"$searchQuery\""
            )
            sorted.isEmpty() -> MEmpty(
                Icons.Rounded.Favorite, "No matches yet",
                "Keep swiping to find\nyour connections"
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
                items(sorted, key = { it.matchId }) { m ->
                    MMatchCard(m, lastMessages[m.matchId], m.matchId in unreadMatchIds, onOpenMatch)
                    Divider(color = MBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun MMatchCard(
    m           : SwipeMatch,
    lastMsg     : Pair<String, Long>?,
    isUnread    : Boolean,
    onOpenMatch : (String) -> Unit
) {
    val uid      = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isA      = m.userA == uid
    val alias    = if (isA) m.aliasB else m.aliasA
    val photo    = if (isA) m.photoB else m.photoA
    val otherAvatar = if (isA) m.avatarB else m.avatarA
    val otherUid = if (isA) m.userB else m.userA

    var isPressed by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        if (isPressed) (if (_matchDark) Color(0xFF272730) else Color(0xFFF2F2F2)) else if (isUnread) (if (_matchDark) Color(0xFF16161D) else Color(0xFFFAFAFA)) else MBg,
        label = "bgColor"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { isPressed = true; onOpenMatch(m.matchId) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar with unread ring
        Box {
            Box(
                Modifier.shadow(if (isUnread) 3.dp else 1.dp, CircleShape, clip = false)
                    .then(if (isUnread)
                        Modifier.border(2.5.dp, MAccent, CircleShape)
                    else Modifier)
            ) {
                MAvatar(alias, photo, avatarColor(otherUid), 56.dp, otherAvatar)
            }
            // Unread dot
            if (isUnread) {
                Box(
                    Modifier.size(14.dp).align(Alignment.BottomEnd)
                        .clip(CircleShape).background(Color.White)
                        .padding(2.dp)
                ) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(MAccent))
                }
            }
        }

        // Info
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    alias.ifBlank { "Anonymous" },
                    fontSize = 15.sp,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                    color = MPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Timestamp
                val timeStr = if (lastMsg != null) mAgo(lastMsg.second)
                              else mAgo(m.matchedAt)
                Text(
                    timeStr,
                    fontSize = 11.sp,
                    color = if (isUnread) MAccent else MSecondary,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lastMsg?.first ?: "Connected on Veil",
                    fontSize = 13.sp,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isUnread) MPrimary else MSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isUnread) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(MAccent)
                    )
                }
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) { kotlinx.coroutines.delay(100); isPressed = false }
    }
}

// ── Compliments Tab ───────────────────────────────────────────────────────────
@Composable
private fun MComplimentsTab(
    received  : List<SwipeCompliment>,
    sent      : List<SentCompliment>,
    loading   : Boolean,
    myProfile : SwipeProfile?,
    onAccept  : (SwipeCompliment) -> Unit,
    onReject  : (SwipeCompliment) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }
    val pendingCount = received.count { it.status == "pending" }

    Column(Modifier.fillMaxSize().background(if (_matchDark) MBg else Color(0xFFF6F7FB))) {

        // ── Sub-tab pill selector ──────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().background(MCard)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (_matchDark) Color(0xFF1A1A26) else Color(0xFFF0F1F5))
                    .padding(3.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("Received" to pendingCount, "Sent" to 0).forEachIndexed { i, (label, badge) ->
                        val sel = subTab == i
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(26.dp))
                                .background(if (sel) MCard else Color.Transparent)
                                .shadow(if (sel) 2.dp else 0.dp, RoundedCornerShape(26.dp))
                                .clickable { subTab = i }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (sel) MPrimary else MSecondary
                                )
                                if (badge > 0 && sel) {
                                    Box(
                                        Modifier.size(20.dp).clip(CircleShape)
                                            .background(MAccent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$badge", fontSize = 10.sp,
                                            color = Color.White, fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (badge > 0) {
                                    Box(
                                        Modifier.size(7.dp).clip(CircleShape)
                                            .background(MAccent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        when (subTab) {
            0 -> when {
                loading -> MShimmer()
                received.isEmpty() -> MEmpty(
                    Icons.Rounded.StarBorder,
                    "No compliments yet",
                    "Someone will take the time\nto write something just for you"
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(received, key = { it.fromUid + it.at }) { c ->
                        MComplimentCard(c, onAccept = { onAccept(c) }, onReject = { onReject(c) })
                    }
                }
            }
            1 -> when {
                loading -> MShimmer()
                sent.isEmpty() -> MEmpty(
                    Icons.Rounded.Send,
                    "No compliments sent",
                    "Tap ✦ on someone's card to\nsend them a thoughtful message"
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sent, key = { it.toUid + it.at }) { s ->
                        SentComplimentCard(s)
                    }
                }
            }
        }
    }
}

// ── Received Compliment Card ──────────────────────────────────────────────────
@Composable
private fun MComplimentCard(
    c       : SwipeCompliment,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val isPending  = c.status == "pending"
    val isAccepted = c.status == "accepted"
    val isRejected = c.status == "rejected"
    var showInfo   by remember { mutableStateOf(false) }

    // ── ℹ️ Info dialog ────────────────────────────────────────────────────────
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("Got it", fontWeight = FontWeight.SemiBold, color = Color(0xFF6D28D9))
                }
            },
            title = {
                Text(
                    "💌  About this compliment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MPrimary
                )
            },
            text = {
                Text(
                    "This person used a compliment credit — a limited, intentional way to step outside the feed and say something meaningful directly to you.\n\nAccepting opens a private conversation for both of you. Declining is completely fine, and they'll be notified respectfully.\n\nThe choice is entirely yours.",
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563),
                    lineHeight = 21.sp
                )
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MCard
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(MCard)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Person row ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar
            Box {
                MAvatar(c.fromAlias, c.fromPhotoUrl, avatarColor(c.fromUid), 56.dp, c.fromAvatar)
                // NEW dot
                if (!c.isRead && isPending) {
                    Box(
                        Modifier
                            .size(14.dp).align(Alignment.TopEnd)
                            .clip(CircleShape).background(MCard)
                            .padding(2.dp)
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(MAccent))
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    c.fromAlias.ifBlank { "Someone" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MPrimary,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    mAgo(c.at),
                    fontSize = 12.sp,
                    color = MSecondary
                )
            }

            // ℹ️ info button (pending only) + Status badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPending) {
                    IconButton(
                        onClick = { showInfo = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "About this compliment",
                            tint = Color(0xFFB794F4),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // Status badge
                when {
                    isAccepted -> Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFECFDF5))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("✓ Chatting", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                    }
                    isRejected -> Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFF9FAFB))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("Declined", fontSize = 12.sp, color = MSecondary)
                    }
                    else -> Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFEEF2FF))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("Pending", fontSize = 12.sp,
                            fontWeight = FontWeight.Medium, color = Color(0xFF4F46E5))
                    }
                }
            }
        }

        // (paid-effort notice removed — tap ℹ️ to see it)

        // ── Message ───────────────────────────────────────────────────────────
        if (c.message.isNotBlank()) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF9F9FB))
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Text(
                    "\"${c.message}\"",
                    fontSize = 14.sp,
                    color = Color(0xFF374151),
                    lineHeight = 21.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    letterSpacing = (-0.1).sp
                )
            }
        }

        // ── Action area ───────────────────────────────────────────────────────
        when {
            isPending -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline button - Professional outlined style
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MPrimary
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Decline",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Decline",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
                
                // Accept button - Gradient filled style (Instagram-inspired)
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            spotColor = MAccent.copy(alpha = 0.3f)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MAccent
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Accept",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Accept",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
            }
            isAccepted -> Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFECFDF5))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CheckCircle, null,
                    tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                Text(
                    "You accepted — the conversation is open",
                    fontSize = 13.sp,
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Medium
                )
            }
            isRejected -> Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF9FAFB))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Cancel, null,
                    tint = MSecondary, modifier = Modifier.size(16.dp))
                Text(
                    "You chose not to accept this",
                    fontSize = 13.sp,
                    color = MSecondary
                )
            }
        }
    }
}

// ── Sent Compliment Card ──────────────────────────────────────────────────────
@Composable
private fun SentComplimentCard(s: SentCompliment) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(MCard)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Person row ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(avatarColor(s.toUid)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.toAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    color = MPrimary.copy(0.65f)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    s.toAlias.ifBlank { "Someone" },
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MPrimary,
                    letterSpacing = (-0.3).sp
                )
                Text(mAgo(s.at), fontSize = 12.sp, color = MSecondary)
            }
            // Status chip
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(when (s.status) {
                        "accepted" -> Color(0xFFECFDF5)
                        "rejected" -> Color(0xFFFEF2F2)
                        else       -> Color(0xFFF3F4F6)
                    })
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    when (s.status) {
                        "accepted" -> "✨ Accepted"
                        "rejected" -> "Declined"
                        else       -> "Awaiting…"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (s.status) {
                        "accepted" -> Color(0xFF16A34A)
                        "rejected" -> Color(0xFFDC2626)
                        else       -> Color(0xFF9CA3AF)
                    }
                )
            }
        }

        // ── Message preview ───────────────────────────────────────────────────
        if (s.message.isNotBlank()) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF9F9FB))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text(
                    "\"${s.message}\"",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 19.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ── Outcome line ──────────────────────────────────────────────────────
        Text(
            when (s.status) {
                "accepted" -> "They said yes — open Matches to start chatting 👋"
                "rejected" -> "They passed this time. Your intention still matters. 💙"
                else       -> "Waiting for their decision…"
            },
            fontSize = 12.sp,
            color = when (s.status) {
                "accepted" -> Color(0xFF16A34A)
                else       -> MSecondary
            },
            fontWeight = if (s.status == "accepted") FontWeight.Medium else FontWeight.Normal,
            lineHeight = 17.sp
        )
    }
}







// ── Shared Components ─────────────────────────────────────────────────────────
@Composable
private fun MAvatar(
    alias     : String,
    photoUrl  : String,
    bgColor   : Color,
    size      : Dp,
    avatarRes : String = ""
) {
    val ctx   = LocalContext.current
    val resId = remember(avatarRes) {
        if (avatarRes.isNotBlank())
            ctx.resources.getIdentifier(avatarRes, "drawable", ctx.packageName)
        else 0
    }

    Box(
        Modifier.size(size).clip(CircleShape).background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            photoUrl.isNotBlank() -> coil.compose.SubcomposeAsyncImage(
                model              = photoUrl,
                contentDescription = alias,
                modifier           = Modifier.fillMaxSize().clip(CircleShape),
                contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                error = {
                    Text(
                        alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontSize   = (size.value * 0.4f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MPrimary.copy(alpha = 0.7f)
                    )
                }
            )
            resId != 0 -> androidx.compose.foundation.Image(
                painter            = painterResource(resId),
                contentDescription = alias,
                modifier           = Modifier.fillMaxSize().clip(CircleShape),
                contentScale       = androidx.compose.ui.layout.ContentScale.Crop
            )
            else -> Text(
                alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize   = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
                color      = MPrimary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MShimmer() {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        0.3f, 0.7f,
        infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        "alpha"
    )
    
    LazyColumn(
        Modifier.fillMaxSize().background(MBg),
        contentPadding = PaddingValues(0.dp)
    ) {
        items(8) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(MMuted.copy(alpha = alpha))
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth(0.4f).height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MMuted.copy(alpha = alpha))
                    )
                    Box(
                        Modifier.fillMaxWidth(0.6f).height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MMuted.copy(alpha = alpha))
                    )
                }
            }
            Divider(color = MBorder, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun MEmpty(icon: ImageVector, title: String, subtitle: String) {
    Box(
        Modifier.fillMaxSize().background(MBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                Modifier.size(80.dp).clip(CircleShape)
                    .border(2.dp, MBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = MSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                subtitle,
                fontSize = 14.sp,
                color = MSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
