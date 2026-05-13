package com.ns.findyourintroverfriend.poll

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  Would You Rather Screen — warm, joyful, clean
// ─────────────────────────────────────────────────────────────────────────────

private val WyrColorA = Color(0xFFE8832A)  // warm amber
private val WyrColorB = Color(0xFFFF7B6B)  // warm coral

@Composable
fun WouldYouRatherScreen(
    vm     : WouldYouRatherViewModel = viewModel(),
    onBack : () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Background pulse
    val inf = rememberInfiniteTransition(label = "wyrBg")
    val pulse by inf.animateFloat(
        initialValue  = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "wyrPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFDF8F2), Color(0xFFFFF4E8), Color(0xFFFEEDD8))
                )
            )
    ) {
        // Warm glow blobs in background
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-60).dp, y = 80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(WyrColorA.copy(alpha = 0.10f * pulse), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = 160.dp)
                .background(
                    Brush.radialGradient(
                        listOf(WyrColorB.copy(alpha = 0.08f * pulse), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(0.8.dp, Color(0xFFEDD9C0), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = Color(0xFF2D1F0E), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(0.8.dp, Color(0xFF7A5C3A).copy(0.3f), CircleShape)
                    ) {
                        androidx.compose.foundation.Image(
                            painter      = androidx.compose.ui.res.painterResource(id = com.ns.findyourintroverfriend.R.drawable.mascot_would_rather),
                            contentDescription = null,
                            modifier     = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Text(
                        "Would You Rather",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color      = Color(0xFF2D1F0E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 17.sp,
                            letterSpacing = 0.3.sp
                        )
                    )
                    Text(
                        "Pick a side  ·  see the truth",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = Color(0xFF7A5C3A),
                            fontSize = 10.sp
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp)) // balance
            }

            // Thin divider gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, WyrColorA.copy(0.3f), WyrColorB.copy(0.3f), Color.Transparent)
                        )
                    )
            )
            Spacer(Modifier.height(8.dp))

            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = WyrColorA,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error!!, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE05C6B)))
                    }
                }
                else -> {
                    val voted = state.questions.count { it.myPick != null }
                    val total = state.questions.size

                    LazyColumn(
                        modifier        = Modifier.fillMaxSize(),
                        contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            // Progress row
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val frac by animateFloatAsState(
                                    if (total > 0) voted.toFloat() / total else 0f,
                                    tween(600), label = "wyrProg"
                                )
                                Text(
                            "$voted / $total",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF7A5C3A), fontSize = 11.sp
                            )
                        )
                        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color(0xFFEDD9C0))) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth(frac)
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(WyrColorA, WyrColorB)))
                            )
                        }
                        Text(
                            if (voted == total && total > 0) "Done!" else "${total - voted} left",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (voted == total && total > 0) WyrColorA else Color(0xFF7A5C3A),
                                fontSize = 11.sp
                            )
                        )
                            }
                        }

                        itemsIndexed(state.questions) { index: Int, qs: WyrQuestionState ->
                            WyrCard(
                                index  = index,
                                qs     = qs,
                                onVote = { pick -> vm.vote(qs.question.id, pick) }
                            )
                        }

                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single WYR card — vibrant redesign
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WyrCard(
    index  : Int,
    qs     : WyrQuestionState,
    onVote : (String) -> Unit
) {
    val voted = qs.myPick != null
    val inf2 = rememberInfiniteTransition(label = "cardGlow$index")
    val cardGlow by inf2.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cg$index"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(
                if (voted) 1.5.dp else 0.8.dp,
                if (voted) Brush.horizontalGradient(listOf(WyrColorA.copy(cardGlow * 0.4f), WyrColorB.copy(cardGlow * 0.4f)))
                else   Brush.horizontalGradient(listOf(Color(0xFFEDD9C0), Color(0xFFEDD9C0))),
                RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Question badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(listOf(WyrColorA.copy(0.20f), WyrColorB.copy(0.20f)))
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Q${index + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                        )
                    )
                }
                Text(
                    "Would You Rather…",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF7A5C3A), fontSize = 10.sp
                    )
                )
            }

            // Option A
            WyrOption(
                label   = qs.question.optA,
                pick    = "A",
                myPick  = qs.myPick,
                tally   = qs.tally,
                accent  = WyrColorA,
                onVote  = onVote
            )

            // OR divider
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.weight(1f).height(0.8.dp).background(
                    Brush.horizontalGradient(listOf(Color.Transparent, WyrColorA.copy(0.35f)))
                ))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF0E0))
                        .border(0.8.dp, Color(0xFFEDD9C0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OR", style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF7A5C3A), fontSize = 9.sp, fontWeight = FontWeight.Bold
                    ))
                }
                Box(Modifier.weight(1f).height(0.8.dp).background(
                    Brush.horizontalGradient(listOf(WyrColorB.copy(0.35f), Color.Transparent))
                ))
            }

            // Option B
            WyrOption(
                label   = qs.question.optB,
                pick    = "B",
                myPick  = qs.myPick,
                tally   = qs.tally,
                accent  = WyrColorB,
                onVote  = onVote
            )

            // After voting — tally summary
            if (voted) {
                TallySummary(tally = qs.tally, myPick = qs.myPick!!)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Choice button — vibrant with accent colours per side
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WyrOption(
    label  : String,
    pick   : String,
    myPick : String?,
    tally  : WyrTally,
    accent : Color,
    onVote : (String) -> Unit
) {
    val voted      = myPick != null
    val isMyPick   = myPick == pick
    val isDisabled = voted && !isMyPick

    val scale by animateFloatAsState(
        targetValue   = if (isMyPick) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label         = "optScale$pick"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    isMyPick   -> Brush.linearGradient(listOf(accent.copy(0.18f), accent.copy(0.08f)))
                    isDisabled -> Brush.linearGradient(listOf(Color(0xFFF5F0EA), Color(0xFFF5F0EA)))
                    else       -> Brush.linearGradient(listOf(Color(0xFFFFF9F4), Color(0xFFFFF9F4)))
                }
            )
            .border(
                if (isMyPick) 1.5.dp else 0.8.dp,
                if (isMyPick) accent.copy(0.6f)
                else if (isDisabled) Color(0xFFE0D0C0)
                else Color(0xFFEDD9C0),
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = !voted) { onVote(pick) }
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Side indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMyPick) accent.copy(0.20f)
                        else Color(0xFFEDD9C0).copy(if (isDisabled) 0.5f else 1f)
                    )
                    .border(
                        if (isMyPick) 2.dp else 1.dp,
                        if (isMyPick) accent.copy(0.7f) else Color(0xFFD0B89A),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isMyPick) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                }
            }

            Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color      = when {
                            isMyPick   -> Color(0xFF2D1F0E)
                            isDisabled -> Color(0xFF7A5C3A).copy(0.50f)
                            else       -> Color(0xFF2D1F0E).copy(0.80f)
                        },
                        fontWeight = if (isMyPick) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = 20.sp,
                        fontSize   = 14.sp
                    ),
                    modifier = Modifier.weight(1f)
                )

            // Vote count after voting
            if (voted) {
                val count = if (pick == "A") tally.countA else tally.countB
                Text(
                    "$count",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color      = if (isMyPick) accent else Color(0xFFB08A60),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tally row after voting
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TallySummary(tally: WyrTally, myPick: String) {
    if (tally.total == 0) return
    val fracA    = if (tally.total > 0) tally.countA.toFloat() / tally.total else 0f
    val animA    by animateFloatAsState(fracA, tween(700), label = "tallyA")

    val sharedCount = if (myPick == "A") tally.countA else tally.countB
    val sharedMsg   = when {
        sharedCount >= tally.total && tally.total > 1 -> "Everyone agreed  ✦"
        sharedCount > 1                               -> "${sharedCount - 1} others felt the same"
        sharedCount == 1                              -> "Only you so far"
        else                                          -> ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Gradient bar A vs B
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(WyrColorB.copy(0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animA)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(WyrColorA, WyrColorA.copy(0.6f))))
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A: ${tally.countA}", style = MaterialTheme.typography.labelSmall.copy(color = WyrColorA.copy(0.7f), fontSize = 10.sp))
            Text("B: ${tally.countB}", style = MaterialTheme.typography.labelSmall.copy(color = WyrColorB.copy(0.7f), fontSize = 10.sp))
        }
        if (sharedMsg.isNotEmpty()) {
            Text(
                sharedMsg,
                style = MaterialTheme.typography.labelSmall.copy(
                    color     = Color(0xFF7A5C3A),
                    textAlign = TextAlign.Center,
                    fontSize  = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
