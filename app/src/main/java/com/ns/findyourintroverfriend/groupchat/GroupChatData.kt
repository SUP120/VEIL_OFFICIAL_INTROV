package com.ns.findyourintroverfriend.groupchat

import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.ns.findyourintroverfriend.notifications.VeilNotifier
import com.ns.findyourintroverfriend.notifications.GroupPushRelay
import com.ns.findyourintroverfriend.notifications.GroupPushNotification
import com.ns.findyourintroverfriend.util.ContentModerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

private const val TAG            = "GroupChat"
private const val ROOMS_COL      = "group_rooms"
private const val MESSAGES_COL   = "messages"
private const val MESSAGE_PAGE   = 50L
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
    val lastMsgAt   : Timestamp?,
    val lastMsgUid  : String = "",
    val announcement: String = ""
)

data class GroupMessage(
    val id           : String,
    val uid          : String,
    val alias        : String,
    val text         : String,
    val sentAt       : Timestamp,
    val replyToId    : String = "",
    val replyToAlias : String = "",
    val replyToText  : String = "",
    val reactions    : Map<String, String> = emptyMap()
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
    private val _joinedRooms = MutableStateFlow<List<GroupRoom>>(emptyList())
    val joinedRooms = _joinedRooms.asStateFlow()
    private val _preferences = MutableStateFlow<Map<String, GroupPreferences>>(emptyMap())
    internal val preferences = _preferences.asStateFlow()
    private val _roomsError = MutableStateFlow(false)
    val roomsError = _roomsError.asStateFlow()

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
    private var joinedListener: ListenerRegistration? = null
    private var preferencesListener: ListenerRegistration? = null

    init {
        loadMyAlias()
        reloadRooms()
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

    fun reloadRooms() {
        roomsListener?.remove()
        joinedListener?.remove()
        preferencesListener?.remove()
        _loading.value = true
        _roomsError.value = false
        val uid = myUid
        if (uid.isBlank()) {
            _roomsError.value = true
            _loading.value = false
            return
        }
        joinedListener = db.collection(ROOMS_COL).whereArrayContains("memberUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) _roomsError.value = true
                if (snapshot != null) _joinedRooms.value = snapshot.documents.mapNotNull { it.toGroupRoom() }
            }
        preferencesListener = db.groupPreferences(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) _preferences.value = snapshot.documents.associate { it.id to it.groupPreferences() }
        }
        roomsListener = db.collection(ROOMS_COL)
            .orderBy("memberCount", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    _roomsError.value = true
                    _loading.value = false; return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { it.toGroupRoom() }
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
                    .get().await()
                _monthlyUsed.value = snap.documents.count { it.getTimestamp("createdAt")?.let { time -> time >= monthStart } == true }
            } catch (_: Exception) { }
        }
    }

    fun createRoom(name: String, description: String, joinMode: String, onCreated: (String) -> Unit) {
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
                val room = db.collection(ROOMS_COL).add(data).await()
                _monthlyUsed.value += 1
                onCreated(room.id)
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
                db.changeGroupMembership(roomId, myUid, GroupMembership.Join)
            } catch (e: Exception) { Log.e(TAG, "joinRoom", e) }
        }
    }

    /** Request to join a room with joinMode="approve". */
    fun requestJoin(roomId: String) {
        viewModelScope.launch {
            try {
                db.changeGroupMembership(roomId, myUid, GroupMembership.Request)
            } catch (e: Exception) { Log.e(TAG, "requestJoin", e) }
        }
    }

    fun clearCreateError() { _createError.value = null }

    override fun onCleared() {
        roomsListener?.remove()
        joinedListener?.remove()
        preferencesListener?.remove()
        super.onCleared()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GroupChatRoomViewModel  — single room: messages, send, moderation, invite
// ─────────────────────────────────────────────────────────────────────────────

class GroupChatRoomViewModel(application: Application) : AndroidViewModel(application) {

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
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()
    private val _roomError = MutableStateFlow(false)
    val roomError = _roomError.asStateFlow()
    private val _messageError = MutableStateFlow(false)
    val messageError = _messageError.asStateFlow()
    private val _hasOlder = MutableStateFlow(false)
    val hasOlder = _hasOlder.asStateFlow()
    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder = _loadingOlder.asStateFlow()
    private val _preferences = MutableStateFlow(GroupPreferences())
    internal val preferences = _preferences.asStateFlow()

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
    private var preferenceListener: ListenerRegistration? = null
    private var oldestMessage: DocumentSnapshot? = null
    private var lastMarkedRead: Timestamp? = null
    private var requestedRead: Timestamp? = null
    private var lastReadWriting = false
    private var currentRoomId = ""

    fun init(roomId: String) {
        if (currentRoomId == roomId) return
        currentRoomId = roomId
        loadMyAlias()
        reloadRoom()
        if (myUid.isNotBlank()) {
            preferenceListener?.remove()
            preferenceListener = db.groupPreferences(myUid).document(roomId).addSnapshotListener { snapshot, _ ->
                if (snapshot != null) _preferences.value = snapshot.groupPreferences()
            }
        }
    }

    fun reloadRoom() {
        if (currentRoomId.isBlank()) return
        roomListener?.remove()
        _roomError.value = false
        listenRoom(currentRoomId)
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
            .addSnapshotListener { d, error ->
                if (error != null || d == null || (!d.exists() && !d.metadata.isFromCache)) {
                    _roomError.value = true
                    return@addSnapshotListener
                }
                val r = d.toGroupRoom() ?: return@addSnapshotListener
                _roomError.value = false
                _room.value = r
                if (myUid in r.memberUids) {
                    if (msgListener == null) listenMessages(roomId)
                } else {
                    msgListener?.remove()
                    msgListener = null
                    oldestMessage = null
                    _messages.value = emptyList()
                    _hasOlder.value = false
                }
                // Load pending user aliases if creator
                if (r.createdBy == myUid && r.pendingUids.isNotEmpty()) {
                    if (_pendingUsers.value.map { it.first } != r.pendingUids) loadPendingAliases(r.pendingUids)
                } else {
                    _pendingUsers.value = emptyList()
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

    fun retryMessages() {
        if (myUid in (_room.value?.memberUids ?: emptyList())) listenMessages(currentRoomId)
    }

    private fun messageQuery(roomId: String) = db.collection(ROOMS_COL).document(roomId)
        .collection(MESSAGES_COL).orderBy("sentAt", Query.Direction.DESCENDING)

    private fun listenMessages(roomId: String) {
        msgListener?.remove()
        _msgLoading.value = _messages.value.isEmpty()
        _messageError.value = false
        msgListener = messageQuery(roomId).limit(MESSAGE_PAGE).addSnapshotListener { snapshot, error ->
            _msgLoading.value = false
            if (error != null || snapshot == null) {
                _messageError.value = true
                return@addSnapshotListener
            }
            if (oldestMessage == null) {
                oldestMessage = snapshot.documents.lastOrNull()
                _hasOlder.value = snapshot.size().toLong() == MESSAGE_PAGE
            }
            mergeMessages(snapshot.documents.mapNotNull { it.toGroupMessage() })
        }
    }

    fun loadEarlier() {
        val oldest = oldestMessage ?: return
        if (_loadingOlder.value) return
        _loadingOlder.value = true
        viewModelScope.launch {
            try {
                val snapshot = messageQuery(currentRoomId).startAfter(oldest).limit(MESSAGE_PAGE).get().await()
                oldestMessage = snapshot.documents.lastOrNull() ?: oldest
                _hasOlder.value = snapshot.size().toLong() == MESSAGE_PAGE
                mergeMessages(snapshot.documents.mapNotNull { it.toGroupMessage() })
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _actionError.value = "Couldn't load older messages. Try again."
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    private fun mergeMessages(incoming: List<GroupMessage>) {
        _messages.update { old ->
            (old + incoming).associateBy { it.id }.values
                .sortedWith(compareBy<GroupMessage> { it.sentAt }.thenBy { it.id })
        }
        val newSenders = incoming.associate { it.uid to it.alias }.filterKeys { it !in _userProfiles.value }
        if (newSenders.isEmpty()) return
        _userProfiles.update { profiles -> profiles + newSenders.mapValues { (_, alias) -> alias to "" } }
        viewModelScope.launch {
            for ((uid, alias) in newSenders) {
                try {
                    val account = db.collection("users").document(uid).get().await()
                    val profile = (account.getString("alias") ?: alias) to account.getString("photoUrl").orEmpty()
                    _userProfiles.update { it + (uid to profile) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep using the alias stored on the message.
                }
            }
        }
    }

    internal fun send(text: String, replyToId: String?, onSent: () -> Unit) {
        if (text.isBlank() || _sending.value) return
        val room = currentRoomId
        val uid = myUid
        val messageId = UUID.randomUUID().toString()
        _sending.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                GroupChatMessages.send(room, uid, messageId, text, replyToId)
                runCatching { GroupPushRelay.enqueue(getApplication(), uid, room, messageId) }
                    .onFailure { Log.w(TAG, "Couldn't queue group push", it) }
                onSent()
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                _actionError.value = error.message ?: "Check your message and try again."
            } catch (_: Exception) {
                _actionError.value = "Couldn't send your message. Check your connection and group membership."
            } finally {
                _sending.value = false
            }
        }
    }

    fun clearActionError() { _actionError.value = null }

    fun markRead(at: Timestamp) {
        val uid = myUid
        val previous = maxOf(lastMarkedRead ?: Timestamp(0, 0), _preferences.value.lastReadAt ?: Timestamp(0, 0))
        if (uid.isBlank() || at <= previous) return
        if (lastReadWriting) {
            requestedRead = maxOf(requestedRead ?: at, at)
            return
        }
        lastReadWriting = true
        val reference = db.groupPreferences(uid).document(currentRoomId)
        viewModelScope.launch {
            try {
                db.runTransaction { transaction ->
                    val existing = transaction.get(reference).getTimestamp("lastReadAt")
                    if (existing == null || at > existing) {
                        transaction.set(reference, mapOf("lastReadAt" to at), com.google.firebase.firestore.SetOptions.merge())
                    }
                }.await()
                lastMarkedRead = at
                GroupPushNotification.cancel(getApplication(), uid, currentRoomId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the group unread when Firestore cannot confirm the change.
            } finally {
                lastReadWriting = false
                val next = requestedRead
                requestedRead = null
                if (next != null && next > at) markRead(next)
            }
        }
    }

    fun setNotificationMode(mode: String) =
        updatePreference("notificationMode", mode)

    fun setGroupPinned(pinned: Boolean) =
        updatePreference("pinned", pinned)

    private fun updatePreference(field: String, value: Any) {
        runGroupAction("Couldn't save your group settings.") {
            db.groupPreferences(myUid).document(currentRoomId)
                .set(mapOf(field to value), com.google.firebase.firestore.SetOptions.merge()).await()
            if (value == "muted") GroupPushNotification.cancel(getApplication(), myUid, currentRoomId)
        }
    }

    fun react(message: GroupMessage, reaction: String) {
        if (reaction !in groupReactions || myUid.isBlank()) return
        val uid = myUid
        val reference = db.collection(ROOMS_COL).document(currentRoomId).collection(MESSAGES_COL).document(message.id)
        runGroupAction("Couldn't save your reaction.") {
            val value = db.runTransaction { transaction ->
                val existing = transaction.get(reference).toGroupMessage()
                check(existing != null) { "This message is unavailable." }
                val selected = reaction.takeUnless { it == existing.reactions[uid] }
                transaction.update(reference, FieldPath.of("reactions", uid), selected ?: FieldValue.delete())
                selected
            }.await()
            _messages.update { messages ->
                messages.map {
                    if (it.id != message.id) it
                    else it.copy(reactions = if (value == null) it.reactions - uid else it.reactions + (uid to value))
                }
            }
        }
    }

    fun saveAnnouncement(text: String, onSaved: () -> Unit) {
        runGroupAction("Couldn't update the announcement.") {
            val clean = ContentModerator.maskPhoneNumbers(text.trim())
            require(clean.length <= 280 && (clean.isEmpty() || ContentModerator.check(clean).isSafe)) {
                "Use up to 280 characters and follow VEIL's community guidelines."
            }
            db.collection(ROOMS_COL).document(currentRoomId).update("announcement", clean).await()
            onSaved()
        }
    }

    fun approveJoin(uid: String) = changeMembership(GroupMembership.Approve, uid)

    fun denyJoin(uid: String) = changeMembership(GroupMembership.Deny, uid)

    fun removeMember(uid: String) = changeMembership(GroupMembership.Remove, uid)

    fun joinRoom(roomId: String) {
        if (roomId == currentRoomId) changeMembership(GroupMembership.Join)
    }

    fun requestJoin(roomId: String) {
        if (roomId == currentRoomId) changeMembership(GroupMembership.Request)
    }

    fun cancelJoinRequest() = changeMembership(GroupMembership.CancelRequest)

    fun leaveGroup(onLeft: () -> Unit) = changeMembership(GroupMembership.Leave, onDone = onLeft)

    private fun changeMembership(action: GroupMembership, target: String = myUid, onDone: () -> Unit = {}) {
        runGroupAction("Couldn't update your group membership. Try again.") {
            db.changeGroupMembership(currentRoomId, myUid, action, target)
            if (action == GroupMembership.Leave) GroupPushNotification.cancel(getApplication(), myUid, currentRoomId)
            onDone()
        }
    }

    fun deleteRoom(onDeleted: () -> Unit) {
        runGroupAction("Couldn't delete the group.") {
            db.collection(ROOMS_COL).document(currentRoomId).delete().await()
            onDeleted()
        }
    }

    private fun runGroupAction(failureMessage: String, action: suspend () -> Unit) {
        _actionError.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                _actionError.value = error.message ?: failureMessage
            } catch (_: Exception) {
                _actionError.value = failureMessage
            }
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
        preferenceListener?.remove()
    }
}
