package com.ns.findyourintroverfriend.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
//  DevFlowPanel
//  A floating developer control panel — ONLY visible to emails in DevConfig.
//
//  Controls:
//    • Day 1 / Day 2 / Day 3  — instantly sets the group day
//    • Ceremony Hour Picker    — change unlock hour (default 8:00 PM)
//    • "Set to Now"            — makes ceremony available at current hour
//    • 🎭 Jump to Ceremony     — force-unlocks ceremony + navigates immediately
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DevFlowPanel(
    vm             : IslandHomeViewModel,
    state          : IslandHomeState,
    isDay          : Boolean,
    showPanel      : Boolean,
    onTogglePanel  : () -> Unit,
    onOpenCeremony : () -> Unit,
    modifier       : Modifier = Modifier
) {
    val currentDay   = state.groupDay
    val unlockHour   = state.ceremonyUnlockHour
    val currentHour  = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val bgCard       = if (isDay) Color(0xFF1A1230).copy(0.93f) else Color(0xFF0D0A1E).copy(0.95f)
    val groupLabel   = if (state.groupId.length > 8) state.groupId.take(8) + "…" else state.groupId
    val isNonDefault = unlockHour != 20

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Expanded control card ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = showPanel,
            enter   = fadeIn() + slideInVertically { it / 2 },
            exit    = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgCard)
                    .border(1.dp, Color(0xFF7C4DFF).copy(0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // ── Header ─────────────────────────────────────────────────
                    Text(
                        "🛠️  Developer Flow Controls",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color         = Color(0xFFB388FF),
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            fontSize      = 11.sp
                        )
                    )
                    Text(
                        "Day $currentDay active  •  group: $groupLabel",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = Color.White.copy(0.40f),
                            fontSize = 9.sp
                        )
                    )

                    // ── Divider ────────────────────────────────────────────────
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.10f)))

                    // ── SET DAY ────────────────────────────────────────────────
                    Text(
                        "SET DAY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color         = Color.White.copy(0.35f),
                            fontSize      = 9.sp,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { day ->
                            val isActive = currentDay == day
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) Color(0xFF7C4DFF) else Color.White.copy(0.07f))
                                    .border(
                                        0.8.dp,
                                        if (isActive) Color(0xFFB388FF).copy(0.6f) else Color.White.copy(0.12f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { vm.devSetDay(day) }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    "Day $day",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color      = if (isActive) Color.White else Color.White.copy(0.55f),
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        fontSize   = 12.sp
                                    )
                                )
                            }
                        }
                    }

                    // ── Divider ────────────────────────────────────────────────
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.10f)))

                    // ── CEREMONY UNLOCK HOUR ───────────────────────────────────
                    Text(
                        "CEREMONY UNLOCK HOUR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color         = Color.White.copy(0.35f),
                            fontSize      = 9.sp,
                            letterSpacing = 1.2.sp
                        )
                    )

                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // − button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.08f))
                                .border(0.8.dp, Color.White.copy(0.15f), CircleShape)
                                .clickable { vm.devSetCeremonyHour(unlockHour - 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "−",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color      = Color.White.copy(0.70f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // Current hour display
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.width(90.dp)
                        ) {
                            Text(
                                vm.formatHour(unlockHour),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color      = if (isNonDefault) Color(0xFFFFD54F) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 16.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            if (isNonDefault) {
                                Text(
                                    "default: 8:00 PM",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color    = Color(0xFFFFD54F).copy(0.55f),
                                        fontSize = 8.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // + button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.08f))
                                .border(0.8.dp, Color.White.copy(0.15f), CircleShape)
                                .clickable { vm.devSetCeremonyHour(unlockHour + 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color      = Color.White.copy(0.70f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // "Set to Now" shortcut
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1B5E20).copy(0.55f))
                            .border(0.8.dp, Color(0xFF66BB6A).copy(0.35f), RoundedCornerShape(10.dp))
                            .clickable { vm.devSetCeremonyHour(currentHour) }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "Set to Now  (${vm.formatHour(currentHour)})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color      = Color(0xFF81C784),
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 10.sp
                            )
                        )
                    }

                    Text(
                        "Sets the hour when ceremony auto-unlocks.\nAll devs in the group share this setting.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color      = Color.White.copy(0.28f),
                            fontSize   = 8.sp,
                            lineHeight = 12.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    // ── Divider ────────────────────────────────────────────────
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.10f)))

                    // ── 🎭 Jump to Ceremony (bypasses clock check entirely) ────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF4A148C).copy(0.75f), Color(0xFF6A1B9A).copy(0.75f))
                                )
                            )
                            .border(0.8.dp, Color(0xFFCE93D8).copy(0.4f), RoundedCornerShape(12.dp))
                            .clickable {
                                vm.devUnlockCeremony()  // force Day 3 + ceremonyOpened=true in Firestore
                                onOpenCeremony()        // navigate to ceremony screen
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🎭  Jump to Ceremony  (skips hour check)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color      = Color(0xFFE1BEE7),
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 11.sp
                            )
                        )
                    }

                    Text(
                        "⚠️  Dev only — invisible to regular users",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = Color(0xFFFF8A65).copy(0.50f),
                            fontSize = 8.sp
                        )
                    )
                }
            }
        }

        // ── Floating pill toggle ───────────────────────────────────────────────
        // Shows current ceremony hour in pill text when non-default (yellow hint)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(
                    when {
                        showPanel    -> Color(0xFF7C4DFF).copy(0.88f)
                        isNonDefault -> Color(0xFF4A148C).copy(0.80f)
                        else         -> Color(0xFF1A1230).copy(0.72f)
                    }
                )
                .border(
                    0.8.dp,
                    if (isNonDefault && !showPanel) Color(0xFFFFD54F).copy(0.50f)
                    else Color(0xFF7C4DFF).copy(if (showPanel) 0.9f else 0.35f),
                    RoundedCornerShape(50.dp)
                )
                .clickable { onTogglePanel() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = when {
                    showPanel    -> "✕  Close"
                    isNonDefault -> "🛠️  DEV  •  ${vm.formatHour(unlockHour)}"
                    else         -> "🛠️  DEV"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = when {
                        showPanel    -> Color.White
                        isNonDefault -> Color(0xFFFFD54F)
                        else         -> Color(0xFFB388FF)
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 10.sp
                )
            )
        }
    }
}
