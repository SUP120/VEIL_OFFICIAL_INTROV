package com.ns.findyourintroverfriend.lobby

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
//  Palette — Tan · Cream · Charcoal · Gold
//  Tan: #B68D40   Cream: #F4EBD0   Charcoal: #122620   Gold: #D6AD60
// ─────────────────────────────────────────────────────────────────────────────

private val CBg          = Color(0xFF122620)   // Charcoal — deep dark green canvas
private val CSurface     = Color(0xFF1A3028)   // slightly lighter charcoal surface
private val CCard        = Color(0xFF1E362C)   // card surface
private val CCardBorder  = Color(0xFF274035)   // card border (subtle)
private val CGold        = Color(0xFFD6AD60)   // Gold — primary accent
private val CGoldDim     = Color(0xFFB68D40)   // Tan — secondary accent
private val CCream       = Color(0xFFF4EBD0)   // Cream — primary text
private val CCreamMid    = Color(0xFFD4C9AB)   // mid cream — secondary text
private val CCreamMuted  = Color(0xFF8C836A)   // muted cream — hint text
private val CGoldTint    = Color(0xFF1F2D20)   // very dark gold tint for chips

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

data class LobbyRoom(
    val id            : String = "",
    val name          : String = "",
    val description   : String = "",
    val emoji         : String = "🌙",
    val memberCount   : Int    = 0,
    val maxMembers    : Int    = 4,
    val maleCount     : Int    = 0,   // current males (max 2)
    val femaleCount   : Int    = 0,   // current females (max 2)
    val theme         : String = "open",
    val createdByName : String = "Anonymous",
    val createdAt     : Long   = 0L
)

sealed class LobbyUiState {
    object Loading : LobbyUiState()
    data class Ready(
        val rooms     : List<LobbyRoom> = emptyList(),
        val userAlias : String          = "You",
        val userEmoji : String          = "🌙",
        val myGender  : String          = "",    // "Male" | "Female" | other
        val errorMsg  : String?         = null
    ) : LobbyUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class LobbyViewModel : ViewModel() {

    private val tag   = "LobbyVM"
    private val auth  = FirebaseAuth.getInstance()
    private val db    = FirebaseFirestore.getInstance()
    private val myUid get() = auth.currentUser?.uid ?: ""

    private val _ui = MutableStateFlow<LobbyUiState>(LobbyUiState.Loading)
    val ui: StateFlow<LobbyUiState> = _ui.asStateFlow()

    private var roomsListener: ListenerRegistration? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(myUid).get().await()
                val alias   = userDoc.getString("alias") ?: "You"
                val emoji   = userDoc.getString("emoji") ?: "🌙"
                _ui.value = LobbyUiState.Ready(userAlias = alias, userEmoji = emoji)
            } catch (e: Exception) {
                Log.e(tag, "load user failed: ${e.message}")
                _ui.value = LobbyUiState.Ready()
            }
            listenToRooms()
        }
    }

    private fun listenToRooms() {
        // ⚠️ NO .orderBy() — compound (whereEq + orderBy) requires a composite Firestore index.
        //    Without the index it silently returns 0 results for other users.
        //    We sort client-side instead.
        roomsListener = db.collection("lobby_rooms")
            .whereEqualTo("status", "waiting")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(tag, "rooms listen error: ${err.message}")
                    return@addSnapshotListener
                }
                val rooms = snap?.documents
                    ?.mapNotNull { doc ->
                        val count = (doc.getLong("memberCount") ?: 0L).toInt()
                        val max   = (doc.getLong("maxMembers")  ?: 4L).toInt()
                        if (count >= max) return@mapNotNull null   // hide full rooms
                        LobbyRoom(
                            id            = doc.id,
                            name          = doc.getString("name")          ?: "Untitled room",
                            description   = doc.getString("description")   ?: "",
                            emoji         = doc.getString("emoji")         ?: "🌙",
                            memberCount   = count,
                            maxMembers    = max,
                            theme         = doc.getString("theme")         ?: "open",
                            createdByName = doc.getString("createdByName") ?: "Anonymous",
                            createdAt     = doc.getTimestamp("createdAt")?.seconds ?: 0L
                        )
                    }
                    ?.sortedByDescending { it.createdAt }   // newest first, client-side
                    ?: emptyList()

                Log.d(tag, "rooms updated: ${rooms.size}")
                val cur = _ui.value
                if (cur is LobbyUiState.Ready) _ui.value = cur.copy(rooms = rooms)
            }
    }

    fun createRoom(
        name        : String,
        description : String,
        emoji       : String,
        theme       : String,
        onDone      : (roomId: String) -> Unit,
        onError     : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val userDoc     = db.collection("users").document(myUid).get().await()
                val creatorName = userDoc.getString("alias") ?: "Anonymous"
                val ref = db.collection("lobby_rooms").add(
                    mapOf(
                        "name"          to name.trim(),
                        "description"   to description.trim(),
                        "emoji"         to emoji,
                        "theme"         to theme,
                        "memberCount"   to 1,
                        "maxMembers"    to 4,
                        "status"        to "waiting",
                        "isPrivate"     to false,
                        "createdBy"     to myUid,
                        "createdByName" to creatorName,
                        "members"       to listOf(myUid),
                        "createdAt"     to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
                db.collection("users").document(myUid).update("lobbyRoomId", ref.id).await()
                onDone(ref.id)
            } catch (e: Exception) {
                Log.e(tag, "createRoom failed: ${e.message}")
                onError("Could not create room. Please try again.")
            }
        }
    }

    fun joinRoom(roomId: String, onDone: (roomId: String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val roomRef = db.collection("lobby_rooms").document(roomId)
                db.runTransaction { tx ->
                    val snap    = tx.get(roomRef)
                    val count   = (snap.getLong("memberCount") ?: 0L).toInt()
                    val max     = (snap.getLong("maxMembers")  ?: 4L).toInt()
                    if (count >= max) throw Exception("Room is full")
                    val members = (snap.get("members") as? List<*> ?: emptyList<Any>()).toMutableList()
                    if (!members.contains(myUid)) {
                        members.add(myUid)
                        tx.update(roomRef, "members", members)
                        tx.update(roomRef, "memberCount", count + 1)
                    }
                }.await()
                db.collection("users").document(myUid).update("lobbyRoomId", roomId).await()
                onDone(roomId)
            } catch (e: Exception) {
                Log.e(tag, "joinRoom failed: ${e.message}")
                onError(e.message ?: "Could not join room.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        roomsListener?.remove()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Root Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RoomLobbyScreen(
    vm          : LobbyViewModel = viewModel(),
    onEnterRoom : (roomId: String) -> Unit
) {
    val ui      by vm.ui.collectAsState()
    var showRoomsPanel by remember { mutableStateOf(false) }
    var showCreate     by remember { mutableStateOf(false) }

    val ready = ui as? LobbyUiState.Ready

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CBg)
            .systemBarsPadding()
    ) {
        when (ui) {
            is LobbyUiState.Loading -> LobbyLoading()
            is LobbyUiState.Ready   -> {
                HomeContent(
                    alias      = ready?.userAlias ?: "",
                    emoji      = ready?.userEmoji ?: "🌙",
                    onOpenRooms = { showRoomsPanel = true }
                )
            }
        }

        AnimatedVisibility(
            visible = showRoomsPanel,
            enter   = fadeIn(tween(220)) + slideInVertically(tween(340)) { it },
            exit    = fadeOut(tween(180)) + slideOutVertically(tween(280)) { it }
        ) {
            RoomsPanel(
                rooms      = ready?.rooms ?: emptyList(),
                myGender   = ready?.myGender ?: "",
                onJoin     = { room ->
                    vm.joinRoom(room.id, onEnterRoom) { /* error - user stays on panel */ }
                },
                onCreate   = { showCreate = true },
                onDismiss  = { showRoomsPanel = false }
            )
        }

        // ── Create Room Sheet ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showCreate,
            enter   = fadeIn(tween(220)) + slideInVertically(tween(340)) { it },
            exit    = fadeOut(tween(180)) + slideOutVertically(tween(280)) { it }
        ) {
            CreateRoomSheet(
                onDismiss = { showCreate = false },
                onCreate  = { name, desc, emoji, theme ->
                    vm.createRoom(name, desc, emoji, theme,
                        onDone  = { roomId -> showCreate = false; showRoomsPanel = false; onEnterRoom(roomId) },
                        onError = { }
                    )
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Home Content — clean, minimal, professional
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(alias: String, emoji: String, onOpenRooms: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(700), label = "ha")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(32.dp))

        // ── App wordmark ──────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(CGoldTint)
                    .border(0.8.dp, CGold.copy(0.35f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) { Text("🌿", fontSize = 17.sp) }
            Text(
                "Veil",
                style = MaterialTheme.typography.titleLarge.copy(
                    color      = CGold,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 20.sp,
                    letterSpacing = 0.3.sp
                )
            )
        }

        Spacer(Modifier.height(36.dp))

        // ── Greeting ──────────────────────────────────────────────────────────
        Text(
            "Good ${timeGreeting()}",
            style = MaterialTheme.typography.bodyMedium.copy(
                color    = CCreamMuted,
                fontSize = 14.sp
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "$alias $emoji",
            style = MaterialTheme.typography.headlineMedium.copy(
                color      = CCream,
                fontWeight = FontWeight.Bold,
                fontSize   = 28.sp
            )
        )

        Spacer(Modifier.height(32.dp))

        // ── Hero card ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1D3D2F), Color(0xFF162B22))
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.linearGradient(listOf(CGold.copy(0.4f), CGoldDim.copy(0.2f))),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Column {
                Text(
                    "Your circle is waiting.",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color      = CCream,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp,
                        lineHeight = 30.sp
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "A private 3-day journey with 4 people who\nchose depth over noise.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color      = CCreamMid,
                        fontSize   = 13.sp,
                        lineHeight = 20.sp
                    )
                )
                Spacer(Modifier.height(20.dp))
                // Mini stat row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatPill("3", "days")
                    StatPill("4", "people")
                    StatPill("🔒", "private")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Rooms button — the main CTA ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CGold)
                .clickable { onOpenRooms() }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🏠", fontSize = 18.sp)
                Text(
                    "Browse Rooms",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color      = CBg,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        letterSpacing = 0.2.sp
                    )
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Journey steps ─────────────────────────────────────────────────────
        Text(
            "HOW IT WORKS",
            style = MaterialTheme.typography.labelSmall.copy(
                color         = CCreamMuted,
                letterSpacing = 2.sp,
                fontWeight    = FontWeight.SemiBold,
                fontSize      = 10.sp
            )
        )
        Spacer(Modifier.height(12.dp))

        JOURNEY_STEPS.forEachIndexed { i, step ->
            JourneyStepRow(number = i + 1, emoji = step.first, text = step.second)
            if (i < JOURNEY_STEPS.lastIndex) {
                Box(modifier = Modifier.padding(start = 19.dp)) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(CCardBorder)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Safety footnote ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(0.7.dp, CCardBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("🔒", fontSize = 18.sp)
                Column {
                    Text(
                        "End-to-end encrypted",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = CGold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                        )
                    )
                    Text(
                        "Everything is private. You control who sees what.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = CCreamMuted, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CGold.copy(0.14f))
            .border(0.6.dp, CGold.copy(0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.labelLarge.copy(color = CGold, fontWeight = FontWeight.Bold, fontSize = 13.sp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = CGold.copy(0.7f), fontSize = 10.sp))
        }
    }
}

private val JOURNEY_STEPS = listOf(
    "🏠" to "Join or create a room",
    "👥" to "Four people gather over time",
    "📝" to "Answer one prompt each day",
    "🌙" to "Day 3 — the island opens",
    "🤝" to "Connect in your private space"
)

@Composable
private fun JourneyStepRow(number: Int, emoji: String, text: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CCard)
                .border(0.7.dp, CCardBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 16.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = CCreamMid, fontSize = 13.sp
                )
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(CGoldTint)
                .border(0.5.dp, CGold.copy(0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = CGold, fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rooms Panel — full-screen overlay with room list + create
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomsPanel(
    rooms     : List<LobbyRoom>,
    myGender  : String,
    onJoin    : (LobbyRoom) -> Unit,
    onCreate  : () -> Unit,
    onDismiss : () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CSurface)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .navigationBarsPadding()
        ) {
            // Handle
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(CCardBorder)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(18.dp))

            // Header
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Open Rooms",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = CCream, fontWeight = FontWeight.Bold, fontSize = 20.sp
                        )
                    )
                    if (rooms.isNotEmpty()) {
                        Text(
                            "${rooms.size} ${if (rooms.size == 1) "room" else "rooms"} available",
                            style = MaterialTheme.typography.bodySmall.copy(color = CCreamMuted, fontSize = 12.sp)
                        )
                    }
                }
                // Create button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CGold)
                        .clickable { onCreate() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "+ Create",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = CBg, fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(CCardBorder))
            Spacer(Modifier.height(16.dp))

            if (rooms.isEmpty()) {
                EmptyRoomsState(onCreate)
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rooms, key = { it.id }) { room ->
                        RoomCard(room = room, myGender = myGender, onJoin = { onJoin(room) })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Room Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomCard(room: LobbyRoom, myGender: String, onJoin: () -> Unit) {
    val accentColor = when (room.theme) {
        "focused" -> CGoldDim
        "night"   -> Color(0xFF5B8A7A)
        else      -> CGold
    }
    val isMale          = myGender == "Male"
    val myGenderFull    = if (isMale) room.maleCount >= 2 else room.femaleCount >= 2
    val fullMsg         = if (isMale) "All male spots taken" else "All female spots taken"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CCard)
            .border(0.7.dp, if (myGenderFull) CCardBorder else accentColor.copy(0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            // ── Top row ─────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
                        .background(CBg).border(0.6.dp, accentColor.copy(0.3f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(room.emoji, fontSize = 22.sp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(room.name, style = MaterialTheme.typography.titleSmall.copy(
                        color = if (myGenderFull) CCreamMuted else CCream,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text("by ${room.createdByName}",
                        style = MaterialTheme.typography.labelSmall.copy(color = CCreamMuted, fontSize = 11.sp))
                }
            }

            if (room.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(room.description, style = MaterialTheme.typography.bodySmall.copy(
                    color = CCreamMid, fontSize = 13.sp, lineHeight = 19.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(12.dp))

            // ── Gender slot indicators ───────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GenderSlots(symbol = "♂", filled = room.maleCount,   total = 2, color = Color(0xFF6BA4E0))
                GenderSlots(symbol = "♀", filled = room.femaleCount, total = 2, color = Color(0xFFB07AE0))
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(CCardBorder))
            Spacer(Modifier.height(12.dp))

            // ── Bottom row ─────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically) {
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, accentColor.copy(0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(room.theme.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accentColor, fontWeight = FontWeight.Medium, fontSize = 10.sp))
                }
                if (myGenderFull) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(CCardBorder).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(fullMsg, style = MaterialTheme.typography.labelLarge.copy(
                            color = CCreamMuted, fontSize = 12.sp))
                    }
                } else {
                    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(accentColor).clickable { onJoin() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Join →", style = MaterialTheme.typography.labelLarge.copy(
                            color = CBg, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GenderSlots(symbol: String, filled: Int, total: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, style = MaterialTheme.typography.labelSmall.copy(color = color, fontSize = 11.sp))
        repeat(total) { i ->
            Box(
                modifier = Modifier.size(width = 22.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i < filled) color else CCardBorder)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyRoomsState(onCreate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CCard)
                    .border(0.7.dp, CCardBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) { Text("🌙", fontSize = 30.sp) }

            Text(
                "No open rooms yet",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = CCream, fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                "Be the first to open a space.\nOthers will find you.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = CCreamMuted, fontSize = 13.sp, lineHeight = 19.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CGold)
                    .clickable { onCreate() }
                    .padding(horizontal = 28.dp, vertical = 13.dp)
            ) {
                Text(
                    "Create a room",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = CBg, fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Create Room Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CreateRoomSheet(
    onDismiss : () -> Unit,
    onCreate  : (name: String, desc: String, emoji: String, theme: String) -> Unit
) {
    var name          by remember { mutableStateOf("") }
    var description   by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🌙") }
    var selectedTheme by remember { mutableStateOf("open") }
    var creating      by remember { mutableStateOf(false) }

    val emojis = listOf("🌙", "🌿", "🌊", "🔮", "🦋", "🍃", "✨", "🎭", "🌺", "⭐")
    val themes  = listOf("open" to "Open", "focused" to "Focused", "night" to "Night")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.6f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CSurface)
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(listOf(CGold.copy(0.3f), Color.Transparent)),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .navigationBarsPadding()
        ) {
            // Handle
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(CCardBorder).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))

            Text(
                "Create your room",
                style = MaterialTheme.typography.titleLarge.copy(color = CCream, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            )
            Text(
                "Give it a name that tells people what to expect.",
                style = MaterialTheme.typography.bodySmall.copy(color = CCreamMuted, fontSize = 13.sp)
            )
            Spacer(Modifier.height(22.dp))

            // Room name
            LabelText("Room name")
            Spacer(Modifier.height(7.dp))
            DarkTextField(
                value         = name,
                onValueChange = { if (it.length <= 40) name = it },
                placeholder   = "e.g. Sunday night thinkers",
                singleLine    = true
            )

            Spacer(Modifier.height(14.dp))

            // Vibe description
            LabelText("A short vibe (optional)")
            Spacer(Modifier.height(7.dp))
            DarkTextField(
                value         = description,
                onValueChange = { if (it.length <= 80) description = it },
                placeholder   = "What kind of people are you looking for?",
                singleLine    = false
            )

            Spacer(Modifier.height(16.dp))

            // Emoji row
            LabelText("Room emoji")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emojis) { e ->
                    val sel = e == selectedEmoji
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (sel) CGold.copy(0.18f) else CCard)
                            .border(if (sel) 1.5.dp else 0.6.dp, if (sel) CGold else CCardBorder, CircleShape)
                            .clickable { selectedEmoji = e },
                        contentAlignment = Alignment.Center
                    ) { Text(e, fontSize = 20.sp) }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Atmosphere
            LabelText("Atmosphere")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { (key, label) ->
                    val sel    = key == selectedTheme
                    val accent = when (key) { "focused" -> CGoldDim; "night" -> Color(0xFF5B8A7A); else -> CGold }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) accent.copy(0.15f) else CCard)
                            .border(if (sel) 1.2.dp else 0.6.dp, if (sel) accent else CCardBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedTheme = key }
                            .padding(horizontal = 18.dp, vertical = 11.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge.copy(color = if (sel) accent else CCreamMuted, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Create button
            val ready = name.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (ready) CGold else CCard)
                    .border(0.6.dp, if (ready) Color.Transparent else CCardBorder, RoundedCornerShape(14.dp))
                    .clickable(enabled = ready && !creating) {
                        creating = true
                        onCreate(name, description, selectedEmoji, selectedTheme)
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (creating) "Creating…" else "Create room",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color      = if (ready) CBg else CCreamMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun LabelText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(
            color = CCreamMid, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
        )
    )
}

@Composable
private fun DarkTextField(
    value         : String,
    onValueChange : (String) -> Unit,
    placeholder   : String,
    singleLine    : Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(CCard)
            .border(0.7.dp, CCardBorder, RoundedCornerShape(11.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        BasicTextField(
            value         = value,
            onValueChange = onValueChange,
            modifier      = Modifier.fillMaxWidth(),
            textStyle     = MaterialTheme.typography.bodyMedium.copy(color = CCream, fontSize = 14.sp),
            cursorBrush   = SolidColor(CGold),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = CCreamMuted, fontSize = 14.sp))
                inner()
            },
            singleLine      = singleLine,
            maxLines        = if (singleLine) 1 else 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LobbyLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = CGold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            Text("Loading…", style = MaterialTheme.typography.bodySmall.copy(color = CCreamMuted))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun timeGreeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when { h < 12 -> "morning"; h < 17 -> "afternoon"; h < 21 -> "evening"; else -> "night" }
}
