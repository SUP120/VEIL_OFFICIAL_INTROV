package com.ns.findyourintroverfriend.lobby

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
//  Palette (same as RoomLobbyScreen)
// ─────────────────────────────────────────────────────────────────────────────

private val WBg         = Color(0xFF122620)
private val WSurface    = Color(0xFF1A3028)
private val WCard       = Color(0xFF1E362C)
private val WCardBorder = Color(0xFF274035)
private val WGold       = Color(0xFFD6AD60)
private val WGoldDim    = Color(0xFFB68D40)
private val WCream      = Color(0xFFF4EBD0)
private val WCreamMid   = Color(0xFFD4C9AB)
private val WCreamMuted = Color(0xFF8C836A)
private val WBlue       = Color(0xFF6BA4E0)
private val WPurple     = Color(0xFFB07AE0)

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

data class WaitMember(val uid: String, val alias: String, val emoji: String, val gender: String)

sealed class WaitUiState {
    object Loading : WaitUiState()
    data class Waiting(
        val roomName      : String,
        val roomEmoji     : String,
        val description   : String,
        val theme         : String,
        val rawMaleCount  : Int,            // from doc directly — instant, used for counter
        val rawFemaleCount: Int,            // from doc directly — instant, used for counter
        val maleMembers   : List<WaitMember>,   // fetched profiles (may arrive later)
        val femaleMembers : List<WaitMember>
    ) : WaitUiState()
    data class GroupReady(val groupId: String) : WaitUiState()
    data class Error(val msg: String) : WaitUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class LobbyWaitingViewModel(private val roomId: String) : ViewModel() {

    private val tag  = "LobbyWait"
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()
    val myUid get()  = auth.currentUser?.uid ?: ""

    private val _ui = MutableStateFlow<WaitUiState>(WaitUiState.Loading)
    val ui: StateFlow<WaitUiState> = _ui.asStateFlow()

    private var roomListener: ListenerRegistration? = null

    init { startListening() }

    private fun startListening() {
        roomListener = db.collection("lobby_rooms").document(roomId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    Log.e(tag, "room listen: ${err?.message}")
                    return@addSnapshotListener
                }

                // Room filled — group has been created
                val groupId = snap.getString("groupId") ?: ""
                if (groupId.isNotBlank()) {
                    _ui.value = WaitUiState.GroupReady(groupId)
                    return@addSnapshotListener
                }

                val maleUids    = (snap.get("maleMembers")   as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val femaleUids  = (snap.get("femaleMembers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                // Fall back to maleCount / femaleCount fields; then to total memberCount for old rooms
                var rawMaleCount   = maleUids.size.takeIf { it > 0 } ?: (snap.getLong("maleCount")?.toInt()   ?: 0)
                var rawFemaleCount = femaleUids.size.takeIf { it > 0 } ?: (snap.getLong("femaleCount")?.toInt() ?: 0)
                if (rawMaleCount + rawFemaleCount == 0) {
                    // Old room format — no gender breakdown yet; use total memberCount as display
                    rawMaleCount = (snap.getLong("memberCount")?.toInt() ?: 0)
                }

                val name    = snap.getString("name")        ?: "Your Room"
                val emoji   = snap.getString("emoji")       ?: "🌙"
                val desc    = snap.getString("description") ?: ""
                val theme   = snap.getString("theme")       ?: "open"

                // 1️⃣ Show counts IMMEDIATELY before profile fetches finish
                val cur = _ui.value
                if (cur is WaitUiState.Waiting) {
                    _ui.value = cur.copy(
                        roomName       = name,
                        roomEmoji      = emoji,
                        description    = desc,
                        rawMaleCount   = rawMaleCount,
                        rawFemaleCount = rawFemaleCount
                    )
                } else {
                    _ui.value = WaitUiState.Waiting(
                        name, emoji, desc, theme,
                        rawMaleCount, rawFemaleCount,
                        emptyList(), emptyList()
                    )
                }

                // 2️⃣ Fetch profiles async and update slot cards
                viewModelScope.launch {
                    val maleMembers   = maleUids.mapNotNull   { fetchMember(it, "Male") }
                    val femaleMembers = femaleUids.mapNotNull { fetchMember(it, "Female") }
                    val updated = _ui.value
                    if (updated is WaitUiState.Waiting) {
                        _ui.value = updated.copy(
                            maleMembers   = maleMembers,
                            femaleMembers = femaleMembers
                        )
                    }
                }
            }
    }

    private suspend fun fetchMember(uid: String, gender: String): WaitMember? = try {
        val doc = db.collection("users").document(uid).get().await()
        WaitMember(
            uid    = uid,
            alias  = doc.getString("alias") ?: "Anonymous",
            emoji  = doc.getString("emoji") ?: "🌙",
            gender = gender
        )
    } catch (e: Exception) { null }

    /** Mark ceremony done + sets groupId on user — called before navigating. */
    fun onGroupReadyNavigate(groupId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .update("ceremonyDone", true).await()
            } catch (e: Exception) {
                Log.w(tag, "ceremonyDone update: ${e.message}")
            }
            onDone()
        }
    }

    fun leaveRoom(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val roomRef = db.collection("lobby_rooms").document(roomId)
                db.runTransaction { tx ->
                    val snap = tx.get(roomRef)
                    val males   = (snap.get("maleMembers")   as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                    val females = (snap.get("femaleMembers") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                    males.remove(myUid); females.remove(myUid)
                    val all = males + females
                    tx.update(roomRef, mapOf(
                        "maleMembers" to males, "femaleMembers" to females,
                        "maleCount"   to males.size, "femaleCount" to females.size,
                        "members"     to all, "memberCount" to all.size,
                        // If empty, mark as abandoned so it disappears from list
                        "status" to if (all.isEmpty()) "abandoned" else "waiting"
                    ))
                }.await()
                db.collection("users").document(myUid)
                    .update("lobbyRoomId", com.google.firebase.firestore.FieldValue.delete()).await()
            } catch (e: Exception) {
                Log.e(tag, "leaveRoom: ${e.message}")
            }
            onDone()
        }
    }

    override fun onCleared() {
        super.onCleared()
        roomListener?.remove()
    }

    companion object {
        fun factory(roomId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LobbyWaitingViewModel(roomId) as T
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LobbyWaitingScreen(
    roomId       : String,
    vm           : LobbyWaitingViewModel = viewModel(factory = LobbyWaitingViewModel.factory(roomId)),
    onGroupReady : (groupId: String) -> Unit,
    onLeaveRoom  : () -> Unit
) {
    val ui by vm.ui.collectAsState()

    // When group is ready, mark ceremony done then navigate
    LaunchedEffect(ui) {
        if (ui is WaitUiState.GroupReady) {
            vm.onGroupReadyNavigate((ui as WaitUiState.GroupReady).groupId) {
                onGroupReady((ui as WaitUiState.GroupReady).groupId)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WBg)
            .systemBarsPadding()
    ) {
        when (val state = ui) {
            is WaitUiState.Loading     -> WaitLoading()
            is WaitUiState.Error       -> WaitError(state.msg)
            is WaitUiState.GroupReady  -> WaitGroupReadyAnimation()
            is WaitUiState.Waiting     -> WaitingContent(
                state     = state,
                myUid     = vm.myUid,
                onLeave   = { vm.leaveRoom(onLeaveRoom) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Waiting content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WaitingContent(
    state   : WaitUiState.Waiting,
    myUid   : String,
    onLeave : () -> Unit
) {
    var showLeaveDialog by remember { mutableStateOf(false) }
    // Use raw counts from the doc for instant display; profile list fills in after async fetch
    val total = state.rawMaleCount + state.rawFemaleCount

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // ── Room identity ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(WSurface)
                .border(0.8.dp, WGold.copy(0.35f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) { Text(state.roomEmoji, fontSize = 32.sp) }

        Spacer(Modifier.height(16.dp))

        Text(
            state.roomName,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = WCream, fontWeight = FontWeight.Bold, fontSize = 22.sp
            ),
            textAlign = TextAlign.Center
        )

        if (state.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = WCreamMuted, fontSize = 13.sp, lineHeight = 19.sp
                ),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Progress banner ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(WSurface)
                .border(0.7.dp, WCardBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "$total / 4 people",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = WGold, fontWeight = FontWeight.Bold, fontSize = 28.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Waiting for ${4 - total} more ${if (4 - total == 1) "person" else "people"}…",
                    style = MaterialTheme.typography.bodyMedium.copy(color = WCreamMuted, fontSize = 13.sp)
                )
                Spacer(Modifier.height(14.dp))
                // Big progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(WCardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(total / 4f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(WGoldDim, WGold)))
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Gender slot grid ──────────────────────────────────────────────────
        Text(
            "SPOTS",
            style = MaterialTheme.typography.labelSmall.copy(
                color = WCreamMuted, letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold, fontSize = 10.sp
            )
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Male slots
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { i ->
                    val member     = state.maleMembers.getOrNull(i)
                    val isOccupied = i < state.rawMaleCount   // from raw count — instant
                    SlotCard(member = member, myUid = myUid, gender = "Male",
                        index = i + 1, color = WBlue, isOccupied = isOccupied)
                }
            }
            // Female slots
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { i ->
                    val member     = state.femaleMembers.getOrNull(i)
                    val isOccupied = i < state.rawFemaleCount
                    SlotCard(member = member, myUid = myUid, gender = "Female",
                        index = i + 1, color = WPurple, isOccupied = isOccupied)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Info note ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(0.7.dp, WCardBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("💡", fontSize = 16.sp)
                Text(
                    "The journey starts automatically when all 4 spots — 2 male and 2 female — are filled.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = WCreamMuted, fontSize = 12.sp, lineHeight = 18.sp
                    )
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Leave button ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(0.8.dp, WCardBorder, RoundedCornerShape(14.dp))
                .clickable { showLeaveDialog = true }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Leave room",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = WCreamMuted, fontWeight = FontWeight.Medium, fontSize = 14.sp
                )
            )
        }

        Spacer(Modifier.height(36.dp))
    }

    // ── Leave confirmation dialog ─────────────────────────────────────────────
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor   = WSurface,
            title = {
                Text("Leave room?", style = MaterialTheme.typography.titleMedium.copy(color = WCream, fontWeight = FontWeight.Bold))
            },
            text = {
                Text(
                    "You'll lose your spot. The room will stay open for others.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = WCreamMid, fontSize = 14.sp)
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF8B3A3A))
                        .clickable { showLeaveDialog = false; onLeave() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Leave", style = MaterialTheme.typography.labelLarge.copy(
                        color = WCream, fontWeight = FontWeight.SemiBold))
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.7.dp, WCardBorder, RoundedCornerShape(10.dp))
                        .clickable { showLeaveDialog = false }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Stay", style = MaterialTheme.typography.labelLarge.copy(
                        color = WCreamMuted))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Slot Card — one gender slot (filled or empty)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlotCard(
    member     : WaitMember?,
    myUid      : String,
    gender     : String,
    index      : Int,
    color      : Color,
    isOccupied : Boolean    // true = someone is here (even if profile not loaded yet)
) {
    val isFilled = member != null
    val isMe     = member?.uid == myUid
    val symbol   = if (gender == "Male") "♂" else "♀"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isOccupied) WCard else WBg)
            .border(
                width = if (isMe) 1.5.dp else 0.8.dp,
                color = if (isMe) WGold
                        else if (isFilled) color.copy(0.4f)
                        else if (isOccupied) color.copy(0.25f)
                        else WCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        when {
            isFilled && member != null -> {
                // Profile loaded — show alias + emoji
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(color.copy(0.18f)).border(0.6.dp, color.copy(0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(member.emoji, fontSize = 17.sp) }
                    Column {
                        Text(
                            if (isMe) "${member.alias} (you)" else member.alias,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (isMe) WGold else WCream,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                            maxLines = 1
                        )
                        Text("$symbol Slot $index",
                            style = MaterialTheme.typography.labelSmall.copy(color = color.copy(0.8f), fontSize = 10.sp))
                    }
                }
            }
            isOccupied -> {
                // Someone joined but profile still loading — show placeholder
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(color.copy(0.12f)).border(0.6.dp, color.copy(0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(symbol, style = MaterialTheme.typography.bodyMedium.copy(color = color, fontSize = 14.sp)) }
                    Column {
                        Text(
                            "Joined ✓",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        )
                        Text("$symbol Slot $index",
                            style = MaterialTheme.typography.labelSmall.copy(color = color.copy(0.6f), fontSize = 10.sp))
                    }
                }
            }
            else -> {
                // Truly empty
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(WBg).border(0.7.dp, WCardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(symbol, style = MaterialTheme.typography.bodyMedium.copy(color = WCreamMuted, fontSize = 14.sp)) }
                    Column {
                        Text("Empty",
                            style = MaterialTheme.typography.labelLarge.copy(color = WCreamMuted, fontSize = 12.sp))
                        Text("$symbol Slot $index",
                            style = MaterialTheme.typography.labelSmall.copy(color = WCardBorder, fontSize = 10.sp))
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  States
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WaitLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = WGold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            Text("Joining room…", style = MaterialTheme.typography.bodySmall.copy(color = WCreamMuted))
        }
    }
}

@Composable
private fun WaitError(msg: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyMedium.copy(
            color = WCreamMuted, textAlign = TextAlign.Center))
    }
}

@Composable
private fun WaitGroupReadyAnimation() {
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { pulse = !pulse; delay(700) } }
    val alpha by animateFloatAsState(if (pulse) 1f else 0.5f, tween(600), label = "p")

    Box(
        Modifier.fillMaxSize().background(WBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🌙", fontSize = 52.sp, modifier = Modifier.alpha(alpha))
            Text(
                "Your circle is complete!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = WGold, fontWeight = FontWeight.Bold, fontSize = 22.sp
                )
            )
            Text(
                "Starting your journey…",
                style = MaterialTheme.typography.bodyMedium.copy(color = WCreamMuted, fontSize = 14.sp)
            )
        }
    }
}
