package com.ns.findyourintroverfriend.aesthetics

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
//  Palette — warm ivory / amber (Instagram-clean feed)
// ─────────────────────────────────────────────────────────────────────────────
private val BgPage      = Color(0xFFFDF8F2)   // warm ivory
private val BgCard      = Color(0xFFFFFFFF)   // white card
private val BgElevated  = Color(0xFFFFF4E8)   // peach-cream
private val BgSheet     = Color(0xFFFFFFFF)   // white sheet
private val BdSubtle    = Color(0xFFF0E4D4)   // very light beige
private val BdActive    = Color(0xFFEDD9C0)   // warm beige
private val TxtPrimary  = Color(0xFF2D1F0E)   // deep warm brown
private val TxtSecond   = Color(0xFF7A5C3A)   // medium warm brown
private val TxtTertiary = Color(0xFFB08A60)   // light warm tan
private val MomAmber    = Color(0xFFE8832A)   // amber accent
private val HeartRed    = Color(0xFFE05050)   // warm red
private val GreenOk     = Color(0xFF2E9668)   // forest green
private val DangerRed   = Color(0xFFD93025)   // danger
private val AccentWhite = Color(0xFFFFFFFF)   // kept for compat

// ─────────────────────────────────────────────────────────────────────────────
//  Root screen — "Moments"
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AestheticsFeedScreen(
    onBack : () -> Unit,
    vm     : AestheticsFeedViewModel = viewModel()
) {
    val state       by vm.state.collectAsStateWithLifecycle()
    val context      = LocalContext.current
    val listState    = rememberLazyListState()

    var showUploadSheet     by remember { mutableStateOf(false) }
    var showGuides          by remember { mutableStateOf(false) }
    var croppedUri          by remember { mutableStateOf<Uri?>(null) }
    var captionText         by remember { mutableStateOf("") }
    val scope                = rememberCoroutineScope()

    // Bottom-sheet state for options menu on a post
    var optionsPost          by remember { mutableStateOf<AestheticPost?>(null) }
    var optionsUploaderUid   by remember { mutableStateOf("") }

    // Report sub-flow
    var reportingPost        by remember { mutableStateOf<AestheticPost?>(null) }
    var showReportSuccess    by remember { mutableStateOf(false) }
    var blockedToast         by remember { mutableStateOf(false) }

    // ── UCrop result ──────────────────────────────────────────────────────────
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultUri = try {
            UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)
        } catch (_: Exception) { null }
        if (result.resultCode == android.app.Activity.RESULT_OK && resultUri != null) {
            croppedUri = resultUri
            captionText = ""
            showUploadSheet = true
        }
    }

    // ── Gallery picker → UCrop (1:1 square like Instagram) ────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { sourceUri ->
        if (sourceUri == null) return@rememberLauncherForActivityResult
        val destFile = File(context.cacheDir, "moments_${System.currentTimeMillis()}.jpg")
        val cropIntent = UCrop.of(sourceUri, Uri.fromFile(destFile))
            .withOptions(UCrop.Options().apply {
                setFreeStyleCropEnabled(false)         // force 1:1 — no free resize
                setShowCropGrid(true)
                setStatusBarColor(android.graphics.Color.parseColor("#FDF8F2"))
                setToolbarColor(android.graphics.Color.parseColor("#FDF8F2"))
                setToolbarWidgetColor(android.graphics.Color.parseColor("#2D1F0E"))
                setActiveControlsWidgetColor(android.graphics.Color.parseColor("#E8832A"))
                setToolbarTitle("Crop Photo")
                setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
                setCompressionQuality(88)
            })
            .withAspectRatio(1f, 1f)              // 1:1 square crop
            .withMaxResultSize(1080, 1080)         // Instagram standard
            .getIntent(context)
        cropLauncher.launch(cropIntent)
    }

    // Auto-dismiss upload sheet on success
    LaunchedEffect(state.uploadState) {
        if (state.uploadState is UploadState.Success) {
            kotlinx.coroutines.delay(1_600)
            showUploadSheet = false
            croppedUri = null
            captionText = ""
            vm.resetUploadState()
        }
    }
    // Auto dismiss block toast
    LaunchedEffect(blockedToast) {
        if (blockedToast) { kotlinx.coroutines.delay(2_000); blockedToast = false }
    }

    Box(Modifier.fillMaxSize().background(
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(BgPage, Color(0xFFFFF4E8), Color(0xFFFEEDD8)))
    )) {

        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ── Top bar ───────────────────────────────────────────────────────
            MomentsTopBar(
                postsToday = state.postsToday,
                onBack     = onBack,
                onUpload   = { if (state.postsToday < 2) showGuides = true }
            )

            when {
                state.loading       -> LoadingView()
                state.error != null -> ErrorView(state.error!!)
                state.groups.isEmpty() -> EmptyOnboarding(
                    hasPosted = state.postsToday > 0,
                    onUpload  = { showGuides = true }
                )
                else -> {
                    LazyColumn(
                        state          = listState,
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier       = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            state.groups,
                            key = { _, g -> g.uploaderUid }
                        ) { _, group ->
                            MomentUserSection(
                                group    = group,
                                onHeart  = { post -> vm.toggleHeart(post) },
                                onOptions = { post ->
                                    optionsPost       = post
                                    optionsUploaderUid = post.uploaderUid
                                }
                            )
                        }
                    }
                }
            }
        }

        // PostFab removed — upload triggered via camera icon in top bar only

        // ── Block toast ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = blockedToast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
            enter    = slideInVertically { it / 2 } + fadeIn(),
            exit     = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(1.dp, BdActive, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Block, null, tint = DangerRed, modifier = Modifier.size(14.dp))
                    Text("User blocked — you won't see their posts", style = MaterialTheme.typography.labelMedium.copy(
                        color = TxtPrimary, fontSize = 12.sp
                    ))
                }
            }
        }

        // ── Report success toast ──────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showReportSuccess,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
            enter    = slideInVertically { it / 2 } + fadeIn(),
            exit     = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(1.dp, BdActive, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(14.dp))
                    Text("Reported — post removed from your feed", style = MaterialTheme.typography.labelMedium.copy(
                        color = TxtPrimary, fontSize = 12.sp
                    ))
                }
            }
        }
    }

    // ── Posting guidelines dialog ─────────────────────────────────────────────
    if (showGuides) {
        AlertDialog(
            onDismissRequest = { showGuides = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(MomAmber.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, null, tint = MomAmber, modifier = Modifier.size(18.dp))
                    }
                    Text("Before you post", style = MaterialTheme.typography.titleMedium.copy(
                        color = TxtPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp
                    ))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Share something that shows your vibe — a selfie, a place, something beautiful.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TxtSecond, lineHeight = 18.sp))
                    Spacer(Modifier.height(4.dp))
                    GuideRow(allowed = true,  text = "Portraits, selfies, your face")
                    GuideRow(allowed = true,  text = "Places, food, art, sky, objects")
                    GuideRow(allowed = false, text = "Nudity or sexual content")
                    GuideRow(allowed = false, text = "Violence, blood or graphic content")
                    GuideRow(allowed = false, text = "Screenshots, memes, text images")
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgElevated)
                            .border(1.dp, BdActive, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, null, tint = TxtTertiary, modifier = Modifier.size(13.dp))
                        Text("Photos are auto-scanned. Limit: 2 per day.",
                            style = MaterialTheme.typography.labelSmall.copy(color = TxtTertiary, fontSize = 10.sp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showGuides = false; galleryLauncher.launch("image/*") },
                    colors  = ButtonDefaults.buttonColors(containerColor = MomAmber, contentColor = Color.White),
                    shape   = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Gallery", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuides = false }) {
                    Text("Cancel", color = TxtTertiary, fontSize = 13.sp)
                }
            }
        )
    }

    // ── Upload sheet ──────────────────────────────────────────────────────────
    if (showUploadSheet && croppedUri != null) {
        ModalBottomSheet(
            onDismissRequest = {
                val us = state.uploadState
                if (us !is UploadState.Uploading && us !is UploadState.CheckingImage) {
                    showUploadSheet = false; vm.resetUploadState()
                }
            },
            containerColor = Color.White,
            shape          = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle     = {
                Box(
                    Modifier.padding(vertical = 14.dp)
                        .width(44.dp).height(4.dp)
                        .clip(CircleShape).background(MomAmber.copy(0.35f))
                )
            }
        ) {
            UploadSheet(
                uri         = croppedUri!!,
                caption     = captionText,
                uploadState = state.uploadState,
                postsLeft   = 2 - state.postsToday,
                onCaption   = { captionText = it },
                onPost      = { vm.uploadPost(context, croppedUri!!, captionText) },
                onCancel    = { showUploadSheet = false; vm.resetUploadState() }
            )
        }
    }

    // ── Post options sheet (⋯ menu) ───────────────────────────────────────────
    if (optionsPost != null) {
        ModalBottomSheet(
            onDismissRequest = { optionsPost = null },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    Modifier.padding(vertical = 14.dp)
                        .width(44.dp).height(4.dp)
                        .clip(CircleShape).background(BdActive)
                )
            }
        ) {
            PostOptionsSheet(
                onReport = {
                    reportingPost = optionsPost
                    optionsPost   = null
                },
                onBlock  = {
                    vm.blockUser(optionsUploaderUid)
                    optionsPost   = null
                    blockedToast  = true
                },
                onDismiss = { optionsPost = null }
            )
        }
    }

    // ── Report reason sheet ───────────────────────────────────────────────────
    if (reportingPost != null) {
        ModalBottomSheet(
            onDismissRequest = { reportingPost = null },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ReportSheet(
                onReport = { reason ->
                    val p = reportingPost!!
                    vm.reportPost(p.postId, p.uploaderUid, reason)
                    reportingPost    = null
                    showReportSuccess = true
                    scope.launch {
                        kotlinx.coroutines.delay(2_500)
                        showReportSuccess = false
                    }
                },
                onDismiss = { reportingPost = null }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar — "Moments"
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MomentsTopBar(
    postsToday : Int,
    onBack     : () -> Unit,
    onUpload   : () -> Unit
) {
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = TxtPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Moments",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = TxtPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 19.sp,
                        letterSpacing = 0.3.sp
                    )
                )
                if (postsToday > 0) {
                    Text(
                        "${2 - postsToday} post${if (2 - postsToday == 1) "" else "s"} remaining today",
                        style = MaterialTheme.typography.labelSmall.copy(color = TxtSecond, fontSize = 9.sp)
                    )
                }
            }
            // Camera / add photo button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (postsToday < 2) MomAmber.copy(0.12f) else Color.Transparent)
                    .border(if (postsToday < 2) 1.dp else 0.dp, MomAmber.copy(0.25f), CircleShape)
                    .clickable(enabled = postsToday < 2, onClick = onUpload),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AddAPhoto,
                    null,
                    tint     = if (postsToday < 2) MomAmber else TxtTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(color = BdSubtle, thickness = 0.7.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Per-user section in feed
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MomentUserSection(
    group     : UserPostGroup,
    onHeart   : (AestheticPost) -> Unit,
    onOptions : (AestheticPost) -> Unit
) {
    val pagerState  = rememberPagerState { group.posts.size }
    val currentPost = group.posts.getOrNull(pagerState.currentPage) ?: return

    Column(Modifier.fillMaxWidth()) {

        // ── User header ───────────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BgElevated)
                        .border(1.5.dp, MomAmber.copy(0.30f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (group.uploaderEmoji.isNotBlank()) {
                        Text(group.uploaderEmoji, fontSize = 17.sp)
                    } else {
                        Icon(Icons.Rounded.Person, null, tint = TxtSecond, modifier = Modifier.size(17.dp))
                    }
                }
                Column {
                    Text(
                        group.uploaderAlias.ifBlank { "Anonymous" },
                        style = MaterialTheme.typography.labelLarge.copy(
                            color      = TxtPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                            letterSpacing = 0.1.sp
                        )
                    )
                    Text(
                        "Day ${currentPost.day}  ·  ${relativeTime(currentPost.postedAt)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TxtSecond, fontSize = 10.sp)
                    )
                }
            }

            // ⋯ Options button
            IconButton(
                onClick  = { onOptions(currentPost) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Rounded.MoreVert, null, tint = TxtSecond, modifier = Modifier.size(18.dp))
            }
        }

        // ── Photo pager ───────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth()) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val post = group.posts[page]
                VibeImage(
                    imageUrl = post.imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 540.dp)
                        .pointerInput(post.postId) {
                            detectTapGestures(onDoubleTap = { onHeart(post) })
                        }
                )
            }

            // Photo index badge
            if (group.posts.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(0.50f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${group.posts.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // ── Dot indicators ────────────────────────────────────────────────────
        if (group.posts.size > 1) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                group.posts.forEachIndexed { i, _ ->
                    val active = i == pagerState.currentPage
                    val dotW: Dp by animateDpAsState(
                        targetValue   = if (active) 18.dp else 5.dp,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy),
                        label         = "dot_$i"
                    )
                    Box(
                        Modifier.padding(horizontal = 2.dp)
                            .height(5.dp).width(dotW)
                            .clip(CircleShape)
                            .background(if (active) MomAmber else BdActive)
                    )
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }

        // ── Actions + caption (animates per pager page) ───────────────────────
        AnimatedContent(
            targetState = currentPost,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "postActions"
        ) { post ->
            Column(Modifier.fillMaxWidth()) {
                // Heart row
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val heartScale by animateFloatAsState(
                        targetValue   = if (post.myHearted) 1.3f else 1f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy),
                        label         = "hs"
                    )
                    IconButton(
                        onClick  = { onHeart(post) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (post.myHearted) Icons.Rounded.Favorite
                                          else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like",
                            tint     = if (post.myHearted) HeartRed else TxtTertiary,
                            modifier = Modifier.size(26.dp).scale(heartScale)
                        )
                    }
                    AnimatedContent(post.heartCount, label = "hc") { n ->
                        if (n > 0) Text(
                            "$n",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color      = if (post.myHearted) HeartRed else TxtSecond,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 13.sp
                            )
                        )
                    }
                }

                // Caption
                if (post.caption.isNotBlank()) {
                    Text(
                        text     = post.caption,
                        style    = MaterialTheme.typography.bodySmall.copy(
                            color      = TxtPrimary,
                            fontSize   = 13.sp,
                            lineHeight = 19.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp)
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        HorizontalDivider(color = BdSubtle, thickness = 0.5.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  VibeImage — handles data:image/jpeg;base64,... and https:// URLs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VibeImage(imageUrl: String, modifier: Modifier = Modifier) {
    when {
        imageUrl.startsWith("data:image") -> {
            val bmp = remember(imageUrl) {
                runCatching {
                    val bytes = Base64.decode(imageUrl.substringAfter("base64,"), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            if (bmp != null) {
                Image(
                    bitmap             = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.FillWidth,
                    modifier           = modifier
                )
            } else BrokenBox(modifier)
        }
        imageUrl.isNotBlank() -> AsyncImage(
            model              = imageUrl,
            contentDescription = null,
            contentScale       = ContentScale.FillWidth,
            modifier           = modifier
        )
        else -> BrokenBox(modifier)
    }
}

@Composable
private fun BrokenBox(modifier: Modifier) {
    Box(
        modifier         = modifier.fillMaxWidth().height(260.dp).background(BgElevated),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.BrokenImage, null, tint = MomAmber.copy(0.4f), modifier = Modifier.size(38.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Upload sheet
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UploadSheet(
    uri         : Uri,
    caption     : String,
    uploadState : UploadState,
    postsLeft   : Int,
    onCaption   : (String) -> Unit,
    onPost      : () -> Unit,
    onCancel    : () -> Unit
) {
    val busy = uploadState is UploadState.Uploading || uploadState is UploadState.CheckingImage

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("New Moment", style = MaterialTheme.typography.titleSmall.copy(
                    color = TxtPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp
                ))
                Text(
                    "$postsLeft post${if (postsLeft == 1) "" else "s"} remaining today",
                    style = MaterialTheme.typography.labelSmall.copy(color = TxtTertiary, fontSize = 10.sp)
                )
            }
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape)
                    .background(BgElevated)
                    .border(1.dp, BdActive, CircleShape)
                    .clickable(enabled = !busy) { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, null,
                    tint = if (!busy) TxtSecond else TxtTertiary,
                    modifier = Modifier.size(15.dp))
            }
        }

        // Photo preview — square since we crop 1:1
        AsyncImage(
            model              = uri,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(BgElevated)
        )

        // Caption field
        OutlinedTextField(
            value         = caption,
            onValueChange = { if (it.length <= 200) onCaption(it) },
            placeholder   = {
                Text("Add a caption…", style = MaterialTheme.typography.bodyMedium.copy(
                    color = TxtTertiary, fontStyle = FontStyle.Italic, fontSize = 13.sp
                ))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = MomAmber.copy(0.7f),
                unfocusedBorderColor    = BdActive,
                focusedTextColor        = TxtPrimary,
                unfocusedTextColor      = TxtPrimary,
                cursorColor             = MomAmber,
                focusedContainerColor   = Color.White,
                unfocusedContainerColor = BgElevated
            ),
            maxLines  = 3,
            shape     = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = TxtPrimary),
            modifier  = Modifier.fillMaxWidth(),
            trailingIcon = {
                Text("${caption.length}/200",
                    style    = MaterialTheme.typography.labelSmall.copy(color = TxtTertiary, fontSize = 9.sp),
                    modifier = Modifier.padding(end = 10.dp))
            }
        )

        // Status banner
        AnimatedContent(uploadState, label = "us") { us ->
            when (us) {
                is UploadState.CheckingImage -> StatusBanner(
                    spinning = true,
                    icon     = Icons.Rounded.Shield,
                    msg      = "Scanning image for safety…",
                    color    = TxtSecond
                )
                is UploadState.Uploading -> StatusBanner(
                    spinning = true,
                    icon     = Icons.Rounded.CloudUpload,
                    msg      = "Uploading…",
                    color    = TxtSecond
                )
                is UploadState.Success -> StatusBanner(
                    icon  = Icons.Rounded.CheckCircle,
                    msg   = "Shared to Moments!",
                    color = GreenOk
                )
                is UploadState.Rejected -> StatusBanner(
                    icon  = Icons.Rounded.Block,
                    msg   = us.reason,
                    color = DangerRed
                )
                is UploadState.Failure -> StatusBanner(
                    icon  = Icons.Rounded.ErrorOutline,
                    msg   = us.msg,
                    color = DangerRed
                )
                else -> Spacer(Modifier.height(0.dp))
            }
        }

        // Post button
        Button(
            onClick  = onPost,
            enabled  = !busy && uploadState !is UploadState.Success,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = MomAmber,
                contentColor           = Color.White,
                disabledContainerColor = BgElevated,
                disabledContentColor   = TxtTertiary
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            when {
                busy -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                uploadState is UploadState.Success -> {
                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shared ✦", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                else -> {
                    Icon(Icons.Rounded.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Moment", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Post options sheet (report / block)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PostOptionsSheet(
    onReport  : () -> Unit,
    onBlock   : () -> Unit,
    onDismiss : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        OptionsItem(
            icon  = Icons.Rounded.Flag,
            label = "Report this photo",
            tint  = DangerRed,
            onClick = onReport
        )
        OptionsItem(
            icon  = Icons.Rounded.Block,
            label = "Block this person",
            tint  = DangerRed,
            onClick = onBlock
        )
        HorizontalDivider(color = BdSubtle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        OptionsItem(
            icon  = Icons.Rounded.Close,
            label = "Cancel",
            tint  = TxtSecond,
            onClick = onDismiss
        )
    }
}

@Composable
private fun OptionsItem(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    tint    : Color,
    onClick : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(
            color = tint, fontWeight = FontWeight.Medium, fontSize = 14.sp
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Report reason sheet
// ─────────────────────────────────────────────────────────────────────────────
private val REPORT_REASONS = listOf(
    "Nudity or sexual content",
    "Violence or graphic content",
    "Harassment or bullying",
    "Spam or irrelevant",
    "Doesn't belong on this feed",
    "Other"
)

@Composable
private fun ReportSheet(onReport: (String) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(DangerRed.copy(0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Flag, null, tint = DangerRed, modifier = Modifier.size(15.dp))
            }
            Text("Report photo", style = MaterialTheme.typography.titleSmall.copy(
                color = TxtPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp
            ))
        }
        Text("Why are you reporting this?",
            style = MaterialTheme.typography.bodySmall.copy(color = TxtSecond, fontSize = 12.sp))

        Spacer(Modifier.height(2.dp))

        REPORT_REASONS.forEach { reason ->
            val active = selected == reason
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MomAmber.copy(0.08f) else Color(0xFFFFF9F4))
                    .border(
                        width = if (active) 1.5.dp else 1.dp,
                        color = if (active) MomAmber.copy(0.60f) else BdSubtle,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { selected = reason }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(reason, style = MaterialTheme.typography.bodyMedium.copy(
                    color      = if (active) TxtPrimary else TxtSecond,
                    fontSize   = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                ))
                if (active) Icon(Icons.Rounded.Check, null, tint = MomAmber, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(2.dp))

        Button(
            onClick  = { onReport(selected!!) },
            enabled  = selected != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = DangerRed,
                contentColor           = Color.White,
                disabledContainerColor = BgElevated,
                disabledContentColor   = TxtTertiary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.Report, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Submit Report", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Status banner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatusBanner(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    msg      : String,
    color    : Color,
    spinning : Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(color.copy(0.07f))
            .border(1.dp, color.copy(0.17f), RoundedCornerShape(11.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (spinning) CircularProgressIndicator(color = color, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        else Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(msg, style = MaterialTheme.typography.bodySmall.copy(
            color = color, fontSize = 12.sp, lineHeight = 16.sp
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty onboarding state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyOnboarding(hasPosted: Boolean, onUpload: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            // Camera icon in circle
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MomAmber.copy(0.10f))
                    .border(2.dp, MomAmber.copy(0.30f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PhotoCamera, null, tint = MomAmber, modifier = Modifier.size(36.dp))
            }

            Text(
                if (hasPosted) "Waiting for others…" else "Be the first to post",
                style = MaterialTheme.typography.titleMedium.copy(
                    color      = TxtPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
            )

            if (!hasPosted) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Share a photo that shows your vibe right now. Your group can see it.",
                        style    = MaterialTheme.typography.bodySmall.copy(
                            color      = TxtSecond,
                            textAlign  = TextAlign.Center,
                            lineHeight = 19.sp,
                            fontSize   = 14.sp
                        )
                    )
                    // How it works mini steps
                    HowItWorks()
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick   = onUpload,
                    colors    = ButtonDefaults.buttonColors(containerColor = MomAmber, contentColor = Color.White),
                    shape     = RoundedCornerShape(14.dp),
                    modifier  = Modifier.height(52.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Rounded.AddAPhoto, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Post a Moment", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                Text(
                    "You've posted. When others share, their photos will appear here.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color     = TxtSecond,
                        textAlign = TextAlign.Center,
                        fontSize  = 13.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    val steps = listOf(
        Icons.Rounded.AddAPhoto    to "Tap the camera icon to pick a photo",
        Icons.Rounded.Crop         to "Crop it exactly how you want",
        Icons.Rounded.Send         to "Share — your group sees it instantly"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, BdActive, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        steps.forEachIndexed { i, (icon, text) ->
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(MomAmber.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MomAmber, modifier = Modifier.size(14.dp))
                }
                Text(text, style = MaterialTheme.typography.bodySmall.copy(
                    color = TxtSecond, fontSize = 12.sp, lineHeight = 16.sp
                ))
            }
            if (i < steps.size - 1) {
                HorizontalDivider(color = BdSubtle, thickness = 0.5.dp, modifier = Modifier.padding(start = 42.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Floating post button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PostFab(postsLeft: Int, onClick: () -> Unit) {
    Button(
        onClick   = onClick,
        shape     = RoundedCornerShape(28.dp),
        colors    = ButtonDefaults.buttonColors(containerColor = AccentWhite, contentColor = Color.Black),
        modifier  = Modifier.height(50.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp, pressedElevation = 6.dp)
    ) {
        Icon(Icons.Rounded.AddAPhoto, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (postsLeft == 2) "Post a Moment" else "Post  ·  1 left",
            fontWeight = FontWeight.Bold,
            fontSize   = 14.sp,
            letterSpacing = 0.2.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Loading / Error
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = MomAmber, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            Text("Loading Moments…", style = MaterialTheme.typography.labelSmall.copy(color = TxtSecond))
        }
    }
}

@Composable
private fun ErrorView(msg: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.WifiOff, null, tint = TxtTertiary, modifier = Modifier.size(32.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall.copy(
                color = DangerRed, textAlign = TextAlign.Center, fontSize = 12.sp
            ))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GuideRow(allowed: Boolean, text: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape)
                .background(if (allowed) GreenOk.copy(0.12f) else DangerRed.copy(0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (allowed) Icons.Rounded.Check else Icons.Rounded.Close,
                null,
                tint     = if (allowed) GreenOk else DangerRed,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(text, style = MaterialTheme.typography.bodySmall.copy(
            color = TxtPrimary, fontSize = 12.sp
        ))
    }
}

private fun relativeTime(epochSeconds: Long): String {
    if (epochSeconds == 0L) return ""
    return try {
        val diff = System.currentTimeMillis() / 1000 - epochSeconds
        when {
            diff < 60    -> "just now"
            diff < 3600  -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochSeconds * 1000))
        }
    } catch (_: Exception) { "" }
}
