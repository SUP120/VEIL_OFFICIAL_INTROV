package com.ns.findyourintroverfriend.util

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

// ─────────────────────────────────────────────────────────────────────────────
//  StrikeManager — 7️⃣  Strike & Penalty System
//
//  Rule (from 18+Customization.md Section 7):
//    1st strike → Warning (in-app banner, no access restriction)
//    2nd strike → Temporary ban (24 hours)
//    3rd strike → Permanent ban
//
//  Strikes are stored in the user's Firestore doc:
//    users/{uid} {
//       strikes      : Int       // cumulative count
//       banType      : String?   // "temporary" | "permanent" | null
//       bannedUntil  : Timestamp?// for temporary bans
//       isBanned     : Boolean
//       adminWarning : String?   // message from admin if any
//    }
//
//  Usage:
//    1. On content violation → call StrikeManager.addStrike(uid)
//    2. On app start / any screen load → call StrikeManager.checkBanStatus(uid)
//       to get BanStatus and block access if needed.
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "StrikeManager"

sealed class BanStatus {
    /** User is in good standing */
    object Clear : BanStatus()
    /** User has received an in-app warning (1st strike) */
    data class Warned(val strikes: Int, val message: String) : BanStatus()
    /** User is temporarily banned */
    data class TempBanned(val until: Date) : BanStatus()
    /** User is permanently banned */
    object PermaBanned : BanStatus()
}

object StrikeManager {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Record one new content violation for [uid].
     * Escalates ban if threshold is reached.
     * Returns the new [BanStatus].
     */
    suspend fun addStrike(uid: String): BanStatus {
        return try {
            // 1. Increment strikes atomically
            val userRef = db.collection("users").document(uid)
            userRef.update("strikes", FieldValue.increment(1)).await()

            // 2. Read new value
            val doc    = userRef.get().await()
            val strikes = (doc.getLong("strikes") ?: 1L).toInt()

            Log.w(TAG, "addStrike: uid=$uid, newStrikes=$strikes")

            // 3. Apply penalty
            when {
                strikes >= 3 -> {
                    // Permanent ban
                    userRef.update(
                        mapOf(
                            "isBanned"  to true,
                            "banType"   to "permanent",
                            "bannedAt"  to Timestamp.now()
                        )
                    ).await()
                    Log.w(TAG, "PERMANENT BAN applied to $uid")
                    BanStatus.PermaBanned
                }
                strikes == 2 -> {
                    // 24-hour temporary ban
                    val bannedUntil = Timestamp(
                        Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)
                    )
                    userRef.update(
                        mapOf(
                            "isBanned"    to true,
                            "banType"     to "temporary",
                            "bannedUntil" to bannedUntil,
                            "bannedAt"    to Timestamp.now()
                        )
                    ).await()
                    Log.w(TAG, "TEMP BAN (24h) applied to $uid")
                    BanStatus.TempBanned(bannedUntil.toDate())
                }
                else -> {
                    // 1st violation — in-app warning only
                    userRef.update(
                        "adminWarning",
                        "⚠️ This is your first violation warning. Repeated violations may result in a temporary or permanent ban."
                    ).await()
                    BanStatus.Warned(
                        strikes = strikes,
                        message = "⚠️ Warning: This message was blocked. Personal, sexual, contact or location content violates community rules. " +
                                  "Repeated violations will result in a temporary ban. [$strikes/3]"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "addStrike failed for $uid", e)
            // Return warned as safe fallback — don't crash
            BanStatus.Warned(strikes = 1,
                message = "⚠️ Your message was blocked for safety reasons.")
        }
    }

    /**
     * Check if [uid] is currently banned.
     * Call this on app open, before loading any chat screen.
     * Returns [BanStatus] — if [BanStatus.TempBanned] and expiry has passed,
     * automatically lifts the ban.
     */
    suspend fun checkBanStatus(uid: String): BanStatus {
        return try {
            val doc     = db.collection("users").document(uid).get().await()
            val isBanned = doc.getBoolean("isBanned") ?: false
            if (!isBanned) return BanStatus.Clear

            val banType  = doc.getString("banType") ?: "temporary"
            if (banType == "permanent") {
                return BanStatus.PermaBanned
            }

            // Temporary — check if expired
            val bannedUntil = doc.getTimestamp("bannedUntil")?.toDate()
            if (bannedUntil != null && System.currentTimeMillis() > bannedUntil.time) {
                // Ban expired → lift it
                db.collection("users").document(uid)
                    .update(mapOf(
                        "isBanned"    to false,
                        "banType"     to null,
                        "bannedUntil" to null
                    )).await()
                Log.d(TAG, "Temp ban lifted for $uid (expired)")
                return BanStatus.Clear
            }

            BanStatus.TempBanned(bannedUntil ?: Date())

        } catch (e: Exception) {
            Log.e(TAG, "checkBanStatus failed for $uid", e)
            BanStatus.Clear   // fail open — don't block access on network error
        }
    }
}
