package com.ns.findyourintroverfriend.poll

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

private const val TAG = "WyrVM"

// ─────────────────────────────────────────────────────────────────────────────
//  Hardcoded Day 2 "Would You Rather" questions
// ─────────────────────────────────────────────────────────────────────────────

data class WyrQuestion(
    val id    : String,
    val optA  : String,
    val optB  : String
)

private val WYR_QUESTIONS = listOf(
    WyrQuestion("wyr_1", "Know everything  ·  but feel nothing",    "Feel everything  ·  but know nothing"),
    WyrQuestion("wyr_2", "Always speak the truth",                  "Always know the truth"),
    WyrQuestion("wyr_3", "Be deeply understood by one person",      "Be admired by everyone"),
    WyrQuestion("wyr_4", "Live 10 rich years",                      "Live 100 quiet years"),
    WyrQuestion("wyr_5", "Have no regrets  ·  but no memories",     "Have memories  ·  but some regret")
)

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

/** How many circle mates picked each option */
data class WyrTally(
    val countA : Int = 0,
    val countB : Int = 0,
    val total  : Int = 0
)

data class WyrQuestionState(
    val question : WyrQuestion,
    val myPick   : String?  = null,   // "A" or "B", null = not voted yet
    val tally    : WyrTally = WyrTally()
)

data class WyrUiState(
    val questions : List<WyrQuestionState> = emptyList(),
    val loading   : Boolean                = true,
    val error     : String?                = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class WouldYouRatherViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(WyrUiState())
    val state: StateFlow<WyrUiState> = _state.asStateFlow()

    private var groupId  = ""
    private var myUid    = ""
    private val listeners = mutableListOf<ListenerRegistration>()

    init { load() }

    private fun load() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = _state.value.copy(loading = false, error = "Not signed in.")
            return
        }
        myUid = uid
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                groupId = userDoc.getString("groupId") ?: ""

                if (groupId.isBlank()) {
                    _state.value = _state.value.copy(loading = false, error = "No active group.")
                    return@launch
                }

                // Build initial state for all WYR questions
                val questionStates = WYR_QUESTIONS.map { q ->
                    // Check if I already picked
                    val myPickDoc = db.collection("groups").document(groupId)
                        .collection("wyr_picks").document(q.id)
                        .collection("answers").document(uid).get().await()
                    val myPick = if (myPickDoc.exists()) myPickDoc.getString("pick") else null

                    // Tally all picks
                    val allPicks = db.collection("groups").document(groupId)
                        .collection("wyr_picks").document(q.id)
                        .collection("answers").get().await()
                    val countA = allPicks.documents.count { it.getString("pick") == "A" }
                    val countB = allPicks.documents.count { it.getString("pick") == "B" }

                    WyrQuestionState(
                        question = q,
                        myPick   = myPick,
                        tally    = WyrTally(countA, countB, allPicks.size())
                    )
                }

                _state.value = _state.value.copy(questions = questionStates, loading = false)

                // Live-listen for tally updates on each question
                listenToTallies()

            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.value = _state.value.copy(loading = false, error = e.localizedMessage)
            }
        }
    }

    private fun listenToTallies() {
        listeners.forEach { it.remove() }
        listeners.clear()
        WYR_QUESTIONS.forEach { q ->
            val reg = db.collection("groups").document(groupId)
                .collection("wyr_picks").document(q.id)
                .collection("answers")
                .addSnapshotListener { snap, _ ->
                    if (snap == null) return@addSnapshotListener
                    val countA = snap.documents.count { it.getString("pick") == "A" }
                    val countB = snap.documents.count { it.getString("pick") == "B" }
                    updateTally(q.id, WyrTally(countA, countB, snap.size()))
                }
            listeners.add(reg)
        }
    }

    fun vote(questionId: String, pick: String /* "A" or "B" */) {
        val current = _state.value.questions.find { it.question.id == questionId }
        if (current?.myPick != null) return   // already voted — immutable
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("wyr_picks").document(questionId)
                    .collection("answers").document(myUid)
                    .set(mapOf("pick" to pick, "votedAt" to Timestamp.now())).await()

                // Optimistic update
                _state.value = _state.value.copy(
                    questions = _state.value.questions.map {
                        if (it.question.id == questionId) it.copy(myPick = pick) else it
                    }
                )
                Log.d(TAG, "Voted $pick on $questionId")
            } catch (e: Exception) {
                Log.e(TAG, "vote failed", e)
            }
        }
    }

    private fun updateTally(questionId: String, tally: WyrTally) {
        _state.value = _state.value.copy(
            questions = _state.value.questions.map {
                if (it.question.id == questionId) it.copy(tally = tally) else it
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach { it.remove() }
    }
}
