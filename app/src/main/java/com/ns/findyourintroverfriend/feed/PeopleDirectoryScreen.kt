package com.ns.findyourintroverfriend.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.ui.theme.currentProfilePalette
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleDirectoryScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    openSettingsOnStart: Boolean = false,
    directoryVm: PeopleDirectoryViewModel = viewModel(),
    approachVm: FeedApproachViewModel = viewModel()
) {
    val directory by directoryVm.directory.collectAsStateWithLifecycle()
    val settingsStatus by directoryVm.mySettings.collectAsStateWithLifecycle()
    val blocked by directoryVm.blocked.collectAsStateWithLifecycle()
    val requests by approachVm.myApproaches.collectAsStateWithLifecycle()
    val following by approachVm.myFollowing.collectAsStateWithLifecycle()
    val friends by approachVm.friendChats.collectAsStateWithLifecycle()
    val result by approachVm.result.collectAsStateWithLifecycle()
    val myAlias by directoryVm.myAlias.collectAsStateWithLifecycle()
    var sendingTo by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(openSettingsOnStart) }
    var friendsOnly by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val owner = directoryVm.uid
    LaunchedEffect(directoryVm) {
        directoryVm.syncAccount()
        directoryVm.openDirectory()
    }
    LaunchedEffect(result) {
        val message = when (val current = result) {
            ApproachResult.Sent -> "Friend request sent."
            is ApproachResult.Error -> current.message
            ApproachResult.NeedCookies -> "Couldn’t send the request. Try again."
            ApproachResult.Idle -> return@LaunchedEffect
        }
        sendingTo = null
        approachVm.resetResult()
        scope.launch { snackbar.showSnackbar(message) }
    }

    val friendByUid = remember(friends, owner) {
        friends.associateBy { if (it.userA == owner) it.userB else it.userA }
    }
    val people = if (!friendsOnly) directory.people else friendByUid.map { (uid, chat) ->
        directory.people.firstOrNull { it.uid == uid } ?: DirectoryPerson(
            uid, if (chat.userA == owner) chat.aliasB else chat.aliasA, ""
        )
    }.filter {
        val term = directory.query.trim()
        term.isBlank() || it.alias.contains(term, ignoreCase = true) ||
            (it.username.isNotBlank() && it.username.startsWith(canonicalUsername(term)))
    }.sortedBy { it.alias.lowercase() }
    val visible = people.filter { it.uid != owner && it.uid !in blocked }
    PeoplePalette {
        val colors = MaterialTheme.colorScheme
        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text("People", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.onBackground,
                        navigationIconContentColor = colors.onBackground
                    )
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                UsernameEntry(settingsStatus.settings, onClick = {
                    showSettings = true
                    directoryVm.syncAccount(refresh = true)
                })
                OutlinedTextField(
                    value = directory.query,
                    onValueChange = { directoryVm.search(it.take(40)) },
                    label = { Text("Search name or @username") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (directory.query.isEmpty()) null else ({
                        IconButton(onClick = { directoryVm.search("", debounce = false) }) {
                            Icon(Icons.Rounded.Close, "Clear search")
                        }
                    }),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = peopleFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        labelColor = colors.onSurface,
                        selectedContainerColor = colors.primaryContainer,
                        selectedLabelColor = colors.onPrimaryContainer
                    )
                    FilterChip(!friendsOnly, onClick = { friendsOnly = false }, label = { Text("Everyone") }, colors = chipColors)
                    FilterChip(friendsOnly, onClick = { friendsOnly = true }, label = { Text("Friends") }, colors = chipColors)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(visible, key = { "person:" + it.uid }) { person ->
                        val chat = friendByUid[person.uid]
                        DirectoryPersonRow(
                            person = person,
                            pending = person.uid in requests,
                            connected = chat != null || person.uid in following,
                            sending = sendingTo == person.uid,
                            canSend = sendingTo == null,
                            onProfile = { onOpenProfile(person.uid) },
                            onMessage = chat?.let { { onOpenChat(it.matchId) } },
                            onConnect = {
                                if (sendingTo == null) {
                                    sendingTo = person.uid
                                    approachVm.sendFreeApproach(person.uid, person.alias, myAlias, "", "")
                                }
                            }
                        )
                    }
                    item(key = "people-status") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
                            when {
                                friendsOnly && visible.isEmpty() ->
                                    Text("No friends match this view.", color = colors.onSurfaceVariant)
                                friendsOnly -> Unit
                                directory.loading -> CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.dp)
                                directory.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(directory.error.orEmpty(), color = colors.onSurface, textAlign = TextAlign.Center)
                                    TextButton(onClick = {
                                        if (directory.hasMore) directoryVm.loadMore()
                                        else directoryVm.search(directory.query, debounce = false)
                                    }) { Text("Try again") }
                                }
                                directory.hasMore -> OutlinedButton(onClick = directoryVm::loadMore) { Text("Load more people") }
                                visible.isEmpty() -> Text(
                                    if (directory.query.isBlank()) "No people to show yet." else "No matching profiles.",
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showSettings) PeopleSettingsDialog(
            status = settingsStatus,
            onDismiss = { showSettings = false },
            onRetry = { directoryVm.syncAccount(refresh = true) },
            onSave = { username, listed ->
                val error = directoryVm.saveSettings(username, listed)
                if (error == null) {
                    showSettings = false
                    scope.launch { snackbar.showSnackbar(if (username != null) "Username updated." else "Visibility saved.") }
                }
                error
            }
        )
    }
}

@Composable
private fun UsernameEntry(settings: PeopleSettings?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val username = settings?.username.orEmpty()
    val action = if (username.isEmpty()) "Set username" else "Edit username"
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.AlternateEmail, null, tint = colors.primary)
            Column(Modifier.weight(1f)) {
                Text(action, fontWeight = FontWeight.SemiBold)
                if (username.isNotEmpty()) {
                    Text(
                        if (settings?.listed == false) "@$username · Hidden from People" else "@$username",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Icon(Icons.Rounded.Edit, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun ProfileUsernameEditor(
    onDismiss: () -> Unit,
    onSaved: (usernameChanged: Boolean) -> Unit,
    directoryVm: PeopleDirectoryViewModel = viewModel(key = "profile-username-editor")
) {
    val status by directoryVm.mySettings.collectAsStateWithLifecycle()
    LaunchedEffect(directoryVm) { directoryVm.syncAccount(refresh = true) }

    PeoplePalette {
        PeopleSettingsDialog(
            status = status,
            onDismiss = onDismiss,
            onRetry = { directoryVm.syncAccount(refresh = true) },
            onSave = { username, listed ->
                val error = directoryVm.saveSettings(username, listed)
                if (error == null) onSaved(username != null)
                error
            }
        )
    }
}

@Composable
private fun DirectoryPersonRow(
    person: DirectoryPerson,
    pending: Boolean,
    connected: Boolean,
    sending: Boolean,
    canSend: Boolean,
    onProfile: () -> Unit,
    onMessage: (() -> Unit)?,
    onConnect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(
                    onClickLabel = "Open ${person.alias}’s profile", onClick = onProfile
                ).padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                PeopleAvatar(person.alias)
                PersonName(person)
            }
            when {
                onMessage != null -> TextButton(onClick = onMessage) { Text("Message") }
                connected -> Text("Friends", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                sending -> CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                pending -> Text("Request sent", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                else -> FilledTonalButton(
                    onClick = onConnect,
                    enabled = canSend,
                    contentPadding = PaddingValues(horizontal = 11.dp)
                ) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun PeopleSettingsDialog(
    status: PeopleSettingsStatus,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSave: suspend (String?, Boolean?) -> String?
) {
    val settings = status.settings
    val colors = MaterialTheme.colorScheme
    if (settings == null || status.loading) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = { Text("Username and visibility") },
            text = {
                Text(if (status.loading) "Loading your username settings…" else
                    status.error ?: "Username settings couldn’t be loaded.")
            },
            confirmButton = { TextButton(onClick = onRetry, enabled = !status.loading) { Text("Retry") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
        return
    }
    var username by rememberSaveable(settings.username) { mutableStateOf(settings.username) }
    var listed by rememberSaveable(settings.listed) { mutableStateOf(settings.listed) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val normalized = canonicalUsername(username)
    val updatedUsername = normalized.takeIf { it != settings.username }
    val updatedVisibility = listed.takeIf { it != settings.listed }
    val validationError = updatedUsername?.let(::usernameValidationError)
    val canSave = !saving && validationError == null && (updatedUsername != null || updatedVisibility != null)
    val saveLabel = when {
        saving -> "Saving…"
        updatedUsername == "" -> "Remove username"
        updatedUsername == null && updatedVisibility != null -> "Save visibility"
        settings.username.isEmpty() -> "Set username"
        else -> "Update username"
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { Text(if (settings.username.isEmpty()) "Set username" else "Edit username") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trimStart().removePrefix("@").take(24); error = null },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    supportingText = {
                        Text(validationError ?: "3–20 letters, numbers or underscores, starting with a letter.")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    singleLine = true,
                    enabled = !saving,
                    isError = validationError != null,
                    colors = peopleFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Show me in People", color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Others can find your alias and username.", style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant)
                    }
                    Switch(listed, onCheckedChange = { listed = it; error = null }, enabled = !saving)
                }
                error?.let { Text(it, color = colors.onSurface) }
            }
        },
        confirmButton = {
            Button(enabled = canSave, onClick = {
                saving = true
                scope.launch {
                    try { error = onSave(updatedUsername, updatedVisibility) } finally { saving = false }
                }
            }) { Text(saveLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

@Composable
internal fun HomePeopleSuggestions(
    people: List<DirectoryPerson>,
    pending: Set<String>,
    sendingTo: String?,
    onViewAll: () -> Unit,
    onProfile: (String) -> Unit,
    onConnect: (DirectoryPerson) -> Unit
) {
    PeoplePalette {
        val colors = MaterialTheme.colorScheme
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("People you can meet", Modifier.weight(1f), color = colors.onBackground,
                    fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onViewAll) { Text("View all") }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(people, key = { it.uid }) { person ->
                    Surface(
                        modifier = Modifier.width(154.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outlineVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(
                                    onClickLabel = "Open ${person.alias}’s profile"
                                ) { onProfile(person.uid) },
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PeopleAvatar(person.alias)
                                PersonName(person)
                            }
                            if (person.uid in pending || sendingTo == person.uid) {
                                Text(if (sendingTo == person.uid) "Sending…" else "Request sent",
                                    color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 9.dp),
                                    style = MaterialTheme.typography.labelMedium)
                            } else {
                                FilledTonalButton({ onConnect(person) }, enabled = sendingTo == null, modifier = Modifier.fillMaxWidth()) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val homePrompts = listOf(
    "What’s one small thing that made your day better?",
    "What’s a quiet place you’d recommend to someone?",
    "What’s something you enjoy doing on your own?",
    "Which song fits your mood today?",
    "What’s a hobby you’d love to try?",
    "What helps you feel at home around someone?"
)

@Composable
internal fun HomeConversationPrompt(onWrite: (String) -> Unit) {
    val prompt = homePrompts[(LocalDate.now().toEpochDay() % homePrompts.size).toInt()]
    PeoplePalette {
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = colors.primaryContainer,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today’s conversation", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Text(prompt, fontFamily = FontFamily.Serif, fontSize = 22.sp, color = colors.onSurface)
                Button(
                    onClick = { onWrite(prompt) },
                    modifier = Modifier.padding(top = 5.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF282329),
                        contentColor = Color(0xFFFFF7F3)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF65535F))
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Write a post", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PeopleAvatar(alias: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(13.dp), color = colors.primaryContainer) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text(alias.firstOrNull()?.uppercase() ?: "?", color = colors.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PersonName(person: DirectoryPerson) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(person.alias, fontWeight = FontWeight.SemiBold, color = colors.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (person.username.isNotBlank()) {
            Text("@${person.username}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun peopleFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
        errorContainerColor = colors.surface,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        errorTextColor = colors.onSurface,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant,
        errorLabelColor = colors.onSurface,
        focusedLeadingIconColor = colors.primary,
        unfocusedLeadingIconColor = colors.onSurfaceVariant
    )
}

@Composable
private fun PeoplePalette(content: @Composable () -> Unit) {
    val palette = currentProfilePalette
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = palette.background,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceContainerHigh = palette.card,
            surfaceVariant = palette.tintedSurface,
            surfaceTint = palette.card,
            onSurfaceVariant = palette.secondaryText,
            outline = palette.secondaryText,
            outlineVariant = palette.border,
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.tintedSurface,
            onPrimaryContainer = palette.text,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            secondaryContainer = palette.tintedSurface,
            onSecondaryContainer = palette.text,
            inverseSurface = palette.text,
            inverseOnSurface = palette.background,
            inversePrimary = palette.background
        ),
        content = content
    )
}
