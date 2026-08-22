@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.ns.findyourintroverfriend.feed

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.ns.findyourintroverfriend.util.ContentModerator
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.VeilThemeManager

// ── Dark-mode state ───────────────────────────────────────────────────────────
private var _skDark by mutableStateOf(VeilThemeManager.initialResolvedDark)

// ── Design tokens ─────────────────────────────────────────────────────────────
private val SKBg       get() = if (_skDark) Color(0xFF0E0E14) else Color(0xFFF7F7F9)
private val SKCard     get() = if (_skDark) Color(0xFF18181F) else Color(0xFFFFFFFF)
private val SKBlack    get() = if (_skDark) Color(0xFFEEEEF8) else Color(0xFF111111)
private val SKGray     get() = if (_skDark) Color(0xFF8888A0) else Color(0xFF888888)
private val SKBorder   get() = if (_skDark) Color(0xFF383848) else Color(0xFFCCCCDD)  // stronger, always visible
private val SKSubtle   get() = if (_skDark) Color(0xFF252532) else Color(0xFFF2F2F5)
private val SKAgree          = Color(0xFF22C55E)
private val SKNope           = Color(0xFFEF4444)

private fun sparkAccent(type: String) = when (type) {
    "hot_take"          -> Color(0xFFEA580C)
    "confession"        -> Color(0xFF8B5CF6)
    "unpopular_opinion" -> Color(0xFF475569)
    "just_wondering"    -> Color(0xFF0891B2)
    else                -> Color(0xFF2563EB)
}

private fun sparkLabel(type: String) = when (type) {
    "hot_take"          -> "Hot Take"
    "confession"        -> "Confession"
    "unpopular_opinion" -> "Unpopular Opinion"
    "just_wondering"    -> "Just Wondering"
    else                -> "Question"
}

private fun skTimeAgo(ms: Long): String {
    if (ms == 0L) return ""
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000      -> "just now"
        d < 3_600_000   -> "${d / 60_000}m ago"
        d < 86_400_000  -> "${d / 3_600_000}h ago"
        d < 604_800_000 -> "${d / 86_400_000}d ago"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            .format(java.util.Date(ms))
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun VeilSparksScreen(
    vm              : VeilSparksViewModel    = viewModel(),
    listState       : LazyListState          = rememberLazyListState(),
    triggerCreate   : Boolean                = false,
    onCreateHandled : () -> Unit             = {}
) {
    val sparks    by vm.sparks.collectAsStateWithLifecycle()
    val loading   by vm.loading.collectAsStateWithLifecycle()
    val sortMode  by vm.sortMode.collectAsStateWithLifecycle()
    val postError by vm.postError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(postError) {
        if (!postError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(postError!!)
            vm.clearPostError()
        }
    }

    var expandedId  by remember { mutableStateOf<String?>(null) }
    var showPostDlg by remember { mutableStateOf(false) }

    LaunchedEffect(triggerCreate) {
        if (triggerCreate) { showPostDlg = true; onCreateHandled() }
    }

    val _veilIsDark = LocalVeilIsDark.current
    SideEffect { _skDark = _veilIsDark }

    // Collapse local sort header on scroll (tab bar collapse handled by parent)
    val headerCollapsed = listState.firstVisibleItemIndex > 1 ||
                         (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 20)

    Box(Modifier.fillMaxSize().background(SKBg)) {

        Column(Modifier.fillMaxSize()) {

            // ── Collapsing sort header ────────────────────────────────────────
            AnimatedVisibility(
                visible = !headerCollapsed,
                enter   = expandVertically(tween(180)) + fadeIn(tween(150)),
                exit    = shrinkVertically(tween(160)) + fadeOut(tween(120))
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(SKCard)
                            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Sort by",
                            fontSize   = 12.sp,
                            color      = SKGray,
                            fontWeight = FontWeight.Medium
                        )
                        // Segmented sort control — no emojis
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SKSubtle)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf("fresh" to "Fresh", "hot" to "Hot").forEach { (key, label) ->
                                val sel = sortMode == key
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) SKCard else Color.Transparent)
                                        .then(
                                            if (sel) Modifier.border(1.dp, SKBorder, RoundedCornerShape(6.dp))
                                            else Modifier
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication        = null
                                        ) { vm.setSort(key) }
                                        .padding(horizontal = 16.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        fontSize   = 13.sp,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                        color      = if (sel) SKBlack else SKGray
                                    )
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(SKBorder))
                }
            }

            // ── Feed ─────────────────────────────────────────────────────────
            when {
                loading && sparks.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(26.dp),
                            strokeWidth = 2.dp,
                            color       = SKGray
                        )
                    }
                }
                sparks.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment  = Alignment.CenterHorizontally,
                            verticalArrangement  = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(SKSubtle)
                                    .border(1.dp, SKBorder, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Lightbulb,
                                    null,
                                    tint     = SKGray,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                "No sparks yet",
                                fontSize   = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = SKBlack
                            )
                            Text(
                                "Be the first to share\nwhat's stirring in your mind",
                                fontSize  = 14.sp,
                                color     = SKGray,
                                textAlign = TextAlign.Center,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state          = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier       = Modifier.fillMaxSize()
                    ) {
                        // ─ Intro banner (always visible as first item) ───────────────────────
                        item(key = "innerly_intro") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 10.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, SKBorder, RoundedCornerShape(16.dp))
                                    .background(SKCard)
                                    .padding(horizontal = 18.dp, vertical = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1E1E2E)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🧠", fontSize = 18.sp)
                                    }
                                    Column {
                                        Text(
                                            "Welcome to Innerly",
                                            fontSize   = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = SKBlack
                                        )
                                        Text(
                                            "Your inner thoughts, anonymously",
                                            fontSize = 11.sp,
                                            color    = SKGray
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Innerly is a safe space to share what's quietly on your mind, questions, hot takes, confessions, and thoughts you wouldn't say out loud. Post anonymously. React honestly. Connect through what you actually feel.",
                                    fontSize   = 13.sp,
                                    color      = SKGray,
                                    lineHeight = 20.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("🔥 Hot Take", "🤔 Question", "🤫 Confession").forEach { tag ->
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SKSubtle)
                                                .border(1.dp, SKBorder, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(tag, fontSize = 11.sp, color = SKGray)
                                        }
                                    }
                                }
                            }
                        }

                        items(sparks, key = { it.sparkId }) { spark ->
                            SparkCard(
                                spark    = spark,
                                vm       = vm,
                                expanded = expandedId == spark.sparkId,
                                onToggle = {
                                    val newId = if (expandedId == spark.sparkId) null else spark.sparkId
                                    expandedId?.let { vm.stopListenAnswers(it) }
                                    expandedId = newId
                                    newId?.let { vm.listenAnswers(it) }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        item { Spacer(Modifier.height(100.dp)) }
                    }
                }
            }
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
            snackbar  = { data ->
                Snackbar(
                    snackbarData   = data,
                    containerColor = Color(0xFF2A0A0A),
                    contentColor   = Color(0xFFFFBBBB),
                    shape          = RoundedCornerShape(12.dp)
                )
            }
        )

        // ── FAB ───────────────────────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, SKBorder, RoundedCornerShape(14.dp))
                .background(SKBlack)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null
                ) { showPostDlg = true }
                .padding(horizontal = 20.dp, vertical = 13.dp)
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Edit, null, tint = SKBg, modifier = Modifier.size(15.dp))
                Text(
                    "New Spark",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = SKBg,
                    letterSpacing = 0.1.sp
                )
            }
        }
    }

    if (showPostDlg) {
        PostSparkDialog(vm = vm, onDismiss = { showPostDlg = false })
    }
}

// ── Spark Card ────────────────────────────────────────────────────────────────
@Composable
private fun SparkCard(
    spark    : VeilSpark,
    vm       : VeilSparksViewModel,
    expanded : Boolean,
    onToggle : () -> Unit
) {
    val answersMap by vm.answers.collectAsStateWithLifecycle()
    val sparkAns   = answersMap[spark.sparkId] ?: emptyList()
    val scope      = rememberCoroutineScope()
    val accent     = sparkAccent(spark.type)

    var answerTxt by remember(spark.sparkId) { mutableStateOf("") }
    var showAll   by remember(spark.sparkId) { mutableStateOf(false) }
    var modWarn   by remember(spark.sparkId) { mutableStateOf<String?>(null) }

    var agreePressed by remember { mutableStateOf(false) }
    var nopePressed  by remember { mutableStateOf(false) }
    val agreeScale by animateFloatAsState(if (agreePressed) 0.84f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "as")
    val nopeScale  by animateFloatAsState(if (nopePressed)  0.84f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ns")
    LaunchedEffect(agreePressed) { if (agreePressed) { kotlinx.coroutines.delay(120); agreePressed = false } }
    LaunchedEffect(nopePressed)  { if (nopePressed)  { kotlinx.coroutines.delay(120); nopePressed  = false } }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, SKBorder, RoundedCornerShape(16.dp))
            .background(SKCard)
    ) {
        // ── Accent header strip ───────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.10f))
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Text(
                        sparkLabel(spark.type).uppercase(),
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = accent,
                        letterSpacing = 1.0.sp
                    )
                }
                Text(
                    skTimeAgo(spark.createdAt),
                    fontSize = 11.sp,
                    color    = SKGray
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(0.8.dp).background(accent.copy(0.15f)))

        // ── Content ───────────────────────────────────────────────────────
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                spark.content,
                fontSize   = 16.sp,
                color      = SKBlack,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (spark.isAnonymous) "anonymous" else spark.authorAlias,
                fontSize  = 11.sp,
                color     = SKGray,
                fontWeight = FontWeight.Medium
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(SKBorder))

        // ── Action bar ────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Agree button
            Row(
                Modifier
                    .scale(agreeScale)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (spark.myResonance)
                            Modifier
                                .background(SKAgree.copy(0.12f))
                                .border(1.dp, SKAgree.copy(0.3f), RoundedCornerShape(10.dp))
                        else
                            Modifier.background(SKSubtle)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { agreePressed = true; vm.resonate(spark.sparkId) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.ThumbUp,
                    null,
                    tint     = if (spark.myResonance) SKAgree else SKGray,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    if (spark.resonanceCount > 0) "${spark.resonanceCount}" else "Agree",
                    fontSize   = 13.sp,
                    fontWeight = if (spark.myResonance) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (spark.myResonance) SKAgree else SKGray
                )
            }

            Spacer(Modifier.width(8.dp))

            // Nope button
            Row(
                Modifier
                    .scale(nopeScale)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (spark.myDisagree)
                            Modifier
                                .background(SKNope.copy(0.12f))
                                .border(1.dp, SKNope.copy(0.3f), RoundedCornerShape(10.dp))
                        else
                            Modifier.background(SKSubtle)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { nopePressed = true; vm.disagree(spark.sparkId) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.ThumbDown,
                    null,
                    tint     = if (spark.myDisagree) SKNope else SKGray,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    if (spark.disagreeCount > 0) "${spark.disagreeCount}" else "Nope",
                    fontSize   = 13.sp,
                    fontWeight = if (spark.myDisagree) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (spark.myDisagree) SKNope else SKGray
                )
            }

            Spacer(Modifier.weight(1f))

            // Replies button
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (expanded)
                            Modifier
                                .background(SKSubtle)
                                .border(1.dp, SKBorder, RoundedCornerShape(10.dp))
                        else
                            Modifier.background(SKSubtle)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = onToggle
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (expanded) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline,
                    null,
                    tint     = if (expanded) SKBlack else SKGray,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    if (spark.answerCount > 0) "${spark.answerCount} replies" else "Reply",
                    fontSize   = 13.sp,
                    fontWeight = if (expanded) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (expanded) SKBlack else SKGray
                )
            }
        }

        // ── Expanded replies ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(spring(0.88f)) + fadeIn(tween(180)),
            exit    = shrinkVertically(tween(160))    + fadeOut(tween(130))
        ) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(SKBorder))

                // Existing replies
                if (sparkAns.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            "No replies yet — share your perspective",
                            fontSize = 13.sp,
                            color    = SKGray
                        )
                    }
                } else {
                    val visible = if (showAll) sparkAns else sparkAns.take(3)
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        visible.forEach { ans ->
                            ReplyItem(
                                ans        = ans,
                                onUpvote   = { vm.vote(spark.sparkId, ans.answerId, 1) },
                                onDownvote = { vm.vote(spark.sparkId, ans.answerId, -1) }
                            )
                        }
                        if (!showAll && sparkAns.size > 3) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { showAll = true }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = SKGray, modifier = Modifier.size(16.dp))
                                Text(
                                    "${sparkAns.size - 3} more replies",
                                    fontSize = 13.sp,
                                    color    = SKGray
                                )
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(SKBorder))

                // Mod warning
                AnimatedVisibility(visible = modWarn != null) {
                    modWarn?.let { w ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(w, fontSize = 12.sp, color = Color(0xFF92400E), lineHeight = 17.sp) }
                    }
                }

                // Reply input row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BasicTextField(
                        value         = answerTxt,
                        onValueChange = {
                            if (it.length <= 240) {
                                answerTxt = it
                                if (modWarn != null) modWarn = null
                            }
                        },
                        modifier      = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SKBg)
                            .border(1.dp, SKBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        textStyle     = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            color    = SKBlack
                        ),
                        cursorBrush   = SolidColor(SKBlack),
                        singleLine    = false,
                        maxLines      = 3,
                        decorationBox = { inner ->
                            if (answerTxt.isEmpty()) {
                                Text("Add your perspective...", fontSize = 13.sp, color = SKGray)
                            }
                            inner()
                        }
                    )
                    AnimatedVisibility(visible = answerTxt.isNotBlank()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SKBlack)
                                .border(1.dp, SKBorder, RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) {
                                    val res = ContentModerator.check(answerTxt)
                                    if (!res.isSafe) { modWarn = res.warningMessage; return@clickable }
                                    scope.launch {
                                        vm.postAnswer(spark.sparkId, answerTxt.trim())
                                        answerTxt = ""
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            Text(
                                "Post",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = SKBg
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Reply Item ─────────────────────────────────────────────────────────────────
@Composable
private fun ReplyItem(
    ans        : SparkAnswer,
    onUpvote   : () -> Unit = {},
    onDownvote : () -> Unit = {}
) {
    val upColor   = Color(0xFFEA580C)
    val downColor = Color(0xFF6366F1)
    val isUp      = ans.myVote == 1
    val isDown    = ans.myVote == -1

    var upPressed   by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    val upScale   by animateFloatAsState(if (upPressed)   0.78f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "us")
    val downScale by animateFloatAsState(if (downPressed) 0.78f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ds")
    LaunchedEffect(upPressed)   { if (upPressed)   { kotlinx.coroutines.delay(110); upPressed   = false } }
    LaunchedEffect(downPressed) { if (downPressed) { kotlinx.coroutines.delay(110); downPressed = false } }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, SKBorder, RoundedCornerShape(10.dp))
            .background(SKBg)
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        // Author + time
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                ans.authorAlias.ifBlank { "anonymous" },
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = SKGray
            )
            Text(skTimeAgo(ans.createdAt), fontSize = 10.sp, color = SKGray)
        }
        Spacer(Modifier.height(5.dp))

        // Content
        Text(
            ans.content,
            fontSize   = 14.sp,
            color      = SKBlack,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(8.dp))

        // Vote row
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                Modifier
                    .scale(upScale)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isUp) upColor.copy(0.12f) else SKSubtle)
                    .border(1.dp, if (isUp) upColor.copy(0.3f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { upPressed = true; onUpvote() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        null,
                        tint     = if (isUp) upColor else SKGray,
                        modifier = Modifier.size(16.dp)
                    )
                    if (ans.score > 0 || isUp) {
                        Text(
                            "+${maxOf(ans.score, 0)}",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isUp) upColor else SKGray
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            Box(
                Modifier
                    .scale(downScale)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isDown) downColor.copy(0.12f) else SKSubtle)
                    .border(1.dp, if (isDown) downColor.copy(0.3f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { downPressed = true; onDownvote() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    null,
                    tint     = if (isDown) downColor else SKGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Post Spark Dialog ─────────────────────────────────────────────────────────
@Composable
private fun PostSparkDialog(
    vm       : VeilSparksViewModel,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf("question") }
    var text         by remember { mutableStateOf("") }
    var isAnon       by remember { mutableStateOf(true) }
    var posting      by remember { mutableStateOf(false) }
    var modWarning   by remember { mutableStateOf<String?>(null) }
    val scope        = rememberCoroutineScope()
    val accent       = sparkAccent(selectedType)

    val types = listOf(
        "question"          to "Question",
        "hot_take"          to "Hot Take",
        "confession"        to "Confession",
        "unpopular_opinion" to "Unpopular",
        "just_wondering"    to "Wondering"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.5f))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(SKCard)
                    .border(
                        width = 1.dp,
                        color = SKBorder,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .navigationBarsPadding()
            ) {
                // Handle bar
                Box(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    Alignment.Center
                ) {
                    Box(
                        Modifier.width(36.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SKBorder)
                    )
                }

                // Accent top strip (changes with type)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(accent)
                )

                // Title + type + close
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "New Spark",
                            fontSize   = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color      = SKBlack
                        )
                        Text(
                            sparkLabel(selectedType),
                            fontSize = 12.sp,
                            color    = accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SKSubtle)
                            .border(1.dp, SKBorder, RoundedCornerShape(8.dp))
                            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = SKGray, modifier = Modifier.size(15.dp))
                    }
                }

                // Type chips
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.padding(bottom = 14.dp)
                ) {
                    items(types) { (key, label) ->
                        val sel    = selectedType == key
                        val acc    = sparkAccent(key)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) acc.copy(0.12f) else SKSubtle)
                                .border(
                                    1.dp,
                                    if (sel) acc.copy(0.5f) else SKBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) { selectedType = key }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                label,
                                fontSize   = 13.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (sel) acc else SKGray
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(SKBorder))

                // Text input
                BasicTextField(
                    value         = text,
                    onValueChange = {
                        if (it.length <= 280) {
                            text = it
                            if (modWarning != null) modWarning = null
                        }
                    },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    textStyle     = LocalTextStyle.current.copy(
                        fontSize   = 15.sp,
                        color      = SKBlack,
                        lineHeight = 24.sp
                    ),
                    cursorBrush   = SolidColor(SKBlack),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                "What's stirring in your mind...",
                                fontSize   = 15.sp,
                                color      = SKGray,
                                lineHeight = 24.sp
                            )
                        }
                        inner()
                    }
                )

                // Mod warning
                AnimatedVisibility(visible = modWarning != null) {
                    modWarning?.let { w ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(w, fontSize = 12.sp, color = Color(0xFF92400E), lineHeight = 17.sp) }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(SKBorder))

                // Footer
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Anon toggle
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null
                            ) { isAnon = !isAnon }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isAnon) SKBlack else Color.Transparent)
                                .border(1.dp, if (isAnon) SKBlack else SKBorder, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAnon) Icon(Icons.Rounded.Check, null, tint = SKBg, modifier = Modifier.size(11.dp))
                        }
                        Text("Post anonymously", fontSize = 13.sp, color = SKGray)
                    }

                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "${280 - text.length}",
                            fontSize = 12.sp,
                            color    = if (text.length > 260) SKNope else SKGray
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (text.isNotBlank() && !posting) SKBlack
                                    else SKSubtle
                                )
                                .border(
                                    1.dp,
                                    if (text.isNotBlank() && !posting) Color.Transparent else SKBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(
                                    enabled           = text.isNotBlank() && !posting,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) {
                                    val result = ContentModerator.check(text)
                                    if (!result.isSafe) {
                                        modWarning = result.warningMessage
                                        return@clickable
                                    }
                                    scope.launch {
                                        posting = true
                                        vm.postSpark(text.trim(), selectedType, isAnon) {
                                            onDismiss()
                                        }
                                    }
                                }
                                .padding(horizontal = 22.dp, vertical = 13.dp)
                        ) {
                            Text(
                                if (posting) "Posting..." else "Spark ✦",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = if (text.isNotBlank() && !posting) SKBg else SKGray
                            )
                        }
                    }
                }
            }
        }
    }
}
