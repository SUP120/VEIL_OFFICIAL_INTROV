package com.ns.findyourintroverfriend.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ns.findyourintroverfriend.data.GroupFields
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.ns.findyourintroverfriend.notifications.VeilNotifier
import java.util.Calendar
import java.util.TimeZone

private const val TAG = "IslandHome"

// ─────────────────────────────────────────────────────────────────────────────
//  Max days is now 3, not 7
// ─────────────────────────────────────────────────────────────────────────────

const val MAX_GROUP_DAYS      = 3
const val MAX_QUESTIONS_PER_DAY = 3          // NEW: 3 questions allowed per day

/** Activity status of a member — used for inactive-user handling */
enum class ActivityStatus { ACTIVE, WARNING, GHOSTED, LEFT }

data class IslandMember(
    val uid               : String,
    val alias             : String,
    val gender            : String,
    val emoji             : String,
    val photoUrl          : String         = "",
    val personalities     : List<String>   = emptyList(),
    val comfortPrefs      : List<String>   = emptyList(),
    val isYou             : Boolean,
    val day               : Int            = 1,
    val isBot             : Boolean        = false,
    val hasPostedQuestion : Boolean        = false,
    val questionsToday    : Int            = 0,
    val activityStatus    : ActivityStatus = ActivityStatus.ACTIVE,
    val lastSeenMs        : Long           = 0L   // C4: epoch ms of last app open
)

data class IslandHomeState(
    val members              : List<IslandMember> = emptyList(),
    val groupDay             : Int                = 1,
    val groupId              : String             = "",
    val isLoading            : Boolean            = true,
    val hasNewQuestion       : Boolean            = false,
    val error                : String?            = null,
    val showCircleReveal     : Boolean            = false,
    val day1PromptPending    : Boolean            = false,
    val myQuestionsToday     : Int                = 0,
    val hasAnsweredToday     : Boolean            = false,
    val ghostedMemberCount   : Int                = 0,
    val leftMemberCount      : Int                = 0,
    val leaveGroupRequested  : Boolean            = false,
    // Countdown
    val nextDayCountdownMs   : Long               = -1L,   // ms until next day; -1 = not applicable
    val dayDurationHours     : Int                = 8,     // 8 hours per day
    val ceremonyUnlocked     : Boolean            = false, // true when ceremony is open
    val dayStartedAtMs       : Long               = 0L,    // epoch ms when current day started
    // Dev-override: ceremony unlocks at this hour (24h, default 20 = 8PM)
    val ceremonyUnlockHour   : Int                = 20     // 8:00 PM (24h). Can be overridden via dev panel.
)

class IslandHomeViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(IslandHomeState())
    val state: StateFlow<IslandHomeState> = _state.asStateFlow()

    private val _photoUploadState = MutableStateFlow<String?>(null)
    val photoUploadState: StateFlow<String?> = _photoUploadState.asStateFlow()

    private var groupListener    : ListenerRegistration? = null
    private var lastSeenJob      : Job? = null   // C4: heartbeat for presence

    init {
        loadGroup()
        loadDevSettings()
        startCountdownTicker()
    }

    private fun loadGroup() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = _state.value.copy(error = "Not signed in.", isLoading = false)
            return
        }
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                val groupId = userDoc.getString("groupId") ?: run {
                    _state.value = _state.value.copy(error = "No group found.", isLoading = false)
                    return@launch
                }

                // Has this user seen the circle reveal before?
                val circleRevealSeen = userDoc.getBoolean("circleRevealSeen") ?: false

                startLastSeenHeartbeat(uid)   // C4: begin writing lastSeen every 2 minutes
                listenToGroup(uid, groupId, circleRevealSeen)
            } catch (e: Exception) {
                Log.e(TAG, "loadGroup failed", e)
                _state.value = _state.value.copy(error = e.localizedMessage, isLoading = false)
            }
        }
    }

    private fun listenToGroup(myUid: String, groupId: String, circleRevealSeen: Boolean) {
        groupListener = db.collection("groups").document(groupId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener

                @Suppress("UNCHECKED_CAST")
                val memberUids = snap.get(GroupFields.MEMBERS) as? List<String> ?: emptyList()
                val day        = (snap.getLong(GroupFields.DAY) ?: 1L).toInt().coerceIn(1, MAX_GROUP_DAYS)

                viewModelScope.launch {
                    try {
                        // ── Day progression: advance every 24 hours ────────────
                        // Uses hours-since-dayStartedAt so it works correctly in
                        // both production (real 24h) and dev (quick testing via devSetDay).
                        val dayStartedAt = snap.getTimestamp(GroupFields.DAY_STARTED_AT)?.toDate()
                        if (dayStartedAt != null && day < MAX_GROUP_DAYS) {
                            val hoursElapsed = (System.currentTimeMillis() - dayStartedAt.time) / (1000 * 60 * 60)
                            if (hoursElapsed >= 8) {
                                val advancedDay = (day + 1).coerceAtMost(MAX_GROUP_DAYS)
                                Log.d(TAG, "Day auto-advance: $day → $advancedDay (${hoursElapsed}h elapsed, 8h threshold)")
                                db.collection("groups").document(groupId)
                                    .update(
                                        GroupFields.DAY, advancedDay,
                                        GroupFields.DAY_STARTED_AT, com.google.firebase.Timestamp.now()
                                    ).await()
                                // H5: Notify all group members the day advanced
                                try {
                                    VeilNotifier.notifyDayAdvanced(
                                        db      = db,
                                        groupId = groupId,
                                        newDay  = advancedDay
                                    )
                                } catch (_: Exception) {}   // non-fatal
                                return@launch  // Listener fires again with new day
                            }
                        }

                        val todayKey = todayKey()

                        // ── H1: Ceremony unlock + notification logic ────────────
                        // With 8-hour days, ceremony unlocks at 8 hours into Day 3
                        // (same duration as Day 1 and 2 — fills the entire Day 3 window)
                        val ceremonyOpened  = snap.getBoolean("ceremonyOpened") ?: false
                        val day3StartedAt   = snap.getTimestamp(GroupFields.DAY_STARTED_AT)?.toDate()
                        val CEREMONY_UNLOCK_AFTER_MS = 8 * 3_600_000L  // 8 hours into Day 3
                        val CEREMONY_CLOSE_AFTER_MS  = 4 * 3_600_000L  // ceremony closes 4h after opening

                        if (day >= 3) {
                            val hoursIntoDay3 = if (day3StartedAt != null)
                                ((System.currentTimeMillis() - day3StartedAt.time) / 3_600_000L).toInt()
                            else 0
                            val msIntoDay3 = if (day3StartedAt != null)
                                System.currentTimeMillis() - day3StartedAt.time
                            else 0L

                            // Unlock the ceremony 4 hours into Day 3
                            if (msIntoDay3 >= CEREMONY_UNLOCK_AFTER_MS && !ceremonyOpened) {
                                try {
                                    db.collection("groups").document(groupId)
                                        .update("ceremonyOpened", true,
                                                "ceremonyOpenedAt", com.google.firebase.Timestamp.now()).await()
                                    Log.d(TAG, "H1: ceremonyOpened=true for $groupId (${hoursIntoDay3}h into Day 3)")
                                    VeilNotifier.notifyCeremonyUnlocked(db, groupId)
                                } catch (e: Exception) { Log.w(TAG, "H1 write failed: ${e.message}") }
                            }

                            // Ceremony reminder notifications:
                            // #1: 6h into Day 3 (2h before ceremony)
                            // #2: 7h into Day 3 (1h before ceremony)
                            // #3: ceremony opens (sent by notifyCeremonyUnlocked above)
                            // #4: 2h after opening
                            if (day3StartedAt != null && !ceremonyOpened) {
                                val msIntoDay3Notif = System.currentTimeMillis() - day3StartedAt.time
                                val preNotifsSent   = (snap.getLong("preNotifsSent") ?: 0L).toInt()
                                val expectedPre = when {
                                    msIntoDay3Notif >= 7 * 3_600_000L -> 2   // 7h+ : 1h-before notif
                                    msIntoDay3Notif >= 6 * 3_600_000L -> 1   // 6h+ : 2h-before notif
                                    else                              -> 0
                                }
                                if (expectedPre > preNotifsSent) {
                                    try {
                                        val msg = when (preNotifsSent + 1) {
                                            1 -> "🕯️ The ceremony opens in 2 hours. Get ready to lift the veil."
                                            else -> "⏳ 1 hour until the ceremony. Your circle is waiting."
                                        }
                                        VeilNotifier.notifyGroupRaw(db, groupId,
                                            "🎭 Ceremony soon", msg,
                                            mapOf("type" to "ceremony_pre_reminder"))
                                        db.collection("groups").document(groupId)
                                            .update("preNotifsSent",
                                                com.google.firebase.firestore.FieldValue.increment(1)).await()
                                    } catch (e: Exception) { Log.w(TAG, "pre-ceremony notif failed: ${e.message}") }
                                }
                            }
                            if (ceremonyOpened) {
                                val openedAt    = snap.getTimestamp("ceremonyOpenedAt")
                                val notifsSent  = (snap.getLong("ceremonyNotifsSent") ?: 0L).toInt()
                                if (openedAt != null) {
                                    val msOpen = System.currentTimeMillis() - openedAt.toDate().time
                                    val expectedNotifs = when {
                                        msOpen >= 3 * 3_600_000L -> 3   // 3h+ after open
                                        msOpen >= 2 * 3_600_000L -> 2   // 2h+ after open
                                        else                     -> 1   // just opened
                                    }
                                    if (expectedNotifs > notifsSent) {
                                        try {
                                            val msg = when (notifsSent + 1) {
                                                1 -> "✨ The veil lifts NOW. Enter the ceremony — meet your circle."
                                                2 -> "🌙 Still waiting for you. The ceremony closes in 3 hours."
                                                else -> "⚠️ Last call — enter before the ceremony closes."
                                            }
                                            VeilNotifier.notifyGroupRaw(db, groupId,
                                                "🎭 Ceremony is open", msg,
                                                mapOf("type" to "ceremony_reminder", "notif" to "${notifsSent + 1}"))
                                            db.collection("groups").document(groupId)
                                                .update("ceremonyNotifsSent",
                                                    com.google.firebase.firestore.FieldValue.increment(1)).await()
                                            Log.d(TAG, "ceremony notif #${notifsSent + 1} sent for $groupId")
                                        } catch (e: Exception) { Log.w(TAG, "ceremony notif failed: ${e.message}") }
                                    }

                                    // Auto-kick 4 hours after ceremony opened (end of Day 3)
                                    val msOpen2 = System.currentTimeMillis() - openedAt.toDate().time
                                    if (msOpen2 >= CEREMONY_CLOSE_AFTER_MS) {
                                        triggerAutoKick(groupId, snap)
                                    }
                                }
                            }
                            _state.value = _state.value.copy(ceremonyUnlocked = ceremonyOpened)
                        }

                        // All questions posted today in this group
                        val qSnap = db.collection("groups").document(groupId)
                            .collection("daily_questions")
                            .whereEqualTo("day", todayKey)
                            .get().await()

                        // Count per-uid how many questions they posted today
                        val questionsByUid = mutableMapOf<String, Int>()
                        for (doc in qSnap.documents) {
                            val qUid = doc.getString("uid") ?: continue
                            questionsByUid[qUid] = (questionsByUid[qUid] ?: 0) + 1
                        }

                        val myQuestionsToday = questionsByUid[myUid] ?: 0
                        val myPostedToday    = myQuestionsToday > 0

                        // Check if user has answered any incoming questions today
                        val hasAnsweredToday = checkHasAnsweredToday(groupId, myUid, todayKey)

                        // ── Inactive member detection ──────────────────────────
                        // WARNING  = no post in past 48h
                        // GHOSTED  = no post in past 72h (group still continues)
                        val nowMs = System.currentTimeMillis()
                        val createdAtMs = snap.getTimestamp(GroupFields.CREATED_AT)?.toDate()?.time ?: nowMs

                        // Read leftMembers map: { uid -> timestamp }
                        @Suppress("UNCHECKED_CAST")
                        val leftMembersMap = (snap.get("leftMembers") as? Map<String, Any>) ?: emptyMap()

                        val members = memberUids.map { memberId ->
                            val mDoc = db.collection("users").document(memberId).get().await()

                            // Find last activity: most recent question posted by this member
                            val lastQSnap = db.collection("groups").document(groupId)
                                .collection("daily_questions")
                                .whereEqualTo("uid", memberId)
                                .get().await()
                            val lastPostMs = lastQSnap.documents
                                .mapNotNull { it.getTimestamp("submittedAt")?.toDate()?.time }
                                .maxOrNull() ?: createdAtMs
                            val hoursInactive = (nowMs - lastPostMs) / (1000 * 60 * 60)

                            val activity = when {
                                hoursInactive >= 72 -> ActivityStatus.GHOSTED
                                hoursInactive >= 48 -> ActivityStatus.WARNING
                                else               -> ActivityStatus.ACTIVE
                            }

                            val personalities = (mDoc.get("personalities") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            val comfortPrefs  = (mDoc.get("comfortPrefs")  as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                            // C4: read lastSeen and derive activityStatus
                            val lastSeenMs = mDoc.getTimestamp("lastSeen")?.toDate()?.time ?: createdAtMs
                            val msSinceSeen = nowMs - lastSeenMs
                            val hoursAway   = msSinceSeen / (1000 * 60 * 60)

                            // Check if member voluntarily left
                            val hasLeft = leftMembersMap.containsKey(memberId)

                            val activityFromPresence = when {
                                hasLeft        -> ActivityStatus.LEFT
                                hoursAway >= 6 -> ActivityStatus.GHOSTED
                                hoursAway >= 3 -> ActivityStatus.WARNING
                                else           -> ActivityStatus.ACTIVE
                            }

                            IslandMember(
                                uid               = memberId,
                                alias             = mDoc.getString("alias") ?: "…",
                                gender            = mDoc.getString("gender") ?: "?",
                                emoji             = mDoc.getString("emoji") ?: pickEmoji(mDoc.getString("gender")),
                                photoUrl          = mDoc.getString("photoUrl") ?: "",
                                personalities     = personalities,
                                comfortPrefs      = comfortPrefs,
                                isYou             = memberId == myUid,
                                day               = day,
                                isBot             = mDoc.getBoolean("isBot") ?: false,
                                hasPostedQuestion = (questionsByUid[memberId] ?: 0) > 0,
                                questionsToday    = questionsByUid[memberId] ?: 0,
                                activityStatus    = if (memberId == myUid) ActivityStatus.ACTIVE else activityFromPresence,
                                lastSeenMs        = lastSeenMs
                            )
                        }

                        val ghostCount = members.count { it.activityStatus == ActivityStatus.GHOSTED && !it.isYou }
                        val leftCount  = members.count { it.activityStatus == ActivityStatus.LEFT && !it.isYou }

                        val dayStartedMs = snap.getTimestamp(GroupFields.DAY_STARTED_AT)
                            ?.toDate()?.time ?: 0L

                        _state.value = _state.value.copy(
                            members              = members,
                            groupDay             = day,
                            groupId              = groupId,
                            isLoading            = false,
                            showCircleReveal     = !circleRevealSeen,
                            day1PromptPending    = day == 1 && !myPostedToday,
                            myQuestionsToday     = myQuestionsToday,
                            hasAnsweredToday     = hasAnsweredToday,
                            ghostedMemberCount   = ghostCount,
                            leftMemberCount      = leftCount,
                            dayStartedAtMs       = dayStartedMs,
                        )
                        Log.d(TAG, "Group loaded: ${members.size} members day=$day myQ=$myQuestionsToday ghosted=$ghostCount")
                    } catch (e: Exception) {
                        Log.e(TAG, "Member fetch failed", e)
                        _state.value = _state.value.copy(error = e.localizedMessage, isLoading = false)
                    }
                }
            }
    }

    private suspend fun checkHasAnsweredToday(groupId: String, myUid: String, todayKey: String): Boolean {
        return try {
            val qSnap = db.collection("groups").document(groupId)
                .collection("daily_questions")
                .whereEqualTo("day", todayKey)
                .get().await()
            for (qDoc in qSnap.documents) {
                val qUid = qDoc.getString("uid") ?: continue
                if (qUid == myUid) continue   // skip my own questions
                val ansDoc = qDoc.reference.collection("answers").document(myUid).get().await()
                if (ansDoc.exists()) return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    // ── Called once user has seen the circle reveal ────────────────────────────
    fun markCircleRevealSeen() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .update("circleRevealSeen", true)
                    .await()
                _state.value = _state.value.copy(showCircleReveal = false)
                Log.d(TAG, "Circle reveal marked seen for $uid")
            } catch (e: Exception) {
                Log.e(TAG, "markCircleRevealSeen failed", e)
                _state.value = _state.value.copy(showCircleReveal = false)
            }
        }
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}_${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    private fun pickEmoji(gender: String?): String = when (gender) {
        "Male"   -> listOf("🌊", "⭐", "🌙", "🌲").random()
        "Female" -> listOf("✨", "🌸", "🌙", "🌿").random()
        else     -> "🌿"
    }

    // ── Dev helpers ────────────────────────────────────────────────────────────
    /** Set group day directly — for dev panel (Day 1 / 2 / 3 buttons). */
    fun devSetDay(day: Int) {
        val gId = _state.value.groupId
        if (gId.isBlank()) return
        val targetDay = day.coerceIn(1, MAX_GROUP_DAYS)
        viewModelScope.launch {
            try {
                db.collection("groups").document(gId)
                    .update("day", targetDay, "dayStartedAt", com.google.firebase.Timestamp.now())
                    .await()
                Log.d(TAG, "dev: day set to $targetDay for group $gId")
            } catch (e: Exception) {
                Log.e(TAG, "devSetDay failed", e)
            }
        }
    }

    /**
     * Dev: instantly unlock the Day 3 ceremony (sets day=3 + ceremonyOpened=true).
     * This lets developers test the full ceremony → Party Island flow
     * without waiting for 8 PM.
     */
    fun devUnlockCeremony() {
        val gId = _state.value.groupId
        if (gId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("groups").document(gId)
                    .update(
                        "day",              MAX_GROUP_DAYS,
                        "dayStartedAt",     com.google.firebase.Timestamp.now(),
                        "ceremonyOpened",   true,
                        "ceremonyOpenedAt", com.google.firebase.Timestamp.now()
                    ).await()
                Log.d(TAG, "dev: ceremony force-unlocked for group $gId")
            } catch (e: Exception) {
                Log.e(TAG, "devUnlockCeremony failed", e)
            }
        }
    }

    /**
     * Dev: override day duration in hours for ALL groups.
     * Writes to Firestore config/devSettings — only developer reads this.
     * E.g. devSetDayDuration(1) = all days are 1 hour each (for testing).
     */
    fun devSetDayDuration(hours: Int) {
        viewModelScope.launch {
            try {
                db.collection("config").document("devSettings")
                    .set(mapOf("dayDurationHours" to hours), com.google.firebase.firestore.SetOptions.merge())
                    .await()
                _state.value = _state.value.copy(dayDurationHours = hours)
                Log.d(TAG, "devSetDayDuration: $hours hours")
            } catch (e: Exception) { Log.e(TAG, "devSetDayDuration failed", e) }
        }
    }

    /**
     * Dev: override the ceremony unlock hour (0–23, default 20 = 8 PM).
     * Persists to Firestore config/devSettings and updates local state instantly.
     * All dev users sharing the same group will see the ceremony unlock at the new hour.
     *
     * Examples:
     *   devSetCeremonyHour(14)  → ceremony unlocks at 2:00 PM
     *   devSetCeremonyHour(20)  → back to default 8:00 PM
     *   devSetCeremonyHour(0)   → unlocks at midnight (immediate if it's already past 0h)
     */
    fun devSetCeremonyHour(hour: Int) {
        val h = hour.coerceIn(0, 23)
        viewModelScope.launch {
            try {
                db.collection("config").document("devSettings")
                    .set(mapOf("ceremonyUnlockHour" to h), com.google.firebase.firestore.SetOptions.merge())
                    .await()
                _state.value = _state.value.copy(ceremonyUnlockHour = h)
                Log.d(TAG, "devSetCeremonyHour: $h (${formatHour(h)})")
            } catch (e: Exception) { Log.e(TAG, "devSetCeremonyHour failed", e) }
        }
    }

    /** Format 24h hour to readable string, e.g. 20 → "8:00 PM", 9 → "9:00 AM". */
    fun formatHour(h: Int): String {
        val ampm  = if (h < 12) "AM" else "PM"
        val hour12 = when {
            h == 0   -> 12
            h <= 12  -> h
            else     -> h - 12
        }
        return "$hour12:00 $ampm"
    }

    /** Load day-duration override from Firestore (dev-only setting). */
    private fun loadDevSettings() {
        val email  = auth.currentUser?.email
        if (!com.ns.findyourintroverfriend.dev.DevConfig.isDev(email)) return
        viewModelScope.launch {
            try {
                val doc   = db.collection("config").document("devSettings").get().await()
                val hours = (doc.getLong("dayDurationHours")   ?: 8L).toInt()
                val cHour = (doc.getLong("ceremonyUnlockHour") ?: 20L).toInt()
                _state.value = _state.value.copy(
                    dayDurationHours   = hours,
                    ceremonyUnlockHour = cHour
                )
                Log.d(TAG, "devSettings loaded: dayDurationHours=$hours  ceremonyUnlockHour=$cHour")
            } catch (_: Exception) {}
        }
    }

    /** Tick every second to update the countdown chip live. */
    private fun startCountdownTicker() {
        viewModelScope.launch {
            while (true) {
                updateCountdown()
                delay(1_000L)  // update every second — live countdown
            }
        }
    }

    private fun updateCountdown() {
        val s = _state.value
        if (s.groupDay >= MAX_GROUP_DAYS || s.dayStartedAtMs == 0L) {
            _state.value = s.copy(nextDayCountdownMs = -1L)
            return
        }
        val durationMs  = s.dayDurationHours * 3_600_000L
        val elapsedMs   = System.currentTimeMillis() - s.dayStartedAtMs
        val remaining   = (durationMs - elapsedMs).coerceAtLeast(0L)
        _state.value    = s.copy(nextDayCountdownMs = remaining)
    }

    /** Auto-kick members who haven't entered ceremony within 18 hours of opening. */
    private fun triggerAutoKick(groupId: String, snap: com.google.firebase.firestore.DocumentSnapshot) {
        val alreadyKicked = snap.getBoolean("autoKickDone") ?: false
        if (alreadyKicked) return
        viewModelScope.launch {
            try {
                // Mark so we don't re-kick repeatedly
                db.collection("groups").document(groupId)
                    .update("autoKickDone", true, "closedAt", com.google.firebase.Timestamp.now())
                    .await()
                // Kick each non-entered member: clear their groupId
                @Suppress("UNCHECKED_CAST")
                val members = snap.get("members") as? List<String> ?: return@launch
                @Suppress("UNCHECKED_CAST")
                val enteredMembers = snap.get("partyMembers") as? List<String> ?: emptyList()
                val toKick = members.filter { it !in enteredMembers }
                for (uid in toKick) {
                    try {
                        db.collection("users").document(uid)
                            .update("groupId", com.google.firebase.firestore.FieldValue.delete()).await()
                        Log.d(TAG, "Auto-kicked $uid from $groupId (18h elapsed)")
                    } catch (e: Exception) { Log.w(TAG, "kick failed for $uid: ${e.message}") }
                }
                Log.d(TAG, "Auto-kick complete for $groupId: kicked ${toKick.size} members")
            } catch (e: Exception) { Log.e(TAG, "triggerAutoKick failed: ${e.message}") }
        }
    }

    // C4: lastSeen heartbeat — writes every 2 minutes while Island is in foreground
    private fun startLastSeenHeartbeat(uid: String) {
        lastSeenJob?.cancel()
        lastSeenJob = viewModelScope.launch {
            // Write immediately on open
            try { db.collection("users").document(uid).update("lastSeen", Timestamp.now()).await() } catch (_: Exception) {}
            while (true) {
                delay(120_000L)   // every 2 minutes
                try {
                    db.collection("users").document(uid).update("lastSeen", Timestamp.now()).await()
                    Log.d(TAG, "C4: lastSeen updated for $uid")
                } catch (_: Exception) {}
            }
        }
    }

    // C3: Voluntary leave — clears groupId so user can re-match
    fun leaveGroup() {
        val uid   = auth.currentUser?.uid ?: return
        val gId   = _state.value.groupId.takeIf { it.isNotBlank() } ?: return
        val myAlias = _state.value.members.find { it.isYou }?.alias ?: "Someone"
        viewModelScope.launch {
            try {
                // Mark this member as having left in the group doc
                db.collection("groups").document(gId)
                    .update("leftMembers.${uid}", Timestamp.now())
                    .await()
                // Remove groupId from user doc so they can rejoin queue
                db.collection("users").document(uid)
                    .update(
                        "groupId",  com.google.firebase.firestore.FieldValue.delete(),
                        "leftRoom", true       // ← tells cold-start to land on VeilHomeScreen
                    ).await()
                Log.d(TAG, "C3: $uid left group $gId")
                // H5: Notify remaining members
                try {
                    VeilNotifier.notifyMemberLeft(
                        db          = db,
                        groupId     = gId,
                        leaverAlias = myAlias,
                        senderUid   = uid
                    )
                } catch (_: Exception) {}   // non-fatal
                _state.value = _state.value.copy(leaveGroupRequested = true)
            } catch (e: Exception) {
                Log.e(TAG, "leaveGroup failed", e)
            }
        }
    }

    /**
     * B5 fix: Called when the NoGroupCard error state sends user back to WaitingRoom.
     * Clears the stale groupId from Firestore so cold-start routing doesn't loop back
     * to a broken Island Home.
     */
    fun clearStaleGroupId() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .update(
                        "groupId",  FieldValue.delete(),
                        "leftRoom", false   // returning to waiting, not home
                    ).await()
                Log.d(TAG, "B5: stale groupId cleared for $uid")
            } catch (e: Exception) {
                Log.w(TAG, "B5: clearStaleGroupId failed: ${e.message}")
            }
        }
    }

    /** Legacy +1 advance — kept for any remaining references. */
    fun devAdvanceDay() {
        val next = (_state.value.groupDay + 1).coerceAtMost(MAX_GROUP_DAYS)
        devSetDay(next)
    }

    /** Upload a new profile photo — Cloudinary CDN (free, full quality). */
    fun uploadMyPhoto(uri: android.net.Uri) {
        val uid = auth.currentUser?.uid ?: run {
            _photoUploadState.value = "error: Not signed in"
            return
        }
        _photoUploadState.value = "uploading"
        viewModelScope.launch {
            try {
                val ctx          = com.google.firebase.FirebaseApp.getInstance().applicationContext
                val cloudName    = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_CLOUD_NAME
                val uploadPreset = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_UPLOAD_PRESET

                val photoUrl: String = if (cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
                    // ── Full-quality Cloudinary upload ────────────────────────
                    com.ns.findyourintroverfriend.util.CloudinaryUploader.upload(
                        context      = ctx,
                        uri          = uri,
                        cloudName    = cloudName,
                        uploadPreset = uploadPreset
                    )
                } else {
                    // ── Fallback: Base64 in Firestore (until Cloudinary is set up) ──
                    Log.w(TAG, "Cloudinary not configured — using Base64 fallback")
                    val stream   = ctx.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open image")
                    val original = android.graphics.BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (original == null) throw Exception("Could not decode selected image")
                    val scaled   = android.graphics.Bitmap.createScaledBitmap(original, 300, 300, true)
                    original.recycle()
                    val baos = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos)
                    scaled.recycle()
                    val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                    "data:image/jpeg;base64,$b64"
                }

                db.collection("users").document(uid)
                    .update("photoUrl", photoUrl).await()

                Log.d(TAG, "Photo saved: ${photoUrl.take(60)}…")
                _photoUploadState.value = "done"
                kotlinx.coroutines.delay(3000)
                _photoUploadState.value = null

            } catch (e: Exception) {
                Log.e(TAG, "Photo save failed", e)
                _photoUploadState.value = "error: ${e.localizedMessage ?: e.javaClass.simpleName}"
                kotlinx.coroutines.delay(6000)
                _photoUploadState.value = null
            }
        }
    }



    override fun onCleared() {
        super.onCleared()
        groupListener?.remove()
        lastSeenJob?.cancel()   // C4: stop heartbeat when screen leaves composition
    }
}
