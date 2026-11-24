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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import com.ns.findyourintroverfriend.ui.theme.currentProfilePalette

// ── Peaceful Serenity — warm parchment · dusty blue · sage · mauve ────────────
private val GrBg: Color @Composable get() = currentProfilePalette.background
private val GrCard: Color @Composable get() = currentProfilePalette.card
private val GrCardAlt: Color @Composable get() = currentProfilePalette.tintedSurface
private val GrBorder: Color @Composable get() = currentProfilePalette.border
private val GrPrimary: Color @Composable get() = currentProfilePalette.text
private val GrSub: Color @Composable get() = currentProfilePalette.secondaryText
private val GrMuted: Color @Composable get() = currentProfilePalette.secondaryText
private val GrPill: Color @Composable get() = currentProfilePalette.tintedSurface
private val GrAccent: Color @Composable get() = currentProfilePalette.accent
private val GrAccentSoft: Color @Composable get() = currentProfilePalette.tintedSurface
private val GrGreen: Color @Composable get() = currentProfilePalette.accent
private val GrGreenSoft: Color @Composable get() = currentProfilePalette.tintedSurface
private val GrRed: Color @Composable get() = currentProfilePalette.accent
private val GrRedSoft: Color @Composable get() = currentProfilePalette.tintedSurface

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
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    vm: GroupRoomsViewModel = viewModel()
) {
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val joined by vm.joinedRooms.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val roomsError by vm.roomsError.collectAsStateWithLifecycle()
    val monthlyUsed by vm.monthlyUsed.collectAsStateWithLifecycle()
    val creating by vm.creating.collectAsStateWithLifecycle()
    val createError by vm.createError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    val mine = filter?.let { it == "joined" } ?: joined.isNotEmpty()
    val canCreate = monthlyUsed < MONTHLY_LIMIT

    val visible = remember(mine, rooms, joined, preferences, query) {
        val source = if (mine) joined.sortedWith(
            compareByDescending<GroupRoom> { preferences[it.id]?.pinned == true }
                .thenByDescending { it.lastMsgAt ?: it.createdAt }
        ) else rooms
        val term = query.trim()
        source.filter { it.name.contains(term, ignoreCase = true) || it.description.contains(term, ignoreCase = true) }
    }
    LaunchedEffect(createError) {
        val error = createError ?: return@LaunchedEffect
        if (createOpen) return@LaunchedEffect
        snackbar.showSnackbar(error)
        vm.clearCreateError()
    }
    Scaffold(
        containerColor = GrBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Groups", color = GrPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = GrPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GrBg)
            )
        },
        floatingActionButton = {
            if (canCreate) {
                ExtendedFloatingActionButton(
                    onClick = { createOpen = true },
                    text = { Text("New group") },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    containerColor = GrAccent,
                    contentColor = currentProfilePalette.onAccent
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupDirectoryTab("My Groups", mine, Modifier.weight(1f)) { filter = "joined" }
                GroupDirectoryTab("Discover", !mine, Modifier.weight(1f)) { filter = "discover" }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text(if (mine) "Search your groups" else "Find a group", color = GrSub) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = GrSub) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = GrPrimary) }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GrPrimary,
                    unfocusedTextColor = GrPrimary,
                    cursorColor = GrAccent,
                    focusedBorderColor = GrAccent,
                    unfocusedBorderColor = GrBorder,
                    focusedContainerColor = GrCard,
                    unfocusedContainerColor = GrCard
                )
            )
            when {
                loading && visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GrAccent)
                }
                roomsError && visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load groups.", color = GrPrimary)
                        TextButton(onClick = vm::reloadRooms) { Text("Retry", color = GrAccent) }
                    }
                }
                visible.isEmpty() && mine && query.isBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your groups will appear here", style = MaterialTheme.typography.titleMedium, color = GrPrimary)
                        TextButton(onClick = { filter = "discover" }) { Text("Discover groups", color = GrAccent) }
                    }
                }
                visible.isEmpty() -> GrEmptyState(query.isNotBlank(), canCreate) { createOpen = true }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(visible, key = { _, group -> group.id }) { index, group ->
                        GrRoomCard(
                            room = group,
                            myUid = vm.myUid,
                            preference = preferences[group.id] ?: GroupPreferences(),
                            accentIdx = index % GrAvatarColors.size,
                            onClick = { onOpenRoom(group.id) }
                        )
                    }
                }
            }
        }
    }

    if (createOpen) {
        GrCreateSheet(
            creating = creating,
            error = createError,
            onDismiss = { createOpen = false; vm.clearCreateError() },
            onCreate = { name, description, joinMode ->
                vm.createRoom(name, description, joinMode) { id ->
                    createOpen = false
                    onOpenRoom(id)
                }
            }
        )
    }
}

@Composable
private fun GroupDirectoryTab(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) GrAccentSoft else GrCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) GrAccent else GrBorder)
    ) {
        Text(
            title, color = if (selected) GrAccent else GrPrimary,
            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            modifier = Modifier.padding(13.dp)
        )
    }
}

@Composable
private fun GrRoomCard(
    room      : GroupRoom,
    myUid     : String,
    preference: GroupPreferences,
    accentIdx : Int,
    onClick   : () -> Unit
) {
    val isMember    = myUid in room.memberUids
    val unread = isMember && room.lastMsgUid != myUid &&
        room.lastMsgAt?.let { preference.lastReadAt == null || it > preference.lastReadAt } == true
    val accent      = GrAvatarColors[accentIdx]
    val accentSoft  = accent.copy(if (LocalVeilIsDark.current) 0.15f else 0.10f)
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
                            Text(if (unread) "New" else "Joined", fontSize = 10.sp, color = GrGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (isMember && preference.pinned) {
                        Icon(Icons.Rounded.PushPin, "Pinned group", tint = GrAccent, modifier = Modifier.size(12.dp))
                    }
                    if (isMember && preference.notificationMode == "muted") {
                        Icon(Icons.Rounded.NotificationsOff, "Muted", tint = GrSub, modifier = Modifier.size(13.dp))
                    }
                }

                Text(
                    if (isMember && room.lastMsg.isNotBlank()) room.lastMsg else room.description.ifBlank { "Open group details" },
                    fontSize = 12.sp,
                    color    = if (unread) GrPrimary else GrSub,
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
    error     : String?,
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
            if (error != null) Text(error, color = GrAccent, style = MaterialTheme.typography.bodySmall)

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
