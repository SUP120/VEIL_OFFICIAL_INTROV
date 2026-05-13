package com.ns.findyourintroverfriend.data

/**
 * Single source of truth for all Firestore field names on the `groups/{id}` document.
 * Use these everywhere instead of raw strings to prevent field-name drift bugs.
 */
object GroupFields {
    const val MEMBERS        = "members"       // List<String> — UIDs of all 4 members
    const val DAY            = "day"           // Int 1..3
    const val DAY_STARTED_AT = "dayStartedAt"  // Timestamp — when current day began (set by Cloud Function)
    const val CEREMONY_DONE  = "ceremonyDone"  // Boolean
    const val CREATED_AT     = "createdAt"     // Timestamp
}

/**
 * Single source of truth for `users/{uid}` document field names.
 */
object UserFields {
    const val GROUP_ID          = "groupId"
    const val FCM_TOKEN         = "fcmToken"
    const val PROFILE_COMPLETE  = "profileComplete"
    const val ALIAS             = "alias"
    const val GENDER            = "gender"
    const val EMOJI             = "emoji"
    const val CEREMONY_DONE     = "ceremonyDone"
    const val PARTY_SESSION_ID  = "partySessionId"
    const val CIRCLE_REVEAL_SEEN = "circleRevealSeen"
}
