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
import com.ns.findyourintroverfriend.util.ContentModerator

// ── Spark Type ────────────────────────────────────────────────────────────────

sealed class SparkType(val key: String, val label: String) {
    object Question         : SparkType("question",          "QUESTION")
    object HotTake          : SparkType("hot_take",          "HOT TAKE")
    object Confession       : SparkType("confession",        "CONFESSION")
    object UnpopularOpinion : SparkType("unpopular_opinion", "UNPOPULAR OPINION")
    object JustWondering    : SparkType("just_wondering",    "JUST WONDERING")

    companion object {
        val all = listOf(Question, HotTake, Confession, UnpopularOpinion, JustWondering)
        fun fromKey(key: String) = all.find { it.key == key } ?: Question
    }
}

// ── Data Models ───────────────────────────────────────────────────────────────

data class VeilSpark(
    val sparkId        : String  = "",
    val authorId       : String  = "",
    val authorAlias    : String  = "Anonymous",
    val content        : String  = "",
    val type           : String  = "question",
    val isAnonymous    : Boolean = false,
    val resonanceCount : Int     = 0,
    val disagreeCount  : Int     = 0,
    val answerCount    : Int     = 0,
    val createdAt      : Long    = 0L,
    val myResonance    : Boolean = false,   // tracked locally per session
    val myDisagree     : Boolean = false    // tracked locally per session
)

data class SparkAnswer(
    val answerId    : String = "",
    val sparkId     : String = "",
    val authorId    : String = "",
    val authorAlias : String = "Anonymous",
    val content     : String = "",
    val score       : Int    = 0,          // net votes (upvotes - downvotes)
    val createdAt   : Long   = 0L,
    val myVote      : Int    = 0           // -1, 0, or +1 — tracked locally
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class VeilSparksViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""

    // ── Sparks ────────────────────────────────────────────────────────────────
    private val _sparks  = MutableStateFlow<List<VeilSpark>>(emptyList())
    val sparks: StateFlow<List<VeilSpark>> = _sparks.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _posting   = MutableStateFlow(false)
    val posting: StateFlow<Boolean> = _posting.asStateFlow()

    private val _postError = MutableStateFlow<String?>(null)
    val postError: StateFlow<String?> = _postError.asStateFlow()

    private val _sortMode   = MutableStateFlow("fresh")   // "fresh" | "hot"
    val sortMode: StateFlow<String> = _sortMode.asStateFlow()

    private val _typeFilter = MutableStateFlow("all")
    val typeFilter: StateFlow<String> = _typeFilter.asStateFlow()

    // ── Answers (keyed by sparkId) ────────────────────────────────────────────
    private val _answers = MutableStateFlow<Map<String, List<SparkAnswer>>>(emptyMap())
    val answers: StateFlow<Map<String, List<SparkAnswer>>> = _answers.asStateFlow()

    private val answerListeners = mutableMapOf<String, ListenerRegistration>()

    // ── Local tracking (per-session, no Firestore read needed) ────────────────
    private val localVotes  = mutableMapOf<String, Int>()    // answerId → -1/0/1
    private val resonated   = mutableSetOf<String>()         // sparkId set (agreed)
    private val dissenters  = mutableSetOf<String>()         // sparkId set (disagreed)

    private var cachedAlias = ""

    init { loadAlias(); loadSparks() }

    // ─────────────────────────────────────────────────────────────────────────
    //  Alias helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadAlias() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            cachedAlias = try {
                db.collection("swipeProfiles").document(uid).get().await()
                    .getString("alias")
                    ?: db.collection("users").document(uid).get().await()
                        .getString("alias")
                    ?: "Introvert"
            } catch (_: Exception) { "Introvert" }
        }
    }

    private suspend fun alias(): String {
        if (cachedAlias.isNotBlank()) return cachedAlias
        return try {
            val a = db.collection("swipeProfiles").document(uid).get().await()
                .getString("alias")
                ?: db.collection("users").document(uid).get().await()
                    .getString("alias")
                ?: "Introvert"
            cachedAlias = a; a
        } catch (_: Exception) { "Introvert" }
    }

    private fun anonAlias(): String {
        val words = listOf(
            "Quiet Soul", "Silent Mind", "Lone Star", "Deep Think",
            "Calm Voice", "Hidden Light", "Reserved One", "Still Water",
            "Soft Echo", "Inner World", "Shadowed Thought", "Gentle Ghost"
        )
        return "${words.random()} #${(1000..9999).random()}"
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load Sparks
    // ─────────────────────────────────────────────────────────────────────────

    fun loadSparks() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val filter = _typeFilter.value
                val snap = if (filter == "all") {
                    db.collection("veilSparks")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(40)
                        .get().await()
                } else {
                    db.collection("veilSparks")
                        .whereEqualTo("type", filter)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(40)
                        .get().await()
                }

                val list = snap.documents.mapNotNull { doc ->
                    try {
                        VeilSpark(
                            sparkId        = doc.id,
                            authorId       = doc.getString("authorId")       ?: "",
                            authorAlias    = doc.getString("authorAlias")    ?: "Anonymous",
                            content        = doc.getString("content")        ?: "",
                            type           = doc.getString("type")           ?: "question",
                            isAnonymous    = doc.getBoolean("isAnonymous")   ?: false,
                            resonanceCount = (doc.getLong("resonanceCount")  ?: 0L).toInt(),
                            disagreeCount  = (doc.getLong("disagreeCount")   ?: 0L).toInt(),
                            answerCount    = (doc.getLong("answerCount")     ?: 0L).toInt(),
                            createdAt      = doc.getLong("createdAt")        ?: 0L,
                            myResonance    = doc.id in resonated,
                            myDisagree     = doc.id in dissenters
                        )
                    } catch (_: Exception) { null }
                }
                _sparks.value = sorted(list)
            } catch (_: Exception) { }
            finally { _loading.value = false }
        }
    }

    private fun sorted(list: List<VeilSpark>) = when (_sortMode.value) {
        "hot" -> list.sortedByDescending { it.answerCount * 3 + it.resonanceCount }
        else  -> list.sortedByDescending { it.createdAt }
    }

    fun setSort(mode: String) {
        if (_sortMode.value == mode) return
        _sortMode.value = mode
        _sparks.value = sorted(_sparks.value)
    }

    fun setTypeFilter(type: String) {
        if (_typeFilter.value == type) return
        _typeFilter.value = type
        loadSparks()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Post a Spark
    // ─────────────────────────────────────────────────────────────────────────

    fun postSpark(content: String, type: String, isAnonymous: Boolean, onDone: () -> Unit = {}) {
        val currentUid = uid
        android.util.Log.d("SPARKS_DEBUG", "postSpark called: uid='$currentUid' content='${content.take(30)}' len=${content.length}")
        if (currentUid.isEmpty()) {
            android.util.Log.e("SPARKS_DEBUG", "ABORT: uid is empty — user not logged in")
            onDone(); return
        }
        if (content.isBlank() || content.length < 5) {
            android.util.Log.e("SPARKS_DEBUG", "ABORT: content too short (${content.length} chars)")
            onDone(); return
        }
        viewModelScope.launch {
            _posting.value = true
            _postError.value = null
            var success = false
            try {
                val a   = if (isAnonymous) anonAlias() else alias()
                android.util.Log.d("SPARKS_DEBUG", "alias resolved: '$a'")
                val ref = db.collection("veilSparks").document()
                val now = System.currentTimeMillis()
                val data = mapOf(
                    "authorId"       to currentUid,
                    "authorAlias"    to a,
                    "content"        to content.trim(),
                    "type"           to type,
                    "isAnonymous"    to isAnonymous,
                    "resonanceCount" to 0,
                    "disagreeCount"  to 0,
                    "answerCount"    to 0,
                    "createdAt"      to now
                )
                android.util.Log.d("SPARKS_DEBUG", "Writing to Firestore: docId=${ref.id} authorId=$currentUid")
                ref.set(data).await()
                android.util.Log.d("SPARKS_DEBUG", "Firestore write SUCCESS")
                val newSpark = VeilSpark(
                    ref.id, currentUid, a, content.trim(),
                    type, isAnonymous, 0, 0, 0, now
                )
                _sparks.value = listOf(newSpark) + _sparks.value
                success = true
            } catch (e: Exception) {
                android.util.Log.e("SPARKS_DEBUG", "Firestore write FAILED: ${e.javaClass.simpleName}: ${e.message}")
                _postError.value = "Could not post — ${e.message?.take(80) ?: "Unknown error"}"
            } finally {
                android.util.Log.d("SPARKS_DEBUG", "finally: success=$success, calling onDone()")
                _posting.value = false
                onDone()   // always dismiss — success or failure
            }
        }
    }

    fun clearPostError() { _postError.value = null }

    // ─────────────────────────────────────────────────────────────────────────
    //  Answers — real-time listener per expanded spark
    // ─────────────────────────────────────────────────────────────────────────

    fun listenAnswers(sparkId: String) {
        if (answerListeners.containsKey(sparkId)) return
        val reg = db.collection("veilSparks").document(sparkId)
            .collection("answers")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        SparkAnswer(
                            answerId    = doc.id,
                            sparkId     = sparkId,
                            authorId    = doc.getString("authorId")    ?: "",
                            authorAlias = doc.getString("authorAlias") ?: "Anonymous",
                            content     = doc.getString("content")     ?: "",
                            score       = (doc.getLong("score") ?: 0L).toInt(),
                            createdAt   = doc.getLong("createdAt") ?: 0L,
                            myVote      = localVotes[doc.id] ?: 0
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                _answers.value = _answers.value + (sparkId to list)
            }
        answerListeners[sparkId] = reg
    }

    fun stopListenAnswers(sparkId: String) {
        answerListeners.remove(sparkId)?.remove()
        _answers.value = _answers.value - sparkId
    }

    fun postAnswer(sparkId: String, content: String, onDone: () -> Unit = {}) {
        if (uid.isEmpty() || content.isBlank()) return
        val modResult = ContentModerator.check(content)
        if (!modResult.isSafe) return   // UI-level moderation already blocks, VM is safety net
        viewModelScope.launch {
            try {
                val a        = alias()
                val sparkRef = db.collection("veilSparks").document(sparkId)
                val ansRef   = sparkRef.collection("answers").document()
                ansRef.set(mapOf(
                    "authorId"    to uid,
                    "authorAlias" to a,
                    "content"     to content.trim(),
                    "score"       to 0,
                    "createdAt"   to System.currentTimeMillis()
                )).await()
                sparkRef.update("answerCount", FieldValue.increment(1)).await()
                _sparks.value = _sparks.value.map { s ->
                    if (s.sparkId == sparkId) s.copy(answerCount = s.answerCount + 1) else s
                }
                onDone()
            } catch (_: Exception) { }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Vote on an answer (toggling)
    // ─────────────────────────────────────────────────────────────────────────

    fun vote(sparkId: String, answerId: String, value: Int) {
        if (uid.isEmpty()) return
        val current = localVotes[answerId] ?: 0
        val newVote = if (current == value) 0 else value   // toggle off if same
        val delta   = newVote - current
        if (delta == 0) return

        localVotes[answerId] = newVote

        // Optimistic local update
        _answers.value = _answers.value.mapValues { (sid, list) ->
            if (sid != sparkId) list
            else list.map { a ->
                if (a.answerId != answerId) a
                else a.copy(score = a.score + delta, myVote = newVote)
            }
        }

        viewModelScope.launch {
            try {
                db.collection("veilSparks").document(sparkId)
                    .collection("answers").document(answerId)
                    .update("score", FieldValue.increment(delta.toLong()))
                    .await()
            } catch (_: Exception) { }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Resonate with a spark ("I've thought about this too")
    // ─────────────────────────────────────────────────────────────────────────

    fun resonate(sparkId: String) {
        if (uid.isEmpty()) return
        val wasResonated = sparkId in resonated
        val delta = if (wasResonated) -1 else 1
        if (wasResonated) resonated.remove(sparkId) else resonated.add(sparkId)

        _sparks.value = _sparks.value.map { s ->
            if (s.sparkId == sparkId) s.copy(
                resonanceCount = (s.resonanceCount + delta).coerceAtLeast(0),
                myResonance    = !wasResonated
            ) else s
        }

        viewModelScope.launch {
            try {
                val sparkRef  = db.collection("veilSparks").document(sparkId)
                val resonRef  = sparkRef.collection("resonators").document(uid)
                sparkRef.update("resonanceCount", FieldValue.increment(delta.toLong())).await()
                if (wasResonated) resonRef.delete().await()
                else resonRef.set(mapOf("at" to System.currentTimeMillis())).await()
            } catch (_: Exception) { }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Disagree with a spark ("Nope" button)
    // ─────────────────────────────────────────────────────────────────────────

    fun disagree(sparkId: String) {
        if (uid.isEmpty()) return
        val wasDisagreed = sparkId in dissenters
        val delta = if (wasDisagreed) -1 else 1
        if (wasDisagreed) dissenters.remove(sparkId) else dissenters.add(sparkId)

        _sparks.value = _sparks.value.map { s ->
            if (s.sparkId == sparkId) s.copy(
                disagreeCount = (s.disagreeCount + delta).coerceAtLeast(0),
                myDisagree    = !wasDisagreed
            ) else s
        }

        viewModelScope.launch {
            try {
                val sparkRef    = db.collection("veilSparks").document(sparkId)
                val dissenterRef = sparkRef.collection("dissenters").document(uid)
                sparkRef.update("disagreeCount", FieldValue.increment(delta.toDouble())).await()
                if (wasDisagreed) dissenterRef.delete().await()
                else dissenterRef.set(mapOf("at" to System.currentTimeMillis())).await()
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        answerListeners.values.forEach { it.remove() }
    }
}
