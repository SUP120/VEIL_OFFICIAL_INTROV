package com.ns.findyourintroverfriend.home

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ns.findyourintroverfriend.data.GroupFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────

data class ActiveRoom(
    val id         : String,
    val day        : Int,
    val memberCount: Int,
    val memberAlias: List<String>,   // up to 4 aliases (or emojis as fallback)
    val createdAt  : Long,           // epoch seconds
)

sealed class ActiveRoomsUiState {
    object Loading                        : ActiveRoomsUiState()
    data class Loaded(val rooms: List<ActiveRoom>) : ActiveRoomsUiState()
    data class Error(val msg: String)     : ActiveRoomsUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "ActiveRoomsVM"

class ActiveRoomsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<ActiveRoomsUiState>(ActiveRoomsUiState.Loading)
    val state: StateFlow<ActiveRoomsUiState> = _state.asStateFlow()

    private var listener: ListenerRegistration? = null

    init { listenToRooms() }

    private fun listenToRooms() {
        listener = db.collection("groups")
            .whereEqualTo(GroupFields.CEREMONY_DONE, false)
            // No orderBy here — that would require a composite Firestore index.
            // We sort client-side instead.
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenToRooms error: ${err.message}")
                    _state.value = ActiveRoomsUiState.Error(
                        "Couldn't load rooms.\n${err.message}"
                    )
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener

                viewModelScope.launch {
                    val rooms = snap.documents.mapNotNull { doc ->
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val members = (doc.get(GroupFields.MEMBERS) as? List<String>)
                                ?: return@mapNotNull null
                            val day = (doc.getLong(GroupFields.DAY) ?: 1L).toInt()
                            val ts  = doc.getTimestamp(GroupFields.CREATED_AT)?.seconds ?: 0L

                            val aliases = members.map { uid ->
                                try {
                                    val u = db.collection("users").document(uid).get().await()
                                    u.getString("alias") ?: u.getString("emoji") ?: "?"
                                } catch (_: Exception) { "?" }
                            }

                            ActiveRoom(
                                id          = doc.id,
                                day         = day,
                                memberCount = members.size,
                                memberAlias = aliases,
                                createdAt   = ts
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "room parse error: ${e.message}")
                            null
                        }
                    }
                    // Sort newest first client-side
                    _state.value = ActiveRoomsUiState.Loaded(
                        rooms.sortedByDescending { it.createdAt }
                    )
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

// Reuse the Ocean Depths palette for consistency
private val ARBg          = Color(0xFF070D15)  // Deep ocean black
private val ARBg2         = Color(0xFF0A1520)  // Rich navy depth
private val ARCard        = Color(0xFF0F1F2E)  // Card surface - deep blue
private val ARCardBorder  = Color(0xFF1A3545)  // Card border - ocean blue
private val ARAccent      = Color(0xFF00D9FF)  // Electric cyan
private val ARGold        = Color(0xFF00FFB3)  // Vibrant teal
private val ARGreen       = Color(0xFF00CC88)  // Mint green
private val ARPrimary     = Color(0xFFE0F4FF)  // Bright cyan-white text
private val ARSecondary   = Color(0xFF7AB8D4)  // Cyan-grey secondary text
private val ARMuted       = Color(0xFF3D5A6E)  // Muted blue-grey

private val dayColors = mapOf(
    1 to ARAccent,  // Cyan for Day 1
    2 to ARGreen,   // Mint for Day 2
    3 to ARGold     // Teal for Day 3
)
private val dayLabels = mapOf(
    1 to "Day 1 · Questions",
    2 to "Day 2 · Games",
    3 to "Day 3 · Ceremony"
)

@Composable
fun ActiveRoomsScreen(
    vm     : ActiveRoomsViewModel = viewModel(),
    onBack : () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Pulsing dot animation for LIVE badge
    val inf = rememberInfiniteTransition(label = "live")
    val livePulse by inf.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "lp"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A1628),  // Deep ocean blue
                        Color(0xFF0D2137),  // Rich navy
                        Color(0xFF0A1520)   // Deep space blue
                    )
                )
            )
    ) {
        // Ambient glow - cyan
        Box(
            Modifier
                .size(320.dp)
                .offset((-40).dp, (-60).dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ARAccent.copy(0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        // Secondary glow - teal
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(60.dp, 80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ARGold.copy(0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Active Rooms",
                            fontSize      = 26.sp,
                            fontWeight    = FontWeight.Bold,
                            color         = ARPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Live groups in progress right now",
                            fontSize = 13.sp,
                            color    = ARSecondary,
                            lineHeight = 18.sp
                        )
                    }

                    // LIVE badge
                    Row(
                        modifier          = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(ARGreen.copy(0.15f))
                            .border(1.dp, ARGreen.copy(0.35f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ARGreen.copy(alpha = livePulse))
                        )
                        Text(
                            "LIVE",
                            fontSize      = 11.sp,
                            fontWeight    = FontWeight.Bold,
                            color         = ARGreen,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Back button - improved design
                Row(
                    modifier          = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ARCard)
                        .border(1.dp, ARCardBorder, RoundedCornerShape(12.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("←", fontSize = 16.sp, color = ARAccent)
                    Text(
                        "Back to Home",
                        fontSize   = 13.sp,
                        color      = ARSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Content ────────────────────────────────────────────────────
            when (val s = state) {
                is ActiveRoomsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color       = ARAccent,
                                strokeWidth = 2.5.dp,
                                modifier    = Modifier.size(32.dp)
                            )
                            Text(
                                "Loading active circles…",
                                fontSize = 13.sp,
                                color    = ARMuted
                            )
                        }
                    }
                }

                is ActiveRoomsUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text("⚠️", fontSize = 36.sp)
                            Text(
                                s.msg,
                                fontSize  = 13.sp,
                                color     = ARSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is ActiveRoomsUiState.Loaded -> {
                    val rooms = s.rooms
                    if (rooms.isEmpty()) {
                        EmptyRoomsState()
                    } else {
                        // Count by day
                        val day1 = rooms.count { it.day == 1 }
                        val day2 = rooms.count { it.day == 2 }
                        val day3 = rooms.count { it.day == 3 }

                        LazyColumn(
                            modifier            = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            contentPadding      = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Summary row
                            item {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SummaryChip("$day1", "Day 1", ARAccent, Modifier.weight(1f))
                                    SummaryChip("$day2", "Day 2", ARGreen,  Modifier.weight(1f))
                                    SummaryChip("$day3", "Day 3", ARGold,   Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            items(rooms, key = { it.id }) { room ->
                                RoomCard(room = room, livePulse = livePulse)
                            }

                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Room card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomCard(room: ActiveRoom, livePulse: Float) {
    val dayColor = dayColors[room.day] ?: ARAccent
    val dayLabel = dayLabels[room.day] ?: "Day ${room.day}"

    val nowSecs    = System.currentTimeMillis() / 1000L
    val aliveFor   = nowSecs - room.createdAt
    val aliveLabel = when {
        aliveFor < 3600       -> "${aliveFor / 60}m ago"
        aliveFor < 86400      -> "${aliveFor / 3600}h ago"
        else                  -> "${aliveFor / 86400}d ago"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ARCard,
                        ARCard.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        dayColor.copy(0.45f),
                        ARCardBorder,
                        dayColor.copy(0.25f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ── Header row ──────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Day badge - improved design
                Row(
                    modifier          = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(dayColor.copy(0.16f))
                        .border(1.dp, dayColor.copy(0.40f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dayColor.copy(alpha = livePulse))
                    )
                    Text(
                        dayLabel,
                        fontSize   = 12.sp,
                        color      = dayColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }

                // Time alive - improved styling
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ARCardBorder.copy(0.3f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("⏱", fontSize = 10.sp)
                    Text(
                        aliveLabel,
                        fontSize = 11.sp,
                        color    = ARSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Member aliases ─────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Members",
                        fontSize = 10.sp,
                        color = ARMuted,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        "${room.memberCount}/4",
                        fontSize   = 11.sp,
                        color      = dayColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Grid layout - 2 columns for better fit
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // First row - 2 members
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        room.memberAlias.take(2).forEachIndexed { i, alias ->
                            MemberPill(alias = alias, index = i, modifier = Modifier.weight(1f))
                        }
                        // Fill empty slots in first row
                        if (room.memberAlias.size < 2) {
                            repeat(2 - room.memberAlias.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    
                    // Second row - remaining members
                    if (room.memberAlias.size > 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            room.memberAlias.drop(2).forEachIndexed { i, alias ->
                                MemberPill(alias = alias, index = i + 2, modifier = Modifier.weight(1f))
                            }
                            // Fill empty slots in second row
                            if (room.memberAlias.size < 4) {
                                repeat(4 - room.memberAlias.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Divider ──────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                ARCardBorder,
                                Color.Transparent
                            )
                        )
                    )
            )

            // ── Bottom info ───────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🔒", fontSize = 11.sp)
                    Text(
                        "Private · Anonymous",
                        fontSize = 11.sp,
                        color    = ARMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Progress dots (day indicator) - improved
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    for (d in 1..3) {
                        Box(
                            Modifier
                                .size(if (d == room.day) 9.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (d <= room.day) {
                                        if (d == room.day) dayColor
                                        else dayColor.copy(0.50f)
                                    } else {
                                        ARMuted.copy(0.25f)
                                    }
                                )
                                .then(
                                    if (d == room.day) {
                                        Modifier.border(1.5.dp, dayColor.copy(0.4f), CircleShape)
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

private val memberColors = listOf(
    Color(0xFF00D9FF), // Electric cyan
    Color(0xFF00CC88), // Mint green
    Color(0xFF00FFB3), // Vibrant teal
    Color(0xFF00A8CC)  // Ocean blue
)

@Composable
private fun MemberPill(alias: String, index: Int, modifier: Modifier = Modifier) {
    val color = memberColors[index % memberColors.size]
    Row(
        modifier          = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(0.12f))
            .border(1.dp, color.copy(0.30f), RoundedCornerShape(20.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        // Blur the name for privacy
        Text(
            text  = alias.take(12),
            fontSize  = 11.sp,
            color     = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.blur(4.dp),  // Blur effect for privacy
            maxLines = 1
        )
    }
}

@Composable
private fun SummaryChip(count: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(0.12f))
            .border(1.dp, color.copy(0.28f), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            count,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            label,
            fontSize = 10.sp,
            color = color.copy(0.70f),
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyRoomsState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            // Icon with glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                ARAccent.copy(0.15f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🌑", fontSize = 48.sp)
            }
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                "No active rooms right now",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = ARPrimary,
                textAlign  = TextAlign.Center
            )
            Text(
                "Rooms form when 4 people enter the matchmaking queue at the same time. Check back soon!",
                fontSize  = 14.sp,
                color     = ARSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}
