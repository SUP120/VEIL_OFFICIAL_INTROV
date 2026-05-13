package com.ns.findyourintroverfriend.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "InboxVM"

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

// The 3 reactions users can send privately
enum class Reaction(val emoji: String, val label: String) {
    FELT("❤️", "I felt this"),
    BEAUTIFUL("✨", "Beautiful"),
    DEEP("🌊", "Deep")
}

data class InboxAnswer(
    val respondentUid   : String,
    val respondentEmoji : String,
    val text            : String,
    val myReaction      : Reaction? = null  // null = not reacted yet
)

sealed class InboxUiState {
    object Loading                                             : InboxUiState()
    object NoQuestion                                          : InboxUiState()  // I haven't posted yet
    data class Ready(
        val myQuestion : String,
        val answers    : List<InboxAnswer>
    )                                                          : InboxUiState()
    data class Error(val msg: String)                          : InboxUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class InboxViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var groupId      = ""
    private var questionDocId = ""

    init { loadInbox() }

    private fun loadInbox() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = InboxUiState.Error("Not signed in.")
            return
        }
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                groupId = userDoc.getString("groupId") ?: ""
                if (groupId.isBlank()) {
                    _state.value = InboxUiState.Error("No active group.")
                    return@launch
                }

                val todayKey = todayKey()
                questionDocId = "${uid}_$todayKey"

                // Does my question exist?
                val myQDoc = db.collection("groups").document(groupId)
                    .collection("daily_questions")
                    .document(questionDocId)
                    .get().await()

                if (!myQDoc.exists()) {
                    _state.value = InboxUiState.NoQuestion
                    return@launch
                }

                val myQuestion = myQDoc.getString("question") ?: ""

                // Load all answers
                val answersSnap = db.collection("groups").document(groupId)
                    .collection("daily_questions")
                    .document(questionDocId)
                    .collection("answers")
                    .get().await()

                val answers = answersSnap.documents.mapNotNull { doc ->
                    val respUid  = doc.getString("uid") ?: return@mapNotNull null
                    val text     = doc.getString("text") ?: return@mapNotNull null

                    // Load responder's emoji
                    val respDoc   = db.collection("users").document(respUid).get().await()
                    val respEmoji = respDoc.getString("emoji") ?: "🌿"

                    // My reaction?
                    val reactionStr = doc.getString("myReaction_$uid")
                    val reaction    = Reaction.values().find { it.name == reactionStr }

                    InboxAnswer(
                        respondentUid   = respUid,
                        respondentEmoji = respEmoji,
                        text            = text,
                        myReaction      = reaction
                    )
                }

                _state.value = InboxUiState.Ready(myQuestion = myQuestion, answers = answers)
                Log.d(TAG, "Inbox loaded: ${answers.size} answers for question=$myQuestion")

            } catch (e: Exception) {
                Log.e(TAG, "loadInbox failed", e)
                _state.value = InboxUiState.Error(e.localizedMessage ?: "Failed to load inbox.")
            }
        }
    }

    fun sendReaction(answer: InboxAnswer, reaction: Reaction) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                // Store my reaction on the answer doc (keyed by my uid so it's private)
                db.collection("groups").document(groupId)
                    .collection("daily_questions")
                    .document(questionDocId)
                    .collection("answers")
                    .document(answer.respondentUid)
                    .update("myReaction_$uid", reaction.name)
                    .await()

                // Update local state
                val current = (_state.value as? InboxUiState.Ready) ?: return@launch
                val updated = current.answers.map {
                    if (it.respondentUid == answer.respondentUid)
                        it.copy(myReaction = reaction)
                    else it
                }
                _state.value = current.copy(answers = updated)
                Log.d(TAG, "Reaction ${reaction.name} sent to ${answer.respondentUid}")

            } catch (e: Exception) {
                Log.e(TAG, "sendReaction failed", e)
            }
        }
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}_${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }
}
