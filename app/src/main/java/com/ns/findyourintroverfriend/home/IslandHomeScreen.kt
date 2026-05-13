package com.ns.findyourintroverfriend.home

import com.ns.findyourintroverfriend.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.ns.findyourintroverfriend.dev.DevConfig
import com.ns.findyourintroverfriend.ui.theme.*
import com.ns.findyourintroverfriend.ui.IslandHomeSkeletonScreen
import kotlin.math.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


// ─────────────────────────────────────────────────────────────────────────────
//  Island Home Screen — the game world
// ─────────────────────────────────────────────────────────────────────────────

// ── Premium Palette – Day Sky (ivory → powder blue → warm peach) ─────────────
private val DaySkyTop    = Color(0xFFF0F4FA)   // cool ivory white
private val DaySkyMid    = Color(0xFFE8EEFF)   // powder-blue tint
private val DaySkyBottom = Color(0xFFFFF0E8)   // warm peach-cream

// ── Premium Palette – Night Sky (deep charcoal → midnight blue → dark teal) ──
private val NightSkyTop    = Color(0xFF0A0E1A)  // near-black charcoal
private val NightSkyMid    = Color(0xFF0F1B2E)  // deep midnight navy
private val NightSkyBottom = Color(0xFF141E30)  // dark slate-navy

private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val warm: Boolean)
private data class Particle(val xFrac: Float, val baseYFrac: Float, val phase: Float, val radius: Float)

/** Returns true if the current local hour is between 6 (6AM) and 18 (6PM). */
fun isCurrentlyDayTime(): Boolean {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return hour in 6..17
}

@Composable
fun IslandHomeScreen(
    vm                   : IslandHomeViewModel = viewModel(),
    onOpenQuestions      : () -> Unit,
    onOpenAnswers        : () -> Unit = {},
    onOpenInbox          : () -> Unit = {},
    onOpenCeremony       : () -> Unit = {},
    onOpenGameInvite     : () -> Unit = {},
    onOpenWouldYouRather : () -> Unit = {},
    onOpenTrueOrMyth     : () -> Unit = {},
    onOpenTomSetup       : () -> Unit = {},
    onOpenSlowChat       : () -> Unit = {},
    onOpenMyQInbox       : () -> Unit = {},   // My Questions Inbox
    onOpenAestheticsFeed : () -> Unit = {},   // Anonymous Aesthetics Vibe Feed (all days)
    onOpenVibes             : () -> Unit = {},   // Circle Vibes: Song / Color / One Word Bio
    onOpenDareToShare       : () -> Unit = {},   // Day 2 — Dare to Share
    onOpenSecretCompliment  : () -> Unit = {},   // Day 2 — Secret Compliment
    onReturnToWaiting    : () -> Unit = {},
    onLeaveRoom          : () -> Unit = {},    // ← navigate to VeilHomeScreen
    onOpenPartyIsland    : (sessionId: String) -> Unit = {},
    onOpenDevInspector   : () -> Unit = {},
    onOpenAdminDashboard : () -> Unit = {}     // Admin only
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // ── Day / Night mode — follows real clock, no dev toggle ────────────────
    val isDay = isCurrentlyDayTime()

    // ── Dashboard overlay state ───────────────────────────────────────────────
    var showDashboard    by remember { mutableStateOf(false) }
    var showLeaveDialog  by remember { mutableStateOf(false) }   // door-button leave
    var showExitDialog   by remember { mutableStateOf(false) }   // back-press exit app

    // ── Dev panel — visible only to emails in DevConfig ──────────────────────
    val currentEmail     = remember { FirebaseAuth.getInstance().currentUser?.email }
    val isDevUser        = remember(currentEmail) { DevConfig.isDev(currentEmail) }
    var showDevPanel     by remember { mutableStateOf(false) }

    // ── Member-left banner: only timer-runs when someone actually left ─────────
    var showLeftBanner by remember { mutableStateOf(true) }
    val leftMemberCount = state.leftMemberCount
    LaunchedEffect(leftMemberCount) {
        if (leftMemberCount > 0) {
            showLeftBanner = true
            kotlinx.coroutines.delay(6_500L)
            showLeftBanner = false
        }
    }

    // ── Profile sheet state ───────────────────────────────────────────────────
    var selectedProfile        by remember { mutableStateOf<IslandMember?>(null) }
    var connectionTappedMember by remember { mutableStateOf<IslandMember?>(null) }

    // C3: navigate back to waiting room when leaveGroup() completes
    LaunchedEffect(state.leaveGroupRequested) {
        if (state.leaveGroupRequested) {
            onLeaveRoom()          // ← go to VeilHomeScreen instead of raw waiting room
        }
    }

    // Day sneak-peek sheet state
    var showDayPeekSheet by remember { mutableStateOf(false) }
    var showDayTimeline   by remember { mutableStateOf(false) }

    // ── Back press — show "Exit App?" dialog, NOT leave-circle ─────────────
    androidx.activity.compose.BackHandler(enabled = !showExitDialog && !showLeaveDialog) {
        showExitDialog = true
    }

    // Bloom palette — animated day ↔ night background (3-stop)
    val bgTop    by animateColorAsState(if (isDay) DaySkyTop    else NightSkyTop,    animationSpec = tween(1400), label = "bgTop")
    val bgMid    by animateColorAsState(if (isDay) DaySkyMid    else NightSkyMid,    animationSpec = tween(1400), label = "bgMid")
    val bgBottom by animateColorAsState(if (isDay) DaySkyBottom else NightSkyBottom, animationSpec = tween(1400), label = "bgBottom")

    // Show Day Peek sheet when chip tapped
    if (showDayPeekSheet) {
        DayPeekSheet(
            currentDay = state.groupDay,
            onDismiss  = { showDayPeekSheet = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgMid, bgBottom)))
    ) {
        // ── Layer 1: Pastel Nature Scenery ────────────────────────────────────
        if (isDay) PastelDaySky() else PastelNightSky()

        // ── Dev flow panel (dev emails only) ──────────────────────────────────
        if (isDevUser) {
            DevFlowPanel(
                vm            = vm,
                state         = state,
                isDay         = isDay,
                showPanel     = showDevPanel,
                onTogglePanel = { showDevPanel = !showDevPanel },
                onOpenCeremony = onOpenCeremony,
                modifier      = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(bottom = 90.dp)
            )
        }

        // ── Layer 2: Content ──────────────────────────────────────────────────
        when {
            state.isLoading -> {
                IslandHomeSkeletonScreen(isDay = isDay)
            }
            state.error != null -> {
                NoGroupCard(
                    errorMsg          = state.error!!,
                    onReturnToWaiting = {
                        vm.clearStaleGroupId()   // B5: clear stale groupId before leaving
                        onReturnToWaiting()
                    },
                    modifier          = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }
            else -> {
                Column(
                    modifier            = Modifier.fillMaxSize().systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ── Top Section ────────────────────────────────────────────────────
                    // Calculate member counts at the top level
                    val activeMembers = state.members.count {
                        !it.isYou && it.activityStatus == ActivityStatus.ACTIVE
                    }
                    val leftMembers = state.leftMemberCount
                    val warningMembers = state.members.count {
                        !it.isYou && it.activityStatus == ActivityStatus.WARNING
                    }
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IslandTopBar(
                            day                  = state.groupDay,
                            isDay                = isDay,
                            onOpenDayTimeline    = { showDayTimeline = true },
                            onOpenDevInspector   = onOpenDevInspector
                        )

                        // Show Day Timeline dialog when pill is tapped
                        if (showDayTimeline) {
                            DayTimelineDialog(
                                day            = state.groupDay,
                                isDay          = isDay,
                                activeMembers  = activeMembers,
                                warningMembers = warningMembers,
                                leftMembers    = leftMembers,
                                countdownMs    = state.nextDayCountdownMs.coerceAtLeast(0L),
                                onDismiss      = { showDayTimeline = false }
                            )
                        }


                        // ── Member Left Banner — auto-dismisses after 6.5 s ────────
                        AnimatedVisibility(
                            visible = leftMembers > 0 && showLeftBanner,
                            enter   = slideInVertically { -it },
                            exit    = slideOutVertically { -it }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFF4444).copy(alpha = 0.15f),
                                                Color(0xFFFF6B6B).copy(alpha = 0.10f)
                                            )
                                        )
                                    )
                                    .border(1.dp, Color(0xFFFF4444).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⚠️", fontSize = 14.sp)
                                    Text(
                                        text = if (leftMembers == 1) "1 member left the circle. Your journey continues with ${state.members.count { it.activityStatus != ActivityStatus.LEFT } - 1} others."
                                               else "$leftMembers members left. Your journey continues.",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isDay) Color(0xFFCC3333) else Color(0xFFFF8888),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }

                        // ── Bond Prompt — warm contextual nudges ───────────────────
                        CircleBondPrompt(
                            day         = state.groupDay,
                            isDay       = isDay,
                            onOpenVibes = onOpenVibes
                        )
                    }

                    // ── Soul Web — center stage ────────────────────────────────
                    Box(
                        modifier         = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        SoulWebPanel(
                            members          = state.members,
                            day              = state.groupDay,
                            isDay            = isDay,
                            onMemberTap      = { m -> selectedProfile = m },
                            onConnectionTap  = { m -> connectionTappedMember = m }
                        )

                        // Day 3: Love is Filling — ceremony unlocks 4h into Day 3
                        // H6: only show when at least 1 active non-you member remains
                        val activeNonYouCount = state.members.count {
                            !it.isYou && it.activityStatus != ActivityStatus.LEFT
                        }
                        if (state.groupDay >= 2 && // TEMP: was >= 3 — restore before prod
                        activeNonYouCount > 0 && state.dayStartedAtMs > 0) {
                            // Seconds remaining until ceremony = 8h after Day 3 started
                            // (fills entire Day 3 window — water reaches 100% exactly when ceremony opens)
                            val CEREMONY_DELAY_MS = 8 * 3_600_000L
                            val currentTime = System.currentTimeMillis()
                            
                            var secsLeft by remember(state.dayStartedAtMs) {
                                val elapsed = (currentTime - state.dayStartedAtMs).coerceAtLeast(0L)
                                val remaining = ((CEREMONY_DELAY_MS - elapsed) / 1000L).coerceAtLeast(0L)
                                mutableStateOf(remaining)
                            }
                            
                            LaunchedEffect(state.dayStartedAtMs) {
                                while (secsLeft > 0) {
                                    kotlinx.coroutines.delay(1_000L)
                                    val elapsed = (System.currentTimeMillis() - state.dayStartedAtMs).coerceAtLeast(0L)
                                    secsLeft = ((CEREMONY_DELAY_MS - elapsed) / 1000L).coerceAtLeast(0L)
                                }
                            }
                            
                            LoveFillingBanner(
                                secsLeft      = secsLeft,
                                totalSecs     = CEREMONY_DELAY_MS / 1000L,
                                onEnter       = onOpenCeremony,
                                modifier      = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                            )
                        }
                    }

                    // ── Private Island Entry Card ──────────────────────────────
                    var partySessionId by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        try {
                            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
                            val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(uid).get().await()
                            val sid = doc.getString("partySessionId")
                            if (!sid.isNullOrBlank()) partySessionId = sid
                        } catch (_: Exception) {}
                    }

                    if (!partySessionId.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF2D5A26), Color(0xFF4E7C47))
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { onOpenPartyIsland(partySessionId!!) }
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("🏝️", fontSize = 22.sp)
                                    Column {
                                        Text(
                                            "Your Private Island",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color      = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize   = 14.sp
                                            )
                                        )
                                        Text(
                                            "Continue your connection",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color    = Color.White.copy(0.65f),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(0.18f))
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        "Enter →",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color      = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize   = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // ── Minimal bottom strip ───────────────────────────────────
                    BottomStrip(
                        day             = state.groupDay,
                        isDay           = isDay,
                        hasNew          = state.hasNewQuestion,
                        onOpenDashboard = { showDashboard = true },
                        onOpenMyQInbox  = onOpenMyQInbox,
                        onDevSetDay     = vm::devSetDay,
                        onLeave         = { showLeaveDialog = true }
                    )
                }

                // ── Profile Sheet ─────────────────────────────────────────────
                selectedProfile?.let { member ->
                    ProfileSheet(
                        member    = member,
                        day       = state.groupDay,
                        isDay     = isDay,
                        onDismiss = { selectedProfile = null }
                    )
                }

                // ── Soul Web Connection Bond Sheet ────────────────────────────
                connectionTappedMember?.let { member ->
                    ConnectionBondSheet(
                        member    = member,
                        day       = state.groupDay,
                        isDay     = isDay,
                        onDismiss = { connectionTappedMember = null }
                    )
                }

                // ── Circle Reveal Overlay ─────────────────────────────────────
                if (state.showCircleReveal) {
                    CircleRevealOverlay(
                        members = state.members,
                        onDone  = vm::markCircleRevealSeen
                    )
                }

                ActivityDashboardOverlay(
                    visible                = showDashboard,
                    day                    = state.groupDay,
                    isDay                  = isDay,
                    hasNew                 = state.hasNewQuestion,
                    day1Pending            = state.day1PromptPending,
                    ghostedCount           = state.ghostedMemberCount,
                    ceremonyUnlockHour     = state.ceremonyUnlockHour,
                    dayStartedAtMs         = state.dayStartedAtMs,
                    onClose                = { showDashboard = false },
                    onOpenQuestions        = onOpenQuestions,
                    onOpenAnswers          = onOpenAnswers,
                    onOpenCeremony         = onOpenCeremony,
                    onOpenTomSetup         = onOpenTomSetup,
                    onOpenSlowChat         = onOpenSlowChat,
                    onOpenWouldYouRather   = onOpenWouldYouRather,
                    onOpenTrueOrMyth       = onOpenTrueOrMyth,
                    onOpenMyQInbox         = onOpenMyQInbox,
                    onOpenAestheticsFeed   = onOpenAestheticsFeed,
                    onOpenVibes            = onOpenVibes,
                    onOpenDareToShare      = onOpenDareToShare,
                    onOpenSecretCompliment = onOpenSecretCompliment
                )
            }
        }

        // ── Leave dialog trigger is now via the bottom strip or swipe back ────────

        // ── C3: Leave Circle dialog ────────────────────────────────────────────
        if (showLeaveDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showLeaveDialog = false }
            ) {
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFFDF8F2))
                        .border(1.dp, Color(0xFFEDD9C0), RoundedCornerShape(28.dp))
                        .padding(28.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF4E8))
                                .border(1.5.dp, Color(0xFFEDD9C0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🚪", fontSize = 26.sp)
                        }
                        Text(
                            "Leave the Circle?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color      = Color(0xFF2D1F0E),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp,
                                textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                        Text(
                            "Your 3 circle members will lose a companion for the rest of this journey. This action cannot be undone.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color     = Color(0xFF7A5C3A),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 20.sp,
                                fontSize  = 13.sp
                            )
                        )
                        HorizontalDivider(color = Color(0xFFF0E4D4), thickness = 0.8.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFE8832A))
                                    .clickable { showLeaveDialog = false }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Stay in the Circle",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFFFF4F4))
                                    .border(1.dp, Color(0xFFFFCCCC), RoundedCornerShape(16.dp))
                                    .clickable { showLeaveDialog = false; vm.leaveGroup() }
                                    .padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Leave Circle",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFFD93025),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }


        // ── Exit App dialog (back-press) ─────────────────────────────────────────
        if (showExitDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showExitDialog = false }
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0E0B1E), Color(0xFF070412))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color(0xFF7C4DFF).copy(0.35f), Color(0xFF7C4DFF).copy(0.10f))
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(28.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🌙", fontSize = 32.sp)
                        Text(
                            "Exit Veil?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color      = Color(0xFFD4BBFF),
                                fontWeight = FontWeight.Bold,
                                textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                        Text(
                            "Your circle is still waiting.\nCome back anytime — your spot is safe.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color     = Color(0xFF9090BB),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.6f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF3A2070), Color(0xFF5C3099))
                                        )
                                    )
                                    .border(0.7.dp, Color(0xFF9E7BFF).copy(0.3f), RoundedCornerShape(14.dp))
                                    .clickable { showExitDialog = false }
                                    .padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Stay in Veil",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFFD4BBFF), fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x12FF4444))
                                    .clickable {
                                        showExitDialog = false
                                        android.os.Process.killProcess(android.os.Process.myPid())
                                    }
                                    .padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Exit",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFFFF8888)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Atmospheric Sky — stars, aurora, moon, shooting star
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PastelNightSky() {
    val inf = rememberInfiniteTransition(label = "nightSky")
    val starTick by inf.animateFloat(
        initialValue  = 0f, targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label         = "starTick"
    )
    val auroraPulse by inf.animateFloat(
        initialValue  = 0.2f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(5_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "auroraPulse"
    )
    val driftPhase by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label         = "driftPhase"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height

        // ── Ambient gradient layers at bottom ────────────────────────────
        drawCircle(
            color  = Color(0xFF0D1B2F).copy(alpha = 0.65f),
            center = Offset(w * -0.05f, h * 0.84f),
            radius = w * 0.72f
        )
        drawCircle(
            color  = Color(0xFF152238).copy(alpha = 0.50f),
            center = Offset(w * 1.02f, h * 0.87f),
            radius = w * 0.78f
        )
        drawCircle(
            color  = Color(0xFF0A1520).copy(alpha = 0.40f),
            center = Offset(w * 0.5f, h * 1.0f),
            radius = w * 0.95f
        )

        // ── Soft nebula wisps (blue/teal tones) ──────────────────────────
        drawOval(
            brush  = Brush.radialGradient(
                listOf(Color(0xFF3B82F6).copy(alpha = auroraPulse * 0.12f), Color.Transparent),
                center = Offset(w * 0.25f, h * 0.18f),
                radius = w * 0.45f
            ),
            topLeft = Offset(w * 0.0f, h * 0.03f),
            size    = Size(w * 0.55f, h * 0.32f)
        )
        drawOval(
            brush  = Brush.radialGradient(
                listOf(Color(0xFF06B6D4).copy(alpha = auroraPulse * 0.08f), Color.Transparent),
                center = Offset(w * 0.78f, h * 0.12f),
                radius = w * 0.38f
            ),
            topLeft = Offset(w * 0.50f, h * 0.02f),
            size    = Size(w * 0.55f, h * 0.25f)
        )

        // ── Moon — silver-blue crescent ──────────────────────────────────
        val mx = w * 0.82f; val my = h * 0.14f
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0xFFCBD5E1).copy(alpha = 0.15f), Color.Transparent),
                center = Offset(mx, my), radius = 95f
            ),
            radius = 95f, center = Offset(mx, my)
        )
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0xFFF1F5F9), Color(0xFFCBD5E1)),
                center = Offset(mx - 5f, my - 4f), radius = 28f
            ),
            radius = 28f, center = Offset(mx, my)
        )
        drawCircle(
            color  = Color(0xFF0F1B2E).copy(alpha = 0.93f),
            radius = 24f,
            center = Offset(mx + 10f, my - 2f)
        )

        // ── Stars — 20 positions, varied sizes ───────────────────────────
        val starPositions = listOf(
            Offset(w*0.06f,h*0.05f) to 2.6f, Offset(w*0.15f,h*0.11f) to 1.6f,
            Offset(w*0.25f,h*0.04f) to 3.0f, Offset(w*0.38f,h*0.08f) to 1.8f,
            Offset(w*0.52f,h*0.03f) to 2.4f, Offset(w*0.60f,h*0.13f) to 1.4f,
            Offset(w*0.70f,h*0.06f) to 2.8f, Offset(w*0.88f,h*0.20f) to 2.0f,
            Offset(w*0.10f,h*0.26f) to 1.4f, Offset(w*0.35f,h*0.20f) to 2.6f,
            Offset(w*0.93f,h*0.09f) to 1.6f, Offset(w*0.04f,h*0.17f) to 2.2f,
            Offset(w*0.48f,h*0.16f) to 1.2f, Offset(w*0.78f,h*0.28f) to 1.8f,
            Offset(w*0.20f,h*0.34f) to 1.0f, Offset(w*0.65f,h*0.32f) to 1.4f,
            Offset(w*0.85f,h*0.38f) to 1.0f, Offset(w*0.42f,h*0.28f) to 1.6f,
            Offset(w*0.96f,h*0.32f) to 1.2f, Offset(w*0.03f,h*0.40f) to 0.9f,
        )
        starPositions.forEachIndexed { i, (pos, r) ->
            val phase = (i * 0.62f + starTick) % 6.28318f
            val alpha = ((sin(phase) + 1f) / 2f) * 0.65f + 0.2f
            // Soft glow
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = alpha * 0.4f), Color.Transparent),
                    center = pos, radius = r * 3f
                ),
                radius = r * 3f, center = pos
            )
            // Sharp star core
            drawCircle(color = Color.White.copy(alpha = alpha * 0.85f), radius = r, center = pos)
        }

        // ── Floating fireflies (warm gold dots drifting slowly) ──────────
        listOf(
            0.15f to 0.45f, 0.35f to 0.55f, 0.55f to 0.42f,
            0.75f to 0.50f, 0.88f to 0.48f, 0.25f to 0.62f
        ).forEachIndexed { i, (xf, yf) ->
            val dPhase = (driftPhase + i * 0.17f) % 1f
            val dy = sin(dPhase * 6.28318f) * 12f
            val fa = (sin((dPhase + i * 0.3f) * 6.28318f) + 1f) / 2f * 0.5f + 0.1f
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(Color(0xFFFFBB55).copy(alpha = fa * 0.6f), Color.Transparent),
                    center = Offset(w * xf, h * yf + dy), radius = 8f
                ),
                radius = 8f, center = Offset(w * xf, h * yf + dy)
            )
            drawCircle(
                color  = Color(0xFFFFBB55).copy(alpha = fa * 0.9f),
                radius = 1.5f, center = Offset(w * xf, h * yf + dy)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pastel Day Sky — soft minimal clouds and rounded shapes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PastelDaySky() {
    val inf = rememberInfiniteTransition(label = "daySky")
    val cloudDrift by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label         = "cloudDrift"
    )
    val sunPulse by inf.animateFloat(
        initialValue  = 0.75f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(4_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "sunPulse"
    )
    val dustDrift by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label         = "dustDrift"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height

        // ── Ambient warm gradient layers ──────────────────────────────────
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0xFFE8E0F0).copy(alpha = 0.3f), Color.Transparent),
                center = Offset(w * 0.2f, h * 0.82f), radius = w * 0.7f
            ),
            radius = w * 0.7f, center = Offset(w * 0.2f, h * 0.82f)
        )
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0xFFFFE8D0).copy(alpha = 0.25f), Color.Transparent),
                center = Offset(w * 0.85f, h * 0.80f), radius = w * 0.65f
            ),
            radius = w * 0.65f, center = Offset(w * 0.85f, h * 0.80f)
        )

        // ── Premium golden sun ───────────────────────────────────────────
        val sx = w * 0.82f; val sy = h * 0.11f
        // Wide ambient glow
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(
                    Color(0xFFFFBB55).copy(alpha = sunPulse * 0.12f),
                    Color(0xFFFF9B40).copy(alpha = sunPulse * 0.04f),
                    Color.Transparent
                ),
                center = Offset(sx, sy), radius = 200f
            ),
            radius = 200f, center = Offset(sx, sy)
        )
        // Mid halo
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0xFFFFE0A0).copy(alpha = 0.45f), Color.Transparent),
                center = Offset(sx, sy), radius = 85f
            ),
            radius = 85f, center = Offset(sx, sy)
        )
        // Sun disc
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0xFFFFFBF0), Color(0xFFFFCC70).copy(alpha = 0.95f)),
                center = Offset(sx, sy), radius = 32f
            ),
            radius = 32f, center = Offset(sx, sy)
        )

        // ── Minimal glass clouds (clean, modern) ─────────────────────────
        val drift = cloudDrift * 14f
        // Cloud 1 — soft wide
        drawRoundRect(
            brush  = Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.15f))
            ),
            topLeft      = Offset(w * 0.05f + drift, h * 0.12f),
            size         = Size(180f, 36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )
        // Cloud 2 — smaller accent
        drawRoundRect(
            brush  = Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.12f))
            ),
            topLeft      = Offset(w * 0.58f - drift * 0.4f, h * 0.19f),
            size         = Size(130f, 28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
        )

        // ── Floating light particles (sunlit dust motes) ─────────────────
        listOf(
            0.12f to 0.22f, 0.28f to 0.15f, 0.45f to 0.30f,
            0.62f to 0.18f, 0.78f to 0.35f, 0.90f to 0.25f,
            0.20f to 0.45f, 0.55f to 0.42f, 0.38f to 0.52f,
            0.72f to 0.48f, 0.85f to 0.55f, 0.08f to 0.38f,
        ).forEachIndexed { i, (xf, yf) ->
            val dPhase = (dustDrift + i * 0.083f) % 1f
            val dy = sin(dPhase * 6.28318f) * 8f
            val dx = cos(dPhase * 6.28318f + i) * 4f
            val fa = (sin((dPhase + i * 0.25f) * 6.28318f) + 1f) / 2f * 0.35f + 0.08f
            val radius = 1.2f + (i % 3) * 0.4f
            drawCircle(
                color  = Color(0xFFFFBB55).copy(alpha = fa),
                radius = radius,
                center = Offset(w * xf + dx, h * yf + dy)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────────
//  Top bar — clean, breathable. Day pill opens the timeline dialog.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IslandTopBar(
    day                : Int,
    isDay              : Boolean = false,
    onOpenDayTimeline  : () -> Unit = {},
    onOpenDevInspector : () -> Unit = {}
) {
    var devTapCount by remember { mutableStateOf(0) }
    val pillBg   = if (isDay) Color(0xFFFFFFFF).copy(alpha = 0.88f) else Color(0xFF1A2640).copy(alpha = 0.90f)
    val dayColor = when (day) { 1 -> Color(0xFF34D399); 2 -> Color(0xFF818CF8); else -> Color(0xFFFFBB55) }
    val labelCol = if (isDay) Color(0xFF475569) else Color(0xFFCBD5E1)
    val wordCol  = if (isDay) Color(0xFF4A5568).copy(alpha = 0.80f) else Color(0xFFCBD5E1).copy(alpha = 0.70f)
    val inf  = rememberInfiniteTransition(label = "tg")
    val glow by inf.animateFloat(0.45f, 1f, infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), "tg")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Tappable Day pill — opens timeline dialog
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(pillBg)
                .border(1.2.dp, Brush.sweepGradient(listOf(dayColor.copy(glow * 0.55f), dayColor.copy(0.12f), dayColor.copy(glow * 0.55f))), RoundedCornerShape(50.dp))
                .clickable { onOpenDayTimeline() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Brush.radialGradient(listOf(dayColor, dayColor.copy(0.3f)))))
                Text(
                    text = when (day) { 1 -> "Day 1  · Discover"; 2 -> "Day 2  · Depth"; else -> "Day 3  · Bond" },
                    style = MaterialTheme.typography.labelMedium.copy(color = labelCol, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                )
                Text(">", style = MaterialTheme.typography.labelMedium.copy(color = dayColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold))
            }
        }
        // Veil wordmark
        Text(
            "Veil",
            style = MaterialTheme.typography.labelLarge.copy(color = wordCol, letterSpacing = 3.sp, fontSize = 18.sp, fontWeight = FontWeight.Black),
            modifier = Modifier.clickable { devTapCount++; if (devTapCount >= 5) { devTapCount = 0; onOpenDevInspector() } }
        )
        // Balance spacer keeps wordmark visually centered
        Box(modifier = Modifier.size(width = 105.dp, height = 1.dp))
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  DayTimelineDialog — premium modal showing 3-day journey + countdown
// ─────────────────────────────────────────────────────────────────────────────

private data class DayInfo(val n: Int, val title: String, val sub: String, val col: Color)

@Composable
private fun DayTimelineDialog(
    day            : Int,
    isDay          : Boolean,
    activeMembers  : Int,
    warningMembers : Int,
    leftMembers    : Int,
    countdownMs    : Long,
    onDismiss      : () -> Unit
) {
    val day1Color = Color(0xFF34D399)
    val day2Color = Color(0xFF818CF8)
    val day3Color = Color(0xFFFFBB55)
    val dialogBg  = if (isDay) Color(0xFFF8FAFC) else Color(0xFF0D1B2E)
    val surface   = if (isDay) Color(0xFFFFFFFF).copy(0.85f) else Color(0xFF1A2640).copy(0.80f)
    val border    = if (isDay) Color(0xFF3B82F6).copy(0.12f) else Color(0xFF60A5FA).copy(0.18f)
    val textPri   = if (isDay) Color(0xFF0F172A) else Color(0xFFF0F4FA)
    val textSec   = if (isDay) Color(0xFF64748B) else Color(0xFF94A3B8)
    val curDayCol = when (day) { 1 -> day1Color; 2 -> day2Color; else -> day3Color }

    val inf  = rememberInfiniteTransition(label = "dlgG")
    val glow by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "g")

    val totalSec = countdownMs / 1000
    val countdownStr = when {
        totalSec / 3600 > 0  -> "${totalSec / 3600}h ${(totalSec % 3600) / 60}m remaining"
        totalSec / 60   > 0  -> "${totalSec / 60}m ${totalSec % 60}s remaining"
        else                 -> "< 1 minute remaining"
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(dialogBg, if (isDay) Color(0xFFF1F5F9) else Color(0xFF0A1628))))
                .border(1.dp, border, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {

                // Header
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Your Journey", style = MaterialTheme.typography.titleMedium.copy(color = textPri, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
                        Text("3 days  ·  3 strangers  ·  1 bond", style = MaterialTheme.typography.labelSmall.copy(color = textSec, fontSize = 11.sp))
                    }
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(if (isDay) Color.Black.copy(0.06f) else Color.White.copy(0.08f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) { Text("x", style = MaterialTheme.typography.labelLarge.copy(color = textSec, fontSize = 15.sp, fontWeight = FontWeight.Bold)) }
                }

                // Day timeline rows
                val days = listOf(
                    DayInfo(1, "Day 1 — Discover", "Anonymous. No names, no photos. Only personality.", day1Color),
                    DayInfo(2, "Day 2 — Depth",    "Profile pictures revealed. Deeper questions unlock.", day2Color),
                    DayInfo(3, "Day 3 — Bond",     "The veil lifts. The strongest bond earns a private island.", day3Color)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    days.forEachIndexed { idx, info ->
                        val isCur  = info.n == day
                        val isPast = info.n < day
                        val alpha  = if (info.n > day) 0.42f else 1f
                        Row(
                            modifier = Modifier.fillMaxWidth().alpha(alpha)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCur) Brush.horizontalGradient(listOf(info.col.copy(0.13f), info.col.copy(0.05f))) else Brush.horizontalGradient(listOf(surface, surface)))
                                .border(if (isCur) 1.5.dp else 1.dp,
                                    if (isCur) Brush.horizontalGradient(listOf(info.col.copy(glow * 0.65f), info.col.copy(glow * 0.2f)))
                                    else Brush.horizontalGradient(listOf(border, border)),
                                    RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp).clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(info.col.copy(if (isPast || isCur) 0.22f else 0.08f), Color.Transparent)))
                                    .border(1.dp, info.col.copy(if (isPast || isCur) 0.60f else 0.22f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isPast) "v" else "${info.n}", style = MaterialTheme.typography.labelMedium.copy(color = info.col, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(info.title, style = MaterialTheme.typography.labelMedium.copy(color = if (isCur) info.col else textPri, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                    if (isCur) Box(Modifier.clip(RoundedCornerShape(50.dp)).background(info.col.copy(0.18f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text("You are here", style = MaterialTheme.typography.labelSmall.copy(color = info.col, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold))
                                    }
                                }
                                Text(info.sub, style = MaterialTheme.typography.labelSmall.copy(color = textSec, fontSize = 11.sp, lineHeight = 16.sp))
                            }
                        }
                        if (idx < days.size - 1) {
                            Box(Modifier.padding(start = 29.dp).width(2.dp).height(7.dp)
                                .background(Brush.verticalGradient(listOf(days[idx].col.copy(0.38f), days[idx+1].col.copy(0.22f)))))
                        }
                    }
                }

                // Photo reveal note (Day 1 only)
                if (day == 1) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                        .background(Brush.horizontalGradient(listOf(day2Color.copy(0.12f), day2Color.copy(0.05f))))
                        .border(1.dp, day2Color.copy(0.28f), RoundedCornerShape(13.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("!", fontSize = 18.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Profile pictures revealed on Day 2", style = MaterialTheme.typography.labelMedium.copy(color = day2Color, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                            Text("For now, only your personality speaks.", style = MaterialTheme.typography.labelSmall.copy(color = textSec, fontSize = 10.sp))
                        }
                    }
                }

                // Live member status
                val inf2 = rememberInfiniteTransition(label = "dd")
                val dp by inf2.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse), "dp")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(surface)
                    .border(1.dp, border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF22C55E).copy(dp), Color(0xFF16A34A).copy(dp * 0.4f)))))
                        Text(when { activeMembers == 0 -> "Just you right now"; activeMembers == 1 -> "You + 1 active"; else -> "You + $activeMembers active" },
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                    }
                    if (warningMembers > 0) Text("  $warningMembers away", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B), fontSize = 11.sp))
                }

                // Countdown (Day 1 & 2 only)
                if (day < 3 && countdownMs > 0) {
                    val nextCol = if (day == 1) day2Color else day3Color
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(nextCol.copy(0.10f), nextCol.copy(0.04f))))
                        .border(1.dp, Brush.horizontalGradient(listOf(nextCol.copy(glow * 0.55f), nextCol.copy(glow * 0.15f))), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("T", fontSize = 16.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Day ${day + 1} unlocks in", style = MaterialTheme.typography.labelSmall.copy(color = textSec, fontSize = 10.sp))
                            Text(countdownStr, style = MaterialTheme.typography.labelLarge.copy(color = nextCol, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp))
                        }
                    }
                }

                // Dismiss button
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(curDayCol.copy(0.20f), curDayCol.copy(0.08f))))
                        .border(1.dp, curDayCol.copy(0.40f), RoundedCornerShape(14.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Got it  ok", style = MaterialTheme.typography.labelLarge.copy(color = curDayCol, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                }
            }
        }
    }
}

@Composable
private fun GlassPill(text: String, accent: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(
                if (accent) Color(0xFF3B82F6).copy(alpha = 0.15f)
                else Color.White.copy(alpha = 0.06f)
            )
            .border(
                0.7.dp,
                if (accent) Color(0xFF3B82F6).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color    = if (accent) Color(0xFFCBD5E1) else Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Soul Web Panel — 4 members connected to a glowing central orb
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SoulWebPanel(
    members          : List<IslandMember>,
    day              : Int,
    isDay            : Boolean = false,
    onMemberTap      : (IslandMember) -> Unit = {},
    onConnectionTap  : (IslandMember) -> Unit = {}
) {
    val inf  = rememberInfiniteTransition(label = "web")

    // Central orb pulse
    val orbPulse by inf.animateFloat(
        initialValue  = 0.82f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "orbP"
    )
    // Particle flow phase
    val flowPhase by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart),
        label         = "flow"
    )
    // Secondary slower drift
    val drift by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_800, easing = LinearEasing), RepeatMode.Restart),
        label         = "drift"
    )
    // ── SINE WAVE PHASE — drives morphing line wiggle ─────────────────────────
    val sinePhase by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart),
        label         = "sinePhase"
    )
    // ── ORBITAL PARTICLE ANGLES — 3 dots at different angular speeds ──────────
    val orbit1 by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart),
        label         = "orb1"
    )
    val orbit2 by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Restart),
        label         = "orb2"
    )
    val orbit3 by inf.animateFloat(
        initialValue  = (2.0 * Math.PI / 3.0).toFloat(),
        targetValue   = ((2.0 * Math.PI / 3.0) + 2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3_600, easing = LinearEasing), RepeatMode.Restart),
        label         = "orb3"
    )

    // ── Soul Web palette ──────────────────────────────────────────────────────
    val orbColor = when {
        isDay && day == 1 -> Color(0xFF34D399)
        isDay && day == 2 -> Color(0xFF818CF8)
        isDay             -> Color(0xFFFFBB55)
        day == 1          -> Color(0xFF5EEAD4)
        day == 2          -> Color(0xFFA78BFA)
        else              -> Color(0xFFFFCC70)
    }
    val lineColor = if (isDay) Color(0xFF3B82F6).copy(alpha = 0.40f) else Color(0xFF60A5FA).copy(alpha = 0.35f)
    val glowColor = if (isDay) Color(0xFFFF6B9D).copy(alpha = 0.65f) else Color(0xFFFF8FAB).copy(alpha = 0.55f)
    val orbBg     = if (isDay) Color(0xFFF5F8FA) else Color(0xFF141E30)

    // ── PER-MEMBER RIPPLE STATE — Animatable 0→1 per avatar ──────────────────
    val rippleProgress = remember { List(4) { androidx.compose.animation.core.Animatable(0f) } }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier         = Modifier.size(340.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Center glow halo
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(
                        orbColor.copy(alpha = orbPulse * 0.25f),
                        orbColor.copy(alpha = orbPulse * 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy), radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f, center = Offset(cx, cy)
            )

            // Diamond member positions
            val r = size.width * 0.38f
            val memberCenters = listOf(
                Offset(cx, cy - r),
                Offset(cx - r, cy),
                Offset(cx + r, cy),
                Offset(cx, cy + r)
            )

            // ── SINE-WAVE MORPHING CONNECTION LINES ───────────────────────────
            memberCenters.forEachIndexed { i, mc ->
                val phase  = (i * 0.25f + flowPhase) % 1f
                val phase2 = (i * 0.25f + drift + 0.5f) % 1f
                val phase3 = (i * 0.25f + flowPhase * 0.7f + 0.3f) % 1f

                val dx      = cx - mc.x
                val dy      = cy - mc.y
                val pathLen = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                // Amplitude breathes slightly per member
                val amplitude = pathLen * 0.055f *
                    (1f + 0.3f * sin((sinePhase + i * 1.1f).toDouble()).toFloat())
                // Perpendicular direction for lateral displacement
                val perpX = -dy / pathLen
                val perpY =  dx / pathLen

                // 32-step sine path from member → center
                val wavePath = Path()
                val steps = 32
                for (step in 0..steps) {
                    val t    = step / steps.toFloat()
                    val bx   = mc.x + dx * t
                    val by_  = mc.y + dy * t
                    val wave = amplitude *
                        sin((t * Math.PI * 2.0 + sinePhase + i * 0.8).toDouble()).toFloat() *
                        sin((t * Math.PI).toDouble()).toFloat() // envelope: 0 at both ends
                    val ptX  = bx + perpX * wave
                    val ptY  = by_ + perpY * wave
                    if (step == 0) wavePath.moveTo(ptX, ptY) else wavePath.lineTo(ptX, ptY)
                }

                // Outer glow stroke
                drawPath(
                    path  = wavePath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, lineColor.copy(alpha = 0.18f), lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.18f), Color.Transparent),
                        start  = mc, end = Offset(cx, cy)
                    ),
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )
                // Core bright stroke
                drawPath(
                    path  = wavePath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, lineColor.copy(alpha = 0.50f), glowColor.copy(alpha = 0.60f), lineColor.copy(alpha = 0.50f), Color.Transparent),
                        start  = mc, end = Offset(cx, cy)
                    ),
                    style = Stroke(width = 2.8f, cap = StrokeCap.Round)
                )

                // Helper: get the wavy point at parameter t
                fun sinePoint(t: Float): Offset {
                    val bx  = mc.x + dx * t
                    val by_ = mc.y + dy * t
                    val w   = amplitude *
                        sin((t * Math.PI * 2.0 + sinePhase + i * 0.8).toDouble()).toFloat() *
                        sin((t * Math.PI).toDouble()).toFloat()
                    return Offset(bx + perpX * w, by_ + perpY * w)
                }

                // Flowing particle 1 — main, rides the sine curve
                val p1  = sinePoint(phase)
                val a1  = (1f - abs(phase - 0.5f) * 2) * 0.9f
                drawCircle(brush = Brush.radialGradient(listOf(glowColor.copy(alpha = a1), Color.Transparent), center = p1, radius = 12f), radius = 12f, center = p1)
                drawCircle(color = glowColor.copy(alpha = a1), radius = 4.5f, center = p1)
                val p1t = sinePoint((phase - 0.05f).coerceAtLeast(0f))
                drawCircle(color = glowColor.copy(alpha = a1 * 0.3f), radius = 2.5f, center = p1t)

                // Flowing particle 2 — secondary
                val p2 = sinePoint(phase2)
                val a2 = (1f - abs(phase2 - 0.5f) * 2) * 0.65f
                drawCircle(color = lineColor.copy(alpha = a2), radius = 3.2f, center = p2)

                // Flowing particle 3 — subtle
                val p3 = sinePoint(phase3)
                val a3 = (1f - abs(phase3 - 0.5f) * 2) * 0.4f
                drawCircle(color = orbColor.copy(alpha = a3), radius = 2f, center = p3)

                // Member outer glow halo
                drawCircle(
                    brush  = Brush.radialGradient(listOf(orbColor.copy(alpha = orbPulse * 0.18f), Color.Transparent), center = mc, radius = 75f),
                    radius = 75f, center = mc
                )

                // ── RIPPLE RINGS per member ───────────────────────────────────
                val rp = rippleProgress.getOrNull(i)?.value ?: 0f
                if (rp > 0.001f) {
                    for (ring in 0..2) {
                        val ringT = ((rp - ring * 0.25f).coerceIn(0f, 1f))
                        if (ringT > 0.001f) {
                            drawCircle(
                                color  = orbColor.copy(alpha = (1f - ringT) * 0.55f),
                                radius = 74f * ringT,
                                center = mc,
                                style  = Stroke(width = (2.5f - ring * 0.6f).coerceAtLeast(0.5f))
                            )
                        }
                    }
                }
            }

            // Cross-connections (subtle diamond web)
            if (memberCenters.size >= 4) {
                listOf(0 to 1, 0 to 2, 1 to 3, 2 to 3).forEach { (a, b) ->
                    drawLine(color = lineColor.copy(alpha = 0.08f), start = memberCenters[a], end = memberCenters[b], strokeWidth = 0.8f, cap = StrokeCap.Round)
                }
            }

            // Central orb
            drawCircle(brush = Brush.radialGradient(listOf(orbColor.copy(alpha = orbPulse * 0.22f), Color.Transparent), center = Offset(cx, cy), radius = 52f * orbPulse), radius = 52f * orbPulse, center = Offset(cx, cy))
            drawCircle(brush = Brush.radialGradient(listOf(orbBg, orbColor.copy(alpha = 0.45f)), center = Offset(cx, cy), radius = 36f), radius = 36f, center = Offset(cx, cy))
            drawCircle(brush = Brush.radialGradient(listOf(glowColor.copy(alpha = orbPulse * 0.25f), Color.Transparent), center = Offset(cx - 4f, cy - 4f), radius = 20f), radius = 20f, center = Offset(cx - 4f, cy - 4f))
            drawCircle(color = glowColor.copy(alpha = orbPulse * 0.75f), radius = 36f, center = Offset(cx, cy), style = Stroke(width = 2.2f))

            // ── ORBITAL MINI-PARTICLES — 3 electrons orbiting center orb ─────
            val orbRadii  = listOf(46f, 55f, 50f)
            val orbAngles = listOf(orbit1, orbit2, orbit3)
            val orbColors = listOf(orbColor.copy(alpha = 0.92f), glowColor.copy(alpha = 0.78f), lineColor.copy(alpha = 0.82f))
            val orbSizes  = listOf(4.0f, 3.0f, 3.6f)
            for (k in 0..2) {
                val ang   = orbAngles[k]
                val dotX  = cx + orbRadii[k] * cos(ang.toDouble()).toFloat()
                val dotY  = cy + orbRadii[k] * sin(ang.toDouble()).toFloat()
                val col   = orbColors[k]
                val sz    = orbSizes[k]
                drawCircle(brush = Brush.radialGradient(listOf(col, Color.Transparent), center = Offset(dotX, dotY), radius = sz * 3.2f), radius = sz * 3.2f, center = Offset(dotX, dotY))
                drawCircle(color = col, radius = sz, center = Offset(dotX, dotY))
                val tAng  = ang - 0.24f
                val trX   = cx + orbRadii[k] * cos(tAng.toDouble()).toFloat()
                val trY   = cy + orbRadii[k] * sin(tAng.toDouble()).toFloat()
                drawCircle(color = col.copy(alpha = col.alpha * 0.28f), radius = sz * 0.65f, center = Offset(trX, trY))
            }
        }

        // Central orb: empty — only canvas-drawn glow is shown
        Box(modifier = Modifier.size(68.dp))

        // ── 4 Member Avatar Circles at diamond corners ────────────────────────
        val sizeVal   = 340f
        val radius    = sizeVal * 0.38f
        val cx0       = sizeVal / 2f
        val cy0       = sizeVal / 2f
        val positions = listOf(
            Offset(cx0, cy0 - radius),
            Offset(cx0 - radius, cy0),
            Offset(cx0 + radius, cy0),
            Offset(cx0, cy0 + radius)
        )

        members.take(4).forEachIndexed { i, member ->
            val pos  = positions[i]
            val xOff = (pos.x - cx0).dp
            val yOff = (pos.y - cy0).dp
            AvatarCircle(
                member   = member,
                index    = i,
                isDay    = isDay,
                onTap    = {
                    // Fire ripple animation, then navigate
                    coroutineScope.launch {
                        rippleProgress.getOrNull(i)?.let { anim ->
                            anim.snapTo(0f)
                            anim.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
                            anim.snapTo(0f)
                        }
                    }
                    onMemberTap(member)
                },
                modifier = Modifier.offset(x = xOff, y = yOff)
            )

            // Invisible tap target at midpoint of connection line
            val midX = ((pos.x + cx0) / 2f - cx0).dp
            val midY = ((pos.y + cy0) / 2f - cy0).dp
            Box(
                modifier = Modifier
                    .offset(x = midX, y = midY)
                    .size(56.dp)
                    .background(Color.Transparent)
                    .clickable(
                        indication        = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { onConnectionTap(member) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Avatar circle — premium with layered rings + question badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AvatarCircle(
    member   : IslandMember,
    index    : Int,
    isDay    : Boolean = false,
    onTap    : () -> Unit = {},
    modifier : Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "av_$index")
    val pulse by inf.animateFloat(
        initialValue  = 0.93f,
        targetValue   = 1.07f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_600 + index * 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_$index"
    )
    // Outer ring glow for "You"
    val ring by inf.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_$index"
    )
    // Veil shimmer for Day 2
    val veilShimmer by inf.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_400 + index * 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "veilShimmer_$index"
    )

    val day = member.day

    val activityBorderColor = when (member.activityStatus) {
        ActivityStatus.GHOSTED -> Color(0xFFFF4020)
        ActivityStatus.WARNING -> Color(0xFFFF8040)
        ActivityStatus.LEFT    -> Color(0xFF888888)
        ActivityStatus.ACTIVE  -> null
    }

    // Disable clicking on Day 1 except for "You"
    val clickModifier = if (day == 1 && !member.isYou) modifier
                        else modifier.clickable { onTap() }


    Box(modifier = clickModifier, contentAlignment = Alignment.TopCenter) {

        // LEFT member: faded ghost treatment
        val isLeft = member.activityStatus == ActivityStatus.LEFT

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = if (isLeft) Modifier.alpha(0.35f) else Modifier
        ) {
            // Outer glow ring for "You" avatar
            if (member.isYou) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF00D9FF).copy(alpha = ring * 0.40f),
                                    Color(0xFF00FFB3).copy(alpha = ring * 0.18f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarCore(member = member, size = 58.dp, day = day, isDay = isDay)
                }
                // Green online dot for "You"
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .offset(x = 22.dp, y = (-14).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                        .border(1.5.dp, if (isDay) Color.White else Color(0xFF141E30), CircleShape)
                )
            } else if (isLeft) {
                // LEFT member: gray desaturated avatar
                Box(
                    modifier = Modifier
                        .scale(0.85f)
                        .border(1.5.dp, Color(0xFF888888).copy(alpha = 0.5f), CircleShape)
                        .padding(3.dp)
                ) {
                    AvatarCore(member = member, size = 48.dp, day = day, isDay = isDay)
                }
            } else {
                // Simplified elegant border styling - no odd circles
                val borderColor = when {
                    activityBorderColor != null -> activityBorderColor
                    day >= 3 -> Color(0xFFFFD77A)
                    day == 2 -> Color(0xFF9B6DFF)
                    else -> Color(0xFF00D9FF)
                }
                
                Box(
                    modifier = Modifier
                        .scale(pulse)
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    borderColor.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .border(2.dp, borderColor.copy(alpha = 0.6f), CircleShape)
                    ) {
                        AvatarCore(member = member, size = 52.dp, day = day, isDay = isDay)
                    }
                }
                
                // Green online dot for active members
                if (member.activityStatus == ActivityStatus.ACTIVE) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .offset(x = 20.dp, y = (-12).dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                            .border(1.dp, if (isDay) Color.White else Color(0xFF141E30), CircleShape)
                    )
                }
            }

            // Label: alias from Day 1 for everyone
            val label = when {
                isLeft       -> member.alias.take(8)
                member.isYou -> "You"
                else         -> member.alias.take(10)
            }
            val labelColor = when {
                isLeft       -> Color(0xFFFF6666)
                member.isYou -> if (isDay) Color(0xFF3B82F6) else Color(0xFF60A5FA)
                day >= 3     -> if (isDay) Color(0xFFB45309) else Color(0xFFFFCC70)
                day == 2     -> if (isDay) Color(0xFF4338CA) else Color(0xFFA5B4FC)
                else         -> if (isDay) Color(0xFF1E7A6D).copy(alpha = 0.9f) else Color(0xFFE2E8F0).copy(alpha = 0.9f)
            }
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = labelColor,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )

            // Activity badge for non-you members
            if (!member.isYou && member.activityStatus != ActivityStatus.ACTIVE) {
                Text(
                    text  = when (member.activityStatus) {
                        ActivityStatus.LEFT    -> "Left"
                        ActivityStatus.GHOSTED -> "👻"
                        ActivityStatus.WARNING -> "💤"
                        else -> ""
                    },
                    fontSize = if (member.activityStatus == ActivityStatus.LEFT) 9.sp else 10.sp,
                    color = when (member.activityStatus) {
                        ActivityStatus.LEFT -> Color(0xFFFF6666)
                        else                -> Color.Unspecified
                    },
                    fontWeight = if (member.activityStatus == ActivityStatus.LEFT) FontWeight.Bold else null
                )
            }
        }

        // ── Question posted badge ─────────────────────────────────────────────
        if (member.hasPostedQuestion && !isLeft) {
            QuestionBadge(modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MemberPhoto — displays a profile photo from either a data: URI (Base64)
//  or a regular HTTPS URL. Coil doesn't support data: URIs natively,
//  so we decode Base64 photos ourselves via BitmapFactory.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemberPhoto(
    photoUrl    : String,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
    modifier    : Modifier = Modifier
) {
    when {
        photoUrl.startsWith("data:image") -> {
            // Base64 data URI — decode to Bitmap and display with Image()
            val imageBitmap = remember(photoUrl) {
                try {
                    val base64 = photoUrl.substringAfter("base64,")
                    val bytes  = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    val bmp    = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bmp?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            if (imageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap             = imageBitmap,
                    contentDescription = null,
                    contentScale       = contentScale,
                    modifier           = modifier
                )
            }
            // If decode failed, show nothing (fallback to emoji handled by caller)
        }
        photoUrl.isNotBlank() -> {
            // Regular HTTPS URL — use Coil as normal
            coil.compose.AsyncImage(
                model              = photoUrl,
                contentDescription = null,
                contentScale       = contentScale,
                modifier           = modifier
            )
        }
        // blank → caller shows emoji instead
    }
}

@Composable
private fun AvatarCore(member: IslandMember, size: Dp, day: Int = 1, isDay: Boolean = true) {

    // Day 1: Show placeholder images from PP folder
    // Day 2+: Show actual profile pictures with 2-second reveal animation
    val showPhoto = !member.isBot && (day >= 1)
    
    // Get placeholder image based on gender for Day 1
    val placeholderRes = when (member.gender.lowercase()) {
        "male" -> if (member.uid.hashCode() % 2 == 0) R.drawable.m1 else R.drawable.m2
        "female" -> if (member.uid.hashCode() % 2 == 0) R.drawable.f1 else R.drawable.f2
        else -> if (member.uid.hashCode() % 2 == 0) R.drawable.m1 else R.drawable.f1
    }

    // On Day 2, trigger 2-second reveal animation
    var revealed by remember(member.uid, day) { mutableStateOf(day >= 3) }
    LaunchedEffect(member.uid, day) {
        if (day == 2 && !revealed) {
            kotlinx.coroutines.delay(200L + (member.uid.hashCode().toLong().and(0x7F)))
            revealed = true
        } else if (day >= 3) {
            revealed = true
        }
    }
    
    val blurRadius by animateFloatAsState(
        targetValue   = if (day == 1 || revealed) 0f else 12f,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label         = "blur_${member.uid}"
    )
    val photoAlpha by animateFloatAsState(
        targetValue   = if (revealed || day == 1) 1f else 0.3f,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label         = "alpha_${member.uid}"
    )

    // Premium background gradients per day
    val bgColors = when {
        member.isYou -> if (isDay)
            listOf(Color(0xFFF5F0FF), Color(0xFFE8E0F8))
        else
            listOf(Color(0xFF1E2A44), Color(0xFF162036))
        day >= 3     -> if (isDay)
            listOf(Color(0xFFFFF8E8), Color(0xFFFFECC0))
        else
            listOf(Color(0xFF2A2010), Color(0xFF3A2C18))
        day == 2     -> if (isDay)
            listOf(Color(0xFFF0EAFF), Color(0xFFE4DBFF))
        else
            listOf(Color(0xFF1A1E38), Color(0xFF242848))
        else         -> if (isDay)
            listOf(Color(0xFFF5F8FA), Color(0xFFEAF0F4))
        else
            listOf(Color(0xFF141E2A), Color(0xFF1A2636))
    }

    // Premium border — thick sweep gradient
    val borderBrush = when {
        member.isYou -> Brush.sweepGradient(listOf(
            Color(0xFF7C3AED), Color(0xFFFF6B9D), Color(0xFF38BDF8), Color(0xFF7C3AED)
        ))
        day >= 3     -> Brush.sweepGradient(listOf(
            Color(0xFFFFBB55), Color(0xFFFFF5CC), Color(0xFFFFCC70), Color(0xFFFFBB55)
        ))
        day == 2     -> Brush.sweepGradient(listOf(
            Color(0xFF818CF8), Color(0xFFA78BFA), Color(0xFF60A5FA), Color(0xFF818CF8)
        ))
        else         -> Brush.sweepGradient(listOf(
            Color(0xFF34D399), Color(0xFFA7F3D0), Color(0xFF38BDF8), Color(0xFF34D399)
        ))
    }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.radialGradient(bgColors))
                .border(width = 2.dp, brush = borderBrush, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (showPhoto) {
                // Day 1: Show placeholder image from PP folder
                // Day 2+: Show actual profile picture with reveal animation
                if (day == 1) {
                    // Show placeholder image
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(placeholderRes),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Day 2+: Show actual photo with reveal animation
                    var loadFailed by remember(member.photoUrl) { mutableStateOf(false) }
                    if (!loadFailed && member.photoUrl.isNotBlank()) {
                        MemberPhoto(
                            photoUrl     = member.photoUrl,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier     = Modifier
                                .fillMaxSize()
                                .alpha(photoAlpha)
                                .then(if (blurRadius > 0.5f) Modifier.blur(blurRadius.dp) else Modifier)
                        )
                    } else {
                        // Fallback to emoji if photo fails to load
                        Text(text = member.emoji, fontSize = if (member.isYou) 28.sp else 22.sp)
                    }
                }
            } else {
                Text(text = member.emoji, fontSize = if (member.isYou) 28.sp else 22.sp)
            }
        }

        // "NEW" pulse badge for Day 2
        if (day == 2 && showPhoto && !member.isYou) {
            val inf  = rememberInfiniteTransition(label = "newBadge_${member.uid}")
            val glow by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ng_${member.uid})")
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF818CF8).copy(alpha = glow)),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", fontSize = 7.sp, color = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Profile Popup — centred card dialog when tapping a member circle
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSheet(
    member    : IslandMember,
    day       : Int,
    isDay     : Boolean = true,
    onDismiss : () -> Unit
) {
    // Day 1: Show placeholder, Day 2+: Show actual photo
    val showPhoto = !member.isBot
    
    // Get placeholder image based on gender for Day 1
    val placeholderRes = when (member.gender.lowercase()) {
        "male" -> if (member.uid.hashCode() % 2 == 0) R.drawable.m1 else R.drawable.m2
        "female" -> if (member.uid.hashCode() % 2 == 0) R.drawable.f1 else R.drawable.f2
        else -> if (member.uid.hashCode() % 2 == 0) R.drawable.m1 else R.drawable.f1
    }

    val chipColors = listOf(
        Color(0xFF7C4DFF), Color(0xFF5EEAD4), Color(0xFFFFBB55), Color(0xFFFF8FAB)
    )

    // Scale-spring pop-in animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val popScale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0.82f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "popScale"
    )
    val popAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label         = "popAlpha"
    )

    // Pulsing ring
    val inf       = rememberInfiniteTransition(label = "profileRing")
    val ringPulse by inf.animateFloat(
        initialValue  = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "rp"
    )

    val cardBg    = if (isDay) Color(0xFFF5F3FF) else Color(0xFF13102A)
    val cardBg2   = if (isDay) Color(0xFFEDE9FF) else Color(0xFF1C1840)
    val textCol   = if (isDay) Color(0xFF211540) else Color(0xFFEDE9FF)
    val subCol    = if (isDay) Color(0xFF5A4A80) else Color(0xFF9E92CC)
    val accentCol = Color(0xFF7C4DFF)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(popScale)
                .alpha(popAlpha)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(cardBg, cardBg2)))
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(accentCol.copy(alpha = 0.40f), accentCol.copy(alpha = 0.10f))
                    ),
                    RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {

                // ── Photo / avatar header ────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(accentCol.copy(alpha = 0.20f), accentCol.copy(alpha = 0.06f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing halo
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(ringPulse)
                            .background(
                                Brush.radialGradient(
                                    listOf(accentCol.copy(alpha = 0.16f * ringPulse), Color.Transparent)
                                )
                            )
                    )
                    // Avatar circle
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(Color(0xFF2A1E5C), Color(0xFF170F36)))
                            )
                            .border(
                                2.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        accentCol.copy(alpha = 0.8f),
                                        Color(0xFFB57BFF).copy(alpha = 0.5f),
                                        accentCol.copy(alpha = 0.8f)
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showPhoto) {
                            // Day 1: Show placeholder, Day 2+: Show actual photo
                            if (day == 1) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(placeholderRes),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else if (member.photoUrl.isNotBlank()) {
                                MemberPhoto(
                                    photoUrl     = member.photoUrl,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier     = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(member.emoji, fontSize = 52.sp)
                            }
                        } else {
                            Text(member.emoji, fontSize = 52.sp)
                        }
                    }

                    // Day lock badge (top-right)
                    if (day < 2 && !member.isYou) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A1040).copy(alpha = 0.88f))
                                .border(0.7.dp, accentCol.copy(0.30f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "🔒 Day 2",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFB57BFF), fontSize = 9.sp
                                )
                            )
                        }
                    }
                }

                // ── Info body ──────────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Name
                    Text(
                        text  = member.alias,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color         = textCol,
                            fontWeight    = FontWeight.ExtraBold,
                            fontSize      = 22.sp,
                            letterSpacing = 0.3.sp
                        )
                    )

                    // Gender pill
                    val genderLabel = when (member.gender.lowercase()) {
                        "male"   -> "He / Him"
                        "female" -> "She / Her"
                        else     -> "They / Them"
                    }
                    val genderEmoji = when (member.gender.lowercase()) {
                        "male"   -> "♂"
                        "female" -> "♀"
                        else     -> "✨"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(accentCol.copy(alpha = 0.12f))
                            .border(0.7.dp, accentCol.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "$genderEmoji  $genderLabel",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFB57BFF), fontSize = 11.sp
                            )
                        )
                    }

                    if (member.personalities.isNotEmpty() || member.comfortPrefs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(0.7.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, accentCol.copy(0.22f), Color.Transparent)
                                    )
                                )
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Personality chips
                    if (member.personalities.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "PERSONALITY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = subCol.copy(alpha = 0.7f), letterSpacing = 2.sp,
                                    fontSize = 9.sp, fontWeight = FontWeight.SemiBold
                                )
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement   = Arrangement.spacedBy(6.dp)
                            ) {
                                member.personalities.forEachIndexed { i, p ->
                                    val c = chipColors[i % chipColors.size]
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(c.copy(alpha = 0.11f))
                                            .border(0.7.dp, c.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(p, style = MaterialTheme.typography.labelSmall.copy(
                                            color = c, fontWeight = FontWeight.Medium, fontSize = 11.sp
                                        ))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Comfort prefs chips
                    if (member.comfortPrefs.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "COMFORTABLE WITH",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = subCol.copy(alpha = 0.7f), letterSpacing = 2.sp,
                                    fontSize = 9.sp, fontWeight = FontWeight.SemiBold
                                )
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement   = Arrangement.spacedBy(6.dp)
                            ) {
                                member.comfortPrefs.forEach { pref ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF5EEAD4).copy(alpha = 0.10f))
                                            .border(0.7.dp, Color(0xFF5EEAD4).copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(pref, style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF5EEAD4), fontWeight = FontWeight.Medium, fontSize = 11.sp
                                        ))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Close button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(accentCol.copy(alpha = 0.12f))
                            .border(0.8.dp, accentCol.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Close",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color(0xFFB57BFF),
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionBadge(modifier: Modifier = Modifier) {
    // Shows a pulsing "!" to indicate a pending question / invite
    val inf   = rememberInfiniteTransition(label = "badge")
    val glow  by inf.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "badgeGlow"
    )
    val scale by inf.animateFloat(
        initialValue  = 0.9f,
        targetValue   = 1.1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "badgeScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .size(18.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(BloomRose, BloomRose.copy(alpha = 0.75f)))
            )
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "!",
            fontSize  = 9.sp,
            fontWeight = FontWeight.Bold,
            color     = Color.White.copy(alpha = glow)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Activities Panel Sheet Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivitiesSheetContent(
    day                  : Int,
    ceremonyUnlockHour   : Int  = 20,   // dev-settable; default 8PM
    isDay                : Boolean = false,
    onOpenGameInvite     : () -> Unit,
    onOpenWouldYouRather : () -> Unit,
    onOpenTrueOrMyth     : () -> Unit,
    onOpenCeremony       : () -> Unit,
    onOpenInbox          : () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "sheetGlow")
    val glowPulse by inf.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "sGlow"
    )

    val titleColor = if (isDay) DayTextPrimary   else NightTextPrimary
    val subColor   = if (isDay) DayTextSecondary  else NightTextSecondary
    val divColor   = if (isDay) DayDivider        else NightDivider
    val dotDay = when (day) { 1 -> BloomMint; 2 -> BloomPeriwinkle; else -> BloomGold }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Activities",
                    style = MaterialTheme.typography.titleMedium.copy(
                        brush      = Brush.horizontalGradient(
                            listOf(if (isDay) BloomPrimary else BloomPrimaryLight, if (isDay) BloomRose else BloomRose)
                        ),
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    when (day) { 1 -> "Day 1 · Discovery 🌱"; 2 -> "Day 2 · Depth 🌿"; else -> "Day 3 · Bond 🌸" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = subColor, fontSize = 11.sp, letterSpacing = 0.6.sp
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(dotDay.copy(alpha = glowPulse), Color.Transparent)))
            )
        }

        // Thin divider
        Box(
            modifier = Modifier
                .fillMaxWidth().height(0.7.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, divColor, Color.Transparent)))
        )
        Spacer(Modifier.height(2.dp))

        // ── Always available ──────────────────────────────────────
        SheetActivityTile(
            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_answer_react,
            emoji    = "📩",
            label    = "Inbox",
            subLabel = "Reactions · echoes your circle sent you",
            accent   = VeilAccentSand,
            locked   = false,
            onClick  = onOpenInbox
        )

        // ── Day 2+ ────────────────────────────────────────────
        SheetActivityTile(
            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_would_rather,
            emoji    = "⬡",
            label    = "Tic-Tac-Toe Challenge",
            subLabel = "Invite someone from the opposite side",
            accent   = VeilAccentGlow,
            locked   = day < 2,
            lockMsg  = "Unlocks Day 2",
            onClick  = onOpenGameInvite
        )

        SheetActivityTile(
            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_would_rather,
            emoji    = "🤔",
            label    = "Would You Rather",
            subLabel = "Vote with your circle · shared picks bond you",
            accent   = Color(0xFFB57BFF),
            locked   = day < 2,
            lockMsg  = "Unlocks Day 2",
            onClick  = onOpenWouldYouRather
        )

        // ── Day 3 ─────────────────────────────────────────────
        SheetActivityTile(
            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_true_myth,
            emoji    = "🎭",
            label    = "True or Myth",
            subLabel = "Guess hidden facts · bond points if correct",
            accent   = Color(0xFFFF7C5A),
            locked   = day < 3,
            lockMsg  = "Unlocks Day 3",
            onClick  = onOpenTrueOrMyth
        )

        // Ceremony tile — time-gated: Day 3 AND after configured unlock hour
        var ceremonySecsLeft by remember { mutableStateOf(secondsUntil8PM(ceremonyUnlockHour)) }
        LaunchedEffect(ceremonyUnlockHour) {
            while (ceremonySecsLeft > 0) {
                kotlinx.coroutines.delay(60_000L)
                ceremonySecsLeft = secondsUntil8PM(ceremonyUnlockHour)
            }
        }
        val ceremonyLocked = day < 3 || ceremonySecsLeft > 0
        // Format unlock hour inline e.g. "8:00 PM" or "2:00 PM"
        val unlockLabel = run {
            val h12 = when { ceremonyUnlockHour == 0 -> 12; ceremonyUnlockHour <= 12 -> ceremonyUnlockHour; else -> ceremonyUnlockHour - 12 }
            val ap  = if (ceremonyUnlockHour < 12) "AM" else "PM"
            "$h12:00 $ap"
        }
        val ceremonyLockMsg = when {
            day < 3              -> "Unlocks Day 3"
            ceremonySecsLeft > 0 -> {
                val h = ceremonySecsLeft / 3600
                val m = (ceremonySecsLeft % 3600) / 60
                if (h > 0) "Opens at $unlockLabel · ${h}h ${m}m left" else "Opens at $unlockLabel · ${m}m left"
            }
            else                 -> ""
        }
        SheetActivityTile(
            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_ceremony,
            emoji    = "✴️",
            label    = "The Ceremony",
            subLabel = if (!ceremonyLocked) "The final reveal · see who your circle truly is"
                       else "The final reveal · all at once at 8PM",
            accent   = Color(0xFFFFD77A),
            locked   = ceremonyLocked,
            lockMsg  = ceremonyLockMsg,
            onClick  = onOpenCeremony
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SheetActivityTile(
    emoji      : String,
    label      : String,
    subLabel   : String,
    accent     : Color,
    locked     : Boolean,
    lockMsg    : String = "",
    onClick    : () -> Unit,
    painterRes : Int? = null,    // mascot image; falls back to emoji if null
) {
    val inf = rememberInfiniteTransition(label = "tile_$label")
    val borderAlpha by inf.animateFloat(
        initialValue  = 0.3f,
        targetValue   = if (locked) 0.15f else 0.6f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "border_$label"
    )
    val glowAlpha by inf.animateFloat(
        initialValue  = 0.3f,
        targetValue   = if (locked) 0f else 0.8f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "glow_$label"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (!locked) {
                    Modifier.background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = glowAlpha * 0.4f), Color.Transparent),
                            radius = 400f
                        )
                    )
                } else Modifier
            )
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (locked) 0.05f else 0.15f),
                        if (locked) Color(0xFF1A0D38).copy(alpha = 0.30f) else Color(0xFF1A0D38).copy(alpha = 0.20f)
                    )
                )
            )
            .border(
                if (locked) 0.6.dp else 1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (locked) borderAlpha * 0.5f else borderAlpha),
                        BloomPrimaryPale.copy(alpha = 0.12f)
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = !locked) { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Mascot image OR emoji orb ────────────────────────────
            if (painterRes != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (locked) Modifier.background(Color.Black.copy(0.15f))
                            else Modifier
                        )
                        .border(
                            if (locked) 0.6.dp else 1.dp,
                            accent.copy(alpha = if (locked) 0.20f else 0.40f),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    androidx.compose.foundation.Image(
                        painter      = androidx.compose.ui.res.painterResource(id = painterRes),
                        contentDescription = null,
                        modifier     = Modifier
                            .fillMaxSize()
                            .then(if (locked) Modifier.alpha(0.40f) else Modifier),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            } else {
                // Emoji orb fallback
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    accent.copy(alpha = if (locked) 0.12f else 0.22f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (locked) VeilTextDisabled else VeilTextPrimary,
                        fontSize = 14.sp
                    )
                )
                Text(
                    if (locked && lockMsg.isNotEmpty()) lockMsg else subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (locked) VeilTextDisabled.copy(alpha = 0.5f) else VeilTextSecondary.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                )
            }

            // Arrow or lock
            Text(
                if (locked) "🔒" else "›",
                fontSize = if (locked) 13.sp else 20.sp,
                color = if (locked) VeilTextDisabled.copy(alpha = 0.4f) else accent.copy(alpha = 0.8f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bottom Glassmorphism CTA
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
//  Bottom Strip — no purple, theme-matched
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomStrip(
    day             : Int,
    isDay           : Boolean,
    hasNew          : Boolean,
    onOpenDashboard : () -> Unit,
    onOpenMyQInbox  : () -> Unit = {},
    onDevSetDay     : (Int) -> Unit = {},
    onLeave         : () -> Unit = {}
) {
    val accentCol = when (day) { 1 -> if (isDay) BloomMint else BloomMintLight; 2 -> BloomPeriwinkle; else -> BloomGold }
    val textCol   = if (isDay) DayTextSecondary else NightTextSecondary
    val borderCol = if (isDay) Color(0xFFE2E8F0) else Color(0xFF252528)
    val surfBg    = if (isDay) Color(0xFFFFFFFF) else Color(0xFF141417)
    
    // Notification badge animation
    val inf = rememberInfiniteTransition(label = "inboxBadge")
    val badgeGlow by inf.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "badgeGlow"
    )

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Two-button row: Activities + Inbox ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Activities / Day panel button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfBg)
                    .border(0.8.dp, borderCol, RoundedCornerShape(16.dp))
                    .clickable { onOpenDashboard() }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentCol.copy(0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Extension,
                            null,
                            tint     = accentCol,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Column {
                        Text(
                            when (day) { 1 -> "Day 1 · Play"; 2 -> "Day 2 · Play"; else -> "Day 3 · Play" },
                            style = MaterialTheme.typography.labelMedium.copy(
                                color      = if (isDay) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            "Open activities",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color   = textCol,
                                fontSize = 10.sp
                            )
                        )
                    }
                    if (hasNew) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(accentCol))
                    }
                }
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    null,
                    tint     = textCol.copy(0.5f),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Inbox button — with notification badge
            val inboxAccent = if (isDay) Color(0xFF92400E) else Color(0xFFD97706)
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfBg)
                        .border(0.8.dp, borderCol, RoundedCornerShape(16.dp))
                        .clickable { onOpenMyQInbox() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(inboxAccent.copy(0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Forum, null, tint = inboxAccent, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        "Inbox",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = if (isDay) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                
                // Notification badge when hasNew is true
                if (hasNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-6).dp, y = 6.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFFFF4444).copy(alpha = badgeGlow),
                                        Color(0xFFFF6666).copy(alpha = badgeGlow * 0.8f)
                                    )
                                )
                            )
                            .border(1.5.dp, surfBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "1",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color      = Color.White,
                                fontSize   = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Leave / door button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfBg)
                    .border(0.8.dp, borderCol, RoundedCornerShape(16.dp))
                    .clickable { onLeave() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("🚪", fontSize = 16.sp)
            }
        }

        // DEV day selector
        val devEmail1 = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.lowercase()
        if (devEmail1 == "supriyamkumar999@gmail.com") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x22000000))
                    .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("DEV", style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFFFF6B35), fontSize = 9.sp, letterSpacing = 1.sp
                ), modifier = Modifier.weight(1f))
                listOf(1, 2, 3).forEach { d ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (day == d) Color(0x33FFFFFF) else Color(0x11FFFFFF))
                            .border(0.7.dp, if (day == d) Color(0x66FFFFFF) else Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                            .clickable { onDevSetDay(d) }
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text("Day $d", style = MaterialTheme.typography.labelSmall.copy(
                            color = if (day == d) Color.White else Color.White.copy(0.5f), fontSize = 9.sp
                        ))
                    }
                }
            }
        }
}
}

// ─────────────────────────────────────────────────────────────────────────────
//  Activity Dashboard Overlay — slide-up panel, all-days
// ─────────────────────────────────────────────────────────────────────────────


@Composable
private fun ActivityDashboardOverlay(
    visible                : Boolean,
    day                    : Int,
    isDay                  : Boolean,
    hasNew                 : Boolean,
    day1Pending            : Boolean,
    ghostedCount           : Int,
    ceremonyUnlockHour     : Int     = 20,   // kept for compat
    dayStartedAtMs         : Long    = 0L,   // epoch ms when current day started
    onClose                : () -> Unit,
    onOpenQuestions        : () -> Unit,
    onOpenAnswers          : () -> Unit,
    onOpenCeremony         : () -> Unit,
    onOpenTomSetup         : () -> Unit,
    onOpenSlowChat         : () -> Unit,
    onOpenWouldYouRather    : () -> Unit = {},
    onOpenTrueOrMyth        : () -> Unit = {},
    onOpenMyQInbox          : () -> Unit = {},
    onOpenAestheticsFeed    : () -> Unit = {},
    onOpenVibes             : () -> Unit = {},
    onOpenDareToShare       : () -> Unit = {},
    onOpenSecretCompliment  : () -> Unit = {}
) {
    // Ceremony unlocks 8 hours after Day 3 started — elapsed-time based, no wall clock
    val CEREMONY_UNLOCK_MS   = 8 * 3_600_000L
    val msElapsedDay3        = if (day >= 3 && dayStartedAtMs > 0L)
        (System.currentTimeMillis() - dayStartedAtMs).coerceAtLeast(0L) else 0L
    val ceremonyUnlocked     = day >= 3 && msElapsedDay3 >= CEREMONY_UNLOCK_MS
    val msRemainingCeremony  = if (day >= 3 && !ceremonyUnlocked)
        (CEREMONY_UNLOCK_MS - msElapsedDay3).coerceAtLeast(0L) else 0L
    val hoursLeft  = (msRemainingCeremony / 3_600_000L).toInt()
    val minsLeft   = ((msRemainingCeremony % 3_600_000L) / 60_000L).toInt()
    val secsLeft   = ((msRemainingCeremony % 60_000L) / 1_000L).toInt()
    val timeLeftStr = when {
        hoursLeft > 0 -> "${hoursLeft}h ${minsLeft}m to go"
        minsLeft  > 0 -> "${minsLeft}m ${secsLeft}s to go"
        else          -> "Opening soon…"
    }

    AnimatedVisibility(
        visible = visible,
        enter   = slideInVertically(initialOffsetY = { it }, animationSpec = tween(340, easing = FastOutSlowInEasing)),
        exit    = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing))
    ) {
        // ── Palette — matches home background, NO purple
        val bgMain   = if (isDay) Color(0xFFF6F7F9) else Color(0xFF0C0C0F)
        val bgCard   = if (isDay) Color(0xFFFFFFFF) else Color(0xFF141417)
        val bdCard   = if (isDay) Color(0xFFE4E4E7) else Color(0xFF252528)
        val txtP     = if (isDay) Color(0xFF111111) else Color(0xFFF0F0F0)
        val txtS     = if (isDay) Color(0xFF6B7280) else Color(0xFF6B7280)
        val accentDay = when (day) {
            1    -> if (isDay) Color(0xFF1D6045) else Color(0xFF4ADE80)
            2    -> if (isDay) Color(0xFF334D8F) else Color(0xFF93C5FD)
            else -> if (isDay) Color(0xFF7A5C1E) else Color(0xFFD4A844)
        }

        // Inline expandable states for new Day-1 features
        var showVibeSong     by remember { mutableStateOf(false) }
        var showColorOfDay   by remember { mutableStateOf(false) }
        var showOneBio       by remember { mutableStateOf(false) }
        var songQuery        by remember { mutableStateOf("") }
        var bioText          by remember { mutableStateOf("") }
        var selectedColor    by remember { mutableStateOf<Color?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgMain)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Activities",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color      = txtP,
                                fontSize   = 21.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                        Text(
                            when (day) { 1 -> "Day 1  ·  Curiosity"; 2 -> "Day 2  ·  Depth"; else -> "Day 3  ·  Connection" },
                            style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 11.sp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isDay) Color(0x0A000000) else Color.White.copy(0.06f))
                            .border(0.7.dp, bdCard, CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = txtP.copy(0.6f), modifier = Modifier.size(17.dp))
                    }
                }

                // ── Thin divider ──────────────────────────────────────────────
                HorizontalDivider(color = bdCard, thickness = 0.5.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 14.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    // ══════════════════════════════════════════════════════════
                    //  SECTION 1 — CIRCLE  (questions, answers, moments)
                    // ══════════════════════════════════════════════════════════
                    DashSectionLabel("Circle", Icons.Rounded.Forum, txtS)

                    // Ask the Circle
                    DashCard(
                        painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_ask_circle,
                        icon      = Icons.Rounded.Campaign,
                        label     = "Ask the Circle",
                        sub       = "Post a question to your group",
                        accent    = accentDay,
                        bgCard    = bgCard,
                        border    = bdCard,
                        txtP      = txtP,
                        txtS      = txtS,
                        badge     = if (hasNew) "New" else null,
                        onClick   = { onClose(); onOpenQuestions() }
                    )

                    // Answer & React
                    DashCard(
                        painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_answer_react,
                        icon      = Icons.Rounded.QuestionAnswer,
                        label     = "Answer & React",
                        sub       = "See what your circle asked you",
                        accent    = accentDay,
                        bgCard    = bgCard,
                        border    = bdCard,
                        txtP      = txtP,
                        txtS      = txtS,
                        onClick   = { onClose(); onOpenAnswers() }
                    )

                    // Moments (was Vibe Feed) — available all days
                    DashCard(
                        painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_moments,
                        icon      = Icons.Rounded.PhotoCamera,
                        label     = "Moments",
                        sub       = "Share a photo with your circle anonymously",
                        accent    = if (isDay) Color(0xFF374151) else Color(0xFF9CA3AF),
                        bgCard    = bgCard,
                        border    = bdCard,
                        txtP      = txtP,
                        txtS      = txtS,
                        onClick   = { onClose(); onOpenAestheticsFeed() }
                    )

                    // ══════════════════════════════════════════════════════════
                    //  SECTION 2 — EXPRESS YOURSELF (Day 1 self-expression)
                    // ══════════════════════════════════════════════════════════
                    Spacer(Modifier.height(4.dp))
                    DashSectionLabel("Express Yourself", Icons.Rounded.AutoAwesome, txtS)

                    // Circle Vibes — one card, opens full-screen VibesScreen
                    DashCard(
                        painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_circle_vibes,
                        icon    = Icons.Rounded.MusicNote,
                        label   = "Circle Vibes",
                        sub     = "Song · Color · One Word — share and see your circle",
                        accent  = if (isDay) Color(0xFF1D4E89) else Color(0xFF60A5FA),
                        bgCard  = bgCard,
                        border  = bdCard,
                        txtP    = txtP,
                        txtS    = txtS,
                        onClick = { onClose(); onOpenVibes() }
                    )

                    // Dare to Share — Day 2+
                    if (day >= 2) {
                        DashCard(
                            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_dare_share,
                            icon    = Icons.Rounded.AutoAwesome,
                            label   = "Dare to Share",
                            sub     = "Pick a prompt · answer anonymously · see the circle's truths",
                            accent  = if (isDay) Color(0xFF1A3A6B) else Color(0xFF7B90D4),
                            bgCard  = bgCard,
                            border  = bdCard,
                            txtP    = txtP,
                            txtS    = txtS,
                            onClick = { onClose(); onOpenDareToShare() }
                        )
                        DashCard(
                            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_secret_comp,
                            icon    = Icons.Rounded.Favorite,
                            label   = "Secret Compliment",
                            sub     = "Send 1 anonymous compliment · see what others say about you",
                            accent  = if (isDay) Color(0xFF5A2030) else Color(0xFFD47A8A),
                            bgCard  = bgCard,
                            border  = bdCard,
                            txtP    = txtP,
                            txtS    = txtS,
                            onClick = { onClose(); onOpenSecretCompliment() }
                        )
                    } else {
                        LockedRow("Dare to Share",     "Unlocks on Day 2", isDay, bgCard, bdCard, txtS)
                        LockedRow("Secret Compliment", "Unlocks on Day 2", isDay, bgCard, bdCard, txtS)
                    }

                    // ══════════════════════════════════════════════════════════
                    //  SECTION 3 — PLAY TOGETHER (games, unlocks by day)
                    // ══════════════════════════════════════════════════════════
                    Spacer(Modifier.height(4.dp))
                    DashSectionLabel("Play Together", Icons.Rounded.Extension, txtS)

                    // Would You Rather — Day 2+
                    if (day >= 2) {
                        DashCard(
                            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_would_rather,
                            icon    = Icons.Rounded.ChatBubbleOutline,
                            label   = "Would You Rather",
                            sub     = "Pick a side · see how they actually think",
                            accent  = if (isDay) Color(0xFF7A5C1E) else Color(0xFFD4A844),
                            bgCard  = bgCard,
                            border  = bdCard,
                            txtP    = txtP,
                            txtS    = txtS,
                            onClick = { onClose(); onOpenWouldYouRather() }
                        )
                        DashCard(
                            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_true_myth,
                            icon    = Icons.Rounded.WorkspacePremium,
                            label   = "True or Myth",
                            sub     = "Guess what's real about your circle",
                            accent  = if (isDay) Color(0xFF27546F) else Color(0xFF7DB8D4),
                            bgCard  = bgCard,
                            border  = bdCard,
                            txtP    = txtP,
                            txtS    = txtS,
                            onClick = { onClose(); onOpenTrueOrMyth() }
                        )
                    } else {
                        LockedRow("Would You Rather", "Unlocks on Day 2", isDay, bgCard, bdCard, txtS)
                        LockedRow("True or Myth",    "Unlocks on Day 2", isDay, bgCard, bdCard, txtS)
                    }

                    // Slow Chat - Day 2+ (TEMP: was day >= 3, restore before prod)
                    if (day >= 2) {
                        DashCard(
                            painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_slow_chat,
                            icon    = Icons.Rounded.Forum,
                            label   = "Slow Chat",
                            sub     = "7 messages each — every word matters",
                            accent  = if (isDay) Color(0xFF059669) else Color(0xFF34D399),
                            bgCard  = bgCard,
                            border  = bdCard,
                            txtP    = txtP,
                            txtS    = txtS,
                            onClick = { onClose(); onOpenSlowChat() }
                        )
                        if (ceremonyUnlocked) {
                            DashCard(
                                painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_ceremony,
                                icon    = Icons.Rounded.AutoAwesome,
                                label   = "The Ceremony",
                                sub     = "The final reveal is ready · now open",
                                accent  = if (isDay) Color(0xFF7A5C1E) else Color(0xFFD4A844),
                                bgCard  = bgCard,
                                border  = bdCard,
                                txtP    = txtP,
                                txtS    = txtS,
                                onClick = { onClose(); onOpenCeremony() }
                            )
                        } else {
                            LockedRow(
                                "The Ceremony",
                                "Lifts after Day 3 completes  ·  $timeLeftStr",
                                isDay, bgCard, bdCard, txtS
                            )
                        }
                    } else {
                        LockedRow("Slow Chat",    "Unlocks on Day 3", isDay, bgCard, bdCard, txtS)
                        LockedRow("The Ceremony", "Unlocks on Day 3", isDay, bgCard, bdCard, txtS)
                    }

                    // ══════════════════════════════════════════════════════════
                    //  SECTION 4 — JOURNEY STATUS
                    // ══════════════════════════════════════════════════════════
                    Spacer(Modifier.height(4.dp))
                    DashSectionLabel("Journey", Icons.Rounded.WaterDrop, txtS)

                    // Day progress track
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgCard)
                            .border(0.7.dp, bdCard, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            (1..3).forEach { d ->
                                Box(
                                    modifier = Modifier
                                        .size(if (d == day) 11.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(if (d <= day) accentDay else txtS.copy(0.18f))
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Circle Journey  ·  Day $day of 3",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = txtP, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                when (day) { 1 -> "Discovering your circle"; 2 -> "Building real connections"; else -> "Final bonds forming" },
                                style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 11.sp)
                            )
                        }
                    }

                    if (ghostedCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF4444).copy(if (isDay) 0.06f else 0.10f))
                                .border(0.7.dp, Color(0xFFFF4444).copy(0.18f), RoundedCornerShape(12.dp))
                                .padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.PersonOff, null, tint = Color(0xFFFF4444).copy(0.7f), modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (ghostedCount == 1) "A member has gone quiet" else "$ghostedCount members have gone quiet",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFFFF4444).copy(0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    "The journey continues without them",
                                    style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 10.sp)
                                )
                            }
                        }
                    }

                    Text(
                        "No open chat  ·  No pressure  ·  Up to 3 questions per day",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = txtS.copy(0.45f), textAlign = TextAlign.Center, fontSize = 10.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// Helper — locked row in Explore tab
@Composable
private fun LockedRow(label: String, lockMsg: String, isDay: Boolean, rowBg: Color, rowBorder: Color, subCol: Color) {
    val lockCol = subCol.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .border(0.7.dp, rowBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(lockCol.copy(0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Lock, null, tint = lockCol, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(color = lockCol, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Text(lockMsg, style = MaterialTheme.typography.labelSmall.copy(color = lockCol.copy(0.7f), fontSize = 10.sp))
        }
    }
}

@Composable
private fun IslandBottomCta(
    day                  : Int,
    isDay                : Boolean = false,
    hasNew               : Boolean,
    day1Pending          : Boolean = false,
    ghostedCount         : Int     = 0,
    ceremonyUnlockHour   : Int     = 20,   // dev-settable; default 8PM
    onClose              : () -> Unit = {},
    onOpenQuestions      : () -> Unit,
    onOpenAnswers        : () -> Unit,
    onOpenCeremony       : () -> Unit = {},
    onOpenTomSetup       : () -> Unit = {},
    onOpenSlowChat       : () -> Unit = {},
    onOpenGameInvite     : () -> Unit = {},
    onOpenWouldYouRather : () -> Unit = {},
    onOpenTrueOrMyth      : () -> Unit = {},
    onOpenMyQInbox        : () -> Unit = {},
    onDevSetDay           : (Int) -> Unit = {},
    onOpenAdminDashboard  : () -> Unit = {}
) {
    // Outer glass card — adapts to day/night
    val cardBg     = if (isDay) Color(0xAAFFFFFF) else Color.White.copy(alpha = 0.04f)
    val cardBorder = if (isDay) Color(0xFF2563EB).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.12f)
    val textPrimary   = if (isDay) Color(0xFF1E293B) else VeilTextSecondary
    val textSecondary = if (isDay) Color(0xFF475569) else VeilTextTertiary
    val ctaBg      = if (isDay)
        Brush.horizontalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFF1D4ED8)))
    else
        Brush.horizontalGradient(listOf(VeilPrimary, VeilPrimaryMid, Color(0xFF3A2080), VeilPrimaryLight))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(cardBg)
            .border(0.8.dp, cardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (day >= 3) {
                // ── CEREMONY MODE — time-gated to 8 PM ──────────────────────

                // ── "Set your fact" prompt — only if user hasn't submitted yet ──
                var factSubmitted by remember { mutableStateOf<Boolean?>(null) }
                val db3 = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
                val me3 = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
                LaunchedEffect(Unit) {
                    if (me3.isNotBlank()) {
                        try {
                            val uDoc = db3.collection("users").document(me3).get().await()
                            val gid  = uDoc.getString("groupId") ?: ""
                            if (gid.isNotBlank()) {
                                val mDoc = db3.collection("groups").document(gid)
                                    .collection("members").document(me3).get().await()
                                factSubmitted = mDoc.getString("tomFact") != null
                            } else {
                                factSubmitted = false
                            }
                        } catch (e: Exception) {
                            factSubmitted = false
                        }
                    }
                }

                if (factSubmitted == false) {
                    val infFact = rememberInfiniteTransition(label = "factPulse")
                    val factGlow by infFact.animateFloat(
                        initialValue  = 0.45f, targetValue = 0.9f,
                        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "fg"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF200A00).copy(alpha = 0.8f))
                            .border(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFF7C5A).copy(alpha = factGlow * 0.6f),
                                        Color(0xFFFFB77A).copy(alpha = factGlow * 0.3f),
                                        Color(0xFFFF7C5A).copy(alpha = factGlow * 0.6f)
                                    )
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { onOpenTomSetup() }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🎭", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "Set your True or Myth",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color    = Color(0xFFFF7C5A).copy(alpha = 0.90f),
                                        fontSize = 13.sp
                                    )
                                )
                                Text(
                                    "Write one statement · your circle will guess",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color    = VeilTextDisabled.copy(alpha = 0.65f),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Text(
                                "›",
                                fontSize = 20.sp,
                                color    = Color(0xFFFF7C5A).copy(alpha = factGlow * 0.8f)
                            )
                        }
                    }
                }

                // Live countdown state — ticks every second
                var secondsLeft by remember {
                    mutableStateOf(secondsUntil8PM(ceremonyUnlockHour))
                }
                LaunchedEffect(ceremonyUnlockHour) {
                    while (secondsLeft > 0) {
                        kotlinx.coroutines.delay(1_000)
                        secondsLeft = secondsUntil8PM(ceremonyUnlockHour)
                    }
                }
                val ceremonyOpen = secondsLeft <= 0

                val infCer = rememberInfiniteTransition(label = "cerPulse")
                val cerGlow by infCer.animateFloat(
                    initialValue  = 0.65f,
                    targetValue   = 1.0f,
                    animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label         = "cerGlow"
                )

                if (!ceremonyOpen) {
                    // ── PRE-CEREMONY: Countdown card ──────────────────────────
                    val hh = (secondsLeft / 3600).toString().padStart(2, '0')
                    val mm = ((secondsLeft % 3600) / 60).toString().padStart(2, '0')
                    val ss = (secondsLeft % 60).toString().padStart(2, '0')

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Ambient message
                        Text(
                            "Day 3 · The mystery almost lifts",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color     = Color(0xFFFFD77A).copy(alpha = 0.65f),
                                textAlign = TextAlign.Center,
                                fontSize  = 11.sp
                            )
                        )

                        // Countdown card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1A0E00), Color(0xFF0E0800))
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFFFD77A).copy(alpha = cerGlow * 0.45f),
                                            Color(0xFFFFB300).copy(alpha = cerGlow * 0.25f),
                                            Color(0xFFFFD77A).copy(alpha = cerGlow * 0.45f)
                                        )
                                    ),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(vertical = 18.dp, horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "✦  Ceremony begins in  ✦",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color       = Color(0xFFFFD77A).copy(alpha = 0.55f),
                                        fontSize    = 9.sp,
                                        letterSpacing = 1.sp
                                    )
                                )
                                // Big clock digits
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ClockChunk(hh, "HRS", Color(0xFFFFD77A), cerGlow)
                                    Text(
                                        ":",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color    = Color(0xFFFFD77A).copy(alpha = cerGlow),
                                            fontSize = 28.sp
                                        )
                                    )
                                    ClockChunk(mm, "MIN", Color(0xFFFFCC77), cerGlow)
                                    Text(
                                        ":",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color    = Color(0xFFFFD77A).copy(alpha = cerGlow),
                                            fontSize = 28.sp
                                        )
                                    )
                                    ClockChunk(ss, "SEC", Color(0xFFD4A8FF), cerGlow)
                                }
                                Text(
                                    "8:00 PM · tonight",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color    = Color(0xFFFFD77A).copy(alpha = 0.38f),
                                        fontSize = 9.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                            }
                        }

                        // "Keep playing" nudge
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(0.6.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Until then · keep bond-building",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color       = VeilTextSecondary.copy(alpha = 0.7f),
                                        textAlign   = TextAlign.Center,
                                        fontSize    = 10.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                Text(
                                    "🎭 True or Myth  ·  🤔 Would You Rather  ·  ⚡ Sync",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color     = VeilTextDisabled.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                        fontSize  = 9.sp
                                    )
                                )
                            }
                        }
                    }

                } else {
                    // ── POST-8PM: Ceremony OPEN ───────────────────────────────
                    Text(
                        "The moment has arrived. The mystery lifts. ✦",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color     = Color(0xFFFFD77A).copy(alpha = 0.80f),
                            textAlign = TextAlign.Center,
                            fontSize  = 11.sp
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF6B3D00), Color(0xFFB8730A), Color(0xFF6B3D00))
                                )
                            )
                            .border(
                                1.5.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFFE066).copy(alpha = cerGlow),
                                        Color(0xFFFFB300).copy(alpha = cerGlow * 0.7f),
                                        Color(0xFFFFE066).copy(alpha = cerGlow)
                                    )
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { onOpenCeremony() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Enter the Ceremony  ✦",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color      = Color(0xFF120800),
                                fontSize   = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        )
                    }
                }

            } else {
                // ── NORMAL DAILY MODE — Horizontal Tab Dashboard ─────────────

                var selectedTab by remember { mutableStateOf(0) }

                val tabLabels  = listOf("Today", "Explore", "Status")
                val accentCol  = if (isDay) Color(0xFF3B82F6) else Color(0xFF60A5FA)
                val tabBg      = if (isDay) Color(0x0C000000) else Color.White.copy(alpha = 0.05f)
                val tabSelBg   = if (isDay) Color(0xFF3B82F6).copy(alpha = 0.10f) else Color(0xFF60A5FA).copy(alpha = 0.10f)
                val rowBg      = if (isDay) Color(0x08000000) else Color.White.copy(alpha = 0.04f)
                val rowBorder  = if (isDay) Color(0xFF94A3B8).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)
                val labelCol   = if (isDay) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                val subCol     = if (isDay) Color(0xFF64748B) else Color(0xFF94A3B8)

                // Vibe headline
                Text(
                    text = when {
                        day == 1 && day1Pending -> "Your circle has gathered"
                        day == 1 -> "Day 1  ·  Go first, ask something"
                        day == 2 -> "Day 2  ·  Going deeper"
                        else     -> "Day 3  ·  Final chapter"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        color         = labelCol.copy(alpha = 0.65f),
                        textAlign     = TextAlign.Center,
                        fontSize      = 11.sp,
                        letterSpacing = 0.25.sp
                    )
                )

                // ── Tab selector row ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tabBg),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    tabLabels.forEachIndexed { i, label ->
                        val selected = (selectedTab == i)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) tabSelBg else Color.Transparent)
                                .border(
                                    if (selected) 0.8.dp else 0.dp,
                                    if (selected) accentCol.copy(alpha = 0.25f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedTab = i }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color      = if (selected) accentCol else labelCol.copy(alpha = 0.4f),
                                    fontSize   = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }

                // ── Tab content ───────────────────────────────────────────────
                when (selectedTab) {

                    // ── TODAY tab ─────────────────────────────────────────────
                    0 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            // Primary CTA: Ask the Circle
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ctaBg)
                                    .border(
                                        1.dp,
                                        if (isDay) Brush.horizontalGradient(listOf(Color(0xFF93C5FD).copy(alpha = 0.35f), Color(0xFFBFDBFE).copy(alpha = 0.2f)))
                                        else       Brush.horizontalGradient(listOf(Color(0xFF60A5FA).copy(alpha = 0.30f), Color(0xFF94A3B8).copy(alpha = 0.15f))),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onOpenQuestions() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    // Mascot avatar circle
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .border(1.dp, Color.White.copy(0.25f), androidx.compose.foundation.shape.CircleShape)
                                    ) {
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_ask_circle),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Ask the Circle",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color      = Color.White,
                                            fontSize   = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    if (hasNew) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(0.6f)))
                                    }
                                }
                            }

                            // Secondary: Answer & React
                            DashboardRow(
                                painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_answer_react,
                                icon     = Icons.Rounded.QuestionAnswer,
                                label    = "Answer & React",
                                subLabel = "See what your circle asked",
                                accentCol = accentCol,
                                rowBg    = rowBg,
                                rowBorder = rowBorder,
                                labelCol = labelCol,
                                subCol   = subCol,
                                onClick  = onOpenAnswers
                            )

                            // Echo (Day 1 only)
                            if (day == 1) {
                                DashboardRow(
                                    painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_answer_react,
                                    icon      = Icons.Rounded.Favorite,
                                    label     = "Echo",
                                    subLabel  = "Mark what deeply resonates",
                                    accentCol = if (isDay) Color(0xFFE11D48) else Color(0xFFFF6B9A),
                                    rowBg     = rowBg,
                                    rowBorder = rowBorder,
                                    labelCol  = labelCol,
                                    subCol    = subCol,
                                    onClick   = onOpenAnswers
                                )
                            }

                            // Slow Chat (Day 2 for testing, restore to day == 3)
                            if (day == 2) {
                                DashboardRow(
                                    painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_slow_chat,
                                    icon      = Icons.Rounded.Forum,
                                    label     = "Slow Chat",
                                    subLabel  = "7 messages each — every word matters",
                                    accentCol = if (isDay) Color(0xFF059669) else Color(0xFF34D399),
                                    rowBg     = rowBg,
                                    rowBorder = rowBorder,
                                    labelCol  = labelCol,
                                    subCol    = subCol,
                                    onClick   = onOpenSlowChat
                                )
                            }
                        }
                    }

                    // ── EXPLORE tab ───────────────────────────────────────────
                    1 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardRow(
                                painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_would_rather,
                                icon      = Icons.Rounded.Extension,
                                label     = "Sync",
                                subLabel  = "Answer together · see how well you match",
                                accentCol = if (isDay) Color(0xFF7C3AED) else Color(0xFFAA80FF),
                                rowBg     = rowBg, rowBorder = rowBorder,
                                labelCol  = labelCol, subCol = subCol,
                                onClick   = { onClose(); onOpenGameInvite() }
                            )
                            DashboardRow(
                                painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_would_rather,
                                icon      = Icons.Rounded.ChatBubbleOutline,
                                label     = "Would You Rather",
                                subLabel  = "Pick a side  ·  see how they think",
                                accentCol = if (isDay) Color(0xFFD97706) else Color(0xFFFFB300),
                                rowBg     = rowBg, rowBorder = rowBorder,
                                labelCol  = labelCol, subCol = subCol,
                                onClick   = { onClose(); onOpenWouldYouRather() }
                            )
                            DashboardRow(
                                painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_true_myth,
                                icon      = Icons.Rounded.WorkspacePremium,
                                label     = "True or Myth",
                                subLabel  = "Guess what's real about them",
                                accentCol = if (isDay) Color(0xFF0284C7) else Color(0xFF38BDF8),
                                rowBg     = rowBg, rowBorder = rowBorder,
                                labelCol  = labelCol, subCol = subCol,
                                onClick   = { onClose(); onOpenTrueOrMyth() }
                            )
                            if (day == 1) {
                                // Locked hint for Day 2+ games
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(rowBg)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "More unlocks on Day 2",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color   = subCol.copy(alpha = 0.5f),
                                            fontSize = 10.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // ── STATUS tab ────────────────────────────────────────────
                    2 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Group day progress
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(rowBg)
                                    .border(0.7.dp, rowBorder, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Day progress dots
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    (1..3).forEach { d ->
                                        Box(
                                            modifier = Modifier
                                                .size(if (d == day) 10.dp else 7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (d <= day) accentCol
                                                    else subCol.copy(alpha = 0.2f)
                                                )
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Circle Journey  ·  Day $day of 3",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color    = labelCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        when (day) {
                                            1 -> "Discovering your circle"
                                            2 -> "Building real connections"
                                            else -> "Final bonds forming"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color    = subCol,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            // Replies to my question — dedicated inbox
                            DashboardRow(
                                painterRes = com.ns.findyourintroverfriend.R.drawable.mascot_ask_circle,
                                icon      = Icons.Rounded.QuestionAnswer,
                                label     = "Replies to my question",
                                subLabel  = "See every answer your circle gave you",
                                accentCol = if (isDay) Color(0xFFD97706) else Color(0xFFFFB947),
                                rowBg     = rowBg, rowBorder = rowBorder,
                                labelCol  = labelCol, subCol = subCol,
                                onClick   = { onClose(); onOpenMyQInbox() }
                            )

                            // Ghosted warning
                            if (ghostedCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFFF4444).copy(alpha = if (isDay) 0.07f else 0.10f))
                                        .border(0.7.dp, Color(0xFFFF4444).copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector        = Icons.Rounded.PersonOff,
                                        contentDescription = null,
                                        tint               = Color(0xFFFF4444).copy(alpha = 0.75f),
                                        modifier           = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (ghostedCount == 1) "A member has gone quiet" else "$ghostedCount members have gone quiet",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color    = Color(0xFFFF4444).copy(alpha = 0.9f),
                                                fontSize = 12.sp
                                            )
                                        )
                                        Text(
                                            "The journey continues without them",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color    = subCol,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }

                            // Footer note
                            Text(
                                if (day == 3) "Slow Chat  ·  True or Myth  ·  Would You Rather"
                                else "No open chat  ·  No pressure  ·  Up to 3 questions/day",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color     = subCol.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    fontSize  = 9.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Safety warning (per 18+ spec §1.2 + §8.2)
                            Text(
                                "⚠️ Misrepresentation may result in permanent ban. Users can report inappropriate behavior.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color     = Color(0xFFEF4444).copy(alpha = 0.45f),
                                    textAlign = TextAlign.Center,
                                    fontSize  = 8.sp,
                                    lineHeight = 12.sp
                                ),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )

                            // ── Admin Panel shortcut (only visible to admin account) ──
                            val adminEmail = "supriyamkumar098@gmail.com"
                            val currentEmail = com.google.firebase.auth.FirebaseAuth.getInstance()
                                .currentUser?.email?.lowercase()
                            if (currentEmail == adminEmail) {
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .background(Color(0xFFEF4444).copy(0.10f))
                                        .border(0.6.dp, Color(0xFFEF4444).copy(0.25f),
                                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .clickable { onOpenAdminDashboard() }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "🔒 Admin Panel",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFEF4444),
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // DEV MODE panel — only visible to developer account
            val devEmail2 = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.lowercase()
            if (devEmail2 == "supriyamkumar999@gmail.com") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF6B35).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFFF6B35).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "DEV",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = Color(0xFFFF6B35),
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    listOf(1, 2, 3).forEach { d ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (day == d) Color(0xFFFF6B35).copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    0.8.dp,
                                    if (day == d) Color(0xFFFF6B35).copy(alpha = 0.6f)
                                    else Color.White.copy(alpha = 0.10f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onDevSetDay(d) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Day $d",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color    = if (day == d) Color(0xFFFF6B35) else VeilTextSecondary,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dashboard Row — sleek icon + text row used in the bottom tab dashboard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardRow(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    label      : String,
    subLabel   : String,
    accentCol  : Color,
    rowBg      : Color,
    rowBorder  : Color,
    labelCol   : Color,
    subCol     : Color,
    onClick    : () -> Unit,
    painterRes : Int? = null,    // when non-null, show mascot image instead of icon
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .border(0.7.dp, rowBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Mascot image OR vector icon ─────────────────────────────────────
        if (painterRes != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .border(
                        1.dp,
                        accentCol.copy(alpha = 0.30f),
                        androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                androidx.compose.foundation.Image(
                    painter      = androidx.compose.ui.res.painterResource(id = painterRes),
                    contentDescription = null,
                    modifier     = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        } else {
            Box(
                modifier         = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accentCol.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = accentCol.copy(alpha = 0.85f),
                    modifier           = Modifier.size(18.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color      = labelCol,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                subLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = subCol,
                    fontSize = 10.sp
                )
            )
        }
        // Chevron accent
        Icon(
            imageVector        = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint               = accentCol.copy(alpha = 0.40f),
            modifier           = Modifier.size(13.dp)
        )
    }
}

// ── Section label for dashboard ────────────────────────────────────────────
@Composable
private fun DashSectionLabel(
    title  : String,
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    txtS   : Color
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Icon(icon, null, tint = txtS.copy(0.5f), modifier = Modifier.size(11.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                color         = txtS.copy(0.5f),
                fontSize      = 9.sp,
                letterSpacing = 1.5.sp,
                fontWeight    = FontWeight.SemiBold
            )
        )
    }
}

// ── Tappable activity card ─────────────────────────────────────────────────
@Composable
private fun DashCard(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    label      : String,
    sub        : String,
    accent     : Color,
    bgCard     : Color,
    border     : Color,
    txtP       : Color,
    txtS       : Color,
    badge      : String? = null,
    painterRes : Int?    = null,   // mascot image — replaces vector icon when set
    onClick    : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgCard)
            .border(0.7.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Mascot image OR vector icon ────────────────────────────────────
        if (painterRes != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.8.dp, accent.copy(0.22f), RoundedCornerShape(12.dp))
            ) {
                androidx.compose.foundation.Image(
                    painter      = androidx.compose.ui.res.painterResource(id = painterRes),
                    contentDescription = null,
                    modifier     = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent.copy(0.85f), modifier = Modifier.size(17.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(
                    color = txtP, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                ))
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelSmall.copy(
                            color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold
                        ))
                    }
                }
            }
            Text(sub, style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 10.sp))
        }
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = txtS.copy(0.3f), modifier = Modifier.size(14.dp))
    }
}

// ── Expandable inline card (song / color / bio) ────────────────────────────
@Composable
private fun DashExpandableCard(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    label    : String,
    sub      : String,
    accent   : Color,
    bgCard   : Color,
    border   : Color,
    txtP     : Color,
    txtS     : Color,
    expanded : Boolean,
    onToggle : () -> Unit,
    content  : @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.7.dp, if (expanded) accent.copy(0.25f) else border, RoundedCornerShape(14.dp))
            .background(bgCard)
    ) {
        // Header row — always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(if (expanded) 0.16f else 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent.copy(if (expanded) 1f else 0.75f), modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(
                    color = txtP, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                ))
                Text(sub, style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 10.sp))
            }
            val rotateAngle by animateFloatAsState(
                targetValue   = if (expanded) 180f else 0f,
                animationSpec = tween(220),
                label         = "chevron"
            )
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                null,
                tint     = if (expanded) accent.copy(0.7f) else txtS.copy(0.35f),
                modifier = Modifier.size(16.dp).then(
                    Modifier.rotate(rotateAngle)
                )
            )
        }

        // Expandable body
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                content = content
            )
        }
    }
}

@Composable
private fun CircleRevealOverlay(
    members : List<IslandMember>,
    onDone  : () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        visible = true
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(900), label = "revealAlpha")

    // Back-press should not dismiss (only the button should)
    androidx.activity.compose.BackHandler(enabled = true) { /* swallow back */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF030612), Color(0xFF060E20), Color(0xFF030612))
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        // Ambient radial glow behind orb
        val inf = rememberInfiniteTransition(label = "revGlow")
        val glow by inf.animateFloat(
            initialValue  = 0.4f, targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "rg"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(Color(0xFF2A1870).copy(alpha = 0.25f * glow), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.22f), radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f, center = Offset(size.width * 0.5f, size.height * 0.22f)
            )
        }

        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // ── Central orb ───────────────────────────────────────────────────
            Box(
                modifier         = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                val orbScale by inf.animateFloat(
                    initialValue  = 0.92f, targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(tween(3_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "orbS"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(orbScale)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF4D2BB0).copy(alpha = glow * 0.30f), Color.Transparent)
                            ), CircleShape
                        )
                )
                Canvas(modifier = Modifier.size(84.dp).scale(orbScale * 0.95f)) {
                    val cx = size.width / 2; val cy = size.height / 2
                    drawCircle(
                        color  = Color(0xFF7C5CBF).copy(alpha = glow * 0.50f),
                        radius = size.minDimension / 2f - 2f,
                        center = Offset(cx, cy),
                        style  = Stroke(width = 1.2f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .scale(orbScale * 0.98f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF1E1255), Color(0xFF0D0830))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF7C5CBF).copy(alpha = glow * 0.8f),
                                    Color(0xFF9B7BE8).copy(alpha = glow * 0.3f),
                                    Color(0xFF7C5CBF).copy(alpha = glow * 0.8f)
                                )
                            ), CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", fontSize = 24.sp, color = Color(0xFF9B7BE8).copy(alpha = glow))
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Title ─────────────────────────────────────────────────────────
            Text(
                "Your Circle Has Formed",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color         = Color(0xFFF0ECFF).copy(alpha = 0.95f),
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 22.sp,
                    letterSpacing = 0.3.sp,
                    textAlign     = TextAlign.Center
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Four strangers. Three days. One mystery.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color     = Color(0xFF7C78A8).copy(alpha = 0.75f),
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center
                )
            )

            // Thin divider
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .width(60.dp).height(0.8.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFF6B4FA8).copy(0.6f), Color.Transparent)
                        )
                    )
            )
            Spacer(Modifier.height(24.dp))

            // ── Member cards — staggered reveal ────────────────────────────────
            members.forEachIndexed { i, m ->
                var cardVisible by remember { mutableStateOf(false) }
                LaunchedEffect(visible) {
                    if (visible) {
                        kotlinx.coroutines.delay(500L + i * 280L)
                        cardVisible = true
                    }
                }
                val cardAlpha by animateFloatAsState(
                    targetValue   = if (cardVisible) 1f else 0f,
                    animationSpec = tween(500),
                    label         = "card$i"
                )
                val cardOffset by animateFloatAsState(
                    targetValue   = if (cardVisible) 0f else 18f,
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label         = "cardY$i"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .alpha(cardAlpha)
                        .offset(y = cardOffset.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (m.isYou)
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF1A1050).copy(0.95f), Color(0xFF241560).copy(0.95f))
                                )
                            else
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0E0C22).copy(0.95f), Color(0xFF131028).copy(0.95f))
                                )
                        )
                        .border(
                            if (m.isYou) 1.dp else 0.6.dp,
                            if (m.isYou) Color(0xFF7C4DFF).copy(alpha = 0.45f) else Color(0xFF2A2448).copy(alpha = 0.70f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (m.isYou)
                                        Brush.radialGradient(listOf(Color(0xFF2A1E5C), Color(0xFF130B35)))
                                    else
                                        Brush.radialGradient(listOf(Color(0xFF181428), Color(0xFF0C0A1E)))
                                )
                                .border(
                                    1.2.dp,
                                    if (m.isYou)
                                        Brush.sweepGradient(listOf(Color(0xFF9B7BE8), Color(0xFF5A3FA0), Color(0xFF9B7BE8)))
                                    else
                                        Brush.sweepGradient(listOf(Color(0xFF3A3465).copy(0.5f), Color(0xFF2A2448).copy(0.3f), Color(0xFF3A3465).copy(0.5f))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (m.photoUrl.isNotBlank() && !m.isBot) {
                                coil.compose.AsyncImage(
                                    model              = m.photoUrl,
                                    contentDescription = m.alias,
                                    contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier           = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(m.emoji, fontSize = 22.sp)
                            }
                        }

                        // Info
                        Column(
                            modifier            = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    m.alias,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color      = if (m.isYou) Color(0xFFB8A4E8) else Color(0xFFD8D4F0),
                                        fontSize   = 15.sp,
                                        fontWeight = if (m.isYou) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                                if (m.isYou) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF7C4DFF).copy(0.15f))
                                            .border(0.5.dp, Color(0xFF7C4DFF).copy(0.35f), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("you", style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFB57BFF).copy(0.80f), fontSize = 9.sp, letterSpacing = 1.sp
                                        ))
                                    }
                                }
                            }
                            Text(
                                m.gender,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color    = Color(0xFF6962A4).copy(0.70f),
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // Right status dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFF4ADE80).copy(0.85f), Color(0xFF22C55E).copy(0.4f))
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Hint text
            Text(
                "Photos are visible from Day 1. Everything else unlocks as you connect.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color     = Color(0xFF4F4A7A).copy(0.70f),
                    fontSize  = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── CTA button ─────────────────────────────────────────────────────
            var ctaVisible by remember { mutableStateOf(false) }
            LaunchedEffect(visible) {
                if (visible) {
                    kotlinx.coroutines.delay(400L + members.size * 280L + 600L)
                    ctaVisible = true
                }
            }
            val ctaAlpha by animateFloatAsState(if (ctaVisible) 1f else 0f, tween(500), label = "ctaA")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .alpha(ctaAlpha)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF3D1FA0), Color(0xFF5C2FD6), Color(0xFF3A1C9A))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(Color(0xFF9B7BE8).copy(0.5f), Color(0xFFB57BFF).copy(0.3f))
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable(enabled = ctaVisible) { onDone() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "I'm Ready  ✦",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color      = Color.White,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                )
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  No-group card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoGroupCard(
    errorMsg          : String,
    onReturnToWaiting : () -> Unit,
    modifier          : Modifier = Modifier
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("🌙", style = MaterialTheme.typography.displaySmall)
        Text(
            "Your circle isn't ready yet",
            style = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center, color = VeilTextPrimary
            )
        )
        Text(
            "You haven't joined a group yet,\nor your previous group session ended.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = VeilTextTertiary, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(VeilPrimary, VeilPrimaryMid)))
                .border(1.dp,
                    Brush.horizontalGradient(
                        listOf(VeilAccentGlow.copy(alpha = 0.4f), VeilAccentSand.copy(alpha = 0.2f))
                    ), RoundedCornerShape(18.dp))
                .clickable { onReturnToWaiting() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Find my circle  →",
                style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFFF0F4FA), fontSize = 15.sp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ceremony helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Returns how many whole seconds remain until 4 hours into Day 3 = ceremony unlock.
 *  Legacy helper kept for any remaining references; pass dayStartedAtMs directly in new code. */
private fun secondsUntil8PM(targetHour: Int = 20): Long {
    // Kept for build compatibility — new ceremony logic uses elapsed time, not wall-clock.
    val now      = java.util.Calendar.getInstance()
    val ceremony = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, targetHour)
        set(java.util.Calendar.MINUTE,      0)
        set(java.util.Calendar.SECOND,      0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val diff = ceremony.timeInMillis - now.timeInMillis
    return if (diff > 0) diff / 1_000L else 0L
}

/** Premium digit tile for ceremony countdown clock */
@Composable
private fun ClockChunk(value: String, label: String, color: Color, glowAlpha: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .width(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0D0820).copy(alpha = 0.85f))
                .border(0.8.dp, color.copy(alpha = glowAlpha * 0.45f), RoundedCornerShape(10.dp))
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(
                    color      = color.copy(alpha = 0.90f + glowAlpha * 0.1f),
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color.copy(alpha = 0.40f), fontSize = 8.sp, letterSpacing = 1.5.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  "Love is Filling" — Day 3 ceremony timer (water-wave fill animation)
// ─────────────────────────────────────────────────────────────────────────────

@Composable

private fun LoveFillingBanner(
    secsLeft  : Long,
    totalSecs : Long,
    onEnter   : () -> Unit,
    modifier  : Modifier = Modifier,
) {
    val haptic   = LocalHapticFeedback.current
    val h        = secsLeft / 3600L
    val m        = (secsLeft % 3600L) / 60L
    val s        = secsLeft % 60L
    val timeStr  = if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    val progress = if (totalSecs > 0) ((totalSecs - secsLeft).toFloat() / totalSecs.toFloat()).coerceIn(0f, 1f) else 1f
    val done     = secsLeft <= 0L

    val inf = rememberInfiniteTransition(label = "love")

    // Wave offset — two waves at different speeds
    val wave1 by inf.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart), "w1"
    )
    val wave2 by inf.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(4_800, easing = LinearEasing), RepeatMode.Restart), "w2"
    )

    // Global pulse for glow / border
    val pulse by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pulse"
    )

    // Floating heart bob
    val heartBob by inf.animateFloat(
        -4f, 4f,
        infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "hob"
    )

    // Shimmer sweep for unlocked state
    val shimmer by inf.animateFloat(
        -1f, 2f,
        infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart), "shi"
    )

    // pulse2 — for unlocked CTA glow (MUST be top-level, not inside else{})
    val pulse2 by inf.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse), "p2"
    )

    if (!done) {
        // ── FILLING STATE ──────────────────────────────────────────────────────
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0D0820))
                .border(
                    1.2.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF6B9D).copy(alpha = 0.30f + pulse * 0.50f),
                            Color(0xFFFF9CC0).copy(alpha = 0.20f + pulse * 0.30f),
                            Color(0xFFFF6B9D).copy(alpha = 0.30f + pulse * 0.50f),
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
        ) {
            // ── Water fill + wave Canvas ──────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                val w = size.width
                val h2 = size.height
                val fillY = h2 * (1f - progress)   // y where waterline sits

                // Solid fill below wave
                drawRect(
                    color  = Color(0xFFFF6B9D).copy(alpha = 0.22f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, fillY + 12f),
                    size    = androidx.compose.ui.geometry.Size(w, h2 - fillY)
                )

                // Wave 1 — slower, deeper pink
                val path1 = androidx.compose.ui.graphics.Path()
                path1.moveTo(0f, fillY)
                var x = 0f
                while (x <= w) {
                    val y = fillY + 9f * kotlin.math.sin(wave1 + x / w * 2 * Math.PI.toFloat())
                    if (x == 0f) path1.moveTo(x, y) else path1.lineTo(x, y)
                    x += 4f
                }
                path1.lineTo(w, h2); path1.lineTo(0f, h2); path1.close()
                drawPath(path1, color = Color(0xFFFF6B9D).copy(alpha = 0.30f))

                // Wave 2 — faster, lighter pink
                val path2 = androidx.compose.ui.graphics.Path()
                var x2 = 0f
                while (x2 <= w) {
                    val y2 = fillY + 6f * kotlin.math.sin(wave2 + x2 / w * 2 * Math.PI.toFloat() + 1f)
                    if (x2 == 0f) path2.moveTo(x2, y2) else path2.lineTo(x2, y2)
                    x2 += 4f
                }
                path2.lineTo(w, h2); path2.lineTo(0f, h2); path2.close()
                drawPath(path2, color = Color(0xFFFFB3D0).copy(alpha = 0.20f))

                // Tiny bubbles rising in the liquid
                val bubblePositions = listOf(0.2f, 0.45f, 0.70f, 0.85f)
                bubblePositions.forEachIndexed { i, xFrac ->
                    val bubbleY = fillY + (h2 - fillY) * ((wave1 + i) % (2 * Math.PI.toFloat())) / (2 * Math.PI.toFloat())
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.12f),
                        radius = 3.5f + i * 1.2f,
                        center = androidx.compose.ui.geometry.Offset(w * xFrac, bubbleY.coerceIn(fillY + 4f, h2 - 6f))
                    )
                }
            }

            // ── Overlay content ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top label
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier.size(5.dp).clip(CircleShape)
                            .background(Color(0xFFFF6B9D).copy(alpha = pulse))
                    )
                    Text(
                        "LOVE IS FILLING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFFF9CC0).copy(0.75f),
                            fontSize = 8.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold
                        )
                    )
                    Box(
                        Modifier.size(5.dp).clip(CircleShape)
                            .background(Color(0xFFFF6B9D).copy(alpha = pulse))
                    )
                }

                // Floating heart + timer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "❤️",
                        fontSize = 22.sp,
                        modifier = Modifier.offset(y = heartBob.dp)
                    )
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = Color(0xFFFFE0EC),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp,
                            letterSpacing = (-0.5).sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }

                // Progress %
                Text(
                    "${(progress * 100).toInt()}% · ceremony opens in ${
                        if (h > 0) "${h}h ${m}m" else "${m}m"
                    }",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFFFB3D0).copy(0.55f),
                        fontSize = 9.sp
                    )
                )
            }
        }

    } else {
        // ── CEREMONY OPEN — glowing amber CTA ─────────────────────────────────
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2C1600).copy(0.98f), Color(0xFF1E1000).copy(0.99f))
                    )
                )
                .border(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFFD77A).copy(0.35f + pulse2 * 0.55f),
                            Color(0xFFFFC842).copy(0.20f + pulse2 * 0.35f),
                            Color(0xFFFFD77A).copy(0.35f + pulse2 * 0.55f),
                        )
                    ),
                    RoundedCornerShape(22.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEnter()
                }
        ) {
            // Shimmer overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFFFFD77A).copy(0.06f),
                                Color.Transparent,
                            ),
                            start = androidx.compose.ui.geometry.Offset(shimmer * 700f - 100f, 0f),
                            end   = androidx.compose.ui.geometry.Offset(shimmer * 700f + 200f, 300f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape)
                        .background(Color(0xFFFFD77A).copy(0.5f + pulse2 * 0.5f)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "THE VEIL HAS LIFTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFFFD77A).copy(0.65f),
                            fontSize = 9.sp, letterSpacing = 2.8.sp, fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape)
                        .background(Color(0xFFFFD77A).copy(0.5f + pulse2 * 0.5f)))
                }
                Text(
                    "Enter the Ceremony",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color(0xFFFFE8A0),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                )
                Text(
                    "Faces revealed · Names unmasked · Your bond finally unveiled",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFD4BBFF).copy(0.55f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFD77A), Color(0xFFFFA500))
                            )
                        )
                        .padding(horizontal = 32.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Lift the Veil  ✨",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFF1A0A00),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CeremonyCountdownBanner — legacy, kept for build compatibility
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CeremonyCountdownBanner(
    secsLeft : Long,
    onEnter  : () -> Unit,
    modifier : Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val inf = rememberInfiniteTransition(label = "cer_banner")
    val pulse by inf.animateFloat(
        initialValue  = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "cerPulse"
    )
    val shimmer by inf.animateFloat(
        initialValue  = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart),
        label         = "cerShimmer"
    )

    if (secsLeft > 0) {
        // ── COUNTDOWN STATE ─────────────────────────────────────────────────────
        val h = secsLeft / 3600
        val m = (secsLeft % 3600) / 60
        val s = secsLeft % 60

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A0D30).copy(alpha = 0.95f), Color(0xFF0D0820).copy(alpha = 0.98f))
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFFD77A).copy(alpha = pulse * 0.5f),
                            Color(0xFFFFBB55).copy(alpha = pulse * 0.3f),
                            Color(0xFFFFD77A).copy(alpha = pulse * 0.5f)
                        )
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top row label
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD77A).copy(alpha = pulse))
                    )
                    Text(
                        "THE CEREMONY OPENS TONIGHT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color         = Color(0xFFFFD77A).copy(alpha = 0.70f),
                            fontSize      = 9.sp,
                            letterSpacing = 2.5.sp,
                            fontWeight    = FontWeight.SemiBold
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD77A).copy(alpha = pulse))
                    )
                }

                // Clock digits
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (h > 0) {
                        ClockChunk("%02d".format(h), "HRS",  Color(0xFFFFD77A), pulse)
                        Text(":", style = MaterialTheme.typography.titleLarge.copy(
                            color = Color(0xFFFFD77A).copy(alpha = 0.30f + pulse * 0.40f),
                            fontSize = 26.sp, fontWeight = FontWeight.Light
                        ))
                    }
                    ClockChunk("%02d".format(m), "MIN", Color(0xFFB57BFF), pulse)
                    Text(":", style = MaterialTheme.typography.titleLarge.copy(
                        color = Color(0xFFB57BFF).copy(alpha = 0.30f + pulse * 0.40f),
                        fontSize = 26.sp, fontWeight = FontWeight.Light
                    ))
                    ClockChunk("%02d".format(s), "SEC", Color(0xFF9E92CC), pulse)
                }

                // Bottom line
                Text(
                    "8:00 PM  ·  the veil lifts · your circle is revealed",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color    = Color(0xFFD4BBFF).copy(alpha = 0.30f),
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
                )
            }
        }

    } else {
        // ── 8PM UNLOCKED STATE — Premium CTA ─────────────────────────────────────
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF2C1600).copy(alpha = 0.98f),
                            Color(0xFF1E1000).copy(alpha = 0.99f)
                        )
                    )
                )
                .border(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFFD77A).copy(alpha = 0.35f + pulse * 0.55f),
                            Color(0xFFFFC842).copy(alpha = 0.20f + pulse * 0.35f),
                            Color(0xFFFFD77A).copy(alpha = 0.35f + pulse * 0.55f)
                        )
                    ),
                    RoundedCornerShape(22.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEnter()
                }
        ) {
            // Shimmer wash behind content
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFFFFD77A).copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            start = androidx.compose.ui.geometry.Offset(shimmer * 700f - 100f, 0f),
                            end   = androidx.compose.ui.geometry.Offset(shimmer * 700f + 200f, 300f)
                        )
                    )
            )

            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top label row
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.size(7.dp).clip(CircleShape)
                            .background(Color(0xFFFFD77A).copy(alpha = 0.5f + pulse * 0.5f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "THE VEIL HAS LIFTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color         = Color(0xFFFFD77A).copy(alpha = 0.65f),
                            fontSize      = 9.sp,
                            letterSpacing = 2.8.sp,
                            fontWeight    = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(7.dp).clip(CircleShape)
                            .background(Color(0xFFFFD77A).copy(alpha = 0.5f + pulse * 0.5f))
                    )
                }

                // Main title
                Text(
                    "Enter the Revelation",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color         = Color(0xFFFFE8A0),
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 22.sp,
                        letterSpacing = 0.2.sp
                    )
                )

                // Subtitle
                Text(
                    "Faces revealed · Names unmasked · Your journey ends tonight",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color     = Color(0xFFFFD77A).copy(alpha = 0.45f),
                        fontSize  = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                )

                Spacer(Modifier.height(4.dp))

                // Enter pill button
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(44.dp)
                        .scale(0.95f + pulse * 0.05f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFFC842).copy(alpha = 0.22f),
                                    Color(0xFFFFD77A).copy(alpha = 0.16f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFFD77A).copy(alpha = 0.50f + pulse * 0.30f),
                                    Color(0xFFFFC842).copy(alpha = 0.30f + pulse * 0.20f)
                                )
                            ),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Tap to Begin  →",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color      = Color(0xFFFFE8A0),
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp
                            )
                        )
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Connection Bond Sheet — shown when tapping a Soul Web line
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionBondSheet(
    member    : IslandMember,
    day       : Int,
    isDay     : Boolean,
    onDismiss : () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "connGlow")
    val pulse by inf.animateFloat(
        initialValue  = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cg"
    )

    val sheetBg  = if (isDay) Color(0xFFF8FAFC) else Color(0xFF111827)
    val lineCol  = if (isDay) Color(0xFF3B82F6) else Color(0xFF60A5FA)
    val bodyText = when (day) {
        1    -> "This thread connects you both in mystery.\nSomething is already forming between you."
        2    -> "Each interaction has charged this line.\nYou're resonating — keep going deeper."
        else -> "This connection carries the weight of 3 days.\nIt's more than a thread now."
    }
    val bondLabel = when (day) {
        1    -> "✦  mystery thread"
        2    -> "✦  resonance line"
        else -> "✦  legacy bond"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = sheetBg,
        scrimColor       = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                Modifier.padding(top = 14.dp, bottom = 6.dp)
                    .width(44.dp).height(4.dp).clip(CircleShape)
                    .background(lineCol.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pulsing orb
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                lineCol.copy(alpha = 0.20f * pulse),
                                (if (isDay) Color(0xFFF8FAFC) else Color(0xFF141E30))
                            )
                        )
                    )
                    .border(1.2.dp, lineCol.copy(alpha = 0.45f * pulse), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(member.emoji, fontSize = 32.sp)
            }

            Text(
                bondLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    color         = lineCol.copy(alpha = 0.65f),
                    fontSize      = 9.sp,
                    letterSpacing = 2.sp
                )
            )

            Text(
                bodyText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color      = if (isDay) Color(0xFF475569) else Color(0xFFCBD5E1),
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp,
                    fontSize   = 14.sp
                )
            )

            // Animated connection line visual
            Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                val y   = size.height / 2f
                val dot = (pulse * size.width)
                drawLine(
                    brush       = Brush.horizontalGradient(
                        listOf(Color.Transparent, lineCol.copy(0.25f), lineCol.copy(0.15f), Color.Transparent)
                    ),
                    start       = Offset(0f, y), end = Offset(size.width, y),
                    strokeWidth = 1.5f
                )
                drawCircle(
                    color  = lineCol.copy(alpha = pulse * 0.85f),
                    radius = 5f,
                    center = Offset(dot, y)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Day Countdown Chip — tiny, non-intrusive, shows time until next day
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DayCountdownChip(
    remainingMs : Long,
    currentDay  : Int,
    isDay       : Boolean,
    onClick     : () -> Unit,
) {
    val totalMs  = 8 * 3_600_000L  // 8 hours in ms
    val progress = ((totalMs - remainingMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    val hours   = (remainingMs / 3_600_000L).toInt()
    val minutes = ((remainingMs % 3_600_000L) / 60_000L).toInt()
    val seconds = ((remainingMs % 60_000L) / 1_000L).toInt()

    val timeStr = when {
        hours > 0  -> "%02d:%02d:%02d".format(hours, minutes, seconds)
        else       -> "%02d:%02d".format(minutes, seconds)
    }

    val nextDayLabel = "Day ${(currentDay + 1)}"

    // Pulse the clock icon when < 30 min left
    val inf = rememberInfiniteTransition(label = "timer")
    val urgentPulse by inf.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "up"
    )
    val isUrgent    = remainingMs < 30 * 60_000L
    val iconAlpha   = if (isUrgent) urgentPulse else 1f

    // Color scheme
    val cardBg      = if (isDay) Color(0xFFFDF8F0) else Color(0xFF1C1830)
    val cardBorder  = if (isDay) Color(0xFFEDD9C0) else Color(0xFF3A3050)
    val labelColor  = if (isDay) Color(0xFF7A5C3A) else Color(0xFFB8A4E4)
    val timeColor   = if (isDay) Color(0xFF2D1F0E) else Color(0xFFF0ECFF)
    val barBg       = if (isDay) Color(0xFFF0E6D4) else Color(0xFF2A2440)
    val barFg       = when {
        isUrgent   -> Color(0xFFE05A2B)   // orange-red when urgent
        isDay      -> Color(0xFFE8832A)   // amber
        else       -> Color(0xFF9B7FE8)   // purple for night
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(0.8.dp, cardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: "Day X → Day Y"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Day $currentDay",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = labelColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    "→",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = labelColor.copy(0.5f), fontSize = 9.sp
                    )
                )
                Text(
                    nextDayLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = barFg,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 10.sp
                    )
                )
            }
            // Right: Live countdown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "⏱",
                    fontSize = 10.sp,
                    modifier = Modifier.alpha(iconAlpha)
                )
                Text(
                    timeStr,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color      = timeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        letterSpacing = (-0.3).sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Progress bar — time elapsed in this day
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(barBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(listOf(barFg.copy(0.7f), barFg))
                    )
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Day Peek Sheet — what unlocks on the next day
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPeekSheet(
    currentDay : Int,
    onDismiss  : () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color(0xFF1A1D2E),
        dragHandle       = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle
            Box(Modifier.size(32.dp, 3.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.15f)).align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(4.dp))

            Text(
                "Coming in Day ${currentDay + 1}",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White, fontWeight = FontWeight.Bold)
            )
            Text(
                "A sneak peek at what unlocks tomorrow",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(0.45f))
            )

            val features: List<Triple<String, String, String>> = when (currentDay) {
                1 -> listOf(
                    Triple("📸", "Profile Photos Visible",
                        "See the faces of your circle for the first time."),
                    Triple("🎮", "Tic-Tac-Toe",
                        "Challenge a circle-mate to a quick game."),
                    Triple("🤔", "Would You Rather",
                        "Bold questions that reveal who you really are."),
                    Triple("🗣️", "Slow Chat unlocks",
                        "Start your first private 7-message conversation."),
                )
                2 -> listOf(
                    Triple("🎭", "The Ceremony",
                        "At 8 PM the veil lifts — full reveals begin."),
                    Triple("✅", "True or Myth",
                        "Is it fact or fiction? Post your own."),
                    Triple("💬", "Unlimited Chat",
                        "Unlock unlimited messages with someone special."),
                    Triple("🏝️", "Party Island",
                        "All 4 enter together for the grand finale."),
                )
                else -> emptyList()
            }

            features.forEach { (emoji, title, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.05f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, fontSize = 22.sp)
                    Column {
                        Text(title, style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White, fontWeight = FontWeight.SemiBold))
                        Text(desc, style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(0.45f), lineHeight = 15.sp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CircleBondPrompt — rotating warm nudges on the Island
//  Alpha-only animation: layout never shifts. Cycles every 8 seconds.
//  Each day has its own emotional prompt set to match where users are.
// ─────────────────────────────────────────────────────────────────────────────

private val BOND_PROMPTS_DAY1 = listOf(
    "Your room has no time limit — take as long as you need",
    "You're known by your personality here, not your face",
    "Say what you actually think. They won't know who you are",
    "Every message in this circle is read carefully",
    "You're not being judged — you're being felt",
)

private val BOND_PROMPTS_DAY2 = listOf(
    "Go a little deeper today — they're ready for it",
    "Share a song that reminds you of today in Vibes ✦",
    "This bond evolves. You're in the middle of it",
    "The best conversations aren't about answers, they're about honesty",
    "You know them a little more today than yesterday",
)

private val BOND_PROMPTS_DAY3 = listOf(
    "Today is the last day of the veil",
    "Share a song that captures how this felt — use Vibes",
    "You've come further than most people ever do with strangers",
    "Whatever happens in the ceremony — this was real",
    "You don't need to say goodbye. This circle stays with you",
)

@Composable
private fun CircleBondPrompt(
    day         : Int,
    isDay       : Boolean,
    onOpenVibes : () -> Unit,
    modifier    : Modifier = Modifier
) {
    val prompts = remember(day) {
        when {
            day >= 3 -> BOND_PROMPTS_DAY3
            day == 2 -> BOND_PROMPTS_DAY2
            else     -> BOND_PROMPTS_DAY1
        }.shuffled()
    }

    var idx       by remember { mutableStateOf(0) }
    val alpha     =  remember { Animatable(1f) }

    LaunchedEffect(prompts) {
        while (true) {
            kotlinx.coroutines.delay(8_000)
            alpha.animateTo(0f, tween(420))
            idx = (idx + 1) % prompts.size
            alpha.animateTo(1f, tween(420))
        }
    }

    // Tap opens Vibes if prompt mentions it
    val prompt = prompts.getOrElse(idx) { prompts[0] }
    val isVibesPrompt = prompt.contains("Vibes", ignoreCase = true)

    val textColor   = if (isDay) Color(0xFF5A4A7A).copy(0.75f) else Color(0xFF9A8FC0).copy(0.65f)
    val dotColor    = if (isDay) Color(0xFF6B8AE0).copy(0.55f) else Color(0xFF7B7FFF).copy(0.55f)
    val borderColor = if (isDay) Color(0xFFD0C8F0).copy(0.40f) else Color(0xFF3A3560).copy(0.55f)
    val bgColor     = if (isDay) Color(0xFFF0EEFF).copy(0.55f) else Color(0xFF14102A).copy(0.55f)

    val inf = rememberInfiniteTransition(label = "bpDot")
    val dotAlpha by inf.animateFloat(
        0.35f, 0.90f,
        infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Reverse),
        "bpd"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .alpha(alpha.value)
                .clip(RoundedCornerShape(18.dp))
                .background(bgColor)
                .border(0.6.dp, borderColor, RoundedCornerShape(18.dp))
                .then(if (isVibesPrompt) Modifier.clickable { onOpenVibes() } else Modifier)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = dotAlpha))
            )
            Text(
                text     = prompt,
                fontSize = 11.sp,
                color    = textColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}



