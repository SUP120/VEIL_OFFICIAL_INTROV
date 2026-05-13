package com.ns.findyourintroverfriend.aesthetics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.ns.findyourintroverfriend.BuildConfig
import com.ns.findyourintroverfriend.util.CloudinaryUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

private const val TAG               = "AestheticsVM"
private const val MAX_POSTS_PER_DAY = 2
private const val LABEL_CONFIDENCE  = 0.75f

private val BLOCKED_LABELS = setOf(
    "Nudity", "Adult content", "Explicit content", "Violence",
    "Blood", "Weapon", "Drug", "Alcohol", "Gore"
)

// ─────────────────────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────────────────────

data class AestheticPost(
    val postId        : String  = "",
    val uploaderUid   : String  = "",
    val uploaderAlias : String  = "",   // shown in feed (Day 1+)
    val uploaderEmoji : String  = "",
    val imageUrl      : String  = "",
    val caption       : String  = "",
    val postedAt      : Long    = 0L,
    val day           : Int     = 1,
    val heartCount    : Int     = 0,
    val myHearted     : Boolean = false
)

data class UserPostGroup(
    val uploaderUid   : String             = "",
    val uploaderAlias : String             = "",
    val uploaderEmoji : String             = "",
    val posts         : List<AestheticPost> = emptyList()
)

data class AestheticsFeedUiState(
    val posts          : List<AestheticPost>  = emptyList(),
    val groups         : List<UserPostGroup>  = emptyList(),
    val blockedUids    : Set<String>          = emptySet(),
    val reportedPostIds: Set<String>          = emptySet(),
    val loading        : Boolean              = true,
    val postsToday     : Int                  = 0,
    val uploadState    : UploadState          = UploadState.Idle,
    val error          : String?              = null
)

sealed class UploadState {
    object Idle                             : UploadState()
    object CheckingImage                    : UploadState()
    object Uploading                        : UploadState()
    object Success                          : UploadState()
    data class Rejected(val reason: String) : UploadState()
    data class Failure(val msg: String)     : UploadState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class AestheticsFeedViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(AestheticsFeedUiState())
    val state: StateFlow<AestheticsFeedUiState> = _state.asStateFlow()

    private var groupId   = ""
    private var myUid     = ""
    private var myAlias   = ""
    private var myEmoji   = ""
    private var groupDay  = 1
    private var feedListener: ListenerRegistration? = null

    init { bootstrap() }

    // ── Bootstrap ─────────────────────────────────────────────────────────────
    private fun bootstrap() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = _state.value.copy(loading = false, error = "Not signed in.")
            return
        }
        myUid = uid
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                groupId  = userDoc.getString("groupId") ?: ""
                myEmoji  = userDoc.getString("emoji")   ?: ""
                myAlias  = userDoc.getString("alias")   ?: "Anonymous"

                if (groupId.isBlank()) {
                    _state.value = _state.value.copy(loading = false, error = "No active group.")
                    return@launch
                }

                val groupDoc = db.collection("groups").document(groupId).get().await()
                groupDay = (groupDoc.getLong("day") ?: 1L).toInt()

                // Load blocked UIDs from Firestore
                loadBlockedUids()
                countMyPostsToday()
                startFeedListener()
            } catch (e: Exception) {
                Log.e(TAG, "bootstrap failed", e)
                _state.value = _state.value.copy(loading = false, error = e.localizedMessage)
            }
        }
    }

    private suspend fun loadBlockedUids() {
        try {
            val doc = db.collection("users").document(myUid)
                .collection("aesthetics_blocks").document("blocked").get().await()
            @Suppress("UNCHECKED_CAST")
            val blocked = (doc.get("uids") as? List<String>)?.toSet() ?: emptySet()
            _state.value = _state.value.copy(blockedUids = blocked)
        } catch (_: Exception) {}
    }

    private suspend fun countMyPostsToday() {
        val key = todayKey()
        val snap = db.collection("groups").document(groupId)
            .collection("aesthetics_posts")
            .whereEqualTo("uploaderUid", myUid)
            .whereEqualTo("dayKey", key)
            .get().await()
        _state.value = _state.value.copy(postsToday = snap.size())
    }

    private fun startFeedListener() {
        feedListener?.remove()
        feedListener = db.collection("groups").document(groupId)
            .collection("aesthetics_posts")
            .orderBy("postedAt", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "feed error", err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener

                val blocked   = _state.value.blockedUids
                val reported  = _state.value.reportedPostIds

                val posts = snap.documents.mapNotNull { doc ->
                    try {
                        val uid = doc.getString("uploaderUid") ?: ""
                        if (uid in blocked) return@mapNotNull null   // skip blocked users
                        val postId = doc.id
                        if (postId in reported) return@mapNotNull null  // skip locally reported

                        val hearts  = (doc.get("hearts") as? Map<*, *>)?.keys?.size ?: 0
                        val myHeart = (doc.get("hearts") as? Map<*, *>)?.containsKey(myUid) == true

                        AestheticPost(
                            postId        = postId,
                            uploaderUid   = uid,
                            uploaderAlias = doc.getString("uploaderAlias") ?: "Anonymous",
                            uploaderEmoji = doc.getString("uploaderEmoji") ?: "",
                            imageUrl      = doc.getString("imageUrl")      ?: "",
                            caption       = doc.getString("caption")       ?: "",
                            postedAt      = doc.getTimestamp("postedAt")?.seconds ?: 0L,
                            day           = (doc.getLong("day") ?: 1L).toInt(),
                            heartCount    = hearts,
                            myHearted     = myHeart
                        )
                    } catch (_: Exception) { null }
                }

                val groups = posts
                    .groupBy { it.uploaderUid }
                    .entries
                    .sortedBy { (_, p) -> p.minOf { it.postedAt } }
                    .map { (uid, userPosts) ->
                        UserPostGroup(
                            uploaderUid   = uid,
                            uploaderAlias = userPosts.first().uploaderAlias,
                            uploaderEmoji = userPosts.first().uploaderEmoji,
                            posts         = userPosts.sortedByDescending { it.postedAt }
                        )
                    }

                _state.value = _state.value.copy(posts = posts, groups = groups, loading = false)
            }
    }

    // ── Upload ────────────────────────────────────────────────────────────────
    fun uploadPost(context: Context, uri: Uri, caption: String) {
        if (_state.value.postsToday >= MAX_POSTS_PER_DAY) {
            _state.value = _state.value.copy(
                uploadState = UploadState.Rejected("You've already posted $MAX_POSTS_PER_DAY photos today.")
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(uploadState = UploadState.CheckingImage)
            try {
                val blocked = isImageBlocked(context, uri)
                if (blocked != null) {
                    _state.value = _state.value.copy(uploadState = UploadState.Rejected(blocked))
                    return@launch
                }
            } catch (e: Exception) { Log.w(TAG, "ML Kit skipped: ${e.message}") }

            _state.value = _state.value.copy(uploadState = UploadState.Uploading)
            try {
                val cloudName    = BuildConfig.CLOUDINARY_CLOUD_NAME
                val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET

                val imageUrl: String = if (cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
                    CloudinaryUploader.upload(context, uri, cloudName, uploadPreset)
                } else {
                    Log.w(TAG, "Cloudinary not configured — Base64 fallback")
                    toBase64(context, uri)
                }

                val docRef = db.collection("groups").document(groupId)
                    .collection("aesthetics_posts").document()

                docRef.set(mapOf(
                    "postId"        to docRef.id,
                    "uploaderUid"   to myUid,
                    "uploaderAlias" to myAlias,
                    "uploaderEmoji" to myEmoji,
                    "imageUrl"      to imageUrl,
                    "caption"       to caption.trim(),
                    "postedAt"      to Timestamp.now(),
                    "dayKey"        to todayKey(),
                    "day"           to groupDay,
                    "hearts"        to emptyMap<String, Boolean>(),
                    "reportCount"   to 0
                )).await()

                _state.value = _state.value.copy(
                    uploadState = UploadState.Success,
                    postsToday  = _state.value.postsToday + 1
                )
            } catch (e: Exception) {
                Log.e(TAG, "upload failed", e)
                _state.value = _state.value.copy(
                    uploadState = UploadState.Failure(e.localizedMessage ?: "Upload failed.")
                )
            }
        }
    }

    // ── Heart ─────────────────────────────────────────────────────────────────
    fun toggleHeart(post: AestheticPost) {
        val ref = db.collection("groups").document(groupId)
            .collection("aesthetics_posts").document(post.postId)
        viewModelScope.launch {
            try {
                if (post.myHearted) ref.update("hearts.$myUid", FieldValue.delete()).await()
                else                ref.update("hearts.$myUid", true).await()
            } catch (e: Exception) { Log.e(TAG, "heart failed", e) }
        }
    }

    // ── Report — hides post locally immediately ────────────────────────────────
    fun reportPost(postId: String, uploaderUid: String, reason: String) {
        // Immediately remove from local state so it disappears from UI
        val newReported = _state.value.reportedPostIds + postId
        val newGroups = _state.value.groups
            .map { group ->
                group.copy(posts = group.posts.filter { it.postId != postId })
            }
            .filter { it.posts.isNotEmpty() }
        _state.value = _state.value.copy(
            reportedPostIds = newReported,
            groups          = newGroups,
            posts           = _state.value.posts.filter { it.postId != postId }
        )

        viewModelScope.launch {
            try {
                // Write to post_reports (for admin photo reports tab)
                db.collection("post_reports").document().set(mapOf(
                    "postId"        to postId,
                    "uploaderUid"   to uploaderUid,
                    "reporterUid"   to myUid,
                    "groupId"       to groupId,
                    "reason"        to reason,
                    "reportedAt"    to Timestamp.now(),
                    "type"          to "photo"
                )).await()
                // Increment reportCount on the post document
                db.collection("groups").document(groupId)
                    .collection("aesthetics_posts").document(postId)
                    .update("reportCount", FieldValue.increment(1)).await()
            } catch (e: Exception) { Log.e(TAG, "report failed", e) }
        }
    }

    // ── Block user — hides ALL their posts permanently ────────────────────────
    fun blockUser(uploaderUid: String) {
        val newBlocked = _state.value.blockedUids + uploaderUid
        val newGroups  = _state.value.groups.filter { it.uploaderUid != uploaderUid }
        val newPosts   = _state.value.posts.filter { it.uploaderUid != uploaderUid }
        _state.value = _state.value.copy(
            blockedUids = newBlocked,
            groups      = newGroups,
            posts       = newPosts
        )
        // Persist to Firestore
        viewModelScope.launch {
            try {
                db.collection("users").document(myUid)
                    .collection("aesthetics_blocks").document("blocked")
                    .set(mapOf("uids" to newBlocked.toList()), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            } catch (e: Exception) { Log.e(TAG, "block failed", e) }
        }
    }

    fun resetUploadState() {
        _state.value = _state.value.copy(uploadState = UploadState.Idle)
    }

    // ── ML Kit ────────────────────────────────────────────────────────────────
    private suspend fun isImageBlocked(context: Context, uri: Uri): String? =
        suspendCancellableCoroutine { cont ->
            try {
                val image   = InputImage.fromFilePath(context, uri)
                val labeler = ImageLabeling.getClient(
                    ImageLabelerOptions.Builder().setConfidenceThreshold(LABEL_CONFIDENCE).build()
                )
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        val hit = labels.firstOrNull { it.text in BLOCKED_LABELS }
                        if (hit != null) cont.resume(
                            "This image was flagged as inappropriate (${hit.text}). " +
                            "Please post only face photos or aesthetic content."
                        ) {}
                        else cont.resume(null) {}
                    }
                    .addOnFailureListener { cont.resume(null) {} }
            } catch (_: Exception) { cont.resume(null) {} }
        }

    // ── Base64 fallback ───────────────────────────────────────────────────────
    private fun toBase64(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open image")
        val bitmap = BitmapFactory.decodeStream(stream)
        stream.close()
        val scaled = Bitmap.createScaledBitmap(bitmap!!, 600, 600, true)
        bitmap.recycle()
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        scaled.recycle()
        return "data:image/jpeg;base64," +
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun todayKey(): String {
        val c = java.util.Calendar.getInstance()
        return "${c.get(java.util.Calendar.YEAR)}_${c.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    override fun onCleared() { super.onCleared(); feedListener?.remove() }
}
