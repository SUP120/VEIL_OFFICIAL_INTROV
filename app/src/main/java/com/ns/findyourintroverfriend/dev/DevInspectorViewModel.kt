package com.ns.findyourintroverfriend.dev

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.data.GroupFields
import com.ns.findyourintroverfriend.data.UserFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "DevInspector"

data class QueueEntry(
    val uid     : String,
    val gender  : String,
    val status  : String,
    val joinedAt: String,
    val hasToken: Boolean,
    val isBot   : Boolean = false
)

data class GroupEntry(
    val groupId    : String,
    val memberUids : List<String>,
    val day        : Int,
    val createdAt  : String
)

data class UserSummary(
    val uid             : String,
    val alias           : String,
    val gender          : String,
    val groupId         : String,
    val profileComplete : Boolean,
    val hasToken        : Boolean
)

data class DevInspectorState(
    val isLoading    : Boolean        = true,
    val myUid        : String         = "",
    val queue        : List<QueueEntry>  = emptyList(),
    val groups       : List<GroupEntry>  = emptyList(),
    val users        : List<UserSummary> = emptyList(),
    val lastRefresh  : String         = "",
    val error        : String?        = null,
    val forceDayMsg  : String?        = null   // transient toast-style feedback
)

class DevInspectorViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(DevInspectorState())
    val state: StateFlow<DevInspectorState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val myUid = auth.currentUser?.uid ?: ""

                // ── Queue ──────────────────────────────────────────────────
                val qSnap = db.collection("matchmaking_queue").get().await()
                val queue = qSnap.documents.map { doc ->
                    QueueEntry(
                        uid      = doc.id,
                        gender   = doc.getString(UserFields.GENDER) ?: "?",
                        status   = doc.getString("status") ?: "?",
                        joinedAt = doc.getTimestamp("joinedAt")?.toDate()?.let {
                            val cal = java.util.Calendar.getInstance()
                            cal.time = it
                            "%02d:%02d:%02d".format(
                                cal.get(java.util.Calendar.HOUR_OF_DAY),
                                cal.get(java.util.Calendar.MINUTE),
                                cal.get(java.util.Calendar.SECOND)
                            )
                        } ?: "?",
                        hasToken = !doc.getString(UserFields.FCM_TOKEN).isNullOrBlank(),
                        isBot    = doc.getBoolean("isBot") ?: false
                    )
                }

                // ── Groups ─────────────────────────────────────────────────
                val gSnap = db.collection("groups").get().await()
                val groups = gSnap.documents.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val members = (doc.get(GroupFields.MEMBERS) as? List<String>) ?: emptyList()
                    GroupEntry(
                        groupId    = doc.id,
                        memberUids = members,
                        day        = (doc.getLong(GroupFields.DAY) ?: 1L).toInt(),
                        createdAt  = doc.getTimestamp(GroupFields.CREATED_AT)?.toDate()?.let {
                            val cal = java.util.Calendar.getInstance()
                            cal.time = it
                            "%02d:%02d".format(
                                cal.get(java.util.Calendar.HOUR_OF_DAY),
                                cal.get(java.util.Calendar.MINUTE)
                            )
                        } ?: "?"
                    )
                }

                // ── All signed-up users (first 20) ─────────────────────────
                val uSnap = db.collection("users").limit(20).get().await()
                val users = uSnap.documents.map { doc ->
                    UserSummary(
                        uid             = doc.id.take(8) + "…",
                        alias           = doc.getString(UserFields.ALIAS) ?: "?",
                        gender          = doc.getString(UserFields.GENDER) ?: "?",
                        groupId         = doc.getString(UserFields.GROUP_ID)?.take(8)?.let { "$it…" } ?: "none",
                        profileComplete = doc.getBoolean(UserFields.PROFILE_COMPLETE) ?: false,
                        hasToken        = !doc.getString(UserFields.FCM_TOKEN).isNullOrBlank()
                    )
                }

                val now = java.util.Calendar.getInstance()
                val timeStr = "%02d:%02d:%02d".format(
                    now.get(java.util.Calendar.HOUR_OF_DAY),
                    now.get(java.util.Calendar.MINUTE),
                    now.get(java.util.Calendar.SECOND)
                )

                _state.value = DevInspectorState(
                    isLoading   = false,
                    myUid       = myUid.take(8) + "…",
                    queue       = queue,
                    groups      = groups,
                    users       = users,
                    lastRefresh = timeStr
                )

                Log.d(TAG, "Refreshed — queue:${queue.size} groups:${groups.size} users:${users.size}")

            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
                _state.value = _state.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    /** Delete all matchmaking_queue entries — useful to reset a stuck queue */
    fun clearQueue() {
        viewModelScope.launch {
            try {
                val snap = db.collection("matchmaking_queue").get().await()
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
                Log.d(TAG, "Queue cleared")
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "clearQueue failed", e)
                _state.value = _state.value.copy(error = "Clear failed: ${e.message}")
            }
        }
    }

    /**
     * Force a group's [day] field to the given [targetDay] (1, 2, or 3).
     * Also resets ceremonyDone=false so Day-3 ceremony can be re-triggered.
     * Used by dev/test accounts to fast-forward a circle for testing.
     */
    fun forceDay(groupId: String, targetDay: Int) {
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>(
                    GroupFields.DAY to targetDay.toLong()
                )
                // If advancing to Day 3, make sure ceremony isn't already flagged done
                if (targetDay == 3) {
                    updates[GroupFields.CEREMONY_DONE] = false
                }
                db.collection("groups").document(groupId)
                    .update(updates)
                    .await()
                Log.d(TAG, "Forced groupId=$groupId to Day $targetDay")
                _state.value = _state.value.copy(forceDayMsg = "✅ Group forced to Day $targetDay")
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "forceDay failed", e)
                _state.value = _state.value.copy(forceDayMsg = "❌ Failed: ${e.message}")
            }
        }
    }

    fun clearForceDayMsg() {
        _state.value = _state.value.copy(forceDayMsg = null)
    }
}
