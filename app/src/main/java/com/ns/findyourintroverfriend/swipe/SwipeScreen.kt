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
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.VeilThemeManager


// ── Dark-mode state ──────────────────────────────────────────────────────────
// Synced from LocalVeilIsDark via SideEffect in SwipeContent.
private var _swipeDark by mutableStateOf(VeilThemeManager.initialResolvedDark)

// ── Peaceful Serenity Palette ─────────────────────────────────────────────────
// Light: warm parchment, dusty mauve, sage green — calm and peaceful
// Dark : deep ink with gentle warm tones
private val SBg         get() = if (_swipeDark) Color(0xFF0F0F14) else Color(0xFFFBF8F4)   // warm parchment / deep ink
private val SCard       get() = if (_swipeDark) Color(0xFF17171E) else Color(0xFFFFFFFF)   // card surface
private val SCardBorder get() = if (_swipeDark) Color(0xFF2C2C34) else Color(0xFFE8E3DC)   // soft border
private val SPrimary    get() = if (_swipeDark) Color(0xFFE0DDD8) else Color(0xFF2C2825)   // warm near-black
private val SSecondary  get() = if (_swipeDark) Color(0xFF918E8A) else Color(0xFF7A7470)   // warm stone
private val SMuted      get() = if (_swipeDark) Color(0xFF5A5855) else Color(0xFFC4BFBA)   // muted warm
// Main accent: dusty mauve — calm, introverted
private val SAccent            = Color(0xFFA07C8C)   // dusty mauve
private val SAccentSoft get() = if (_swipeDark) Color(0xFF251520) else Color(0xFFF7EFF3)   // very soft mauve tint
// Like: sage green — nature, calm
private val SLike              = Color(0xFF7A9E87)   // sage green
private val SLikeSoft   get() = if (_swipeDark) Color(0xFF0E1E13) else Color(0xFFEFF5F1)   // soft sage tint
// Pass: muted terracotta — not harsh red
private val SPass              = Color(0xFFBF6B6B)   // muted terracotta
private val SPassSoft   get() = if (_swipeDark) Color(0xFF1E0E0E) else Color(0xFFF9EEEE)   // soft terracotta tint
// Gold: warm sand — not vivid yellow
private val SGold              = Color(0xFFBFA882)   // warm sand/taupe
private val SGoldSoft   get() = if (_swipeDark) Color(0xFF1A1610) else Color(0xFFF7F3EC)   // soft sand tint
// Undo: soft dusty blue
private val SUndo              = Color(0xFF7B9AC4)   // dusty blue
// Super-like: a deeper mauve (compliment)
private val SSuperLike         = Color(0xFF9B7BAE)   // soft plum/lavender
private val SSuperSoft  get() = if (_swipeDark) Color(0xFF1A1225) else Color(0xFFF3EFF8)   // soft plum tint

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

    // Sync dark mode
    val _veilIsDark = LocalVeilIsDark.current
    SideEffect { _swipeDark = _veilIsDark }

    // Auto-refresh once when loading finishes and feed is empty
    val hasAutoRefreshed = remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (!loading && feed.isEmpty() && !hasAutoRefreshed.value) {
            hasAutoRefreshed.value = true
            vm.forceSeedAndReload()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(SBg)) {

        val inSetup = isCreatingProfile || isEditingProfile

        // ── Main swipe UI — fades out when profile setup opens ────────────────
        AnimatedVisibility(
            visible = !inSetup,
            enter   = fadeIn(tween(220)),
            exit    = fadeOut(tween(180))
        ) {
            Column(Modifier.fillMaxSize()) {
                // -- Gamified Header --
                val pulseInf = rememberInfiniteTransition(label = "hp")
                val headerPulse by pulseInf.animateFloat(1f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), "hps")
                val hapticH = LocalHapticFeedback.current
                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(SBg, SAccentSoft.let { if (_swipeDark) Color(0xFF1A1020) else Color(0xFFF9F3F6) }, SBg)
                            )
                        )
                        .border(BorderStroke(0.5.dp, SCardBorder))
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Discover",
                            fontSize = 21.sp, fontWeight = FontWeight.Bold,
                            color = SPrimary, letterSpacing = (-0.5).sp
                        )
                        if (swipesLeft > 0) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SLikeSoft)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(SLike))
                                Text("$swipesLeft left today", fontSize = 10.sp, color = SLike, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp)
                                .shadow(3.dp, CircleShape, spotColor = SAccent.copy(0.08f))
                                .clip(CircleShape).background(SCard)
                                .border(0.8.dp, SCardBorder, CircleShape)
                                .clickable { hapticH.performHapticFeedback(HapticFeedbackType.LongPress); onNavigateToMatches() },
                            contentAlignment = Alignment.Center
                        ) { Image(painterResource(R.drawable.ic_swipe_connections_heart), null, modifier = Modifier.size(20.dp), colorFilter = ColorFilter.tint(SAccent, BlendMode.SrcIn)) }
                        Box(
                            Modifier.size(40.dp)
                                .shadow(3.dp, CircleShape, spotColor = SAccent.copy(0.08f))
                                .clip(CircleShape).background(SCard)
                                .border(0.8.dp, SCardBorder, CircleShape)
                                .clickable { hapticH.performHapticFeedback(HapticFeedbackType.LongPress); if (myProfile != null) { vm.loadProfileIntoForm(); isEditingProfile = true } else isCreatingProfile = true },
                            contentAlignment = Alignment.Center
                        ) { Image(painterResource(R.drawable.ic_user_pen_swipe), null, modifier = Modifier.size(20.dp), colorFilter = ColorFilter.tint(SPrimary, BlendMode.SrcIn)) }
                    }
                }
                // -- Content --
                when {
                    loading        -> SwipeSkeletonLoader()
                    swipesLeft <= 0 -> SwipeLimitState()
                    feed.isEmpty() -> SwipeEmptyState(vm)
                    else -> {
                        Box(
                            Modifier.weight(1f).fillMaxWidth()
                                .background(SBg)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            feed.take(3).asReversed().forEachIndexed { revIdx, profile ->
                                val stackIdx = feed.take(3).size - 1 - revIdx
                                SwipeCard(
                                    profile      = profile,
                                    isTopCard    = stackIdx == 0,
                                    stackDepth   = stackIdx,
                                    onSwipeRight = { if (myProfile == null) isCreatingProfile = true else vm.swipeRight(profile) },
                                    onSwipeLeft  = { if (myProfile == null) isCreatingProfile = true else vm.swipeLeft(profile) },
                                    onDragChange = { x -> activeDragX = x },
                                    onDragEnd    = { activeDragX = 0f }
                                )
                            }
                        }
                        // ── Peaceful Action Buttons ──
                        val hapticBtn = LocalHapticFeedback.current
                        var passPressed   by remember { mutableStateOf(false) }
                        var likePressed   by remember { mutableStateOf(false) }
                        var starPressed   by remember { mutableStateOf(false) }
                        val passScale by animateFloatAsState(if (passPressed) 0.84f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ps")
                        val likeScale by animateFloatAsState(if (likePressed) 0.84f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ls")
                        val starScale by animateFloatAsState(if (starPressed) 0.84f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ss")
                        LaunchedEffect(passPressed) { if (passPressed) { kotlinx.coroutines.delay(120); passPressed = false } }
                        LaunchedEffect(likePressed) { if (likePressed) { kotlinx.coroutines.delay(120); likePressed = false } }
                        LaunchedEffect(starPressed) { if (starPressed) { kotlinx.coroutines.delay(120); starPressed = false } }
                        Row(
                            Modifier.fillMaxWidth()
                                .background(SBg)
                                .border(BorderStroke(0.5.dp, SCardBorder))
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Undo — soft dusty blue
                            VeilBtn(size = 48.dp, bg = SCard, shadow = SUndo.copy(0.08f), borderColor = SCardBorder, onClick = {}) {
                                Icon(Icons.Rounded.Undo, null, tint = SUndo.copy(0.65f), modifier = Modifier.size(20.dp))
                            }
                            // Pass — soft terracotta
                            Box(
                                Modifier.size(58.dp).scale(passScale)
                                    .shadow(6.dp, CircleShape, spotColor = SPass.copy(0.15f))
                                    .clip(CircleShape)
                                    .background(SPassSoft)
                                    .border(1.5.dp, SPass.copy(0.35f), CircleShape)
                                    .clickable {
                                        passPressed = true
                                        hapticBtn.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (myProfile == null) isCreatingProfile = true
                                        else feed.firstOrNull()?.let { vm.swipeLeft(it) }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Close, null, tint = SPass, modifier = Modifier.size(24.dp)) }
                            // Compliment — soft plum sparkle
                            Box(
                                Modifier.size(68.dp).scale(starScale)
                                    .shadow(8.dp, CircleShape, spotColor = SSuperLike.copy(0.18f))
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(listOf(SSuperSoft, SSuperSoft.let {
                                            if (_swipeDark) Color(0xFF221535) else Color(0xFFEDE5F5)
                                        }))
                                    )
                                    .border(1.5.dp, SSuperLike.copy(0.40f), CircleShape)
                                    .clickable {
                                        starPressed = true
                                        hapticBtn.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (myProfile == null) isCreatingProfile = true
                                        else feed.firstOrNull()?.let { p -> complimentTarget = p; showComplimentDialog = true }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = SSuperLike, modifier = Modifier.size(28.dp))
                            }
                            // Like — sage green
                            Box(
                                Modifier.size(58.dp).scale(likeScale)
                                    .shadow(6.dp, CircleShape, spotColor = SLike.copy(0.18f))
                                    .clip(CircleShape)
                                    .background(SLikeSoft)
                                    .border(1.5.dp, SLike.copy(0.40f), CircleShape)
                                    .clickable {
                                        likePressed = true
                                        hapticBtn.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (myProfile == null) isCreatingProfile = true
                                        else feed.firstOrNull()?.let { vm.swipeRight(it) }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Favorite, null, tint = SLike, modifier = Modifier.size(24.dp)) }
                        }
                    }
                }
            }
        } // end AnimatedVisibility (main swipe UI)

        // ── Profile Setup — slides up from bottom ─────────────────────────────
        AnimatedVisibility(
            visible = inSetup,
            enter   = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(280)),
            exit    = slideOutVertically(tween(360)) { it } + fadeOut(tween(260))
        ) {
            SwipeSetupInline(
                vm        = vm,
                isEditing = isEditingProfile,
                onBack    = { isEditingProfile = false; isCreatingProfile = false }
            )
        }

        // ── Overlays (sit on top regardless of state) ─────────────────────────
        if (match != null && vm.isDemoMatch) {
            MatchOverlay(match = match!!, onDismiss = { vm.clearDemoMatch() })
        }
        if (showComplimentDialog && complimentTarget != null) {
            ComplimentDialog(
                profile       = complimentTarget!!,
                cookieBalance = cookieBalance,
                onDismiss = { showComplimentDialog = false; complimentTarget = null },
                onSend = { message ->
                    vm.sendCompliment(complimentTarget!!, message)
                    showComplimentDialog = false
                    complimentTarget = null
                }
            )
        }
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
                    .background(SCard)
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
                    // LIKE stamp — softer sage stroke
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(24.dp)
                            .rotate(-20f)
                            .alpha(if (animX > 0) stampAlpha else 0f)
                            .scale(1f + stampAlpha * 0.08f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SLike.copy(0.12f))
                            .border(2.5.dp, SLike.copy(0.8f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "CONNECT",
                            color = SLike,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    // NOPE stamp — soft terracotta
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .rotate(20f)
                            .alpha(if (animX < 0) stampAlpha else 0f)
                            .scale(1f + stampAlpha * 0.08f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SPass.copy(0.10f))
                            .border(2.5.dp, SPass.copy(0.75f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "PASS",
                            color = SPass,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = 2.sp
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

                // Name + info overlay at bottom of photo — no verified badge
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
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
                                fontSize = 20.sp,
                                color = Color.White.copy(0.75f),
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }

                    val loc = listOf(profile.state, profile.country).filter { it.isNotBlank() }.joinToString(", ")
                    if (loc.isNotBlank()) {
                        Row(
                            Modifier.padding(top = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.LocationOn,
                                null,
                                tint = Color.White.copy(0.65f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                loc,
                                fontSize = 12.sp,
                                color = Color.White.copy(0.65f)
                            )
                        }
                    }

                    if (profile.availableTonight.isNotBlank()) {
                        Row(
                            Modifier
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SLike.copy(0.20f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(SLike)
                            )
                            Text(
                                "Free tonight · ${profile.availableTonight}",
                                fontSize = 11.sp,
                                color = Color.White.copy(0.90f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Profile details ────────────────────────────────────────────────
            val cardBg      = if (_swipeDark) Color(0xFF0F0F14) else Color(0xFFFBF8F4)  // warm parchment
            val sectionBg   = if (_swipeDark) Color(0xFF17171E) else Color(0xFFFFFFFF)  // card white
            val dividerColor = if (_swipeDark) Color(0xFF2C2C34) else Color(0xFFE8E3DC) // soft border

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cardBg)
                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

                // ── Intro questions ─────────────────────────────────────────────
                if (profile.comfortWhen.isNotBlank() || profile.perfectEvening.isNotBlank() ||
                    profile.openUpWhen.isNotBlank() || profile.thingsIAvoid.isNotBlank()) {

                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(sectionBg)
                            .border(1.dp, dividerColor, RoundedCornerShape(16.dp))
                    ) {
                        listOfNotNull(
                            if (profile.comfortWhen.isNotBlank())  ("Comfortable when" to profile.comfortWhen) else null,
                            if (profile.perfectEvening.isNotBlank()) ("Perfect evening" to profile.perfectEvening) else null,
                            if (profile.openUpWhen.isNotBlank())   ("Opens up when" to profile.openUpWhen) else null,
                            if (profile.thingsIAvoid.isNotBlank()) ("Tends to avoid" to profile.thingsIAvoid) else null
                        ).forEachIndexed { idx, (label, text) ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    label.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SMuted,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text,
                                    fontSize = 14.sp,
                                    color = SPrimary,
                                    lineHeight = 22.sp
                                )
                            }
                            if (idx < 3) Box(Modifier.fillMaxWidth().height(0.5.dp).background(dividerColor))
                        }
                    }
                }

                // ── Ask me about ────────────────────────────────────────────────
                if (profile.askMeAbout.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SAccentSoft)
                            .border(1.dp, SAccent.copy(0.15f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Ask me about",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SAccent,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "“${profile.askMeAbout}”",
                            fontSize = 14.sp,
                            color = SPrimary,
                            lineHeight = 21.sp
                        )
                    }
                }

                // ── Talk for hours ──────────────────────────────────────────────
                if (profile.talkHoursAbout.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SGoldSoft)
                            .border(1.dp, SGold.copy(0.15f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Hours on topic",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SGold.copy(0.85f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            profile.talkHoursAbout,
                            fontSize = 14.sp,
                            color = SPrimary,
                            lineHeight = 21.sp
                        )
                    }
                }

                // ── Vibe tags ───────────────────────────────────────────────────
                if (profile.introvertTags.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Personality",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SMuted,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        profile.introvertTags.forEach { tag ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(sectionBg)
                                    .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
                            ) {
                                Text(tag, fontSize = 12.sp, color = SSecondary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // ── Into (hobbies) ──────────────────────────────────────────────
                if (profile.hobbies.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Interests",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SMuted,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        profile.hobbies.forEach { h ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(SAccentSoft)
                                    .border(1.dp, SAccent.copy(0.18f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
                            ) {
                                Text(h, fontSize = 12.sp, color = SAccent.copy(0.85f), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // ── Free time slots ─────────────────────────────────────────────
                if (profile.preferredTimeSlots.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Usually free",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SMuted,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        for (i in 0 until minOf(profile.preferredTimeSlots.size, 4)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SGoldSoft)
                                    .border(1.dp, SGold.copy(0.20f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    profile.preferredTimeSlots[i],
                                    fontSize = 12.sp,
                                    color = SGold.copy(0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
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
    val lottieComp by rememberLottieComposition(LottieCompositionSpec.Asset("Allcaughup.lottie"))
    val lottieProg by animateLottieCompositionAsState(composition = lottieComp, iterations = LottieConstants.IterateForever, speed = 0.9f)
    val inf = rememberInfiniteTransition(label = "es")
    val floatY  by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fy")
    val glowA   by inf.animateFloat(0.04f, 0.14f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), "ga")
    val dotA    by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "da")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Lottie — floating, no card box ─────────────────────────────────
        Box(
            Modifier
                .offset(y = floatY.dp)
                .size(200.dp)
                .shadow(20.dp, CircleShape, spotColor = SAccent.copy(glowA), clip = false),
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = lottieComp,
                progress = { lottieProg },
                modifier = Modifier.size(200.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── Title ───────────────────────────────────────────────────────────
        Text(
            "All Caught Up",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SPrimary,
            letterSpacing = (-1.2).sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You've explored everyone for now.\nNew introverts join every day.",
            fontSize = 13.sp,
            color = SSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(28.dp))

        // ── Live dot + "Refreshing automatically" status ────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(SLike.copy(dotA)))
            Text(
                "Checking for new profiles automatically",
                fontSize = 11.sp,
                color = SMuted,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Decorative divider ──────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.weight(1f).height(0.5.dp).background(SCardBorder))
            Text("WHILE YOU WAIT", fontSize = 9.sp, color = SMuted, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
            Box(Modifier.weight(1f).height(0.5.dp).background(SCardBorder))
        }

        Spacer(Modifier.height(16.dp))

        // ── What's next card ────────────────────────────────────────────────
        val actions = listOf(
            Triple(Icons.Rounded.Send,         "Message a connection",   "Break the ice in Slow Chat"),
            Triple(Icons.Rounded.Star,          "Send a compliment",      "Stand out with a kind note"),
            Triple(Icons.Rounded.AccountCircle, "Update your profile",    "Get discovered by more people")
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SCard)
                .border(1.dp, SCardBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            actions.forEachIndexed { idx, (icon, title, sub) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(SAccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = SAccent, modifier = Modifier.size(17.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SPrimary)
                        Text(sub, fontSize = 11.sp, color = SSecondary)
                    }
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = SMuted, modifier = Modifier.size(16.dp))
                }
                if (idx < actions.size - 1) {
                    Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(0.5.dp).background(SCardBorder))
                }
            }
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
                .background(Color.Black.copy(alpha = 0.80f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                // Soft pulsing ring around avatars
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(200.dp).scale(ringPulse).clip(CircleShape)
                            .background(SLike.copy(glowAlpha * 0.10f))
                            .border(1.dp, SLike.copy(glowAlpha * 0.35f), CircleShape)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-22).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(105.dp)
                                .shadow(12.dp, CircleShape, spotColor = SLike.copy(0.25f))
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(theirGradient.first, theirGradient.second)))
                                .border(2.5.dp, SLike.copy(0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (match.photoB.isNotBlank()) {
                                coil.compose.AsyncImage(match.photoB, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(match.aliasB.firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Box(
                            Modifier.size(105.dp)
                                .shadow(12.dp, CircleShape, spotColor = SAccent.copy(0.25f)).zIndex(1f)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(myGradient.first, myGradient.second)))
                                .border(2.5.dp, SAccent.copy(0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (match.photoA.isNotBlank()) {
                                coil.compose.AsyncImage(match.photoA, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(match.aliasA.firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
                // Soft sparkle icon above title
                Icon(Icons.Rounded.AutoAwesome, null, tint = SLike.copy(0.85f), modifier = Modifier.size(22.dp))
                Text("It's a Connection!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, letterSpacing = (-0.5).sp)
                Text("You and ${match.aliasB} resonated.", fontSize = 15.sp, color = Color.White.copy(0.75f), textAlign = TextAlign.Center)
                Text("A private chat unlocks when you both connect.", fontSize = 12.sp, color = Color.White.copy(0.40f), textAlign = TextAlign.Center, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SLike
                    ),
                    modifier = Modifier.fillMaxWidth(0.68f).height(50.dp),
                    elevation = ButtonDefaults.buttonElevation(4.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Keep exploring", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
                    .background(if (canAfford) SGoldSoft else SPassSoft)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (canAfford) SGoldSoft else SPassSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Star, null,
                        tint = if (canAfford) SGold else SPass,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        "$cost cookies",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (canAfford) SGold else SPass
                    )
                    Text(
                        if (cookieBalance >= 0) "Balance: $cookieBalance" else "Checking…",
                        fontSize = 11.sp,
                        color    = if (canAfford) SGold.copy(0.70f) else SPass.copy(0.65f)
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
            .background(Color.Black.copy(0.55f))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SBg)
                .border(BorderStroke(0.5.dp, SCardBorder), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Handle
            Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SCardBorder))
            // Icon instead of emoji
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(SGoldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Star, null, tint = SGold, modifier = Modifier.size(26.dp))
            }
            Text(
                "Not Enough Cookies",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SPrimary
            )
            Text(
                "You need at least 15 cookies to send a compliment.\nVisit the Cookie Store to top up!",
                fontSize = 13.sp,
                color = SSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SAccent)
            ) {
                Text("Got it", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
        HorizontalDivider(color = SCardBorder, thickness = 0.5.dp)

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

