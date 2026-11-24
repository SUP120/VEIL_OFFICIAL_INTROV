package com.ns.findyourintroverfriend.feed

// ── Data models for the Veil Post Feed ───────────────────────────────────────

data class VeilPost(
    val postId        : String                    = "",
    val authorUid     : String                    = "",
    val authorAlias   : String                    = "",
    val authorPhotoUrl: String                    = "",
    val authorAvatar  : String                    = "",
    val caption       : String                    = "",
    val imageUrls     : List<String>              = emptyList(),
    val likedBy       : List<String>              = emptyList(),   // union of all reactors (ranker compat)
    val reactions     : Map<String, List<String>> = emptyMap(),    // emoji → [uid, ...]
    val commentsCount : Long                      = 0L,
    val createdAt     : Long                      = 0L,
    val tag           : String                    = "",            // "🌙 Night Thoughts", "📸 Aesthetic", etc.
    val editedAt      : Long                      = 0L,
    val pollOptions   : List<String>              = emptyList(),
    val pollCounts    : List<Long>                = emptyList()
)

internal val FEED_REACTION_LABELS = linkedMapOf(
    "❤️" to "Love", "🥀" to "Felt this", "🌙" to "Relatable", "😂" to "Funny", "🤍" to "Support"
)

internal fun VeilPost.displayReactions(): Map<String, List<String>> {
    val reacted = reactions.values.flatten().toSet()
    val olderLikes = likedBy.filterNot { it in reacted }
    return if (olderLikes.isEmpty()) reactions else reactions + ("❤️" to (reactions["❤️"].orEmpty() + olderLikes))
}

internal fun VeilPost.reactionFor(uid: String): String? =
    reactions.entries.firstOrNull { uid in it.value }?.key ?: "❤️".takeIf { uid in likedBy }

internal fun VeilPost.withReaction(uid: String, emoji: String?): VeilPost {
    val updated = reactions.toMutableMap()
    for ((reaction, users) in reactions) {
        if (uid !in users) continue
        val remaining = users.filterNot { it == uid }
        if (remaining.isEmpty()) updated.remove(reaction) else updated[reaction] = remaining
    }
    if (emoji != null) updated[emoji] = updated[emoji].orEmpty() + uid
    val others = likedBy.filterNot { it == uid }
    return copy(reactions = updated, likedBy = if (emoji == null) others else others + uid)
}

internal data class SavedFeedPosts(
    val posts: List<VeilPost> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val error: Boolean = false
)

internal const val MAX_POLL_OPTIONS = 4
internal const val MAX_POLL_OPTION_LENGTH = 72
internal const val MAX_POLL_QUESTION_LENGTH = 240

internal fun validPollDraft(question: String, options: List<String>): Boolean =
    question.trim().length in 1..MAX_POLL_QUESTION_LENGTH && options.size in 2..MAX_POLL_OPTIONS &&
        options.all { it.isNotBlank() && it.length <= MAX_POLL_OPTION_LENGTH } &&
        options.map { it.trim().lowercase() }.distinct().size == options.size

internal data class FeedPollVote(
    val choice: Int? = null,
    val isBusy: Boolean = false,
    val loadFailed: Boolean = false
)

data class VeilComment(
    val commentId      : String       = "",
    val authorUid      : String       = "",
    val authorAlias    : String       = "",
    val authorPhotoUrl : String       = "",
    val authorAvatar   : String       = "",
    val text           : String       = "",
    val likedBy        : List<String> = emptyList(),
    val createdAt      : Long         = 0L,
    val replyCount     : Int          = 0      // ← tracks nested replies
)

data class VeilReply(
    val replyId        : String = "",
    val authorUid      : String = "",
    val authorAlias    : String = "",
    val authorPhotoUrl : String = "",
    val authorAvatar   : String = "",
    val text           : String = "",
    val replyToAlias   : String = "",   // the @alias being replied to
    val createdAt      : Long   = 0L
)
