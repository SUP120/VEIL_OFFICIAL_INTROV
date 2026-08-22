package com.ns.findyourintroverfriend.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists a rolling window of DM notification messages per matchId in SharedPreferences.
 *
 * Used to rebuild [NotificationCompat.MessagingStyle] conversation threads across
 * multiple separate incoming push notifications — without a Firestore read.
 * Holds a maximum of [MAX_MESSAGES] entries per matchId (oldest are trimmed).
 */
object DmNotifHistory {

    private const val MAX_MESSAGES = 7
    private const val PREFS_NAME   = "veil_dm_notif_history"

    data class Msg(val text: String, val ts: Long, val isMine: Boolean)

    // ── Read ────────────────────────────────────────────────────────────────────

    fun getMessages(context: Context, matchId: String): List<Msg> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json  = prefs.getString(matchId, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Msg(
                    text   = obj.getString("text"),
                    ts     = obj.getLong("ts"),
                    isMine = obj.getBoolean("mine")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Write ───────────────────────────────────────────────────────────────────

    fun addMessage(
        context : Context,
        matchId : String,
        text    : String,
        ts      : Long,
        isMine  : Boolean
    ) {
        val existing = getMessages(context, matchId).toMutableList()
        existing.add(Msg(text, ts, isMine))
        val trimmed = if (existing.size > MAX_MESSAGES) existing.takeLast(MAX_MESSAGES) else existing

        val arr = JSONArray()
        trimmed.forEach { m ->
            arr.put(JSONObject().apply {
                put("text", m.text)
                put("ts",   m.ts)
                put("mine", m.isMine)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(matchId, arr.toString()).apply()
    }

    // ── Clear (call when the chat screen is opened) ─────────────────────────────

    fun clear(context: Context, matchId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(matchId).apply()
    }
}
