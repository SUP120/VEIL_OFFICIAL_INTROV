package com.ns.findyourintroverfriend.navigation

// ── All screen routes in one place ───────────────────────────────────────────
object VeilRoute {
    const val AUTH           = "auth"
    const val PROFILE_SETUP  = "profile_setup"
    const val GAME_INTRO     = "game_intro"     // one-time onboarding intro slides
    const val ROOM_LOBBY     = "room_lobby"     // home screen with room browsing
    const val LOBBY_WAITING  = "lobby_waiting"  // ← waiting inside a specific room
    const val WAITING_ROOM   = "waiting_room"
    const val ISLAND_HOME    = "island_home"
    const val PROFILE_THEMES = "profile_themes" // Premium avatar frame purchase
    const val ACTIVE_ROOMS    = "active_rooms"    // Live viewer — all currently active groups
    const val VEIL_HOME      = "veil_home"       // Home screen — find new rooms / leave room
    const val QUESTION_CARD  = "question_card"
    const val ANSWER_CARD    = "answer_card"
    const val INBOX                = "inbox"
    const val MY_QUESTIONS_INBOX   = "my_questions_inbox"   // see replies to my questions
    const val CEREMONY       = "ceremony"
    const val PARTY          = "party"

    // Day 2 — Tic-Tac-Toe
    const val GAME_INVITE      = "game_invite"
    const val TIC_TAC_TOE      = "tic_tac_toe"   // + "/{gameId}"

    // Day 2 — Would You Rather
    const val WOULD_YOU_RATHER = "would_you_rather"

    // Day 3
    const val TRUE_OR_MYTH       = "true_or_myth"
    const val TRUE_OR_MYTH_SETUP = "true_or_myth_setup"   // submit your own fact
    const val SLOW_CHAT          = "slow_chat"            // Day 3 — limited chat (7 msgs)
    const val CHAT_UNLOCK        = "chat_unlock"           // ₹79 one-time paywall (legacy)
    const val CHAT_CREDITS       = "chat_credits"          // New credits system (packs + group gifting)

    // ── All-days feed ──────────────────────────────────────────────────────────
    const val AESTHETICS_FEED = "aesthetics_feed"   // Anonymous photo vibe feed (Day 1, 2, 3)
    const val VIBES            = "vibes"             // Vibe Song · Color · One Word Bio

    // ── Day 2 express ──────────────────────────────────────────────────────────
    const val DARE_TO_SHARE       = "dare_to_share"        // Anonymous confession prompts
    const val SECRET_COMPLIMENT   = "secret_compliment"    // One anonymous compliment per circle

    // Dev tools (DEBUG builds only)
    const val DEV_INSPECTOR = "dev_inspector"

    // Admin
    const val ADMIN_DASHBOARD = "admin_dashboard"

    // Founders — early believers page
    const val FOUNDERS = "founders"

    // Introvert Feed — anonymous posting feed
    const val INTROVERT_FEED = "introvert_feed"

    // ── Swipe feature ──────────────────────────────────────────────────────────
    const val SWIPE_PROFILE_SETUP = "swipe_profile_setup"  // one-time swipe profile creation
    const val SWIPE               = "swipe"                // main card stack swipe screen
    const val MATCHES_SCREEN      = "matches_screen"       // matches and compliments screen
    const val CONNECTION_ROOM     = "connection_room"      // + "/{matchId}"

    // ── Post Feed ──────────────────────────────────────────────────────────────
    const val FEED          = "feed"             // introvert post feed
    const val FEED_DM_INBOX = "feed_dm_inbox"    // DM inbox inside feed
    const val COOKIE_STORE  = "cookie_store"     // Cookie purchase screen
    const val USER_PROFILE  = "user_profile/{uid}" // Public profile viewer
    // ── Shared Listening Rooms ───────────────────────────────────────────────
    const val LISTENING_LOBBY = "listening_lobby"
    const val LISTENING_ROOM  = "listening_room/{roomId}/{amHost}"
    fun listeningRoom(roomId: String, amHost: Boolean) = "listening_room/$roomId/$amHost"

    // ── Invite Friends ─────────────────────────────────────────────────────────
    const val INVITE_FRIENDS = "invite_friends"

    // ── Draw & Guess ──────────────────────────────────────────────────────────
    const val DRAW_GUESS_WAITING     = "draw_guess_waiting/{roomId}/{matchId}"
    const val DRAW_GUESS_MATCH_FOUND = "draw_guess_match_found/{roomId}/{matchId}"
    const val DRAW_GUESS_GAME        = "draw_guess_game/{roomId}/{matchId}"
    const val DRAW_GUESS_RESULT      = "draw_guess_result/{roomId}/{matchId}"
    const val DRAW_GUESS_PUBLIC      = "draw_guess_public"
    fun drawGuessWaiting(roomId: String, matchId: String)    = "draw_guess_waiting/$roomId/$matchId"
    fun drawGuessMatchFound(roomId: String, matchId: String) = "draw_guess_match_found/$roomId/$matchId"
    fun drawGuessGame(roomId: String, matchId: String)       = "draw_guess_game/$roomId/$matchId"
    fun drawGuessResult(roomId: String, matchId: String)     = "draw_guess_result/$roomId/$matchId"

    // ── Voice Rooms (LiveKit) ───────────────────────────────────────────────────
    const val VOICE_ROOM_LOBBY  = "voice_room_lobby"
    const val VOICE_ROOM_ACTIVE = "voice_room_active/{roomId}"
    fun voiceRoomActive(roomId: String) = "voice_room_active/$roomId"

    // ── Group Chat ───────────────────────────────────────────────────────────────
    const val GROUP_ROOMS     = "group_rooms"
    const val GROUP_CHAT_ROOM = "group_chat_room/{roomId}"
    fun groupChatRoom(roomId: String) = "group_chat_room/$roomId"
}
