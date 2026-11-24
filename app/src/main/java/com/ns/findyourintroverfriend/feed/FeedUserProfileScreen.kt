package com.ns.findyourintroverfriend.feed

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.report.BlockUserDialog
import com.ns.findyourintroverfriend.report.SafetyForm
import com.ns.findyourintroverfriend.report.SafetyTarget
import com.ns.findyourintroverfriend.report.contactAvailability
import com.ns.findyourintroverfriend.ui.theme.currentProfilePalette
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Design tokens ─────────────────────────────────────────────────────────────
private val PBg: Color
    @Composable get() = currentProfilePalette.background
private val PBlack: Color
    @Composable get() = currentProfilePalette.text
private val PGray: Color
    @Composable get() = currentProfilePalette.secondaryText
private val PDivider: Color
    @Composable get() = currentProfilePalette.border
private val PAccent: Color
    @Composable get() = currentProfilePalette.accent
private val PSurface: Color
    @Composable get() = currentProfilePalette.tintedSurface

data class UserProfileData(
    val uid               : String         = "",
    val alias             : String         = "Anonymous",
    val photoUrl          : String         = "",
    val postCount         : Long           = 0L,
    val matchCount        : Long           = 0L,
    val approachesReceived: Long           = 0L,
    val joinedAt          : Long           = 0L,
    val introvertScore    : Int            = 0,
    val posts             : List<VeilPost> = emptyList(),
    val isLoading         : Boolean        = true,
    val error             : String?        = null
)

private suspend fun <T> safeGet(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) { throw e
} catch (e: Exception) { Log.w("ProfileScreen", e.message ?: "fetch failed"); null }

private fun com.google.firebase.firestore.DocumentSnapshot.safeGetMillis(field: String): Long? =
    try { getLong(field) } catch (_: Exception) {
        try { getTimestamp(field)?.toDate()?.time } catch (_: Exception) { null }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedUserProfileScreen(
    targetUid        : String,
    approachVm       : FeedApproachViewModel = viewModel(),
    cookieVm         : com.ns.findyourintroverfriend.billing.CookieViewModel = viewModel(),
    onBack           : () -> Unit = {},
    onOpenCookieStore: () -> Unit = {}
) {
    val db    = remember { FirebaseFirestore.getInstance() }
    val auth  = remember { FirebaseAuth.getInstance() }
    val myUid = auth.currentUser?.uid ?: ""
    var safetyMenu by remember(targetUid) { mutableStateOf(false) }
    var sendingRequest by remember(targetUid) { mutableStateOf(false) }
    var reportUser by remember(targetUid) { mutableStateOf(false) }
    var blockUser by remember(targetUid) { mutableStateOf(false) }
    val canContact = contactAvailability(myUid, targetUid)
    var username by remember(targetUid, myUid) { mutableStateOf("") }
    LaunchedEffect(targetUid, myUid) {
        if (targetUid.isNotBlank() && myUid.isNotBlank()) {
            val collection = if (targetUid == myUid) "peopleSettings" else "peopleProfiles"
            username = safeGet { db.collection(collection).document(targetUid).get().await().getString("username") }.orEmpty()
        }
    }

    if (reportUser) SafetyForm(SafetyTarget("profile", "Report User", reportedUserId = targetUid)) { reportUser = false }
    if (blockUser) BlockUserDialog(myUid, targetUid, onDismiss = { blockUser = false }, onBlocked = { blockUser = false })

    var profile       by remember { mutableStateOf(UserProfileData()) }
    val myApproaches  by approachVm.myApproaches.collectAsStateWithLifecycle()
    val result        by approachVm.result.collectAsStateWithLifecycle()
    val cookieBalance by cookieVm.cookieBalance.collectAsStateWithLifecycle()
    val myFollowing   by approachVm.myFollowing.collectAsStateWithLifecycle()
    var showCookieDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var myOwnAlias by remember { mutableStateOf(auth.currentUser?.displayName ?: "Anonymous") }
    var myOwnPhoto by remember { mutableStateOf(auth.currentUser?.photoUrl?.toString() ?: "") }

    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        try {
            val swipe = db.collection("swipeProfiles").document(myUid).get().await()
            val users = db.collection("users").document(myUid).get().await()
            myOwnAlias = swipe.getString("alias")?.takeIf { it.isNotBlank() }
                ?: users.getString("alias")?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.displayName ?: "Anonymous"
            val cameraShy = swipe.getBoolean("isCameraShy") ?: false
            if (!cameraShy) {
                myOwnPhoto = swipe.getString("photoUrl")?.takeIf { it.isNotBlank() }
                    ?: users.getString("photoUrl")?.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.photoUrl?.toString() ?: ""
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(targetUid) {
        if (targetUid.isBlank()) { profile = UserProfileData(isLoading = false, error = "Invalid user"); return@LaunchedEffect }
        profile = UserProfileData(isLoading = true)
        try {
            val userDoc  = safeGet { db.collection("users").document(targetUid).get().await() }
            val swipeDoc = safeGet { db.collection("swipeProfiles").document(targetUid).get().await() }

            val rawPosts = safeGet {
                db.collection("veilPosts").whereEqualTo("authorUid", targetUid).limit(30).get().await()
            }?.documents ?: emptyList()

            val posts = rawPosts.mapNotNull(DocumentSnapshot::toProfilePost).sortedByDescending { it.createdAt }

            val alias = userDoc?.getString("alias")?.takeIf { it.isNotBlank() }
                ?: swipeDoc?.getString("alias")?.takeIf { it.isNotBlank() }
                ?: posts.firstOrNull()?.authorAlias?.takeIf { it.isNotBlank() }
                ?: "Anonymous"

            val isCameraShy = swipeDoc?.getBoolean("isCameraShy") ?: false
            val photoUrl = if (isCameraShy) "" else
                posts.firstOrNull()?.authorPhotoUrl?.takeIf { it.isNotBlank() }
                    ?: swipeDoc?.getString("photoUrl")?.takeIf { it.isNotBlank() }
                    ?: userDoc?.getString("photoUrl")?.takeIf { it.isNotBlank() }
                    ?: ""

            val postCount  = posts.size.toLong()
            val matchCount = run {
                val fromDoc = try { userDoc?.getLong("matchCount") ?: 0L } catch (_: Exception) { 0L }
                val a = safeGet { db.collection("matches").whereEqualTo("userA", targetUid).get().await() }?.documents?.size?.toLong() ?: -1L
                val b = safeGet { db.collection("matches").whereEqualTo("userB", targetUid).get().await() }?.documents?.size?.toLong() ?: -1L
                if (a >= 0 && b >= 0) a + b else fromDoc
            }
            val approachesRcvd = run {
                val fromDoc = try { userDoc?.getLong("approachesReceived") ?: 0L } catch (_: Exception) { 0L }
                safeGet { db.collection("friendApproaches").whereEqualTo("toUid", targetUid).get().await() }?.documents?.size?.toLong() ?: fromDoc
            }
            val joinedAt = userDoc?.safeGetMillis("createdAt") ?: userDoc?.safeGetMillis("joinedAt") ?: 0L
            val score    = computeIntrovertScore(postCount, matchCount, approachesRcvd, joinedAt)

            profile = UserProfileData(
                uid = targetUid, alias = alias, photoUrl = photoUrl,
                postCount = postCount, matchCount = matchCount,
                approachesReceived = approachesRcvd, joinedAt = joinedAt,
                introvertScore = score, posts = posts, isLoading = false
            )
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e("ProfileScreen", "load error", e)
            profile = UserProfileData(isLoading = false, error = e.message ?: "Unknown error")
        }
    }

    LaunchedEffect(result) {
        when (val r = result) {
            is ApproachResult.Sent -> {
                sendingRequest = false
                approachVm.resetResult()
            }
            is ApproachResult.NeedCookies -> {
                sendingRequest = false
                showCookieDialog = true
                approachVm.resetResult()
            }
            is ApproachResult.Error -> {
                sendingRequest = false
                approachVm.resetResult()
                scope.launch { snackbar.showSnackbar(r.message) }
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = PBg,
        snackbarHost   = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = PBlack,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = PBlack, modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    if (myUid.isNotBlank() && targetUid.isNotBlank() && targetUid != myUid) {
                        Box {
                            IconButton(onClick = { safetyMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, "Profile safety options", tint = PBlack)
                            }
                            val menuBackground = Color(0xFF282329)
                            val menuForeground = Color(0xFFFFF7F3)
                            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                                surface = menuBackground,
                                surfaceContainer = menuBackground,
                                surfaceTint = menuBackground,
                                onSurface = menuForeground
                            )) {
                                val menuColors = MenuDefaults.itemColors(
                                    textColor = menuForeground, leadingIconColor = menuForeground,
                                    disabledTextColor = Color(0xFFBCB0B7), disabledLeadingIconColor = Color(0xFFBCB0B7)
                                )
                                DropdownMenu(expanded = safetyMenu, onDismissRequest = { safetyMenu = false }) {
                                    DropdownMenuItem(text = { Text("Report user") }, colors = menuColors,
                                        leadingIcon = { Icon(Icons.Rounded.Flag, null) }, onClick = {
                                            safetyMenu = false
                                            reportUser = true
                                        })
                                    DropdownMenuItem(text = { Text("Block user") }, colors = menuColors,
                                        leadingIcon = { Icon(Icons.Rounded.Block, null) }, enabled = canContact == true, onClick = {
                                            safetyMenu = false
                                            blockUser = true
                                        })
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PBg),
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                canContact == false -> Text("Direct contact is unavailable.", Modifier.align(Alignment.Center), color = PBlack)
                profile.isLoading -> ProfileSkeletonLoader()
                profile.error != null -> ProfileErrorState(profile.error!!) {
                    profile = UserProfileData(isLoading = true)
                }
                else -> ProfileContent(
                    profile           = profile,
                    username          = username,
                    isOwnProfile      = targetUid == myUid,
                    alreadyApproached = targetUid in myApproaches,
                    isFollowing       = targetUid in myFollowing,
                    sendingRequest    = sendingRequest,
                    canSendRequest    = canContact == true,
                    onApproach        = {
                        if (!sendingRequest && canContact == true) {
                            sendingRequest = true
                            approachVm.sendFreeApproach(
                                toUid = targetUid, toAlias = profile.alias,
                                myAlias = myOwnAlias, myAvatar = "", myPhotoUrl = myOwnPhoto
                            )
                        }
                    }
                )
            }
        }
    }

    // Cookie dialog removed — connect is now free
}

// ── Profile Content ───────────────────────────────────────────────────────────
@Composable
private fun ProfileContent(
    profile          : UserProfileData,
    username         : String,
    isOwnProfile     : Boolean,
    alreadyApproached: Boolean,
    isFollowing      : Boolean,
    sendingRequest   : Boolean,
    canSendRequest   : Boolean,
    onApproach       : () -> Unit
) {
    var expandedPost by remember { mutableStateOf<VeilPost?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeader(
                    profile, username, isOwnProfile, alreadyApproached,
                    isFollowing, sendingRequest, canSendRequest, onApproach
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Posts", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = PBlack,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            }
            if (profile.posts.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.GridView, null, tint = PDivider, modifier = Modifier.size(40.dp))
                            Text("No posts yet", color = PGray, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(profile.posts, key = { it.postId }) { post ->
                    ProfilePostTile(post, onClick = { expandedPost = post })
                }
            }
        }
        expandedPost?.let { post ->
            PostExpandOverlay(post = post, onDismiss = { expandedPost = null })
        }
    }
}

// ── Profile Skeleton Loader ───────────────────────────────────────────────────
@Composable
private fun ProfileSkeletonLoader() {
    val transition = rememberInfiniteTransition(label = "pshimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -600f, targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "pshimmer"
    )
    val brush = Brush.linearGradient(
        colors = listOf(PSurface, PDivider, PSurface),
        start = Offset(shimmerX, 0f),
        end = Offset(shimmerX + 400f, 0f)
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(222.dp).clip(RoundedCornerShape(28.dp)).background(brush))
        }
        items(6) {
            Box(Modifier.aspectRatio(1.15f).clip(RoundedCornerShape(18.dp)).background(brush))
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfileData,
    username: String,
    isOwnProfile: Boolean,
    alreadyApproached: Boolean,
    isFollowing: Boolean,
    sendingRequest: Boolean,
    canSendRequest: Boolean,
    onApproach: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        val shape = RoundedCornerShape(28.dp)
        val heroEnd = if (LocalVeilIsDark.current) Color(0xFF362635) else Color(0xFFF8EEF4)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(PSurface, heroEnd)))
                .border(1.dp, PDivider, shape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(68.dp).clip(CircleShape).background(PBg)
                    .border(1.dp, PDivider, CircleShape), contentAlignment = Alignment.Center) {
                    if (profile.photoUrl.isNotBlank()) {
                        AsyncImage(model = profile.photoUrl, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(profile.alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = PAccent)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(profile.alias, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = PBlack,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (username.isNotBlank()) {
                        Text("@$username", fontSize = 14.sp, color = PAccent,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (profile.joinedAt > 0L) {
                        val joined = remember(profile.joinedAt) {
                            java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date(profile.joinedAt))
                        }
                        Text("Joined $joined", fontSize = 13.sp, color = PGray)
                    }
                }
            }
            HorizontalDivider(color = PDivider)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatPill(profile.postCount.toString(), "Posts")
                StatPill(profile.matchCount.toString(), "Friends")
            }
            if (!isOwnProfile && (sendingRequest || alreadyApproached || isFollowing)) {
                Surface(
                    color = PBg,
                    border = BorderStroke(1.dp, PDivider),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(36.dp).background(PSurface, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            if (sendingRequest) {
                                CircularProgressIndicator(Modifier.size(17.dp), color = PAccent, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Check, null, tint = PAccent, modifier = Modifier.size(19.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    isFollowing -> "Connected"
                                    sendingRequest -> "Sending request…"
                                    else -> "Request sent"
                                },
                                fontWeight = FontWeight.SemiBold, color = PBlack
                            )
                            if (!isFollowing && !sendingRequest) {
                                Text("Waiting for a response", fontSize = 12.sp, color = PGray)
                            }
                        }
                    }
                }
            } else if (!isOwnProfile) {
                Button(
                    onClick = onApproach, enabled = canSendRequest,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PAccent,
                        contentColor = currentProfilePalette.onAccent,
                        disabledContainerColor = PBg,
                        disabledContentColor = PGray
                    )
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = PBlack, letterSpacing = (-0.3).sp)
        Text(label, fontSize = 11.sp, color = PGray, letterSpacing = 0.sp)
    }
}

internal fun DocumentSnapshot.toProfilePost(): VeilPost? = try {
    VeilPost(
        postId = id,
        authorUid = getString("authorUid").orEmpty(),
        authorAlias = getString("authorAlias").orEmpty(),
        authorPhotoUrl = getString("authorPhotoUrl").orEmpty(),
        authorAvatar = getString("authorAvatar").orEmpty(),
        caption = getString("caption").orEmpty(),
        imageUrls = (get("imageUrls") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        likedBy = (get("likedBy") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        commentsCount = getLong("commentsCount") ?: 0L,
        createdAt = safeGetMillis("createdAt") ?: 0L
    )
} catch (_: Exception) { null }

// ── Post Grid Tile ────────────────────────────────────────────────────────────
@Composable
internal fun ProfilePostTile(post: VeilPost, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.aspectRatio(1.15f).clip(shape).background(PSurface)
            .border(1.dp, PDivider, shape).clickable(onClickLabel = "Open post", onClick = onClick)
    ) {
        if (post.imageUrls.isNotEmpty()) {
            val thumbnail = com.ns.findyourintroverfriend.util.CloudinaryUtil
                .thumbnail(post.imageUrls.first(), width = 400, quality = 60)
            AsyncImage(model = thumbnail, contentDescription = post.caption.ifBlank { "Photo post" },
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (post.imageUrls.size > 1) {
                Icon(Icons.Rounded.Collections, "Multiple photos", tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp))
            }
        } else {
            Text(post.caption.ifBlank { "Post" }, color = PBlack, fontSize = 14.sp, lineHeight = 20.sp,
                maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
        }
    }
}

// ── Post Expand Overlay ───────────────────────────────────────────────────────
@Composable
internal fun PostExpandOverlay(post: VeilPost, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false
        )
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = maxHeight * 0.90f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* intercept — don't dismiss */ }
                    .verticalScroll(rememberScrollState())
            ) {
                // Image or text content
                if (post.imageUrls.isNotEmpty()) {
                    PostPhotoGallery(post.imageUrls)
                } else {
                    // Full text display
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 180.dp)
                            .background(PSurface)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            post.caption,
                            fontSize   = 15.sp,
                            color      = PBlack,
                            lineHeight = 22.sp,
                            textAlign  = TextAlign.Center
                        )
                    }
                }

                // Footer: caption (for image posts) + meta
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Caption (only for image posts — text posts already show full content above)
                    if (post.imageUrls.isNotEmpty() && post.caption.isNotBlank()) {
                        Text(
                            post.caption,
                            fontSize   = 14.sp,
                            color      = PBlack,
                            lineHeight = 20.sp
                        )
                    }

                    // Meta row: likes + comments
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (post.likedBy.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFED4956),
                                    modifier = Modifier.size(15.dp))
                                Text("${post.likedBy.size}", fontSize = 13.sp,
                                    color = PBlack, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (post.commentsCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.ChatBubbleOutline, null, tint = PGray,
                                    modifier = Modifier.size(14.dp))
                                Text("${post.commentsCount}", fontSize = 13.sp, color = PGray)
                            }
                        }
                    }

                    // Close pill
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PSurface)
                                .clickable { onDismiss() }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text("Close", fontSize = 13.sp, color = PGray,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
// ── Error state ───────────────────────────────────────────────────────────────
@Composable
private fun ProfileErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(PSurface), Alignment.Center) {
                Icon(Icons.Rounded.WifiOff, null, tint = PGray, modifier = Modifier.size(26.dp))
            }
            Text("Couldn't load profile", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PBlack)
            Text(message, fontSize = 12.sp, color = PGray, textAlign = TextAlign.Center, lineHeight = 18.sp)
            Button(onClick = onRetry, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PBlack)) {
                Text("Try again", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}
