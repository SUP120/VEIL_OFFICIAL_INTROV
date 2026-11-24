package com.ns.findyourintroverfriend.util

import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

object FeedRanker {
    private var sessionSalt = Random.nextLong()

    fun newSession() { sessionSalt = Random.nextLong() }

    fun <T> rank(
        posts: List<T>,
        getPostId: (T) -> String,
        getLikeCount: (T) -> Int,
        getCommentCount: (T) -> Int,
        getCreatedAt: (T) -> Long,
        isDeveloper: (T) -> Boolean = { false },
        getAuthorId: (T) -> String = { "" },
        getAffinity: (T) -> Double = { 0.0 },
        getVoteCount: (T) -> Long = { 0L },
        reserveFresh: Boolean = true,
        lastViewedAt: (T) -> Long = { 0L },
        explorationSeed: Long? = null,
        precedingAuthors: List<String> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): List<T> {
        val candidates = posts.distinctBy(getPostId)
        fun author(post: T) = getAuthorId(post).ifBlank { getPostId(post) }
        fun ageHours(post: T) = (now - getCreatedAt(post)).coerceAtLeast(0L) / 3_600_000.0
        fun soonAfterView(post: T) = lastViewedAt(post).let { it > 0 && now - it < 72 * 3_600_000L }

        val fresh = if (reserveFresh) candidates.filter { ageHours(it) <= 12 && !soonAfterView(it) }
            .sortedByDescending(getCreatedAt).distinctBy { author(it) }.take(if (explorationSeed == null) 2 else 1)
            else emptyList()
        val freshIds = fresh.mapTo(mutableSetOf(), getPostId)
        val remaining = candidates.filterNot { getPostId(it) in freshIds }.map { post ->
            val age = ageHours(post)
            val freshness = 0.5.pow(age / 40.0)
            val engagement = ln(1.0 + getLikeCount(post).coerceAtLeast(0)) +
                1.3 * ln(1.0 + getCommentCount(post).coerceAtLeast(0)) +
                0.5 * ln(1.0 + getVoteCount(post).coerceAtLeast(0))
            val affinity = getAffinity(post).coerceIn(0.0, 1.0)
            val announcement = if (isDeveloper(post) && age <= 72) 0.35 else 0.0
            val random = Random(getPostId(post).hashCode().toLong() xor (explorationSeed ?: sessionSalt)).nextDouble()
            val relevance = (2.0 + engagement.coerceAtMost(9.0) + affinity * 1.6 + announcement) * freshness
            val score = if (explorationSeed == null) relevance + random * 0.04 * freshness else {
                // Keep previously viewed posts available while reserving the top of Home for unseen ones.
                (relevance + 0.6 * 0.5.pow(age / 168.0)) *
                    (if (soonAfterView(post)) 0.16 else 1.0) * (0.82 + random * 0.42)
            }
            post to score
        }.sortedByDescending { (_, score) -> score }.toMutableList()

        val ordered = fresh.toMutableList()
        while (remaining.isNotEmpty()) {
            val recentAuthors = (precedingAuthors.takeLast(2) + ordered.takeLast(2).map { author(it) }).takeLast(2)
            val minimumComparableScore = remaining.first().second * 0.70
            val diverse = remaining.take(6).indexOfFirst { (post, score) ->
                score >= minimumComparableScore && author(post) !in recentAuthors
            }
            val next = if (explorationSeed != null && ordered.size % 4 == 2) {
                val discoveries = remaining.take(10).withIndex().filter { (_, scored) ->
                    val (post, score) = scored
                    score >= minimumComparableScore && !soonAfterView(post) &&
                        ageHours(post) <= 14 * 24 && author(post) !in recentAuthors
                }
                discoveries.randomOrNull(Random(explorationSeed xor ordered.size.toLong()))?.index ?: diverse
            } else diverse
            ordered += remaining.removeAt(next.coerceAtLeast(0)).first
        }
        return ordered
    }
}
