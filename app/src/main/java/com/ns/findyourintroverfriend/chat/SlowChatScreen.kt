package com.ns.findyourintroverfriend.chat

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.ns.findyourintroverfriend.BuildConfig
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.data.GroupFields
import com.ns.findyourintroverfriend.report.ReportSheet
import com.ns.findyourintroverfriend.ui.SlowChatSkeletonScreen
import com.ns.findyourintroverfriend.ui.theme.*
import com.ns.findyourintroverfriend.util.BanStatus
import com.ns.findyourintroverfriend.util.CloudinaryUploader
import com.ns.findyourintroverfriend.util.ContentModerator
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.StrikeManager
import com.ns.findyourintroverfriend.util.VeilThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

private var _chatDark by androidx.compose.runtime.mutableStateOf(VeilThemeManager.initialResolvedDark)

private const val TAG             = "SlowChat"
private const val DEFAULT_MAX_MSG = 7

// ─────────────────────────────────────────────────────────────────────────────
//  Model
// ─────────────────────────────────────────────────────────────────────────────

data class ChatMessage(
    val id           : String,
    val uid          : String,
    val alias        : String,
    val emoji        : String,
    val text         : String,
    val sentAt       : Timestamp,
    val isMe         : Boolean,
    val isSticker    : Boolean = false,
    val imageUrl     : String  = "",
    val replyToMsgId : String  = "",
    val replyToText  : String  = "",
    val replyToAlias : String  = ""
)

data class ChatMember(
    val uid   : String,
    val alias : String,
    val emoji : String
)

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Ready(
        val messages          : List<ChatMessage>,
        val mySentCount       : Int,
        val groupDay          : Int,
        val maxMessages       : Int,
        val chatLimitUnlocked : Boolean,
        val groupMembers      : List<ChatMember> = emptyList(),
        val moderationWarning : String? = null,
        val violationCount    : Int = 0
    ) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
    data class Banned(
        val isPermanent : Boolean,
        val bannedUntil : Date? = null,
        val message     : String
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
    val dynamicMaxMsg: StateFlow<Int>      = _dynamicMaxMsg.asStateFlow()
    val chatLimitUnlocked: StateFlow<Boolean> = _chatLimitUnlocked.asStateFlow()

    private var groupId = ""
    private var myUid   = ""
    private var chatListener: ListenerRegistration? = null

    private val _groupId = MutableStateFlow("")
    val activeGroupId: StateFlow<String> = _groupId.asStateFlow()

    private val _members = MutableStateFlow<List<ChatMember>>(emptyList())
    val members: StateFlow<List<ChatMember>> = _members.asStateFlow()

    init { load() }

    private fun load() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = ChatUiState.Error("Not signed in."); return
        }
        myUid = uid
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()

                val banStatus = StrikeManager.checkBanStatus(uid)
                if (banStatus is BanStatus.PermaBanned) {
                    _state.value = ChatUiState.Banned(
                        isPermanent = true,
                        message     = "🚫 Your account has been permanently banned due to repeated violations."
                    ); return@launch
                }
                if (banStatus is BanStatus.TempBanned) {
                    _state.value = ChatUiState.Banned(
                        isPermanent = false,
                        bannedUntil = banStatus.until,
                        message     = "⏸️ You are temporarily banned until ${formatBanUntil(banStatus.until)}."
                    ); return@launch
                }

                groupId = userDoc.getString("groupId") ?: ""
                _groupId.value = groupId
                if (groupId.isBlank()) {
                    _state.value = ChatUiState.Error("No active group."); return@launch
                }

                val chatLimitUnlocked = userDoc.getBoolean("chatLimitUnlocked") ?: false
                val msgLimit = (userDoc.getLong("chatMsgLimit") ?: DEFAULT_MAX_MSG.toLong()).toInt()
                _dynamicMaxMsg.value     = msgLimit
                _chatLimitUnlocked.value = chatLimitUnlocked

                val groupDoc = db.collection("groups").document(groupId).get().await()
                val day = (groupDoc.getLong("day") ?: 1L).toInt()

                if (day < 2) {
                    _state.value = ChatUiState.Error("Chat unlocks on Day 2 🌙"); return@launch
                }

                listenToChat(day)
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.value = ChatUiState.Error(e.localizedMessage ?: "Failed to load chat.")
            }
        }
    }

    private fun formatBanUntil(date: Date): String =
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date)

    private suspend fun loadGroupMembers() {
        try {
            val groupDoc = db.collection("groups").document(groupId).get().await()
            @Suppress("UNCHECKED_CAST")
            val uids = groupDoc.get(GroupFields.MEMBERS) as? List<String> ?: emptyList()
            _members.value = uids.filter { it != myUid }.mapNotNull { uid ->
                try {
                    val doc = db.collection("users").document(uid).get().await()
                    ChatMember(uid, doc.getString("alias") ?: "Someone", doc.getString("emoji") ?: "🌙")
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { }
    }

    private fun listenToChat(day: Int) {
        viewModelScope.launch { loadGroupMembers() }
        chatListener = db.collection("groups").document(groupId)
            .collection("chat")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                viewModelScope.launch {
                    try {
                        val freshUser = db.collection("users").document(myUid).get().await()
                        _dynamicMaxMsg.value     = (freshUser.getLong("chatMsgLimit") ?: DEFAULT_MAX_MSG.toLong()).toInt()
                        _chatLimitUnlocked.value = freshUser.getBoolean("chatLimitUnlocked") ?: false
                    } catch (_: Exception) { }

                    val messages = snap.documents.mapNotNull { doc ->
                        val uid      = doc.getString("uid")  ?: return@mapNotNull null
                        val text     = doc.getString("text") ?: ""
                        val sentAt   = doc.getTimestamp("sentAt") ?: return@mapNotNull null
                        val alias    = try { db.collection("users").document(uid).get().await().getString("alias") ?: "Someone" } catch (_: Exception) { "Someone" }
                        val emoji    = try { db.collection("users").document(uid).get().await().getString("emoji")  ?: "🌙" } catch (_: Exception) { "🌙" }
                        ChatMessage(
                            id           = doc.id,
                            uid          = uid,
                            alias        = alias,
                            emoji        = emoji,
                            text         = text,
                            sentAt       = sentAt,
                            isMe         = uid == myUid,
                            isSticker    = doc.getBoolean("isSticker")    ?: false,
                            imageUrl     = doc.getString("imageUrl")      ?: "",
                            replyToMsgId = doc.getString("replyToMsgId") ?: "",
                            replyToText  = doc.getString("replyToText")  ?: "",
                            replyToAlias = doc.getString("replyToAlias") ?: ""
                        )
                    }
                    val mySent  = messages.count { it.isMe }
                    val current = _state.value as? ChatUiState.Ready
                    _state.value = ChatUiState.Ready(
                        messages          = messages,
                        mySentCount       = mySent,
                        groupDay          = day,
                        maxMessages       = _dynamicMaxMsg.value,
                        chatLimitUnlocked = _chatLimitUnlocked.value,
                        groupMembers      = _members.value,
                        moderationWarning = current?.moderationWarning,
                        violationCount    = current?.violationCount ?: 0
                    )
                }
            }
    }

    fun sendSticker(emoji: String) {
        val current = _state.value as? ChatUiState.Ready ?: return
        if (current.mySentCount >= current.maxMessages) return
        if (!current.chatLimitUnlocked) return
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId).collection("chat").add(
                    mapOf("uid" to myUid, "text" to emoji, "sentAt" to Timestamp.now(), "isSticker" to true)
                ).await()
            } catch (e: Exception) { Log.e(TAG, "sendSticker failed", e) }
        }
    }

    fun send(
        text         : String,
        replyToMsgId : String = "",
        replyToText  : String = "",
        replyToAlias : String = ""
    ) {
        val current = _state.value as? ChatUiState.Ready ?: return
        if (current.mySentCount >= current.maxMessages || text.isBlank()) return

        val safeText = ContentModerator.maskPhoneNumbers(text)

        viewModelScope.launch {
            val banStatus = StrikeManager.checkBanStatus(myUid)
            if (banStatus is BanStatus.PermaBanned) {
                _state.value = ChatUiState.Banned(true, message = "🚫 Permanently banned."); return@launch
            }
            if (banStatus is BanStatus.TempBanned) {
                _state.value = ChatUiState.Banned(false, banStatus.until, "⏸️ Temporarily banned until ${formatBanUntil(banStatus.until)}."); return@launch
            }

            val modResult = ContentModerator.check(safeText)
            if (!modResult.isSafe) {
                val newBan     = StrikeManager.addStrike(myUid)
                val current2   = _state.value as? ChatUiState.Ready ?: return@launch
                val newCount   = current2.violationCount + 1
                when (newBan) {
                    is BanStatus.PermaBanned -> _state.value = ChatUiState.Banned(true, message = "🚫 Permanently banned for repeated violations.")
                    is BanStatus.TempBanned  -> _state.value = ChatUiState.Banned(false, newBan.until, "⏸️ Temporarily banned until ${formatBanUntil(newBan.until)}.")
                    is BanStatus.Warned      -> _state.value = current2.copy(moderationWarning = newBan.message, violationCount = newCount)
                    else                     -> _state.value = current2.copy(moderationWarning = modResult.warningMessage ?: "⚠️ Message blocked.", violationCount = newCount)
                }
                return@launch
            }

            try {
                val data = mutableMapOf<String, Any>("uid" to myUid, "text" to safeText.trim(), "sentAt" to Timestamp.now())
                if (replyToMsgId.isNotEmpty()) { data["replyToMsgId"] = replyToMsgId; data["replyToText"] = replyToText; data["replyToAlias"] = replyToAlias }
                db.collection("groups").document(groupId).collection("chat").add(data).await()
            } catch (e: Exception) { Log.e(TAG, "send failed", e) }
        }
    }

    fun sendImage(context: android.content.Context, imageUri: android.net.Uri, replyToMsgId: String = "", replyToText: String = "", replyToAlias: String = "") {
        val current = _state.value as? ChatUiState.Ready ?: return
        if (current.mySentCount >= current.maxMessages) return
        viewModelScope.launch {
            try {
                val cloudName    = BuildConfig.CLOUDINARY_CLOUD_NAME
                val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
                val imageUrl     = CloudinaryUploader.upload(context, imageUri, cloudName, uploadPreset)
                val data = mutableMapOf<String, Any>("uid" to myUid, "text" to "", "imageUrl" to imageUrl, "sentAt" to Timestamp.now())
                if (replyToMsgId.isNotEmpty()) { data["replyToMsgId"] = replyToMsgId; data["replyToText"] = replyToText; data["replyToAlias"] = replyToAlias }
                db.collection("groups").document(groupId).collection("chat").add(data).await()
            } catch (e: Exception) { Log.e(TAG, "sendImage failed", e) }
        }
    }

    fun dismissWarning() {
        val current = _state.value as? ChatUiState.Ready ?: return
        _state.value = current.copy(moderationWarning = null)
    }

    fun blockUser(targetUid: String) {
        if (targetUid.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("users").document(myUid)
                    .update("blockedUsers.$targetUid", com.google.firebase.firestore.FieldValue.serverTimestamp())
                    .await()
            } catch (e: Exception) { Log.e(TAG, "blockUser failed", e) }
        }
    }

    override fun onCleared() { super.onCleared(); chatListener?.remove() }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlowChatScreen(
    vm       : SlowChatViewModel = viewModel(),
    onBack   : () -> Unit = {},
    onUnlock : () -> Unit = {}
) {
    val state         by vm.state.collectAsStateWithLifecycle()
    val members       by vm.members.collectAsStateWithLifecycle()
    val activeGroupId by vm.activeGroupId.collectAsStateWithLifecycle()
    var draft         by remember { mutableStateOf("") }
    val keyboard      = LocalSoftwareKeyboardController.current
    val listState     = rememberLazyListState()
    val haptic        = LocalHapticFeedback.current
    val ctx           = LocalContext.current
    val veilDark      = LocalVeilIsDark.current
    SideEffect { _chatDark = veilDark }

    var showMembersSheet by remember { mutableStateOf(false) }
    var blockTarget      by remember { mutableStateOf<ChatMember?>(null) }
    var reportTarget     by remember { mutableStateOf<ChatMember?>(null) }

    // Reply state
    var replyToMsg        by remember { mutableStateOf<ChatMessage?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showAttachPanel    by remember { mutableStateOf(false) }
    var showStickerPicker  by remember { mutableStateOf(false) }
    var selectedPackTab    by remember { mutableStateOf(0) }

    // Image quota (5 free/day)
    val imgQuotaPrefs = remember { ctx.getSharedPreferences("veil_img_quota_sc", android.content.Context.MODE_PRIVATE) }
    val todayKey      = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) }
    var imgSentToday  by remember {
        mutableStateOf(if (imgQuotaPrefs.getString("date", "") == todayKey) imgQuotaPrefs.getInt("count", 0) else 0)
    }
    var showNoCookiesDialog by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (imgSentToday >= 5) { showNoCookiesDialog = true; return@rememberLauncherForActivityResult }
        val rm = replyToMsg
        vm.sendImage(
            context      = ctx,
            imageUri     = uri,
            replyToMsgId = rm?.id ?: "",
            replyToText  = if ((rm?.imageUrl ?: "").isNotEmpty()) "[Image]" else (rm?.text?.take(60) ?: ""),
            replyToAlias = rm?.alias ?: ""
        )
        imgSentToday += 1
        imgQuotaPrefs.edit().putString("date", todayKey).putInt("count", imgSentToday).apply()
        replyToMsg = null
    }

    // Palette
    val myBubbleBg    = Color(0xFF1C1C1E)
    val theirBubbleBg = Color(0xFFF2F2F7)
    val teal          = Color(0xFF34C759)
    val danger        = Color(0xFFEF4444)
    val accentBlue    = Color(0xFF4A8FBF)

    // Auto-scroll to latest
    LaunchedEffect(state) {
        if (state is ChatUiState.Ready) {
            val msgs = (state as ChatUiState.Ready).messages
            if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1)
        }
    }

    Box(Modifier.fillMaxSize()) {

        // Background
        Image(
            painter = painterResource(id = R.drawable.chat_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize()
                .background(if (_chatDark) Color(0xFF0A0E1A).copy(0.78f) else Color(0xFF1A1E2E).copy(0.55f))
        )

        Column(Modifier.fillMaxSize()) {

            // ── Top bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(if (_chatDark) Color(0xFF0D1128).copy(0.88f) else Color(0xFF111520).copy(0.82f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Color(0xFF1E2A4A).copy(if (_chatDark) 0.9f else 0.75f))
                        .border(1.dp, accentBlue.copy(0.3f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = accentBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).border(0.8.dp, accentBlue.copy(0.3f), CircleShape)
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.mascot_slow_chat),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Slow Chat", style = MaterialTheme.typography.titleMedium.copy(
                        color = Color(0xFFF8FAFC), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
                    Text("Make every word count", style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF4A9E87).copy(0.7f), fontSize = 10.sp))
                }
                // Members safety button
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Color(0xFF1E2A4A).copy(0.7f))
                        .border(1.dp, accentBlue.copy(0.2f), CircleShape)
                        .clickable { showMembersSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("👥", fontSize = 16.sp)
                }
            }

            // ── State handling ─────────────────────────────────────────────────
            when (val s = state) {
                is ChatUiState.Loading -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SlowChatSkeletonScreen(myBubbleBg, teal)
                    }
                }

                is ChatUiState.Error -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🌙", fontSize = 42.sp)
                            Text(s.message, color = Color(0xFFDFDFEA), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp))
                        }
                    }
                }

                is ChatUiState.Banned -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)) {
                            Text(if (s.isPermanent) "🚫" else "⏸️", fontSize = 48.sp)
                            Text(s.message, color = Color(0xFFDFDFEA), textAlign = TextAlign.Center, lineHeight = 22.sp)
                        }
                    }
                }

                is ChatUiState.Ready -> {

                    // Moderation warning banner
                    s.moderationWarning?.let { warning ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Color(0xFFFF6B35).copy(0.15f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️ $warning", color = Color(0xFFFF6B35), fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.dismissWarning() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, null, tint = Color(0xFFFF6B35), modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Message limit banner (when not unlocked and limit reached)
                    if (!s.chatLimitUnlocked && s.mySentCount >= s.maxMessages) {
                        Box(
                            Modifier.fillMaxWidth()
                                .background(if (_chatDark) Color(0xFF102018) else Color(0xFFF2FAF5))
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("✓", color = teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("All ${s.maxMessages} messages sent",
                                    color = if (_chatDark) Color(0xFF4A9E6A) else Color(0xFF3A7D44), fontSize = 12.sp)
                            }
                        }
                    }

                    // ── Messages list with swipe-to-reply ─────────────────────
                    LazyColumn(
                        state           = listState,
                        modifier        = Modifier.weight(1f),
                        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (s.messages.isEmpty()) {
                            item {
                                Box(Modifier.fillParentMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Box(Modifier.size(72.dp).clip(CircleShape)
                                            .background(myBubbleBg.copy(0.12f))
                                            .border(1.dp, myBubbleBg.copy(0.25f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Send, null, tint = myBubbleBg.copy(0.6f), modifier = Modifier.size(28.dp))
                                        }
                                        Text("Start the conversation",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = if (_chatDark) Color(0xFFDFDFEA) else Color(0xFF2D1F0E),
                                                fontWeight = FontWeight.SemiBold))
                                        Text("You each have ${s.maxMessages} messages.\nEvery word matters here.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (_chatDark) Color(0xFFAAAAAE) else Color(0xFF7A5C3A),
                                                textAlign = TextAlign.Center, fontSize = 12.sp))
                                    }
                                }
                            }
                        }

                        items(s.messages, key = { it.id }) { msg ->
                            var swipeX by remember(msg.id) { mutableStateOf(0f) }
                            val animX  by animateFloatAsState(swipeX, spring(stiffness = Spring.StiffnessMedium), label = "swipe")
                            Box(
                                Modifier.fillMaxWidth().offset(x = animX.dp)
                                    .pointerInput(msg.id) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (swipeX > 55f) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); replyToMsg = msg }
                                                swipeX = 0f
                                            },
                                            onDragCancel = { swipeX = 0f },
                                            onHorizontalDrag = { _, delta -> swipeX = (swipeX + delta * 0.4f).coerceIn(0f, 75f) }
                                        )
                                    }
                            ) {
                                ChatBubble(msg, myBubbleBg, teal) { url -> fullScreenImageUrl = url }
                            }
                        }
                    }

                    val canSend = s.mySentCount < s.maxMessages

                    // ── Sticker picker ─────────────────────────────────────────
                    AnimatedVisibility(showStickerPicker,
                        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
                        exit  = slideOutVertically(tween(220)) { it } + fadeOut(tween(160))
                    ) {
                        val pack1Stickers = listOf(
                            R.drawable.sticker_pack1_1, R.drawable.sticker_pack1_2,
                            R.drawable.sticker_pack1_3, R.drawable.sticker_pack1_4,
                            R.drawable.sticker_pack1_5, R.drawable.sticker_pack1_6,
                            R.drawable.sticker_pack1_7, R.drawable.sticker_pack1_8,
                            R.drawable.sticker_pack1_9, R.drawable.sticker_pack1_10
                        )
                        val pack1Bought = com.ns.findyourintroverfriend.billing.VeilBillingClient
                            .unlockedStickers.collectAsStateWithLifecycle().value
                            .contains(com.ns.findyourintroverfriend.billing.VeilProductId.STICKER_PACK_1)

                        Column(
                            Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF0B0F1E))
                        ) {
                            // Tab bar
                            Row(Modifier.fillMaxWidth().background(Color(0xFF0D1128)).padding(horizontal = 16.dp)) {
                                listOf("Pack 1", "Pack 2", "Pack 3").forEachIndexed { idx, label ->
                                    Column(
                                        Modifier.clickable { selectedPackTab = idx }.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(label, fontSize = 12.sp,
                                            fontWeight = if (selectedPackTab == idx) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selectedPackTab == idx) Color(0xFF8BA4FF) else Color(0xFF5A6480))
                                        if (selectedPackTab == idx) {
                                            Spacer(Modifier.height(2.dp))
                                            Box(Modifier.width(24.dp).height(2.dp).background(Color(0xFF8BA4FF), RoundedCornerShape(1.dp)))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF1E2A4A), thickness = 0.5.dp)

                            Box(Modifier.fillMaxSize()) {
                                when (selectedPackTab) {
                                    0 -> if (pack1Bought) {
                                        LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(pack1Stickers.chunked(5).size) { rowIdx ->
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    pack1Stickers.chunked(5)[rowIdx].forEach { res ->
                                                        Box(
                                                            Modifier.size(60.dp).clip(RoundedCornerShape(10.dp))
                                                                .background(Color.White.copy(0.04f))
                                                                .clickable {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    vm.sendSticker("sticker_pack1_${pack1Stickers.indexOf(res) + 1}")
                                                                    showStickerPicker = false
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            androidx.compose.foundation.Image(
                                                                painter = painterResource(res), contentDescription = null,
                                                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text("🔒 Pack 1 — Exclusive Stickers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(0.85f))
                                            Spacer(Modifier.height(14.dp))
                                            Box(
                                                Modifier.clip(RoundedCornerShape(20.dp))
                                                    .background(Brush.horizontalGradient(listOf(Color(0xFF3D5CF5), Color(0xFF8BA4FF))))
                                                    .clickable {
                                                        val activity = ctx as? android.app.Activity
                                                        activity?.let {
                                                            com.ns.findyourintroverfriend.billing.VeilBillingClient
                                                                .buySticker(it, com.ns.findyourintroverfriend.billing.VeilProductId.STICKER_PACK_1)
                                                        }
                                                    }
                                                    .padding(horizontal = 28.dp, vertical = 10.dp)
                                            ) {
                                                Text("Unlock for ₹49", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                    else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("✨", fontSize = 32.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Pack ${selectedPackTab + 1} coming soon", fontSize = 14.sp, color = Color.White.copy(0.7f))
                                    }
                                }
                            }
                        }
                    }

                    // ── Attachment panel ───────────────────────────────────────
                    AnimatedVisibility(showAttachPanel,
                        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
                        exit  = slideOutVertically(tween(180)) { it } + fadeOut(tween(130))
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Color(0xFF0B0F1E))
                                .border(BorderStroke(0.5.dp, accentBlue.copy(0.2f)))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Box(Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(3.dp)
                                .clip(RoundedCornerShape(2.dp)).background(accentBlue.copy(0.3f)))
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                // Stickers
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.clickable { showAttachPanel = false; showStickerPicker = true; keyboard?.hide() }) {
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1E2A4A))
                                        .border(0.5.dp, accentBlue.copy(0.3f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                        Text("🎭", fontSize = 24.sp)
                                    }
                                    Text("Stickers", fontSize = 10.sp, color = accentBlue)
                                }
                                // Photo
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.clickable { showAttachPanel = false; imageLauncher.launch("image/*") }) {
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1E2A4A))
                                        .border(0.5.dp, accentBlue.copy(0.3f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                        Text("🖼", fontSize = 24.sp)
                                    }
                                    Text(if (imgSentToday < 5) "Photo (${5 - imgSentToday} free)" else "Photo (5🍪)",
                                        fontSize = 10.sp, color = accentBlue, textAlign = TextAlign.Center)
                                }
                                // Audio (soon)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.alpha(0.35f)) {
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1E2A4A))
                                        .border(0.5.dp, accentBlue.copy(0.15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                        Text("🎵", fontSize = 24.sp)
                                    }
                                    Text("Audio\n(Soon)", fontSize = 10.sp, color = accentBlue, textAlign = TextAlign.Center)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    // ── Reply banner ───────────────────────────────────────────
                    AnimatedVisibility(replyToMsg != null,
                        enter = slideInVertically(tween(180)) { it } + fadeIn(tween(140)),
                        exit  = slideOutVertically(tween(140)) { it } + fadeOut(tween(100))
                    ) {
                        replyToMsg?.let { replied ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(Color(0xFF0D1128).copy(0.94f))
                                    .border(BorderStroke(0.5.dp, accentBlue.copy(0.25f))),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(3.dp).height(50.dp).background(accentBlue))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(replied.alias, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentBlue)
                                    val snippet = if (replied.imageUrl.isNotEmpty()) "📷 Image" else replied.text.take(55).replace('\n', ' ')
                                    Text(snippet, fontSize = 11.sp, color = Color(0xFFAAAAAA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { replyToMsg = null }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.Close, null, tint = accentBlue, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // ── Input row ─────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .background(Color(0xFF0D1128).copy(0.92f))
                            .border(1.dp, accentBlue.copy(0.2f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attachment (+) button
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (showAttachPanel) accentBlue.copy(0.3f) else Color(0xFF1E2A4A).copy(0.6f))
                                .border(1.dp, if (showAttachPanel) accentBlue else accentBlue.copy(0.2f), CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAttachPanel = !showAttachPanel
                                    showStickerPicker = false
                                    if (showAttachPanel) keyboard?.hide()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = accentBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))

                        OutlinedTextField(
                            value         = draft,
                            onValueChange = { if (it.length <= 280) draft = it },
                            modifier      = Modifier.weight(1f),
                            enabled       = canSend,
                            placeholder   = {
                                Text(if (canSend) "Say something meaningful…" else "No messages left",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF4A9E87).copy(0.4f)))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = accentBlue,
                                unfocusedBorderColor    = accentBlue.copy(0.3f),
                                focusedTextColor        = Color(0xFFF8FAFC),
                                unfocusedTextColor      = Color(0xFFF8FAFC),
                                cursorColor             = accentBlue,
                                focusedContainerColor   = Color(0xFF1E2A4A).copy(0.5f),
                                unfocusedContainerColor = Color(0xFF1E2A4A).copy(0.3f),
                                disabledContainerColor  = Color(0xFF1E2A4A).copy(0.2f),
                                disabledBorderColor     = accentBlue.copy(0.15f),
                                disabledTextColor       = Color(0xFF4A9E87).copy(0.3f)
                            ),
                            shape           = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (canSend && draft.isNotBlank()) {
                                    val rm = replyToMsg; replyToMsg = null
                                    vm.send(draft, rm?.id ?: "", if ((rm?.imageUrl ?: "").isNotEmpty()) "[Image]" else (rm?.text?.take(60) ?: ""), rm?.alias ?: "")
                                    draft = ""; keyboard?.hide()
                                }
                            }),
                            maxLines = 4
                        )

                        Spacer(Modifier.width(10.dp))
                        val sendReady = canSend && draft.isNotBlank()
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(if (sendReady) Brush.linearGradient(listOf(accentBlue, Color(0xFF4A9E87)))
                                            else Brush.linearGradient(listOf(Color(0xFF1E2A4A).copy(0.5f), Color(0xFF1E2A4A).copy(0.5f))))
                                .clickable(enabled = sendReady) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val rm = replyToMsg; replyToMsg = null
                                    vm.send(draft, rm?.id ?: "", if ((rm?.imageUrl ?: "").isNotEmpty()) "[Image]" else (rm?.text?.take(60) ?: ""), rm?.alias ?: "")
                                    draft = ""; keyboard?.hide()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Send, "Send",
                                tint = if (sendReady) Color(0xFF0A0E1A) else Color(0xFF4A9E87).copy(0.3f),
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // ── No-cookies dialog ──────────────────────────────────────────────────
        if (showNoCookiesDialog) {
            AlertDialog(
                onDismissRequest = { showNoCookiesDialog = false },
                shape = RoundedCornerShape(16.dp),
                title = { Text("📷 Daily limit reached", fontWeight = FontWeight.Bold) },
                text  = { Text("You've used your 5 free image sends today. Limit resets tomorrow.", fontSize = 13.sp, lineHeight = 20.sp) },
                confirmButton = { TextButton(onClick = { showNoCookiesDialog = false }) { Text("Got it") } },
                dismissButton = null
            )
        }

        // ── Full-screen image viewer ───────────────────────────────────────────
        fullScreenImageUrl?.let { imgUrl ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.92f)).clickable { fullScreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imgUrl, contentDescription = "Full image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                )
                Text("Tap to close", fontSize = 11.sp, color = Color.White.copy(0.4f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
            }
        }

        // ── Members safety sheet ───────────────────────────────────────────────
        if (showMembersSheet) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { showMembersSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF131625), Color(0xFF0D1030))))
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                            .width(40.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(0.18f)))
                        Spacer(Modifier.height(16.dp))
                        Text("👥  Group Members", style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp))
                        Text("Pick a member to block or report", style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(0.35f), fontSize = 10.sp))
                        Spacer(Modifier.height(14.dp))
                        if (members.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = accentBlue, strokeWidth = 2.dp)
                            }
                        } else {
                            members.forEach { member ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(0.04f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(member.emoji, fontSize = 20.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(member.alias, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    // Report button
                                    Box(Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(danger.copy(0.08f)).border(0.6.dp, danger.copy(0.25f), RoundedCornerShape(10.dp))
                                        .clickable { showMembersSheet = false; reportTarget = member }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text("Report", fontSize = 11.sp, color = danger, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    // Block button
                                    Box(Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(danger.copy(0.12f)).border(0.6.dp, danger.copy(0.30f), RoundedCornerShape(10.dp))
                                        .clickable { showMembersSheet = false; blockTarget = member }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text("Block", fontSize = 11.sp, color = danger, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }

        // Block confirm dialog
        blockTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { blockTarget = null },
                shape = RoundedCornerShape(16.dp),
                title = { Text("Block ${target.alias}?", fontWeight = FontWeight.Bold) },
                text  = { Text("They won't be able to see your messages and you won't see theirs.", fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = { vm.blockUser(target.uid); blockTarget = null }) {
                        Text("Block", color = danger, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { blockTarget = null }) { Text("Cancel") }
                }
            )
        }

        // Report sheet
        reportTarget?.let { target ->
            ReportSheet(
                targetUid   = target.uid,
                targetAlias = target.alias,
                targetEmoji = target.emoji,
                groupId     = activeGroupId,
                onDismiss   = { reportTarget = null }
            )
        }

    } // end Box
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chat bubble composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(
    msg          : ChatMessage,
    myBubbleBg   : Color,
    teal         : Color,
    onImageClick : (String) -> Unit = {}
) {
    val timeStr = remember(msg.sentAt) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(msg.sentAt.toDate())
    }

    val myBg      = Brush.linearGradient(listOf(Color(0xFF4A8FBF), Color(0xFF00B8D4)))
    val theirBg   = Color(0xFF1E2A4A).copy(0.85f)
    val myShape   = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    val theirShape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)

    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom
    ) {
        if (!msg.isMe) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF1E2A4A).copy(0.7f))
                .border(1.dp, Color(0xFF4A9E87).copy(0.3f), CircleShape), contentAlignment = Alignment.Center) {
                Text(msg.emoji, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start) {
            if (!msg.isMe) {
                Text(msg.alias, style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF4A9E87).copy(0.8f), fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
            }

            // Reply quote card
            if (msg.replyToAlias.isNotEmpty()) {
                val accentC = if (msg.isMe) Color(0xFF00B8D4).copy(0.8f) else Color(0xFF4A9E87).copy(0.8f)
                val bgC     = if (msg.isMe) Color(0xFF003040) else Color(0xFF131E36)
                Row(Modifier.widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(bgC)) {
                    Box(Modifier.width(3.dp).height(40.dp).background(accentC))
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(msg.replyToAlias, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentC, maxLines = 1)
                        Text(msg.replyToText.ifEmpty { "…" }, fontSize = 11.sp, color = Color(0xFFAAAAAA),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            // Bubble content
            when {
                msg.isSticker && msg.text.startsWith("sticker_") -> {
                    val drawableId = remember(msg.text) {
                        val c = androidx.compose.ui.platform.LocalContext
                        0 // resolved below via LocalContext
                    }
                    val context = LocalContext.current
                    val resId = remember(msg.text) {
                        context.resources.getIdentifier(msg.text, "drawable", context.packageName)
                    }
                    if (resId != 0) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(resId), contentDescription = "Sticker",
                            modifier = Modifier.size(110.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit)
                    } else {
                        Text(msg.text, fontSize = 36.sp)
                    }
                }
                msg.imageUrl.isNotEmpty() -> {
                    AsyncImage(
                        model = msg.imageUrl, contentDescription = "Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.widthIn(max = 200.dp).aspectRatio(1f)
                            .clip(if (msg.isMe) myShape else theirShape)
                            .clickable { onImageClick(msg.imageUrl) }
                    )
                }
                else -> {
                    Box(
                        Modifier.clip(if (msg.isMe) myShape else theirShape)
                            .then(if (msg.isMe) Modifier.background(myBg)
                                  else Modifier.background(theirBg).border(1.dp, Color(0xFF4A8FBF).copy(0.2f), theirShape))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .widthIn(max = 260.dp)
                    ) {
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (msg.isMe) Color(0xFF0A0E1A) else Color(0xFFF8FAFC),
                            lineHeight = 20.sp, fontSize = 14.sp,
                            fontWeight = if (msg.isMe) FontWeight.SemiBold else FontWeight.Normal))
                    }
                }
            }

            Text(timeStr, style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF4A9E87).copy(0.5f), fontSize = 9.sp),
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp))
        }

        if (msg.isMe) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF1E2A4A).copy(0.7f))
                .border(1.dp, Color(0xFF4A8FBF).copy(0.3f), CircleShape), contentAlignment = Alignment.Center) {
                Text(msg.emoji, fontSize = 15.sp)
            }
        }
    }
}
