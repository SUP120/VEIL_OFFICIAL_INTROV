package com.ns.findyourintroverfriend.myth

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

private const val TAG = "TOMvm"

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

data class MythCard(
    val targetUid    : String,
    val emoji        : String,
    val fact         : String,          // the statement about this person
    val isTrue       : Boolean,         // the real answer (loaded after guessing)
    val myGuess      : Boolean?,        // null = not guessed yet
    val isPlaceholder: Boolean = false  // true when member hasn't set their fact yet
)

/** How the circle guessed YOUR fact */
data class MyFactStats(
    val myFact     : String,
    val isTrue     : Boolean,   // the real answer
    val correctCount : Int,
    val wrongCount   : Int,
    val totalGuesses : Int
)

data class TomUiState(
    val cards        : List<MythCard>    = emptyList(),
    val loading      : Boolean           = true,
    val error        : String?           = null,
    /** uid → correct(true)/wrong(false) — filled as Firestore confirms */
    val results      : Map<String, Boolean> = emptyMap(),
    /** Stats about how the circle guessed the CURRENT USER’s own fact */
    val myFactStats  : MyFactStats?      = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class TrueOrMythViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(TomUiState())
    val state: StateFlow<TomUiState> = _state.asStateFlow()

    private var groupId = ""
    private var myUid   = ""

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

                // Load all circle members
                val groupDoc = db.collection("groups").document(groupId).get().await()
                @Suppress("UNCHECKED_CAST")
                val memberUids = (groupDoc.get("members") as? List<String>) ?: emptyList()

                // For each other member, load their fact + check if I already guessed
                val cards = mutableListOf<MythCard>()
                for (targetUid in memberUids) {
                    if (targetUid == uid) continue

                    val memberDoc = db.collection("groups").document(groupId)
                        .collection("members").document(targetUid).get().await()

                    val fact   = memberDoc.getString("tomFact")
                    val isTrue = memberDoc.getBoolean("tomIsTrue") ?: true
                    val emoji  = memberDoc.getString("emoji") ?: "🌑"

                    if (fact.isNullOrBlank()) {
                        // Member hasn't posted their fact yet — show placeholder
                        cards.add(MythCard(
                            targetUid     = targetUid,
                            emoji         = emoji,
                            fact          = "",
                            isTrue        = false,
                            myGuess       = null,
                            isPlaceholder = true
                        ))
                        continue
                    }

                    // Did I already guess for this person?
                    val guessDoc = db.collection("groups").document(groupId)
                        .collection("tom_guesses").document(uid)
                        .collection("guesses").document(targetUid).get().await()

                    val myGuess: Boolean? = if (guessDoc.exists())
                        guessDoc.getBoolean("guess")
                    else null

                    cards.add(MythCard(
                        targetUid = targetUid,
                        emoji     = emoji,
                        fact      = fact,
                        isTrue    = isTrue,
                        myGuess   = myGuess
                    ))
                }

                // Pre-fill results for already-guessed cards
                val preResults = cards
                    .filter { it.myGuess != null }
                    .associate { it.targetUid to (it.myGuess == it.isTrue) }

                // ── Load how the circle guessed MY fact ───────────────────────────────────
                val myMemberDoc = db.collection("groups").document(groupId)
                    .collection("members").document(uid).get().await()
                val myFact   = myMemberDoc.getString("tomFact")
                val myIsTrue = myMemberDoc.getBoolean("tomIsTrue") ?: true

                val myFactStats: MyFactStats? = if (!myFact.isNullOrBlank()) {
                    var correct = 0; var wrong = 0
                    for (otherUid in memberUids) {
                        if (otherUid == uid) continue
                        try {
                            val gDoc = db.collection("groups").document(groupId)
                                .collection("tom_guesses").document(otherUid)
                                .collection("guesses").document(uid).get().await()
                            if (gDoc.exists()) {
                                if (gDoc.getBoolean("correct") == true) correct++ else wrong++
                            }
                        } catch (_: Exception) {}
                    }
                    MyFactStats(
                        myFact       = myFact,
                        isTrue       = myIsTrue,
                        correctCount = correct,
                        wrongCount   = wrong,
                        totalGuesses = correct + wrong
                    )
                } else null

                _state.value = TomUiState(
                    cards       = cards,
                    loading     = false,
                    results     = preResults,
                    myFactStats = myFactStats
                )
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.value = _state.value.copy(loading = false, error = e.localizedMessage)
            }
        }
    }

    /** Submit a True/False guess for a specific circle member */
    fun guess(targetUid: String, guessTrue: Boolean) {
        val card = _state.value.cards.find { it.targetUid == targetUid }
        if (card == null || card.myGuess != null) return   // already answered

        // Optimistic update — show guess
        _state.value = _state.value.copy(
            cards = _state.value.cards.map {
                if (it.targetUid == targetUid) it.copy(myGuess = guessTrue) else it
            }
        )

        viewModelScope.launch {
            try {
                val correct = guessTrue == card.isTrue

                // Write guess to Firestore
                db.collection("groups").document(groupId)
                    .collection("tom_guesses").document(myUid)
                    .collection("guesses").document(targetUid)
                    .set(mapOf(
                        "guess"     to guessTrue,
                        "correct"   to correct,
                        "guessedAt" to Timestamp.now()
                    )).await()

                // Update result map
                _state.value = _state.value.copy(
                    results = _state.value.results + (targetUid to correct)
                )
                Log.d(TAG, "Guessed $guessTrue for $targetUid → correct=$correct")
            } catch (e: Exception) {
                Log.e(TAG, "guess failed", e)
                // Revert optimistic update
                _state.value = _state.value.copy(
                    cards = _state.value.cards.map {
                        if (it.targetUid == targetUid) it.copy(myGuess = null) else it
                    }
                )
            }
        }
    }
}
