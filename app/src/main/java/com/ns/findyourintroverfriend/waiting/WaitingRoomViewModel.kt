package com.ns.findyourintroverfriend.waiting

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ns.findyourintroverfriend.data.GroupFields
import com.ns.findyourintroverfriend.data.UserFields
import com.ns.findyourintroverfriend.notifications.VeilNotifier
import com.onesignal.OneSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG              = "VeilWaiting"
private const val POLL_INTERVAL    = 6_000L
private const val MAX_WAIT_MS      = 600_000L
private const val HEARTBEAT_MS     = 45_000L     // C1: update queue presence every 45s
private const val STALE_QUEUE_MS   = 300_000L    // C1: 5 min without heartbeat = ghost user

// ─────────────────────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────────────────────

data class MemberPreview(val uid: String, val alias: String, val gender: String)
data class PendingRoom(val roomNumber: Int, val males: List<MemberPreview>, val females: List<MemberPreview>) {
    val total  get() = males.size + females.size
    val isFull get() = males.size >= 2 && females.size >= 2
}
data class CompletedRoom(val roomNumber: Int, val groupId: String, val members: List<String>)

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────
sealed class WaitingUiState {
    object Joining : WaitingUiState()

    data class InQueue(
        val malesReady     : Int,
        val femalesReady   : Int,
        val totalInQueue   : Int,          // FIX #4: live count for UI
        val myBatchNumber  : Int,
        val members        : List<MemberPreview>,
        val pendingRooms   : List<PendingRoom>,
        val completedRooms : List<CompletedRoom>,
        val userGender     : String
    ) : WaitingUiState()

    /** All 4 members detected in the QUEUE ITSELF — show Begin button immediately.
     *  No Firestore trigger needed. User taps Begin → group is formed → GroupFound. */
    data class CircleFull(
        val members   : List<MemberPreview>,   // 4 names to display
        val batchUids : List<String>           // 4 UIDs to pass to beginJourney
    ) : WaitingUiState()

    /** Group document created — auto-navigate now */
    data class GroupFound(val groupId: String) : WaitingUiState()

    data class NoMatchYet(val waitedMs: Long) : WaitingUiState()
    data class Error(val msg: String)         : WaitingUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class WaitingRoomViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<WaitingUiState>(WaitingUiState.Joining)
    val state: StateFlow<WaitingUiState> = _state.asStateFlow()

    // FIX #1: set to true when user taps "Notify me — close app"
    private val _isBackgroundMode = MutableStateFlow(false)
    val isBackgroundMode: StateFlow<Boolean> = _isBackgroundMode.asStateFlow()

    private var queueListener : ListenerRegistration? = null
    private var userListener  : ListenerRegistration? = null
    private var pollJob       : Job? = null
    private var heartbeatJob  : Job? = null   // C1: keeps queue entry alive
    private var myGender      = ""
    private var myUidCache    = ""            // C1: cached for cleanup

    init { enterQueue() }

    // ── 1. Enter queue ────────────────────────────────────────────────────────
    private fun enterQueue() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = WaitingUiState.Error("Not signed in."); return
        }
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()

                // Guard: already has a group → navigate directly
                val existingGroupId = userDoc.getString(UserFields.GROUP_ID)
                if (!existingGroupId.isNullOrBlank()) {
                    Log.d(TAG, "Already in group $existingGroupId")
                    _state.value = WaitingUiState.GroupFound(existingGroupId)
                    return@launch
                }

                myGender   = userDoc.getString(UserFields.GENDER) ?: "Unknown"
                myUidCache = uid

                // Register this device with OneSignal — persists subId to Firestore
                // This ensures targeted notifications work even before ExternalID syncs
                VeilNotifier.registerAndPersistUser(uid, db)

                db.collection("matchmaking_queue").document(uid).set(
                    mapOf(
                        "uid"             to uid,
                        UserFields.GENDER to myGender,
                        "status"          to "waiting",
                        "joinedAt"        to Timestamp.now(),
                        "heartbeatAt"     to Timestamp.now()   // C1: initial heartbeat
                    )
                ).await()

                Log.d(TAG, "Entered queue uid=$uid gender=$myGender")
                startHeartbeat(uid)   // C1: keep queue entry alive
                listenToQueue()

            } catch (e: Exception) {
                Log.e(TAG, "enterQueue failed", e)
                _state.value = WaitingUiState.Error(
                    e.localizedMessage ?: "Failed to join queue. Please try again."
                )
            }
        }
    }

    // ── 2. Single listener — handles BOTH stats AND circle-full detection ──────
    private fun listenToQueue() {
        queueListener = db.collection("matchmaking_queue")
            .whereEqualTo("status", "waiting")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                if (_state.value is WaitingUiState.GroupFound) return@addSnapshotListener

                val myUid = auth.currentUser?.uid ?: return@addSnapshotListener
                val nowMs  = System.currentTimeMillis()

                // C1: Remove stale ghost entries (>5 min without heartbeat) ──────
                viewModelScope.launch {
                    snap.documents.forEach { doc ->
                        val hbAt = doc.getTimestamp("heartbeatAt")?.toDate()?.time ?: 0L
                        val ageMs = nowMs - hbAt
                        if (ageMs > STALE_QUEUE_MS && doc.id != myUid) {
                            try {
                                db.collection("matchmaking_queue").document(doc.id).delete().await()
                                Log.d(TAG, "C1: Removed ghost queue entry for ${doc.id} (${ageMs/1000}s stale)")
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Filter out stale entries BEFORE batch calculation
                // FIX #1: background-mode entries are allowed to be stale for 4 hours
                val BACKGROUND_STALE_MS = 4 * 60 * 60 * 1000L
                val live = snap.documents.filter { doc ->
                    val hbAt       = doc.getTimestamp("heartbeatAt")?.toDate()?.time ?: 0L
                    val isBg       = doc.getBoolean("backgroundMode") ?: false
                    val staleLimit = if (isBg) BACKGROUND_STALE_MS else STALE_QUEUE_MS
                    (nowMs - hbAt) <= staleLimit || doc.id == myUid
                }

                val all     = live.sortedBy { it.getTimestamp("joinedAt")?.seconds ?: 0L }
                val males   = all.filter { it.getString(UserFields.GENDER) == "Male" }
                val females = all.filter { it.getString(UserFields.GENDER) == "Female" }

                // H6: Non-binary / Other users form their own groups of 4
                val others  = all.filter { doc ->
                    val g = doc.getString(UserFields.GENDER) ?: "Unknown"
                    g != "Male" && g != "Female"
                }

                val myGenderVal = all.find { it.id == myUid }?.getString(UserFields.GENDER) ?: myGender

                // Determine which lane this user is in
                val isOther = myGenderVal != "Male" && myGenderVal != "Female"

                if (isOther) {
                    // H6: Non-binary lane — groups of any 4 non-binary/other users
                    val myOtherIdx = others.indexOfFirst { it.id == myUid }
                    val myBatch    = if (myOtherIdx >= 0) myOtherIdx / 4 else 0
                    val batchOthers = others.drop(myBatch * 4).take(4)

                    viewModelScope.launch {
                        if (_state.value is WaitingUiState.GroupFound || _state.value is WaitingUiState.CircleFull) return@launch
                        val memberPreviews = batchOthers.mapNotNull { doc ->
                            fetchPreview(doc.id, doc.getString(UserFields.GENDER) ?: "Other")
                        }
                        if (batchOthers.size >= 4) {
                            Log.d(TAG, "🎉 H6: Non-binary circle full!")
                            _state.value = WaitingUiState.CircleFull(
                                members   = memberPreviews,
                                batchUids = batchOthers.map { it.id }
                            )
                        } else {
                            // Show waiting state with count
                            val waitedMs = nowMs - (all.find { it.id == myUid }?.getTimestamp("joinedAt")?.toDate()?.time ?: nowMs)
                            _state.value = WaitingUiState.InQueue(
                                malesReady     = 0,
                                femalesReady   = 0,
                                totalInQueue   = live.size,
                                myBatchNumber  = myBatch + 1,
                                members        = memberPreviews,
                                pendingRooms   = emptyList(),
                                completedRooms = fetchCompletedRooms(),
                                userGender     = myGenderVal
                            )
                        }
                    }
                    return@addSnapshotListener
                }

                // Classic Male / Female matchmaking (unchanged)
                val maleIdx   = males.indexOfFirst   { it.id == myUid }
                val femaleIdx = females.indexOfFirst { it.id == myUid }
                val myBatch   = when {
                    maleIdx   >= 0 -> maleIdx   / 2
                    femaleIdx >= 0 -> femaleIdx / 2
                    else           -> 0
                }

                val batchMales   = males.drop(myBatch * 2).take(2)
                val batchFemales = females.drop(myBatch * 2).take(2)
                val malesInBatch = batchMales.size
                val femalesInBatch = batchFemales.size

                viewModelScope.launch {
                    if (_state.value is WaitingUiState.GroupFound || _state.value is WaitingUiState.CircleFull) return@launch

                    val memberPreviews = (batchMales + batchFemales).mapNotNull { doc ->
                        fetchPreview(doc.id, doc.getString(UserFields.GENDER) ?: "?")
                    }

                    // ── CIRCLE FULL: 2M + 2F in my batch → show Begin button ───
                    if (malesInBatch >= 2 && femalesInBatch >= 2) {
                        Log.d(TAG, "🎉 Circle full! Showing Begin button immediately.")
                        _state.value = WaitingUiState.CircleFull(
                            members   = memberPreviews,
                            batchUids = (batchMales + batchFemales).map { it.id }
                        )
                        return@launch
                    }

                    // ── NORMAL: still waiting ─────────────────────────────────
                    val totalBatches = maxOf(
                        if (males.isEmpty())   0 else (males.size   - 1) / 2 + 1,
                        if (females.isEmpty()) 0 else (females.size - 1) / 2 + 1
                    )
                    val pendingRooms = buildPendingRooms(males, females, totalBatches)
                    val completedRooms = fetchCompletedRooms()

                    _state.value = WaitingUiState.InQueue(
                        malesReady     = malesInBatch,
                        femalesReady   = femalesInBatch,
                        totalInQueue   = live.size,
                        myBatchNumber  = myBatch + 1,
                        members        = memberPreviews,
                        pendingRooms   = pendingRooms,
                        completedRooms = completedRooms,
                        userGender     = myGender
                    )
                }
            }
    }

    // ── 3. User taps "Begin the Journey" ──────────────────────────────────────
    //
    //  Called from the CircleFull overlay.
    //  1. Check if groupId is already set (background orchestrator may have run)
    //  2. If not, run the transaction ourselves
    //  3. Set GroupFound → screen auto-navigates
    //
    fun beginJourney(batchUids: List<String>) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            if (_state.value is WaitingUiState.GroupFound) return@launch

            try {
                // Fast path: orchestrator already formed the group
                val myDoc = db.collection("users").document(uid).get().await()
                val existingGId = myDoc.getString(UserFields.GROUP_ID)
                if (!existingGId.isNullOrBlank()) {
                    Log.d(TAG, "Group already formed by orchestrator: $existingGId")
                    _state.value = WaitingUiState.GroupFound(existingGId)
                    return@launch
                }

                // Slow path: form the group ourselves
                Log.d(TAG, "Forming group on Begin tap: $batchUids")
                val groupRef = db.collection("groups").document()
                val now = Timestamp.now()

                db.runTransaction { tx ->
                    // Deduplication: verify nobody has a groupId yet
                    for (memberUid in batchUids) {
                        val doc = tx.get(db.collection("users").document(memberUid))
                        if (!doc.getString(UserFields.GROUP_ID).isNullOrBlank()) {
                            throw Exception("group_already_formed")
                        }
                    }
                    tx.set(groupRef, mapOf(
                        GroupFields.MEMBERS        to batchUids,
                        GroupFields.DAY            to 1,
                        GroupFields.DAY_STARTED_AT to now,
                        GroupFields.CREATED_AT     to now,
                        GroupFields.CEREMONY_DONE  to false
                    ))
                    for (memberUid in batchUids) {
                        tx.update(db.collection("users").document(memberUid), UserFields.GROUP_ID, groupRef.id)
                        tx.delete(db.collection("matchmaking_queue").document(memberUid))
                    }
                }.await()

                Log.d(TAG, "✅ Group formed on Begin: ${groupRef.id}")

                // ── Notify all 4 members the circle is ready ──────────────────
                try {
                    VeilNotifier.notifyGroupFormed(db, groupRef.id)
                } catch (_: Exception) {}  // non-fatal

                _state.value = WaitingUiState.GroupFound(groupRef.id)

            } catch (e: Exception) {
                if (e.message == "group_already_formed" || e.message?.contains("group_already_formed") == true) {
                    // Another device formed it — read our groupId
                    val gId = db.collection("users").document(uid).get().await()
                        .getString(UserFields.GROUP_ID)
                    if (!gId.isNullOrBlank()) {
                        Log.d(TAG, "Using group formed by another device: $gId")
                        _state.value = WaitingUiState.GroupFound(gId)
                    }
                } else {
                    Log.e(TAG, "beginJourney failed: ${e.message}")
                    // Check one more time if groupId was set
                    val gId = try {
                        db.collection("users").document(uid).get().await().getString(UserFields.GROUP_ID)
                    } catch (e2: Exception) { null }
                    if (!gId.isNullOrBlank()) {
                        _state.value = WaitingUiState.GroupFound(gId)
                    } else {
                        _state.value = WaitingUiState.Error("Couldn't start. Tap again.")
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun fetchPreview(uid: String, gender: String): MemberPreview? =
        try {
            val u = db.collection("users").document(uid).get().await()
            MemberPreview(uid, u.getString(UserFields.ALIAS) ?: "Anonymous", gender)
        } catch (e: Exception) { null }

    private suspend fun buildPendingRooms(
        males: List<com.google.firebase.firestore.DocumentSnapshot>,
        females: List<com.google.firebase.firestore.DocumentSnapshot>,
        totalBatches: Int
    ): List<PendingRoom> = (0 until totalBatches).map { batchIdx ->
        val bM = males.drop(batchIdx * 2).take(2)
        val bF = females.drop(batchIdx * 2).take(2)
        PendingRoom(
            batchIdx + 1,
            bM.mapNotNull { fetchPreview(it.id, "Male") },
            bF.mapNotNull { fetchPreview(it.id, "Female") }
        )
    }

    private suspend fun fetchCompletedRooms(): List<CompletedRoom> = try {
        db.collection("groups").get().await().documents.mapIndexed { idx, doc ->
            @Suppress("UNCHECKED_CAST")
            val uids = (doc.get(GroupFields.MEMBERS) as? List<String>) ?: emptyList()
            val aliases = uids.mapNotNull { uid ->
                try { db.collection("users").document(uid).get().await().getString(UserFields.ALIAS) } catch (e: Exception) { null }
            }
            CompletedRoom(idx + 1, doc.id, aliases)
        }
    } catch (e: Exception) { emptyList() }

    // C1: Heartbeat — keeps the user's queue entry alive every 45s ─────────────
    private fun startHeartbeat(uid: String) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                if (_state.value is WaitingUiState.GroupFound || _state.value is WaitingUiState.CircleFull) break
                try {
                    db.collection("matchmaking_queue").document(uid)
                        .update("heartbeatAt", Timestamp.now())
                        .await()
                } catch (_: Exception) {}
            }
        }
    }

    fun leaveQueue() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("matchmaking_queue").document(uid).delete().await()
                // Mark leftRoom so cold-start routes to VeilHomeScreen
                db.collection("users").document(uid)
                    .update("leftRoom", true).await()
            } catch (e: Exception) { Log.w(TAG, "leaveQueue: ${e.message}") }
        }
    }

    // FIX #1: Stay in queue, close the app, wait for FCM notification.
    // NonCancellable ensures the Firestore write completes even as the VM is cleared.
    // WorkManager heartbeat keeps the entry alive indefinitely (until uninstall / match / cancel).
    fun stayInQueueAndLeave(context: Context) {
        val uid = myUidCache.takeIf { it.isNotBlank() } ?: auth.currentUser?.uid ?: return
        _isBackgroundMode.value = true
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    db.collection("matchmaking_queue").document(uid).update(
                        mapOf(
                            "backgroundMode" to true,
                            "heartbeatAt"    to Timestamp.now()
                        )
                    ).await()
                    Log.d(TAG, "FIX#1: backgroundMode set — queue entry will persist")
                } catch (e: Exception) {
                    Log.w(TAG, "stayInQueueAndLeave Firestore update failed: ${e.message}")
                }
            }
        }
        // WorkManager periodic heartbeat — survives app kill + reboot, until uninstall
        QueueHeartbeatWorker.schedule(context)
    }

    /** Cancel background queue: stop the heartbeat worker and remove the queue entry */
    fun cancelBackgroundQueue(context: Context) {
        _isBackgroundMode.value = false
        QueueHeartbeatWorker.cancel(context)
        leaveQueue()
    }

    // H2: Leave current batch and re-enter queue fresh (new joinedAt → new batch)
    fun requestNewRoom() {
        val uid = myUidCache.takeIf { it.isNotBlank() } ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                // Remove from queue first
                db.collection("matchmaking_queue").document(uid).delete().await()
                Log.d(TAG, "H2: Left current batch, re-entering queue...")
                // Cancel old listener so we don't get stale updates
                queueListener?.remove()
                queueListener = null
                heartbeatJob?.cancel()
                // Small delay so the queue listener has time to see the deletion
                delay(400L)
                // Re-enter as new user (fresh timestamp)
                enterQueue()
            } catch (e: Exception) {
                Log.e(TAG, "H2: requestNewRoom failed", e)
                _state.value = WaitingUiState.Error("Couldn't switch room. Try again.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueListener?.remove()
        userListener?.remove()
        pollJob?.cancel()
        heartbeatJob?.cancel()
        // C5: ensure queue entry is cleaned up when ViewModel dies
        // FIX #1: skip cleanup if user chose background mode (entry must persist for FCM match)
        if (myUidCache.isNotBlank()
            && _state.value !is WaitingUiState.GroupFound
            && !_isBackgroundMode.value) {
            try {
                db.collection("matchmaking_queue").document(myUidCache).delete()
                Log.d(TAG, "C5: queue cleanup on ViewModel.onCleared()")
            } catch (_: Exception) {}
        } else if (_isBackgroundMode.value) {
            Log.d(TAG, "C5: skipping cleanup — user is in background queue mode")
        }
    }
}
