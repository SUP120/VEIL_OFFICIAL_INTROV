package com.ns.findyourintroverfriend.dev

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.ns.findyourintroverfriend.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

// ─────────────────────────────────────────────────────────────────────────────
//  Dev Inspector Screen
//
//  Two tabs:
//    🗺  TELEPORT  — jump to any screen instantly, no game state needed
//    🔧  INSPECTOR — live Firestore state: queue, groups, users
//
//  Gate: only DEV_EMAILS get full access (force-day, danger zone, teleport).
// ─────────────────────────────────────────────────────────────────────────────

private val DEV_EMAILS = setOf(
    "supriyamkumar098@gmail.com",
    "itspossible4202@gmail.com"
)

// ── Colours ───────────────────────────────────────────────────────────────────
private val DBg       = Color(0xFF020711)
private val DCard     = Color(0xFF0D1525)
private val DBorder   = Color(0xFF1E2A40)
private val DAccent   = Color(0xFF7A9FE8)
private val DGold     = Color(0xFFD4A853)
private val DGreen    = Color(0xFF4AE080)
private val DRed      = Color(0xFFE08080)
private val DViolet   = Color(0xFF9B7FE8)
private val DTxt      = Color(0xFFD0DCF0)
private val DTxtMuted = Color(0xFF6B7FA3)

// ─────────────────────────────────────────────────────────────────────────────
//  Screen Teleport destinations — every screen in the app
// ─────────────────────────────────────────────────────────────────────────────

data class TeleportDest(
    val label    : String,
    val emoji    : String,
    val route    : String,
    val color    : Color,
    val note     : String = ""
)

private val TELEPORT_DESTINATIONS = listOf(
    // ── Core flow ─────────────────────────────────────────────────────────────
    TeleportDest("Veil Home",       "🏠", "veil_home",       DAccent,  "Find circle / main landing"),
    TeleportDest("Waiting Room",    "⏳", "waiting_room",    DViolet,  "Matchmaking queue"),
    TeleportDest("Island Home",     "🏝️", "island_home",     DGreen,   "Day 1-2-3 activity hub"),
    TeleportDest("Reveal Ceremony", "🎭", "ceremony",        DGold,    "Day 3 bond reveal"),
    TeleportDest("Founders",        "🏅", "founders",        DGold,    "Early believers page"),

    // ── Day 1 ─────────────────────────────────────────────────────────────────
    TeleportDest("Question Card",   "❓", "question_card",   DAccent,  "Submit a question to circle"),
    TeleportDest("Answer Card",     "💬", "answer_card",     DAccent,  "Answer incoming questions"),
    TeleportDest("Circle Vibes",    "🎨", "vibes",           DViolet,  "Song · Color · One Word Bio"),
    TeleportDest("Inbox",           "📬", "inbox",           DAccent,  "Notifications & match updates"),

    // ── Day 2 ─────────────────────────────────────────────────────────────────
    TeleportDest("Would You Rather","🎲", "would_you_rather",DViolet,  "Day 2 poll game"),
    TeleportDest("Game Invite",     "🎮", "game_invite",     DViolet,  "Tic-Tac-Toe challenge hub"),
    TeleportDest("Dare to Share",   "🤫", "dare_to_share",   DViolet,  "Anonymous confessions Day 2"),
    TeleportDest("Secret Compliment","💌","secret_compliment",DViolet, "One anonymous compliment"),

    // ── Day 3 ─────────────────────────────────────────────────────────────────
    TeleportDest("True or Myth",    "🔮", "true_or_myth",    DGold,    "Guess who wrote what"),
    TeleportDest("True/Myth Setup", "✍️", "true_or_myth_setup",DGold,  "Submit your own fact"),
    TeleportDest("Slow Chat",       "🐢", "slow_chat",       DGold,    "⭐ Day 3 — test billing here"),

    // ── Billing / Payment screens ─────────────────────────────────────────────
    TeleportDest("Chat Credits",    "💎", "chat_credits",    DGold,    "🛒 Credit packs paywall — TEST HERE"),
    TeleportDest("Chat Unlock",     "🔓", "chat_unlock",     DRed,     "Legacy ₹79 one-time unlock"),

    // ── Feed ──────────────────────────────────────────────────────────────────
    TeleportDest("Aesthetics Feed", "📸", "aesthetics_feed", DViolet,  "Photo vibe feed (all days)"),
    TeleportDest("Active Rooms",    "🌕", "active_rooms",    DAccent,  "Live room viewer"),
    TeleportDest("My Q Inbox",      "📥", "my_questions_inbox",DAccent,"Replies to my questions"),

    // ── Admin only ────────────────────────────────────────────────────────────
    TeleportDest("Admin Dashboard", "⚙️", "admin_dashboard", DRed,     "Admin only"),
)

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DevInspectorScreen(
    vm         : DevInspectorViewModel = viewModel(),
    onBack     : () -> Unit,
    onNavigate : (route: String) -> Unit = {}   // ← NEW: nav callback for teleport
) {
    val state        by vm.state.collectAsStateWithLifecycle()
    val snackbarHost  = remember { SnackbarHostState() }
    val scope         = rememberCoroutineScope()

    val currentEmail  = FirebaseAuth.getInstance().currentUser?.email?.lowercase() ?: ""
    val hasFullAccess = currentEmail in DEV_EMAILS

    // Active tab: 0 = Teleport, 1 = Inspector
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.forceDayMsg) {
        state.forceDayMsg?.let { msg ->
            scope.launch { snackbarHost.showSnackbar(msg, duration = SnackbarDuration.Short) }
            vm.clearForceDayMsg()
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = DBg
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .systemBarsPadding()
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "← Back",
                    style    = MaterialTheme.typography.labelMedium.copy(color = DAccent),
                    modifier = Modifier.clickable { onBack() }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛠 Dev Panel", style = MaterialTheme.typography.titleSmall.copy(color = DTxt))
                    Text(
                        if (hasFullAccess) "✅ Full Access  ·  $currentEmail"
                        else               "👁 Read-only",
                        fontSize = 9.sp, color = DTxtMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    "↺ Refresh",
                    style    = MaterialTheme.typography.labelMedium.copy(color = DGold),
                    modifier = Modifier.clickable { vm.refresh() }
                )
            }

            // ── Tab selector ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A1020))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabChip(label = "🗺 Screen Teleport", selected = tab == 0) { tab = 0 }
                TabChip(label = "🔧 Inspector",        selected = tab == 1) { tab = 1 }
            }

            // ── Content ───────────────────────────────────────────────────────
            when (tab) {
                0 -> TeleportTab(
                    hasFullAccess = hasFullAccess,
                    onNavigate    = onNavigate,
                    snackHost     = snackbarHost,
                    scope         = scope
                )
                1 -> InspectorTab(
                    state         = state,
                    hasFullAccess = hasFullAccess,
                    vm            = vm
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 0 — Screen Teleport
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TeleportTab(
    hasFullAccess : Boolean,
    onNavigate    : (String) -> Unit,
    snackHost     : SnackbarHostState,
    scope         : kotlinx.coroutines.CoroutineScope
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header info box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0D1A2A))
                .border(0.8.dp, DViolet.copy(0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("🗺  Screen Teleporter", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DViolet)
                Text(
                    "Jump directly to any screen. No game state required.\nPerfect for testing purchase flows and UI.",
                    fontSize = 11.sp, color = DTxtMuted, lineHeight = 17.sp
                )
            }
        }

        if (!hasFullAccess) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A1A0D))
                    .border(0.8.dp, Color(0xFF6B3A1A), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🔒  Full access restricted to dev accounts.\nSign in with a dev email to unlock teleport.",
                    fontSize = 11.sp, color = DRed, textAlign = TextAlign.Center, lineHeight = 17.sp
                )
            }
            return
        }

        // ── Billing highlight card ────────────────────────────────────────────
        Text("⭐ BILLING SCREENS — TEST HERE", fontSize = 9.sp, color = DGold,
            letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)

        listOf(
            TELEPORT_DESTINATIONS.first { it.route == "chat_credits" },
            TELEPORT_DESTINATIONS.first { it.route == "slow_chat" },
            TELEPORT_DESTINATIONS.first { it.route == "chat_unlock" },
        ).forEach { dest ->
            TeleportCard(dest = dest, highlighted = true, onNavigate = onNavigate)
        }

        Spacer(Modifier.height(4.dp))
        Text("ALL SCREENS", fontSize = 9.sp, color = DTxtMuted,
            letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)

        // Group into sections by label prefix
        val sections = listOf(
            "Core Flow"   to listOf("veil_home","waiting_room","island_home","ceremony","founders"),
            "Day 1"       to listOf("question_card","answer_card","vibes","inbox"),
            "Day 2"       to listOf("would_you_rather","game_invite","dare_to_share","secret_compliment"),
            "Day 3"       to listOf("true_or_myth","true_or_myth_setup","slow_chat"),
            "Billing"     to listOf("chat_credits","chat_unlock"),
            "Feed"        to listOf("aesthetics_feed","active_rooms","my_questions_inbox"),
            "Admin"       to listOf("admin_dashboard"),
        )

        sections.forEach { (sectionName, routes) ->
            val dests = TELEPORT_DESTINATIONS.filter { it.route in routes }
            if (dests.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    sectionName.uppercase(),
                    fontSize = 9.sp, color = DTxtMuted.copy(0.60f),
                    letterSpacing = 1.2.sp, fontFamily = FontFamily.Monospace
                )
                dests.forEach { dest ->
                    val isBilling = dest.route in listOf("chat_credits","chat_unlock","slow_chat")
                    TeleportCard(dest = dest, highlighted = isBilling, onNavigate = onNavigate)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TeleportCard(
    dest        : TeleportDest,
    highlighted : Boolean = false,
    onNavigate  : (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (highlighted) Color(0xFF1A1508) else DCard
            )
            .border(
                0.8.dp,
                if (highlighted) DGold.copy(0.45f) else DBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable { onNavigate(dest.route) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Emoji icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(dest.color.copy(0.12f))
                .border(0.8.dp, dest.color.copy(0.30f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(dest.emoji, fontSize = 16.sp)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                dest.label,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (highlighted) DGold else DTxt
            )
            if (dest.note.isNotBlank()) {
                Text(dest.note, fontSize = 10.sp, color = DTxtMuted, lineHeight = 14.sp)
            }
        }

        // Route chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(dest.color.copy(0.10f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text("→", fontSize = 14.sp, color = dest.color)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 1 — Inspector (original Firestore state viewer)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InspectorTab(
    state         : DevInspectorState,
    hasFullAccess : Boolean,
    vm            : DevInspectorViewModel
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DAccent, strokeWidth = 2.dp)
        }
        return
    }

    state.error?.let { err ->
        Text("⚠ $err", style = MaterialTheme.typography.bodySmall.copy(color = DRed),
            modifier = Modifier.padding(16.dp))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        DevCard(title = "📍 Session") {
            DevRow("My UID",       state.myUid)
            DevRow("Last refresh", state.lastRefresh)
            DevRow("Access level", if (hasFullAccess) "✅ Full dev access" else "👁 Read-only")
        }

        DevCard(title = "⏳ Queue  (${state.queue.size} waiting)") {
            if (state.queue.isEmpty()) {
                DevNote("Queue is empty")
            } else {
                val males   = state.queue.count { it.gender == "Male" }
                val females = state.queue.count { it.gender == "Female" }
                val bots    = state.queue.count { it.isBot }
                DevRow("Males in queue",   "♂ $males / 2  ${if (males >= 2) "✅" else "❌"}")
                DevRow("Females in queue", "♀ $females / 2  ${if (females >= 2) "✅" else "❌"}")
                if (bots > 0) {
                    DevRow("Bots in queue", "🤖 $bots")
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = DBorder)
                Spacer(Modifier.height(6.dp))
                state.queue.forEach { e ->
                    DevRow(
                        label = "${if (e.isBot) "🤖" else ""} ${e.gender}  ${e.uid.take(8)}…",
                        value = "joined ${e.joinedAt}  ${if (e.hasToken) "🔔" else "🔕"}"
                    )
                }
                if (males >= 2 && females >= 2) {
                    Spacer(Modifier.height(8.dp))
                    StatusBadge("✅ READY TO MATCH", green = true)
                } else {
                    Spacer(Modifier.height(8.dp))
                    StatusBadge("⏳ Need ${2 - males} more ♂  +  ${2 - females} more ♀", green = false)
                }
            }
        }

        // ── Bot Controls (dev only) ───────────────────────────────────────────
        if (hasFullAccess) {
            DevCard(title = "🤖 Bot Controls") {
                DevNote("Add fake users to test all screens without real people")
                Spacer(Modifier.height(8.dp))
                
                // Bot count display
                val scope = rememberCoroutineScope()
                var botCount by remember { mutableStateOf(0) }
                var isLoading by remember { mutableStateOf(false) }
                var statusMsg by remember { mutableStateOf("") }
                
                LaunchedEffect(Unit) {
                    isLoading = true
                    DevBotManager.getBotCountInQueue().onSuccess { count ->
                        botCount = count
                    }
                    isLoading = false
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Bots in queue:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = DTxtMuted, fontSize = 11.sp
                        )
                    )
                    Text(
                        "🤖 $botCount",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (botCount > 0) DGreen else DTxtMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                if (statusMsg.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DViolet.copy(0.10f))
                            .padding(8.dp)
                    ) {
                        Text(
                            statusMsg,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = DViolet, fontSize = 10.sp
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DBorder)
                Spacer(Modifier.height(10.dp))
                
                // Add bots buttons
                Text(
                    "ADD BOTS TO QUEUE:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DTxtMuted, fontSize = 9.sp, letterSpacing = 1.sp
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 3, 5, 10).forEach { count ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DGreen.copy(0.12f))
                                .border(0.8.dp, DGreen.copy(0.35f), RoundedCornerShape(8.dp))
                                .clickable(enabled = !isLoading) {
                                    isLoading = true
                                    statusMsg = "Adding $count bot${if (count > 1) "s" else ""}..."
                                    scope.launch {
                                        DevBotManager.addBotsToQueue(count).onSuccess { added ->
                                            botCount += added
                                            statusMsg = "✅ Added $added bot${if (added > 1) "s" else ""} to queue"
                                            vm.refresh()
                                        }.onFailure { e ->
                                            statusMsg = "❌ Error: ${e.message}"
                                        }
                                        isLoading = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+$count",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = DGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(10.dp))
                
                // Clear bots button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DRed.copy(0.12f))
                        .border(0.8.dp, DRed.copy(0.35f), RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLoading && botCount > 0) {
                            isLoading = true
                            statusMsg = "Clearing all bots..."
                            scope.launch {
                                DevBotManager.clearBotsFromQueue().onSuccess { cleared ->
                                    botCount = 0
                                    statusMsg = "✅ Cleared $cleared bot${if (cleared > 1) "s" else ""}"
                                    vm.refresh()
                                }.onFailure { e ->
                                    statusMsg = "❌ Error: ${e.message}"
                                }
                                isLoading = false
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 Clear All Bots",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (botCount > 0) DRed else DTxtMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DBorder)
                Spacer(Modifier.height(10.dp))
                
                // Instant group formation
                Text(
                    "INSTANT GROUP:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DTxtMuted, fontSize = 9.sp, letterSpacing = 1.sp
                    )
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DViolet.copy(0.12f))
                        .border(0.8.dp, DViolet.copy(0.35f), RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLoading) {
                            isLoading = true
                            statusMsg = "Forming group with 3 bots..."
                            scope.launch {
                                DevBotManager.injectBotsAndFormGroup().onSuccess { groupId ->
                                    statusMsg = "✅ Group formed: ${groupId.take(20)}..."
                                    vm.refresh()
                                }.onFailure { e ->
                                    statusMsg = "❌ Error: ${e.message}"
                                }
                                isLoading = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⚡ Form Group with 3 Bots",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = DViolet,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Spacer(Modifier.height(6.dp))
                DevNote("Creates instant group (you + 3 bots) and navigates to Island Home")
            }
        }

        DevCard(title = "🌕 Groups  (${state.groups.size} active)") {
            if (state.groups.isEmpty()) {
                DevNote("No groups created yet")
            } else {
                state.groups.forEachIndexed { i, g ->
                    if (i > 0) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DBorder)
                        Spacer(Modifier.height(8.dp))
                    }
                    DevRow("Group ID", g.groupId)
                    DevRow("Day",      "${g.day}")
                    DevRow("Created",  g.createdAt)
                    DevRow("Members (${g.memberUids.size})", "")
                    g.memberUids.forEachIndexed { mi, uid ->
                        DevRow("  [$mi]", uid)
                    }
                    val ok = g.memberUids.size == 4
                    Spacer(Modifier.height(4.dp))
                    StatusBadge(
                        if (ok) "✅ 4 members — group is correct"
                        else    "⚠️ Only ${g.memberUids.size}/4 members!",
                        green = ok
                    )

                    if (hasFullAccess) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Force to Day:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = DTxtMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3).forEach { targetDay ->
                                val isCurrent = g.day == targetDay
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCurrent) Color(0xFF1A3A2A) else Color(0xFF0D1A2A))
                                        .border(
                                            0.8.dp,
                                            if (isCurrent) Color(0xFF2A8B5A) else Color(0xFF1E3A5A),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable(enabled = !isCurrent) { vm.forceDay(g.groupId, targetDay) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (isCurrent) "Day $targetDay ✓" else "Day $targetDay",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color      = if (isCurrent) DGreen else DAccent,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize   = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        DevCard(title = "👤 All Users  (first 20)") {
            if (state.users.isEmpty()) {
                DevNote("No users found")
            } else {
                state.users.forEachIndexed { i, u ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${u.alias}  ${if (u.gender == "Male") "♂" else "♀"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = DTxt, fontFamily = FontFamily.Monospace, fontSize = 11.sp
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(if (u.profileComplete) "✓ profile" else "✗ profile", u.profileComplete)
                            StatusPill(if (u.groupId != "none") "✓ group" else "✗ group",   u.groupId != "none")
                            StatusPill(if (u.hasToken) "🔔" else "🔕", u.hasToken)
                        }
                    }
                    Text(
                        "uid: ${u.uid}   groupId: ${u.groupId}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF4A5A7A), fontFamily = FontFamily.Monospace, fontSize = 9.sp
                        )
                    )
                }
            }
        }

        if (hasFullAccess) {
            DevCard(title = "⚠️ Danger Zone") {
                DevNote("These actions affect live Firestore data")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DangerButton("🗑  Clear Queue") { vm.clearQueue() }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DViolet.copy(0.20f) else Color(0xFF0D1020))
            .border(0.8.dp, if (selected) DViolet.copy(0.55f) else DBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize   = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color      = if (selected) DViolet else DTxtMuted
        )
    }
}

@Composable
private fun DevCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DCard)
            .border(0.8.dp, DBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium.copy(color = DAccent, fontSize = 12.sp))
            Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelSmall.copy(color = DTxtMuted))
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
private fun DevRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall.copy(
                color = DTxtMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp
            ),
            modifier = Modifier.weight(0.45f)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(
                color     = DTxt, fontFamily = FontFamily.Monospace,
                fontSize  = 10.sp, textAlign = TextAlign.End
            ),
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun DevNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(color = DTxtMuted, fontSize = 11.sp))
}

@Composable
private fun StatusBadge(label: String, green: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (green) Color(0xFF0D2A1A) else Color(0xFF2A1A0D))
            .border(0.8.dp, if (green) Color(0xFF1A6B3A) else Color(0xFF6B3A1A), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (green) DGreen else Color(0xFFE08040), fontSize = 11.sp, textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) Color(0xFF0D2A1A) else Color(0xFF1A1A1A))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (active) DGreen else Color(0xFF4A4A4A), fontSize = 9.sp
            )
        )
    }
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (confirm) Color(0xFF4A1A1A) else Color(0xFF1A1A1A))
            .border(0.8.dp, if (confirm) Color(0xFF8B3A3A) else Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
            .clickable {
                if (confirm) { onClick(); confirm = false } else { confirm = true }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            if (confirm) "Tap again to confirm" else label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (confirm) DRed else Color(0xFF6B4A4A), fontSize = 11.sp
            )
        )
    }
}
