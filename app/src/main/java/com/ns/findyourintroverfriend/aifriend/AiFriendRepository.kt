package com.ns.findyourintroverfriend.aifriend

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
//  VEIL Friends — Repository
//  All AI calls go through the Cloud Function (never directly to Gemini).
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "AiFriendRepo"

// ⚠️  Update this URL after you deploy the Cloud Function.
//     Run: firebase deploy --only functions
//     Then copy the URL from the console output.
private const val CLOUD_FUNCTION_URL =
    "https://asia-south1-findyourintroverfriend-2cc9c.cloudfunctions.net/aiFriendChat"

class AiFriendRepository {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val uid get() = auth.currentUser?.uid ?: ""

    // ── Send message → Cloud Function → Gemini ────────────────────────────
    suspend fun sendMessage(
        friendId       : String,
        conversationId : String?,
        message        : String
    ): Result<Pair<String, String>> {   // Pair<reply, conversationId>
        return try {
            val payload = JSONObject().apply {
                put("uid",            uid)
                put("friendId",       friendId)
                if (!conversationId.isNullOrBlank() && conversationId != "new") {
                    put("conversationId", conversationId)
                }
                put("message", message)
            }

            val body = payload.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(CLOUD_FUNCTION_URL)
                .post(body)
                .build()

            val response = http.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "CF error ${response.code}: $responseBody")
                return Result.failure(Exception("Server error: ${response.code}"))
            }

            val json   = JSONObject(responseBody)
            val reply  = json.getString("reply")
            val convId = json.getString("conversationId")
            Result.success(Pair(reply, convId))

        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed", e)
            Result.failure(e)
        }
    }

    // ── Stream messages for a conversation ───────────────────────────────
    fun streamMessages(conversationId: String): Flow<List<AiMessage>> = callbackFlow {
        val ref = db.collection("users/$uid/ai_conversations/$conversationId/messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val listener = ref.addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val msgs = snap?.documents?.map { d ->
                AiMessage(
                    id        = d.id,
                    sender    = d.getString("sender") ?: "user",
                    content   = d.getString("content") ?: "",
                    createdAt = d.getDate("createdAt")?.time ?: 0L,
                )
            } ?: emptyList()
            trySend(msgs)
        }
        awaitClose { listener.remove() }
    }

    // ── Get all conversations for a friend ────────────────────────────────
    suspend fun getConversations(friendId: String): List<AiConversation> {
        return try {
            val snap = db.collection("users/$uid/ai_conversations")
                .whereEqualTo("friendId", friendId)
                .whereEqualTo("archived", false)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .get().await()

            snap.documents.map { d ->
                AiConversation(
                    id            = d.id,
                    friendId      = d.getString("friendId") ?: "",
                    title         = d.getString("title") ?: "",
                    summary       = d.getString("summary") ?: "",
                    lastMessageAt = d.getDate("lastMessageAt")?.time ?: 0L,
                    messageCount  = d.getLong("messageCount")?.toInt() ?: 0,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConversations failed", e); emptyList()
        }
    }

    // ── Get latest conversation for a friend (for resume flow) ───────────
    suspend fun getLatestConversation(friendId: String): AiConversation? {
        return getConversations(friendId).firstOrNull()
    }

    // ── Get memories for a friend ─────────────────────────────────────────
    suspend fun getMemories(friendId: String): List<AiMemory> {
        return try {
            val snap = db.collection("users/$uid/ai_memories")
                .whereEqualTo("friendId", friendId)
                .whereEqualTo("isDeleted", false)
                .orderBy("importance", Query.Direction.DESCENDING)
                .get().await()
            snap.documents.map { d ->
                AiMemory(
                    id         = d.id,
                    memoryType = d.getString("memoryType") ?: "general",
                    memoryText = d.getString("memoryText") ?: "",
                    importance = d.getDouble("importance") ?: 0.5,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMemories failed", e); emptyList()
        }
    }

    // ── Delete a specific memory ──────────────────────────────────────────
    suspend fun deleteMemory(memoryId: String): Boolean {
        return try {
            db.collection("users/$uid/ai_memories").document(memoryId)
                .update("isDeleted", true).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteMemory failed", e); false
        }
    }

    // ── Delete all memories for a friend ─────────────────────────────────
    suspend fun deleteAllMemories(friendId: String): Boolean {
        return try {
            val snap = db.collection("users/$uid/ai_memories")
                .whereEqualTo("friendId", friendId).get().await()
            val batch = db.batch()
            snap.documents.forEach { batch.update(it.reference, "isDeleted", true) }
            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteAllMemories failed", e); false
        }
    }
}
