package com.ns.findyourintroverfriend.notifications

/**
 * Tracks active chat state and foreground status for notification suppression.
 *
 * Rules (mirrors WhatsApp behaviour):
 *  • [activeMatchId] == incoming matchId → app is ON that chat → suppress completely
 *  • [isAppForeground] == true but different chat → app is open, user is elsewhere
 *                        → show notification silently (no sound/vibration)
 *  • [isAppForeground] == false → app is in background → full notification with sound
 *
 * [isAppForeground] is set by MainActivity.onResume / onStop.
 * [activeMatchId]   is set by ConnectionRoomScreen enter / leave DisposableEffect.
 */
object ActiveChatTracker {

    /**
     * The matchId currently displayed in ConnectionRoomScreen, or null when
     * no DM chat is open. @Volatile so the FCM service thread sees it immediately.
     */
    @Volatile
    var activeMatchId: String? = null

    /**
     * True whenever MainActivity is in the foreground (onResume called, onStop not yet).
     * Used to silence notifications when the user is actively using the app.
     */
    @Volatile
    var isAppForeground: Boolean = false
}
