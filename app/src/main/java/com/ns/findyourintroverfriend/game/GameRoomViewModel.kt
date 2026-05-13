package com.ns.findyourintroverfriend.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Models ────────────────────────────────────────────────────────────────────
data class ChatMessage(
    val uid   : String = "",
    val alias : String = "",
    val text  : String = "",
    val sentAt: Long   = 0L
)

data class GameRoom(
    val id               : String       = "",
    val name             : String       = "",
    val hostUid          : String       = "",
    val hostAlias        : String       = "",
    val hasPassword      : Boolean      = false,
    val maxRounds        : Int          = 5,
    val gameType         : String       = "guess_country",  // game identifier
    val status           : String       = "waiting",        // waiting | playing | finished
    val playerCount      : Int          = 0,
    val players          : Map<String, PlayerInfo> = emptyMap(),
    val currentRound     : Int          = 0,
    val currentEmoji     : String       = "",
    val currentHint      : String       = "",
    val currentAnswer    : String       = "",
    val options          : List<String> = emptyList(),
    val roundPhase       : String       = "emoji",
    val roundWinner      : String       = "",
    val roundWinnerAlias : String       = "",
    val createdAt        : Long         = 0L
)

data class PlayerInfo(val alias: String = "", val score: Int = 0)

data class GameRoomUiState(
    val rooms            : List<GameRoom>    = emptyList(),
    val currentRoom      : GameRoom?         = null,
    val loading          : Boolean           = true,
    val joining          : Boolean           = false,
    val error            : String?           = null,
    val myAlias          : String            = "",
    val myUid            : String            = "",
    val mySelection      : String            = "",
    val selectedGameType : String            = "",   // set when user picks a game from hub
    val chatMessages     : List<ChatMessage> = emptyList()
)

class GameRoomViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    val uid  get() = auth.currentUser?.uid ?: ""

    private val _state = MutableStateFlow(GameRoomUiState())
    val state: StateFlow<GameRoomUiState> = _state.asStateFlow()

    private var roomsListener : ListenerRegistration? = null
    private var roomListener  : ListenerRegistration? = null
    private var chatListener  : ListenerRegistration? = null
    private var currentRoomId = ""
    private var usedCountries = mutableListOf<String>()

    init { loadAlias(); listenToRooms() }

    // ── Alias ─────────────────────────────────────────────────────────────────
    private fun loadAlias() {
        viewModelScope.launch {
            try {
                val alias = db.collection("swipeProfiles").document(uid).get().await()
                    .getString("alias")
                    ?: db.collection("users").document(uid).get().await().getString("alias")
                    ?: "Player"
                _state.value = _state.value.copy(myAlias = alias, myUid = uid, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(myAlias = "Player", myUid = uid, loading = false)
            }
        }
    }

    // ── Lobby listener ────────────────────────────────────────────────────────
    fun listenToRooms(gameType: String = "guess_country") {
        roomsListener?.remove()
        roomsListener = db.collection("gameRooms")
            .whereEqualTo("status", "waiting")
            .whereEqualTo("gameType", gameType)
            .addSnapshotListener { snap, _ ->
                val rooms = snap?.documents?.mapNotNull { d ->
                    @Suppress("UNCHECKED_CAST")
                    val pRaw = d.get("players") as? Map<String, Any> ?: emptyMap()
                    val players = pRaw.mapValues { (_, v) ->
                        val m = v as? Map<String, Any> ?: emptyMap()
                        PlayerInfo(m["alias"] as? String ?: "", (m["score"] as? Long)?.toInt() ?: 0)
                    }
                    GameRoom(
                        id          = d.id,
                        name        = d.getString("name") ?: "Room",
                        hostUid     = d.getString("hostUid") ?: "",
                        hostAlias   = d.getString("hostAlias") ?: "",
                        hasPassword = (d.getString("password") ?: "").isNotEmpty(),
                        maxRounds   = (d.getLong("maxRounds") ?: 5).toInt(),
                        gameType    = d.getString("gameType") ?: "guess_country",
                        status      = d.getString("status") ?: "waiting",
                        playerCount = (d.getLong("playerCount") ?: 0).toInt(),
                        players     = players,
                        createdAt   = d.getLong("createdAt") ?: 0L
                    )
                } ?: emptyList()
                _state.value = _state.value.copy(rooms = rooms.sortedByDescending { it.createdAt })
            }
    }

    // ── Create room ───────────────────────────────────────────────────────────
    fun createRoom(name: String, password: String, maxRounds: Int, gameType: String = "guess_country") {
        val alias = _state.value.myAlias
        viewModelScope.launch {
            try {
                val ref = db.collection("gameRooms").document()
                ref.set(mapOf(
                    "name"             to name,
                    "hostUid"          to uid,
                    "hostAlias"        to alias,
                    "password"         to password,
                    "maxRounds"        to maxRounds,
                    "gameType"         to gameType,
                    "status"           to "waiting",
                    "playerCount"      to 1,
                    "players"          to mapOf(uid to mapOf("alias" to alias, "score" to 0)),
                    "currentRound"     to 0,
                    "currentEmoji"     to "",
                    "currentHint"      to "",
                    "currentAnswer"    to "",
                    "options"          to emptyList<String>(),
                    "roundPhase"       to "emoji",
                    "roundWinner"      to "",
                    "roundWinnerAlias" to "",
                    "createdAt"        to System.currentTimeMillis()
                )).await()
                currentRoomId = ref.id
                listenToRoom(ref.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── Join room ─────────────────────────────────────────────────────────────
    fun joinRoom(roomId: String, password: String = "", onWrongPw: () -> Unit = {}) {
        _state.value = _state.value.copy(joining = true)
        viewModelScope.launch {
            try {
                val doc   = db.collection("gameRooms").document(roomId).get().await()
                val roomPw = doc.getString("password") ?: ""
                if (roomPw.isNotEmpty() && roomPw != password) {
                    _state.value = _state.value.copy(joining = false); onWrongPw(); return@launch
                }
                val alias = _state.value.myAlias
                db.collection("gameRooms").document(roomId).update(mapOf(
                    "players.$uid" to mapOf("alias" to alias, "score" to 0),
                    "playerCount"  to FieldValue.increment(1)
                )).await()
                currentRoomId = roomId
                listenToRoom(roomId)
                _state.value = _state.value.copy(joining = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(joining = false, error = e.message)
            }
        }
    }

    // ── Room listener ─────────────────────────────────────────────────────────
    private fun listenToRoom(roomId: String) {
        roomListener?.remove()
        roomListener = db.collection("gameRooms").document(roomId)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val pRaw = snap.get("players") as? Map<String, Any> ?: emptyMap()
                val players = pRaw.mapValues { (_, v) ->
                    val m = v as? Map<String, Any> ?: emptyMap()
                    PlayerInfo(m["alias"] as? String ?: "", (m["score"] as? Long)?.toInt() ?: 0)
                }
                @Suppress("UNCHECKED_CAST")
                val opts = snap.get("options") as? List<String> ?: emptyList()
                val room = GameRoom(
                    id               = snap.id,
                    name             = snap.getString("name") ?: "",
                    hostUid          = snap.getString("hostUid") ?: "",
                    hostAlias        = snap.getString("hostAlias") ?: "",
                    hasPassword      = (snap.getString("password") ?: "").isNotEmpty(),
                    maxRounds        = (snap.getLong("maxRounds") ?: 5).toInt(),
                    status           = snap.getString("status") ?: "waiting",
                    playerCount      = (snap.getLong("playerCount") ?: 0).toInt(),
                    players          = players,
                    currentRound     = (snap.getLong("currentRound") ?: 0).toInt(),
                    currentEmoji     = snap.getString("currentEmoji") ?: "",
                    currentHint      = snap.getString("currentHint") ?: "",
                    currentAnswer    = snap.getString("currentAnswer") ?: "",
                    options          = opts,
                    roundPhase       = snap.getString("roundPhase") ?: "emoji",
                    roundWinner      = snap.getString("roundWinner") ?: "",
                    roundWinnerAlias = snap.getString("roundWinnerAlias") ?: "",
                    createdAt        = snap.getLong("createdAt") ?: 0L
                )
                // Reset local selection whenever a new round starts
                val prev = _state.value.currentRoom
                val newSel = if (prev?.currentRound != room.currentRound) "" else _state.value.mySelection
                _state.value = _state.value.copy(currentRoom = room, mySelection = newSel)
            }
        listenToChat(roomId)
    }

    // ── Chat listener ─────────────────────────────────────────────────────────
    private fun listenToChat(roomId: String) {
        chatListener?.remove()
        chatListener = db.collection("gameRooms").document(roomId)
            .collection("chat")
            .orderBy("sentAt")
            .addSnapshotListener { snap, _ ->
                val msgs = snap?.documents?.mapNotNull { d ->
                    ChatMessage(
                        uid    = d.getString("uid")   ?: "",
                        alias  = d.getString("alias") ?: "",
                        text   = d.getString("text")  ?: "",
                        sentAt = d.getLong("sentAt")  ?: 0L
                    )
                } ?: emptyList()
                _state.value = _state.value.copy(chatMessages = msgs)
            }
    }

    // ── Send chat message ─────────────────────────────────────────────────────
    fun sendChat(text: String) {
        if (text.isBlank() || currentRoomId.isEmpty()) return
        val alias = _state.value.myAlias
        viewModelScope.launch {
            try {
                db.collection("gameRooms").document(currentRoomId)
                    .collection("chat")
                    .add(mapOf(
                        "uid"    to uid,
                        "alias"  to alias,
                        "text"   to text.trim(),
                        "sentAt" to System.currentTimeMillis()
                    ))
            } catch (e: Exception) { Log.e("GameVM", "sendChat: ${e.message}") }
        }
    }

    // ── Start game (host) ─────────────────────────────────────────────────────
    fun startGame() {
        usedCountries.clear()
        viewModelScope.launch {
            db.collection("gameRooms").document(currentRoomId).update("status", "playing").await()
        }
        startRound(1)
    }

    // ── Start round (host picks country + 2 decoys) ───────────────────────────
    fun startRound(round: Int) {
        val room = _state.value.currentRoom ?: return
        if (round > room.maxRounds) { endGame(); return }

        val available = ALL_COUNTRIES.filter { it.name !in usedCountries }
        val pool      = if (available.isEmpty()) { usedCountries.clear(); ALL_COUNTRIES } else available
        val country   = pool.random()
        usedCountries.add(country.name)

        // Pick 2 decoys (different from correct, unique)
        val decoys = ALL_COUNTRIES
            .filter { it.name != country.name }
            .shuffled()
            .take(2)

        val options = (listOf(country.name) + decoys.map { it.name }).shuffled()

        viewModelScope.launch {
            try {
                db.collection("gameRooms").document(currentRoomId).update(mapOf(
                    "currentRound"     to round,
                    "currentEmoji"     to country.emoji,
                    "currentHint"      to country.hint,
                    "currentAnswer"    to country.name.lowercase(),
                    "options"          to options.map { it.lowercase() },
                    "roundPhase"       to "emoji",
                    "roundWinner"      to "",
                    "roundWinnerAlias" to ""
                )).await()
            } catch (e: Exception) { Log.e("GameVM", e.message ?: "") }
        }
    }

    // ── Player taps an option ─────────────────────────────────────────────────
    fun selectOption(chosen: String) {
        val room  = _state.value.currentRoom ?: return
        // Ignore if already revealed or already picked or not playing
        if (room.roundPhase == "reveal") return
        if (_state.value.mySelection.isNotEmpty()) return
        if (room.status != "playing") return

        // Record locally immediately (optimistic)
        _state.value = _state.value.copy(mySelection = chosen)

        val isCorrect = chosen.equals(room.currentAnswer, ignoreCase = true)
        if (isCorrect && room.roundWinner.isEmpty()) {
            advanceToReveal(uid, _state.value.myAlias)
        }
        // Wrong tap: just highlight red locally — no server write needed
    }

    // ── Phase advances (host only) ────────────────────────────────────────────
    fun advanceToHint() {
        viewModelScope.launch {
            db.collection("gameRooms").document(currentRoomId)
                .update("roundPhase", "hint").await()
        }
    }

    fun advanceToReveal(winnerId: String = "", winnerAlias: String = "") {
        viewModelScope.launch {
            val updates = mutableMapOf<String, Any>(
                "roundPhase"       to "reveal",
                "roundWinner"      to winnerId,
                "roundWinnerAlias" to winnerAlias
            )
            if (winnerId.isNotEmpty()) {
                updates["players.$winnerId.score"] = FieldValue.increment(1)
            }
            try {
                db.collection("gameRooms").document(currentRoomId).update(updates).await()
            } catch (e: Exception) { Log.e("GameVM", e.message ?: "") }
        }
    }

    private fun endGame() {
        viewModelScope.launch {
            db.collection("gameRooms").document(currentRoomId)
                .update("status", "finished").await()
        }
    }

    // ── Leave room ────────────────────────────────────────────────────────────
    fun leaveRoom() {
        viewModelScope.launch {
            try {
                if (currentRoomId.isEmpty()) return@launch
                val room = _state.value.currentRoom
                if (room?.hostUid == uid) {
                    db.collection("gameRooms").document(currentRoomId)
                        .update("status", "finished").await()
                } else {
                    db.collection("gameRooms").document(currentRoomId).update(mapOf(
                        "players.$uid" to FieldValue.delete(),
                        "playerCount"  to FieldValue.increment(-1)
                    )).await()
                }
            } catch (_: Exception) {}
            roomListener?.remove()
            currentRoomId = ""
            _state.value = _state.value.copy(currentRoom = null, mySelection = "")
        }
    }

    fun setSelectedGame(gameType: String) {
        _state.value = _state.value.copy(selectedGameType = gameType)
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }


    override fun onCleared() {
        super.onCleared()
        roomsListener?.remove()
        roomListener?.remove()
        chatListener?.remove()
    }
}
