package com.ns.findyourintroverfriend.groupchat

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.util.LocalVeilIsDark

// ── Peaceful Serenity — warm parchment · dusty blue · sage · mauve ────────────
private var _grDark by mutableStateOf(true)

// Backgrounds
private val GrBg       get() = if (_grDark) Color(0xFF0F0F14) else Color(0xFFF0EBE3)
private val GrCard     get() = if (_grDark) Color(0xFF17171E) else Color(0xFFF7F3EE)
private val GrCardAlt  get() = if (_grDark) Color(0xFF1E1E26) else Color(0xFFE8E2D8)
private val GrBorder   get() = if (_grDark) Color(0xFF2C2C34) else Color(0xFFD8D2C8)

// Text
private val GrPrimary  get() = if (_grDark) Color(0xFFE0DDD8) else Color(0xFF2C2825)
private val GrSub      get() = if (_grDark) Color(0xFF918E8A) else Color(0xFF7A7470)
private val GrMuted    get() = if (_grDark) Color(0xFF5A5855) else Color(0xFFB0A89E)
private val GrPill     get() = if (_grDark) Color(0xFF1C1C23) else Color(0xFFE8E2D8)

// Accent — dusty blue (matches Explore Groups card)
private val GrAccent          = Color(0xFF7B9AC4)
private val GrAccentSoft get() = if (_grDark) Color(0xFF101825) else Color(0xFFECF1F8)

// Semantic
private val GrGreen           = Color(0xFF7A9E87)   // sage green
private val GrGreenSoft get() = if (_grDark) Color(0xFF0E1E13) else Color(0xFFEFF5F1)
private val GrRed             = Color(0xFFBF6B6B)   // muted terracotta
private val GrRedSoft   get() = if (_grDark) Color(0xFF1E0F0F) else Color(0xFFF5EEED)

// Avatar accent palette (cycles through rooms)
private val GrAvatarColors = listOf(
    Color(0xFF7B9AC4), Color(0xFF7A9E87), Color(0xFFA07C8C),
    Color(0xFFBFA882), Color(0xFF9A8AB4), Color(0xFF7AB4A0)
)

// ─────────────────────────────────────────────────────────────────────────────
//  GroupRoomsScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupRoomsScreen(
    onBack     : () -> Unit,
    onOpenRoom : (roomId: String) -> Unit,
    vm         : GroupRoomsViewModel = viewModel()
) {
    val dark = LocalVeilIsDark.current
    SideEffect { _grDark = dark }

    val rooms       by vm.rooms.collectAsStateWithLifecycle()
    val loading     by vm.loading.collectAsStateWithLifecycle()
    val monthlyUsed by vm.monthlyUsed.collectAsStateWithLifecycle()
    val creating    by vm.creating.collectAsStateWithLifecycle()
    val createError by vm.createError.collectAsStateWithLifecycle()

    var searchQuery     by remember { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var searchFocused   by remember { mutableStateOf(false) }

    val filtered = remember(rooms, searchQuery) {
        if (searchQuery.isBlank()) rooms
        else rooms.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val canCreate = monthlyUsed < MONTHLY_LIMIT

    Box(Modifier.fillMaxSize().background(GrBg)) {

        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ── Gradient header ───────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (dark) Color(0xFF131020) else Color(0xFFDDE6F0),
                                GrBg
                            )
                        )
                    )
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Back + label row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(GrPill)
                                .border(0.5.dp, GrBorder, CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ArrowBack, null, tint = GrSub, modifier = Modifier.size(16.dp))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(GrAccent.copy(0.6f)))
                            Text(
                                "GROUPS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GrAccent.copy(0.7f),
                                letterSpacing = 1.8.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Open Rooms",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = GrPrimary,
                                letterSpacing = (-0.4).sp
                            )
                            Text(
                                "Chat rooms anyone can join",
                                fontSize = 12.sp,
                                color = GrSub
                            )
                        }

                        // Quota pill
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(GrCardAlt)
                                .border(0.5.dp, GrBorder, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "${MONTHLY_LIMIT - monthlyUsed} creates left",
                                fontSize   = 10.sp,
                                color      = if (monthlyUsed >= MONTHLY_LIMIT) GrRed else GrSub,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(
                        elevation = if (searchFocused) 4.dp else 1.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = GrAccent.copy(0.12f)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(GrCard)
                    .border(
                        1.dp,
                        if (searchFocused) GrAccent.copy(0.4f) else GrBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Search, null,
                        tint = if (searchFocused) GrAccent else GrMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = GrPrimary),
                        cursorBrush = SolidColor(GrAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Find a group…", fontSize = 13.sp, color = GrMuted)
                            }
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Icon(
                            Icons.Rounded.Close, null,
                            tint = GrMuted,
                            modifier = Modifier.size(14.dp).clickable { searchQuery = "" }
                        )
                    }
                }
            }

            // ── Stats strip ───────────────────────────────────────────────────
            if (rooms.isNotEmpty() && searchQuery.isBlank()) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(GrGreen))
                    Text(
                        "${rooms.size} group${if (rooms.size != 1) "s" else ""} active",
                        fontSize = 11.sp,
                        color = GrMuted
                    )
                }
            }

            // ── Room list ─────────────────────────────────────────────────────
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = GrAccent,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                filtered.isEmpty() -> {
                    GrEmptyState(
                        hasSearch = searchQuery.isNotBlank(),
                        canCreate = canCreate,
                        onCreate = { showCreateSheet = true }
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 4.dp, bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, r -> r.id }) { idx, room ->
                            GrRoomCard(
                                room      = room,
                                myUid     = vm.myUid,
                                accentIdx = idx % GrAvatarColors.size,
                                onClick   = { onOpenRoom(room.id) }
                            )
                        }
                    }
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Row(
                Modifier
                    .shadow(6.dp, RoundedCornerShape(20.dp), spotColor = GrAccent.copy(0.20f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (canCreate)
                            Brush.linearGradient(listOf(GrAccent, GrAccent.copy(0.85f)))
                        else
                            Brush.linearGradient(listOf(GrMuted, GrMuted))
                    )
                    .clickable(enabled = canCreate) { showCreateSheet = true }
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    "New Group",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }
        }

        // ── Error snack ───────────────────────────────────────────────────────
        createError?.let { err ->
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 80.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(GrRedSoft)
                    .border(1.dp, GrRed.copy(0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = GrRed, modifier = Modifier.size(14.dp))
                    Text(err, fontSize = 12.sp, color = GrRed, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Rounded.Close, null, tint = GrRed,
                        modifier = Modifier.size(14.dp).clickable { vm.clearCreateError() }
                    )
                }
            }
        }
    }

    // ── Create sheet ──────────────────────────────────────────────────────────
    if (showCreateSheet) {
        GrCreateSheet(
            creating  = creating,
            onCreate  = { name, desc, joinMode ->
                vm.createRoom(name, desc, joinMode)
                showCreateSheet = false
            },
            onDismiss = { showCreateSheet = false }
        )
    }
}

// ── Room card ─────────────────────────────────────────────────────────────────
@Composable
private fun GrRoomCard(
    room      : GroupRoom,
    myUid     : String,
    accentIdx : Int,
    onClick   : () -> Unit
) {
    val isMember    = myUid in room.memberUids
    val accent      = GrAvatarColors[accentIdx]
    val accentSoft  = accent.copy(if (_grDark) 0.15f else 0.10f)
    val initial     = room.name.take(1).uppercase()

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = accent.copy(0.08f))
            .clip(RoundedCornerShape(16.dp))
            .background(GrCard)
            .border(1.dp, GrBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        // Subtle left accent bar
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(accent.copy(0.6f))
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentSoft)
                    .border(1.dp, accent.copy(0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (room.isSystem) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        initial,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = accent
                    )
                }
            }

            // Content
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        room.name,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = GrPrimary,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (room.isSystem) {
                        Box(
                            Modifier.clip(RoundedCornerShape(5.dp))
                                .background(GrAccentSoft)
                                .border(0.5.dp, GrAccent.copy(0.3f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("Official", fontSize = 8.sp, color = GrAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (isMember) {
                        Box(
                            Modifier.clip(RoundedCornerShape(5.dp))
                                .background(GrGreenSoft)
                                .border(0.5.dp, GrGreen.copy(0.3f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("Joined", fontSize = 8.sp, color = GrGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Text(
                    if (room.lastMsg.isNotBlank()) room.lastMsg else room.description.ifBlank { "No description" },
                    fontSize = 12.sp,
                    color    = GrSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: member count
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Rounded.Group, null, tint = GrMuted, modifier = Modifier.size(11.dp))
                    Text("${room.memberCount}", fontSize = 11.sp, color = GrMuted)
                }
                Icon(
                    Icons.Rounded.ChevronRight, null,
                    tint = GrMuted.copy(0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun GrEmptyState(
    hasSearch : Boolean,
    canCreate : Boolean,
    onCreate  : () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "ge")
    val sc  by inf.animateFloat(0.93f, 1.07f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), "ges")

    Box(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .scale(sc)
                    .clip(CircleShape)
                    .background(GrAccentSoft)
                    .border(1.dp, GrAccent.copy(0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (hasSearch) Icons.Rounded.SearchOff else Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = GrAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (hasSearch) "No groups match" else "No groups yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = GrPrimary
            )
            Text(
                if (hasSearch) "Try a different search term" else "Be the first to create one",
                fontSize = 13.sp,
                color = GrSub
            )
            if (!hasSearch && canCreate) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(GrAccentSoft)
                        .border(1.dp, GrAccent.copy(0.35f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onCreate)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = GrAccent, modifier = Modifier.size(16.dp))
                        Text("Create a Group", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GrAccent)
                    }
                }
            }
        }
    }
}

// ── Create Room bottom sheet ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrCreateSheet(
    creating  : Boolean,
    onCreate  : (name: String, description: String, joinMode: String) -> Unit,
    onDismiss : () -> Unit
) {
    var name     by remember { mutableStateOf("") }
    var desc     by remember { mutableStateOf("") }
    var joinMode by remember { mutableStateOf("auto") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = GrCard,
        dragHandle       = null,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle
            Box(
                Modifier.align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(GrBorder)
            )

            // Title row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("New Group", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GrPrimary)
                    Text("Create a space for your community", fontSize = 11.sp, color = GrSub)
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(GrPill)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, null, tint = GrMuted, modifier = Modifier.size(14.dp))
                }
            }

            HorizontalDivider(color = GrBorder, thickness = 0.5.dp)

            // Name field
            GrInputField(
                value       = name,
                onValue     = { if (it.length <= 40) name = it },
                label       = "Group Name",
                placeholder = "e.g. Late Night Thinkers",
                singleLine  = true,
                trailingText = "${name.length}/40"
            )

            // Description field
            GrInputField(
                value       = desc,
                onValue     = { if (it.length <= 120) desc = it },
                label       = "Description",
                placeholder = "What is this group about? (optional)",
                singleLine  = false
            )

            // Join mode
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Who can join?", fontSize = 11.sp, color = GrSub, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GrModeChip(
                        icon     = Icons.Rounded.LockOpen,
                        label    = "Auto Accept",
                        sub      = "Anyone can join",
                        selected = joinMode == "auto",
                        onClick  = { joinMode = "auto" },
                        modifier = Modifier.weight(1f)
                    )
                    GrModeChip(
                        icon     = Icons.Rounded.AdminPanelSettings,
                        label    = "Approval",
                        sub      = "You approve joins",
                        selected = joinMode == "approve",
                        onClick  = { joinMode = "approve" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Create button
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        if (name.trim().isNotBlank()) 4.dp else 0.dp,
                        RoundedCornerShape(14.dp),
                        spotColor = GrAccent.copy(0.20f)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (name.trim().isNotBlank() && !creating)
                            Brush.linearGradient(listOf(GrAccent, GrAccent.copy(0.85f)))
                        else
                            Brush.linearGradient(listOf(GrMuted.copy(0.4f), GrMuted.copy(0.4f)))
                    )
                    .clickable(enabled = name.trim().isNotBlank() && !creating) {
                        onCreate(name, desc, joinMode)
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Forum, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            "Create Group",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Input field ───────────────────────────────────────────────────────────────
@Composable
private fun GrInputField(
    value        : String,
    onValue      : (String) -> Unit,
    label        : String,
    placeholder  : String,
    singleLine   : Boolean,
    trailingText : String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 11.sp, color = GrSub, fontWeight = FontWeight.Medium)
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GrBg)
                .border(1.dp, GrBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicTextField(
                    value         = value,
                    onValueChange = onValue,
                    singleLine    = singleLine,
                    textStyle     = TextStyle(fontSize = 13.sp, color = GrPrimary),
                    cursorBrush   = SolidColor(GrAccent),
                    decorationBox = { inner ->
                        if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = GrMuted)
                        inner()
                    },
                    modifier = Modifier.weight(1f).let { m ->
                        if (singleLine) m else m.heightIn(min = 56.dp)
                    }
                )
                if (trailingText != null) {
                    Text(trailingText, fontSize = 10.sp, color = GrMuted, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

// ── Join mode chip ────────────────────────────────────────────────────────────
@Composable
private fun GrModeChip(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    label    : String,
    sub      : String,
    selected : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GrAccentSoft else GrBg)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) GrAccent.copy(0.5f) else GrBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon, null,
                tint = if (selected) GrAccent else GrMuted,
                modifier = Modifier.size(14.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    label,
                    fontSize   = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (selected) GrAccent else GrSub
                )
                Text(sub, fontSize = 9.sp, color = GrMuted)
            }
        }
    }
}

// Keep old names for compat (sheet field + chip)
@Composable
private fun SheetInputField(value: String, onValue: (String) -> Unit, placeholder: String, singleLine: Boolean) =
    GrInputField(value, onValue, "", placeholder, singleLine)

@Composable
private fun JoinModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) GrPrimary else GrPill)
            .border(1.dp, if (selected) GrPrimary else GrBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) GrBg else GrSub)
    }
}
