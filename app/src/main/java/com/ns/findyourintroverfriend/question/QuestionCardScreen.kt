package com.ns.findyourintroverfriend.question

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.home.MAX_QUESTIONS_PER_DAY
import com.ns.findyourintroverfriend.ui.theme.*
import com.ns.findyourintroverfriend.ui.QuestionSkeletonScreen
import com.ns.findyourintroverfriend.util.ContentModerator

// ─────────────────────────────────────────────────────────────────────────────
//  Ask the Circle — warm, joyful, peaceful playing area
// ─────────────────────────────────────────────────────────────────────────────

// ── Warm palette tokens (local to this screen) ────────────────────────────────
private val AskBg         = Color(0xFFFDF8F2)          // warm ivory
private val AskBgMid      = Color(0xFFFFF4E8)          // soft peach-cream
private val AskBgDeep     = Color(0xFFFEEDD8)          // gentle amber blush
private val AskAccent     = Color(0xFFE8832A)          // warm amber-orange
private val AskAccentSoft = Color(0xFFF5A654)          // light amber
private val AskAccentGlow = Color(0xFFFFB96B)          // golden glow
private val AskSelected   = Color(0xFFFF7B6B)          // warm coral for selection
private val AskText       = Color(0xFF2D1F0E)          // deep warm brown
private val AskTextSub    = Color(0xFF7A5C3A)          // medium warm brown
private val AskTextLight  = Color(0xFFB08A60)          // light warm tan
private val AskCard       = Color(0xFFFFFFFF)          // pure white card
private val AskCardBorder = Color(0xFFEDD9C0)          // warm beige border
private val AskDivider    = Color(0xFFF0DFC8)          // delicate divider

@Composable
fun QuestionCardScreen(
    vm          : QuestionCardViewModel = viewModel(),
    onBack      : () -> Unit,
    onSubmitted : () -> Unit = {}
) {
    val state          by vm.state.collectAsStateWithLifecycle()
    val moderationWarn by vm.moderationWarning.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is QuestionUiState.DoneForToday) {
            kotlinx.coroutines.delay(2_200)
            onSubmitted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(AskBg, AskBgMid, AskBgDeep)
                )
            )
    ) {
        // Soft warm glow — top right
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(80.dp, (-80).dp)
                .background(
                    Brush.radialGradient(
                        listOf(AskAccentGlow.copy(0.18f), Color.Transparent)
                    )
                )
        )
        // Soft warm glow — bottom left
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomStart)
                .offset((-60).dp, 80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(AskAccentSoft.copy(0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // ── Moderation warning banner ─────────────────────────────────────────
            AnimatedVisibility(
                visible = moderationWarn != null,
                enter   = slideInVertically { -it } + fadeIn(),
                exit    = slideOutVertically { -it } + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFB91C1C).copy(0.92f), Color(0xFFDC2626).copy(0.92f))
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        text  = moderationWarn ?: "",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = Color.White,
                            fontSize   = 12.sp,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            // ── Top bar ───────────────────────────────────────────────────────
            val slotsUsed = when (val s = state) {
                is QuestionUiState.Picking      -> MAX_QUESTIONS_PER_DAY - s.slotsRemaining
                is QuestionUiState.DoneForToday -> MAX_QUESTIONS_PER_DAY
                else                            -> 0
            }
            AskTopBar(onBack = onBack, slotsUsed = slotsUsed)

            // ── Body ──────────────────────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState   = state,
                    transitionSpec = {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                    },
                    label = "qState"
                ) { s ->
                    when (s) {
                        is QuestionUiState.Loading      -> LoadingView()
                        is QuestionUiState.Picking      -> PickingView(
                            suggestions    = s.suggestions,
                            slotsRemaining = s.slotsRemaining,
                            onSubmit       = vm::submitQuestion
                        )
                        is QuestionUiState.Submitting   -> SubmittingView()
                        is QuestionUiState.Submitted    -> SubmittedView()
                        is QuestionUiState.DoneForToday -> DoneForTodayView(
                            questions = s.questions,
                            onBack    = onBack
                        )
                        is QuestionUiState.Error        -> ErrorView(
                            msg    = s.msg,
                            onBack = onBack
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AskTopBar(onBack: () -> Unit, slotsUsed: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(AskCard)
                .border(1.dp, AskCardBorder, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint               = AskText,
                modifier           = Modifier.size(18.dp)
            )
        }

        // Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(0.8.dp, AskAccentSoft.copy(0.4f), CircleShape)
            ) {
                androidx.compose.foundation.Image(
                    painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_ask_circle),
                    contentDescription = null,
                    modifier     = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Text(
                text  = "Ask the Circle",
                style = MaterialTheme.typography.titleSmall.copy(
                    color         = AskText,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 17.sp,
                    letterSpacing = 0.2.sp
                )
            )
            Text(
                text  = "Your question · their truth",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = AskTextLight,
                    fontSize = 10.sp
                )
            )
        }

        // Slot counter
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (slotsUsed < 3)
                        AskAccent.copy(0.12f)
                    else
                        Color(0xFFF0DFC8)
                )
                .border(
                    1.dp,
                    if (slotsUsed < 3) AskAccent.copy(0.40f) else AskCardBorder,
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text  = "$slotsUsed / 3",
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = if (slotsUsed < 3) AskAccent else AskTextLight,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 11.sp
                )
            )
        }
    }
    // Warm gradient divider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, AskDivider, AskAccentSoft.copy(0.50f), AskDivider, Color.Transparent)
                )
            )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Picking view — 3 suggestion cards + custom input
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PickingView(
    suggestions    : List<String>,
    slotsRemaining : Int,
    onSubmit       : (String) -> Unit
) {
    var selected      by remember { mutableStateOf<String?>(null) }
    var customText    by remember { mutableStateOf("") }
    var useCustom     by remember { mutableStateOf(false) }
    var customFocused by remember { mutableStateOf(false) }

    val finalQuestion = if (useCustom) customText.trim() else selected ?: ""
    val canSubmit     = finalQuestion.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // ── Header ─────────────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Warm icon orb
            val inf = rememberInfiniteTransition(label = "orb")
            val pulse by inf.animateFloat(
                initialValue  = 0.92f, targetValue = 1.04f,
                animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label         = "orbPulse"
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .border(1.2.dp, AskAccentSoft.copy(0.50f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_ask_circle),
                    contentDescription = null,
                    modifier     = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Text(
                text  = "Ask the Circle",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color      = AskText,
                    textAlign  = TextAlign.Center,
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
            )
            Text(
                text  = "$slotsRemaining question${if (slotsRemaining == 1) "" else "s"} left today  ·  They'll answer it just for you.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = AskTextSub,
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp,
                    fontSize   = 12.sp
                )
            )
        }

        Spacer(Modifier.height(2.dp))

        // ── Suggestion cards ─────────────────────────────────────────────────
        suggestions.forEachIndexed { i, q ->
            AskSuggestionCard(
                question   = q,
                isSelected = !useCustom && selected == q,
                index      = i,
                onClick    = {
                    useCustom = false
                    selected  = q
                }
            )
        }

        // ── OR divider ────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.weight(1f).height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, AskDivider)))
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AskCard)
                    .border(1.dp, AskCardBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    "or write your own",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AskTextLight, fontSize = 10.sp
                    )
                )
            }
            Box(
                Modifier.weight(1f).height(1.dp)
                    .background(Brush.horizontalGradient(listOf(AskDivider, Color.Transparent)))
            )
        }

        // ── Custom question input ─────────────────────────────────────────────
        val liveCheck   = if (useCustom && customText.length > 4) ContentModerator.check(customText) else null
        val liveBlocked = liveCheck?.isSafe == false

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AskCard)
                .border(
                    width = if (liveBlocked || (useCustom && customFocused)) 1.5.dp else 1.dp,
                    brush = when {
                        liveBlocked              -> Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFEF4444)))
                        useCustom && customFocused -> Brush.horizontalGradient(listOf(AskAccent, AskAccentSoft))
                        else                     -> Brush.horizontalGradient(listOf(AskCardBorder, AskCardBorder))
                    },
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint               = when {
                        liveBlocked -> Color(0xFFDC2626)
                        useCustom   -> AskAccent
                        else        -> AskTextLight
                    },
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                BasicTextField(
                    value         = customText,
                    onValueChange = {
                        customText = it
                        useCustom  = it.isNotBlank()
                        if (it.isNotBlank()) selected = null
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color      = if (liveBlocked) Color(0xFFB91C1C) else AskText,
                        fontSize   = 15.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(AskAccent),
                    modifier    = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { customFocused = it.isFocused },
                    decorationBox = { inner ->
                        if (customText.isEmpty()) {
                            Text(
                                "Type your own question…",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color    = AskTextLight.copy(0.70f),
                                    fontSize = 15.sp
                                )
                            )
                        }
                        inner()
                    }
                )
            }
        }

        if (liveBlocked) {
            Text(
                text  = "⚠️ ${liveCheck?.warningMessage ?: "This content is not allowed."}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = Color(0xFFDC2626).copy(0.85f),
                    fontSize   = 10.sp,
                    lineHeight = 14.sp
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Submit button ─────────────────────────────────────────────────────
        AskSubmitButton(
            enabled  = canSubmit && !liveBlocked,
            question = finalQuestion,
            onSubmit = onSubmit
        )

        // Preview of selected
        if (finalQuestion.isNotBlank()) {
            Text(
                text  = "\"$finalQuestion\"",
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = AskTextSub,
                    textAlign  = TextAlign.Center,
                    fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                    lineHeight = 18.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(36.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Suggestion card — warm, clean, with coral selection state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AskSuggestionCard(
    question   : String,
    isSelected : Boolean,
    index      : Int,
    onClick    : () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "scale_$index"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSelected)
                    Brush.horizontalGradient(listOf(Color(0xFFFFF0E8), Color(0xFFFFE8D8)))
                else
                    Brush.horizontalGradient(listOf(AskCard, AskCard))
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                brush = if (isSelected)
                    Brush.horizontalGradient(listOf(AskSelected.copy(0.80f), AskAccent.copy(0.60f)))
                else
                    Brush.horizontalGradient(listOf(AskCardBorder, AskCardBorder)),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 17.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Selection dot
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected)
                            Brush.radialGradient(listOf(AskSelected, AskAccent))
                        else
                            Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .border(
                        1.5.dp,
                        if (isSelected) AskSelected else AskCardBorder,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White)
                    )
                }
            }

            Text(
                text     = question,
                style    = MaterialTheme.typography.bodyMedium.copy(
                    color      = if (isSelected) AskText else AskTextSub,
                    lineHeight = 22.sp,
                    fontSize   = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Submit button — warm amber gradient
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AskSubmitButton(
    enabled  : Boolean,
    question : String,
    onSubmit : (String) -> Unit
) {
    val inf = rememberInfiniteTransition(label = "submitGlow")
    val glow by inf.animateFloat(
        initialValue  = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "sg"
    )
    val scale by animateFloatAsState(
        targetValue   = if (enabled) 1f else 0.97f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label         = "btnScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(
                        listOf(AskAccent, AskSelected, AskAccent)
                    )
                else
                    Brush.horizontalGradient(listOf(AskDivider, AskDivider))
            )
            .border(
                width = if (enabled) 1.dp else 0.dp,
                brush = if (enabled)
                    Brush.horizontalGradient(
                        listOf(AskAccentGlow.copy(glow * 0.6f), AskSelected.copy(glow * 0.4f))
                    )
                else
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled) { onSubmit(question) }
            .alpha(if (enabled) 1f else 0.55f),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("✦", fontSize = 15.sp, color = Color.White.copy(if (enabled) 0.90f else 0.40f))
            Text(
                text  = "Send to my circle",
                style = MaterialTheme.typography.labelLarge.copy(
                    color         = Color.White.copy(if (enabled) 1f else 0.45f),
                    fontSize      = 15.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Submitting — warm loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubmittingView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(color = AskAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
        Text(
            "Sending to your circle…",
            style = MaterialTheme.typography.bodyMedium.copy(color = AskTextSub)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Submitted — warm success
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubmittedView() {
    val inf = rememberInfiniteTransition(label = "success")
    val pulse by inf.animateFloat(
        initialValue  = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "sp"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier            = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(AskAccent.copy(0.18f), AskAccentGlow.copy(0.06f), Color.Transparent))
                )
                .border(1.5.dp, AskAccentSoft.copy(0.50f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✦", fontSize = 30.sp, color = AskAccent)
        }
        Text(
            text  = "Question sent!",
            style = MaterialTheme.typography.headlineSmall.copy(
                color      = AskText,
                textAlign  = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold
            )
        )
        Text(
            text  = "Your circle will reflect on it.\nAnswers arrive in their own time.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color      = AskTextSub,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Done for today
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DoneForTodayView(questions: List<String>, onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier            = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text("✦", style = MaterialTheme.typography.displaySmall.copy(color = AskAccent))
        Text(
            text  = "All 3 questions sent!",
            style = MaterialTheme.typography.headlineSmall.copy(
                color      = AskText,
                textAlign  = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold
            )
        )
        Text(
            text  = "Your circle has a lot to think about today.",
            style = MaterialTheme.typography.bodySmall.copy(
                color     = AskTextSub,
                textAlign = TextAlign.Center
            )
        )
        Spacer(Modifier.height(4.dp))

        questions.forEachIndexed { i, q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AskCard)
                    .border(1.dp, AskCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(AskAccent.copy(0.12f))
                        .border(1.dp, AskAccent.copy(0.30f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color      = AskAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 11.sp
                        )
                    )
                }
                Text(
                    text  = q,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color      = AskText,
                        lineHeight = 22.sp,
                        fontSize   = 14.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AskCard)
                .border(1.dp, AskCardBorder, RoundedCornerShape(16.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "← Back to island",
                style = MaterialTheme.typography.labelLarge.copy(
                    color      = AskTextSub,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingView() {
    QuestionSkeletonScreen()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Error
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorView(msg: String, onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier            = Modifier.padding(32.dp)
    ) {
        Text(
            text  = msg,
            style = MaterialTheme.typography.bodyMedium.copy(
                color     = Color(0xFFB91C1C),
                textAlign = TextAlign.Center
            )
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(AskCard)
                .border(1.dp, AskCardBorder, RoundedCornerShape(14.dp))
                .clickable { onBack() }
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                "← Go back",
                style = MaterialTheme.typography.labelMedium.copy(color = AskTextSub)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Report Question Sheet — clean bottom sheet, warm theme
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReportQuestionSheet(
    questionText : String,
    questionDocId: String,
    groupId      : String,
    onDismiss    : () -> Unit
) {
    val db         = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    val myUid      = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val reasons    = listOf("Inappropriate", "Harassment", "Personal information request")
    var selected   by remember { mutableStateOf<String?>(null) }
    var submitted  by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.45f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(AskBg)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .clickable(enabled = false) {}
        ) {
            if (submitted) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("✅", fontSize = 32.sp)
                    Text(
                        "Report submitted",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = AskText, fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Thank you. Our team will review this question.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AskTextSub, textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(AskCard)
                            .border(1.dp, AskCardBorder, RoundedCornerShape(14.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "Close",
                            style = MaterialTheme.typography.labelLarge.copy(color = AskTextSub)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .width(40.dp).height(3.dp)
                            .clip(CircleShape)
                            .background(AskCardBorder)
                    )
                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Report Question",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color      = AskText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 16.sp
                        )
                    )

                    // Question preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF4E8))
                            .border(1.dp, AskCardBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "\"${questionText.take(120)}${if (questionText.length > 120) "…" else "\""}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color      = AskTextSub,
                                fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 18.sp
                            )
                        )
                    }

                    Text(
                        "Why are you reporting this?",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = AskTextLight, fontSize = 11.sp
                        )
                    )

                    reasons.forEach { reason ->
                        val isChosen = selected == reason
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isChosen) Color(0xFFFFF0E8)
                                    else          AskCard
                                )
                                .border(
                                    if (isChosen) 1.5.dp else 1.dp,
                                    if (isChosen) AskAccent.copy(0.55f) else AskCardBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { selected = reason }
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isChosen) AskAccent.copy(0.15f)
                                            else          Color.Transparent
                                        )
                                        .border(
                                            1.5.dp,
                                            if (isChosen) AskAccent else AskCardBorder,
                                            CircleShape
                                        )
                                )
                                Text(
                                    reason,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color      = if (isChosen) AskText else AskTextSub,
                                        fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Submit
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected != null && !submitting)
                                    Brush.horizontalGradient(listOf(Color(0xFFB91C1C), Color(0xFFEF4444)))
                                else
                                    Brush.horizontalGradient(listOf(AskDivider, AskDivider))
                            )
                            .alpha(if (selected != null && !submitting) 1f else 0.50f)
                            .clickable(enabled = selected != null && !submitting) {
                                submitting = true
                                val data = mapOf(
                                    "questionDocId" to questionDocId,
                                    "questionText"  to questionText,
                                    "reason"        to (selected ?: ""),
                                    "reporterUid"   to myUid,
                                    "groupId"       to groupId,
                                    "reportedAt"    to com.google.firebase.Timestamp.now()
                                )
                                db.collection("reported_questions").add(data)
                                    .addOnSuccessListener { submitted = true; submitting = false }
                                    .addOnFailureListener { submitted = true; submitting = false }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(22.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Submit Report",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color      = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    TextButton(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                    ) {
                        Text("Cancel", color = AskTextLight, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
