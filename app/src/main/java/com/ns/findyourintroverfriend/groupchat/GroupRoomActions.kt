package com.ns.findyourintroverfriend.groupchat

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

internal data class GroupPreferences(
    val notificationMode: String = "all",
    val lastReadAt: Timestamp? = null,
    val pinned: Boolean = false
)

internal fun DocumentSnapshot.groupPreferences() = GroupPreferences(
    notificationMode = getString("notificationMode") ?: "all",
    lastReadAt = getTimestamp("lastReadAt"),
    pinned = getBoolean("pinned") == true
)

internal fun FirebaseFirestore.groupPreferences(uid: String) =
    collection("users").document(uid).collection("group_chat_state")

internal suspend fun FirebaseFirestore.changeGroupMembership(
    roomId: String,
    actor: String,
    action: GroupMembership,
    target: String = actor
) {
    require(groupDocumentId.matches(roomId) && actor.isNotBlank() && target.isNotBlank())
    val room = collection("group_rooms").document(roomId)
    runTransaction { transaction ->
        val data = transaction.get(room)
        check(data.exists()) { "This group is no longer available." }
        val members = (data.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val pending = (data.get("pendingUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val owner = data.getString("createdBy") == actor
        val mode = data.getString("joinMode") ?: "auto"

        val updatedMembers = when (action) {
            GroupMembership.Join -> {
                check(mode == "auto") { "This group requires approval." }
                (members + actor).distinct()
            }
            GroupMembership.Approve -> {
                check(owner && target in pending) { "This join request was already handled." }
                (members + target).distinct()
            }
            GroupMembership.Leave, GroupMembership.Remove -> {
                check(if (action == GroupMembership.Remove) owner && target != actor else !owner) {
                    "The group creator can't leave the group."
                }
                members.filterNot { it == target }.distinct()
            }
            else -> members
        }
        val updatedPending = when (action) {
            GroupMembership.Request -> {
                check(mode == "approve" && actor !in members) { "This group is already available to you." }
                (pending + actor).distinct()
            }
            GroupMembership.Approve, GroupMembership.Deny, GroupMembership.CancelRequest -> pending.filterNot { it == target }.distinct()
            GroupMembership.Join -> pending.filterNot { it == actor }.distinct()
            else -> pending
        }
        val values = mutableMapOf<String, Any>()
        if (updatedMembers != members) {
            values["memberUids"] = updatedMembers
            values["memberCount"] = updatedMembers.size
        }
        if (updatedPending != pending) values["pendingUids"] = updatedPending
        if (values.isNotEmpty()) transaction.update(room, values)
    }.await()
}

internal enum class GroupMembership { Join, Request, Approve, Deny, Leave, Remove, CancelRequest }

internal fun DocumentSnapshot.toGroupRoom(): GroupRoom? {
    val name = getString("name") ?: return null
    return GroupRoom(
        id = id,
        name = name,
        description = getString("description").orEmpty(),
        createdBy = getString("createdBy").orEmpty(),
        createdAt = getTimestamp("createdAt") ?: Timestamp(0, 0),
        memberCount = (getLong("memberCount") ?: 0).toInt(),
        memberUids = (get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        pendingUids = (get("pendingUids") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        isSystem = getBoolean("isSystem") == true,
        joinMode = getString("joinMode") ?: "auto",
        lastMsg = getString("lastMsg").orEmpty(),
        lastMsgAt = getTimestamp("lastMsgAt"),
        lastMsgUid = getString("lastMsgUid").orEmpty(),
        announcement = getString("announcement").orEmpty()
    )
}

internal fun DocumentSnapshot.toGroupMessage(): GroupMessage? {
    val sender = getString("uid") ?: return null
    val sent = getTimestamp("sentAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return GroupMessage(
        id = id,
        uid = sender,
        alias = getString("alias").orEmpty().ifBlank { "VEIL member" },
        text = getString("text").orEmpty(),
        sentAt = sent,
        replyToId = getString("replyToId").orEmpty(),
        replyToAlias = getString("replyToAlias").orEmpty(),
        replyToText = getString("replyToText").orEmpty(),
        reactions = (get("reactions") as? Map<*, *>).orEmpty().entries.mapNotNull { (key, value) ->
            if (key is String && value is String && value in groupReactions) key to value else null
        }.toMap()
    )
}
