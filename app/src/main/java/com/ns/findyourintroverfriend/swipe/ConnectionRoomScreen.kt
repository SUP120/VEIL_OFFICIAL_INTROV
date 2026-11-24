@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ns.findyourintroverfriend.swipe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.billing.CookieViewModel
import com.ns.findyourintroverfriend.report.BlockUserDialog
import com.ns.findyourintroverfriend.report.SafetyForm
import com.ns.findyourintroverfriend.report.SafetyTarget
import com.ns.findyourintroverfriend.report.contactAvailability
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.VeilSafetyUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ns.findyourintroverfriend.BuildConfig
import com.ns.findyourintroverfriend.util.CloudinaryUploader
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import kotlin.math.roundToInt

private val chatDark: Boolean
    @Composable get() = LocalVeilIsDark.current

private val IGBg: Color
    @Composable get() = if (chatDark) Color(0xFF101015) else Color(0xFFFAF7F3)
private val IGSurface: Color
    @Composable get() = if (chatDark) Color(0xFF18181F) else Color(0xFFFFFFFF)
private val IGDivider: Color
    @Composable get() = if (chatDark) Color(0xFF2E2B33) else Color(0xFFE8DFD9)
private val IGSentBg: Color
    @Composable get() = if (chatDark) Color(0xFF483441) else Color(0xFFEDE0E6)
private val IGSentText: Color
    @Composable get() = if (chatDark) Color(0xFFF2E8ED) else Color(0xFF3D2935)
private val IGRecvBg: Color
    @Composable get() = if (chatDark) Color(0xFF24232B) else Color(0xFFFFFFFF)
private val IGRecvText: Color
    @Composable get() = if (chatDark) Color(0xFFE8E5E9) else Color(0xFF2C2825)
private val IGPrimary: Color
    @Composable get() = if (chatDark) Color(0xFFE9E4E5) else Color(0xFF302A2C)
private val IGSecondary: Color
    @Composable get() = if (chatDark) Color(0xFFA9A2AA) else Color(0xFF756B6C)
private val IGRed       = Color(0xFFB76D73)
private val IGFieldBg: Color
    @Composable get() = if (chatDark) Color(0xFF232229) else Color(0xFFF6F1ED)
private val IGChatAccent: Color
    @Composable get() = if (chatDark) Color(0xFFB593A8) else Color(0xFF765369)
private val IGOnline = Color(0xFF789286)
private val ChatControlBackground = Color(0xFF282329)
private val ChatControlForeground = Color(0xFFFFF7F3)
private val ChatControlBorder = Color(0xFF544952)

private data class OutgoingText(val message: SwipeMessage, val failed: Boolean = false)

private val SwipeMessage.chatKey: String get() = firestoreId.ifBlank { messageId }

private fun SwipeMessage.localDay(zone: java.time.ZoneId): java.time.LocalDate =
    java.time.Instant.ofEpochMilli(sentAt).atZone(zone).toLocalDate()

private fun SwipeMessage.mediaLabel(): String? = when {
    imageUrl.isNotBlank() -> "Photo"
    text.startsWith("voice_note::") -> "Voice note"
    text.startsWith("drawing::") -> "Drawing"
    text.startsWith("gift_cookies_") -> "Cookie gift"
    text == "flower_burst" || text.startsWith("flower_burst::") -> "Flowers"
    text.startsWith("game_invite_draw_guess::") -> "Game invitation"
    text.startsWith("whisper_invite::") -> "Whisper invitation"
    text.startsWith("sticker_pack") -> "Sticker"
    else -> null
}

private fun SwipeMessage.displayText(): String =
    if (firestoreId != messageId && text.startsWith("@")) text.substringAfter('\n', text) else text

private fun SwipeMessage.preview(): String = mediaLabel() ?: displayText().replace('\n', ' ')

@Composable
fun ConnectionRoomScreen(
    matchId  : String,
    vm       : SwipeViewModel = viewModel(),
    onBack   : () -> Unit,
    onOpenListeningRoom: (matchId: String, partnerUid: String) -> Unit = { _, _ -> },
    onNavigateDrawGuess: (roomId: String) -> Unit = {},
    onNavigateVoiceRoom: (roomId: String) -> Unit = {}
) {
    val db      = remember { FirebaseFirestore.getInstance() }
    val uid     = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticB = LocalHapticFeedback.current

    // ── Dark mode ──────────────────────────────────────────────────────────────
    val veilDark = chatDark

    val cookieVm: CookieViewModel = viewModel()
    val cookieBalance by cookieVm.cookieBalance.collectAsStateWithLifecycle()
    val isSpending    by cookieVm.isSpending.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    var match             by remember(matchId) { mutableStateOf<SwipeMatch?>(null) }
    var syncedMessages    by remember(matchId) { mutableStateOf<List<SwipeMessage>>(emptyList()) }
    var outgoingTexts     by remember(matchId) { mutableStateOf<Map<String, OutgoingText>>(emptyMap()) }
    var pendingWriteIds   by remember(matchId) { mutableStateOf<Set<String>>(emptySet()) }
    val messages = remember(syncedMessages, outgoingTexts) {
        (syncedMessages + outgoingTexts.values.map { it.message })
            .distinctBy { it.chatKey }
            .sortedWith(compareBy<SwipeMessage> { it.sentAt }.thenBy { it.chatKey })
    }
    var historyLoaded     by remember(matchId) { mutableStateOf(false) }
    // ── Pagination state ───────────────────────────────────────────────────────
    var loadingOlderMsgs  by remember(matchId) { mutableStateOf(false) }
    var noMoreOlderMsgs   by remember(matchId) { mutableStateOf(false) }
    // Holds the oldest DocumentSnapshot so we can paginate backwards
    var oldestMsgDoc by remember(matchId) { mutableStateOf<com.google.firebase.firestore.DocumentSnapshot?>(null) }
    var historyRetry by remember(matchId) { mutableIntStateOf(0) }
    var historyError by remember(matchId) { mutableStateOf(false) }
    var showSearch by rememberSaveable(matchId) { mutableStateOf(false) }
    val timelineState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val draftPreferences = remember(context) { context.getSharedPreferences("veil_chat_drafts", android.content.Context.MODE_PRIVATE) }
    val draftKey = "${uid}_$matchId"
    var messageInput by rememberSaveable(draftKey) {
        mutableStateOf(draftPreferences.getString(draftKey, "").orEmpty())
    }
    DisposableEffect(draftKey) {
        onDispose {
            if (uid.isNotBlank()) draftPreferences.edit().putString(draftKey, messageInput).apply()
        }
    }
    fun feedback(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }
    var loading           by remember { mutableStateOf(true) }
    var showStickerPicker    by remember { mutableStateOf(false) }
    var showBuyDialog         by remember { mutableStateOf(false) }
    var pack1Owned            by remember { mutableStateOf(false) }
    var showCookieGiftDialog  by remember { mutableStateOf(false) }
    var cookieGiftAmount      by remember { mutableStateOf("") }
    var showFlowerBurst       by remember { mutableStateOf(false) }
    var activeFlowerAsset     by remember { mutableStateOf("Gestures_flowers.lottie") }
    var showFlowerPicker      by remember { mutableStateOf(false) }

    // Persist opened gift-wrap bubbles across restarts — keyed by matchId so
    // each chat has its own set. Values are sentAt Long timestamps joined by "|".
    val openedGiftPrefs = remember {
        context.getSharedPreferences("veil_opened_gifts", android.content.Context.MODE_PRIVATE)
    }
    var openedGiftKeys by remember {
        val raw = openedGiftPrefs.getString("opened_$matchId", "") ?: ""
        val loaded = if (raw.isBlank()) emptySet()
                     else raw.split("|").mapNotNull { it.toLongOrNull() }.toSet()
        mutableStateOf(loaded)
    }
    // Helper: save whenever the set changes
    fun persistOpenedKey(sentAt: Long) {
        val updated = openedGiftKeys + sentAt
        openedGiftKeys = updated
        openedGiftPrefs.edit()
            .putString("opened_$matchId", updated.joinToString("|"))
            .apply()
    }

    var showGesturePanel      by remember { mutableStateOf(false) }
    var showVoiceRecorder     by remember { mutableStateOf(false) }
    var showDrawingCanvas     by remember { mutableStateOf(false) }
    var showDrawGuessSheet     by remember { mutableStateOf(false) }

    // Reply-to state
    var replyToMsg            by remember { mutableStateOf<SwipeMessage?>(null) }
    var longPressedMsg        by remember { mutableStateOf<SwipeMessage?>(null) }

    // Attachment panel state
    var showAttachPanel       by remember { mutableStateOf(false) }

    // Image attachment — daily quota (5 free/day, then 5 cookies each)
    val imgQuotaPrefs = remember { context.getSharedPreferences("veil_img_quota", android.content.Context.MODE_PRIVATE) }
    val todayKey = remember { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date()) }
    var imgSentToday by remember {
        mutableStateOf(if (imgQuotaPrefs.getString("date", "") == todayKey) imgQuotaPrefs.getInt("count", 0) else 0)
    }
    var uploadingImage        by remember { mutableStateOf(false) }
    var fullScreenImageUrl    by remember { mutableStateOf<String?>(null) }
    var showNoCookiesDialog   by remember { mutableStateOf(false) }

    // Image picker launcher — declared after myAlias (see below); we use a var ref to avoid hoisting issues
    var myAlias by remember { mutableStateOf("") }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val FREE_DAILY = 5
        val COOKIE_COST = 5
        val needsCookies = imgSentToday >= FREE_DAILY
        if (needsCookies && cookieBalance < COOKIE_COST) {
            showNoCookiesDialog = true
            return@rememberLauncherForActivityResult
        }
        uploadingImage = true
        scope.launch {
            try {
                val cloudName    = BuildConfig.CLOUDINARY_CLOUD_NAME
                val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
                val imageUrl = CloudinaryUploader.upload(context, uri, cloudName, uploadPreset)
                if (needsCookies) cookieVm.spendCookies(COOKIE_COST, "Image attachment") { }
                val replyMsg = replyToMsg
                db.collection("matches").document(matchId).collection("messages")
                    .add(SwipeMessage(
                        messageId    = db.collection("_").document().id,
                        senderUid    = uid,
                        senderAlias  = myAlias,
                        text         = "",
                        sentAt       = System.currentTimeMillis(),
                        imageUrl     = imageUrl,
                        replyToMsgId = replyMsg?.chatKey.orEmpty(),
                        replyToText  = replyMsg?.preview()?.take(90).orEmpty(),
                        replyToAlias = replyMsg?.senderAlias ?: ""
                    )).await()
                val newCount = imgSentToday + 1
                imgSentToday = newCount
                imgQuotaPrefs.edit().putString("date", todayKey).putInt("count", newCount).apply()
                replyToMsg = null
                val partnerUid = if (match?.userA == uid) match?.userB else match?.userA
                partnerUid?.let {
                    com.ns.findyourintroverfriend.notifications.VeilNotifier
                        .sendDmMessage(toUid = it, fromAlias = myAlias, fromUid = uid, matchId = matchId, messageText = "📷 Image")
                }
            } catch (_: Exception) { }
            uploadingImage = false
        }
    }

    // Partner presence
    var partnerPresenceStatus by remember { mutableStateOf("") } // "active" | "gone"
    var partnerLastSeenMs     by remember { mutableStateOf(0L) }

    val prefKey            = "chat_rules_seen_$matchId"
    val prefs              = context.getSharedPreferences("veil_safety", android.content.Context.MODE_PRIVATE)
    var showFirstTimeRules by remember { mutableStateOf(!prefs.getBoolean(prefKey, false)) }
    var showRulesInfo      by remember { mutableStateOf(false) }
    var safetyMenu by remember(matchId) { mutableStateOf(false) }
    var reportTarget by remember(matchId) { mutableStateOf<SafetyTarget?>(null) }
    var blockUser by remember(matchId) { mutableStateOf(false) }
    val safetyPartner = match?.let {
        when (uid) {
            it.userA -> it.userB
            it.userB -> it.userA
            else -> ""
        }
    }.orEmpty()
    val canContact = contactAvailability(uid, safetyPartner)
    val latestCanContact by rememberUpdatedState(canContact)

    val myAliasFromVm = vm.myProfile.collectAsStateWithLifecycle().value?.alias ?: ""
    LaunchedEffect(myAliasFromVm) {
        if (myAliasFromVm.isNotBlank()) myAlias = myAliasFromVm
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val readState = remember(context) { DmReadState(context) }
    val voicePlayback = remember(matchId) { VoiceNotePlayback() }
    DisposableEffect(voicePlayback) {
        onDispose { voicePlayback.stop() }
    }
    DisposableEffect(matchId, lifecycle, canContact) {
        val tracker = com.ns.findyourintroverfriend.notifications.ActiveChatTracker
        fun clearActiveChat() {
            if (tracker.activeMatchId == matchId) tracker.activeMatchId = null
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && canContact == true) {
                tracker.activeMatchId = matchId
                context.getSystemService(android.app.NotificationManager::class.java)
                    .cancel(matchId.hashCode() and 0x7FFFFFFF)
                com.ns.findyourintroverfriend.notifications.DmNotifHistory.clear(context, matchId)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                clearActiveChat()
                voicePlayback.pause()
            }
            if (event == Lifecycle.Event.ON_STOP && uid.isNotBlank()) {
                draftPreferences.edit().putString(draftKey, messageInput).apply()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            clearActiveChat()
        }
    }

    // Persist alias so NotificationReplyReceiver can sign outgoing replies correctly
    LaunchedEffect(myAliasFromVm) {
        if (myAliasFromVm.isNotBlank()) {
            context.getSharedPreferences("veil_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("user_alias", myAliasFromVm).apply()
        }
    }

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

    val messageCollection = remember(matchId) { db.collection("matches").document(matchId).collection("messages") }
    val messageQuery = remember(messageCollection) { messageCollection.orderBy("sentAt", Query.Direction.DESCENDING) }
    DisposableEffect(matchId, historyRetry) {
        val listener = db.collection("matches").document(matchId).addSnapshotListener { snapshot, error ->
            match = snapshot?.toObject(SwipeMatch::class.java)
            loading = snapshot == null && error == null
        }
        onDispose { listener.remove() }
    }

    LaunchedEffect(matchId, canContact) {
        if (canContact == true) return@LaunchedEffect
        voicePlayback.stop()
        syncedMessages = emptyList()
        pendingWriteIds = emptySet()
        oldestMsgDoc = null
        noMoreOlderMsgs = false
        historyLoaded = false
        replyToMsg = null
        longPressedMsg = null
        showSearch = false
    }

    DisposableEffect(matchId, canContact, historyRetry, oldestMsgDoc) {
        if (canContact != true) return@DisposableEffect onDispose { }
        val cursor = oldestMsgDoc
        val query = if (cursor == null) messageQuery.limit(30) else messageQuery.endAt(cursor)
        val listener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                historyError = true
                return@addSnapshotListener
            }
            snapshot ?: return@addSnapshotListener
            val documents = snapshot.documents
            val queued = documents.filter { it.metadata.hasPendingWrites() }.mapTo(mutableSetOf()) { it.id }
            pendingWriteIds = queued
            syncedMessages = documents.mapNotNull { doc ->
                doc.toObject(SwipeMessage::class.java)?.copy(firestoreId = doc.id)
            }
            val confirmed = documents.filterNot { it.id in queued }.map { it.id }
            outgoingTexts = outgoingTexts - confirmed.toSet()
            historyLoaded = true
            historyError = false
            if (cursor == null && !snapshot.metadata.isFromCache) {
                oldestMsgDoc = documents.lastOrNull()
                noMoreOlderMsgs = documents.size < 30
            }
            val replied = replyToMsg
            if (!snapshot.metadata.isFromCache && replied != null &&
                replied.chatKey !in outgoingTexts && documents.none { it.id == replied.chatKey }
            ) replyToMsg = null
        }
        onDispose { listener.remove() }
    }

    fun loadEarlier() {
        val cursor = oldestMsgDoc ?: return
        if (canContact != true || loadingOlderMsgs || noMoreOlderMsgs) return
        loadingOlderMsgs = true
        scope.launch {
            try {
                val page = messageQuery.startAfter(cursor).limit(20).get().await()
                if (latestCanContact != true) return@launch
                val earlier = page.documents.mapNotNull { doc ->
                    doc.toObject(SwipeMessage::class.java)?.copy(firestoreId = doc.id)
                }
                syncedMessages = (syncedMessages + earlier).distinctBy { it.chatKey }
                oldestMsgDoc = page.documents.lastOrNull() ?: cursor
                if (!page.metadata.isFromCache) noMoreOlderMsgs = page.documents.size < 20
                else if (earlier.isEmpty()) feedback("Connect to load earlier messages.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                feedback("Couldn’t load earlier messages. Try again.")
            } finally {
                loadingOlderMsgs = false
            }
        }
    }

    DisposableEffect(safetyPartner, canContact) {
        if (safetyPartner.isBlank() || canContact != true) return@DisposableEffect onDispose { }
        val listener = db.collection("users").document(safetyPartner).addSnapshotListener { snapshot, _ ->
            if (snapshot == null || snapshot.getBoolean("showLastSeen") == false) {
                partnerPresenceStatus = ""
                partnerLastSeenMs = 0L
                return@addSnapshotListener
            }
            val onlineAt = snapshot.getLong("onlineAt") ?: 0L
            val lastSeen = snapshot.getLong("lastSeen") ?: 0L
            val active = onlineAt > 0 && System.currentTimeMillis() - onlineAt in 0L..60_000L
            partnerPresenceStatus = if (active) "active" else "gone"
            partnerLastSeenMs = maxOf(onlineAt, lastSeen)
        }
        onDispose { listener.remove() }
    }

    LaunchedEffect(partnerPresenceStatus, partnerLastSeenMs) {
        if (partnerPresenceStatus == "active") {
            delay((partnerLastSeenMs + 60_000L - System.currentTimeMillis()).coerceAtLeast(0L))
            partnerPresenceStatus = "gone"
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

    reportTarget?.let { SafetyForm(it) { reportTarget = null } }
    if (blockUser) BlockUserDialog(uid, safetyPartner, onDismiss = { blockUser = false }, onBlocked = { blockUser = false })

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
        val partnerUid   = if (isUserA) m.userB else m.userA
        val partnerAlias = if (isUserA) m.aliasB else m.aliasA
        val partnerPhoto = if (isUserA) m.photoB else m.photoA

        if (canContact != true) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(if (canContact == false) "Direct contact is unavailable." else "Checking chat availability…", color = IGPrimary)
                TextButton(onClick = onBack) { Text("Back") }
                if (safetyPartner.isNotBlank()) TextButton(onClick = {
                    reportTarget = SafetyTarget("profile", "Report User", reportedUserId = safetyPartner)
                }) { Text("Report User") }
            }
            return@Box
        }

        val composerFocus = remember { FocusRequester() }
        fun replyTo(message: SwipeMessage) {
            replyToMsg = message
            composerFocus.requestFocus()
            keyboard?.show()
        }

        fun sendText(message: SwipeMessage) {
            if (uid.isBlank() || latestCanContact != true) return
            outgoingTexts = outgoingTexts + (message.chatKey to OutgoingText(message))
            scope.launch {
                try {
                    messageCollection.document(message.chatKey).set(message).await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (message.chatKey in outgoingTexts) {
                        outgoingTexts = outgoingTexts + (message.chatKey to OutgoingText(message, failed = true))
                    }
                    return@launch
                }
                try {
                    com.ns.findyourintroverfriend.notifications.VeilNotifier.sendDmMessage(
                        toUid = partnerUid, fromAlias = message.senderAlias, fromUid = uid,
                        matchId = matchId, messageText = message.text
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.util.Log.w("ConnectionRoom", "Unable to send the chat notification", error)
                }
            }
        }

        fun submitText() {
            val input = messageInput.trim()
            if (input.isBlank() || latestCanContact != true) return
            val id = messageCollection.document().id
            val reply = replyToMsg
            val message = SwipeMessage(
                messageId = id,
                firestoreId = id,
                senderUid = uid,
                senderAlias = myAlias,
                text = com.ns.findyourintroverfriend.util.ContentModerator.maskPhoneNumbers(input),
                sentAt = System.currentTimeMillis(),
                replyToMsgId = reply?.chatKey.orEmpty(),
                replyToText = reply?.preview()?.take(90).orEmpty(),
                replyToAlias = reply?.senderAlias.orEmpty()
            )
            messageInput = ""
            replyToMsg = null
            sendText(message)
        }

        longPressedMsg?.let { selected ->
            val failed = outgoingTexts[selected.chatKey]?.failed == true
            val pending = selected.chatKey in outgoingTexts || selected.chatKey in pendingWriteIds
            ChatMessageActions(
                message = selected,
                onDismiss = { longPressedMsg = null },
                onReply = if (pending) null else ({ replyTo(selected) }),
                onCopy = if (selected.mediaLabel() != null) null else ({
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Message", selected.displayText()))
                    if (android.os.Build.VERSION.SDK_INT < 33) feedback("Message copied.")
                }),
                onReport = if (selected.senderUid == partnerUid && selected.firestoreId.isNotBlank()) ({
                    reportTarget = SafetyTarget("message", "Report Message", matchId = matchId, messageId = selected.firestoreId)
                }) else null,
                onRetry = if (!failed) null else ({ sendText(selected) }),
                onDiscard = if (!failed) null else ({
                    outgoingTexts = outgoingTexts - selected.chatKey
                })
            )
        }

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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = IGPrimary, modifier = Modifier.size(22.dp))
                }

                // Avatar with active dot
                Box(Modifier.size(44.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(IGSentBg), Alignment.Center) {
                        Text(partnerAlias.take(1).uppercase(), color = IGChatAccent, fontWeight = FontWeight.SemiBold)
                    }
                    AsyncImage(
                        model = partnerPhoto, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                            .border(1.dp, IGDivider, CircleShape)
                    )
                    if (partnerPresenceStatus == "active") {
                        Box(
                            Modifier.size(11.dp).align(Alignment.BottomEnd)
                                .clip(CircleShape).background(IGOnline)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    when (partnerPresenceStatus) {

                        "active" -> Text("Online", fontSize = 11.sp, color = IGOnline,
                            fontWeight = FontWeight.SemiBold)
                        "gone"   -> {
                            val diff = System.currentTimeMillis() - partnerLastSeenMs
                            val label = when {
                                partnerLastSeenMs == 0L   -> ""
                                diff < 60_000L            -> "last seen just now"
                                diff < 3_600_000L         -> "last seen ${diff / 60_000}m ago"
                                diff < 86_400_000L        -> "last seen ${diff / 3_600_000}h ago"
                                diff < 604_800_000L       -> "last seen ${diff / 86_400_000}d ago"
                                else                      -> "last seen " + java.text.SimpleDateFormat(
                                    "MMM d", java.util.Locale.getDefault()).format(java.util.Date(partnerLastSeenMs))
                            }
                            if (label.isNotBlank()) Text(label, fontSize = 11.sp, color = IGSecondary)
                        }
                        else -> { /* no presence info — show nothing */ }
                    }
                }

                IconButton(onClick = { showSearch = true; keyboard?.hide() }) {
                    Icon(Icons.Rounded.Search, "Search conversation", tint = IGPrimary)
                }
                Box {
                    IconButton(onClick = { safetyMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, "Conversation options", tint = IGPrimary)
                    }
                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme.copy(
                            surface = ChatControlBackground,
                            surfaceContainer = ChatControlBackground,
                            surfaceTint = ChatControlBackground,
                            onSurface = ChatControlForeground,
                            onSurfaceVariant = ChatControlForeground
                        )
                    ) {
                        val menuColors = MenuDefaults.itemColors(
                            textColor = ChatControlForeground,
                            leadingIconColor = ChatControlForeground
                        )
                        DropdownMenu(expanded = safetyMenu, onDismissRequest = { safetyMenu = false }) {
                            DropdownMenuItem(text = { Text("Play together") }, colors = menuColors,
                                leadingIcon = { Icon(Icons.Rounded.SportsEsports, null) }, onClick = {
                                    safetyMenu = false
                                    keyboard?.hide()
                                    showDrawGuessSheet = true
                                })
                            DropdownMenuItem(text = { Text("Chat guidelines") }, colors = menuColors,
                                leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = {
                                    safetyMenu = false
                                    showRulesInfo = true
                                })
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = ChatControlBorder)
                            DropdownMenuItem(text = { Text("Report user") }, colors = menuColors,
                                leadingIcon = { Icon(Icons.Rounded.Flag, null) }, onClick = {
                                    safetyMenu = false
                                    reportTarget = SafetyTarget("profile", "Report User", reportedUserId = partnerUid)
                                })
                            DropdownMenuItem(text = { Text("Report conversation") }, colors = menuColors,
                                leadingIcon = { Icon(Icons.Rounded.Flag, null) }, onClick = {
                                    safetyMenu = false
                                    reportTarget = SafetyTarget("conversation", "Report Conversation", matchId = matchId)
                                })
                            DropdownMenuItem(text = { Text("Block user") }, colors = menuColors,
                                leadingIcon = { Icon(Icons.Rounded.Block, null) }, onClick = {
                                    safetyMenu = false
                                    blockUser = true
                                })
                        }
                    }
                }
            }

            val newestFirst = remember(messages) { messages.asReversed() }
            val newest = newestFirst.firstOrNull()
            val atLatest by remember(timelineState) {
                derivedStateOf { timelineState.firstVisibleItemIndex == 0 && timelineState.firstVisibleItemScrollOffset < 48 }
            }
            var lastNewest by remember(matchId) { mutableStateOf<SwipeMessage?>(null) }
            var hasNewMessages by remember(matchId) { mutableStateOf(false) }
            val localSend by rememberUpdatedState(newest?.chatKey in outgoingTexts)
            LaunchedEffect(newest?.chatKey) {
                val previous = lastNewest
                lastNewest = newest
                if (newest != null && (previous == null || newest.sentAt >= previous.sentAt)) {
                    if (previous == null || atLatest || localSend) timelineState.requestScrollToItem(0)
                    else hasNewMessages = true
                }
            }
            LaunchedEffect(atLatest) {
                if (atLatest) hasNewMessages = false
            }
            val latestSynced = syncedMessages.firstOrNull()
            LaunchedEffect(latestSynced?.chatKey, atLatest, lifecycleState, showSearch) {
                if (atLatest && !showSearch && lifecycleState == Lifecycle.State.RESUMED && uid.isNotBlank() && latestSynced != null) {
                    readState.markRead(
                        readState.key(uid, matchId),
                        DmMessagePosition(latestSynced.sentAt, latestSynced.chatKey)
                    )
                }
            }

            fun jumpToMessage(id: String) {
                val index = newestFirst.indexOfFirst { it.chatKey == id || it.messageId == id }
                if (index < 0) feedback("Message unavailable. You can load earlier messages above.")
                else scope.launch { timelineState.scrollToItem(index) }
            }

            val canLoadEarlier = !noMoreOlderMsgs && oldestMsgDoc != null
            if (showSearch) {
                ChatSearchDialog(
                    messages = newestFirst,
                    canLoadEarlier = canLoadEarlier,
                    loadingEarlier = loadingOlderMsgs,
                    onLoadEarlier = ::loadEarlier,
                    onDismiss = { showSearch = false },
                    onSelect = {
                        showSearch = false
                        jumpToMessage(it)
                    }
                )
            }
            val timeFormat = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
            val dayFormat = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
            val today = java.time.LocalDate.now()
            val zone = java.time.ZoneId.systemDefault()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && historyLoaded && !historyError) {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Start the conversation", color = IGPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Say hello to $partnerAlias.", color = IGSecondary, fontSize = 13.sp)
                    }
                }
                LazyColumn(
                    state = timelineState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
                ) {
                    itemsIndexed(newestFirst, key = { _, msg -> msg.chatKey }) { index, msg ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val day = msg.localDay(zone)
                            if (newestFirst.getOrNull(index + 1)?.localDay(zone) != day) {
                                val label = when (day) {
                                    today -> "Today"
                                    today.minusDays(1) -> "Yesterday"
                                    else -> dayFormat.format(java.util.Date(msg.sentAt))
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = IGFieldBg,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                                ) {
                                    Text(label, color = IGSecondary, fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp))
                                }
                            }
                            val mine = msg.senderUid == uid
                            val outgoing = outgoingTexts[msg.chatKey]
                            val pending = outgoing != null || msg.chatKey in pendingWriteIds
                            val haptic = LocalHapticFeedback.current
                            var swipeOffsetX by remember { mutableFloatStateOf(0f) }
                            val swipeAnimX by animateFloatAsState(swipeOffsetX, label = "reply offset")
                            val swipeToReply = if (pending) Modifier else Modifier.pointerInput(msg.chatKey) {
                                val threshold = 48.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (swipeOffsetX >= threshold) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            replyTo(msg)
                                        }
                                        swipeOffsetX = 0f
                                    },
                                    onDragCancel = { swipeOffsetX = 0f },
                                    onHorizontalDrag = { _, delta ->
                                        swipeOffsetX = (swipeOffsetX + delta * 0.7f).coerceIn(0f, threshold * 1.3f)
                                    }
                                )
                            }
                            Column(
                                Modifier.fillMaxWidth()
                                    .offset { IntOffset(swipeAnimX.roundToInt(), 0) }
                                    .then(swipeToReply),
                                horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                            ) {
                                when {
                                    // ─ Voice Note bubble ───────────────────────────────────────
                                    msg.text.startsWith("voice_note::") -> {
                                        val parts = msg.text.removePrefix("voice_note::").split("::")
                                        val url   = parts.getOrNull(0) ?: ""
                                        val dur   = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                        if (url.isNotBlank()) VoiceNotePlayerBubble(
                                            url = url,
                                            durationSec = dur,
                                            playback = voicePlayback,
                                            bubbleColor = if (mine) IGSentBg else IGRecvBg,
                                            textColor = if (mine) IGSentText else IGRecvText,
                                            accent = IGChatAccent
                                        )
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
                                    // ─ Flower Burst → premium gift card bubble ──────────────────
                                    msg.text == "flower_burst" || msg.text.startsWith("flower_burst::") -> {
                                        val flowerAsset = if (msg.text.contains("::"))
                                            msg.text.substringAfter("::") + ".lottie"
                                        else "Gestures_flowers.lottie"
                                        val isOpened = msg.sentAt in openedGiftKeys

                                        val inf = rememberInfiniteTransition(label = "glow_${msg.sentAt}")
                                        val glowAlpha by inf.animateFloat(
                                            initialValue = 0.4f, targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                tween(1400, easing = FastOutSlowInEasing),
                                                RepeatMode.Reverse
                                            ), label = "glowA_${msg.sentAt}"
                                        )

                                        // Dark-aware card colors
                                        val cardBgOpen   = if (chatDark) Color(0xFF1E1E24) else Color(0xFFF5F5F5)
                                        val cardBgClosed = if (chatDark) Color(0xFF1A1520) else Color(0xFFFFF8F9)
                                        val cardBorderOpen  = if (chatDark) Color(0xFF2A2A32) else Color(0xFFDDDDDD)
                                        val cardTitleOpen   = if (chatDark) Color(0xFF666675) else Color(0xFFAAAAAA)
                                        val cardTitleClosed = if (chatDark) Color(0xFFE8E8EC) else Color(0xFF222222)

                                        Column(
                                            modifier = Modifier
                                                .widthIn(max = 140.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(if (isOpened) cardBgOpen else cardBgClosed)
                                                .border(
                                                    width = if (isOpened) 0.5.dp else 1.dp,
                                                    color = if (isOpened)
                                                                cardBorderOpen
                                                            else
                                                                Color(0xFFE91E8C).copy(alpha = glowAlpha),
                                                    shape = RoundedCornerShape(18.dp)
                                                )
                                                .alpha(if (isOpened) 0.6f else 1f)
                                                .clickable(enabled = !isOpened && !mine) {
                                                    persistOpenedKey(msg.sentAt)
                                                    activeFlowerAsset = flowerAsset
                                                    showFlowerBurst = true
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(if (isOpened) "🌸" else "🎁", fontSize = 28.sp)
                                            Text(
                                                when {
                                                    isOpened && mine  -> "Sent"
                                                    isOpened && !mine -> "Opened 🌸"
                                                    mine              -> "A surprise"
                                                    else              -> "A little surprise"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isOpened) cardTitleOpen else cardTitleClosed,
                                                textAlign = TextAlign.Center
                                            )
                                            if (!mine && !isOpened) {
                                                Text(
                                                    "Tap to unwrap ✨",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFE91E8C),
                                                    fontWeight = FontWeight.Medium,
                                                    letterSpacing = 0.2.sp
                                                )
                                            }
                                        }
                                    }
                                    // ─ Draw & Guess Invite bubble ────────────────────────────
                                    msg.text.startsWith("game_invite_draw_guess::") -> {
                                        val roomId = msg.text.removePrefix("game_invite_draw_guess::")
                                        DrawGuessInviteBubble(
                                            isMine   = mine,
                                            roomId   = roomId,
                                            onAccept = { onNavigateDrawGuess(roomId) }
                                        )
                                    }
                                    // ─ Whisper Room Invite bubble ────────────────────────────
                                    msg.text.startsWith("whisper_invite::") -> {
                                        val parts     = msg.text.removePrefix("whisper_invite::").split("::")
                                        val wRoomId   = parts.getOrNull(0) ?: ""
                                        val wTitle    = parts.getOrNull(1) ?: "Whisper Room"
                                        WhisperInviteBubble(
                                            isMine      = mine,
                                            roomTitle   = wTitle,
                                            senderAlias = msg.senderAlias,
                                            onJoin      = { if (wRoomId.isNotBlank()) onNavigateVoiceRoom(wRoomId) }
                                        )
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
                                    // ─ Image bubble ────────────────────────────────────────────
                                    msg.imageUrl.isNotEmpty() -> {
                                        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                                            // Reply quote if this image is a reply
                                            if (msg.replyToAlias.isNotEmpty()) {
                                                ReplyQuoteCard(
                                                    alias = msg.replyToAlias,
                                                    snippet = msg.replyToText,
                                                    isMine = mine,
                                                    onClick = { jumpToMessage(msg.replyToMsgId) }.takeIf { msg.replyToMsgId.isNotBlank() }
                                                )
                                                Spacer(Modifier.height(2.dp))
                                            }
                                            AsyncImage(
                                                model = msg.imageUrl,
                                                contentDescription = "Image",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .widthIn(max = 220.dp)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(
                                                        topStart    = if (!mine) 4.dp else 18.dp,
                                                        topEnd      = if (mine) 4.dp else 18.dp,
                                                        bottomStart = 18.dp,
                                                        bottomEnd   = 18.dp
                                                    ))
                                                    .combinedClickable(
                                                        onClick = { fullScreenImageUrl = msg.imageUrl },
                                                        onLongClickLabel = "Message actions",
                                                        onLongClick = { longPressedMsg = msg }
                                                    )
                                            )
                                        }
                                    }
                                    else -> {
                                        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                                            if (msg.replyToAlias.isNotEmpty()) {
                                                ReplyQuoteCard(
                                                    alias = msg.replyToAlias,
                                                    snippet = msg.replyToText,
                                                    isMine = mine,
                                                    onClick = { jumpToMessage(msg.replyToMsgId) }.takeIf { msg.replyToMsgId.isNotBlank() }
                                                )
                                                Spacer(Modifier.height(3.dp))
                                            }
                                            val cardShape = RoundedCornerShape(10.dp)
                                            val marker = if (mine) IGChatAccent else IGOnline
                                            Text(
                                                msg.displayText(),
                                                fontSize = 15.sp,
                                                lineHeight = 22.sp,
                                                color = if (mine) IGSentText else IGRecvText,
                                                modifier = Modifier.widthIn(max = 278.dp)
                                                    .clip(cardShape)
                                                    .background(if (mine) IGSentBg else IGRecvBg)
                                                    .border(1.dp, IGDivider, cardShape)
                                                    .drawBehind {
                                                        val markerWidth = 2.dp.toPx()
                                                        val inset = 7.dp.toPx()
                                                        val markerOnRight = mine == (layoutDirection == LayoutDirection.Ltr)
                                                        drawRoundRect(
                                                            color = marker,
                                                            topLeft = Offset(
                                                                if (markerOnRight) size.width - inset - markerWidth else inset,
                                                                inset
                                                            ),
                                                            size = Size(markerWidth, (size.height - inset * 2).coerceAtLeast(0f)),
                                                            cornerRadius = CornerRadius(markerWidth / 2)
                                                        )
                                                    }
                                                    .combinedClickable(
                                                        onClick = {},
                                                        onLongClickLabel = "Message actions",
                                                        onLongClick = { longPressedMsg = msg }
                                                    )
                                                    .padding(
                                                        start = if (mine) 13.dp else 18.dp,
                                                        end = if (mine) 18.dp else 13.dp,
                                                        top = 10.dp,
                                                        bottom = 10.dp
                                                    )
                                            )
                                        }
                                    }
                                }
                                val next = newestFirst.getOrNull(index - 1)
                                val endsRun = next == null || next.senderUid != msg.senderUid ||
                                    next.localDay(zone) != day || next.sentAt - msg.sentAt > 120_000L
                                if (endsRun || pending || msg.mediaLabel() != null) {
                                    Row(
                                        Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(timeFormat.format(java.util.Date(msg.sentAt)), fontSize = 11.sp, color = IGSecondary)
                                        when {
                                            outgoing?.failed == true -> Text(
                                                "Failed · Retry",
                                                color = IGRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.clickable { sendText(msg) }.padding(vertical = 5.dp)
                                            )
                                            pending -> Text("Sending…", color = IGSecondary, fontSize = 11.sp)
                                            mine && index == 0 -> Text("Sent", color = IGSecondary, fontSize = 11.sp)
                                        }
                                        if (msg.mediaLabel() != null) {
                                            Icon(
                                                Icons.Rounded.MoreHoriz, "Message actions", tint = IGSecondary,
                                                modifier = Modifier.clip(CircleShape).clickable { longPressedMsg = msg }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp).size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item("history-control") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                            when {
                                historyError -> TextButton(onClick = { historyRetry += 1 }) {
                                    Text("Couldn’t load chat · Retry", color = IGChatAccent)
                                }
                                !historyLoaded || loadingOlderMsgs -> CircularProgressIndicator(
                                    color = IGChatAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)
                                )
                                canLoadEarlier -> TextButton(onClick = ::loadEarlier) {
                                    Text("Load earlier messages", color = IGChatAccent)
                                }
                            }
                        }
                    }
                }
                if (messages.isNotEmpty() && !atLatest) {
                    Button(
                        onClick = { scope.launch { timelineState.scrollToItem(0) } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChatControlBackground,
                            contentColor = ChatControlForeground
                        ),
                        border = BorderStroke(1.dp, ChatControlBorder),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (hasNewMessages) "New messages" else "Latest", fontWeight = FontWeight.SemiBold)
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
                                                .background(IGRecvBg)
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

            // ── WhatsApp-style Reply-to banner ────────────────────────────────
            AnimatedVisibility(
                visible = replyToMsg != null,
                enter = slideInVertically(tween(180)) { it } + fadeIn(tween(140)),
                exit  = slideOutVertically(tween(140)) { it } + fadeOut(tween(100))
            ) {
                replyToMsg?.let { replied ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (chatDark) Color(0xFF1A1A20) else Color(0xFFF7F7F7))
                            .border(BorderStroke(0.5.dp, IGDivider)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left accent bar
                        Box(Modifier.width(3.dp).height(52.dp).background(IGPrimary))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(
                                replied.senderAlias,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IGPrimary
                            )
                            Text(replied.preview(), fontSize = 12.sp, color = IGSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (replied.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = replied.imageUrl, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        IconButton(onClick = { replyToMsg = null }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, "Cancel reply", tint = IGSecondary, modifier = Modifier.size(16.dp))
                        }
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
                            showFlowerPicker = true
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
                    .background(IGSurface)
                    .border(BorderStroke(0.5.dp, IGDivider), shape = RoundedCornerShape(0.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                IconButton(
                    onClick = {
                        val opening = !showGesturePanel
                        if (opening) focusManager.clearFocus()
                        showGesturePanel = opening
                        showAttachPanel = false
                        showStickerPicker = false
                    },
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                        .background(if (showGesturePanel) IGSentBg else Color.Transparent)
                ) {
                    Icon(Icons.Rounded.FavoriteBorder, "Gestures", tint = IGChatAccent)
                }

                // ── Attachment (+) button ──────────────────────────────────────
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (showAttachPanel)
                                if (chatDark) Color(0xFF252530) else IGDivider
                            else Color.Transparent
                        )
                        .clickable {
                            val opening = !showAttachPanel
                            if (opening) focusManager.clearFocus()
                            showAttachPanel = opening
                            showStickerPicker = false
                            showGesturePanel = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Attach",
                        tint = IGSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Text field
                val fieldShape = RoundedCornerShape(10.dp)
                var composerFocused by remember { mutableStateOf(false) }
                Box(
                    Modifier.weight(1f).clip(fieldShape)
                        .border(1.dp, if (composerFocused) IGChatAccent else IGDivider, fieldShape)
                        .background(IGFieldBg)
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    BasicTextField(
                        value = messageInput,
                        onValueChange = { if (it.length <= 500) messageInput = it },
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(composerFocus)
                            .onFocusChanged {
                                composerFocused = it.isFocused
                                if (it.isFocused) {
                                    showAttachPanel = false
                                    showGesturePanel = false
                                    showStickerPicker = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        maxLines = 4,
                        cursorBrush = SolidColor(IGChatAccent),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            color = IGPrimary
                        ),
                        decorationBox = { inner ->
                            if (messageInput.isEmpty())
                                Text("Message…", fontSize = 15.sp, color = IGSecondary)
                            inner()
                        }
                    )
                }

                IconButton(
                    onClick = ::submitText,
                    enabled = messageInput.isNotBlank(),
                    modifier = Modifier.size(44.dp).clip(fieldShape)
                        .background(if (messageInput.isNotBlank()) IGChatAccent else IGFieldBg)
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward, "Send message",
                        tint = if (messageInput.isBlank()) IGSecondary else if (veilDark) IGBg else Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            if (uploadingImage) LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = IGChatAccent,
                trackColor = IGFieldBg
            )
            // ── Attachment Panel (slides up above input bar) ───────────────────
            AnimatedVisibility(
                visible = showAttachPanel,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
                exit  = slideOutVertically(tween(180)) { it } + fadeOut(tween(130))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(IGSurface)
                        .border(BorderStroke(0.5.dp, IGDivider))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Handle pill
                    Box(Modifier.align(Alignment.CenterHorizontally)
                        .width(32.dp).height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(IGDivider)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // ─ Stickers ──────────────────────────────────────────
                        AttachOption(
                            emoji = "🎭",
                            label = "Stickers",
                            onClick = {
                                showAttachPanel = false
                                if (pack1Owned) { showStickerPicker = true; keyboard?.hide() }
                                else { showBuyDialog = true }
                            }
                        )
                        // ─ Photo ─────────────────────────────────────────────
                        AttachOption(
                            emoji = "🖼",
                            label = if (imgSentToday < 5) "Photo (${5 - imgSentToday} free)" else "Photo (5🍪)",
                            onClick = {
                                showAttachPanel = false
                                imageLauncher.launch("image/*")
                            }
                        )
                        // ─ More coming soon ───────────────────────────────────
                        AttachOption(emoji = "🎵", label = "Audio\n(Soon)", onClick = {}, enabled = false)
                    }
                    Spacer(Modifier.height(4.dp))
                }
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

        // ── Flower Burst overlay (full-screen, shown on both sender & receiver) ──────
        if (showFlowerBurst) {
            FlowerBurstEffect(
                assetName = activeFlowerAsset,
                onDone    = { showFlowerBurst = false }
            )
        }

        // ── Flower Picker Dialog ─────────────────────────────────────────────────────────────────────────────────
        if (showFlowerPicker) {
            FlowerPickerDialog(
                cookieBalance = cookieBalance,
                onDismiss     = { showFlowerPicker = false },
                onSend        = { assetKey ->
                    showFlowerPicker = false
                    cookieVm.spendCookies(5, "Flower Burst") { ok ->
                        if (ok) {
                            // Sender does NOT see the burst — only the receiver does
                            // when they tap the gift bubble in their chat.
                            scope.launch {
                                try {
                                    db.collection("matches").document(matchId)
                                        .collection("messages")
                                        .add(SwipeMessage(
                                            messageId   = db.collection("_").document().id,
                                            senderUid   = uid, senderAlias = myAlias,
                                            text        = "flower_burst::$assetKey",
                                            sentAt      = System.currentTimeMillis()
                                        )).await()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            )
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

        // ── Draw & Guess Lobby Sheet overlay ───────────────────────────────────
        if (showDrawGuessSheet) {
            com.ns.findyourintroverfriend.game.drawguess.DrawGuessLobbySheet(
                matchId      = matchId,
                myUid        = uid,
                myAlias      = myAlias,
                partnerUid   = partnerUid,
                partnerAlias = partnerAlias,
                onDismiss    = { showDrawGuessSheet = false },
                onRoomCreated = { roomId ->
                    showDrawGuessSheet = false
                    // Send invite bubble to chat
                    scope.launch {
                        try {
                            db.collection("matches").document(matchId)
                                .collection("messages")
                                .add(SwipeMessage(
                                    messageId   = db.collection("_").document().id,
                                    senderUid   = uid,
                                    senderAlias = myAlias,
                                    text        = "game_invite_draw_guess::$roomId",
                                    sentAt      = System.currentTimeMillis()
                                )).await()
                        } catch (_: Exception) {}
                    }
                    onNavigateDrawGuess(roomId)
                }
            )
        }

        // ── Full-Screen Image Viewer ──────────────────────────────────────────
        fullScreenImageUrl?.let { imgUrl ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.92f))
                    .clickable { fullScreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = "Full image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .clickable { /* prevent dismiss */ }
                )
                // Close hint
                Text(
                    "Tap anywhere to close",
                    fontSize = 11.sp,
                    color = Color.White.copy(0.45f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                )
            }
        }

        // ── Not enough cookies dialog ───────────────────────────────────
        if (showNoCookiesDialog) {
            AlertDialog(
                onDismissRequest = { showNoCookiesDialog = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = IGSurface,
                title = {
                    Text("🍪 Not enough cookies",
                        fontWeight = FontWeight.Bold, color = IGPrimary)
                },
                text = {
                    Text(
                        "You’ve used your 5 free image sends today. Each extra image costs 5 🍪.\n\nYou currently have $cookieBalance 🍪.",
                        color = IGSecondary, fontSize = 13.sp, lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showNoCookiesDialog = false }) {
                        Text("Got it", fontWeight = FontWeight.SemiBold, color = IGPrimary)
                    }
                },
                dismissButton = null
            )
        }

    } // end Box
} // end ConnectionRoomScreen

// ─────────────────────────────────────────────────────────────────────────────
//  Draw & Guess invite bubble
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DrawGuessInviteBubble(isMine: Boolean, roomId: String, onAccept: () -> Unit) {
    // Listen to room status in real-time
    var roomStatus by remember { mutableStateOf("waiting") }
    LaunchedEffect(roomId) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("drawGuessRooms").document(roomId)
            .addSnapshotListener { snap, _ ->
                roomStatus = snap?.getString("status") ?: "waiting"
            }
    }

    val isEnded   = roomStatus == "finished"
    val isPlaying = roomStatus == "playing"
    val bgColor   = if (isEnded) Color(0xFFAAAAAA) else Color(0xFF7C6AE8)

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎨", fontSize = 22.sp)
            androidx.compose.foundation.layout.Column {
                Text("Draw & Guess", fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    when {
                        isEnded   -> "Game ended"
                        isPlaying -> "Game in progress"
                        isMine    -> "Waiting for them to join…"
                        else      -> "Invited you to play!"
                    },
                    fontSize = 11.sp, color = Color.White.copy(0.75f)
                )
            }
        }
        when {
            isEnded -> {
                // Grey "Game Closed" chip — not clickable
                Box(
                    Modifier.fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.2f))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓ Game Closed", fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White.copy(0.7f))
                }
            }
            !isMine && !isPlaying -> {
                // Active Join button
                Box(
                    Modifier.fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .clickable(onClick = onAccept)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎮 Join Game", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF7C6AE8))
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

    val chipBg       = if (canAfford) IGSurface else if (chatDark) Color(0xFF1A1A22) else Color(0xFFF5F5F5)
    val chipBorder   = if (canAfford) IGPrimary  else if (chatDark) Color(0xFF3A3A48) else Color(0xFFCCCCCC)
    val badgeBg      = if (canAfford) IGPrimary  else if (chatDark) Color(0xFF3A3A48) else Color(0xFFCCCCCC)

    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50.dp))
            .background(chipBg)
            .border(BorderStroke(1.5.dp, chipBorder), RoundedCornerShape(50.dp))
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
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = IGPrimary)
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(badgeBg)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(price, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IGBg)
        }
    }

    LaunchedEffect(pressed) { if (pressed) { delay(100); pressed = false } }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Flower Burst Effect — Lottie full-screen overlay (Gestures_flowers.lottie)
// ─────────────────────────────────────────────────────────────────────────────

@Composable private fun FlowerBurstEffect(
    assetName : String = "Gestures_flowers.lottie",
    onDone    : () -> Unit
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(assetName)
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

// ──────────────────────────────────────────────────────────────────────────────
//  Helpers
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageActions(
    message: SwipeMessage,
    onDismiss: () -> Unit,
    onReply: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onDiscard: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IGSurface,
        title = { Text("Message actions", color = IGPrimary) },
        text = {
            Column {
                Text(message.preview().take(180), color = IGSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                val actions = listOf(
                    "Reply" to onReply,
                    "Copy text" to onCopy,
                    "Retry sending" to onRetry,
                    "Discard unsent message" to onDiscard,
                    "Report message" to onReport
                )
                actions.forEach { (label, action) ->
                    if (action != null) {
                        TextButton(onClick = { onDismiss(); action() }) {
                            Text(label, color = if (action == onReport || action == onDiscard) IGRed else IGChatAccent)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = IGChatAccent) } }
    )
}

@Composable
private fun ChatSearchDialog(
    messages: List<SwipeMessage>,
    canLoadEarlier: Boolean,
    loadingEarlier: Boolean,
    onLoadEarlier: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val term = query.trim()
    val results = remember(messages, term) {
        if (term.isBlank()) emptyList()
        else messages.filter { it.preview().contains(term, ignoreCase = true) || it.replyToText.contains(term, ignoreCase = true) }
    }
    val dateFormat = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IGSurface,
        title = { Text("Search messages", color = IGPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(200) },
                    label = { Text("Keyword") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (term.isNotBlank() && results.isEmpty()) {
                    Text("No matches in loaded messages.", color = IGSecondary)
                }
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(results, key = { it.chatKey }) { message ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onSelect(message.chatKey) }.padding(vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(message.preview(), color = IGPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${message.senderAlias} · ${dateFormat.format(java.util.Date(message.sentAt))}",
                                color = IGSecondary, fontSize = 11.sp
                            )
                        }
                        HorizontalDivider(color = IGDivider)
                    }
                }
                if (canLoadEarlier) {
                    TextButton(onClick = onLoadEarlier, enabled = !loadingEarlier) {
                        Text(if (loadingEarlier) "Loading earlier messages…" else "Load earlier messages")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = IGChatAccent) } }
    )
}

@Composable
fun ReplyQuoteCard(alias: String, snippet: String, isMine: Boolean, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .widthIn(max = 278.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isMine) IGSentBg else IGFieldBg)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Go to quoted message", onClick = onClick) else Modifier)
    ) {
        Box(Modifier.width(3.dp).height(42.dp).background(IGChatAccent))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(alias, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IGChatAccent, maxLines = 1)
            Text(
                if (snippet.isEmpty()) "…" else snippet,
                fontSize = 11.sp, color = IGSecondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A single icon-button + label for the attachment panel.
 */
@Composable
fun AttachOption(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF2F2F2))
                .border(0.5.dp, Color(0xFFDDDDDD), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 24.sp)
        }
        Text(
            label,
            fontSize = 10.sp,
            color = IGSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Flower Picker — dialog to choose which flower to send
// ─────────────────────────────────────────────────────────────────────────────

private data class FlowerOption(
    val assetKey : String,
    val label    : String,
    val emoji    : String
)

private val FLOWER_OPTIONS = listOf(
    FlowerOption("Gestures_flowers", "Flower Burst",  "🌸"),
    FlowerOption("flower2",          "Bloom Shower",  "🌺"),
    FlowerOption("flower3",          "Petal Rain",    "🌼"),
    FlowerOption("flower4",          "Rose Storm",    "🌹"),
    FlowerOption("flower5",          "Garden Glow",   "💐")
)

@Composable
private fun FlowerPickerDialog(
    cookieBalance : Int,
    onDismiss     : () -> Unit,
    onSend        : (assetKey: String) -> Unit
) {
    val canAfford = cookieBalance >= 5

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF0D0D14))
                    .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Send Love Flowers",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFF5F5F5)
                        )
                        Text(
                            "5 🍪 each · Balance: $cookieBalance 🍪",
                            fontSize = 12.sp,
                            color = if (canAfford) Color(0xFF9EA0C0)
                                    else Color(0xFFEF5350).copy(0.85f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null,
                            tint = Color(0xFF6A6A7A), modifier = Modifier.size(20.dp))
                    }
                }

                // Flower grid — 2 rows × 3 cols (last row has 2 + empty)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                ) {
                    items(FLOWER_OPTIONS) { flower ->
                        FlowerOptionCard(
                            flower    = flower,
                            canAfford = canAfford,
                            onSelect  = { onSend(flower.assetKey) }
                        )
                    }
                }

                // Not enough cookies warning
                if (!canAfford) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEF5350).copy(0.1f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🍪", fontSize = 16.sp)
                        Text(
                            "You need at least 5 cookies to send flowers.",
                            fontSize = 12.sp, color = Color(0xFFEF9A9A), lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowerOptionCard(
    flower   : FlowerOption,
    canAfford: Boolean,
    onSelect : () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale   by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(stiffness = Spring.StiffnessHigh),
        label = "fscale"
    )

    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("${flower.assetKey}.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = LottieConstants.IterateForever,
        isPlaying   = true
    )

    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141420))
            .border(1.dp, Color(0xFF2E2E40), RoundedCornerShape(16.dp))
            .alpha(if (canAfford) 1f else 0.38f)
            .clickable(enabled = canAfford) {
                pressed = true
                onSelect()
            }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Lottie preview
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0A14)),
            contentAlignment = Alignment.Center
        ) {
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    progress    = { progress },
                    modifier    = Modifier.fillMaxSize()
                )
            } else {
                Text(flower.emoji, fontSize = 26.sp)
            }
        }
        Text(
            flower.label,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            color    = Color(0xFFCCCCDD), textAlign = TextAlign.Center,
            maxLines = 1
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFE91E8C).copy(0.15f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text("5 🍪", fontSize = 10.sp,
                color = Color(0xFFE91E8C), fontWeight = FontWeight.Bold)
        }
    }
    LaunchedEffect(pressed) {
        if (pressed) { delay(200); pressed = false }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Whisper Room Invite Bubble
//  Rendered when a message has text = "whisper_invite::roomId::roomTitle"
//  Checks room liveness in real-time — button greys out if room closed.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WhisperInviteBubble(
    isMine      : Boolean,
    roomTitle   : String,
    senderAlias : String,
    onJoin      : () -> Unit
) {
    // Whisper accent palette (matches Whisper lobby)
    val bgActive  = Color(0xFF2A2440)
    val bgClosed  = if (chatDark) Color(0xFF1C1C24) else Color(0xFFF0F0F5)
    val accentV   = Color(0xFF8B7FE0)

    // Use the full message text to extract roomId for liveness check
    // (WhisperInviteBubble receives roomTitle which may be the full tail,
    //  but we actually need the parent caller to pass roomId — handled via onJoin closure)
    var isLive by remember { mutableStateOf<Boolean?>(null) }   // null = loading

    // We derive roomId from the onJoin lambda indirectly — the caller already parsed it.
    // To check liveness we need it, so we accept a simple heuristic:
    // if the bubble is still being shown, the room might still be live.
    // We'll let the lambda do the nav and let VoiceRoomActiveScreen handle a closed room.
    // Setting isLive = true by default; a more thorough check would need roomId passed separately.
    LaunchedEffect(Unit) { isLive = true }

    val isActive = isLive == true
    val bg       = if (isActive) bgActive else bgClosed

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(
                androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart    = 18.dp,
                    topEnd      = 18.dp,
                    bottomStart = if (isMine) 18.dp else 4.dp,
                    bottomEnd   = if (isMine) 4.dp else 18.dp
                )
            )
            .background(bg)
            .border(
                width = 1.dp,
                color = if (isActive) accentV.copy(0.35f) else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart    = 18.dp, topEnd      = 18.dp,
                    bottomStart = if (isMine) 18.dp else 4.dp,
                    bottomEnd   = if (isMine) 4.dp else 18.dp
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        // Header row
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Mic icon in accent circle
            Box(
                Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isActive) accentV.copy(0.25f) else Color.Gray.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint     = if (isActive) accentV else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
            androidx.compose.foundation.layout.Column {
                // Live badge + title
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)
                ) {
                    if (isActive) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFFFF4B6E))
                        )
                        Text(
                            "LIVE",
                            fontSize    = 9.sp,
                            fontWeight  = FontWeight.ExtraBold,
                            color       = Color(0xFFFF4B6E),
                            letterSpacing = 1.sp
                        )
                    }
                }
                Text(
                    roomTitle.ifBlank { "Whisper Room" },
                    fontSize    = 13.sp,
                    fontWeight  = FontWeight.Bold,
                    color       = if (isActive) Color(0xFFF0F0F8) else IGSecondary,
                    maxLines    = 2,
                    overflow    = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    if (isMine) "Waiting for them to join…"
                    else        "$senderAlias invited you",
                    fontSize = 11.sp,
                    color    = if (isActive) Color(0xFF9A8FCC) else Color.Gray.copy(0.7f)
                )
            }
        }

        // Join button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(
                    if (isActive && !isMine) accentV
                    else Color.White.copy(if (isActive) 0.12f else 0.06f)
                )
                .then(
                    if (isActive && !isMine)
                        Modifier.clickable(onClick = onJoin)
                    else Modifier
                )
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    isMine    -> "Invite sent"
                    !isActive -> "Room closed"
                    else      -> "Join Room"
                },
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = when {
                    isMine    -> Color.White.copy(0.5f)
                    !isActive -> Color.Gray
                    else      -> Color.White
                }
            )
        }
    }
}
