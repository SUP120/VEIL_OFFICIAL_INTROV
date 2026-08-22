package com.ns.findyourintroverfriend.voiceroom

import com.google.firebase.Timestamp

// ─────────────────────────────────────────────────────────────────────────────
//  Whisper Voice Rooms — constants
// ─────────────────────────────────────────────────────────────────────────────

const val WHISPER_FREE_MINS         = 15L   // free session duration
const val WHISPER_EXTENSION_MINS    = 15L   // extension per cookie spend
const val WHISPER_EXTENSION_COOKIES = 5     // cost per extension (5 cookies)
const val WHISPER_MAX_EXTENSIONS    = 3     // max extensions per room

// ─────────────────────────────────────────────────────────────────────────────
//  Supporting data classes
// ─────────────────────────────────────────────────────────────────────────────

data class JoinRequest(
    val uid        : String = "",
    val alias      : String = "",
    val emoji      : String = "🌙",
    val requestedAt: Long   = 0L,
)

data class RaisedHand(
    val uid      : String = "",
    val alias    : String = "",
    val emoji    : String = "🌙",
    val raisedAt : Long   = 0L,
)

// ─────────────────────────────────────────────────────────────────────────────
//  VoiceRoom — primary Firestore document model
// ─────────────────────────────────────────────────────────────────────────────

data class VoiceRoom(
    val roomId          : String            = "",
    val hostUid         : String            = "",
    val hostAlias       : String            = "",
    val hostEmoji       : String            = "🌙",
    val topic           : String            = "",
    val description     : String            = "",   // optional room description
    val language        : String            = "",   // optional language
    val isLocked        : Boolean           = false, // private room (requires approval)
    val tags            : List<String>      = emptyList(),
    val isLive          : Boolean           = true,
    val participantUids : List<String>      = emptyList(),
    val participantCount: Int               = 0,
    val maxParticipants : Int               = 6,
    val speakerUids     : List<String>      = emptyList(), // non-host speakers with mic
    val joinRequests    : List<JoinRequest> = emptyList(), // pending join requests
    val raisedHands     : List<RaisedHand>  = emptyList(), // voice queue
    val mutedByHost     : List<String>      = emptyList(), // host-muted participant uids
    val kickedUids      : List<String>      = emptyList(), // removed participants
    val extensionCount  : Int               = 0,           // how many times extended
    val createdAt       : Timestamp?        = null,
    val expiresAt       : Timestamp?        = null,
    val closedReason    : String            = "",
) {
    /** Remaining seconds — returns free-tier default when expiresAt not yet synced */
    fun remainingSecs(): Long {
        val expMs = expiresAt?.toDate()?.time ?: return WHISPER_FREE_MINS * 60L
        return (expMs - System.currentTimeMillis()) / 1000L
    }

    val isFull get() = participantCount >= maxParticipants

    fun remainingLabel(): String {
        val secs = remainingSecs().coerceAtLeast(0L)
        return "%02d:%02d".format(secs / 60, secs % 60)
    }

    /** Elapsed time since room was created, human-readable */
    fun ageLabel(): String {
        val ageMs = System.currentTimeMillis() - (createdAt?.toDate()?.time ?: return "")
        val secs  = ageMs / 1000L
        return when {
            secs < 60    -> "just now"
            secs < 3600  -> "${secs / 60} min ago"
            else         -> "${secs / 3600}h ago"
        }
    }

    /** Can the room still be extended? */
    val canExtend get() = extensionCount < WHISPER_MAX_EXTENSIONS
}

// ─────────────────────────────────────────────────────────────────────────────
//  Lobby UI state
// ─────────────────────────────────────────────────────────────────────────────

data class VoiceRoomLobbyUiState(
    val rooms      : List<VoiceRoom> = emptyList(),
    val isLoading  : Boolean         = true,
    val error      : String?         = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Active-room phases
// ─────────────────────────────────────────────────────────────────────────────

enum class RoomPhase {
    CONNECTING,        // fetching token, joining LiveKit
    PENDING_APPROVAL,  // locked room — waiting for host to approve
    ACTIVE,            // audio flowing
    EXPIRING,          // last 2 min → show extension prompt
    EXPIRED,           // 15-min free limit reached
    ERROR,             // connection / token failure
    LEFT,              // user or host ended
    REJECTED,          // host rejected join request
}

// ─────────────────────────────────────────────────────────────────────────────
//  Active-room UI state (merged lobby + livekit state)
// ─────────────────────────────────────────────────────────────────────────────

data class VoiceRoomActiveUiState(
    val room               : VoiceRoom?          = null,
    val phase              : RoomPhase           = RoomPhase.CONNECTING,
    val isMuted            : Boolean             = false,
    val remainingSecs      : Long                = WHISPER_FREE_MINS * 60L,
    val activeSpeakers     : Set<String>         = emptySet(),
    // Real display names fetched from Firestore
    val participantAliases : Map<String, String> = emptyMap(), // uid → alias
    val participantEmojis  : Map<String, String> = emptyMap(), // uid → emoji
    // Cookie state
    val cookieBalance      : Int                 = 0,
    val showExtendDialog   : Boolean             = false,
    val isExtending        : Boolean             = false,
    // Personal state
    val hasRaisedHand      : Boolean             = false,
    val error              : String?             = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Mood tags & languages
// ─────────────────────────────────────────────────────────────────────────────

val WHISPER_TAGS = listOf(
    "Night talks 🌙",
    "Deep thoughts 💭",
    "Vent freely 🌊",
    "Just vibing ✨",
    "Study together 📚",
    "Chill & quiet 🍃",
)

val WHISPER_LANGUAGES = listOf(
    "Any", "English", "Hindi", "Tamil", "Telugu",
    "Malayalam", "Bengali", "Marathi", "Kannada", "Punjabi",
)
