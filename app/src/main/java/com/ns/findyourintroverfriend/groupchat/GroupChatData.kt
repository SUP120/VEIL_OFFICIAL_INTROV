package com.ns.findyourintroverfriend.groupchat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.ns.findyourintroverfriend.notifications.VeilNotifier
import com.ns.findyourintroverfriend.util.ContentModerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

private const val TAG            = "GroupChat"
private const val ROOMS_COL      = "group_rooms"
private const val MESSAGES_COL   = "messages"
internal const val MONTHLY_LIMIT = 3

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

data class GroupRoom(
    val id          : String,
    val name        : String,
    val description : String,
    val createdBy   : String,
    val createdAt   : Timestamp,
    val memberCount : Int,
    val memberUids  : List<String>,
    val pendingUids : List<String>,
    val isSystem    : Boolean,
    val joinMode    : String,   // "auto" | "approve"
    val lastMsg     : String,
    val lastMsgAt   : Timestamp?
)

data class GroupMessage(
    val id           : String,
    val uid          : String,
    val alias        : String,
    val text         : String,
    val sentAt       : Timestamp,
    val replyToId    : String = "",
    val replyToAlias : String = "",
    val replyToText  : String = ""
)

data class GroupInviteUser(
    val uid      : String,
    val alias    : String,
    val source   : String,   // "friend" | "swipe"
    val photoUrl : String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
//  GroupRoomsViewModel  — manages the room list + creation
// ─────────────────────────────────────────────────────────────────────────────

class GroupRoomsViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    val myUid get() = auth.currentUser?.uid ?: ""

    // Room list state
    private val _rooms   = MutableStateFlow<List<GroupRoom>>(emptyList())
    val rooms: StateFlow<List<GroupRoom>> = _rooms.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Create-room state
    private val _creating      = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val _createError   = MutableStateFlow<String?>(null)
    val createError: StateFlow<String?> = _createError.asStateFlow()

    private val _monthlyUsed   = MutableStateFlow(0)
    val monthlyUsed: StateFlow<Int> = _monthlyUsed.asStateFlow()

    // My alias (for invite DMs)
    private val _myAlias = MutableStateFlow("")
    val myAlias: StateFlow<String> = _myAlias.asStateFlow()

    private var roomsListener: ListenerRegistration? = null

    init {
        loadMyAlias()
        listenRooms()
        loadMonthlyCount()
    }

    private fun loadMyAlias() {
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(myUid).get().await()
                _myAlias.value = doc.getString("alias") ?: "Someone"
            } catch (_: Exception) { }
        }
    }

    private fun listenRooms() {
        _loading.value = true
        roomsListener = db.collection(ROOMS_COL)
            .orderBy("memberCount", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    _loading.value = false; return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { d ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        GroupRoom(
                            id          = d.id,
                            name        = d.getString("name")        ?: return@mapNotNull null,
                            description = d.getString("description") ?: "",
                            createdBy   = d.getString("createdBy")   ?: "",
                            createdAt   = d.getTimestamp("createdAt") ?: Timestamp.now(),
                            memberCount = (d.getLong("memberCount") ?: 0L).toInt(),
                            memberUids  = d.get("memberUids")  as? List<String> ?: emptyList(),
                            pendingUids = d.get("pendingUids") as? List<String> ?: emptyList(),
                            isSystem    = d.getBoolean("isSystem") ?: false,
                            joinMode    = d.getString("joinMode") ?: "auto",
                            lastMsg     = d.getString("lastMsg")  ?: "",
                            lastMsgAt   = d.getTimestamp("lastMsgAt")
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "parse room ${d.id}", e); null
                    }
                }
                // Pin system room to top
                _rooms.value = list.sortedWith(
                    compareByDescending<GroupRoom> { it.isSystem }
                        .thenByDescending { it.memberCount }
                )
                _loading.value = false
            }
    }

    private fun loadMonthlyCount() {
        viewModelScope.launch {
            try {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                val monthStart = Timestamp(cal.time)
                val snap = db.collection(ROOMS_COL)
                    .whereEqualTo("createdBy", myUid)
                    .whereGreaterThanOrEqualTo("createdAt", monthStart)
                    .get().await()
                _monthlyUsed.value = snap.size()
            } catch (_: Exception) { }
        }
    }

    fun createRoom(name: String, description: String, joinMode: String) {
        if (_creating.value) return
        val trimName = name.trim()
        if (trimName.isBlank()) { _createError.value = "Room name is required."; return }
        if (_monthlyUsed.value >= MONTHLY_LIMIT) {
            _createError.value = "You can create up to $MONTHLY_LIMIT rooms per month."
            return
        }
        _creating.value = true
        _createError.value = null
        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "name"        to trimName,
                    "description" to description.trim(),
                    "createdBy"   to myUid,
                    "createdAt"   to Timestamp.now(),
                    "memberCount" to 1,
                    "memberUids"  to listOf(myUid),
                    "pendingUids" to emptyList<String>(),
                    "isSystem"    to false,
                    "joinMode"    to joinMode,
                    "lastMsg"     to "",
                    "lastMsgAt"   to null
                )
                db.collection(ROOMS_COL).add(data).await()
                _monthlyUsed.value += 1
            } catch (e: Exception) {
                Log.e(TAG, "createRoom", e)
                _createError.value = "Failed to create room. Try again."
            } finally {
                _creating.value = false
            }
        }
    }

    /** Called when user taps a room with joinMode="auto" and they're not a member. */
    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            try {
                db.collection(ROOMS_COL).document(roomId).update(
                    mapOf(
                        "memberUids"  to FieldValue.arrayUnion(myUid),
                        "memberCount" to FieldValue.increment(1)
                    )
                ).await()
            } catch (e: Exception) { Log.e(TAG, "joinRoom", e) }
        }
    }

    /** Request to join a room with joinMode="approve". */
    fun requestJoin(roomId: String) {
        viewModelScope.launch {
            try {
                db.collection(ROOMS_COL).document(roomId).update(
                    "pendingUids", FieldValue.arrayUnion(myUid)
                ).await()
            } catch (e: Exception) { Log.e(TAG, "requestJoin", e) }
        }
    }

    fun clearCreateError() { _createError.value = null }

    override fun onCleared() { super.onCleared(); roomsListener?.remove() }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GroupChatRoomViewModel  — single room: messages, send, moderation, invite
// ─────────────────────────────────────────────────────────────────────────────

class GroupChatRoomViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    val myUid get() = auth.currentUser?.uid ?: ""

    // Room meta
    private val _room    = MutableStateFlow<GroupRoom?>(null)
    val room: StateFlow<GroupRoom?> = _room.asStateFlow()

    // Messages
    private val _messages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val messages: StateFlow<List<GroupMessage>> = _messages.asStateFlow()

    private val _msgLoading = MutableStateFlow(true)
    val msgLoading: StateFlow<Boolean> = _msgLoading.asStateFlow()

    // Send state
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    // My alias
    private val _myAlias = MutableStateFlow("")

    // Invite connections
    private val _inviteUsers = MutableStateFlow<List<GroupInviteUser>>(emptyList())
    val inviteUsers: StateFlow<List<GroupInviteUser>> = _inviteUsers.asStateFlow()

    private val _inviteLoading = MutableStateFlow(false)
    val inviteLoading: StateFlow<Boolean> = _inviteLoading.asStateFlow()

    private val _inviteSending = MutableStateFlow(false)
    val inviteSending: StateFlow<Boolean> = _inviteSending.asStateFlow()

    // Pending join requests (only visible to room creator)
    private val _pendingUsers = MutableStateFlow<List<Pair<String, String>>>(emptyList()) // uid, alias
    val pendingUsers: StateFlow<List<Pair<String, String>>> = _pendingUsers.asStateFlow()

    // Profile cache: uid → (alias, photoUrl)
    private val _userProfiles = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val userProfiles: StateFlow<Map<String, Pair<String, String>>> = _userProfiles.asStateFlow()

    private var roomListener: ListenerRegistration? = null
    private var msgListener : ListenerRegistration? = null
    private var currentRoomId = ""

    fun init(roomId: String) {
        if (currentRoomId == roomId) return
        currentRoomId = roomId
        roomListener?.remove()
        msgListener?.remove()
        loadMyAlias()
        listenRoom(roomId)
        listenMessages(roomId)
    }

    private fun loadMyAlias() {
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(myUid).get().await()
                _myAlias.value = doc.getString("alias") ?: "Someone"
            } catch (_: Exception) { }
        }
    }

    private fun listenRoom(roomId: String) {
        roomListener = db.collection(ROOMS_COL).document(roomId)
            .addSnapshotListener { d, _ ->
                if (d == null || !d.exists()) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val r = GroupRoom(
                    id          = d.id,
                    name        = d.getString("name")        ?: "",
                    description = d.getString("description") ?: "",
                    createdBy   = d.getString("createdBy")   ?: "",
                    createdAt   = d.getTimestamp("createdAt") ?: Timestamp.now(),
                    memberCount = (d.getLong("memberCount") ?: 0L).toInt(),
                    memberUids  = d.get("memberUids")  as? List<String> ?: emptyList(),
                    pendingUids = d.get("pendingUids") as? List<String> ?: emptyList(),
                    isSystem    = d.getBoolean("isSystem") ?: false,
                    joinMode    = d.getString("joinMode") ?: "auto",
                    lastMsg     = d.getString("lastMsg")  ?: "",
                    lastMsgAt   = d.getTimestamp("lastMsgAt")
                )
                _room.value = r
                // Load pending user aliases if creator
                if (r.createdBy == myUid && r.pendingUids.isNotEmpty()) {
                    loadPendingAliases(r.pendingUids)
                }
            }
    }

    private fun loadPendingAliases(uids: List<String>) {
        viewModelScope.launch {
            val result = uids.mapNotNull { uid ->
                try {
                    val d = db.collection("users").document(uid).get().await()
                    uid to (d.getString("alias") ?: "Someone")
                } catch (_: Exception) { null }
            }
            _pendingUsers.value = result
        }
    }

    private fun listenMessages(roomId: String) {
        _msgLoading.value = true
        msgListener = db.collection(ROOMS_COL).document(roomId)
            .collection(MESSAGES_COL)
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    _msgLoading.value = false; return@addSnapshotListener
                }
                viewModelScope.launch {
                    val msgs = snap.documents.mapNotNull { d ->
                        try {
                            val uid = d.getString("uid") ?: return@mapNotNull null
                            GroupMessage(
                                id           = d.id,
                                uid          = uid,
                                alias        = d.getString("alias")        ?: "Someone",
                                text         = d.getString("text")         ?: "",
                                sentAt       = d.getTimestamp("sentAt")    ?: Timestamp.now(),
                                replyToId    = d.getString("replyToId")    ?: "",
                                replyToAlias = d.getString("replyToAlias") ?: "",
                                replyToText  = d.getString("replyToText")  ?: ""
                            )
                        } catch (_: Exception) { null }
                    }
                    _messages.value = msgs
                    _msgLoading.value = false

                    // ── Lazy-fetch profiles for senders not yet cached ──────────
                    val cached = _userProfiles.value
                    val newUids = msgs.map { it.uid }.distinct().filter { it !in cached }
                    if (newUids.isNotEmpty()) {
                        val updates = cached.toMutableMap()
                        for (uid in newUids) {
                            // Seed with alias from message immediately
                            val seedAlias = msgs.lastOrNull { it.uid == uid }?.alias ?: "Someone"
                            updates[uid] = seedAlias to ""
                        }
                        _userProfiles.value = updates
                        // Now fetch real photoUrl in background
                        for (uid in newUids) {
                            try {
                                val doc = db.collection("users").document(uid).get().await()
                                val alias    = doc.getString("alias")    ?: updates[uid]?.first ?: "Someone"
                                val photoUrl = doc.getString("photoUrl") ?: ""
                                _userProfiles.value = _userProfiles.value.toMutableMap().also {
                                    it[uid] = alias to photoUrl
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
            }
    }

    fun send(
        text         : String,
        replyToId    : String = "",
        replyToAlias : String = "",
        replyToText  : String = ""
    ) {
        val t = text.trim()
        if (t.isBlank() || _sending.value) return
        val roomId = currentRoomId
        val alias  = _myAlias.value
        _sending.value = true
        viewModelScope.launch {
            try {
                val safe = ContentModerator.maskPhoneNumbers(t)
                val mod  = ContentModerator.check(safe)
                if (!mod.isSafe) { _sending.value = false; return@launch }
                val data = mutableMapOf<String, Any>(
                    "uid"    to myUid,
                    "alias"  to alias,
                    "text"   to safe,
                    "sentAt" to Timestamp.now()
                )
                if (replyToId.isNotBlank()) {
                    data["replyToId"]    = replyToId
                    data["replyToAlias"] = replyToAlias
                    data["replyToText"]  = replyToText.take(120)
                }
                db.collection(ROOMS_COL).document(roomId)
                    .collection(MESSAGES_COL).add(data).await()
                db.collection(ROOMS_COL).document(roomId).update(
                    mapOf(
                        "lastMsg"   to safe.take(60),
                        "lastMsgAt" to Timestamp.now()
                    )
                ).await()
            } catch (e: Exception) {
                Log.e(TAG, "send", e)
            } finally {
                _sending.value = false
            }
        }
    }

    // ── Room creator controls ───────────────────────────────────────────────

    fun approveJoin(uid: String) {
        val roomId = currentRoomId
        viewModelScope.launch {
            try {
                db.collection(ROOMS_COL).document(roomId).update(
                    mapOf(
                        "pendingUids" to FieldValue.arrayRemove(uid),
                        "memberUids"  to FieldValue.arrayUnion(uid),
                        "memberCount" to FieldValue.increment(1)
                    )
                ).await()
            } catch (e: Exception) { Log.e(TAG, "approveJoin", e) }
        }
    }

    fun denyJoin(uid: String) {
        val roomId = currentRoomId
        viewModelScope.launch {
            try {
                db.collection(ROOMS_COL).document(roomId).update(
                    "pendingUids", FieldValue.arrayRemove(uid)
                ).await()
            } catch (e: Exception) { Log.e(TAG, "denyJoin", e) }
        }
    }

    fun removeMember(uid: String) {
        val roomId = currentRoomId
        viewModelScope.launch {
            try {
                db.collection(ROOMS_COL).document(roomId).update(
                    mapOf(
                        "memberUids"  to FieldValue.arrayRemove(uid),
                        "memberCount" to FieldValue.increment(-1)
                    )
                ).await()
            } catch (e: Exception) { Log.e(TAG, "removeMember", e) }
        }
    }

    fun deleteRoom(onDeleted: () -> Unit) {
        val roomId = currentRoomId
        viewModelScope.launch {
            try {
                db.collection(ROOMS_COL).document(roomId).delete().await()
                onDeleted()
            } catch (e: Exception) { Log.e(TAG, "deleteRoom", e) }
        }
    }

    /** Auto-join a room (joinMode = "auto"). */
    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            try {
                db.collection(ROOMS_COL).document(roomId).update(
                    mapOf(
                        "memberUids"  to FieldValue.arrayUnion(myUid),
                        "memberCount" to FieldValue.increment(1)
                    )
                ).await()
            } catch (e: Exception) { Log.e(TAG, "joinRoom", e) }
        }
    }

    /** Request to join a room (joinMode = "approve"). */
    fun requestJoin(roomId: String) {
        viewModelScope.launch {
            try {
                db.collection(ROOMS_COL).document(roomId).update(
                    "pendingUids", FieldValue.arrayUnion(myUid)
                ).await()
            } catch (e: Exception) { Log.e(TAG, "requestJoin", e) }
        }
    }

    // ── Invite ──────────────────────────────────────────────────────────────

    fun loadInviteConnections() {
        if (_inviteLoading.value) return
        _inviteLoading.value = true
        viewModelScope.launch {
            try {
                val result      = mutableListOf<GroupInviteUser>()
                val memberUids  = _room.value?.memberUids ?: emptyList()

                // ── Feed friends: userFriends/{myUid}/index ──────────────────
                try {
                    val feedSnap = db.collection("userFriends").document(myUid)
                        .collection("index")
                        .get().await()
                    for (d in feedSnap.documents) {
                        val uA = d.getString("userA") ?: continue
                        val uB = d.getString("userB") ?: continue
                        val otherUid = if (uA == myUid) uB else uA
                        if (otherUid.isBlank() || otherUid in memberUids) continue
                        if (result.any { it.uid == otherUid }) continue
                        val storedAlias = if (uA == myUid) d.getString("aliasB") else d.getString("aliasA")
                        var alias    = storedAlias?.takeIf { it.isNotBlank() } ?: "Someone"
                        var photoUrl = ""
                        try {
                            val uDoc = db.collection("users").document(otherUid).get().await()
                            if (alias == "Someone") alias = uDoc.getString("alias") ?: "Someone"
                            photoUrl = uDoc.getString("photoUrl") ?: ""
                        } catch (_: Exception) { }
                        result.add(GroupInviteUser(uid = otherUid, alias = alias, source = "friend", photoUrl = photoUrl))
                    }
                } catch (e: Exception) { Log.w(TAG, "loadFriends", e) }

                // ── Swipe matches: matches collection, userA / userB ─────────
                try {
                    val matchSnapA = db.collection("matches")
                        .whereEqualTo("userA", myUid).get().await()
                    val matchSnapB = db.collection("matches")
                        .whereEqualTo("userB", myUid).get().await()
                    for (d in matchSnapA.documents + matchSnapB.documents) {
                        val uA = d.getString("userA") ?: continue
                        val uB = d.getString("userB") ?: continue
                        val otherUid = if (uA == myUid) uB else uA
                        if (otherUid.isBlank() || otherUid in memberUids) continue
                        if (result.any { it.uid == otherUid }) continue
                        val aliasA = d.getString("aliasA") ?: ""
                        val aliasB = d.getString("aliasB") ?: ""
                        val storedAlias = if (uA == myUid) aliasB else aliasA
                        var alias    = storedAlias.takeIf { it.isNotBlank() } ?: "Someone"
                        var photoUrl = ""
                        try {
                            val uDoc = db.collection("users").document(otherUid).get().await()
                            if (alias == "Someone") alias = uDoc.getString("alias") ?: "Someone"
                            photoUrl = uDoc.getString("photoUrl") ?: ""
                        } catch (_: Exception) { }
                        result.add(GroupInviteUser(uid = otherUid, alias = alias, source = "swipe", photoUrl = photoUrl))
                    }
                } catch (e: Exception) { Log.w(TAG, "loadMatches", e) }

                _inviteUsers.value = result
            } catch (e: Exception) {
                Log.e(TAG, "loadInviteConnections", e)
            } finally {
                _inviteLoading.value = false
            }
        }
    }

    fun sendInvites(roomId: String, targetUids: List<String>) {
        if (_inviteSending.value) return
        _inviteSending.value = true
        val myAlias  = _myAlias.value
        val roomName = _room.value?.name ?: "a group"
        viewModelScope.launch {
            try {
                for (uid in targetUids) {
                    try {
                        val matchId = findMatchId(uid)
                        if (matchId != null) {
                            val inviteText = "group_invite::$roomId::$roomName"
                            db.collection("matches").document(matchId)
                                .collection("messages").add(
                                    mapOf(
                                        "uid"    to myUid,
                                        "text"   to inviteText,
                                        "sentAt" to com.google.firebase.Timestamp.now()
                                    )
                                ).await()
                        }
                    } catch (_: Exception) { }
                    try {
                        VeilNotifier.sendToUser(
                            uid   = uid,
                            title = "$myAlias invited you",
                            body  = "Join \"$roomName\" — a group chat on Veil"
                        )
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendInvites", e)
            } finally {
                _inviteSending.value = false
            }
        }
    }

    private suspend fun findMatchId(otherUid: String): String? {
        // Check swipe matches first
        return try {
            val snap = db.collection("matches")
                .whereArrayContains("userIds", myUid)
                .whereEqualTo("status", "matched")
                .get().await()
            snap.documents.firstOrNull { d ->
                @Suppress("UNCHECKED_CAST")
                val uids = d.get("userIds") as? List<String> ?: emptyList()
                otherUid in uids
            }?.id
        } catch (_: Exception) { null }
    }

    override fun onCleared() {
        super.onCleared()
        roomListener?.remove()
        msgListener?.remove()
    }
}
