package com.ns.findyourintroverfriend.swipe

// ─────────────────────────────────────────────────────────────────────────────
//  SwipeProfile — stored at swipeProfiles/{uid} in Firestore
// ─────────────────────────────────────────────────────────────────────────────

data class SwipeProfile(
    val uid            : String  = "",
    val alias          : String  = "",
    val age            : Int     = 0,
    val gender         : String  = "",
    val photoUrl       : String  = "",
    val isCameraShy    : Boolean = false,   // user prefers to hide photo
    val avatarResName  : String  = "",       // e.g. "avatarb1" or "avatarg3" — drawable name

    // ── Location ─────────────────────────────────────────────────────────────
    val country        : String  = "",      // e.g. "India", "United States"
    val state          : String  = "",      // e.g. "Maharashtra", "California"
    val city           : String  = "",      // optional finer grain
    val timezone       : String  = "",      // e.g. "Asia/Kolkata"

    // ── Introvert Core ───────────────────────────────────────────────────────
    val comfortWhen    : String  = "",
    val perfectEvening : String  = "",
    val openUpWhen     : String  = "",
    val thingsIAvoid   : String  = "",

    // ── Style Toggles ─────────────────────────────────────────────────────────
    val placePreference: String  = "quiet",   // "quiet" | "social"
    val commStyle      : String  = "text",    // "text" | "call"
    val sleepStyle     : String  = "nightowl",// "nightowl" | "earlybird"

    // ── Personality Tags ─────────────────────────────────────────────────────
    val introvertTags  : List<String> = emptyList(), // e.g. ["deep thinker","book lover"]
    val comfortLevel   : Int     = 3,         // 1 (very private) – 5 (somewhat open)

    // ── Availability ─────────────────────────────────────────────────────────
    val preferredTimeSlots : List<String> = emptyList(), // "morning","afternoon","evening","night"
    val availableTonight   : String = "",     // e.g. "8–10 PM" or ""

    // ── Conversation Starters ────────────────────────────────────────────────
    val askMeAbout     : String  = "",
    val talkHoursAbout : String  = "",
    val hobbies        : List<String> = emptyList(),

    // ── Activity (updated by backend / app) ───────────────────────────────────
    val activityScore  : Float   = 0f,
    val lastActiveTime : Long    = 0L,

    // ── Daily limits ──────────────────────────────────────────────────────────
    val dailySwipesUsed      : Int    = 0,
    val dailySwipesResetDate : String = "",
    val dailyComplimentsUsed : Int    = 0,
    val dailyComplimentsDate : String = "",

    // ── System ────────────────────────────────────────────────────────────────
    val isProfileComplete: Boolean = false,
    val lastActive       : Long    = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid"                  to uid,
        "alias"                to alias,
        "age"                  to age,
        "gender"               to gender,
        "photoUrl"             to photoUrl,
        "isCameraShy"          to isCameraShy,
        "avatarResName"        to avatarResName,
        "country"              to country,
        "state"                to state,
        "city"                 to city,
        "timezone"             to timezone,
        "comfortWhen"          to comfortWhen,
        "perfectEvening"       to perfectEvening,
        "openUpWhen"           to openUpWhen,
        "thingsIAvoid"         to thingsIAvoid,
        "placePreference"      to placePreference,
        "commStyle"            to commStyle,
        "sleepStyle"           to sleepStyle,
        "introvertTags"        to introvertTags,
        "comfortLevel"         to comfortLevel,
        "preferredTimeSlots"   to preferredTimeSlots,
        "availableTonight"     to availableTonight,
        "askMeAbout"           to askMeAbout,
        "talkHoursAbout"       to talkHoursAbout,
        "hobbies"              to hobbies,
        "activityScore"        to activityScore,
        "lastActiveTime"       to lastActiveTime,
        "dailySwipesUsed"      to dailySwipesUsed,
        "dailySwipesResetDate" to dailySwipesResetDate,
        "dailyComplimentsUsed" to dailyComplimentsUsed,
        "dailyComplimentsDate" to dailyComplimentsDate,
        "isProfileComplete"    to isProfileComplete,
        "lastActive"           to lastActive
    )
}

// ── Match document ─────────────────────────────────────────────────────────────
data class SwipeMatch(
    val matchId   : String = "",
    val userA     : String = "",
    val userB     : String = "",
    val aliasA    : String = "",
    val aliasB    : String = "",
    val photoA    : String = "",
    val photoB    : String = "",
    val avatarA   : String = "",
    val avatarB   : String = "",
    val matchedAt : Long   = 0L,
    val source    : String = "swipe",  // "swipe" | "friend"
    val icebreakerAnsweredA : String  = "",
    val icebreakerAnsweredB : String  = "",
    val chatUnlocked        : Boolean = false
)

// ── Message inside Connection Room ────────────────────────────────────────────
data class SwipeMessage(
    val messageId   : String  = "",
    val senderUid   : String  = "",
    val senderAlias : String  = "",
    val text        : String  = "",
    val sentAt      : Long    = 0L,
    val isSticker   : Boolean = false,   // true when text is "sticker_pack1_N"
    val imageUrl    : String  = "",      // non-empty for image messages
    val replyToMsgId: String  = "",      // id of quoted message
    val replyToText : String  = "",      // snippet of quoted text (or "[Image]")
    val replyToAlias: String  = ""       // alias of quoted sender
)

// ── Compliment received ───────────────────────────────────────────────────────
data class SwipeCompliment(
    val fromUid      : String  = "",
    val fromAlias    : String  = "",
    val fromPhotoUrl : String  = "",   // sender's photo (or empty if camera shy)
    val fromAvatar   : String  = "",   // sender's avatar res name if camera shy
    val message      : String  = "",
    val at           : Long    = 0L,
    val isRead       : Boolean = false,
    // "pending" → awaiting receiver decision
    // "accepted" → receiver accepted → DM unlocked
    // "rejected" → receiver declined
    val status       : String  = "pending"
)

// ── Sent compliment — stored at compliments/{senderUid}/sent/{receiverUid} ────
data class SentCompliment(
    val toUid    : String = "",
    val toAlias  : String = "",
    val message  : String = "",
    val at       : Long   = 0L,
    val status   : String = "pending"  // "pending" | "accepted" | "rejected"
)

// ── Pending like — someone swiped right on me, awaiting my response ───────────
data class PendingLike(
    val fromUid      : String = "",
    val fromAlias    : String = "",
    val fromPhotoUrl : String = "",
    val fromAvatar   : String = "",   // drawable res name e.g. "avatarb1"
    val at           : Long   = 0L
)

const val DAILY_SWIPE_LIMIT      = 15
const val DAILY_COMPLIMENT_LIMIT = 2

// ── Country → States map (add more as needed) ─────────────────────────────────
val COUNTRY_STATES: Map<String, List<String>> = mapOf(
    "India" to listOf(
        "Andhra Pradesh","Arunachal Pradesh","Assam","Bihar","Chhattisgarh",
        "Goa","Gujarat","Haryana","Himachal Pradesh","Jharkhand","Karnataka",
        "Kerala","Madhya Pradesh","Maharashtra","Manipur","Meghalaya","Mizoram",
        "Nagaland","Odisha","Punjab","Rajasthan","Sikkim","Tamil Nadu","Telangana",
        "Tripura","Uttar Pradesh","Uttarakhand","West Bengal",
        "Delhi","Chandigarh","Puducherry","Jammu & Kashmir","Ladakh"
    ),
    "United States" to listOf(
        "Alabama","Alaska","Arizona","Arkansas","California","Colorado","Connecticut",
        "Delaware","Florida","Georgia","Hawaii","Idaho","Illinois","Indiana","Iowa",
        "Kansas","Kentucky","Louisiana","Maine","Maryland","Massachusetts","Michigan",
        "Minnesota","Mississippi","Missouri","Montana","Nebraska","Nevada",
        "New Hampshire","New Jersey","New Mexico","New York","North Carolina",
        "North Dakota","Ohio","Oklahoma","Oregon","Pennsylvania","Rhode Island",
        "South Carolina","South Dakota","Tennessee","Texas","Utah","Vermont",
        "Virginia","Washington","West Virginia","Wisconsin","Wyoming"
    ),
    "United Kingdom" to listOf(
        "England","Scotland","Wales","Northern Ireland"
    ),
    "Canada" to listOf(
        "Alberta","British Columbia","Manitoba","New Brunswick",
        "Newfoundland and Labrador","Nova Scotia","Ontario","Prince Edward Island",
        "Quebec","Saskatchewan"
    ),
    "Australia" to listOf(
        "New South Wales","Victoria","Queensland","Western Australia",
        "South Australia","Tasmania","ACT","Northern Territory"
    ),
    "Germany" to listOf(
        "Bavaria","Berlin","Brandenburg","Bremen","Hamburg","Hesse",
        "Lower Saxony","Mecklenburg-Vorpommern","North Rhine-Westphalia",
        "Rhineland-Palatinate","Saarland","Saxony","Saxony-Anhalt",
        "Schleswig-Holstein","Thuringia"
    ),
    "Other" to listOf("Not listed")
)

val ALL_COUNTRIES: List<String> = COUNTRY_STATES.keys.toList()

val INTROVERT_TAGS: List<String> = listOf(
    "Deep thinker","Book lover","Night thinker","Creative soul","Quiet observer",
    "Coffee person","Nature lover","Music listener","Film buff","Homebody",
    "Overthinker","Selective talker","Empathetic","Slow to trust","Thoughtful",
    "Minimalist","Journal writer","Curious mind","Late night texter","Daydreamer"
)

val TIME_SLOTS: List<String> = listOf("Morning","Afternoon","Evening","Night","Late night")
