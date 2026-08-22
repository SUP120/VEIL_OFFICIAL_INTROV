package com.ns.findyourintroverfriend.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.MainActivity
import com.ns.findyourintroverfriend.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles the "Reply" direct-reply action on DM notifications.
 *
 * Flow (mirrors WhatsApp):
 *  1. User types reply in the notification shade → taps Send
 *  2. Android fires this receiver with the typed text in a RemoteInput Bundle
 *  3. We write the message to Firestore (fire-and-forget)
 *  4. We update the notification thread to show the sent reply (no dismissal, stays open)
 *  5. We fire a push notification to the partner via OneSignal
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // ── 1. Extract the typed reply ───────────────────────────────────────
        val bundle    = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = bundle.getCharSequence(REPLY_KEY)?.toString()?.trim()
        if (replyText.isNullOrBlank()) return

        val matchId      = intent.getStringExtra(EXTRA_MATCH_ID)      ?: return
        val notifId      = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val partnerUid   = intent.getStringExtra(EXTRA_PARTNER_UID)   ?: return
        val partnerAlias = intent.getStringExtra(EXTRA_PARTNER_ALIAS) ?: "User"

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db  = FirebaseFirestore.getInstance()

        // Read our own alias from SharedPreferences (saved by ConnectionRoomScreen)
        val myAlias = context.getSharedPreferences("veil_prefs", Context.MODE_PRIVATE)
            .getString("user_alias", "Me") ?: "Me"

        Log.d(TAG, "Reply from notification: matchId=$matchId, len=${replyText.length}")

        // ── 2. Write message to Firestore ────────────────────────────────────
        val now = System.currentTimeMillis()
        db.collection("matches").document(matchId)
            .collection("messages")
            .add(
                mapOf(
                    "messageId"   to db.collection("_").document().id,
                    "senderUid"   to uid,
                    "senderAlias" to myAlias,
                    "text"        to replyText,
                    "sentAt"      to now
                )
            )

        // ── 3. Update the notification to show reply in the thread ───────────
        val me      = Person.Builder().setName("You").setKey("me").build()
        val partner = Person.Builder().setName(partnerAlias).setKey("partner").build()

        // Add the sent reply to history
        DmNotifHistory.addMessage(context, matchId, replyText, now, isMine = true)

        // Rebuild MessagingStyle with full history
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(partnerAlias)
        DmNotifHistory.getMessages(context, matchId).forEach { msg ->
            style.addMessage(msg.text, msg.ts, if (msg.isMine) me else partner)
        }

        // Open-chat tap intent
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(VeilMessagingService.EXTRA_OPEN_DM, true)
            putExtra(VeilMessagingService.EXTRA_MATCH_ID, matchId)
        }
        val openPending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Rebuild reply action so the user can keep replying
        val replyPending = buildReplyPendingIntent(context, notifId, matchId, partnerUid, partnerAlias, myAlias)

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_send_notification,
            "Reply",
            replyPending
        )
            .addRemoteInput(RemoteInput.Builder(REPLY_KEY).setLabel("Reply…").build())
            .setAllowGeneratedReplies(false)
            .build()

        val updatedNotif = NotificationCompat.Builder(context, DM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(style)
            .setContentIntent(openPending)
            .addAction(replyAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)   // don't vibrate again for the reply confirmation
            .setAutoCancel(false)     // keep open so more messages can be seen
            .build()

        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(notifId, updatedNotif)
        } catch (_: Exception) {}

        // ── 4. Send push to partner ──────────────────────────────────────────
        CoroutineScope(Dispatchers.IO).launch {
            try {
                VeilNotifier.sendDmMessage(
                    toUid       = partnerUid,
                    fromAlias   = myAlias,
                    fromUid     = uid,
                    matchId     = matchId,
                    messageText = replyText
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send partner notification after reply: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "NotifReplyReceiver"

        const val REPLY_KEY           = "veil_reply_text"
        const val DM_CHANNEL_ID       = "veil_dm_chat"
        const val ACTION_DM_REPLY     = "com.ns.findyourintroverfriend.ACTION_DM_REPLY"

        const val EXTRA_MATCH_ID      = "extra_match_id"
        const val EXTRA_NOTIF_ID      = "extra_notif_id"
        const val EXTRA_PARTNER_UID   = "extra_partner_uid"
        const val EXTRA_PARTNER_ALIAS = "extra_partner_alias"
        const val EXTRA_MY_ALIAS      = "extra_my_alias"

        /** Builds the mutable PendingIntent for the reply RemoteInput action. */
        fun buildReplyPendingIntent(
            context      : Context,
            notifId      : Int,
            matchId      : String,
            partnerUid   : String,
            partnerAlias : String,
            myAlias      : String
        ): PendingIntent {
            val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
                action = ACTION_DM_REPLY
                putExtra(EXTRA_MATCH_ID,      matchId)
                putExtra(EXTRA_NOTIF_ID,      notifId)
                putExtra(EXTRA_PARTNER_UID,   partnerUid)
                putExtra(EXTRA_PARTNER_ALIAS, partnerAlias)
                putExtra(EXTRA_MY_ALIAS,      myAlias)
            }
            // FLAG_MUTABLE is required on Android 12+ so the system can attach RemoteInput results
            return PendingIntent.getBroadcast(
                context, notifId, replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }
    }
}
