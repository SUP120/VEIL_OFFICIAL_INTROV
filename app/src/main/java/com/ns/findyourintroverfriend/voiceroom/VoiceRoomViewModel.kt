package com.ns.findyourintroverfriend.voiceroom

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val TAG = "WhisperVM"

// ── Lobby-level connection state ──────────────────────────────────────────────
sealed class VoiceConnectState {
    object Idle                          : VoiceConnectState()
    data class Working(val msg: String)  : VoiceConnectState()
    data class Ready(val roomId: String) : VoiceConnectState()
    data class Failed(val message: String): VoiceConnectState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  VoiceRoomViewModel — Whisper edition
//
//  Flow:
//    Lobby  → createAndJoin / joinExistingRoom / requestToJoin
//           → Firestore write → Ready(roomId) → navigate
//    Active → LaunchedEffect(roomId) → vm.connectToLiveKit(roomId, ctx)
//           → CONNECTING → ACTIVE (or ERROR)
// ─────────────────────────────────────────────────────────────────────────────

class VoiceRoomViewModel : ViewModel() {

    private val repo = VoiceRoomRepository()
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ── Public state streams ──────────────────────────────────────────────────

    private val _lobby        = MutableStateFlow(VoiceRoomLobbyUiState())
    val lobbyState: StateFlow<VoiceRoomLobbyUiState> = _lobby.asStateFlow()

    private val _active       = MutableStateFlow(VoiceRoomActiveUiState())
    val activeState: StateFlow<VoiceRoomActiveUiState> = _active.asStateFlow()

    private val _connectState = MutableStateFlow<VoiceConnectState>(VoiceConnectState.Idle)
    val connectState: StateFlow<VoiceConnectState> = _connectState.asStateFlow()

    // ── Invite sheet state ────────────────────────────────────────────────────

    /** All connections (swipe + feed) for the invite sheet */
    private val _inviteConnections = MutableStateFlow<List<VoiceRoomRepository.InvitableUser>>(emptyList())
    val inviteConnections: StateFlow<List<VoiceRoomRepository.InvitableUser>> = _inviteConnections.asStateFlow()

    private val _inviteSheetOpen  = MutableStateFlow(false)
    val inviteSheetOpen: StateFlow<Boolean> = _inviteSheetOpen.asStateFlow()

    private val _inviteLoading    = MutableStateFlow(false)
    val inviteLoading: StateFlow<Boolean> = _inviteLoading.asStateFlow()

    private val _inviteSentSnack  = MutableStateFlow(false)
    val inviteSentSnack: StateFlow<Boolean> = _inviteSentSnack.asStateFlow()

    private val _broadcastUsed    = MutableStateFlow(false)
    val broadcastUsed: StateFlow<Boolean> = _broadcastUsed.asStateFlow()

    // ── Internal LiveKit state ─────────────────────────────────────────────────
    private var lkRoom        : Room?  = null
    private var timerJob      : Job?   = null
    private var speakerJob    : Job?   = null
    private var roomObserverJob: Job?  = null
    private var currentRoomId : String = ""
    private var isMuted       : Boolean = false

    init { observeLobby() }

    fun resetConnectState() { _connectState.value = VoiceConnectState.Idle }

    // ── Lobby: real-time room list ────────────────────────────────────────────

    private fun observeLobby() {
        viewModelScope.launch {
            repo.liveRoomsFlow()
                .catch { e -> _lobby.value = _lobby.value.copy(error = e.message) }
                .collect { rooms ->
                    _lobby.value = VoiceRoomLobbyUiState(rooms = rooms, isLoading = false)
                }
        }
    }

    // ── Lobby actions ─────────────────────────────────────────────────────────

    fun createAndJoin(
        topic          : String,
        description    : String,
        language       : String,
        tags           : List<String>,
        maxParticipants: Int,
        isLocked       : Boolean,
    ) {
        viewModelScope.launch {
            _connectState.value = VoiceConnectState.Working("Creating room…")
            try {
                val roomId = repo.createRoom(topic, description, language, tags, maxParticipants, isLocked)
                _connectState.value = VoiceConnectState.Ready(roomId)
            } catch (e: Exception) {
                Log.e(TAG, "createAndJoin failed", e)
                _connectState.value = VoiceConnectState.Failed(e.message ?: "Failed to create room")
            }
        }
    }

    fun joinExistingRoom(roomId: String) {
        viewModelScope.launch {
            _connectState.value = VoiceConnectState.Working("Joining room…")
            try {
                repo.joinRoomFirestore(roomId)
                _connectState.value = VoiceConnectState.Ready(roomId)
            } catch (e: Exception) {
                Log.e(TAG, "joinExistingRoom failed", e)
                _connectState.value = VoiceConnectState.Failed(e.message ?: "Failed to join room")
            }
        }
    }

    /** For locked rooms — sends request to host, then moves to PENDING_APPROVAL screen */
    fun requestToJoin(roomId: String) {
        viewModelScope.launch {
            _connectState.value = VoiceConnectState.Working("Sending request…")
            try {
                repo.requestToJoin(roomId)
                // Navigate to active screen in PENDING_APPROVAL phase
                _active.value = VoiceRoomActiveUiState(phase = RoomPhase.PENDING_APPROVAL)
                currentRoomId = roomId
                _connectState.value = VoiceConnectState.Ready(roomId)
                // Start watching for approval (room join request cleared + uid added to participants)
                watchForApproval(roomId)
            } catch (e: Exception) {
                Log.e(TAG, "requestToJoin failed", e)
                _connectState.value = VoiceConnectState.Failed(e.message ?: "Request failed")
            }
        }
    }

    /** Watch for host approval of locked room */
    private fun watchForApproval(roomId: String) {
        val myUid = auth.currentUser?.uid ?: return
        roomObserverJob?.cancel()
        roomObserverJob = viewModelScope.launch {
            repo.roomFlow(roomId)
                .catch { Log.e(TAG, "approval watch error", it) }
                .collect { room ->
                    if (room == null) return@collect
                    when {
                        myUid in room.kickedUids -> {
                            // Host rejected — signal rejection
                            _active.value = _active.value.copy(phase = RoomPhase.REJECTED)
                        }
                        myUid in room.participantUids && _active.value.phase == RoomPhase.PENDING_APPROVAL -> {
                            // Host approved! Now connect to LiveKit
                            roomObserverJob?.cancel()
                            // LiveKit will be connected by the active screen's LaunchedEffect
                            // We just update the phase to CONNECTING to trigger it
                            _active.value = _active.value.copy(phase = RoomPhase.CONNECTING)
                        }
                    }
                }
        }
    }

    // ── Host join-request controls ────────────────────────────────────────────

    fun approveJoinRequest(roomId: String, requesterUid: String) {
        viewModelScope.launch {
            runCatching { repo.approveJoinRequest(roomId, requesterUid) }
                .onFailure { Log.e(TAG, "approveJoinRequest failed", it) }
        }
    }

    fun rejectJoinRequest(roomId: String, requesterUid: String) {
        viewModelScope.launch {
            runCatching { repo.rejectJoinRequest(roomId, requesterUid) }
                .onFailure { Log.e(TAG, "rejectJoinRequest failed", it) }
        }
    }

    // ── Raise Hand / Voice Queue ──────────────────────────────────────────────

    fun raiseHand(roomId: String) {
        viewModelScope.launch {
            runCatching { repo.raiseHand(roomId) }
                .onSuccess { _active.value = _active.value.copy(hasRaisedHand = true) }
                .onFailure { Log.e(TAG, "raiseHand failed", it) }
        }
    }

    fun lowerHand(roomId: String) {
        viewModelScope.launch {
            runCatching { repo.lowerHand(roomId) }
                .onSuccess { _active.value = _active.value.copy(hasRaisedHand = false) }
                .onFailure { Log.e(TAG, "lowerHand failed", it) }
        }
    }

    fun promoteToSpeaker(roomId: String, targetUid: String) {
        viewModelScope.launch {
            runCatching { repo.promoteToSpeaker(roomId, targetUid) }
                .onFailure { Log.e(TAG, "promoteToSpeaker failed", it) }
        }
    }

    fun demoteSpeaker(roomId: String, targetUid: String) {
        viewModelScope.launch {
            runCatching { repo.demoteSpeaker(roomId, targetUid) }
                .onFailure { Log.e(TAG, "demoteSpeaker failed", it) }
        }
    }

    // ── Host moderation ───────────────────────────────────────────────────────

    fun muteParticipant(roomId: String, targetUid: String) {
        viewModelScope.launch {
            runCatching { repo.muteParticipant(roomId, targetUid) }
                .onFailure { Log.e(TAG, "muteParticipant failed", it) }
        }
    }

    fun unmuteParticipant(roomId: String, targetUid: String) {
        viewModelScope.launch {
            runCatching { repo.unmuteParticipant(roomId, targetUid) }
                .onFailure { Log.e(TAG, "unmuteParticipant failed", it) }
        }
    }

    fun kickParticipant(roomId: String, targetUid: String) {
        viewModelScope.launch {
            runCatching { repo.kickParticipant(roomId, targetUid) }
                .onFailure { Log.e(TAG, "kickParticipant failed", it) }
        }
    }

    fun setRoomLocked(roomId: String, locked: Boolean) {
        viewModelScope.launch {
            runCatching { repo.setRoomLocked(roomId, locked) }
                .onFailure { Log.e(TAG, "setRoomLocked failed", it) }
        }
    }

    // ── Cookie extension ──────────────────────────────────────────────────────

    fun extendRoom(roomId: String) {
        val hostUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _active.value = _active.value.copy(isExtending = true)
            val success = repo.extendRoom(roomId, hostUid)
            _active.value = _active.value.copy(
                isExtending    = false,
                showExtendDialog = false,
            )
            if (!success) {
                _active.value = _active.value.copy(error = "Not enough cookies to extend (need $WHISPER_EXTENSION_COOKIES 🍪)")
            }
        }
    }

    fun showExtendDialog() {
        _active.value = _active.value.copy(showExtendDialog = true)
    }

    fun dismissExtendDialog() {
        _active.value = _active.value.copy(showExtendDialog = false)
    }

    // ── Active screen: connect to LiveKit ─────────────────────────────────────

    fun connectToLiveKit(roomId: String, appContext: Context) {
        viewModelScope.launch {
            _active.value = _active.value.copy(phase = RoomPhase.CONNECTING)
            currentRoomId = roomId
            isMuted = false

            try {
                Log.d(TAG, "[1/4] Fetching token for room=$roomId")
                val (token, livekitUrl) = repo.fetchLiveKitToken(roomId)
                Log.d(TAG, "[1/4] Token OK len=${token.length}")

                if (token.count { it == '.' } != 2) {
                    error("Invalid JWT from token server. Check Cloudflare Worker variables.")
                }

                Log.d(TAG, "[2/4] Creating LiveKit Room")
                val room = LiveKit.create(
                    appContext = appContext.applicationContext,
                    options    = RoomOptions(adaptiveStream = true, dynacast = true),
                )
                lkRoom = room

                Log.d(TAG, "[3/4] Connecting to $livekitUrl…")
                withTimeout(30_000L) { room.connect(url = livekitUrl, token = token) }
                Log.d(TAG, "[3/4] Connected ✓")

                Log.d(TAG, "[4/4] Enabling mic…")
                try {
                    withTimeout(10_000L) { room.localParticipant.setMicrophoneEnabled(true) }
                    isMuted = false
                    Log.d(TAG, "[4/4] Mic enabled ✓")
                } catch (micErr: Exception) {
                    Log.w(TAG, "[4/4] Mic enable failed (non-fatal): ${micErr.message}")
                    isMuted = true
                }

                // Start room observer (Firestore) + LiveKit events + timer
                observeRoom(roomId)
                loadCookieBalance()

                // Wait briefly for room doc to arrive
                var waited = 0
                while (_active.value.room == null && waited < 3000) {
                    delay(100); waited += 100
                }

                observeLiveKitEvents(room)
                startTimer()

                _active.value = _active.value.copy(phase = RoomPhase.ACTIVE, isMuted = isMuted)
                Log.d(TAG, "Room ACTIVE ✓  isMuted=$isMuted")

            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "connectToLiveKit TIMED OUT", e)
                _active.value = _active.value.copy(
                    phase = RoomPhase.ERROR,
                    error = "Connection timed out after 30 s.\n• Check internet connection\n• Verify Cloudflare Worker config",
                )
                cleanupLiveKit()

            } catch (e: Exception) {
                Log.e(TAG, "connectToLiveKit FAILED: ${e.message}", e)
                _active.value = _active.value.copy(
                    phase = RoomPhase.ERROR,
                    error = e.message ?: "Connection failed",
                )
                cleanupLiveKit()
            }
        }
    }

    // ── Room Firestore observer ───────────────────────────────────────────────

    private fun observeRoom(roomId: String) {
        val myUid = auth.currentUser?.uid ?: return
        roomObserverJob?.cancel()
        roomObserverJob = viewModelScope.launch {
            repo.roomFlow(roomId)
                .catch { Log.e(TAG, "roomFlow error", it) }
                .collect { room ->
                    if (room == null) return@collect

                    // Fetch participant names whenever the list changes
                    if (room.participantUids.isNotEmpty()) {
                        val (aliases, emojis) = repo.fetchParticipantInfo(room.participantUids)
                        _active.value = _active.value.copy(
                            participantAliases = aliases,
                            participantEmojis  = emojis,
                        )
                    }

                    // Detect host-mute signal for self
                    val hostMutedMe = myUid in room.mutedByHost
                    if (hostMutedMe && !isMuted) {
                        forceMute()
                    }

                    // Detect kick signal for self
                    if (myUid in room.kickedUids) {
                        Log.d(TAG, "I was kicked from room")
                        cleanupLiveKit()
                        _active.value = _active.value.copy(
                            room  = room,
                            phase = RoomPhase.LEFT,
                            error = "You were removed from the room."
                        )
                        return@collect
                    }

                    // Room closed by host
                    if (!room.isLive) {
                        _active.value = _active.value.copy(room = room, phase = RoomPhase.EXPIRED)
                        cleanupLiveKit()
                        return@collect
                    }

                    // Update raised hand state
                    val myHandRaised = room.raisedHands.any { it.uid == myUid }

                    _active.value = _active.value.copy(
                        room         = room,
                        hasRaisedHand = myHandRaised,
                    )
                }
        }
    }

    // ── Force-mute (host signal) ──────────────────────────────────────────────

    private fun forceMute() {
        viewModelScope.launch {
            val room = lkRoom ?: return@launch
            try {
                room.localParticipant.setMicrophoneEnabled(false)
                isMuted = true
                _active.value = _active.value.copy(isMuted = true)
                Log.d(TAG, "Force-muted by host")
            } catch (e: Exception) {
                Log.e(TAG, "forceMute failed: ${e.message}", e)
            }
        }
    }

    // ── LiveKit events ────────────────────────────────────────────────────────

    private fun observeLiveKitEvents(room: Room) {
        speakerJob?.cancel()
        speakerJob = viewModelScope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ActiveSpeakersChanged -> {
                        val ids = event.speakers
                            .mapNotNull { it.identity?.value }
                            .toSet()
                        _active.value = _active.value.copy(activeSpeakers = ids)
                    }
                    is RoomEvent.TrackPublished   -> Log.d(TAG, "Track published by ${event.participant.identity?.value}")
                    is RoomEvent.TrackUnpublished -> Log.d(TAG, "Track unpublished by ${event.participant.identity?.value}")
                    else -> Unit
                }
            }
        }
    }

    // ── Countdown timer ───────────────────────────────────────────────────────

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val room  = _active.value.room
                val secs  = room?.remainingSecs() ?: (WHISPER_FREE_MINS * 60L)
                val clamped = secs.coerceAtLeast(0L)

                // Show extend dialog at 2-minute warning (if host and can extend)
                val isHost    = room?.hostUid == auth.currentUser?.uid
                val canExtend = room?.canExtend ?: false
                val phase     = _active.value.phase
                if (clamped in 1L..120L && isHost && canExtend && phase == RoomPhase.ACTIVE) {
                    _active.value = _active.value.copy(
                        remainingSecs    = clamped,
                        showExtendDialog = true,
                        phase            = RoomPhase.EXPIRING,
                    )
                } else {
                    _active.value = _active.value.copy(remainingSecs = clamped)
                }

                if (clamped <= 0L) {
                    val newPhase = if (isHost && canExtend) RoomPhase.EXPIRING else RoomPhase.EXPIRED
                    _active.value = _active.value.copy(
                        phase            = newPhase,
                        showExtendDialog = isHost && canExtend,
                    )
                    if (!canExtend) {
                        cleanupLiveKit()
                        break
                    }
                }
                delay(1_000L)
            }
        }
    }

    // ── Cookie balance ────────────────────────────────────────────────────────

    private fun loadCookieBalance() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val snap = db.collection("users").document(uid).get().await()
                _active.value = _active.value.copy(
                    cookieBalance = (snap.getLong("cookieBalance") ?: 0L).toInt()
                )
            } catch (_: Exception) {}
        }
    }

    // ── Mic toggle ────────────────────────────────────────────────────────────

    fun toggleMic() {
        viewModelScope.launch {
            val room = lkRoom ?: run { Log.w(TAG, "toggleMic: room null"); return@launch }
            val newMuted = !isMuted
            try {
                room.localParticipant.setMicrophoneEnabled(!newMuted)
                isMuted = newMuted
                _active.value = _active.value.copy(isMuted = isMuted)
                Log.d(TAG, "Mic toggled → muted=$isMuted")
            } catch (e: Exception) {
                Log.e(TAG, "toggleMic failed: ${e.message}", e)
                _active.value = _active.value.copy(isMuted = isMuted)
            }
        }
    }

    // ── Invite actions ────────────────────────────────────────────────────────

    fun openInviteSheet() {
        _inviteSheetOpen.value = true
        loadConnections()
    }

    fun closeInviteSheet() { _inviteSheetOpen.value = false }

    private fun loadConnections() {
        if (_inviteLoading.value) return
        viewModelScope.launch {
            _inviteLoading.value = true
            try {
                _inviteConnections.value = repo.getMyConnections()
            } catch (e: Exception) {
                Log.e(TAG, "loadConnections: ${e.message}")
            } finally {
                _inviteLoading.value = false
            }
        }
    }

    /**
     * Send invites to [targetUids] (non-empty = personal invite, empty = broadcast).
     * Fetches host alias from active room state.
     */
    fun sendInvites(roomId: String, targetUids: List<String>) {
        if (_inviteLoading.value) return
        viewModelScope.launch {
            _inviteLoading.value = true
            try {
                val room        = _active.value.room
                val hostAlias   = room?.hostAlias ?: auth.currentUser?.displayName ?: "Someone"
                val roomTitle   = room?.topic?.ifBlank { "Whisper Room" } ?: "Whisper Room"
                // Pass the full connections list so inviteToRoom() can find matchIds for chat messages
                val connections = _inviteConnections.value
                repo.inviteToRoom(
                    roomId      = roomId,
                    roomTitle   = roomTitle,
                    hostAlias   = hostAlias,
                    targetUids  = targetUids,
                    connections = connections
                )
                if (targetUids.isEmpty()) _broadcastUsed.value = true
                _inviteSentSnack.value = true
                _inviteSheetOpen.value = false
            } catch (e: Exception) {
                Log.e(TAG, "sendInvites: ${e.message}")
            } finally {
                _inviteLoading.value = false
            }
        }
    }

    fun clearInviteSentSnack() { _inviteSentSnack.value = false }

    fun checkBroadcastUsed(roomId: String) {
        viewModelScope.launch {
            try {
                val snap = db.collection("voiceRooms").document(roomId).get().await()
                _broadcastUsed.value = snap.getBoolean("broadcastSent") == true
            } catch (_: Exception) {}
        }
    }

    // ── Leave / End room ──────────────────────────────────────────────────────

    fun leaveRoom(isHost: Boolean) {
        viewModelScope.launch {
            try {
                if (isHost) repo.closeRoom(currentRoomId)
                else        repo.leaveRoomFirestore(currentRoomId)
            } catch (e: Exception) { Log.e(TAG, "leaveRoom error", e) }
            cleanupLiveKit()
            _active.value = _active.value.copy(phase = RoomPhase.LEFT)
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun cleanupLiveKit() {
        timerJob?.cancel(); timerJob = null
        speakerJob?.cancel(); speakerJob = null
        roomObserverJob?.cancel(); roomObserverJob = null
        try { lkRoom?.disconnect() } catch (_: Exception) {}
        try { lkRoom?.release()    } catch (_: Exception) {}
        lkRoom = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupLiveKit()
    }
}
