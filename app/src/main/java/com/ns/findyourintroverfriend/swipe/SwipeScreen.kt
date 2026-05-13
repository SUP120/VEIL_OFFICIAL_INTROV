@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ns.findyourintroverfriend.swipe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.abs
import com.airbnb.lottie.compose.*
import androidx.compose.ui.res.painterResource
import com.ns.findyourintroverfriend.R


// Veil refined theme - calm, professional, introvert-friendly
private val SBg         = Color(0xFFFAFAFA)   // soft off-white
private val SCard       = Color(0xFFFFFFFF)   // pure white
private val SCardBorder = Color(0xFFE8E8E8)   // subtle gray border
private val SPrimary    = Color(0xFF1F1F1F)   // soft black for text
private val SSecondary  = Color(0xFF6B6B6B)   // medium gray
private val SMuted      = Color(0xFF9E9E9E)   // light gray
private val SAccent     = Color(0xFF6366F1)   // refined indigo
private val SAccentSoft = Color(0xFFF0F0FF)   // very light indigo
private val SLike       = Color(0xFF10B981)   // emerald green
private val SPass       = Color(0xFFEF4444)   // clean red
private val SGold       = Color(0xFFF59E0B)   // amber
private val SGoldSoft   = Color(0xFFFFF7ED)   // light amber
private val SUndo       = Color(0xFF3B82F6)   // blue
private val SSuperLike  = Color(0xFF8B5CF6)   // purple star

// ── Top-level entry: called by VeilNavGraph ──────────────────────────────────
@Composable
fun SwipeScreen(
    onOpenMatch       : (matchId: String) -> Unit = {},
    onNeedProfile     : () -> Unit                = {},
    onBack            : () -> Unit                = {},
    onNavigateToMatches: () -> Unit               = {}
) {
    SwipeContent(
        onOpenMatch         = onOpenMatch,
        onNavigateToMatches = onNavigateToMatches
    )
}

// SwipeContent - embedded inside VeilHomeScreen (nav bar always visible)
@Composable
fun SwipeContent(
    vm                : SwipeViewModel = viewModel(),
    modifier          : Modifier = Modifier,
    onOpenMatch       : (matchId: String) -> Unit = {},
    onNavigateToMatches : () -> Unit = {}
) {
    val feed            by vm.feed.collectAsStateWithLifecycle()
    val match           by vm.match.collectAsStateWithLifecycle()
    val loading         by vm.loading.collectAsStateWithLifecycle()
    val swipesLeft      by vm.swipesLeft.collectAsStateWithLifecycle()
    val complimentsLeft by vm.complimentsLeft.collectAsStateWithLifecycle()
    val cookieBalance   by vm.cookieBalance.collectAsStateWithLifecycle()
    val complimentResult by vm.complimentResult.collectAsStateWithLifecycle()
    val myProfile       by vm.myProfile.collectAsStateWithLifecycle()

    var activeDragX       by remember { mutableFloatStateOf(0f) }
    var isEditingProfile  by remember { mutableStateOf(false) }
    var isCreatingProfile by remember { mutableStateOf(false) }
    var showComplimentDialog by remember { mutableStateOf(false) }
    var complimentTarget  by remember { mutableStateOf<SwipeProfile?>(null) }
    var showNoCookiesAlert by remember { mutableStateOf(false) }

    // React to compliment result from VM
    LaunchedEffect(complimentResult) {
        when (complimentResult) {
            false -> { showNoCookiesAlert = true; vm.clearComplimentResult() }
            true  -> { vm.clearComplimentResult() }
            null  -> {}
        }
    }

    LaunchedEffect(vm.profileSaved) {
        if (vm.profileSaved) { isEditingProfile = false; isCreatingProfile = false }
    }

    LaunchedEffect(match) {
        match?.let {
            if (!vm.isDemoMatch) { onOpenMatch(it.matchId); vm.clearMatch() }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        // Profile creation/edit shown as full-screen overlay — shown before feed
        if (isCreatingProfile || isEditingProfile) {
            SwipeSetupInline(
                vm = vm,
                isEditing = isEditingProfile,
                onBack = { isEditingProfile = false; isCreatingProfile = false }
            )
            return@Box
        }

        when {
            loading -> SwipeSkeletonLoader()
            else -> Column(Modifier.fillMaxSize()) {
                // ── Gamified Header ──
                val pulseInf = rememberInfiniteTransition(label = "hp")
                val headerPulse by pulseInf.animateFloat(1f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), "hps")
                val hapticH = LocalHapticFeedback.current
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color.White)
                        .border(BorderStroke(0.5.dp, SCardBorder))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Logo + live swipe counter pill
                    Column {
                        Text("Discover", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SPrimary, letterSpacing = (-0.8).sp)
                        if (swipesLeft > 0) {
                            Row(
                                Modifier.scale(headerPulse)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SAccentSoft)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(SLike))
                                Text("$swipesLeft swipes left", fontSize = 10.sp, color = SAccent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    // Right: Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(42.dp).shadow(4.dp, CircleShape, spotColor = SAccent.copy(0.12f))
                                .clip(CircleShape).background(Color.White)
                                .border(1.dp, SCardBorder, CircleShape)
                                .clickable { hapticH.performHapticFeedback(HapticFeedbackType.LongPress); onNavigateToMatches() },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(painterResource(R.drawable.ic_swipe_connection), null, modifier = Modifier.size(22.dp))
                        }
                        Box(
                            Modifier.size(42.dp).shadow(4.dp, CircleShape, spotColor = SAccent.copy(0.12f))
                                .clip(CircleShape).background(Color.White)
                                .border(1.dp, SCardBorder, CircleShape)
                                .clickable {
                                    hapticH.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (myProfile != null) { vm.loadProfileIntoForm(); isEditingProfile = true } else isCreatingProfile = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(painterResource(R.drawable.ic_swipe_profile), null, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                // ── Content ──
                when {
                    swipesLeft <= 0 -> SwipeLimitState()
                    feed.isEmpty()  -> SwipeEmptyState(vm)
                    else -> {
                        // ── Card stack ──
                        Box(
                            Modifier.weight(1f).fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            feed.take(3).asReversed().forEachIndexed { revIdx, profile ->
                                val stackIdx = feed.take(3).size - 1 - revIdx
                                SwipeCard(
                                    profile      = profile,
                                    isTopCard    = stackIdx == 0,
                                    stackDepth   = stackIdx,
                                    onSwipeRight = {
                                        if (myProfile == null) isCreatingProfile = true
                                        else vm.swipeRight(profile)
                                    },
                                    onSwipeLeft  = {
                                        if (myProfile == null) isCreatingProfile = true
                                        else vm.swipeLeft(profile)
                                    },
                                    onDragChange = { x -> activeDragX = x },
                                    onDragEnd    = { activeDragX = 0f }
                                )
                            }
                        }
                        // ── Gamified Action Buttons ──
                        val hapticBtn = LocalHapticFeedback.current
                        var passPressed   by remember { mutableStateOf(false) }
                        var likePressed   by remember { mutableStateOf(false) }
                        var starPressed   by remember { mutableStateOf(false) }
                        val passScale by animateFloatAsState(if (passPressed) 0.82f else 1f, spring(Spring.DampingRatioMediumBouncy), label="ps")
                        val likeScale by animateFloatAsState(if (likePressed) 0.82f else 1f, spring(Spring.DampingRatioMediumBouncy), label="ls")
                        val starScale by animateFloatAsState(if (starPressed) 0.82f else 1f, spring(Spring.DampingRatioMediumBouncy), label="ss")
                        LaunchedEffect(passPressed) { if (passPressed) { kotlinx.coroutines.delay(120); passPressed = false } }
                        LaunchedEffect(likePressed) { if (likePressed) { kotlinx.coroutines.delay(120); likePressed = false } }
                        LaunchedEffect(starPressed) { if (starPressed) { kotlinx.coroutines.delay(120); starPressed = false } }
                        val starInf = rememberInfiniteTransition(label="sg")
                        val starGlow by starInf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), "sgv")
                        Row(
                            Modifier.fillMaxWidth().background(Color.White)
                                .border(BorderStroke(0.5.dp, SCardBorder))
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Undo
                            VeilBtn(size = 52.dp, bg = Color.White, shadow = SUndo.copy(0.10f), borderColor = SCardBorder, onClick = {}) {
                                Icon(Icons.Rounded.Undo, null, tint = SUndo.copy(0.6f), modifier = Modifier.size(22.dp))
                            }
                            // Pass
                            Box(
                                Modifier.size(62.dp).scale(passScale)
                                    .shadow(8.dp, CircleShape, spotColor = SPass.copy(0.25f))
                                    .clip(CircleShape).background(Color.White)
                                    .border(2.dp, SPass.copy(0.4f), CircleShape)
                                    .clickable {
                                        passPressed = true
                                        hapticBtn.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (myProfile == null) isCreatingProfile = true
                                        else feed.firstOrNull()?.let { vm.swipeLeft(it) }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Close, null, tint = SPass, modifier = Modifier.size(28.dp)) }
                            // Super like — shimmer streak + pulsing glow
                            val shimInf = rememberInfiniteTransition(label = "starShim")
                            // Diagonal shimmer X position sweeping across button
                            val shimSweep by shimInf.animateFloat(
                                -120f, 220f,
                                infiniteRepeatable(
                                    tween(1600, easing = FastOutSlowInEasing),
                                    RepeatMode.Restart
                                ), "shimSweep"
                            )
                            // Rotating outer ring
                            val ringRot by shimInf.animateFloat(
                                0f, 360f,
                                infiniteRepeatable(tween(3000, easing = LinearEasing)),
                                "ringRot"
                            )
                            // Shimmer brush — white diagonal streak
                            val shimmerBrush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.55f),
                                    Color.Transparent
                                ),
                                start = androidx.compose.ui.geometry.Offset(shimSweep - 40f, 0f),
                                end   = androidx.compose.ui.geometry.Offset(shimSweep + 40f, 120f)
                            )
                            Box(
                                Modifier.size(74.dp).scale(starScale),
                                contentAlignment = Alignment.Center
                            ) {
                                // Rotating dashed-glow ring behind button
                                Box(
                                    Modifier
                                        .size(74.dp)
                                        .rotate(ringRot)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            brush = Brush.sweepGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color(0xFFD8B4FE),
                                                    Color.Transparent,
                                                    Color(0xFF7C3AED),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )
                                // Main button body
                                Box(
                                    Modifier
                                        .size(70.dp)
                                        .shadow(
                                            elevation = (12 + (starGlow * 8).toInt()).dp,
                                            shape = CircleShape,
                                            spotColor = SSuperLike.copy(starGlow * 0.7f)
                                        )
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(Color(0xFF9F67F7), SSuperLike, Color(0xFF5B21B6))
                                            )
                                        )
                                        .border(1.5.dp, Color.White.copy(0.25f), CircleShape)
                                        .clickable {
                                            starPressed = true
                                            hapticBtn.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (myProfile == null) isCreatingProfile = true
                                            else feed.firstOrNull()?.let { p -> complimentTarget = p; showComplimentDialog = true }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                    // Shimmer streak overlay
                                    Box(Modifier.fillMaxSize().background(shimmerBrush))
                                }
                            }
                            // Like
                            Box(
                                Modifier.size(62.dp).scale(likeScale)
                                    .shadow(8.dp, CircleShape, spotColor = SLike.copy(0.25f))
                                    .clip(CircleShape).background(Color.White)
                                    .border(2.dp, SLike.copy(0.4f), CircleShape)
                                    .clickable {
                                        likePressed = true
                                        hapticBtn.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (myProfile == null) isCreatingProfile = true
                                        else feed.firstOrNull()?.let { vm.swipeRight(it) }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Favorite, null, tint = SLike, modifier = Modifier.size(28.dp)) }
                        }
                    }
                }
            }
        }
        if (match != null && vm.isDemoMatch) {
            MatchOverlay(match = match!!, onDismiss = { vm.clearDemoMatch() })
        }
        
        // Compliment Dialog
        if (showComplimentDialog && complimentTarget != null) {
            ComplimentDialog(
                profile       = complimentTarget!!,
                cookieBalance = cookieBalance,
                onDismiss = { 
                    showComplimentDialog = false
                    complimentTarget = null
                },
                onSend = { message ->
                    vm.sendCompliment(complimentTarget!!, message)
                    showComplimentDialog = false
                    complimentTarget = null
                }
            )
        }

        // Not enough cookies alert
        if (showNoCookiesAlert) {
            NoCookiesAlert(onDismiss = { showNoCookiesAlert = false })
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Inline Profile Setup (no nav, same VM, shown once until saved)
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeSetupInline(vm: SwipeViewModel, isEditing: Boolean = false, onBack: () -> Unit = {}) {
    val scroll = rememberScrollState()
    var showCountryPicker by remember { mutableStateOf(false) }
    var showStatePicker   by remember { mutableStateOf(false) }
    val stateList = if (vm.country.isNotBlank()) (COUNTRY_STATES[vm.country] ?: emptyList()) else emptyList()

    // ── Auto-populate form when editing: copy saved profile into form vars ──
    // Without this, isCameraShy / avatarResName / all fields reset to defaults
    // every time the app restarts because loadMyProfile() only sets _myProfile
    // StateFlow but never copies values into the mutable form state variables.
    val myProfile by vm.myProfile.collectAsStateWithLifecycle()
    LaunchedEffect(myProfile) {
        if (myProfile != null) vm.loadProfileIntoForm()
    }

    // Root Box so pickers can overlay the form
    Box(Modifier.fillMaxSize().background(SBg)) {

        // Scrollable form content
        Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {

            // ── Hero Banner (identity + avatar) ──
            Box(
                Modifier.fillMaxWidth().height(200.dp)
                    .background(Color.White)
            ) {
                // Back button
                if (isEditing) {
                    Box(
                        Modifier.padding(16.dp).clip(CircleShape)
                            .background(SCard).border(0.8.dp, SCardBorder, CircleShape)
                            .clickable { onBack() }.padding(10.dp)
                    ) { Icon(Icons.Rounded.ArrowBack, null, tint = SPrimary, modifier = Modifier.size(18.dp)) }
                }

                // Large centered avatar circle
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar: photo if available, camera shy avatar, or initials
                    Box(
                        Modifier.size(88.dp)
                            .shadow(4.dp, CircleShape, spotColor = SAccent.copy(0.10f))
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(SAccent.copy(0.85f), SAccent.copy(0.65f)))
                            )
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            // Show photo if available and not camera shy
                            vm.gamePhotoUrl.isNotBlank() && !vm.isCameraShy -> {
                                AsyncImage(
                                    model = vm.gamePhotoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Show selected avatar if camera shy
                            vm.isCameraShy && vm.avatarResName.isNotBlank() -> {
                                val ctx = LocalContext.current
                                val avatarResId = ctx.resources.getIdentifier(
                                    vm.avatarResName, "drawable", ctx.packageName
                                )
                                if (avatarResId != 0) {
                                    Image(
                                        painter = painterResource(avatarResId),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    // Fallback to initials if avatar not found
                                    Text(
                                        text = vm.gameAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            // Fallback: show initials
                            else -> {
                                Text(
                                    text = vm.gameAlias.firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    // Name + age
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            vm.gameAlias.ifBlank { "Your Name" },
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SPrimary
                        )
                        if (vm.gameAge > 0) Text(
                            "${vm.gameAge}",
                            fontSize = 14.sp, color = SSecondary
                        )
                    }
                    // Auto-filled tag
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(SAccentSoft)
                            .border(0.7.dp, SAccent.copy(0.20f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (isEditing) "Editing your profile" else "Setting up your profile",
                            fontSize = 10.sp, color = SAccent, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Form Fields ──
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // -- Location --
                SFormLabel("Where are you based?")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(14.dp)).background(SCard)
                            .border(0.8.dp, if (vm.country.isNotBlank()) SAccent.copy(0.50f) else SCardBorder, RoundedCornerShape(14.dp))
                            .clickable { showCountryPicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Country", fontSize = 10.sp, color = CMuted, fontWeight = FontWeight.Medium)
                            Text(
                                vm.country.ifBlank { "Select country" },
                                fontSize = 13.sp,
                                color = if (vm.country.isBlank()) CMuted else SPrimary,
                                fontWeight = if (vm.country.isBlank()) FontWeight.Normal else FontWeight.SemiBold
                            )
                        }
                    }
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (stateList.isEmpty()) Color(0xFFF5F2EE) else SCard)
                            .border(0.8.dp, if (vm.state.isNotBlank()) SAccent.copy(0.50f) else SCardBorder, RoundedCornerShape(14.dp))
                            .clickable(enabled = stateList.isNotEmpty()) { showStatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("State / Region", fontSize = 10.sp, color = CMuted, fontWeight = FontWeight.Medium)
                            Text(
                                vm.state.ifBlank { if (stateList.isEmpty()) "Pick country first" else "Select" },
                                fontSize = 13.sp,
                                color = if (vm.state.isBlank()) CMuted else SPrimary,
                                fontWeight = if (vm.state.isBlank()) FontWeight.Normal else FontWeight.SemiBold
                            )
                        }
                    }
                }

                HorizontalDivider(color = SCardBorder, thickness = 0.6.dp)
                SFormLabel("About you")

                SField("I feel most comfortable when...", vm.comfortWhen, { vm.comfortWhen = it }, "e.g. in a quiet corner with no pressure to talk")
                SField("A perfect evening looks like...", vm.perfectEvening, { vm.perfectEvening = it }, "e.g. Hot tea, a good book, rain outside")
                SField("I open up when...", vm.openUpWhen, { vm.openUpWhen = it }, "e.g. Someone asks what I am genuinely passionate about")
                SField("Things I tend to avoid...", vm.thingsIAvoid, { vm.thingsIAvoid = it }, "e.g. Loud parties, phone calls from unknown numbers", required = false)

                HorizontalDivider(color = SCardBorder, thickness = 0.6.dp)
                SFormLabel("Your energy style")

                SToggle("I prefer", "Quiet places", "quiet", "Social places", "social", vm.placePreference) { vm.placePreference = it }
                SToggle("I prefer to", "Text", "text", "Call", "call", vm.commStyle) { vm.commStyle = it }
                SToggle("I am a", "Night owl", "nightowl", "Early bird", "earlybird", vm.sleepStyle) { vm.sleepStyle = it }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(SCard).border(0.8.dp, SCardBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Camera shy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SPrimary)
                        Text("Show a gradient avatar instead of your photo", fontSize = 11.sp, color = SSecondary)
                    }
                    Switch(
                        checked = vm.isCameraShy, onCheckedChange = { vm.isCameraShy = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = SAccent,
                            uncheckedTrackColor = SCardBorder, uncheckedThumbColor = Color.White
                        )
                    )
                }

                AnimatedVisibility(
                    visible = vm.isCameraShy,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SCard)
                            .border(0.8.dp, SAccent.copy(0.30f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Choose your avatar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SPrimary
                        )
                        Text(
                            "This will appear on your swipe card instead of your photo",
                            fontSize = 11.sp,
                            color = SSecondary
                        )
                        val avatarList = if (vm.gameGender.lowercase() == "female")
                            listOf("avatarg1","avatarg2","avatarg3","avatarg4","avatarg5")
                        else
                            listOf("avatarb1","avatarb2","avatarb3","avatarb4","avatarb5")

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (idx in avatarList.indices) {
                                val resName = avatarList[idx]
                                val resId = when (resName) {
                                    "avatarb1" -> R.drawable.avatarb1
                                    "avatarb2" -> R.drawable.avatarb2
                                    "avatarb3" -> R.drawable.avatarb3
                                    "avatarb4" -> R.drawable.avatarb4
                                    "avatarb5" -> R.drawable.avatarb5
                                    "avatarg1" -> R.drawable.avatarg1
                                    "avatarg2" -> R.drawable.avatarg2
                                    "avatarg3" -> R.drawable.avatarg3
                                    "avatarg4" -> R.drawable.avatarg4
                                    "avatarg5" -> R.drawable.avatarg5
                                    else       -> 0
                                }
                                val isSelected = vm.avatarResName == resName
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) SAccent else SCardBorder,
                                            shape = CircleShape
                                        )
                                        .clickable { vm.avatarResName = resName }
                                ) {
                                    if (resId != 0) {
                                        Image(
                                            painter = painterResource(resId),
                                            contentDescription = resName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            Modifier.fillMaxSize().background(SAccent.copy(0.18f)),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Box(
                                                Modifier.fillMaxWidth().background(SAccent.copy(0.75f)).padding(2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Selected", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = SCardBorder, thickness = 0.6.dp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SFormLabel("Your vibe tags")
                    Text("Pick up to 5 that describe you", fontSize = 11.sp, color = CMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        INTROVERT_TAGS.forEach { tag ->
                            val sel = tag in vm.selectedIntrovertTags
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) SAccentSoft else SCard)
                                    .border(0.8.dp, if (sel) SAccent else SCardBorder, RoundedCornerShape(20.dp))
                                    .clickable { vm.toggleIntrovertTag(tag) }
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
                            ) {
                                Text(tag, fontSize = 12.sp,
                                    color = if (sel) SAccent else SSecondary,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }

                HorizontalDivider(color = SCardBorder, thickness = 0.6.dp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SFormLabel("What are you into?")
                    Text("Pick up to 5", fontSize = 11.sp, color = CMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HOBBY_OPTIONS.forEach { h ->
                            val sel = h in vm.selectedHobbies
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) SAccentSoft else SCard)
                                    .border(0.8.dp, if (sel) SAccent else SCardBorder, RoundedCornerShape(20.dp))
                                    .clickable { vm.toggleHobby(h) }
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
                            ) {
                                Text(h, fontSize = 12.sp,
                                    color = if (sel) SAccent else SSecondary,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }

                HorizontalDivider(color = SCardBorder, thickness = 0.6.dp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SFormLabel("When are you usually free?")
                    Text("Select all that apply", fontSize = 11.sp, color = CMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TIME_SLOTS.forEach { slot ->
                            val sel = slot in vm.selectedTimeSlots
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) SGoldSoft else SCard)
                                    .border(0.8.dp, if (sel) SGold else SCardBorder, RoundedCornerShape(20.dp))
                                    .clickable { vm.toggleTimeSlot(slot) }
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
                            ) {
                                Text(slot, fontSize = 12.sp,
                                    color = if (sel) SGold else SSecondary,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }

                SField("Available tonight? (optional)", vm.availableTonight, { vm.availableTonight = it }, "e.g. 8-10 PM", required = false)

                HorizontalDivider(color = SCardBorder, thickness = 0.6.dp)
                SFormLabel("Conversation starters")
                SField("Ask me about...", vm.askMeAbout, { vm.askMeAbout = it }, "e.g. indie films, lucid dreaming, why I prefer cats", required = false)
                SField("I can talk for hours about...", vm.talkHoursAbout, { vm.talkHoursAbout = it }, "e.g. philosophy, music theory, random history facts", required = false)

                vm.saveError?.let { err -> Text(err, fontSize = 12.sp, color = SPass) }

                Button(
                    onClick = { vm.saveSwipeProfile() },
                    enabled = vm.canSaveProfile && !vm.isSavingProfile,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                        .shadow(if (vm.canSaveProfile) 8.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = SAccent.copy(0.25f)),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SAccent, disabledContainerColor = SCardBorder)
                ) {
                    if (vm.isSavingProfile) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (isEditing) "Save changes" else "Create my profile",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // ── Picker overlays (rendered on top of the scrollable form) ──
        if (showCountryPicker) {
            PickerSheet(
                title    = "Select your country",
                items    = ALL_COUNTRIES,
                onSelect = { vm.country = it; vm.state = ""; showCountryPicker = false },
                onDismiss = { showCountryPicker = false }
            )
        }
        if (showStatePicker) {
            PickerSheet(
                title    = "Select state / region",
                items    = stateList,
                onSelect = { vm.state = it; showStatePicker = false },
                onDismiss = { showStatePicker = false }
            )
        }
    }
}

private val CMuted = SMuted

// ──────────────────────────────────────────────────────────────────────────────
//  Swipe Card
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SField(label: String, value: String, onValue: (String) -> Unit, placeholder: String, required: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SPrimary)
            if (!required) Text("optional", fontSize = 10.sp, color = CMuted)
        }
        OutlinedTextField(
            value = value, onValueChange = { if (it.length <= 200) onValue(it) },
            placeholder = { Text(placeholder, fontSize = 13.sp, color = CMuted) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SAccent.copy(0.60f),
                unfocusedBorderColor = SCardBorder,
                focusedContainerColor = SCard, unfocusedContainerColor = SCard
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = SPrimary)
        )
    }
}

@Composable
private fun SwipeCard(
    profile      : SwipeProfile,
    isTopCard    : Boolean,
    stackDepth   : Int,
    onSwipeRight : () -> Unit,
    onSwipeLeft  : () -> Unit,
    onDragChange : (Float) -> Unit = {},
    onDragEnd    : () -> Unit = {}
) {
    val haptic  = LocalHapticFeedback.current
    val density = LocalDensity.current
    val config  = LocalConfiguration.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val scroll  = rememberScrollState()

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Smooth spring animations for card movement
    val animX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardX"
    )
    val animY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardY"
    )
    
    // Professional rotation based on swipe
    val rotate = (animX / screenW) * 15f
    
    // Stamp opacity based on swipe distance
    val stampAlpha = (kotlin.math.abs(animX) / (screenW * 0.25f)).coerceIn(0f, 1f)
    
    // Stack effect - cards behind are slightly smaller and lower
    val scale = if (!isTopCard) (1f - stackDepth * 0.04f) else 1f
    val yOffset = if (!isTopCard) (stackDepth * 16).dp else 0.dp
    val cardAlpha = if (!isTopCard) (1f - stackDepth * 0.15f) else 1f
    
    val gradientPair = remember(profile.uid) { veilGradient(profile.uid) }

    Box(
        Modifier
            .fillMaxWidth(0.92f)
            .offset(y = yOffset)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = cardAlpha
            }
            .zIndex(if (isTopCard) 10f else (5f - stackDepth))
            .shadow(
                elevation = if (isTopCard) 16.dp else 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(0.12f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(SCard)
            .border(1.5.dp, SCardBorder, RoundedCornerShape(24.dp))
            .then(
                if (isTopCard) {
                    Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    // Swipe threshold - 35% of screen width
                                    if (kotlin.math.abs(offsetX) > screenW * 0.35f) {
                                        if (offsetX > 0) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSwipeRight()
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSwipeLeft()
                                        }
                                    }
                                    offsetX = 0f
                                    offsetY = 0f
                                    onDragEnd()
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    offsetX += drag.x
                                    offsetY += drag.y * 0.3f // Slight vertical movement
                                    onDragChange(offsetX)
                                }
                            )
                        }
                        .graphicsLayer {
                            translationX = animX
                            translationY = animY
                            rotationZ = rotate
                        }
                } else {
                    Modifier
                }
            )
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(if (isTopCard) scroll else rememberScrollState())) {

            // ── PHOTO / AVATAR HERO ──────────────────────────────────────
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                // Determine what to show with explicit fallback chain
                val showPhoto = profile.photoUrl.isNotBlank() && !profile.isCameraShy
                val showAvatar = profile.isCameraShy && profile.avatarResName.isNotBlank()
                
                when {
                    showPhoto -> {
                        // Show real photo
                        AsyncImage(
                            model = profile.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = {
                                android.util.Log.e("SwipeCard", "Failed to load photo for ${profile.alias}: ${profile.photoUrl}")
                            }
                        )
                    }
                    showAvatar -> {
                        // Show selected avatar using getIdentifier (same as MAvatar)
                        val ctx = LocalContext.current
                        val avatarResId = ctx.resources.getIdentifier(
                            profile.avatarResName, "drawable", ctx.packageName
                        )
                        if (avatarResId != 0) {
                            Image(
                                painter = painterResource(avatarResId),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // "Camera Shy" badge
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(16.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(0.40f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Camera shy", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            // Avatar resource not found - show gradient
                            Box(Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(gradientPair.first, gradientPair.second))
                            ))
                            Box(Modifier.align(Alignment.Center)) {
                                Box(Modifier.size(100.dp).clip(CircleShape)
                                    .background(Color.White.copy(0.20f))
                                    .border(3.dp, Color.White.copy(0.45f), CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    Text(profile.alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                    else -> {
                        // Fallback: gradient with initials
                        Box(Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(gradientPair.first, gradientPair.second))
                        ))
                        Box(Modifier.align(Alignment.Center)) {
                            Box(Modifier.size(100.dp).clip(CircleShape)
                                .background(Color.White.copy(0.20f))
                                .border(3.dp, Color.White.copy(0.45f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(profile.alias.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // Professional LIKE / NOPE stamps with better styling
                if (isTopCard) {
                    // LIKE stamp (top left)
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(24.dp)
                            .rotate(-20f)
                            .alpha(if (animX > 0) stampAlpha else 0f)
                            .scale(1f + stampAlpha * 0.1f)
                            .border(4.dp, SLike, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "LIKE",
                            color = SLike,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            letterSpacing = 3.sp
                        )
                    }
                    
                    // NOPE stamp (top right)
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .rotate(20f)
                            .alpha(if (animX < 0) stampAlpha else 0f)
                            .scale(1f + stampAlpha * 0.1f)
                            .border(4.dp, SPass, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "NOPE",
                            color = SPass,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            letterSpacing = 3.sp
                        )
                    }
                }

                // Bottom gradient overlay - smoother
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(0.75f)
                                )
                            )
                        )
                )

                // Name + info overlay at bottom of photo
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            profile.alias,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        if (profile.age > 0) {
                            Text(
                                "${profile.age}",
                                fontSize = 24.sp,
                                color = Color.White.copy(0.90f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(SAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    
                    val loc = listOf(profile.state, profile.country).filter { it.isNotBlank() }.joinToString(", ")
                    if (loc.isNotBlank()) {
                        Row(
                            Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.LocationOn,
                                null,
                                tint = Color.White.copy(0.75f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                loc,
                                fontSize = 13.sp,
                                color = Color.White.copy(0.75f)
                            )
                        }
                    }
                    
                    if (profile.availableTonight.isNotBlank()) {
                        Row(
                            Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Text(
                                "Active tonight",
                                fontSize = 12.sp,
                                color = Color.White.copy(0.88f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Personality - clean, airy layout
            Column(
                Modifier.fillMaxWidth().background(Color.White).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (profile.comfortWhen.isNotBlank())
                    LightInfoSection(emoji = "🛡", label = "COMFORTABLE WHEN", text = profile.comfortWhen)
                if (profile.perfectEvening.isNotBlank())
                    LightInfoSection(emoji = "🌙", label = "PERFECT EVENING", text = profile.perfectEvening)
                if (profile.openUpWhen.isNotBlank())
                    LightInfoSection(emoji = "💡", label = "OPENS UP WHEN", text = profile.openUpWhen)
                if (profile.thingsIAvoid.isNotBlank())
                    LightInfoSection(emoji = "🚫", label = "AVOIDS", text = profile.thingsIAvoid)

                if (profile.askMeAbout.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(SAccentSoft)
                            .border(1.dp, SAccent.copy(0.20f), RoundedCornerShape(16.dp))
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("◎  ASK ME ABOUT", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = SAccent, letterSpacing = 1.sp)
                            Text("\"${profile.askMeAbout}\"", fontSize = 14.sp,
                                color = SPrimary, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
                        }
                    }
                }

                if (profile.talkHoursAbout.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(SGoldSoft)
                            .border(1.dp, SGold.copy(0.20f), RoundedCornerShape(16.dp))
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✨  HOURS ON TOPIC", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = SGold.copy(0.85f), letterSpacing = 1.sp)
                            Text(profile.talkHoursAbout, fontSize = 14.sp,
                                color = SPrimary, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
                        }
                    }
                }

                if (profile.introvertTags.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("VIBE TAGS", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = SSecondary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        profile.introvertTags.forEach { tag ->
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(SAccentSoft)
                                    .border(1.dp, SAccent.copy(0.25f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text(tag, fontSize = 13.sp, color = SAccent, fontWeight = FontWeight.Medium) }
                        }
                    }
                }

                if (profile.hobbies.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("INTO", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = SSecondary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        profile.hobbies.forEach { h ->
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(SBg)
                                    .border(1.dp, SCardBorder, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text(h, fontSize = 13.sp, color = SSecondary, fontWeight = FontWeight.Normal) }
                        }
                    }
                }

                if (profile.preferredTimeSlots.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("FREE TO CHAT", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = SSecondary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until minOf(profile.preferredTimeSlots.size, 4)) {
                            Box(
                                Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(SBg)
                                    .border(1.dp, SCardBorder, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) { Text(profile.preferredTimeSlots[i], fontSize = 12.sp, color = SPrimary, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LightInfoSection(emoji: String, label: String, text: String) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(18.dp))
        Text("$emoji  $label", fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = SSecondary, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Text(text, fontSize = 14.sp, color = SPrimary,
            lineHeight = 22.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = SCardBorder, thickness = 1.dp)
    }
}

@Composable
private fun SFormLabel(label: String) {
    Text(
        text  = label,
        fontSize   = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color      = SSecondary,
        letterSpacing = 0.3.sp
    )
}

@Composable
private fun SToggle(
    label   : String,
    optA    : String,  valA: String,
    optB    : String,  valB: String,
    current : String,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = SSecondary, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(optA to valA, optB to valB).forEach { (opt, v) ->
                val sel = current == v
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) SAccent else SBg)
                        .border(0.8.dp, if (sel) SAccent else SCardBorder, RoundedCornerShape(20.dp))
                        .clickable { onChange(v) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(opt, fontSize = 13.sp, color = if (sel) Color.White else SPrimary,
                         fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun VeilBtn(size: Dp, bg: Color, shadow: Color, borderColor: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size)
            .shadow(6.dp, CircleShape, spotColor = shadow)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Generates a unique warm gradient pair from a uid hash — consistent per person */
private fun veilGradient(uid: String): Pair<Color, Color> {
    val palettes = listOf(
        Pair(Color(0xFF6B4E8C), Color(0xFFA07BB5)),
        Pair(Color(0xFF2D6A4F), Color(0xFF74C69D)),
        Pair(Color(0xFF9B4444), Color(0xFFE07A5F)),
        Pair(Color(0xFF1B4080), Color(0xFF457BC7)),
        Pair(Color(0xFF5C4A1E), Color(0xFFB8965E)),
        Pair(Color(0xFF2F4858), Color(0xFF6B8FA8)),
        Pair(Color(0xFF6B2D6A), Color(0xFFB07BAF)),
        Pair(Color(0xFF3D405B), Color(0xFF8185A8)),
    )
    return palettes[Math.abs(uid.hashCode()) % palettes.size]
}

@Composable private fun SwipeEmptyState(vm: SwipeViewModel) {
    val inf = rememberInfiniteTransition(label = "es")
    val sc by inf.animateFloat(0.92f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "esc")
    val glow by inf.animateFloat(0.15f, 0.35f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), "esg")
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(90.dp).scale(sc)
                .shadow(16.dp, CircleShape, spotColor = SAccent.copy(glow))
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(SAccentSoft, Color.White)))
                .border(2.dp, SAccent.copy(0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.SearchOff, null, tint = SAccent, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("All caught up", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SPrimary, textAlign = TextAlign.Center, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(8.dp))
        Text("No new profiles right now.\nCheck back soon, the queue moves fast.", fontSize = 13.sp, color = SSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { vm.forceSeedAndReload() },
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SAccent),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Refresh feed", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun SwipeLimitState() {
    val inf = rememberInfiniteTransition(label = "ls")
    val hourRot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), "hr")
    val glow    by inf.animateFloat(0.2f, 0.5f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), "lg")
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(90.dp)
                .shadow(14.dp, CircleShape, spotColor = SGold.copy(glow))
                .clip(CircleShape).background(SGoldSoft)
                .border(2.dp, SGold.copy(0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.HourglassBottom, null, tint = SGold, modifier = Modifier.size(38.dp).rotate(hourRot)) }
        Spacer(Modifier.height(24.dp))
        Text("Daily limit reached", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SPrimary, textAlign = TextAlign.Center, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(8.dp))
        Text("You have used all $DAILY_SWIPE_LIMIT swipes for today.\nCome back tomorrow for a fresh batch.", fontSize = 13.sp, color = SSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(SGoldSoft)
                .border(1.dp, SGold.copy(0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) { Text("Resets at midnight", fontSize = 12.sp, color = SGold, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun MatchOverlay(match: SwipeMatch, onDismiss: () -> Unit) {
    val theirGradient = veilGradient(match.userB)
    val myGradient    = veilGradient(match.userA)
    val inf = rememberInfiniteTransition(label = "mo")
    val ringPulse by inf.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "rp")
    val glowAlpha by inf.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), "ga")
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.85f)
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                // Pulsing ring around avatars
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(220.dp).scale(ringPulse).clip(CircleShape)
                        .background(SLike.copy(glowAlpha * 0.12f))
                        .border(1.5.dp, SLike.copy(glowAlpha * 0.4f), CircleShape))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-22).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(110.dp).shadow(16.dp, CircleShape, spotColor = SLike.copy(0.4f))
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(theirGradient.first, theirGradient.second)))
                                .border(3.dp, SLike, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (match.photoB.isNotBlank()) {
                                coil.compose.AsyncImage(match.photoB, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(match.aliasB.firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                        Box(
                            Modifier.size(110.dp).shadow(16.dp, CircleShape, spotColor = SAccent.copy(0.4f)).zIndex(1f)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(myGradient.first, myGradient.second)))
                                .border(3.dp, SAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (match.photoA.isNotBlank()) {
                                coil.compose.AsyncImage(match.photoA, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(match.aliasA.firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                    }
                }
                Text("It's a Match!", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, letterSpacing = (-1).sp)
                Text("You and ${match.aliasB} vibe.", fontSize = 16.sp, color = Color.White.copy(0.80f), textAlign = TextAlign.Center)
                Text("A private chat unlocks when you both connect.", fontSize = 12.sp, color = Color.White.copy(0.45f), textAlign = TextAlign.Center, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SLike),
                    modifier = Modifier.fillMaxWidth(0.70f).height(52.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Keep swiping", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ComplimentDialog(
    profile       : SwipeProfile,
    cookieBalance : Int,
    onDismiss     : () -> Unit,
    onSend        : (String) -> Unit
) {
    val cost      = SwipeViewModel.COMPLIMENT_COOKIE_COST
    val canAfford = cookieBalance < 0 || cookieBalance >= cost
    var message   by remember { mutableStateOf("") }
    val maxLength = 200
    val charsLeft = maxLength - message.length

    // Dim scrim — dismiss on tap
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Sheet — absorbs clicks so tapping inside doesn't dismiss
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {})
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .navigationBarsPadding()
                .imePadding()                     // ← rises above keyboard
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Drag handle ───────────────────────────────────────────────────
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFDDDDDD))
                    .align(Alignment.CenterHorizontally)
            )

            // ── Header row ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Send a compliment",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = SPrimary
                    )
                    Text(
                        "to ${profile.alias}",
                        fontSize = 13.sp,
                        color    = SSecondary
                    )
                }
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close, "Close",
                        tint     = SMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Cookie cost row ───────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canAfford) Color(0xFFFFFBEB) else Color(0xFFFEF2F2))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🍪", fontSize = 18.sp)
                Column {
                    Text(
                        "$cost cookies",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (canAfford) Color(0xFF92400E) else SPass
                    )
                    Text(
                        if (cookieBalance >= 0) "Balance: $cookieBalance" else "Checking…",
                        fontSize = 11.sp,
                        color    = if (canAfford) Color(0xFFB45309) else SPass.copy(0.75f)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!canAfford) {
                    Text(
                        "Not enough",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color      = SPass
                    )
                }
            }

            // ── Message input ─────────────────────────────────────────────────
            OutlinedTextField(
                value         = message,
                onValueChange = { if (it.length <= maxLength) message = it },
                enabled       = canAfford,
                modifier      = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp),
                placeholder   = {
                    Text(
                        "Write something genuine…\ne.g. \"I love your perspective on quiet evenings\"",
                        fontSize   = 14.sp,
                        color      = SMuted,
                        lineHeight = 20.sp
                    )
                },
                shape  = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = SPrimary.copy(0.5f),
                    unfocusedBorderColor    = SCardBorder,
                    focusedContainerColor   = Color(0xFFFAFAFA),
                    unfocusedContainerColor = Color(0xFFFAFAFA),
                    disabledContainerColor  = Color(0xFFF3F3F3),
                    disabledBorderColor     = SCardBorder,
                    cursorColor             = SPrimary
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize   = 14.sp,
                    color      = SPrimary,
                    lineHeight = 21.sp
                ),
                maxLines = 6,
                minLines = 4
            )

            // ── Character count ───────────────────────────────────────────────
            Text(
                "$charsLeft remaining",
                fontSize = 11.sp,
                color    = if (charsLeft <= 20) SPass else SMuted,
                modifier = Modifier.align(Alignment.End)
            )

            // ── Info note ─────────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Info, null,
                    tint     = SSecondary,
                    modifier = Modifier.size(14.dp).padding(top = 1.dp)
                )
                Text(
                    "Delivered to their inbox. They can accept and start a conversation.",
                    fontSize   = 12.sp,
                    color      = SSecondary,
                    lineHeight = 17.sp
                )
            }

            // ── Action buttons ────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.dp, SCardBorder),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = SSecondary)
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick  = { if (message.isNotBlank() && canAfford) onSend(message.trim()) },
                    enabled  = message.isNotBlank() && canAfford,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = SPrimary,
                        disabledContainerColor = SCardBorder
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Rounded.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Send", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Not-enough-cookies bottom alert ─────────────────────────────────────────
@Composable
private fun NoCookiesAlert(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.65f))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🍪", fontSize = 40.sp)
            Text(
                "Not Enough Cookies",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SPrimary
            )
            Text(
                "You need at least 15 cookies to send a compliment.\nVisit the Cookie Store to top up!",
                fontSize = 14.sp,
                color = SSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SSuperLike)
            ) {
                Text("Got it", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

//  PickerSheet - scrollable picker for country / state selection
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun PickerSheet(title: String, items: List<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)).clickable(enabled = false, onClick = {})) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.70f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(SBg)
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SCardBorder))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SPrimary, modifier = Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(SCard).border(0.8.dp, SCardBorder, CircleShape).clickable { onDismiss() }.padding(8.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = SMuted, modifier = Modifier.size(16.dp))
                }
            }
            HorizontalDivider(color = SCardBorder, thickness = 0.5.dp)
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SCard).border(0.8.dp, SCardBorder, RoundedCornerShape(12.dp)).clickable { onSelect(item) }.padding(horizontal = 16.dp, vertical = 13.dp)) {
                        Text(item, fontSize = 14.sp, color = SPrimary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Skeleton Loader - Professional shimmer loading state
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun SwipeSkeletonLoader() {
    val inf = rememberInfiniteTransition(label = "shimmer")
    // Moving shimmer sweep
    val shimX by inf.animateFloat(-600f, 1200f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), "sx")
    val shimBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFEEEEEE), Color(0xFFF8F8F8), Color(0xFFEEEEEE)),
        start  = androidx.compose.ui.geometry.Offset(shimX - 200f, 0f),
        end    = androidx.compose.ui.geometry.Offset(shimX + 200f, 0f)
    )
    // Legacy alpha for button circles
    val alpha by inf.animateFloat(0.25f, 0.55f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), "shimmerAlpha")

    Column(Modifier.fillMaxSize().background(SBg).systemBarsPadding()) {
        // Header skeleton
        Row(
            Modifier.fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(SMuted.copy(alpha = alpha))
                )
                Box(
                    Modifier.width(80.dp).height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SMuted.copy(alpha = alpha))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.width(90.dp).height(36.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SMuted.copy(alpha = alpha))
                )
                Box(
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(SMuted.copy(alpha = alpha))
                )
            }
        }
        Divider(color = SCardBorder, thickness = 0.5.dp)

        // Card skeleton
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxWidth(0.93f).fillMaxHeight(0.85f)
                    .shadow(12.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(SCard)
                    .border(1.dp, SCardBorder, RoundedCornerShape(20.dp))
            ) {
                Column {
                    // Image skeleton
                    Box(
                        Modifier.fillMaxWidth().height(390.dp)
                            .background(shimBrush)
                    )
                    
                    // Content skeleton
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Name skeleton
                        Box(
                            Modifier.width(150.dp).height(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(shimBrush)
                        )
                        
                        // Info sections skeleton
                        repeat(3) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.width(110.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(shimBrush))
                                Box(Modifier.fillMaxWidth().height(15.dp).clip(RoundedCornerShape(4.dp)).background(shimBrush))
                                Box(Modifier.fillMaxWidth(0.78f).height(15.dp).clip(RoundedCornerShape(4.dp)).background(shimBrush))
                            }
                        }
                    }
                }
            }
        }

        // Action buttons skeleton
        Row(
            Modifier.fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp)
                    .clip(CircleShape)
                    .background(SMuted.copy(alpha = alpha))
            )
            Box(
                Modifier.size(64.dp)
                    .clip(CircleShape)
                    .background(SMuted.copy(alpha = alpha))
            )
            Box(
                Modifier.size(72.dp)
                    .clip(CircleShape)
                    .background(SMuted.copy(alpha = alpha))
            )
            Box(
                Modifier.size(64.dp)
                    .clip(CircleShape)
                    .background(SMuted.copy(alpha = alpha))
            )
        }
    }
}

