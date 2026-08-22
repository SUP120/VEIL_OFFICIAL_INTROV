package com.ns.findyourintroverfriend.swipe

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ns.findyourintroverfriend.util.PresenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MatchesViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""

    // ── State ────────────────────────────────────────────────────────────────
    private val _matches      = MutableStateFlow<List<SwipeMatch>>(emptyList())
    val matches: StateFlow<List<SwipeMatch>> = _matches.asStateFlow()

    // Feed-friendship matches only (source == "friend")
    private val _friendMatches = MutableStateFlow<List<SwipeMatch>>(emptyList())
    val friendMatches: StateFlow<List<SwipeMatch>> = _friendMatches.asStateFlow()

    private val _compliments  = MutableStateFlow<List<SwipeCompliment>>(emptyList())
    val compliments: StateFlow<List<SwipeCompliment>> = _compliments.asStateFlow()

    private val _sentCompliments = MutableStateFlow<List<SentCompliment>>(emptyList())
    val sentCompliments: StateFlow<List<SentCompliment>> = _sentCompliments.asStateFlow()

    private val _pendingLikes = MutableStateFlow<List<PendingLike>>(emptyList())
    val pendingLikes: StateFlow<List<PendingLike>> = _pendingLikes.asStateFlow()

    private val _loading      = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Last message preview per matchId: matchId -> Pair(text, sentAt)
    private val _lastMessages = MutableStateFlow<Map<String, Pair<String, Long>>>(emptyMap())
    val lastMessages: StateFlow<Map<String, Pair<String, Long>>> = _lastMessages.asStateFlow()

    // Set of matchIds with unread messages (last msg sent by partner, not us)
    private val _unreadMatchIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadMatchIds: StateFlow<Set<String>> = _unreadMatchIds.asStateFlow()

    // Presence map: otherUid → Pair(isOnline, lastSeenMs)
    private val _presenceMap = MutableStateFlow<Map<String, Pair<Boolean, Long>>>(emptyMap())
    val presenceMap: StateFlow<Map<String, Pair<Boolean, Long>>> = _presenceMap.asStateFlow()

    private val mergedMatches = mutableMapOf<String, SwipeMatch>()

    private var indexListener        : ListenerRegistration? = null
    private var pendingListener      : ListenerRegistration? = null
    private var complimentsListener  : ListenerRegistration? = null
    private var sentListener         : ListenerRegistration? = null
    private val msgListeners         = mutableMapOf<String, ListenerRegistration>()

    /**
     * Local override set — once a user taps 'Accept', we mark that compliment
     * permanently accepted in-memory. This prevents Firestore rules/rollbacks from
     * reverting the UI back to the 'pending' state after the user took action.
     * Survives snapshot listener re-fires within the same ViewModel lifecycle.
     */
    private val locallyAccepted = mutableSetOf<String>()  // stores fromUid values

    init {
        loadMatches()
        loadPendingLikes()
        loadCompliments()
        loadSentCompliments()
    }

    override fun onCleared() {
        super.onCleared()
        indexListener?.remove()
        pendingListener?.remove()
        complimentsListener?.remove()
        sentListener?.remove()
        msgListeners.values.forEach { it.remove() }
    }

    // ── Delete a chat (match) ─────────────────────────────────────────────────
    fun deleteMatch(matchId: String) {
        // Optimistic — remove from list immediately
        mergedMatches.remove(matchId)
        publishMatches()
        viewModelScope.launch {
            try {
                db.collection("matches").document(matchId).delete().await()
                // Also remove from userMatches index for both users
                val match = _matches.value.firstOrNull { it.matchId == matchId }
                val otherUid = match?.let { if (it.userA == uid) it.userB else it.userA }
                db.collection("userMatches").document(uid).collection("index")
                    .document(matchId).delete().await()
                if (otherUid != null) {
                    db.collection("userMatches").document(otherUid).collection("index")
                        .document(matchId).delete().await()
                }
                Log.d("MatchesVM", "deleteMatch: $matchId")
            } catch (e: Exception) {
                Log.e("MatchesVM", "deleteMatch failed: ${e.message}")
            }
        }
    }

    // ── Block a user from DM inbox ────────────────────────────────────────────
    fun blockUser(targetUid: String, matchId: String) {
        // Optimistic — remove any chat with this user
        mergedMatches.remove(matchId)
        publishMatches()
        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .update("blockedUsers", com.google.firebase.firestore.FieldValue.arrayUnion(targetUid))
                    .await()
                // Also delete the match
                db.collection("matches").document(matchId).delete().await()
                Log.d("MatchesVM", "blocked $targetUid, removed match $matchId")
            } catch (e: Exception) {
                Log.e("MatchesVM", "blockUser failed: ${e.message}")
            }
        }
    }

    // ── Manual field deserialization ──────────────────────────────────────────
    private fun docToMatch(doc: com.google.firebase.firestore.DocumentSnapshot): SwipeMatch? = try {
        SwipeMatch(
            matchId   = doc.getString("matchId") ?: doc.id,
            userA     = doc.getString("userA")     ?: "",
            userB     = doc.getString("userB")     ?: "",
            aliasA    = doc.getString("aliasA")    ?: "",
            aliasB    = doc.getString("aliasB")    ?: "",
            photoA    = doc.getString("photoA")    ?: "",
            photoB    = doc.getString("photoB")    ?: "",
            avatarA   = doc.getString("avatarA")   ?: "",
            avatarB   = doc.getString("avatarB")   ?: "",
            matchedAt = doc.getLong("matchedAt")   ?: 0L,
            source    = doc.getString("source")    ?: "swipe"
        )
    } catch (e: Exception) {
        Log.e("MatchesVM", "docToMatch error: ${e.message}")
        null
    }

    private fun publishMatches() {
        val all = mergedMatches.values.sortedByDescending { it.matchedAt }
        _matches.value       = all.filter { it.source != "friend" }
        _friendMatches.value = all.filter { it.source == "friend" }
        // Refresh presence for all match partners
        refreshPresence()
    }

    fun refreshPresence() {
        if (uid.isEmpty()) return
        val allMatches = mergedMatches.values.toList()
        val otherUids = allMatches.map { if (it.userA == uid) it.userB else it.userA }
        if (otherUids.isEmpty()) return
        viewModelScope.launch {
            val map = PresenceManager.fetchPresence(otherUids)
            _presenceMap.value = map
        }
    }

    // ── PRIMARY: listen to userMatches/{uid}/index for reliable match discovery ─
    // Falls back to whereEqualTo queries if userMatches is empty
    private fun loadMatches() {
        if (uid.isEmpty()) { _loading.value = false; return }

        indexListener = db.collection("userMatches")
            .document(uid)
            .collection("index")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("MatchesVM", "index listener error: ${err.message}")
                    // fallback: try whereEqualTo approach
                    loadMatchesFallback()
                    return@addSnapshotListener
                }
                val matchIds = snap?.documents?.mapNotNull { it.getString("matchId") ?: it.id }
                    ?: emptyList()

                if (matchIds.isEmpty()) {
                    _loading.value = false
                    // Also try fallback in case index hasn't been written yet
                    loadMatchesFallback()
                    return@addSnapshotListener
                }

                // Fetch each match doc by ID — simple read, always allowed by security rules
                viewModelScope.launch {
                    matchIds.forEach { matchId ->
                        try {
                            val doc = db.collection("matches").document(matchId).get().await()
                            if (doc.exists()) docToMatch(doc)?.let { mergedMatches[it.matchId] = it }
                        } catch (e: Exception) {
                            Log.e("MatchesVM", "fetch match $matchId failed: ${e.message}")
                        }
                    }
                    publishMatches()
                    _loading.value = false
                    listenToLastMessages(matchIds)
                }
            }
    }

    // ── FALLBACK: whereEqualTo queries (works when rules allow it) ────────────
    private fun loadMatchesFallback() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val snapA = db.collection("matches").whereEqualTo("userA", uid).get().await()
                val snapB = db.collection("matches").whereEqualTo("userB", uid).get().await()
                val ids = mutableListOf<String>()
                (snapA.documents + snapB.documents).forEach { doc ->
                    docToMatch(doc)?.let { mergedMatches[it.matchId] = it; ids += it.matchId }
                }
                publishMatches()
                listenToLastMessages(ids)
            } catch (e: Exception) {
                Log.e("MatchesVM", "fallback query failed: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Per-match last-message listeners ─────────────────────────────────────
    private fun listenToLastMessages(matchIds: List<String>) {
        matchIds.forEach { matchId ->
            if (msgListeners.containsKey(matchId)) return@forEach
            val reg = db.collection("matches").document(matchId)
                .collection("messages")
                .orderBy("sentAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snap, _ ->
                    val doc = snap?.documents?.firstOrNull() ?: return@addSnapshotListener
                    val text   = doc.getString("text") ?: ""
                    val sentAt = doc.getLong("sentAt") ?: 0L
                    val sender = doc.getString("senderUid") ?: ""
                    val preview = when {
                        text.startsWith("voice_note::") -> "🎙 Voice note"
                        text.startsWith("drawing::")   -> "🎨 Drawing"
                        text.startsWith("gift_cookies_") -> "🍪 Cookie gift"
                        text == "flower_burst"          -> "🌸 Flower Burst"
                        text.startsWith("sticker_pack") -> "😄 Sticker"
                        text.startsWith("@")            -> "↩ ${text.lines().getOrElse(1){text}.take(40)}"
                        else -> text.take(50)
                    }
                    _lastMessages.value = _lastMessages.value + (matchId to Pair(preview, sentAt))
                    // Unread = last msg sent by partner, not me
                    val isUnread = sender.isNotBlank() && sender != uid
                    _unreadMatchIds.value = if (isUnread)
                        _unreadMatchIds.value + matchId
                    else
                        _unreadMatchIds.value - matchId
                }
            msgListeners[matchId] = reg
        }
    }

    fun markMatchRead(matchId: String) {
        _unreadMatchIds.value = _unreadMatchIds.value - matchId
    }

    // ── Pending Likes ──────────────────────────────────────────────────────────
    private fun loadPendingLikes() {
        if (uid.isEmpty()) return
        pendingListener = db.collection("swipeLikes").document(uid).collection("received")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val matchedUids = mergedMatches.values.flatMap { listOf(it.userA, it.userB) }.toSet()
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        PendingLike(
                            fromUid      = doc.getString("fromUid")      ?: doc.id,
                            fromAlias    = doc.getString("fromAlias")    ?: "Someone",
                            fromPhotoUrl = doc.getString("fromPhotoUrl") ?: "",
                            fromAvatar   = doc.getString("fromAvatar")   ?: "",
                            at           = doc.getLong("at")             ?: 0L
                        )
                    } catch (_: Exception) { null }
                }?.filter { it.fromUid !in matchedUids }
                    ?.sortedByDescending { it.at } ?: emptyList()
                _pendingLikes.value = list
            }
    }

    // ── Received Compliments ────────────────────────────────────────────────────────
    private fun loadCompliments() {
        if (uid.isEmpty()) return
        complimentsListener = db.collection("compliments").document(uid).collection("received")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("MatchesVM", "loadCompliments error: ${err.message}")
                    return@addSnapshotListener
                }
                val mapped = snap?.documents?.mapNotNull { doc ->
                    try {
                        SwipeCompliment(
                            fromUid      = doc.getString("fromUid")      ?: doc.id,
                            fromAlias    = doc.getString("fromAlias")    ?: "Someone",
                            fromPhotoUrl = doc.getString("fromPhotoUrl") ?: "",
                            fromAvatar   = doc.getString("fromAvatar")   ?: "",
                            message      = doc.getString("message")      ?: "",
                            at           = doc.getLong("at")             ?: 0L,
                            isRead       = doc.getBoolean("isRead")      ?: false,
                            status       = doc.getString("status")       ?: "pending"
                        )
                    } catch (_: Exception) { null }
                }?.sortedByDescending { it.at } ?: emptyList()
                // Apply local overrides — if the user already tapped Accept,
                // never let a Firestore rollback revert the UI back to 'pending'.
                _compliments.value = if (locallyAccepted.isEmpty()) mapped else mapped.map { c ->
                    if (c.fromUid in locallyAccepted) c.copy(status = "accepted") else c
                }

                // Backfill photo/avatar for old compliment docs that were sent before
                // the app stored fromPhotoUrl/fromAvatar fields in the compliment doc.
                val needsPhoto = mapped.filter { it.fromPhotoUrl.isBlank() && it.fromAvatar.isBlank() }
                if (needsPhoto.isNotEmpty()) {
                    viewModelScope.launch {
                        needsPhoto.forEach { c ->
                            try {
                                val s = db.collection("swipeProfiles").document(c.fromUid).get().await()
                                val isCameraShy = s.getBoolean("isCameraShy") ?: false
                                val photo  = if (isCameraShy) "" else (s.getString("photoUrl") ?: "")
                                val avatar = s.getString("avatarResName") ?: ""
                                if (photo.isNotBlank() || avatar.isNotBlank()) {
                                    _compliments.value = _compliments.value.map { existing ->
                                        if (existing.fromUid == c.fromUid &&
                                            existing.fromPhotoUrl.isBlank() &&
                                            existing.fromAvatar.isBlank()) {
                                            existing.copy(fromPhotoUrl = photo, fromAvatar = avatar)
                                        } else existing
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
    }

    // ── Sent Compliments ────────────────────────────────────────────────────────
    private fun loadSentCompliments() {
        if (uid.isEmpty()) return
        // Read from swipeLikes/{uid}/given where action=="compliment".
        // This collection is owned by the sender — no extra Firestore rules needed.
        sentListener = db.collection("swipeLikes").document(uid).collection("given")
            .whereEqualTo("action", "compliment")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("MatchesVM", "loadSentCompliments error: ${err.message}")
                    return@addSnapshotListener
                }
                _sentCompliments.value = snap?.documents?.mapNotNull { doc ->
                    try {
                        SentCompliment(
                            toUid   = doc.id,
                            toAlias = doc.getString("toAlias")          ?: "Someone",
                            message = doc.getString("message")          ?: "",
                            at      = doc.getLong("at")                 ?: 0L,
                            status  = doc.getString("complimentStatus") ?: "pending"
                        )
                    } catch (_: Exception) { null }
                }?.sortedByDescending { it.at } ?: emptyList()
            }
    }

    // ── Smash pending like → mutual match ─────────────────────────────────────
    fun smashPendingLike(pending: PendingLike, myProfile: SwipeProfile?, onMatch: (String) -> Unit) {
        if (uid.isEmpty() || myProfile == null) return
        viewModelScope.launch {
            try {
                val matchId = listOf(uid, pending.fromUid).sorted().joinToString("_")

                // Record my like
                db.collection("swipeLikes").document(uid)
                    .collection("given").document(pending.fromUid)
                    .set(mapOf("action" to "like", "at" to System.currentTimeMillis())).await()

                // Remove from received
                db.collection("swipeLikes").document(uid)
                    .collection("received").document(pending.fromUid).delete().await()

                // Create match doc if not exists
                val existing = db.collection("matches").document(matchId).get().await()
                if (!existing.exists()) {
                    db.collection("matches").document(matchId).set(mapOf(
                        "matchId"              to matchId,
                        "userA"                to uid,
                        "userB"                to pending.fromUid,
                        "aliasA"               to myProfile.alias,
                        "aliasB"               to pending.fromAlias,
                        "photoA"               to myProfile.photoUrl,
                        "photoB"               to pending.fromPhotoUrl,
                        "matchedAt"            to System.currentTimeMillis(),
                        "icebreakerAnsweredA"  to "",
                        "icebreakerAnsweredB"  to "",
                        "chatUnlocked"         to false
                    )).await()
                }

                // Write index for both
                val idxData = mapOf("matchId" to matchId, "at" to System.currentTimeMillis())
                try { db.collection("userMatches").document(uid)
                    .collection("index").document(matchId).set(idxData).await() } catch (_: Exception) {}
                try { db.collection("userMatches").document(pending.fromUid)
                    .collection("index").document(matchId).set(idxData).await() } catch (_: Exception) {}

                // Send match notifications to both
                try {
                    com.ns.findyourintroverfriend.notifications.VeilNotifier.sendSwipeMatch(
                        toUid = pending.fromUid, fromAlias = myProfile.alias)
                    com.ns.findyourintroverfriend.notifications.VeilNotifier.sendSwipeMatch(
                        toUid = uid, fromAlias = pending.fromAlias)
                } catch (_: Exception) {}

                _pendingLikes.value = _pendingLikes.value.filter { it.fromUid != pending.fromUid }
                onMatch(matchId)
            } catch (e: Exception) {
                Log.e("MatchesVM", "smashPendingLike failed: ${e.message}")
            }
        }
    }

    // ── Pass pending like ──────────────────────────────────────────────────────
    fun passPendingLike(pending: PendingLike) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                db.collection("swipeLikes").document(uid)
                    .collection("received").document(pending.fromUid).delete().await()
                db.collection("swipeLikes").document(uid)
                    .collection("given").document(pending.fromUid)
                    .set(mapOf("action" to "pass", "at" to System.currentTimeMillis())).await()
                _pendingLikes.value = _pendingLikes.value.filter { it.fromUid != pending.fromUid }
            } catch (_: Exception) {}
        }
    }

    // ── Manual refresh ─────────────────────────────────────────────────────────
    fun refresh() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Re-fetch via index
                val indexSnap = db.collection("userMatches").document(uid)
                    .collection("index").get().await()
                val matchIds = indexSnap.documents.mapNotNull { it.getString("matchId") ?: it.id }

                if (matchIds.isNotEmpty()) {
                    matchIds.forEach { mId ->
                        try {
                            val doc = db.collection("matches").document(mId).get().await()
                            if (doc.exists()) docToMatch(doc)?.let { mergedMatches[it.matchId] = it }
                        } catch (_: Exception) {}
                    }
                } else {
                    // Fallback
                    val snapA = db.collection("matches").whereEqualTo("userA", uid).get().await()
                    val snapB = db.collection("matches").whereEqualTo("userB", uid).get().await()
                    (snapA.documents + snapB.documents).forEach { doc ->
                        docToMatch(doc)?.let { mergedMatches[it.matchId] = it }
                    }
                }
                publishMatches()

                // Pending
                val recvSnap = db.collection("swipeLikes").document(uid)
                    .collection("received").get().await()
                val matchedUids = mergedMatches.values.flatMap { listOf(it.userA, it.userB) }.toSet()
                _pendingLikes.value = recvSnap.documents.mapNotNull { doc ->
                    try { PendingLike(
                        fromUid      = doc.getString("fromUid")      ?: doc.id,
                        fromAlias    = doc.getString("fromAlias")    ?: "Someone",
                        fromPhotoUrl = doc.getString("fromPhotoUrl") ?: "",
                        fromAvatar   = doc.getString("fromAvatar")   ?: "",
                        at           = doc.getLong("at")             ?: 0L
                    ) } catch (_: Exception) { null }
                }.filter { it.fromUid !in matchedUids }.sortedByDescending { it.at }

                // Compliments
                val cSnap = db.collection("compliments").document(uid)
                    .collection("received").get().await()
                _compliments.value = cSnap.documents.mapNotNull { doc ->
                    try { SwipeCompliment(
                        fromUid      = doc.getString("fromUid")      ?: doc.id,
                        fromAlias    = doc.getString("fromAlias")    ?: "Someone",
                        fromPhotoUrl = doc.getString("fromPhotoUrl") ?: "",
                        fromAvatar   = doc.getString("fromAvatar")   ?: "",
                        message      = doc.getString("message")      ?: "",
                        at           = doc.getLong("at")             ?: 0L,
                        isRead       = doc.getBoolean("isRead")      ?: false,
                        status       = doc.getString("status")       ?: "pending"
                    ) } catch (_: Exception) { null }
                }.sortedByDescending { it.at }

            } catch (e: Exception) {
                Log.e("MatchesVM", "refresh failed: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ── Accept compliment → open DM ────────────────────────────────────────────────────────────────
    fun acceptCompliment(
        c           : SwipeCompliment,
        myProfile   : SwipeProfile?,
        onMatchOpen : (String) -> Unit
    ) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            // Resolve profile — fetch from Firestore if not yet loaded
            val profile = try {
                myProfile ?: run {
                    val s = db.collection("swipeProfiles").document(uid).get().await()
                    SwipeProfile(
                        uid           = uid,
                        alias         = s.getString("alias")         ?: "Someone",
                        photoUrl      = s.getString("photoUrl")      ?: "",
                        avatarResName = s.getString("avatarResName") ?: "",
                        isCameraShy   = s.getBoolean("isCameraShy")  ?: false
                    )
                }
            } catch (e: Exception) {
                Log.e("MatchesVM", "acceptCompliment: could not load profile: ${e.message}")
                return@launch
            }

            // Mark locally accepted immediately — this persists across listener re-fires
            locallyAccepted.add(c.fromUid)

            // Optimistic UI update
            _compliments.value = _compliments.value.map {
                if (it.fromUid == c.fromUid) it.copy(status = "accepted") else it
            }

            val matchId = listOf(uid, c.fromUid).sorted().joinToString("_")
            val now     = System.currentTimeMillis()
            Log.d("MatchesVM", "acceptCompliment: matchId=$matchId")

            // ── Step 2: Persist acceptance in Firestore (receiver's own doc — should always succeed) ──
            try {
                db.collection("compliments").document(uid)
                    .collection("received").document(c.fromUid)
                    .set(mapOf("status" to "accepted"), com.google.firebase.firestore.SetOptions.merge())
                    .await()
                Log.d("MatchesVM", "acceptCompliment: received doc updated")
            } catch (e: Exception) {
                Log.e("MatchesVM", "acceptCompliment: received doc write FAILED — ${e.message}")
                // Don't revert; locallyAccepted keeps UI showing 'accepted'
            }

            // ── Step 3: Create or confirm match document ──────────────────────────────────
            var matchCreated = false
            try {
                val existing = db.collection("matches").document(matchId).get().await()
                if (!existing.exists()) {
                    db.collection("matches").document(matchId).set(mapOf(
                        "matchId"        to matchId,
                        "userA"          to uid,
                        "userB"          to c.fromUid,
                        "aliasA"         to profile.alias,
                        "aliasB"         to c.fromAlias,
                        "photoA"         to profile.photoUrl,
                        "photoB"         to c.fromPhotoUrl,
                        "avatarA"        to profile.avatarResName,
                        "avatarB"        to c.fromAvatar,
                        "matchedAt"      to now,
                        "chatUnlocked"   to true,
                        "fromCompliment" to true
                    )).await()
                    Log.d("MatchesVM", "acceptCompliment: match doc created")
                } else {
                    Log.d("MatchesVM", "acceptCompliment: match doc already existed")
                }
                matchCreated = true
            } catch (e: Exception) {
                Log.e("MatchesVM", "acceptCompliment: match creation FAILED — ${e.message}\n" +
                    "  → Add Firestore rules: allow write on /matches/{matchId} where userA or userB == auth.uid")
            }

            // ── Step 4: userMatches index (receiver) ──────────────────────────────────────
            try {
                db.collection("userMatches").document(uid)
                    .collection("index").document(matchId)
                    .set(mapOf("matchId" to matchId, "at" to now)).await()
            } catch (_: Exception) {}

            // ── Step 5: sender's index (may be blocked by rules — silent) ────────────────
            try {
                db.collection("userMatches").document(c.fromUid)
                    .collection("index").document(matchId)
                    .set(mapOf("matchId" to matchId, "at" to now)).await()
            } catch (_: Exception) {}

            // ── Step 6: update sender's sent-compliment status ────────────────────────────
            try {
                db.collection("swipeLikes").document(c.fromUid)
                    .collection("given").document(uid)
                    .set(mapOf("complimentStatus" to "accepted"),
                        com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (_: Exception) {}

            // ── Step 7: high-priority push to sender ──────────────────────────────────────
            try {
                com.ns.findyourintroverfriend.notifications.VeilNotifier.sendSwipeComplimentAccepted(
                    toUid         = c.fromUid,
                    acceptorAlias = profile.alias
                )
            } catch (_: Exception) {}

            // Open the DM if match doc was created (or already existed)
            if (matchCreated) onMatchOpen(matchId)
            Log.d("MatchesVM", "acceptCompliment: complete. matchCreated=$matchCreated")
        }
    }



    // ── Reject compliment ─────────────────────────────────────────────────────
    fun rejectCompliment(fromUid: String) {
        if (uid.isEmpty() || fromUid.isEmpty()) return
        _compliments.value = _compliments.value.map {
            if (it.fromUid == fromUid) it.copy(status = "rejected") else it
        }
        viewModelScope.launch {
            try {
                db.collection("compliments").document(uid)
                    .collection("received").document(fromUid)
                    .set(mapOf("status" to "rejected"),
                        com.google.firebase.firestore.SetOptions.merge()).await()
                try {
                    db.collection("swipeLikes").document(fromUid)
                        .collection("given").document(uid)
                        .set(mapOf("complimentStatus" to "rejected"),
                            com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    // ── Mark compliment read ───────────────────────────────────────────────────
    fun markComplimentAsRead(fromUid: String) {
        if (uid.isEmpty() || fromUid.isEmpty()) return
        viewModelScope.launch {
            try {
                db.collection("compliments").document(uid)
                    .collection("received").document(fromUid)
                    .update("isRead", true).await()
            } catch (_: Exception) {}
        }
    }

    /**
     * Marks ALL unread compliments as read in a single pass.
     * Optimistic update: immediately sets isRead=true locally so the UI
     * stops showing "NEW" badges and the count drops to 0 right away,
     * without waiting for the Firestore snapshot round-trip (which caused
     * the flickering loop when called one-by-one from LaunchedEffect).
     */
    fun markAllComplimentsRead() {
        val unread = _compliments.value.filter { !it.isRead }
        if (unread.isEmpty()) return

        // Optimistic local update — clears all badges instantly, no flicker
        _compliments.value = _compliments.value.map { it.copy(isRead = true) }

        // Persist each to Firestore in the background
        viewModelScope.launch {
            unread.forEach { c ->
                try {
                    db.collection("compliments").document(uid)
                        .collection("received").document(c.fromUid)
                        .update("isRead", true).await()
                } catch (_: Exception) {}
            }
        }
    }

    val unreadComplimentsCount: Int get() = _compliments.value.count { !it.isRead }
    val pendingLikesCount: Int      get() = _pendingLikes.value.size
}
