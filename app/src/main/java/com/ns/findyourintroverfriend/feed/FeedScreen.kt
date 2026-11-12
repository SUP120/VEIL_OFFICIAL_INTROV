package com.ns.findyourintroverfriend.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import coil.compose.AsyncImage
import com.ns.findyourintroverfriend.util.VeilSafetyUtil
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.VeilThemeManager

// ── Dark-mode state ──────────────────────────────────────────────────────────────────
// _fsDark is synced from LocalVeilIsDark via SideEffect in FeedScreen.
// Being file-level snapshot state, PostCard / CommentsSheet / FeedNotifPanel
// all recompose automatically when the theme changes.
private var _fsDark by mutableStateOf(VeilThemeManager.initialResolvedDark)

// ── Peaceful Serenity Palette ─────────────────────────────────────────────────
// Light: warm parchment backgrounds, dusty mauve accents, sage green, soft stone text
// Dark: deep ink with gentle warm tones
private val IGBg         get() = if (_fsDark) Color(0xFF0F0F14) else Color(0xFFFBF8F4)   // warm parchment / deep ink
private val IGCard        get() = if (_fsDark) Color(0xFF17171E) else Color(0xFFFFFFFF)   // pure card surface
private val IGSurface     get() = if (_fsDark) Color(0xFF1E1E27) else Color(0xFFF5F2EE)   // subtle tinted surface
private val IGBlack       get() = if (_fsDark) Color(0xFFE0DDD8) else Color(0xFF2C2825)   // warm near-black
private val IGGray        get() = if (_fsDark) Color(0xFF918E8A) else Color(0xFF9A9490)   // warm stone gray
private val IGMuted       get() = if (_fsDark) Color(0xFF5A5855) else Color(0xFFC4BFBA)   // muted warm
private val IGDivider     get() = if (_fsDark) Color(0xFF232328) else Color(0xFFEDE9E4)   // hairline divider
private val IGBorderSoft  get() = if (_fsDark) Color(0xFF2C2C34) else Color(0xFFE8E3DC)   // card border
// Accent: dusty mauve/rose — calm, feminine, introverted
private val IGAccent      = Color(0xFFA07C8C)   // dusty mauve
private val IGAccentSoft  get() = if (_fsDark) Color(0xFF251520) else Color(0xFFF7EFF3)   // very soft mauve tint
// Secondary accent: sage green (nature, calm)
private val IGSage        = Color(0xFF7A9E87)   // sage green
private val IGSageSoft    get() = if (_fsDark) Color(0xFF0E1E13) else Color(0xFFEFF5F1)   // very soft sage tint
// Danger — muted terracotta, not harsh red
private val IGRed         = Color(0xFFBF6B6B)   // muted terracotta
private val IGBlue        = Color(0xFF7B9AC4)   // soft dusty blue (for reply/comment accents)
private val IGLightGray   get() = if (_fsDark) Color(0xFF1C1C23) else Color(0xFFF4F0EB)



private fun igAgo(ms: Long): String {
    if (ms == 0L) return ""
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000      -> "just now"
        d < 3_600_000   -> "${d / 60_000}m"
        d < 86_400_000  -> "${d / 3_600_000}h"
        d < 604_800_000 -> "${d / 86_400_000}d"
        else            -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

// ── Main Feed Screen ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm               : FeedViewModel    = viewModel(),
    cookieVm         : com.ns.findyourintroverfriend.billing.CookieViewModel = viewModel(),
    approachVm       : FeedApproachViewModel = viewModel(),
    modifier         : Modifier = Modifier,
    bottomNavPadding : androidx.compose.ui.unit.Dp = 0.dp,
    onOpenDms        : () -> Unit = {},
    onOpenCookieStore: () -> Unit = {},
    onOpenUserProfile: (uid: String) -> Unit = {}
) {
    val posts          by vm.filteredPosts.collectAsStateWithLifecycle()
    val loading        by vm.loading.collectAsStateWithLifecycle()
    val isRefreshing   by vm.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingMore  by vm.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore        by vm.hasMore.collectAsStateWithLifecycle()
    val myAlias        by vm.myAlias.collectAsStateWithLifecycle()
    val myPhoto        by vm.myPhoto.collectAsStateWithLifecycle()
    val cookieBalance  by cookieVm.cookieBalance.collectAsStateWithLifecycle()
    val myApproaches   by approachVm.myApproaches.collectAsStateWithLifecycle()
    val freeLeft       by approachVm.freeLeft.collectAsStateWithLifecycle()
    val approachResult by approachVm.result.collectAsStateWithLifecycle()
    val savedPostIds   by vm.savedPostIds.collectAsStateWithLifecycle()
    val selectedTag    by vm.selectedTag.collectAsStateWithLifecycle()

    val pagerState      = rememberPagerState(pageCount = { 2 })
    val scope           = rememberCoroutineScope()
    val postsListState  = rememberLazyListState()
    val sparksListState = rememberLazyListState()

    var showCreate       by remember { mutableStateOf(false) }
    var showSparksCreate by remember { mutableStateOf(false) }
    var commentPost      by remember { mutableStateOf<VeilPost?>(null) }
    var reportTarget     by remember { mutableStateOf<String?>(null) }
    var pendingApproachPost by remember { mutableStateOf<VeilPost?>(null) }
    var showNotifPanel   by remember { mutableStateOf(false) }

    // Collapse tab bar when either feed is scrolled
    val postsScrolled  = postsListState.firstVisibleItemIndex  > 0 || postsListState.firstVisibleItemScrollOffset  > 40
    val sparksScrolled = sparksListState.firstVisibleItemIndex > 0 || sparksListState.firstVisibleItemScrollOffset > 40
    val tabCollapsed   = when (pagerState.currentPage) { 1 -> sparksScrolled; else -> postsScrolled }
    val snackbarHostState = remember { SnackbarHostState() }

    val unreadCount    by vm.unreadCount.collectAsStateWithLifecycle()
    val notifItems     by vm.notifItems.collectAsStateWithLifecycle()

    // DM unread count — includes both Feed friends AND Swipe connections
    val matchesVm      = viewModel<com.ns.findyourintroverfriend.swipe.MatchesViewModel>()
    val unreadMatchIds by matchesVm.unreadMatchIds.collectAsStateWithLifecycle()
    val friendMatches  by matchesVm.friendMatches.collectAsStateWithLifecycle()
    val swipeMatches   by matchesVm.matches.collectAsStateWithLifecycle()
    val unreadDmCount  = remember(unreadMatchIds, friendMatches, swipeMatches) {
        (friendMatches + swipeMatches).count { it.matchId in unreadMatchIds }
    }

    // Handle approach result → show snackbar feedback
    LaunchedEffect(approachResult) {
        when (val r = approachResult) {
            is ApproachResult.Sent        -> { snackbarHostState.showSnackbar("🤝 Approached!"); approachVm.resetResult() }
            is ApproachResult.NeedCookies -> approachVm.resetResult()  // pendingApproachPost already set in the button's onClick
            is ApproachResult.Error       -> { snackbarHostState.showSnackbar("❌ ${r.message}"); approachVm.resetResult() }
            else -> Unit
        }
    }

    // ── Sync theme with app-wide VeilTheme ─────────────────────────────────────
    val _veilIsDark = LocalVeilIsDark.current
    SideEffect { _fsDark = _veilIsDark }

    Box(modifier.fillMaxSize().background(IGBg)) {
        // Snackbar host at bottom of the main box
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = bottomNavPadding + 16.dp)
        )
        Column(Modifier.fillMaxSize()) {
            // ── Offline banner ────────────────────────────────────────────────
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val isOnline = remember {
                derivedStateOf {
                    val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                        as android.net.ConnectivityManager
                    cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }
            }
            androidx.compose.animation.AnimatedVisibility(visible = !isOnline.value) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color(0xFFF59E0B))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "📡 No internet — showing cached posts",
                        fontSize  = 11.sp,
                        color     = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Top bar — Peaceful, airy, minimal ────────────────────────────
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (_fsDark) Color(0xFF13131A) else Color(0xFFFEFBF7),
                                IGBg
                            )
                        )
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Brand — soft editorial wordmark
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            // Tiny accent dot
                            Box(
                                Modifier.size(6.dp).clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(IGAccent, IGAccent.copy(0.5f))
                                        )
                                    )
                            )
                            Text(
                                "Introvert Feed",
                                fontSize      = 20.sp,
                                fontWeight    = FontWeight.SemiBold,
                                color         = IGBlack,
                                letterSpacing = (-0.4).sp
                            )
                        }
                        Text(
                            "your quiet space to share",
                            fontSize      = 10.5.sp,
                            color         = IGMuted,
                            letterSpacing = 0.2.sp
                        )
                    }
                    // Action icons — soft, minimal
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // ── Notification bell ────────────────────────────────────
                        Box {
                            IconButton(onClick = {
                                showNotifPanel = true
                                vm.markAllNotifsRead()
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Rounded.NotificationsNone, null,
                                    tint     = if (unreadCount > 0) IGAccent else IGGray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (unreadCount > 0) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(IGAccent)
                                        .border(1.5.dp, IGBg, CircleShape)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-3).dp, y = 3.dp)
                                )
                            }
                        }
                        // ── DM Inbox button with unread badge ───────────────
                        Box {
                            IconButton(onClick = onOpenDms, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(
                                        id = com.ns.findyourintroverfriend.R.drawable.ic_feed_dm
                                    ),
                                    contentDescription = "Messages",
                                    tint     = if (unreadDmCount > 0) IGSage else IGGray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (unreadDmCount > 0) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(IGSage)
                                        .border(1.5.dp, IGBg, CircleShape)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-3).dp, y = 3.dp)
                                )
                            }
                        }
                        // ── Cookie balance badge ───────────────────────────
                        com.ns.findyourintroverfriend.billing.CookieBadge(
                            balance  = cookieBalance,
                            onClick  = onOpenCookieStore,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                HorizontalDivider(color = IGDivider, thickness = 0.5.dp)

                // ── Tab row: Posts | Innerly — collapses on scroll ───────────────
                AnimatedVisibility(
                    visible = !tabCollapsed,
                    enter   = expandVertically(tween(180)) + fadeIn(tween(160)),
                    exit    = shrinkVertically(tween(160)) + fadeOut(tween(120))
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().background(IGBg)
                                .padding(horizontal = 24.dp)
                        ) {
                            listOf(
                                "Posts" to Icons.Rounded.Forum,
                                "Innerly" to Icons.Rounded.AutoAwesome
                            ).forEachIndexed { idx, (title, icon) ->
                                val selected = pagerState.currentPage == idx
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication        = null
                                        ) { scope.launch { pagerState.animateScrollToPage(idx) } }
                                        .padding(vertical = 11.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            icon, null,
                                            tint     = if (selected) IGAccent else IGMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            title,
                                            fontSize   = 13.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color      = if (selected) IGBlack else IGMuted,
                                            letterSpacing = 0.1.sp
                                        )
                                    }
                                    val indicatorWidth by animateDpAsState(
                                        targetValue   = if (selected) 22.dp else 0.dp,
                                        animationSpec = tween(220),
                                        label         = "feedtab_$idx"
                                    )
                                    Box(
                                        Modifier
                                            .width(indicatorWidth)
                                            .height(1.5.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(IGAccent)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                    }
                }
            }

            // ── Paged content ───────────────────────────────────────────────
            HorizontalPager(
                state             = pagerState,
                modifier          = Modifier.weight(1f),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> when {
                        loading         -> FeedSkeletonLoader()
                        posts.isEmpty() -> FeedEmptyState { showCreate = true }
                        else -> {
                            val listState = postsListState
                            LaunchedEffect(listState.firstVisibleItemIndex) {
                                val totalItems  = listState.layoutInfo.totalItemsCount
                                val lastVisible = listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size
                                if (totalItems > 0 && lastVisible >= totalItems - 3 && hasMore && !isLoadingMore) vm.loadMore()
                            }
                            @OptIn(ExperimentalMaterial3Api::class)
                            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh    = { vm.refresh() },
                                modifier     = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    state    = listState,
                                    modifier = Modifier.fillMaxSize().background(IGBg),
                                    contentPadding = PaddingValues(top = 6.dp, bottom = 88.dp)
                                ) {
                                    // ── Tag filter chips (sticky) ─────────────────
                                    stickyHeader(key = "tagFilter") {
                                        TagFilterRow(
                                            selectedTag   = selectedTag,
                                            onTagSelected = { vm.setTagFilter(it) }
                                        )
                                    }
                                    items(posts, key = { it.postId }) { post ->
                                        PostCard(
                                            post               = post,
                                            myUid              = vm.uid,
                                            userReaction       = vm.getUserReaction(post),
                                            onReact            = { emoji -> vm.toggleReaction(post, emoji) },
                                            isBookmarked       = post.postId in savedPostIds,
                                            onBookmark         = { vm.toggleBookmark(post.postId) },
                                            onComment          = { commentPost = post },
                                            onDelete           = { vm.deletePost(post.postId) },
                                            onReport           = { vm.hidePostLocally(post.postId); reportTarget = post.postId },
                                            onOpenProfile      = { onOpenUserProfile(post.authorUid) },
                                            alreadyApproached  = post.authorUid in myApproaches,
                                            isOwnPost          = post.authorUid == vm.uid,
                                            onApproach         = {
                                                if (freeLeft > 0) approachVm.sendFreeApproach(post.authorUid, post.authorAlias, myAlias, "", myPhoto)
                                                else pendingApproachPost = post
                                            }
                                        )
                                    }
                                    item {
                                        if (isLoadingMore) {
                                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), Alignment.Center) {
                                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = IGBlack.copy(0.4f))
                                            }
                                        } else if (!hasMore && posts.isNotEmpty()) {
                                            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), Arrangement.Center, Alignment.CenterVertically) {
                                                HorizontalDivider(Modifier.width(60.dp), color = IGDivider, thickness = 0.8.dp)
                                                Text("  you're all caught up  ", fontSize = 11.sp, color = IGGray)
                                                HorizontalDivider(Modifier.width(60.dp), color = IGDivider, thickness = 0.8.dp)
                                            }
                                        } else { Spacer(Modifier.height(100.dp)) }
                                    }
                                }
                            }
                        }
                    }
                    else -> VeilSparksScreen(
                        listState       = sparksListState,
                        triggerCreate   = showSparksCreate,
                        onCreateHandled = { showSparksCreate = false }
                    )
                }
            }
        }

        // ── FAB — soft, gentle, peaceful ─────────────────────────────────────
        val fabInf   = rememberInfiniteTransition(label = "fab")
        val fabPulse by fabInf.animateFloat(
            1f, 1.04f,
            infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fp"
        )
        val fabHaptic = LocalHapticFeedback.current
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = bottomNavPadding)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .scale(fabPulse)
                    .shadow(
                        8.dp, CircleShape,
                        spotColor = IGAccent.copy(0.35f),
                        ambientColor = IGAccent.copy(0.15f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (_fsDark) Color(0xFF3A2230) else Color(0xFFF0E4EC),
                                if (_fsDark) Color(0xFF2C1A24) else Color(0xFFE8D5E0)
                            )
                        )
                    )
                    .clickable {
                        fabHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (pagerState.currentPage == 0) showCreate = true
                        else showSparksCreate = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.EditNote, "New Post",
                    tint     = IGAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // ── Report Dialog ───────────────────────────────────────────────
    reportTarget?.let { postId ->
        FeedPostReportDialog(
            onDismiss = { reportTarget = null },
            onSubmit  = { reason, note ->
                vm.reportPost(postId, reason, note)
                reportTarget = null
            }
        )
    }

    // ── Comments bottom sheet ──────────────────────────────────────────
    commentPost?.let { post ->
        CommentsSheet(
            post              = post,
            vm                = vm,
            onDismiss         = { commentPost = null },
            onOpenUserProfile = onOpenUserProfile
        )
    }

    // ── Create post sheet ────────────────────────────────────────────
    if (showCreate) {
        CreatePostSheet(
            vm        = vm,
            myAlias   = myAlias,
            onDismiss = { showCreate = false }
        )
    }

    // ── Cookie approach dialog ───────────────────────────────────────
    pendingApproachPost?.let { post ->
        AlertDialog(
            onDismissRequest = { pendingApproachPost = null },
            title = {
                Text("Approach for Friendship", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = IGBlack)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your 3 free monthly approaches are used.",
                        color = IGGray, fontSize = 14.sp)
                    Text("Approaching ${post.authorAlias} costs 15 🍪 cookies.",
                        color = IGBlack, fontSize = 14.sp)
                    if (cookieBalance < 15) {
                        Text("You have $cookieBalance 🍪 — not enough.",
                            color = Color(0xFFEF4444), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = cookieBalance >= 15,
                    onClick = {
                        approachVm.sendPaidApproach(
                            toUid      = post.authorUid,
                            toAlias    = post.authorAlias,
                            myAlias    = myAlias,
                            myAvatar   = "",
                            myPhotoUrl = myPhoto,
                            cookieVm   = cookieVm
                        )
                        pendingApproachPost = null
                    }
                ) {
                    Text("Spend 15 🍪", color = IGBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (cookieBalance < 15) {
                        TextButton(onClick = { pendingApproachPost = null; onOpenCookieStore() }) {
                            Text("Get Cookies", color = Color(0xFFF59E0B))
                        }
                    }
                    TextButton(onClick = { pendingApproachPost = null }) { Text("Cancel") }
                }
            }
        )
    }
    if (showNotifPanel) {
        FeedNotifPanel(
            items     = notifItems,
            onDismiss = { showNotifPanel = false }
        )
    }
}

// ── Notification panel — gentle, airy bottom sheet ────────────────────────────
@Composable
private fun FeedNotifPanel(
    items    : List<Map<String, Any>>,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF2C2825).copy(0.38f))
                .clickable(remember{MutableInteractionSource()}, null) { onDismiss() },
            Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(IGCard)
                    .clickable(remember{MutableInteractionSource()}, null) {}
            ) {
                // Handle bar
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                    Box(Modifier.width(32.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(IGMuted.copy(0.4f)))
                }
                Row(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.NotificationsNone, null,
                        tint = IGAccent, modifier = Modifier.size(18.dp))
                    Text("Notifications", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = IGBlack)
                }
                HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(56.dp).clip(CircleShape).background(IGSurface),
                                Alignment.Center
                            ) {
                                Icon(Icons.Rounded.NotificationsNone, null,
                                    tint = IGMuted, modifier = Modifier.size(26.dp))
                            }
                            Text("All quiet here", fontSize = 15.sp,
                                fontWeight = FontWeight.Medium, color = IGBlack)
                            Text("Comments and replies will appear here",
                                fontSize = 12.sp, color = IGGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 36.dp))
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items) { n ->
                            val type    = n["type"] as? String ?: ""
                            val alias   = n["fromAlias"] as? String ?: ""
                            val preview = n["previewText"] as? String ?: ""
                            val read    = n["read"] as? Boolean ?: false
                            val ts      = n["createdAt"] as? Long ?: 0L
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (!read) IGAccentSoft else Color.Transparent)
                                    .padding(horizontal = 18.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(IGAccentSoft),
                                    Alignment.Center
                                ) {
                                    Icon(
                                        when(type) { "reply" -> Icons.Rounded.Reply; else -> Icons.Rounded.ChatBubbleOutline },
                                        null, tint = IGAccent, modifier = Modifier.size(17.dp)
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        when(type) { "reply" -> "$alias replied to your comment"; else -> "$alias commented on your post" },
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = IGBlack
                                    )
                                    if (preview.isNotBlank()) Text(
                                        "\"$preview\"",
                                        fontSize = 12.sp, color = IGGray,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(igAgo(ts), fontSize = 11.sp, color = IGMuted)
                                }
                                if (!read) Box(
                                    Modifier.size(7.dp).clip(CircleShape)
                                        .background(IGAccent).align(Alignment.CenterVertically)
                                )
                            }
                            HorizontalDivider(color = IGDivider, thickness = 0.3.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Post Card — Soft, journal-style card ──────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PostCard(
    post             : VeilPost,
    myUid            : String,
    userReaction     : String?    = null,
    onReact          : (String) -> Unit = {},
    isBookmarked     : Boolean    = false,
    onBookmark       : () -> Unit = {},
    onComment        : () -> Unit,
    onDelete         : () -> Unit,
    onReport         : () -> Unit,
    onOpenProfile    : () -> Unit = {},
    onApproach       : () -> Unit = {},
    alreadyApproached: Boolean    = false,
    isOwnPost        : Boolean    = false
) {
    val hasReaction = userReaction != null
    val isDev  = com.ns.findyourintroverfriend.util.VeilDevConfig.isDeveloper(post.authorUid)
    var doubleTapLike      by remember { mutableStateOf(false) }
    var showOptions        by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var visible            by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 12 }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 5.dp)
                .shadow(
                    elevation     = if (_fsDark) 0.dp else 2.dp,
                    shape         = RoundedCornerShape(18.dp),
                    ambientColor  = Color(0xFF8A7070).copy(0.08f),
                    spotColor     = Color(0xFF8A7070).copy(0.06f)
                )
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isDev)
                        if (_fsDark) Color(0xFF0D1826) else Color(0xFFF0F6FF)
                    else IGCard
                )
                .then(
                    if (isDev) Modifier.border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color(0xFF6B9FE8).copy(0.5f), Color(0xFF6B9FE8).copy(0.1f))
                        ),
                        RoundedCornerShape(18.dp)
                    ) else Modifier.border(0.5.dp, IGBorderSoft, RoundedCornerShape(18.dp))
                )
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).clickable(onClick = onOpenProfile)) {
                    IGAvatar(post.authorAlias, post.authorPhotoUrl, post.authorAvatar, 36.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable(onClick = onOpenProfile),
                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            post.authorAlias.ifBlank { "Anonymous" },
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = IGBlack,
                            letterSpacing = (-0.1).sp
                        )
                        if (isDev) {
                            Box(
                                Modifier.clip(RoundedCornerShape(50.dp))
                                    .background(Color(0xFF5B8ED4).copy(0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Rounded.Verified, null,
                                        tint = Color(0xFF5B8ED4), modifier = Modifier.size(9.dp))
                                    Text("Developer", fontSize = 8.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF5B8ED4), letterSpacing = 0.2.sp)
                                }
                            }
                        }
                    }
                    if (post.createdAt > 0) {
                        Text(igAgo(post.createdAt), fontSize = 10.5.sp, color = IGMuted)
                    }
                }
                IconButton(onClick = { showOptions = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.MoreHoriz, null, tint = IGMuted, modifier = Modifier.size(16.dp))
                }
            }

            // ── Images ───────────────────────────────────────────────────────
            if (post.imageUrls.isNotEmpty()) {
                val thumbUrl = com.ns.findyourintroverfriend.util.CloudinaryUtil
                    .thumbnail(post.imageUrls.first(), width = 400, quality = 50)
                var innerFullScreenUrl by remember { mutableStateOf<String?>(null) }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp))
                        .combinedClickable(
                            onClick       = { innerFullScreenUrl = post.imageUrls.first() },
                            onDoubleClick = { if (userReaction == null) onReact("❤️"); doubleTapLike = true }
                        )
                ) {
                    AsyncImage(
                        model              = thumbUrl,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                    if (post.imageUrls.size > 1) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("1/${post.imageUrls.size}", fontSize = 12.sp,
                                color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                innerFullScreenUrl?.let { rawUrl ->
                    ImageFullScreenViewer(
                        url     = com.ns.findyourintroverfriend.util.CloudinaryUtil.fullRes(rawUrl),
                        onClose = { innerFullScreenUrl = null }
                    )
                }
            }

            // ── Tag badge ─────────────────────────────────────────────────────
            if (post.tag.isNotBlank()) {
                Box(
                    Modifier.padding(horizontal = 16.dp)
                        .padding(top = if (post.imageUrls.isNotEmpty()) 10.dp else 6.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(IGAccentSoft)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(post.tag, fontSize = 10.5.sp, color = IGAccent, fontWeight = FontWeight.SemiBold)
                }
            }
            // ── Caption — soft inline text (no inline bold name) ─────────────
            if (post.caption.isNotBlank()) {
                Text(
                    text      = post.caption,
                    fontSize  = 14.sp,
                    color     = IGBlack,
                    lineHeight = 21.sp,
                    letterSpacing = (-0.1).sp,
                    modifier  = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = if (post.imageUrls.isNotEmpty() || post.tag.isNotBlank()) 6.dp else 0.dp, bottom = 4.dp)
                )
                // Author alias below caption — quiet, small
                Text(
                    "— ${post.authorAlias.ifBlank { "Anonymous" }}",
                    fontSize  = 11.sp,
                    color     = IGMuted,
                    fontWeight = FontWeight.Medium,
                    modifier  = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                )
            }
            // ── Reaction summary pills ─────────────────────────────────────────
            val topReactions = post.reactions.entries
                .filter { it.value.isNotEmpty() }
                .sortedByDescending { it.value.size }
                .take(3)
            if (topReactions.isNotEmpty()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topReactions.forEach { (emoji, uids) ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (_fsDark) Color(0xFF252530) else Color(0xFFF0EDE8))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(emoji, fontSize = 12.sp)
                                Text("${uids.size}", fontSize = 11.sp, color = IGGray, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    // Total reaction count
                    val totalReactions = post.reactions.values.sumOf { it.size }
                    if (totalReactions > topReactions.sumOf { it.value.size }) {
                        Text("+${totalReactions - topReactions.sumOf { it.value.size }}", fontSize = 11.sp, color = IGGray)
                    }
                }
            }

            // ── Comments link — soft, understated ────────────────────────────
            if (post.commentsCount > 0) {
                Text(
                    "View ${post.commentsCount} ${if (post.commentsCount == 1L) "comment" else "comments"}",
                    fontSize = 12.sp, color = IGGray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable { onComment() }
                )
            }

            // ── Action row ────────────────────────────────────────────────────
            HorizontalDivider(
                color = IGDivider, thickness = 0.5.dp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Reaction button — short tap = ❤️, long press = picker ──────
                Box {
                    IconButton(
                        onClick   = { onReact(userReaction ?: "❤️") },
                        modifier  = Modifier.size(38.dp).combinedClickable(
                            onClick     = { onReact(userReaction ?: "❤️") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showReactionPicker = true
                            }
                        )
                    ) {
                        val reactionEmoji = userReaction
                        if (reactionEmoji != null) {
                            Text(reactionEmoji, fontSize = 18.sp)
                        } else {
                            Icon(Icons.Rounded.FavoriteBorder, null,
                                tint = IGMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                    // Reaction picker popup
                    if (showReactionPicker) {
                        ReactionPicker(
                            currentReaction = userReaction,
                            onReact = { emoji -> onReact(emoji); showReactionPicker = false },
                            onDismiss = { showReactionPicker = false }
                        )
                    }
                }
                // Comment
                IconButton(onClick = onComment, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.ChatBubbleOutline, null,
                        tint = IGMuted, modifier = Modifier.size(19.dp))
                }
                // Share
                val ctx2 = LocalContext.current
                IconButton(onClick = {
                    val postText = if (post.caption.isNotBlank()) "\"${post.caption}\"\n\n" else ""
                    val shareText = "${postText}Someone just shared this on Veil — the anonymous space for introverts to connect, share, and find their people. 🤍\n\n" +
                        "Find your introvert tribe, match anonymously, and be yourself without the noise.\n\n" +
                        "📲 Download Veil:\nhttps://play.google.com/store/apps/details?id=com.ns.findyourintroverfriend"
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    ctx2.startActivity(android.content.Intent.createChooser(intent, "Share via"))
                }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.IosShare, null, tint = IGMuted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                // ── Bookmark ──────────────────────────────────────────────────
                IconButton(onClick = onBookmark, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = "Save",
                        tint     = if (isBookmarked) IGAccent else IGMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                // Approach for Friendship
                if (!isOwnPost) {
                    IconButton(
                        onClick  = onApproach,
                        enabled  = !alreadyApproached,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Handshake,
                            contentDescription = "Approach",
                            tint     = if (alreadyApproached) IGSage else IGMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }

    // ── Post options bottom sheet — gentle, calm ──────────────────────────────
    if (showOptions) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOptions = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xFF2C2825).copy(0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showOptions = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(IGCard)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .navigationBarsPadding()
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.width(32.dp).height(3.dp)
                            .clip(CircleShape).background(IGMuted.copy(0.4f)))
                    }

                    if (post.authorUid == myUid) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { showOptions = false; onDelete() }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, null,
                                tint = IGRed, modifier = Modifier.size(18.dp))
                            Text("Delete post", fontSize = 15.sp, color = IGRed,
                                fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                    }

                    if (post.authorUid != myUid) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { showOptions = false; onReport() }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Flag, null,
                                tint = IGRed, modifier = Modifier.size(18.dp))
                            Text("Report post", fontSize = 15.sp, color = IGRed,
                                fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                    }

                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { showOptions = false }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Close, null,
                            tint = IGGray, modifier = Modifier.size(18.dp))
                        Text("Cancel", fontSize = 15.sp, color = IGBlack)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── Comments Bottom Sheet ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    post             : VeilPost,
    vm               : FeedViewModel,
    onDismiss        : () -> Unit,
    onOpenUserProfile: (String) -> Unit = {}
) {
    val comments      by vm.comments.collectAsStateWithLifecycle()
    var commentText    by remember { mutableStateOf(TextFieldValue("")) }
    var replyToAlias   by remember { mutableStateOf("") }
    var replyToCommentId by remember { mutableStateOf("") }
    // Which comment IDs have their replies panel expanded
    var expandedReplies by remember { mutableStateOf(setOf<String>()) }
    val focusReq       = remember { FocusRequester() }
    val focusMgr       = LocalFocusManager.current

    LaunchedEffect(post.postId) { vm.loadComments(post.postId) }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = IGBg,
        dragHandle        = { BottomSheetDefaults.DragHandle() },
        sheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), Alignment.Center) {
                Text("Comments", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = IGBlack)
            }
            HorizontalDivider(color = IGDivider, thickness = 0.5.dp)

            // Reply-to banner
            if (replyToAlias.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().background(IGBlue.copy(0.06f)).padding(horizontal = 16.dp, vertical = 7.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Text("Replying to @$replyToAlias", fontSize = 12.sp, color = IGBlue, fontWeight = FontWeight.Medium)
                    Icon(Icons.Rounded.Close, null, tint = IGGray, modifier = Modifier.size(16.dp)
                        .clickable { replyToAlias = ""; replyToCommentId = ""; commentText = TextFieldValue("") })
                }
            }

            LazyColumn(
                Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Text("No comments yet.\nBe the first!", color = IGGray, fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                items(comments, key = { it.commentId }) { c ->
                    CommentRow(
                        c                = c,
                        myUid            = vm.uid,
                        postId           = post.postId,
                        vm               = vm,
                        isExpanded       = c.commentId in expandedReplies,
                        onToggleExpand   = {
                            val wasExpanded = c.commentId in expandedReplies
                            expandedReplies = if (wasExpanded)
                                expandedReplies - c.commentId
                            else
                                expandedReplies + c.commentId
                            // Load from Firestore only on MANUAL expand (not auto-expand after posting)
                            if (!wasExpanded) vm.loadReplies(post.postId, c.commentId)
                        },
                        onOpenUserProfile = onOpenUserProfile,
                        onReply          = { alias ->
                            replyToAlias     = alias
                            replyToCommentId = c.commentId
                            val prefill      = "@$alias "
                            commentText      = TextFieldValue(prefill, selection = TextRange(prefill.length))
                            focusReq.requestFocus()
                        }
                    )
                }
            }

            HorizontalDivider(color = IGDivider, thickness = 0.5.dp)

            // Input row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextField(
                    value         = commentText,
                    onValueChange = { commentText = it },
                    modifier      = Modifier.weight(1f).focusRequester(focusReq),
                    placeholder   = {
                        Text(
                            if (replyToAlias.isBlank()) "Add a comment…" else "Reply to @$replyToAlias…",
                            color = IGGray, fontSize = 14.sp
                        )
                    },
                    colors   = TextFieldDefaults.colors(
                        focusedContainerColor   = IGLightGray, unfocusedContainerColor = IGLightGray,
                        focusedIndicatorColor   = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor  = Color.Transparent
                    ),
                    shape    = RoundedCornerShape(20.dp),
                    maxLines = 3,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (commentText.text.isNotBlank()) {
                            if (replyToCommentId.isNotBlank()) {
                                vm.addReply(post.postId, replyToCommentId, commentText.text, replyToAlias)
                                expandedReplies = expandedReplies + replyToCommentId  // auto-expand
                            } else {
                                vm.addComment(post.postId, commentText.text)
                            }
                            commentText = TextFieldValue(""); replyToAlias = ""; replyToCommentId = ""; focusMgr.clearFocus()
                        }
                    })
                )
                AnimatedVisibility(commentText.text.isNotBlank()) {
                    TextButton(onClick = {
                        if (commentText.text.isNotBlank()) {
                            if (replyToCommentId.isNotBlank()) {
                                vm.addReply(post.postId, replyToCommentId, commentText.text, replyToAlias)
                                expandedReplies = expandedReplies + replyToCommentId  // auto-expand
                            } else {
                                vm.addComment(post.postId, commentText.text)
                            }
                            commentText = TextFieldValue(""); replyToAlias = ""; replyToCommentId = ""; focusMgr.clearFocus()
                        }
                    }) {
                        Text("Post", color = IGBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    c                : VeilComment,
    myUid            : String,
    postId           : String,
    vm               : FeedViewModel,
    isExpanded       : Boolean = false,
    onToggleExpand   : () -> Unit = {},
    onReply          : (String) -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {}
) {
    val liked          = c.likedBy.contains(myUid)
    val isOwn          = c.authorUid == myUid
    val repliesMap     by vm.replies.collectAsStateWithLifecycle()
    val replies        = repliesMap[c.commentId] ?: emptyList()

    // ── Edit / Delete state ────────────────────────────────────────────────
    var showOptions    by remember { mutableStateOf(false) }
    var isEditing      by remember { mutableStateOf(false) }
    var editText       by remember(c.commentId) { mutableStateOf(c.text) }
    val focusReqEdit   = remember { FocusRequester() }
    val focusMgr       = LocalFocusManager.current

    LaunchedEffect(isEditing) {
        if (isEditing) kotlinx.coroutines.delay(80).also { focusReqEdit.requestFocus() }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick      = {},
                    onLongClick  = { if (isOwn) showOptions = true }
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            IGAvatar(c.authorAlias, c.authorPhotoUrl, c.authorAvatar, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (isEditing) {
                    // ── Inline edit field ─────────────────────────────────
                    TextField(
                        value         = editText,
                        onValueChange = { editText = it },
                        modifier      = Modifier.fillMaxWidth().focusRequester(focusReqEdit),
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = IGLightGray,
                            unfocusedContainerColor = IGLightGray,
                            focusedIndicatorColor   = IGBlue,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor  = Color.Transparent
                        ),
                        shape    = RoundedCornerShape(12.dp),
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = IGBlack),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (editText.isNotBlank()) {
                                vm.editComment(postId, c.commentId, editText)
                                isEditing = false
                                focusMgr.clearFocus()
                            }
                        })
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                editText  = c.text
                                isEditing = false
                                focusMgr.clearFocus()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("Cancel", fontSize = 12.sp, color = IGGray) }
                        TextButton(
                            onClick = {
                                if (editText.isNotBlank()) {
                                    vm.editComment(postId, c.commentId, editText)
                                    isEditing = false
                                    focusMgr.clearFocus()
                                }
                            },
                            enabled = editText.isNotBlank() && editText != c.text,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("Save", fontSize = 12.sp, color = IGBlue, fontWeight = FontWeight.SemiBold) }
                    }
                } else {
                    // ── Normal display ────────────────────────────────────
                    val annotated = buildCommentAnnotated(c.authorAlias, c.text)
                    ClickableText(
                        text  = annotated,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize   = 13.sp,
                            color      = IGBlack,
                            lineHeight = 20.sp
                        ),
                        onClick = { offset ->
                            annotated.getStringAnnotations("alias", offset, offset)
                                .firstOrNull()?.let { span ->
                                    vm.lookupUidByAlias(span.item) { uid -> uid?.let { onOpenUserProfile(it) } }
                                }
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(igAgo(c.createdAt), fontSize = 11.sp, color = IGGray)
                        if (c.likedBy.isNotEmpty()) Text(" · ${c.likedBy.size} likes", fontSize = 11.sp, color = IGGray)
                        // "edited" badge — shown only if Firestore has editedAt (we infer from text being edited)
                        // We store editedAt server-side; for simplicity show nothing extra unless you add editedAt to VeilComment model
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .clickable(remember{MutableInteractionSource()}, null) { onReply(c.authorAlias) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Reply", fontSize = 12.sp, color = IGBlue, fontWeight = FontWeight.SemiBold)
                        }
                        if (isOwn) {
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .clickable(remember{MutableInteractionSource()}, null) { showOptions = true }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.MoreHoriz, null,
                                    tint = IGGray, modifier = Modifier.size(14.dp))
                            }
                        }
                        if (c.replyCount > 0) {
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .clickable(remember{MutableInteractionSource()}, null) { onToggleExpand() }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (isExpanded) "Hide replies"
                                    else "View ${c.replyCount} ${if (c.replyCount == 1) "reply" else "replies"}",
                                    fontSize = 12.sp, color = IGGray, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            if (!isEditing) {
                IconButton(onClick = { vm.toggleCommentLike(postId, c) }, modifier = Modifier.size(30.dp)) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
                        tint = if (liked) IGRed else IGGray, modifier = Modifier.size(14.dp))
                }
            }
        }

        // ── Nested replies ─────────────────────────────────────────────────
        AnimatedVisibility(visible = isExpanded) {
            Column {
                if (replies.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(start = 52.dp, top = 4.dp, bottom = 6.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = IGGray)
                    }
                } else {
                    replies.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 52.dp, end = 14.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            IGAvatar(r.authorAlias, r.authorPhotoUrl, r.authorAvatar, 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                val rAnnotated = buildCommentAnnotated(r.authorAlias, r.text)
                                ClickableText(
                                    text  = rAnnotated,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize   = 12.sp,
                                        color      = IGBlack,
                                        lineHeight = 18.sp
                                    ),
                                    onClick = { offset ->
                                        rAnnotated.getStringAnnotations("alias", offset, offset)
                                            .firstOrNull()?.let { span ->
                                                vm.lookupUidByAlias(span.item) { uid -> uid?.let { onOpenUserProfile(it) } }
                                            }
                                    }
                                )
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(igAgo(r.createdAt), fontSize = 10.sp, color = IGGray,
                                        modifier = Modifier.padding(top = 2.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(6.dp))
                                            .clickable(remember{MutableInteractionSource()}, null) {
                                                onReply(r.authorAlias)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text("Reply", fontSize = 11.sp, color = IGBlue,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Comment options bottom sheet (own comment only) ────────────────────
    if (showOptions) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOptions = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xFF2C2825).copy(0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showOptions = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(IGCard)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .navigationBarsPadding()
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), Alignment.Center) {
                        Box(Modifier.width(32.dp).height(3.dp).clip(CircleShape).background(IGMuted.copy(0.4f)))
                    }
                    // Edit
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                showOptions = false
                                editText    = c.text
                                isEditing   = true
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Edit, null, tint = IGBlack, modifier = Modifier.size(18.dp))
                        Text("Edit comment", fontSize = 15.sp, color = IGBlack)
                    }
                    HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                    // Delete
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                showOptions = false
                                vm.deleteComment(postId, c.commentId)
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = IGRed, modifier = Modifier.size(18.dp))
                        Text("Delete comment", fontSize = 15.sp, color = IGRed, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                    // Cancel
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { showOptions = false }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = IGGray, modifier = Modifier.size(18.dp))
                        Text("Cancel", fontSize = 15.sp, color = IGBlack)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/** Builds a single AnnotatedString: bold alias + space + @mention-highlighted text.
 *  Each @mention gets a 'alias' string annotation (value = alias WITHOUT the @) for tap detection. */
private fun buildCommentAnnotated(alias: String, text: String): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(alias) }
        append("  ")
        var i = 0
        while (i < text.length) {
            if (text[i] == '@') {
                val end = text.indexOf(' ', i).let { if (it == -1) text.length else it }
                    .coerceAtMost(text.indexOf('\n', i).let { if (it == -1) text.length else it })
                val mentionName = text.substring(i + 1, end)   // strip the @
                pushStringAnnotation(tag = "alias", annotation = mentionName)
                withStyle(SpanStyle(color = IGBlue, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(i, end))
                }
                pop()
                i = end
            } else {
                val next = text.indexOf('@', i).let { if (it == -1) text.length else it }
                append(text.substring(i, next))
                i = next
            }
        }
    }

// ── Create Post Sheet ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostSheet(vm: FeedViewModel, myAlias: String, onDismiss: () -> Unit) {
    val isPosting by vm.isPosting.collectAsStateWithLifecycle()
    val context   = LocalContext.current
    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> pickedUris = uris; vm.draftImageUris = uris }

    ModalBottomSheet(
        onDismissRequest = { if (!isPosting) onDismiss() },
        containerColor   = IGBg,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 16.dp).imePadding()) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (!isPosting) onDismiss() }) {
                    Text("Cancel", color = IGGray, fontSize = 15.sp)
                }
                Text("New Post", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = IGBlack)
                TextButton(
                    onClick  = { vm.submitPost(context) { onDismiss() } },
                    enabled  = (!isPosting)
                           && (vm.draftCaption.isNotBlank() || pickedUris.isNotEmpty())
                           && !VeilSafetyUtil.containsProfanity(vm.draftCaption)
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = IGBlue)
                    } else {
                        Text("Share", color = IGBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
            Spacer(Modifier.height(14.dp))

            // Author header
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(IGLightGray)
                    .border(1.dp, IGDivider, CircleShape), contentAlignment = Alignment.Center) {
                    Text(myAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IGGray)
                }
                Text(myAlias.ifBlank { "You" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = IGBlack)
            }

            Spacer(Modifier.height(12.dp))

            // ── Content policy warning banner ───────────────────────────────────
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (_fsDark) Color(0xFF2D1318) else Color(0xFFFFF3F3))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, null, tint = IGRed, modifier = Modifier.size(14.dp))
                Text(
                    "No vulgar or abusive content. Violations = permanent ban.",
                    fontSize = 11.sp,
                    color = if (_fsDark) Color(0xFFFF8A8A) else Color(0xFFB91C1C),
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            // Caption input with profanity detection
            val hasProfanity = VeilSafetyUtil.containsProfanity(vm.draftCaption)
            TextField(
                value         = vm.draftCaption,
                onValueChange = { vm.draftCaption = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Write a caption…", color = IGGray, fontSize = 15.sp) },
                colors        = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor   = if (hasProfanity) IGRed else IGBlue,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor  = Color.Transparent
                ),
                minLines  = 3,
                maxLines  = 8,
                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = IGBlack)
            )

            if (hasProfanity) {
                Text(
                    "🚫 This caption violates our content policy and cannot be posted.",
                    fontSize = 11.sp, color = IGRed, modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Picked images preview
            if (pickedUris.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pickedUris) { uri ->
                        Box(Modifier.size(80.dp).clip(RoundedCornerShape(10.dp))) {
                            AsyncImage(model = uri, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Tag picker ────────────────────────────────────────────────────
            Text(
                "Tag this post",
                fontSize   = 12.sp,
                color      = IGGray,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(VEIL_POST_TAGS) { tag ->
                    val selected = vm.draftTag == tag
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) IGAccent
                                else if (_fsDark) Color(0xFF252530) else Color(0xFFF0EDE8)
                            )
                            .clickable {
                                vm.draftTag = if (selected) "" else tag
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            tag,
                            fontSize   = 12.sp,
                            color      = if (selected) Color.White else IGBlack,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Add photo button
            OutlinedButton(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, IGDivider)
            ) {
                Icon(Icons.Rounded.Image, null, tint = IGBlack, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (pickedUris.isEmpty()) "Add Photos" else "Change Photos",
                    color = IGBlack, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Shared avatar — soft pastel monogram ──────────────────────────────────────
@Composable
private fun IGAvatar(alias: String, photoUrl: String, avatarRes: String, size: Dp) {
    val ctx   = LocalContext.current
    val resId = remember(avatarRes) {
        if (avatarRes.isNotBlank()) ctx.resources.getIdentifier(avatarRes, "drawable", ctx.packageName) else 0
    }
    // Soft earthy avatar background based on alias hash
    val softAvatarBgs = listOf(
        Color(0xFFEEE0EB), Color(0xFFE0EAE4), Color(0xFFE4E0EE),
        Color(0xFFEEE8E0), Color(0xFFE0E8EE), Color(0xFFEEE4E0)
    )
    val softAvatarTexts = listOf(
        Color(0xFFA07C8C), Color(0xFF7A9E87), Color(0xFF8C7CA0),
        Color(0xFFA09070), Color(0xFF709090), Color(0xFFA08070)
    )
    val idx    = kotlin.math.abs(alias.hashCode()) % 6
    val bgClr  = if (photoUrl.isBlank() && resId == 0)
        (if (_fsDark) softAvatarBgs[idx].copy(0.15f) else softAvatarBgs[idx])
    else IGLightGray
    val txtClr = softAvatarTexts[idx]

    Box(
        Modifier.size(size).clip(CircleShape)
            .background(bgClr)
            .border(0.5.dp, IGBorderSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when {
            photoUrl.isNotBlank() -> AsyncImage(
                model = photoUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            resId != 0 -> Image(
                painter = androidx.compose.ui.res.painterResource(resId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            else -> Text(
                alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.SemiBold,
                color = txtClr
            )
        }
    }
}

// ── Feed Post Report Dialog ───────────────────────────────────────────────────
@Composable
private fun FeedPostReportDialog(
    onDismiss: () -> Unit,
    onSubmit : (reason: String, note: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf("") }
    var note           by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(16.dp),
        containerColor   = IGBg,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Flag, null, tint = IGRed, modifier = Modifier.size(18.dp))
                Text("Report Post", fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = IGBlack)
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("What's wrong with this post?", fontSize = 12.sp, color = IGGray,
                    modifier = Modifier.padding(bottom = 4.dp))

                VeilSafetyUtil.POST_REPORT_REASONS.forEach { reason ->
                    val selected = selectedReason == reason
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFFFFF0F1) else IGLightGray)
                            .border(1.dp,
                                if (selected) IGRed.copy(0.4f) else Color.Transparent,
                                RoundedCornerShape(8.dp))
                            .clickable { selectedReason = reason }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape)
                            .background(if (selected) IGRed else IGGray.copy(0.4f)))
                        Text(reason, fontSize = 13.sp,
                            color = if (selected) IGRed else IGBlack,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = note,
                    onValueChange = { if (it.length <= 150) note = it },
                    placeholder   = { Text("Extra details (optional)", fontSize = 12.sp, color = IGGray) },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 2,
                    shape    = RoundedCornerShape(8.dp),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IGRed, unfocusedBorderColor = IGDivider
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = IGBlack)
                )
                Text("⚠️ False reports may result in action against your account.",
                    fontSize = 10.sp, color = IGGray, modifier = Modifier.padding(top = 2.dp))
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = IGGray, fontSize = 13.sp)
                }
                Button(
                    onClick  = { if (selectedReason.isNotBlank()) onSubmit(selectedReason, note) },
                    enabled  = selectedReason.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(containerColor = IGRed,
                        disabledContainerColor = IGGray.copy(0.3f)),
                    shape    = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Report", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        },
        dismissButton = null
    )
}

// ── Full-screen Image Viewer ───────────────────────────────────────────────────
// Loads the full-res image ONLY when the user taps — saves bandwidth for scrollers.
@Composable
private fun ImageFullScreenViewer(url: String, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside   = true
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            var loading by remember { mutableStateOf(true) }
            AsyncImage(
                model              = url,
                contentDescription = "Full image",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize(),
                onSuccess          = { loading = false },
                onError            = { loading = false }
            )
            if (loading) {
                CircularProgressIndicator(color = Color.White.copy(0.7f), strokeWidth = 2.dp,
                    modifier = Modifier.size(36.dp))
            }
            // Close button
            IconButton(
                onClick  = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    .background(Color.Black.copy(0.5f), shape = CircleShape).size(36.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close",
                    tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Feed Skeleton Loader — soft pulsing shimmer ───────────────────────────────
@Composable
private fun FeedSkeletonLoader() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -600f, targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing), RepeatMode.Restart
        ), label = "shimmer"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = if (_fsDark)
            listOf(Color(0xFF1E1E26), Color(0xFF28283200), Color(0xFF1E1E26))
        else
            listOf(Color(0xFFEDE8E2), Color(0xFFF8F5F1), Color(0xFFEDE8E2)),
        start  = Offset(shimmerX, 0f),
        end    = Offset(shimmerX + 400f, 0f)
    )
    LazyColumn(Modifier.fillMaxSize().background(IGBg), userScrollEnabled = false,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(4) { SkeletonPostCard(shimmerBrush) }
    }
}

@Composable
private fun SkeletonPostCard(brush: Brush) {
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadow(1.dp, RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(0.04f))
            .clip(RoundedCornerShape(18.dp))
            .background(IGCard)
            .border(0.5.dp, IGBorderSoft, RoundedCornerShape(18.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(brush))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.30f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(Modifier.fillMaxWidth(0.16f).height(9.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).background(brush))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth(0.75f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.55f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        }
        HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(brush)) }
        }
    }
}

// ── Feed Empty State — soft, welcoming ────────────────────────────────────────
@Composable
private fun FeedEmptyState(onCreatePost: () -> Unit) {
    Box(Modifier.fillMaxSize().background(IGBg), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            // Soft glowing icon circle
            Box(
                Modifier.size(88.dp).clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(IGAccentSoft, IGBg)
                        )
                    ),
                Alignment.Center
            ) {
                Box(
                    Modifier.size(66.dp).clip(CircleShape)
                        .background(IGAccentSoft),
                    Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoStories, null,
                        tint = IGAccent, modifier = Modifier.size(30.dp))
                }
            }
            Text(
                "Your quiet feed awaits",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 17.sp,
                color      = IGBlack,
                letterSpacing = (-0.2).sp
            )
            Text(
                "Be the first to share a thought with the introvert community. 🤍",
                fontSize  = 13.sp,
                color     = IGGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(IGAccent.copy(0.85f), IGAccent)
                        )
                    )
                    .clickable { onCreatePost() }
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.EditNote, null,
                        tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Share a thought", color = Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Tag Filter Row — sticky chips at top of feed ─────────────────────────────
@Composable
private fun TagFilterRow(
    selectedTag  : String,
    onTagSelected: (String) -> Unit
) {
    val allTags = listOf("All") + VEIL_POST_TAGS
    Surface(
        color     = IGBg,
        shadowElevation = 0.dp,
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column {
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(allTags) { tag ->
                    val selected = tag == selectedTag
                    val scale by animateFloatAsState(
                        targetValue   = if (selected) 1.04f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label         = "chip_$tag"
                    )
                    Box(
                        Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) IGAccent
                                else if (_fsDark) Color(0xFF1E1E27) else Color(0xFFF0EDE8)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null
                            ) { onTagSelected(tag) }
                            .border(
                                width  = if (selected) 0.dp else 0.8.dp,
                                color  = if (selected) Color.Transparent else IGBorderSoft,
                                shape  = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            tag,
                            fontSize   = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (selected) Color.White else IGBlack,
                            maxLines   = 1
                        )
                    }
                }
            }
            HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
        }
    }
}

// ── Reaction Picker — floating row of 5 emoji (long-press trigger) ────────────
@Composable
private fun ReactionPicker(
    currentReaction: String?,
    onReact        : (String) -> Unit,
    onDismiss      : () -> Unit
) {
    val reactions = listOf("❤️", "🥀", "🌙", "😂", "🤍")
    val labels    = listOf("Love", "Felt This", "Relatable", "Funny", "Support")

    // Dismiss on outside click via dialog
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomStart
        ) {
            // Floating pill
            Box(
                Modifier
                    .padding(start = 12.dp, bottom = 160.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (_fsDark) Color(0xFF2A2A36) else Color.White,
                        RoundedCornerShape(50)
                    )
                    .shadow(8.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(0.15f))
                    .clickable(remember { MutableInteractionSource() }, null) {}  // block dismiss
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    reactions.forEachIndexed { idx, emoji ->
                        val isActive = emoji == currentReaction
                        var pressed by remember { mutableStateOf(false) }
                        val emojiScale by animateFloatAsState(
                            targetValue   = if (pressed || isActive) 1.35f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label         = "emoji_$idx"
                        )
                        val haptic = LocalHapticFeedback.current
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onReact(emoji)
                                }
                                .padding(6.dp)
                        ) {
                            Text(
                                emoji,
                                fontSize = 24.sp,
                                modifier = Modifier.scale(emojiScale)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                labels[idx],
                                fontSize  = 8.sp,
                                color     = if (isActive) IGAccent else IGGray,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines  = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

