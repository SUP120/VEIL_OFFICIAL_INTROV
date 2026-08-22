package com.ns.findyourintroverfriend.game.drawguess

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val SheetBg      = Color(0xFFFFFFFF)
private val SheetBorder  = Color(0xFFEFEFEF)
private val SheetPrimary = Color(0xFF1C1916)
private val SheetSub     = Color(0xFF8A8480)
private val SheetAccent  = Color(0xFF7C6AE8)
private val SheetPill    = Color(0xFFF0EBE3)

private val SHEET_CATEGORIES = listOf(
    "animals" to "🐾", "food" to "🍕", "movies" to "🎬",
    "objects" to "📦", "emotions" to "💭"
)

/**
 * Bottom sheet invoked from chat screens (Swipe + Feed ConnectionRoomScreen).
 *
 * Lets the host configure rounds & category, then creates a private Draw & Guess
 * room via the ViewModel and calls [onRoomCreated] with the new roomId so the
 * caller can send the invite bubble and navigate to the waiting room.
 */
@Composable
fun DrawGuessLobbySheet(
    matchId      : String,
    myUid        : String,
    myAlias      : String,
    partnerUid   : String,
    partnerAlias : String,
    vm           : DrawGuessViewModel = viewModel(),
    onDismiss    : () -> Unit,
    onRoomCreated: (roomId: String) -> Unit
) {
    var rounds    by remember { mutableIntStateOf(5) }
    var category  by remember { mutableStateOf("animals") }
    var creating  by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SheetBg)
                .clickable(enabled = false) {}
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Handle
            Box(
                Modifier.size(36.dp, 4.dp).clip(CircleShape)
                    .background(SheetBorder).align(Alignment.CenterHorizontally)
            )

            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🎨", fontSize = 28.sp)
                Column {
                    Text("Draw & Guess", fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold, color = SheetPrimary)
                    Text("Invite $partnerAlias to play", fontSize = 12.sp, color = SheetSub)
                }
            }

            HorizontalDivider(color = SheetBorder)

            // Rounds picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ROUNDS", fontSize = 10.sp, color = SheetSub,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(5, 10).forEach { r ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (rounds == r) SheetAccent else SheetPill)
                                .border(1.dp,
                                    if (rounds == r) SheetAccent else SheetBorder,
                                    RoundedCornerShape(12.dp))
                                .clickable { rounds = r }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$r Rounds", fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (rounds == r) Color.White else SheetPrimary)
                        }
                    }
                }
            }

            // Category picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CATEGORY", fontSize = 10.sp, color = SheetSub,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SHEET_CATEGORIES.forEach { (id, emoji) ->
                        val sel = category == id
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (sel) SheetAccent.copy(0.12f) else SheetPill)
                                .border(1.5.dp,
                                    if (sel) SheetAccent else SheetBorder,
                                    RoundedCornerShape(12.dp))
                                .clickable { category = id }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(emoji, fontSize = 18.sp)
                            Text(id.replaceFirstChar { it.uppercase() },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sel) SheetAccent else SheetSub)
                        }
                    }
                }
            }

            // Create & invite button
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (creating) SheetBorder else SheetAccent)
                    .clickable(enabled = !creating) {
                        creating = true
                        vm.createPrivateRoom(
                            matchId      = matchId,
                            myUid        = myUid,
                            myAlias      = myAlias,
                            partnerUid   = partnerUid,
                            partnerAlias = partnerAlias,
                            rounds       = rounds,
                            category     = category,
                            onCreated    = { roomId ->
                                creating = false
                                onRoomCreated(roomId)
                            }
                        )
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        color = SheetAccent,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Send, null,
                            tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Send Invite to $partnerAlias", fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
