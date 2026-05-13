package com.ns.findyourintroverfriend.notifications

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * VeilNotifier — OneSignal-powered push notification engine.
 *
 * Architecture (reliable approach):
 *   1. On every login: call registerAndPersistUser(uid, db)
 *      - Calls OneSignal.login(uid) to set External ID
 *      - Observes subscription changes and writes subId to Firestore
 *   2. When sending, we read subIds from Firestore — guaranteed to exist.
 *   3. Fallback: external_id-based sends for group notifications.
 */
object VeilNotifier {

    private const val TAG            = "VeilNotifier"
    private const val ONE_SIGNAL_URL = "https://api.onesignal.com/notifications"
    private val APP_ID   get() = com.ns.findyourintroverfriend.BuildConfig.ONESIGNAL_APP_ID
    // REST key lives here only — NOT in BuildConfig so it isn't in the APK manifest/metadata.
    // For production, move this to a Firebase Cloud Function so it never ships in the APK.
    private const val REST_KEY = "os_v2_app_xif7u3jranfrhbw4xfhjoh5xtahrzziksjbegc5wvajg45xt6buuwfs2tosnnegiyxtt7rn3l2nnxa3y7ce4de7advk77tbkze62yci"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ─────────────────────────────────────────────────────────────────────────
    //  Device Registration — call on every app open / login
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Register this device with OneSignal and persist the subscription ID to Firestore.
     *
     * Strategy:
     *  1. OneSignal.login(uid) sets External ID (async, may take seconds)
     *  2. We immediately try to read pushSubscription.id — if available, write to Firestore
     *  3. We also launch a background coroutine that polls for up to 10s in case
     *     the subscription isn't ready yet (e.g., permission dialog still pending)
     *
     * Result: oneSignalSubId in Firestore is always up-to-date, enabling reliable targeting.
     */
    fun registerAndPersistUser(uid: String, db: FirebaseFirestore) {
        OneSignal.login(uid)
        Log.d(TAG, "OneSignal.login($uid)")

        // Try immediately — sub ID may already be available
        val immediate = OneSignal.User.pushSubscription.id
        if (!immediate.isNullOrBlank()) {
            db.collection("users").document(uid)
                .update("oneSignalSubId", immediate)
                .addOnSuccessListener { Log.d(TAG, "✅ subId persisted immediately: $immediate") }
            return  // done!
        }

        // Poll in background until we get a subscription ID (up to 10s)
        CoroutineScope(Dispatchers.IO).launch {
            repeat(20) { attempt ->
                delay(500L)
                val subId = OneSignal.User.pushSubscription.id
                if (!subId.isNullOrBlank()) {
                    db.collection("users").document(uid)
                        .update("oneSignalSubId", subId).await()
                    Log.d(TAG, "✅ subId persisted after ${(attempt + 1) * 500}ms: $subId")
                    return@launch  // success
                }
            }
            Log.w(TAG, "⚠️ subId not available after 10s for uid=$uid — will retry on next open")
        }
    }

    /** Legacy alias — used in places that don't have db context */
    fun registerUser(uid: String) {
        OneSignal.login(uid)
        Log.d(TAG, "OneSignal.login($uid) — basic registration")
    }

    fun unregisterUser() {
        OneSignal.logout()
        Log.d(TAG, "OneSignal user logged out")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Notification triggers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Welcome — sent when a new user completes profile setup.
     * Uses subscription ID stored in Firestore — the most reliable approach.
     */
    suspend fun notifyWelcome(uid: String, db: FirebaseFirestore) {
        // Wait up to 5 seconds for the subId to be written
        val subId = waitForSubId(db, uid, maxWaitMs = 5_000L)
        if (subId != null) {
            sendToSubscriptionIds(
                ids   = listOf(subId),
                title = "🌿 Welcome to Veil",
                body  = "Your journey begins. A circle of 4 is waiting for you — start yours."
            )
            Log.d(TAG, "notifyWelcome via subId → $uid")
        } else {
            // Fallback: broadcast segment (user must be subscribed)
            Log.w(TAG, "notifyWelcome: subId not available for $uid within 5s, trying external_id")
            delay(2_000L)
            sendToExternalIds(
                uids  = listOf(uid),
                title = "🌿 Welcome to Veil",
                body  = "Your journey begins. A circle of 4 is waiting for you — start yours."
            )
        }
    }

    /**
     * Group formed — all 4 members get notified.
     * Reads subscriber IDs from Firestore — reliable even if login() hasn't synced yet.
     */
    suspend fun notifyGroupFormed(db: FirebaseFirestore, groupId: String) {
        val uids   = getGroupUids(db, groupId)
        val subIds = getSubIds(db, uids)

        if (subIds.isNotEmpty()) {
            sendToSubscriptionIds(
                ids   = subIds,
                title = "🌿 Your circle is ready!",
                body  = "3 companions are waiting. Step into your island."
            )
            Log.d(TAG, "notifyGroupFormed via ${subIds.size} subIds → $groupId")
        } else {
            // Fallback: External ID (needs login() propagation)
            delay(3_000L)
            sendToExternalIds(
                uids  = uids,
                title = "🌿 Your circle is ready!",
                body  = "3 companions are waiting. Step into your island.",
                data  = mapOf("type" to "group_formed", "groupId" to groupId)
            )
            Log.w(TAG, "notifyGroupFormed fallback via externalIds → $groupId")
        }
    }

    /** Day advanced — Day 2 or Day 3 transition */
    suspend fun notifyDayAdvanced(db: FirebaseFirestore, groupId: String, newDay: Int) {
        val (title, body) = when (newDay) {
            2    -> "☀️ Day 2 begins" to "The veil lifts a little — photos are now visible."
            3    -> "🌙 Day 3 is here" to "Your last day together. The ceremony opens at 8 PM tonight."
            else -> return
        }
        sendToGroup(db, groupId, title, body, mapOf("type" to "day_advanced", "day" to newDay.toString()))
    }

    /** Slow chat — recipient gets notified of a new message */
    suspend fun notifySlowChatMessage(db: FirebaseFirestore, senderAlias: String, recipientUid: String, groupId: String) {
        val subId = getSubId(db, recipientUid)
        if (subId != null) {
            sendToSubscriptionIds(
                ids   = listOf(subId),
                title = "💬 $senderAlias sent you a message",
                body  = "Open your island — every word in the circle matters."
            )
        } else {
            sendToExternalIds(
                uids  = listOf(recipientUid),
                title = "💬 $senderAlias sent you a message",
                body  = "Open your island — every word in the circle matters.",
                data  = mapOf("type" to "slow_chat", "groupId" to groupId)
            )
        }
        Log.d(TAG, "notifySlowChatMessage → $recipientUid")
    }

    /** Member left — remaining members get notified */
    suspend fun notifyMemberLeft(db: FirebaseFirestore, groupId: String, leaverAlias: String, senderUid: String) {
        val uids = getGroupUids(db, groupId).filter { it != senderUid }
        sendToGroup(db, groupId, "😔 $leaverAlias left the circle",
            "Your journey continues. Keep the bond alive.",
            mapOf("type" to "member_left"), excludeUid = senderUid)
        Log.d(TAG, "notifyMemberLeft ($leaverAlias) → ${uids.size} remaining")
    }

    /** Ceremony reminder — called ~1hr before 8PM */
    suspend fun notifyCeremonyStarting(db: FirebaseFirestore, groupId: String) {
        sendToGroup(db, groupId,
            "✨ The ceremony begins tonight",
            "Be there at 8 PM — the veil lifts and your circle is revealed.",
            mapOf("type" to "ceremony_soon", "groupId" to groupId))
    }

    /** Ceremony 8PM unlocked — the moment arrives */
    suspend fun notifyCeremonyUnlocked(db: FirebaseFirestore, groupId: String) {
        sendToGroup(db, groupId,
            "🎭 The veil has lifted",
            "The ceremony is open. Enter now to reveal your circle.",
            mapOf("type" to "ceremony_unlocked", "groupId" to groupId))
    }

    /** User banned — admin action */
    suspend fun notifyUserBanned(db: FirebaseFirestore, uid: String, reason: String, isPermanent: Boolean) {
        val subId = getSubId(db, uid)
        val title = if (isPermanent) "🚫 Account suspended" else "⏸ Account temporarily suspended"
        val body  = if (isPermanent) "Your account has been permanently banned: $reason"
                    else "Your account is suspended for 24 hours: $reason"
        if (subId != null) {
            sendToSubscriptionIds(listOf(subId), title, body)
        } else {
            sendToExternalIds(listOf(uid), title, body)
        }
    }

    /** Friendship approach — sent when someone taps the approach button on a post */
    suspend fun notifyApproach(toUid: String, fromAlias: String, db: FirebaseFirestore) {
        val subId = getSubId(db, toUid)
        val title = "🤝 $fromAlias wants to connect"
        val body  = "They approached you for friendship on Veil. Check your Requests!"
        if (subId != null) {
            sendToSubscriptionIds(listOf(subId), title, body,
                data = mapOf("type" to "approach"))
        } else {
            sendToExternalIds(listOf(toUid), title, body,
                data = mapOf("type" to "approach"))
        }
        Log.d(TAG, "notifyApproach → $toUid from $fromAlias")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Admin Manual Notification API
    // ─────────────────────────────────────────────────────────────────────────

    /** Send to ALL subscribers (broadcast) */
    suspend fun sendToAll(title: String, body: String): Boolean =
        sendRaw(buildPayload(title, body).apply {
            put("included_segments", JSONArray(listOf("Total Subscriptions")))
        })

    /** Send to a specific Firebase UID (single user) */
    suspend fun sendToUser(uid: String, title: String, body: String): Boolean {
        val db = FirebaseFirestore.getInstance()
        val subId = getSubId(db, uid)
        return if (subId != null) {
            sendToSubscriptionIds(listOf(subId), title, body)
        } else {
            sendToExternalIds(listOf(uid), title, body)
        }
    }

    /** Send to all members of a group by groupId */
    suspend fun sendToGroup(
        db      : FirebaseFirestore,
        groupId : String,
        title   : String,
        body    : String
    ): Boolean = sendToGroup(db, groupId, title, body, emptyMap())

    /** Raw group notification with custom data map — used by ceremony reminders. */
    suspend fun notifyGroupRaw(
        db      : FirebaseFirestore,
        groupId : String,
        title   : String,
        body    : String,
        data    : Map<String, String> = emptyMap()
    ): Boolean = sendToGroup(db, groupId, title, body, data)

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun sendToGroup(
        db         : FirebaseFirestore,
        groupId    : String,
        title      : String,
        body       : String,
        data       : Map<String, String> = emptyMap(),
        excludeUid : String = ""
    ): Boolean {
        val uids   = getGroupUids(db, groupId).filter { it != excludeUid }
        val subIds = getSubIds(db, uids)
        return if (subIds.isNotEmpty()) {
            sendToSubscriptionIds(subIds, title, body, data)
        } else {
            sendToExternalIds(uids, title, body, data)
        }
    }

    /** Send directly to OneSignal subscription IDs (player IDs) — most reliable */
    private suspend fun sendToSubscriptionIds(
        ids   : List<String>,
        title : String,
        body  : String,
        data  : Map<String, String> = emptyMap(),
        urgent: Boolean = false
    ): Boolean {
        if (ids.isEmpty()) return false
        val payload = buildPayload(title, body, data, urgent).apply {
            put("include_subscription_ids", JSONArray(ids))
        }
        return sendRaw(payload)
    }

    /** Send by Firebase UID (OneSignal External ID) — requires login() to have propagated */
    private suspend fun sendToExternalIds(
        uids  : List<String>,
        title : String,
        body  : String,
        data  : Map<String, String> = emptyMap(),
        urgent: Boolean = false
    ): Boolean {
        if (uids.isEmpty()) return false
        val payload = buildPayload(title, body, data, urgent).apply {
            put("include_aliases", JSONObject().apply {
                put("external_id", JSONArray(uids))
            })
            put("target_channel", "push")
        }
        return sendRaw(payload)
    }

    private fun buildPayload(
        title   : String,
        body    : String,
        data    : Map<String, String> = emptyMap(),
        urgent  : Boolean = false
    ): JSONObject = JSONObject().apply {
        put("app_id", APP_ID)
        put("headings", JSONObject().apply { put("en", title) })
        put("contents", JSONObject().apply { put("en", body) })
        put("priority", 10)
        put("android_sound", "default")
        put("small_icon", "ic_launcher_veil")
        put("large_icon", "ic_launcher_veil")
        if (urgent) {
            // Heads-up / peek notification on Android — shows on lock screen publicly
            put("android_visibility",   1)      // PUBLIC: show full content on lock screen
            put("android_led_color",    "FF7B67EE") // Veil purple LED pulse
            put("android_accent_color", "FF7B67EE")
            put("ttl", 86400)                   // 24-hour delivery window
        }
        if (data.isNotEmpty()) put("data", JSONObject(data))
    }

    private suspend fun sendRaw(payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(ONE_SIGNAL_URL)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Basic $REST_KEY")
                .addHeader("Content-Type", "application/json")
                .build()

            http.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Log.d(TAG, "✅ OneSignal sent OK — ${payload.optJSONObject("contents")?.optString("en")?.take(50)}")
                    true
                } else {
                    Log.w(TAG, "⚠️ OneSignal HTTP ${resp.code}: $respBody")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ OneSignal send failed: ${e.message}")
            false
        }
    }

    // ── Firestore helpers ─────────────────────────────────────────────────────

    /** Get all member UIDs from a group doc */
    private suspend fun getGroupUids(db: FirebaseFirestore, groupId: String): List<String> =
        try {
            val doc = db.collection("groups").document(groupId).get().await()
            @Suppress("UNCHECKED_CAST")
            (doc.get("members") as? List<String>) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "getGroupUids failed: ${e.message}")
            emptyList()
        }

    /** Get OneSignal subscription ID for a single user */
    private suspend fun getSubId(db: FirebaseFirestore, uid: String): String? =
        try {
            db.collection("users").document(uid).get().await()
                .getString("oneSignalSubId")
                .takeIf { !it.isNullOrBlank() }
        } catch (e: Exception) { null }

    /** Get OneSignal subscription IDs for a list of UIDs */
    private suspend fun getSubIds(db: FirebaseFirestore, uids: List<String>): List<String> =
        uids.mapNotNull { uid -> getSubId(db, uid) }

    /**
     * Poll Firestore until oneSignalSubId exists for the UID, or maxWaitMs elapses.
     * Used for welcome notification where subId might not be written yet.
     */
    private suspend fun waitForSubId(
        db       : FirebaseFirestore,
        uid      : String,
        maxWaitMs: Long = 5_000L
    ): String? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            val subId = getSubId(db, uid)
            if (subId != null) return subId
            delay(500L)
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Swipe feature notifications
    // ─────────────────────────────────────────────────────────────────────────

    /** Mutual match — sent instantly to the other person */
    suspend fun sendSwipeMatch(toUid: String, fromAlias: String) {
        val db    = FirebaseFirestore.getInstance()
        val subId = getSubId(db, toUid)
        if (subId != null) {
            sendToSubscriptionIds(
                ids   = listOf(subId),
                title = "You have a new connection on Veil",
                body  = "Someone you liked also liked you back. Open the Connection Room.",
                data  = mapOf("type" to "swipe_match")
            )
        } else {
            sendToExternalIds(
                uids  = listOf(toUid),
                title = "You have a new connection on Veil",
                body  = "Someone you liked also liked you back. Open the Connection Room.",
                data  = mapOf("type" to "swipe_match")
            )
        }
        Log.d(TAG, "sendSwipeMatch -> $toUid")
    }

    /** Compliment — priority notification, no sender name revealed (builds curiosity) */
    suspend fun sendSwipeCompliment(toUid: String, fromAlias: String, message: String = "") {
        val db    = FirebaseFirestore.getInstance()
        val subId = getSubId(db, toUid)
        
        // Show message preview if provided
        val body = if (message.isNotBlank()) {
            val preview = message.take(60)
            "\"${preview}${if (message.length > 60) "..." else ""}\""
        } else {
            "A special compliment is waiting for you."
        }
        
        if (subId != null) {
            sendToSubscriptionIds(
                ids   = listOf(subId),
                title = "Someone really noticed you on Veil",
                body  = body,
                data  = mapOf("type" to "swipe_compliment")
            )
        } else {
            sendToExternalIds(
                uids  = listOf(toUid),
                title = "Someone really noticed you on Veil",
                body  = body,
                data  = mapOf("type" to "swipe_compliment")
            )
        }
        Log.d(TAG, "sendSwipeCompliment -> $toUid")
    }

    /** Like notification — sent when someone likes you (not just on match) */
    suspend fun sendSwipeLike(toUid: String, fromAlias: String) {
        val db    = FirebaseFirestore.getInstance()
        val subId = getSubId(db, toUid)
        if (subId != null) {
            sendToSubscriptionIds(
                ids   = listOf(subId),
                title = "Someone liked you on Veil",
                body  = "You have a new admirer. Check your swipe feed!",
                data  = mapOf("type" to "swipe_like")
            )
        } else {
            sendToExternalIds(
                uids  = listOf(toUid),
                title = "Someone liked you on Veil",
                body  = "You have a new admirer. Check your swipe feed!",
                data  = mapOf("type" to "swipe_like")
            )
        }
        Log.d(TAG, "sendSwipeLike -> $toUid")
    }

    /** Compliment accepted — HIGH PRIORITY — sent to the original compliment sender */
    suspend fun sendSwipeComplimentAccepted(toUid: String, acceptorAlias: String) {
        val db    = FirebaseFirestore.getInstance()
        val subId = getSubId(db, toUid)
        val title = "✨ Your compliment was accepted!"
        val body  = "$acceptorAlias opened a conversation with you. Go say hi!"
        if (subId != null) {
            sendToSubscriptionIds(
                ids    = listOf(subId),
                title  = title,
                body   = body,
                data   = mapOf("type" to "compliment_accepted"),
                urgent = true
            )
        } else {
            sendToExternalIds(
                uids   = listOf(toUid),
                title  = title,
                body   = body,
                data   = mapOf("type" to "compliment_accepted"),
                urgent = true
            )
        }
        Log.d(TAG, "sendSwipeComplimentAccepted -> $toUid")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DM / Connection Room message notification
    //  WhatsApp-style: tapping the notification opens the exact chat thread.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call this immediately after writing a DM message to Firestore.
     * Sends a high-priority heads-up notification to [toUid] with:
     *   • Title  = sender's alias (WhatsApp style)
     *   • Body   = first 80 chars of the message
     *   • Data   = type:"dm_message" + matchId for deep-link navigation
     *
     * The receiver's [VeilMessagingService] reads the data payload and routes
     * the tap directly to ConnectionRoomScreen/{matchId}.
     */
    suspend fun sendDmMessage(
        toUid      : String,
        fromAlias  : String,
        matchId    : String,
        messageText: String
    ) {
        val db    = FirebaseFirestore.getInstance()
        val subId = getSubId(db, toUid)
        val title = fromAlias.ifBlank { "New message" }
        val body  = messageText.take(80).let { if (messageText.length > 80) "$it…" else it }
        val data  = mapOf(
            "type"    to "dm_message",
            "matchId" to matchId
        )
        if (subId != null) {
            sendToSubscriptionIds(
                ids    = listOf(subId),
                title  = title,
                body   = body,
                data   = data,
                urgent = true     // heads-up banner, even when phone is in use
            )
        } else {
            sendToExternalIds(
                uids   = listOf(toUid),
                title  = title,
                body   = body,
                data   = data,
                urgent = true
            )
        }
        Log.d(TAG, "sendDmMessage -> $toUid (matchId=$matchId)")
    }
}
