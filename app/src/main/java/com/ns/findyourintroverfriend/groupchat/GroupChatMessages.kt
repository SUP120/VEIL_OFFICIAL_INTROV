package com.ns.findyourintroverfriend.groupchat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.BuildConfig
import com.ns.findyourintroverfriend.util.ContentModerator
import kotlinx.coroutines.tasks.await

internal const val GROUP_MESSAGE_LIMIT = 2000
internal val groupDocumentId = Regex("[A-Za-z0-9_-]{1,128}")
internal val groupReactions = listOf("❤️", "😂", "🙌", "🤍", "✨", "👍")

internal object GroupChatMessages {
    suspend fun send(roomId: String, uid: String, messageId: String, text: String, replyToId: String? = null) {
        require(groupDocumentId.matches(roomId) && groupDocumentId.matches(messageId))
        require(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Sign in to reply to this group." }
        val cleanText = ContentModerator.maskPhoneNumbers(text.trim())
        require(cleanText.isNotEmpty() && cleanText.length <= GROUP_MESSAGE_LIMIT) {
            "Write a message of up to $GROUP_MESSAGE_LIMIT characters."
        }
        require(ContentModerator.check(cleanText).isSafe) { "This message doesn't follow VEIL's community guidelines." }

        val db = FirebaseFirestore.getInstance()
        val alias = db.collection("users").document(uid).get().await()
            .getString("alias").orEmpty().trim().take(80).ifBlank { "VEIL member" }
        val room = db.collection("group_rooms").document(roomId)
        val message = room.collection("messages").document(messageId)
        db.runTransaction { transaction ->
            val group = transaction.get(room)
            val members = group.get("memberUids") as? List<*>
            check(members?.contains(uid) == true) { "Join this group to send messages." }
            val existing = transaction.get(message)
            if (!existing.exists()) {
                val data = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "alias" to alias,
                    "text" to cleanText,
                    "sentAt" to FieldValue.serverTimestamp(),
                    "pushViaRelay" to BuildConfig.GROUP_PUSH_RELAY_URL.isNotBlank()
                )
                if (replyToId != null && groupDocumentId.matches(replyToId)) {
                    val original = transaction.get(room.collection("messages").document(replyToId))
                    if (original.exists()) {
                        data["replyToId"] = original.id
                        data["replyToAlias"] = original.getString("alias").orEmpty().take(80)
                        data["replyToText"] = original.getString("text").orEmpty().take(120)
                    }
                }
                transaction.set(message, data)
                transaction.update(room, mapOf(
                    "lastMsg" to if (group.getString("joinMode") == "approve") "New message" else cleanText.take(60),
                    "lastMsgAt" to FieldValue.serverTimestamp(),
                    "lastMsgUid" to uid
                ))
            } else {
                check(existing.getString("uid") == uid)
            }
        }.await()
    }
}
