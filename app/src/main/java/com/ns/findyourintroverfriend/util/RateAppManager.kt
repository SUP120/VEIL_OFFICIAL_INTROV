package com.ns.findyourintroverfriend.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Smart rate-prompt manager.
 *
 * Rules:
 *  • First prompt: 3 days after first install (gives users time to experience the app)
 *  • "Remind me later": snooze randomly 2–3 days
 *  • "Already reviewed" or "Review now": set reviewed flag → never prompt again
 *  • Never shows more than once per session to avoid annoyance
 */
object RateAppManager {

    private const val PREFS_NAME          = "veil_rate_prefs"
    private const val KEY_FIRST_LAUNCH    = "first_launch_ms"
    private const val KEY_NEXT_PROMPT     = "next_prompt_ms"
    private const val KEY_REVIEWED        = "user_reviewed"
    private const val KEY_SESSION_SHOWN   = "shown_this_session"

    // ── tunables ──────────────────────────────────────────────────────────────
    private const val INITIAL_DELAY_MS    = 3L * 24 * 60 * 60 * 1000   // 3 days
    private const val SNOOZE_MIN_MS       = 2L * 24 * 60 * 60 * 1000   // 2 days
    private const val SNOOZE_MAX_MS       = 3L * 24 * 60 * 60 * 1000   // 3 days

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Call once when the app starts to stamp the very first launch. */
    fun recordFirstLaunch(ctx: Context) {
        val p = prefs(ctx)
        if (p.getLong(KEY_FIRST_LAUNCH, 0L) == 0L) {
            val now = System.currentTimeMillis()
            p.edit()
                .putLong(KEY_FIRST_LAUNCH, now)
                .putLong(KEY_NEXT_PROMPT, now + INITIAL_DELAY_MS)
                .apply()
        }
        // Reset session flag on every new launch
        p.edit().putBoolean(KEY_SESSION_SHOWN, false).apply()
    }

    /**
     * Returns true if the dialog should be shown right now.
     * Only returns true once per session even if conditions are met.
     */
    fun shouldShow(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (p.getBoolean(KEY_REVIEWED, false))          return false  // done forever
        if (p.getBoolean(KEY_SESSION_SHOWN, false))     return false  // already shown this session
        if (p.getLong(KEY_NEXT_PROMPT, 0L) == 0L)       return false  // not initialised yet
        return System.currentTimeMillis() >= p.getLong(KEY_NEXT_PROMPT, Long.MAX_VALUE)
    }

    /** Mark shown for this session so we don't re-show after navigation. */
    fun markShownThisSession(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_SESSION_SHOWN, true).apply()
    }

    /** User tapped "Remind me later" — snooze 2–3 days randomly. */
    fun snooze(ctx: Context) {
        val snooze = SNOOZE_MIN_MS + (Math.random() * (SNOOZE_MAX_MS - SNOOZE_MIN_MS)).toLong()
        prefs(ctx).edit()
            .putLong(KEY_NEXT_PROMPT, System.currentTimeMillis() + snooze)
            .putBoolean(KEY_SESSION_SHOWN, true)
            .apply()
    }

    /** User tapped "Review Now" or "Already Reviewed" — never show again. */
    fun markReviewed(ctx: Context) {
        prefs(ctx).edit()
            .putBoolean(KEY_REVIEWED, true)
            .putBoolean(KEY_SESSION_SHOWN, true)
            .apply()
    }
}
