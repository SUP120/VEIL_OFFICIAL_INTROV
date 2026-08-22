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
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.util.VeilThemeManager
import com.ns.findyourintroverfriend.voiceroom.VoiceRoomViewModel

// -- Dark-mode state ---------------------------------------------------------
private var _gameDark by mutableStateOf(VeilThemeManager.initialResolvedDark)

// ── Design tokens (theme-aware) ────────────────────────────────────────────────
// Peaceful Serenity — matching FeedScreen & SwipeScreen palette
private val HBg         get() = if (_gameDark) Color(0xFF0F0F14) else Color(0xFFFBF8F4)   // warm parchment
private val HCard       get() = if (_gameDark) Color(0xFF17171E) else Color(0xFFFFFFFF)   // card surface
private val HBorder     get() = if (_gameDark) Color(0xFF2C2C34) else Color(0xFFE8E3DC)   // soft border
private val HAccent            = Color(0xFFA07C8C)   // dusty mauve
private val HAccentSoft get() = if (_gameDark) Color(0xFF251520) else Color(0xFFF7EFF3)   // soft mauve tint
private val HGold              = Color(0xFFBFA882)   // warm sand
private val HGoldSoft   get() = if (_gameDark) Color(0xFF1A1610) else Color(0xFFF7F3EC)
private val HPrimary    get() = if (_gameDark) Color(0xFFE0DDD8) else Color(0xFF2C2825)   // warm near-black
private val HSub        get() = if (_gameDark) Color(0xFF918E8A) else Color(0xFF7A7470)   // warm stone
private val HMuted      get() = if (_gameDark) Color(0xFF5A5855) else Color(0xFFC4BFBA)   // muted warm
// Sage green for Whisper (voice/live)
private val HGreen             = Color(0xFF7A9E87)   // sage green
private val HGreenSoft  get() = if (_gameDark) Color(0xFF0E1E13) else Color(0xFFEFF5F1)
// Dusty blue for Groups (text/chat)
private val HBlue              = Color(0xFF7B9AC4)   // dusty blue
private val HBlueSoft   get() = if (_gameDark) Color(0xFF101825) else Color(0xFFECF1F8)
private val HRed               = Color(0xFFBF6B6B)   // muted terracotta
private val HPill       get() = if (_gameDark) Color(0xFF1C1C23) else Color(0xFFF4F0EB)

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
        emoji       = "guess_country",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = true,
        accentColor = Color(0xFFA07C8C)   // dusty mauve
    ),
    GameDef(
        id          = "draw_guess",
        title       = "Draw & Guess",
        description = "One draws, one guesses — race the clock in real time!",
        emoji       = "draw_guess",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = true,
        accentColor = Color(0xFF7B9AC4)   // dusty blue
    ),
    GameDef(
        id          = "movie_quiz",
        title       = "Movie Quiz",
        description = "Guess the movie from scenes — coming soon!",
        emoji       = "movie_quiz",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = false,
        accentColor = Color(0xFFBF6B6B)   // muted terracotta
    ),
    GameDef(
        id          = "music_guess",
        title       = "Guess The Song",
        description = "Lyric clues — guess faster than your opponent!",
        emoji       = "music_guess",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = false,
        accentColor = Color(0xFF7A9E87)   // sage green
    ),
    GameDef(
        id          = "flag_game",
        title       = "Flag Frenzy",
        description = "Recognize the flag in seconds — speed matters!",
        emoji       = "flag_game",
        players     = "2 Players",
        duration    = "5–10 rounds",
        available   = false,
        accentColor = Color(0xFFBFA882)   // warm sand
    ),
)

// ── Category catalogue (add new categories here in future) ───────────────────
data class CategoryDef(
    val id          : String,
    val title       : String,
    val subtitle    : String,
    val emoji       : String,
    val available   : Boolean = true,
    val isNew       : Boolean = false,
    val accentColor : Color   = Color(0xFF7C6AE8)
)

val CATEGORIES = listOf(
    CategoryDef(
        id          = "whisper",
        title       = "Whisper",
        subtitle    = "Live voice rooms for introverts",
        emoji       = "🪧",
        available   = true,
        isNew       = true,
        accentColor = Color(0xFF0ABFB8)
    ),
    CategoryDef(
        id          = "groups",
        title       = "Groups",
        subtitle    = "Open chat rooms for everyone",
        emoji       = "💬",
        available   = true,
        isNew       = true,
        accentColor = Color(0xFF4A8FBF)
    ),
    CategoryDef(
        id          = "games",
        title       = "Games",
        subtitle    = "Quiz games, emoji challenges & more",
        emoji       = "🎮",
        available   = false,
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
fun GameLobbyScreen(
    vm                   : GameRoomViewModel = viewModel(),
    onNavigateDrawGuess  : () -> Unit = {},
    onOpenVoiceRooms     : () -> Unit = {},
    onOpenGroups         : () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val room  = state.currentRoom

    // Sync theme
    val _veilIsDark = LocalVeilIsDark.current
    SideEffect { _gameDark = _veilIsDark }

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
            // Draw & Guess uses its own nav flow — route to public lobby
            if (gameDef.id == "draw_guess") {
                vm.setSelectedGame("")
                onNavigateDrawGuess()
                return
            }
            GameRoomLobby(
                vm      = vm,
                state   = state,
                gameDef = gameDef,
                onBack  = { vm.setSelectedGame("") }
            )
            return
        }
    }

    // Whisper — navigate directly to voice room lobby
    if (selectedCategory == "whisper") {
        selectedCategory = ""
        onOpenVoiceRooms()
        return
    }

    // Groups — navigate to group rooms list
    if (selectedCategory == "groups") {
        selectedCategory = ""
        onOpenGroups()
        return
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
                if (gameId == "draw_guess") {
                    onNavigateDrawGuess()
                } else {
                    vm.setSelectedGame(gameId)
                    vm.listenToRooms(gameId)
                }
            },
            onBack = { selectedCategory = "" }
        )
        return
    }

    // Default: Categories screen
    CategoriesScreen(
        onSelectCategory = { catId -> selectedCategory = catId },
        onOpenVoiceRooms = onOpenVoiceRooms,
    )
}

// ── Explore (Categories) Screen ───────────────────────────────────────────────
@Composable
private fun CategoriesScreen(
    onSelectCategory : (String) -> Unit,
    onOpenVoiceRooms : () -> Unit = {},
) {
    val dark = _gameDark

    Column(
        Modifier
            .fillMaxSize()
            .background(HBg)
            .systemBarsPadding()
    ) {

        // ── Calm gradient header ───────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (dark) Color(0xFF17121A) else Color(0xFFF7EFF3),
                            HBg
                        )
                    )
                )
                .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Tiny accent dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background(HAccent.copy(0.6f))
                    )
                    Text(
                        "EXPLORE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = HAccent.copy(0.7f),
                        letterSpacing = 1.8.sp
                    )
                }
                Text(
                    "Your quiet spaces",
                    fontSize      = 26.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = HPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "Rooms built for people who prefer depth over noise",
                    fontSize = 13.sp,
                    color    = HSub,
                    lineHeight = 18.sp
                )
            }
        }

        // ── Main cards ───────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section label
            Text(
                "LIVE SPACES",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = HMuted,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )

            WhisperCard(
                dark    = dark,
                onClick = { onSelectCategory("whisper") }
            )

            GroupsCard(
                dark    = dark,
                onClick = { onSelectCategory("groups") }
            )

            Spacer(Modifier.height(8.dp))

            // ── Coming soon footer strip ───────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.weight(1f).height(0.5.dp).background(HBorder))
                Text(
                    "MORE COMING SOON",
                    fontSize = 8.sp,
                    color = HMuted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                Box(Modifier.weight(1f).height(0.5.dp).background(HBorder))
            }

            // Teaser chips row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Games" to Icons.Rounded.SportsEsports,
                    "Music Room" to Icons.Rounded.MusicNote,
                    "Art Room" to Icons.Rounded.Brush
                ).forEach { (label, icon) ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(HPill)
                            .border(0.5.dp, HBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(icon, null, tint = HMuted, modifier = Modifier.size(12.dp))
                        Text(label, fontSize = 11.sp, color = HMuted, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
 // ── Whisper hero card ─────────────────────────────────────────────────────
@Composable
private fun WhisperCard(
    dark    : Boolean,
    onClick : () -> Unit
) {
    val voiceVm: VoiceRoomViewModel = viewModel()
    val lobbyState by voiceVm.lobbyState.collectAsStateWithLifecycle()
    val liveRooms    = lobbyState.rooms
    val totalPeople  = liveRooms.sumOf { it.participantCount }
    val isLoading    = lobbyState.isLoading

    // Animated live dot
    val pulse = rememberInfiniteTransition(label = "wpulse")
    val dotAlpha by pulse.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        "wda"
    )

    val liveText = when {
        isLoading           -> "Checking activity…"
        liveRooms.isEmpty() -> "No rooms live · Be the first"
        liveRooms.size == 1 -> "1 room live · $totalPeople listening"
        else                -> "${liveRooms.size} rooms live · $totalPeople listening"
    }
    val hasLive = liveRooms.isNotEmpty()

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = HGreen.copy(0.10f))
            .clip(RoundedCornerShape(20.dp))
            .background(HCard)
            .border(1.dp, if (hasLive) HGreen.copy(0.30f) else HBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // Subtle sage tint wash when rooms are live
        if (hasLive) {
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                HGreenSoft.copy(if (dark) 0.6f else 0.5f),
                                HCard
                            )
                        )
                    )
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon circle
            Box(
                Modifier
                    .size(52.dp)
                    .shadow(3.dp, CircleShape, spotColor = HGreen.copy(0.15f))
                    .clip(CircleShape)
                    .background(HGreenSoft)
                    .border(1.dp, HGreen.copy(0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Use drawable if available, otherwise fallback to mic icon
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        com.ns.findyourintroverfriend.R.drawable.whispericon
                    ),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Content
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Whisper",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = HPrimary
                    )
                    if (hasLive) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(HGreen.copy(0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "LIVE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = HGreen,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
                Text(
                    "Live voice rooms · listen & speak quietly",
                    fontSize = 12.sp,
                    color    = HSub
                )
                // Live status row
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier.size(5.dp).clip(CircleShape)
                            .background(
                                if (hasLive) HGreen.copy(alpha = dotAlpha)
                                else HMuted
                            )
                    )
                    Text(
                        liveText,
                        fontSize = 11.sp,
                        color    = if (hasLive) HGreen.copy(0.85f) else HMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint     = HMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
// ── Groups hero card ──────────────────────────────────────────────────────────
@Composable
private fun GroupsCard(
    dark    : Boolean,
    onClick : () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = HBlue.copy(0.10f))
            .clip(RoundedCornerShape(20.dp))
            .background(HCard)
            .border(1.dp, HBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // Subtle dusty blue tint wash
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            HBlueSoft.copy(if (dark) 0.5f else 0.4f),
                            HCard
                        )
                    )
                )
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon circle
            Box(
                Modifier
                    .size(52.dp)
                    .shadow(3.dp, CircleShape, spotColor = HBlue.copy(0.12f))
                    .clip(CircleShape)
                    .background(HBlueSoft)
                    .border(1.dp, HBlue.copy(0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = HBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Content
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Groups",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = HPrimary
                )
                Text(
                    "Open chat rooms · create or join anytime",
                    fontSize = 12.sp,
                    color    = HSub
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier.size(5.dp).clip(CircleShape)
                            .background(HBlue.copy(0.5f))
                    )
                    Text(
                        "Public · anyone can join",
                        fontSize = 11.sp,
                        color    = HMuted
                    )
                }
            }

            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint     = HMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


// ── Coming-soon tile (half-width) ─────────────────────────────────────────────
@Composable
private fun SoonTile(
    cat     : CategoryDef,
    dark    : Boolean,
    surface : Color,
    border  : Color,
    sub     : Color,
    muted   : Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color(0xFF151517) else Color(0xFFFAFAFA))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Icon + Soon pill row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (dark) Color(0xFF222228) else Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                when (cat.id) {
                    "games" -> androidx.compose.foundation.Image(
                        painter            = androidx.compose.ui.res.painterResource(
                            com.ns.findyourintroverfriend.R.drawable.games
                        ),
                        contentDescription = cat.title,
                        modifier           = Modifier.size(20.dp)
                    )
                    else -> Text(cat.emoji, fontSize = 18.sp)
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (dark) Color(0xFF222228) else Color(0xFFEEEEEE))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "Soon",
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = muted
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                cat.title,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = sub
            )
            Text(
                cat.subtitle,
                fontSize  = 10.sp,
                color     = muted,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                lineHeight = 14.sp
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
                        listOf(Color(0x00A07C8C), Color(0x25A07C8C), Color(0x00A07C8C)),
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
            // Icon box — icon-based, no emoji
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
                    .background(game.accentColor.copy(0.12f))
                    .border(1.dp, game.accentColor.copy(0.20f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (game.id) {
                        "draw_guess"   -> Icons.Rounded.Brush
                        "music_guess"  -> Icons.Rounded.MusicNote
                        "movie_quiz"   -> Icons.Rounded.Movie
                        "flag_game"    -> Icons.Rounded.Flag
                        else           -> Icons.Rounded.SportsEsports
                    },
                    contentDescription = null,
                    tint     = game.accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamePill(Icons.Rounded.Group, game.players)
                    GamePill(Icons.Rounded.Schedule, game.duration)
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
private fun GamePill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(HPill)
            .border(0.5.dp, HBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = HMuted, modifier = Modifier.size(10.dp))
        Text(label, fontSize = 10.sp, color = HSub, fontWeight = FontWeight.Medium)
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
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(HAccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SportsEsports, null, tint = HAccent, modifier = Modifier.size(22.dp))
            }
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
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                            .background(HAccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.SportsEsports, null, tint = HAccent, modifier = Modifier.size(32.dp))
                    }
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