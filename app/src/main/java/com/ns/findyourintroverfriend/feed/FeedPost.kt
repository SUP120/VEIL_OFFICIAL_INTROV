package com.ns.findyourintroverfriend.feed

// ── Data models for the Veil Post Feed ───────────────────────────────────────

data class VeilPost(
    val postId        : String       = "",
    val authorUid     : String       = "",
    val authorAlias   : String       = "",
    val authorPhotoUrl: String       = "",
    val authorAvatar  : String       = "",   // local drawable name e.g. "avatarb1"
    val caption       : String       = "",
    val imageUrls     : List<String> = emptyList(),
    val likedBy       : List<String> = emptyList(),
    val commentsCount : Long         = 0L,
    val createdAt     : Long         = 0L
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
