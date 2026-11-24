package com.ns.findyourintroverfriend.aifriend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.ui.theme.currentProfilePalette
import kotlinx.coroutines.launch

@Composable
fun AiFriendChatScreen(
    friendId: String,
    conversationId: String,
    onBack: () -> Unit,
    onOpenCookieStore: () -> Unit
) {
    val friend = VeilFriends.find(friendId)
    val colors = currentProfilePalette
    if (friend == null) {
        Scaffold(
            containerColor = colors.background,
            topBar = { AiTopBar(onBack = onBack) { Text("VEIL Friends AI", color = colors.text) } }
        ) { padding ->
            Text("This AI friend is unavailable.", Modifier.padding(padding).padding(20.dp), color = colors.text)
        }
        return
    }

    val model: AiFriendViewModel = viewModel()
    val chat by model.chat.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val memories by model.memories.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var memoriesOpen by rememberSaveable { mutableStateOf(false) }
    var reportMessageId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(friend.id, conversationId) { model.open(friend.id, conversationId) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshUsage() }
    LaunchedEffect(memoriesOpen) { if (memoriesOpen) model.loadMemories() }
    LaunchedEffect(chat.pending?.id) { if (chat.pending != null) listState.scrollToItem(0) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            AiTopBar(onBack, actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Chat options", tint = colors.text)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = colors.card
                    ) {
                        DropdownMenuItem(
                            text = { Text("Saved memories", color = colors.text) },
                            onClick = {
                                menuOpen = false
                                memoriesOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cookie Store", color = colors.text) },
                            onClick = {
                                menuOpen = false
                                onOpenCookieStore()
                            }
                        )
                    }
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AiPortrait(friend, 42.dp)
                    Column {
                        Text(friend.name, style = MaterialTheme.typography.titleMedium, color = colors.text)
                        Text("AI friend · ${friend.style}", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            HorizontalDivider(color = colors.border)
            AiUsageBar(chat, onRetry = model::retryConnection)
            val pending = chat.pending
            val messages = if (pending != null && chat.messages.none { it.id == pending.id }) {
                chat.messages + pending
            } else {
                chat.messages
            }
            val serviceUnavailable = chat.serviceIssue?.blocksSending == true
            val historyError = chat.historyError.takeUnless { serviceUnavailable && messages.isEmpty() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    chat.findingConversation -> CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(26.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp
                    )
                    chat.conversationError != null && !serviceUnavailable -> Box(Modifier.align(Alignment.Center)) {
                        AiNotice(chat.conversationError.orEmpty(), "Retry", model::retryConnection)
                    }
                    messages.isEmpty() && historyError == null -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        item { AiStarters(friend, onSelect = model::editDraft) }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (chat.sending) {
                                item(key = "replying") {
                                    Text(
                                        "${friend.name} is replying…",
                                        modifier = Modifier.padding(start = 10.dp)
                                            .semantics { liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                            items(messages.asReversed(), key = { it.id }) { message ->
                                AiBubble(message, isPending = pending?.id == message.id) {
                                    reportMessageId = message.id
                                }
                            }
                            historyError?.let { error ->
                                item(key = "history-error") { AiNotice(error, "Retry", model::retryHistory) }
                            }
                            if (chat.hasOlder) {
                                item(key = "load-earlier") {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                        TextButton(onClick = model::loadEarlier, enabled = !chat.loadingOlder) {
                                            Text(if (chat.loadingOlder) "Loading…" else "Load earlier messages", color = colors.accent)
                                        }
                                    }
                                }
                            }
                        }
                        if (messages.isNotEmpty() && listState.firstVisibleItemIndex > 2) {
                            Surface(
                                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                                shape = CircleShape,
                                color = colors.card,
                                border = BorderStroke(1.dp, colors.border),
                                shadowElevation = 2.dp
                            ) {
                                Text("Latest", Modifier.padding(horizontal = 16.dp, vertical = 9.dp), color = colors.accent)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.border)
            AiComposer(
                draft = draft,
                canSend = chat.canSend,
                error = chat.error,
                onEdit = model::editDraft,
                onSend = model::send
            )
        }
    }

    chat.payment?.let { payment ->
        val price = chat.usage?.cookieCost ?: 1
        val balance = chat.usage?.cookieBalance ?: 0
        val needsCookies = payment == AiPayment.Insufficient
        val cookieLabel = if (price == 1) "1 cookie" else "$price cookies"
        AlertDialog(
            onDismissRequest = model::dismissPayment,
            containerColor = colors.card,
            title = { Text(if (needsCookies) "Get cookies to continue" else "Send for $cookieLabel?", color = colors.text) },
            text = {
                Text(
                    "You've used your free replies. This reply costs $cookieLabel. Your balance is $balance.",
                    color = colors.secondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (needsCookies) {
                            model.dismissPayment()
                            onOpenCookieStore()
                        } else {
                            model.confirmPayment()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                ) {
                    Text(if (needsCookies) "Cookie Store" else "Send for $cookieLabel")
                }
            },
            dismissButton = { TextButton(onClick = model::dismissPayment) { Text("Cancel", color = colors.accent) } }
        )
    }

    if (memoriesOpen) {
        AiMemoriesDialog(
            memories = memories,
            onDismiss = { memoriesOpen = false },
            onRetry = model::loadMemories,
            onForget = model::forgetMemories
        )
    }
    reportMessageId?.let { messageId ->
        AiReportDialog(
            onDismiss = { reportMessageId = null },
            onSubmit = { reason -> model.reportReply(messageId, reason) }
        )
    }
}

@Composable
private fun AiUsageBar(chat: AiChat, onRetry: () -> Unit) {
    val colors = currentProfilePalette
    val usage = chat.usage
    Box(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center
    ) {
        val issue = chat.serviceIssue
        if (issue != null) {
            AiNotice(issue.message, "Retry", onRetry, enabled = !chat.statusLoading)
        } else {
            val text = when {
                usage == null -> "Connecting to VEIL Friends…"
                usage.freeRemaining > 0 -> "${usage.freeRemaining} of ${usage.freeLimit} free replies left"
                else -> "${usage.cookieCost} cookie per reply · ${usage.cookieBalance} in your wallet"
            }
            Text(
                text, Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelMedium, color = colors.accent
            )
        }
    }
}

@Composable
private fun AiStarters(friend: AiFriend, onSelect: (String) -> Unit) {
    val colors = currentProfilePalette
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AiPortrait(friend, 80.dp)
        Text("Talk with ${friend.name}", style = MaterialTheme.typography.headlineSmall, color = colors.text)
        Text(friend.description, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryText)
        Text(
            "Start with a prompt or write your own message.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            modifier = Modifier.padding(top = 6.dp)
        )
        friend.starters.forEach { starter ->
            Surface(
                onClick = { onSelect(starter) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                color = colors.card,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Text(starter, Modifier.padding(horizontal = 16.dp, vertical = 13.dp), color = colors.text)
            }
        }
    }
}

@Composable
private fun AiBubble(message: AiMessage, isPending: Boolean, onReport: () -> Unit) {
    val colors = currentProfilePalette
    val sent = message.sender == "user"
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val bubbleWidth = minOf(maxWidth * 0.86f, 520.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                modifier = Modifier.widthIn(max = bubbleWidth),
                color = if (sent) colors.tintedSurface else colors.card,
                border = BorderStroke(1.dp, colors.border),
                shape = if (sent) {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
                } else {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
                }
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(message.content, color = colors.text, style = MaterialTheme.typography.bodyMedium)
                    if (isPending) {
                        Text("Sending…", Modifier.padding(top = 4.dp), color = colors.secondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (message.sender == "assistant") {
                IconButton(onClick = onReport) {
                    Icon(
                        Icons.Default.Flag, contentDescription = "Report AI reply", tint = colors.secondaryText,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AiComposer(draft: String, canSend: Boolean, error: String?, onEdit: (String) -> Unit, onSend: () -> Unit) {
    val colors = currentProfilePalette
    val enabled = canSend && draft.isNotBlank()
    Surface(color = colors.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            error?.let {
                Text(
                    it,
                    Modifier.padding(start = 4.dp, bottom = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    color = colors.accent,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onEdit,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message…", color = colors.secondaryText) },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        cursorColor = colors.accent
                    )
                )
                Surface(
                    onClick = onSend,
                    enabled = enabled,
                    shape = CircleShape,
                    color = if (enabled) colors.accent else colors.tintedSurface,
                    modifier = Modifier.padding(bottom = 4.dp).size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (enabled) colors.onAccent else colors.secondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (draft.length >= AI_MESSAGE_LIMIT - 50) {
                Text(
                    "${draft.length}/$AI_MESSAGE_LIMIT",
                    Modifier.align(Alignment.End).padding(top = 4.dp, end = 55.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            }
        }
    }
}

@Composable
private fun AiNotice(message: String, action: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = currentProfilePalette
    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(message, color = colors.secondaryText, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f, fill = false))
        TextButton(onClick = onClick, enabled = enabled) {
            Text(if (enabled) action else "Checking…", color = colors.accent)
        }
    }
}

@Composable
private fun AiMemoriesDialog(
    memories: AiMemoryState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onForget: (List<String>) -> Unit
) {
    val colors = currentProfilePalette
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (confirmAll) confirmAll = false else onDismiss() },
        containerColor = colors.card,
        title = { Text(if (confirmAll) "Forget all saved memories?" else "Saved memories", color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    confirmAll -> Text("This removes the saved memories for this friend.", color = colors.secondaryText)
                    memories.loading -> CircularProgressIndicator(Modifier.size(22.dp), color = colors.accent, strokeWidth = 2.dp)
                    memories.error != null -> AiNotice(memories.error, "Retry", onRetry)
                    memories.memories.isEmpty() -> Text("You don't have any saved memories for this friend.", color = colors.secondaryText)
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(memories.memories, key = { it.id }) { memory ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(memory.text, Modifier.weight(1f), color = colors.text)
                                IconButton(onClick = { onForget(listOf(memory.id)) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Forget memory", tint = colors.accent)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (confirmAll) {
                    onForget(memories.memories.map { it.id })
                    confirmAll = false
                } else {
                    onDismiss()
                }
            }) { Text(if (confirmAll) "Forget all" else "Done", color = colors.accent) }
        },
        dismissButton = {
            if (confirmAll || (memories.memories.isNotEmpty() && !memories.loading && memories.error == null)) {
                TextButton(onClick = { confirmAll = !confirmAll }) {
                    Text(if (confirmAll) "Cancel" else "Forget all", color = colors.accent)
                }
            }
        }
    )
}

private val aiReportReasons = listOf(
    "inappropriate" to "Inappropriate content",
    "child_safety" to "Child safety concern",
    "threats" to "Threats or violence",
    "harassment" to "Harassment or bullying",
    "other" to "Something else"
)

@Composable
private fun AiReportDialog(onDismiss: () -> Unit, onSubmit: suspend (String) -> String?) {
    val colors = currentProfilePalette
    val scope = rememberCoroutineScope()
    var reason by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var reported by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        containerColor = colors.card,
        title = { Text(if (reported) "Report received" else "Report AI reply", color = colors.text) },
        text = {
            if (reported) {
                Text("The VEIL team will review this reply.", color = colors.secondaryText)
            } else {
                LazyColumn(Modifier.heightIn(max = 330.dp).selectableGroup()) {
                    item { Text("Only this AI reply will be shared with the moderation team.", color = colors.secondaryText) }
                    items(aiReportReasons, key = { it.first }) { (id, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .selectable(reason == id, !submitting, Role.RadioButton, onClick = { reason = id }),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = reason == id,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colors.accent, unselectedColor = colors.secondaryText
                                )
                            )
                            Text(label, color = colors.text)
                        }
                    }
                    error?.let { message -> item { Text(message, color = colors.accent) } }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = reported || (!submitting && reason.isNotBlank()),
                onClick = {
                    if (reported) onDismiss()
                    else {
                        submitting = true
                        scope.launch {
                            try {
                                error = onSubmit(reason)
                                reported = error == null
                            } finally {
                                submitting = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) {
                Text(if (reported) "Done" else if (submitting) "Sending…" else "Send report")
            }
        },
        dismissButton = {
            if (!reported) {
                TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel", color = colors.accent) }
            }
        }
    )
}
