package com.ns.findyourintroverfriend.dev

// ─────────────────────────────────────────────────────────────────────────────
//  DevConfig — Developer email allowlist
//
//  Add any Gmail address here to enable the Developer Controls panel inside
//  IslandHomeScreen. The panel lets you skip Day 1 → 2 → 3 → Ceremony
//  without waiting 24 hours, so you can test the full app flow.
//
//  These emails NEVER see payment screens or are treated as admins automatically.
//  Only IslandHome dev controls are unlocked.
//
//  To add yourself: add your Google account email below.
// ─────────────────────────────────────────────────────────────────────────────

object DevConfig {

    /**
     * Emails that can see the Developer Day Controls panel on IslandHomeScreen.
     * Add / remove as needed. Lowercase only.
     */
    val DEV_EMAILS: Set<String> = setOf(
        "supriyamkumar098@gmail.com",
        "itspossible4202@gmail.com"
        // add more dev emails here as needed
    )


    /** Returns true if the given email (case-insensitive) is in the dev list. */
    fun isDev(email: String?): Boolean =
        email?.lowercase()?.trim() in DEV_EMAILS
}
