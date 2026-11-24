package com.ns.findyourintroverfriend.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.feed.PostExpandOverlay
import com.ns.findyourintroverfriend.feed.ProfileUsernameEditor
import com.ns.findyourintroverfriend.feed.ProfilePostTile
import com.ns.findyourintroverfriend.feed.VeilPost
import com.ns.findyourintroverfriend.feed.toProfilePost
import com.ns.findyourintroverfriend.ui.theme.currentProfilePalette
import com.ns.findyourintroverfriend.util.VeilThemeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun OwnProfileTab(
    uid: String,
    email: String,
    state: VeilHomeUiState,
    isDev: Boolean,
    uploadingPhoto: Boolean,
    photoUploadError: String?,
    bottomAndSystemPadding: PaddingValues,
    onChangePhoto: () -> Unit,
    onSaveDisplayName: suspend (String) -> String?,
    onOpenProfileThemes: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenVeilFriends: () -> Unit,
    onOpenCookieStore: () -> Unit,
    onOpenInvite: () -> Unit,
    onOpenHelpSafety: () -> Unit,
    onOpenDevPanel: () -> Unit,
    onLogout: () -> Unit,
    onOpenHome: () -> Unit
) {
    val palette = currentProfilePalette
    val context = LocalContext.current
    var settingsOpen by rememberSaveable(uid) { mutableStateOf(false) }
    var displayNameEditorOpen by rememberSaveable(uid) { mutableStateOf(false) }
    var usernameEditorOpen by rememberSaveable(uid) { mutableStateOf(false) }
    val profileScroll = rememberLazyGridState()
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    Column(
        Modifier.fillMaxSize().background(palette.background).padding(bottomAndSystemPadding)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (settingsOpen) {
                IconButton(onClick = { settingsOpen = false }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to profile", tint = palette.text)
                }
            }
            Text(
                if (settingsOpen) "Settings" else "Your profile",
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Serif,
                fontSize = 29.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text
            )
            if (!settingsOpen) {
                OutlinedIconButton(
                    onClick = { settingsOpen = true },
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = palette.text),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Settings, "Profile settings", modifier = Modifier.size(21.dp))
                }
            }
        }
        if (settingsOpen) {
            ProfileSettings(
                email = email,
                isDev = isDev,
                onManageUsername = { usernameEditorOpen = true },
                onOpenProfileThemes = onOpenProfileThemes,
                onOpenPeople = onOpenPeople,
                onOpenVeilFriends = onOpenVeilFriends,
                onOpenCookieStore = onOpenCookieStore,
                onOpenInvite = onOpenInvite,
                onOpenHelpSafety = onOpenHelpSafety,
                onOpenDevPanel = onOpenDevPanel,
                onLogout = onLogout
            )
        } else {
            OwnProfileContent(
                uid, state, profileScroll, uploadingPhoto, photoUploadError,
                onChangePhoto, onOpenHome, onEditDisplayName = { displayNameEditorOpen = true }
            )
        }
    }
    if (displayNameEditorOpen) {
        DisplayNameEditor(
            currentName = state.alias,
            onDismiss = { displayNameEditorOpen = false },
            onSave = { name ->
                onSaveDisplayName(name).also { error ->
                    if (error == null) {
                        displayNameEditorOpen = false
                        Toast.makeText(context, "Display name updated.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
    if (usernameEditorOpen) {
        ProfileUsernameEditor(
            onDismiss = { usernameEditorOpen = false },
            onSaved = { usernameChanged ->
                usernameEditorOpen = false
                Toast.makeText(context, if (usernameChanged) "Username updated." else "Visibility saved.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun OwnProfileContent(
    uid: String,
    state: VeilHomeUiState,
    gridState: LazyGridState,
    uploadingPhoto: Boolean,
    photoUploadError: String?,
    onChangePhoto: () -> Unit,
    onOpenHome: () -> Unit,
    onEditDisplayName: () -> Unit
) {
    val palette = currentProfilePalette
    var posts by remember(uid) { mutableStateOf<List<VeilPost>>(emptyList()) }
    var username by remember(uid) { mutableStateOf("") }
    var loadingPosts by remember(uid) { mutableStateOf(uid.isNotBlank()) }
    var postsError by remember(uid) { mutableStateOf(false) }
    var retry by remember(uid) { mutableIntStateOf(0) }
    var selectedPostId by rememberSaveable(uid) { mutableStateOf<String?>(null) }
    val db = remember { FirebaseFirestore.getInstance() }

    DisposableEffect(uid, retry) {
        val listener = uid.takeIf(String::isNotBlank)?.let {
            db.collection("veilPosts").whereEqualTo("authorUid", it).addSnapshotListener { snapshot, error ->
                loadingPosts = false
                postsError = error != null
                if (snapshot != null) {
                    posts = snapshot.documents.mapNotNull(DocumentSnapshot::toProfilePost)
                        .sortedByDescending(VeilPost::createdAt)
                }
            }
        }
        onDispose { listener?.remove() }
    }
    DisposableEffect(uid) {
        val listener = uid.takeIf(String::isNotBlank)?.let {
            db.collection("peopleSettings").document(it).addSnapshotListener { snapshot, _ ->
                snapshot?.let { username = it.getString("username").orEmpty() }
            }
        }
        onDispose { listener?.remove() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(146.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileIdentity(state, username, uploadingPhoto, photoUploadError, onChangePhoto, onEditDisplayName)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your posts", Modifier.weight(1f), fontFamily = FontFamily.Serif, fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold, color = palette.text)
                if (posts.isNotEmpty()) Text(posts.size.toString(), color = palette.secondaryText)
            }
        }
        items(posts, key = { it.postId }) { post ->
            ProfilePostTile(post) { selectedPostId = post.postId }
        }
        if (loadingPosts || postsError || posts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(shape = RoundedCornerShape(18.dp), color = palette.card, border = BorderStroke(1.dp, palette.border)) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 23.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when {
                            loadingPosts -> CircularProgressIndicator(Modifier.size(23.dp), color = palette.accent, strokeWidth = 2.dp)
                            postsError -> {
                                Text("Couldn’t load your posts.", color = palette.text, textAlign = TextAlign.Center)
                                TextButton(onClick = {
                                    loadingPosts = true
                                    postsError = false
                                    retry += 1
                                }, colors = ButtonDefaults.textButtonColors(contentColor = palette.accent)) { Text("Try again") }
                            }
                            else -> {
                                Icon(Icons.Rounded.Forum, null, tint = palette.accent)
                                Text("Your first post can wait", color = palette.text, fontWeight = FontWeight.SemiBold)
                                Text("Share a thought or a photo whenever you’re ready.", color = palette.secondaryText,
                                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = onOpenHome,
                                    colors = ButtonDefaults.textButtonColors(contentColor = palette.accent)) { Text("Go to Home") }
                            }
                        }
                    }
                }
            }
        }
    }
    posts.firstOrNull { it.postId == selectedPostId }?.let { post ->
        PostExpandOverlay(post, onDismiss = { selectedPostId = null })
    }
}

@Composable
private fun ProfileIdentity(
    state: VeilHomeUiState,
    username: String,
    uploadingPhoto: Boolean,
    photoUploadError: String?,
    onChangePhoto: () -> Unit,
    onEditDisplayName: () -> Unit
) {
    val palette = currentProfilePalette
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.tintedSurface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Box(Modifier.size(82.dp)) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(palette.card)
                            .border(1.dp, palette.border, CircleShape)
                            .clickable(enabled = !uploadingPhoto, onClickLabel = "Change profile photo", onClick = onChangePhoto),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = state.photoUrl,
                                contentDescription = "Your profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(state.emoji.ifBlank { "🌙" }, fontSize = 30.sp, color = palette.text)
                        }
                        if (uploadingPhoto) {
                            Box(Modifier.fillMaxSize().background(palette.card.copy(alpha = 0.85f)), Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(25.dp), color = palette.accent, strokeWidth = 2.dp)
                            }
                        }
                    }
                    if (!uploadingPhoto) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(27.dp).background(palette.accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null, tint = palette.onAccent, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (state.isLoading && state.alias.isBlank()) "Loading profile…" else state.alias.ifBlank { "Wanderer" },
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Serif, fontSize = 25.sp, lineHeight = 29.sp,
                            fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = onEditDisplayName,
                            enabled = !state.isLoading
                        ) {
                            Icon(
                                Icons.Rounded.Edit, "Edit display name",
                                modifier = Modifier.size(18.dp), tint = palette.accent
                            )
                        }
                    }
                    if (username.isNotBlank()) {
                        Text("@$username", color = palette.accent, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.joinedAt > 0) {
                        val joined = remember(state.joinedAt) { SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(state.joinedAt)) }
                        Text("Joined $joined", color = palette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (photoUploadError != null) Text(photoUploadError, color = palette.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DisplayNameEditor(currentName: String, onDismiss: () -> Unit, onSave: suspend (String) -> String?) {
    val palette = currentProfilePalette
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val trimmed = name.trim()
    val canSave = !saving && trimmed != currentName &&
        trimmed.length in DISPLAY_NAME_MIN_LENGTH..DISPLAY_NAME_MAX_LENGTH && trimmed.none(Char::isISOControl)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = palette.card,
        title = { Text("Edit display name", color = palette.text) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.none(Char::isISOControl) && (it.length <= DISPLAY_NAME_MAX_LENGTH || it.length < name.length)) {
                        name = it
                        error = null
                    }
                },
                enabled = !saving,
                singleLine = true,
                label = { Text("Display name") },
                supportingText = {
                    Text(error ?: "Use $DISPLAY_NAME_MIN_LENGTH–$DISPLAY_NAME_MAX_LENGTH characters.",
                        color = if (error == null) palette.secondaryText else palette.text)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.text,
                    unfocusedTextColor = palette.text,
                    disabledTextColor = palette.text,
                    focusedLabelColor = palette.accent,
                    unfocusedLabelColor = palette.secondaryText,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border,
                    cursorColor = palette.accent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            error = onSave(trimmed)
                        } finally {
                            saving = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
            ) { Text(if (saving) "Saving…" else "Save name") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel", color = palette.accent) }
        }
    )
}

@Composable
private fun ProfileSettings(
    email: String,
    isDev: Boolean,
    onManageUsername: () -> Unit,
    onOpenProfileThemes: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenVeilFriends: () -> Unit,
    onOpenCookieStore: () -> Unit,
    onOpenInvite: () -> Unit,
    onOpenHelpSafety: () -> Unit,
    onOpenDevPanel: () -> Unit,
    onLogout: () -> Unit
) {
    val palette = currentProfilePalette
    val context = LocalContext.current
    val links = LocalUriHandler.current
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    fun openLink(url: String) {
        if (runCatching { links.openUri(url) }.isFailure) {
            Toast.makeText(context, "Couldn’t open this link.", Toast.LENGTH_SHORT).show()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        SettingsGroup("Profile and discovery") {
            SettingsRow(Icons.Rounded.AlternateEmail, "Username and visibility", "Manage how people find you", onManageUsername)
            SettingsDivider()
            SettingsRow(Icons.Rounded.PersonSearch, "People", "Find friends on VEIL", onOpenPeople)
        }
        SettingsGroup("Appearance") {
            ProfileThemeOptions()
            SettingsDivider()
            SettingsRow(Icons.Rounded.Palette, "Profile themes", onClick = onOpenProfileThemes)
        }
        SettingsGroup("Community") {
            SettingsRow(Icons.Rounded.ShoppingBag, "Cookie Store", onClick = onOpenCookieStore)
            SettingsDivider()
            SettingsRow(Icons.Rounded.PersonAdd, "Invite friends", onClick = onOpenInvite)
            SettingsDivider()
            SettingsRow(Icons.Rounded.AutoAwesome, "VEIL Friends AI", "On hold", onClick = onOpenVeilFriends)
        }
        SettingsGroup("Support") {
            SettingsRow(Icons.Rounded.Shield, "Help & Safety", "Feedback, reports, and privacy policy", onOpenHelpSafety)
            SettingsDivider()
            SettingsRow(Icons.Rounded.Description, "Terms of service") { openLink("https://veil-app.in/terms") }
            SettingsDivider()
            SettingsRow(Icons.Rounded.StarBorder, "Rate VEIL") {
                openLink("https://play.google.com/store/apps/details?id=com.ns.findyourintroverfriend")
            }
        }
        SettingsGroup("Account") {
            if (email.isNotBlank()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("Signed in as", color = palette.secondaryText, style = MaterialTheme.typography.labelMedium)
                    Text(email, color = palette.text, style = MaterialTheme.typography.bodyMedium)
                }
                SettingsDivider()
            }
            SettingsRow(Icons.Rounded.ExitToApp, "Sign out") { confirmSignOut = true }
            SettingsDivider()
            SettingsRow(Icons.Rounded.DeleteOutline, "Request account deletion") {
                openLink("https://docs.google.com/forms/d/e/1FAIpQLScLs32PUEm-BFcwq_M8q-wy5a1M0d1ZIpOZoTW9a7BYNvlg6Q/viewform")
            }
        }
        if (isDev) {
            SettingsGroup("Developer tools") {
                SettingsRow(Icons.Rounded.Build, "Dev Panel", onClick = onOpenDevPanel)
            }
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = palette.card,
            titleContentColor = palette.text,
            textContentColor = palette.secondaryText,
            title = { Text("Sign out of VEIL?") },
            text = { Text("You can sign back in with your Google account.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmSignOut = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.text)) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = currentProfilePalette
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = palette.text, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 3.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp), color = palette.card, contentColor = palette.text,
            border = BorderStroke(1.dp, palette.border)
        ) { Column(content = content) }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 13.dp), color = currentProfilePalette.border)
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, detail: String? = null, onClick: () -> Unit) {
    val palette = currentProfilePalette
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 50.dp).padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Icon(icon, null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(detail, color = palette.secondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.secondaryText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProfileThemeOptions() {
    val palette = currentProfilePalette
    val context = LocalContext.current
    val mode = VeilThemeManager.themeMode
    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("App theme", color = palette.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                VeilThemeManager.ThemeMode.SYSTEM to "System",
                VeilThemeManager.ThemeMode.LIGHT to "Light",
                VeilThemeManager.ThemeMode.DARK to "Dark"
            ).forEach { (value, label) ->
                val selected = value == mode
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.weight(1f).clip(shape).background(if (selected) palette.accent else palette.card)
                        .border(1.dp, if (selected) palette.accent else palette.border, shape)
                        .selectable(selected, role = Role.RadioButton) { VeilThemeManager.setTheme(context, value) }
                        .heightIn(min = 46.dp).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (selected) palette.onAccent else palette.text, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
