package com.ns.findyourintroverfriend.util

/**
 * Veil developer configuration.
 *
 * HOW TO GET YOUR FIREBASE UID:
 *   1. Open Firebase Console → Authentication → Users
 *   2. Search for supriyamkumar098@gmail.com → copy the "User UID" column
 *   3. Do the same for itspossible4202@gmail.com
 *   4. Paste both UIDs into the set below.
 *
 * These UIDs are only used to show the 🔵 developer badge and highlight
 * developer posts with a soft blue card background. No data is exposed.
 */
object VeilDevConfig {

    /**
     * Firebase UIDs of the developer accounts.
     * Replace the placeholder strings with your actual Firebase UIDs.
     */
    val DEVELOPER_UIDS: Set<String> = setOf(
        "vUQxNo76bffMDr5Mw3FvopvqfbB2",    // supriyamkumar098@gmail.com
        "PASTE_UID_FOR_itspossible4202_HERE"       // itspossible4202@gmail.com
    )

    fun isDeveloper(uid: String): Boolean = uid in DEVELOPER_UIDS
}
