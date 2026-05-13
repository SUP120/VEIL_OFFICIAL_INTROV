package com.ns.findyourintroverfriend.listening

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

// ── Track catalogue — rawName maps to res/raw/<name>.mp3 ─────────────────────
data class MusicTrack(
    val rawName : String,   // filename without extension in res/raw/
    val title   : String,
    val artist  : String,
    val genre   : String
) {
    /** Resolves the raw resource ID at runtime — returns 0 if file missing. */
    fun resId(ctx: Context): Int =
        ctx.resources.getIdentifier(rawName, "raw", ctx.packageName)
}

val MUSIC_TRACKS = listOf(
    MusicTrack("lofi_good_night",         "Good Night",       "Fassounds",  "lofi"),
    MusicTrack("lofi_study_calm",         "Study Calm",       "Fassounds",  "lofi"),
    MusicTrack("lofi_hip_hop",            "Hip Hop Vibes",    "Leberch",    "lofi"),
    MusicTrack("lofi_chill_1",            "Lofi Chill",       "Lemon Lab",  "lofi"),
    MusicTrack("lofi_girl_1",             "Lofi Girl I",      "Monda",      "lofi"),
    MusicTrack("lofi_girl_2",             "Lofi Girl II",     "Monda",      "lofi"),
    MusicTrack("lofi_music_1",            "Lofi Music I",     "Monda",      "lofi"),
    MusicTrack("lofi_music_2",            "Lofi Music II",    "Monda",      "lofi"),
    MusicTrack("lofi_music_3",            "Lofi Music III",   "Monda",      "lofi"),
    MusicTrack("lofi_chill_2",            "Lofi Chill II",    "Monda",      "lofi"),
    MusicTrack("lofi_mood",              "Lofi Mood",        "Pulsebox",   "lofi"),
    MusicTrack("lofi_production",        "Lofi Production",  "Pulsebox",   "lofi"),
    MusicTrack("lofi_girl_3",            "Lofi Girl III",    "Watermello", "lofi"),
    MusicTrack("lofi_girl_4",            "Lofi Girl IV",     "Watermello", "lofi"),
    MusicTrack("ambient_sleepy_cat",     "Sleepy Cat",       "Mixkit",     "ambient"),
    MusicTrack("ambient_sweet_september","Sweet September",  "Mixkit",     "ambient"),
)

// ── Shuffle engine — each track plays exactly once per cycle ──────────────────
class ShuffleEngine(private val tracks: List<MusicTrack>) {
    private var remaining = mutableListOf<Int>()
    init { resetCycle() }
    private fun resetCycle() { remaining = tracks.indices.toMutableList().also { it.shuffle() } }
    fun next(): Int {
        if (remaining.isEmpty()) resetCycle()
        return remaining.removeAt(0)
    }
    fun peek(): Int = if (remaining.isEmpty()) { resetCycle(); remaining[0] } else remaining[0]
}

// ── Data models ───────────────────────────────────────────────────────────────
data class MusicRoom(
    val roomId       : String         = "",
    val hostUid      : String         = "",
    val hostAlias    : String         = "",
    val members      : List<String>   = emptyList(),  // list of uid
    val memberAliases: Map<String,String> = emptyMap(),
    val maxMembers   : Int            = 5,
    val isPrivate    : Boolean        = false,
    val trackIndex   : Int            = 0,
    val shuffleOrder : List<Int>      = emptyList(),  // current cycle order
    val cyclesDone   : Int            = 0,
    val isPlaying    : Boolean        = false,
    val positionMs   : Long           = 0L,
    val updatedAt    : Long           = 0L,
    val status       : String         = "waiting",    // waiting | playing | ended
    val createdAt    : Long           = 0L
) {
    val isFull: Boolean get() = members.size >= maxMembers
    val currentTrack: MusicTrack? get() = MUSIC_TRACKS.getOrNull(
        shuffleOrder.getOrNull(trackIndex) ?: 0
    )
}

data class ChatMessage(
    val id        : String = "",
    val uid       : String = "",
    val alias     : String = "",
    val text      : String = "",
    val sentAt    : Long   = 0L
)

sealed class MusicRoomUiState {
    object Idle    : MusicRoomUiState()
    object Loading : MusicRoomUiState()
    data class Active(val room: MusicRoom, val amHost: Boolean) : MusicRoomUiState()
    data class Error(val msg: String) : MusicRoomUiState()
}

sealed class LobbyState {
    object Loading : LobbyState()
    data class Loaded(val rooms: List<MusicRoom>) : LobbyState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────
class MusicRoomViewModel : ViewModel() {

    private val db   = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    val myUid   : String get() = auth.currentUser?.uid ?: ""
    val myAlias : String get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Listener"

    private val _lobby = MutableStateFlow<LobbyState>(LobbyState.Loading)
    val lobby: StateFlow<LobbyState> = _lobby.asStateFlow()

    private val _roomState = MutableStateFlow<MusicRoomUiState>(MusicRoomUiState.Idle)
    val roomState: StateFlow<MusicRoomUiState> = _roomState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var lobbyReg  : ListenerRegistration? = null
    private var roomReg   : ListenerRegistration? = null
    private var chatReg   : ListenerRegistration? = null
    private var currentRoomId = ""

    private var mediaPlayer  : MediaPlayer? = null
    private var syncJob      : Job? = null
    private var debounceJob  : Job? = null

    // Position broadcast every 3s while playing (host only)
    private var positionBroadcastJob: Job? = null

    init { startLobbyListener() }

    // ── Lobby ─────────────────────────────────────────────────────────────────
    private fun startLobbyListener() {
        lobbyReg?.remove()
        lobbyReg = db.collection("musicRooms")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snap, _ ->
                val rooms = snap?.documents
                    ?.mapNotNull { parseRoom(it) }
                    ?.filter { it.status != "ended" }
                    ?: emptyList()
                _lobby.value = LobbyState.Loaded(rooms)
            }
    }

    // ── Create Room ───────────────────────────────────────────────────────────
    fun createRoom(maxMembers: Int, isPrivate: Boolean, onCreated: (String) -> Unit) {
        if (myUid.isEmpty()) return
        _roomState.value = MusicRoomUiState.Loading
        viewModelScope.launch {
            try {
                // Build a shuffled order for first cycle
                val order = MUSIC_TRACKS.indices.toMutableList().also { it.shuffle() }
                val ref   = db.collection("musicRooms").document()
                val now   = System.currentTimeMillis()
                ref.set(mapOf(
                    "roomId"        to ref.id,
                    "hostUid"       to myUid,
                    "hostAlias"     to myAlias,
                    "members"       to listOf(myUid),
                    "memberAliases" to mapOf(myUid to myAlias),
                    "maxMembers"    to maxMembers,
                    "isPrivate"     to isPrivate,
                    "trackIndex"    to 0,
                    "shuffleOrder"  to order,
                    "cyclesDone"    to 0,
                    "isPlaying"     to false,
                    "positionMs"    to 0L,
                    "updatedAt"     to now,
                    "status"        to "waiting",
                    "createdAt"     to now
                )).await()
                currentRoomId = ref.id
                startRoomListener(ref.id, amHost = true)
                startChatListener(ref.id)
                // Broadcast FCM to all users (via Firestore trigger → Cloud Function)
                if (!isPrivate) broadcastInvite(ref.id, myAlias)
                onCreated(ref.id)
            } catch (e: Exception) {
                _roomState.value = MusicRoomUiState.Error("Could not create room")
                Log.e("MusicRoomVM", "createRoom: ${e.message}")
            }
        }
    }

    // ── Join Room ─────────────────────────────────────────────────────────────
    fun joinRoom(roomId: String, onJoined: () -> Unit) {
        if (myUid.isEmpty()) return
        _roomState.value = MusicRoomUiState.Loading
        viewModelScope.launch {
            try {
                val doc = db.collection("musicRooms").document(roomId).get().await()
                val members = (doc.get("members") as? List<*>)?.map { it.toString() } ?: emptyList()
                val max     = (doc.getLong("maxMembers") ?: 5).toInt()
                if (members.size >= max && myUid !in members) {
                    _roomState.value = MusicRoomUiState.Error("Room is full")
                    return@launch
                }
                val newMembers = if (myUid in members) members else members + myUid
                val aliases    = (doc.get("memberAliases") as? Map<*, *>)
                    ?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() }
                    ?.toMutableMap() ?: mutableMapOf()
                aliases[myUid] = myAlias
                db.collection("musicRooms").document(roomId).update(mapOf(
                    "members"       to newMembers,
                    "memberAliases" to aliases,
                    "updatedAt"     to System.currentTimeMillis()
                )).await()
                currentRoomId = roomId
                startRoomListener(roomId, amHost = doc.getString("hostUid") == myUid)
                startChatListener(roomId)
                onJoined()
            } catch (e: Exception) {
                _roomState.value = MusicRoomUiState.Error("Could not join room")
            }
        }
    }

    // ── Play / Pause (host only, syncs to Firestore) ─────────────────────────
    fun togglePlayPause(context: Context) {
        val state = _roomState.value as? MusicRoomUiState.Active ?: return
        if (!state.amHost) return
        val room = state.room
        val nowPlaying = !room.isPlaying
        viewModelScope.launch {
            runCatching {
                db.collection("musicRooms").document(room.roomId).update(mapOf(
                    "isPlaying"  to nowPlaying,
                    "positionMs" to (mediaPlayer?.currentPosition?.toLong() ?: 0L),
                    "updatedAt"  to System.currentTimeMillis(),
                    "status"     to if (nowPlaying) "playing" else "waiting"
                )).await()
            }
        }
        if (nowPlaying) applyPlayback(context, room, room.positionMs, play = true)
        else mediaPlayer?.pause()
    }

    // ── Next Track (host only) ────────────────────────────────────────────────
    fun nextTrack(context: Context) {
        val state = _roomState.value as? MusicRoomUiState.Active ?: return
        if (!state.amHost) return
        val room = state.room
        viewModelScope.launch {
            var nextIndex  = room.trackIndex + 1
            var order      = room.shuffleOrder
            var cycles     = room.cyclesDone
            // If we exhausted the cycle, build a new shuffle
            if (nextIndex >= order.size) {
                order  = MUSIC_TRACKS.indices.toMutableList().also { it.shuffle() }
                nextIndex = 0
                cycles++
            }
            runCatching {
                db.collection("musicRooms").document(room.roomId).update(mapOf(
                    "trackIndex"   to nextIndex,
                    "shuffleOrder" to order,
                    "cyclesDone"   to cycles,
                    "isPlaying"    to true,
                    "positionMs"   to 0L,
                    "updatedAt"    to System.currentTimeMillis()
                )).await()
            }
        }
    }

    // ── Apply playback on this device ─────────────────────────────────────────
    private var loadedResId: Int = 0   // track which file is currently loaded

    fun applyPlayback(context: Context, room: MusicRoom, seekMs: Long, play: Boolean) {
        val track = room.currentTrack ?: return
        val resId = track.resId(context)
        if (resId == 0) {
            android.util.Log.w("MusicRoomVM", "Missing raw resource: ${track.rawName}")
            return
        }

        // Reload player only when track actually changed
        if (resId != loadedResId) {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                isLooping = false
                setOnCompletionListener { onTrackComplete(context) }
            }
            loadedResId = resId
        }

        mediaPlayer?.let { mp ->
            // Seek if guest is far off (>3s) from host position
            if (seekMs > 0 && Math.abs(mp.currentPosition - seekMs) > 3000) {
                mp.seekTo(seekMs.toInt())
            }
            if (play && !mp.isPlaying) { mp.start(); startPositionBroadcast(room.roomId) }
            else if (!play && mp.isPlaying) { mp.pause(); positionBroadcastJob?.cancel() }
        }
    }

    fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        loadedResId = 0
        positionBroadcastJob?.cancel()
    }

    private fun onTrackComplete(context: Context) {
        nextTrack(context)
    }

    // Broadcast position every 3s so guests stay in sync
    private fun startPositionBroadcast(roomId: String) {
        positionBroadcastJob?.cancel()
        positionBroadcastJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val pos = mediaPlayer?.currentPosition?.toLong() ?: continue
                runCatching {
                    db.collection("musicRooms").document(roomId).update(
                        "positionMs", pos, "updatedAt", System.currentTimeMillis()
                    )
                }
            }
        }
    }

    // ── Leave / End ───────────────────────────────────────────────────────────
    fun leaveRoom(context: Context) {
        val roomId = currentRoomId.ifBlank { return }
        viewModelScope.launch {
            runCatching {
                val state = _roomState.value as? MusicRoomUiState.Active
                if (state?.amHost == true) {
                    db.collection("musicRooms").document(roomId)
                        .update("status", "ended", "updatedAt", System.currentTimeMillis())
                } else {
                    val doc = db.collection("musicRooms").document(roomId).get().await()
                    val members = (doc.get("members") as? List<*>)
                        ?.map { it.toString() }?.filter { it != myUid } ?: emptyList()
                    val aliases = (doc.get("memberAliases") as? Map<*, *>)
                        ?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() }
                        ?.toMutableMap() ?: mutableMapOf()
                    aliases.remove(myUid)
                    db.collection("musicRooms").document(roomId).update(
                        "members", members, "memberAliases", aliases,
                        "updatedAt", System.currentTimeMillis()
                    )
                }
            }
        }
        releasePlayer()
        roomReg?.remove(); chatReg?.remove()
        currentRoomId = ""
        _roomState.value = MusicRoomUiState.Idle
        _messages.value  = emptyList()
    }

    // ── Chat ──────────────────────────────────────────────────────────────────
    fun sendMessage(text: String) {
        val roomId = currentRoomId.ifBlank { return }
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val ref = db.collection("musicRooms").document(roomId)
                    .collection("chat").document()
                ref.set(mapOf(
                    "id"     to ref.id,
                    "uid"    to myUid,
                    "alias"  to myAlias,
                    "text"   to text.trim(),
                    "sentAt" to System.currentTimeMillis()
                ))
            }
        }
    }

    private fun startChatListener(roomId: String) {
        chatReg?.remove()
        chatReg = db.collection("musicRooms").document(roomId).collection("chat")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(80)
            .addSnapshotListener { snap, _ ->
                _messages.value = snap?.documents?.mapNotNull { doc ->
                    try {
                        ChatMessage(
                            id     = doc.id,
                            uid    = doc.getString("uid")   ?: "",
                            alias  = doc.getString("alias") ?: "?",
                            text   = doc.getString("text")  ?: "",
                            sentAt = doc.getLong("sentAt")  ?: 0L
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
            }
    }

    // ── Room listener ─────────────────────────────────────────────────────────
    private fun startRoomListener(roomId: String, amHost: Boolean) {
        roomReg?.remove()
        roomReg = db.collection("musicRooms").document(roomId)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val room = parseRoom(snap) ?: return@addSnapshotListener
                if (room.status == "ended" && !amHost) {
                    // Clean up without needing context — just reset state
                    releasePlayer()
                    roomReg?.remove(); chatReg?.remove()
                    currentRoomId = ""
                    _roomState.value = MusicRoomUiState.Idle
                    _messages.value  = emptyList()
                    return@addSnapshotListener
                }
                _roomState.value = MusicRoomUiState.Active(room, amHost)
            }
    }

    // ── FCM Broadcast (writes a Firestore doc → Cloud Function picks it up) ──
    private fun broadcastInvite(roomId: String, hostAlias: String) {
        viewModelScope.launch {
            runCatching {
                db.collection("musicRoomInvites").document(roomId).set(mapOf(
                    "roomId"    to roomId,
                    "hostAlias" to hostAlias,
                    "sentAt"    to System.currentTimeMillis(),
                    "type"      to "music_room_invite"
                ))
            }
        }
    }

    // ── Parse Firestore doc → MusicRoom ───────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    private fun parseRoom(doc: DocumentSnapshot): MusicRoom? = try {
        MusicRoom(
            roomId        = doc.getString("roomId")       ?: doc.id,
            hostUid       = doc.getString("hostUid")      ?: "",
            hostAlias     = doc.getString("hostAlias")    ?: "",
            members       = (doc.get("members") as? List<*>)?.map { it.toString() } ?: emptyList(),
            memberAliases = (doc.get("memberAliases") as? Map<String, Any>)
                ?.mapValues { it.value.toString() } ?: emptyMap(),
            maxMembers    = (doc.getLong("maxMembers") ?: 5L).toInt(),
            isPrivate     = doc.getBoolean("isPrivate")   ?: false,
            trackIndex    = (doc.getLong("trackIndex")    ?: 0L).toInt(),
            shuffleOrder  = (doc.get("shuffleOrder") as? List<*>)?.map { (it as Long).toInt() } ?: emptyList(),
            cyclesDone    = (doc.getLong("cyclesDone")    ?: 0L).toInt(),
            isPlaying     = doc.getBoolean("isPlaying")   ?: false,
            positionMs    = doc.getLong("positionMs")     ?: 0L,
            updatedAt     = doc.getLong("updatedAt")      ?: 0L,
            status        = doc.getString("status")       ?: "waiting",
            createdAt     = doc.getLong("createdAt")      ?: 0L
        )
    } catch (e: Exception) { Log.w("MusicRoomVM", "parseRoom: ${e.message}"); null }

    override fun onCleared() {
        super.onCleared()
        lobbyReg?.remove(); roomReg?.remove(); chatReg?.remove()
        mediaPlayer?.release()
        positionBroadcastJob?.cancel()
    }
}
