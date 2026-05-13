package com.ns.findyourintroverfriend.util

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * VeilFeedRanker — Instagram/YouTube-style feed ranking.
 *
 * Algorithm:
 *   score = (engagementScore * recencyFactor) + sessionNoise
 *
 *   engagementScore  = likes * 3 + comments * 2      (rewards popular content)
 *   recencyFactor    = exp(-age_hours / HALF_LIFE)    (exponential decay, 48h half-life)
 *   sessionNoise     = seeded per (postId + sessionSalt) so each refresh reshuffles
 *
 * Result: recent + popular posts surface first, but order is never exactly the same twice.
 * Developer posts always float to the top.
 */
object FeedRanker {

    /** Changes every app session → guarantees a different shuffle each time */
    private val SESSION_SALT = Random.nextLong()

    /** Half-life in hours: posts lose half their recency score after this many hours */
    private const val HALF_LIFE_HOURS = 48.0

    /** Weight of random noise vs engagement (0–1). Higher = more random, lower = more engagement-driven) */
    private const val NOISE_WEIGHT = 0.35f

    /**
     * Ranks a list of posts using the scoring algorithm.
     *
     * @param posts          List of any type — provide mappers for the fields needed.
     * @param getPostId      Returns the unique post ID string.
     * @param getLikeCount   Returns like count.
     * @param getCommentCount Returns comment count.
     * @param getCreatedAt   Returns creation timestamp in milliseconds.
     * @param isDeveloper    Returns true if the post is from a developer account.
     * @return Posts sorted by score descending (best first).
     */
    fun <T> rank(
        posts         : List<T>,
        getPostId     : (T) -> String,
        getLikeCount  : (T) -> Int,
        getCommentCount: (T) -> Int,
        getCreatedAt  : (T) -> Long,
        isDeveloper   : (T) -> Boolean = { false }
    ): List<T> {
        val now = System.currentTimeMillis()
        return posts.sortedByDescending { post ->
            // Developer posts always pin to the top
            if (isDeveloper(post)) return@sortedByDescending Double.MAX_VALUE

            val ageMs    = (now - getCreatedAt(post)).coerceAtLeast(0L)
            val ageHours = ageMs / 3_600_000.0

            // Engagement score (normalised so a post with 0 likes still gets a chance)
            val engagement = getLikeCount(post) * 3.0 + getCommentCount(post) * 2.0

            // Exponential decay: score halves every HALF_LIFE_HOURS
            val recency = exp(-ageHours * ln(2.0) / HALF_LIFE_HOURS)

            // Per-post session noise: deterministic for this session, random across sessions
            val postSeed = getPostId(post).hashCode().toLong() xor SESSION_SALT
            val noise    = Random(postSeed).nextDouble()   // 0.0 – 1.0

            // Final score
            val baseScore = (engagement + 1.0) * recency
            baseScore * (1.0 - NOISE_WEIGHT) + noise * NOISE_WEIGHT * baseScore.coerceAtLeast(1.0)
        }
    }
}
