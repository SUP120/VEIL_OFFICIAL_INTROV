package com.ns.findyourintroverfriend.aifriend

import androidx.annotation.DrawableRes
import com.ns.findyourintroverfriend.R

internal data class AiFriend(
    val id: String,
    val name: String,
    @DrawableRes val portrait: Int,
    val style: String,
    val description: String,
    val starters: List<String>
)

internal object VeilFriends {
    val all = listOf(
        AiFriend("luna", "Luna", R.drawable.veil_friend_luna, "Reflective",
            "For the thoughts that stay with you after everyone goes home.",
            listOf("Help me untangle a thought", "Let's talk about my day")),
        AiFriend("blaze", "Blaze", R.drawable.veil_friend_blaze, "Playful",
            "A little banter, a funny story, or a lighter take on an awkward day.",
            listOf("Tell me a silly story", "Help me laugh about an awkward moment")),
        AiFriend("rose", "Rose", R.drawable.veil_friend_rose, "Creative",
            "Finds a story in small moments and gives you a place to get creative.",
            listOf("Give me a writing prompt", "Help me put a feeling into words")),
        AiFriend("sage", "Sage", R.drawable.veil_friend_sage, "Curious",
            "Good company for big questions and decisions with no obvious answer.",
            listOf("Help me think through a decision", "Ask me an interesting question")),
        AiFriend("ego", "Ego", R.drawable.veil_friend_ego, "Confident",
            "Practice saying what you mean, setting a boundary, or speaking up.",
            listOf("Help me say no kindly", "Practice speaking up with me")),
        AiFriend("kira", "Kira", R.drawable.veil_friend_kira, "Quiet listener",
            "Room to vent, pause, or find words for how you feel.",
            listOf("Can I vent for a minute?", "Help me name what I'm feeling")),
        AiFriend("nox", "Nox", R.drawable.veil_friend_nox, "Imaginative",
            "Dry humor, strange hypotheticals, and stories you can make up together.",
            listOf("Let's invent a tiny world", "Give me a strange what-if")),
        AiFriend("rex", "Rex", R.drawable.veil_friend_rex, "Straight-talking",
            "Break an awkward conversation or a big decision into a first step.",
            listOf("Help me start a conversation", "Give me one practical next step"))
    )

    fun find(id: String): AiFriend? = all.firstOrNull { it.id == id }
}

internal data class AiMessage(
    val id: String,
    val sender: String,
    val content: String,
    val createdAt: Long
)

internal data class AiUsage(
    val freeLimit: Int,
    val freeRemaining: Int,
    val cookieCost: Int,
    val cookieBalance: Int
)

internal data class AiReply(
    val conversationId: String,
    val messages: List<AiMessage>,
    val usage: AiUsage
)

internal data class AiMemory(val id: String, val text: String)

internal const val AI_MESSAGE_LIMIT = 700
