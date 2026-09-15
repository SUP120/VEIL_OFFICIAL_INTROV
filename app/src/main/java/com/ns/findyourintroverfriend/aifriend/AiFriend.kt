package com.ns.findyourintroverfriend.aifriend

// ─────────────────────────────────────────────────────────────────────────────
//  VEIL Friends — Domain Models
// ─────────────────────────────────────────────────────────────────────────────

data class AiFriend(
    val id             : String,
    val name           : String,
    val emoji          : String,
    val tagline        : String,
    val statusLine     : String,
    val description    : String,
    val personalityTags: List<String>,
    val conversationTopics: List<String>,
    val backgroundAsset: String,     // drawable name for chat background
)

data class AiMessage(
    val id        : String = "",
    val sender    : String = "user",   // "user" | "assistant"
    val content   : String = "",
    val createdAt : Long   = 0L,
)

data class AiMemory(
    val id         : String = "",
    val memoryType : String = "general",
    val memoryText : String = "",
    val importance : Double = 0.5,
    val isDeleted  : Boolean = false,
)

data class AiConversation(
    val id            : String = "",
    val friendId      : String = "",
    val title         : String = "",
    val summary       : String = "",
    val lastMessageAt : Long   = 0L,
    val messageCount  : Int    = 0,
)

// ── All 8 launch personalities (defined client-side for display only) ──────
object VeilFriends {
    val all: List<AiFriend> = listOf(
        AiFriend(
            id = "luna", name = "Luna", emoji = "🌙",
            tagline = "The Late-Night Friend",
            statusLine = "Here for you",
            description = "Some conversations are better after midnight.",
            personalityTags = listOf("Calm", "Deep", "Curious", "Patient"),
            conversationTopics = listOf("Life questions", "Relationships", "Dreams", "Random thoughts"),
            backgroundAsset = "luna_bg"
        ),
        AiFriend(
            id = "blaze", name = "Blaze", emoji = "😄",
            tagline = "The Playful Friend",
            statusLine = "Ready to vibe",
            description = "Here to make you smile, even when nothing is funny.",
            personalityTags = listOf("Funny", "Teasing", "Energetic", "Warm"),
            conversationTopics = listOf("Random chaos", "Pop culture", "Your day", "Bad jokes"),
            backgroundAsset = "blaze_bg"
        ),
        AiFriend(
            id = "rose", name = "Rose", emoji = "🌸",
            tagline = "The Romanticizing Friend",
            statusLine = "Finding beauty in everything",
            description = "She turns ordinary moments into poetry.",
            personalityTags = listOf("Poetic", "Optimistic", "Expressive", "Gentle"),
            conversationTopics = listOf("Little things", "Feelings", "Art", "Everyday magic"),
            backgroundAsset = "rose_bg"
        ),
        AiFriend(
            id = "sage", name = "Sage", emoji = "🧠",
            tagline = "The Philosopher Friend",
            statusLine = "Thinking deeply",
            description = "Every question leads to a better question.",
            personalityTags = listOf("Curious", "Analytical", "Deep", "Thoughtful"),
            conversationTopics = listOf("Existential questions", "Human nature", "Purpose", "Ideas"),
            backgroundAsset = "sage_bg"
        ),
        AiFriend(
            id = "ego", name = "Ego", emoji = "👑",
            tagline = "The Egoistic but Loving Friend",
            statusLine = "Always right (lovingly)",
            description = "Confident, a little arrogant, but fiercely protective of you.",
            personalityTags = listOf("Confident", "Protective", "Funny", "Loving"),
            conversationTopics = listOf("Self-respect", "Decisions", "Relationships", "Life choices"),
            backgroundAsset = "ego_bg"
        ),
        AiFriend(
            id = "kira", name = "Kira", emoji = "🤫",
            tagline = "The Listener Friend",
            statusLine = "I'm listening",
            description = "She doesn't talk much. She doesn't need to.",
            personalityTags = listOf("Present", "Quiet", "Empathetic", "Safe"),
            conversationTopics = listOf("Anything on your mind", "Feelings", "Things you can't say out loud"),
            backgroundAsset = "kira_bg"
        ),
        AiFriend(
            id = "nox", name = "Nox", emoji = "🌀",
            tagline = "The Delusional Friend",
            statusLine = "Making it a movie",
            description = "Everything is dramatic. Everything is a sign.",
            personalityTags = listOf("Absurd", "Dramatic", "Playful", "Hilarious"),
            conversationTopics = listOf("Overthinking", "Crush updates", "Signs from the universe", "Life drama"),
            backgroundAsset = "nox_bg"
        ),
        AiFriend(
            id = "rex", name = "Rex", emoji = "🎯",
            tagline = "The Unfiltered Friend",
            statusLine = "No sugarcoating",
            description = "He'll tell you what you need to hear, not what you want.",
            personalityTags = listOf("Direct", "Honest", "Real", "Grounding"),
            conversationTopics = listOf("Hard truths", "Decisions", "Self-awareness", "Accountability"),
            backgroundAsset = "rex_bg"
        ),
    )
    fun find(id: String) = all.firstOrNull { it.id == id }
}
