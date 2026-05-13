package com.ns.findyourintroverfriend.vibes

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
//  Palette — warm ivory / amber (matches app theme)
// ─────────────────────────────────────────────────────────────────────────────
private val VBg        = Color(0xFFFDF8F2)   // warm ivory
private val VCard      = Color(0xFFFFFFFF)   // pure white card
private val VElevated  = Color(0xFFFFF4E8)   // peach-cream elevated
private val VBorder    = Color(0xFFEDD9C0)   // warm beige border
private val VBorderAct = Color(0xFFE8832A)   // amber active border
private val VTxtP      = Color(0xFF2D1F0E)   // deep warm brown
private val VTxtS      = Color(0xFF7A5C3A)   // medium warm brown
private val VTxtT      = Color(0xFFB08A60)   // light warm tan
private val VGreen     = Color(0xFF2E9668)   // forest green
private val VGreenDim  = Color(0xFFE6F5EE)   // light green tint

// Color-of-day palette
private val ColorPalette = listOf(
    Color(0xFFEF4444) to "Red",
    Color(0xFFF97316) to "Orange",
    Color(0xFFEAB308) to "Yellow",
    Color(0xFF22C55E) to "Green",
    Color(0xFF3B82F6) to "Blue",
    Color(0xFF8B5CF6) to "Violet",
    Color(0xFFEC4899) to "Pink",
    Color(0xFF14B8A6) to "Teal",
    Color(0xFF6B7280) to "Grey",
    Color(0xFFF1F5F9) to "White"
)

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────
data class VibeEntry(
    val uid        : String = "",
    val alias      : String = "",
    val vibeSong   : String = "",
    val colorHex   : String = "",
    val colorName  : String = "",
    val oneWordBio : String = "",
    val day        : Int    = 1,
    val timestamp  : Long   = 0L
)

// ─────────────────────────────────────────────────────────────────────────────
//  VibesScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VibesScreen(onBack: () -> Unit = {}) {
    val db    = remember { FirebaseFirestore.getInstance() }
    val auth  = remember { FirebaseAuth.getInstance() }
    val myUid = remember { auth.currentUser?.uid ?: "" }
    val scope = rememberCoroutineScope()

    // ── Group info ────────────────────────────────────────────────────────────
    var groupId  by remember { mutableStateOf("") }
    var myAlias  by remember { mutableStateOf("") }
    var groupDay by remember { mutableStateOf(1) }
    var loading  by remember { mutableStateOf(true) }

    // ── Feed ──────────────────────────────────────────────────────────────────
    // Raw list from Firestore — sorted client-side to avoid composite index
    var allEntries by remember { mutableStateOf<List<VibeEntry>>(emptyList()) }

    // ── My own submission ─────────────────────────────────────────────────────
    val myEntry = allEntries.firstOrNull { it.uid == myUid }
    val hasSubmitted = myEntry != null

    // Others sorted newest first (my own excluded from the bottom list)
    val othersEntries = allEntries
        .filter { it.uid != myUid }
        .sortedByDescending { it.timestamp }

    // ── Input state (only used before first submit) ───────────────────────────
    var songInput     by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf<Pair<Color, String>?>(null) }
    var bioInput      by remember { mutableStateOf("") }
    var isSubmitting  by remember { mutableStateOf(false) }
    var submitError   by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // ── Load group & alias ────────────────────────────────────────────────────
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) { loading = false; return@LaunchedEffect }
        try {
            val userDoc = db.collection("users").document(myUid).get().await()
            groupId  = userDoc.getString("groupId") ?: ""
            myAlias  = userDoc.getString("alias") ?: "You"
            if (groupId.isNotBlank()) {
                val grpDoc = db.collection("groups").document(groupId).get().await()
                groupDay = (grpDoc.getLong("day") ?: 1L).toInt()
            }
        } catch (_: Exception) {}
        loading = false
    }

    // ── Real-time feed — simple query, no orderBy (no composite index needed) ─
    DisposableEffect(groupId) {
        if (groupId.isBlank()) return@DisposableEffect onDispose {}
        val reg = db.collection("vibes")
            .whereEqualTo("groupId", groupId)
            // No orderBy — we sort client-side to avoid needing a composite index
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { doc ->
                    try {
                        VibeEntry(
                            uid        = doc.getString("uid") ?: return@mapNotNull null,
                            alias      = doc.getString("alias") ?: "Someone",
                            vibeSong   = doc.getString("vibeSong") ?: "",
                            colorHex   = doc.getString("colorHex") ?: "",
                            colorName  = doc.getString("colorName") ?: "",
                            oneWordBio = doc.getString("oneWordBio") ?: "",
                            day        = (doc.getLong("day") ?: 1L).toInt(),
                            timestamp  = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (_: Exception) { null }
                }
                allEntries = list
            }
        onDispose { reg.remove() }
    }

    // ── Submit ────────────────────────────────────────────────────────────────
    fun submit() {
        if (isSubmitting) return
        if (songInput.isBlank() && selectedColor == null && bioInput.isBlank()) {
            submitError = "Fill at least one field before sharing."
            return
        }
        isSubmitting = true
        submitError  = ""
        scope.launch {
            try {
                val colorHex  = selectedColor?.first?.let {
                    String.format("#%06X", 0xFFFFFF and it.value.toInt())
                } ?: ""
                val colorName = selectedColor?.second ?: ""
                db.collection("vibes")
                    .document("${groupId}_${myUid}")
                    .set(mapOf(
                        "uid"        to myUid,
                        "groupId"    to groupId,
                        "alias"      to myAlias,
                        "vibeSong"   to songInput.trim(),
                        "colorHex"   to colorHex,
                        "colorName"  to colorName,
                        "oneWordBio" to bioInput.trim(),
                        "day"        to groupDay,
                        "timestamp"  to System.currentTimeMillis()
                    ))
                    .await()
                // success — feed listener will update allEntries automatically
            } catch (e: Exception) {
                submitError = "Could not save. Check connection and try again."
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
                    listOf(VBg, Color(0xFFFFF4E8), Color(0xFFFEEDD8))
                )
            )
            .systemBarsPadding()
    ) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(VCard)
                            .border(0.7.dp, VBorder, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.ArrowBackIosNew, null,
                            tint     = VTxtP.copy(0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(0.8.dp, VBorder.copy(0.5f), CircleShape)
                        ) {
                            androidx.compose.foundation.Image(
                                painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_circle_vibes),
                                contentDescription = null,
                                modifier     = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        Text(
                            "Circle Vibes",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = VTxtP, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp
                            )
                        )
                        Text(
                            "Song · Color · One Word",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = VTxtS, fontSize = 10.sp
                            )
                        )
                    }
                    Spacer(Modifier.size(38.dp))
                }
                HorizontalDivider(color = VBorder, thickness = 0.5.dp)
            }

            // ── STATE A: Not yet submitted → show input form ─────────────────
            if (!hasSubmitted) {
                item(key = "form") {
                    AnimatedVisibility(
                        visible = !hasSubmitted,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Section label
                            SectionLabel("YOUR VIBES TODAY", Icons.Rounded.Edit)
                            Spacer(Modifier.height(2.dp))

                            // Vibe Song
                            InputCard(Icons.Rounded.MusicNote, "Vibe Song", "Song you're feeling today") {
                                OutlinedTextField(
                                    value         = songInput,
                                    onValueChange = { songInput = it },
                                    placeholder   = {
                                        Text("Type a Hindi or English song…",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = VTxtS.copy(0.45f), fontSize = 13.sp))
                                    },
                                    singleLine  = true,
                                    shape       = RoundedCornerShape(10.dp),
                                    colors      = tfColors(Color(0xFF60A5FA)),
                                    textStyle   = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp, color = VTxtP),
                                    modifier    = Modifier.fillMaxWidth()
                                )
                            }

                            // Color of the Day
                            InputCard(Icons.Rounded.Palette, "Color of the Day", "Your mood in a color") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ColorPalette.take(5).forEach { (col, name) ->
                                            ColorDot(col, selectedColor?.first == col) {
                                                selectedColor = if (selectedColor?.first == col) null else col to name
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ColorPalette.drop(5).forEach { (col, name) ->
                                            ColorDot(col, selectedColor?.first == col) {
                                                selectedColor = if (selectedColor?.first == col) null else col to name
                                            }
                                        }
                                    }
                                    AnimatedVisibility(visible = selectedColor != null) {
                                        selectedColor?.let { (col, name) ->
                                            Row(
                                                verticalAlignment     = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                                                Text("$name — your mood today",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = VTxtS, fontSize = 11.sp))
                                            }
                                        }
                                    }
                                }
                            }

                            // One Word Bio
                            InputCard(Icons.Rounded.Title, "One Word Bio", "Describe yourself in one word") {
                                OutlinedTextField(
                                    value         = bioInput,
                                    onValueChange = { if (it.length <= 20 && !it.contains(" ")) bioInput = it },
                                    placeholder   = {
                                        Text("One word only…",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = VTxtS.copy(0.45f), fontSize = 14.sp))
                                    },
                                    singleLine    = true,
                                    shape         = RoundedCornerShape(10.dp),
                                    colors        = tfColors(VTxtS),
                                    textStyle     = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VTxtP),
                                    trailingIcon  = {
                                        Text("${bioInput.length}/20",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = VTxtT, fontSize = 9.sp),
                                            modifier = Modifier.padding(end = 10.dp))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Error
                            AnimatedVisibility(visible = submitError.isNotBlank()) {
                                Text(submitError,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFEF4444), fontSize = 11.sp))
                            }

                            // Submit button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(VElevated)
                                    .border(0.8.dp, VBorderAct, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isSubmitting) { submit() }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(18.dp),
                                        color       = VTxtP,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.Send, null,
                                            tint     = VTxtP,
                                            modifier = Modifier.size(15.dp))
                                        Text("Share with Circle",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color      = VTxtP,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize   = 13.sp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── STATE B: Submitted → show success banner + own card pinned ────
            if (hasSubmitted) {
                item(key = "success_banner") {
                    // Green success pill
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(VGreenDim.copy(0.25f))
                                .border(0.8.dp, VGreen.copy(0.30f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null,
                                    tint     = VGreen,
                                    modifier = Modifier.size(18.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Vibes shared with your circle!",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = VGreen, fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp))
                                    Text("Waiting for others to share their vibes…",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = VTxtS, fontSize = 11.sp))
                                }
                            }
                        }

                        // "Your card" label
                        SectionLabel("YOUR VIBE", Icons.Rounded.Person)
                    }
                }

                // My pinned card
                myEntry?.let { me ->
                    item(key = "my_card") {
                        VibeCard(entry = me, isMe = true)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ── Circle feed section header ────────────────────────────────────
            item(key = "feed_header") {
                Spacer(Modifier.height(if (hasSubmitted) 4.dp else 8.dp))
                HorizontalDivider(color = VBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionLabel(
                        if (othersEntries.isEmpty()) "CIRCLE'S VIBES"
                        else "YOUR CIRCLE (${othersEntries.size})",
                        Icons.Rounded.People
                    )
                    if (hasSubmitted && othersEntries.isNotEmpty()) {
                        Text("${othersEntries.size} shared",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = VTxtT, fontSize = 9.sp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── Empty state for others ────────────────────────────────────────
            if (othersEntries.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.HourglassTop, null,
                                tint     = VTxtT,
                                modifier = Modifier.size(28.dp))
                            Text(
                                if (hasSubmitted)
                                    "Waiting for your circle to share…"
                                else
                                    "Be the first to share your vibes!",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = VTxtS, fontSize = 13.sp, textAlign = TextAlign.Center))
                            if (!hasSubmitted) {
                                Text("Fill in the form above and tap Share",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = VTxtT, fontSize = 11.sp, textAlign = TextAlign.Center))
                            }
                        }
                    }
                }
            }

            // ── Others' vibe cards ────────────────────────────────────────────
            items(othersEntries, key = { "card_${it.uid}" }) { entry ->
                VibeCard(entry = entry, isMe = false)
                Spacer(Modifier.height(8.dp))
            }

            item(key = "bottom_pad") { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier              = Modifier.padding(horizontal = 16.dp)
    ) {
        Icon(icon, null, tint = VTxtS.copy(0.45f), modifier = Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                color         = VTxtS.copy(0.45f),
                fontSize      = 9.sp,
                letterSpacing = 1.4.sp,
                fontWeight    = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun InputCard(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    hint    : String,
    content : @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VCard)
            .border(0.7.dp, VBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = VTxtS, modifier = Modifier.size(15.dp))
            }
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(
                    color = VTxtP, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                Text(hint, style = MaterialTheme.typography.labelSmall.copy(
                    color = VTxtS, fontSize = 10.sp))
            }
        }
        content()
    }
}

@Composable
private fun RowScope.ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color)
            .border(
                if (selected) 2.5.dp else 0.dp,
                if (selected) Color.White else Color.Transparent,
                CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null,
                tint     = Color.White,
                modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun tfColors(accentColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = accentColor.copy(0.5f),
    unfocusedBorderColor    = VBorder,
    focusedTextColor        = VTxtP,
    unfocusedTextColor      = VTxtP,
    focusedContainerColor   = VBg,
    unfocusedContainerColor = VBg,
    cursorColor             = accentColor
)

// ─────────────────────────────────────────────────────────────────────────────
//  Vibe feed card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VibeCard(entry: VibeEntry, isMe: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isMe) Color(0xFF161619) else VCard)
            .border(
                0.7.dp,
                if (isMe) VGreen.copy(0.18f) else VBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(VElevated)
                        .border(
                            0.8.dp,
                            if (isMe) VGreen.copy(0.3f) else VBorderAct,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        entry.alias.take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = if (isMe) VGreen.copy(0.9f) else VTxtS,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp
                        )
                    )
                }
                Column {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(entry.alias,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = VTxtP, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                        if (isMe) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(VGreenDim.copy(0.35f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("You",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = VGreen, fontSize = 8.sp))
                            }
                        }
                    }
                    Text(relativeTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = VTxtT, fontSize = 9.sp))
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(VElevated)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("Day ${entry.day}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = VTxtT, fontSize = 8.sp))
            }
        }

        HorizontalDivider(color = VBorder, thickness = 0.4.dp)

        // Song + Color row
        val hasSong  = entry.vibeSong.isNotBlank()
        val hasColor = entry.colorName.isNotBlank()
        if (hasSong || hasColor) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hasSong) {
                    VibeChip(
                        icon     = Icons.Rounded.MusicNote,
                        label    = "SONG",
                        value    = entry.vibeSong,
                        accent   = Color(0xFF60A5FA),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (hasColor) {
                    val displayColor = ColorPalette.firstOrNull { it.second == entry.colorName }?.first
                        ?: safeParseColor(entry.colorHex)
                    ColorChip(color = displayColor, name = entry.colorName)
                }
            }
        }

        // One word bio
        if (entry.oneWordBio.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VElevated)
                    .border(0.6.dp, VBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Title, null, tint = VTxtT, modifier = Modifier.size(12.dp))
                    Text("\"${entry.oneWordBio}\"",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = VTxtP, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chips
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VibeChip(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    label    : String,
    value    : String,
    accent   : Color,
    modifier : Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VElevated)
            .border(0.6.dp, VBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = accent.copy(0.7f), modifier = Modifier.size(10.dp))
            Text(label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = VTxtT, fontSize = 8.sp, letterSpacing = 0.8.sp))
        }
        Text(value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = VTxtP, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 2)
    }
}

@Composable
private fun ColorChip(color: Color, name: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VElevated)
            .border(0.6.dp, VBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement  = Arrangement.spacedBy(5.dp),
        horizontalAlignment  = Alignment.CenterHorizontally
    ) {
        Text("COLOR",
            style = MaterialTheme.typography.labelSmall.copy(
                color = VTxtT, fontSize = 8.sp, letterSpacing = 0.8.sp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(0.1f), CircleShape)
        )
        Text(name,
            style = MaterialTheme.typography.labelSmall.copy(
                color = VTxtS, fontSize = 9.sp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Utils
// ─────────────────────────────────────────────────────────────────────────────
private fun safeParseColor(hex: String): Color {
    return try {
        val clean = hex.trimStart('#')
        val rgb   = clean.toLong(16)
        Color((0xFF000000 or rgb).toInt())
    } catch (_: Exception) {
        Color(0xFF6B7280)
    }
}

private fun relativeTime(ts: Long): String {
    if (ts == 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L     -> "just now"
        diff < 3_600_000L  -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else               -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts))
    }
}
