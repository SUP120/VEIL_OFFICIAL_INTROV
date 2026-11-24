package com.ns.findyourintroverfriend.aifriend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

internal data class AiMessagePage(
    val messages: List<AiMessage>,
    val oldest: DocumentSnapshot?,
    val hasMore: Boolean
)

internal class AiFriendRepository {
    private val database = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val callable = FirebaseFunctions.getInstance("asia-south1")
        .getHttpsCallable("aiFriendChat")

    private fun user() = database.collection("users").document(
        requireNotNull(auth.currentUser?.uid) { "Sign in to use VEIL Friends." }
    )

    suspend fun usage(): AiUsage = call(mapOf("action" to "status"), timeoutSeconds = 10).toUsage()

    suspend fun reportReply(conversationId: String, messageId: String, category: String) {
        call(mapOf(
            "action" to "report",
            "conversationId" to conversationId,
            "messageId" to messageId,
            "category" to category
        ))
    }

    suspend fun send(
        friendId: String,
        conversationId: String?,
        requestId: String,
        message: String,
        allowPaid: Boolean
    ): AiReply {
        val response = call(mapOf(
            "action" to "send",
            "friendId" to friendId,
            "conversationId" to conversationId,
            "requestId" to requestId,
            "message" to message,
            "allowPaid" to allowPaid
        ))
        val messages = (response["messages"] as List<*>).map {
            val entry = it as Map<*, *>
            AiMessage(
                id = entry["id"] as String,
                sender = entry["sender"] as String,
                content = entry["content"] as String,
                createdAt = (entry["createdAt"] as Number).toLong()
            )
        }
        return AiReply(
            conversationId = response["conversationId"] as String,
            messages = messages,
            usage = (response["usage"] as Map<*, *>).toUsage()
        )
    }

    suspend fun latestConversation(friendId: String): String? =
        user().collection("ai_conversations").whereEqualTo("friendId", friendId).get().await()
            .documents.filter { it.getBoolean("archived") != true }
            .maxByOrNull { it.getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L }?.id

    fun streamMessages(conversationId: String): Flow<AiMessagePage> = callbackFlow {
        val listener = messageQuery(conversationId).limit(PAGE_SIZE).addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) trySend(snapshot.documents.toPage())
        }
        awaitClose { listener.remove() }
    }

    suspend fun olderMessages(conversationId: String, oldest: DocumentSnapshot): AiMessagePage =
        messageQuery(conversationId).startAfter(oldest).limit(PAGE_SIZE).get().await().documents.toPage()

    suspend fun memories(friendId: String): List<AiMemory> =
        user().collection("ai_memories").whereEqualTo("friendId", friendId).get().await().documents
            .filter { it.getBoolean("isDeleted") != true }
            .sortedByDescending { it.getDouble("importance") ?: 0.0 }
            .mapNotNull { doc -> doc.getString("memoryText")?.let { AiMemory(doc.id, it) } }

    suspend fun forgetMemories(ids: List<String>) {
        val collection = user().collection("ai_memories")
        ids.chunked(400).forEach { chunk ->
            val batch = database.batch()
            chunk.forEach { batch.update(collection.document(it), "isDeleted", true) }
            batch.commit().await()
        }
    }

    private fun messageQuery(conversationId: String) = user().collection("ai_conversations")
        .document(conversationId).collection("messages").orderBy("createdAt", Query.Direction.DESCENDING)

    private fun List<DocumentSnapshot>.toPage() = AiMessagePage(
        messages = map { doc ->
            AiMessage(
                id = doc.id,
                sender = doc.getString("sender").orEmpty(),
                content = doc.getString("content").orEmpty(),
                createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
            )
        },
        oldest = lastOrNull(),
        hasMore = size.toLong() == PAGE_SIZE
    )

    private suspend fun call(input: Map<String, Any?>, timeoutSeconds: Long = 55): Map<*, *> =
        callable.withTimeout(timeoutSeconds, TimeUnit.SECONDS).call(input).await().getData() as? Map<*, *>
            ?: error("Invalid chat response.")

    private companion object {
        const val PAGE_SIZE = 40L
    }
}

internal fun Map<*, *>.toUsage() = AiUsage(
    freeLimit = (get("freeLimit") as Number).toInt(),
    freeRemaining = (get("freeRemaining") as Number).toInt(),
    cookieCost = (get("cookieCost") as Number).toInt(),
    cookieBalance = (get("cookieBalance") as Number).toInt()
)
