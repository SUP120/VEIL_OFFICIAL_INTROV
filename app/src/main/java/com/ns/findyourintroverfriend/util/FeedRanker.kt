package com.ns.findyourintroverfriend.util

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * VeilFeedRanker — Instagram/YouTube-style feed ranking.
 *
 * Algorithm:
 *   • Top 3 most recent posts are ALWAYS pinned at positions #1, #2, #3
 *     (guarantees fresh content is immediately visible to everyone).
 *   • Remaining posts ranked by:
 *       score = (engagementScore * recencyFactor) + sessionNoise
 *       engagementScore  = likes * 3 + comments * 2
 *       recencyFactor    = exp(-age_hours / HALF_LIFE)  (48h half-life)
 *       sessionNoise     = seeded per (postId + sessionSalt) — reshuffles on refresh
 */
object FeedRanker {

    /** Rotated on every pull-to-refresh */
    private var SESSION_SALT = Random.nextLong()

    fun newSession() { SESSION_SALT = Random.nextLong() }

    private const val HALF_LIFE_HOURS = 48.0
    private const val NOISE_WEIGHT    = 0.35f

    fun <T> rank(
        posts          : List<T>,
        getPostId      : (T) -> String,
        getLikeCount   : (T) -> Int,
        getCommentCount: (T) -> Int,
        getCreatedAt   : (T) -> Long,
        isDeveloper    : (T) -> Boolean = { false }
    ): List<T> {
        if (posts.isEmpty()) return posts

        val now = System.currentTimeMillis()

        // ── Step 1: Pin top 3 most recent posts first ─────────────────────
        val sortedByTime  = posts.sortedByDescending { getCreatedAt(it) }
        val top3          = sortedByTime.take(3)
        val top3Ids       = top3.map { getPostId(it) }.toSet()
        val rest          = posts.filter { getPostId(it) !in top3Ids }

        // ── Step 2: Rank the remaining posts ──────────────────────────────
        val rankedRest = rest.sortedByDescending { post ->
            if (isDeveloper(post)) return@sortedByDescending Double.MAX_VALUE

            val ageMs    = (now - getCreatedAt(post)).coerceAtLeast(0L)
            val ageHours = ageMs / 3_600_000.0
            val engagement = getLikeCount(post) * 3.0 + getCommentCount(post) * 2.0
            val recency    = exp(-ageHours * ln(2.0) / HALF_LIFE_HOURS)
            val postSeed   = getPostId(post).hashCode().toLong() xor SESSION_SALT
            val noise      = Random(postSeed).nextDouble()
            val baseScore  = (engagement + 1.0) * recency
            baseScore * (1.0 - NOISE_WEIGHT) + noise * NOISE_WEIGHT * baseScore.coerceAtLeast(1.0)
        }

        // ── Step 3: Prepend pinned top-3 ─────────────────────────────────
        return top3 + rankedRest
    }
}
