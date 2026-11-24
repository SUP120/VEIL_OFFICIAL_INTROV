package com.ns.findyourintroverfriend.feed

import android.content.Context
import org.json.JSONObject

/** A small, account-scoped record used only to rank this device's Home feed. */
internal class HomeFeedHistory(context: Context) {
    private val preferences = context.getSharedPreferences("veil_home_views", Context.MODE_PRIVATE)

    fun read(uid: String, now: Long): Map<String, Long> {
        val json = runCatching { JSONObject(preferences.getString(uid, "{}").orEmpty()) }.getOrDefault(JSONObject())
        return json.keys().asSequence().map { it to json.optLong(it) }
            .filter { (_, time) -> time in (now - 7 * 86_400_000L)..now }
            .sortedByDescending { it.second }.take(500).toMap()
    }

    fun mark(uid: String, ids: Collection<String>, now: Long) {
        if (uid.isBlank() || ids.isEmpty()) return
        val current = read(uid, now)
        val updated = ids.filter { now - (current[it] ?: 0L) >= 30 * 60_000L }
        if (updated.isEmpty()) return
        val rows = (current + updated.associateWith { now }).entries.sortedByDescending { it.value }
        val json = JSONObject()
        rows.take(500).forEach { json.put(it.key, it.value) }
        preferences.edit().putString(uid, json.toString()).apply()
    }
}
