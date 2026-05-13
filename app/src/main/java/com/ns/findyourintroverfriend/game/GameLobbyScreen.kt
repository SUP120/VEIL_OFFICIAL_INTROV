package com.ns.findyourintroverfriend.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// ── App palette ───────────────────────────────────────────────────────────────
private val HBg         = Color(0xFFF7F5F0)
private val HCard       = Color(0xFFFFFFFF)
private val HBorder     = Color(0xFFEDE8DF)
private val HAccent     = Color(0xFF7C6AE8)
private val HAccentSoft = Color(0xFFEFEBFF)
private val HGold       = Color(0xFFE89820)
private val HGoldSoft   = Color(0xFFFFF3DC)
private val HPrimary    = Color(0xFF1C1916)
private val HSub        = Color(0xFF8A8480)
private val HMuted      = Color(0xFFBBB5AC)
private val HGreen      = Color(0xFF2E9E6A)
private val HGreenSoft  = Color(0xFFE6F5EE)
private val HRed        = Color(0xFFD94F4F)
private val HPill       = Color(0xFFF0EBE3)

// ── Game catalogue (add new games here in future) ─────────────────────────────
data class GameDef(
    val id          : String,
    val title       : String,
    val description : String,
    val emoji       : String,
    val players     : String,
    val duration    : String,
    val available   : Boolean = true,
    val accentColor : Color   = Color(0xFF7C6AE8)
)

val GAME_CATALOGUE = listOf(
    GameDef(
        id          = "guess_country",
        title       = "Guess The Country",
        description = "Emoji clues → hint → first to pick wins the round!",
        emoji       = "🌍",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = true,
        accentColor = Color(0xFF7C6AE8)
    ),
    GameDef(
        id          = "movie_quiz",
        title       = "Movie Quiz",
        description = "Guess the movie from emoji scenes — coming soon!",
        emoji       = "🎬",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = false,
        accentColor = Color(0xFFD94F4F)
    ),
    GameDef(
        id          = "music_guess",
        title       = "Guess The Song",
        description = "Emoji lyrics clues — guess faster than your opponent!",
        emoji       = "🎵",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = false,
        accentColor = Color(0xFF2E9E6A)
    ),
    GameDef(
        id          = "flag_game",
        title       = "Flag Frenzy",
        description = "Recognize the flag in seconds — speed matters!",
        emoji       = "🚩",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = false,
        accentColor = Color(0xFFE89820)
    ),
)

// ── Category catalogue (add new categories here in future) ───────────────────
data class CategoryDef(
    val id          : String,
    val title       : String,
    val subtitle    : String,
    val emoji       : String,
    val available   : Boolean = true,
    val accentColor : Color   = Color(0xFF7C6AE8)
)

val CATEGORIES = listOf(
    CategoryDef(
        id          = "games",
        title       = "Games",
        subtitle    = "Quiz games, emoji challenges & more",
        emoji       = "🎮",
        available   = true,
        accentColor = Color(0xFF7C6AE8)
    ),
    CategoryDef(
        id          = "music_room",
        title       = "Music Room",
        subtitle    = "Listen together, share vibes in sync",
        emoji       = "🎵",
        available   = false,
        accentColor = Color(0xFF2E9E6A)
    ),
    CategoryDef(
        id          = "watch_party",
        title       = "Watch Party",
        subtitle    = "Watch videos together in real time",
        emoji       = "📺",
        available   = false,
        accentColor = Color(0xFFD94F4F)
    ),
    CategoryDef(
        id          = "art_room",
        title       = "Art Room",
        subtitle    = "Draw, create and guess together",
        emoji       = "🎨",
        available   = false,
        accentColor = Color(0xFFE89820)
    ),
)

// ── Root composable ───────────────────────────────────────────────────────────
@Composable
fun GameLobbyScreen(vm: GameRoomViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val room  = state.currentRoom

    // Local nav state: which category / game is open
    var selectedCategory by remember { mutableStateOf("") }

    // Room screens take highest priority
    if (room != null) {
        when (room.status) {
            "waiting"  -> GameWaitingRoom(vm, state, room)
            "playing"  -> GameRoomScreen(vm, state, room)
            "finished" -> GameResultScreen(vm, state, room)
        }
        return
    }

    // Game lobby (specific game selected)
    if (state.selectedGameType.isNotEmpty()) {
        val gameDef = GAME_CATALOGUE.find { it.id == state.selectedGameType }
        if (gameDef != null) {
            GameRoomLobby(
                vm      = vm,
                state   = state,
                gameDef = gameDef,
                onBack  = { vm.setSelectedGame("") }
            )
            return
        }
    }

    // Music Room
    if (selectedCategory == "music_room") {
        com.ns.findyourintroverfriend.listening.MusicRoomRoot(
            onBack = { selectedCategory = "" }
        )
        return
    }

    // Games hub (category = games, no specific game yet)
    if (selectedCategory == "games") {
        GameHub(
            onSelectGame = { gameId ->
                vm.setSelectedGame(gameId)
                vm.listenToRooms(gameId)
            },
            onBack = { selectedCategory = "" }
        )
        return
    }

    // Default: Categories screen
    CategoriesScreen(onSelectCategory = { catId -> selectedCategory = catId })
}

// ── Categories Screen ─────────────────────────────────────────────────────────
@Composable
private fun CategoriesScreen(onSelectCategory: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(HBg).systemBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Explore", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = HPrimary)
            Text("Choose a category to get started", fontSize = 12.sp, color = HSub)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(CATEGORIES) { cat ->
                CategoryCard(cat = cat, onClick = { if (cat.available) onSelectCategory(cat.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CategoryCard(cat: CategoryDef, onClick: () -> Unit) {
    // Duolingo-style shine sweep — white diagonal strip gliding left → right
    val shimmerInf = rememberInfiniteTransition(label = "catShine_${cat.id}")
    val shimmerX by shimmerInf.animateFloat(
        initialValue = -500f,
        targetValue  = 1200f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = LinearEasing, delayMillis = if (cat.id == "games") 0 else 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_${cat.id}"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (cat.available) 1f else 0.55f)
            .clip(RoundedCornerShape(20.dp))
            .background(HCard)
            .border(1.dp, HBorder, RoundedCornerShape(20.dp))
            .drawWithContent {
                drawContent()
                if (cat.id == "games" || cat.id == "music_room") {
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x007C6AE8),
                            Color(0x457C6AE8),   // ~27% — clearly visible
                            Color(0x007C6AE8)
                        ),
                        start = Offset(shimmerX, 0f),
                        end   = Offset(shimmerX + 200f, size.height)
                    )
                    drawRect(brush = brush, size = size)
                }
            }
            .clickable(enabled = cat.available, onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                .background(cat.accentColor.copy(0.10f)),
                contentAlignment = Alignment.Center) {
                if (cat.id == "games") {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.games),
                        contentDescription = "Games",
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Text(cat.emoji, fontSize = 26.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cat.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HPrimary)
                    if (!cat.available) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(HPill)
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Soon", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = HSub)
                        }
                    }
                }
                Text(cat.subtitle, fontSize = 12.sp, color = HSub, lineHeight = 16.sp)
            }
            Icon(
                if (cat.available) Icons.Rounded.ChevronRight else Icons.Rounded.Lock,
                null, tint = HMuted, modifier = Modifier.size(if (cat.available) 20.dp else 16.dp)
            )
        }
    }
}

// ── Game Hub — grid of games inside Games category ────────────────────────────
@Composable
private fun GameHub(onSelectGame: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(HBg).systemBarsPadding()) {
        // Header with back
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(HPill)
                .clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, null, tint = HPrimary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Games", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = HPrimary)
                Text("Pick a game to play with a friend", fontSize = 11.sp, color = HSub)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(GAME_CATALOGUE) { game ->
                GameCard(game = game, onClick = { if (game.available) onSelectGame(game.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Game card ─────────────────────────────────────────────────────────────────
@Composable
private fun GameCard(game: GameDef, onClick: () -> Unit) {
    val alpha = if (game.available) 1f else 0.5f
    // Own shimmer per card so it always runs inside LazyColumn
    val inf = rememberInfiniteTransition(label = "gc_${game.id}")
    val shimX by inf.animateFloat(
        -400f, 1200f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        "gcx_${game.id}"
    )
    val scale = remember { Animatable(1f) }
    Box(
        Modifier.fillMaxWidth().alpha(alpha)
            .clip(RoundedCornerShape(20.dp)).background(HCard)
            .border(1.dp, HBorder, RoundedCornerShape(20.dp))
            .drawWithContent {
                drawContent()
                if (game.available) {
                    val br = Brush.linearGradient(
                        listOf(Color(0x007C6AE8), Color(0x407C6AE8), Color(0x007C6AE8)),
                        Offset(shimX, 0f), Offset(shimX + 200f, size.height)
                    )
                    drawRect(brush = br)
                }
            }
            .scale(scale.value)
            .clickable(enabled = game.available) {
                onClick()
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Emoji icon
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
                    .background(game.accentColor.copy(0.10f)),
                contentAlignment = Alignment.Center
            ) { Text(game.emoji, fontSize = 28.sp) }

            // Info
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(game.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HPrimary)
                    if (!game.available) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(HPill)
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Soon", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = HSub, letterSpacing = 0.3.sp)
                        }
                    }
                }
                Text(game.description, fontSize = 12.sp, color = HSub, lineHeight = 16.sp, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GamePill("👥 ${game.players}")
                    GamePill("⏱ ${game.duration}")
                }
            }

            // Chevron / lock
            if (game.available) {
                Icon(Icons.Rounded.ChevronRight, null, tint = HMuted, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Rounded.Lock, null, tint = HMuted, modifier = Modifier.size(16.dp))
            }
        }

        // Live badge for available games
        if (game.available) {
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp))
                    .background(HGold.copy(0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("LIVE", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        color = HGold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun GamePill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(HPill)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 10.sp, color = HSub, fontWeight = FontWeight.Medium)
    }
}

// ── Game Room Lobby (room list for a specific game) ───────────────────────────
@Composable
private fun GameRoomLobby(
    vm      : GameRoomViewModel,
    state   : GameRoomUiState,
    gameDef : GameDef,
    onBack  : () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var joinDialog by remember { mutableStateOf<GameRoom?>(null) }

    Column(Modifier.fillMaxSize().background(HBg).systemBarsPadding()) {

        // Header with back
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(HPill)
                .clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, null, tint = HPrimary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(gameDef.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = HPrimary)
                Text("${state.rooms.size} room${if (state.rooms.size != 1) "s" else ""} open", fontSize = 11.sp, color = HSub)
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(HAccent)
                .clickable { showCreate = true }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("Create", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Game info strip
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp)).background(HCard)
            .border(1.dp, HBorder, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(gameDef.emoji, fontSize = 28.sp)
            Column(Modifier.weight(1f)) {
                Text(gameDef.description, fontSize = 12.sp, color = HSub, lineHeight = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("  Open Rooms", fontSize = 11.sp, color = HSub, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))

        if (state.rooms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(gameDef.emoji, fontSize = 48.sp)
                    Text("No rooms open", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HPrimary)
                    Text("Create one and invite a friend!", fontSize = 13.sp, color = HSub)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(HAccentSoft)
                        .clickable { showCreate = true }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text("Create Room", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HAccent)
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.rooms, key = { it.id }) { room ->
                    RoomCard(room = room, gameDef = gameDef, onJoin = { joinDialog = room })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showCreate) {
        CreateRoomDialog(gameDef = gameDef, onDismiss = { showCreate = false },
            onCreate = { name, pw, rounds ->
                showCreate = false
                vm.createRoom(name, pw, rounds, gameDef.id)
            })
    }
    joinDialog?.let { r ->
        JoinRoomDialog(room = r, joining = state.joining, onDismiss = { joinDialog = null },
            onJoin = { pw -> vm.joinRoom(r.id, pw); joinDialog = null })
    }
}

@Composable
private fun RoomCard(room: GameRoom, gameDef: GameDef, onJoin: () -> Unit) {
    val isClosed = room.status == "playing" || room.status == "finished"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HCard)
            .border(1.dp, if (isClosed) HBorder.copy(0.5f) else HBorder, RoundedCornerShape(16.dp))
            .alpha(if (isClosed) 0.6f else 1f)
            .clickable(enabled = !isClosed, onClick = onJoin).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(HAccentSoft),
            contentAlignment = Alignment.Center) { Text(gameDef.emoji, fontSize = 22.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(room.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("by ${room.hostAlias}", fontSize = 11.sp, color = HSub)
                Text("·", fontSize = 11.sp, color = HMuted)
                Text("${room.maxRounds} rounds", fontSize = 11.sp, color = HSub)
            }
        }
        if (isClosed) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(HRed.copy(0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Closed", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HRed)
            }
        } else {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${room.playerCount}/2", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (room.playerCount >= 2) HGreen else HPrimary)
                Icon(Icons.Rounded.ChevronRight, null, tint = HMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Create Room dialog ────────────────────────────────────────────────────────
@Composable
private fun CreateRoomDialog(gameDef: GameDef, onDismiss: () -> Unit, onCreate: (String, String, Int) -> Unit) {
    var name     by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rounds   by remember { mutableIntStateOf(5) }
    var showPw   by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(HCard)
                .border(1.dp, HBorder, RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(gameDef.emoji, fontSize = 26.sp)
                Text("Create Room", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = HPrimary)
            }
            HInputField("Room Name", name, { name = it }, "My ${gameDef.title} Room")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HInputField("Password (optional)", password, { password = it },
                    isPassword = !showPw, modifier = Modifier.weight(1f))
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        null, tint = HSub, modifier = Modifier.size(20.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ROUNDS", fontSize = 10.sp, color = HSub, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(5, 10).forEach { r ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (rounds == r) HAccent else HPill)
                                .border(1.dp, if (rounds == r) HAccent else HBorder, RoundedCornerShape(12.dp))
                                .clickable { rounds = r }.padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$r Rounds", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (rounds == r) Color.White else HPrimary)
                        }
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (name.isNotBlank()) HAccent else HPill)
                    .clickable(enabled = name.isNotBlank()) { onCreate(name.trim(), password.trim(), rounds) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Create & Wait 🚀", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = if (name.isNotBlank()) Color.White else HSub) }
        }
    }
}

// ── Join Room dialog ──────────────────────────────────────────────────────────
@Composable
private fun JoinRoomDialog(room: GameRoom, joining: Boolean, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(24.dp)).background(HCard)
                .border(1.dp, HBorder, RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(room.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = HPrimary)
            Text("Host: ${room.hostAlias}  ·  ${room.maxRounds} rounds", fontSize = 12.sp, color = HSub)
            if (room.hasPassword) HInputField("Password", pw, { pw = it }, isPassword = true)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(HAccent)
                    .clickable(enabled = !joining) { onJoin(pw) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (joining) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Join Room", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ── Input field ───────────────────────────────────────────────────────────────
@Composable
private fun HInputField(
    label      : String,
    value      : String,
    onValue    : (String) -> Unit,
    placeholder: String = label,
    isPassword : Boolean = false,
    modifier   : Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 10.sp, color = HSub, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HPill)
            .border(1.dp, HBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
                textStyle = TextStyle(fontSize = 14.sp, color = HPrimary),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = HMuted)
                    inner()
                }, modifier = Modifier.weight(1f)
            )
        }
    }
}
