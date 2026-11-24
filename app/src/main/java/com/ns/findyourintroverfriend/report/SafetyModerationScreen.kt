package com.ns.findyourintroverfriend.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private enum class SafetyQueue(val label: String) {
    PENDING("Pending"), HISTORY("History"), FEEDBACK("Feedback")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SafetyModerationScreen(onBack: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var queue by remember { mutableStateOf(SafetyQueue.PENDING) }
    var refresh by remember { mutableIntStateOf(0) }
    var allowed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var selected by remember { mutableStateOf<DocumentSnapshot?>(null) }

    LaunchedEffect(queue, refresh) {
        loading = true
        entries = emptyList()
        error = ""
        try {
            allowed = SafetyService.isModerator()
            if (allowed) {
                val reports = db.collection("safetyReports")
                val query = when (queue) {
                    SafetyQueue.PENDING -> reports.whereLessThan("queueRank", 2).orderBy("queueRank")
                    SafetyQueue.HISTORY -> reports.orderBy("reviewedAt", Query.Direction.DESCENDING)
                    SafetyQueue.FEEDBACK -> db.collection("safetyFeedback").orderBy("createdAt", Query.Direction.DESCENDING)
                }
                entries = query.limit(100).get(Source.SERVER).await().documents
                    .let { documents ->
                        if (queue == SafetyQueue.PENDING) documents.sortedWith(compareBy(
                            { it.getLong("queueRank") }, { it.getTimestamp("createdAt") }
                        )) else documents
                    }
            } else error = "Safety moderator access is required."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = safetyError(failure)
        } finally {
            loading = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Safety moderation") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            actions = { TextButton(onClick = { refresh++ }) { Text("Refresh") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (allowed) Row {
                SafetyQueue.entries.forEach { tab ->
                    TextButton(onClick = { queue = tab }, enabled = tab != queue) { Text(tab.label) }
                }
            }
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error.isNotEmpty() -> Text(error)
                entries.isEmpty() -> Text("No entries to show.", Modifier.padding(top = 12.dp))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        OutlinedCard(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val category = entry.getString("category").orEmpty()
                                Text(category.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                                if (entry.getString("kind") == "ai_reply") {
                                    Text("VEIL Friends AI", style = MaterialTheme.typography.bodySmall)
                                }
                                val highPriority = entry.getString("priority") == "high"
                                Text(if (highPriority) "High priority" else entry.getString("status").orEmpty(),
                                    color = if (highPriority) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                entry.getTimestamp("createdAt")?.let { Text(it.toDate().toString(), style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { report ->
        ReviewEntry(report, queue, onDismiss = { selected = null }, onReviewed = { selected = null; refresh++ })
    }
}

@Composable
private fun ReviewEntry(entry: DocumentSnapshot, queue: SafetyQueue, onDismiss: () -> Unit, onReviewed: () -> Unit) {
    val scope = rememberCoroutineScope()
    val target = entry.getString("reportedUserId").orEmpty()
    val canReview = queue == SafetyQueue.PENDING
    var action by remember(entry.id) { mutableStateOf("reviewed") }
    var note by remember(entry.id) { mutableStateOf("") }
    var alias by remember(entry.id) { mutableStateOf("") }
    var auditNotes by remember(entry.id) { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var error by remember(entry.id) { mutableStateOf("") }
    var saving by remember(entry.id) { mutableStateOf(false) }
    var removeMessage by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        try {
            if (target.isNotEmpty()) {
                val profile = FirebaseFirestore.getInstance().collection("users").document(target).get(Source.SERVER).await()
                alias = profile.getString("alias").orEmpty()
            }
            if (queue == SafetyQueue.HISTORY) {
                auditNotes = entry.reference.collection("actions").get(Source.SERVER).await().documents
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Some account or audit details could not be loaded."
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (queue == SafetyQueue.FEEDBACK) "Feedback" else "Safety report") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Case ID  ${entry.id}")
                Text("Category  ${entry.getString("category").orEmpty().replace('_', ' ')}")
                Text("Reporter  ${entry.getString("reporterId").orEmpty()}")
                if (target.isNotEmpty()) Text("Reported account  ${alias.ifEmpty { target }}\n$target")
                Text(entry.getString("description").orEmpty().ifEmpty { "No additional details supplied." })
                entry.getString("aiReply.content")?.let { reply ->
                    Text("Reported AI reply from ${entry.getString("aiReply.friend").orEmpty()}")
                    Text(reply, style = MaterialTheme.typography.bodyMedium)
                }
                val matchId = entry.getString("messageReference.matchId")
                val messageId = entry.getString("messageReference.messageId")
                if (matchId != null && messageId != null) {
                    Text("Conversation  $matchId\nMessage  $messageId")
                    entry.getLong("messageReference.sentAt")?.let { Text("Message sent  ${java.util.Date(it)}") }
                } else {
                    entry.getString("conversationId")?.takeIf { it.isNotEmpty() }?.let { Text("Conversation  $it") }
                }
                if (canReview) {
                    val actions = linkedMapOf("reviewed" to "Mark reviewed")
                    if (target.isNotEmpty()) {
                        actions["suspend"] = "Suspend for 7 days"
                        actions["ban"] = "Permanently ban"
                    }
                    SafetyChoices(actions, action, !saving) { action = it }
                    if (messageId != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = removeMessage, onCheckedChange = { removeMessage = it }, enabled = !saving)
                        Text("Remove the source message from VEIL chat")
                    }
                    OutlinedTextField(note, { if (it.length <= 1500) note = it }, enabled = !saving,
                        label = { Text("Reason for decision") }, modifier = Modifier.fillMaxWidth())
                }
                auditNotes.forEach { decision ->
                    Text("Decision  ${decision.getString("action").orEmpty()}")
                    Text("Moderator  ${decision.getString("moderatorId").orEmpty()}")
                    if (decision.getBoolean("removedMessage") == true) Text("Source message removed from chat")
                    decision.getTimestamp("reviewedAt")?.let { Text(it.toDate().toString()) }
                    Text(decision.getString("note").orEmpty())
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            if (canReview) Button(enabled = !saving && note.isNotBlank(), onClick = {
                saving = true
                scope.launch {
                    try {
                        SafetyService.call("reviewSafetyReport", mapOf(
                            "id" to entry.id, "action" to action, "note" to note, "removeMessage" to removeMessage
                        ))
                        onReviewed()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = safetyError(failure)
                    } finally {
                        saving = false
                    }
                }
            }) { Text(if (saving) "Saving…" else "Save decision") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") } }
    )
}
