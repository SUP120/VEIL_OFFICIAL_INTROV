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

private const val TAG = "TicTacToeVM"

// ─────────────────────────────────────────────────────────────────────────────
//  Models
// ─────────────────────────────────────────────────────────────────────────────

/** 9-cell board: null = empty, "X" or "O" */
typealias Board = List<String?>

data class TicTacToeState(
    val board           : Board     = List(9) { null },
    val mySymbol        : String    = "X",          // "X" or "O"
    val currentTurn     : String    = "",            // uid whose turn it is
    val myUid           : String    = "",
    val opponentAlias   : String    = "Them",
    val opponentEmoji   : String    = "🌙",
    val winner          : String?   = null,          // uid or "draw"
    val gameOver        : Boolean   = false,
    val loading         : Boolean   = true,
    val error           : String?   = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class TicTacToeViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(TicTacToeState())
    val state: StateFlow<TicTacToeState> = _state.asStateFlow()

    private var groupId  = ""
    private var gameId   = ""
    private var listener : ListenerRegistration? = null

    /** Call once from the screen; gameId comes from the accepted invite doc. */
    fun init(gameIdParam: String) {
        val uid = auth.currentUser?.uid ?: return
        gameId  = gameIdParam
        _state.value = _state.value.copy(myUid = uid, loading = true)

        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                groupId = userDoc.getString("groupId") ?: ""

                val gameDoc = db.collection("groups").document(groupId)
                    .collection("ttt_games").document(gameId).get().await()

                val playerX = gameDoc.getString("playerX") ?: ""
                val playerO = gameDoc.getString("playerO") ?: ""
                val mySymbol = if (uid == playerX) "X" else "O"
                val oppUid   = if (uid == playerX) playerO else playerX

                val oppDoc   = db.collection("users").document(oppUid).get().await()
                val oppAlias = oppDoc.getString("alias") ?: "Them"
                val oppEmoji = oppDoc.getString("emoji") ?: "🌙"

                _state.value = _state.value.copy(
                    mySymbol      = mySymbol,
                    opponentAlias = oppAlias,
                    opponentEmoji = oppEmoji,
                    loading       = false
                )

                // Real-time listener on game doc
                listenToGame()

            } catch (e: Exception) {
                Log.e(TAG, "init failed", e)
                _state.value = _state.value.copy(loading = false, error = e.localizedMessage)
            }
        }
    }

    private fun listenToGame() {
        listener?.remove()
        listener = db.collection("groups").document(groupId)
            .collection("ttt_games").document(gameId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener

                @Suppress("UNCHECKED_CAST")
                val rawBoard = snap.get("board") as? List<String?> ?: List(9) { null }
                val turn     = snap.getString("currentTurn") ?: ""
                val winner   = snap.getString("winner")
                val gameOver = snap.getBoolean("gameOver") ?: false

                _state.value = _state.value.copy(
                    board       = rawBoard,
                    currentTurn = turn,
                    winner      = winner,
                    gameOver    = gameOver
                )
            }
    }

    fun makeMove(cellIndex: Int) {
        val s = _state.value
        if (s.gameOver) return
        if (s.currentTurn != s.myUid) return
        if (s.board[cellIndex] != null) return

        val newBoard = s.board.toMutableList().also { it[cellIndex] = s.mySymbol }
        val winnerSym = checkWinner(newBoard)
        val isDraw    = winnerSym == null && newBoard.none { it == null }

        val uid = auth.currentUser?.uid ?: return
        val gameRef = db.collection("groups").document(groupId)
            .collection("ttt_games").document(gameId)

        viewModelScope.launch {
            try {
                // Determine next turn: find opponent uid from game doc
                val gameDoc  = gameRef.get().await()
                val playerX  = gameDoc.getString("playerX") ?: ""
                val playerO  = gameDoc.getString("playerO") ?: ""
                val nextTurn = if (uid == playerX) playerO else playerX

                val updates = mutableMapOf<String, Any?>(
                    "board"       to newBoard,
                    "currentTurn" to nextTurn,
                    "updatedAt"   to Timestamp.now()
                )
                if (winnerSym != null) {
                    val winnerUid = if (winnerSym == "X") playerX else playerO
                    updates["winner"]   = winnerUid
                    updates["gameOver"] = true
                } else if (isDraw) {
                    updates["winner"]   = "draw"
                    updates["gameOver"] = true
                }
                gameRef.update(updates).await()
            } catch (e: Exception) {
                Log.e(TAG, "makeMove failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }

    // ── Win detection ─────────────────────────────────────────────────────────
    private val WIN_LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // rows
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // cols
        listOf(0, 4, 8), listOf(2, 4, 6)                   // diagonals
    )

    private fun checkWinner(board: Board): String? {
        for (line in WIN_LINES) {
            val (a, b, c) = line
            val sym = board[a]
            if (sym != null && sym == board[b] && sym == board[c]) return sym
        }
        return null
    }
}
