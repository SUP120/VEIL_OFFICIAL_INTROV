package com.ns.findyourintroverfriend.feed

import android.content.Context
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random
import com.ns.findyourintroverfriend.util.VeilSafetyUtil

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    val uid: String get() = auth.currentUser?.uid ?: ""

    private val _posts      = MutableStateFlow<List<VeilPost>>(emptyList())
    val posts: StateFlow<List<VeilPost>> = _posts.asStateFlow()

    private val _comments   = MutableStateFlow<List<VeilComment>>(emptyList())
    val comments: StateFlow<List<VeilComment>> = _comments.asStateFlow()
    private var activeCommentsPostId: String? = null

    // replies keyed by commentId
    private val _replies = MutableStateFlow<Map<String, List<VeilReply>>>(emptyMap())
    val replies: StateFlow<Map<String, List<VeilReply>>> = _replies.asStateFlow()

    private val _loading    = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _isPosting  = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    var draftCaption   by mutableStateOf("")
    var draftImageUris by mutableStateOf<List<Uri>>(emptyList())
    var draftPollOptions by mutableStateOf<List<String>>(emptyList())
    var postError by mutableStateOf<String?>(null)

    private val _pollVotes = MutableStateFlow<Map<String, FeedPollVote>>(emptyMap())
    internal val pollVotes = _pollVotes.asStateFlow()

    private val _myAlias  = MutableStateFlow("")
    private val _myPhoto  = MutableStateFlow("")
    private val _myAvatar = MutableStateFlow("")
    val myAlias : StateFlow<String> = _myAlias.asStateFlow()
    val myPhoto : StateFlow<String> = _myPhoto.asStateFlow()
    val myAvatar: StateFlow<String> = _myAvatar.asStateFlow()

    // ── Pagination cursor ─────────────────────────────────────────────────────
    private var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private var feedGeneration = 0
    private val viewedPosts = HomeFeedHistory(application)
    private var homeSeed = Random.nextLong()
    private var lastFeedLoadAt = 0L
    private var connectionAuthors = emptySet<String>()
    private val _hasMore        = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private val _isLoadingMore  = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    // Fetch a larger batch so the ranker has enough variety to shuffle meaningfully
    private val PAGE_SIZE = 50L

    private val reactionsInFlight = mutableSetOf<String>()

    // ── In-app notifications (real-time) ─────────────────────────────────────────
    /** Raw notification docs for the panel (latest 30, newest first) */
    private val _notifItems    = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val notifItems: StateFlow<List<Map<String, Any>>> = _notifItems.asStateFlow()

    /** Count of unread notifications — drives the red dot on the bell */
    private val _unreadCount   = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private var notifListener: ListenerRegistration? = null

    // ── Bookmarks (saved posts) ───────────────────────────────────────────────
    private val _savedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val savedPostIds: StateFlow<Set<String>> = _savedPostIds.asStateFlow()
    private val _savedLibrary = MutableStateFlow(SavedFeedPosts())
    internal val savedLibrary = _savedLibrary.asStateFlow()
    private val savedReadIds = mutableSetOf<String>()
    private val bookmarksInFlight = mutableSetOf<String>()

    init { loadMyProfile(); loadFeed(); startNotifListener(); loadSavedPosts() }

    private var _isCameraShy = false

    // ── Profile ───────────────────────────────────────────────────────────────
    private fun loadMyProfile() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val authPhotoUrl = auth.currentUser?.photoUrl?.toString() ?: ""
                val authName     = auth.currentUser?.displayName ?: ""
                val doc = db.collection("swipeProfiles").document(uid).get().await()
                _isCameraShy    = doc.getBoolean("isCameraShy") ?: false
                _myAvatar.value = doc.getString("avatarResName") ?: ""
                _myAlias.value  = doc.getString("alias")?.takeIf { it.isNotBlank() }
                    ?: run {
                        val u = db.collection("users").document(uid).get().await()
                        u.getString("alias")?.takeIf { it.isNotBlank() } ?: authName.ifBlank { "Anonymous" }
                    }
                // If camera shy, never expose the real photo URL in posts
                _myPhoto.value = if (_isCameraShy) "" else {
                    doc.getString("photoUrl")?.takeIf { it.isNotBlank() }
                        ?: run {
                            val u = db.collection("users").document(uid).get().await()
                            u.getString("photoUrl")?.takeIf { it.isNotBlank() } ?: authPhotoUrl
                        }
                }
                // Now that we have a uid from auth, start the notif listener if not yet started
                startNotifListener()
            } catch (e: Exception) {
                Log.e("FeedVM", "loadMyProfile: ${e.message}")
                _myAlias.value = auth.currentUser?.displayName ?: "Anonymous"
                _myPhoto.value = auth.currentUser?.photoUrl?.toString() ?: ""
            }
        }
    }

    // ── Notification listener (real-time) ─────────────────────────────────────
    private fun startNotifListener() {
        if (uid.isEmpty() || notifListener != null) return
        notifListener = db.collection("feedNotifications").document(uid)
            .collection("items")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.data } ?: emptyList()
                _notifItems.value  = list
                _unreadCount.value = list.count { (it["read"] as? Boolean) != true }
            }
    }

    /** Call when the user opens the notification panel — marks all as read in Firestore */
    fun markAllNotifsRead() {
        if (uid.isEmpty()) return
        val snap = _notifItems.value
        if (snap.none { (it["read"] as? Boolean) != true }) return
        viewModelScope.launch {
            runCatching {
                val batch = db.batch()
                db.collection("feedNotifications").document(uid)
                    .collection("items")
                    .whereEqualTo("read", false)
                    .get().await()
                    .documents.forEach { d -> batch.update(d.reference, "read", true) }
                batch.commit().await()
                // Optimistic local update
                _unreadCount.value = 0
                _notifItems.value  = _notifItems.value.map { it + ("read" to true) }
            }
        }
    }

    private fun loadFeed() = reloadFeed(refreshing = false)

    fun refresh() = reloadFeed(refreshing = true)

    fun refreshIfStale() {
        if (lastFeedLoadAt > 0 && System.currentTimeMillis() - lastFeedLoadAt >= 15 * 60_000L &&
            !_isRefreshing.value && !_loading.value
        ) refresh()
    }

    fun recordViewedPosts(postIds: Set<String>) {
        viewedPosts.mark(uid, postIds, System.currentTimeMillis())
    }

    fun setConnectionAuthors(ids: Set<String>) {
        connectionAuthors = ids
    }

    private fun reloadFeed(refreshing: Boolean) {
        val generation = ++feedGeneration
        val account = uid
        _loading.value = !refreshing
        _isRefreshing.value = refreshing
        _isLoadingMore.value = false
        homeSeed = Random.nextLong()
        viewModelScope.launch {
            try {
                val query = db.collection("veilPosts").orderBy("createdAt", Query.Direction.DESCENDING)
                val first = query.limit(PAGE_SIZE).get().await()
                if (feedGeneration != generation || uid != account) return@launch
                val now = System.currentTimeMillis()
                val history = viewedPosts.read(account, now)
                val firstPosts = first.documents.mapNotNull(::toPost)
                val cursor = first.documents.lastOrNull()
                val firstHasMore = first.size().toLong() == PAGE_SIZE
                val more = if (cursor != null && firstHasMore && !first.metadata.isFromCache &&
                    firstPosts.count { now - (history[it.postId] ?: 0L) >= 72 * 3_600_000L } < 12
                ) {
                    try {
                        query.startAfter(cursor).limit(30).get().await()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Log.w("FeedVM", "Could not expand recommendation pool", failure)
                        null
                    }
                } else null
                if (feedGeneration != generation || uid != account) return@launch
                lastDoc = more?.documents?.lastOrNull() ?: cursor
                _hasMore.value = if (more == null) firstHasMore else more.size() == 30
                _posts.value = rankHomePosts(firstPosts + more?.documents.orEmpty().mapNotNull(::toPost), history = history)
                lastFeedLoadAt = now
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "reloadFeed", e)
            } finally {
                if (feedGeneration == generation) {
                    _loading.value = false
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun loadMore() {
        val cursor = lastDoc ?: return
        if (_loading.value || _isRefreshing.value || _isLoadingMore.value || !_hasMore.value) return
        val generation = feedGeneration
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val snapshot = db.collection("veilPosts").orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(cursor).limit(PAGE_SIZE).get().await()
                if (feedGeneration != generation) return@launch
                lastDoc = snapshot.documents.lastOrNull() ?: cursor
                _hasMore.value = snapshot.documents.size.toLong() == PAGE_SIZE
                val existingIds = _posts.value.mapTo(mutableSetOf()) { it.postId }
                val nextPage = snapshot.documents.mapNotNull(::toPost).filterNot { it.postId in existingIds }
                _posts.value = _posts.value + rankHomePosts(nextPage, reserveFresh = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "loadMore", e)
            } finally {
                if (feedGeneration == generation) _isLoadingMore.value = false
            }
        }
    }

    private fun rankHomePosts(
        posts: List<VeilPost>,
        reserveFresh: Boolean = true,
        history: Map<String, Long> = viewedPosts.read(uid, System.currentTimeMillis())
    ): List<VeilPost> {
        val account = uid
        val saved = _savedPostIds.value
        val signals = (_posts.value + posts + _savedLibrary.value.posts).distinctBy { it.postId }
            .filter { it.authorUid.isNotBlank() && it.authorUid != account }
            .groupingBy { it.authorUid }
            .fold(0.0) { total, post ->
                total + (if (post.postId in saved) 0.50 else 0.0) +
                    (if (account.isNotEmpty() && post.reactionFor(account) != null) 0.25 else 0.0)
            }
        return com.ns.findyourintroverfriend.util.FeedRanker.rank(
            posts = posts,
            getPostId = VeilPost::postId,
            getLikeCount = { it.likedBy.size },
            getCommentCount = { it.commentsCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt() },
            getCreatedAt = VeilPost::createdAt,
            isDeveloper = { com.ns.findyourintroverfriend.util.VeilDevConfig.isDeveloper(it.authorUid) },
            getAuthorId = VeilPost::authorUid,
            getAffinity = { (signals[it.authorUid] ?: 0.0) + if (it.authorUid in connectionAuthors) 0.45 else 0.0 },
            getVoteCount = { it.pollCounts.sum() },
            reserveFresh = reserveFresh,
            lastViewedAt = { history[it.postId] ?: 0L },
            explorationSeed = homeSeed,
            precedingAuthors = if (reserveFresh) emptyList() else _posts.value.takeLast(2).map { it.authorUid }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun toPost(doc: com.google.firebase.firestore.DocumentSnapshot): VeilPost? = try {
        val rawReactions = doc.get("reactions") as? Map<*, *>
        val reactions = rawReactions?.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val uids = (v as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            key to uids
        }?.toMap() ?: emptyMap()
        val pollOptions = (doc.get("pollOptions") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val pollCounts = (doc.get("pollCounts") as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }.orEmpty()
        val isPoll = pollOptions.size in 2..MAX_POLL_OPTIONS && pollCounts.size == MAX_POLL_OPTIONS
        VeilPost(
            postId         = doc.id,
            authorUid      = doc.getString("authorUid")      ?: "",
            authorAlias    = doc.getString("authorAlias")    ?: "Anonymous",
            authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
            authorAvatar   = doc.getString("authorAvatar")   ?: "",
            caption        = doc.getString("caption")        ?: "",
            imageUrls      = (doc.get("imageUrls") as? List<String>) ?: emptyList(),
            likedBy        = (doc.get("likedBy")   as? List<String>) ?: emptyList(),
            reactions      = reactions,
            commentsCount  = doc.getLong("commentsCount")    ?: 0L,
            createdAt      = doc.getLong("createdAt")        ?: 0L,
            tag            = doc.getString("tag")            ?: "",
            editedAt       = doc.getLong("editedAt")         ?: 0L,
            pollOptions    = if (isPoll) pollOptions else emptyList(),
            pollCounts     = if (isPoll) pollCounts else emptyList()
        )
    } catch (_: Exception) { null }

    // ── Reactions (5 emoji) ─────────────────────────────────────────────────────
    /** Returns the emoji this user has reacted with, or null if no reaction. */
    fun getUserReaction(post: VeilPost): String? = post.reactionFor(uid)

    fun toggleReaction(post: VeilPost, emoji: String, onError: (String) -> Unit = {}) {
        val account = uid
        if (account.isEmpty() || emoji !in FEED_REACTION_LABELS || !reactionsInFlight.add(post.postId)) return
        val previousReaction = post.reactionFor(account)
        val desiredReaction = emoji.takeUnless { it == previousReaction }
        updatePost(post.postId) { it.withReaction(account, desiredReaction) }
        viewModelScope.launch {
            try {
                val ref = db.collection("veilPosts").document(post.postId)
                val saved = db.runTransaction { transaction ->
                    val document = transaction.get(ref)
                    check(document.exists())
                    val current = checkNotNull(toPost(document))
                    val updated = current.withReaction(account, desiredReaction)
                    // A plain like is stored in likedBy for clients and rules that predate emoji reactions.
                    val usesLegacyLike = (desiredReaction == null || desiredReaction == "❤️") &&
                        current.reactions.values.none { account in it }
                    if (usesLegacyLike) {
                        transaction.update(ref, "likedBy", updated.likedBy)
                        updated.copy(reactions = current.reactions)
                    } else {
                        transaction.update(ref, mapOf("reactions" to updated.reactions, "likedBy" to updated.likedBy))
                        updated
                    }
                }.await()
                if (uid == account) {
                    updatePost(post.postId) { it.copy(reactions = saved.reactions, likedBy = saved.likedBy) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "toggleReaction: ${e.message}")
                if (uid == account) {
                    updatePost(post.postId) { it.withReaction(account, previousReaction) }
                    onError("Couldn’t update your reaction. Try again.")
                }
            } finally {
                reactionsInFlight.remove(post.postId)
            }
        }
    }

    fun loadPollVote(postId: String, retry: Boolean = false) {
        val account = uid
        val existing = _pollVotes.value[postId]
        if (account.isEmpty() || (existing != null && (!retry || !existing.loadFailed))) return
        setPollVote(postId, FeedPollVote(isBusy = true))
        viewModelScope.launch {
            try {
                val vote = db.collection("veilPosts").document(postId)
                    .collection("pollVotes").document(account).get().await()
                if (uid == account) setPollVote(postId, FeedPollVote(choice = vote.getLong("option")?.toInt()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (uid == account) setPollVote(postId, FeedPollVote(loadFailed = true))
            }
        }
    }

    fun voteInPoll(post: VeilPost, option: Int, onError: (String) -> Unit) {
        val account = uid
        val state = _pollVotes.value[post.postId] ?: return
        if (account.isEmpty() || state.isBusy || state.loadFailed || state.choice != null || option !in post.pollOptions.indices) return
        setPollVote(post.postId, state.copy(isBusy = true))
        viewModelScope.launch {
            try {
                val postRef = db.collection("veilPosts").document(post.postId)
                val voteRef = postRef.collection("pollVotes").document(account)
                val (choice, counts) = db.runTransaction { transaction ->
                    val current = checkNotNull(toPost(transaction.get(postRef)))
                    check(option in current.pollOptions.indices)
                    val previous = transaction.get(voteRef).getLong("option")?.toInt()
                    if (previous != null) {
                        previous to current.pollCounts
                    } else {
                        val updated = current.pollCounts.mapIndexed { index, count -> if (index == option) count + 1 else count }
                        transaction.update(postRef, "pollCounts", updated)
                        transaction.set(voteRef, mapOf("option" to option))
                        option to updated
                    }
                }.await()
                if (uid == account) {
                    updatePost(post.postId) { it.copy(pollCounts = counts) }
                    setPollVote(post.postId, FeedPollVote(choice = choice))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (uid == account) {
                    setPollVote(post.postId, state)
                    onError("Couldn’t submit your vote. Try again.")
                }
            }
        }
    }

    private fun setPollVote(postId: String, state: FeedPollVote) {
        _pollVotes.value = _pollVotes.value + (postId to state)
    }

    private fun updatePost(postId: String, update: (VeilPost) -> VeilPost) {
        _posts.value = _posts.value.map { if (it.postId == postId) update(it) else it }
        _savedLibrary.value = _savedLibrary.value.copy(
            posts = _savedLibrary.value.posts.map { if (it.postId == postId) update(it) else it }
        )
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────
    private fun loadSavedPosts() {
        val account = uid
        if (account.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val ids = readSavedIds(account)
                if (uid == account && bookmarksInFlight.isEmpty()) _savedPostIds.value = ids
            }
        }
    }

    private suspend fun readSavedIds(account: String): Set<String> =
        db.collection("users").document(account).collection("savedPosts").get().await()
            .documents.sortedByDescending {
                when (val savedAt = it.get("savedAt")) {
                    is Number -> savedAt.toLong()
                    is com.google.firebase.Timestamp -> savedAt.toDate().time
                    else -> 0L
                }
            }.map { it.id }.toSet()

    fun loadSavedLibrary(reset: Boolean = true) {
        val account = uid
        if (account.isEmpty() || _savedLibrary.value.isLoading) return
        if (reset) savedReadIds.clear()
        _savedLibrary.value = (if (reset) SavedFeedPosts() else _savedLibrary.value).copy(isLoading = true, error = false)
        viewModelScope.launch {
            try {
                val ids = if (reset) readSavedIds(account) else _savedPostIds.value
                if (uid != account) return@launch
                if (reset && bookmarksInFlight.isEmpty()) _savedPostIds.value = ids
                val unread = _savedPostIds.value.filterNot { it in savedReadIds }
                val page = unread.take(24)
                val documents = page.chunked(10).flatMap { chunk ->
                    db.collection("veilPosts").whereIn(FieldPath.documentId(), chunk).get().await().documents
                }.mapNotNull(::toPost).associateBy { it.postId }
                if (uid != account) return@launch
                savedReadIds.addAll(page)
                _savedLibrary.value = SavedFeedPosts(
                    posts = _savedLibrary.value.posts + page.mapNotNull(documents::get),
                    hasMore = _savedPostIds.value.any { it !in savedReadIds }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "loadSavedLibrary: ${e.message}")
                _savedLibrary.value = _savedLibrary.value.copy(isLoading = false, error = true)
            } finally {
                if (_savedLibrary.value.isLoading) _savedLibrary.value = _savedLibrary.value.copy(isLoading = false)
            }
        }
    }

    fun toggleBookmark(postId: String, onError: (String) -> Unit = {}) {
        val account = uid
        if (account.isEmpty() || !bookmarksInFlight.add(postId)) return
        val isSaved = postId in _savedPostIds.value
        // Optimistic update
        _savedPostIds.value = if (isSaved) _savedPostIds.value - postId else setOf(postId) + _savedPostIds.value
        viewModelScope.launch {
            try {
                val ref = db.collection("users").document(account)
                    .collection("savedPosts").document(postId)
                if (isSaved) ref.delete().await()
                else         ref.set(mapOf("savedAt" to System.currentTimeMillis())).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "toggleBookmark: ${e.message}")
                // Rollback
                if (uid == account) {
                    _savedPostIds.value = if (isSaved) _savedPostIds.value + postId else _savedPostIds.value - postId
                    onError("Couldn’t update your saved posts. Try again.")
                }
            } finally {
                bookmarksInFlight.remove(postId)
            }
        }
    }

    // ── Comments ──────────────────────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    fun loadComments(postId: String) {
        if (activeCommentsPostId != postId) {
            activeCommentsPostId = postId
            _comments.value = emptyList()
        }
        viewModelScope.launch {
            try {
                val snap = db.collection("veilPosts").document(postId)
                    .collection("comments")
                    .orderBy("createdAt", Query.Direction.ASCENDING).get().await()
                if (activeCommentsPostId != postId) return@launch
                _comments.value = snap.documents.mapNotNull { doc ->
                    try {
                        VeilComment(
                            commentId      = doc.id,
                            authorUid      = doc.getString("authorUid")      ?: "",
                            authorAlias    = doc.getString("authorAlias")    ?: "Anonymous",
                            authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
                            authorAvatar   = doc.getString("authorAvatar")   ?: "",
                            text           = doc.getString("text")           ?: "",
                            likedBy        = (doc.get("likedBy") as? List<String>) ?: emptyList(),
                            createdAt      = doc.getLong("createdAt")        ?: 0L,
                            replyCount     = (doc.getLong("replyCount")      ?: 0L).toInt()
                        )
                    } catch (_: Exception) { null }
                }
            } catch (e: Exception) { Log.e("FeedVM", "loadComments: ${e.message}") }
        }
    }

    /** Ids of comment threads where we just posted an optimistic reply — skip Firestore reload for these */
    private val _justRepliedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    val justRepliedCommentIds: StateFlow<Set<String>> = _justRepliedCommentIds.asStateFlow()

    fun loadReplies(postId: String, commentId: String) {
        viewModelScope.launch {
            try {
                val snap = db.collection("veilPosts").document(postId)
                    .collection("comments").document(commentId)
                    .collection("replies")
                    .orderBy("createdAt", Query.Direction.ASCENDING).get().await()
                val fromFirestore = snap.documents.mapNotNull { doc ->
                    try {
                        VeilReply(
                            replyId        = doc.id,
                            authorUid      = doc.getString("authorUid")      ?: "",
                            authorAlias    = doc.getString("authorAlias")    ?: "Anonymous",
                            authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
                            authorAvatar   = doc.getString("authorAvatar")   ?: "",
                            text           = doc.getString("text")           ?: "",
                            replyToAlias   = doc.getString("replyToAlias")   ?: "",
                            createdAt      = doc.getLong("createdAt")        ?: 0L
                        )
                    } catch (_: Exception) { null }
                }
                // Merge with existing optimistic entries, deduplicate by replyId
                val existing = _replies.value[commentId] ?: emptyList()
                val merged   = (fromFirestore + existing).distinctBy { it.replyId }
                _replies.value = _replies.value + (commentId to merged)
            } catch (e: Exception) { Log.e("FeedVM", "loadReplies: ${e.message}") }
        }
    }

    fun addComment(postId: String, text: String) {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                val id   = UUID.randomUUID().toString()
                val data = mapOf(
                    "commentId"      to id,
                    "authorUid"      to uid,
                    "authorAlias"    to _myAlias.value,
                    "authorPhotoUrl" to _myPhoto.value,
                    "authorAvatar"   to _myAvatar.value,
                    "text"           to text.trim(),
                    "likedBy"        to emptyList<String>(),
                    "replyCount"     to 0L,
                    "createdAt"      to System.currentTimeMillis()
                )
                val postRef = db.collection("veilPosts").document(postId)
                db.batch()
                    .set(postRef.collection("comments").document(id), data)
                    .update(postRef, "commentsCount", FieldValue.increment(1)).commit().await()
                if (activeCommentsPostId == postId) loadComments(postId)
                updatePost(postId) { it.copy(commentsCount = it.commentsCount + 1) }
                // Write in-app notification + push for post author
                val postDoc = postRef.get().await()
                val authorUid = postDoc.getString("authorUid") ?: ""
                if (authorUid.isNotBlank() && authorUid != uid) {
                    writeInAppNotif(authorUid, "comment", _myAlias.value, text.trim().take(60), postId, id)
                    sendPushNotif(authorUid, "💬 ${_myAlias.value} commented on your post", text.trim().take(80))
                }
            } catch (e: Exception) { Log.e("FeedVM", "addComment: ${e.message}") }
        }
    }

    fun addReply(postId: String, commentId: String, text: String, replyToAlias: String) {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                val id  = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val commentRef = db.collection("veilPosts").document(postId)
                    .collection("comments").document(commentId)

                val data = mapOf(
                    "replyId"        to id,
                    "authorUid"      to uid,
                    "authorAlias"    to _myAlias.value,
                    "authorPhotoUrl" to _myPhoto.value,
                    "authorAvatar"   to _myAvatar.value,
                    "text"           to text.trim(),
                    "replyToAlias"   to replyToAlias,
                    "createdAt"      to now
                )

                // ── Write to Firestore ───────────────────────────────────────────────────
                db.batch()
                    .set(commentRef.collection("replies").document(id), data)
                    .update(commentRef, "replyCount", FieldValue.increment(1)).commit().await()

                // ── OPTIMISTIC UPDATE: patch local state immediately, no network roundtrip ──
                // 1. Insert the new reply into _replies cache so it shows instantly
                val newReply = VeilReply(
                    replyId        = id,
                    authorUid      = uid,
                    authorAlias    = _myAlias.value,
                    authorPhotoUrl = _myPhoto.value,
                    authorAvatar   = _myAvatar.value,
                    text           = text.trim(),
                    replyToAlias   = replyToAlias,
                    createdAt      = now
                )
                val existing = _replies.value[commentId] ?: emptyList()
                _replies.value  = _replies.value + (commentId to (existing + newReply))

                // Mark so LaunchedEffect in CommentRow skips the Firestore load (prevents duplicate)
                _justRepliedCommentIds.value = _justRepliedCommentIds.value + commentId

                // 2. Increment replyCount on the comment in _comments
                _comments.value = _comments.value.map { c ->
                    if (c.commentId == commentId) c.copy(replyCount = c.replyCount + 1) else c
                }

                // ── Notifications (non-blocking, separate coroutine) ──────────────────
                viewModelScope.launch {
                    runCatching {
                        val authorUid = commentRef.get().await().getString("authorUid") ?: ""
                        if (authorUid.isNotBlank() && authorUid != uid) {
                            writeInAppNotif(authorUid, "reply", _myAlias.value, text.trim().take(60), postId, commentId)
                            sendPushNotif(authorUid, "↩️ ${_myAlias.value} replied to your comment", text.trim().take(80))
                        }
                    }
                }
            } catch (e: Exception) { Log.e("FeedVM", "addReply: ${e.message}") }
        }
    }

    // ── Resolve alias → uid for @mention taps ────────────────────────────────────────
    private val aliasUidCache = mutableMapOf<String, String>()

    fun lookupUidByAlias(alias: String, onResult: (String?) -> Unit) {
        if (alias.isBlank()) { onResult(null); return }
        aliasUidCache[alias]?.let { onResult(it); return }  // cache hit
        viewModelScope.launch {
            try {
                val snap = db.collection("swipeProfiles")
                    .whereEqualTo("alias", alias).limit(1).get().await()
                val found = snap.documents.firstOrNull()?.id
                if (found != null) aliasUidCache[alias] = found
                onResult(found)
            } catch (e: Exception) {
                Log.e("FeedVM", "lookupUidByAlias($alias): ${e.message}")
                onResult(null)
            }
        }
    }

    private suspend fun writeInAppNotif(toUid: String, type: String, fromAlias: String, preview: String, postId: String, commentId: String) {
        runCatching {
            db.collection("feedNotifications").document(toUid).collection("items").add(
                mapOf("type" to type, "fromAlias" to fromAlias, "previewText" to preview,
                    "postId" to postId, "commentId" to commentId,
                    "createdAt" to System.currentTimeMillis(), "read" to false)
            ).await()
        }
    }

    private fun sendPushNotif(toUid: String, title: String, body: String) {
        viewModelScope.launch {
            runCatching {
                com.ns.findyourintroverfriend.notifications.VeilNotifier
                    .sendToUser(toUid, title, body)
            }
        }
    }

    fun toggleCommentLike(postId: String, comment: VeilComment) {
        if (uid.isEmpty()) return
        val already = comment.likedBy.contains(uid)
        _comments.value = _comments.value.map { c ->
            if (c.commentId != comment.commentId) c
            else {
                val newLikes: List<String> = if (already) c.likedBy - uid else c.likedBy + uid
                c.copy(likedBy = newLikes)
            }
        }
        viewModelScope.launch {
            try {
                val ref = db.collection("veilPosts").document(postId)
                    .collection("comments").document(comment.commentId)
                if (already) ref.update("likedBy", FieldValue.arrayRemove(uid)).await()
                else         ref.update("likedBy", FieldValue.arrayUnion(uid)).await()
            } catch (e: Exception) { Log.e("FeedVM", "toggleCommentLike: ${e.message}") }
        }
    }

    // ── Create post — uploads images to Cloudinary, then saves to Firestore ──
    fun submitPost(context: Context, onDone: () -> Unit) {
        val account = uid
        val caption = draftCaption.trim()
        val photos = draftImageUris
        val options = draftPollOptions.map(String::trim)
        if (account.isEmpty() || _isPosting.value || (caption.isEmpty() && photos.isEmpty())) return
        if (VeilSafetyUtil.containsProfanity(caption) || options.any(VeilSafetyUtil::containsProfanity) ||
            (options.isNotEmpty() && !validPollDraft(caption, options))
        ) return
        _isPosting.value = true
        postError = null
        viewModelScope.launch {
            try {
                val cloudName    = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_CLOUD_NAME
                val uploadPreset = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_UPLOAD_PRESET

                val imageUrls = photos.map { uri ->
                    check(cloudName.isNotBlank() && uploadPreset.isNotBlank())
                    com.ns.findyourintroverfriend.util.CloudinaryUploader.upload(
                        context = context, uri = uri, cloudName = cloudName, uploadPreset = uploadPreset
                    )
                }

                check(uid == account)
                val postId = UUID.randomUUID().toString()
                val data = mutableMapOf<String, Any>(
                    "postId"         to postId,
                    "authorUid"      to account,
                    "authorAlias"    to _myAlias.value,
                    "authorPhotoUrl" to _myPhoto.value,
                    "authorAvatar"   to _myAvatar.value,
                    "caption"        to caption,
                    "imageUrls"      to imageUrls,
                    "likedBy"        to emptyList<String>(),
                    "reactions"      to emptyMap<String, List<String>>(),
                    "commentsCount"  to 0L,
                    "createdAt"      to System.currentTimeMillis()
                )
                if (options.isNotEmpty()) {
                    data["pollOptions"] = options
                    data["pollCounts"] = List(MAX_POLL_OPTIONS) { 0L }
                }
                db.collection("veilPosts").document(postId).set(data).await()
                draftCaption   = ""
                draftImageUris = emptyList()
                draftPollOptions = emptyList()
                refresh()
                onDone()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "submitPost: ${e.message}")
                postError = "Couldn’t publish. Your draft is still here."
            }
            finally { _isPosting.value = false }
        }
    }

    fun editPost(post: VeilPost, caption: String, onDone: (Boolean) -> Unit) {
        val trimmed = caption.trim()
        if (uid.isEmpty() || post.authorUid != uid || post.pollOptions.isNotEmpty() ||
            (trimmed.isEmpty() && post.imageUrls.isEmpty()) ||
            VeilSafetyUtil.containsProfanity(trimmed)
        ) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            try {
                val editedAt = System.currentTimeMillis()
                db.collection("veilPosts").document(post.postId)
                    .update(mapOf("caption" to trimmed, "editedAt" to editedAt)).await()
                updatePost(post.postId) { it.copy(caption = trimmed, editedAt = editedAt) }
                onDone(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "editPost: ${e.message}")
                onDone(false)
            }
        }
    }

    fun deletePost(postId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                db.collection("veilPosts").document(postId).delete().await()
                _posts.value = _posts.value.filter { it.postId != postId }
                _savedLibrary.value = _savedLibrary.value.copy(posts = _savedLibrary.value.posts.filterNot { it.postId == postId })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "deletePost: ${e.message}")
                onError("Couldn’t delete the post. Try again.")
            }
        }
    }

    // ── Edit a comment (author only) ──────────────────────────────────────────
    fun editComment(postId: String, commentId: String, newText: String) {
        if (uid.isEmpty() || newText.isBlank()) return
        // Optimistic update
        _comments.value = _comments.value.map { c ->
            if (c.commentId == commentId) c.copy(text = newText.trim()) else c
        }
        viewModelScope.launch {
            try {
                db.collection("veilPosts").document(postId)
                    .collection("comments").document(commentId)
                    .update(mapOf(
                        "text"     to newText.trim(),
                        "editedAt" to System.currentTimeMillis()
                    )).await()
            } catch (e: Exception) {
                Log.e("FeedVM", "editComment: ${e.message}")
                // On failure reload to restore truth
                loadComments(postId)
            }
        }
    }

    // ── Delete a comment (author only) ────────────────────────────────────────
    fun deleteComment(postId: String, commentId: String) {
        if (uid.isEmpty()) return
        // Optimistic update
        _comments.value = _comments.value.filter { it.commentId != commentId }
        updatePost(postId) { it.copy(commentsCount = (it.commentsCount - 1).coerceAtLeast(0)) }
        viewModelScope.launch {
            try {
                db.collection("veilPosts").document(postId)
                    .collection("comments").document(commentId).delete().await()
                db.collection("veilPosts").document(postId)
                    .update("commentsCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
            } catch (e: Exception) { Log.e("FeedVM", "deleteComment: ${e.message}") }
        }
    }

    fun reportPost(postId: String, reason: String, note: String, onDone: (Boolean) -> Unit) {
        val account = uid
        if (account.isEmpty()) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            try {
                db.collection("veilReports").add(
                    mapOf(
                        "type"       to "post",
                        "postId"     to postId,
                        "reportedBy" to account,
                        "reason"     to reason,
                        "note"       to note,
                        "reportedAt" to System.currentTimeMillis(),
                        "status"     to "pending"
                    )
                ).await()
                onDone(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FeedVM", "reportPost: ${e.message}")
                onDone(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        notifListener?.remove()
    }
}
