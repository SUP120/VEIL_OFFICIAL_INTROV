package com.ns.findyourintroverfriend.question

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.home.MAX_QUESTIONS_PER_DAY
import com.ns.findyourintroverfriend.util.BanStatus
import com.ns.findyourintroverfriend.util.ContentModerator
import com.ns.findyourintroverfriend.util.StrikeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "QuestionVM"

// ─────────────────────────────────────────────────────────────────────────────
//  Curated question banks — per day theme
// ─────────────────────────────────────────────────────────────────────────────

/** Day 1 — Icebreakers: light, warm, fun. Gets people talking. */
val DAY1_QUESTIONS = listOf(
    "What's a small thing that always makes your day better?",
    "If you could live anywhere for one year, where would it be?",
    "What's something you're weirdly proud of?",
    "What does a perfect Sunday look like for you?",
    "What's one thing people always get wrong about you?",
    "What kind of music do you listen to when no one's watching?",
    "What's the last thing that genuinely made you laugh?",
    "If you could master any skill instantly, what would it be?",
    "What's your relationship with mornings — love or complicated?",
    "What was the best phase of your life so far?"
)

/** Day 2 — Curiosity: deeper values and connection. */
val DAY2_QUESTIONS = listOf(
    "What matters more to you right now — peace or ambition?",
    "Is there something you deeply respect but rarely talk about?",
    "What's one belief you hold that most people around you don't?",
    "How do you react when someone you care about disagrees with you?",
    "What does trust mean to you — and how long does it take to earn?",
    "What's something about yourself you wish people asked about more?",
    "How do you show that you care — without saying it directly?",
    "Is there a version of yourself that only a few people have ever seen?",
    "What's one thing you've unlearned that made you better?",
    "What does 'home' feel like to you — is it a place, a feeling, or a person?"
)

/** Day 3 — Connection: vulnerability, growth, future. */
val DAY3_QUESTIONS = listOf(
    "What would you do differently if no one was watching or judging?",
    "What's one thing you're quietly working on becoming?",
    "If you could change one social norm, what would it be and why?",
    "What's the most meaningful thing someone has ever said to you?",
    "What emotion do you find hardest to express, and why?",
    "What does a life well-lived look like to you?",
    "What's one fear you haven't fully admitted to yourself yet?",
    "When did you last feel truly understood by someone?",
    "What part of yourself are you still trying to figure out?",
    "If I could know one thing about you that you never talk about — what would it be?"
)

// Legacy alias — keeps old references compiling
val VEIL_QUESTIONS get() = DAY1_QUESTIONS + DAY2_QUESTIONS + DAY3_QUESTIONS

/** Returns the right bank for the given group day. */
fun questionsForDay(day: Int): List<String> = when (day) {
    1    -> DAY1_QUESTIONS
    2    -> DAY2_QUESTIONS
    else -> DAY3_QUESTIONS
}

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────

sealed class QuestionUiState {
    object Loading                                                         : QuestionUiState()
    /** User can post another question; shows how many slots remain. */
    data class Picking(
        val suggestions    : List<String>,
        val slotsRemaining : Int              // e.g. 3 = none posted yet
    )                                                                      : QuestionUiState()
    object Submitting                                                      : QuestionUiState()
    object Submitted                                                       : QuestionUiState()
    /** User has used all 3 slots today. */
    data class DoneForToday(val questions: List<String>)                   : QuestionUiState()
    data class Error(val msg: String)                                      : QuestionUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class QuestionCardViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<QuestionUiState>(QuestionUiState.Loading)
    val state: StateFlow<QuestionUiState> = _state.asStateFlow()

    private var groupId  : String = ""
    private var groupDay : Int    = 1

    // ── Moderation: live warning shown in UI + violation counter ──────────────
    private val _moderationWarning = MutableStateFlow<String?>(null)
    val moderationWarning: StateFlow<String?> = _moderationWarning.asStateFlow()

    /** How many times the user has tried to post unsafe content this session */
    private var violationCount = 0

    init { loadState() }

    private fun loadState() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = QuestionUiState.Error("Not signed in.")
            return
        }
        viewModelScope.launch {
            try {
                // Get user's groupId + group day
                val userDoc = db.collection("users").document(uid).get().await()
                groupId     = userDoc.getString("groupId") ?: ""

                if (groupId.isBlank()) {
                    _state.value = QuestionUiState.Error("No active group.")
                    return@launch
                }

                val groupDoc = db.collection("groups").document(groupId).get().await()
                groupDay = (groupDoc.getLong("day") ?: 1L).toInt()

                refreshPickState(uid)

            } catch (e: Exception) {
                Log.e(TAG, "loadState failed", e)
                _state.value = QuestionUiState.Error(e.localizedMessage ?: "Error loading.")
            }
        }
    }

    /** (Re)compute how many slots remain and update state accordingly. */
    private suspend fun refreshPickState(uid: String) {
        val todayKey  = todayKey()
        val qSnap     = db.collection("groups").document(groupId)
            .collection("daily_questions")
            .whereEqualTo("uid", uid)
            .whereEqualTo("day", todayKey)
            .get().await()

        val submitted = qSnap.documents.mapNotNull { it.getString("question") }
        val remaining = MAX_QUESTIONS_PER_DAY - submitted.size

        if (remaining <= 0) {
            _state.value = QuestionUiState.DoneForToday(submitted)
            return
        }

        // Fresh suggestions from the day's bank (excluding already submitted ones)
        val bank        = questionsForDay(groupDay).filter { it !in submitted }
        val seed        = uid.hashCode().toLong() + todayKey.hashCode().toLong() + submitted.size
        val rng         = java.util.Random(seed)
        val suggestions = bank.shuffled(rng).take(4)   // show 4 options, user picks one

        _state.value = QuestionUiState.Picking(suggestions, remaining)
        Log.d(TAG, "refreshPickState: day=$groupDay submitted=${submitted.size} remaining=$remaining")
    }

    fun submitQuestion(question: String) {
        val uid = auth.currentUser?.uid ?: return
        if (question.isBlank()) return

        // ── Moderation gate ───────────────────────────────────────────────────
        val modResult = ContentModerator.check(question)
        if (!modResult.isSafe) {
            violationCount++
            viewModelScope.launch {
                // 7️⃣ Unified strike system
                val banStatus = StrikeManager.addStrike(uid)
                val warning = when (banStatus) {
                    is BanStatus.PermaBanned ->
                        "🚫 Your account has been permanently banned for repeated violations."
                    is BanStatus.TempBanned  ->
                        "⏸️ You have been temporarily banned for 24 hours due to repeated violations."
                    is BanStatus.Warned      -> banStatus.message
                    else -> modResult.warningMessage
                            ?: "⚠️ Personal, sexual, contact or location questions are not allowed. Violations may result in a ban."
                }
                _moderationWarning.value = warning
                kotlinx.coroutines.delay(6_000L)  // auto-dismiss after 6 seconds
                _moderationWarning.value = null
            }
            Log.w(TAG, "Question blocked by moderation [${modResult.category}]: $question")
            return   // ← Do NOT write to Firestore
        }
        // Clear any previous warning on a clean submit
        _moderationWarning.value = null

        viewModelScope.launch {
            _state.value = QuestionUiState.Submitting
            try {
                val todayKey = todayKey()
                // Use uid + todayKey + nonce (count of existing) to allow multiple docs per day
                val existingCount = db.collection("groups").document(groupId)
                    .collection("daily_questions")
                    .whereEqualTo("uid", uid)
                    .whereEqualTo("day", todayKey)
                    .get().await().size()

                val docId = "${uid}_${todayKey}_$existingCount"
                val data  = mapOf(
                    "uid"         to uid,
                    "question"    to question.trim(),
                    "submittedAt" to Timestamp.now(),
                    "day"         to todayKey,
                    "answered"    to false
                )
                db.collection("groups").document(groupId)
                    .collection("daily_questions")
                    .document(docId)
                    .set(data).await()

                Log.d(TAG, "Question submitted (#${existingCount + 1}): $question")
                _state.value = QuestionUiState.Submitted

                // After brief "submitted" moment, return to picking if slots remain
                kotlinx.coroutines.delay(1_200L)
                refreshPickState(uid)

            } catch (e: Exception) {
                Log.e(TAG, "submitQuestion failed", e)
                _state.value = QuestionUiState.Error(e.localizedMessage ?: "Failed to submit.")
            }
        }
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}_${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }
}
