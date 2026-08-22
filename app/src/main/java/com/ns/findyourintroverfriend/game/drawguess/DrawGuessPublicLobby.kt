package com.ns.findyourintroverfriend.game.drawguess

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

private val LBg      = Color(0xFFF7F5F0)
private val LCard    = Color(0xFFFFFFFF)
private val LBorder  = Color(0xFFEDE8DF)
private val LAccent  = Color(0xFF7C6AE8)
private val LSub     = Color(0xFF8A8480)
private val LMuted   = Color(0xFFBBB5AC)
private val LPrimary = Color(0xFF1C1916)
private val LPill    = Color(0xFFF0EBE3)
private val LGreen   = Color(0xFF2E9E6A)
private val LRed     = Color(0xFFD94F4F)
private val LGold    = Color(0xFFE89820)

private val LOBBY_CATEGORIES = listOf(
    "animals" to "🐾", "food" to "🍕", "movies" to "🎬",
    "objects" to "📦", "emotions" to "💭"
)

@Composable
fun DrawGuessPublicLobby(
    vm     : DrawGuessViewModel = viewModel(),
    onBack : () -> Unit,
    onEnterRoom: (roomId: String) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Public rooms listener (local to this screen)
    var publicRooms by remember { mutableStateOf<List<DrawGuessRoom>>(emptyList()) }
    var loadingRooms by remember { mutableStateOf(true) }
    val db = remember { FirebaseFirestore.getInstance() }

    DisposableEffect(Unit) {
        val reg: ListenerRegistration = db.collection("drawGuessRooms")
            .whereEqualTo("inviteType", "public")
            .whereEqualTo("status", "waiting")
            .addSnapshotListener { snap, _ ->
                loadingRooms = false
                publicRooms = snap?.documents?.mapNotNull { d ->
                    @Suppress("UNCHECKED_CAST")
                    val sc = d.get("scores") as? Map<String, Long> ?: emptyMap()
                    DrawGuessRoom(
                        id           = d.id,
                        inviteType   = "public",
                        name         = d.getString("name") ?: "Room",
                        hostUid      = d.getString("hostUid") ?: "",
                        hostAlias    = d.getString("hostAlias") ?: "",
                        guestUid     = d.getString("guestUid") ?: "",
                        hasPassword  = d.getBoolean("hasPassword") ?: false,
                        maxRounds    = (d.getLong("maxRounds") ?: 5).toInt(),
                        category     = d.getString("category") ?: "animals",
                        status       = d.getString("status") ?: "waiting",
                        scores       = sc.mapValues { it.value.toInt() },
                        createdAt    = d.getLong("createdAt") ?: 0L
                    )
                }?.sortedByDescending { it.createdAt } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    LaunchedEffect(Unit) { if (state.myUid.isEmpty()) vm.init("") }

    var showCreate  by remember { mutableStateOf(false) }
    var joinDialog  by remember { mutableStateOf<DrawGuessRoom?>(null) }

    Column(Modifier.fillMaxSize().background(LBg).systemBarsPadding()) {

        // Header
        Row(
            Modifier.fillMaxWidth().background(LCard)
                .border(BorderStroke(0.5.dp, LBorder))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = LPrimary, modifier = Modifier.size(20.dp))
            }
            Text("🎨", fontSize = 22.sp)
            Column(Modifier.weight(1f)) {
                Text("Draw & Guess", fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold, color = LPrimary)
                Text("${publicRooms.size} room${if (publicRooms.size != 1) "s" else ""} open",
                    fontSize = 11.sp, color = LSub)
            }
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(LAccent)
                    .clickable { showCreate = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("Create", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Game info strip
        Row(
            Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(14.dp)).background(LCard)
                .border(1.dp, LBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🎨", fontSize = 28.sp)
            Column(Modifier.weight(1f)) {
                Text("Draw & Guess", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LPrimary)
                Text("One draws, one guesses — 80s per round!", fontSize = 12.sp,
                    color = LSub, lineHeight = 16.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LobbyPill("👥 2 Players")
                LobbyPill("⏱ 5–10 rounds")
            }
        }

        Text("  Open Rooms", fontSize = 11.sp, color = LSub,
            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))

        if (loadingRooms) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = LAccent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        } else if (publicRooms.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🎨", fontSize = 48.sp)
                    Text("No rooms open", fontSize = 17.sp,
                        fontWeight = FontWeight.Bold, color = LPrimary)
                    Text("Create one and wait for a player!", fontSize = 13.sp, color = LSub)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(LAccent.copy(0.12f))
                            .clickable { showCreate = true }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Create Room", fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, color = LAccent)
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(publicRooms, key = { it.id }) { room ->
                    PublicRoomCard(room = room, myUid = state.myUid,
                        onJoin = { joinDialog = room })
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // Create room dialog
    if (showCreate) {
        CreateDrawGuessRoomDialog(
            onDismiss = { showCreate = false },
            onCreate  = { name, pw, rounds, cat ->
                showCreate = false
                vm.createPublicRoom(name, pw, rounds, cat)
                // Room id will be set inside vm, navigate after init completes
            }
        )
    }

    // Join dialog
    joinDialog?.let { room ->
        JoinDrawGuessRoomDialog(
            room      = room,
            onDismiss = { joinDialog = null },
            onJoin    = { pw ->
                joinDialog = null
                vm.joinPublicRoom(room.id, pw,
                    onWrongPw = { /* show snackbar if needed */ })
                onEnterRoom(room.id)
            }
        )
    }

    // Auto-navigate when vm creates a room
    val createdRoom = state.room
    LaunchedEffect(createdRoom?.id) {
        if (createdRoom != null && createdRoom.hostUid == state.myUid) {
            onEnterRoom(createdRoom.id)
        }
    }
}

// ── Room card ────────────────────────────────────────────────────────────────
@Composable
private fun PublicRoomCard(room: DrawGuessRoom, myUid: String, onJoin: () -> Unit) {
    val guestJoined = room.guestUid.isNotBlank()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LCard)
            .border(1.dp, LBorder, RoundedCornerShape(16.dp))
            .alpha(if (guestJoined) 0.55f else 1f)
            .clickable(enabled = !guestJoined, onClick = onJoin)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .background(LAccent.copy(0.1f)), Alignment.Center) {
            Text("🎨", fontSize = 22.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(room.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (room.hasPassword)
                    Icon(Icons.Rounded.Lock, null, tint = LMuted, modifier = Modifier.size(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("by ${room.hostAlias}", fontSize = 11.sp, color = LSub)
                Text("·", fontSize = 11.sp, color = LMuted)
                Text("${room.maxRounds} rounds", fontSize = 11.sp, color = LSub)
                Text("·", fontSize = 11.sp, color = LMuted)
                Text(room.category.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = LSub)
            }
        }
        if (guestJoined) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(LRed.copy(0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Full", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LRed)
            }
        } else {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(LGreen.copy(0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Join", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LGreen)
            }
        }
    }
}

// ── Create dialog ─────────────────────────────────────────────────────────────
@Composable
private fun CreateDrawGuessRoomDialog(
    onDismiss: () -> Unit,
    onCreate : (name: String, pw: String, rounds: Int, category: String) -> Unit
) {
    var name     by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rounds   by remember { mutableIntStateOf(5) }
    var category by remember { mutableStateOf("animals") }
    var showPw   by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(LCard)
                .border(1.dp, LBorder, RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎨", fontSize = 24.sp)
                Text("Create Room", fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold, color = LPrimary)
            }
            // Name
            LInputField("Room Name", name, { name = it }, "My Draw & Guess Room")
            // Password
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LInputField("Password (optional)", password, { password = it },
                    isPassword = !showPw, modifier = Modifier.weight(1f))
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        null, tint = LSub, modifier = Modifier.size(20.dp))
                }
            }
            // Rounds
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ROUNDS", fontSize = 10.sp, color = LSub,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(5, 10).forEach { r ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (rounds == r) LAccent else LPill)
                                .border(1.dp, if (rounds == r) LAccent else LBorder, RoundedCornerShape(12.dp))
                                .clickable { rounds = r }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$r Rounds", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (rounds == r) Color.White else LPrimary)
                        }
                    }
                }
            }
            // Category
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CATEGORY", fontSize = 10.sp, color = LSub,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LOBBY_CATEGORIES.forEach { (id, emoji) ->
                        val sel = category == id
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) LAccent.copy(0.12f) else LPill)
                                .border(1.dp, if (sel) LAccent else LBorder, RoundedCornerShape(10.dp))
                                .clickable { category = id }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(emoji, fontSize = 16.sp)
                            Text(id.take(4), fontSize = 8.sp, color = if (sel) LAccent else LSub,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Create button
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (name.isNotBlank()) LAccent else LPill)
                    .clickable(enabled = name.isNotBlank()) {
                        onCreate(name.trim(), password.trim(), rounds, category)
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Create & Wait 🚀", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (name.isNotBlank()) Color.White else LSub)
            }
        }
    }
}

// ── Join dialog ───────────────────────────────────────────────────────────────
@Composable
private fun JoinDrawGuessRoomDialog(
    room     : DrawGuessRoom,
    onDismiss: () -> Unit,
    onJoin   : (String) -> Unit
) {
    var pw by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(24.dp)).background(LCard)
                .border(1.dp, LBorder, RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎨", fontSize = 24.sp)
                Column {
                    Text(room.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = LPrimary)
                    Text("Host: ${room.hostAlias}  ·  ${room.maxRounds} rounds  ·  ${room.category.replaceFirstChar { it.uppercase() }}",
                        fontSize = 11.sp, color = LSub)
                }
            }
            if (room.hasPassword) LInputField("Password", pw, { pw = it }, isPassword = true)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LAccent)
                    .clickable { onJoin(pw) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Join Room", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun LobbyPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(LPill)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 10.sp, color = LSub, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LInputField(
    label      : String,
    value      : String,
    onValue    : (String) -> Unit,
    placeholder: String = label,
    isPassword : Boolean = false,
    modifier   : Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 10.sp, color = LSub, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LPill)
            .border(1.dp, LBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
                textStyle = TextStyle(fontSize = 14.sp, color = LPrimary),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = LMuted)
                    inner()
                }, modifier = Modifier.weight(1f)
            )
        }
    }
}
