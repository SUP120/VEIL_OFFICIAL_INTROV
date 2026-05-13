package com.ns.findyourintroverfriend.day2

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
//  Palette — warm ivory / amber (matches app theme) + warm rose for compliments
// ─────────────────────────────────────────────────────────────────────────────
private val SCBg       = Color(0xFFFDF8F2)   // warm ivory
private val SCCard     = Color(0xFFFFFFFF)   // pure white card
private val SCElev     = Color(0xFFFFF4E8)   // peach-cream elevated
private val SCBorder   = Color(0xFFEDD9C0)   // warm beige border
private val SCBorderA  = Color(0xFFE8832A)   // amber accent border
private val SCTxtP     = Color(0xFF2D1F0E)   // deep warm brown
private val SCTxtS     = Color(0xFF7A5C3A)   // medium warm brown
private val SCTxtT     = Color(0xFFB08A60)   // light warm tan
private val SCRose     = Color(0xFFE05A7A)   // warm rose — kept vibrant for compliments
private val SCRoseDim  = Color(0xFFFDE8EE)   // light rose tint
private val SCGreen    = Color(0xFF2E9668)
private val SCGreenDim = Color(0xFFE6F5EE)

// ─────────────────────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────────────────────
data class CirclePeer(val uid: String, val alias: String)

data class Compliment(
    val id        : String = "",
    val fromUid   : String = "",
    val toUid     : String = "",
    val groupId   : String = "",
    val message   : String = "",
    val timestamp : Long   = 0L
)

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SecretComplimentScreen(onBack: () -> Unit = {}) {
    val db    = remember { FirebaseFirestore.getInstance() }
    val auth  = remember { FirebaseAuth.getInstance() }
    val myUid = remember { auth.currentUser?.uid ?: "" }
    val scope = rememberCoroutineScope()

    var groupId  by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(true) }

    // Circle members (excluding self)
    var peers by remember { mutableStateOf<List<CirclePeer>>(emptyList()) }

    // Compliment I sent this group-session
    var sentCompliment by remember { mutableStateOf<Compliment?>(null) }

    // Compliments I received
    var received by remember { mutableStateOf<List<Compliment>>(emptyList()) }

    // Input
    var selectedPeer by remember { mutableStateOf<CirclePeer?>(null) }
    var msgText      by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError  by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    val hasSent = sentCompliment != null

    // ── Load group + members ──────────────────────────────────────────────────
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) { loading = false; return@LaunchedEffect }
        try {
            val userDoc = db.collection("users").document(myUid).get().await()
            groupId = userDoc.getString("groupId") ?: ""

            if (groupId.isNotBlank()) {
                // Load peers: other users in same group
                val snap = db.collection("users")
                    .whereEqualTo("groupId", groupId)
                    .get().await()
                peers = snap.documents
                    .filter { it.id != myUid }
                    .mapNotNull { doc ->
                        val alias = doc.getString("alias") ?: return@mapNotNull null
                        CirclePeer(uid = doc.id, alias = alias)
                    }
            }
        } catch (_: Exception) {}
        loading = false
    }

    // ── Sent compliment listener ──────────────────────────────────────────────
    DisposableEffect(groupId, myUid) {
        if (groupId.isBlank() || myUid.isBlank()) return@DisposableEffect onDispose {}
        val reg = db.collection("secret_compliments")
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("fromUid", myUid)
            .addSnapshotListener { snap, _ ->
                sentCompliment = snap?.documents?.firstOrNull()?.let { doc ->
                    Compliment(
                        id        = doc.id,
                        fromUid   = doc.getString("fromUid") ?: "",
                        toUid     = doc.getString("toUid") ?: "",
                        groupId   = doc.getString("groupId") ?: "",
                        message   = doc.getString("message") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }
            }
        onDispose { reg.remove() }
    }

    // ── Received compliments listener ─────────────────────────────────────────
    DisposableEffect(groupId, myUid) {
        if (groupId.isBlank() || myUid.isBlank()) return@DisposableEffect onDispose {}
        val reg = db.collection("secret_compliments")
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("toUid", myUid)
            .addSnapshotListener { snap, _ ->
                received = snap?.documents?.mapNotNull { doc ->
                    try {
                        Compliment(
                            id        = doc.id,
                            fromUid   = doc.getString("fromUid") ?: "",
                            toUid     = doc.getString("toUid") ?: "",
                            groupId   = doc.getString("groupId") ?: "",
                            message   = doc.getString("message") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (_: Exception) { null }
                }?.sortedByDescending { it.timestamp } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    // ── Submit ────────────────────────────────────────────────────────────────
    fun sendCompliment() {
        val peer = selectedPeer ?: run { submitError = "Pick someone first."; return }
        if (msgText.isBlank()) { submitError = "Write something kind first."; return }
        if (isSubmitting) return
        isSubmitting = true
        submitError  = ""
        scope.launch {
            try {
                // Doc keyed by groupId+fromUid → one compliment per sender per group
                db.collection("secret_compliments")
                    .document("${groupId}_${myUid}")
                    .set(mapOf(
                        "fromUid"   to myUid,
                        "toUid"     to peer.uid,
                        "groupId"   to groupId,
                        "toAlias"   to peer.alias,   // stored so sender can recall who they sent to
                        "message"   to msgText.trim(),
                        "timestamp" to System.currentTimeMillis()
                    ))
                    .await()
            } catch (_: Exception) {
                submitError = "Could not send. Check connection and try again."
            } finally {
                isSubmitting = false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(SCBg, Color(0xFFFFF4E8), Color(0xFFFEEDD8))
                )
            )
    ) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color       = SCRose,
                    strokeWidth = 2.dp,
                    modifier    = Modifier.size(28.dp)
                )
            }
            return@Box
        }

        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 56.dp)
        ) {

            // ── Top bar ─────────────────────────────────────────────────────
            item(key = "topbar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ScBackBtn(onBack)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(0.8.dp, SCTxtS.copy(0.3f), CircleShape)
                        ) {
                            androidx.compose.foundation.Image(
                                painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_secret_comp),
                                contentDescription = null,
                                modifier     = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        Text(
                            "Secret Compliment",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color      = SCTxtP,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 17.sp
                            )
                        )
                        Text(
                            "Anonymous · One compliment per circle",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = SCTxtS,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Spacer(Modifier.size(38.dp))
                }
                HorizontalDivider(color = SCBorder, thickness = 0.5.dp)
            }

            // ── PRE-SEND: Peer picker + message input ──────────────────────
            if (!hasSent) {
                item(key = "picker_label") {
                    Spacer(Modifier.height(14.dp))
                    ScSectionLabel("WHO DO YOU WANT TO COMPLIMENT?", Icons.Rounded.FavoriteBorder)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "They'll receive it anonymously — they won't know it's from you",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SCTxtT, fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Peer list
                item(key = "peer_list") {
                    if (peers.isEmpty()) {
                        Box(
                            modifier         = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Your circle members will appear here once they've joined.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color     = SCTxtS,
                                    fontSize  = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    } else {
                        Column(
                            modifier            = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            peers.forEach { peer ->
                                val isSelected = selectedPeer?.uid == peer.uid
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) SCRose.copy(0.09f) else SCCard
                                        )
                                        .border(
                                            if (isSelected) 1.dp else 0.7.dp,
                                            if (isSelected) SCRose.copy(0.45f) else SCBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            selectedPeer =
                                                if (selectedPeer?.uid == peer.uid) null else peer
                                        }
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(SCElev)
                                            .border(
                                                0.8.dp,
                                                if (isSelected) SCRose.copy(0.4f) else SCBorderA,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            peer.alias.take(1).uppercase(),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color      = if (isSelected) SCRose else SCTxtS,
                                                fontWeight = FontWeight.Bold,
                                                fontSize   = 14.sp
                                            )
                                        )
                                    }
                                    Text(
                                        peer.alias,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color      = if (isSelected) SCTxtP else SCTxtS,
                                            fontSize   = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle, null,
                                            tint     = SCRose,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Message input — appears when peer selected
                item(key = "msg_input") {
                    AnimatedVisibility(
                        visible = selectedPeer != null,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = SCBorder, thickness = 0.5.dp)
                            Spacer(Modifier.height(4.dp))
                            ScSectionLabel("WRITE YOUR COMPLIMENT", Icons.Rounded.Edit)

                            // Recipient chip
                            selectedPeer?.let { peer ->
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        "To",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = SCTxtT, fontSize = 11.sp
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SCRose.copy(0.12f))
                                            .border(0.6.dp, SCRose.copy(0.3f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 9.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            peer.alias,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color      = SCRose,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize   = 11.sp
                                            )
                                        )
                                    }
                                    Text(
                                        "· anonymous sender",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = SCTxtT, fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            // Text field
                            OutlinedTextField(
                                value         = msgText,
                                onValueChange = { if (it.length <= 200) msgText = it },
                                placeholder   = {
                                    Text(
                                        "Say something kind, honest, or meaningful…",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = SCTxtT, fontSize = 13.sp
                                        )
                                    )
                                },
                                minLines  = 3,
                                maxLines  = 5,
                                shape     = RoundedCornerShape(12.dp),
                                colors    = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor      = SCRose.copy(0.5f),
                                    unfocusedBorderColor    = SCBorder,
                                    focusedTextColor        = SCTxtP,
                                    unfocusedTextColor      = SCTxtP,
                                    focusedContainerColor   = SCCard,
                                    unfocusedContainerColor = SCCard,
                                    cursorColor             = SCRose
                                ),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = SCTxtP, fontSize = 13.sp, lineHeight = 19.sp
                                ),
                                trailingIcon = {
                                    Text(
                                        "${msgText.length}/200",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = SCTxtT, fontSize = 9.sp
                                        ),
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Error
                            AnimatedVisibility(visible = submitError.isNotBlank()) {
                                Text(
                                    submitError,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFE57373), fontSize = 11.sp
                                    )
                                )
                            }

                            // Send button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SCElev)
                                    .border(0.8.dp, SCBorderA, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isSubmitting) { sendCompliment() }
                                    .padding(vertical = 15.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(18.dp),
                                        color       = SCRose,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Favorite, null,
                                            tint     = SCRose,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "Send Anonymously",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color      = SCTxtP,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize   = 13.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── POST-SEND: Success + what I sent ──────────────────────────
            if (hasSent) {
                item(key = "sent_success") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Rose success banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SCRoseDim.copy(0.5f))
                                .border(0.8.dp, SCRose.copy(0.28f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Favorite, null,
                                tint     = SCRose,
                                modifier = Modifier.size(17.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Compliment sent — they'll never know it was you.",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color      = SCRose,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize   = 13.sp
                                    )
                                )
                                sentCompliment?.let { sc ->
                                    val toAlias = peers.firstOrNull { it.uid == sc.toUid }?.alias
                                        ?: "someone in the circle"
                                    Text(
                                        "Sent to $toAlias · anonymous",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color    = SCTxtS,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── RECEIVED section ──────────────────────────────────────────
            item(key = "received_header") {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = SCBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ScSectionLabel(
                        if (received.isEmpty()) "YOUR COMPLIMENTS"
                        else "YOUR COMPLIMENTS (${received.size})",
                        Icons.Rounded.Inbox
                    )
                    Text(
                        "All anonymous",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SCTxtT, fontSize = 9.sp
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Empty received state
            if (received.isEmpty()) {
                item(key = "received_empty") {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FavoriteBorder, null,
                                tint     = SCTxtT,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                if (hasSent) "No compliments received yet…"
                                else "Send one first — you might get one back!",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color     = SCTxtS,
                                    fontSize  = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            }

            // Received cards
            items(received, key = { "sc_${it.id}" }) { c ->
                ComplimentCard(compliment = c)
                Spacer(Modifier.height(8.dp))
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Compliment card (received — always anonymous)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ComplimentCard(compliment: Compliment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SCCard)
            .border(0.7.dp, SCBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top
    ) {
        // Rose heart icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SCRoseDim.copy(0.5f))
                .border(0.7.dp, SCRose.copy(0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Favorite, null,
                tint     = SCRose,
                modifier = Modifier.size(15.dp)
            )
        }

        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "From: someone in your circle",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SCTxtT, fontSize = 10.sp
                    )
                )
                Text(
                    scRelativeTime(compliment.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SCTxtT, fontSize = 9.sp
                    )
                )
            }

            Text(
                compliment.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = SCTxtP,
                    fontSize   = 13.sp,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ScBackBtn(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(SCCard)
            .border(0.7.dp, SCBorder, CircleShape)
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.ArrowBackIosNew, null,
            tint     = SCTxtP.copy(0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ScSectionLabel(
    text : String,
    icon : androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier              = Modifier.padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, tint = SCTxtT, modifier = Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                color         = SCTxtT,
                fontSize      = 9.sp,
                letterSpacing = 1.4.sp,
                fontWeight    = FontWeight.SemiBold
            )
        )
    }
}

private fun scRelativeTime(ts: Long): String {
    if (ts == 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L     -> "just now"
        diff < 3_600_000L  -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else               -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts))
    }
}
