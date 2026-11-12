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
)

// ── Post tags ─────────────────────────────────────────────────────────────────
val VEIL_POST_TAGS = listOf("🌙 Night Thoughts", "📸 Aesthetic", "🌿 Calm", "💬 Vent", "✨ Just Vibing")


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
