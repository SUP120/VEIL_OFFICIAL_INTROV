package com.ns.findyourintroverfriend.report

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG                = "ReportVM"
private const val REPORT_THRESHOLD   = 3   // H1: 3 unique reporters → user gets flagged

// ─────────────────────────────────────────────────────────────────────────────
//  Report reasons — shown as chips in the UI
// ─────────────────────────────────────────────────────────────────────────────
enum class ReportReason(val label: String) {
    OFFENSIVE_LANGUAGE("Offensive language"),
    HARASSMENT("Harassment"),
    SPAM("Spam / flooding"),
    INAPPROPRIATE_CONTENT("Inappropriate content"),
    SEXUAL_CONTENT("Sexual content"),
    IMPERSONATION("Pretending to be someone else"),
    FAKE_GENDER("Report fake gender"),
    MINOR_SUSPICION("Suspected minor (under 18)"),
    OTHER("Other")
}

sealed class ReportUiState {
    object Idle      : ReportUiState()
    object Submitting: ReportUiState()
    object AlreadyReported : ReportUiState()   // This user already filed a report for this target
    data class Success(val targetAlias: String, val flagged: Boolean) : ReportUiState()
    data class Error(val msg: String) : ReportUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class ReportViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    /**
     * H1: File a report against [targetUid] from within [groupId].
     *
     * Logic:
     * 1. Check if the current user already reported this target in this group → block duplicate
     * 2. Write a report doc to `reports/{groupId}__{reporterUid}__{targetUid}`
     * 3. Increment the target's `reportCount` in their user doc (FieldValue.increment)
     * 4. If reportCount reaches REPORT_THRESHOLD (3) → set `isFlagged = true`
     *    and write `flaggedInGroup.{groupId} = timestamp` for context
     * 5. The flag is visible to the group in IslandHomeScreen (gray avatar tint + "Reported" badge)
     */
    fun submitReport(
        targetUid   : String,
        targetAlias : String,
        groupId     : String,
        reason      : ReportReason,
        extraNote   : String = ""
    ) {
        val reporterUid = auth.currentUser?.uid ?: run {
            _state.value = ReportUiState.Error("Not signed in.")
            return
        }
        if (reporterUid == targetUid) {
            _state.value = ReportUiState.Error("You can't report yourself.")
            return
        }

        _state.value = ReportUiState.Submitting

        viewModelScope.launch {
            try {
                val reportDocId = "${groupId}__${reporterUid}__${targetUid}"
                val reportRef   = db.collection("reports").document(reportDocId)

                // 1. Check for duplicate report
                val existing = reportRef.get().await()
                if (existing.exists()) {
                    Log.d(TAG, "H1: Duplicate report blocked for $targetUid")
                    _state.value = ReportUiState.AlreadyReported
                    return@launch
                }

                // 2. Write the report document
                reportRef.set(
                    mapOf(
                        "reporterUid"  to reporterUid,
                        "targetUid"    to targetUid,
                        "targetAlias"  to targetAlias,
                        "groupId"      to groupId,
                        "reason"       to reason.name,
                        "reasonLabel"  to reason.label,
                        "extraNote"    to extraNote.take(300),
                        "reportedAt"   to Timestamp.now()
                    )
                ).await()

                // 3. Increment reportCount on target's user doc
                db.collection("users").document(targetUid)
                    .update("reportCount", FieldValue.increment(1))
                    .await()

                // 4. Check if threshold reached → flag the user
                val targetDoc   = db.collection("users").document(targetUid).get().await()
                val reportCount = (targetDoc.getLong("reportCount") ?: 1L).toInt()
                val flagged     = reportCount >= REPORT_THRESHOLD

                if (flagged) {
                    Log.w(TAG, "H1: User $targetUid flagged — $reportCount reports")
                    db.collection("users").document(targetUid)
                        .update(
                            "isFlagged",                  true,
                            "flaggedInGroups.$groupId",   Timestamp.now()
                        ).await()
                }

                Log.d(TAG, "H1: Report submitted against $targetAlias — flagged=$flagged")
                _state.value = ReportUiState.Success(targetAlias, flagged)

            } catch (e: Exception) {
                Log.e(TAG, "H1: submitReport failed", e)
                _state.value = ReportUiState.Error(e.localizedMessage ?: "Report failed.")
            }
        }
    }

    fun reset() { _state.value = ReportUiState.Idle }
}
