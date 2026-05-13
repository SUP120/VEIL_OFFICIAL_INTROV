package com.ns.findyourintroverfriend.dev

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  DevBotManager — DEVELOPER ONLY
//  Enhanced bot system for testing all screens without real users
//
//  Features:
//  1. Add bots to queue (1-10 bots)
//  2. Form instant group with bots
//  3. Bots can answer questions, play games, send messages
//  4. Clear all bots from queue
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "DevBots"

object DevBotManager {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Expanded bot pool for variety
    private val BOT_POOL = listOf(
        BotProfile("bot_m_001", "Atlas", "Male", "🌊", listOf("Deep thinker", "Observer")),
        BotProfile("bot_m_002", "Orion", "Male", "⭐", listOf("Creative", "Calm")),
        BotProfile("bot_m_003", "Kai", "Male", "🎭", listOf("Playful", "Curious")),
        BotProfile("bot_m_004", "Zane", "Male", "🔥", listOf("Energetic", "Bold")),
        BotProfile("bot_m_005", "River", "Male", "🌿", listOf("Peaceful", "Wise")),
        BotProfile("bot_f_001", "Nova", "Female", "🌙", listOf("Empath", "Loyal")),
        BotProfile("bot_f_002", "Lyra", "Female", "✨", listOf("Observer", "Thoughtful")),
        BotProfile("bot_f_003", "Luna", "Female", "🦋", listOf("Dreamer", "Kind")),
        BotProfile("bot_f_004", "Aria", "Female", "🎨", listOf("Creative", "Warm")),
        BotProfile("bot_f_005", "Sage", "Female", "🌸", listOf("Gentle", "Intuitive"))
    )

    /**
     * Add N bots to the matchmaking queue
     * @param count Number of bots to add (1-10)
     * @return Number of bots actually added
     */
    suspend fun addBotsToQueue(count: Int): Result<Int> = runCatching {
        val actualCount = count.coerceIn(1, 10)
        
        // Debug: Log current user info
        val currentUser = auth.currentUser
        Log.d(TAG, "=== BOT CREATION DEBUG ===")
        Log.d(TAG, "Current user UID: ${currentUser?.uid}")
        Log.d(TAG, "Current user email: ${currentUser?.email}")
        Log.d(TAG, "Attempting to add $actualCount bots")
        
        val batch = db.batch()
        
        // Get random bots from pool
        val selectedBots = BOT_POOL.shuffled().take(actualCount)
        
        selectedBots.forEach { bot ->
            Log.d(TAG, "Creating bot: ${bot.uid} (${bot.alias})")
            
            // Add to queue
            val qRef = db.collection("matchmaking_queue").document(bot.uid)
            batch.set(qRef, mapOf(
                "uid"      to bot.uid,
                "gender"   to bot.gender,
                "status"   to "waiting",
                "joinedAt" to Timestamp.now(),
                "isBot"    to true
            ))
            
            // Create user profile
            val uRef = db.collection("users").document(bot.uid)
            batch.set(uRef, mapOf(
                "alias"           to bot.alias,
                "gender"          to bot.gender,
                "emoji"           to bot.emoji,
                "personalities"   to bot.personality,
                "profileComplete" to true,
                "isBot"           to true,
                "photoUrl"        to ""
            ))
        }
        
        Log.d(TAG, "Committing batch write for $actualCount bots...")
        batch.commit().await()
        Log.d(TAG, "✅ Successfully added $actualCount bots to queue")
        actualCount
    }

    /**
     * Remove all bots from the queue
     */
    suspend fun clearBotsFromQueue(): Result<Int> = runCatching {
        val queueSnap = db.collection("matchmaking_queue")
            .whereEqualTo("isBot", true)
            .get().await()
        
        val batch = db.batch()
        queueSnap.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
        
        val count = queueSnap.size()
        Log.d(TAG, "Cleared $count bots from queue")
        count
    }

    /**
     * Get current bot count in queue
     */
    suspend fun getBotCountInQueue(): Result<Int> = runCatching {
        db.collection("matchmaking_queue")
            .whereEqualTo("isBot", true)
            .whereEqualTo("status", "waiting")
            .get().await().size()
    }

    /**
     * Form instant group with bots (original functionality)
     * Inserts 3 bots and immediately forms a group.
     */
    suspend fun injectBotsAndFormGroup(): Result<String> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw Exception("Not signed in.")

        // Get real user's gender
        val userDoc = db.collection("users").document(uid).get().await()
        val realGender = userDoc.getString("gender") ?: "Male"

        // Decide which bots to use (always 2M + 2F)
        val bots: List<BotProfile> = if (realGender == "Male") {
            listOf(BOT_POOL[1], BOT_POOL[5], BOT_POOL[6])   // 1M + 2F
        } else {
            listOf(BOT_POOL[0], BOT_POOL[1], BOT_POOL[6])   // 2M + 1F
        }

        val allUids = listOf(uid) + bots.map { it.uid }

        // 1. Write bot queue entries
        val batch = db.batch()
        bots.forEach { bot ->
            val qRef = db.collection("matchmaking_queue").document(bot.uid)
            batch.set(qRef, mapOf(
                "uid"      to bot.uid,
                "gender"   to bot.gender,
                "status"   to "waiting",
                "joinedAt" to Timestamp.now(),
                "isBot"    to true
            ))
        }

        // 2. Write bot user profiles
        bots.forEach { bot ->
            val uRef = db.collection("users").document(bot.uid)
            batch.set(uRef, mapOf(
                "alias"           to bot.alias,
                "gender"          to bot.gender,
                "emoji"           to bot.emoji,
                "personalities"   to bot.personality,
                "profileComplete" to true,
                "isBot"           to true,
                "photoUrl"        to ""
            ))
        }
        batch.commit().await()

        // 3. Create the group document
        val groupId   = "dev_group_${System.currentTimeMillis()}"
        val groupData = mapOf(
            "members"     to allUids,
            "maleCount"   to 2,
            "femaleCount" to 2,
            "status"      to "active",
            "createdAt"   to Timestamp.now(),
            "day"         to 1,
            "isDev"       to true
        )
        db.collection("groups").document(groupId).set(groupData).await()

        // 4. Assign groupId to all 4 members
        val batch2 = db.batch()
        allUids.forEach { memberId ->
            batch2.update(db.collection("users").document(memberId), "groupId", groupId)
        }
        // Also clean up queue entries
        allUids.forEach { memberId ->
            batch2.update(
                db.collection("matchmaking_queue").document(memberId),
                "status", "matched"
            )
        }
        batch2.commit().await()

        Log.d(TAG, "Dev group formed: $groupId with members $allUids")
        groupId
    }

    /**
     * Make bots answer a specific question in the group
     * @param groupId The group ID
     * @param questionDocId The question document ID
     */
    suspend fun makeBotsAnswerQuestion(groupId: String, questionDocId: String): Result<Int> = runCatching {
        val groupDoc = db.collection("groups").document(groupId).get().await()
        @Suppress("UNCHECKED_CAST")
        val members = groupDoc.get("members") as? List<String> ?: emptyList()
        
        val botMembers = members.filter { it.startsWith("bot_") }
        
        val answers = listOf(
            "I think deeply about this. It's fascinating how we connect.",
            "Honestly, I've always wondered about this too.",
            "This resonates with me on so many levels.",
            "I'd say it depends on the context, but generally yes.",
            "That's a great question. I feel like we're all searching for meaning.",
            "I've experienced something similar. It changed my perspective.",
            "This makes me think about what really matters in life.",
            "I'm still figuring this out, but I appreciate the question."
        )
        
        val batch = db.batch()
        botMembers.forEach { botUid ->
            val answerRef = db.collection("groups").document(groupId)
                .collection("daily_questions").document(questionDocId)
                .collection("answers").document(botUid)
            
            batch.set(answerRef, mapOf(
                "uid"  to botUid,
                "text" to answers.random(),
                "answeredAt" to Timestamp.now()
            ))
        }
        batch.commit().await()
        
        Log.d(TAG, "${botMembers.size} bots answered question $questionDocId")
        botMembers.size
    }

    /**
     * Make bots send chat messages
     * @param groupId The group ID
     * @param messageCount Number of messages per bot
     */
    suspend fun makeBotsSendChatMessages(groupId: String, messageCount: Int = 2): Result<Int> = runCatching {
        val groupDoc = db.collection("groups").document(groupId).get().await()
        @Suppress("UNCHECKED_CAST")
        val members = groupDoc.get("members") as? List<String> ?: emptyList()
        
        val botMembers = members.filter { it.startsWith("bot_") }
        
        val messages = listOf(
            "Hey everyone! This is interesting.",
            "I'm really enjoying getting to know you all.",
            "What do you think about this?",
            "That's a great point!",
            "I never thought about it that way.",
            "This conversation is so meaningful.",
            "I feel like we're really connecting here.",
            "Thanks for sharing that with us."
        )
        
        var totalSent = 0
        botMembers.forEach { botUid ->
            repeat(messageCount) {
                db.collection("groups").document(groupId)
                    .collection("chat")
                    .add(mapOf(
                        "uid"    to botUid,
                        "text"   to messages.random(),
                        "sentAt" to Timestamp.now()
                    )).await()
                totalSent++
            }
        }
        
        Log.d(TAG, "Bots sent $totalSent chat messages")
        totalSent
    }
}

private data class BotProfile(
    val uid         : String,
    val alias       : String,
    val gender      : String,
    val emoji       : String,
    val personality : List<String>
)
