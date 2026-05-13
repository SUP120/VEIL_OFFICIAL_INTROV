package com.ns.findyourintroverfriend.inbox

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.ui.theme.*
import com.ns.findyourintroverfriend.ui.entranceAnimation
import com.ns.findyourintroverfriend.ui.InboxSkeletonScreen

// ---------------------------------------------------------------------------
//  Inbox Screen -- what your circle said about your question
// ---------------------------------------------------------------------------

@Composable
fun InboxScreen(
    vm     : InboxViewModel = viewModel(),
    onBack : () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF040810), Color(0xFF080F24), Color(0xFF0C1530))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            InboxTopBar(
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                }
            )

            AnimatedContent(
                targetState   = state,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                label         = "inboxState"
            ) { s ->
                when (s) {
                    is InboxUiState.Loading    -> InboxSkeletonScreen()
                    is InboxUiState.NoQuestion -> NoQuestionView(onBack = onBack)
                    is InboxUiState.Ready      -> InboxContent(
                        state = s, onReact = vm::sendReaction
                    )
                    is InboxUiState.Error      -> ErrorView(s.msg, onBack)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Top bar
// ---------------------------------------------------------------------------

@Composable
private fun InboxTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(0.7.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ArrowBack, null,
                tint     = VeilTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "What they said",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = VeilTextSecondary, letterSpacing = 1.sp
                )
            )
            Text(
                "Reactions are private",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = VeilTextDisabled, fontSize = 10.sp
                )
            )
        }

        Box(modifier = Modifier.size(40.dp))
    }
}

// ---------------------------------------------------------------------------
//  Main content -- my question + all answers
// ---------------------------------------------------------------------------

@Composable
private fun InboxContent(
    state   : InboxUiState.Ready,
    onReact : (InboxAnswer, Reaction) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(modifier = Modifier.entranceAnimation(0, 60)) {
                MyQuestionCard(question = state.myQuestion)
            }
            Spacer(Modifier.height(4.dp))
        }

        if (state.answers.isEmpty()) {
            item { 
                Box(modifier = Modifier.entranceAnimation(1, 60)) {
                    NoAnswersYet()
                }
            }
        } else {
            item {
                Box(modifier = Modifier.entranceAnimation(1, 60)) {
                    val count = state.answers.size
                    Text(
                        "$count ${if (count == 1) "answer" else "answers"} received",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = VeilTextDisabled, fontSize = 11.sp
                        )
                    )
                }
            }
            items(state.answers, key = { it.respondentUid }) { answer ->
                val index = state.answers.indexOf(answer)
                Box(modifier = Modifier.entranceAnimation(index + 2, 60)) {
                    AnswerCard(
                        answer  = answer,
                        onReact = { r ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReact(answer, r)
                        }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ---------------------------------------------------------------------------
//  My question card (header)
// ---------------------------------------------------------------------------

@Composable
private fun MyQuestionCard(question: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(VeilPrimary.copy(alpha = 0.20f), VeilPrimaryMid.copy(alpha = 0.10f))
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(VeilAccentGlow.copy(alpha = 0.30f), VeilAccentSand.copy(alpha = 0.15f))
                ),
                RoundedCornerShape(22.dp)
            )
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VeilAccentGlow)
                )
                Text(
                    "Your question today",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = VeilAccentGlow.copy(alpha = 0.8f), fontSize = 11.sp
                    )
                )
            }
            // Wrap in escaped quotes to avoid smart-quote encoding issues
            Text(
                text  = "\"" + question + "\"",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color      = VeilTextPrimary,
                    lineHeight = 26.sp,
                    fontStyle  = FontStyle.Italic,
                    fontSize   = 16.sp
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Single answer card
// ---------------------------------------------------------------------------

@Composable
private fun AnswerCard(answer: InboxAnswer, onReact: (Reaction) -> Unit) {
    val isReacted = answer.myReaction != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                0.7.dp,
                if (isReacted) VeilAccentGlow.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // -- Avatar row -------------------------------------------------
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(Color(0xFF181E30), Color(0xFF0C1020)))
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(answer.respondentEmoji, fontSize = 18.sp)
                }

                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Anonymous",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = VeilTextDisabled, fontSize = 10.sp
                        )
                    )
                    Text(
                        text  = answer.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color      = VeilTextPrimary,
                            lineHeight = 23.sp,
                            fontSize   = 15.sp
                        )
                    )
                }
            }

            // -- Divider ----------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )

            // -- Reaction area ----------------------------------------------
            if (isReacted) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(answer.myReaction!!.emoji, fontSize = 14.sp)
                    Text(
                        "You reacted: " + answer.myReaction.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = VeilTextTertiary, fontSize = 11.sp
                        )
                    )
                }
            } else {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Reaction.values().forEach { r ->
                        ReactionChip(reaction = r, onClick = { onReact(r) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Reaction chip
// ---------------------------------------------------------------------------

@Composable
private fun ReactionChip(reaction: Reaction, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "reactionScale"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50.dp))
            .clickable { 
                pressed = true
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(reaction.emoji, fontSize = 13.sp)
            Text(
                reaction.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = VeilTextTertiary, fontSize = 10.sp
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Empty / loading / error states
// ---------------------------------------------------------------------------

@Composable
private fun NoAnswersYet() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🌙", style = MaterialTheme.typography.titleLarge)
            Text(
                "No answers yet",
                style = MaterialTheme.typography.bodyMedium.copy(color = VeilTextSecondary)
            )
            Text(
                "Your circle is reflecting.\nCheck back a little later.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = VeilTextTertiary,
                    textAlign  = TextAlign.Center,
                    lineHeight = 19.sp
                )
            )
        }
    }
}

@Composable
private fun NoQuestionView(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp)
        ) {
            Text(
                "✦",
                style = MaterialTheme.typography.displaySmall.copy(color = VeilAccentSand)
            )
            Text(
                "Post your question first",
                style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)
            )
            Text(
                "Once you send today's question, you will see your circle's answers here.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = VeilTextTertiary,
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp
                )
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.7.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    "<- Go back",
                    style = MaterialTheme.typography.labelMedium.copy(color = VeilTextSecondary)
                )
            }
        }
    }
}

// Removed - using InboxSkeletonScreen instead

@Composable
private fun ErrorView(msg: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp)
        ) {
            Text(
                msg,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = VeilError, textAlign = TextAlign.Center
                )
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.7.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    "<- Go back",
                    style = MaterialTheme.typography.labelMedium.copy(color = VeilTextSecondary)
                )
            }
        }
    }
}
