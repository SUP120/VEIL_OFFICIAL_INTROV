package com.ns.findyourintroverfriend.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ns.findyourintroverfriend.MainActivity
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.data.UserFields

private const val TAG          = "VeilFCM"
private const val CHANNEL_ID   = "veil_events"
private const val CHANNEL_NAME = "Veil Events"

/**
 * VeilMessagingService — Firebase Cloud Messaging handler.
 *
 * This service is the ONLY way to receive FCM push notifications when the app
 * is completely killed (force-stopped). It must be registered in AndroidManifest
 * with the com.google.firebase.MESSAGING_EVENT intent filter.
 *
 * HOW BACKGROUND / KILLED-APP NOTIFICATIONS WORK:
 * ─────────────────────────────────────────────────
 * FCM has two message types:
 *  1. notification-only  → handled by the OS tray, onMessageReceived NOT called when killed
 *  2. data-only OR notification+data → onMessageReceived IS called even when app is killed
 *
 * Our Cloud Function (notifyUsers) sends BOTH notification + data fields.
 * This means:
 *   • Foreground: onMessageReceived fires → we show a custom notification.
 *   • Background/killed: OS shows the banner from the `notification` field AND
 *     onMessageReceived fires from the `data` field so we can do routing logic.
 */
class VeilMessagingService : FirebaseMessagingService() {

    // ── New / refreshed FCM token ──────────────────────────────────────────────
    // This fires independently of the app being open or closed.
    // We MUST save the latest token immediately — if it's not in Firestore,
    // notifyUsers() can't reach this device.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(20)}…")
        saveTokenToFirestore(token)
    }

    // ── Message handler ────────────────────────────────────────────────────────
    //
    //  This is called in THREE scenarios:
    //  1. App is in FOREGROUND → always fires, we show a custom notification
    //  2. App is in BACKGROUND (not killed) → fires for data/data+notification messages
    //  3. App is KILLED → fires for data/data+notification messages (notification-only → skipped)
    //
    //  Our server sends notification+data so all three cases are covered.
    //
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Pull data fields first — these are always present from our server
        val type    = message.data["type"]
        val groupId = message.data["groupId"]

        // Title/body: prefer data (more reliable cross-platform) over notification object
        val title = message.data["title"]
            ?: message.notification?.title
            ?: "Veil"
        val body  = message.data["body"]
            ?: message.notification?.body
            ?: ""

        Log.d(TAG, "onMessageReceived: type=$type title=$title (fromPkg=${message.from})")

        // Build the tap intent with optional deep-link data
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
                "day_advanced" -> {
                    putExtra(EXTRA_OPEN_ISLAND, true)
                }
                // ── DM / Connection Room message ──────────────────────────────────────
                // Tapping opens ConnectionRoomScreen/{matchId} directly.
                "dm_message" -> {
                    val matchId = message.data["matchId"]
                    if (!matchId.isNullOrBlank()) {
                        putExtra(EXTRA_OPEN_DM, true)
                        putExtra(EXTRA_MATCH_ID, matchId)
                    }
                }
            }
        }

        // Use notification type as stable ID seed so ceremony alerts replace earlier ones
        // but different notification types stack separately
        val notificationId = type?.hashCode() ?: System.currentTimeMillis().toInt()

        showNotification(title, body, intent, notificationId)
    }

    // ── Save token to users/{uid}.fcmToken and users/{uid}.fcmTokenUpdatedAt ──
    // Uses a fire-and-forget approach — the service can be called when no UI exists.
    // Also dual-writes to ensure the token is always available for server-side sends.
    private fun saveTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            // Direct Firestore write (fastest path when user is logged in)
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update(
                    mapOf(
                        UserFields.FCM_TOKEN to token,
                        "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now(),
                    )
                )
                .addOnSuccessListener { Log.d(TAG, "✅ FCM token saved to Firestore for uid=$uid") }
                .addOnFailureListener { e ->
                    Log.w(TAG, "⚠️ Failed to save FCM token: ${e.message} — will retry on next open")
                }
        } else {
            // No user logged in (e.g. token refreshed before login) — store locally
            // so we can save it when the user next signs in.
            Log.w(TAG, "No logged-in user — storing token locally for later save")
            getSharedPreferences("veil_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("pending_fcm_token", token)
                .apply()
        }
    }

    // ── Build and show a high-importance system notification ──────────────────
    private fun showNotification(
        title: String,
        body: String,
        intent: Intent,
        notificationId: Int,
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Always (re-)create the channel — safe to call multiple times, idempotent
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Important events in your Veil circle"
            enableLights(true)
            lightColor = android.graphics.Color.rgb(155, 111, 255)   // Veil purple
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)

        // Music Room channel
        val musicChannel = NotificationChannel(
            "music_room", "Music Room Invites", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when someone opens a Music Room"
            enableLights(true); lightColor = android.graphics.Color.rgb(46, 158, 106)
            enableVibration(true)
        }
        manager.createNotificationChannel(musicChannel)

        // FLAG_IMMUTABLE is required on Android 12+ for security
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
            .setDefaults(NotificationCompat.DEFAULT_ALL)     // sound + vibrate + lights
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)   // hide on lock screen
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        manager.notify(notificationId, notification)
        Log.d(TAG, "✅ Notification posted: id=$notificationId \"$title\"")
    }

    companion object {
        const val EXTRA_OPEN_WAITING_ROOM = "open_waiting_room"
        const val EXTRA_OPEN_CHAT         = "open_chat"
        const val EXTRA_OPEN_CEREMONY     = "open_ceremony"
        const val EXTRA_OPEN_ISLAND       = "open_island"
        const val EXTRA_GROUP_ID          = "group_id"
        // DM deep-link extras
        const val EXTRA_OPEN_DM           = "open_dm"       // true to open a specific chat
        const val EXTRA_MATCH_ID          = "match_id"      // matchId for ConnectionRoomScreen

        /**
         * Call this on login to flush any FCM token that was refreshed
         * before the user was authenticated (stored in SharedPreferences).
         */
        fun flushPendingTokenIfNeeded(context: Context, uid: String) {
            val prefs = context.getSharedPreferences("veil_prefs", Context.MODE_PRIVATE)
            val pendingToken = prefs.getString("pending_fcm_token", null) ?: return
            Log.d(TAG, "Flushing pending FCM token for uid=$uid")
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update(
                    mapOf(
                        UserFields.FCM_TOKEN to pendingToken,
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
