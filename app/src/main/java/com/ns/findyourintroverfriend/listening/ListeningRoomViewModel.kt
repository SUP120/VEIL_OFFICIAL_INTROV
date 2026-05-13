package com.ns.findyourintroverfriend.listening

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
//  Firestore: listeningRooms/{roomId}
//
//  roomId          : String  (auto-generated)
//  hostUid         : String
//  hostAlias       : String
//  participantUid  : String  ("" = open slot)
//  participantAlias: String
//  trackId         : String  (YouTube videoId)
//  trackTitle      : String
//  isPlaying       : Boolean
//  positionSec     : Long
//  updatedAt       : Long    (ms — for drift correction)
//  status          : String  "waiting" | "listening" | "ended"
//  createdAt       : Long
// ─────────────────────────────────────────────────────────────────────────────

data class ListeningRoom(
    val roomId           : String  = "",
    val hostUid          : String  = "",
    val hostAlias        : String  = "",
    val participantUid   : String  = "",
    val participantAlias : String  = "",
    val trackId          : String  = "",
    val trackTitle       : String  = "",
    val isPlaying        : Boolean = false,
    val positionSec      : Long    = 0L,
    val updatedAt        : Long    = 0L,
    val status           : String  = "waiting",
    val createdAt        : Long    = 0L
) {
    val isFull: Boolean get() = participantUid.isNotBlank()
    val hasTrack: Boolean get() = trackId.isNotBlank()
}

sealed class LobbyUiState {
    object Loading : LobbyUiState()
    data class Loaded(val rooms: List<ListeningRoom>) : LobbyUiState()
}

sealed class RoomUiState {
    object Idle      : RoomUiState()
    object Loading   : RoomUiState()
    data class Active(val room: ListeningRoom, val amHost: Boolean) : RoomUiState()
    data class Error(val msg: String) : RoomUiState()
}

class ListeningRoomViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""
    private val myAlias get() = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "Listener"

    // ── Lobby state ───────────────────────────────────────────────────────────
    private val _lobbyState = MutableStateFlow<LobbyUiState>(LobbyUiState.Loading)
    val lobbyState: StateFlow<LobbyUiState> = _lobbyState.asStateFlow()

    // ── Room state ────────────────────────────────────────────────────────────
    private val _roomState = MutableStateFlow<RoomUiState>(RoomUiState.Idle)
    val roomState: StateFlow<RoomUiState> = _roomState.asStateFlow()

    private var lobbyListener: ListenerRegistration? = null
    private var roomListener : ListenerRegistration? = null
    private var debounceJob  : Job? = null
    private var currentRoomId = ""

    init { startLobbyListener() }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lobby — real-time list of open/active rooms
    // ─────────────────────────────────────────────────────────────────────────
    private fun startLobbyListener() {
        lobbyListener = db.collection("listeningRooms")
            .whereNotEqualTo("status", "ended")
            .orderBy("status")          // "listening" before "waiting" alphabetically — fine
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { _lobbyState.value = LobbyUiState.Loaded(emptyList()); return@addSnapshotListener }
                val rooms = snap?.documents?.mapNotNull { doc ->
                    try {
                        ListeningRoom(
                            roomId           = doc.getString("roomId")           ?: doc.id,
                            hostUid          = doc.getString("hostUid")          ?: "",
                            hostAlias        = doc.getString("hostAlias")        ?: "Someone",
                            participantUid   = doc.getString("participantUid")   ?: "",
                            participantAlias = doc.getString("participantAlias") ?: "",
                            trackId          = doc.getString("trackId")          ?: "",
                            trackTitle       = doc.getString("trackTitle")       ?: "",
                            isPlaying        = doc.getBoolean("isPlaying")       ?: false,
                            positionSec      = doc.getLong("positionSec")        ?: 0L,
                            updatedAt        = doc.getLong("updatedAt")          ?: 0L,
                            status           = doc.getString("status")           ?: "waiting",
                            createdAt        = doc.getLong("createdAt")          ?: 0L
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                _lobbyState.value = LobbyUiState.Loaded(rooms)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Create a new room (host)
    // ─────────────────────────────────────────────────────────────────────────
    fun createRoom(trackId: String, trackTitle: String, onCreated: (roomId: String) -> Unit) {
        if (uid.isEmpty()) return
        _roomState.value = RoomUiState.Loading
        viewModelScope.launch {
            try {
                val ref = db.collection("listeningRooms").document()
                val now = System.currentTimeMillis()
                ref.set(mapOf(
                    "roomId"           to ref.id,
                    "hostUid"          to uid,
                    "hostAlias"        to myAlias,
                    "participantUid"   to "",
                    "participantAlias" to "",
                    "trackId"          to trackId,
                    "trackTitle"       to trackTitle,
                    "isPlaying"        to false,
                    "positionSec"      to 0L,
                    "updatedAt"        to now,
                    "status"           to "waiting",
                    "createdAt"        to now
                )).await()
                currentRoomId = ref.id
                startRoomListener(ref.id, amHost = true)
                onCreated(ref.id)
            } catch (e: Exception) {
                Log.e("ListeningVM", "createRoom failed: ${e.message}")
                _roomState.value = RoomUiState.Error("Could not create room: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Join an existing room (participant)
    // ─────────────────────────────────────────────────────────────────────────
    fun joinRoom(roomId: String, onJoined: () -> Unit) {
        if (uid.isEmpty()) return
        _roomState.value = RoomUiState.Loading
        viewModelScope.launch {
            try {
                db.collection("listeningRooms").document(roomId)
                    .update(mapOf(
                        "participantUid"   to uid,
                        "participantAlias" to myAlias,
                        "status"           to "listening",
                        "updatedAt"        to System.currentTimeMillis()
                    )).await()
                currentRoomId = roomId
                startRoomListener(roomId, amHost = false)
                onJoined()
            } catch (e: Exception) {
                Log.e("ListeningVM", "joinRoom failed: ${e.message}")
                _roomState.value = RoomUiState.Error("Could not join room: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Host: sync play/pause/seek — debounced to avoid Firestore spam
    // ─────────────────────────────────────────────────────────────────────────
    fun syncPlayback(isPlaying: Boolean, positionSec: Long) {
        val roomId = currentRoomId.ifBlank { return }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(600)
            runCatching {
                db.collection("listeningRooms").document(roomId).update(mapOf(
                    "isPlaying"   to isPlaying,
                    "positionSec" to positionSec,
                    "updatedAt"   to System.currentTimeMillis(),
                    "status"      to if (isPlaying) "listening" else "waiting"
                )).await()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Host: change song
    // ─────────────────────────────────────────────────────────────────────────
    fun changeSong(trackId: String, trackTitle: String) {
        val roomId = currentRoomId.ifBlank { return }
        viewModelScope.launch {
            runCatching {
                db.collection("listeningRooms").document(roomId).update(mapOf(
                    "trackId"     to trackId,
                    "trackTitle"  to trackTitle,
                    "isPlaying"   to false,
                    "positionSec" to 0L,
                    "updatedAt"   to System.currentTimeMillis()
                )).await()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Leave room
    // ─────────────────────────────────────────────────────────────────────────
    fun leaveRoom() {
        val roomId = currentRoomId.ifBlank { return }
        viewModelScope.launch {
            runCatching {
                val room = (_roomState.value as? RoomUiState.Active)?.room
                if (room?.hostUid == uid) {
                    // Host leaves → end the room
                    db.collection("listeningRooms").document(roomId)
                        .update("status", "ended", "updatedAt", System.currentTimeMillis()).await()
                } else {
                    // Participant leaves → free the slot
                    db.collection("listeningRooms").document(roomId).update(mapOf(
                        "participantUid"   to "",
                        "participantAlias" to "",
                        "status"           to "waiting",
                        "updatedAt"        to System.currentTimeMillis()
                    )).await()
                }
            }
        }
        roomListener?.remove()
        currentRoomId = ""
        _roomState.value = RoomUiState.Idle
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal: real-time room listener
    // ─────────────────────────────────────────────────────────────────────────
    private fun startRoomListener(roomId: String, amHost: Boolean) {
        roomListener?.remove()
        roomListener = db.collection("listeningRooms").document(roomId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val room = ListeningRoom(
                    roomId           = snap.getString("roomId")           ?: roomId,
                    hostUid          = snap.getString("hostUid")          ?: "",
                    hostAlias        = snap.getString("hostAlias")        ?: "",
                    participantUid   = snap.getString("participantUid")   ?: "",
                    participantAlias = snap.getString("participantAlias") ?: "",
                    trackId          = snap.getString("trackId")          ?: "",
                    trackTitle       = snap.getString("trackTitle")       ?: "",
                    isPlaying        = snap.getBoolean("isPlaying")       ?: false,
                    positionSec      = snap.getLong("positionSec")        ?: 0L,
                    updatedAt        = snap.getLong("updatedAt")          ?: 0L,
                    status           = snap.getString("status")           ?: "waiting",
                    createdAt        = snap.getLong("createdAt")          ?: 0L
                )
                if (room.status == "ended" && !amHost) {
                    leaveRoom()
                    return@addSnapshotListener
                }
                _roomState.value = RoomUiState.Active(room = room, amHost = amHost)
            }
    }

    override fun onCleared() {
        super.onCleared()
        lobbyListener?.remove()
        roomListener?.remove()
        debounceJob?.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Util: extract YouTube video ID from URL or raw ID
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        fun extractVideoId(input: String): String {
            val t = input.trim()
            val patterns = listOf(
                Regex("""youtu\.be/([A-Za-z0-9_\-]{11})"""),
                Regex("""[?&]v=([A-Za-z0-9_\-]{11})"""),
                Regex("""embed/([A-Za-z0-9_\-]{11})""")
            )
            for (p in patterns) {
                p.find(t)?.groupValues?.get(1)?.let { return it }
            }
            if (t.length == 11 && t.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return t
            return ""
        }
    }
}
