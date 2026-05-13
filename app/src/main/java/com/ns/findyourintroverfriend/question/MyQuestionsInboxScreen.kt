package com.ns.findyourintroverfriend.question

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.ui.theme.*
import com.ns.findyourintroverfriend.ui.entranceAnimation
import com.ns.findyourintroverfriend.ui.MyQuestionsSkeletonScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

data class QuestionReply(
    val answererEmoji : String,
    val answererAlias : String,   // "?" until Day 3
    val text          : String
)

data class AskedQuestion(
    val docId     : String,
    val question  : String,
    val dayKey    : String,
    val replies   : List<QuestionReply>
)

sealed class MyQInboxState {
    object Loading : MyQInboxState()
    data class Ready(val questions: List<AskedQuestion>, val groupDay: Int) : MyQInboxState()
    data class Error(val msg: String) : MyQInboxState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class MyQuestionsInboxViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<MyQInboxState>(MyQInboxState.Loading)
    val state: StateFlow<MyQInboxState> = _state.asStateFlow()

    init { load() }

    fun refresh() { load() }

    private fun load() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = MyQInboxState.Error("Not signed in.")
            return
        }
        _state.value = MyQInboxState.Loading
        viewModelScope.launch {
            try {
                // 1. Get groupId
                val userDoc = db.collection("users").document(uid).get().await()
                val groupId = userDoc.getString("groupId") ?: run {
                    _state.value = MyQInboxState.Error("No active group.")
                    return@launch
                }
                val groupDoc = db.collection("groups").document(groupId).get().await()
                val groupDay = (groupDoc.getLong("day") ?: 1L).toInt()

                // 2. Fetch ALL questions this user has ever asked (no day filter)
                val qSnap = db.collection("groups").document(groupId)
                    .collection("daily_questions")
                    .whereEqualTo("uid", uid)
                    .get().await()

                Log.d("MyQInbox", "Found ${qSnap.documents.size} questions asked by me")

                val result = mutableListOf<AskedQuestion>()

                for (qDoc in qSnap.documents) {
                    val questionText = qDoc.getString("question") ?: continue
                    val dayKey       = qDoc.getString("day")      ?: ""

                    // 3. Load ALL answers for this question
                    val ansSnap = qDoc.reference.collection("answers").get().await()
                    Log.d("MyQInbox", "Q '${qDoc.id}' → ${ansSnap.documents.size} raw answer docs")

                    val replies = mutableListOf<QuestionReply>()
                    for (ansDoc in ansSnap.documents) {
                        // Skip if answerer is the question owner (myself)
                        val answererUid = ansDoc.getString("uid") ?: ansDoc.id
                        if (answererUid == uid) continue

                        val text = ansDoc.getString("text") ?: continue

                        // Load answerer profile
                        val aProfile = try {
                            db.collection("users").document(answererUid).get().await()
                        } catch (_: Exception) { null }

                        val emoji = aProfile?.getString("emoji") ?: "🌙"
                        val alias = if (groupDay >= 3)
                            (aProfile?.getString("alias") ?: "Someone")
                        else
                            "Anonymous"

                        replies.add(QuestionReply(emoji, alias, text))
                    }

                    result.add(AskedQuestion(qDoc.id, questionText, dayKey, replies))
                }

                // Most recent first (sort by docId descending — contains nonce)
                result.sortByDescending { it.docId }

                _state.value = MyQInboxState.Ready(result, groupDay)
                Log.d("MyQInbox", "Done: ${result.size} questions, total replies: ${result.sumOf { it.replies.size }}")

            } catch (e: Exception) {
                Log.e("MyQInbox", "load failed", e)
                _state.value = MyQInboxState.Error(e.localizedMessage ?: "Failed to load.")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MyQuestionsInboxScreen(
    vm     : MyQuestionsInboxViewModel = viewModel(),
    onBack : () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // ── Warm palette ───────────────────────────────────────────────────────────
    val MqBg          = Color(0xFFFDF8F2)
    val MqBgMid       = Color(0xFFFFF4E8)
    val MqCard        = Color(0xFFFFFFFF)
    val MqCardBorder  = Color(0xFFEDD9C0)
    val MqAccent      = Color(0xFFE8832A)
    val MqAccentSoft  = Color(0xFFF5A654)
    val MqText        = Color(0xFF2D1F0E)
    val MqTextSub     = Color(0xFF7A5C3A)
    val MqTextLight   = Color(0xFFB08A60)
    val MqDivider     = Color(0xFFF0DFC8)
    val MqSelected    = Color(0xFFFF7B6B)
    val MqGlowLight   = Color(0xFFFFB96B)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MqBg, MqBgMid, Color(0xFFFEEDD8))
                )
            )
            .systemBarsPadding()
    ) {
        // Warm ambient glow
        Box(
            modifier = Modifier
                .size(280.dp).align(Alignment.TopEnd)
                .offset(80.dp, (-60).dp)
                .background(Brush.radialGradient(listOf(MqGlowLight.copy(0.18f), Color.Transparent)))
        )
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            MyQTopBar(
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                },
                onRefresh = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.refresh()
                }
            )

            // ── Body ──────────────────────────────────────────────────────────
            when (val s = state) {
                is MyQInboxState.Loading -> MyQuestionsSkeletonScreen()
                is MyQInboxState.Error   -> MyQErrorView(s.msg)
                is MyQInboxState.Ready   -> {
                    if (s.questions.isEmpty()) {
                        MyQEmptyView()
                    } else {
                        MyQRepliesList(questions = s.questions, groupDay = s.groupDay)
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
private fun MyQTopBar(onBack: () -> Unit, onRefresh: () -> Unit) {
    val mqAccent     = Color(0xFFE8832A)
    val mqAccentSoft = Color(0xFFF5A654)
    val mqText       = Color(0xFF2D1F0E)
    val mqTextLight  = Color(0xFFB08A60)
    val mqCard       = Color(0xFFFFFFFF)
    val mqBorder     = Color(0xFFEDD9C0)
    val mqDivider    = Color(0xFFF0DFC8)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(mqCard)
                    .border(0.8.dp, mqBorder, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ArrowBack, null,
                    tint     = mqText,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "My Questions",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color      = mqText,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 18.sp,
                        letterSpacing = 0.3.sp
                    )
                )
                Text(
                    "See how your circle responded",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color    = mqTextLight,
                        fontSize = 10.sp
                    )
                )
            }

            // Refresh pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(mqAccent.copy(0.12f))
                    .border(0.7.dp, mqAccent.copy(0.3f), RoundedCornerShape(10.dp))
                    .clickable { onRefresh() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("↺", style = MaterialTheme.typography.labelMedium.copy(
                    color = mqAccent, fontSize = 15.sp
                ))
            }
        }

        // Warm amber divider
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, mqDivider, mqAccentSoft.copy(0.50f), mqDivider, Color.Transparent)
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Replies list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MyQRepliesList(questions: List<AskedQuestion>, groupDay: Int) {
    val totalReplies = questions.sumOf { it.replies.size }

    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary pill
        val sqAccent    = Color(0xFFE8832A)
        val sqCardBdr   = Color(0xFFEDD9C0)
        item {
            Box(modifier = Modifier.entranceAnimation(0, 60)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(sqAccent.copy(0.08f))
                        .border(0.6.dp, sqAccent.copy(0.22f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${questions.size} question${if (questions.size > 1) "s" else ""} — $totalReplies repl${if (totalReplies == 1) "y" else "ies"} total",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = sqAccent,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.Center
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        items(questions, key = { it.docId }) { q ->
            val index = questions.indexOf(q) + 1
            Box(modifier = Modifier.entranceAnimation(index, 60)) {
                QuestionReplyCard(question = q, groupDay = groupDay)
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Per-question card with expandable replies
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuestionReplyCard(question: AskedQuestion, groupDay: Int) {
    val accent    = Color(0xFFE8832A)
    val accentSoft= Color(0xFFF5A654)
    val coral     = Color(0xFFFF7B6B)
    val mqCard    = Color(0xFFFFFFFF)
    val mqBorder  = Color(0xFFEDD9C0)
    val mqText    = Color(0xFF2D1F0E)
    val mqTextSub = Color(0xFF7A5C3A)
    val mqLight   = Color(0xFFB08A60)
    val haptic    = LocalHapticFeedback.current

    var expanded by remember { mutableStateOf(question.replies.isNotEmpty()) }

    val inf = rememberInfiniteTransition(label = "qrglow")
    val glow by inf.animateFloat(
        initialValue  = 0.45f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "g"
    )

    val borderBrush = if (question.replies.isNotEmpty())
        Brush.horizontalGradient(listOf(accent.copy(glow * 0.6f), accentSoft.copy(glow * 0.4f)))
    else
        Brush.horizontalGradient(listOf(mqBorder, mqBorder))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(mqCard)
            .border(1.5.dp, borderBrush, RoundedCornerShape(22.dp))
    ) {
        Column {
            // ── Question header ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        if (question.replies.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            expanded = !expanded
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Question orb
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(accent.copy(0.15f), Color.Transparent)))
                        .border(0.8.dp, accent.copy(if (question.replies.isNotEmpty()) glow * 0.65f else 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", fontSize = 16.sp, color = accent)
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(0.12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "✦ You asked",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        val count = question.replies.size
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(coral.copy(0.12f))
                                    .border(0.5.dp, coral.copy(0.30f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "$count 💬",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = coral, fontSize = 9.sp, fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        } else {
                            Text(
                                "No replies yet",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = mqLight, fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Text(
                        question.question,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color      = mqText,
                            lineHeight = 24.sp,
                            fontSize   = 14.sp
                        )
                    )
                }

                if (question.replies.isNotEmpty()) {
                    Text(
                        if (expanded) "∧" else "∨",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = mqLight, fontSize = 14.sp
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && question.replies.isNotEmpty(),
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(0.6.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color(0xFFF0DFC8), Color.Transparent)
                                )
                            )
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "✨ Replies from your circle",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = mqTextSub,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    question.replies.forEachIndexed { index, reply ->
                        ReplyBubble(reply = reply, index = index, groupDay = groupDay)
                        if (index < question.replies.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single reply bubble
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReplyBubble(reply: QuestionReply, index: Int, groupDay: Int) {
    val accent   = Color(0xFFE8832A)
    val mqBorder = Color(0xFFEDD9C0)
    val mqText   = Color(0xFF2D1F0E)
    val mqSubTxt = Color(0xFF7A5C3A)
    val bgCard   = Color(0xFFFFF9F4)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgCard)
            .border(0.6.dp, mqBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(0.10f))
                .border(0.7.dp, accent.copy(0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(reply.answererEmoji, fontSize = 18.sp)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (groupDay >= 3) reply.answererAlias else "Anonymous #${index + 1}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = accent,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                reply.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color      = mqText,
                    fontSize   = 14.sp,
                    lineHeight = 22.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Loading / Empty / Error states
// ─────────────────────────────────────────────────────────────────────────────

// Removed - using MyQuestionsSkeletonScreen instead

@Composable
private fun MyQEmptyView() {
    val mqText    = Color(0xFF2D1F0E)
    val mqTextSub = Color(0xFF7A5C3A)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text("📭", fontSize = 48.sp)
            Text(
                "You haven't asked any questions yet",
                style = MaterialTheme.typography.titleSmall.copy(
                    color     = mqText,
                    fontSize  = 16.sp,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                "Go to Activities → Ask the Circle to post your first question.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color     = mqTextSub,
                    fontSize  = 11.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun MyQErrorView(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚠️", fontSize = 36.sp)
            Text(
                msg,
                style = MaterialTheme.typography.labelMedium.copy(
                    color     = Color(0xFFB91C1C),
                    textAlign = TextAlign.Center,
                    fontSize  = 12.sp
                ),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
