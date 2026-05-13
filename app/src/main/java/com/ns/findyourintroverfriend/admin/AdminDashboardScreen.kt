package com.ns.findyourintroverfriend.admin

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.ns.findyourintroverfriend.notifications.VeilNotifier
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
//  Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG          = "AdminDashboard"
private const val ADMIN_EMAIL  = "supriyamkumar098@gmail.com"

// ─────────────────────────────────────────────────────────────────────────────
//  Data Models
// ─────────────────────────────────────────────────────────────────────────────

data class ReportItem(
    val docId: String = "",
    val reporterUid: String = "",
    val targetUid: String = "",
    val targetAlias: String = "",
    val groupId: String = "",
    val reason: String = "",
    val reasonLabel: String = "",
    val extraNote: String = "",
    val reportedAt: Timestamp? = null
)

data class ReportedUserSummary(
    val uid: String,
    val alias: String = "Unknown",
    val emoji: String = "🌙",
    val email: String = "",
    val gender: String = "",
    val reportCount: Int = 0,
    val isFlagged: Boolean = false,
    val isBanned: Boolean = false,
    val banType: String? = null,
    val strikes: Int = 0,
    val reports: List<ReportItem> = emptyList()
)

data class AdminUserRow(
    val uid: String,
    val alias: String = "Unknown",
    val emoji: String = "🌙",
    val email: String = "",
    val gender: String = "",
    val groupId: String = "",
    val isBanned: Boolean = false,
    val strikes: Int = 0,
    val reportCount: Int = 0,
    val createdAt: Timestamp? = null
)

data class AdminGroupRow(
    val id: String,
    val day: Int = 1,
    val memberCount: Int = 0,
    val memberAliases: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val ceremonyDone: Boolean = false
)

data class PhotoReportItem(
    val docId       : String = "",
    val postId      : String = "",
    val uploaderUid : String = "",
    val reporterUid : String = "",
    val groupId     : String = "",
    val reason      : String = "",
    val reportedAt  : Timestamp? = null
)

data class AdminStats(
    val totalUsers   : Int = 0,
    val activeGroups : Int = 0,
    val totalReports : Int = 0,
    val bannedUsers  : Int = 0,
    val flaggedUsers : Int = 0,
    val totalStrikes : Int = 0,
    val photoReports : Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────

sealed class AdminUiState {
    object Loading : AdminUiState()
    object Unauthorized : AdminUiState()
    data class Ready(
        val reports       : List<ReportedUserSummary> = emptyList(),
        val users         : List<AdminUserRow>         = emptyList(),
        val groups        : List<AdminGroupRow>        = emptyList(),
        val photoReports  : List<PhotoReportItem>      = emptyList(),
        val stats         : AdminStats                 = AdminStats(),
        val actionInProgress: String? = null,
        val snackbar      : String? = null
    ) : AdminUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class AdminViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<AdminUiState>(AdminUiState.Loading)
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init { checkAccessAndLoad() }

    private fun checkAccessAndLoad() {
        val email = auth.currentUser?.email?.lowercase()
        if (email != ADMIN_EMAIL) {
            Log.w(TAG, "Unauthorized access attempt: $email")
            _state.value = AdminUiState.Unauthorized
            return
        }
        viewModelScope.launch { loadAll() }
    }

    fun refresh() { viewModelScope.launch { loadAll() } }

    private suspend fun loadAll() {
        _state.value = AdminUiState.Loading
        try {
            // ── 1. Reports ────────────────────────────────────────────────────
            val reportDocs = db.collection("reports")
                .orderBy("reportedAt", Query.Direction.DESCENDING)
                .limit(300)
                .get().await()

            val allReports = reportDocs.documents.mapNotNull { doc ->
                try {
                    ReportItem(
                        docId       = doc.id,
                        reporterUid = doc.getString("reporterUid") ?: "",
                        targetUid   = doc.getString("targetUid")   ?: "",
                        targetAlias = doc.getString("targetAlias") ?: "Unknown",
                        groupId     = doc.getString("groupId")     ?: "",
                        reason      = doc.getString("reason")      ?: "",
                        reasonLabel = doc.getString("reasonLabel") ?: "",
                        extraNote   = doc.getString("extraNote")   ?: "",
                        reportedAt  = doc.getTimestamp("reportedAt")
                    )
                } catch (_: Exception) { null }
            }

            val grouped = allReports.groupBy { it.targetUid }
            val reportSummaries = grouped.map { (uid, reports) ->
                val userDoc = try { db.collection("users").document(uid).get().await() } catch (_: Exception) { null }
                ReportedUserSummary(
                    uid         = uid,
                    alias       = userDoc?.getString("alias")  ?: reports.firstOrNull()?.targetAlias ?: "Unknown",
                    emoji       = userDoc?.getString("emoji")  ?: "🌙",
                    email       = userDoc?.getString("email")  ?: "",
                    gender      = userDoc?.getString("gender") ?: "",
                    reportCount = userDoc?.getLong("reportCount")?.toInt() ?: reports.size,
                    isFlagged   = userDoc?.getBoolean("isFlagged") ?: false,
                    isBanned    = userDoc?.getBoolean("isBanned")  ?: false,
                    banType     = userDoc?.getString("banType"),
                    strikes     = userDoc?.getLong("strikes")?.toInt() ?: 0,
                    reports     = reports
                )
            }.sortedByDescending { it.reportCount }

            // ── 2. All Users ──────────────────────────────────────────────────
            val userDocs = db.collection("users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200)
                .get().await()

            val allUsers = userDocs.documents.mapNotNull { doc ->
                try {
                    AdminUserRow(
                        uid         = doc.id,
                        alias       = doc.getString("alias")    ?: "Unknown",
                        emoji       = doc.getString("emoji")    ?: "🌙",
                        email       = doc.getString("email")    ?: "",
                        gender      = doc.getString("gender")   ?: "",
                        groupId     = doc.getString("groupId")  ?: "",
                        isBanned    = doc.getBoolean("isBanned")?: false,
                        strikes     = doc.getLong("strikes")?.toInt() ?: 0,
                        reportCount = doc.getLong("reportCount")?.toInt() ?: 0,
                        createdAt   = doc.getTimestamp("createdAt")
                    )
                } catch (_: Exception) { null }
            }

            // ── 3. All Groups ────────────────────────────────────────────────
            val groupDocs = db.collection("groups")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get().await()

            val allGroups = groupDocs.documents.mapNotNull { doc ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val members = doc.get("members") as? List<String> ?: emptyList()
                    // Fetch member aliases
                    val aliases = members.mapNotNull { uid ->
                        try { db.collection("users").document(uid).get().await().getString("alias") }
                        catch (_: Exception) { null }
                    }
                    AdminGroupRow(
                        id            = doc.id,
                        day           = doc.getLong("day")?.toInt() ?: 1,
                        memberCount   = members.size,
                        memberAliases = aliases,
                        createdAt     = doc.getTimestamp("createdAt"),
                        ceremonyDone  = doc.getBoolean("ceremonyDone") ?: false
                    )
                } catch (_: Exception) { null }
            }

            // ── 4. Photo Reports (post_reports collection) ───────────────────
            val photoReportDocs = db.collection("post_reports")
                .orderBy("reportedAt", Query.Direction.DESCENDING)
                .limit(200)
                .get().await()

            val photoReports = photoReportDocs.documents.mapNotNull { doc ->
                try {
                    PhotoReportItem(
                        docId       = doc.id,
                        postId      = doc.getString("postId")      ?: "",
                        uploaderUid = doc.getString("uploaderUid") ?: "",
                        reporterUid = doc.getString("reporterUid") ?: "",
                        groupId     = doc.getString("groupId")     ?: "",
                        reason      = doc.getString("reason")      ?: "",
                        reportedAt  = doc.getTimestamp("reportedAt")
                    )
                } catch (_: Exception) { null }
            }

            // ── 5. Stats ─────────────────────────────────────────────────────
            val stats = AdminStats(
                totalUsers   = allUsers.size,
                activeGroups = allGroups.count { !it.ceremonyDone },
                totalReports = allReports.size,
                bannedUsers  = allUsers.count { it.isBanned },
                flaggedUsers = reportSummaries.count { it.isFlagged },
                totalStrikes = allUsers.sumOf { it.strikes },
                photoReports = photoReports.size
            )

            _state.value = AdminUiState.Ready(
                reports      = reportSummaries,
                users        = allUsers,
                groups       = allGroups,
                photoReports = photoReports,
                stats        = stats
            )
            Log.d(TAG, "Admin data loaded: ${allUsers.size} users, ${reportSummaries.size} reported")

        } catch (e: Exception) {
            Log.e(TAG, "loadAll failed", e)
            _state.value = AdminUiState.Ready(snackbar = "Load failed: ${e.localizedMessage}")
        }
    }

    /** Admin takes an action on a reported user */
    fun takeAction(targetUid: String, action: String) {
        val current = _state.value as? AdminUiState.Ready ?: return
        viewModelScope.launch {
            _state.value = current.copy(actionInProgress = targetUid, snackbar = null)
            try {
                val userRef = db.collection("users").document(targetUid)
                val logData = mutableMapOf<String, Any>(
                    "adminEmail" to ADMIN_EMAIL,
                    "targetUid"  to targetUid,
                    "action"     to action,
                    "actionAt"   to Timestamp.now()
                )
                when (action) {
                    "warning"  -> {
                        userRef.update(mapOf(
                            "strikes"       to com.google.firebase.firestore.FieldValue.increment(1),
                            "adminWarning"  to "⚠️ You have received an official warning from the moderation team.",
                            "lastWarningAt" to Timestamp.now()
                        )).await()
                    }
                    "temp_ban" -> {
                        val until = Timestamp(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
                        userRef.update(mapOf(
                            "isBanned"    to true,
                            "banType"     to "temporary",
                            "bannedUntil" to until,
                            "bannedAt"    to Timestamp.now(),
                            "strikes"     to com.google.firebase.firestore.FieldValue.increment(1)
                        )).await()
                    }
                    "perm_ban" -> {
                        userRef.update(mapOf(
                            "isBanned"  to true,
                            "banType"   to "permanent",
                            "bannedAt"  to Timestamp.now()
                        )).await()
                    }
                    "unban" -> {
                        userRef.update(mapOf(
                            "isBanned"    to false,
                            "banType"     to null,
                            "bannedUntil" to null
                        )).await()
                    }
                    "dismiss" -> {
                        val repDocs = db.collection("reports").whereEqualTo("targetUid", targetUid).get().await()
                        for (doc in repDocs) doc.reference.delete().await()
                        userRef.update(mapOf("reportCount" to 0, "isFlagged" to false)).await()
                    }
                    "reset_strikes" -> {
                        userRef.update("strikes", 0).await()
                    }
                }
                db.collection("admin_logs").add(logData).await()
                Log.d(TAG, "Action '$action' on $targetUid")
                loadAll()   // full refresh
            } catch (e: Exception) {
                Log.e(TAG, "takeAction failed", e)
                _state.value = current.copy(actionInProgress = null, snackbar = "Failed: ${e.localizedMessage}")
            }
        }
    }

    fun dismissPhotoReport(docId: String) {
        val cur = _state.value as? AdminUiState.Ready ?: return
        viewModelScope.launch {
            try {
                db.collection("post_reports").document(docId).delete().await()
                _state.value = cur.copy(
                    photoReports = cur.photoReports.filter { it.docId != docId },
                    snackbar     = "Photo report dismissed."
                )
            } catch (e: Exception) {
                _state.value = cur.copy(snackbar = "Failed: ${e.localizedMessage}")
            }
        }
    }

    fun dismissSnackbar() {
        val cur = _state.value as? AdminUiState.Ready ?: return
        _state.value = cur.copy(snackbar = null)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    vm: AdminViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val bg      = Brush.verticalGradient(listOf(Color(0xFF080810), Color(0xFF10101E), Color(0xFF0A0A16)))
    val accent  = Color(0xFFEF4444)
    val gold    = Color(0xFFFFB020)
    val green   = Color(0xFF22C55E)
    val blue    = Color(0xFF3B82F6)
    val txtP    = Color(0xFFF0F4FA)
    val txtS    = Color(0xFF94A3B8)

    // Tabs: Stats | Reports | Photos | Users | Groups | Notify
    val tabs = listOf("📊 Stats", "🚨 Reports", "📸 Photos", "👤 Users", "🏝 Groups", "🔔 Notify")
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(bg)) {

        when (state) {
            is AdminUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Text("Loading admin data…", style = MaterialTheme.typography.bodySmall.copy(color = txtS))
                    }
                }
            }

            is AdminUiState.Unauthorized -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Rounded.Lock, null, tint = accent, modifier = Modifier.size(56.dp))
                        Text("Access Denied", style = MaterialTheme.typography.headlineSmall.copy(color = accent, fontWeight = FontWeight.Bold))
                        Text("This panel is for authorized administrators only.", style = MaterialTheme.typography.bodyMedium.copy(color = txtS, textAlign = TextAlign.Center), modifier = Modifier.padding(horizontal = 40.dp))
                    }
                }
            }

            is AdminUiState.Ready -> {
                val s = state as AdminUiState.Ready

                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Status bar spacer + Top bar ───────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Admin badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626))))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("🛡 DEV CONSOLE", style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.2.sp
                            ))
                        }

                        Spacer(Modifier.weight(1f))

                        // Refresh
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.07f))
                                .border(0.7.dp, blue.copy(0.30f), CircleShape)
                                .clickable { vm.refresh() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Refresh, "Refresh", tint = blue, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Gradient divider
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(0.5f), Color.Transparent))))

                    // ── Tab row ───────────────────────────────────────────────
                    ScrollableTabRow(
                        selectedTabIndex  = pagerState.currentPage,
                        containerColor    = Color.Transparent,
                        contentColor      = accent,
                        edgePadding       = 16.dp,
                        indicator         = { tabPositions ->
                            if (pagerState.currentPage < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    color    = accent
                                )
                            }
                        },
                        divider = {}
                    ) {
                        tabs.forEachIndexed { i, label ->
                            Tab(
                                selected  = pagerState.currentPage == i,
                                onClick   = { scope.launch { pagerState.animateScrollToPage(i) } },
                                text      = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Normal,
                                            color      = if (pagerState.currentPage == i) accent else txtS
                                        )
                                    )
                                }
                            )
                        }
                    }

                    // ── Pager ─────────────────────────────────────────────────
                    HorizontalPager(
                        state    = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> StatsTab(s.stats, accent, gold, green, blue, txtP, txtS)
                            1 -> ReportsTab(s, accent, txtP, txtS, vm::takeAction)
                            2 -> PhotoReportsTab(s.photoReports, accent, gold, txtP, txtS, vm::dismissPhotoReport)
                            3 -> UsersTab(s.users, accent, gold, green, txtP, txtS, vm::takeAction)
                            4 -> GroupsTab(s.groups, blue, txtP, txtS)
                            5 -> NotifyTab(accent, txtP, txtS)
                        }
                    }
                }

                // ── Snackbar ──────────────────────────────────────────────────
                s.snackbar?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E2E))
                                .border(0.6.dp, accent.copy(0.30f), RoundedCornerShape(12.dp))
                                .clickable { vm.dismissSnackbar() }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(msg, style = MaterialTheme.typography.bodySmall.copy(color = txtP))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 1 — Stats Overview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsTab(
    stats : AdminStats,
    accent: Color, gold: Color, green: Color, blue: Color,
    txtP  : Color, txtS : Color
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Platform Overview",
                style = MaterialTheme.typography.titleMedium.copy(color = txtP, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Live stats — tap Refresh to update",
                style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 10.sp)
            )
        }

        item {
            // 2×2 stat grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("👥", "Total Users",   stats.totalUsers.toString(),   blue,  Modifier.weight(1f))
                    StatCard("🏝",  "Active Groups", stats.activeGroups.toString(), green, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("🚨", "Total Reports", stats.totalReports.toString(), accent, Modifier.weight(1f))
                    StatCard("🚫", "Banned Users",  stats.bannedUsers.toString(),  accent, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("⚠️", "Flagged Users", stats.flaggedUsers.toString(), gold, Modifier.weight(1f))
                    StatCard("⚡", "Total Strikes", stats.totalStrikes.toString(), gold, Modifier.weight(1f))
                }
            }
        }

        item {
            // Admin identity card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1A0A0A), Color(0xFF200E0E))))
                    .border(0.8.dp, accent.copy(0.25f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(accent.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Text("🛡", fontSize = 22.sp) }
                    Column {
                        Text("Developer Account", style = MaterialTheme.typography.labelMedium.copy(color = accent, fontWeight = FontWeight.Bold))
                        Text(ADMIN_EMAIL, style = MaterialTheme.typography.bodySmall.copy(color = txtS, fontSize = 11.sp))
                        Text("Full access · All actions logged", style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.5f), fontSize = 9.sp))
                    }
                }
            }
        }

        item {
            // Quick actions
            Text("Quick Actions", style = MaterialTheme.typography.labelMedium.copy(color = txtS, letterSpacing = 1.sp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminChipButton("📦 Export", blue, Modifier.weight(1f)) { /* future: export CSV */ }
                AdminChipButton("🧹 Clear Logs", gold, Modifier.weight(1f)) { /* future */ }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatCard(emoji: String, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.03f))
            .border(0.7.dp, color.copy(0.20f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 22.sp)
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(color = color, fontWeight = FontWeight.ExtraBold))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(0.45f), fontSize = 10.sp))
        }
    }
}

@Composable
private fun AdminChipButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(0.10f))
            .border(0.6.dp, color.copy(0.25f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(color = color, fontWeight = FontWeight.Bold))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 2 — Reports
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReportsTab(
    s      : AdminUiState.Ready,
    accent : Color,
    txtP   : Color,
    txtS   : Color,
    onAction: (String, String) -> Unit
) {
    if (s.reports.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(44.dp))
                Text("All Clear 🎉", style = MaterialTheme.typography.titleMedium.copy(color = txtP, fontWeight = FontWeight.Bold))
                Text("No reported users at this time.", style = MaterialTheme.typography.bodySmall.copy(color = txtS))
            }
        }
        return
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "${s.reports.size} reported user(s)",
                style = MaterialTheme.typography.labelMedium.copy(color = txtS, letterSpacing = 1.sp)
            )
        }

        items(s.reports, key = { it.uid }) { user ->
            ReportedUserCard(
                user        = user,
                isActing    = s.actionInProgress == user.uid,
                accent      = accent,
                txtP        = txtP,
                txtS        = txtS,
                onAction    = { action -> onAction(user.uid, action) }
            )
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun ReportedUserCard(
    user    : ReportedUserSummary,
    isActing: Boolean,
    accent  : Color,
    txtP    : Color,
    txtS    : Color,
    onAction: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(if (user.isBanned) 0.03f else 0.05f))
            .border(
                0.8.dp,
                when {
                    user.isBanned  -> accent.copy(0.50f)
                    user.isFlagged -> Color(0xFFF59E0B).copy(0.40f)
                    else           -> Color.White.copy(0.08f)
                },
                RoundedCornerShape(16.dp)
            )
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Avatar
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape)
                    .background(
                        when {
                            user.isBanned  -> accent.copy(0.20f)
                            user.isFlagged -> Color(0xFFF59E0B).copy(0.15f)
                            else           -> Color.White.copy(0.07f)
                        }
                    ).border(1.dp,
                        when {
                            user.isBanned  -> accent.copy(0.50f)
                            user.isFlagged -> Color(0xFFF59E0B).copy(0.40f)
                            else           -> Color.White.copy(0.10f)
                        }, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(user.emoji, fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.alias, style = MaterialTheme.typography.titleSmall.copy(color = txtP, fontWeight = FontWeight.Bold))
                    if (user.isBanned) StatusBadge("BANNED", accent)
                    else if (user.isFlagged) StatusBadge("FLAGGED", Color(0xFFF59E0B))
                }
                Text(
                    "${user.reportCount} report(s)  ·  ${user.strikes} strike(s)  ·  ${user.gender.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 10.sp)
                )
            }

            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null, tint = txtS, modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // UID chip
                Text("UID: ${user.uid}", style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.4f), fontSize = 8.sp))

                // Individual report cards
                user.reports.forEach { report ->
                    ReportDetailChip(report, accent, txtP, txtS)
                }

                HorizontalDivider(color = Color.White.copy(0.07f), modifier = Modifier.padding(vertical = 4.dp))

                // Action buttons
                if (isActing) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdminActionBtn("⚠️ Warn",     Color(0xFFF59E0B), Modifier.weight(1f)) { onAction("warning") }
                            AdminActionBtn("⏸ Temp Ban", Color(0xFFFF6B35), Modifier.weight(1f)) { onAction("temp_ban") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdminActionBtn("🚫 Perm Ban", accent,            Modifier.weight(1f)) { onAction("perm_ban") }
                            AdminActionBtn("✓ Dismiss",  Color(0xFF22C55E), Modifier.weight(1f)) { onAction("dismiss") }
                        }
                        if (user.isBanned) {
                            AdminActionBtn("🔓 Unban", Color(0xFF3B82F6), Modifier.fillMaxWidth()) { onAction("unban") }
                        }
                        if (user.strikes > 0) {
                            AdminActionBtn("🔄 Reset Strikes", Color(0xFF94A3B8), Modifier.fillMaxWidth()) { onAction("reset_strikes") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportDetailChip(report: ReportItem, accent: Color, txtP: Color, txtS: Color) {
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(0.03f))
            .border(0.5.dp, accent.copy(0.15f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(report.reasonLabel.ifBlank { report.reason }, style = MaterialTheme.typography.labelMedium.copy(
            color = accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
        ))
        if (report.extraNote.isNotBlank()) {
            Text("\"${report.extraNote}\"", style = MaterialTheme.typography.bodySmall.copy(
                color = txtS, fontSize = 11.sp
            ))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Group: ${report.groupId.take(10)}…", style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.4f), fontSize = 8.sp))
            if (report.reportedAt != null) {
                Text(fmt.format(report.reportedAt.toDate()), style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.3f), fontSize = 8.sp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 3 — All Users
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UsersTab(
    users   : List<AdminUserRow>,
    accent  : Color, gold: Color, green: Color,
    txtP    : Color, txtS: Color,
    onAction: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = users.filter {
        if (searchQuery.isBlank()) true
        else it.alias.contains(searchQuery, true) || it.email.contains(searchQuery, true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value            = searchQuery,
            onValueChange    = { searchQuery = it },
            modifier         = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder      = { Text("Search by alias or email…", color = txtS, fontSize = 13.sp) },
            leadingIcon      = { Icon(Icons.Rounded.Search, null, tint = txtS, modifier = Modifier.size(18.dp)) },
            trailingIcon     = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, null, tint = txtS, modifier = Modifier.size(16.dp))
                    }
                }
            },
            shape            = RoundedCornerShape(12.dp),
            singleLine       = true,
            colors           = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = accent,
                unfocusedBorderColor = Color.White.copy(0.12f),
                focusedTextColor     = txtP,
                unfocusedTextColor   = txtP,
                cursorColor          = accent
            )
        )

        Text(
            "${filtered.size} user(s)",
            style    = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.uid }) { user ->
                UserAdminRow(user, accent, gold, green, txtP, txtS, onAction)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun UserAdminRow(
    user    : AdminUserRow,
    accent  : Color, gold: Color, green: Color,
    txtP    : Color, txtS: Color,
    onAction: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(if (user.isBanned) 0.03f else 0.04f))
            .border(0.7.dp,
                if (user.isBanned) accent.copy(0.30f)
                else if (user.reportCount > 0) gold.copy(0.25f)
                else Color.White.copy(0.07f),
                RoundedCornerShape(12.dp)
            )
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Avatar
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(0.06f)),
                contentAlignment = Alignment.Center
            ) { Text(user.emoji, fontSize = 18.sp) }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.alias, style = MaterialTheme.typography.bodyMedium.copy(color = txtP, fontWeight = FontWeight.SemiBold))
                    if (user.isBanned)       StatusBadge("BAN", accent)
                    if (user.strikes >= 2)   StatusBadge("⚡${user.strikes}", gold)
                    if (user.reportCount > 0) StatusBadge("🚨${user.reportCount}", Color(0xFFFF6B35))
                }
                Text(
                    user.gender.ifBlank { "?" }.uppercase() +
                    (if (user.email.isNotBlank()) "  •  ${user.email}" else "") +
                    (if (user.createdAt != null) "  •  ${fmt.format(user.createdAt.toDate())}" else ""),
                    style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null, tint = txtS, modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("UID: ${user.uid}", style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.4f), fontSize = 8.sp))
                if (user.groupId.isNotBlank())
                    Text("Group: ${user.groupId}", style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.4f), fontSize = 8.sp))

                HorizontalDivider(color = Color.White.copy(0.06f))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdminActionBtn("⚠️ Warn",     gold,              Modifier.weight(1f)) { onAction(user.uid, "warning") }
                    AdminActionBtn("⏸ Temp",     Color(0xFFFF6B35), Modifier.weight(1f)) { onAction(user.uid, "temp_ban") }
                    AdminActionBtn("🚫 Ban",      accent,            Modifier.weight(1f)) { onAction(user.uid, "perm_ban") }
                    if (user.isBanned)
                        AdminActionBtn("🔓 Unban", green, Modifier.weight(1f)) { onAction(user.uid, "unban") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 4 — Groups
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GroupsTab(
    groups : List<AdminGroupRow>,
    blue   : Color,
    txtP   : Color,
    txtS   : Color
) {
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val active   = groups.filter { !it.ceremonyDone }
    val completed = groups.filter { it.ceremonyDone }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatLabel("Active", active.size.toString(), Color(0xFF22C55E))
                StatLabel("Completed", completed.size.toString(), txtS)
                StatLabel("Total", groups.size.toString(), blue)
            }
        }

        if (active.isNotEmpty()) {
            item {
                Text("🟢 Active Groups", style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                ))
            }
            items(active, key = { it.id }) { g ->
                GroupCard(g, Color(0xFF22C55E), blue, txtP, txtS, fmt)
            }
        }

        if (completed.isNotEmpty()) {
            item {
                Text("⚫ Completed", style = MaterialTheme.typography.labelMedium.copy(
                    color = txtS, letterSpacing = 1.sp
                ))
            }
            items(completed, key = { it.id }) { g ->
                GroupCard(g, txtS, blue, txtP, txtS, fmt)
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun StatLabel(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(color = color, fontWeight = FontWeight.ExtraBold))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = color.copy(0.70f), fontSize = 9.sp))
    }
}

@Composable
private fun GroupCard(
    g      : AdminGroupRow,
    color  : Color,
    blue   : Color,
    txtP   : Color,
    txtS   : Color,
    fmt    : SimpleDateFormat
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.04f))
            .border(0.7.dp, color.copy(0.25f), RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(blue.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Text("🏝", fontSize = 17.sp) }

            Column(modifier = Modifier.weight(1f)) {
                Text("Day ${g.day}  ·  ${g.memberCount} members", style = MaterialTheme.typography.bodyMedium.copy(
                    color = txtP, fontWeight = FontWeight.SemiBold
                ))
                Text(
                    (if (g.createdAt != null) "Created ${fmt.format(g.createdAt.toDate())}" else "No date") +
                    if (g.ceremonyDone) "  ·  COMPLETED" else "",
                    style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 9.sp)
                )
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = txtS, modifier = Modifier.size(18.dp))
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Group ID: ${g.id}", style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.4f), fontSize = 8.sp))
                if (g.memberAliases.isNotEmpty()) {
                    Text("Members: ${g.memberAliases.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall.copy(color = txtS, fontSize = 11.sp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reusable mini-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(0.15f))
            .border(0.4.dp, color.copy(0.35f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold, fontSize = 8.sp))
    }
}

@Composable
private fun AdminActionBtn(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(0.10f))
            .border(0.6.dp, color.copy(0.25f), RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(
            color = color, fontWeight = FontWeight.Bold, fontSize = 9.5.sp
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 5 — Manual Notification Sender (OneSignal)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotifyTab(accent: Color, txtP: Color, txtS: Color) {
    val scope  = rememberCoroutineScope()
    val db     = FirebaseFirestore.getInstance()
    val cardBg = Color(0xFF0E1120)
    val border = Color(0xFF2A2D4A)

    // ── Broadcast state ──────────────────────────────────────────────────────
    var bTitle     by remember { mutableStateOf("") }
    var bBody      by remember { mutableStateOf("") }
    var bStatus    by remember { mutableStateOf<String?>(null) }
    var bSending   by remember { mutableStateOf(false) }

    // ── User notify state ─────────────────────────────────────────────────────
    var uUid       by remember { mutableStateOf("") }
    var uTitle     by remember { mutableStateOf("") }
    var uBody      by remember { mutableStateOf("") }
    var uStatus    by remember { mutableStateOf<String?>(null) }
    var uSending   by remember { mutableStateOf(false) }

    // ── Group notify state ─────────────────────────────────────────────────────
    var gGroupId   by remember { mutableStateOf("") }
    var gTitle     by remember { mutableStateOf("") }
    var gBody      by remember { mutableStateOf("") }
    var gStatus    by remember { mutableStateOf<String?>(null) }
    var gSending   by remember { mutableStateOf(false) }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "📣 Push Notifications",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = txtP, fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Send targeted or broadcast notifications via OneSignal",
                style = MaterialTheme.typography.bodySmall.copy(color = txtS)
            )
        }

        // ── Panel 1: Broadcast to ALL ────────────────────────────────────────
        item {
            NotifyPanel(
                title  = "🌐 Broadcast to All Users",
                accent = accent, cardBg = cardBg, border = border
            ) {
                NotifyTextField("Notification Title", bTitle, txtP) { bTitle = it }
                Spacer(Modifier.height(8.dp))
                NotifyTextField("Message Body", bBody, txtP, singleLine = false) { bBody = it }
                Spacer(Modifier.height(12.dp))
                bStatus?.let { StatusLabel(it) }
                NotifySendButton(
                    label    = if (bSending) "Sending…" else "Send to Everyone",
                    accent   = accent,
                    enabled  = bTitle.isNotBlank() && bBody.isNotBlank() && !bSending
                ) {
                    bSending = true
                    scope.launch {
                        val ok = VeilNotifier.sendToAll(bTitle.trim(), bBody.trim())
                        bStatus  = if (ok) "✅ Broadcast sent successfully!" else "❌ Send failed — check Logcat"
                        bSending = false
                        if (ok) { bTitle = ""; bBody = "" }
                    }
                }
            }
        }

        // ── Panel 2: Send to a specific User ────────────────────────────────
        item {
            NotifyPanel(
                title  = "👤 Send to Specific User",
                accent = accent, cardBg = cardBg, border = border
            ) {
                NotifyTextField("Firebase UID", uUid, txtP) { uUid = it }
                Spacer(Modifier.height(8.dp))
                NotifyTextField("Notification Title", uTitle, txtP) { uTitle = it }
                Spacer(Modifier.height(8.dp))
                NotifyTextField("Message Body", uBody, txtP, singleLine = false) { uBody = it }
                Spacer(Modifier.height(12.dp))
                uStatus?.let { StatusLabel(it) }
                NotifySendButton(
                    label   = if (uSending) "Sending…" else "Send to User",
                    accent  = accent,
                    enabled = uUid.isNotBlank() && uTitle.isNotBlank() && uBody.isNotBlank() && !uSending
                ) {
                    uSending = true
                    scope.launch {
                        val ok = VeilNotifier.sendToUser(uUid.trim(), uTitle.trim(), uBody.trim())
                        uStatus  = if (ok) "✅ Sent to user!" else "❌ Failed — verify UID is correct"
                        uSending = false
                        if (ok) { uUid = ""; uTitle = ""; uBody = "" }
                    }
                }
            }
        }

        // ── Panel 3: Send to a Group ─────────────────────────────────────────
        item {
            NotifyPanel(
                title  = "🏝 Send to a Circle (Group)",
                accent = accent, cardBg = cardBg, border = border
            ) {
                NotifyTextField("Group ID", gGroupId, txtP) { gGroupId = it }
                Spacer(Modifier.height(8.dp))
                NotifyTextField("Notification Title", gTitle, txtP) { gTitle = it }
                Spacer(Modifier.height(8.dp))
                NotifyTextField("Message Body", gBody, txtP, singleLine = false) { gBody = it }
                Spacer(Modifier.height(12.dp))
                gStatus?.let { StatusLabel(it) }
                NotifySendButton(
                    label   = if (gSending) "Sending…" else "Send to Group",
                    accent  = accent,
                    enabled = gGroupId.isNotBlank() && gTitle.isNotBlank() && gBody.isNotBlank() && !gSending
                ) {
                    gSending = true
                    scope.launch {
                        val ok = VeilNotifier.sendToGroup(db, gGroupId.trim(), gTitle.trim(), gBody.trim())
                        gStatus  = if (ok) "✅ Sent to group!" else "❌ Failed — verify Group ID"
                        gSending = false
                        if (ok) { gGroupId = ""; gTitle = ""; gBody = "" }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun NotifyPanel(
    title  : String,
    accent : Color,
    cardBg : Color,
    border : Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(0.7.dp, border, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(
                color = accent, fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun NotifyTextField(
    placeholder : String,
    value       : String,
    txtP        : Color,
    singleLine  : Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        placeholder   = { Text(placeholder, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF506070))) },
        singleLine    = singleLine,
        minLines      = if (singleLine) 1 else 2,
        maxLines      = if (singleLine) 1 else 4,
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(10.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Color(0xFF6B7AFF),
            unfocusedBorderColor = Color(0xFF2A2D4A),
            focusedTextColor     = txtP,
            unfocusedTextColor   = txtP,
            cursorColor          = Color(0xFF6B7AFF)
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = txtP)
    )
}

@Composable
private fun NotifySendButton(label: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(accent, Color(0xFF8B5CF6)))
                else Brush.horizontalGradient(listOf(Color(0xFF2A2D4A), Color(0xFF2A2D4A)))
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                color      = if (enabled) Color.White else Color(0xFF506070),
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun StatusLabel(msg: String) {
    val isOk = msg.startsWith("✅")
    Text(
        msg,
        style = MaterialTheme.typography.bodySmall.copy(
            color = if (isOk) Color(0xFF4ADE80) else Color(0xFFFF8080)
        ),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Photo Reports Tab — reported posts from the Moments feed
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoReportsTab(
    reports   : List<PhotoReportItem>,
    accent    : Color,
    gold      : Color,
    txtP      : Color,
    txtS      : Color,
    onDismiss : (String) -> Unit
) {
    val fmt = remember { java.text.SimpleDateFormat("dd MMM · hh:mm a", java.util.Locale.getDefault()) }

    if (reports.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(44.dp))
                Text("No photo reports", style = MaterialTheme.typography.titleMedium.copy(
                    color = txtP, fontWeight = FontWeight.Bold
                ))
                Text("All Moments posts are clean!", style = MaterialTheme.typography.bodySmall.copy(color = txtS))
            }
        }
        return
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.PhotoCamera, null, tint = gold, modifier = Modifier.size(17.dp))
                Text(
                    "${reports.size} photo report${if (reports.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium.copy(color = txtS, letterSpacing = 0.8.sp)
                )
            }
        }

        items(reports, key = { it.docId }) { report ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.04f))
                    .border(0.7.dp, accent.copy(0.20f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(accent.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Photo, null, tint = accent, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            "Photo Report",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp
                            )
                        )
                    }
                    report.reportedAt?.let {
                        Text(
                            fmt.format(it.toDate()),
                            style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.5f), fontSize = 9.sp)
                        )
                    }
                }

                // Reason
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.03f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        report.reason.ifBlank { "No reason given" },
                        style = MaterialTheme.typography.bodySmall.copy(color = txtP, fontSize = 12.sp)
                    )
                }

                // Meta info chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChipAdmin("Uploader", report.uploaderUid.take(8) + "…", txtS)
                    InfoChipAdmin("Post", report.postId.take(8) + "…", txtS)
                }

                // Dismiss button
                OutlinedButton(
                    onClick   = { onDismiss(report.docId) },
                    shape     = RoundedCornerShape(8.dp),
                    modifier  = Modifier.fillMaxWidth().height(38.dp),
                    border    = androidx.compose.foundation.BorderStroke(0.7.dp, Color.White.copy(0.12f)),
                    colors    = ButtonDefaults.outlinedButtonColors(contentColor = txtS)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Dismiss report", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun InfoChipAdmin(label: String, value: String, txtS: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(0.04f))
            .border(0.5.dp, Color.White.copy(0.07f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("$label:", style = MaterialTheme.typography.labelSmall.copy(color = txtS.copy(0.5f), fontSize = 9.sp))
        Text(value, style = MaterialTheme.typography.labelSmall.copy(color = txtS, fontSize = 9.sp))
    }
}

