package com.ns.findyourintroverfriend.answer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AnswerVM"

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

data class CircleQuestion(
    val docId        : String,   // "{uid}_{dayKey}_{nonce}"
    val ownerUid     : String,
    val ownerAlias   : String,   // NEW: visible alias after Day 1 reveal
    val ownerEmoji   : String,
    val ownerGender  : String,
    val question     : String,
    val myAnswer     : String?,  // null = not answered yet
    val answeredAt   : Timestamp? = null,
    // Reaction I sent to this answer (emoji or null)
    val myReaction   : String?  = null,
    // Whether I've echoed this answer (resonated with it)
    val echoSent     : Boolean  = false
)

/** A single anonymous answer to the current-user's OWN question */
data class AnonymousAnswer(
    val answererEmoji: String,
    val answererAlias: String,   // hidden as "?" until Day 3
    val text         : String,
    val reaction     : String?   // emoji the answerer sent back, if any
)

sealed class AnswerUiState {
    object Loading                                        : AnswerUiState()
    data class Ready(
        val questions     : List<CircleQuestion>,
        val groupDay      : Int,
        val groupId       : String            = "",
        val myOwnQuestion : CircleQuestion?   = null,
        val myQAnswers    : List<AnonymousAnswer> = emptyList()  // answers others gave to your Q
    ) : AnswerUiState()
    data class Error(val msg: String)                     : AnswerUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class AnswerCardViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<AnswerUiState>(AnswerUiState.Loading)
    val state: StateFlow<AnswerUiState> = _state.asStateFlow()

    private var groupId = ""
    private var myUid   = ""

    init { loadQuestions() }

    private fun loadQuestions() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = AnswerUiState.Error("Not signed in.")
            return
        }
        myUid = uid
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                groupId = userDoc.getString("groupId") ?: ""
                if (groupId.isBlank()) {
                    _state.value = AnswerUiState.Error("No active group.")
                    return@launch
                }

                val groupDoc = db.collection("groups").document(groupId).get().await()
                val groupDay = (groupDoc.getLong("day") ?: 1L).toInt()

                val todayKey = todayKey()

                // All questions posted today by circle mates
                val qSnap = db.collection("groups").document(groupId)
                    .collection("daily_questions")
                    .whereEqualTo("day", todayKey)
                    .get().await()

                val questions = mutableListOf<CircleQuestion>()
                var myOwnQuestion: CircleQuestion? = null

                qSnap.documents.mapNotNull { doc ->
                    val ownerUid = doc.getString("uid") ?: return@mapNotNull null
                    val isMyOwn  = (ownerUid == uid)

                    val questionText = doc.getString("question") ?: return@mapNotNull null

                    // Load owner profile
                    val ownerDoc    = db.collection("users").document(ownerUid).get().await()
                    val ownerEmoji  = ownerDoc.getString("emoji")  ?: "🌙"
                    val ownerGender = ownerDoc.getString("gender") ?: "?"
                    val ownerAlias  = ownerDoc.getString("alias")  ?: "Someone"

                    // Check if I've answered
                    val answerDoc = doc.reference.collection("answers")
                        .document(uid).get().await()

                    // Check if I've reacted (stored on answer doc under "reactions" subcollection)
                    val myReaction: String? = if (answerDoc.exists()) {
                        try {
                            val rxDoc = answerDoc.reference.collection("reactions")
                                .document(uid).get().await()
                            rxDoc.getString("emoji")
                        } catch (e: Exception) { null }
                    } else null

                    // Check if I've echoed (stored in group echoes collection)
                    val echoSent = try {
                        val echoDoc = db.collection("groups").document(groupId)
                            .collection("echoes").document(ownerUid)
                            .collection("sources").document(uid).get().await()
                        echoDoc.exists()
                    } catch (e: Exception) { false }

                    val cq = CircleQuestion(
                        docId       = doc.id,
                        ownerUid    = ownerUid,
                        ownerAlias  = ownerAlias,
                        ownerEmoji  = ownerEmoji,
                        ownerGender = ownerGender,
                        question    = questionText,
                        myAnswer    = if (answerDoc.exists()) answerDoc.getString("text") else null,
                        answeredAt  = if (answerDoc.exists()) answerDoc.getTimestamp("answeredAt") else null,
                        myReaction  = myReaction,
                        echoSent    = echoSent
                    )
                    if (isMyOwn) myOwnQuestion = cq else questions.add(cq)
                    cq   // mapNotNull expects a return value; we don't use the list result
                }

                _state.value = AnswerUiState.Ready(questions, groupDay, groupId, myOwnQuestion)

                // ── Load answers received for my OWN question ─────────────────
                if (myOwnQuestion != null) {
                    val receivedAnswers = mutableListOf<AnonymousAnswer>()
                    try {
                        val answerSnap = db.collection("groups").document(groupId)
                            .collection("daily_questions")
                            .document(myOwnQuestion.docId)
                            .collection("answers")
                            .get().await()
                        for (ansDoc in answerSnap.documents) {
                            val answererUid = ansDoc.getString("uid") ?: continue
                            if (answererUid == uid) continue   // skip my own answer
                            val text = ansDoc.getString("text") ?: continue
                            val aDoc = db.collection("users").document(answererUid).get().await()
                            val emoji = aDoc.getString("emoji") ?: "🌙"
                            val alias = if (groupDay >= 3) aDoc.getString("alias") ?: "Someone" else "?"
                            // Any emoji reaction they sent
                            val rxEmoji = try {
                                val rx = ansDoc.reference.collection("reactions")
                                    .document(answererUid).get().await()
                                rx.getString("emoji")
                            } catch (_: Exception) { null }
                            receivedAnswers.add(AnonymousAnswer(emoji, alias, text, rxEmoji))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load received answers: ${e.message}")
                    }
                    _state.value = AnswerUiState.Ready(
                        questions     = questions,
                        groupDay      = groupDay,
                        groupId       = groupId,
                        myOwnQuestion = myOwnQuestion,
                        myQAnswers    = receivedAnswers
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "loadQuestions failed", e)
                _state.value = AnswerUiState.Error(e.localizedMessage ?: "Failed to load questions.")
            }
        }
    }

    fun submitAnswer(question: CircleQuestion, answerText: String) {
        val uid = auth.currentUser?.uid ?: return
        if (answerText.isBlank()) return

        viewModelScope.launch {
            try {
                val answerData = mapOf(
                    "uid"        to uid,
                    "text"       to answerText.trim(),
                    "answeredAt" to Timestamp.now()
                )
                db.collection("groups").document(groupId)
                    .collection("daily_questions")
                    .document(question.docId)
                    .collection("answers")
                    .document(uid)
                    .set(answerData).await()

                updateQuestion(question.docId) { it.copy(myAnswer = answerText.trim(), answeredAt = Timestamp.now()) }
                Log.d(TAG, "Answer submitted for ${question.docId}")

            } catch (e: Exception) {
                Log.e(TAG, "submitAnswer failed", e)
            }
        }
    }

    // ── Soft Reaction ─────────────────────────────────────────────────────────
    /**
     * React to the question owner's answer to MY question with an emoji.
     * The reaction is stored under:
     *   groups/{gId}/daily_questions/{qDocId}/answers/{myUid}/reactions/{ownerUid}
     * ie: "I reacted to MY OWN answer in ownerUid's question with emoji"
     *
     * Actually simpler: we let the CURRENT USER react to ANOTHER person's answer to
     * that person's OWN question. Stored on the answer doc.
     *
     * Reaction path: daily_questions/{qDocId}/answers/{ownerUid_who_answered}/reactions/{myUid}
     * But since this is answer card, myAnswer is what I wrote. We react to ourselves on answered
     * questions as "validation sent back."
     *
     * REAL use: after I answer qDoc, the qOwner can later react to my answer.
     * Since we're showing incoming questions, the "sendReaction" here means:
     * "I answered and I want to send an emoji back on my OWN answer doc to signal the owner."
     * Stored: daily_questions/{q.docId}/answers/{myUid}/reactions/{myUid} = {emoji, ..}
     */
    fun sendReaction(question: CircleQuestion, emoji: String) {
        viewModelScope.launch {
            try {
                val data = mapOf(
                    "emoji"     to emoji,
                    "sentAt"    to Timestamp.now(),
                    "fromUid"   to myUid
                )
                db.collection("groups").document(groupId)
                    .collection("daily_questions").document(question.docId)
                    .collection("answers").document(myUid)
                    .collection("reactions").document(myUid)
                    .set(data).await()

                // Also write a cross-reference for BondCalculator
                db.collection("groups").document(groupId)
                    .collection("reactions").document(myUid)
                    .collection("sources").document(question.ownerUid)
                    .set(mapOf("emoji" to emoji, "sentAt" to Timestamp.now())).await()

                updateQuestion(question.docId) { it.copy(myReaction = emoji) }
                Log.d(TAG, "Reaction $emoji sent on ${question.docId}")
            } catch (e: Exception) {
                Log.e(TAG, "sendReaction failed", e)
            }
        }
    }

    // ── Echo (resonance) ─────────────────────────────────────────────────────
    /**
     * Echo = "I deeply resonated with this question / answer."
     * Stored in: groups/{gId}/echoes/{ownerUid}/sources/{myUid}
     * BondCalculator reads this path for +2 bonus.
     */
    fun sendEcho(question: CircleQuestion) {
        if (question.echoSent) return   // idempotent
        viewModelScope.launch {
            try {
                val data = mapOf(
                    "fromUid" to myUid,
                    "qDocId"  to question.docId,
                    "sentAt"  to Timestamp.now()
                )
                db.collection("groups").document(groupId)
                    .collection("echoes").document(question.ownerUid)
                    .collection("sources").document(myUid)
                    .set(data).await()

                updateQuestion(question.docId) { it.copy(echoSent = true) }
                Log.d(TAG, "Echo sent to ${question.ownerUid}")
            } catch (e: Exception) {
                Log.e(TAG, "sendEcho failed", e)
            }
        }
    }

    /** Convenience: update a single question in state. */
    private fun updateQuestion(docId: String, transform: (CircleQuestion) -> CircleQuestion) {
        val currentState = _state.value as? AnswerUiState.Ready ?: return
        _state.value = currentState.copy(
            questions = currentState.questions.map { if (it.docId == docId) transform(it) else it }
        )
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}_${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }
}
