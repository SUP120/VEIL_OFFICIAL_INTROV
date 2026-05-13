package com.ns.findyourintroverfriend.answer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.ui.theme.*
import com.ns.findyourintroverfriend.ui.entranceAnimation
import com.ns.findyourintroverfriend.ui.AnswerCardSkeletonScreen
import com.ns.findyourintroverfriend.question.ReportQuestionSheet

// ─────────────────────────────────────────────────────────────────────────────
//  Answer Card Screen — answer your circle's questions, then react + echo
// ─────────────────────────────────────────────────────────────────────────────

// Emoji palette for Soft Reactions
private val REACTION_EMOJIS = listOf("🔥", "✨", "💙", "🌊", "🌸", "🤍", "💫", "👀")

@Composable
fun AnswerCardScreen(
    vm     : AnswerCardViewModel = viewModel(),
    onBack : () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E1A),  // Deep navy
                        Color(0xFF141B2E),  // Midnight blue
                        Color(0xFF1A2438)   // Slate blue
                    )
                )
            )
    ) {
        // Subtle ambient glow — top right (cyan)
        Box(
            modifier = Modifier.size(300.dp).align(Alignment.TopEnd)
                .offset(100.dp, (-80).dp)
                .background(Brush.radialGradient(listOf(Color(0xFF00D9FF).copy(alpha = 0.08f), Color.Transparent)))
        )
        // Subtle ambient glow — bottom left (teal)
        Box(
            modifier = Modifier.size(250.dp).align(Alignment.BottomStart)
                .offset((-70).dp, 90.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF00FFB3).copy(alpha = 0.06f), Color.Transparent)))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            AnswerTopBar(
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                }
            )

            // ── Body ──────────────────────────────────────────────────────────
            AnimatedContent(
                targetState   = state,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                label         = "answerState"
            ) { s ->
                when (s) {
                    is AnswerUiState.Loading -> AnswerCardSkeletonScreen()
                    is AnswerUiState.Ready   -> {
                        if (s.questions.isEmpty() && s.myOwnQuestion == null) {
                            EmptyView()
                        } else {
                            QuestionList(
                                questions     = s.questions,
                                groupDay      = s.groupDay,
                                groupId       = s.groupId,
                                myOwnQuestion = s.myOwnQuestion,
                                myQAnswers    = s.myQAnswers,
                                onSubmit      = { q, text ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.submitAnswer(q, text)
                                },
                                onReact       = { q, emoji ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.sendReaction(q, emoji)
                                },
                                onEcho        = { q ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.sendEcho(q)
                                }
                            )
                        }
                    }
                    is AnswerUiState.Error   -> ErrorView(s.msg, onBack)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnswerTopBar(onBack: () -> Unit) {
    val accent  = Color(0xFF00D9FF)  // Electric cyan
    val text    = Color(0xFFF8FAFC)  // Very light gray - improved contrast
    val subTxt  = Color(0xFFCBD5E1)  // Lighter medium gray - improved contrast
    val card    = Color(0xFF1E293B)  // Dark slate
    val border  = Color(0xFF475569)  // Lighter slate border - improved contrast
    val divider = Color(0xFF00D9FF)  // Cyan divider
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button - larger and more prominent
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                card,
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(1.2.dp, border, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint               = text,
                    modifier           = Modifier.size(20.dp)
                )
            }

            // Title - centered and prominent
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(0.8.dp, Color(0xFF00D9FF).copy(0.35f), CircleShape)
                ) {
                    androidx.compose.foundation.Image(
                        painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_answer_react),
                        contentDescription = null,
                        modifier     = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Text(
                    text  = "Your Circle Asked",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color         = text,
                        fontWeight    = FontWeight.Black,
                        fontSize      = 19.sp,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text  = "Answer  ·  React  ·  Echo",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color         = subTxt,
                        fontSize      = 11.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight    = FontWeight.Medium
                    )
                )
            }

            // Spacer for symmetry
            Box(modifier = Modifier.size(44.dp))
        }
        
        // Enhanced gradient divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            divider.copy(0.2f),
                            accent.copy(0.7f),
                            divider.copy(0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Question list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuestionList(
    questions     : List<CircleQuestion>,
    groupDay      : Int,
    groupId       : String,
    myOwnQuestion : CircleQuestion? = null,
    myQAnswers    : List<AnonymousAnswer> = emptyList(),
    onSubmit      : (CircleQuestion, String) -> Unit,
    onReact       : (CircleQuestion, String) -> Unit,
    onEcho        : (CircleQuestion) -> Unit
) {
    val answered = questions.count { it.myAnswer != null }
    val total    = questions.size

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(modifier = Modifier.entranceAnimation(0, 60)) {
                ProgressPill(answered = answered, total = total)
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Your own question + received answers ────────────────────────────────────
        myOwnQuestion?.let { ownQ ->
            item(key = "ownQ_${ownQ.docId}") {
                Box(modifier = Modifier.entranceAnimation(1, 60)) {
                    OwnQuestionCard(question = ownQ, answers = myQAnswers, groupDay = groupDay)
                }
            }
        }

        items(questions, key = { it.docId }) { q ->
            val index = questions.indexOf(q) + (if (myOwnQuestion != null) 2 else 1)
            Box(modifier = Modifier.entranceAnimation(index, 60)) {
                CircleQuestionCard(
                    question  = q,
                    groupDay  = groupDay,
                    groupId   = groupId,
                    onSubmit  = { text -> onSubmit(q, text) },
                    onReact   = { emoji -> onReact(q, emoji) },
                    onEcho    = { onEcho(q) }
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun ProgressPill(answered: Int, total: Int) {
    val fraction = if (total > 0) answered.toFloat() / total else 0f
    val bgColor = Color(0xFF1E293B).copy(alpha = 0.6f)
    val borderColor = Color(0xFF334155).copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Progress text
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text  = "$answered / $total",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = Color(0xFFF8FAFC),
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text  = "answered",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color    = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            
            // Progress bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F172A))
            ) {
                val animFraction by animateFloatAsState(
                    targetValue   = fraction,
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    label         = "progress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animFraction)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF00D9FF),
                                    Color(0xFF00FFB3)
                                )
                            )
                        )
                )
            }
            
            // Completion badge
            if (answered == total && total > 0) {
                val badgeBorder = Color(0xFF00FFB3).copy(alpha = 0.4f)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF00FFB3).copy(alpha = 0.15f),
                                    Color(0xFF00D9FF).copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(1.dp, badgeBorder)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text  = "✦ Done",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color      = Color(0xFF00FFB3),
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Your OWN question card — shown with a distinct badge at the top
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OwnQuestionCard(
    question : CircleQuestion,
    answers  : List<AnonymousAnswer> = emptyList(),
    groupDay : Int = 1
) {
    val inf = rememberInfiniteTransition(label = "ownQGlow")
    val glow by inf.animateFloat(
        initialValue  = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "og"
    )
    val cyanA = Color(0xFF00D9FF)
    val cyanB = Color(0xFF00FFB3)
    var expanded by remember { mutableStateOf(true) }  // show answers expanded by default

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
            )
            .border(
                1.5.dp,
                Brush.horizontalGradient(listOf(cyanA.copy(glow * 0.6f), cyanB.copy(glow * 0.4f))),
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Badge row
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(cyanA.copy(0.15f), cyanB.copy(0.10f))))
                        .border(0.8.dp, cyanA.copy(0.3f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "✦  Your Question",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = cyanA, fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    "You asked the circle",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B), fontSize = 10.sp
                    )
                )
                Spacer(Modifier.weight(1f))
                // Answer count badge
                if (answers.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(cyanB.copy(0.15f))
                            .border(0.8.dp, cyanB.copy(0.3f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${answers.size} 💬",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = cyanB, fontSize = 10.sp, fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Question text - MAXIMUM CONTRAST & READABILITY
            val questionBgOwn = Color(0xFF0A0E1A).copy(alpha = 0.6f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(questionBgOwn)
                    .padding(18.dp)
            ) {
                Text(
                    text  = question.question,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color      = Color(0xFFFAFAFA),  // Near-white for maximum readability
                        lineHeight = 28.sp,
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                )
            }

            // ── Answers received ──────────────────────────────────────
            if (answers.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(0.8.dp, Color(0xFF334155))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔌", fontSize = 14.sp)
                    Text(
                        "No answers yet. Your circle is still thinking...",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8), fontSize = 11.sp, lineHeight = 16.sp
                        )
                    )
                }
            } else {
                // Header: tap to collapse/expand
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cyanA.copy(0.08f))
                        .border(0.8.dp, cyanA.copy(0.2f))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "✨ ${answers.size} answer${if (answers.size > 1) "s" else ""} from your circle",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        if (expanded) "∧" else "∨",
                        style = MaterialTheme.typography.labelMedium.copy(color = cyanA.copy(0.7f))
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.height(2.dp))
                        answers.forEachIndexed { index, ans ->
                            ReceivedAnswerTile(
                                answer   = ans,
                                index    = index,
                                groupDay = groupDay
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedAnswerTile(
    answer   : AnonymousAnswer,
    index    : Int,
    groupDay : Int
) {
    val cyanA = Color(0xFF00D9FF)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(0.8.dp, Color(0xFF334155))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Anonymous avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155)),
            contentAlignment = Alignment.Center
        ) {
            Text(answer.answererEmoji, fontSize = 16.sp)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                if (groupDay >= 3) answer.answererAlias else "Anonymous #${index + 1}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = cyanA, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            )
            Text(
                answer.text,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFE2E8F0), fontSize = 13.sp, lineHeight = 20.sp
                )
            )
            // Reaction they sent back
            answer.reaction?.let { emoji ->
                Text(
                    "reacted $emoji",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B), fontSize = 10.sp
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single question card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircleQuestionCard(
    question  : CircleQuestion,
    groupDay  : Int,
    groupId   : String,
    onSubmit  : (String) -> Unit,
    onReact   : (String) -> Unit,
    onEcho    : () -> Unit
) {
    val isAnswered = question.myAnswer != null
    var answerText by remember { mutableStateOf(question.myAnswer ?: "") }
    var focused    by remember { mutableStateOf(false) }
    var submitted  by remember { mutableStateOf(isAnswered) }
    var showReport by remember { mutableStateOf(false) }

    LaunchedEffect(question.myAnswer) {
        if (question.myAnswer != null) {
            answerText = question.myAnswer
            submitted  = true
        }
    }

    val cardBg = if (submitted)
        Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
    else
        Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                width = if (submitted) 1.5.dp else 1.dp,
                brush = if (submitted)
                    Brush.horizontalGradient(listOf(Color(0xFF00D9FF).copy(0.6f), Color(0xFF00FFB3).copy(0.4f)))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF334155))),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ── Question header ───────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Day-based avatar
                val inf = rememberInfiniteTransition(label = "av_inf")
                val veilShimmer by inf.animateFloat(
                    initialValue  = 0.4f, targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "veil_shimmer"
                )

                val borderModifier = when (groupDay) {
                    3    -> Modifier.border(1.5.dp, Brush.sweepGradient(listOf(Color(0xFFFFD77A), Color(0xFFFFBB55), Color(0xFFFFD77A))), CircleShape)
                    2    -> Modifier.border(1.5.dp, Brush.sweepGradient(listOf(Color(0xFF9B6DFF).copy(alpha = veilShimmer), Color(0xFF7C4DFF).copy(alpha = veilShimmer * 0.6f), Color(0xFF9B6DFF).copy(alpha = veilShimmer))), CircleShape)
                    else -> Modifier.border(1.dp, Color(0xFF334155), CircleShape)
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))))
                        .then(borderModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(question.ownerEmoji, fontSize = 18.sp)
                }

                Column {
                    Text(
                        text  = if (groupDay >= 3) question.ownerAlias.take(8) else "?",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color    = if (groupDay >= 3) Color(0xFF00D9FF) else Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text  = "asked the circle",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    )
                }

                Spacer(Modifier.weight(1f))

                // ⚠️ Report button — always visible, top right
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF4444).copy(0.12f))
                        .border(0.8.dp, Color(0xFFEF4444).copy(0.35f))
                        .clickable { showReport = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        "⚠️ Report",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = Color(0xFFEF4444),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (submitted) {
                    Icon(
                        imageVector        = Icons.Rounded.CheckCircle,
                        contentDescription = "Answered",
                        tint               = Color(0xFF00FFB3),
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }

            // ── Question text - MAXIMUM CONTRAST & READABILITY ─────────────────────────────────────────────────
            val questionBg = Color(0xFF0A0E1A).copy(alpha = 0.6f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(questionBg)
                    .padding(18.dp)
            ) {
                Text(
                    text  = question.question,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color      = Color(0xFFFAFAFA),  // Near-white for maximum readability
                        lineHeight = 28.sp,
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                )
            }

            // ── Answer area ───────────────────────────────────────────────────
            if (submitted) {
                // Read-only answer display - enhanced
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF0A0E1A)
                                )
                            )
                        )
                        .border(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF00D9FF).copy(0.4f),
                                    Color(0xFF00FFB3).copy(0.3f)
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text  = "Your answer",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color      = Color(0xFF00D9FF),
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        )
                        Text(
                            text  = answerText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color      = Color(0xFFF1F5F9),
                                lineHeight = 24.sp,
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }

                // ── Soft Reactions row ────────────────────────────────────────
                ReactionBar(
                    selectedEmoji = question.myReaction,
                    onReact       = onReact
                )

                // ── Echo button ────────────────────────────────────────────────
                EchoButton(
                    echoSent = question.echoSent,
                    onEcho   = onEcho
                )

            } else {
                // Input field - enhanced
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .border(
                            width = if (focused) 1.8.dp else 1.dp,
                            brush = if (focused)
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF00D9FF),
                                        Color(0xFF00FFB3)
                                    )
                                )
                            else
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF334155).copy(alpha = 0.6f),
                                        Color(0xFF334155).copy(alpha = 0.6f)
                                    )
                                ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(18.dp)
                ) {
                    BasicTextField(
                        value         = answerText,
                        onValueChange = { answerText = it },
                        textStyle     = MaterialTheme.typography.bodyMedium.copy(
                            color      = Color(0xFFF1F5F9),
                            fontSize   = 15.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush   = SolidColor(Color(0xFF00D9FF)),
                        minLines      = 3,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            if (answerText.isEmpty()) {
                                Text(
                                    "Write your honest answer…",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color    = Color(0xFF64748B),
                                        fontSize = 15.sp
                                    )
                                )
                            }
                            inner()
                        }
                    )
                }

                // Send button - enhanced
                val canSend = answerText.isNotBlank()
                val scale   by animateFloatAsState(
                    targetValue   = if (canSend) 1f else 0.98f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label         = "sendScale"
                )
                
                val inf = rememberInfiniteTransition(label = "sendGlow")
                val sendGlow by inf.animateFloat(
                    initialValue  = 0.6f,
                    targetValue   = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(2000, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "sg"
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (canSend)
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF00D9FF).copy(alpha = sendGlow * 0.9f),
                                        Color(0xFF00FFB3).copy(alpha = sendGlow * 0.8f)
                                    )
                                )
                            else
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF1E293B),
                                        Color(0xFF1E293B)
                                    )
                                )
                        )
                        .border(
                            1.2.dp,
                            if (canSend) Color(0xFF00FFB3).copy(alpha = 0.4f) else Color(0xFF334155).copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = canSend) {
                            onSubmit(answerText)
                            submitted = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "Send answer  →",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color      = if (canSend) Color(0xFF0A0E1A) else Color(0xFF64748B),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }
        }
    }

    // ── Report Question overlay ───────────────────────────────────────────────
    if (showReport) {
        ReportQuestionSheet(
            questionText  = question.question,
            questionDocId = question.docId,
            groupId       = groupId,
            onDismiss     = { showReport = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Soft Reaction bar — shown after answering
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReactionBar(
    selectedEmoji : String?,
    onReact       : (String) -> Unit
) {
    val reactionBg = Color(0xFF0F172A).copy(alpha = 0.6f)
    val selectedBorder = Color(0xFF00D9FF).copy(alpha = 0.3f)
    val unselectedBorder = Color(0xFF334155).copy(alpha = 0.4f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(reactionBg)
            .border(
                1.dp,
                if (selectedEmoji != null) selectedBorder else unselectedBorder
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text  = if (selectedEmoji != null) "You reacted $selectedEmoji" else "Send a vibe",
                style = MaterialTheme.typography.labelMedium.copy(
                    color      = if (selectedEmoji != null) Color(0xFF00D9FF) else Color(0xFFCBD5E1),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                REACTION_EMOJIS.forEach { emoji ->
                    val isSelected = selectedEmoji == emoji
                    val scale by animateFloatAsState(
                        targetValue   = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label         = "emojiScale$emoji"
                    )
                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected)
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF00D9FF).copy(alpha = 0.25f),
                                            Color(0xFF00FFB3).copy(alpha = 0.15f)
                                        )
                                    )
                                else
                                    SolidColor(Color(0xFF1E293B))
                            )
                            .border(
                                1.5.dp,
                                if (isSelected) Color(0xFF00D9FF).copy(alpha = 0.6f)
                                else Color(0xFF334155).copy(alpha = 0.5f)
                            )
                            .clickable { if (selectedEmoji == null) onReact(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 19.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Echo button — "I deeply resonate with this"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EchoButton(
    echoSent : Boolean,
    onEcho   : () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (echoSent) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "echoScale"
    )
    
    val inf = rememberInfiniteTransition(label = "echoGlow")
    val echoGlow by inf.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "eg"
    )
    
    val echoBgColor = Color(0xFF0F172A).copy(alpha = 0.6f)
    val echoBorderColor = Color(0xFF334155).copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (echoSent)
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF00D9FF).copy(alpha = 0.15f),
                            Color(0xFF00FFB3).copy(alpha = 0.10f)
                        )
                    )
                else
                    SolidColor(echoBgColor)
            )
            .border(
                1.2.dp,
                if (echoSent)
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF00D9FF).copy(alpha = echoGlow * 0.6f),
                            Color(0xFF00FFB3).copy(alpha = echoGlow * 0.4f)
                        )
                    )
                else
                    SolidColor(echoBorderColor),
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = !echoSent) { onEcho() }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (echoSent)
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF00D9FF).copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        else
                            SolidColor(Color(0xFF1E293B))
                    )
                    .border(
                        1.dp,
                        if (echoSent) Color(0xFF00D9FF).copy(alpha = 0.5f)
                        else Color(0xFF334155).copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text     = if (echoSent) "🌊" else "〜",
                    fontSize = 20.sp
                )
            }
            
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text  = if (echoSent) "You echoed this" else "Echo this",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color      = if (echoSent) Color(0xFF00D9FF) else Color(0xFFF1F5F9),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text  = if (echoSent) "This resonance is noted." else "It deeply resonated with you",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color    = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                )
            }
            
            if (!echoSent) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00FFB3).copy(alpha = 0.15f))
                        .border(0.8.dp, Color(0xFF00FFB3).copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text  = "+2 bond",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color      = Color(0xFF00FFB3),
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty view — no questions yet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp)
        ) {
            Text("✦", style = MaterialTheme.typography.displaySmall.copy(color = VeilAccentSand))
            Text(
                "No questions from your circle yet",
                style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)
            )
            Text(
                "They haven't posted today's question yet.\nCheck back a little later.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color     = VeilTextTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Loading / Error
// ─────────────────────────────────────────────────────────────────────────────

// Removed - using AnswerCardSkeletonScreen instead

@Composable
private fun ErrorView(msg: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp)
        ) {
            Text(
                text  = msg,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = VeilError, textAlign = TextAlign.Center
                )
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(VeilSurface)
                    .border(0.8.dp, VeilDivider)
                    .clickable { onBack() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("← Go back", style = MaterialTheme.typography.labelMedium.copy(color = VeilTextSecondary))
            }
        }
    }
}
