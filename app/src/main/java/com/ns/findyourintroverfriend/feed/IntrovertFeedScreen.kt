package com.ns.findyourintroverfriend.feed

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.util.VeilSafetyUtil
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import com.google.firebase.auth.FirebaseAuth
import com.ns.findyourintroverfriend.swipe.MatchesViewModel
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.VeilThemeManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// ── Design Tokens (theme-aware) ─────────────────────────────────────────────────────
// Serenity palette — warm paper (light) — deep ink (dark)
private var _feedDark by mutableStateOf(VeilThemeManager.initialResolvedDark)

// Backgrounds
private val FBg     get() = if (_feedDark) Color(0xFF0E0E12) else Color(0xFFFAF9F7)  // warm cream / deep ink
private val FCard   get() = if (_feedDark) Color(0xFF16161C) else Color(0xFFFFFFFF)  // card surface
private val FSurface get() = if (_feedDark) Color(0xFF1C1C24) else Color(0xFFF4F3F0) // subtle tint

// Borders & dividers
private val FBorder     get() = if (_feedDark) Color(0xFF252530) else Color(0xFFECEBE8)
private val FBorderDark get() = if (_feedDark) Color(0xFF38384A) else Color(0xFFD8D6D2)

// Typography
private val FPrimary   get() = if (_feedDark) Color(0xFFE8E6E1) else Color(0xFF1A1916)  // warm near-black
private val FSecondary get() = if (_feedDark) Color(0xFF8C8A88) else Color(0xFF7A7875)  // warm gray
private val FMuted     get() = if (_feedDark) Color(0xFF565450) else Color(0xFFB8B5B0)  // muted warm

// Accent — sage green (calm, nature, introspective)
private val FAccent     = Color(0xFF6B8F71)   // sage green
private val FAccentSoft get() = if (_feedDark) Color(0xFF0F1E11) else Color(0xFFEFF5F0)

// Danger
private val FRed     = Color(0xFFB85C5C)   // muted terracotta (not harsh)
private val FRedSoft get() = if (_feedDark) Color(0xFF211212) else Color(0xFFFAEEEE)

// Avatar palette — earthy, muted tones
private val avatarColorsDark = listOf(
    Color(0xFF1C2E1F), Color(0xFF2A1C28), Color(0xFF1C1A2E),
    Color(0xFF2A2518), Color(0xFF1C2828), Color(0xFF2A1E1A),
)
private val avatarColorsLight = listOf(
    Color(0xFFE8F0E9), Color(0xFFF0E8EF), Color(0xFFE9E8F2),
    Color(0xFFF2EFE6), Color(0xFFE6F0EF), Color(0xFFF2EAE8),
)
private fun avatarColor(uid: String) =
    (if (_feedDark) avatarColorsDark else avatarColorsLight)[kotlin.math.abs(uid.hashCode()) % 6]

private val avatarTextColors = listOf(
    Color(0xFF4A7850), Color(0xFF8F5C8A), Color(0xFF5C608F),
    Color(0xFF8A7840), Color(0xFF407878), Color(0xFF8A5040),
)
private fun avatarTextColor(uid: String) =
    avatarTextColors[kotlin.math.abs(uid.hashCode()) % avatarTextColors.size]

private fun timeAgo(ms: Long): String {
    if (ms == 0L) return "now"
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000      -> "just now"
        d < 3_600_000   -> "${d / 60_000}m ago"
        d < 86_400_000  -> "${d / 3_600_000}h ago"
        d < 604_800_000 -> "${d / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

// ── Mood Filter Tags ──────────────────────────────────────────────────────────────────
private val moodFilters = listOf("All", "Thoughts", "Feelings", "Connect", "Wins")

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntrovertFeedScreen(
    vm         : IntrovertFeedViewModel = viewModel(),
    matchesVm  : MatchesViewModel       = viewModel(),   // for DM unread count
    onBack     : () -> Unit = {},
    onOpenDms  : () -> Unit = {}
) {
    val posts          by vm.posts.collectAsStateWithLifecycle()
    val loading        by vm.loading.collectAsStateWithLifecycle()
    val unreadCount    by vm.unreadCount.collectAsStateWithLifecycle()
    val notifs         by vm.notifs.collectAsStateWithLifecycle()
    val scrollToTop    by vm.scrollToTop.collectAsStateWithLifecycle()

    // ── Sync theme ── makes all F-token getters return correct dark/light color
    val _veilIsDark = LocalVeilIsDark.current
    SideEffect { _feedDark = _veilIsDark }

    // DM unread count — includes both Feed friends AND Swipe connections
    val unreadMatchIds by matchesVm.unreadMatchIds.collectAsStateWithLifecycle()
    val friendMatches  by matchesVm.friendMatches.collectAsStateWithLifecycle()
    val swipeMatches   by matchesVm.matches.collectAsStateWithLifecycle()
    val unreadDmCount  = remember(unreadMatchIds, friendMatches, swipeMatches) {
        (friendMatches + swipeMatches).count { it.matchId in unreadMatchIds }
    }

    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()

    var selectedFilter  by remember { mutableStateOf("All") }
    var showCreatePost  by remember { mutableStateOf(false) }
    var reportTarget    by remember { mutableStateOf<String?>(null) }
    var showNotifPanel  by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 2 })

    // Scroll-to-top on new post
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) { listState.animateScrollToItem(0); vm.consumeScrollToTop() }
    }

    Scaffold(
        containerColor = FBg,
        topBar = {
            FeedTopBar(
                unreadCount    = unreadCount,
                unreadDmCount  = unreadDmCount,
                onNotif        = { showNotifPanel = true; vm.markAllNotifsRead() },
                onCreatePost   = { showCreatePost = true },
                onOpenDms      = onOpenDms,
                selectedFilter = selectedFilter,
                onFilterChange = { selectedFilter = it },
                currentPage    = pagerState.currentPage,
                onTabSelect    = { scope.launch { pagerState.animateScrollToPage(it) } }
            )
        },
        floatingActionButton = {
            // Only show on Posts page
            if (pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick        = { showCreatePost = true },
                    containerColor = FAccent,
                    contentColor   = Color.White,
                    shape          = CircleShape
                ) { Icon(Icons.Rounded.EditNote, null, modifier = Modifier.size(24.dp)) }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(
                state             = pagerState,
                modifier          = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> Box(Modifier.fillMaxSize()) {
                        when {
                            loading && posts.isEmpty() -> FeedShimmer()
                            posts.isEmpty()            -> FeedEmpty()
                            else -> FeedContent(
                                posts         = posts,
                                vm            = vm,
                                listState     = listState,
                                myConnections = vm.myConnections.collectAsStateWithLifecycle().value,
                                myUid         = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                                onLike        = { vm.toggleLike(it) },
                                onReport      = { reportTarget = it }
                            )
                        }
                    }
                    else -> VeilSparksScreen()
                }
            }
        }
    }

    reportTarget?.let { postId ->
        PostReportDialog(
            onDismiss = { reportTarget = null },
            onSubmit  = { reason, note -> vm.reportPost(postId, reason, note); reportTarget = null }
        )
    }
    if (showCreatePost) {
        CreatePostDialog(
            onDismiss = { showCreatePost = false },
            onPost    = { content -> vm.createPost(content); showCreatePost = false }
        )
    }
    if (showNotifPanel) {
        NotifPanel(notifs = notifs, onDismiss = { showNotifPanel = false })
    }
}

// ── Top Bar ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FeedTopBar(
    unreadCount    : Int,
    unreadDmCount  : Int = 0,
    onNotif        : () -> Unit,
    onCreatePost   : () -> Unit,
    onOpenDms      : () -> Unit = {},
    selectedFilter : String,
    onFilterChange : (String) -> Unit,
    currentPage    : Int,
    onTabSelect    : (Int) -> Unit
) {
    val showFilters = currentPage == 0
    Surface(
        color           = FCard,
        shadowElevation = 0.dp,
        modifier        = Modifier.fillMaxWidth()
    ) {
        Column {
            // ── Masthead row ─────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 18.dp, bottom = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand block
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Small sage dot
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(FAccent)
                        )
                        Text(
                            "Quiet Space",
                            fontSize      = 18.sp,
                            fontWeight    = FontWeight.Bold,
                            color         = FPrimary,
                            letterSpacing = (-0.3).sp
                        )
                    }
                    Text(
                        "anonymous · thoughtful · safe",
                        fontSize      = 11.sp,
                        color         = FMuted,
                        letterSpacing = 0.3.sp
                    )
                }

                // Action icons — soft and minimal
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // DM icon
                    Box {
                        IconButton(onClick = onOpenDms, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Rounded.MailOutline,
                                contentDescription = "Messages",
                                tint     = if (unreadDmCount > 0) FAccent else FMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (unreadDmCount > 0) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(FAccent)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-3).dp, y = 3.dp)
                                    .border(1.dp, FCard, CircleShape)
                            )
                        }
                    }
                    // Notif icon
                    Box {
                        IconButton(onClick = onNotif, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Rounded.NotificationsNone, null,
                                tint     = if (unreadCount > 0) FAccent else FMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (unreadCount > 0) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(FAccent)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-3).dp, y = 3.dp)
                                    .border(1.dp, FCard, CircleShape)
                            )
                        }
                    }
                    // Compose
                    IconButton(onClick = onCreatePost, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.EditNote, null,
                            tint     = FPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ── Tab row ─────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                listOf("Thoughts" to Icons.Rounded.Forum, "Sparks" to Icons.Rounded.AutoAwesome)
                    .forEachIndexed { idx, (title, icon) ->
                        val selected = currentPage == idx
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) { onTabSelect(idx) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon, null,
                                    tint = if (selected) FAccent else FMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    title,
                                    fontSize   = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color      = if (selected) FAccent else FMuted
                                )
                            }
                            // Underline indicator
                            val indicatorWidth by animateDpAsState(
                                targetValue   = if (selected) 24.dp else 0.dp,
                                animationSpec = tween(200),
                                label         = "tab_ind_$idx"
                            )
                            Box(
                                Modifier
                                    .width(indicatorWidth)
                                    .height(1.5.dp)
                                    .background(FAccent, RoundedCornerShape(1.dp))
                            )
                        }
                    }
            }

            // ── Mood filter tags (Posts tab only) ─────────────────────────────
            AnimatedVisibility(
                visible = showFilters,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.padding(top = 2.dp, bottom = 10.dp)
                ) {
                    items(moodFilters) { label ->
                        val selected = label == selectedFilter
                        val indicatorAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = tween(160),
                            label = "filterAlpha"
                        )
                        Column(
                            Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) { onFilterChange(label) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                label,
                                fontSize   = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (selected) FPrimary else FMuted
                            )
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .height(1.dp)
                                    .background(FAccent.copy(alpha = indicatorAlpha))
                            )
                        }
                    }
                }
            }

            // Bottom hairline
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(FBorder)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedContent(
    posts         : List<IntrovertPost>,
    vm            : IntrovertFeedViewModel,
    listState     : LazyListState,
    myConnections : Set<String>,
    myUid         : String,
    onLike        : (String) -> Unit,
    onReport      : (String) -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = {
            isRefreshing = true
            vm.refreshPosts()
            scope.launch { delay(1200); isRefreshing = false }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state             = listState,
            modifier          = Modifier.fillMaxSize().background(FBg),
            contentPadding    = PaddingValues(bottom = 88.dp)
        ) {
            items(posts, key = { it.postId }) { post ->
                PostCard(
                    post        = post,
                    vm          = vm,
                    isConnected = myConnections.contains(post.authorId),
                    isOwnPost   = post.authorId == myUid,
                    onLike      = { onLike(post.postId) },
                    onReport    = { onReport(post.postId) },
                    onConnect   = { vm.sendConnect(post.authorId, post.authorAlias) }
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

// ── Post Card — Journal / Thought Board aesthetic ───────────────────────────────────────
@Composable
private fun PostCard(
    post        : IntrovertPost,
    vm          : IntrovertFeedViewModel,
    isConnected : Boolean,
    isOwnPost   : Boolean,
    onLike      : () -> Unit,
    onReport    : () -> Unit,
    onConnect   : () -> Unit
) {
    var showMenu     by remember { mutableStateOf(false) }
    var showComments by rememberSaveable { mutableStateOf(false) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(320))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FCard)
                .border(1.dp, FBorder, RoundedCornerShape(16.dp))
        ) {
            // ── Top row: timestamp + menu ─────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    timeAgo(post.createdAt),
                    fontSize = 10.sp,
                    color    = FMuted,
                    letterSpacing = 0.3.sp
                )

                // Three-dot menu
                Box {
                    IconButton(
                        onClick  = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MoreHoriz, null,
                            tint     = FMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    DropdownMenu(
                        expanded         = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier         = Modifier.background(FCard)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Flag, null,
                                        tint = FRed, modifier = Modifier.size(14.dp))
                                    Text("Report post", color = FRed,
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            },
                            onClick = { showMenu = false; onReport() }
                        )
                    }
                }
            }

            // ── Content — the thought is the hero ────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    // Opening quote mark — editorial touch
                    Text(
                        "\u201C",
                        fontSize   = 36.sp,
                        color      = FAccent.copy(alpha = 0.35f),
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 20.sp
                    )
                    Text(
                        post.content,
                        fontSize      = 15.sp,
                        color         = FPrimary,
                        lineHeight    = 24.sp,
                        letterSpacing = (-0.1).sp,
                        modifier      = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // ── Hairline ──────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(FBorder))

            // ── Footer: author info + actions ─────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Author chip — compact, left-aligned
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tiny monogram
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(avatarColor(post.authorId)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            post.authorAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "◦",
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color      = avatarTextColor(post.authorId)
                        )
                    }
                    Text(
                        post.authorAlias,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Medium,
                        color         = FSecondary,
                        letterSpacing = (-0.1).sp
                    )
                    if (!isOwnPost && !isConnected) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .border(0.7.dp, FAccent.copy(0.4f), RoundedCornerShape(20.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onConnect
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "+ connect",
                                fontSize   = 9.sp,
                                color      = FAccent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (!isOwnPost && isConnected) {
                        Text(
                            "· friend",
                            fontSize = 10.sp,
                            color    = FAccent.copy(0.7f)
                        )
                    }
                }

                // Action icons — comment + share only (no likes)
                val context = LocalContext.current
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick  = { showComments = !showComments },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (showComments) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline,
                            null,
                            tint     = if (showComments) FAccent else FMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick  = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT,
                                    "\"${post.content}\"\n\n— from Veil")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.IosShare, null,
                            tint     = FMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Comments section
            if (showComments) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(FBorder))
                CommentSection(postId = post.postId, vm = vm)
            }
        }
    }
}


// ── Post Report Dialog ────────────────────────────────────────────────────────
@Composable
private fun PostReportDialog(
    onDismiss : () -> Unit,
    onSubmit  : (reason: String, note: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf("") }
    var note           by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(20.dp),
        containerColor   = FCard,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, null, tint = FRed, modifier = Modifier.size(20.dp))
                Text("Report Post", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = FPrimary)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("What's wrong with this post?", fontSize = 13.sp, color = FSecondary,
                    modifier = Modifier.padding(bottom = 4.dp))
                VeilSafetyUtil.POST_REPORT_REASONS.forEach { reason ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedReason == reason) FRedSoft else FSurface)
                            .border(1.dp,
                                if (selectedReason == reason) FRed.copy(0.30f) else FBorder,
                                RoundedCornerShape(12.dp))
                            .clickable { selectedReason = reason }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick  = { selectedReason = reason },
                            colors   = RadioButtonDefaults.colors(selectedColor = FRed)
                        )
                        Text(reason, fontSize = 13.sp, color = FPrimary, fontWeight = FontWeight.Medium)
                    }
                }
                OutlinedTextField(
                    value         = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    placeholder   = { Text("Additional details (optional)", fontSize = 13.sp, color = FMuted) },
                    modifier      = Modifier.fillMaxWidth().padding(top = 4.dp),
                    minLines      = 2,
                    maxLines      = 3,
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = FRed,
                        unfocusedBorderColor    = FBorderDark,
                        focusedContainerColor   = FCard,
                        unfocusedContainerColor = FCard
                    )
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(FSurface)
                        .padding(10.dp)
                ) {
                    Icon(Icons.Rounded.Info, null, tint = FSecondary, modifier = Modifier.size(14.dp))
                    Text("False reports may result in action against your account.",
                        fontSize = 11.sp, color = FSecondary, lineHeight = 16.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (selectedReason.isNotBlank()) onSubmit(selectedReason, note) },
                enabled  = selectedReason.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = FRed),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) { Text("Submit Report", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FSecondary, fontWeight = FontWeight.Medium)
            }
        }
    )
}

// ── Create Post Dialog ────────────────────────────────────────────────────────
@Composable
private fun CreatePostDialog(
    onDismiss : () -> Unit,
    onPost    : (String) -> Unit
) {
    var content        by remember { mutableStateOf("") }
    var showBanWarning by remember { mutableStateOf(false) }
    var hasProfanity   by remember { mutableStateOf(false) }
    val maxLength      = 500
    val focusReq       = remember { FocusRequester() }
    val myUid          = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    if (showBanWarning) {
        AlertDialog(
            onDismissRequest = { showBanWarning = false },
            confirmButton    = { TextButton(onClick = { showBanWarning = false }) { Text("Got it", fontWeight = FontWeight.SemiBold) } },
            title  = { Text("⚠️ Content Violation", fontWeight = FontWeight.Bold, color = FRed) },
            text   = { Text("Vulgar or abusive content leads to a permanent ban. Please revise.", fontSize = 14.sp, lineHeight = 21.sp, color = FPrimary) },
            shape  = RoundedCornerShape(20.dp),
            containerColor = FCard
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties       = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.55f))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(FCard)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .imePadding()
            ) {
                // ── Handle ────────────────────────────────────────────────────
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FMuted))
                }

                // ── Action row ────────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 6.dp, bottom = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick         = onDismiss,
                        contentPadding  = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("Cancel", fontSize = 15.sp, color = FPrimary, fontWeight = FontWeight.Normal)
                    }
                    Text("New Thought", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FPrimary)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (content.trim().length >= 10 && !hasProfanity) FAccent
                                else FMuted
                            )
                            .clickable(
                                enabled           = content.trim().length >= 10 && !hasProfanity,
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null
                            ) {
                                val t = content.trim()
                                if (VeilSafetyUtil.containsProfanity(t)) showBanWarning = true
                                else onPost(t)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Post", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                HorizontalDivider(color = FBorder, thickness = 1.dp)

                // ── Author + Input ────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(avatarColor(myUid)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", fontSize = 18.sp, color = avatarTextColor(myUid))
                    }

                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier              = Modifier.padding(bottom = 10.dp)
                        ) {
                            Text("Anonymous", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FPrimary)
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(FAccentSoft)
                                    .border(1.dp, FAccent.copy(0.25f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Lock, null, tint = FAccent, modifier = Modifier.size(10.dp))
                                    Text("Private", fontSize = 11.sp, color = FAccent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        BasicTextField(
                            value         = content,
                            onValueChange = {
                                if (it.length <= maxLength) {
                                    content = it
                                    hasProfanity = VeilSafetyUtil.containsProfanity(it)
                                }
                            },
                            modifier      = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 120.dp)
                                .focusRequester(focusReq),
                            textStyle     = LocalTextStyle.current.copy(
                                fontSize   = 16.sp,
                                color      = FPrimary,
                                lineHeight = 24.sp
                            ),
                            decorationBox = { inner ->
                                if (content.isEmpty()) Text(
                                    "What's on your introvert mind…",
                                    fontSize = 16.sp, color = FMuted, lineHeight = 24.sp
                                )
                                inner()
                            }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── Footer ────────────────────────────────────────────────────
                if (hasProfanity) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FRedSoft)
                            .border(1.dp, FRed.copy(0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = FRed, modifier = Modifier.size(14.dp))
                        Text("Content violates community guidelines", fontSize = 12.sp, color = FRed, fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider(color = FBorder, thickness = 1.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(FCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Forum, null, tint = FSecondary, modifier = Modifier.size(14.dp))
                        Text("Visible to all introverts", fontSize = 12.sp, color = FSecondary)
                    }
                    // Char counter ring
                    val remaining  = maxLength - content.length
                    val fraction   = content.length.toFloat() / maxLength
                    val cColor     = when {
                        fraction >= 1f    -> FRed
                        fraction >= 0.85f -> FRed.copy(0.7f)
                        else             -> FSecondary
                    }
                    Text(
                        "$remaining",
                        fontSize   = 12.sp,
                        color      = cColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focusReq.requestFocus() } }
}

// ── Empty State ───────────────────────────────────────────────────────────────────────────
@Composable
private fun FeedEmpty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            // Leaf / serene icon
            Text("🌿", fontSize = 44.sp)
            Text(
                "This space is quiet",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = FPrimary,
                textAlign  = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )
            Text(
                "Be the first to share a thought.\nYour name stays hidden — always.",
                fontSize  = 14.sp,
                color     = FSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Shimmer ───────────────────────────────────────────────────────────────────
@Composable
private fun FeedShimmer() {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        0.25f, 0.6f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        "alpha"
    )

    LazyColumn(
        modifier            = Modifier.fillMaxSize().background(FBg),
        contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(5) {
            Surface(
                shape           = RoundedCornerShape(16.dp),
                color           = FCard,
                border          = BorderStroke(1.dp, FBorder),
                shadowElevation = 2.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(FMuted.copy(alpha)))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(FMuted.copy(alpha)))
                            Box(Modifier.width(60.dp).height(12.dp).clip(RoundedCornerShape(7.dp)).background(FMuted.copy(alpha * 0.7f)))
                        }
                    }
                    HorizontalDivider(color = FBorder, thickness = 1.dp)
                    // Content lines
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(15.dp).clip(RoundedCornerShape(7.dp)).background(FMuted.copy(alpha)))
                        Box(Modifier.fillMaxWidth(0.88f).height(15.dp).clip(RoundedCornerShape(7.dp)).background(FMuted.copy(alpha)))
                        Box(Modifier.fillMaxWidth(0.60f).height(15.dp).clip(RoundedCornerShape(7.dp)).background(FMuted.copy(alpha * 0.6f)))
                    }
                    HorizontalDivider(color = FBorder, thickness = 1.dp)
                    // Action bar
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(3) {
                            Box(Modifier.width(48.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(FMuted.copy(alpha * 0.7f)))
                        }
                    }
                }
            }
        }
    }
}

// ── Comment Section ───────────────────────────────────────────────────────────
@Composable
private fun CommentSection(postId: String, vm: IntrovertFeedViewModel) {
    val commentsMap by vm.comments.collectAsStateWithLifecycle()
    val scope       = rememberCoroutineScope()
    val focusReq    = remember { FocusRequester() }

    var inputText        by remember { mutableStateOf("") }
    var replyToCommentId by remember { mutableStateOf("") }
    var replyToAlias     by remember { mutableStateOf("") }

    LaunchedEffect(postId) { vm.listenComments(postId) }
    DisposableEffect(postId) { onDispose { vm.stopListenComments(postId) } }

    val postComments = commentsMap[postId] ?: emptyList()

    Column(Modifier.fillMaxWidth().background(FSurface)) {
        postComments.forEach { comment ->
            CommentItem(
                comment = comment,
                postId  = postId,
                vm      = vm,
                onReply = { alias ->
                    replyToCommentId = comment.commentId
                    replyToAlias     = alias
                    inputText        = "@$alias "
                    scope.launch { delay(100); runCatching { focusReq.requestFocus() } }
                }
            )
            HorizontalDivider(color = FBorder, thickness = 0.5.dp, modifier = Modifier.padding(start = 68.dp))
        }
        if (postComments.isEmpty()) {
            Text(
                "No comments yet · be the first ✨",
                fontSize = 13.sp,
                color    = FSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        // Reply-to banner
        if (replyToAlias.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(FAccentSoft)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Reply, null, tint = FAccent, modifier = Modifier.size(14.dp))
                    Text("Replying to @$replyToAlias", fontSize = 12.sp, color = FAccent, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Rounded.Close, null, tint = FSecondary,
                    modifier = Modifier.size(16.dp).clickable {
                        replyToAlias = ""; replyToCommentId = ""; inputText = ""
                    })
            }
        }

        // Input bar
        HorizontalDivider(color = FBorder, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .background(FCard)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BasicTextField(
                value         = inputText,
                onValueChange = { inputText = it },
                modifier      = Modifier
                    .weight(1f)
                    .focusRequester(focusReq)
                    .clip(RoundedCornerShape(20.dp))
                    .background(FSurface)
                    .border(1.dp, FBorderDark, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle     = LocalTextStyle.current.copy(fontSize = 13.sp, color = FPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) {
                        if (replyToCommentId.isNotBlank()) {
                            vm.postReply(postId, replyToCommentId, inputText, replyToAlias) {
                                inputText = ""; replyToAlias = ""; replyToCommentId = ""
                            }
                        } else {
                            vm.postComment(postId, inputText) { inputText = "" }
                        }
                    }
                }),
                decorationBox = { inner ->
                    if (inputText.isEmpty()) Text(
                        if (replyToAlias.isNotBlank()) "Reply to @$replyToAlias…" else "Write a comment…",
                        fontSize = 13.sp, color = FMuted
                    )
                    inner()
                }
            )
            AnimatedVisibility(
                visible = inputText.isNotBlank(),
                enter   = scaleIn() + fadeIn(),
                exit    = scaleOut() + fadeOut()
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(FAccent)
                        .clickable {
                            if (replyToCommentId.isNotBlank()) {
                                vm.postReply(postId, replyToCommentId, inputText, replyToAlias) {
                                    inputText = ""; replyToAlias = ""; replyToCommentId = ""
                                }
                            } else {
                                vm.postComment(postId, inputText) { inputText = "" }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

// ── Comment Item ──────────────────────────────────────────────────────────────
@Composable
private fun CommentItem(
    comment : FeedComment,
    postId  : String,
    vm      : IntrovertFeedViewModel,
    onReply : (String) -> Unit
) {
    val repliesMap  by vm.replies.collectAsStateWithLifecycle()
    var showReplies by rememberSaveable { mutableStateOf(false) }
    val replyKey    = "${postId}__${comment.commentId}"

    LaunchedEffect(showReplies) { if (showReplies) vm.listenReplies(postId, comment.commentId) }
    DisposableEffect(comment.commentId) {
        onDispose { if (showReplies) vm.stopListenReplies(postId, comment.commentId) }
    }

    val replies = repliesMap[replyKey] ?: emptyList()

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(avatarColor(comment.authorId)),
                Alignment.Center
            ) {
                Text(
                    comment.authorAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = avatarTextColor(comment.authorId)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(comment.authorAlias, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FPrimary)
                    Text("·", fontSize = 12.sp, color = FMuted)
                    Text(timeAgo(comment.createdAt), fontSize = 11.sp, color = FSecondary)
                }
                MentionText(comment.text, sz = 14.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(remember { MutableInteractionSource() }, null) { onReply(comment.authorAlias) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text("Reply", fontSize = 12.sp, color = FSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    if (comment.replyCount > 0) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(remember { MutableInteractionSource() }, null) { showReplies = !showReplies }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                if (showReplies) "Hide replies"
                                else "View ${comment.replyCount} ${if (comment.replyCount == 1) "reply" else "replies"}",
                                fontSize   = 12.sp,
                                color      = FAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        if (showReplies) {
            replies.forEach { reply ->
                ReplyItem(reply = reply, onReply = { onReply(it) })
            }
        }
    }
}

// ── Reply Item ────────────────────────────────────────────────────────────────
@Composable
private fun ReplyItem(reply: FeedReply, onReply: (String) -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FCard.copy(0.6f))
            .padding(start = 56.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(avatarColor(reply.authorId)),
            Alignment.Center
        ) {
            Text(
                reply.authorAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                color      = avatarTextColor(reply.authorId)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(reply.authorAlias, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FPrimary)
                Text("·", fontSize = 11.sp, color = FMuted)
                Text(timeAgo(reply.createdAt), fontSize = 11.sp, color = FSecondary)
            }
            MentionText(reply.text, sz = 13.sp)
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onReply(reply.authorAlias) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Reply", fontSize = 11.sp, color = FSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── @mention text ─────────────────────────────────────────────────────────────
@Composable
private fun MentionText(text: String, modifier: Modifier = Modifier, sz: TextUnit = 14.sp) {
    val annotated = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text[i] == '@') {
                val spaceIdx = text.indexOf(' ', i)
                val nlIdx    = text.indexOf('\n', i)
                val end = when {
                    spaceIdx == -1 && nlIdx == -1 -> text.length
                    spaceIdx == -1 -> nlIdx
                    nlIdx == -1    -> spaceIdx
                    else           -> minOf(spaceIdx, nlIdx)
                }
                withStyle(SpanStyle(color = FAccent, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(i, end))
                }
                i = end
            } else {
                val next = text.indexOf('@', i).let { if (it == -1) text.length else it }
                append(text.substring(i, next))
                i = next
            }
        }
    }
    Text(annotated, fontSize = sz, color = FPrimary, lineHeight = 20.sp, modifier = modifier)
}

// ── Notification Panel ────────────────────────────────────────────────────────
@Composable
private fun NotifPanel(notifs: List<FeedNotif>, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties       = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.45f))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(FCard)
                    .clickable(remember { MutableInteractionSource() }, null) {}
            ) {
                // Handle
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FMuted))
                }

                // Title row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Notifications", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FPrimary)
                    if (notifs.isNotEmpty()) {
                        Text("${notifs.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = FAccent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(FAccentSoft)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                HorizontalDivider(color = FBorder, thickness = 1.dp)

                if (notifs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsNone, null,
                                tint = FMuted, modifier = Modifier.size(44.dp))
                            Text("All caught up!", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FPrimary)
                            Text("No new notifications", fontSize = 13.sp, color = FSecondary)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(notifs, key = { it.notifId }) { n ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (!n.read) FAccentSoft else Color.Transparent)
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment     = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                val icon = when (n.type) {
                                    "reply"   -> Icons.Rounded.Reply
                                    "comment" -> Icons.Rounded.ChatBubbleOutline
                                    else      -> Icons.Rounded.PersonAdd
                                }
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(FAccentSoft)
                                        .border(1.dp, FAccent.copy(0.15f), RoundedCornerShape(12.dp)),
                                    Alignment.Center
                                ) {
                                    Icon(icon, null, tint = FAccent, modifier = Modifier.size(18.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        when (n.type) {
                                            "reply"   -> "${n.fromAlias} replied to your comment"
                                            "comment" -> "${n.fromAlias} commented on your post"
                                            else      -> "${n.fromAlias} sent you a friend request"
                                        },
                                        fontSize   = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = FPrimary
                                    )
                                    if (n.previewText.isNotBlank()) {
                                        Text(
                                            "\"${n.previewText}\"",
                                            fontSize  = 12.sp,
                                            color     = FSecondary,
                                            maxLines  = 1,
                                            overflow  = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(timeAgo(n.createdAt), fontSize = 11.sp, color = FMuted)
                                }
                                if (!n.read) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(FAccent)
                                            .align(Alignment.CenterVertically)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color    = FBorder,
                                thickness = 0.5.dp,
                                modifier  = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Instagram-Style Bottom Sheet Menu ─────────────────────────────────────────
@Composable
private fun InstagramStyleMenu(
    onDismiss : () -> Unit,
    onReport  : () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(FCard)
                    .clickable(remember { MutableInteractionSource() }, null) {}
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FMuted))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onReport() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Rounded.Flag, null, tint = FRed, modifier = Modifier.size(22.dp))
                    Text("Report post", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FRed)
                }
                HorizontalDivider(color = FBorder, thickness = 0.5.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FSecondary)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
