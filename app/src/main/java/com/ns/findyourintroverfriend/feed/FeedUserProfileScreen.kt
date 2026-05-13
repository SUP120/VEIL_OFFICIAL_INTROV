package com.ns.findyourintroverfriend.feed

import android.util.Log
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
import androidx.compose.ui.draw.drawBehind
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// ── Design tokens ─────────────────────────────────────────────────────────────
private val PBg      = Color(0xFFFFFFFF)
private val PBlack   = Color(0xFF1A1A1A)
private val PGray    = Color(0xFF8A8A8A)
private val PDivider = Color(0xFFE8E8E8)
private val PAccent  = Color(0xFF6366F1)   // indigo
private val PSurface = Color(0xFFF7F7F8)
private val PGold    = Color(0xFFF59E0B)

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

    var profile       by remember { mutableStateOf(UserProfileData()) }
    val myApproaches  by approachVm.myApproaches.collectAsStateWithLifecycle()
    val freeLeft      by approachVm.freeLeft.collectAsStateWithLifecycle()
    val result        by approachVm.result.collectAsStateWithLifecycle()
    val cookieBalance by cookieVm.cookieBalance.collectAsStateWithLifecycle()
    val myFollowing   by approachVm.myFollowing.collectAsStateWithLifecycle()
    var showCookieDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

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

            @Suppress("UNCHECKED_CAST")
            val rawPosts = safeGet {
                db.collection("veilPosts").whereEqualTo("authorUid", targetUid).limit(30).get().await()
            }?.documents ?: emptyList()

            val posts = rawPosts.mapNotNull { doc ->
                try {
                    VeilPost(
                        postId = doc.id,
                        authorUid      = doc.getString("authorUid")      ?: "",
                        authorAlias    = doc.getString("authorAlias")    ?: "",
                        authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
                        authorAvatar   = doc.getString("authorAvatar")   ?: "",
                        caption        = doc.getString("caption")        ?: "",
                        imageUrls      = (doc.get("imageUrls") as? List<String>) ?: emptyList(),
                        likedBy        = (doc.get("likedBy")   as? List<String>) ?: emptyList(),
                        commentsCount  = doc.getLong("commentsCount") ?: 0L,
                        createdAt      = doc.getLong("createdAt")     ?: 0L
                    )
                } catch (_: Exception) { null }
            }.sortedByDescending { it.createdAt }

            val alias = posts.firstOrNull()?.authorAlias?.takeIf { it.isNotBlank() }
                ?: swipeDoc?.getString("alias")?.takeIf { it.isNotBlank() }
                ?: userDoc?.getString("alias")?.takeIf { it.isNotBlank() }
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
            is ApproachResult.Sent        -> { snackbar.showSnackbar("Request sent ✓"); approachVm.resetResult() }
            is ApproachResult.NeedCookies -> { showCookieDialog = true; approachVm.resetResult() }
            is ApproachResult.Error       -> { snackbar.showSnackbar(r.message); approachVm.resetResult() }
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
                        if (profile.isLoading) "" else profile.alias,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = PBlack,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, null, tint = PBlack, modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PBg),
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                profile.isLoading -> ProfileSkeletonLoader()
                profile.error != null -> ProfileErrorState(profile.error!!) {
                    profile = UserProfileData(isLoading = true)
                }
                else -> ProfileContent(
                    profile           = profile,
                    isOwnProfile      = targetUid == myUid,
                    alreadyApproached = targetUid in myApproaches,
                    isFollowing       = targetUid in myFollowing,
                    freeLeft          = freeLeft,
                    onApproach        = {
                        approachVm.sendFreeApproach(
                            toUid = targetUid, toAlias = profile.alias,
                            myAlias = myOwnAlias, myAvatar = "", myPhotoUrl = myOwnPhoto
                        )
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
    isOwnProfile     : Boolean,
    alreadyApproached: Boolean,
    isFollowing      : Boolean,
    freeLeft         : Int,
    onApproach       : () -> Unit
) {
    var expandedPost by remember { mutableStateOf<VeilPost?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
            item(span = { GridItemSpan(3) }) {
                ProfileHeader(profile, isOwnProfile, alreadyApproached, isFollowing, freeLeft, onApproach)
            }
            item(span = { GridItemSpan(3) }) {
                Column {
                    HorizontalDivider(color = PDivider, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box(
                            Modifier.padding(vertical = 10.dp)
                                .drawBehind {
                                    drawLine(Color(0xFF1A1A1A), Offset(0f, size.height),
                                        Offset(size.width, size.height), strokeWidth = 2f)
                                }.padding(horizontal = 24.dp)
                        ) {
                            Icon(Icons.Rounded.GridView, null, tint = PBlack, modifier = Modifier.size(22.dp))
                        }
                    }
                    HorizontalDivider(color = PDivider, thickness = 0.5.dp)
                }
            }
            if (profile.posts.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.GridView, null, tint = PDivider, modifier = Modifier.size(40.dp))
                            Text("No posts yet", color = PGray, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(profile.posts) { post ->
                    ProfilePostTile(post, onClick = { expandedPost = post })
                }
            }
            item(span = { GridItemSpan(3) }) { Spacer(Modifier.height(40.dp)) }
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
        colors = listOf(Color(0xFFE8E8E8), Color(0xFFF6F6F6), Color(0xFFE8E8E8)),
        start  = Offset(shimmerX, 0f),
        end    = Offset(shimmerX + 400f, 0f)
    )

    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false) {
        // Header skeleton
        item(span = { GridItemSpan(3) }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Avatar circle
                    Box(Modifier.size(80.dp).clip(CircleShape).background(brush))
                    // Stats
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(2) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.width(28.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                                Box(Modifier.width(44.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Name line
                Box(Modifier.fillMaxWidth(0.38f).height(14.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                Spacer(Modifier.height(8.dp))
                // Sub line (joined date)
                Box(Modifier.fillMaxWidth(0.28f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.height(16.dp))
                // Button placeholder
                Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).background(brush))
            }
        }
        // Divider + tab bar
        item(span = { GridItemSpan(3) }) {
            HorizontalDivider(color = PDivider, thickness = 0.5.dp)
            Spacer(Modifier.height(44.dp)) // tab bar height placeholder
            HorizontalDivider(color = PDivider, thickness = 0.5.dp)
        }
        // Grid tile skeletons
        items(9) {
            Box(
                Modifier.aspectRatio(1f).padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    profile          : UserProfileData,
    isOwnProfile     : Boolean,
    alreadyApproached: Boolean,
    isFollowing      : Boolean,
    freeLeft         : Int,
    onApproach       : () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 4.dp)
    ) {
        // Top row: Avatar + Stats
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Avatar
            Box(Modifier.size(80.dp)) {
                if (profile.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = profile.photoUrl, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                            .border(2.dp, PAccent, CircleShape)
                    )
                } else {
                    // Monogram avatar — clean and minimal
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFEEF0FF), Color(0xFFF3F4FF)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            profile.alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = PAccent
                        )
                    }
                }
            }

            // Stats row — Posts + Introvert Friends only
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatPill(profile.postCount.toString(), "Posts")
                // Vertical divider
                Box(Modifier.width(1.dp).height(32.dp).background(PDivider))
                StatPill(profile.matchCount.toString(), "Introvert Friends")
            }
        }

        Spacer(Modifier.height(14.dp))

        // Name + days-since-joined badge inline
        val daysSinceJoined = if (profile.joinedAt > 0L)
            ((System.currentTimeMillis() - profile.joinedAt) / 86_400_000L).toInt().coerceAtLeast(1)
        else 0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile.alias, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = PBlack)
            if (daysSinceJoined > 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(PSurface)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "Day $daysSinceJoined",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PAccent
                    )
                }
            }
        }

        // Joined date — small, subtle
        if (profile.joinedAt > 0L) {
            val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = profile.joinedAt }
            val joined = "Joined ${months[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}"
            Text(joined, fontSize = 12.sp, color = PGray, modifier = Modifier.padding(top = 2.dp))
        }

        // Approach / Following button
        if (!isOwnProfile) {
            Spacer(Modifier.height(14.dp))
            when {
                isFollowing -> {
                    OutlinedButton(
                        onClick  = {},
                        enabled  = false,
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape    = RoundedCornerShape(10.dp),
                        border   = BorderStroke(1.dp, PDivider)
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = PGray, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Friends", color = PGray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                alreadyApproached -> {
                    OutlinedButton(
                        onClick  = {},
                        enabled  = false,
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape    = RoundedCornerShape(10.dp),
                        border   = BorderStroke(1.dp, PAccent.copy(0.4f))
                    ) {
                        Icon(Icons.Rounded.HourglassTop, null, tint = PAccent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Request Sent", color = PAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> {
                    Button(
                        onClick  = onApproach,
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PBlack)
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Connect",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = PBlack, letterSpacing = (-0.3).sp)
        Text(label, fontSize = 11.sp, color = PGray, letterSpacing = 0.sp)
    }
}

// ── Post Grid Tile ────────────────────────────────────────────────────────────
@Composable
private fun ProfilePostTile(post: VeilPost, onClick: () -> Unit = {}) {
    Box(
        Modifier.aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(PSurface)
            .border(0.5.dp, PDivider, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        if (post.imageUrls.isNotEmpty()) {
            val thumb = com.ns.findyourintroverfriend.util.CloudinaryUtil
                .thumbnail(post.imageUrls.first(), width = 200, quality = 45)
            AsyncImage(model = thumb, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (post.imageUrls.size > 1)
                Icon(Icons.Rounded.Collections, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(15.dp))
        } else {
            // Text post tile — clean minimal look, NO gradient brush (crash-safe)
            Box(
                Modifier.fillMaxSize().background(Color(0xFFF0F0FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    post.caption.take(60).let { if (post.caption.length > 60) "$it…" else it },
                    fontSize   = 9.5.sp,
                    color      = PBlack.copy(0.75f),
                    textAlign  = TextAlign.Center,
                    lineHeight = 13.sp,
                    modifier   = Modifier.padding(8.dp)
                )
            }
            // "T" badge top-right corner
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp)
                    .size(16.dp).clip(CircleShape)
                    .background(Color.White.copy(0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text("T", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PAccent)
            }
        }
        // Like count overlay — crash-safe solid scrim instead of verticalGradient
        if (post.likedBy.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.30f))
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    Text("${post.likedBy.size}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Post Expand Overlay ───────────────────────────────────────────────────────
@Composable
private fun PostExpandOverlay(post: VeilPost, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false
        )
    ) {
        Box(
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(PBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* intercept — don't dismiss */ }
            ) {
                // Image or text content
                if (post.imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model            = post.imageUrls.first(),
                        contentDescription = null,
                        contentScale     = ContentScale.FillWidth,
                        modifier         = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    )
                } else {
                    // Full text display
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 180.dp)
                            .background(Color(0xFFF0F0FF))
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
