package com.ns.findyourintroverfriend.party

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
//  Prompt data
// ─────────────────────────────────────────────────────────────────────────────

private data class KnowPrompt(
    val id     : String,
    val prompt : String,
    val hint   : String
)

private val KNOW_PROMPTS = listOf(
    KnowPrompt("k1", "What does a good day look like for you — not perfect, just good?",       "The small, ordinary things matter here."),
    KnowPrompt("k2", "What's something you're still figuring out about yourself?",             "No need for a complete answer. Partial is fine."),
    KnowPrompt("k3", "How do you know when you feel safe around someone?",                    "There's no wrong way to feel this."),
    KnowPrompt("k4", "What do you wish people asked you more often?",                         "Take your time. You don't have to respond today."),
    KnowPrompt("k5", "What does rest actually look like for you?",                            "Not productive rest. Just rest."),
    KnowPrompt("k6", "What's one thing you've changed your mind about in the last year?",     "Growth is allowed to be quiet."),
    KnowPrompt("k7", "What do you find genuinely difficult to talk about?",                   "Share only what feels right, and only when it does.")
)

// ─────────────────────────────────────────────────────────────────────────────
//  KnowEachOtherScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun KnowEachOtherScreen(
    me      : PartyMember?,
    partner : PartyMember?,
    onBack  : () -> Unit
) {
    val myAnswers      = remember { mutableStateMapOf<String, String>() }
    val partnerAnswers = remember { mutableStateMapOf<String, String>() }

    var currentIndex by remember { mutableIntStateOf(0) }

    // Fade-in
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "fa")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PBg)
            .graphicsLayer(alpha = alpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PCard)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PCardSoft)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", style = MaterialTheme.typography.bodyLarge.copy(color = PText))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Reflect Together",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold, color = PText
                            )
                        )
                        Text(
                            "${currentIndex + 1} of ${KNOW_PROMPTS.size} prompts",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PTextMid, fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(PBorder)
            ) {
                val progress = (currentIndex + 1f) / KNOW_PROMPTS.size
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(PSage)
                )
            }

            // ── Prompt content ────────────────────────────────────────────────
            AnimatedContent(
                targetState  = currentIndex,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(350)) },
                label        = "prompt",
                modifier     = Modifier.weight(1f)
            ) { idx ->
                val p = KNOW_PROMPTS[idx]
                KnowContent(
                    prompt        = p,
                    me            = me,
                    partner       = partner,
                    myAnswer      = myAnswers[p.id],
                    partnerAnswer = partnerAnswers[p.id],
                    onSubmit      = { ans -> myAnswers[p.id] = ans }
                )
            }

            // ── Nav bar ───────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(PBorder))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PCard)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Prev
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (currentIndex > 0) PCardSoft else Color.Transparent)
                            .clickable(enabled = currentIndex > 0) { currentIndex-- }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "← Prev",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (currentIndex > 0) PText else PTextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(KNOW_PROMPTS.size) { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (i == currentIndex) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(if (i == currentIndex) PSage else PBorder)
                            )
                        }
                    }

                    // Next
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (currentIndex < KNOW_PROMPTS.size - 1) PSage else Color.Transparent)
                            .clickable(enabled = currentIndex < KNOW_PROMPTS.size - 1) { currentIndex++ }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Next →",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (currentIndex < KNOW_PROMPTS.size - 1) Color.White else PTextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Prompt content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KnowContent(
    prompt        : KnowPrompt,
    me            : PartyMember?,
    partner       : PartyMember?,
    myAnswer      : String?,
    partnerAnswer : String?,
    onSubmit      : (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var draft    by remember(prompt.id) { mutableStateOf("") }
    val submitted = myAnswer?.isNotBlank() == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        // Prompt badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PSageLight)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                "PROMPT",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = PSage, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp, fontSize = 9.sp
                )
            )
        }

        Spacer(Modifier.height(16.dp))

        // Prompt text — large, clear
        Text(
            text      = prompt.prompt,
            style     = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color      = PText,
                fontSize   = 20.sp,
                lineHeight = 30.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        // Hint
        Text(
            text      = prompt.hint,
            style     = MaterialTheme.typography.bodySmall.copy(
                color = PTextMuted, fontSize = 12.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // ── Your response ─────────────────────────────────────────────────────
        if (!submitted) {
            // Input area
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "YOUR ANSWER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = PTextMuted, letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.SemiBold, fontSize = 10.sp
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PCard)
                        .border(1.dp, PBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    BasicTextField(
                        value          = draft,
                        onValueChange  = { draft = it },
                        modifier       = Modifier.fillMaxWidth(),
                        textStyle      = MaterialTheme.typography.bodyMedium.copy(
                            color = PText, fontSize = 15.sp, lineHeight = 23.sp
                        ),
                        cursorBrush    = SolidColor(PSage),
                        decorationBox  = { inner ->
                            if (draft.isEmpty()) Text(
                                "Take your time with this…",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PTextMuted, fontSize = 15.sp
                                )
                            )
                            inner()
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction      = ImeAction.Done,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (draft.isNotBlank()) { onSubmit(draft); focusManager.clearFocus() }
                        }),
                        singleLine = false,
                        maxLines   = 8
                    )
                }
                if (draft.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PSage)
                            .clickable {
                                if (draft.isNotBlank()) { onSubmit(draft); focusManager.clearFocus() }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Share →",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White, fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        } else {
            // My answer card
            KnowAnswerBox(
                label  = "YOU WROTE",
                name   = me?.name ?: "You",
                answer = myAnswer ?: "",
                accent = PSage
            )

            Spacer(Modifier.height(16.dp))

            // Divider
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.weight(1f).height(0.5.dp).background(PBorder))
                Text(
                    "& their take",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = PTextMuted, fontSize = 10.sp, letterSpacing = 0.5.sp
                    )
                )
                Box(Modifier.weight(1f).height(0.5.dp).background(PBorder))
            }

            Spacer(Modifier.height(16.dp))

            // Partner answer or waiting
            if (partnerAnswer?.isNotBlank() == true) {
                KnowAnswerBox(
                    label  = "THEY WROTE",
                    name   = partner?.name ?: "Them",
                    answer = partnerAnswer,
                    accent = PBlue
                )
            } else {
                // Waiting card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PCardSoft)
                        .border(1.dp, PBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "WAITING FOR ${(partner?.name ?: "THEM").uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PTextMuted, letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.SemiBold, fontSize = 9.sp
                            )
                        )
                        Text(
                            "${partner?.name ?: "They"} hasn't answered yet.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = PTextMid, fontSize = 14.sp
                            )
                        )
                        Text(
                            "Silence is fine. There's no deadline.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = PTextMuted, fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Answer box
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KnowAnswerBox(label: String, name: String, answer: String, accent: Color) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = accent, letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold, fontSize = 9.sp
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PCard)
                .border(1.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    answer,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = PText, fontSize = 14.sp, lineHeight = 22.sp
                    )
                )
                Text(
                    "— $name",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accent.copy(alpha = 0.65f), fontSize = 11.sp
                    )
                )
            }
        }
    }
}
