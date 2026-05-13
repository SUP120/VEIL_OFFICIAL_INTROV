package com.ns.findyourintroverfriend.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "GameInviteVM"

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

data class CircleMate(
    val uid    : String,
    val alias  : String,
    val emoji  : String,
    val gender : String,
    val isBot  : Boolean = false
)

data class IncomingInvite(
    val inviteId   : String,
    val fromUid    : String,
    val fromAlias  : String,
    val fromEmoji  : String,
    val sentAt     : Timestamp? = null
)

data class GameInviteState(
    val myGender       : String          = "",
    val mates          : List<CircleMate> = emptyList(),  // opposite gender, invitable
    val pendingInvite  : String?         = null,         // uid I already invited
    val incomingInvite : IncomingInvite? = null,
    val acceptedGameId : String?         = null,         // non-null when game is ready
    val loading        : Boolean         = true,
    val error          : String?         = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class GameInviteViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(GameInviteState())
    val state: StateFlow<GameInviteState> = _state.asStateFlow()

    private var groupId    = ""
    private var myUid      = ""
    private var invListener: ListenerRegistration? = null

    init { load() }

    private fun load() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = _state.value.copy(loading = false, error = "Not signed in.")
            return
        }
        myUid = uid
        viewModelScope.launch {
            try {
                val userDoc  = db.collection("users").document(uid).get().await()
                groupId      = userDoc.getString("groupId") ?: ""
                val myGender = userDoc.getString("gender") ?: "?"

                if (groupId.isBlank()) {
                    _state.value = _state.value.copy(loading = false, error = "No active group.")
                    return@launch
                }

                // Load group members of opposite gender
                val groupDoc = db.collection("groups").document(groupId).get().await()
                @Suppress("UNCHECKED_CAST")
                val memberIds = (groupDoc.get("members") as? List<String>) ?: emptyList()
                val oppositeGender = if (myGender.equals("male", ignoreCase = true)) "female" else "male"

                val mates = memberIds
                    .filter { it != uid }
                    .mapNotNull { mUid ->
                        val mDoc = db.collection("users").document(mUid).get().await()
                        val g = mDoc.getString("gender") ?: return@mapNotNull null
                        if (!g.equals(oppositeGender, ignoreCase = true)) null
                        else CircleMate(
                            uid    = mUid,
                            alias  = mDoc.getString("alias")  ?: "Someone",
                            emoji  = mDoc.getString("emoji")  ?: "🌙",
                            gender = g,
                            isBot  = mDoc.getBoolean("isBot") ?: false
                        )
                    }

                // Check if I already sent an invite
                val sentSnap = db.collection("groups").document(groupId)
                    .collection("game_invites")
                    .whereEqualTo("fromUid", uid)
                    .whereEqualTo("status", "pending")
                    .get().await()
                val pendingInvite = sentSnap.documents.firstOrNull()?.getString("toUid")

                _state.value = _state.value.copy(
                    myGender      = myGender,
                    mates         = mates,
                    pendingInvite = pendingInvite,
                    loading       = false
                )

                listenForIncoming()

            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.value = _state.value.copy(loading = false, error = e.localizedMessage)
            }
        }
    }

    /** Listen for invites sent TO me that are still pending. */
    private fun listenForIncoming() {
        invListener?.remove()
        invListener = db.collection("groups").document(groupId)
            .collection("game_invites")
            .whereEqualTo("toUid", myUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull()
                if (doc != null) {
                    viewModelScope.launch {
                        try {
                            val fromUid   = doc.getString("fromUid") ?: return@launch
                            val fromDoc   = db.collection("users").document(fromUid).get().await()
                            _state.value = _state.value.copy(
                                incomingInvite = IncomingInvite(
                                    inviteId  = doc.id,
                                    fromUid   = fromUid,
                                    fromAlias = fromDoc.getString("alias") ?: "Someone",
                                    fromEmoji = fromDoc.getString("emoji") ?: "🌙",
                                    sentAt    = doc.getTimestamp("sentAt")
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "listenForIncoming fetch failed", e)
                        }
                    }
                } else {
                    _state.value = _state.value.copy(incomingInvite = null)
                }
            }
    }

    /** Send an invite to a circle mate. Cross-gender only — enforced by UI filter. */
    fun sendInvite(toMate: CircleMate) {
        if (_state.value.pendingInvite != null) return
        viewModelScope.launch {
            try {
                val inviteRef = db.collection("groups").document(groupId)
                    .collection("game_invites").document()
                inviteRef.set(mapOf(
                    "fromUid" to myUid,
                    "toUid"   to toMate.uid,
                    "status"  to "pending",
                    "sentAt"  to Timestamp.now()
                )).await()
                _state.value = _state.value.copy(pendingInvite = toMate.uid)
                Log.d(TAG, "Invite sent to ${toMate.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "sendInvite failed", e)
            }
        }
    }

    /** Accept an incoming invite — creates the game doc and returns gameId. */
    fun acceptInvite(invite: IncomingInvite) {
        viewModelScope.launch {
            try {
                // Mark invite as accepted
                db.collection("groups").document(groupId)
                    .collection("game_invites").document(invite.inviteId)
                    .update("status", "accepted").await()

                // Create a new TTT game doc
                // Inviter (X) vs Accepter (O) — inviter goes first
                val gameRef = db.collection("groups").document(groupId)
                    .collection("ttt_games").document()

                gameRef.set(mapOf(
                    "playerX"     to invite.fromUid,
                    "playerO"     to myUid,
                    "board"       to List(9) { null },
                    "currentTurn" to invite.fromUid,  // X goes first
                    "gameOver"    to false,
                    "winner"      to null,
                    "createdAt"   to Timestamp.now()
                )).await()

                _state.value = _state.value.copy(
                    incomingInvite = null,
                    acceptedGameId = gameRef.id
                )
                Log.d(TAG, "Game created: ${gameRef.id}")

            } catch (e: Exception) {
                Log.e(TAG, "acceptInvite failed", e)
            }
        }
    }

    /** Decline incoming invite. */
    fun declineInvite(invite: IncomingInvite) {
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("game_invites").document(invite.inviteId)
                    .update("status", "declined").await()
                _state.value = _state.value.copy(incomingInvite = null)
            } catch (e: Exception) {
                Log.e(TAG, "declineInvite failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        invListener?.remove()
    }
}
