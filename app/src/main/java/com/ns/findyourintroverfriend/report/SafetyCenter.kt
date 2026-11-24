package com.ns.findyourintroverfriend.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.ns.findyourintroverfriend.util.LocalVeilIsDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val CHILD_SAFETY_URL = "https://sites.google.com/view/veil-childsafetystandards/home"
private const val PRIVACY_URL = "https://sites.google.com/view/veil-privacy-policy/home"
private const val SUPPORT_EMAIL = "solynlabs@gmail.com"

private val reportCategories = linkedMapOf(
    "child_safety" to "Child Safety Concern",
    "sexual_exploitation" to "Sexual Exploitation",
    "harassment" to "Harassment or Bullying",
    "inappropriate" to "Inappropriate Behaviour",
    "threats" to "Threats or Violence",
    "spam" to "Spam or Fake Account",
    "other" to "Other"
)
private val feedbackCategories = linkedMapOf(
    "bug" to "App problem", "suggestion" to "Suggestion", "account" to "Account help", "other" to "Other"
)

internal data class SafetyTarget(
    val kind: String,
    val title: String,
    val reportedUserId: String = "",
    val matchId: String = "",
    val messageId: String = ""
)

internal object SafetyService {
    fun blocks(owner: String) = FirebaseFirestore.getInstance()
        .collection("userBlocks").document(owner).collection("blocked")

    fun blockRef(owner: String, target: String) = blocks(owner).document(target)

    suspend fun block(owner: String, target: String) {
        blockRef(owner, target).set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun call(name: String, data: Map<String, Any>) {
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable(name).call(data).await()
    }

    suspend fun isModerator(): Boolean = FirebaseAuth.getInstance().currentUser
        ?.getIdToken(true)?.await()?.claims?.get("safetyModerator") == true
}

internal fun safetyError(error: Exception): String = when ((error as? FirebaseFunctionsException)?.code) {
    FirebaseFunctionsException.Code.INVALID_ARGUMENT,
    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
    FirebaseFunctionsException.Code.FAILED_PRECONDITION,
    FirebaseFunctionsException.Code.NOT_FOUND -> error.message ?: "Check the information and try again."
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Sign in again and retry."
    FirebaseFunctionsException.Code.PERMISSION_DENIED -> "You don't have access to complete this action."
    else -> "The action could not be completed. Check your connection and try again."
}

@Composable
internal fun contactAvailability(owner: String, target: String): Boolean? {
    var outgoing by remember(owner, target) { mutableStateOf<Boolean?>(null) }
    var incoming by remember(owner, target) { mutableStateOf<Boolean?>(null) }
    DisposableEffect(owner, target) {
        if (owner.isBlank() || target.isBlank() || owner == target) return@DisposableEffect onDispose { }
        val ownListener = SafetyService.blockRef(owner, target).addSnapshotListener { snapshot, _ ->
            outgoing = snapshot?.exists()
        }
        val otherListener = SafetyService.blockRef(target, owner).addSnapshotListener { snapshot, _ ->
            incoming = snapshot?.exists()
        }
        onDispose {
            ownListener.remove()
            otherListener.remove()
        }
    }
    if (outgoing == true || incoming == true) return false
    return if (outgoing == false && incoming == false) true else null
}

@Composable
internal fun showSafetyAccountRestriction(): Boolean {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var userId by remember { mutableStateOf(auth.currentUser?.uid) }
    var permanent by remember(userId) { mutableStateOf(false) }
    var expiresAt by remember(userId) { mutableStateOf(0L) }
    var temporary by remember(userId) { mutableStateOf(false) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { userId = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    DisposableEffect(userId) {
        val listener = userId?.let { uid ->
            db.collection("safetyRestrictions").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                permanent = snapshot.getBoolean("permanent") == true
                expiresAt = snapshot.getTimestamp("until")?.toDate()?.time ?: 0L
                temporary = expiresAt > System.currentTimeMillis()
            }
        }
        onDispose { listener?.remove() }
    }
    LaunchedEffect(userId, expiresAt) {
        delay((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
        temporary = false
    }

    val restricted = permanent || temporary
    if (restricted) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (permanent) "VEIL account banned" else "VEIL account suspended", style = MaterialTheme.typography.headlineSmall)
                Text("Contact $SUPPORT_EMAIL for help with this decision.")
                TextButton(onClick = {
                    com.ns.findyourintroverfriend.notifications.VeilNotifier.unregisterUser()
                    auth.signOut()
                }) { Text("Sign out") }
            }
        }
    }
    return restricted
}

@Composable
internal fun SafetyForm(target: SafetyTarget, onDismiss: () -> Unit) {
    SafetyCenterTheme {
        SafetyEntryDialog(target, onDismiss)
    }
}

@Composable
private fun SafetyEntryDialog(target: SafetyTarget, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = target.kind == "feedback"
    val categories = if (feedback) feedbackCategories else reportCategories
    var category by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    var sending by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val requiresDescription = feedback || target.kind == "concern"

    if (sent) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            icon = { Icon(Icons.Rounded.Check, null, tint = colors.primary) },
            title = { Text(if (feedback) "Feedback received" else "Report received") },
            text = { Text("Your submission has been sent to the VEIL team.") },
            confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (feedback) Icons.Rounded.Feedback else Icons.Rounded.Flag, null, tint = colors.primary)
                Text(target.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (feedback) "Tell us what went wrong or what you’d like improved."
                    else "Choose the category that best describes your concern.",
                    style = MaterialTheme.typography.bodyMedium
                )
                SafetyCategoryPicker(categories, category, enabled = !sending) { category = it }
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 1500) description = it },
                    label = { Text(if (requiresDescription) "Details (required)" else "Details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 5,
                    supportingText = { Text("${description.length} / 1,500") },
                    enabled = !sending
                )
                Text(
                    "Leave out personal details, links, screenshots, and files.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (error.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = colors.errorContainer, contentColor = colors.onErrorContainer) {
                        Text(error, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !sending && category.isNotEmpty() && (!requiresDescription || description.isNotBlank()),
                onClick = {
                    sending = true
                    error = ""
                    scope.launch {
                        try {
                            SafetyService.call("submitSafetyEntry", mapOf(
                                "kind" to target.kind, "category" to category, "description" to description,
                                "requestId" to requestId, "reportedUserId" to target.reportedUserId,
                                "matchId" to target.matchId, "messageId" to target.messageId
                            ))
                            sent = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = safetyError(failure)
                        } finally {
                            sending = false
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = colors.primaryContainer,
                    disabledContentColor = colors.onSurfaceVariant
                )
            ) { Text(if (sending) "Sending…" else if (feedback) "Send feedback" else "Submit report") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") } }
    )
}

@Composable
private fun SafetyCategoryPicker(
    choices: Map<String, String>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Category (required)", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (expanded) colors.primary else colors.outline)
            ) {
                Text(choices[selected] ?: "Choose a category", Modifier.weight(1f), color = colors.onSurface)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.primary)
            }
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 210.dp)
            ) {
                for ((key, label) in choices) {
                    DropdownMenuItem(
                        text = { Text(label, fontWeight = if (key == selected) FontWeight.SemiBold else FontWeight.Normal) },
                        trailingIcon = if (key == selected) ({ Icon(Icons.Rounded.Check, "Selected") }) else null,
                        colors = MenuDefaults.itemColors(textColor = colors.onSurface, trailingIconColor = colors.primary),
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SafetyChoices(choices: Map<String, String>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    choices.forEach { (key, label) ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = key == selected, onClick = { onSelect(key) }, enabled = enabled)
            TextButton(onClick = { onSelect(key) }, enabled = enabled) { Text(label) }
        }
    }
}

@Composable
internal fun BlockUserDialog(owner: String, target: String, onDismiss: () -> Unit, onBlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Block this user?") },
        text = { Text(error.ifEmpty { "VEIL will close your direct chat and prevent new direct messages and requests between you." }) },
        confirmButton = {
            Button(enabled = !sending, onClick = {
                sending = true
                scope.launch {
                    try {
                        SafetyService.block(owner, target)
                        onBlocked()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = safetyError(failure)
                    } finally {
                        sending = false
                    }
                }
            }) { Text(if (sending) "Blocking…" else "Block user") }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HelpSafetyScreen(onBack: () -> Unit, onOpenModeration: () -> Unit) {
    var formKind by rememberSaveable { mutableStateOf("") }
    var moderator by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun openLink(url: String) {
        if (runCatching { uriHandler.openUri(url) }.isFailure) {
            scope.launch { snackbar.showSnackbar("Couldn’t open the link. Contact $SUPPORT_EMAIL.") }
        }
    }
    LaunchedEffect(Unit) {
        try {
            moderator = SafetyService.isModerator()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { }
    }

    SafetyCenterTheme {
        val colors = MaterialTheme.colorScheme
        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text("Help & Safety", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.onBackground,
                        navigationIconContentColor = colors.onBackground
                    )
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.primaryContainer,
                    contentColor = colors.onSurface,
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    SafetyHubRow("Report a safety concern", "Tell VEIL about harassment, abuse, or a child safety concern.",
                        Icons.Rounded.Flag, onClick = { formKind = "concern" })
                }
                SafetyHubSection("Policies") {
                    SafetyHubRow("Child Safety Standards", "Read VEIL’s child safety standards and reporting process.",
                        Icons.Rounded.Shield, external = true, onClick = { openLink(CHILD_SAFETY_URL) })
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SafetyHubRow("Privacy Policy", "See how VEIL handles your information.",
                        Icons.Rounded.Lock, external = true, onClick = { openLink(PRIVACY_URL) })
                }
                SafetyHubSection("Support") {
                    SafetyHubRow("Send feedback", "Report an app problem or suggest an improvement.",
                        Icons.Rounded.Feedback, onClick = { formKind = "feedback" })
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SafetyHubRow("Email support", SUPPORT_EMAIL,
                        Icons.Rounded.Email, external = true, onClick = { openLink("mailto:$SUPPORT_EMAIL") })
                }
                if (moderator) {
                    SafetyHubSection("Moderation") {
                        SafetyHubRow("Safety moderation", "Review safety reports and feedback.",
                            Icons.Rounded.Shield, onClick = onOpenModeration)
                    }
                }
            }
        }
        when (formKind) {
            "feedback" -> SafetyEntryDialog(SafetyTarget("feedback", "Send feedback")) { formKind = "" }
            "concern" -> SafetyEntryDialog(SafetyTarget("concern", "Report a safety concern")) { formKind = "" }
        }
    }
}

@Composable
private fun SafetyCenterTheme(content: @Composable () -> Unit) {
    val dark = LocalVeilIsDark.current
    val background = if (dark) Color(0xFF131017) else Color(0xFFFAF5F2)
    val card = if (dark) Color(0xFF231E27) else Color.White
    val heading = if (dark) Color(0xFFF3EAF0) else Color(0xFF30252C)
    val detail = if (dark) Color(0xFFCCBEC8) else Color(0xFF63545E)
    val accent = if (dark) Color(0xFFD3ADC5) else Color(0xFF734D67)
    val accentSurface = if (dark) Color(0xFF3F2C3A) else Color(0xFFF0E3EB)
    val line = if (dark) Color(0xFF493E48) else Color(0xFFE4D8DF)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = background,
            onBackground = heading,
            surface = card,
            onSurface = heading,
            surfaceContainer = card,
            surfaceContainerHigh = card,
            surfaceTint = card,
            onSurfaceVariant = detail,
            outline = detail,
            outlineVariant = line,
            primary = accent,
            onPrimary = if (dark) Color(0xFF30202A) else Color.White,
            primaryContainer = accentSurface,
            onPrimaryContainer = heading
        ),
        content = content
    )
}

@Composable
private fun SafetyHubSection(title: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.padding(start = 3.dp), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SafetyHubRow(
    title: String,
    detail: String,
    icon: ImageVector,
    external: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(
            onClickLabel = if (external) "Open $title" else null,
            onClick = onClick
        ).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(11.dp), color = colors.primaryContainer) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = colors.primary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Icon(
            if (external) Icons.Rounded.OpenInNew else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.primary
        )
    }
}
