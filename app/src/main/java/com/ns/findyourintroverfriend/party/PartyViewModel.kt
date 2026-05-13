package com.ns.findyourintroverfriend.party

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "PartyVM"

// ─────────────────────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────────────────────

data class ChatMessage(
    val id        : String,
    val uid       : String,
    val text      : String,
    val timestamp : Long = 0L
)

data class PartyMember(
    val uid      : String,
    val name     : String,
    val emoji    : String,
    val photoUrl : String = ""
)

// ── Game prompts ──────────────────────────────────────────────────────────────

val FINISH_PROMPTS = listOf(
    "fs_1" to "My perfect day looks like…",
    "fs_2" to "I feel most alive when…",
    "fs_3" to "Something I've never told anyone is…"
)

val HOT_TAKE_PROMPTS = listOf(
    "ht_1" to "Introverts make the most thoughtful friends",
    "ht_2" to "One deep friendship beats ten surface ones",
    "ht_3" to "Silence between two people says more than words"
)

val WYR_PROMPTS = listOf(
    "wyr_1" to listOf(
        "A long drive with music at 2 am",
        "A quiet morning coffee watching the rain"
    ),
    "wyr_2" to listOf(
        "Deep 3-hour conversations",
        "Comfortable silence side by side"
    ),
    "wyr_3" to listOf(
        "Know each other's biggest secrets now",
        "Discover them slowly over time"
    ),
    "wyr_4" to listOf(
        "A spontaneous adventure",
        "A perfectly planned beautiful routine"
    )
)

// ── Game result types ─────────────────────────────────────────────────────────

data class FinishAnswer(val myAnswer: String = "", val partnerAnswer: String = "")
data class HotTakeResult(val myChoice: String = "", val partnerChoice: String = "")
data class WyrResult(val myChoice: String = "", val partnerChoice: String = "")

data class PartyState(
    val me               : PartyMember?                   = null,
    val partner          : PartyMember?                   = null,
    val bondScore        : Int                            = 0,
    val messages         : List<ChatMessage>              = emptyList(),
    val finishData       : Map<String, FinishAnswer>      = emptyMap(),
    val hotTakeData      : Map<String, HotTakeResult>     = emptyMap(),
    val wyrData          : Map<String, WyrResult>         = emptyMap(),
    val isLoading        : Boolean                        = true,
    val error            : String?                        = null,
    // ── Room presence ─────────────────────────────────────────────────────────
    val partnerLeft      : Boolean                        = false,   // partner left, I'm still here
    val iLeft            : Boolean                        = false,   // I left the room
    // ── Island arrival & online status ────────────────────────────────────────
    val partnerIsOnIsland: Boolean                        = false,   // partner wrote enteredAt_ marker
    val partnerLastSeen  : Long                           = 0L       // epoch seconds; 0 = unknown
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class PartyViewModel(private val sessionId: String) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()
    private val myUid get() = auth.currentUser?.uid ?: ""

    private val sessionRef = db.collection("party_sessions").document(sessionId)

    private val _state = MutableStateFlow(PartyState())
    val state: StateFlow<PartyState> = _state.asStateFlow()

    private var chatListener    : ListenerRegistration? = null
    private var sessionListener : ListenerRegistration? = null
    private var finishListener  : ListenerRegistration? = null
    private var htListener      : ListenerRegistration? = null
    private var wyrListener     : ListenerRegistration? = null

    // ── Pending messages (optimistic, not yet confirmed by Firestore) ─────────
    private val pendingMessages   = mutableListOf<ChatMessage>()
    private var confirmedMessages = listOf<ChatMessage>()

    init { loadSession() }

    // ── Session init ──────────────────────────────────────────────────────────

    private fun loadSession() {
        viewModelScope.launch {
            try {
                val sessionDoc = sessionRef.get().await()
                val user1      = sessionDoc.getString("user1")  ?: ""
                val user2      = sessionDoc.getString("user2")  ?: ""
                val score      = (sessionDoc.getLong("bondScore") ?: 0L).toInt()
                val partnerUid = if (myUid == user1) user2 else user1

                val myDoc      = db.collection("users").document(myUid).get().await()
                val partnerDoc = db.collection("users").document(partnerUid).get().await()

                val me = PartyMember(
                    uid      = myUid,
                    name     = myDoc.getString("alias")    ?: "You",
                    emoji    = myDoc.getString("emoji")    ?: "🌙",
                    photoUrl = myDoc.getString("photoUrl") ?: ""
                )
                val partner = PartyMember(
                    uid      = partnerUid,
                    name     = partnerDoc.getString("alias")    ?: "Someone special",
                    emoji    = partnerDoc.getString("emoji")    ?: "✨",
                    photoUrl = partnerDoc.getString("photoUrl") ?: ""
                )

                _state.value = _state.value.copy(
                    me        = me,
                    partner   = partner,
                    bondScore = score,
                    isLoading = false
                )

                listenToChat()
                listenToGameData(partnerUid)
                listenToSessionPresence()   // detect if partner leaves

            } catch (e: Exception) {
                Log.e(TAG, "loadSession failed", e)
                _state.value = _state.value.copy(
                    error     = e.localizedMessage ?: "Something went wrong.",
                    isLoading = false
                )
            }
        }
    }

    // ── Real-time chat ────────────────────────────────────────────────────────

    /** Rebuild the visible message list = Firestore confirmed + still-pending local ones. */
    private fun rebuildMessages() {
        // Pending = local messages that don't yet have a confirmed Firestore mirror
        val stillPending = pendingMessages.filter { p ->
            confirmedMessages.none { c -> c.uid == p.uid && c.text == p.text }
        }
        val combined = (confirmedMessages + stillPending)
            .sortedBy { it.timestamp }
        Log.d(TAG, "rebuildMessages: confirmed=${confirmedMessages.size} pending=${stillPending.size} total=${combined.size}")
        _state.value = _state.value.copy(messages = combined)
    }

    private fun listenToChat() {
        // No .orderBy() — server timestamp is null during hasPendingWrites,
        // which silently drops documents from ordered queries.
        chatListener = sessionRef.collection("messages")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenToChat error: ${err.message}")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener

                val msgs = snap.documents.mapNotNull { doc ->
                    val text = doc.getString("text")?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    ChatMessage(
                        id        = doc.id,
                        uid       = doc.getString("uid") ?: "",
                        text      = text,
                        timestamp = doc.getTimestamp("timestamp")?.seconds
                                    ?: System.currentTimeMillis() / 1000
                    )
                }.sortedBy { it.timestamp }

                confirmedMessages = msgs
                rebuildMessages()
            }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val trimmed = text.trim()

        // ── 1. Create a local pending message and show it immediately ──────────
        val pending = ChatMessage(
            id        = "local_${System.currentTimeMillis()}",
            uid       = myUid,
            text      = trimmed,
            timestamp = System.currentTimeMillis() / 1000
        )
        pendingMessages.add(pending)
        rebuildMessages()   // shows message instantly

        // ── 2. Write to Firestore ─────────────────────────────────────────────
        viewModelScope.launch {
            try {
                sessionRef.collection("messages").add(
                    mapOf(
                        "uid"       to myUid,
                        "text"      to trimmed,
                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
                // The snapshot listener will fire, confirmedMessages will include the
                // real message, rebuildMessages() will drop the pending entry.
                Log.d(TAG, "sendMessage OK: $trimmed")
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage failed: ${e.message}")
                pendingMessages.remove(pending)
                rebuildMessages()
            }
        }
    }

    /** Leave this party session and mark the user as gone in Firestore. */
    fun leaveSession() {
        viewModelScope.launch {
            try {
                // Mark session doc with who left
                sessionRef.update("leftBy", myUid).await()
                // Clear user's own partySessionId so next app-open goes to Room Lobby
                db.collection("users").document(myUid)
                    .update("partySessionId", com.google.firebase.firestore.FieldValue.delete())
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "leaveSession failed: ${e.message}")
            }
            _state.value = _state.value.copy(iLeft = true)
        }
    }

    /** Listen to the session document for leftBy + partner arrival + last-seen changes. */
    private fun listenToSessionPresence() {
        sessionListener = sessionRef.addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            val partnerUid = _state.value.partner?.uid ?: return@addSnapshotListener

            // Partner left?
            val leftBy = snap.getString("leftBy") ?: ""
            if (leftBy.isNotBlank() && leftBy != myUid) {
                _state.value = _state.value.copy(partnerLeft = true)
            }

            // Partner arrived on island?
            val partnerIsOnIsland = snap.contains("enteredAt_$partnerUid")

            // Partner last seen (written periodically by their ViewModel heartbeat)
            val partnerLastSeen = snap.getTimestamp("lastSeenAt_$partnerUid")?.seconds ?: 0L

            _state.value = _state.value.copy(
                partnerIsOnIsland = partnerIsOnIsland,
                partnerLastSeen   = partnerLastSeen
            )
        }
    }


    /** Manual refresh — one-shot get (no orderBy, sort client-side) */
    fun refreshMessages() {
        viewModelScope.launch {
            try {
                val snap = sessionRef.collection("messages").get().await()
                val msgs = snap.documents.mapNotNull { doc ->
                    val text = doc.getString("text") ?: return@mapNotNull null
                    val uid  = doc.getString("uid")  ?: return@mapNotNull null
                    ChatMessage(
                        id        = doc.id,
                        uid       = uid,
                        text      = text,
                        timestamp = doc.getTimestamp("timestamp")?.seconds ?: 0L
                    )
                }.sortedBy { it.timestamp }
                _state.value = _state.value.copy(messages = msgs)
            } catch (e: Exception) {
                Log.e(TAG, "refreshMessages failed: ${e.message}")
            }
        }
    }

    // ── Game listeners ────────────────────────────────────────────────────────

    private fun listenToGameData(partnerUid: String) {
        finishListener = sessionRef.collection("game_finish")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val fsMap = snap.documents.associate { doc ->
                    doc.id to FinishAnswer(
                        myAnswer     = doc.getString(myUid) ?: "",
                        partnerAnswer = doc.getString(partnerUid) ?: ""
                    )
                }
                _state.value = _state.value.copy(finishData = fsMap)
            }

        htListener = sessionRef.collection("game_hot_take")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val htMap = snap.documents.associate { doc ->
                    doc.id to HotTakeResult(
                        myChoice     = doc.getString(myUid) ?: "",
                        partnerChoice = doc.getString(partnerUid) ?: ""
                    )
                }
                _state.value = _state.value.copy(hotTakeData = htMap)
            }

        wyrListener = sessionRef.collection("game_wyr")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val wyrMap = snap.documents.associate { doc ->
                    doc.id to WyrResult(
                        myChoice     = doc.getString(myUid) ?: "",
                        partnerChoice = doc.getString(partnerUid) ?: ""
                    )
                }
                _state.value = _state.value.copy(wyrData = wyrMap)
            }
    }

    // ── Game submit actions ───────────────────────────────────────────────────

    fun submitFinishSentence(promptId: String, answer: String) {
        if (answer.isBlank()) return
        viewModelScope.launch {
            runCatching {
                sessionRef.collection("game_finish").document(promptId)
                    .set(mapOf(myUid to answer.trim()), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }
        }
    }

    fun submitHotTake(promptId: String, choice: String) {
        viewModelScope.launch {
            runCatching {
                sessionRef.collection("game_hot_take").document(promptId)
                    .set(mapOf(myUid to choice), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }
        }
    }

    fun submitWouldYouRather(promptId: String, choice: String) {
        viewModelScope.launch {
            runCatching {
                sessionRef.collection("game_wyr").document(promptId)
                    .set(mapOf(myUid to choice), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        chatListener?.remove()
        sessionListener?.remove()
        finishListener?.remove()
        htListener?.remove()
        wyrListener?.remove()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(sessionId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PartyViewModel(sessionId) as T
            }
    }
}
