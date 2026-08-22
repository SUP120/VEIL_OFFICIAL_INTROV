package com.ns.findyourintroverfriend.util

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * PresenceManager
 *
 * Writes `onlineAt` (epoch ms) to users/{uid} every 30 s while the app is
 * foreground.  `onlineAt` is also set on app launch and cleared to the
 * current time on app background (acting as `lastSeen`).
 *
 * Firestore field layout (on users/{uid}):
 *   onlineAt   : Long   — updated every 30 s while online; 0 when offline
 *   lastSeen   : Long   — last time the user was online (set on go-offline)
 *   showLastSeen: Boolean — user preference; default true
 *
 * Usage:
 *   PresenceManager.goOnline(context)   // call from onResume / LaunchedEffect
 *   PresenceManager.goOffline()         // call from onPause / DisposableEffect
 *   PresenceManager.setShowLastSeen(ctx, bool)
 *   PresenceManager.showLastSeenPref(ctx): Boolean
 */
object PresenceManager {

    private const val PREFS        = "veil_presence"
    private const val KEY_SHOW     = "show_last_seen"
    private const val HEARTBEAT_MS = 30_000L
    private const val ONLINE_THRESHOLD_MS = 60_000L   // within 60 s → "Online"

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var heartbeatJob: Job? = null
    private var offlineJob  : Job? = null   // delayed lastSeen write (cancelled if goOnline fires first)
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Preference helpers ───────────────────────────────────────────────────

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun showLastSeenPref(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SHOW, true)

    fun setShowLastSeen(ctx: Context, show: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SHOW, show).apply()
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            runCatching {
                db.collection("users").document(uid)
                    .set(mapOf("showLastSeen" to show), SetOptions.merge())
                    .await()
            }
        }
    }

    // ── Online / Offline ─────────────────────────────────────────────────────

    fun goOnline(ctx: Context) {
        val uid = auth.currentUser?.uid ?: return
        // Cancel any pending offline write (user came back before the grace period)
        offlineJob?.cancel()
        offlineJob = null

        if (!showLastSeenPref(ctx)) return   // user opted out — don't publish

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                runCatching {
                    db.collection("users").document(uid)
                        .set(mapOf("onlineAt" to System.currentTimeMillis()), SetOptions.merge())
                        .await()
                }
                delay(HEARTBEAT_MS)
            }
        }
    }

    fun goOffline() {
        val uid = auth.currentUser?.uid ?: return
        heartbeatJob?.cancel()
        heartbeatJob = null

        // Grace delay: wait 3 s before writing lastSeen.
        // If goOnline() fires within that window (e.g. user navigated away and back),
        // the offlineJob is cancelled and lastSeen is never written.
        offlineJob?.cancel()
        offlineJob = scope.launch {
            delay(3_000L)
            if (!isActive) return@launch
            runCatching {
                val now = System.currentTimeMillis()
                db.collection("users").document(uid)
                    .set(
                        mapOf(
                            "onlineAt" to 0L,
                            "lastSeen" to now
                        ),
                        SetOptions.merge()
                    ).await()
            }
        }
    }

    // ── Fetch presence for a list of UIDs ────────────────────────────────────

    /**
     * Fetches each user document by its document ID (the uid IS the doc ID).
     * Falls back to the legacy `presence/{uid}` collection for users who
     * haven't opened the new app version yet.
     *
     * Returns map of uid → Pair(isOnline, lastSeenMs).
     */
    suspend fun fetchPresence(uids: List<String>): Map<String, Pair<Boolean, Long>> {
        if (uids.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Pair<Boolean, Long>>()
        val now = System.currentTimeMillis()

        uids.forEach { uid ->
            runCatching {
                // ── Primary: users/{uid} (new presence fields) ────────────────
                val doc = db.collection("users").document(uid).get().await()
                val showPref = doc.getBoolean("showLastSeen") ?: true
                if (!showPref) return@runCatching   // user opted out

                val onlineAt = doc.getLong("onlineAt") ?: 0L
                var lastSeen = doc.getLong("lastSeen")  ?: 0L

                if (onlineAt == 0L && lastSeen == 0L) {
                    // ── Fallback: legacy presence/{uid} collection ────────────
                    runCatching {
                        val legacyDoc = db.collection("presence").document(uid).get().await()
                        val legacySeen = legacyDoc.getLong("lastSeen") ?: 0L
                        if (legacySeen > 0L) lastSeen = legacySeen
                    }
                }

                val isOnline = onlineAt > 0L && (now - onlineAt) < ONLINE_THRESHOLD_MS
                val displayTs = if (isOnline) now else lastSeen
                if (isOnline || displayTs > 0L) {
                    result[uid] = Pair(isOnline, displayTs)
                }
            }
        }
        return result
    }

    // ── Format helper ─────────────────────────────────────────────────────────

    fun formatPresence(isOnline: Boolean, lastSeenMs: Long): String {
        if (isOnline) return "Online"
        if (lastSeenMs == 0L) return ""
        val diff = System.currentTimeMillis() - lastSeenMs
        return "last seen " + when {
            diff < 60_000L      -> "just now"
            diff < 3_600_000L   -> "${diff / 60_000}m ago"
            diff < 86_400_000L  -> "${diff / 3_600_000}h ago"
            diff < 604_800_000L -> "${diff / 86_400_000}d ago"
            else                -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                                       .format(java.util.Date(lastSeenMs))
        }
    }
}
