package com.ns.findyourintroverfriend.day2

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
//  Palette — warm ivory / amber (matches Ask the Circle theme)
// ─────────────────────────────────────────────────────────────────────────────
private val D2Bg       = Color(0xFFFDF8F2)   // warm ivory
private val D2Card     = Color(0xFFFFFFFF)   // pure white card
private val D2Elev     = Color(0xFFFFF4E8)   // peach-cream elevated
private val D2Border   = Color(0xFFEDD9C0)   // warm beige border
private val D2BorderA  = Color(0xFFE8832A)   // amber accent border
private val D2TxtP     = Color(0xFF2D1F0E)   // deep warm brown
private val D2TxtS     = Color(0xFF7A5C3A)   // medium warm brown
private val D2TxtT     = Color(0xFFB08A60)   // light warm tan
private val D2Accent   = Color(0xFFE8832A)   // warm amber
private val D2Green    = Color(0xFF2E9668)   // forest green retained
private val D2GreenDim = Color(0xFFE6F5EE)   // light green tint

// ─────────────────────────────────────────────────────────────────────────────
//  Prompts
// ─────────────────────────────────────────────────────────────────────────────
private val DARE_PROMPTS = listOf(
    "Share something you've never said out loud to anyone",
    "What's a fear you've hidden from people close to you?",
    "Something you regret not doing — be honest",
    "A thought you have that feels too strange to say",
    "What would you do differently if no one could judge you?",
    "A secret you've been carrying for way too long",
    "Something you wish someone would just ask you about",
    "The last time you felt truly lonely — what happened?",
    "What's a compliment you've always wanted to receive?",
    "Something you pretend not to care about, but deeply do",
    "A small victory no one in your life knows about",
    "Your most embarrassing memory that still makes you cringe",
    "Something you wanted but never found the courage to ask for",
    "One thing you'd change about yourself if you could",
    "A childhood memory you still think about often",
    "The last time you cried — what triggered it?",
    "Something that made you unexpectedly stronger",
    "Your honest first impression of this group, said anonymously",
    "A habit you've tried to break but keep returning to",
    "Something you've only ever told one person in your life",
    "What's the kindest thing a stranger has ever done for you?",
    "A dream you've quietly given up on — what was it?",
    "Something you believe that most people around you don't",
    "What's the bravest thing you've ever done quietly?",
    "One thing you're secretly proud of that no one knows"
)

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────
data class DareAnswer(
    val uid        : String = "",
    val groupId    : String = "",
    val promptText : String = "",
    val answer     : String = "",
    val timestamp  : Long   = 0L
)

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DareToShareScreen(onBack: () -> Unit = {}) {
    val db    = remember { FirebaseFirestore.getInstance() }
    val auth  = remember { FirebaseAuth.getInstance() }
    val myUid = remember { auth.currentUser?.uid ?: "" }
    val scope = rememberCoroutineScope()

    var groupId    by remember { mutableStateOf("") }
    var groupDay   by remember { mutableStateOf(1) }
    var loading    by remember { mutableStateOf(true) }
    var allAnswers by remember { mutableStateOf<List<DareAnswer>>(emptyList()) }

    var selectedPrompt by remember { mutableStateOf<String?>(null) }
    var answerText     by remember { mutableStateOf("") }
    var isSubmitting   by remember { mutableStateOf(false) }
    var submitError    by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Derived
    val myAnswer     = allAnswers.firstOrNull { it.uid == myUid }
    val hasSubmitted = myAnswer != null
    val othersAnswers = allAnswers.filter { it.uid != myUid }
        .sortedByDescending { it.timestamp }

    // ── Load group ────────────────────────────────────────────────────────────
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) { loading = false; return@LaunchedEffect }
        try {
            val doc = db.collection("users").document(myUid).get().await()
            groupId  = doc.getString("groupId") ?: ""
            if (groupId.isNotBlank()) {
                val grp = db.collection("groups").document(groupId).get().await()
                groupDay = (grp.getLong("day") ?: 1L).toInt()
            }
        } catch (_: Exception) {}
        loading = false
    }

    // ── Real-time listener (no orderBy → no composite index needed) ───────────
    DisposableEffect(groupId) {
        if (groupId.isBlank()) return@DisposableEffect onDispose {}
        val reg = db.collection("dare_answers")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { doc ->
                    try {
                        DareAnswer(
                            uid        = doc.getString("uid") ?: return@mapNotNull null,
                            groupId    = doc.getString("groupId") ?: "",
                            promptText = doc.getString("promptText") ?: "",
                            answer     = doc.getString("answer") ?: "",
                            timestamp  = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (_: Exception) { null }
                }
                allAnswers = list
                // Pre-fill selected prompt if already answered
                if (selectedPrompt == null) {
                    list.firstOrNull { it.uid == myUid }?.let {
                        selectedPrompt = it.promptText
                        answerText     = it.answer
                    }
                }
            }
        onDispose { reg.remove() }
    }

    // ── Submit ────────────────────────────────────────────────────────────────
    fun submit() {
        val prompt = selectedPrompt ?: return
        if (answerText.isBlank()) { submitError = "Write something first."; return }
        if (isSubmitting) return
        isSubmitting = true
        submitError  = ""
        scope.launch {
            try {
                db.collection("dare_answers")
                    .document("${groupId}_${myUid}")
                    .set(mapOf(
                        "uid"        to myUid,
                        "groupId"    to groupId,
                        "promptText" to prompt,
                        "answer"     to answerText.trim(),
                        "timestamp"  to System.currentTimeMillis()
                    ))
                    .await()
            } catch (_: Exception) {
                submitError = "Could not save. Check connection and try again."
            } finally {
                isSubmitting = false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(D2Bg, Color(0xFFFFF4E8), Color(0xFFFEEDD8))
                )
            )
    ) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 56.dp)
        ) {

            // ── Top bar ─────────────────────────────────────────────────────
            item(key = "topbar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DtsBackBtn(onBack)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(0.8.dp, D2Border.copy(0.5f), CircleShape)
                        ) {
                            androidx.compose.foundation.Image(
                                painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_dare_share),
                                contentDescription = null,
                                modifier     = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        Text(
                            "Dare to Share",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = D2TxtP, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp
                            )
                        )
                        Text(
                            "Anonymous · Honest · Safe",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = D2TxtS, fontSize = 10.sp
                            )
                        )
                    }
                    Spacer(Modifier.size(38.dp))
                }
                HorizontalDivider(color = D2Border, thickness = 0.5.dp)
            }

            // ── PRE-SUBMIT: Prompt picker ─────────────────────────────────
            if (!hasSubmitted) {
                item(key = "pick_label") {
                    Spacer(Modifier.height(14.dp))
                    DtsSectionLabel("PICK YOUR PROMPT", Icons.Rounded.AutoAwesome)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "All answers are shown anonymously to the group",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = D2TxtT, fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }

                item(key = "prompts_list") {
                    Column(
                        modifier            = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DARE_PROMPTS.forEach { prompt ->
                            val isSelected = selectedPrompt == prompt
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) D2Accent.copy(0.10f) else D2Card
                                    )
                                    .border(
                                        if (isSelected) 1.dp else 0.7.dp,
                                        if (isSelected) D2Accent.copy(0.5f) else D2Border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedPrompt =
                                            if (selectedPrompt == prompt) null else prompt
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                // Bullet / check
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) D2Accent else D2Elev
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) D2Accent else D2Border,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.Check, null,
                                            tint     = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                                Text(
                                    prompt,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color      = if (isSelected) D2TxtP else D2TxtS,
                                        fontSize   = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                        lineHeight = 18.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Answer input — only after a prompt is selected
                item(key = "answer_input") {
                    AnimatedVisibility(
                        visible = selectedPrompt != null,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = D2Border, thickness = 0.5.dp)
                            Spacer(Modifier.height(4.dp))
                            DtsSectionLabel("YOUR ANSWER", Icons.Rounded.Edit)

                            // Prompt recap chip
                            selectedPrompt?.let { p ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(D2Accent.copy(0.08f))
                                        .border(0.7.dp, D2Accent.copy(0.25f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                ) {
                                    Text(
                                        "\"$p\"",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color      = D2Accent.copy(0.85f),
                                            fontSize   = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 17.sp
                                        )
                                    )
                                }
                            }

                            // Text field
                            OutlinedTextField(
                                value         = answerText,
                                onValueChange = { if (it.length <= 300) answerText = it },
                                placeholder   = {
                                    Text(
                                        "Write honestly — no one will know it's you…",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = D2TxtT, fontSize = 13.sp
                                        )
                                    )
                                },
                                minLines  = 3,
                                maxLines  = 6,
                                shape     = RoundedCornerShape(12.dp),
                                colors    = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor      = D2Accent.copy(0.5f),
                                    unfocusedBorderColor    = D2Border,
                                    focusedTextColor        = D2TxtP,
                                    unfocusedTextColor      = D2TxtP,
                                    focusedContainerColor   = D2Card,
                                    unfocusedContainerColor = D2Card,
                                    cursorColor             = D2Accent
                                ),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = D2TxtP, fontSize = 13.sp, lineHeight = 19.sp
                                ),
                                trailingIcon = {
                                    Text(
                                        "${answerText.length}/300",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = D2TxtT, fontSize = 9.sp
                                        ),
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Error
                            AnimatedVisibility(visible = submitError.isNotBlank()) {
                                Text(
                                    submitError,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFE57373), fontSize = 11.sp
                                    )
                                )
                            }

                            // Submit button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(D2Elev)
                                    .border(0.8.dp, D2BorderA, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isSubmitting) { submit() }
                                    .padding(vertical = 15.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(18.dp),
                                        color       = D2Accent,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Send, null,
                                            tint     = D2TxtP,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "Share Anonymously",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color      = D2TxtP,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize   = 13.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── POST-SUBMIT: Success banner + own card ─────────────────────
            if (hasSubmitted) {
                item(key = "success") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Green banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(D2GreenDim.copy(0.4f))
                                .border(0.8.dp, D2Green.copy(0.30f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle, null,
                                tint     = D2Green,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "You shared! No one knows it was you.",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = D2Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                                    )
                                )
                                Text(
                                    "Waiting to see what others reveal…",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = D2TxtS, fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        // Own card (labelled "You" but prompt + answer shown)
                        myAnswer?.let { mine ->
                            DtsSectionLabel("YOUR RESPONSE", Icons.Rounded.Person)
                            DareCard(entry = mine, isMe = true)
                        }
                    }
                }
            }

            // ── Circle feed section ────────────────────────────────────────
            item(key = "feed_header") {
                Spacer(Modifier.height(if (hasSubmitted) 4.dp else 16.dp))
                HorizontalDivider(color = D2Border, thickness = 0.5.dp)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DtsSectionLabel(
                        if (othersAnswers.isEmpty()) "CIRCLE'S ANSWERS"
                        else "CIRCLE'S ANSWERS (${othersAnswers.size})",
                        Icons.Rounded.People
                    )
                    Text(
                        "Anonymous",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = D2TxtT, fontSize = 9.sp
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── Empty ──────────────────────────────────────────────────────
            if (othersAnswers.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.HourglassTop, null,
                                tint     = D2TxtT,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                if (hasSubmitted)
                                    "Waiting for others to share…"
                                else
                                    "Pick a prompt above and share first",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = D2TxtS, fontSize = 13.sp, textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            }

            // ── Others' cards (anonymous) ──────────────────────────────────
            items(othersAnswers, key = { "dare_${it.uid}" }) { entry ->
                DareCard(entry = entry, isMe = false)
                Spacer(Modifier.height(8.dp))
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dare card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DareCard(entry: DareAnswer, isMe: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isMe) D2Accent.copy(0.07f) else D2Card)
            .border(
                0.7.dp,
                if (isMe) D2Accent.copy(0.30f) else D2Border,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header: time + anonymous badge
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(D2Elev)
                        .border(0.7.dp, D2BorderA, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Person, null,
                        tint     = if (isMe) D2Accent else D2TxtT,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    if (isMe) "You" else "Anonymous",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = if (isMe) D2Accent else D2TxtS,
                        fontSize   = 12.sp,
                        fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
            Text(
                relativeTime(entry.timestamp),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = D2TxtT, fontSize = 9.sp
                )
            )
        }

        // Prompt chip
        if (entry.promptText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(D2Elev)
                    .border(0.5.dp, D2Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    entry.promptText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = D2TxtS,
                        fontSize   = 10.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Answer
        HorizontalDivider(color = D2Border, thickness = 0.4.dp)
        Text(
            entry.answer,
            style = MaterialTheme.typography.bodySmall.copy(
                color      = D2TxtP,
                fontSize   = 13.sp,
                lineHeight = 20.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DtsBackBtn(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(D2Card)
            .border(0.7.dp, D2Border, CircleShape)
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.ArrowBackIosNew, null,
            tint     = D2TxtP.copy(0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DtsSectionLabel(
    text : String,
    icon : androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier              = Modifier.padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, tint = D2TxtT, modifier = Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                color         = D2TxtT,
                fontSize      = 9.sp,
                letterSpacing = 1.4.sp,
                fontWeight    = FontWeight.SemiBold
            )
        )
    }
}

private fun relativeTime(ts: Long): String {
    if (ts == 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L     -> "just now"
        diff < 3_600_000L  -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else               -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts))
    }
}
