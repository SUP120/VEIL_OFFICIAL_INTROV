package com.ns.findyourintroverfriend.reveal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ns.findyourintroverfriend.party.BondCalculator
import com.ns.findyourintroverfriend.party.BondResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "RevealVM"

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

data class RevealMember(
    val uid      : String,
    val name     : String,
    val emoji    : String,
    val gender   : String,
    val photoUrl : String  = "",
    val isYou    : Boolean
)

sealed class RevealUiState {
    object Loading : RevealUiState()

    /**
     * Bond revealed + user is a WINNER.
     * [partnerHasJoined] starts false; flips to true when Firestore shows the
     * partner has also entered the party session. Once true, the "waiting" banner
     * disappears and both can fully enter.
     */
    data class Winner(
        val members          : List<RevealMember>,
        val bondResult       : BondResult,
        val partnerHasJoined : Boolean = false
    ) : RevealUiState()

    /**
     * Bond revealed + user is NOT a winner this round.
     * Shows a graceful message with two CTAs.
     */
    data class NoMatch(
        val members    : List<RevealMember>,
        val bondResult : BondResult
    ) : RevealUiState()

    /** Still computing the bond (shown very briefly). */
    data class Computing(val members: List<RevealMember>) : RevealUiState()

    data class Error(val msg: String) : RevealUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class RevealCeremonyViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<RevealUiState>(RevealUiState.Loading)
    val state: StateFlow<RevealUiState> = _state.asStateFlow()

    /** Firestore listener watching party_sessions/{sessionId} for partner join */
    private var partnerListener: ListenerRegistration? = null

    init { loadReveal() }

    // ── Load members + compute bond immediately — no waiting for anyone else ──
    private fun loadReveal() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = RevealUiState.Error("Not signed in.")
            return
        }
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                // groupId may have been deleted already if user updated app mid-ceremony.
                // pendingCeremony holds the groupId in that case as a fallback.
                val groupId = userDoc.getString("groupId")?.takeIf { it.isNotBlank() }
                    ?: userDoc.getString("pendingCeremony")?.takeIf { it.isNotBlank() }
                    ?: run {
                        _state.value = RevealUiState.Error("No active group.")
                        return@launch
                    }

                val groupDoc   = db.collection("groups").document(groupId).get().await()
                @Suppress("UNCHECKED_CAST")
                val memberUids = groupDoc.get("members") as? List<String> ?: emptyList()
                val day        = (groupDoc.getLong("day") ?: 1L).toInt()

                // Fetch all member profiles in parallel feel (sequential for safety)
                val members = memberUids.map { memberId ->
                    val mDoc = db.collection("users").document(memberId).get().await()
                    RevealMember(
                        uid      = memberId,
                        name     = mDoc.getString("alias")    ?: "Mystery",
                        emoji    = mDoc.getString("emoji")    ?: "🌙",
                        gender   = mDoc.getString("gender")   ?: "",
                        photoUrl = mDoc.getString("photoUrl") ?: "",
                        isYou    = memberId == uid
                    )
                }

                // ── Mark ceremony in-progress ─────────────────────────────────────
                //  Step 1: Write pendingCeremony=groupId to user doc FIRST.
                //          Cold-start router reads this to route back to CEREMONY
                //          even if the user updated the app mid-ceremony and groupId
                //          is already deleted.
                //  Step 2: Mark group ceremonyDone=true so other members also skip.
                //  Step 3: Delete groupId from user (so Find New Room never loops back).
                //          pendingCeremony is cleared after bond calculation succeeds.
                db.collection("users").document(uid)
                    .update("pendingCeremony", groupId)
                    .await()

                try {
                    db.collection("groups").document(groupId)
                        .update("ceremonyDone", true)
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "groupDoc ceremonyDone write failed (non-fatal): ${e.message}")
                }
                db.collection("users").document(uid)
                    .update(
                        mapOf(
                            "ceremonyDone" to true,
                            "groupId"      to com.google.firebase.firestore.FieldValue.delete()
                        )
                    ).await()

                // Show "Computing…" briefly while bond is calculated
                _state.value = RevealUiState.Computing(members)

                // ── Compute bond score — completely async, no waiting for others ──
                val bond = BondCalculator.findBestPair(db, groupId, members, uid)

                if (bond == null) {
                    _state.value = RevealUiState.Error("Could not calculate bond. Try again.")
                    return@launch
                }

                if (bond.isCurrentUserWinner) {
                    // Winner: go to Winner state and start watching for partner
                    _state.value = RevealUiState.Winner(members, bond, partnerHasJoined = false)
                    watchForPartner(bond.sessionId, uid)
                } else {
                    // Non-winner: show NoMatch state
                    _state.value = RevealUiState.NoMatch(members, bond)
                }

                // ── Clear pendingCeremony — bond computed, ceremony shown ───────────
                //  Cold-start router no longer needs this marker.
                try {
                    db.collection("users").document(uid)
                        .update("pendingCeremony", com.google.firebase.firestore.FieldValue.delete())
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "pendingCeremony clear failed (non-fatal): ${e.message}")
                }

                Log.d(TAG, "Reveal done: isWinner=${bond.isCurrentUserWinner} session=${bond.sessionId}")

            } catch (e: Exception) {
                Log.e(TAG, "loadReveal failed", e)
                _state.value = RevealUiState.Error(e.localizedMessage ?: "Failed to load ceremony.")
            }
        }
    }

    /**
     * Listen on party_sessions/{sessionId} for a field [enteredAt_{partnerUid}].
     * When the partner taps "Enter Island", this field is written, and we update
     * [RevealUiState.Winner.partnerHasJoined] = true so the "waiting" banner disappears.
     */
    private fun watchForPartner(sessionId: String, myUid: String) {
        partnerListener?.remove()
        partnerListener = db.collection("party_sessions").document(sessionId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val cur = _state.value as? RevealUiState.Winner ?: return@addSnapshotListener

                // Partner has written their "entered" marker
                val partnerUid  = if (cur.bondResult.winnerA.uid == myUid)
                    cur.bondResult.winnerB.uid else cur.bondResult.winnerA.uid
                val partnerInDoc = snap.contains("enteredAt_$partnerUid")

                if (partnerInDoc && !cur.partnerHasJoined) {
                    _state.value = cur.copy(partnerHasJoined = true)
                    Log.d(TAG, "Partner joined the session!")
                }
            }
    }

    /**
     * Called when the winner taps "Enter the Island".
     * Writes their enteredAt marker to the party session doc so their partner
     * sees [partnerHasJoined] = true.
     */
    fun markSelfEntered(sessionId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("party_sessions").document(sessionId)
                    .update("enteredAt_$uid", com.google.firebase.Timestamp.now())
                    .await()
                db.collection("users").document(uid)
                    .update("partySessionId", sessionId)
                    .await()
                Log.d(TAG, "markSelfEntered: $uid → $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "markSelfEntered failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        partnerListener?.remove()
    }
}
