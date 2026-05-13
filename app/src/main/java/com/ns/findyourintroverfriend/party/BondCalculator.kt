package com.ns.findyourintroverfriend.party

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.reveal.RevealMember
import kotlinx.coroutines.tasks.await

private const val TAG = "BondCalc"

// ─────────────────────────────────────────────────────────────────────────────
//  Result model
// ─────────────────────────────────────────────────────────────────────────────

data class BondResult(
    val winnerA             : RevealMember,
    val winnerB             : RevealMember,
    val otherPair           : List<RevealMember>,
    val bondScore           : Int,
    val sessionId           : String,
    val isCurrentUserWinner : Boolean
)

// ─────────────────────────────────────────────────────────────────────────────
//  Scoring table (per pair, over all 3 days)
// ─────────────────────────────────────────────────────────────────────────────
//
//  [Questions — Day 1/2/3]
//    A answered B's question                     → +3 per question
//    B reacted to A's answer (emoji reaction)    → +2 per reaction
//    BOTH A & B answered the SAME question        → +2 mutual bonus (connection)
//
//  [Soft Reactions — Day 1 profile vibes]
//    A/B sent a soft reaction to the other's profile → +1 each direction
//
//  [Echoes — Day 1 "I feel this" on answers]
//    A/B echoed the other's answer                → +2 each direction
//
//  [Tic-Tac-Toe — Day 2]
//    Game invite sent between this pair           → +1
//    Game actually played (accepted)              → +2
//
//  [Would You Rather — Day 2]
//    Same pick on a WYR question                  → +2 per matching answer
//
//  [True or Myth — Day 3]
//    Correct cross-guess on the other's statement → +1 each direction
//
//  Winner = highest-scoring pair.
//  Gender preference = opposite-gender pair picked first.
//  Tie-break = same score → keep the pair that includes the current user (personal best).
// ─────────────────────────────────────────────────────────────────────────────

object BondCalculator {

    suspend fun findBestPair(
        db         : FirebaseFirestore,
        groupId    : String,
        members    : List<RevealMember>,
        currentUid : String
    ): BondResult? {
        if (members.size < 2) return null

        // ── Pre-fetch shared collections ONCE (not per pair) ─────────────────
        val qDocs = try {
            db.collection("groups").document(groupId)
                .collection("daily_questions").get().await().documents
        } catch (e: Exception) {
            Log.w(TAG, "daily_questions fetch failed: ${e.message}")
            emptyList()
        }

        val wyrDocs = try {
            db.collection("groups").document(groupId)
                .collection("wyr_picks").get().await().documents
        } catch (e: Exception) {
            Log.w(TAG, "wyr_picks fetch failed: ${e.message}")
            emptyList()
        }

        // ── Accumulate scores for every unordered pair ───────────────────────
        val scores = mutableMapOf<Pair<String, String>, Int>()

        for (i in members.indices) {
            for (j in (i + 1) until members.size) {
                val uidA = members[i].uid
                val uidB = members[j].uid
                var score = 0

                // ── Questions + Answer reactions + Mutual-answer bonus ────────
                for (qDoc in qDocs) {
                    val author = qDoc.getString("uid") ?: continue

                    // Fetch answer docs for both A and B on this question
                    val aAnsDoc = try {
                        qDoc.reference.collection("answers").document(uidA).get().await()
                    } catch (e: Exception) { null }
                    val bAnsDoc = try {
                        qDoc.reference.collection("answers").document(uidB).get().await()
                    } catch (e: Exception) { null }

                    val aAnswered = aAnsDoc?.exists() == true
                    val bAnswered = bAnsDoc?.exists() == true

                    when (author) {
                        uidB -> {
                            // B asked this question — A answering it shows engagement with B
                            if (aAnswered) {
                                score += 3
                                // B reacted to A's answer?
                                if (aAnsDoc?.getString("myReaction_$uidB") != null) score += 2
                            }
                        }
                        uidA -> {
                            // A asked this question — B answering it shows engagement with A
                            if (bAnswered) {
                                score += 3
                                // A reacted to B's answer?
                                if (bAnsDoc?.getString("myReaction_$uidA") != null) score += 2
                            }
                        }
                        else -> {
                            // Question was asked by a THIRD member.
                            // If BOTH A and B answered the same question, they engaged with the
                            // same content — give a mutual bonus (+2) as this signals alignment.
                            if (aAnswered && bAnswered) {
                                score += 2
                            }
                        }
                    }
                }

                // ── Soft Reactions (profile vibes) ────────────────────────────
                score += scoreIfExists(db, "groups/$groupId/reactions/$uidA/sources/$uidB", 1)
                score += scoreIfExists(db, "groups/$groupId/reactions/$uidB/sources/$uidA", 1)

                // ── Echoes ─────────────────────────────────────────────────────
                score += scoreIfExists(db, "groups/$groupId/echoes/$uidA/sources/$uidB", 2)
                score += scoreIfExists(db, "groups/$groupId/echoes/$uidB/sources/$uidA", 2)

                // ── Tic-Tac-Toe (Day 2) ────────────────────────────────────────
                try {
                    val inviteSnap = db.collection("groups").document(groupId)
                        .collection("game_invites")
                        .whereIn("fromUid", listOf(uidA, uidB))
                        .get().await()
                    for (invDoc in inviteSnap.documents) {
                        val from   = invDoc.getString("fromUid") ?: continue
                        val to     = invDoc.getString("toUid")   ?: continue
                        val status = invDoc.getString("status")  ?: continue
                        val bothInPair = (from == uidA && to == uidB) || (from == uidB && to == uidA)
                        if (!bothInPair) continue
                        score += 1                                   // invite sent
                        if (status == "accepted") score += 2        // game played
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "TicTacToe scoring skipped: ${e.message}")
                }

                // ── Would You Rather (Day 2) — same pick = +2 ─────────────────
                for (wyrDoc in wyrDocs) {
                    try {
                        val aPickDoc = wyrDoc.reference.collection("answers").document(uidA).get().await()
                        val bPickDoc = wyrDoc.reference.collection("answers").document(uidB).get().await()
                        val aPick    = aPickDoc.getString("pick")
                        val bPick    = bPickDoc.getString("pick")
                        if (aPick != null && bPick != null && aPick == bPick) score += 2
                    } catch (e: Exception) {
                        Log.w(TAG, "WYR pair scoring skipped: ${e.message}")
                    }
                }

                // ── True or Myth (Day 3) — correct cross-guess = +1 each ──────
                try {
                    val aGuessOnB = db.collection("groups").document(groupId)
                        .collection("tom_guesses").document(uidA)
                        .collection("guesses").document(uidB).get().await()
                    if (aGuessOnB.getBoolean("correct") == true) score += 1

                    val bGuessOnA = db.collection("groups").document(groupId)
                        .collection("tom_guesses").document(uidB)
                        .collection("guesses").document(uidA).get().await()
                    if (bGuessOnA.getBoolean("correct") == true) score += 1
                } catch (e: Exception) {
                    Log.w(TAG, "TOM scoring skipped: ${e.message}")
                }

                scores[Pair(uidA, uidB)] = score
                Log.d(TAG, "Bond $uidA <-> $uidB = $score")
            }
        }

        // ── Pick the winner ───────────────────────────────────────────────────
        // Strategy:
        //   1. Prefer opposite-gender pairs (highest score among them).
        //   2. Tie-break: if tied, prefer the pair that includes currentUser.
        //   3. Fall-back: if no opposite-gender pairs → any pair (all-same group edge case).
        val genderOf = members.associate { it.uid to it.gender.lowercase().trim() }

        fun isOpposite(pair: Pair<String, String>): Boolean {
            val gA = genderOf[pair.first]  ?: ""
            val gB = genderOf[pair.second] ?: ""
            return gA.isNotBlank() && gB.isNotBlank() && gA != gB
        }

        val oppositeScores = scores.filter { (pair, _) -> isOpposite(pair) }
        val candidatePool  = oppositeScores.ifEmpty { scores }

        // Find the max score; among ties prefer pair containing currentUser
        val maxScore   = candidatePool.values.maxOrNull() ?: return null
        val bestEntry  = candidatePool
            .filter { it.value == maxScore }
            .maxByOrNull { (pair, _) ->
                if (pair.first == currentUid || pair.second == currentUid) 1 else 0
            } ?: return null

        val (aUid, bUid) = bestEntry.key
        val winnerA   = members.firstOrNull { it.uid == aUid } ?: return null
        val winnerB   = members.firstOrNull { it.uid == bUid } ?: return null
        val otherPair = members.filter { it.uid != aUid && it.uid != bUid }

        // Deterministic session ID (alphabetically sorted so both users share same doc)
        val sessionId = listOf(aUid, bUid).sorted().joinToString("_")

        // Persist party session (idempotent — set() overwrites safely)
        db.collection("party_sessions").document(sessionId).set(
            mapOf(
                "user1"     to aUid,
                "user2"     to bUid,
                "groupId"   to groupId,
                "bondScore" to bestEntry.value,
                "createdAt" to Timestamp.now()
            )
        ).await()

        Log.d(TAG, "Best pair: $aUid <-> $bUid score=${bestEntry.value} session=$sessionId")

        return BondResult(
            winnerA             = winnerA,
            winnerB             = winnerB,
            otherPair           = otherPair,
            bondScore           = bestEntry.value,
            sessionId           = sessionId,
            isCurrentUserWinner = currentUid == aUid || currentUid == bUid
        )
    }

    /** Returns [pts] if a Firestore document exists at the given slash-separated path, else 0. */
    private suspend fun scoreIfExists(
        db    : FirebaseFirestore,
        path  : String,
        pts   : Int
    ): Int {
        return try {
            val parts = path.split("/")
            var ref = db.collection(parts[0]).document(parts[1])
            var i = 2
            while (i < parts.size - 1) {
                ref = ref.collection(parts[i]).document(parts[i + 1])
                i += 2
            }
            if (ref.get().await().exists()) pts else 0
        } catch (e: Exception) {
            Log.w(TAG, "scoreIfExists failed for $path: ${e.message}")
            0
        }
    }
}
