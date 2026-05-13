package com.ns.findyourintroverfriend.reveal

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.party.BondResult
import com.ns.findyourintroverfriend.ui.RevealSkeletonScreen
import kotlinx.coroutines.delay
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
//  Palette — WARM, BRIGHT, CELEBRATORY (not dark)
// ─────────────────────────────────────────────────────────────────────────────

private val RBg        = Color(0xFFFFF8F0)  // warm cream white
private val RBgDeep    = Color(0xFFFFF0DC)  // warm amber cream
private val RCard      = Color(0xFFFFFFFF)  // pure white card
private val RCardAlt   = Color(0xFFFFF5E8)  // soft peach card
private val RBorder    = Color(0xFFEDD9C0)  // warm sand border
private val RBorderA   = Color(0xFFF4A75A)  // amber border (active)
private val RGold      = Color(0xFFE8952E)  // warm gold — primary accent
private val RGoldLight = Color(0xFFF5C842)  // bright golden yellow
private val RTxtP      = Color(0xFF2A1A0A)  // rich warm dark brown (primary text)
private val RTxtS      = Color(0xFF8A6040)  // medium warm brown (secondary)
private val RTxtT      = Color(0xFFC4A278)  // light warm peach (tertiary)
private val RRose      = Color(0xFFE8647A)  // warm rose/red — heart colour
private val RRoseLight = Color(0xFFFFD6DD)  // light rose
private val RGreen     = Color(0xFF4A9E6A)  // sage green for "you"
private val RBlue      = Color(0xFF5B8BC9)  // soft calm blue
private val RDivider   = Color(0xFFEBD5BB)  // warm divider

// ─────────────────────────────────────────────────────────────────────────────
//  Reveal Ceremony Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RevealCeremonyScreen(
    vm             : RevealCeremonyViewModel = viewModel(),
    onGoHome       : () -> Unit = {},
    onFindNewRoom  : () -> Unit = {},
    onGoToParty    : (sessionId: String) -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Block back button during ceremony
    androidx.activity.compose.BackHandler(enabled = true) { /* intentionally consumed */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(RBg, RBgDeep, RBg)
                )
            )
    ) {
        // Soft warm glow blobs in background
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(RGold.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.10f),
                    radius = size.width * 0.55f
                ),
                center = Offset(size.width * 0.15f, size.height * 0.10f),
                radius = size.width * 0.55f
            )
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(RRose.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.85f),
                    radius = size.width * 0.55f
                ),
                center = Offset(size.width * 0.85f, size.height * 0.85f),
                radius = size.width * 0.55f
            )
        }

        AnimatedContent(
            targetState   = state,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
            label         = "revealState"
        ) { s ->
            when (s) {
                is RevealUiState.Loading   -> CerLoader()
                is RevealUiState.Computing -> CerComputing(s.members)
                is RevealUiState.Winner    -> WinnerScreen(
                    state         = s,
                    onEnterIsland = { sessionId ->
                        vm.markSelfEntered(sessionId)
                        onGoToParty(sessionId)
                    }
                )
                is RevealUiState.NoMatch   -> NoMatchScreen(
                    state        = s,
                    onGoHome     = onGoHome,
                    onFindNewRoom = onFindNewRoom
                )
                is RevealUiState.Error     -> CerError(s.msg)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Winner Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WinnerScreen(
    state         : RevealUiState.Winner,
    onEnterIsland : (String) -> Unit
) {
    val bond        = state.bondResult
    val memberCount = state.members.size
    var revealedCount by remember { mutableIntStateOf(0) }
    var showBond      by remember { mutableStateOf(false) }
    var showConfetti  by remember { mutableStateOf(false) }

    LaunchedEffect(memberCount) {
        delay(500L)
        repeat(memberCount) {
            revealedCount++
            delay(1_100L)
        }
        delay(500L)
        showBond = true
        delay(300L)
        showConfetti = true
    }

    Box(Modifier.fillMaxSize()) {
        // Confetti burst — only after reveal
        if (showConfetti) {
            ConfettiCanvas()
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding      = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { CeremonyHeader(isWinner = true) }

            itemsIndexed(state.members) { index, member ->
                RevealMemberCard(member = member, revealed = index < revealedCount)
            }

            item {
                AnimatedVisibility(
                    visible = showBond,
                    enter   = fadeIn(tween(800)) + slideInVertically(tween(700)) { it / 2 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier            = Modifier.fillMaxWidth()
                    ) {
                        // ── Bond reveal card ──────────────────────────────────────
                        BondRevealCard(bond = bond)

                        // ── Compatibility score card ───────────────────────────────
                        CompatibilityCard(bond = bond)

                        // ── Enter island / waiting ─────────────────────────────────
                        if (state.partnerHasJoined) {
                            EnterIslandButton(
                                sessionId     = bond.sessionId,
                                partnerName   = if (bond.winnerA.isYou) bond.winnerB.name else bond.winnerA.name,
                                onEnterIsland = onEnterIsland
                            )
                        } else {
                            WaitingForPartnerBanner(
                                partnerName   = if (bond.winnerA.isYou) bond.winnerB.name else bond.winnerA.name,
                                sessionId     = bond.sessionId,
                                onEnterIsland = onEnterIsland
                            )
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  No-Match Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoMatchScreen(
    state        : RevealUiState.NoMatch,
    onGoHome     : () -> Unit,
    onFindNewRoom : () -> Unit
) {
    val memberCount   = state.members.size
    var revealedCount by remember { mutableIntStateOf(0) }
    var showResult    by remember { mutableStateOf(false) }

    LaunchedEffect(memberCount) {
        delay(500L)
        repeat(memberCount) { revealedCount++; delay(1_100L) }
        delay(500L)
        showResult = true
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding      = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { CeremonyHeader(isWinner = false) }

        itemsIndexed(state.members) { index, member ->
            RevealMemberCard(member = member, revealed = index < revealedCount)
        }

        item {
            AnimatedVisibility(
                visible = showResult,
                enter   = fadeIn(tween(800)) + slideInVertically(tween(700)) { it / 2 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier            = Modifier.fillMaxWidth()
                ) {
                    WinnersAnnouncement(bond = state.bondResult)
                    NoBondCard(onGoHome = onGoHome, onFindNewRoom = onFindNewRoom)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bond Reveal Card — pulsing heart with glowing aura
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BondRevealCard(bond: BondResult) {
    val inf = rememberInfiniteTransition(label = "bondGlow")
    val glow by inf.animateFloat(
        initialValue  = 0.65f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "bg"
    )
    val heartScale by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "hs"
    )
    val heartAnim by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(1_200, easing = FastOutSlowInEasing),
        label         = "ha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFF3E8), Color(0xFFFFE8D6))
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(RBorderA.copy(glow * 0.5f), RRose.copy(glow * 0.4f), RBorderA.copy(glow * 0.5f))),
                RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Label
            Text(
                "✦  A Bond Was Formed  ✦",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily    = FontFamily.Serif,
                    color         = RGold.copy(alpha = glow * 0.85f),
                    fontSize      = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight    = FontWeight.SemiBold
                )
            )

            // Heart animation canvas
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    // Outer glow rings
                    for (ring in 1..3) {
                        drawCircle(
                            color  = RRose.copy(alpha = heartAnim * 0.06f * glow / ring),
                            radius = (50.dp.toPx() + ring * 18.dp.toPx()) * heartAnim,
                            center = Offset(cx, cy)
                        )
                    }

                    // Heart shape
                    val hs = 38.dp.toPx() * heartScale * heartAnim
                    if (hs > 0.01f) {
                        val heartPath = Path().apply {
                            moveTo(cx, cy + hs * 0.75f)
                            cubicTo(cx - hs * 2f, cy - hs * 0.9f, cx - hs * 2f, cy - hs * 1.8f, cx, cy - hs * 0.45f)
                            cubicTo(cx + hs * 2f, cy - hs * 1.8f, cx + hs * 2f, cy - hs * 0.9f, cx, cy + hs * 0.75f)
                            close()
                        }
                        // Filled heart
                        drawPath(
                            path  = heartPath,
                            brush = Brush.radialGradient(
                                listOf(RRose.copy(alpha = 0.90f * glow * heartAnim), RGold.copy(alpha = 0.70f * glow * heartAnim)),
                                center = Offset(cx, cy),
                                radius = hs * 2.5f
                            )
                        )
                        // Heart stroke
                        drawPath(
                            path  = heartPath,
                            color = RRose.copy(alpha = 0.35f * heartAnim),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Sparkle dots around heart
                    val angleStep = (2 * PI / 8).toFloat()
                    for (i in 0 until 8) {
                        val angle  = i * angleStep
                        val radius = (52 + 8 * sin(i.toFloat())).dp.toPx() * heartAnim
                        val sx     = cx + cos(angle) * radius
                        val sy     = cy + sin(angle) * radius
                        drawCircle(
                            color  = if (i % 2 == 0) RGold.copy(alpha = 0.55f * glow * heartAnim)
                            else   RRose.copy(alpha = 0.40f * glow * heartAnim),
                            radius = (if (i % 2 == 0) 3.5f else 2.5f).dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }

                // Person emojis floating on sides
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    PersonFloatingChip(bond.winnerA.emoji, bond.winnerA.name, bond.winnerA.isYou, glow)
                    Spacer(Modifier.width(80.dp))
                    PersonFloatingChip(bond.winnerB.emoji, bond.winnerB.name, bond.winnerB.isYou, glow)
                }
            }

            // Warm tagline
            Text(
                "Three days of honesty built something beautiful.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle  = FontStyle.Italic,
                    color      = RTxtS.copy(alpha = 0.70f),
                    textAlign  = TextAlign.Center,
                    fontSize   = 13.sp,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

@Composable
private fun PersonFloatingChip(emoji: String, name: String, isYou: Boolean, glow: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isYou) RGreen.copy(0.12f) else RRose.copy(0.10f))
                .border(
                    1.5.dp,
                    if (isYou) RGreen.copy(0.45f * glow) else RRose.copy(0.40f * glow),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 22.sp) }
        Text(
            if (isYou) "You" else name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Serif,
                color      = if (isYou) RGreen else RTxtS,
                fontSize   = 10.sp,
                fontWeight = if (isYou) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Compatibility Score Card  ← NEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompatibilityCard(bond: BondResult) {
    // bondScore max ~30 pts → map to 0–100 %
    val rawPct    = (bond.bondScore / 30f).coerceIn(0f, 1f)
    val pct       = (rawPct * 100).toInt().coerceAtLeast(60)   // floor at 60% so it always feels good
    val animPct   by animateFloatAsState(
        targetValue   = rawPct.coerceAtLeast(0.60f),
        animationSpec = tween(1_400, delayMillis = 300, easing = FastOutSlowInEasing),
        label         = "compat"
    )

    val (compatLabel, compatMsg, compatColour) = when {
        pct >= 90 -> Triple("Soulmate Energy",  "You two clicked on almost everything. That's rare.", RRose)
        pct >= 75 -> Triple("Deep Connection",  "A strong bond of honesty and curiosity between you two.", RGold)
        pct >= 60 -> Triple("Genuine Match",    "You built real rapport through three days of openness.", RGreen)
        else      -> Triple("Heartfelt Match",  "Your bond is quiet, but it's real — and that matters.", RBlue)
    }

    // match-point descriptors (derived from score tiers — illustrative summaries)
    val things = buildList {
        if (bond.bondScore >= 5)  add("questions you answered for each other")
        if (bond.bondScore >= 10) add("moments you reacted to")
        if (bond.bondScore >= 15) add("times you echoed each other's feelings")
        if (bond.bondScore >= 20) add("layers of trust you showed up for")
        if (isEmpty())            add("honest conversations across 3 days")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RCard)
            .border(1.dp, RDivider, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Compatibility Score",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color         = RTxtT,
                            fontSize      = 9.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight    = FontWeight.SemiBold
                        )
                    )
                    Text(
                        compatLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color      = compatColour,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 16.sp
                        )
                    )
                }
                // Big score circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(compatColour.copy(0.10f))
                        .border(2.dp, compatColour.copy(0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$pct",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color      = compatColour,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 20.sp
                            )
                        )
                        Text(
                            "%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = compatColour.copy(0.60f),
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(RDivider)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animPct)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(compatColour.copy(0.7f), compatColour)
                            )
                        )
                )
            }

            HorizontalDivider(color = RDivider, thickness = 0.5.dp)

            // Things you matched on
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "You matched on",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color         = RTxtT,
                        fontSize      = 9.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight    = FontWeight.SemiBold
                    )
                )
                things.forEach { thing ->
                    Row(
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "✦",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RGold, fontSize = 10.sp
                            )
                        )
                        Text(
                            thing,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color      = RTxtS,
                                fontSize   = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }
                }
            }

            // Bottom message
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(compatColour.copy(0.06f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    compatMsg,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle  = FontStyle.Italic,
                        color      = compatColour.copy(0.75f),
                        textAlign  = TextAlign.Center,
                        fontSize   = 12.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Waiting for Partner Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WaitingForPartnerBanner(
    partnerName   : String,
    sessionId     : String,
    onEnterIsland : (String) -> Unit
) {
    val inf  = rememberInfiniteTransition(label = "waitPulse")
    val pulse by inf.animateFloat(
        initialValue  = 0.55f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "wp"
    )

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Waiting chip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RCardAlt)
                .border(1.dp, Brush.horizontalGradient(listOf(RBorderA.copy(pulse * 0.5f), RRose.copy(pulse * 0.35f), RBorderA.copy(pulse * 0.5f))), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(RRose.copy(alpha = pulse * 0.85f))
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Waiting for $partnerName…",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Serif,
                            color      = RTxtP.copy(0.80f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp
                        )
                    )
                    Text(
                        "Your bond is sealed. They'll arrive any moment.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle  = FontStyle.Italic,
                            color      = RTxtT,
                            fontSize   = 10.sp
                        )
                    )
                }
            }
        }

        // Enter anyway
        EnterIslandButton(
            sessionId     = sessionId,
            partnerName   = partnerName,
            onEnterIsland = onEnterIsland,
            dimmed        = true
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Enter Island Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EnterIslandButton(
    sessionId     : String,
    partnerName   : String,
    onEnterIsland : (String) -> Unit,
    dimmed        : Boolean = false
) {
    val inf  = rememberInfiniteTransition(label = "enterGlow")
    val glow by inf.animateFloat(
        initialValue  = 0.65f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "eg"
    )

    if (!dimmed) {
        // Partner joined chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(RGreen.copy(0.12f))
                .border(0.8.dp, RGreen.copy(0.35f * glow), RoundedCornerShape(50.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                "✓  $partnerName is here!",
                style = MaterialTheme.typography.labelSmall.copy(
                    color         = RGreen.copy(glow * 0.90f),
                    fontSize      = 11.sp,
                    fontFamily    = FontFamily.Serif,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            )
        }
        Spacer(Modifier.height(4.dp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (dimmed)
                    Brush.horizontalGradient(listOf(RCardAlt, RCardAlt))
                else
                    Brush.horizontalGradient(
                        listOf(RGold.copy(0.18f * glow), RRose.copy(0.12f * glow), RGold.copy(0.18f * glow))
                    )
            )
            .border(
                if (dimmed) 0.8.dp else 1.5.dp,
                if (dimmed)
                    Brush.horizontalGradient(listOf(RBorderA.copy(0.3f), RBorderA.copy(0.3f)))
                else
                    Brush.horizontalGradient(listOf(RGold.copy(0.70f * glow), RRose.copy(0.50f * glow), RGold.copy(0.70f * glow))),
                RoundedCornerShape(18.dp)
            )
            .clickable { onEnterIsland(sessionId) }
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (dimmed) "enter now" else "your private island awaits",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily    = FontFamily.Serif,
                        color         = if (dimmed) RTxtT else RGold.copy(0.65f),
                        letterSpacing = 1.5.sp,
                        fontSize      = 9.sp
                    )
                )
                Text(
                    "Enter the Island",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        color      = if (dimmed) RTxtS else RTxtP,
                        fontWeight = if (dimmed) FontWeight.Light else FontWeight.SemiBold,
                        fontSize   = 22.sp
                    )
                )
                Text(
                    if (dimmed) "$partnerName will follow when ready"
                    else        "Chat · Discover · Connect",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle  = FontStyle.Italic,
                        color      = if (dimmed) RTxtT else RTxtS.copy(0.55f),
                        fontSize   = 11.sp
                    )
                )
            }
            Text(
                "→",
                style = MaterialTheme.typography.titleLarge.copy(
                    color    = if (dimmed) RTxtT else RGold.copy(0.65f * glow),
                    fontSize = 24.sp
                )
            )
        }
    }

    if (!dimmed) {
        Text(
            "Your shared story begins here.",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Serif,
                fontStyle  = FontStyle.Italic,
                color      = RTxtT.copy(0.55f),
                fontSize   = 10.sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Winners Announcement (shown to non-winners)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WinnersAnnouncement(bond: BondResult) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RCard)
            .border(1.dp, RDivider, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "✦  A Bond Formed in Your Circle  ✦",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily    = FontFamily.Serif,
                    color         = RGold,
                    fontSize      = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight    = FontWeight.SemiBold
                )
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                PersonFloatingChip(bond.winnerA.emoji, bond.winnerA.name, bond.winnerA.isYou, 0.85f)
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("♡", fontSize = 20.sp, color = RRose)
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(RDivider))
                }
                PersonFloatingChip(bond.winnerB.emoji, bond.winnerB.name, bond.winnerB.isYou, 0.85f)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  No Bond Card — warm, uplifting
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoBondCard(
    onGoHome      : () -> Unit,
    onFindNewRoom : () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Warm message card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RCard)
                .border(1.dp, RDivider, RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🌱", fontSize = 36.sp)
                Text(
                    "Every connection has its own timing.",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Serif,
                        color      = RTxtP.copy(0.80f),
                        fontWeight = FontWeight.Light,
                        fontSize   = 18.sp,
                        textAlign  = TextAlign.Center,
                        lineHeight = 26.sp
                    )
                )
                Text(
                    "This circle gave you 3 days of honest conversation and real human moments. That's never wasted.\nYour next circle might be the one.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle  = FontStyle.Italic,
                        color      = RTxtS.copy(0.55f),
                        textAlign  = TextAlign.Center,
                        fontSize   = 12.sp,
                        lineHeight = 19.sp
                    )
                )
            }
        }

        // CTA: Find New Room
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(RGold.copy(0.12f), RRose.copy(0.08f))))
                .border(1.dp, RBorderA.copy(0.38f), RoundedCornerShape(14.dp))
                .clickable { onFindNewRoom() }
                .padding(vertical = 17.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "Join a New Circle",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily    = FontFamily.Serif,
                        color         = RTxtP.copy(0.85f),
                        fontWeight    = FontWeight.SemiBold,
                        fontSize      = 16.sp,
                        letterSpacing = 0.3.sp
                    )
                )
                Text(
                    "A fresh start · new faces · new possibilities",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle  = FontStyle.Italic,
                        color      = RTxtS.copy(0.50f),
                        fontSize   = 10.sp
                    )
                )
            }
        }

        // CTA: Go Home
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Transparent)
                .border(0.7.dp, RDivider, RoundedCornerShape(14.dp))
                .clickable { onGoHome() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Return Home",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily    = FontFamily.Serif,
                    color         = RTxtT.copy(0.70f),
                    fontWeight    = FontWeight.Light,
                    fontSize      = 13.sp,
                    letterSpacing = 0.3.sp
                )
            )
        }

        Text(
            "Your journey here is never lost.",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Serif,
                fontStyle  = FontStyle.Italic,
                color      = RTxtT.copy(0.35f),
                fontSize   = 10.sp,
                textAlign  = TextAlign.Center
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ceremony Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CeremonyHeader(isWinner: Boolean) {
    val inf  = rememberInfiniteTransition(label = "hdr")
    val glow by inf.animateFloat(
        initialValue  = 0.50f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "g"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier            = Modifier.padding(top = 8.dp, bottom = 8.dp)
    ) {
        // Animated star/sparkle
        val starScale by animateFloatAsState(
            targetValue   = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label         = "ss"
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(starScale * (0.85f + 0.15f * glow))
                .clip(CircleShape)
                .border(1.5.dp, RGold.copy(0.45f * glow), CircleShape)
        ) {
            androidx.compose.foundation.Image(
                painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_ceremony),
                contentDescription = null,
                modifier     = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        Text(
            "The Circle Revealed",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily    = FontFamily.Serif,
                fontWeight    = FontWeight.SemiBold,
                color         = RTxtP.copy(0.90f),
                fontSize      = 28.sp,
                letterSpacing = 0.3.sp
            )
        )
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, RGold.copy(0.60f * glow), Color.Transparent)
                    )
                )
        )
        Text(
            if (isWinner)
                "Your 3-day journey is complete.\nThe faces behind the emojis — finally here."
            else
                "Three days of real connection.\nTime to see who you shared it with.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif,
                fontStyle  = FontStyle.Italic,
                color      = RTxtS.copy(0.55f),
                textAlign  = TextAlign.Center,
                lineHeight = 20.sp,
                fontSize   = 12.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Member Reveal Card — warm bounce-in
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RevealMemberCard(member: RevealMember, revealed: Boolean) {
    val cardScale by animateFloatAsState(
        targetValue   = if (revealed) 1f else 0.93f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "cs"
    )
    val cardAlpha by animateFloatAsState(
        targetValue   = if (revealed) 1f else 0.35f,
        animationSpec = tween(550),
        label         = "ca"
    )
    var displayName by remember { mutableStateOf("") }
    LaunchedEffect(revealed) {
        if (revealed) {
            displayName = ""
            member.name.forEach { char ->
                delay(48L)
                displayName += char
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (revealed) RCard else Color(0xFFF5EDE0))
            .border(
                width = if (revealed) 1.dp else 0.5.dp,
                color = if (revealed) RBorderA.copy(0.35f) else RDivider.copy(0.50f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        // Top reveal shimmer
        if (revealed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, RGold.copy(0.65f), RRose.copy(0.35f), Color.Transparent)
                        )
                    )
            )
        }

        Row(
            modifier              = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (revealed) RCardAlt else Color(0xFFEFE2D0))
                    .border(
                        width = if (revealed) 1.5.dp else 0.7.dp,
                        color = if (revealed) RBorderA.copy(0.45f) else RDivider.copy(0.40f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (revealed && member.photoUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model              = member.photoUrl,
                        contentDescription = member.name,
                        contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text     = if (revealed) member.emoji else "?",
                        fontSize = if (revealed) 26.sp else 20.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (revealed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text  = displayName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily    = FontFamily.Serif,
                                color         = RTxtP.copy(0.90f),
                                fontWeight    = FontWeight.SemiBold,
                                fontSize      = 20.sp,
                                letterSpacing = 0.2.sp
                            )
                        )
                        if (displayName.length < member.name.length) {
                            val blink by rememberInfiniteTransition(label = "cur").animateFloat(
                                initialValue  = 0f, targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
                                label         = "b"
                            )
                            Text("|", style = MaterialTheme.typography.titleLarge.copy(
                                color = RGold.copy(alpha = blink), fontSize = 18.sp
                            ))
                        }
                    }
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            member.gender,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Serif,
                                color      = RTxtS.copy(0.50f), fontSize = 11.sp
                            )
                        )
                        if (member.isYou) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(RGreen.copy(0.12f))
                                    .border(0.5.dp, RGreen.copy(0.30f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("you", style = MaterialTheme.typography.labelSmall.copy(
                                    color = RGreen.copy(0.80f), fontSize = 9.sp, letterSpacing = 1.sp
                                ))
                            }
                        }
                    }
                    Text(
                        "3 days of mystery together",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle  = FontStyle.Italic,
                            color      = RTxtT.copy(0.55f), fontSize = 10.sp
                        )
                    )
                } else {
                    Text(
                        "Waiting to reveal…",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle  = FontStyle.Italic,
                            color      = RTxtT.copy(0.40f)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .width(90.dp).height(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(RDivider)
                    )
                }
            }

            if (revealed) {
                Box(
                    modifier = Modifier
                        .width(2.dp).height(44.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, RGold.copy(0.40f), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Confetti Canvas — celebratory burst
// ─────────────────────────────────────────────────────────────────────────────

private data class Confetto(
    val x      : Float,
    val speed  : Float,
    val phase  : Float,
    val size   : Float,
    val color  : Color,
    val drift  : Float,
    val rotate : Float
)

@Composable
private fun ConfettiCanvas() {
    val confettiColors = listOf(RGold, RGoldLight, RRose, RRoseLight, RGreen.copy(0.7f), RBlue.copy(0.6f), Color(0xFFFFB347))
    val pieces = remember {
        (0 until 55).map {
            Confetto(
                x      = kotlin.random.Random.nextFloat(),
                speed  = kotlin.random.Random.nextFloat() * 0.12f + 0.05f,
                phase  = kotlin.random.Random.nextFloat(),
                size   = kotlin.random.Random.nextFloat() * 5f + 3f,
                color  = confettiColors[it % confettiColors.size],
                drift  = (kotlin.random.Random.nextFloat() - 0.5f) * 0.06f,
                rotate = kotlin.random.Random.nextFloat()
            )
        }
    }
    val inf  = rememberInfiniteTransition(label = "confetti")
    val tick by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
        label         = "ct"
    )
    Canvas(Modifier.fillMaxSize()) {
        pieces.forEach { p ->
            val t   = (tick + p.phase) % 1f
            val y   = size.height * t
            val ox  = sin(t * 6 * Math.PI.toFloat() + p.phase * 4f) * 18.dp.toPx() * p.drift / 0.03f
            val x   = p.x * size.width + ox
            val alpha = if (t < 0.1f) t / 0.1f else if (t > 0.85f) (1f - t) / 0.15f else 1f
            // Alternate rectangles and circles
            if (p.rotate > 0.5f) {
                drawCircle(
                    color  = p.color.copy(alpha = alpha * 0.70f),
                    radius = p.size.dp.toPx() / 2,
                    center = Offset(x, y)
                )
            } else {
                val ro = t * 360 * (if (p.drift > 0) 1 else -1)
                drawRect(
                    color    = p.color.copy(alpha = alpha * 0.65f),
                    topLeft  = Offset(x - p.size.dp.toPx() / 2, y - p.size.dp.toPx() / 4),
                    size     = androidx.compose.ui.geometry.Size(p.size.dp.toPx(), p.size.dp.toPx() / 2)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Computing + Loading + Error helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CerComputing(members: List<RevealMember>) {
    val memberCount   = members.size
    var revealedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(memberCount) {
        delay(400L)
        repeat(memberCount) { revealedCount++; delay(1_100L) }
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding      = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { CeremonyHeader(isWinner = false) }
        itemsIndexed(members) { index, member ->
            RevealMemberCard(member = member, revealed = index < revealedCount)
        }
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(16.dp),
                    color       = RGold,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Unveiling your bond…",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle  = FontStyle.Italic,
                        color      = RTxtS.copy(0.60f), fontSize = 12.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun CerLoader() {
    Box(Modifier.fillMaxSize()) {
        RevealSkeletonScreen()
        Text(
            "Preparing the ceremony…",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif,
                fontStyle  = FontStyle.Italic,
                color      = RTxtS.copy(0.50f)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun CerError(msg: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            msg,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Serif,
                color      = RTxtS.copy(0.60f),
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )
        )
    }
}
