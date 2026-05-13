package com.ns.findyourintroverfriend.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

// ── Approach Request model ────────────────────────────────────────────────────
data class FriendApproach(
    val docId        : String = "",
    val fromUid      : String = "",
    val fromAlias    : String = "",
    val fromAvatar   : String = "",
    val fromPhotoUrl : String = "",
    val toUid        : String = "",
    val status       : String = "pending",
    val timestamp    : Long   = 0L
)

sealed class ApproachResult {
    object Idle        : ApproachResult()
    object Sent        : ApproachResult()
    object NeedCookies : ApproachResult()
    data class Error(val message: String) : ApproachResult()
}

/** Lightweight model for the Friends inbox list */
data class FriendChat(
    val matchId   : String = "",
    val userA     : String = "",
    val userB     : String = "",
    val aliasA    : String = "",
    val aliasB    : String = "",
    val photoA    : String = "",
    val photoB    : String = "",
    val avatarA   : String = "",
    val avatarB   : String = "",
    val createdAt : Long   = 0L
)


// ── ViewModel ─────────────────────────────────────────────────────────────────
class FeedApproachViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""

    /** UIDs I have already approached — drives the filled/outline button state */
    private val _myApproaches = MutableStateFlow<Set<String>>(emptySet())
    val myApproaches: StateFlow<Set<String>> = _myApproaches.asStateFlow()

    /** UIDs whose approach requests I sent AND they accepted → Following */
    private val _myFollowing = MutableStateFlow<Set<String>>(emptySet())
    val myFollowing: StateFlow<Set<String>> = _myFollowing.asStateFlow()

    /** Incoming pending approach requests (others → me) */
    private val _incoming = MutableStateFlow<List<FriendApproach>>(emptyList())
    val incoming: StateFlow<List<FriendApproach>> = _incoming.asStateFlow()

    /** How many free approaches remain this month */
    private val _freeLeft = MutableStateFlow(3)
    val freeLeft: StateFlow<Int> = _freeLeft.asStateFlow()

    /** One-shot result that UI observes to show snackbar/dialog */
    private val _result = MutableStateFlow<ApproachResult>(ApproachResult.Idle)
    val result: StateFlow<ApproachResult> = _result.asStateFlow()

    private var outListener  : ListenerRegistration? = null
    private var inListener   : ListenerRegistration? = null
    private var userListener : ListenerRegistration? = null
    private var friendsListener : ListenerRegistration? = null

    /** Accepted friend chats — loaded from userFriends/{uid}/index */
    private val _friendChats = MutableStateFlow<List<FriendChat>>(emptyList())
    val friendChats: StateFlow<List<FriendChat>> = _friendChats.asStateFlow()

    /**
     * Permanently hides requests the user has already acted on (accepted or declined).
     * This prevents the "flicker back" bug where:
     *   1. User taps Accept → optimistic removal from _incoming
     *   2. Firestore snapshot fires again before the write propagates → doc comes back as "pending"
     *   3. Row reappears for ~1 second, then disappears again once write completes
     *
     * By tracking acted-on docIds here, the snapshot filter always excludes them.
     */
    private val locallyActedOn = mutableSetOf<String>()

    init {
        if (uid.isNotEmpty()) {
            startListening()
            fixLegacyFriendSources()
        }
    }

    /**
     * One-time in-app migration. Tags match docs with source="friend" if they
     * were created by friend acceptance (identified by chatUnlocked==true, which
     * only acceptRequest() sets). Runs once per user, guarded by Firestore flag.
     *
     * NOTE: Old friend matches created BEFORE chatUnlocked=true was introduced
     * cannot be automatically identified. They remain in the Matches tab.
     * Only matches accepted AFTER this app update will correctly appear in Friends tab.
     */
    private fun fixLegacyFriendSources() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.getBoolean("friendMigrationDone") == true) return@launch

                Log.d("ApproachVM", "Running friend source migration for $uid")
                val indexSnap = db.collection("userMatches").document(uid)
                    .collection("index").get().await()

                var tagged = 0
                for (indexDoc in indexSnap.documents) {
                    val matchId  = indexDoc.getString("matchId") ?: indexDoc.id
                    val matchDoc = db.collection("matches").document(matchId).get().await()
                    if (!matchDoc.exists()) continue

                    // Only tag matches where chatUnlocked==true (set by acceptRequest after this update)
                    // or already has source="friend"
                    val alreadyFriend = matchDoc.getString("source") == "friend"
                    val isChatUnlocked = matchDoc.getBoolean("chatUnlocked") == true
                    if (!alreadyFriend && isChatUnlocked) {
                        db.collection("matches").document(matchId)
                            .update("source", "friend").await()
                        tagged++
                    }
                }

                db.collection("users").document(uid)
                    .update("friendMigrationDone", true).await()
                Log.d("ApproachVM", "Migration done: $tagged match docs tagged source=friend")
            } catch (e: Exception) {
                Log.e("ApproachVM", "fixLegacyFriendSources: ${e.message}")
            }
        }
    }




    // ── FriendChat model ──────────────────────────────────────────────────────
    // Lightweight model just for the Friends inbox list


    private fun startListening() {
        outListener?.remove()
        inListener?.remove()
        userListener?.remove()
        friendsListener?.remove()
        loadFriendChats()

        outListener = db.collection("friendApproaches")
            .whereEqualTo("fromUid", uid)
            .addSnapshotListener { snap, _ ->
                val docs = snap?.documents ?: emptyList()
                _myApproaches.value = docs.mapNotNull { it.getString("toUid") }.toSet()
                _myFollowing.value  = docs
                    .filter { it.getString("status") == "accepted" }
                    .mapNotNull { it.getString("toUid") }.toSet()
            }

        // Simple single-field query — no composite index needed.
        // Accepted/dismissed docs are DELETED (not updated), so they never re-appear here.
        inListener = db.collection("friendApproaches")
            .whereEqualTo("toUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("ApproachVM", "inListener error: ${err.message}")
                    return@addSnapshotListener
                }
                _incoming.value = snap?.documents?.mapNotNull { doc ->
                    try {
                        FriendApproach(
                            docId        = doc.id,
                            fromUid      = doc.getString("fromUid")      ?: "",
                            fromAlias    = doc.getString("fromAlias")    ?: "Anonymous",
                            fromAvatar   = doc.getString("fromAvatar")   ?: "",
                            fromPhotoUrl = doc.getString("fromPhotoUrl") ?: "",
                            toUid        = doc.getString("toUid")        ?: "",
                            status       = doc.getString("status")       ?: "pending",
                            timestamp    = doc.getLong("timestamp")      ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
                    ?.filter { it.status == "pending" && it.docId !in locallyActedOn }
                    ?.sortedByDescending { it.timestamp }
                    ?: emptyList()
            }

        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val reset = (snap.getLong("freeApproachesResetMonth") ?: -1L).toInt()
                val used  = if (reset == currentYearMonth())
                    (snap.getLong("freeApproachesUsedThisMonth") ?: 0L).toInt() else 0
                _freeLeft.value = maxOf(0, 3 - used)
            }
    }

    // ── Send a FREE approach ──────────────────────────────────────────────────
    fun sendFreeApproach(
        toUid       : String,
        toAlias     : String,
        myAlias     : String,
        myAvatar    : String,
        myPhotoUrl  : String
    ) {
        if (uid.isEmpty() || toUid == uid) return
        viewModelScope.launch {
            try {
                val docId  = "${uid}_${toUid}"
                val docRef = db.collection("friendApproaches").document(docId)
                if (docRef.get().await().exists()) { _result.value = ApproachResult.Sent; return@launch }

                // Connect is free — no quota, no cookie cost
                docRef.set(buildApproachMap(toUid, toAlias, myAlias, myAvatar, myPhotoUrl)).await()

                runCatching {
                    db.collection("users").document(toUid)
                        .set(mapOf("approachesReceived" to FieldValue.increment(1)),
                            com.google.firebase.firestore.SetOptions.merge()).await()
                }

                notifyApproach(toUid, myAlias)
                _result.value = ApproachResult.Sent
            } catch (e: Exception) {
                Log.e("ApproachVM", e.message ?: "")
                _result.value = ApproachResult.Error(e.message ?: "Failed")
            }
        }
    }

    // ── Send a COOKIE-PAID approach ───────────────────────────────────────────
    fun sendPaidApproach(
        toUid      : String,
        toAlias    : String,
        myAlias    : String,
        myAvatar   : String,
        myPhotoUrl : String,
        cookieVm   : com.ns.findyourintroverfriend.billing.CookieViewModel
    ) {
        if (uid.isEmpty()) return
        cookieVm.spendCookies(15, "approach_$toUid") { success ->
            if (!success) { _result.value = ApproachResult.Error("Not enough cookies"); return@spendCookies }
            viewModelScope.launch {
                try {
                    val docId = "${uid}_${toUid}"
                    db.collection("friendApproaches").document(docId)
                        .set(buildApproachMap(toUid, toAlias, myAlias, myAvatar, myPhotoUrl)).await()
                    runCatching {
                        db.collection("users").document(toUid)
                            .set(mapOf("approachesReceived" to FieldValue.increment(1)),
                                com.google.firebase.firestore.SetOptions.merge()).await()
                    }
                    notifyApproach(toUid, myAlias)
                    _result.value = ApproachResult.Sent
                } catch (e: Exception) {
                    _result.value = ApproachResult.Error(e.message ?: "Failed")
                }
            }
        }
    }

    // ── Accept an incoming request ────────────────────────────────────────────────
    /**
     * Accepts a friendship request:
     * 1. Instantly removes the request from UI (optimistic + locallyActedOn guard)
     * 2. Fetches the current user's swipeProfile for alias/photo
     * 3. Batch-writes:
     *    • DELETE the friendApproach doc  → no flicker, no index requirement
     *    • CREATE matches/{matchId}        → both users appear in Messages tab
     *    • CREATE userMatches index entry for BOTH users
     */
    fun acceptRequest(request: FriendApproach) {
        val docId = request.docId
        // 1. Mark acted-on + optimistic removal
        locallyActedOn.add(docId)
        _incoming.value = _incoming.value.filter { it.docId != docId }

        viewModelScope.launch {
            try {
                // 2. Fetch acceptor (current user) swipeProfile for alias + photo
                val mySnap   = db.collection("swipeProfiles").document(uid).get().await()
                val myAlias  = mySnap.getString("alias")    ?: ""
                val myPhoto  = mySnap.getString("photoUrl") ?: ""
                val myAvatar = mySnap.getString("avatarResName") ?: ""

                // 3. Generate a unique matchId
                val matchId = db.collection("matches").document().id
                val now     = System.currentTimeMillis()

                val matchData = mapOf(
                    "matchId"   to matchId,
                    "userA"     to request.fromUid,
                    "userB"     to uid,
                    "aliasA"    to request.fromAlias,
                    "aliasB"    to myAlias,
                    "photoA"    to request.fromPhotoUrl,
                    "photoB"    to myPhoto,
                    "avatarA"   to request.fromAvatar,
                    "avatarB"   to myAvatar,
                    "matchedAt" to now,
                    "source"    to "friend",   // ← marks as Feed friendship, NOT swipe match
                    "chatUnlocked" to true,    // friends can chat immediately
                    "icebreakerAnsweredA" to "",
                    "icebreakerAnsweredB" to ""
                )
                val indexData = mapOf("matchId" to matchId, "createdAt" to now)

                val batch = db.batch()
                batch.delete(db.collection("friendApproaches").document(docId))
                batch.set(db.collection("matches").document(matchId), matchData)
                // Write to userMatches index — MatchesViewModel reads from here to discover all matches.
                // source="friend" on the match doc is what routes it to the Friends tab (not Matches tab).
                batch.set(
                    db.collection("userMatches").document(request.fromUid)
                        .collection("index").document(matchId), indexData
                )
                batch.set(
                    db.collection("userMatches").document(uid)
                        .collection("index").document(matchId), indexData
                )
                batch.commit().await()
                Log.d("ApproachVM", "accept OK → friend match (source=friend) created: $matchId")

            } catch (e: Exception) {
                Log.e("ApproachVM", "acceptRequest failed: ${e.message}")
                locallyActedOn.remove(docId)
                startListening() // restore list so user can retry
            }
        }
    }

    // ── Dismiss an incoming request ────────────────────────────────────────────────
    // Delete the doc entirely — avoids any flicker from status update.
    fun dismissRequest(docId: String) {
        locallyActedOn.add(docId)
        _incoming.value = _incoming.value.filter { it.docId != docId }
        viewModelScope.launch {
            try {
                db.collection("friendApproaches").document(docId).delete().await()
            } catch (e: Exception) {
                Log.e("ApproachVM", "dismiss: ${e.message}")
                locallyActedOn.remove(docId)
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try {
                db.collection("chats").document(chatId).delete().await()
            } catch (e: Exception) { Log.e("FeedVM", "deleteChat: ${e.message}") }
        }
    }

    fun blockUser(targetUid: String) {
        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .update("blockedUsers", FieldValue.arrayUnion(targetUid)).await()
            } catch (e: Exception) { Log.e("FeedVM", "blockUser: ${e.message}") }
        }
    }

    fun resetResult() { _result.value = ApproachResult.Idle }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun buildApproachMap(
        toUid: String, toAlias: String,
        myAlias: String, myAvatar: String, myPhotoUrl: String
    ) = mapOf(
        "fromUid"      to uid,
        "fromAlias"    to myAlias,
        "fromAvatar"   to myAvatar,
        "fromPhotoUrl" to myPhotoUrl,
        "toUid"        to toUid,
        "toAlias"      to toAlias,
        "status"       to "pending",
        "timestamp"    to System.currentTimeMillis()
    )

    private fun notifyApproach(toUid: String, fromAlias: String) {
        viewModelScope.launch {
            runCatching {
                com.ns.findyourintroverfriend.notifications.VeilNotifier
                    .notifyApproach(toUid, fromAlias, db)
            }
        }
    }

    private fun currentYearMonth(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
    }

    override fun onCleared() {
        super.onCleared()
        outListener?.remove()
        inListener?.remove()
        userListener?.remove()
        friendsListener?.remove()
    }

    // ── Load friend chats from userFriends/{uid}/index ────────────────────────
    private fun loadFriendChats() {
        if (uid.isEmpty()) return
        friendsListener = db.collection("userFriends").document(uid)
            .collection("index")
            .addSnapshotListener { snap, err ->
                if (err != null) { android.util.Log.e("ApproachVM", "friendChats error: ${err.message}"); return@addSnapshotListener }
                _friendChats.value = snap?.documents?.mapNotNull { doc ->
                    try {
                        FriendChat(
                            matchId   = doc.getString("matchId") ?: doc.id,
                            userA     = doc.getString("userA")   ?: "",
                            userB     = doc.getString("userB")   ?: "",
                            aliasA    = doc.getString("aliasA")  ?: "",
                            aliasB    = doc.getString("aliasB")  ?: "",
                            photoA    = doc.getString("photoA")  ?: "",
                            photoB    = doc.getString("photoB")  ?: "",
                            avatarA   = doc.getString("avatarA") ?: "",
                            avatarB   = doc.getString("avatarB") ?: "",
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    } catch (e: Exception) { null }
                }?.sortedByDescending { it.createdAt } ?: emptyList()
            }
    }
}

// ── Introvert Score helpers (shared across screens) ───────────────────────────
fun computeIntrovertScore(
    postCount        : Long,
    matchCount       : Long,
    approachesReceived: Long,
    joinedAt         : Long
): Int {
    val days = if (joinedAt > 0)
        ((System.currentTimeMillis() - joinedAt) / 86_400_000L).toInt() else 0
    return minOf(1000,
        minOf(postCount, 20L).toInt()          * 15 +   // max 300  — content creator
        minOf(matchCount, 20L).toInt()         * 15 +   // max 300  — social resonance
        minOf(approachesReceived, 20L).toInt() * 10 +   // max 200  — others find you interesting
        minOf(days, 200)                                  // max 200  — tenure loyalty
    )
}

fun introvertTier(score: Int): Pair<String, String> = when {
    score >= 850 -> "Veil Legend"        to "👁️"
    score >= 650 -> "Soul Sage"          to "🌸"
    score >= 450 -> "Deep Thinker"       to "🔮"
    score >= 250 -> "Thoughtful Dreamer" to "✨"
    score >= 100 -> "Quiet Observer"     to "🌙"
    else         -> "Wandering Soul"     to "🌿"
}
