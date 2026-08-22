package com.ns.findyourintroverfriend.feed

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.util.UUID

class FeedViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    val uid: String get() = auth.currentUser?.uid ?: ""

    private val _posts      = MutableStateFlow<List<VeilPost>>(emptyList())
    val posts: StateFlow<List<VeilPost>> = _posts.asStateFlow()

    private val _comments   = MutableStateFlow<List<VeilComment>>(emptyList())
    val comments: StateFlow<List<VeilComment>> = _comments.asStateFlow()

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

    private val _myAlias  = MutableStateFlow("")
    private val _myPhoto  = MutableStateFlow("")
    private val _myAvatar = MutableStateFlow("")
    val myAlias : StateFlow<String> = _myAlias.asStateFlow()
    val myPhoto : StateFlow<String> = _myPhoto.asStateFlow()
    val myAvatar: StateFlow<String> = _myAvatar.asStateFlow()

    // ── Pagination cursor ─────────────────────────────────────────────────────
    private var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private val _hasMore        = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private val _isLoadingMore  = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    // Fetch a larger batch so the ranker has enough variety to shuffle meaningfully
    private val PAGE_SIZE = 50L

    // ── Rate limiting ─────────────────────────────────────────────────────────────
    // Prevent spam: max 1 like-toggle per 1.5 seconds per post.
    private val lastLikeTime = mutableMapOf<String, Long>()
    private val LIKE_COOLDOWN_MS = 1500L

    // ── In-app notifications (real-time) ─────────────────────────────────────────
    /** Raw notification docs for the panel (latest 30, newest first) */
    private val _notifItems    = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val notifItems: StateFlow<List<Map<String, Any>>> = _notifItems.asStateFlow()

    /** Count of unread notifications — drives the red dot on the bell */
    private val _unreadCount   = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private var notifListener: ListenerRegistration? = null

    init { loadMyProfile(); loadFeed(); startNotifListener() }

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

    // ── Feed ─────────────────────────────────────────────────────────────
    fun loadFeed() {
        viewModelScope.launch {
            _loading.value = true
            lastDoc = null          // reset cursor
            _hasMore.value = true
            try {
                val snap = db.collection("veilPosts")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE).get().await()
                lastDoc = snap.documents.lastOrNull()
                _hasMore.value = snap.documents.size.toLong() == PAGE_SIZE
                val raw = snap.documents.mapNotNull { toPost(it) }
                // ── Rank: engagement × recency × session noise ──────────────────────
                _posts.value = com.ns.findyourintroverfriend.util.FeedRanker.rank(
                    posts          = raw,
                    getPostId      = { it.postId },
                    getLikeCount   = { it.likedBy.size },
                    getCommentCount = { it.commentsCount.toInt() },
                    getCreatedAt   = { it.createdAt },
                    isDeveloper    = { com.ns.findyourintroverfriend.util.VeilDevConfig.isDeveloper(it.authorUid) }
                )
            } catch (e: Exception) { Log.e("FeedVM", "loadFeed: ${e.message}") }
            finally { _loading.value = false }
        }
    }

    /** Append next page — called when user scrolls near the bottom */
    fun loadMore() {
        val cursor = lastDoc ?: return
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val snap = db.collection("veilPosts")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(cursor)
                    .limit(PAGE_SIZE).get().await()
                lastDoc = snap.documents.lastOrNull() ?: cursor
                _hasMore.value = snap.documents.size.toLong() == PAGE_SIZE
                val raw = snap.documents.mapNotNull { toPost(it) }
                val ranked = com.ns.findyourintroverfriend.util.FeedRanker.rank(
                    posts          = raw,
                    getPostId      = { it.postId },
                    getLikeCount   = { it.likedBy.size },
                    getCommentCount = { it.commentsCount.toInt() },
                    getCreatedAt   = { it.createdAt },
                    isDeveloper    = { com.ns.findyourintroverfriend.util.VeilDevConfig.isDeveloper(it.authorUid) }
                )
                _posts.value = _posts.value + ranked
            } catch (e: Exception) { Log.e("FeedVM", "loadMore: ${e.message}") }
            finally { _isLoadingMore.value = false }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Rotate salt → completely new ordering every pull-to-refresh
            com.ns.findyourintroverfriend.util.FeedRanker.newSession()
            lastDoc = null
            _hasMore.value = true
            try {
                val snap = db.collection("veilPosts")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE).get().await()
                lastDoc = snap.documents.lastOrNull()
                _hasMore.value = snap.documents.size.toLong() == PAGE_SIZE
                val raw = snap.documents.mapNotNull { toPost(it) }
                _posts.value = com.ns.findyourintroverfriend.util.FeedRanker.rank(
                    posts          = raw,
                    getPostId      = { it.postId },
                    getLikeCount   = { it.likedBy.size },
                    getCommentCount = { it.commentsCount.toInt() },
                    getCreatedAt   = { it.createdAt },
                    isDeveloper    = { com.ns.findyourintroverfriend.util.VeilDevConfig.isDeveloper(it.authorUid) }
                )
            } catch (e: Exception) { Log.e("FeedVM", "refresh: ${e.message}") }
            finally { _isRefreshing.value = false }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toPost(doc: com.google.firebase.firestore.DocumentSnapshot): VeilPost? = try {
        VeilPost(
            postId         = doc.id,
            authorUid      = doc.getString("authorUid")      ?: "",
            authorAlias    = doc.getString("authorAlias")    ?: "Anonymous",
            authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
            authorAvatar   = doc.getString("authorAvatar")   ?: "",
            caption        = doc.getString("caption")        ?: "",
            imageUrls      = (doc.get("imageUrls") as? List<String>) ?: emptyList(),
            likedBy        = (doc.get("likedBy")   as? List<String>) ?: emptyList(),
            commentsCount  = doc.getLong("commentsCount")    ?: 0L,
            createdAt      = doc.getLong("createdAt")        ?: 0L
        )
    } catch (_: Exception) { null }

    // ── Likes ───────────────────────────────────────────────────────────────────
    fun toggleLike(post: VeilPost) {
        if (uid.isEmpty()) return
        // — Rate limit: ignore if tapped too fast (prevents Firestore write spam)
        val now = System.currentTimeMillis()
        if (now - (lastLikeTime[post.postId] ?: 0L) < LIKE_COOLDOWN_MS) return
        lastLikeTime[post.postId] = now

        val already = post.likedBy.contains(uid)
        _posts.value = _posts.value.map { p ->
            if (p.postId != post.postId) p
            else {
                val newLikes: List<String> = if (already) p.likedBy - uid else p.likedBy + uid
                p.copy(likedBy = newLikes)
            }
        }
        viewModelScope.launch {
            try {
                val ref = db.collection("veilPosts").document(post.postId)
                if (already) ref.update("likedBy", FieldValue.arrayRemove(uid)).await()
                else         ref.update("likedBy", FieldValue.arrayUnion(uid)).await()
            } catch (e: Exception) {
                Log.e("FeedVM", "toggleLike: ${e.message}")
                _posts.value = _posts.value.map { p ->
                    if (p.postId == post.postId) p.copy(likedBy = post.likedBy) else p
                }
            }
        }
    }

    // ── Comments ──────────────────────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                val snap = db.collection("veilPosts").document(postId)
                    .collection("comments")
                    .orderBy("createdAt", Query.Direction.ASCENDING).get().await()
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
                db.collection("veilPosts").document(postId)
                    .collection("comments").document(id).set(data).await()
                db.collection("veilPosts").document(postId)
                    .update("commentsCount", FieldValue.increment(1)).await()
                loadComments(postId)
                _posts.value = _posts.value.map { p ->
                    if (p.postId == postId) p.copy(commentsCount = p.commentsCount + 1) else p
                }
                // Write in-app notification + push for post author
                val postDoc = db.collection("veilPosts").document(postId).get().await()
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
                commentRef.collection("replies").document(id).set(data).await()
                commentRef.update("replyCount", FieldValue.increment(1)).await()

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
        if (uid.isEmpty()) return
        if (draftCaption.isBlank() && draftImageUris.isEmpty()) return
        viewModelScope.launch {
            _isPosting.value = true
            try {
                val cloudName    = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_CLOUD_NAME
                val uploadPreset = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_UPLOAD_PRESET

                // ── Upload each image to Cloudinary ───────────────────────────
                val imageUrls = mutableListOf<String>()
                for (uri in draftImageUris) {
                    try {
                        if (cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
                            val url = com.ns.findyourintroverfriend.util.CloudinaryUploader.upload(
                                context = context, uri = uri,
                                cloudName = cloudName, uploadPreset = uploadPreset
                            )
                            imageUrls.add(url)
                        } else {
                            Log.w("FeedVM", "Cloudinary not configured — skipping image upload")
                        }
                    } catch (e: Exception) {
                        Log.e("FeedVM", "Image upload failed: ${e.message}")
                        // Skip failed images — don't block the whole post
                    }
                }

                val postId = UUID.randomUUID().toString()
                val data = mapOf(
                    "postId"         to postId,
                    "authorUid"      to uid,
                    "authorAlias"    to _myAlias.value,
                    "authorPhotoUrl" to _myPhoto.value,   // empty if camera shy
                    "authorAvatar"   to _myAvatar.value,  // avatar name if camera shy
                    "caption"        to draftCaption.trim(),
                    "imageUrls"      to imageUrls,         // ✅ real Cloudinary URLs
                    "likedBy"        to emptyList<String>(),
                    "commentsCount"  to 0L,
                    "createdAt"      to System.currentTimeMillis()
                )
                db.collection("veilPosts").document(postId).set(data).await()
                draftCaption   = ""
                draftImageUris = emptyList()
                refresh()
                onDone()
            } catch (e: Exception) { Log.e("FeedVM", "submitPost: ${e.message}") }
            finally { _isPosting.value = false }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                db.collection("veilPosts").document(postId).delete().await()
                _posts.value = _posts.value.filter { it.postId != postId }
            } catch (e: Exception) { Log.e("FeedVM", "deletePost: ${e.message}") }
        }
    }

    fun reportPost(postId: String, reason: String, note: String) {
        viewModelScope.launch {
            try {
                db.collection("veilReports").add(
                    mapOf(
                        "type"       to "post",
                        "postId"     to postId,
                        "reportedBy" to uid,
                        "reason"     to reason,
                        "note"       to note,
                        "reportedAt" to System.currentTimeMillis(),
                        "status"     to "pending"
                    )
                ).await()
                db.collection("veilPosts").document(postId)
                    .update("flaggedAt", System.currentTimeMillis()).await()
            } catch (e: Exception) { Log.e("FeedVM", "reportPost: ${e.message}") }
        }
    }

    /** Instantly remove post from local feed (optimistic, no network needed) */
    fun hidePostLocally(postId: String) {
        _posts.value = _posts.value.filter { it.postId != postId }
    }

    override fun onCleared() {
        super.onCleared()
        notifListener?.remove()
    }
}
