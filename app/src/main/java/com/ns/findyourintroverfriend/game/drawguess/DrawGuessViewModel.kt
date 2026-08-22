package com.ns.findyourintroverfriend.game.drawguess

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "DrawGuessVM"
private const val COLLECTION = "drawGuessRooms"

class DrawGuessViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    val myUid get() = auth.currentUser?.uid ?: ""

    private val _state = MutableStateFlow(DrawGuessUiState())
    val state: StateFlow<DrawGuessUiState> = _state.asStateFlow()

    private var roomListener : ListenerRegistration? = null
    private var chatListener : ListenerRegistration? = null
    private var strokeListener: ListenerRegistration? = null
    private var currentRoomId = ""
    private var timerJob: Job? = null

    // ── Init ─────────────────────────────────────────────────────────────────

    fun init(roomId: String) {
        currentRoomId = roomId
        viewModelScope.launch {
            try {
                val alias = db.collection("swipeProfiles").document(myUid).get().await()
                    .getString("alias")
                    ?: db.collection("users").document(myUid).get().await().getString("alias")
                    ?: "Player"
                _state.value = _state.value.copy(myUid = myUid, myAlias = alias, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(myUid = myUid, myAlias = "Player", loading = false)
            }
            listenToRoom(roomId)
            listenToChat(roomId)
            listenToStrokes(roomId)
        }
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun listenToRoom(roomId: String) {
        roomListener?.remove()
        roomListener = db.collection(COLLECTION).document(roomId)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val scoresRaw = snap.get("scores") as? Map<String, Long> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val wordChoicesRaw = snap.get("wordChoices") as? List<String> ?: emptyList()
                val room = DrawGuessRoom(
                    id               = snap.id,
                    inviteType       = snap.getString("inviteType") ?: "private",
                    matchId          = snap.getString("matchId") ?: "",
                    hostUid          = snap.getString("hostUid") ?: "",
                    hostAlias        = snap.getString("hostAlias") ?: "",
                    guestUid         = snap.getString("guestUid") ?: "",
                    guestAlias       = snap.getString("guestAlias") ?: "",
                    guestJoined      = snap.getBoolean("guestJoined") ?: false,
                    name             = snap.getString("name") ?: "",
                    hasPassword      = snap.getBoolean("hasPassword") ?: false,
                    status           = snap.getString("status") ?: "waiting",
                    maxRounds        = (snap.getLong("maxRounds") ?: 5).toInt(),
                    category         = snap.getString("category") ?: "animals",
                    currentRound     = (snap.getLong("currentRound") ?: 0).toInt(),
                    drawerUid        = snap.getString("drawerUid") ?: "",
                    currentWordMasked= snap.getString("currentWordMasked") ?: "",
                    wordChoices      = wordChoicesRaw,
                    roundPhase       = snap.getString("roundPhase") ?: "picking",
                    roundStartedAt   = snap.getLong("roundStartedAt") ?: 0L,
                    hintRevealedAt   = snap.getLong("hintRevealedAt") ?: 0L,
                    guessedCorrectly = snap.getBoolean("guessedCorrectly") ?: false,
                    guesserGuessAt   = snap.getLong("guesserGuessAt") ?: 0L,
                    scores           = scoresRaw.mapValues { it.value.toInt() },
                    inviteStatus     = snap.getString("inviteStatus") ?: "pending",
                    createdAt        = snap.getLong("createdAt") ?: 0L
                )
                val prev = _state.value.room
                // Read the actual word — both players need it for validation
                // The UI only shows it to the drawer; the guesser uses it for fuzzy matching
                val word = snap.getString("currentWord") ?: ""
                _currentWord = word  // ← FIX: was never set from listener before!
                // Show word choices only to drawer
                val choices = if (myUid == room.drawerUid && room.roundPhase == "picking") wordChoicesRaw else emptyList()
                // Only expose the actual word in state to the drawer
                val wordForDrawer = if (myUid == room.drawerUid) word else ""
                _state.value = _state.value.copy(
                    room = room, wordChoices = choices,
                    hintMasked = room.currentWordMasked,
                    currentWordForDrawer = wordForDrawer
                )
                // Start timer when drawing phase begins
                if (prev?.roundPhase != "drawing" && room.roundPhase == "drawing") startTimer(room)
                if (room.roundPhase == "reveal") { timerJob?.cancel(); _state.value = _state.value.copy(timerSecondsLeft = 0) }
            }
    }

    private fun listenToChat(roomId: String) {
        chatListener?.remove()
        chatListener = db.collection(COLLECTION).document(roomId)
            .collection("chat").orderBy("sentAt")
            .addSnapshotListener { snap, _ ->
                val msgs = snap?.documents?.mapNotNull { d ->
                    DrawGuessChat(
                        uid     = d.getString("uid") ?: "",
                        alias   = d.getString("alias") ?: "",
                        text    = d.getString("text") ?: "",
                        sentAt  = d.getLong("sentAt") ?: 0L,
                        isGuess = d.getBoolean("isGuess") ?: false
                    )
                } ?: emptyList()
                _state.value = _state.value.copy(chatMessages = msgs)
            }
    }

    private fun listenToStrokes(roomId: String) {
        strokeListener?.remove()
        strokeListener = db.collection(COLLECTION).document(roomId)
            .collection("strokes").orderBy("strokeAt")
            .addSnapshotListener { snap, _ ->
                val strokes = snap?.documents?.mapNotNull { d ->
                    @Suppress("UNCHECKED_CAST")
                    val pts = d.get("points") as? List<Map<String, Any>> ?: return@mapNotNull null
                    DrawStroke(
                        points  = pts.map { pt ->
                            val x = (pt["x"] as? Number)?.toFloat() ?: 0f
                            val y = (pt["y"] as? Number)?.toFloat() ?: 0f
                            mapOf<String, Float>("x" to x, "y" to y)
                        },
                        color   = d.getString("color") ?: "#000000",
                        width   = (d.getDouble("width") ?: 8.0).toFloat(),
                        eraser  = d.getBoolean("eraser") ?: false,
                        strokeAt= d.getLong("strokeAt") ?: 0L
                    )
                } ?: emptyList()
                _state.value = _state.value.copy(strokes = strokes)
            }
    }

    // ── Timer ────────────────────────────────────────────────────────────────

    private fun startTimer(room: DrawGuessRoom) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val elapsed = ((System.currentTimeMillis() - room.roundStartedAt) / 1000).toInt().coerceAtLeast(0)
            var remaining = (80 - elapsed).coerceAtLeast(0)
            while (remaining > 0) {
                _state.value = _state.value.copy(timerSecondsLeft = remaining)
                delay(1000)
                remaining--
                // Host reveals hint at 40s
                val r = _state.value.room ?: break
                if (remaining == 40 && r.roundPhase == "drawing" && myUid == r.hostUid) advanceHint()
            }
            _state.value = _state.value.copy(timerSecondsLeft = 0)
            val r = _state.value.room
            if (r?.roundPhase == "drawing" && myUid == r.hostUid) timeUp()
        }
    }

    // ── Public Room ──────────────────────────────────────────────────────────

    fun createPublicRoom(name: String, password: String, maxRounds: Int, category: String) {
        val alias = _state.value.myAlias
        viewModelScope.launch {
            try {
                val ref = db.collection(COLLECTION).document()
                ref.set(mapOf(
                    "inviteType"        to "public",
                    "matchId"           to "",
                    "hostUid"           to myUid,
                    "hostAlias"         to alias,
                    "guestUid"          to "",
                    "guestAlias"        to "",
                    "name"              to name,
                    "hasPassword"       to password.isNotBlank(),
                    "password"          to password,
                    "status"            to "waiting",
                    "maxRounds"         to maxRounds,
                    "category"          to category,
                    "currentRound"      to 0,
                    "drawerUid"         to "",
                    "currentWordMasked" to "",
                    "wordChoices"       to emptyList<String>(),
                    "roundPhase"        to "picking",
                    "roundStartedAt"    to 0L,
                    "hintRevealedAt"    to 0L,
                    "guessedCorrectly"  to false,
                    "guesserGuessAt"    to 0L,
                    "scores"            to mapOf(myUid to 0),
                    "inviteStatus"      to "accepted",
                    "createdAt"         to System.currentTimeMillis()
                )).await()
                currentRoomId = ref.id
                init(ref.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun joinPublicRoom(roomId: String, password: String, onWrongPw: () -> Unit = {}) {
        val alias = _state.value.myAlias
        viewModelScope.launch {
            try {
                val doc   = db.collection(COLLECTION).document(roomId).get().await()
                val stored = doc.getString("password") ?: ""
                if (stored.isNotBlank() && stored != password) { onWrongPw(); return@launch }
                val hostUid = doc.getString("hostUid") ?: ""
                db.collection(COLLECTION).document(roomId).update(mapOf(
                    "guestUid"   to myUid,
                    "guestAlias" to alias,
                    "scores.$myUid" to 0
                )).await()
                currentRoomId = roomId
                init(roomId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── Private Invite ───────────────────────────────────────────────────────

    fun createPrivateRoom(
        matchId      : String,
        myUid        : String,
        myAlias      : String,
        partnerUid   : String,
        partnerAlias : String,
        rounds       : Int,
        category     : String,
        onCreated    : (roomId: String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val ref = db.collection(COLLECTION).document()
                ref.set(mapOf(
                    "inviteType"        to "private",
                    "matchId"           to matchId,
                    "hostUid"           to myUid,
                    "hostAlias"         to myAlias,
                    "guestUid"          to partnerUid,
                    "guestAlias"        to partnerAlias,
                    "guestJoined"       to false,        // guest hasn't opened the room yet
                    "name"              to "",
                    "hasPassword"       to false,
                    "password"          to "",
                    "status"            to "waiting",
                    "maxRounds"         to rounds,
                    "category"          to category,
                    "currentRound"      to 0,
                    "drawerUid"         to "",
                    "currentWordMasked" to "",
                    "wordChoices"       to emptyList<String>(),
                    "roundPhase"        to "picking",
                    "roundStartedAt"    to 0L,
                    "hintRevealedAt"    to 0L,
                    "guessedCorrectly"  to false,
                    "guesserGuessAt"    to 0L,
                    "scores"            to mapOf(myUid to 0, partnerUid to 0),
                    "inviteStatus"      to "pending",
                    "createdAt"         to System.currentTimeMillis()
                )).await()
                currentRoomId = ref.id
                _state.value = _state.value.copy(myUid = myUid, myAlias = myAlias)
                listenToRoom(ref.id)
                listenToChat(ref.id)
                listenToStrokes(ref.id)
                onCreated(ref.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun acceptPrivateInvite(roomId: String) {
        viewModelScope.launch {
            try {
                db.collection(COLLECTION).document(roomId).update(mapOf(
                    "inviteStatus"   to "accepted",
                    "guestJoined"    to true,           // guest is now actually in the waiting room
                    "scores.$myUid"  to 0
                )).await()
                currentRoomId = roomId
                init(roomId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun declinePrivateInvite(roomId: String) {
        viewModelScope.launch {
            try { db.collection(COLLECTION).document(roomId).update("inviteStatus", "declined").await() }
            catch (_: Exception) {}
        }
    }

    /**
     * Called when the GUEST opens the WaitingRoom for a private invite.
     * Sets guestJoined=true in Firestore so the host's UI shows "✓ Ready".
     * Safe to call multiple times (idempotent Firestore field update).
     */
    fun markGuestJoined() {
        if (currentRoomId.isEmpty()) return
        val room = _state.value.room ?: return
        if (myUid != room.guestUid) return       // only the guest should set this
        if (room.guestJoined) return             // already marked, skip
        viewModelScope.launch {
            try {
                db.collection(COLLECTION).document(currentRoomId)
                    .update("guestJoined", true).await()
            } catch (e: Exception) { Log.e(TAG, "markGuestJoined: ${e.message}") }
        }
    }

    // ── Game Flow ─────────────────────────────────────────────────────────────

    fun startGame() {
        val room = _state.value.room ?: return
        val words = DrawGuessWords.threePicks(room.category)
        viewModelScope.launch {
            try {
                db.collection(COLLECTION).document(currentRoomId).update(mapOf(
                    "status"            to "playing",
                    "currentRound"      to 1,
                    "drawerUid"         to myUid,
                    "wordChoices"       to words,
                    "roundPhase"        to "picking",
                    "guessedCorrectly"  to false,
                    "currentWordMasked" to ""
                )).await()
            } catch (e: Exception) { Log.e(TAG, "startGame: ${e.message}") }
        }
    }

    fun pickWord(word: String) {
        val masked = DrawGuessWords.maskWord(word)
        viewModelScope.launch {
            try {
                db.collection(COLLECTION).document(currentRoomId).update(mapOf(
                    "currentWord"       to word,
                    "currentWordMasked" to masked,
                    "wordChoices"       to emptyList<String>(),
                    "roundPhase"        to "drawing",
                    "roundStartedAt"    to System.currentTimeMillis()
                )).await()
            } catch (e: Exception) { Log.e(TAG, "pickWord: ${e.message}") }
        }
    }

    fun sendStroke(stroke: DrawStroke) {
        viewModelScope.launch {
            try {
                db.collection(COLLECTION).document(currentRoomId)
                    .collection("strokes").add(mapOf(
                        "points"   to stroke.points,
                        "color"    to stroke.color,
                        "width"    to stroke.width,
                        "eraser"   to stroke.eraser,
                        "strokeAt" to System.currentTimeMillis()
                    ))
            } catch (e: Exception) { Log.e(TAG, "sendStroke: ${e.message}") }
        }
    }

    fun undoLastStroke() {
        viewModelScope.launch {
            try {
                val snap = db.collection(COLLECTION).document(currentRoomId)
                    .collection("strokes").orderBy("strokeAt")
                    .get().await()
                snap.documents.lastOrNull()?.reference?.delete()?.await()
            } catch (e: Exception) { Log.e(TAG, "undo: ${e.message}") }
        }
    }

    fun clearCanvas() {
        viewModelScope.launch {
            try {
                val snaps = db.collection(COLLECTION).document(currentRoomId)
                    .collection("strokes").get().await()
                val batch = db.batch()
                snaps.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            } catch (e: Exception) { Log.e(TAG, "clear: ${e.message}") }
        }
    }

    fun sendGuess(text: String) {
        if (text.isBlank()) return
        val alias = _state.value.myAlias
        val room  = _state.value.room ?: return
        if (myUid == room.drawerUid) return // drawer can't guess
        if (room.roundPhase != "drawing") return
        val target = getCurrentWord()
        val trimmed = text.trim()
        viewModelScope.launch {
            try {
                val correct = isFuzzyMatch(trimmed, target)
                val close   = !correct && isCloseGuess(trimmed, target)
                db.collection(COLLECTION).document(currentRoomId)
                    .collection("chat").add(mapOf(
                        "uid"      to myUid,
                        "alias"    to alias,
                        "text"     to trimmed,
                        "sentAt"   to System.currentTimeMillis(),
                        "isGuess"  to true,
                        "isCorrect" to correct,
                        "isClose"   to close
                    )).await()
                if (correct) {
                    val elapsed = System.currentTimeMillis() - room.roundStartedAt
                    // Spec scoring: +10 correct, +5 bonus under 15s, +2 drawer
                    val guesserPts = if (elapsed < 15_000L) 15L else 10L
                    db.collection(COLLECTION).document(currentRoomId).update(mapOf(
                        "guessedCorrectly"          to true,
                        "guesserGuessAt"            to elapsed,
                        "roundPhase"                to "reveal",
                        "scores.$myUid"             to FieldValue.increment(guesserPts),
                        "scores.${room.drawerUid}"  to FieldValue.increment(2L)
                    )).await()
                    timerJob?.cancel()
                    _state.value = _state.value.copy(guessResult = "correct")
                } else if (close) {
                    _state.value = _state.value.copy(guessResult = "close")
                }
            } catch (e: Exception) { Log.e(TAG, "sendGuess: ${e.message}") }
        }
    }

    // ── Fuzzy matching (Levenshtein distance) ─────────────────────────────────
    private fun levenshtein(a: String, b: String): Int {
        val al = a.lowercase(); val bl = b.lowercase()
        val m = al.length;      val n  = bl.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (al[i-1] == bl[j-1]) dp[i-1][j-1]
            else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        }
        return dp[m][n]
    }
    // Correct = within 1 edit for short words, 2 edits for medium, 3 for long
    private fun isFuzzyMatch(guess: String, answer: String): Boolean {
        if (answer.isBlank()) return false
        val maxEdits = when {
            answer.length <= 4  -> 0   // short words must be exact
            answer.length <= 7  -> 1
            answer.length <= 11 -> 2
            else                -> 3
        }
        return levenshtein(guess.replace(" ", ""), answer.replace(" ", "")) <= maxEdits
    }
    // Close = within 4 edits — show "warm" feedback
    private fun isCloseGuess(guess: String, answer: String): Boolean {
        if (answer.isBlank()) return false
        return levenshtein(guess.replace(" ", ""), answer.replace(" ", "")) <= 4
    }

    private fun advanceHint() {
        val room = _state.value.room ?: return
        viewModelScope.launch {
            try {
                val word   = getCurrentWord()
                val newMask = DrawGuessWords.revealOneHint(word, room.currentWordMasked)
                db.collection(COLLECTION).document(currentRoomId).update(mapOf(
                    "currentWordMasked" to newMask,
                    "hintRevealedAt"    to System.currentTimeMillis()
                )).await()
            } catch (e: Exception) { Log.e(TAG, "hint: ${e.message}") }
        }
    }

    private fun timeUp() {
        val room = _state.value.room ?: return
        if (room.roundPhase != "drawing") return
        viewModelScope.launch {
            try {
                db.collection(COLLECTION).document(currentRoomId).update(mapOf(
                    "roundPhase"       to "reveal",
                    "guessedCorrectly" to false
                )).await()
            } catch (e: Exception) { Log.e(TAG, "timeUp: ${e.message}") }
        }
    }

    fun nextRound() {
        val room = _state.value.room ?: return
        val nextRound = room.currentRound + 1
        if (nextRound > room.maxRounds) { endGame(); return }
        // Swap drawer
        val nextDrawer = if (room.drawerUid == room.hostUid) room.guestUid else room.hostUid
        val words = DrawGuessWords.threePicks(room.category)
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val ref = db.collection(COLLECTION).document(currentRoomId)
                batch.update(ref, mapOf(
                    "currentRound"      to nextRound,
                    "drawerUid"         to nextDrawer,
                    "currentWord"       to "",
                    "currentWordMasked" to "",
                    "wordChoices"       to words,
                    "roundPhase"        to "picking",
                    "roundStartedAt"    to 0L,
                    "hintRevealedAt"    to 0L,
                    "guessedCorrectly"  to false,
                    "guesserGuessAt"    to 0L
                ))
                // Delete strokes
                val strokes = db.collection(COLLECTION).document(currentRoomId)
                    .collection("strokes").get().await()
                strokes.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            } catch (e: Exception) { Log.e(TAG, "nextRound: ${e.message}") }
        }
    }

    fun playAgain() {
        val room = _state.value.room ?: return
        val words = DrawGuessWords.threePicks(room.category)
        viewModelScope.launch {
            try {
                val ref = db.collection(COLLECTION).document(currentRoomId)
                val scores = mapOf(room.hostUid to 0, room.guestUid to 0)
                db.runBatch { batch ->
                    batch.update(ref, mapOf(
                        "status"            to "playing",
                        "currentRound"      to 1,
                        "drawerUid"         to room.hostUid,
                        "currentWord"       to "",
                        "currentWordMasked" to "",
                        "wordChoices"       to words,
                        "roundPhase"        to "picking",
                        "roundStartedAt"    to 0L,
                        "hintRevealedAt"    to 0L,
                        "guessedCorrectly"  to false,
                        "scores"            to scores
                    ))
                }.await()
                val strokes = db.collection(COLLECTION).document(currentRoomId)
                    .collection("strokes").get().await()
                val batch2 = db.batch()
                strokes.documents.forEach { batch2.delete(it.reference) }
                batch2.commit().await()
            } catch (e: Exception) { Log.e(TAG, "playAgain: ${e.message}") }
        }
    }

    fun leaveRoom() {
        timerJob?.cancel()
        viewModelScope.launch {
            try {
                if (currentRoomId.isEmpty()) return@launch
                val room = _state.value.room ?: return@launch
                if (room.hostUid == myUid) {
                    db.collection(COLLECTION).document(currentRoomId).update("status", "finished").await()
                } else {
                    db.collection(COLLECTION).document(currentRoomId).update(mapOf(
                        "guestUid"   to "",
                        "guestAlias" to ""
                    )).await()
                }
            } catch (_: Exception) {}
            roomListener?.remove(); chatListener?.remove(); strokeListener?.remove()
            currentRoomId = ""
            _state.value = DrawGuessUiState()
        }
    }

    fun sendChat(text: String) {
        if (text.isBlank()) return
        val alias = _state.value.myAlias
        viewModelScope.launch {
            try {
                db.collection(COLLECTION).document(currentRoomId)
                    .collection("chat").add(mapOf(
                        "uid"     to myUid,
                        "alias"   to alias,
                        "text"    to text.trim(),
                        "sentAt"  to System.currentTimeMillis(),
                        "isGuess" to false
                    ))
            } catch (e: Exception) { Log.e(TAG, "sendChat: ${e.message}") }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    // currentWord is read from Firestore in listenToRoom and stored here.
    // Both players read it for validation; only the drawer sees it in the UI.
    private var _currentWord = ""
    private fun getCurrentWord(): String = _currentWord

    private fun endGame() {
        viewModelScope.launch {
            try { db.collection(COLLECTION).document(currentRoomId).update("status","finished").await() }
            catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        roomListener?.remove()
        chatListener?.remove()
        strokeListener?.remove()
    }
}
