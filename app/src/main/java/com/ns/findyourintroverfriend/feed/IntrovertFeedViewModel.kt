package com.ns.findyourintroverfriend.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Data Models ───────────────────────────────────────────────────────────────

data class IntrovertPost(
    val postId: String = "",
    val authorId: String = "",
    val authorAlias: String = "Anonymous",
    val content: String = "",
    val createdAt: Long = 0L,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val commentCount: Int = 0,
    val reportCount: Int = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "postId" to postId, "authorId" to authorId, "authorAlias" to authorAlias,
        "content" to content, "createdAt" to createdAt, "likeCount" to likeCount,
        "commentCount" to commentCount, "reportCount" to reportCount
    )
}

data class FeedComment(
    val commentId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorAlias: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
    val replyCount: Int = 0
)

data class FeedReply(
    val replyId: String = "",
    val authorId: String = "",
    val authorAlias: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
    val replyToAlias: String = ""
)

data class FeedNotif(
    val notifId: String = "",
    val type: String = "",   // "comment" | "reply" | "friend_request"
    val fromAlias: String = "",
    val previewText: String = "",
    val postId: String = "",
    val commentId: String = "",
    val createdAt: Long = 0L,
    val read: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class IntrovertFeedViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""

    // ── Posts ─────────────────────────────────────────────────────────────────
    private val _posts   = MutableStateFlow<List<IntrovertPost>>(emptyList())
    val posts: StateFlow<List<IntrovertPost>> = _posts.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // ── Comments keyed by postId ──────────────────────────────────────────────
    private val _comments = MutableStateFlow<Map<String, List<FeedComment>>>(emptyMap())
    val comments: StateFlow<Map<String, List<FeedComment>>> = _comments.asStateFlow()

    // ── Replies keyed by "postId__commentId" ─────────────────────────────────
    private val _replies = MutableStateFlow<Map<String, List<FeedReply>>>(emptyMap())
    val replies: StateFlow<Map<String, List<FeedReply>>> = _replies.asStateFlow()

    // ── Notifications ─────────────────────────────────────────────────────────
    private val _notifs       = MutableStateFlow<List<FeedNotif>>(emptyList())
    val notifs: StateFlow<List<FeedNotif>> = _notifs.asStateFlow()

    private val _unreadCount  = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private var commentListeners = mutableMapOf<String, ListenerRegistration>()
    private var replyListeners   = mutableMapOf<String, ListenerRegistration>()
    private var notifListener    : ListenerRegistration? = null

    // ── Connections (friend requests sent) ────────────────────────────────────
    private val _myConnections = MutableStateFlow<Set<String>>(emptySet())
    val myConnections: StateFlow<Set<String>> = _myConnections.asStateFlow()

    init { loadMyAlias(); loadPosts(); loadNotifications(); loadConnections() }

    // ── Cached alias — read once, reuse everywhere ────────────────────────────
    private var _cachedAlias: String = ""
    private fun loadMyAlias() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _cachedAlias = try {
                db.collection("users").document(uid).get().await()
                    .getString("alias") ?: "Introvert"
            } catch (_: Exception) { "Introvert" }
        }
    }

    // ── Rate-limit likes: 1 toggle per 1.5s per post ─────────────────────────
    private val lastLikeTime = mutableMapOf<String, Long>()
    private val LIKE_COOLDOWN_MS = 1500L

    // ── My liked post IDs — loaded once, updated locally ─────────────────────
    private val _likedPostIds = MutableStateFlow<Set<String>>(emptySet())
    private fun loadLikedPosts() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                @Suppress("UNCHECKED_CAST")
                val ids = (doc.get("likedIntrovertPosts") as? List<String>) ?: emptyList()
                _likedPostIds.value = ids.toSet()
            } catch (_: Exception) {}
        }
    }

    // ── Pagination ──────────────────────────────────────────────────────────────
    // Fetch 50 so the ranker has enough variety to produce meaningfully different orderings
    private val PAGE = 50L
    private var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private val _hasMore        = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private val _isLoadingMore  = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private fun loadConnections() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val snap = db.collection("friendApproaches")
                    .whereEqualTo("fromUid", uid).get().await()
                _myConnections.value = snap.documents.mapNotNull { it.getString("toUid") }.toSet()
            } catch (_: Exception) {}
        }
    }

    fun sendConnect(toUid: String, toAlias: String) {
        if (uid.isEmpty() || toUid == uid) return
        if (_myConnections.value.contains(toUid)) return   // already sent
        viewModelScope.launch {
            try {
                val myAlias = getMyAlias()
                val ref = db.collection("friendApproaches").document()
                ref.set(mapOf(
                    "fromUid"   to uid,
                    "fromAlias" to myAlias,
                    "toUid"     to toUid,
                    "toAlias"   to toAlias,
                    "sentAt"    to System.currentTimeMillis(),
                    "status"    to "pending"
                )).await()
                _myConnections.value = _myConnections.value + toUid
            } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Posts
    // ─────────────────────────────────────────────────────────────────────────
    // ── Posts — paginated one-shot fetch (no real-time listener on feed) ──────
    //  Real-time listeners on high-volume collections burn reads on every write
    //  by every user. Instead: one-shot .get() with cursor pagination.
    private fun loadPosts() {
        viewModelScope.launch {
            _loading.value = true
            lastDoc = null
            _hasMore.value = true
            try {
                val snap = db.collection("introvertPosts")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(PAGE).get().await()
                lastDoc = snap.documents.lastOrNull()
                _hasMore.value = snap.size().toLong() == PAGE
                val likedIds = _likedPostIds.value
                val raw = snap.documents.mapNotNull { doc ->
                    try {
                        // Filter hidden posts client-side (works for docs missing the field too)
                        if (doc.getBoolean("hidden") == true) return@mapNotNull null
                        IntrovertPost(
                            postId       = doc.id,
                            authorId     = doc.getString("authorId")    ?: "",
                            authorAlias  = doc.getString("authorAlias") ?: "Anonymous",
                            content      = doc.getString("content")     ?: "",
                            createdAt    = doc.getLong("createdAt")     ?: 0L,
                            likeCount    = (doc.getLong("likeCount")    ?: 0L).toInt(),
                            commentCount = (doc.getLong("commentCount") ?: 0L).toInt(),
                            reportCount  = (doc.getLong("reportCount")  ?: 0L).toInt(),
                            isLiked      = doc.id in likedIds
                        )
                    } catch (_: Exception) { null }
                }
                _posts.value = com.ns.findyourintroverfriend.util.FeedRanker.rank(
                    posts           = raw,
                    getPostId       = { it.postId },
                    getLikeCount    = { it.likeCount },
                    getCommentCount = { it.commentCount },
                    getCreatedAt    = { it.createdAt }
                )
            } catch (_: Exception) {}
            finally { _loading.value = false }
        }
    }

    fun loadMorePosts() {
        val cursor = lastDoc ?: return
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val snap = db.collection("introvertPosts")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(cursor)
                    .limit(PAGE).get().await()
                lastDoc = snap.documents.lastOrNull() ?: cursor
                _hasMore.value = snap.size().toLong() == PAGE
                val likedIds = _likedPostIds.value
                val raw = snap.documents.mapNotNull { doc ->
                    try {
                        if (doc.getBoolean("hidden") == true) return@mapNotNull null
                        IntrovertPost(
                            postId       = doc.id,
                            authorId     = doc.getString("authorId")    ?: "",
                            authorAlias  = doc.getString("authorAlias") ?: "Anonymous",
                            content      = doc.getString("content")     ?: "",
                            createdAt    = doc.getLong("createdAt")     ?: 0L,
                            likeCount    = (doc.getLong("likeCount")    ?: 0L).toInt(),
                            commentCount = (doc.getLong("commentCount") ?: 0L).toInt(),
                            reportCount  = (doc.getLong("reportCount")  ?: 0L).toInt(),
                            isLiked      = doc.id in likedIds
                        )
                    } catch (_: Exception) { null }
                }
                val ranked = com.ns.findyourintroverfriend.util.FeedRanker.rank(
                    posts           = raw,
                    getPostId       = { it.postId },
                    getLikeCount    = { it.likeCount },
                    getCommentCount = { it.commentCount },
                    getCreatedAt    = { it.createdAt }
                )
                _posts.value = _posts.value + ranked
            } catch (_: Exception) {}
            finally { _isLoadingMore.value = false }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Like / Report / Create post  (unchanged logic)
    // ─────────────────────────────────────────────────────────────────────────
    fun refreshPosts() {
        // Rotate salt → new order on every pull-to-refresh
        com.ns.findyourintroverfriend.util.FeedRanker.newSession()
        loadPosts()
        loadLikedPosts()
    }

    fun toggleLike(postId: String) {
        if (uid.isEmpty()) return
        // Rate limit — prevent double-tap spam
        val now = System.currentTimeMillis()
        if (now - (lastLikeTime[postId] ?: 0L) < LIKE_COOLDOWN_MS) return
        lastLikeTime[postId] = now

        val post = _posts.value.find { it.postId == postId } ?: return
        val liked = post.isLiked

        // Optimistic local update — instant UI, no read needed
        _posts.value = _posts.value.map { p ->
            if (p.postId != postId) p
            else p.copy(
                isLiked   = !liked,
                likeCount = if (liked) p.likeCount - 1 else p.likeCount + 1
            )
        }
        _likedPostIds.value = if (liked)
            _likedPostIds.value - postId
        else
            _likedPostIds.value + postId

        viewModelScope.launch {
            try {
                val postRef = db.collection("introvertPosts").document(postId)
                if (liked) {
                    postRef.update("likeCount", FieldValue.increment(-1)).await()
                } else {
                    postRef.update("likeCount", FieldValue.increment(1)).await()
                }
                // Track liked IDs in a single array on the user doc (1 write, no subcollection)
                db.collection("users").document(uid).update(
                    "likedIntrovertPosts",
                    if (liked) FieldValue.arrayRemove(postId) else FieldValue.arrayUnion(postId)
                ).await()
            } catch (_: Exception) {
                // Rollback on failure
                _posts.value = _posts.value.map { p ->
                    if (p.postId == postId) p.copy(isLiked = liked,
                        likeCount = if (liked) p.likeCount + 1 else p.likeCount - 1
                    ) else p
                }
            }
        }
    }

    // Signals the UI to scroll to top after a new post is created
    private val _scrollToTop = MutableStateFlow(false)
    val scrollToTop: StateFlow<Boolean> = _scrollToTop.asStateFlow()
    fun consumeScrollToTop() { _scrollToTop.value = false }

    fun createPost(content: String) {
        if (uid.isEmpty() || content.trim().length < 10) return
        viewModelScope.launch {
            try {
                val alias   = generateAnonymousAlias()
                val postId  = db.collection("introvertPosts").document().id
                val now     = System.currentTimeMillis()
                val newPost = IntrovertPost(postId, uid, alias, content.trim(), now, 0, false, 0, 0)
                // Optimistic: put at very front — rank() will keep it in top-3 (newest createdAt)
                _posts.value    = listOf(newPost) + _posts.value
                _scrollToTop.value = true
                db.collection("introvertPosts").document(postId)
                    .set(newPost.toMap()).await()
            } catch (_: Exception) {}
        }
    }

    fun reportPost(postId: String, reason: String = "Other", note: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val postRef   = db.collection("introvertPosts").document(postId)
                val reportRef = postRef.collection("reports").document(uid)
                if (reportRef.get().await().exists()) return@launch
                val now = System.currentTimeMillis()
                reportRef.set(mapOf("uid" to uid, "reason" to reason, "note" to note, "reportedAt" to now)).await()
                postRef.update("reportCount", FieldValue.increment(1)).await()
                val snap = postRef.get().await()
                db.collection("veilReports").add(mapOf(
                    "type" to "post", "postId" to postId,
                    "authorId" to (snap.getString("authorId") ?: ""),
                    "authorAlias" to (snap.getString("authorAlias") ?: ""),
                    "content" to (snap.getString("content") ?: ""),
                    "reportedBy" to uid, "reason" to reason, "note" to note,
                    "reportedAt" to now, "status" to "pending"
                )).await()
                val count = (postRef.get().await().getLong("reportCount") ?: 0L).toInt()
                if (count >= 5) {
                    postRef.update("hidden", true).await()
                    _posts.value = _posts.value.filter { it.postId != postId }
                }
            } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Comments — real-time listener per expanded post
    // ─────────────────────────────────────────────────────────────────────────
    fun listenComments(postId: String) {
        if (commentListeners.containsKey(postId)) return
        val reg = db.collection("introvertPosts").document(postId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        FeedComment(
                            commentId   = doc.id,
                            postId      = postId,
                            authorId    = doc.getString("authorId")    ?: "",
                            authorAlias = doc.getString("authorAlias") ?: "",
                            text        = doc.getString("text")        ?: "",
                            createdAt   = doc.getLong("createdAt")     ?: 0L,
                            replyCount  = (doc.getLong("replyCount")   ?: 0L).toInt()
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                _comments.value = _comments.value + (postId to list)
            }
        commentListeners[postId] = reg
    }

    fun stopListenComments(postId: String) {
        commentListeners.remove(postId)?.remove()
        _comments.value = _comments.value - postId
    }

    fun postComment(postId: String, text: String, onDone: () -> Unit = {}) {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                val myAlias  = getMyAlias()
                val postRef  = db.collection("introvertPosts").document(postId)
                val commentRef = postRef.collection("comments").document()
                commentRef.set(mapOf(
                    "commentId"   to commentRef.id,
                    "postId"      to postId,
                    "authorId"    to uid,
                    "authorAlias" to myAlias,
                    "text"        to text.trim(),
                    "createdAt"   to System.currentTimeMillis(),
                    "replyCount"  to 0L
                )).await()
                postRef.update("commentCount", FieldValue.increment(1)).await()

                // Notify post author
                val authorId = postRef.get().await().getString("authorId") ?: ""
                if (authorId.isNotBlank() && authorId != uid) {
                    writeNotif(authorId, "comment", myAlias, text.trim().take(60), postId, commentRef.id)
                }
                onDone()
            } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Replies
    // ─────────────────────────────────────────────────────────────────────────
    fun listenReplies(postId: String, commentId: String) {
        val key = "${postId}__${commentId}"
        if (replyListeners.containsKey(key)) return
        val reg = db.collection("introvertPosts").document(postId)
            .collection("comments").document(commentId)
            .collection("replies")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        FeedReply(
                            replyId      = doc.id,
                            authorId     = doc.getString("authorId")     ?: "",
                            authorAlias  = doc.getString("authorAlias")  ?: "",
                            text         = doc.getString("text")         ?: "",
                            createdAt    = doc.getLong("createdAt")      ?: 0L,
                            replyToAlias = doc.getString("replyToAlias") ?: ""
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                _replies.value = _replies.value + (key to list)
            }
        replyListeners[key] = reg
    }

    fun stopListenReplies(postId: String, commentId: String) {
        val key = "${postId}__${commentId}"
        replyListeners.remove(key)?.remove()
        _replies.value = _replies.value - key
    }

    fun postReply(postId: String, commentId: String, text: String, replyToAlias: String, onDone: () -> Unit = {}) {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                val myAlias    = getMyAlias()
                val commentRef = db.collection("introvertPosts").document(postId)
                    .collection("comments").document(commentId)
                val replyRef   = commentRef.collection("replies").document()
                replyRef.set(mapOf(
                    "replyId"      to replyRef.id,
                    "authorId"     to uid,
                    "authorAlias"  to myAlias,
                    "text"         to text.trim(),
                    "createdAt"    to System.currentTimeMillis(),
                    "replyToAlias" to replyToAlias
                )).await()
                commentRef.update("replyCount", FieldValue.increment(1)).await()

                // Notify comment author
                val commentAuthorId = commentRef.get().await().getString("authorId") ?: ""
                if (commentAuthorId.isNotBlank() && commentAuthorId != uid) {
                    writeNotif(commentAuthorId, "reply", myAlias, text.trim().take(60), postId, commentId)
                }
                onDone()
            } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Notifications
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadNotifications() {
        if (uid.isEmpty()) return
        notifListener = db.collection("feedNotifications").document(uid)
            .collection("items")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        FeedNotif(
                            notifId     = doc.id,
                            type        = doc.getString("type")        ?: "",
                            fromAlias   = doc.getString("fromAlias")   ?: "",
                            previewText = doc.getString("previewText") ?: "",
                            postId      = doc.getString("postId")      ?: "",
                            commentId   = doc.getString("commentId")   ?: "",
                            createdAt   = doc.getLong("createdAt")     ?: 0L,
                            read        = doc.getBoolean("read")       ?: false
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                _notifs.value       = list
                _unreadCount.value  = list.count { !it.read }
            }
    }

    fun markAllNotifsRead() {
        if (uid.isEmpty()) return
        val unread = _notifs.value.filter { !it.read }
        if (unread.isEmpty()) return
        val batch = db.batch()
        unread.forEach { n ->
            val ref = db.collection("feedNotifications").document(uid)
                .collection("items").document(n.notifId)
            batch.update(ref, "read", true)
        }
        viewModelScope.launch { runCatching { batch.commit().await() } }
    }

    private suspend fun writeNotif(toUid: String, type: String, fromAlias: String, preview: String, postId: String, commentId: String) {
        runCatching {
            val ref = db.collection("feedNotifications").document(toUid).collection("items").document()
            ref.set(mapOf(
                "type" to type, "fromAlias" to fromAlias,
                "previewText" to preview, "postId" to postId,
                "commentId" to commentId, "createdAt" to System.currentTimeMillis(),
                "read" to false
            )).await()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun getMyAlias(): String {
        if (_cachedAlias.isNotBlank()) return _cachedAlias
        return try {
            val alias = db.collection("users").document(uid).get().await()
                .getString("alias") ?: "Introvert"
            _cachedAlias = alias
            alias
        } catch (_: Exception) { "Introvert" }
    }

    private fun generateAnonymousAlias(): String {
        val prefixes = listOf("Quiet Soul","Silent Thinker","Lone Wolf","Deep Mind",
            "Peaceful Heart","Calm Spirit","Thoughtful One","Gentle Soul",
            "Reserved Mind","Introspective","Solitary Star","Quiet Storm",
            "Hidden Gem","Silent Observer","Peaceful Wanderer","Quiet Dreamer")
        return "${prefixes.random()} #${(1000..9999).random()}"
    }

    override fun onCleared() {
        super.onCleared()
        commentListeners.values.forEach { it.remove() }
        replyListeners.values.forEach { it.remove() }
        notifListener?.remove()
    }
}
