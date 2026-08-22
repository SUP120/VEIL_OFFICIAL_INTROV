package com.ns.findyourintroverfriend.notifications

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ns.findyourintroverfriend.MainActivity
import com.ns.findyourintroverfriend.R

private const val TAG           = "VeilFCM"
private const val CHANNEL_ID    = "veil_events"
private const val CHANNEL_NAME  = "Veil Events"

/**
 * VeilMessagingService — Firebase Cloud Messaging handler.
 *
 * Notification suppression tiers (DM messages only):
 *  Tier 1 — User is IN that exact chat → suppress completely (no notification)
 *  Tier 2 — App is in foreground (any screen) → suppress completely
 *            (the user is actively using the app; they see the message live)
 *  Tier 3 — App is in background / killed → full notification with sound + vibration
 *
 * Foreground detection uses TWO independent checks so one can never miss:
 *  A. ActiveChatTracker.isAppForeground (set by MainActivity.onResume/onStop)
 *  B. ActivityManager.getRunningAppProcesses() importance check (OS-level, always accurate)
 */
class VeilMessagingService : FirebaseMessagingService() {

    // ── Token refresh ──────────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(20)}…")
        saveTokenToFirestore(token)
    }

    // ── Message received ───────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type    = message.data["type"]
        val groupId = message.data["groupId"]
        val title   = message.data["title"]  ?: message.notification?.title ?: "Veil"
        val body    = message.data["body"]   ?: message.notification?.body  ?: ""

        Log.d(TAG, "onMessageReceived: type=$type title=$title")

        // ── DM message — WhatsApp-style ────────────────────────────────────────
        if (type == "dm_message") {
            val matchId     = message.data["matchId"]    ?: return
            val fromUid     = message.data["fromUid"]    ?: ""
            val senderAlias = title.ifBlank { "New message" }

            showDmNotification(
                matchId      = matchId,
                senderAlias  = senderAlias,
                senderUid    = fromUid,
                messageText  = body
            )
            return
        }

        // ── All other notification types — standard banner ─────────────────────
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            when (type) {
                "group_ready", "group_formed" -> {
                    if (groupId != null) {
                        putExtra(EXTRA_OPEN_WAITING_ROOM, true)
                        putExtra(EXTRA_GROUP_ID, groupId)
                    }
                }
                "slow_chat" -> {
                    if (groupId != null) {
                        putExtra(EXTRA_OPEN_CHAT, true)
                        putExtra(EXTRA_GROUP_ID, groupId)
                    }
                }
                "ceremony_unlocked", "ceremony_soon" -> {
                    if (groupId != null) {
                        putExtra(EXTRA_OPEN_CEREMONY, true)
                        putExtra(EXTRA_GROUP_ID, groupId)
                    }
                }
                "day_advanced" -> putExtra(EXTRA_OPEN_ISLAND, true)
                "whisper_invite" -> {
                    val roomId = message.data["roomId"]
                    if (!roomId.isNullOrBlank()) {
                        putExtra(EXTRA_OPEN_VOICE_ROOM, true)
                        putExtra(EXTRA_ROOM_ID, roomId)
                    }
                }
            }
        }

        val notificationId = type?.hashCode() ?: System.currentTimeMillis().toInt()
        showGenericNotification(title, body, intent, notificationId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Foreground detection — dual-check for maximum reliability
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if the app process is currently in the foreground.
     * Uses ActivityManager.getRunningAppProcesses() as primary check (OS-level, reliable),
     * and falls back to our ActiveChatTracker.isAppForeground flag as secondary.
     */
    private fun isAppInForeground(): Boolean {
        // Primary: OS-level check — most reliable
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val importance = am?.runningAppProcesses
            ?.firstOrNull { it.pid == android.os.Process.myPid() }
            ?.importance
        if (importance != null) {
            return importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        // Fallback: our flag set in MainActivity.onResume / onStop
        return ActiveChatTracker.isAppForeground
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DM notification — WhatsApp style, 3-tier suppression
    // ─────────────────────────────────────────────────────────────────────────

    private fun showDmNotification(
        matchId     : String,
        senderAlias : String,
        senderUid   : String,
        messageText : String
    ) {
        // ── Tier 1: Suppress if this exact chat is currently on screen ────────
        if (ActiveChatTracker.activeMatchId == matchId) {
            Log.d(TAG, "DM suppressed — chat $matchId is open on screen")
            return
        }

        // ── Tier 2: Suppress if app is in foreground (any screen) ────────────
        // When the user is actively using the app they will see the message
        // live in the Firestore listener — no notification needed.
        if (isAppInForeground()) {
            Log.d(TAG, "DM suppressed — app is in foreground")
            return
        }

        // ── Tier 3: App is in background/killed → show full notification ─────
        val notifId = (matchId.hashCode() and 0x7FFFFFFF)

        ensureDmChannel()

        // ── Build persons for MessagingStyle ──────────────────────────────────
        val me      = Person.Builder().setName("You").setKey("me").build()
        val partner = Person.Builder().setName(senderAlias).setKey("partner").build()

        // Persist this message to history so thread builds up correctly
        val now = System.currentTimeMillis()
        DmNotifHistory.addMessage(this, matchId, messageText, now, isMine = false)

        // MessagingStyle — shows full conversation thread like WhatsApp
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(senderAlias)
        DmNotifHistory.getMessages(this, matchId).forEach { msg ->
            style.addMessage(msg.text, msg.ts, if (msg.isMine) me else partner)
        }

        // ── Open-chat tap intent ───────────────────────────────────────────────
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DM, true)
            putExtra(EXTRA_MATCH_ID, matchId)
        }
        val openPending = PendingIntent.getActivity(
            this, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Reply action — WhatsApp-style inline reply ─────────────────────────
        val myAlias = getSharedPreferences("veil_prefs", Context.MODE_PRIVATE)
            .getString("user_alias", "Me") ?: "Me"

        val replyPending = NotificationReplyReceiver.buildReplyPendingIntent(
            context      = this,
            notifId      = notifId,
            matchId      = matchId,
            partnerUid   = senderUid,
            partnerAlias = senderAlias,
            myAlias      = myAlias
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_send_notification,
            "Reply",
            replyPending
        )
            .addRemoteInput(
                RemoteInput.Builder(NotificationReplyReceiver.REPLY_KEY)
                    .setLabel("Reply to $senderAlias…")
                    .build()
            )
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        // ── Build notification ─────────────────────────────────────────────────
        val notification = NotificationCompat.Builder(this, NotificationReplyReceiver.DM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(style)
            .setContentIntent(openPending)
            .addAction(replyAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("veil_dm_$matchId")
            .setGroupSummary(false)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, notification)
        Log.d(TAG, "DM notification posted: id=$notifId matchId=$matchId")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Generic (non-DM) notification — standard banner
    // ─────────────────────────────────────────────────────────────────────────

    private fun showGenericNotification(
        title          : String,
        body           : String,
        intent         : Intent,
        notificationId : Int,
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureGeneralChannel(manager)
        ensureMusicChannel(manager)

        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        manager.notify(notificationId, notification)
        Log.d(TAG, "General notification posted: id=$notificationId \"$title\"")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Channel helpers (idempotent — safe to call multiple times)
    // ─────────────────────────────────────────────────────────────────────────

    private fun ensureDmChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NotificationReplyReceiver.DM_CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            NotificationReplyReceiver.DM_CHANNEL_ID,
            "Direct Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "One-to-one chats between matched users"
            enableLights(true)
            lightColor  = android.graphics.Color.rgb(0, 0, 0)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120)
            setShowBadge(true)
        }
        manager.createNotificationChannel(ch)
    }

    private fun ensureGeneralChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important events in your Veil circle"
            enableLights(true)
            lightColor  = android.graphics.Color.rgb(155, 111, 255)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setShowBadge(true)
        }
        manager.createNotificationChannel(ch)
    }

    private fun ensureMusicChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel("music_room") != null) return
        val ch = NotificationChannel(
            "music_room", "Music Room Invites", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when someone opens a Music Room"
            enableLights(true)
            lightColor  = android.graphics.Color.rgb(46, 158, 106)
            enableVibration(true)
        }
        manager.createNotificationChannel(ch)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FCM token persistence
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update(
                    mapOf(
                        "fcmToken"          to token,
                        "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now(),
                    )
                )
                .addOnSuccessListener { Log.d(TAG, "✅ FCM token saved for uid=$uid") }
                .addOnFailureListener { e ->
                    Log.w(TAG, "⚠️ Failed to save FCM token: ${e.message}")
                }
        } else {
            Log.w(TAG, "No logged-in user — storing token locally for later save")
            getSharedPreferences("veil_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("pending_fcm_token", token)
                .apply()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constants shared across the notification system
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        const val EXTRA_OPEN_WAITING_ROOM = "open_waiting_room"
        const val EXTRA_OPEN_CHAT         = "open_chat"
        const val EXTRA_OPEN_CEREMONY     = "open_ceremony"
        const val EXTRA_OPEN_ISLAND       = "open_island"
        const val EXTRA_GROUP_ID          = "group_id"
        const val EXTRA_OPEN_DM           = "open_dm"
        const val EXTRA_MATCH_ID          = "match_id"
        const val EXTRA_OPEN_VOICE_ROOM   = "open_voice_room"
        const val EXTRA_ROOM_ID           = "room_id"

        fun flushPendingTokenIfNeeded(context: Context, uid: String) {
            val prefs = context.getSharedPreferences("veil_prefs", Context.MODE_PRIVATE)
            val pendingToken = prefs.getString("pending_fcm_token", null) ?: return
            Log.d(TAG, "Flushing pending FCM token for uid=$uid")
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update(
                    mapOf(
                        "fcmToken"          to pendingToken,
                        "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now(),
                    )
                )
                .addOnSuccessListener {
                    prefs.edit().remove("pending_fcm_token").apply()
                    Log.d(TAG, "✅ Pending FCM token flushed for uid=$uid")
                }
        }
    }
}
