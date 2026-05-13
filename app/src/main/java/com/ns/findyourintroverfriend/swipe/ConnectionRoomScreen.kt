package com.ns.findyourintroverfriend.swipe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.billing.CookieViewModel
import com.ns.findyourintroverfriend.util.VeilSafetyUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset

// ── Instagram-style palette (black & white, no purple) ────────────────────────
private val IGBg         = Color(0xFFFFFFFF)   // pure white background
private val IGSurface    = Color(0xFFFFFFFF)   // header / input bg
private val IGDivider    = Color(0xFFEFEFEF)   // subtle divider
private val IGSentBg     = Color(0xFF000000)   // sent bubble — black
private val IGSentText   = Color(0xFFFFFFFF)   // text on sent bubble
private val IGRecvBg     = Color(0xFFF0F0F0)   // received bubble — light gray
private val IGRecvText   = Color(0xFF111111)   // text on received bubble
private val IGPrimary    = Color(0xFF000000)   // headers, icons
private val IGSecondary  = Color(0xFF737373)   // subtitles, placeholders
private val IGRed        = Color(0xFFED4956)   // report accent
private val IGRedSoft    = Color(0xFFFFF0F1)

@Composable
fun ConnectionRoomScreen(
    matchId  : String,
    vm       : SwipeViewModel = viewModel(),
    onBack   : () -> Unit,
    onOpenListeningRoom: (matchId: String, partnerUid: String) -> Unit = { _, _ -> }
) {
    val db      = FirebaseFirestore.getInstance()
    val uid     = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    val cookieVm: CookieViewModel = viewModel()
    val cookieBalance by cookieVm.cookieBalance.collectAsStateWithLifecycle()
    val isSpending    by cookieVm.isSpending.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    var match             by remember { mutableStateOf<SwipeMatch?>(null) }
    var messages          by remember { mutableStateOf<List<SwipeMessage>>(emptyList()) }
    var messageInput      by remember { mutableStateOf("") }
    var loading           by remember { mutableStateOf(true) }
    var showStickerPicker    by remember { mutableStateOf(false) }
    var showBuyDialog         by remember { mutableStateOf(false) }
    var pack1Owned            by remember { mutableStateOf(false) }
    var showCookieGiftDialog  by remember { mutableStateOf(false) }
    var cookieGiftAmount      by remember { mutableStateOf("") }
    var showFlowerBurst       by remember { mutableStateOf(false) }
    var lastSeenBurstMsgId    by remember { mutableStateOf("") }
    var openedGiftKeys        by remember { mutableStateOf(setOf<Long>()) }  // gift wraps tapped this session
    var showGesturePanel      by remember { mutableStateOf(false) }
    var showVoiceRecorder     by remember { mutableStateOf(false) }
    var showDrawingCanvas     by remember { mutableStateOf(false) }

    // Reply-to state
    var replyToMsg            by remember { mutableStateOf<SwipeMessage?>(null) }
    var longPressedMsg        by remember { mutableStateOf<SwipeMessage?>(null) }

    // Partner presence
    var partnerPresenceStatus by remember { mutableStateOf("") } // "active" | "gone"

    val prefKey            = "chat_rules_seen_$matchId"
    val prefs              = context.getSharedPreferences("veil_safety", android.content.Context.MODE_PRIVATE)
    var showFirstTimeRules by remember { mutableStateOf(!prefs.getBoolean(prefKey, false)) }
    var showRulesInfo      by remember { mutableStateOf(false) }
    var showReportDialog   by remember { mutableStateOf(false) }
    var reportAnimPhase    by remember { mutableIntStateOf(0) }

    val myAlias = vm.myProfile.collectAsStateWithLifecycle().value?.alias ?: ""

    // Load sticker pack ownership once
    LaunchedEffect(uid) {
        if (uid.isBlank()) return@LaunchedEffect
        try {
            val doc = db.collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val owned = (doc.get("ownedStickerPacks") as? List<String>) ?: emptyList()
            pack1Owned = "pack1" in owned
        } catch (_: Exception) {}
    }

    // Credit incoming cookie gifts to this receiver
    val giftPrefs = remember { context.getSharedPreferences("veil_gifts_$matchId", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(messages) {
        if (uid.isBlank()) return@LaunchedEffect
        messages.filter { it.text.startsWith("gift_cookies_") && it.senderUid != uid }.forEach { msg ->
            // Use senderUid+sentAt as key — avoids messageId deserialization issues
            val giftKey = "${msg.senderUid}_${msg.sentAt}"
            if (!giftPrefs.getBoolean(giftKey, false)) {
                val amount = msg.text.removePrefix("gift_cookies_").toIntOrNull() ?: 0
                if (amount > 0) {
                    try {
                        db.collection("users").document(uid)
                            .update(mapOf(
                                "cookieBalance" to com.google.firebase.firestore.FieldValue.increment(amount.toLong()),
                                "cookiesEarned" to com.google.firebase.firestore.FieldValue.increment(amount.toLong())
                            )).await()
                        giftPrefs.edit().putBoolean(giftKey, true).apply()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Gift wrap bubbles in chat trigger the burst on tap — no auto-trigger.
    // lastSeenBurstMsgId still used so sender’s own sent burst is trackable.

    // ── Write own presence; read partner presence ──────────────────────────────
    LaunchedEffect(uid) {
        if (uid.isBlank()) return@LaunchedEffect
        // Write "I am active"
        try {
            db.collection("presence").document(uid)
                .set(mapOf("lastSeen" to System.currentTimeMillis(), "online" to true)).await()
        } catch (_: Exception) {}
    }

    LaunchedEffect(matchId) {
        try {
            match = db.collection("matches").document(matchId).get().await()
                .toObject(SwipeMatch::class.java)
        } catch (_: Exception) {}
        loading = false
    }

    LaunchedEffect(matchId) {
        db.collection("matches").document(matchId)
            .addSnapshotListener { snap, _ -> snap?.toObject(SwipeMatch::class.java)?.let { match = it } }
    }

    LaunchedEffect(matchId) {
        db.collection("matches").document(matchId).collection("messages")
            .orderBy("sentAt").addSnapshotListener { snap, _ ->
                snap?.let { messages = it.toObjects(SwipeMessage::class.java) }
            }
    }

    // ── Real-time partner presence listener ────────────────────────────────────
    LaunchedEffect(match) {
        val m2 = match ?: return@LaunchedEffect
        val partnerUid = if (m2.userA == uid) m2.userB else m2.userA
        if (partnerUid.isBlank()) return@LaunchedEffect
        db.collection("presence").document(partnerUid)
            .addSnapshotListener { snap, _ ->
                val lastSeen = snap?.getLong("lastSeen") ?: 0L
                val diffMs   = System.currentTimeMillis() - lastSeen
                partnerPresenceStatus = if (diffMs < 10 * 60 * 1000L) "active" else "gone"
            }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showFirstTimeRules) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(16.dp),
            containerColor = IGSurface,
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF5F5F5)), Alignment.Center) {
                        Icon(Icons.Rounded.Shield, null, tint = IGPrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Before You Chat", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = IGPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "Never ask for or send money",
                        "No explicit or vulgar content",
                        "No threats or harassment",
                        "Be your real self — no impersonation",
                        "Don't share contact info to bypass Veil"
                    ).forEach { rule ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(IGPrimary).align(Alignment.CenterVertically))
                            Text(rule, fontSize = 13.sp, color = IGSecondary, lineHeight = 19.sp)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "🚨 Violations result in an immediate permanent ban.",
                        fontSize = 11.sp, color = IGRed, lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { prefs.edit().putBoolean(prefKey, true).apply(); showFirstTimeRules = false },
                    colors = ButtonDefaults.buttonColors(containerColor = IGPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("I Understand", fontWeight = FontWeight.SemiBold) }
            }
        )
    }

    if (showRulesInfo) {
        AlertDialog(
            onDismissRequest = { showRulesInfo = false },
            shape = RoundedCornerShape(16.dp), containerColor = IGSurface,
            title = { Text("Chat Guidelines", fontWeight = FontWeight.Bold, color = IGPrimary) },
            text  = { Text(VeilSafetyUtil.CHAT_RULES, fontSize = 13.sp, color = IGSecondary, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { showRulesInfo = false }) {
                    Text("Got it", fontWeight = FontWeight.SemiBold, color = IGPrimary)
                }
            }
        )
    }

    if (showReportDialog) {
        val m = match
        val reportAlias = if (m?.userA == uid) m?.aliasB ?: "User" else m?.aliasA ?: "User"
        ChatReportDialog(
            partnerAlias = reportAlias,
            onDismiss = { showReportDialog = false },
            onSubmit  = { reason, note ->
                showReportDialog = false; reportAnimPhase = 1
                scope.launch {
                    try {
                        val m2 = match ?: return@launch
                        val partnerId = if (m2.userA == uid) m2.userB else m2.userA
                        db.collection("veilReports").add(mapOf(
                            "type" to "chat", "matchId" to matchId,
                            "reportedUid" to partnerId, "reportedBy" to uid,
                            "reason" to reason, "note" to note,
                            "reportedAt" to System.currentTimeMillis(), "status" to "pending"
                        )).await()
                        db.collection("matches").document(matchId)
                            .update("flaggedAt", System.currentTimeMillis(), "flaggedBy", uid).await()
                    } catch (_: Exception) {}
                }
            }
        )
    }

    // ── Main UI ───────────────────────────────────────────────────────────────
    Box(
        Modifier.fillMaxSize().background(IGBg).systemBarsPadding().imePadding()
    ) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IGPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            return@Box
        }

        val m = match ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Match not found.", color = IGSecondary, fontSize = 14.sp)
            }
            return@Box
        }

        val isUserA      = m.userA == uid
        val partnerAlias = if (isUserA) m.aliasB else m.aliasA
        val partnerPhoto = if (isUserA) m.photoB else m.photoA

        Column(Modifier.fillMaxSize()) {

            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IGSurface)
                    .shadow(elevation = 2.dp, ambientColor = Color.Black.copy(0.06f), spotColor = Color.Black.copy(0.06f))
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = IGPrimary, modifier = Modifier.size(22.dp))
                }

                // Avatar with active dot
                Box(Modifier.size(44.dp)) {
                    AsyncImage(
                        model = partnerPhoto, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                            .border(1.dp, IGDivider, CircleShape)
                    )
                    if (partnerPresenceStatus == "active") {
                        Box(
                            Modifier.size(11.dp).align(Alignment.BottomEnd)
                                .clip(CircleShape).background(Color(0xFF4CAF50))
                                .border(2.dp, IGSurface, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Name + presence
                Column(Modifier.weight(1f)) {
                    Text(
                        partnerAlias,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = IGPrimary,
                        maxLines = 1
                    )
                    when (partnerPresenceStatus) {
                        "active" -> Text("Active now", fontSize = 11.sp, color = Color(0xFF4CAF50))
                        "gone"   -> Text("Gone to meditate 🧘", fontSize = 11.sp, color = IGSecondary)
                        else     -> Text("Veil user", fontSize = 11.sp, color = IGSecondary)
                    }
                }

                // Info
                IconButton(onClick = { showRulesInfo = true }) {
                    Icon(Icons.Rounded.Info, null, tint = IGSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showReportDialog = true }) {
                    Icon(Icons.Rounded.Flag, null, tint = IGRed, modifier = Modifier.size(20.dp))
                }
            }

            val scrollState = rememberScrollState()
            LaunchedEffect(messages.size) { scrollState.animateScrollTo(scrollState.maxValue) }

               



            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (messages.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AsyncImage(
                            model = partnerPhoto, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(CircleShape).border(1.dp, IGDivider, CircleShape)
                        )
                        Text(partnerAlias, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IGPrimary)
                        Text("Connected on Veil", fontSize = 12.sp, color = IGSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("Say hello", fontSize = 13.sp, color = IGSecondary)
                    }
                }

                messages.forEach { msg ->
                    val mine = msg.senderUid == uid
                    val haptic = LocalHapticFeedback.current
                    var swipeOffsetX by remember(msg.sentAt) { mutableStateOf(0f) }
                    val swipeAnimX by animateFloatAsState(
                        targetValue = swipeOffsetX,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "swipe_${msg.sentAt}"
                    )
                    Row(
                        Modifier.fillMaxWidth()
                            .offset(x = swipeAnimX.dp)
                            .pointerInput(msg.sentAt) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (swipeOffsetX > 60f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            replyToMsg = msg
                                            longPressedMsg = null
                                            val prefix = "@${msg.senderAlias}\n"
                                            if (!messageInput.startsWith(prefix)) messageInput = prefix
                                        }
                                        swipeOffsetX = 0f
                                    },
                                    onDragCancel = { swipeOffsetX = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        val new = (swipeOffsetX + dragAmount * 0.4f).coerceIn(0f, 80f)
                                        swipeOffsetX = new
                                    }
                                )
                            },
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Receiver avatar (small, only for received)
                        if (!mine) {
                            AsyncImage(
                                model = partnerPhoto, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(26.dp).clip(CircleShape)
                                    .border(0.5.dp, IGDivider, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                        }

                        when {
                            // ─ Voice Note bubble ───────────────────────────────────────
                            msg.text.startsWith("voice_note::") -> {
                                val parts = msg.text.removePrefix("voice_note::").split("::")
                                val url   = parts.getOrNull(0) ?: ""
                                val dur   = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                if (url.isNotBlank()) VoiceNotePlayerBubble(url, dur, mine)
                            }
                            // ─ Drawing bubble ────────────────────────────────────────────
                            msg.text.startsWith("drawing::") -> {
                                val url = msg.text.removePrefix("drawing::")
                                if (url.isNotBlank()) DrawingBubble(url)
                            }
                            // ─ Cookie Gift bubble ───────────────────────────────────────────
                            msg.text.startsWith("gift_cookies_") -> {
                                val amount = msg.text.removePrefix("gift_cookies_").toIntOrNull() ?: 0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .widthIn(max = 240.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (mine) Color(0xFFFFF8E1) else Color(0xFFFFFDE7))
                                        .border(1.dp, Color(0xFFFFD54F).copy(0.5f), RoundedCornerShape(18.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFFD54F).copy(0.3f)),
                                        Alignment.Center) {
                                        Icon(Icons.Rounded.Favorite, null,
                                            tint = Color(0xFF7A5200), modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text(
                                            if (mine) "Gifted $amount cookies" else "Received $amount cookies",
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                            color = Color(0xFF7A5200)
                                        )
                                        Text(
                                            if (mine) "A sweet gesture" else "What a treat!",
                                            fontSize = 11.sp, color = Color(0xFFAA7020)
                                        )
                                    }
                                }
                            }
                            // ─ Flower Burst → Gift Wrap bubble (tap to reveal) ──────────
                            msg.text == "flower_burst" -> {
                                val isOpened  = msg.sentAt in openedGiftKeys
                                val inf       = rememberInfiniteTransition(label = "gift_${msg.sentAt}")
                                val glowState = inf.animateFloat(
                                    0.45f, 1f,
                                    infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                    "giftGlow_${msg.sentAt}"
                                )
                                val glow = glowState.value
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .widthIn(max = 230.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (isOpened) Color(0xFFF8E6FF)
                                            else Color(0xFF1C1916)
                                        )
                                        .border(
                                            width = if (isOpened) 1.dp else (glow + 0.5f).dp,
                                            color = if (isOpened) Color(0xFFF48FB1).copy(0.35f)
                                                    else Color(0xFFFFD54F).copy(glow),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .clickable(enabled = !isOpened) {
                                            openedGiftKeys = openedGiftKeys + msg.sentAt
                                            showFlowerBurst = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape)
                                            .background(
                                                if (isOpened) Color(0xFFF48FB1).copy(0.25f)
                                                else Color(0xFFFFD54F).copy(if (!isOpened) glow * 0.18f else 0.08f)
                                            ),
                                        Alignment.Center
                                    ) {
                                        Icon(
                                            if (isOpened) Icons.Rounded.AutoAwesome
                                            else Icons.Rounded.CardGiftcard,
                                            null,
                                            tint = if (isOpened) Color(0xFFAD1457) else Color(0xFFFFD54F),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            if (isOpened) {
                                                if (mine) "Flower Burst sent" else "Gift opened!"
                                            } else {
                                                if (mine) "Flower Burst" else "A gift for you"
                                            },
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                            color = if (isOpened) Color(0xFFAD1457) else Color.White
                                        )
                                        Text(
                                            if (isOpened) "5 cookies \u00b7 screen effect"
                                            else if (!mine) "Tap to reveal the surprise"
                                            else "5 cookies",
                                            fontSize = 11.sp,
                                            color = if (isOpened) Color(0xFFCE77A8).copy(0.8f)
                                                    else Color.White.copy(0.55f)
                                        )
                                    }
                                }
                            }
                            // ─ Sticker bubble ────────────────────────────────────────────
                            msg.text.startsWith("sticker_pack") -> {
                                val drawableId = remember(msg.text) {
                                    context.resources.getIdentifier(msg.text, "drawable", context.packageName)
                                }
                                if (drawableId != 0) {
                                    Image(
                                        painter = painterResource(drawableId),
                                        contentDescription = "Sticker",
                                        modifier = Modifier.size(120.dp).padding(2.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Box(Modifier.size(36.dp).clip(CircleShape).background(IGDivider),
                                        Alignment.Center) {
                                        Icon(Icons.Rounded.Collections, null, tint = IGSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            // ─ Normal text bubble ────────────────────────────────────────
                            else -> {
                                val showTime = longPressedMsg?.sentAt == msg.sentAt
                                Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                                    // Reply-to quote preview inside bubble
                                    if (msg.text.startsWith("@")) {
                                        val firstNewline = msg.text.indexOf('\n')
                                        val tagLine = if (firstNewline > 0) msg.text.substring(0, firstNewline) else ""
                                        if (tagLine.startsWith("@")) {
                                            Box(
                                                Modifier
                                                    .widthIn(max = 260.dp)
                                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                    .background(if (mine) Color(0xFF2A2A2A) else Color(0xFFD8D8D8))
                                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                                            ) {
                                                Text(tagLine, fontSize = 11.sp,
                                                    color = if (mine) Color(0xFFAAAAAA) else Color(0xFF555555),
                                                    fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                    Box(
                                        Modifier
                                            .widthIn(max = 260.dp)
                                            .clip(RoundedCornerShape(
                                                topStart    = if (!mine && msg.text.startsWith("@")) 0.dp else 18.dp,
                                                topEnd      = if (mine  && msg.text.startsWith("@")) 0.dp else 18.dp,
                                                bottomStart = if (mine) 18.dp else 4.dp,
                                                bottomEnd   = if (mine) 4.dp  else 18.dp
                                            ))
                                            .background(if (mine) IGSentBg else IGRecvBg)
                                            .combinedClickable(
                                                onClick      = { longPressedMsg = null },
                                                onLongClick  = {
                                                    longPressedMsg = if (longPressedMsg?.sentAt == msg.sentAt) null else msg
                                                }
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        val displayText = if (msg.text.startsWith("@")) {
                                            val nl = msg.text.indexOf('\n')
                                            if (nl > 0) msg.text.substring(nl + 1) else msg.text
                                        } else msg.text
                                        Text(displayText, fontSize = 14.sp,
                                            color = if (mine) IGSentText else IGRecvText, lineHeight = 20.sp)
                                    }
                                    // Timestamp + Reply shown on long press
                                    if (showTime) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
                                        ) {
                                            val fmt = remember { java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()) }
                                            Text(fmt.format(java.util.Date(msg.sentAt)), fontSize = 10.sp, color = IGSecondary)
                                            Text("·", fontSize = 10.sp, color = IGSecondary)
                                            Text(
                                                "Reply",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = IGPrimary,
                                                modifier = Modifier.clickable {
                                                    replyToMsg  = msg
                                                    longPressedMsg = null
                                                    val prefix = "@${msg.senderAlias}\n"
                                                    if (!messageInput.startsWith(prefix)) messageInput = prefix
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            // ── Sticker picker panel (WhatsApp-style, above input bar) ──────────
            val pack1Stickers = listOf(
                R.drawable.sticker_pack1_1,  R.drawable.sticker_pack1_2,  R.drawable.sticker_pack1_3,
                R.drawable.sticker_pack1_4,  R.drawable.sticker_pack1_5,  R.drawable.sticker_pack1_6,
                R.drawable.sticker_pack1_7,  R.drawable.sticker_pack1_8,  R.drawable.sticker_pack1_9,
                R.drawable.sticker_pack1_10
            )
            AnimatedVisibility(
                visible = showStickerPicker,
                enter = slideInVertically(tween(240)) { it } + fadeIn(tween(180)),
                exit  = slideOutVertically(tween(200)) { it } + fadeOut(tween(140))
            ) {
                Column(
                    Modifier.fillMaxWidth().height(380.dp)
                        .background(IGSurface)
                        .border(BorderStroke(0.5.dp, IGDivider))
                ) {
                    // Tab bar
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFFAFAFA))
                            .border(BorderStroke(0.5.dp, IGDivider))
                            .padding(horizontal = 12.dp)
                    ) {
                        Box(
                            Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                                .border(BorderStroke(2.dp, IGPrimary), RoundedCornerShape(2.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("Pack 1", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = IGPrimary) }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("Pack 2 · Soon", fontSize = 12.sp, color = IGSecondary) }
                    }
                    // Content area
                    if (pack1Owned) {
                        // 3 per row, scrollable — big enough to read sticker art
                        val stickerScrollState = rememberScrollState()
                        val rows = pack1Stickers.chunked(3)
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(stickerScrollState)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rows.forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    row.forEachIndexed { _, res ->
                                        val stickerIdx = pack1Stickers.indexOf(res)
                                        Box(
                                            Modifier
                                                .size(96.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFFF0F0F0))
                                                .clickable {
                                                    val key = "sticker_pack1_${stickerIdx + 1}"
                                                    showStickerPicker = false
                                                    scope.launch {
                                                        try {
                                                            db.collection("matches").document(matchId)
                                                                .collection("messages")
                                                                .add(SwipeMessage(
                                                                    messageId   = db.collection("_").document().id,
                                                                    senderUid   = uid,
                                                                    senderAlias = myAlias,
                                                                    text        = key,
                                                                    sentAt      = System.currentTimeMillis(),
                                                                    isSticker   = true
                                                                )).await()
                                                        } catch (_: Exception) {}
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painterResource(res), null,
                                                Modifier.size(82.dp).padding(4.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                    // Fill empty slots in last row so layout stays even
                                    repeat(3 - row.size) { Spacer(Modifier.size(96.dp)) }
                                }
                            }
                        }
                    } else {
                        // Locked — buy CTA
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.alpha(0.2f).padding(bottom = 10.dp)
                            ) {
                                pack1Stickers.take(4).forEach { res ->
                                    Image(painterResource(res), null,
                                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit)
                                }
                            }
                            Text("Veil Sticker Pack 1", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = IGPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text("10 stickers · One-time unlock", fontSize = 11.sp, color = IGSecondary)
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(IGPrimary)
                                    .clickable { showBuyDialog = true }
                                    .padding(horizontal = 20.dp, vertical = 9.dp)
                            ) {
                                Text("🍪", fontSize = 15.sp)
                                Text("Unlock for 50 Cookies", fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ── Reply-to banner ───────────────────────────────────────────────
            if (replyToMsg != null) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .border(BorderStroke(0.5.dp, IGDivider))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(28.dp).background(IGPrimary, RoundedCornerShape(2.dp)))
                        Column {
                            Text("Replying to @${replyToMsg!!.senderAlias}",
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IGPrimary)
                            Text(replyToMsg!!.text.take(40).replace('\n', ' '),
                                fontSize = 11.sp, color = IGSecondary, maxLines = 1)
                        }
                    }
                    IconButton(onClick = { replyToMsg = null; messageInput = "" }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, null, tint = IGSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Bottom Gesture Quick Strip (above input bar) ──────────────────
            AnimatedVisibility(
                visible = showGesturePanel,
                enter   = slideInVertically(tween(200)) { it } + fadeIn(tween(160)),
                exit    = slideOutVertically(tween(160)) { it } + fadeOut(tween(120))
            ) {
                val hapticB = LocalHapticFeedback.current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(IGSurface)
                        .border(BorderStroke(0.5.dp, IGDivider))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GesturePill(
                        emoji = "🍪", label = "Gift Cookies", price = "FREE",
                        canAfford = cookieBalance >= 1,
                        onClick = { hapticB.performHapticFeedback(HapticFeedbackType.LongPress); showGesturePanel = false; showCookieGiftDialog = true }
                    )
                    GesturePill(
                        emoji = "🌸", label = "Send Love Flowers", price = "5 🍪",
                        canAfford = cookieBalance >= 5 && !showFlowerBurst,
                        onClick = {
                            hapticB.performHapticFeedback(HapticFeedbackType.LongPress)
                            showGesturePanel = false
                            cookieVm.spendCookies(5, "Flower Burst") { ok ->
                                if (ok) {
                                    showFlowerBurst = true
                                    scope.launch {
                                        try {
                                            db.collection("matches").document(matchId)
                                                .collection("messages")
                                                .add(SwipeMessage(
                                                    messageId   = db.collection("_").document().id,
                                                    senderUid   = uid, senderAlias = myAlias,
                                                    text        = "flower_burst",
                                                    sentAt      = System.currentTimeMillis()
                                                )).await()
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    )
                    GesturePill(
                        emoji = "🎨", label = "Draw Together", price = "10 🍪",
                        canAfford = cookieBalance >= 10,
                        onClick = { hapticB.performHapticFeedback(HapticFeedbackType.LongPress); showGesturePanel = false; showDrawingCanvas = true }
                    )
                    GesturePill(
                        emoji = "🎙️", label = "Voice Note", price = "8 🍪",
                        canAfford = cookieBalance >= 8,
                        onClick = { hapticB.performHapticFeedback(HapticFeedbackType.LongPress); showGesturePanel = false; showVoiceRecorder = true }
                    )
                }
            }

            // ── Input Bar ─────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 4.dp, ambientColor = Color.Black.copy(0.08f), spotColor = Color.Black.copy(0.08f))
                    .background(IGSurface)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ── Gesture button — Gesture.png (R.drawable.gesture) ──────────
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (showGesturePanel) Color(0xFFFFE4EF) else Color.Transparent)
                        .border(
                            BorderStroke(
                                if (showGesturePanel) 1.5.dp else 0.dp,
                                if (showGesturePanel) Color(0xFFE91E63) else Color.Transparent
                            ),
                            CircleShape
                        )
                        .clickable { showGesturePanel = !showGesturePanel; if (showGesturePanel) keyboard?.hide() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.gesture),
                        contentDescription = "Love Gestures",
                        modifier = Modifier.size(26.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // ── Sticker button — sticker.png (R.drawable.sticker) ──────────
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (showStickerPicker) Color(0xFFF0F0F0) else Color.Transparent)
                        .clickable {
                            if (pack1Owned) {
                                showStickerPicker = !showStickerPicker
                                if (showStickerPicker) keyboard?.hide()
                            } else { showBuyDialog = true }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.sticker),
                        contentDescription = "Stickers",
                        modifier = Modifier.size(26.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // Text field
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                        .border(1.dp, IGDivider, RoundedCornerShape(24.dp))
                        .background(Color(0xFFF9F9F9))
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    BasicTextField(
                        value = messageInput,
                        onValueChange = { if (it.length <= 500) messageInput = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = IGPrimary),
                        decorationBox = { inner ->
                            if (messageInput.isEmpty()) Text("Message…", fontSize = 14.sp, color = IGSecondary)
                            inner()
                        }
                    )
                }

                // Send
                AnimatedVisibility(visible = messageInput.isNotBlank(), enter = scaleIn()+fadeIn(), exit = scaleOut()+fadeOut()) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(IGPrimary)
                            .clickable {
                                val text = messageInput.trim(); messageInput = ""; replyToMsg = null; longPressedMsg = null
                                scope.launch {
                                    try {
                                        db.collection("matches").document(matchId).collection("messages")
                                            .add(SwipeMessage(
                                                messageId=db.collection("_").document().id,
                                                senderUid=uid, senderAlias=myAlias,
                                                text=text, sentAt=System.currentTimeMillis()
                                            )).await()
                                        val partnerUid = if (isUserA) m.userB else m.userA
                                        com.ns.findyourintroverfriend.notifications.VeilNotifier
                                            .sendDmMessage(toUid=partnerUid, fromAlias=myAlias, matchId=matchId, messageText=text)
                                    } catch (_: Exception) {}
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                // Mic button removed per design update
            }

            // ── Sticker purchase dialog ────────────────────────────────────────
            if (showBuyDialog) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showBuyDialog = false }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(IGSurface)
                    ) {
                        Column(Modifier.fillMaxWidth()) {

                            // ── Dark header banner ────────────────────────────
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(IGPrimary)
                                    .padding(horizontal = 20.dp, vertical = 18.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Smiley logo
                                        val logoId = context.resources.getIdentifier(
                                            "logoForStickers", "drawable", context.packageName
                                        )
                                        Box(
                                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(0.10f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (logoId != 0) {
                                                Image(painterResource(logoId), null,
                                                    modifier = Modifier.size(30.dp),
                                                    contentScale = ContentScale.Fit)
                                            } else {
                                                Text("🎭", fontSize = 20.sp)
                                            }
                                        }
                                        Column {
                                            Text(
                                                "Veil Sticker Pack 1",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                "10 stickers · one-time unlock · yours forever",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(0.55f)
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Sticker grid preview ──────────────────────────
                            val previewRows = pack1Stickers.chunked(5)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                previewRows.forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        row.forEach { res ->
                                            Box(
                                                Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFF5F5F5)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    painterResource(res), null,
                                                    modifier = Modifier.size(44.dp).padding(2.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }
                                        // Fill empty slots so last row stays aligned
                                        repeat(5 - row.size) {
                                            Spacer(Modifier.size(52.dp))
                                        }
                                    }
                                }
                            }

                            // ── Divider ───────────────────────────────────────
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(IGDivider))

                            // ── Balance row ───────────────────────────────────
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Unlock price",
                                        fontSize = 11.sp, color = IGSecondary
                                    )
                                    Text(
                                        "50 🍪 cookies",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IGPrimary
                                    )
                                }
                                // Balance chip
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (cookieBalance >= 50) Color(0xFFE8F5E9) else Color(0xFFFFF0F1))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "Balance: $cookieBalance 🍪",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (cookieBalance >= 50) Color(0xFF2E7D32) else IGRed
                                    )
                                }
                            }

                            if (cookieBalance < 50) {
                                Text(
                                    "You need ${50 - cookieBalance} more cookies to unlock",
                                    fontSize = 11.sp,
                                    color = IGRed,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 8.dp)
                                )
                            }

                            // ── Divider ───────────────────────────────────────
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(IGDivider))

                            // ── CTA buttons ───────────────────────────────────
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (cookieBalance >= 50) {
                                    Button(
                                        onClick = {
                                            cookieVm.spendCookies(50, "Sticker Pack 1") { ok ->
                                                if (ok) {
                                                    scope.launch {
                                                        try {
                                                            db.collection("users").document(uid)
                                                                .update("ownedStickerPacks",
                                                                    com.google.firebase.firestore.FieldValue.arrayUnion("pack1")).await()
                                                            pack1Owned = true
                                                        } catch (_: Exception) {}
                                                    }
                                                    showBuyDialog = false
                                                }
                                            }
                                        },
                                        enabled = !isSpending,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IGPrimary),
                                        elevation = ButtonDefaults.buttonElevation(0.dp)
                                    ) {
                                        if (isSpending) {
                                            CircularProgressIndicator(
                                                Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(
                                                "Unlock Pack · 50 🍪",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { showBuyDialog = false },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IGPrimary),
                                        elevation = ButtonDefaults.buttonElevation(0.dp)
                                    ) {
                                        Text("Get More Cookies", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                                TextButton(
                                    onClick = { showBuyDialog = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Maybe later", color = IGSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Cookie Gift Dialog ────────────────────────────────────────────────
            if (showCookieGiftDialog) {
                CookieGiftDialog(
                    myBalance    = cookieBalance,
                    isSpending   = isSpending,
                    partnerAlias = partnerAlias,
                    onDismiss    = { showCookieGiftDialog = false },
                    onSend       = { amount ->
                        cookieVm.spendCookies(amount, "Cookie Gift") { ok ->
                            if (ok) {
                                scope.launch {
                                    try {
                                        db.collection("matches").document(matchId)
                                            .collection("messages")
                                            .add(SwipeMessage(
                                                messageId   = db.collection("_").document().id,
                                                senderUid   = uid,
                                                senderAlias = myAlias,
                                                text        = "gift_cookies_$amount",
                                                sentAt      = System.currentTimeMillis()
                                            )).await()
                                    } catch (_: Exception) {}
                                }
                                showCookieGiftDialog = false
                            }
                        }
                    }
                )
            }

        } // end Column

        // ── Report overlay ────────────────────────────────────────────────────
        if (reportAnimPhase > 0) {
            ReportAnimOverlay(
                phase = reportAnimPhase,
                onPhaseEnd = { p -> if (p == 1) reportAnimPhase = 2 else reportAnimPhase = 0 }
            )
        }

        // ── Flower Burst overlay (full-screen, shown on both sender & receiver) ──────
        if (showFlowerBurst) {
            FlowerBurstEffect(onDone = { showFlowerBurst = false })
        }

        // ── Voice Note overlay ──────────────────────────────────────────────────
        if (showVoiceRecorder) {
            VoiceNoteRecorderSheet(
                matchId        = matchId,
                uid            = uid,
                myAlias        = myAlias,
                cookieBalance  = cookieBalance,
                onSpendCookies = { amount, reason, cb -> cookieVm.spendCookies(amount, reason, cb) },
                onDismiss      = { showVoiceRecorder = false }
            )
        }

        // ── Drawing Canvas overlay ─────────────────────────────────────────────
        if (showDrawingCanvas) {
            DrawingCanvasScreen(
                matchId        = matchId,
                uid            = uid,
                myAlias        = myAlias,
                cookieBalance  = cookieBalance,
                onSpendCookies = { amount, reason, cb -> cookieVm.spendCookies(amount, reason, cb) },
                onDismiss      = { showDrawingCanvas = false }
            )
        }

    } // end Box
} // end ConnectionRoomScreen

// ─────────────────────────────────────────────────────────────────────────────
//  Report animation overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable private fun ReportAnimOverlay(phase: Int, onPhaseEnd: (Int) -> Unit) {
    val assetName = if (phase == 1) "afterBanAnimation.lottie" else "SuccessfullyReported.lottie"
    val label     = if (phase == 1) "Report filed" else "Report submitted!"
    val sublabel  = if (phase == 1) "Our team will review within 24 hours." else "Thank you for keeping Veil safe."
    key(phase) {
        val composition by rememberLottieComposition(LottieCompositionSpec.Asset(assetName))
        val progress    by animateLottieCompositionAsState(composition = composition, iterations = 1, isPlaying = true, restartOnPlay = false)
        LaunchedEffect(progress >= 1f, composition) {
            if (progress >= 1f && composition != null) { delay(400); onPhaseEnd(phase) }
        }
        Box(Modifier.fillMaxSize().background(Color(0xF5FFFFFF)), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(200.dp))
                Text(label,    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = IGPrimary)
                Text(sublabel, fontSize = 14.sp, color = IGSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chat Report Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable private fun ChatReportDialog(
    partnerAlias: String, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit
) {
    var selectedReason by remember { mutableStateOf("") }
    var note           by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
            Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(IGSurface)
            ) {
                // Header
                Box(
                    Modifier.fillMaxWidth().background(IGPrimary)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Flag, null, tint = IGRed, modifier = Modifier.size(18.dp))
                            Text("Report $partnerAlias", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                        Text("Select a reason below. Your report is anonymous.",
                            fontSize = 11.sp, color = Color.White.copy(0.55f),
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
                // Reason chips
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("What's happening?", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = IGSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    VeilSafetyUtil.CHAT_REPORT_REASONS.forEach { reason ->
                        val sel = selectedReason == reason
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) IGPrimary else Color(0xFFF5F5F5))
                                .clickable { selectedReason = reason }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(reason, fontSize = 13.sp,
                                color = if (sel) Color.White else IGPrimary,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                            if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                // Note field
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = note, onValueChange = { if (it.length <= 200) note = it },
                        placeholder = { Text(if (selectedReason.endsWith("below)")) "Describe what happened…" else "Additional details (optional)",
                            fontSize = 12.sp, color = IGSecondary) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IGPrimary, unfocusedBorderColor = IGDivider,
                            focusedTextColor = IGPrimary, unfocusedTextColor = IGPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⚠️ False reports may lead to account action.", fontSize = 10.sp, color = IGSecondary)
                        Text("${note.length}/200", fontSize = 10.sp, color = IGSecondary)
                    }
                }
                // Buttons
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, IGDivider)
                    ) { Text("Cancel", color = IGSecondary, fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { if (selectedReason.isNotBlank()) onSubmit(selectedReason, note) },
                        enabled = selectedReason.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IGRed, disabledContainerColor = IGSecondary.copy(0.25f))
                    ) { Text("Submit Report", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable private fun StatusPill(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (done) Color(0xFF4CAF50) else IGDivider))
        Text(label, fontSize = 12.sp, color = IGSecondary)
    }
}

// ─────────────────────────────────────────────────────
//  Gesture Pill — compact quick-access pill button
// ─────────────────────────────────────────────────────

@Composable private fun GesturePill(
    emoji     : String,
    label     : String,
    price     : String,
    canAfford : Boolean,
    onClick   : () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow), label = "pill_$label"
    )

    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50.dp))
            .background(if (canAfford) Color.White else Color(0xFFF5F5F5))
            .border(
                BorderStroke(1.5.dp, if (canAfford) IGPrimary else Color(0xFFCCCCCC)),
                RoundedCornerShape(50.dp)
            )
            .clickable(enabled = canAfford) {
                pressed = true
                onClick()
            }
            .alpha(if (canAfford) 1f else 0.45f)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = IGPrimary
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (canAfford) IGPrimary else Color(0xFFCCCCCC))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                price,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }

    LaunchedEffect(pressed) { if (pressed) { delay(100); pressed = false } }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Flower Burst Effect — Lottie full-screen overlay (Gestures_flowers.lottie)
// ─────────────────────────────────────────────────────────────────────────────

@Composable private fun FlowerBurstEffect(onDone: () -> Unit) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("Gestures_flowers.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition  = composition,
        iterations   = 1,
        isPlaying    = true,
        restartOnPlay = false
    )
    LaunchedEffect(progress, composition) {
        if (composition != null && progress >= 1f) { delay(200L); onDone() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {}
            .clickable(enabled = true, indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDone() },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.15f)))
        LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("🌸  Love flowers from your match", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cookie Gift Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable private fun CookieGiftDialog(
    myBalance    : Int,
    isSpending   : Boolean,
    partnerAlias : String,
    onDismiss    : () -> Unit,
    onSend       : (Int) -> Unit
) {
    val presets       = listOf(10, 25, 50, 100)
    var selectedPreset by remember { mutableStateOf(0) }
    var customText     by remember { mutableStateOf("") }

    val finalAmount = if (selectedPreset > 0) selectedPreset
                      else customText.toIntOrNull() ?: 0
    val canSend = finalAmount in 1..myBalance && !isSpending

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = IGSurface,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🍪", fontSize = 48.sp)
                Spacer(Modifier.height(4.dp))
                Text("Send Cookie Gift",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = IGPrimary)
                Text("to $partnerAlias 💕",
                    fontSize = 13.sp, color = IGSecondary)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFFF8E1))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Your balance: $myBalance 🍪",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7A5200))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Preset chips
                Text("Quick amounts", fontSize = 12.sp, color = IGSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { amount ->
                        val selected = selectedPreset == amount
                        val enough   = myBalance >= amount
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color(0xFFFFD54F) else if (!enough) Color(0xFFF5F5F5) else Color(0xFFFFF8E1))
                                .border(1.dp, if (selected) Color(0xFFFFB300) else Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                                .clickable(enabled = enough) {
                                    selectedPreset = if (selectedPreset == amount) 0 else amount
                                    customText = ""
                                }
                                .padding(vertical = 10.dp)
                                .alpha(if (enough) 1f else 0.4f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$amount 🍪",
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color(0xFF7A5200) else IGPrimary)
                        }
                    }
                }
                // Custom input
                Text("Custom amount", fontSize = 12.sp, color = IGSecondary)
                OutlinedTextField(
                    value       = customText,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() } && v.length <= 4) {
                            customText = v; selectedPreset = 0
                        }
                    },
                    placeholder  = { Text("Enter 🍪 amount", fontSize = 13.sp, color = IGSecondary) },
                    singleLine   = true,
                    modifier     = Modifier.fillMaxWidth(),
                    shape        = RoundedCornerShape(12.dp),
                    colors       = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFFFFB300),
                        unfocusedBorderColor = IGDivider,
                        focusedTextColor     = IGPrimary,
                        unfocusedTextColor   = IGPrimary
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )
                if (finalAmount > 0 && myBalance < finalAmount) {
                    Text("Not enough cookies (you have $myBalance 🍪)",
                        fontSize = 11.sp, color = IGRed)
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick  = { onSend(finalAmount) },
                    enabled  = canSend,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor        = Color(0xFFFFB300),
                        disabledContainerColor = IGDivider
                    )
                ) {
                    if (isSpending) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (finalAmount > 0) "Gift $finalAmount 🍪" else "Select an amount",
                            fontWeight = FontWeight.Bold,
                            color = if (canSend) Color.White else IGSecondary
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = IGSecondary)
                }
            }
        },
        dismissButton = null
    )
}
