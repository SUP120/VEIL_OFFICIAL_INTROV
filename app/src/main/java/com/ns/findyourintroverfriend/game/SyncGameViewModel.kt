package com.ns.findyourintroverfriend.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Questions bank (hardcoded = zero server reads) ────────────────────────────
val SYNC_QUESTIONS = listOf(
    SyncQuestion("🌅 Morning person or 🦉 Night owl?",         listOf("Morning person 🌅", "Night owl 🦉")),
    SyncQuestion("🍕 Pizza or 🍣 Sushi?",                       listOf("Pizza 🍕", "Sushi 🍣")),
    SyncQuestion("📚 Books or 🎬 Movies?",                       listOf("Books 📚", "Movies 🎬")),
    SyncQuestion("🏖️ Beach vacation or 🏔️ Mountain trip?",    listOf("Beach 🏖️", "Mountains 🏔️")),
    SyncQuestion("🤫 Introvert or 🎉 Extrovert?",              listOf("Introvert 🤫", "Extrovert 🎉")),
    SyncQuestion("😂 Funny or 🧠 Deep conversations?",          listOf("Funny 😂", "Deep 🧠")),
    SyncQuestion("☕ Coffee or 🍵 Tea?",                         listOf("Coffee ☕", "Tea 🍵")),
    SyncQuestion("🎮 Games or 🎨 Art?",                          listOf("Games 🎮", "Art 🎨")),
    SyncQuestion("🌧️ Rain or ☀️ Sunshine?",                    listOf("Rain 🌧️", "Sunshine ☀️")),
    SyncQuestion("🐶 Dog person or 🐱 Cat person?",             listOf("Dogs 🐶", "Cats 🐱")),
)

data class SyncQuestion(val text: String, val options: List<String>)

data class SyncGameState(
    val gameId       : String         = "",
    val round        : Int            = 0,        // 0-indexed, 0..9
    val myAnswer     : String?        = null,      // chosen this round, not yet submitted
    val phase        : SyncPhase      = SyncPhase.WAITING_PARTNER,
    val revealA      : String?        = null,
    val revealB      : String?        = null,
    val synced       : Boolean        = false,
    val score        : Int            = 0,         // how many rounds synced
    val totalRounds  : Int            = SYNC_QUESTIONS.size,
    val isPlayerA    : Boolean        = true,
    val partnerAlias : String         = "Partner",
    val loading      : Boolean        = true,
    val error        : String?        = null,
    val gameOver     : Boolean        = false,
    val finalScore   : Int            = 0,
)

enum class SyncPhase {
    CHOOSING,         // both pick an answer
    WAITING_PARTNER,  // I picked, waiting for partner
    REVEALING,        // both picked → show reveal
    NEXT_ROUND,       // brief pause before next question
}

class SyncGameViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""

    private val _state = MutableStateFlow(SyncGameState())
    val state: StateFlow<SyncGameState> = _state.asStateFlow()

    private var gameListener: ListenerRegistration? = null
    private var currentGameId = ""

    // ── Start or join a Sync game ─────────────────────────────────────────────
    fun startGame(matchId: String, partnerAlias: String) {
        currentGameId = "sync_$matchId"
        viewModelScope.launch {
            try {
                val ref = db.collection("syncGames").document(currentGameId)
                val doc = ref.get().await()

                val isA: Boolean
                if (!doc.exists()) {
                    // Create fresh game — I am player A
                    isA = true
                    ref.set(mapOf(
                        "playerA"     to uid,
                        "playerB"     to "",
                        "round"       to 0,
                        "answerA"     to "",
                        "answerB"     to "",
                        "score"       to 0,
                        "gameOver"    to false,
                        "createdAt"   to System.currentTimeMillis()
                    )).await()
                } else {
                    // Join existing game — I am player B
                    isA = doc.getString("playerA") == uid
                    if (!isA && doc.getString("playerB").isNullOrEmpty()) {
                        ref.update("playerB", uid).await()
                    }
                }

                _state.value = _state.value.copy(
                    gameId       = currentGameId,
                    isPlayerA    = isA,
                    partnerAlias = partnerAlias,
                    loading      = false,
                    phase        = SyncPhase.CHOOSING
                )
                listenToGame(isA)
            } catch (e: Exception) {
                Log.e("SyncGameVM", "startGame: ${e.message}")
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    // ── Listen to single game doc (1 listener, minimal reads) ────────────────
    private fun listenToGame(isA: Boolean) {
        gameListener?.remove()
        gameListener = db.collection("syncGames").document(currentGameId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener

                val round    = (snap.getLong("round") ?: 0).toInt()
                val answerA  = snap.getString("answerA") ?: ""
                val answerB  = snap.getString("answerB") ?: ""
                val score    = (snap.getLong("score") ?: 0).toInt()
                val gameOver = snap.getBoolean("gameOver") ?: false

                val bothAnswered = answerA.isNotEmpty() && answerB.isNotEmpty()
                val myAnswer    = if (isA) answerA else answerB
                val partnerAns  = if (isA) answerB else answerA

                val phase = when {
                    gameOver     -> SyncPhase.REVEALING
                    bothAnswered -> SyncPhase.REVEALING
                    myAnswer.isNotEmpty() -> SyncPhase.WAITING_PARTNER
                    else         -> SyncPhase.CHOOSING
                }

                val synced = bothAnswered && answerA == answerB

                _state.value = _state.value.copy(
                    round     = round,
                    phase     = phase,
                    revealA   = if (bothAnswered) answerA else null,
                    revealB   = if (bothAnswered) answerB else null,
                    synced    = synced,
                    score     = score,
                    gameOver  = gameOver,
                    finalScore = if (gameOver) score else _state.value.finalScore
                )
            }
    }

    // ── Player submits answer ─────────────────────────────────────────────────
    fun submitAnswer(answer: String) {
        val isA = _state.value.isPlayerA
        val field = if (isA) "answerA" else "answerB"
        _state.value = _state.value.copy(phase = SyncPhase.WAITING_PARTNER)
        viewModelScope.launch {
            try {
                db.collection("syncGames").document(currentGameId)
                    .update(field, answer).await()
            } catch (e: Exception) {
                Log.e("SyncGameVM", "submitAnswer: ${e.message}")
            }
        }
    }

    // ── Advance to next round (either player can trigger) ────────────────────
    fun nextRound() {
        val current = _state.value
        val nextRound = current.round + 1
        val newScore  = if (current.synced) current.score + 1 else current.score
        val isOver    = nextRound >= SYNC_QUESTIONS.size

        viewModelScope.launch {
            try {
                db.collection("syncGames").document(currentGameId)
                    .update(mapOf(
                        "round"    to nextRound,
                        "answerA"  to "",
                        "answerB"  to "",
                        "score"    to newScore,
                        "gameOver" to isOver
                    )).await()
            } catch (e: Exception) {
                Log.e("SyncGameVM", "nextRound: ${e.message}")
            }
        }
    }

    fun resetGame() {
        viewModelScope.launch {
            try {
                db.collection("syncGames").document(currentGameId)
                    .update(mapOf(
                        "round"    to 0,
                        "answerA"  to "",
                        "answerB"  to "",
                        "score"    to 0,
                        "gameOver" to false
                    )).await()
            } catch (e: Exception) { Log.e("SyncGameVM", "resetGame: ${e.message}") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameListener?.remove()
    }
}
