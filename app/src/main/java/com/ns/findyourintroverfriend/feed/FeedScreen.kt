package com.ns.findyourintroverfriend.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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

// ── Color tokens (clean B&W – no vibrant colours) ────────────────────────────
private val IGBg        = Color(0xFFFFFFFF)
private val IGDivider   = Color(0xFFDBDBDB)
private val IGBlack     = Color(0xFF262626)
private val IGGray      = Color(0xFF8E8E8E)
private val IGLightGray = Color(0xFFF9F9F9)
private val IGRed       = Color(0xFFED4956)   // Instagram like-heart red
private val IGBlue      = Color(0xFF0095F6)   // Instagram action blue

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
    val posts          by vm.posts.collectAsStateWithLifecycle()
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

    var showCreate  by remember { mutableStateOf(false) }
    var commentPost by remember { mutableStateOf<VeilPost?>(null) }
    var reportTarget by remember { mutableStateOf<String?>(null) }
    var pendingApproachPost by remember { mutableStateOf<VeilPost?>(null) }
    var showNotifPanel by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val unreadCount by vm.unreadCount.collectAsStateWithLifecycle()
    val notifItems  by vm.notifItems.collectAsStateWithLifecycle()

    // Handle approach result → show snackbar feedback
    LaunchedEffect(approachResult) {
        when (val r = approachResult) {
            is ApproachResult.Sent        -> { snackbarHostState.showSnackbar("🤝 Approached!"); approachVm.resetResult() }
            is ApproachResult.NeedCookies -> approachVm.resetResult()  // pendingApproachPost already set in the button's onClick
            is ApproachResult.Error       -> { snackbarHostState.showSnackbar("❌ ${r.message}"); approachVm.resetResult() }
            else -> Unit
        }
    }

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

            // ── Top bar — Instagram-style ─────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().background(IGBg).statusBarsPadding()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Introvert Feed",
                        fontSize      = 24.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = IGBlack,
                        letterSpacing = (-0.5).sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // ── Notification bell ────────────────────────────────────
                        Box {
                            IconButton(onClick = {
                                showNotifPanel = true
                                vm.markAllNotifsRead()
                            }) {
                                Icon(Icons.Rounded.Notifications, null, tint = IGBlack, modifier = Modifier.size(24.dp))
                            }
                            // Unread badge dot
                            if (unreadCount > 0) {
                                Box(
                                    Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(IGRed)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 4.dp)
                                )
                            }
                        }
                        // ── DM Inbox button ─────────────────────────────────
                        IconButton(onClick = onOpenDms) {
                            Icon(
                                androidx.compose.ui.res.painterResource(
                                    id = com.ns.findyourintroverfriend.R.drawable.ic_feed_dm
                                ),
                                contentDescription = "Messages",
                                tint     = IGBlack,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        // ── Cookie balance badge ───────────────────────────
                        com.ns.findyourintroverfriend.billing.CookieBadge(
                            balance  = cookieBalance,
                            onClick  = onOpenCookieStore,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        // ── No + button here — moved to FAB below ──────────
                    }
                }
                HorizontalDivider(color = IGDivider, thickness = 0.8.dp)
            }

            // ── Feed list ────────────────────────────────────────────────────
            when {
                loading         -> FeedSkeletonLoader()
                posts.isEmpty() -> FeedEmptyState { showCreate = true }
                else -> {
                    val listState = rememberLazyListState()
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
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(posts, key = { it.postId }) { post ->
                                PostCard(
                                    post               = post,
                                    myUid              = vm.uid,
                                    onLike             = { vm.toggleLike(post) },
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
                                HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
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
        }

        // ── Gamified FAB — shimmer + breathing glow ──────────────────────────
        val fabInf = rememberInfiniteTransition(label = "fab")
        val fabGlow by fabInf.animateFloat(0.25f, 0.55f,
            infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fg")
        val fabShimX by fabInf.animateFloat(-80f, 180f,
            infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), "fsx")
        val fabScale by fabInf.animateFloat(1f, 1.06f,
            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fsc")
        val shimBrush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(0.35f), Color.Transparent),
            start  = Offset(fabShimX - 30f, 0f),
            end    = Offset(fabShimX + 30f, 90f)
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
                    .size(58.dp)
                    .scale(fabScale)
                    .shadow(16.dp, CircleShape, spotColor = IGBlack.copy(fabGlow))
                    .clip(CircleShape)
                    .background(IGBlack)
                    .clickable {
                        fabHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCreate = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, "New Post", tint = Color.White,
                    modifier = Modifier.size(26.dp))
                // Shimmer streak overlay
                Box(Modifier.fillMaxSize().background(shimBrush))
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

// ── Notification panel (Instagram-style bottom sheet) ─────────────────────────
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
            Modifier.fillMaxSize().background(IGBlack.copy(0.45f))
                .clickable(remember{MutableInteractionSource()}, null) { onDismiss() },
            Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(IGBg)
                    .clickable(remember{MutableInteractionSource()}, null) {}
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(IGDivider))
                }
                Text("Notifications", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = IGBlack,
                    modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 10.dp))
                HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Notifications, null, tint = IGDivider, modifier = Modifier.size(40.dp))
                            Text("No notifications yet", fontSize = 14.sp, color = IGGray)
                            Text("Comments and replies on your posts will appear here",
                                fontSize = 12.sp, color = IGGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp))
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(items) { n ->
                            val type    = n["type"] as? String ?: ""
                            val alias   = n["fromAlias"] as? String ?: ""
                            val preview = n["previewText"] as? String ?: ""
                            val read    = n["read"] as? Boolean ?: false
                            val ts      = n["createdAt"] as? Long ?: 0L
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (!read) IGBlue.copy(0.06f) else IGBg)
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(IGBlue.copy(0.12f)), Alignment.Center) {
                                    Icon(
                                        when(type) { "reply" -> Icons.Rounded.Reply; else -> Icons.Rounded.ChatBubbleOutline },
                                        null, tint = IGBlue, modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        when(type) { "reply" -> "$alias replied to your comment"; else -> "$alias commented on your post" },
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = IGBlack
                                    )
                                    if (preview.isNotBlank()) Text("\"$preview\"", fontSize = 12.sp, color = IGGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(igAgo(ts), fontSize = 11.sp, color = IGGray)
                                }
                                if (!read) Box(Modifier.size(7.dp).clip(CircleShape).background(IGBlue).align(Alignment.CenterVertically))
                            }
                            HorizontalDivider(color = IGDivider, thickness = 0.3.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Post Card ─────────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PostCard(
    post             : VeilPost,
    myUid            : String,
    onLike           : () -> Unit,
    onComment        : () -> Unit,
    onDelete         : () -> Unit,
    onReport         : () -> Unit,
    onOpenProfile    : () -> Unit = {},
    onApproach       : () -> Unit = {},
    alreadyApproached: Boolean    = false,
    isOwnPost        : Boolean    = false
) {
    val liked = post.likedBy.contains(myUid)
    var doubleTapLike      by remember { mutableStateOf(false) }
    var showOptions        by remember { mutableStateOf(false) }
    // Full-screen viewer state: holds the full-res URL of the tapped image
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().background(IGBg)) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clickable(onClick = onOpenProfile)
            ) {
                IGAvatar(post.authorAlias, post.authorPhotoUrl, post.authorAvatar, 34.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpenProfile)) {
                Text(post.authorAlias.ifBlank { "Anonymous" }, fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold, color = IGBlack)
                if (post.createdAt > 0) {
                    Text(igAgo(post.createdAt), fontSize = 11.sp, color = IGGray)
                }
            }
            IconButton(onClick = { showOptions = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.MoreVert, null, tint = IGBlack, modifier = Modifier.size(18.dp))
            }
        }

        // ── Images ───────────────────────────────────────────────────────────────────────
        // Feed loads THUMBNAIL (~50-80 KB). Single tap → full-screen viewer.
        if (post.imageUrls.isNotEmpty()) {
            val thumbUrl = com.ns.findyourintroverfriend.util.CloudinaryUtil
                .thumbnail(post.imageUrls.first(), width = 400, quality = 50)
            var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .combinedClickable(
                        onClick       = { fullScreenImageUrl = post.imageUrls.first() },
                        onDoubleClick = { if (!liked) onLike(); doubleTapLike = true }
                    )
            ) {
                AsyncImage(
                    model              = thumbUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                if (post.imageUrls.size > 1) {
                    Box(Modifier.align(Alignment.TopEnd).padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("1/${post.imageUrls.size}", fontSize = 12.sp, color = Color.White,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
            // Full-res viewer overlay (loads on demand only when tapped)
            fullScreenImageUrl?.let { rawUrl ->
                ImageFullScreenViewer(
                    url     = com.ns.findyourintroverfriend.util.CloudinaryUtil.fullRes(rawUrl),
                    onClose = { fullScreenImageUrl = null }
                )
            }
        }

        // ── Action row ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like
            IconButton(onClick = onLike, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    null,
                    tint     = if (liked) IGRed else IGBlack,
                    modifier = Modifier.size(26.dp)
                )
            }
            // Comment
            IconButton(onClick = onComment, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, tint = IGBlack,
                    modifier = Modifier.size(24.dp))
            }
            // Share
            val ctx2 = LocalContext.current
            IconButton(onClick = {
                val postText = if (post.caption.isNotBlank())
                    "\"${post.caption}\"\n\n"
                else ""
                val shareText = "${postText}Someone just shared this on Veil — the anonymous space for introverts to connect, share, and find their people. 🤍\n\n" +
                    "Find your introvert tribe, match anonymously, and be yourself without the noise.\n\n" +
                    "📲 Download Veil:\nhttps://play.google.com/store/apps/details?id=com.ns.findyourintroverfriend"
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                }
                ctx2.startActivity(android.content.Intent.createChooser(intent, "Share via"))
            }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Share, null, tint = IGBlack, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            // Approach for Friendship (hidden on own posts)
            if (!isOwnPost) {
                IconButton(
                    onClick  = onApproach,
                    enabled  = !alreadyApproached,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (alreadyApproached) Icons.Rounded.Handshake else Icons.Rounded.Handshake,
                        contentDescription = "Approach",
                        tint     = if (alreadyApproached) IGBlue else IGBlack.copy(0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── Likes count ───────────────────────────────────────────────────────
        if (post.likedBy.isNotEmpty()) {
            Text(
                "${post.likedBy.size} ${if (post.likedBy.size == 1) "like" else "likes"}",
                fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = IGBlack,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }

        // ── Caption (name + text flow inline, like Instagram) ────────────────
        if (post.caption.isNotBlank()) {
            val captionText = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)) {
                    append(post.authorAlias.ifBlank { "Anonymous" })
                }
                append("  ")
                withStyle(SpanStyle(fontSize = 13.5.sp)) {
                    append(post.caption)
                }
            }
            Text(
                text       = captionText,
                color      = IGBlack,
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                lineHeight = 20.sp
            )
        }

        // ── Comments link ─────────────────────────────────────────────────────
        if (post.commentsCount > 0) {
            Text(
                "View all ${post.commentsCount} comments",
                fontSize = 13.sp, color = IGGray, fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 14.dp).clickable { onComment() }
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    // ── Instagram-style post options bottom sheet ─────────────────────────────
    if (showOptions) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOptions = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showOptions = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(IGBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .navigationBarsPadding()
                ) {
                    // Handle bar
                    Box(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.width(36.dp).height(4.dp)
                                .clip(CircleShape).background(IGDivider)
                        )
                    }

                    // Own post: Delete
                    if (post.authorUid == myUid) {
                        Box(
                            Modifier.fillMaxWidth()
                                .clickable { showOptions = false; onDelete() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Delete", fontSize = 16.sp, color = IGRed,
                                fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                    }

                    // Other's post: Report
                    if (post.authorUid != myUid) {
                        Box(
                            Modifier.fillMaxWidth()
                                .clickable { showOptions = false; onReport() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Report", fontSize = 16.sp, color = IGRed,
                                fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(color = IGDivider, thickness = 0.5.dp)
                    }

                    // Cancel
                    Box(
                        Modifier.fillMaxWidth()
                            .clickable { showOptions = false }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", fontSize = 16.sp, color = IGBlack,
                            fontWeight = FontWeight.Normal)
                    }
                    Spacer(Modifier.height(8.dp))
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
    val repliesMap     by vm.replies.collectAsStateWithLifecycle()
    val replies        = repliesMap[c.commentId] ?: emptyList()
    // No LaunchedEffect here — loadReplies is triggered by onToggleExpand (manual) only.
    // Auto-expand after posting uses the optimistic cache, so no Firestore call needed.

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
            IGAvatar(c.authorAlias, c.authorPhotoUrl, c.authorAvatar, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                // Single AnnotatedString: bold alias + @mention-coloured text — tappable aliases
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(igAgo(c.createdAt), fontSize = 11.sp, color = IGGray)
                    if (c.likedBy.isNotEmpty()) Text(" · ${c.likedBy.size} likes", fontSize = 11.sp, color = IGGray)
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .clickable(remember{MutableInteractionSource()}, null) { onReply(c.authorAlias) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Reply", fontSize = 12.sp, color = IGBlue, fontWeight = FontWeight.SemiBold)
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
            IconButton(onClick = { vm.toggleCommentLike(postId, c) }, modifier = Modifier.size(30.dp)) {
                Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
                    tint = if (liked) IGRed else IGGray, modifier = Modifier.size(14.dp))
            }
        }
        // ── Nested replies ────────────────────────────────────────────────────
        AnimatedVisibility(visible = isExpanded) {
            Column {
                if (replies.isEmpty()) {
                    // Still loading
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
                                    // Reply button on each reply — replies to parent comment, @mentions this user
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
                    .background(Color(0xFFFFF3F3))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, null, tint = IGRed, modifier = Modifier.size(14.dp))
                Text(
                    "No vulgar or abusive content. Violations = permanent ban.",
                    fontSize = 11.sp, color = Color(0xFFB91C1C), lineHeight = 16.sp
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

// ── Shared avatar ─────────────────────────────────────────────────────────────
@Composable
private fun IGAvatar(alias: String, photoUrl: String, avatarRes: String, size: Dp) {
    val ctx   = LocalContext.current
    val resId = remember(avatarRes) {
        if (avatarRes.isNotBlank()) ctx.resources.getIdentifier(avatarRes, "drawable", ctx.packageName) else 0
    }
    Box(Modifier.size(size).clip(CircleShape).background(IGLightGray)
        .border(0.5.dp, IGDivider, CircleShape), contentAlignment = Alignment.Center) {
        when {
            photoUrl.isNotBlank() -> AsyncImage(model = photoUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            resId != 0 -> Image(painter = androidx.compose.ui.res.painterResource(resId),
                contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop)
            else -> Text(alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold, color = IGGray)
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

// ── Feed Skeleton Loader ───────────────────────────────────────────────────────
@Composable
private fun FeedSkeletonLoader() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -600f, targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = LinearEasing), RepeatMode.Restart
        ), label = "shimmer"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFE8E8E8), Color(0xFFF6F6F6), Color(0xFFE8E8E8)),
        start  = Offset(shimmerX, 0f),
        end    = Offset(shimmerX + 400f, 0f)
    )
    LazyColumn(Modifier.fillMaxSize(), userScrollEnabled = false) {
        items(4) {
            SkeletonPostCard(shimmerBrush)
            HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
        }
    }
}

@Composable
private fun SkeletonPostCard(brush: Brush) {
    Column(Modifier.fillMaxWidth().background(IGBg).padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(brush))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(0.32f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Box(Modifier.fillMaxWidth(0.18f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(brush))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(brush)) }
        }
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.8f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ── Feed Empty State ───────────────────────────────────────────────────────────
@Composable
private fun FeedEmptyState(onCreatePost: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Box(
                Modifier.size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFF5F5F5)),
                Alignment.Center
            ) {
                Icon(Icons.Rounded.AutoAwesome, null,
                    tint = Color(0xFFBBBBBB), modifier = Modifier.size(32.dp))
            }
            Text("Nothing here yet",
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = IGBlack)
            Text(
                "Be the first to share something with the introvert community.",
                fontSize = 13.sp, color = IGGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 19.sp
            )
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(IGBlack)
                    .clickable { onCreatePost() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("Create Post", color = Color.White,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}
