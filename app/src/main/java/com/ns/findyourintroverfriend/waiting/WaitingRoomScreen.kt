package com.ns.findyourintroverfriend.waiting

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
//  Rotating quotes — shown while waiting
// ─────────────────────────────────────────────────────────────────────────────

private val VEIL_QUOTES = listOf(
    "Good things take time.\nYour circle is on its way.",
    "Depth over speed.\nThis is how it always should have been.",
    "Every genuine connection\nbegins in silence.",
    "You don't need a crowd.\nYou need the right four.",
    "Slow down.\nThe best conversations can't be rushed.",
    "Real curiosity\nis worth the wait.",
    "The quieter you are,\nthe more you can hear.",
    "Somewhere out there,\nyour circle is already searching too.",
    "The most meaningful things\nhappen when you stop rushing.",
    "Being selective isn't loneliness.\nIt's a higher standard for connection.",
    "Three strangers + you\n= something you haven't felt before.",
    "Great chemistry takes time to find.\nBut only a second to feel.",
)

// ─────────────────────────────────────────────────────────────────────────────
//  Waiting Room Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingRoomScreen(
    vm           : WaitingRoomViewModel = viewModel(),
    onGroupFound : (groupId: String) -> Unit,
    onLeave      : () -> Unit = {},    // ← navigate to VeilHomeScreen
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var showRoomsPanel by remember { mutableStateOf(false) }

    // FIX #1: check backgroundMode before cleaning up queue entry
    val isBackgroundMode by vm.isBackgroundMode.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose {
            if (vm.state.value !is WaitingUiState.GroupFound &&
                vm.state.value !is WaitingUiState.CircleFull &&
                !isBackgroundMode) {
                vm.leaveQueue()
            }
        }
    }

    var showOnboarding    by remember { mutableStateOf(false) }
    var pendingGroupId    by remember { mutableStateOf("") }

    // -- CircleFull: 4/4 detected in LIVE QUEUE - show Begin button immediately
    // Note: we check !showOnboarding so that after tapping Begin, we don't re-show GroupReadyOverlay
    if (state is WaitingUiState.CircleFull && !showOnboarding) {
        val s = state as WaitingUiState.CircleFull
        GroupReadyOverlay(
            members = s.members,
            onBegin = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showOnboarding = true
                vm.beginJourney(s.batchUids)
            }
        )
        return
    }

    // -- GroupFound: group ID arrives - store it; navigate only if onboarding already dismissed
    LaunchedEffect(state) {
        if (state is WaitingUiState.GroupFound) {
            pendingGroupId = (state as WaitingUiState.GroupFound).groupId
            if (!showOnboarding) {
                // Onboarding was not triggered (e.g. edge case) - navigate directly
                onGroupFound(pendingGroupId)
            }
            // If onboarding IS showing, VeilOnboardingOverlay.onDone() will call onGroupFound
        }
    }

    // -- Onboarding overlay - shown immediately after tapping Begin, before Island loads
    if (showOnboarding) {
        VeilOnboardingOverlay(
            onDone = {
                showOnboarding = false
                if (pendingGroupId.isNotBlank()) {
                    onGroupFound(pendingGroupId)
                }
                // If group not formed yet, the LaunchedEffect above will navigate when ready
            }
        )
        return
    }

        // ── Back press — ask "Are you sure you want to quit?" ──────────────────────
    var showQuitDialog by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { showQuitDialog = true }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            containerColor   = Color(0xFF0E0B1E),
            shape            = RoundedCornerShape(20.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                    Text("🌙", fontSize = 32.sp)
                    Spacer(androidx.compose.ui.Modifier.height(8.dp))
                    Text(
                        "Leave the queue?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color(0xFFD4BBFF), fontWeight = FontWeight.Bold
                        )
                    )
                }
            },
            text = {
                Text(
                    "Your circle may still find you. You can search again anytime from your home screen.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF9090BB), textAlign = TextAlign.Center, lineHeight = 20.sp
                    )
                )
            },
            confirmButton = {
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1040))
                        .border(0.7.dp, Color(0xFF7C4DFF).copy(0.5f), RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showQuitDialog = false
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "🔄 Keep Waiting",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFFB57BFF), fontWeight = FontWeight.Bold
                        )
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF))
                        .border(0.5.dp, Color(0xFF444466), RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showQuitDialog = false
                            vm.leaveQueue()
                            onLeave()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Leave Queue",
                        style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF8888AA))
                    )
                }
            }
        )
    }

        // Rotating quotes — Animatable alpha, no boolean visibility toggle
    var quoteIndex  by remember { mutableStateOf(0) }
    val quoteAlpha  = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_600)
            quoteAlpha.animateTo(0f, tween(380))
            quoteIndex = (quoteIndex + 1) % VEIL_QUOTES.size
            quoteAlpha.animateTo(1f, tween(380))
        }
    }

    // Elapsed wait timer
    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            elapsedSeconds++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1628),  // Deep ocean blue
                        Color(0xFF0D2137),  // Rich navy
                        Color(0xFF0F1B2E),  // Dark blue-grey
                        Color(0xFF0A1520),  // Almost black with blue tint
                        Color(0xFF050A12)   // Deep space blue
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background effects
        AmbientRings()
        
        // Additional atmospheric glow layers
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top glow - electric cyan
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00D9FF).copy(alpha = 0.12f),
                        Color(0xFF00A8CC).copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.15f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.5f, size.height * 0.15f)
            )
            
            // Bottom glow - vibrant teal
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00FFB3).copy(alpha = 0.08f),
                        Color(0xFF00CC88).copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.85f),
                    radius = size.width * 0.5f
                ),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.5f, size.height * 0.85f)
            )
        }

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top bar: VEIL tag + Rooms button ─────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Rooms button with enhanced design
                val totalRooms = if (state is WaitingUiState.InQueue)
                    (state as WaitingUiState.InQueue).completedRooms.size else 0
                val pendingCount = if (state is WaitingUiState.InQueue)
                    (state as WaitingUiState.InQueue).pendingRooms.size else 0

                val buttonScale by animateFloatAsState(
                    targetValue = if (totalRooms > 0 || pendingCount > 0) 1f else 0.95f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "roomsButtonScale"
                )

                Box(
                    modifier = Modifier
                        .scale(buttonScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF111827),
                                    Color(0xFF0F1520)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF2A3850),
                                    Color(0xFF1A2840)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showRoomsPanel = true
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Animated moon
                        val moonRotation by rememberInfiniteTransition(label = "moon").animateFloat(
                            initialValue  = -5f,
                            targetValue   = 5f,
                            animationSpec = infiniteRepeatable(
                                animation  = tween(2_000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "moonRot"
                        )
                        
                        Text(
                            "🌙",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.graphicsLayer { rotationZ = moonRotation }
                        )
                        
                        Text(
                            text  = if (totalRooms > 0) "$totalRooms active" else "Rooms",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = Color(0xFF6BA4E0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        if (pendingCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF3A5A9F),
                                                Color(0xFF2A4A7F)
                                            )
                                        )
                                    )
                                    .border(0.5.dp, Color(0xFF4A6AAF).copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$pendingCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color    = Color(0xFF8BB8FF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // VEIL wordmark with glow - cyan color
                val veilGlow by rememberInfiniteTransition(label = "veilGlow").animateFloat(
                    initialValue  = 0.5f,
                    targetValue   = 1f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(3_000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "veilGlowAlpha"
                )
                
                Text(
                    text  = "VEIL",
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 6.sp,
                        color         = Color(0xFF00D9FF).copy(alpha = 0.8f * veilGlow),
                        fontWeight    = FontWeight.ExtraBold
                    )
                )

                // Spacer to balance layout
                Spacer(Modifier.width(80.dp))
            }

            // ── Centre block ────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                FourSlotVisualiser(state = state)
                StatusText(state = state, elapsedSeconds = elapsedSeconds)
                QuoteCard(quote = VEIL_QUOTES[quoteIndex], alpha = quoteAlpha.value)
            }

            // ── Bottom section — FIX #4 honest wait UI + FIX #1 Notify Me ─────
            Column(
                modifier            = Modifier.padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state is WaitingUiState.Error) {
                    Text(
                        text  = (state as WaitingUiState.Error).msg,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color     = VeilError,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                // Live queue count
                val totalInQueue = if (state is WaitingUiState.InQueue)
                    (state as WaitingUiState.InQueue).totalInQueue else 0
                if (totalInQueue > 0) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399))
                        )
                        Text(
                            "$totalInQueue people in queue right now",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = Color(0xFF8888AA),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Honest wait estimate
                Text(
                    "Usually takes 5–20 minutes",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color    = Color(0xFF555577),
                        fontSize = 11.sp
                    )
                )

                Spacer(Modifier.height(4.dp))

                // FIX #1: Notify Me — prominent amber button
                val context = LocalContext.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFC567), Color(0xFFFFD080))
                            )
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.stayInQueueAndLeave(context)
                            onLeave()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🔔  Notify me — I'll close the app",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color      = Color(0xFF1A1208),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp
                        )
                    )
                }

                // Leave queue (small, subtle)
                Text(
                    "Leave queue entirely",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color    = Color(0xFF444466),
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showQuitDialog = true
                    }
                )
            }
        }

        // Leave queue button — small, subtle, bottom-left corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x15FFFFFF))
                .border(0.5.dp, Color(0x30FFFFFF), RoundedCornerShape(20.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showQuitDialog = true
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text  = "← Leave",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = Color(0x88FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }
    }


    // ── Rooms Panel Bottom Sheet ─────────────────────────────────────────────
    if (showRoomsPanel && state is WaitingUiState.InQueue) {
        RoomsPanelSheet(
            state    = state as WaitingUiState.InQueue,
            onDismiss = { showRoomsPanel = false }
        )
    }
}

// DevBotButton removed — use Dev Panel (🛠 DEV button on VeilHome) instead

// ─────────────────────────────────────────────────────────────────────────────
//  Four-slot visualiser
// ─────────────────────────────────────────────────────────────────────────────

private data class SlotInfo(val genderSymbol: String, val filled: Boolean)

@Composable
private fun FourSlotVisualiser(state: WaitingUiState) {
    // Always fixed ♂ ♂ ♀ ♀ — fills based on MY BATCH progress (max 2 each)
    val m = if (state is WaitingUiState.InQueue) state.malesReady   else 0
    val f = if (state is WaitingUiState.InQueue) state.femalesReady else 0

    val slots = listOf(
        SlotInfo("♂", m >= 1),
        SlotInfo("♂", m >= 2),
        SlotInfo("♀", f >= 1),
        SlotInfo("♀", f >= 2),
    )

    val positions = listOf(
        Pair(0.50f, 0.00f),
        Pair(0.00f, 0.50f),
        Pair(1.00f, 0.50f),
        Pair(0.50f, 1.00f),
    )

    val orbiterSize = 72.dp
    val gridSize    = 220.dp

    Box(
        modifier         = Modifier.size(gridSize),
        contentAlignment = Alignment.Center
    ) {
        CentralOrb()
        ConnectingLines(slots = slots, boxSize = gridSize)

        slots.forEachIndexed { i, slot ->
            val (xF, yF) = positions[i]
            val xOff = ((xF - 0.5f) * (gridSize.value - orbiterSize.value)).dp
            val yOff = ((yF - 0.5f) * (gridSize.value - orbiterSize.value)).dp

            AvatarSlotCircle(
                slot     = slot,
                index    = i,
                modifier = Modifier.offset(x = xOff, y = yOff)
            )
        }
    }
}

@Composable
private fun CentralOrb() {
    val inf = rememberInfiniteTransition(label = "orbPulse")
    val scale by inf.animateFloat(
        initialValue  = 0.88f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )
    val alpha by inf.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 0.9f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbAlpha"
    )
    val rotation by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(15_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )
    
    // Sonar rings - electric cyan
    val sonar1 by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        "s1"
    )
    val sonar2 by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_400, delayMillis = 800, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        "s2"
    )
    val sonar3 by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_400, delayMillis = 1_600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        "s3"
    )

    Box(contentAlignment = Alignment.Center) {
        // Sonar rings drawn behind orb
        Canvas(modifier = Modifier.size(120.dp)) {
            listOf(sonar1, sonar2, sonar3).forEach { progress ->
                drawCircle(
                    color  = Color(0xFF00D9FF).copy(alpha = (1f - progress) * 0.45f),
                    radius = (size.minDimension / 2f) * (0.3f + progress * 0.7f),
                    style  = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
        
        // Rotating outer ring with cyan-teal gradient
        Canvas(modifier = Modifier.size(80.dp).graphicsLayer { rotationZ = rotation }) {
            val ringRadius = size.minDimension / 2f - 4.dp.toPx()
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00D9FF).copy(alpha = 0.8f),
                        Color(0xFF00FFB3).copy(alpha = 0.5f),
                        Color.Transparent,
                        Color.Transparent,
                        Color(0xFF00D9FF).copy(alpha = 0.8f)
                    )
                ),
                radius = ringRadius,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Main orb with cyan-teal gradient
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00FFB3).copy(alpha = alpha),
                            Color(0xFF00D9FF).copy(alpha = alpha * 0.7f),
                            Color(0xFF0088AA).copy(alpha = alpha * 0.5f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00D9FF).copy(alpha = 0.6f),
                            Color(0xFF00FFB3).copy(alpha = 0.4f)
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Inner glow dot - bright cyan
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(scale * 0.8f)
                .clip(CircleShape)
                .background(Color(0xFF00FFFF).copy(alpha = alpha * 0.8f))
        )
    }
}

@Composable
private fun ConnectingLines(slots: List<SlotInfo>, boxSize: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "lineFlow")
    val flowProgress by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow"
    )
    
    Canvas(modifier = Modifier.size(boxSize)) {
        val cx = size.width  / 2f
        val cy = size.height / 2f
        val r  = (size.minDimension / 2f) * 0.72f
        val angles = listOf(270f, 180f, 0f, 90f)
        
        angles.forEachIndexed { i, angleDeg ->
            val rad = Math.toRadians(angleDeg.toDouble())
            val tx  = (cx + r * cos(rad)).toFloat()
            val ty  = (cy + r * sin(rad)).toFloat()
            
            val isFilled = slots[i].filled
            
            // Base line - cyan when filled
            drawLine(
                color       = if (isFilled) Color(0xFF00D9FF).copy(alpha = 0.6f) else Color(0xFF1A3A4A).copy(alpha = 0.3f),
                start       = Offset(cx, cy),
                end         = Offset(tx, ty),
                strokeWidth = if (isFilled) 1.5.dp.toPx() else 1.2.dp.toPx(),
                cap         = StrokeCap.Round
            )
            
            // Animated flow effect when filled - electric cyan
            if (isFilled) {
                val flowPos = flowProgress
                val flowX = cx + (tx - cx) * flowPos
                val flowY = cy + (ty - cy) * flowPos
                
                // Draw flowing dot
                drawCircle(
                    color  = Color(0xFF00FFB3).copy(alpha = 0.9f),
                    radius = 3.dp.toPx(),
                    center = Offset(flowX, flowY)
                )
                
                // Draw glow trail
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00FFB3).copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        radius = 8.dp.toPx()
                    ),
                    radius = 8.dp.toPx(),
                    center = Offset(flowX, flowY)
                )
            }
        }
    }
}

@Composable
private fun AvatarSlotCircle(slot: SlotInfo, index: Int, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "slot_$index")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.2f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_600 + index * 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_$index"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue  = 0.95f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(3_000 + index * 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slotScale_$index"
    )
    
    // Fill animation
    val fillScale by animateFloatAsState(
        targetValue = if (slot.filled) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fillScale_$index"
    )
    
    val fillAlpha by animateFloatAsState(
        targetValue = if (slot.filled) 1f else 0.4f,
        animationSpec = tween(400),
        label = "fillAlpha_$index"
    )
    
    Box(
        modifier = modifier
            .size(54.dp)
            .scale(scale)
            .graphicsLayer { alpha = fillAlpha },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring when filled - cyan glow
        if (slot.filled) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(fillScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D9FF).copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        
        // Main circle
        Box(
            modifier = Modifier
                .size(54.dp)
                .scale(fillScale)
                .clip(CircleShape)
                .background(
                    if (slot.filled) 
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1A3A4A),
                                Color(0xFF0F2530),
                                Color(0xFF0A1520)
                            )
                        )
                    else             
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0F2530),
                                Color(0xFF0A1520)
                            )
                        )
                )
                .border(
                    width = if (slot.filled) 2.dp else 1.dp,
                    brush = if (slot.filled)
                        Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF00D9FF),
                                Color(0xFF00FFB3),
                                Color(0xFF00A8CC),
                                Color(0xFF00D9FF)
                            )
                        )
                    else
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D9FF).copy(alpha = ringAlpha * 0.4f),
                                Color(0xFF1A3A4A).copy(alpha = 0.3f)
                            )
                        ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (slot.filled) {
                // Filled state - show gender symbol with cyan color
                Text(
                    text  = slot.genderSymbol,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color    = Color(0xFF00FFB3),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.scale(fillScale)
                )
            } else {
                // Empty state - show pulsing dot
                BeatDot(delayMs = index * 200L)
            }
        }
    }
}

@Composable
private fun BeatDot(delayMs: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.15f,
        targetValue   = 0.6f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, delayMillis = delayMs.toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFF00D9FF).copy(alpha = alpha))
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Status text
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusText(state: WaitingUiState, elapsedSeconds: Long = 0L) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val title = when (state) {
            is WaitingUiState.Joining     -> "Preparing your space…"
            is WaitingUiState.InQueue     -> "Finding your circle…"
            is WaitingUiState.CircleFull  -> "Your circle is complete! 🌙"
            is WaitingUiState.GroupFound  -> "Your circle is ready! 🌙"
            is WaitingUiState.NoMatchYet  -> "Still searching…"
            is WaitingUiState.Error       -> "Something went wrong"
        }

        Text(
            text  = title,
            style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)
        )

        if (state is WaitingUiState.InQueue) {
            val m     = state.malesReady
            val f     = state.femalesReady
            val total = m + f

            Text(
                text  = "$total of 4 in your circle",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color     = VeilTextSecondary,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                GenderCounter(symbol = "♂", filled = m, max = 2)
                Box(Modifier.width(1.dp).height(18.dp).background(VeilDivider))
                GenderCounter(symbol = "♀", filled = f, max = 2)
            }

            // ── Who has joined — show names as they arrive ────────────────
            if (state.members.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text  = "In your circle",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = VeilTextSecondary.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp
                        )
                    )
                    state.members.forEachIndexed { index, member ->
                        // Entrance animation for each member
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(member.uid) {
                            kotlinx.coroutines.delay(index * 100L)
                            visible = true
                        }
                        
                        val slideOffset by animateFloatAsState(
                            targetValue = if (visible) 0f else 30f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "memberSlide_$index"
                        )
                        
                        val memberAlpha by animateFloatAsState(
                            targetValue = if (visible) 1f else 0f,
                            animationSpec = tween(400),
                            label = "memberAlpha_$index"
                        )
                        
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = slideOffset
                                    alpha = memberAlpha
                                }
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (member.gender == "Male")
                                            listOf(Color(0xFF1A2A3F), Color(0xFF152030))
                                        else
                                            listOf(Color(0xFF2A1A35), Color(0xFF201028))
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (member.gender == "Male") 
                                        Color(0xFF2A4A6F).copy(alpha = 0.5f)
                                    else 
                                        Color(0xFF4A2A5F).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            // Gender symbol with glow
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (member.gender == "Male")
                                            Color(0xFF2A4A7F).copy(alpha = 0.3f)
                                        else
                                            Color(0xFF4A2A7F).copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text  = if (member.gender == "Male") "♂" else "♀",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color    = if (member.gender == "Male") Color(0xFF6BA4E0) else Color(0xFFB07AE0),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                            Text(
                                text  = member.alias,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color    = VeilTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            // ── Elapsed time ─────────────────────────────────────────────
            if (elapsedSeconds >= 10L) {
                Spacer(Modifier.height(4.dp))
                val mins = elapsedSeconds / 60
                val secs = elapsedSeconds % 60
                val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                Text(
                    text  = "Searching for $timeStr  —  worth it",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color     = VeilTextTertiary.copy(0.50f),
                        textAlign = TextAlign.Center,
                        fontSize  = 10.sp
                    )
                )
            }

            // ── Social proof ──────────────────────────────────────────────
            val completedCount = state.completedRooms.size
            if (completedCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = "🌙 $completedCount circle${if (completedCount == 1) "" else "s"} active right now",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color     = VeilAccentSand.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontSize  = 11.sp
                    )
                )
            }
        }

        if (state is WaitingUiState.Joining) {
            BreathingDots()
        }
    }
}

@Composable
private fun GenderCounter(symbol: String, filled: Int, max: Int) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text  = symbol,
            style = MaterialTheme.typography.bodyMedium.copy(color = VeilAccentGlow, fontSize = 16.sp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..max).forEach { i ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (i <= filled) VeilAccentGlow else VeilSurface)
                        .border(0.8.dp, VeilDivider, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun BreathingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..2).forEach { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue  = 0.2f,
                targetValue   = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(600, delayMillis = i * 200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$i"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(VeilAccentGlow.copy(alpha = alpha))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quote card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuoteCard(quote: String, alpha: Float = 1f) {
    val infiniteTransition = rememberInfiniteTransition(label = "quoteGlow")
    val borderGlow by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(3_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderGlow"
    )
    
    // Fixed-size card — only opacity animates, layout never shifts
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2530).copy(alpha = 0.8f),
                        Color(0xFF0A1520).copy(alpha = 0.6f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00D9FF).copy(alpha = borderGlow * 0.4f),
                        Color(0xFF00FFB3).copy(alpha = borderGlow * 0.3f),
                        Color(0xFF00D9FF).copy(alpha = borderGlow * 0.4f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Animated sparkle - cyan color
            val sparkleRotation by infiniteTransition.animateFloat(
                initialValue  = 0f,
                targetValue   = 360f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(8_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sparkle"
            )
            
            Text(
                "✦",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF00FFB3).copy(alpha = borderGlow)
                ),
                modifier = Modifier.graphicsLayer { rotationZ = sparkleRotation }
            )
            
            Text(
                text  = quote,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color      = Color(0xFFB0D0E0),
                    textAlign  = TextAlign.Center,
                    lineHeight = 24.sp,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Notification note
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationNote() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VeilSurface.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text     = "🔔  We'll notify you the moment your circle is complete.\nYou don't need to keep the app open.",
            style    = MaterialTheme.typography.labelSmall.copy(
                color      = VeilTextTertiary,
                textAlign  = TextAlign.Center,
                lineHeight = 18.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ambient rings
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AmbientRings() {
    val infiniteTransition = rememberInfiniteTransition(label = "ambientRings")
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue  = 0.7f,
        targetValue   = 1.1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(5_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "r1"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(7_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "r2"
    )
    val ring3Rotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "r3"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width  / 2f
        val cy = size.height / 2f

        // Enhanced gradient rings with cyan/teal colors
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00D9FF).copy(alpha = 0.15f),
                    Color(0xFF00A8CC).copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = size.minDimension * 0.55f * ring1Scale
            ),
            radius = size.minDimension * 0.55f * ring1Scale,
            center = Offset(cx, cy)
        )
        
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00FFB3).copy(alpha = 0.12f),
                    Color(0xFF00CC88).copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = size.minDimension * 0.75f * ring2Scale
            ),
            radius = size.minDimension * 0.75f * ring2Scale,
            center = Offset(cx, cy)
        )
        
        // Rotating ring with dots - electric blue
        val dotCount = 8
        val ringRadius = size.minDimension * 0.48f
        for (i in 0 until dotCount) {
            val angle = (ring3Rotation + i * (360f / dotCount)) * (PI / 180f)
            val x = cx + ringRadius * cos(angle).toFloat()
            val y = cy + ringRadius * sin(angle).toFloat()
            drawCircle(
                color  = Color(0xFF00D9FF).copy(alpha = 0.25f),
                radius = 2.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Outer decorative ring - electric cyan
        drawCircle(
            color  = Color(0xFF00D9FF).copy(alpha = 0.12f),
            radius = size.minDimension * 0.48f,
            center = Offset(cx, cy),
            style  = Stroke(width = 1.dp.toPx())
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Group Ready Overlay — shown when all 4 are in the circle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GroupReadyOverlay(
    members : List<MemberPreview>,
    onBegin : () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 0.9f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue  = 0.95f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050C18),
                        Color(0xFF0A1528),
                        Color(0xFF050C18)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glowing backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3A8FDE).copy(alpha = 0.15f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.7f * ringScale
                ),
                radius = size.minDimension * 0.7f * ringScale,
                center = Offset(cx, cy)
            )
        }

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Moon emoji ─────────────────────────────────────────────────
            Text(
                text  = "🌕",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 64.sp
                )
            )

            Spacer(Modifier.height(24.dp))

            // ── Headline ───────────────────────────────────────────────────
            Text(
                text  = "Your Circle Found You",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color         = Color(0xFFF0F4F8),
                    textAlign     = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text  = "Four people. All real. All here.\nSomething is about to begin.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color      = Color(0xFF6A8AAA),
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp
                )
            )

            Spacer(Modifier.height(36.dp))

            // ── Member cards ───────────────────────────────────────────────
            if (members.isNotEmpty()) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D1828))
                        .border(1.dp, Color(0xFF1E3050), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text  = "YOUR CIRCLE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color         = Color(0xFF3A5A8A),
                            fontSize      = 10.sp,
                            letterSpacing = 2.sp
                        )
                    )
                    members.forEach { member ->
                        val gSym  = when (member.gender) { "Male" -> "♂" ; "Female" -> "♀" ; else -> "⚧" }
                        val isMale = member.gender == "Male"
                        val dotBg = when (member.gender) {
                            "Male"   -> Color(0xFF4A90D0)
                            "Female" -> Color(0xFF8A50C0)
                            else     -> Color(0xFF50B09A)  // teal for non-binary
                        }
                        val rowBg = when (member.gender) {
                            "Male"   -> Color(0xFF0F2035)
                            "Female" -> Color(0xFF1A0F30)
                            else     -> Color(0xFF0F2520)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(rowBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Coloured gender dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotBg)
                            )
                            Text(
                                text  = member.alias,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color    = Color(0xFFD0DFF0),
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text  = gSym,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color    = dotBg,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            } else {
                // Members still loading — show skeleton
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0D1828))
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Begin button ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1A5A9A), Color(0xFF2A3A8A))
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF3A8ADA).copy(alpha = glowAlpha),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onBegin() }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                Text(
                    text  = "I'm ready  →",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color         = Color(0xFFE8F0FF),
                        letterSpacing = 0.5.sp
                    )
                )
            }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rooms Panel — bottom sheet showing all forming + completed rooms
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomsPanelSheet(
    state     : WaitingUiState.InQueue,
    onDismiss : () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF0A1020),
        scrimColor       = Color.Black.copy(alpha = 0.7f),
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2A3850))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Active Rooms",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color         = Color(0xFFF0F4F8),
                        letterSpacing = 1.sp
                    )
                )
                val total = state.completedRooms.size + state.pendingRooms.size
                if (total > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A2A4A))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "$total room${if (total == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = Color(0xFF6BA4E0),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // ── Forming rooms (in queue) ────────────────────────────────────
            if (state.pendingRooms.isNotEmpty()) {
                Text(
                    text  = "FORMING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color         = Color(0xFF4A6A9A),
                        fontSize      = 10.sp,
                        letterSpacing = 2.sp
                    )
                )
                state.pendingRooms.forEach { room ->
                    val isMyRoom = room.roomNumber == state.myBatchNumber
                    RoomCard(
                        roomNumber = room.roomNumber,
                        males      = room.males,
                        females    = room.females,
                        isMyRoom   = isMyRoom,
                        isComplete = false
                    )
                }
            }

            // ── Completed circles ───────────────────────────────────────────
            if (state.completedRooms.isNotEmpty()) {
                Text(
                    text  = "ACTIVE CIRCLES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color         = Color(0xFF4A8A6A),
                        fontSize      = 10.sp,
                        letterSpacing = 2.sp
                    )
                )
                state.completedRooms.forEach { room ->
                    CompletedRoomCard(room)
                }
            }

            if (state.pendingRooms.isEmpty() && state.completedRooms.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No rooms yet — you're the first one here 🌙",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color     = Color(0xFF4A5A7A),
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomCard(
    roomNumber : Int,
    males      : List<MemberPreview>,
    females    : List<MemberPreview>,
    isMyRoom   : Boolean,
    isComplete : Boolean
) {
    val borderColor = when {
        isMyRoom   -> Color(0xFF3A6AAA)
        isComplete -> Color(0xFF3A7A5A)
        else       -> Color(0xFF1E2E44)
    }
    val bgColor = when {
        isMyRoom   -> Color(0xFF0F1E35)
        isComplete -> Color(0xFF0F2520)
        else       -> Color(0xFF0D1525)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Room header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Room $roomNumber",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color    = if (isMyRoom) Color(0xFF8BB8FF) else Color(0xFF5A7AAA),
                        fontSize = 13.sp
                    )
                )
                if (isMyRoom) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A3A6A))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "YOUR ROOM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = Color(0xFF6BA4E0),
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }
            // Fill indicator: ♂♂♀♀
            val total = males.size + females.size
            Text(
                text  = "$total/4",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = if (total == 4) Color(0xFF5ABF8A) else Color(0xFF4A6A8A),
                    fontSize = 12.sp
                )
            )
        }

        // Members
        val allMembers = males + females
        if (allMembers.isNotEmpty()) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allMembers.forEach { m ->
                    val gSym  = when (m.gender) { "Male" -> "♂" ; "Female" -> "♀" ; else -> "⚧" }
                    val chipBg = when (m.gender) {
                        "Male"   -> Color(0xFF132030)
                        "Female" -> Color(0xFF1E1030)
                        else     -> Color(0xFF102520)
                    }
                    val chipFg = when (m.gender) {
                        "Male"   -> Color(0xFF5A90D0)
                        "Female" -> Color(0xFF9A60C0)
                        else     -> Color(0xFF4AB09A)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(chipBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = gSym,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = chipFg,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text  = m.alias,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = Color(0xFFB0C4DE),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                // Empty slots
                repeat(4 - allMembers.size) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D1525))
                            .border(0.8.dp, Color(0xFF1E2E44), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "·  ·  ·",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = Color(0xFF2A3A55),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedRoomCard(room: CompletedRoom) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D2018))
            .border(1.dp, Color(0xFF2A5A3A), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "🌕 Circle ${room.roomNumber}",
                style = MaterialTheme.typography.labelMedium.copy(
                    color    = Color(0xFF5ABF8A),
                    fontSize = 13.sp
                )
            )
            Text(
                "4 members",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = Color(0xFF3A7A5A),
                    fontSize = 11.sp
                )
            )
        }
        Text(
            room.members.joinToString(", "),
            style = MaterialTheme.typography.labelSmall.copy(
                color    = Color(0xFF4A7A6A),
                fontSize = 10.sp
            ),
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Veil Onboarding Overlay — shown once after "Begin the Journey" is tapped
//  Explains the 3-day arc before the user lands on Island Home
// ─────────────────────────────────────────────────────────────────────────────

private data class OnboardSlide(val emoji: String, val title: String, val body: String)

private val ONBOARD_SLIDES = listOf(
    OnboardSlide(
        "🌙",
        "Tonight, 3 strangers\nare waiting for you.",
        "Somewhere out there right now, they're just as nervous.\nJust as curious. Just as real."
    ),
    OnboardSlide(
        "✦",
        "You don't need to\nimpress anyone here.",
        "No profile photos. No follower count.\nJust how you think, and how you make people feel."
    ),
    OnboardSlide(
        "🌿",
        "Take your time.\nThere is no rush.",
        "These conversations have no expiry date.\nSay what you actually mean, not what sounds good."
    ),
    OnboardSlide(
        "🔮",
        "You'll feel something\nyou didn't expect.",
        "Most people come for curiosity.\nThey stay because something surprised them.\nThat's Veil."
    ),
    OnboardSlide(
        "🌸",
        "Your circle is ready.",
        "This is the beginning of something\nyou can't quite name yet.\nTrust it."
    ),
)

@Composable
fun VeilOnboardingOverlay(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val slide = ONBOARD_SLIDES[page]
    val isLast = page == ONBOARD_SLIDES.lastIndex

    val inf = rememberInfiniteTransition(label = "obGlow")
    val glow by inf.animateFloat(
        initialValue  = 0.45f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "g"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF060B18), Color(0xFF0D1430), Color(0xFF060B18)))
            )
            .clickable {
                if (page < ONBOARD_SLIDES.lastIndex) page++ else onDone()
            },
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF3A5FDE).copy(alpha = 0.12f * glow), Color.Transparent))
                )
        )

        AnimatedContent(
            targetState   = page,
            transitionSpec = {
                (fadeIn(tween(400)) + slideInHorizontally(tween(400)) { it / 3 }) togetherWith
                        (fadeOut(tween(280)) + slideOutHorizontally(tween(280)) { -it / 3 })
            },
            label = "obSlide"
        ) { idx ->
            val s = ONBOARD_SLIDES[idx]
            Column(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Emoji orb
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.2.dp, Color(0xFF3A70DE).copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s.emoji, fontSize = 44.sp)
                }

                Text(
                    s.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color      = Color(0xFFF0F4FF),
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center,
                        lineHeight = 30.sp
                    )
                )

                Text(
                    s.body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color      = Color(0xFF8A9AC0),
                        textAlign  = TextAlign.Center,
                        lineHeight = 22.sp,
                        fontSize   = 14.sp
                    )
                )
            }
        }

        // Bottom area — page dots + CTA button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ONBOARD_SLIDES.forEachIndexed { i, _ ->
                    val isActive = i == page
                    val dotWidth by animateDpAsState(
                        targetValue   = if (isActive) 22.dp else 6.dp,
                        animationSpec = tween(300),
                        label         = "dotW$i"
                    )
                    Box(
                        modifier = Modifier
                            .width(dotWidth)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) Color(0xFF5A8AFF) else Color.White.copy(alpha = 0.20f)
                            )
                    )
                }
            }

            // CTA button
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1A3A8A), Color(0xFF2A2A8A))
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF3A70DE).copy(alpha = glow),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        if (page < ONBOARD_SLIDES.lastIndex) page++ else onDone()
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isLast) "Step in  →" else "Next  →",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color      = Color(0xFFD8E8FF),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            // Skip button — visible from page 1 onward, jumps to last slide
            if (!isLast && page > 0) {
                Text(
                    "Skip →",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color    = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.clickable {
                        page = ONBOARD_SLIDES.lastIndex
                    }
                )
            } else if (!isLast) {
                Text(
                    "Swipe or tap to continue",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color    = Color.White.copy(alpha = 0.18f),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
