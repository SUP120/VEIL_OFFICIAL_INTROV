package com.ns.findyourintroverfriend.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.ns.findyourintroverfriend.MainActivity
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.groupchat.groupDocumentId

internal object GroupPushNotification {
    const val ACTION_REPLY = "com.ns.findyourintroverfriend.ACTION_GROUP_REPLY"
    const val REPLY_KEY = "veil_group_reply"
    const val CHANNEL = "veil_group_chat"

    fun createChannel(context: Context) {
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL, "Group messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Messages in your joined VEIL groups"
        })
    }

    fun cancel(context: Context, uid: String, room: String) {
        manager(context).cancel(tag(uid, room), ID)
    }

    @Synchronized
    fun showIncoming(context: Context, data: Map<String, String>) {
        val uid = data["recipientUid"] ?: return
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        val room = data["roomId"]?.takeIf(groupDocumentId::matches) ?: return
        val messageId = data["messageId"]?.takeIf(groupDocumentId::matches)
        val receipt = messageId?.let { "$uid/$room/$it" }
        val prefs = context.getSharedPreferences("veil_group_push_receipts", Context.MODE_PRIVATE)
        val seen = prefs.getString("recent", "").orEmpty().split('|').filter(String::isNotEmpty)
        if (receipt != null && receipt in seen) return

        val name = data["roomName"].orEmpty().take(80).ifBlank { "VEIL group" }
        val alias = data["senderAlias"].orEmpty().take(80).ifBlank { "Group member" }
        val text = data["preview"].orEmpty().take(160).ifBlank { "New message" }
        val timestamp = data["sentAt"]?.toLongOrNull() ?: System.currentTimeMillis()
        val sender = Person.Builder().setName(alias).setKey(data["senderUid"] ?: alias).build()
        val style = conversation(context, uid, room, name).addMessage(text, timestamp, sender)
        if (post(context, uid, room, name, messageId, style, silent = false) && receipt != null) {
            prefs.edit().putString("recent", (seen.takeLast(59) + receipt).joinToString("|")).apply()
        }
    }

    fun showReplyState(
        context: Context,
        uid: String,
        room: String,
        name: String,
        messageId: String?,
        status: String,
        allowReply: Boolean = true
    ) {
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        val style = conversation(context, uid, room, name)
        post(context, uid, room, name, messageId, style, silent = true, subText = status, allowReply = allowReply)
    }

    private fun conversation(context: Context, uid: String, room: String, name: String): NotificationCompat.MessagingStyle {
        val existing = manager(context).activeNotifications.firstOrNull { it.tag == tag(uid, room) && it.id == ID }
        val previous = existing?.notification?.let(NotificationCompat.MessagingStyle::extractMessagingStyleFromNotification)
        val me = Person.Builder().setName("You").setKey(uid).build()
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(name).setGroupConversation(true)
        previous?.messages?.takeLast(6)?.forEach { style.addMessage(it) }
        return style
    }

    private fun post(
        context: Context, uid: String, room: String, name: String, messageId: String?,
        style: NotificationCompat.MessagingStyle, silent: Boolean, subText: String? = null,
        allowReply: Boolean = true
    ): Boolean {
        createChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = actionUri("open", uid, room)
            putExtra(VeilMessagingService.EXTRA_GROUP_CHAT_ROOM, room)
            putExtra(VeilMessagingService.EXTRA_GROUP_CHAT_RECIPIENT, uid)
        }
        val open = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("VEIL")
            .setContentText("New group message")
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(name)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setStyle(style)
            .setSubText(subText)
            .setOnlyAlertOnce(silent)
            .setAutoCancel(true)
        if (allowReply) {
            val replyIntent = Intent(context, GroupNotificationReplyReceiver::class.java).apply {
                action = ACTION_REPLY
                data = actionUri("reply", uid, room, messageId)
                putExtra("uid", uid)
                putExtra("roomId", room)
                putExtra("roomName", name)
                putExtra("inReplyTo", messageId)
            }
            val reply = PendingIntent.getBroadcast(
                context, 0, replyIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_send_notification, "Reply", reply)
                    .addRemoteInput(RemoteInput.Builder(REPLY_KEY).setLabel("Reply to group").build())
                    .setAllowGeneratedReplies(false)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .setAuthenticationRequired(true)
                    .build()
            )
        }
        val service = manager(context)
        if (!service.areNotificationsEnabled()) return false
        return try {
            service.notify(tag(uid, room), ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun actionUri(action: String, uid: String, room: String, messageId: String? = null): Uri =
        Uri.Builder().scheme("veilapp").authority("group-notification").appendPath(action)
            .appendPath(uid).appendPath(room).apply { messageId?.let { appendPath(it) } }.build()

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
    private fun tag(uid: String, room: String) = "group:$uid:$room"
    private const val ID = 73
}
