package com.ns.findyourintroverfriend.swipe

import android.content.Context
import android.content.SharedPreferences

/** The latest message seen on this device, using the same ordering as the message query. */
internal data class DmMessagePosition(val sentAt: Long, val documentId: String) : Comparable<DmMessagePosition> {
    override fun compareTo(other: DmMessagePosition): Int =
        compareValuesBy(this, other, DmMessagePosition::sentAt, DmMessagePosition::documentId)
}

internal class DmReadState(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("veil_dm_read", Context.MODE_PRIVATE)

    fun key(uid: String, matchId: String) = "$uid/$matchId"

    fun position(key: String): DmMessagePosition? {
        val entry = preferences.getString(key, null) ?: return null
        val time = entry.substringBefore(':').toLongOrNull() ?: return null
        return DmMessagePosition(time, entry.substringAfter(':'))
    }

    fun markRead(key: String, latest: DmMessagePosition) {
        val current = position(key)
        if (current == null || latest > current) {
            preferences.edit().putString(key, "${latest.sentAt}:${latest.documentId}").apply()
        }
    }
}
