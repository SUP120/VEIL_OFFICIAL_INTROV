package com.ns.findyourintroverfriend.feed

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// ── Professional Design Tokens (Instagram-inspired) ──────────────────────────
private val FBg         = Color(0xFFFFFFFF)      // Pure white background
private val FCard       = Color(0xFFFFFFFF)      // White cards
private val FBorder     = Color(0xFFDBDBDB)      // Subtle gray border
private val FPrimary    = Color(0xFF262626)      // Near black text
private val FSecondary  = Color(0xFF8E8E8E)      // Gray secondary text
private val FMuted      = Color(0xFFC7C7C7)      // Light gray
private val FAccent     = Color(0xFF0095F6)      // Instagram blue
private val FAccentSoft = Color(0xFFE7F3FF)      // Very light blue
private val FRed        = Color(0xFFED4956)      // Instagram red/like color
private val FRedSoft    = Color(0xFFFFF0F1)      // Light red bg

// Avatar colors - subtle, professional pastels
private val avatarColors = listOf(
    Color(0xFFE3F2FD), Color(0xFFFCE4EC), Color(0xFFF3E5F5),
    Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFE0F2F1),
)

private fun avatarColor(uid: String): Color =
    avatarColors[kotlin.math.abs(uid.hashCode()) % avatarColors.size]

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

// ── Screen Entry ──────────────────────────────────────────────────────────────
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun IntrovertFeedScreen(
    vm     : IntrovertFeedViewModel = viewModel(),
    onBack : () -> Unit = {}
) {
    val posts        by vm.posts.collectAsStateWithLifecycle()
    val loading      by vm.loading.collectAsStateWithLifecycle()
    val unreadCount  by vm.unreadCount.collectAsStateWithLifecycle()
    val notifs       by vm.notifs.collectAsStateWithLifecycle()

    var showCreatePost  by remember { mutableStateOf(false) }
    var reportTarget    by remember { mutableStateOf<String?>(null) }
    var showNotifPanel  by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = FBg,
        topBar = {
            FeedTopBar(
                onBack       = onBack,
                onCreatePost = { showCreatePost = true },
                unreadCount  = unreadCount,
                onNotif      = { showNotifPanel = true; vm.markAllNotifsRead() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showCreatePost = true },
                containerColor = FAccent,
                contentColor   = Color.White,
                shape          = CircleShape
            ) { Icon(Icons.Rounded.Add, "New Post", modifier = Modifier.size(26.dp)) }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && posts.isEmpty() -> FeedShimmer()
                posts.isEmpty()            -> FeedEmpty()
                else -> FeedContent(
                    posts         = posts,
                    vm            = vm,
                    myConnections = vm.myConnections.collectAsStateWithLifecycle().value,
                    myUid         = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                    onLike        = { postId -> vm.toggleLike(postId) },
                    onReport      = { postId -> reportTarget = postId }
                )
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

// ── Top Bar (Instagram style) ─────────────────────────────────────────────────
@Composable
private fun FeedTopBar(
    onBack      : () -> Unit,
    onCreatePost: () -> Unit,
    unreadCount : Int = 0,
    onNotif     : () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(FBg).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Introvert Thoughts", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = FPrimary, letterSpacing = (-0.3).sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // New post
                IconButton(onClick = onCreatePost, modifier = Modifier.size(40.dp).clip(CircleShape).background(FAccent)) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                // Notification bell
                Box {
                    IconButton(onClick = onNotif, modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, FBorder, CircleShape)) {
                        Icon(Icons.Rounded.Notifications, null, tint = FPrimary, modifier = Modifier.size(22.dp))
                    }
                    if (unreadCount > 0) {
                        Box(
                            Modifier.size(9.dp).clip(CircleShape).background(FRed)
                                .align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp)
                        )
                    }
                }
            }
        }
        Divider(color = FBorder, thickness = 0.5.dp)
    }
}

// ── Feed Content (Scrollable Posts) ───────────────────────────────────────────
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FeedContent(
    posts         : List<IntrovertPost>,
    vm            : IntrovertFeedViewModel,
    myConnections : Set<String>,
    myUid         : String,
    onLike        : (String) -> Unit,
    onReport      : (String) -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = { isRefreshing = true; vm.refreshPosts(); scope.launch { delay(1200); isRefreshing = false } },
        modifier     = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
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
                Divider(color = FBorder, thickness = 0.5.dp)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Post Card (Instagram-inspired) ────────────────────────────────────────────
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

    val shimInf = rememberInfiniteTransition(label = "csf_${post.postId}")
    val shimX by shimInf.animateFloat(
        -200f, 600f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        "csx_${post.postId}"
    )

    val likeScale by animateFloatAsState(
        if (post.isLiked) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "likeScale"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .background(FBg)
            .padding(vertical = 12.dp)
    ) {
        // Header: Avatar + Name + Time + Menu (Single line like Instagram/X)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Anonymous avatar
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(avatarColor(post.authorId)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        post.authorAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FPrimary.copy(alpha = 0.6f)
                    )
                }
                
                // Name and time + Connect button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        post.authorAlias,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FPrimary,
                        letterSpacing = (-0.2).sp
                    )
                    Text("•", fontSize = 12.sp, color = FSecondary)
                    Text(
                        timeAgo(post.createdAt),
                        fontSize = 12.sp,
                        color = FSecondary
                    )
                    // Connect / Connected pill (hidden for own posts)
                    if (!isOwnPost) {
                        val connected = isConnected
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (connected) FAccentSoft else FAccent)
                                .drawWithContent {
                                    drawContent()
                                    if (!connected) {
                                        val br = Brush.linearGradient(
                                            listOf(Color(0x000095F6), Color(0x550095F6), Color(0x000095F6)),
                                            androidx.compose.ui.geometry.Offset(shimX, 0f),
                                            androidx.compose.ui.geometry.Offset(shimX + 80f, size.height)
                                        )
                                        drawRect(brush = br)
                                    }
                                }
                                .clickable(enabled = !connected, onClick = onConnect)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                if (connected) "Connected ✓" else "Connect",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (connected) FAccent else Color.White
                            )
                        }
                    }
                }
            }
            
            // ── Three-dot menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        null,
                        tint = FSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    // Report option
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Flag,
                                    contentDescription = null,
                                    tint = FRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Report",
                                    color = FRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        onClick = {
                            showMenu = false
                            onReport()
                        }
                    )
                }
            }
        }
        
        // Content - No extra top padding, flows naturally after header
        Text(
            post.content,
            fontSize = 15.sp,
            color = FPrimary,
            lineHeight = 22.sp,
            letterSpacing = (-0.1).sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
        )
        
        // Action bar
        val context = LocalContext.current
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Like
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onLike() }
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(
                    if (post.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    null, tint = if (post.isLiked) FRed else FSecondary,
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale }
                )
                if (post.likeCount > 0) Text(
                    "${post.likeCount}", fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, color = if (post.isLiked) FRed else FSecondary
                )
            }
            // Comment
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { showComments = !showComments }
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Rounded.ChatBubbleOutline, null,
                    tint = if (showComments) FAccent else FSecondary, modifier = Modifier.size(22.dp))
                if (post.commentCount > 0) Text(
                    "${post.commentCount}", fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, color = if (showComments) FAccent else FSecondary
                )
            }
            // Share
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT,
                                "\"${post.content}\"\n\n— shared from Veil")
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share thought"))
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Rounded.Share, null, tint = FSecondary, modifier = Modifier.size(22.dp))
            }
        }
        // Comments section
        if (showComments) {
            CommentSection(postId = post.postId, vm = vm)
        }
    }
}

// ── Post Report Dialog ────────────────────────────────────────────────────────
@Composable
private fun PostReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (reason: String, note: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf("") }
    var note           by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, null, tint = FRed, modifier = Modifier.size(20.dp))
                Text("Report Post", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = FPrimary)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What's wrong with this post?", fontSize = 13.sp, color = FSecondary)
                VeilSafetyUtil.POST_REPORT_REASONS.forEach { reason ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedReason == reason) FRedSoft else Color(0xFFF9FAFB))
                            .border(1.dp, if (selectedReason == reason) FRed.copy(0.35f) else FBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedReason = reason }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick  = { selectedReason = reason },
                            colors   = RadioButtonDefaults.colors(selectedColor = FRed)
                        )
                        Text(reason, fontSize = 13.sp, color = FPrimary)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    placeholder = { Text("Add details (optional)", fontSize = 13.sp, color = FMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FRed, unfocusedBorderColor = FBorder,
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                    )
                )
                Text(
                    "⚠️ False reports may result in action against your own account.",
                    fontSize = 11.sp, color = FSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedReason.isNotBlank()) onSubmit(selectedReason, note) },
                enabled = selectedReason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FRed),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Submit Report", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FSecondary) }
        }
    )
}

// ── Create Post — Professional Bottom Sheet ────────────────────────────────────
@Composable
private fun CreatePostDialog(
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
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
            confirmButton = { TextButton(onClick = { showBanWarning = false }) { Text("Got it") } },
            title = { Text("🚨 Content Violation", fontWeight = FontWeight.Bold, color = FRed) },
            text = { Text("Vulgar, explicit, or abusive content leads to a permanent ban. Please revise your post.", fontSize = 14.sp, lineHeight = 21.sp) },
            shape = RoundedCornerShape(20.dp), containerColor = Color.White
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.5f))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color.White)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .imePadding()
            ) {
                // ── Sheet handle ────────────────────────────────────────────
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FMuted))
                }

                // ── Top bar ─────────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Cancel", fontSize = 15.sp, color = FPrimary, fontWeight = FontWeight.Normal)
                    }
                    Text("New Post", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FPrimary)
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (content.trim().length >= 10 && !hasProfanity) FAccent else FMuted)
                            .clickable(enabled = content.trim().length >= 10 && !hasProfanity) {
                                val t = content.trim()
                                if (VeilSafetyUtil.containsProfanity(t)) showBanWarning = true
                                else onPost(t)
                            }.padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text("Post", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                HorizontalDivider(color = FBorder, thickness = 0.5.dp)

                // ── Author row + text area ───────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(avatarColor(myUid)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", fontSize = 18.sp, color = FPrimary.copy(0.5f))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Anonymous", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FPrimary)
                        Spacer(Modifier.height(8.dp))
                        BasicTextField(
                            value = content,
                            onValueChange = {
                                if (it.length <= maxLength) {
                                    content = it
                                    hasProfanity = VeilSafetyUtil.containsProfanity(it)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp)
                                .focusRequester(focusReq),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 16.sp, color = FPrimary, lineHeight = 24.sp
                            ),
                            decorationBox = { inner ->
                                if (content.isEmpty()) Text(
                                    "What's on your introvert mind?",
                                    fontSize = 16.sp, color = FMuted, lineHeight = 24.sp
                                )
                                inner()
                            }
                        )
                    }
                }

                // ── Footer: warnings + char count ────────────────────────────
                Spacer(Modifier.weight(1f))

                if (hasProfanity) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = FRed, modifier = Modifier.size(14.dp))
                        Text("Content violates community guidelines", fontSize = 12.sp, color = FRed)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(Color.White),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Lock, null, tint = FAccent, modifier = Modifier.size(14.dp))
                        Text("Anonymous post", fontSize = 12.sp, color = FAccent, fontWeight = FontWeight.Medium)
                    }
                    // Circular char counter
                    val fraction = content.length.toFloat() / maxLength
                    val counterColor = when {
                        fraction >= 1f   -> FRed
                        fraction >= 0.85f -> FRed.copy(0.7f)
                        else              -> FMuted
                    }
                    Text(
                        "${maxLength - content.length}",
                        fontSize = 12.sp, color = counterColor, fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider(color = FBorder, thickness = 0.3.dp)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focusReq.requestFocus() } }
}

// ── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun FeedEmpty() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(2.dp, FBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Forum,
                    null,
                    tint = FSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                "No posts yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = FPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                "Be the first to share your\nintrovert thoughts",
                fontSize = 14.sp,
                color = FSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Shimmer Loading ───────────────────────────────────────────────────────────
@Composable
private fun FeedShimmer() {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        0.3f, 0.7f,
        infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        "alpha"
    )
    
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(5) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FMuted.copy(alpha = alpha))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .width(100.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(FMuted.copy(alpha = alpha))
                        )
                        Box(
                            Modifier
                                .width(60.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(FMuted.copy(alpha = alpha))
                        )
                    }
                }
                
                // Content
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(FMuted.copy(alpha = alpha))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.9f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(FMuted.copy(alpha = alpha))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(FMuted.copy(alpha = alpha))
                    )
                }
            }
            Divider(color = FBorder, thickness = 0.5.dp)
        }
    }
}

// ── Instagram-Style Bottom Sheet Menu ─────────────────────────────────────────
@Composable
private fun InstagramStyleMenu(
    onDismiss: () -> Unit,
    onReport: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Prevent click through */ }
            ) {
                // Handle bar
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(FMuted)
                    )
                }
                
                // Menu items
                Column(Modifier.fillMaxWidth()) {
                    // Report option
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onReport() }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Flag,
                            null,
                            tint = FRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Report",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = FRed
                        )
                    }
                    
                    Divider(color = FBorder, thickness = 0.5.dp)
                    
                    // Cancel option
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss() }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Cancel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = FSecondary
                        )
                    }
                }
                
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  @mention text renderer
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MentionText(text: String, modifier: Modifier = Modifier, sz: TextUnit = 14.sp) {
    val annotated = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text[i] == '@') {
                val end = text.indexOfFirst { idx -> idx == ' ' || idx == '\n' }.let {
                    val spaceIdx = text.indexOf(' ', i); val nlIdx = text.indexOf('\n', i)
                    when { spaceIdx == -1 && nlIdx == -1 -> text.length; spaceIdx == -1 -> nlIdx; nlIdx == -1 -> spaceIdx; else -> minOf(spaceIdx, nlIdx) }
                }
                withStyle(SpanStyle(color = FAccent, fontWeight = FontWeight.Medium)) { append(text.substring(i, end)) }
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

// ─────────────────────────────────────────────────────────────────────────────
//  Comment Section
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CommentSection(postId: String, vm: IntrovertFeedViewModel) {
    val commentsMap by vm.comments.collectAsStateWithLifecycle()
    val scope       = rememberCoroutineScope()
    val focusReq    = remember { FocusRequester() }

    var inputText          by remember { mutableStateOf("") }
    var replyToCommentId   by remember { mutableStateOf("") }
    var replyToAlias       by remember { mutableStateOf("") }

    LaunchedEffect(postId) { vm.listenComments(postId) }
    DisposableEffect(postId) { onDispose { vm.stopListenComments(postId) } }

    val postComments = commentsMap[postId] ?: emptyList()

    Column(Modifier.fillMaxWidth().background(Color(0xFFFAFAFA))) {
        // Comment list
        postComments.forEach { comment ->
            CommentItem(
                comment  = comment,
                postId   = postId,
                vm       = vm,
                onReply  = { alias ->
                    replyToCommentId = comment.commentId
                    replyToAlias     = alias
                    inputText        = "@$alias "
                    scope.launch { kotlinx.coroutines.delay(100); runCatching { focusReq.requestFocus() } }
                }
            )
        }
        if (postComments.isEmpty()) {
            Text("No comments yet · be the first ✨", fontSize = 12.sp, color = FMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }

        // Reply-to banner
        if (replyToAlias.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().background(FAccentSoft).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Replying to @$replyToAlias", fontSize = 12.sp, color = FAccent, fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.Close, null, tint = FSecondary, modifier = Modifier.size(16.dp).clickable {
                    replyToAlias = ""; replyToCommentId = ""; inputText = ""
                })
            }
        }

        // Input bar
        HorizontalDivider(color = FBorder, thickness = 0.4.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value         = inputText,
                onValueChange = { inputText = it },
                modifier      = Modifier.weight(1f).focusRequester(focusReq)
                    .clip(RoundedCornerShape(20.dp)).background(FBorder.copy(0.35f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
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
                        if (replyToAlias.isNotBlank()) "Reply to @$replyToAlias…" else "Add a comment…",
                        fontSize = 13.sp, color = FMuted)
                    inner()
                }
            )
            if (inputText.isNotBlank()) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(FAccent)
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
                ) { Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Comment Item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CommentItem(comment: FeedComment, postId: String, vm: IntrovertFeedViewModel, onReply: (String) -> Unit) {
    val repliesMap  by vm.replies.collectAsStateWithLifecycle()
    var showReplies by rememberSaveable { mutableStateOf(false) }
    val replyKey    = "${postId}__${comment.commentId}"

    LaunchedEffect(showReplies) { if (showReplies) vm.listenReplies(postId, comment.commentId) }
    DisposableEffect(comment.commentId) { onDispose { if (showReplies) vm.stopListenReplies(postId, comment.commentId) } }

    val replies = repliesMap[replyKey] ?: emptyList()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Avatar
            Box(Modifier.size(32.dp).clip(CircleShape).background(avatarColor(comment.authorId)), Alignment.Center) {
                Text(comment.authorAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FPrimary.copy(0.6f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.authorAlias, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FPrimary)
                    Text(timeAgo(comment.createdAt), fontSize = 11.sp, color = FSecondary)
                }
                MentionText(comment.text, sz = 14.sp)
                // Action row — bigger touch targets
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .clickable(remember { MutableInteractionSource() }, null) { onReply(comment.authorAlias) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Reply", fontSize = 12.sp, color = FSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    if (comment.replyCount > 0) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .clickable(remember { MutableInteractionSource() }, null) { showReplies = !showReplies }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                if (showReplies) "Hide replies" else "View ${comment.replyCount} ${if (comment.replyCount == 1) "reply" else "replies"}",
                                fontSize = 12.sp, color = FAccent, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        // Nested replies — each reply can also be replied to (replies to parent comment)
        if (showReplies) {
            replies.forEach { reply ->
                ReplyItem(reply = reply, onReply = { alias ->
                    onReply(alias)   // triggers reply to parent comment, pre-fills @alias
                })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reply Item (indented)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ReplyItem(reply: FeedReply, onReply: (String) -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 42.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(avatarColor(reply.authorId)), Alignment.Center) {
            Text(reply.authorAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FPrimary.copy(0.6f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(reply.authorAlias, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FPrimary)
                Text(timeAgo(reply.createdAt), fontSize = 11.sp, color = FSecondary)
            }
            MentionText(reply.text, sz = 13.sp)
            // Reply button — replies go to the parent comment thread, @mentioning this person
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onReply(reply.authorAlias) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("Reply", fontSize = 12.sp, color = FSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Notification Panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NotifPanel(notifs: List<FeedNotif>, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable(remember{MutableInteractionSource()}, null) { onDismiss() }, Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.65f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color.White)
                    .clickable(remember{MutableInteractionSource()}, null) {}
            ) {
                // Handle
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FMuted))
                }
                Text("Notifications", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp))
                HorizontalDivider(color = FBorder, thickness = 0.5.dp)
                if (notifs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Notifications, null, tint = FMuted, modifier = Modifier.size(40.dp))
                            Text("No notifications yet", fontSize = 14.sp, color = FSecondary)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(notifs, key = { it.notifId }) { n ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (!n.read) FAccentSoft else Color.White)
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val icon = when (n.type) {
                                    "reply"   -> Icons.Rounded.Reply
                                    "comment" -> Icons.Rounded.ChatBubbleOutline
                                    else      -> Icons.Rounded.PersonAdd
                                }
                                Box(Modifier.size(36.dp).clip(CircleShape).background(FAccentSoft), Alignment.Center) {
                                    Icon(icon, null, tint = FAccent, modifier = Modifier.size(18.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        when (n.type) {
                                            "reply"   -> "${n.fromAlias} replied to your comment"
                                            "comment" -> "${n.fromAlias} commented on your post"
                                            else      -> "${n.fromAlias} sent you a friend request"
                                        },
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FPrimary
                                    )
                                    if (n.previewText.isNotBlank()) {
                                        Text("\"${n.previewText}\"", fontSize = 12.sp, color = FSecondary, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(timeAgo(n.createdAt), fontSize = 11.sp, color = FMuted)
                                }
                                if (!n.read) Box(Modifier.size(7.dp).clip(CircleShape).background(FAccent).align(Alignment.CenterVertically))
                            }
                            HorizontalDivider(color = FBorder, thickness = 0.3.dp)
                        }
                    }
                }
            }
        }
    }
}
