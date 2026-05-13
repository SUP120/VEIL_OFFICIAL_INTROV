package com.ns.findyourintroverfriend.chat

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.ui.theme.*
import com.ns.findyourintroverfriend.ui.SlowChatSkeletonScreen
import com.ns.findyourintroverfriend.data.GroupFields
import com.ns.findyourintroverfriend.report.ReportSheet
import com.ns.findyourintroverfriend.util.ContentModerator
import com.ns.findyourintroverfriend.util.StrikeManager
import com.ns.findyourintroverfriend.util.BanStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

private const val TAG             = "SlowChat"
private const val DEFAULT_MAX_MSG = 7   // Free tier. Paid unlock → 20 (set by backend)

// ─────────────────────────────────────────────────────────────────────────────
//  Model
// ─────────────────────────────────────────────────────────────────────────────

data class ChatMessage(
    val id        : String,
    val uid       : String,
    val alias     : String,
    val emoji     : String,
    val text      : String,
    val sentAt    : Timestamp,
    val isMe      : Boolean,
    val isSticker : Boolean = false  // true when text is a sticker resource name
)

// All other (non-me) members in the group — shown in the Member picker
data class ChatMember(
    val uid   : String,
    val alias : String,
    val emoji : String
)

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Ready(
        val messages         : List<ChatMessage>,
        val mySentCount      : Int,
        val groupDay         : Int,
        val maxMessages      : Int     = DEFAULT_MAX_MSG,  // dynamic from Firestore
        val chatLimitUnlocked: Boolean = false,
        val groupMembers     : List<ChatMember> = emptyList(),
        val partnerUid       : String = "",
        val partnerAlias     : String = "Partner",
        val moderationWarning: String? = null,
        val violationCount   : Int = 0
    ) : ChatUiState()
    data class Error(val msg: String) : ChatUiState()
    // ── Ban gate — shown when isBanned == true in Firestore ──────────────────
    data class Banned(
        val isPermanent  : Boolean,
        val bannedUntil  : java.util.Date? = null,   // null == permanent
        val message      : String
    ) : ChatUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class SlowChatViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _dynamicMaxMsg     = MutableStateFlow(DEFAULT_MAX_MSG)
    private val _chatLimitUnlocked = MutableStateFlow(false)
    val dynamicMaxMsg: StateFlow<Int>     = _dynamicMaxMsg.asStateFlow()
    val chatLimitUnlocked: StateFlow<Boolean> = _chatLimitUnlocked.asStateFlow()

    private var groupId = ""
    private var myUid   = ""
    private var chatListener: ListenerRegistration? = null

    // Expose groupId so the UI can pass it to ReportSheet
    private val _groupId = MutableStateFlow("")
    val activeGroupId: StateFlow<String> = _groupId.asStateFlow()

    init { load() }

    private fun load() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = ChatUiState.Error("Not signed in."); return
        }
        myUid = uid
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()

                // ── 🔒 Ban gate — check BEFORE doing anything else ────────────
                val banStatus = StrikeManager.checkBanStatus(uid)
                if (banStatus is BanStatus.PermaBanned) {
                    _state.value = ChatUiState.Banned(
                        isPermanent = true,
                        message     = "🚫 Your account has been permanently banned due to repeated violations of community guidelines."
                    )
                    return@launch
                }
                if (banStatus is BanStatus.TempBanned) {
                    _state.value = ChatUiState.Banned(
                        isPermanent = false,
                        bannedUntil = banStatus.until,
                        message     = "⏸️ You are temporarily banned until ${formatBanUntil(banStatus.until)}. You will regain access automatically once the ban expires."
                    )
                    return@launch
                }
                // ─────────────────────────────────────────────────────────────

                groupId = userDoc.getString("groupId") ?: ""
                _groupId.value = groupId
                if (groupId.isBlank()) {
                    _state.value = ChatUiState.Error("No active group.")
                    return@launch
                }

                // Read dynamic message limit (set by backend webhook on purchase)
                val chatLimitUnlocked = userDoc.getBoolean("chatLimitUnlocked") ?: false
                val msgLimit = (userDoc.getLong("chatMsgLimit") ?: DEFAULT_MAX_MSG.toLong()).toInt()
                _dynamicMaxMsg.value    = msgLimit
                _chatLimitUnlocked.value = chatLimitUnlocked

                val groupDoc = db.collection("groups").document(groupId).get().await()
                val day = (groupDoc.getLong("day") ?: 1L).toInt()

                if (day < 2) {  // TEMP: day < 3 for prod — reverted to 2 for testing
                    _state.value = ChatUiState.Error("Chat unlocks on Day 2 🌙")
                    return@launch
                }

                listenToChat(day)
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.value = ChatUiState.Error(e.localizedMessage ?: "Failed to load chat.")
            }
        }
    }

    /** Format bannedUntil timestamp for display */
    private fun formatBanUntil(date: java.util.Date): String =
        java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(date)


    // ── Group members (all non-me) — loaded once, used in member picker ──────
    private val _members = MutableStateFlow<List<ChatMember>>(emptyList())
    val members: StateFlow<List<ChatMember>> = _members.asStateFlow()

    private suspend fun loadGroupMembers() {
        try {
            val groupDoc = db.collection("groups").document(groupId).get().await()
            @Suppress("UNCHECKED_CAST")
            val uids = groupDoc.get(GroupFields.MEMBERS) as? List<String> ?: emptyList()
            val chatMembers = uids
                .filter { it != myUid }
                .mapNotNull { uid ->
                    try {
                        val doc = db.collection("users").document(uid).get().await()
                        ChatMember(
                            uid   = uid,
                            alias = doc.getString("alias") ?: "Someone",
                            emoji = doc.getString("emoji")  ?: "🌙"
                        )
                    } catch (_: Exception) { null }
                }
            _members.value = chatMembers
        } catch (_: Exception) { }
    }

    private fun listenToChat(day: Int) {
        // Load group members once (for block/report picker)
        viewModelScope.launch { loadGroupMembers() }

        chatListener = db.collection("groups").document(groupId)
            .collection("chat")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                viewModelScope.launch {
                    // Re-read unlock status from Firestore on every update
                    // so the 20-message limit shows immediately after payment
                    try {
                        val freshUser = db.collection("users").document(myUid).get().await()
                        val freshUnlocked = freshUser.getBoolean("chatLimitUnlocked") ?: false
                        val freshLimit    = (freshUser.getLong("chatMsgLimit") ?: DEFAULT_MAX_MSG.toLong()).toInt()
                        _dynamicMaxMsg.value     = freshLimit
                        _chatLimitUnlocked.value = freshUnlocked
                    } catch (_: Exception) { }

                    val messages = snap.documents.mapNotNull { doc ->
                        val uid    = doc.getString("uid")   ?: return@mapNotNull null
                        val text   = doc.getString("text")  ?: return@mapNotNull null
                        val sentAt = doc.getTimestamp("sentAt") ?: return@mapNotNull null
                        val alias  = try {
                            db.collection("users").document(uid).get().await().getString("alias") ?: "Someone"
                        } catch (e: Exception) { "Someone" }
                        val emoji  = try {
                            db.collection("users").document(uid).get().await().getString("emoji") ?: "🌙"
                        } catch (e: Exception) { "🌙" }
                        val isSticker = doc.getBoolean("isSticker") ?: false
                        ChatMessage(doc.id, uid, alias, emoji, text, sentAt, uid == myUid, isSticker)
                    }
                    val mySent = messages.count { it.isMe }
                    val current = _state.value as? ChatUiState.Ready
                    _state.value = ChatUiState.Ready(
                        messages           = messages,
                        mySentCount        = mySent,
                        groupDay           = day,
                        maxMessages        = _dynamicMaxMsg.value,
                        chatLimitUnlocked  = _chatLimitUnlocked.value,
                        groupMembers       = _members.value,
                        moderationWarning  = current?.moderationWarning,
                        violationCount     = current?.violationCount ?: 0
                    )
                }
            }
    }

    fun sendSticker(emoji: String) {
        val current = _state.value as? ChatUiState.Ready ?: return
        if (current.mySentCount >= current.maxMessages) return
        if (!current.chatLimitUnlocked) return   // stickers only for unlocked users
        viewModelScope.launch {
            try {
                val data = mapOf(
                    "uid"    to myUid,
                    "text"   to emoji,     // store emoji as text message
                    "sentAt" to Timestamp.now(),
                    "isSticker" to true
                )
                db.collection("groups").document(groupId)
                    .collection("chat")
                    .add(data).await()
                Log.d(TAG, "Sticker sent: $emoji")
            } catch (e: Exception) {
                Log.e(TAG, "sendSticker failed", e)
            }
        }
    }

    fun send(text: String) {
        val current = _state.value as? ChatUiState.Ready ?: return
        if (current.mySentCount >= current.maxMessages) return
        if (text.isBlank()) return

        viewModelScope.launch {
            // ── 🔒 Re-check ban status on EVERY send attempt ─────────────────
            //    This closes the loophole: user gets warned/banned via Strike,
            //    then types a clean-looking word — we still catch it here.
            val banStatus = StrikeManager.checkBanStatus(myUid)
            if (banStatus is BanStatus.PermaBanned) {
                _state.value = ChatUiState.Banned(
                    isPermanent = true,
                    message     = "🚫 Your account has been permanently banned."
                )
                return@launch
            }
            if (banStatus is BanStatus.TempBanned) {
                _state.value = ChatUiState.Banned(
                    isPermanent = false,
                    bannedUntil = banStatus.until,
                    message     = "⏸️ You are temporarily banned until ${formatBanUntil(banStatus.until)}."
                )
                return@launch
            }

            // ── Content moderation ────────────────────────────────────────────
            val modResult = ContentModerator.check(text)
            if (!modResult.isSafe) {
                // 7️⃣ Strike system: addStrike handles warn / temp-ban / perm-ban
                val newBanStatus = StrikeManager.addStrike(myUid)
                val current2 = _state.value as? ChatUiState.Ready ?: return@launch
                val newViolationCount = current2.violationCount + 1
                when (newBanStatus) {
                    is BanStatus.PermaBanned -> {
                        _state.value = ChatUiState.Banned(
                            isPermanent = true,
                            message     = "🚫 Your account has been permanently banned for repeated violations."
                        )
                    }
                    is BanStatus.TempBanned -> {
                        _state.value = ChatUiState.Banned(
                            isPermanent = false,
                            bannedUntil = newBanStatus.until,
                            message     = "⏸️ You are temporarily banned until ${formatBanUntil(newBanStatus.until)} due to repeated violations."
                        )
                    }
                    is BanStatus.Warned -> {
                        _state.value = current2.copy(
                            moderationWarning = newBanStatus.message,
                            violationCount    = newViolationCount
                        )
                    }
                    else -> {
                        _state.value = current2.copy(
                            moderationWarning = modResult.warningMessage
                                ?: "⚠️ This message was blocked. Personal, sexual, contact or location content is not allowed.",
                            violationCount = newViolationCount
                        )
                    }
                }
                return@launch
            }

            // ── Safe message — send it ────────────────────────────────────────
            try {
                val data = mapOf(
                    "uid"    to myUid,
                    "text"   to text.trim(),
                    "sentAt" to Timestamp.now()
                )
                db.collection("groups").document(groupId)
                    .collection("chat")
                    .add(data).await()
                Log.d(TAG, "Message sent (#${current.mySentCount + 1})")
            } catch (e: Exception) {
                Log.e(TAG, "send failed", e)
            }
        }
    }


    fun dismissWarning() {
        val current = _state.value as? ChatUiState.Ready ?: return
        _state.value = current.copy(moderationWarning = null)
    }

    // Block a specific member by uid
    fun blockUser(targetUid: String) {
        if (targetUid.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("users").document(myUid)
                    .update("blockedUsers.$targetUid",
                        com.google.firebase.firestore.FieldValue.serverTimestamp())
                    .await()
                Log.d(TAG, "Blocked $targetUid")
            } catch (e: Exception) {
                Log.e(TAG, "blockUser failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatListener?.remove()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlowChatScreen(
    vm       : SlowChatViewModel = viewModel(),
    onBack   : () -> Unit = {},
    onUnlock : () -> Unit = {}    // Navigate to ChatUnlockScreen
) {
    val state    by vm.state.collectAsStateWithLifecycle()
    val members  by vm.members.collectAsStateWithLifecycle()
    val activeGroupId by vm.activeGroupId.collectAsStateWithLifecycle()
    var draft    by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val haptic   = LocalHapticFeedback.current

    // Members safety sheet
    var showMembersSheet by remember { mutableStateOf(false) }
    // Which member is being blocked (shows confirm dialog)
    var blockTarget by remember { mutableStateOf<ChatMember?>(null) }
    // Which member to report (passes to ReportSheet)
    var reportTarget by remember { mutableStateOf<ChatMember?>(null) }

    // Palette — warm, natural, no purple
    val myBlue  = Color(0xFF1C1C1E)   // my bubble: deep charcoal (like iMessage dark mode)
    val teal    = Color(0xFF34C759)   // success/info accent (iOS green, feels trusted)
    val danger  = Color(0xFFEF4444)
    val myBubbleBg   = Color(0xFF1C1C1E)   // sent bubble background
    val theirBubbleBg = Color(0xFFF2F2F7)  // received bubble background (light grey)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ── Beautiful Background Image with Overlay ──────────────────────────
        Image(
            painter = painterResource(id = R.drawable.chat_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Dark overlay to ensure text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E1A).copy(alpha = 0.75f))
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color(0xFF0D1128).copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2A4A).copy(alpha = 0.9f))
                        .border(1.dp, Color(0xFF00D9FF).copy(alpha = 0.3f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color(0xFF00D9FF), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(0.8.dp, Color(0xFF00D9FF).copy(0.3f), CircleShape)
                ) {
                    androidx.compose.foundation.Image(
                        painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_slow_chat),
                        contentDescription = null,
                        modifier     = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Slow Chat",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color(0xFFF8FAFC), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp
                        )
                    )
                    Text(
                        "Day 3  ·  Make every word count",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF00FFB3).copy(alpha = 0.7f), fontSize = 10.sp
                        )
                    )
                }

                // ── Members safety button ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2A4A).copy(alpha = 0.9f))
                        .border(1.dp, Color(0xFF00D9FF).copy(alpha = 0.3f), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMembersSheet = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("👥", fontSize = 16.sp)
                }

                // ── Get messages icon ──────────────────────────────────────────
                val isReady   = state is ChatUiState.Ready
                val msgLimit  = (state as? ChatUiState.Ready)?.maxMessages ?: 7
                val sentCount = (state as? ChatUiState.Ready)?.mySentCount ?: 0
                val isAtLimit = isReady && sentCount >= msgLimit
                if (isReady) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAtLimit) Color(0xFF00D9FF).copy(alpha = 0.2f)
                                else Color(0xFF1E2A4A).copy(alpha = 0.9f)
                            )
                            .border(
                                1.dp,
                                if (isAtLimit) Color(0xFF00D9FF) else Color(0xFF00D9FF).copy(alpha = 0.3f),
                                CircleShape
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onUnlock()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.buy_messages),
                            contentDescription = "Get more messages",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            // Enhanced gradient divider
            Box(
                modifier = Modifier.fillMaxWidth().height(2.dp)
                    .background(Brush.horizontalGradient(
                        listOf(
                            Color.Transparent, 
                            Color(0xFF00D9FF).copy(alpha = 0.5f), 
                            Color(0xFF00FFB3).copy(alpha = 0.5f), 
                            Color(0xFF00D9FF).copy(alpha = 0.5f), 
                            Color.Transparent
                        )
                    ))
            )

            when (val s = state) {
                is ChatUiState.Loading -> {
                    SlowChatSkeletonScreen(myBlue = myBlue, teal = teal)
                }

                is ChatUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(72.dp).clip(CircleShape)
                                    .background(myBlue.copy(0.10f))
                                    .border(1.dp, myBlue.copy(0.20f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Send, null, tint = myBlue.copy(0.5f), modifier = Modifier.size(28.dp))
                            }
                            Text(
                                s.msg,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White.copy(0.60f), textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }

                is ChatUiState.Banned -> {
                    BanWallScreen(
                        bState  = s,
                        accent  = danger,
                        onBack  = onBack
                    )
                }

                is ChatUiState.Ready -> {

                    LaunchedEffect(s.messages.size) {
                        if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.size - 1)
                    }

                    // ── Moderation warning banner ─────────────────────────
                    AnimatedVisibility(
                        visible = s.moderationWarning != null,
                        enter = slideInVertically() + fadeIn(),
                        exit  = slideOutVertically() + fadeOut()
                    ) {
                        s.moderationWarning?.let { warning ->
                            val isRepeated = s.violationCount >= 2
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isRepeated) danger.copy(0.20f)
                                        else Color(0xFFF59E0B).copy(0.15f)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    warning,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isRepeated) danger else Color(0xFFF59E0B),
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "OK",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(0.60f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.clickable { vm.dismissWarning() }.padding(4.dp)
                                )
                            }
                        }
                    }

                    // ── Message quota banner — HIDDEN for now (Day 3 top-up coming soon) ────
                    // Quota tracking logic retained in ViewModel, UI hidden per user request

                    // ── Full limit banner — clean white card, high-trust upsell ─────────
                    AnimatedVisibility(
                        visible = s.mySentCount >= s.maxMessages,
                        enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
                        exit  = slideOutVertically(tween(250)) { -it } + fadeOut(tween(200))
                    ) {
                        if (!s.chatLimitUnlocked) {
                            // ── Upsell card — free tier exhausted ─────────────────────────
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                                    .border(0.dp, Color.Transparent)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onUnlock()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Static icon — no bounce animation
                                    Image(
                                        painter = painterResource(id = R.drawable.buy_messages),
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp)
                                    )

                                    // Text
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "You've used all ${s.maxMessages} free messages",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color(0xFF1C1C1E),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        )
                                        Text(
                                            "Continue the conversation — ₹79 one-time",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF6C6C70),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }

                                    // Tap chip
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1C1C1E))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onUnlock()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Unlock",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                            // Hairline divider below card
                            Box(
                                modifier = Modifier.fillMaxWidth().height(0.5.dp)
                                    .background(Color(0xFFE5E5EA))
                            )
                        } else {
                            // Already unlocked — soft success pill
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color(0xFFF2FAF5))
                                    .padding(horizontal = 20.dp, vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("✓", color = teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        "All ${s.maxMessages} messages sent for this session",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFF3A7D44),
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // ── Messages list ─────────────────────────────────────────────────
                    LazyColumn(
                        state           = listState,
                        modifier        = Modifier.weight(1f),
                        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (s.messages.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxWidth().padding(top = 60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Box(
                                            modifier = Modifier.size(72.dp).clip(CircleShape)
                                                .background(myBlue.copy(0.12f))
                                                .border(1.dp, myBlue.copy(0.25f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Send, null, tint = myBlue.copy(0.6f), modifier = Modifier.size(28.dp))
                                        }
                                        Text(
                                            "Start the conversation",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color(0xFF2D1F0E), fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Text(
                                            "You each have ${s.maxMessages} messages.\nEvery word matters here.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF7A5C3A), textAlign = TextAlign.Center, fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        items(s.messages, key = { it.id }) { msg ->
                            ChatBubble(msg, myBlue, teal)
                        }
                    }

                    // ── Input ────────────────────────────────────────────────────────────
                    val canSend = s.mySentCount < s.maxMessages
                    var showStickerPicker by remember { mutableStateOf(false) }
                    var selectedPackTab  by remember { mutableStateOf(0) }  // 0=Pack1, 1=Pack2, 2=Pack3

                    // ── Sticker picker panel (WhatsApp-style slide-up sheet) ──────────────
                    AnimatedVisibility(
                        visible = showStickerPicker,
                        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
                        exit  = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160))
                    ) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val pack1Stickers = listOf(
                            R.drawable.sticker_pack1_1,  R.drawable.sticker_pack1_2,
                            R.drawable.sticker_pack1_3,  R.drawable.sticker_pack1_4,
                            R.drawable.sticker_pack1_5,  R.drawable.sticker_pack1_6,
                            R.drawable.sticker_pack1_7,  R.drawable.sticker_pack1_8,
                            R.drawable.sticker_pack1_9,  R.drawable.sticker_pack1_10,
                        )
                        val pack1Bought = com.ns.findyourintroverfriend.billing.VeilBillingClient
                            .unlockedStickers.collectAsStateWithLifecycle().value
                            .contains(com.ns.findyourintroverfriend.billing.VeilProductId.STICKER_PACK_1)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(Color(0xFF0B0F1E))
                        ) {
                            // ── Pack tab bar ──────────────────────────────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0D1128))
                                    .padding(horizontal = 16.dp, vertical = 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Pack 1", "Pack 2", "Pack 3").forEachIndexed { idx, label ->
                                    val isSelected = selectedPackTab == idx
                                    val isAvailable = idx == 0  // only pack 1 available
                                    Column(
                                        modifier = Modifier
                                            .clickable { selectedPackTab = idx }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFF8BA4FF) else Color(0xFF5A6480)
                                            )
                                            if (!isAvailable) {
                                                Text("Soon", fontSize = 9.sp,
                                                    color = Color(0xFF8BA4FF).copy(0.5f),
                                                    modifier = Modifier
                                                        .background(Color(0xFF1E2A4A), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                        if (isSelected) {
                                            Spacer(Modifier.height(2.dp))
                                            Box(Modifier.width(24.dp).height(2.dp)
                                                .background(Color(0xFF8BA4FF), RoundedCornerShape(1.dp)))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF1E2A4A), thickness = 0.5.dp)

                            // ── Pack content area ─────────────────────────────────────────
                            Box(modifier = Modifier.fillMaxSize()) {
                                when (selectedPackTab) {
                                    0 -> {
                                        // Pack 1 — real sticker images
                                        if (pack1Bought) {
                                            // Unlocked: show stickers as grid
                                            val rows = pack1Stickers.chunked(5)
                                            androidx.compose.foundation.lazy.LazyColumn(
                                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                items(rows.size) { rowIdx ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        rows[rowIdx].forEach { res ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(60.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(Color.White.copy(0.04f))
                                                                    .clickable {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                        vm.sendSticker("sticker_pack1_${pack1Stickers.indexOf(res) + 1}")
                                                                        showStickerPicker = false
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Image(
                                                                    painter = painterResource(res),
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                                                    contentScale = ContentScale.Crop
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // Locked: show blurred preview + Buy button
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                // Preview row (blurred)
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier
                                                        .alpha(0.25f)
                                                        .padding(bottom = 12.dp)
                                                ) {
                                                    pack1Stickers.take(5).forEach { res ->
                                                        Image(
                                                            painter = painterResource(res),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                }
                                                Text("🔒 Pack 1 — Exclusive Stickers",
                                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                                    color = Color.White.copy(0.85f))
                                                Spacer(Modifier.height(4.dp))
                                                Text("10 stickers · One-time purchase",
                                                    fontSize = 11.sp, color = Color(0xFF8BA4FF).copy(0.7f))
                                                Spacer(Modifier.height(14.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(Color(0xFF3D5CF5), Color(0xFF8BA4FF))
                                                            )
                                                        )
                                                        .clickable {
                                                            val activity = ctx as? android.app.Activity
                                                            activity?.let {
                                                                com.ns.findyourintroverfriend.billing.VeilBillingClient
                                                                    .buySticker(it, com.ns.findyourintroverfriend.billing.VeilProductId.STICKER_PACK_1)
                                                            }
                                                        }
                                                        .padding(horizontal = 28.dp, vertical = 10.dp)
                                                ) {
                                                    Text("Unlock for ₹49", fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                    1, 2 -> {
                                        // Pack 2 & 3 — coming soon
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text("✨", fontSize = 32.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("Pack ${selectedPackTab + 1} coming soon",
                                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(0.7f))
                                            Text("Stay tuned for more exclusive stickers",
                                                fontSize = 11.sp, color = Color(0xFF5A6480))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .background(Color(0xFF0D1128).copy(alpha = 0.92f))
                            .border(1.dp, Color(0xFF00D9FF).copy(alpha = 0.2f), RoundedCornerShape(0.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── Sticker button (leftmost, unlocked users only) ─────────────
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (showStickerPicker) Color(0xFF00D9FF).copy(alpha = 0.3f)
                                        else Color(0xFF1E2A4A).copy(alpha = 0.6f)
                                    )
                                    .border(
                                        1.dp,
                                        if (showStickerPicker) Color(0xFF00D9FF)
                                        else Color(0xFF00D9FF).copy(alpha = 0.2f),
                                        CircleShape
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showStickerPicker = !showStickerPicker
                                        if (showStickerPicker) keyboard?.hide()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎭", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value         = draft,
                            onValueChange = { if (it.length <= 280) draft = it },
                            modifier      = Modifier.weight(1f),
                            enabled       = canSend,
                            placeholder   = {
                                Text(
                                    if (canSend) "Say something meaningful…" else "No messages left",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF00FFB3).copy(alpha = 0.4f))
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Color(0xFF00D9FF),
                                unfocusedBorderColor = Color(0xFF00D9FF).copy(alpha = 0.3f),
                                focusedTextColor     = Color(0xFFF8FAFC),
                                unfocusedTextColor   = Color(0xFFF8FAFC),
                                cursorColor          = Color(0xFF00D9FF),
                                focusedContainerColor   = Color(0xFF1E2A4A).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF1E2A4A).copy(alpha = 0.3f),
                                disabledContainerColor  = Color(0xFF1E2A4A).copy(alpha = 0.2f),
                                disabledBorderColor     = Color(0xFF00D9FF).copy(alpha = 0.15f),
                                disabledTextColor       = Color(0xFF00FFB3).copy(alpha = 0.3f)
                            ),
                            shape          = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (canSend && draft.isNotBlank()) { vm.send(draft); draft = ""; keyboard?.hide() }
                            }),
                            maxLines = 4
                        )
                        Spacer(Modifier.width(10.dp))
                        val sendReady = canSend && draft.isNotBlank()
                        val sendBgBrush = if (sendReady) {
                            Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF00FFB3)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF1E2A4A).copy(alpha = 0.5f), Color(0xFF1E2A4A).copy(alpha = 0.5f)))
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(sendBgBrush)
                                .clickable(enabled = sendReady) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.send(draft); draft = ""; keyboard?.hide()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Send,
                                contentDescription = "Send",
                                tint = if (sendReady) Color(0xFF0A0E1A) else Color(0xFF00FFB3).copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Members Safety Sheet ─────────────────────────────────────────────────
    // Wrapping Box provides the BoxScope needed for bottom-alignment
    if (showMembersSheet) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.55f))
                .clickable { showMembersSheet = false },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Sheet panel (stops click from propagating to scrim)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF131625), Color(0xFF0D1030))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 20.dp)
                    .clickable(enabled = false) {}   // absorb clicks so scrim doesn't fire
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Sheet handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .width(40.dp).height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.18f))
                    )
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "👥  Group Members",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                        )
                    )
                    Text(
                        "Pick a member to block or report",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(0.35f), fontSize = 10.sp
                        )
                    )

                    Spacer(Modifier.height(14.dp))

                    if (members.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color    = myBlue,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        members.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(0.04f))
                                    .border(0.6.dp, Color.White.copy(0.09f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(myBlue.copy(0.14f))
                                        .border(0.7.dp, myBlue.copy(0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(member.emoji, fontSize = 18.sp)
                                }
                                // Name
                                Text(
                                    member.alias,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(0.88f),
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                // Report button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF59E0B).copy(0.12f))
                                        .border(0.6.dp, Color(0xFFF59E0B).copy(0.30f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showMembersSheet = false
                                            reportTarget = member
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "⚠️ Report",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFF59E0B),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                // Block button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(danger.copy(0.12f))
                                        .border(0.6.dp, danger.copy(0.30f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showMembersSheet = false
                                            blockTarget = member
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "🚫 Block",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = danger,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showMembersSheet = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    ) {
                        Text("Cancel", color = Color.White.copy(0.4f), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // ── Block confirmation dialog for named member ─────────────────────────────────────
    blockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            containerColor    = Color(0xFF1A1A2E),
            titleContentColor = Color(0xFFF0F4FA),
            textContentColor  = Color(0xFF94A3B8),
            title = {
                Text(
                    "🚫 Block ${target.emoji} ${target.alias}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Blocking ${target.alias} will prevent them from interacting with you. " +
                    "This action is stored securely and cannot be undone here.\n\nAre you sure?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.blockUser(target.uid)
                    blockTarget = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }) {
                    Text("Block ${target.alias}", color = danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTarget = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // ── Report sheet for named member ─────────────────────────────────────────────────────
    reportTarget?.let { target ->
        ReportSheet(
            targetUid   = target.uid,
            targetAlias = target.alias,
            targetEmoji = target.emoji,
            groupId     = activeGroupId,           // ← was "" — now uses real groupId
            onDismiss   = { reportTarget = null }
        )
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, myBlue: Color, teal: Color) {
    val timeStr = remember(msg.sentAt) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(msg.sentAt.toDate())
    }

    // ── Bubble color palette — enhanced for dark background with image ──────
    // My bubble:    Cyan gradient with glow
    // Their bubble: Dark semi-transparent with white text
    val myBg    = Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF00B8D4)))
    val theirBg = Color(0xFF1E2A4A).copy(alpha = 0.85f)
    val myShape = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    val theirShape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)

    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom
    ) {
        // ── Their avatar ─────────────────────────────────────────────────────
        if (!msg.isMe) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2A4A).copy(alpha = 0.7f))
                    .border(1.dp, Color(0xFF00FFB3).copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(msg.emoji, fontSize = 15.sp) }
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start) {

            // ── Sender name (others only) ─────────────────────────────────────
            if (!msg.isMe) {
                Text(
                    msg.alias,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF00FFB3).copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }


            // ── Sticker image or text bubble ────────────────────────────────────
            if (msg.isSticker && msg.text.startsWith("sticker_")) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val drawableId = remember(msg.text) {
                    context.resources.getIdentifier(msg.text, "drawable", context.packageName)
                }
                if (drawableId != 0) {
                    Image(
                        painter = painterResource(drawableId),
                        contentDescription = "Sticker",
                        modifier = Modifier.size(110.dp).clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(msg.text, fontSize = 36.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(if (msg.isMe) myShape else theirShape)
                        .then(
                            if (msg.isMe) Modifier.background(myBg)
                            else Modifier.background(theirBg)
                                .border(1.dp, Color(0xFF00D9FF).copy(alpha = 0.2f), theirShape)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .widthIn(max = 260.dp)
                ) {
                    Text(
                        msg.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (msg.isMe) Color(0xFF0A0E1A) else Color(0xFFF8FAFC),
                            lineHeight = 20.sp, fontSize = 14.sp,
                            fontWeight = if (msg.isMe) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                }
            }

            // ── Timestamp ────────────────────────────────────────────────────
            Text(
                timeStr,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF00FFB3).copy(alpha = 0.5f),
                    fontSize = 9.sp
                ),
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
            )
        }

        // ── My avatar ────────────────────────────────────────────────────────
        if (msg.isMe) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF00FFB3)))
                    )
                    .border(1.dp, Color(0xFF00D9FF).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(msg.emoji, fontSize = 15.sp) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ban Wall — full-screen hard block shown when user is banned
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BanWallScreen(
    bState : ChatUiState.Banned,
    accent : Color,
    onBack : () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "banPulse")
    val pulse by inf.animateFloat(
        initialValue  = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1A0000), Color(0xFF0D0000), Color(0xFF000000)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 36.dp)
        ) {
            // Pulsing ban icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(accent.copy(0.15f))
                    .border(2.dp,
                        Brush.sweepGradient(listOf(accent.copy(0.8f), accent.copy(0.2f), accent.copy(0.8f))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (bState.isPermanent) "🚫" else "⏸️", fontSize = 42.sp)
            }

            // Ban type label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(0.15f))
                    .border(0.6.dp, accent.copy(0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    if (bState.isPermanent) "PERMANENT BAN" else "TEMPORARY BAN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accent, fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp, letterSpacing = 1.5.sp
                    )
                )
            }

            // Main message
            Text(
                bState.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(0.80f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            )

            if (!bState.isPermanent) {
                Text(
                    "The chat will automatically unlock once your ban expires.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(0.40f),
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                )
            } else {
                Text(
                    "This ban is permanent and cannot be reversed by the user. Contact support if you believe this was a mistake.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(0.40f),
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // Back button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.06f))
                    .border(0.6.dp, Color.White.copy(0.12f), RoundedCornerShape(14.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 32.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "← Go Back",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White.copy(0.65f),
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}


