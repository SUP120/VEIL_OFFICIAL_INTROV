package com.ns.findyourintroverfriend.party

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.report.ReportSheet
import com.ns.findyourintroverfriend.util.ContentModerator
import com.ns.findyourintroverfriend.home.MemberPhoto
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
//  Palette — light, warm, professional
// ─────────────────────────────────────────────────────────────────────────────

internal val PBg        = Color(0xFFF7F5F2)
internal val PCard      = Color(0xFFFFFFFF)
internal val PCardSoft  = Color(0xFFF0EDE8)
internal val PSage      = Color(0xFF4E7C47)
internal val PSageLight = Color(0xFFE8F0E6)
internal val PBlue      = Color(0xFF4A7FA5)
internal val PBlueLight = Color(0xFFE3EDF5)
internal val PText      = Color(0xFF1C1C1E)
internal val PTextMid   = Color(0xFF6B6B70)
internal val PTextMuted = Color(0xFFABABAF)
internal val PBorder    = Color(0xFFE8E5E0)
internal val PDanger    = Color(0xFFB85C50)

// Legacy colour aliases used by KnowEachOtherScreen
internal val F7Bg        = PBg
internal val F7BgSurface = PCard
internal val F7Sage      = PSage
internal val F7Amber     = PBlue
internal val F7TealWarm  = PBlue
internal val F7Cream     = PText
internal val F7CreamDim  = PTextMid
internal val F7WarmGrey  = PTextMuted
internal val F7Rule      = PBorder

internal fun serifF7(
    size          : androidx.compose.ui.unit.TextUnit,
    color         : Color,
    italic        : Boolean    = false,
    letterSpacing : androidx.compose.ui.unit.TextUnit = 0.sp,
    lineHeight    : androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    weight        : FontWeight = FontWeight.Normal
) = androidx.compose.ui.text.TextStyle(
    fontWeight    = weight,
    color         = color,
    fontSize      = size,
    letterSpacing = letterSpacing,
    lineHeight    = if (lineHeight == androidx.compose.ui.unit.TextUnit.Unspecified) size * 1.5f else lineHeight
)

// ─────────────────────────────────────────────────────────────────────────────
//  Chat rules data
// ─────────────────────────────────────────────────────────────────────────────

private data class ChatRule(val emoji: String, val title: String, val desc: String)

private val CHAT_RULES = listOf(
    ChatRule("🔒", "End-to-End Encrypted",      "All messages are encrypted. Only you and your match can read them."),
    ChatRule("🫂", "Be kind and genuine",        "This is a safe space. No hate, harassment or cruelty."),
    ChatRule("🚫", "No personal contact details","Don't share phone numbers, handles or IDs until you're both ready."),
    ChatRule("🙈", "Respect privacy",            "What's shared here stays here. No screenshots or leaks."),
    ChatRule("⚠️", "No inappropriate content",   "Sexual, violent or offensive messages result in an immediate ban."),
    ChatRule("💬", "One honest conversation",    "If you're not interested, a kind message is better than silence."),
    ChatRule("🌱", "Give each other space",       "Your match may take time to reply. Patience goes a long way."),
    ChatRule("🛡️", "Report abuse",               "Use the report button for any misconduct. We act on every report.")
)

// ─────────────────────────────────────────────────────────────────────────────
//  Entry Point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PartyIslandScreen(
    sessionId    : String,
    onStartFresh : () -> Unit = {}
) {
    val vm    : PartyViewModel = viewModel(factory = PartyViewModel.factory(sessionId))
    val state by vm.state.collectAsStateWithLifecycle()

    var showChat    by remember { mutableStateOf(false) }
    var showKnow    by remember { mutableStateOf(false) }
    var showRules   by remember { mutableStateOf(false) }
    var showQuitDlg by remember { mutableStateOf(false) }

    // Auto-navigate when I leave
    LaunchedEffect(state.iLeft) {
        if (state.iLeft) onStartFresh()
    }

    Box(modifier = Modifier.fillMaxSize().background(PBg)) {
        // ── Main screen logic ─────────────────────────────────────────────────
        when {
            state.isLoading      -> PLoading()
            state.error != null  -> PError(state.error.orEmpty())
            state.partnerLeft    -> PartnerLeftScreen(
                partner      = state.partner,
                onFindRooms  = onStartFresh
            )
            showKnow             -> KnowEachOtherScreen(
                me      = state.me,
                partner = state.partner,
                onBack  = { showKnow = false }
            )
            showRules            -> ChatRulesScreen(
                onAccept = { showRules = false; showChat = true },
                onBack   = { showRules = false }
            )
            showChat             -> ChatScreen(
                me        = state.me,
                partner   = state.partner,
                messages  = state.messages,
                onSend    = vm::sendMessage,
                onRefresh = vm::refreshMessages,
                onBack    = { showChat = false }
            )
            else                 -> PMatchPage(
                me                = state.me,
                partner           = state.partner,
                partnerIsOnIsland = state.partnerIsOnIsland,
                partnerLastSeen   = state.partnerLastSeen,
                bondScore         = state.bondScore,
                onChat            = { showRules = true },
                onKnow            = { showKnow = true },
                onQuit            = { showQuitDlg = true },
                onStartFresh      = onStartFresh
            )
        }

        // ── Quit dialog — inside Box so it renders on top ─────────────────────
        if (showQuitDlg) {
            QuitRoomDialog(
                onConfirm = {
                    showQuitDlg = false
                    vm.leaveSession()   // also sets iLeft=true → LaunchedEffect navigates
                },
                onDismiss = { showQuitDlg = false }
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Partner Left — full-screen notification overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PartnerLeftScreen(partner: PartyMember?, onFindRooms: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(120); show = true }
    val alpha by animateFloatAsState(if (show) 1f else 0f, tween(600), label = "pla")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PBg)
            .graphicsLayer(alpha = alpha)
            .systemBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Soft illustration
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(PCardSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("🌊", fontSize = 44.sp)
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "${partner?.name ?: "Your match"} has left the island",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color      = PText,
                fontSize   = 22.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            "They stepped away from this private space.\nEvery connection is a step forward — this one was real.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color      = PTextMid,
                fontSize   = 14.sp,
                lineHeight = 22.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        // Encryption reminder — stays tasteful
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(PSageLight)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                "This room and its messages remain private.",
                style = MaterialTheme.typography.labelSmall.copy(color = PSage, fontSize = 11.sp)
            )
        }

        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PSage)
                .clickable { onFindRooms() }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Find other rooms →",
                style = MaterialTheme.typography.titleSmall.copy(
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp
                )
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "You'll keep your profile and journey progress.",
            style = MaterialTheme.typography.bodySmall.copy(color = PTextMuted, fontSize = 11.sp),
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quit confirmation dialog (renders over full screen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuitRoomDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(22.dp))
                .background(PCard)
                .padding(28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("⚠️", fontSize = 36.sp)
                Text(
                    "Leave this room?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, color = PText, fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    "You will leave this private island and be sent to find a new group. Your current connection will end for both of you.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = PTextMid, fontSize = 13.sp, lineHeight = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PDanger)
                        .clickable { onConfirm() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Yes, leave the room",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PCardSoft)
                        .clickable { onDismiss() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Stay in the room",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = PText, fontWeight = FontWeight.Medium, fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rules & Safety Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatRulesScreen(onAccept: () -> Unit, onBack: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "ra")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PBg)
            .graphicsLayer(alpha = alpha)
            .systemBarsPadding()
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PCard)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PCardSoft)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", style = MaterialTheme.typography.bodyLarge.copy(color = PText))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Before you chat",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = PText, fontSize = 18.sp)
                    )
                    Text(
                        "A few things to keep this safe",
                        style = MaterialTheme.typography.bodySmall.copy(color = PTextMid, fontSize = 12.sp)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(PBorder))

        // E2E encryption banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PSageLight)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(PSage.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text("🔒", fontSize = 16.sp) }
                Column {
                    Text(
                        "End-to-End Encrypted",
                        style = MaterialTheme.typography.labelLarge.copy(color = PSage, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    )
                    Text(
                        "Messages can only be read by you and your match.",
                        style = MaterialTheme.typography.bodySmall.copy(color = PSage.copy(0.70f), fontSize = 11.sp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "COMMUNITY GUIDELINES",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = PTextMuted, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontSize = 10.sp
                )
            )
            Spacer(Modifier.height(2.dp))

            CHAT_RULES.drop(1).forEach { rule ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PCard)
                        .border(0.6.dp, PBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment     = Alignment.Top
                    ) {
                        Text(rule.emoji, fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                rule.title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold, color = PText, fontSize = 13.sp
                                )
                            )
                            Text(
                                rule.desc,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = PTextMid, fontSize = 12.sp, lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "By starting the conversation you agree to these guidelines.\nViolations may result in removal from the app.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = PTextMuted, fontSize = 11.sp, lineHeight = 17.sp
                ),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(PBorder))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PCard)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PSage)
                    .clickable { onAccept() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "I understand — start chatting",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Match Page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PMatchPage(
    me                : PartyMember?,
    partner           : PartyMember?,
    partnerIsOnIsland : Boolean = false,
    partnerLastSeen   : Long    = 0L,
    bondScore         : Int     = 0,
    onChat            : () -> Unit,
    onKnow            : () -> Unit,
    onQuit            : () -> Unit,
    onStartFresh      : () -> Unit
) {
    val nowSecs       = System.currentTimeMillis() / 1000L
    val secsAgo       = if (partnerLastSeen > 0L) nowSecs - partnerLastSeen else -1L
    val partnerOnline = secsAgo in 0L..180L
    var w0 by remember { mutableStateOf(false) }
    var w1 by remember { mutableStateOf(false) }
    var w2 by remember { mutableStateOf(false) }
    var w3 by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80);  w0 = true
        delay(220); w1 = true
        delay(220); w2 = true
        delay(220); w3 = true
    }
    val a0 by animateFloatAsState(if (w0) 1f else 0f, tween(600), label = "a0")
    val a1 by animateFloatAsState(if (w1) 1f else 0f, tween(700), label = "a1")
    val a2 by animateFloatAsState(if (w2) 1f else 0f, tween(800), label = "a2")
    val a3 by animateFloatAsState(if (w3) 1f else 0f, tween(900), label = "a3")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header with QUIT button top right ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PCard)
                .alpha(a0)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    "Your Island",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold, color = PText, fontSize = 26.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "A connection built over 3 days of honesty",
                    style = MaterialTheme.typography.bodyMedium.copy(color = PTextMid, fontSize = 14.sp)
                )
            }
            // Quit button — top right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PDanger.copy(alpha = 0.10f))
                    .border(0.6.dp, PDanger.copy(0.20f), RoundedCornerShape(10.dp))
                    .clickable { onQuit() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    "Leave room",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = PDanger, fontWeight = FontWeight.SemiBold, fontSize = 11.sp
                    )
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(PBorder))
        Spacer(Modifier.height(16.dp))

        // ── Connection Card ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .alpha(a1)
                .shadow(3.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(0.07f))
                .clip(RoundedCornerShape(22.dp))
                .background(PCard)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Dual profile photos with glowing heart connector ──────────
                val inf  = rememberInfiniteTransition(label = "hGl")
                val glow by inf.animateFloat(
                    0.45f, 1f,
                    infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "hg"
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Me
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PLargeAvatar(member = me, isYou = true, showOnlineDot = false, online = false)
                        Text(
                            me?.name ?: "You",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = PText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(PSageLight)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("you", style = MaterialTheme.typography.labelSmall.copy(
                                color = PSage, fontSize = 9.sp
                            ))
                        }
                    }

                    // Heart
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(PSage.copy(alpha = 0.10f * glow)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("♡", fontSize = 22.sp, color = PSage.copy(alpha = glow * 0.85f))
                        }
                        if (bondScore > 0) {
                            Text(
                                "$bondScore pts",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = PSage.copy(0.60f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    // Partner
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PLargeAvatar(
                            member        = partner,
                            isYou         = false,
                            showOnlineDot = true,
                            online        = partnerOnline
                        )
                        Text(
                            partner?.name ?: "Your match",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = PText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                        )
                        // Island arrival / online chip
                        val (chipText, chipBg, chipFg) = when {
                            !partnerIsOnIsland -> Triple("Not on island yet", PTextMuted.copy(0.10f), PTextMuted)
                            partnerOnline      -> Triple("Online now",        Color(0x1A34C759),      Color(0xFF34C759))
                            secsAgo in 0..3599 -> Triple("Active ${secsAgo/60}m ago", PTextMuted.copy(0.10f), PTextMuted)
                            else               -> Triple("Offline",           PTextMuted.copy(0.10f), PTextMuted)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(chipBg)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(chipText, style = MaterialTheme.typography.labelSmall.copy(
                                color = chipFg, fontSize = 9.sp
                            ))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(PBorder))
                Spacer(Modifier.height(12.dp))

                Text(
                    "3 days  ·  4 strangers  ·  1 rare bond",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = PSage, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp, fontSize = 11.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Action cards ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .alpha(a2)
        ) {
            Text(
                "WHERE TO START",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = PTextMuted, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontSize = 10.sp
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp).alpha(a2),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PActionCard(emoji = "💬", title = "Start the conversation",
                subtitle = "Your space. Your pace. No scripts.", accent = PSage, bg = PSageLight, onClick = onChat)
            PActionCard(emoji = "🌿", title = "Reflect together",
                subtitle = "One quiet prompt. One honest answer each.", accent = PBlue, bg = PBlueLight, onClick = onKnow)
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .alpha(a3)
        ) {
            Text(
                "COMING SOON",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = PTextMuted, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontSize = 10.sp
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp).alpha(a3),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PActionCardDisabled(emoji = "🎲", title = "Soft games & things",     subtitle = "Little ways to discover each other.")
            PActionCardDisabled(emoji = "🎵", title = "A shared quiet moment", subtitle = "Music corner — no words needed.")
        }

        Spacer(Modifier.height(48.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Avatars
// ─────────────────────────────────────────────────────────────────────────────

/** Small avatar — still used elsewhere */
@Composable
private fun PAvatar(member: PartyMember?) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(0.08f))
            .clip(CircleShape)
            .background(PCardSoft)
            .border(2.5.dp, PCard, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val p = member?.photoUrl ?: ""
        if (p.isNotBlank()) {
            MemberPhoto(
                photoUrl     = p,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier     = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(member?.emoji ?: "?", fontSize = 28.sp)
        }
    }
}

/** Large avatar for the match card — 82dp, with optional online dot */
@Composable
private fun PLargeAvatar(
    member        : PartyMember?,
    isYou         : Boolean,
    showOnlineDot : Boolean,
    online        : Boolean
) {
    Box {
        Box(
            modifier = Modifier
                .size(82.dp)
                .shadow(5.dp, CircleShape, spotColor = Color.Black.copy(0.09f))
                .clip(CircleShape)
                .background(PCardSoft)
                .border(
                    width = if (isYou) 2.5.dp else 2.dp,
                    color = if (isYou) PSage.copy(0.40f) else PBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val p = member?.photoUrl ?: ""
            if (p.isNotBlank()) {
                MemberPhoto(
                    photoUrl     = p,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier     = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(member?.emoji ?: "?", fontSize = 32.sp)
            }
        }
        // Online indicator dot (bottom-right)
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(PCard)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(if (online) Color(0xFF34C759) else PTextMuted.copy(0.45f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Action cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PActionCard(emoji: String, title: String, subtitle: String, accent: Color, bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(0.05f))
            .clip(RoundedCornerShape(16.dp))
            .background(PCard)
            .border(0.8.dp, accent.copy(0.15f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(bg),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 22.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = PText, fontSize = 15.sp))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = PTextMid, fontSize = 12.sp))
            }
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(accent.copy(0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text("→", style = MaterialTheme.typography.bodyLarge.copy(color = accent, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun PActionCardDisabled(emoji: String, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PCardSoft)
            .border(0.6.dp, PBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(PBorder.copy(0.5f)).alpha(0.5f),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 22.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = PTextMuted, fontSize = 15.sp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(PBorder).padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text("Soon", style = MaterialTheme.typography.labelSmall.copy(color = PTextMuted, fontSize = 9.sp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = PTextMuted.copy(0.6f), fontSize = 12.sp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chat Screen — WhatsApp style with imePadding
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(
    me        : PartyMember?,
    partner   : PartyMember?,
    messages  : List<ChatMessage>,
    onSend    : (String) -> Unit,
    onRefresh : () -> Unit = {},
    onBack    : () -> Unit
) {
    // Resolve myUid from Auth directly — never empty for a signed-in user
    val myUid = remember {
        me?.uid?.takeIf { it.isNotBlank() }
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: ""
    }
    var text              by remember { mutableStateOf("") }
    val listState         = rememberLazyListState()
    val focusManager      = LocalFocusManager.current
    var refreshing        by remember { mutableStateOf(false) }
    var moderationWarning by remember { mutableStateOf<String?>(null) }
    var violationCount    by remember { mutableIntStateOf(0) }
    var showReport        by remember { mutableStateOf(false) }
    var showBlockDlg      by remember { mutableStateOf(false) }

    // Danger / amber colours reused from theme
    val danger = Color(0xFFEF4444)
    val amber  = Color(0xFFF59E0B)

    // Auto-scroll to newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val t = text.trim()
        if (t.isBlank()) return

        // Content moderation gate
        val result = ContentModerator.check(t)
        if (!result.isSafe) {
            violationCount++
            moderationWarning = if (violationCount >= 2) {
                "⚠️ Repeated violations detected. Further violations may result in a ban."
            } else {
                result.warningMessage
                    ?: "⚠️ This message was blocked. Personal, sexual, contact or location content is not allowed."
            }
            return
        }

        onSend(t); text = ""; focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PBg)
            .systemBarsPadding()
            .imePadding()           // lifts input bar above keyboard
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().background(PCard)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(PCardSoft).clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) { Text("←", style = MaterialTheme.typography.bodyLarge.copy(color = PText)) }

                    Spacer(Modifier.width(10.dp))

                    // Partner avatar
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PCardSoft)
                            .border(1.5.dp, PSageLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val p = partner?.photoUrl ?: ""
                        if (p.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = p, contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else { Text(partner?.emoji ?: "?", fontSize = 18.sp) }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            partner?.name ?: "Your match",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = PText, fontSize = 16.sp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🔒", fontSize = 10.sp)
                            Text(
                                "End-to-end encrypted",
                                style = MaterialTheme.typography.labelSmall.copy(color = PSage, fontSize = 10.sp)
                            )
                        }
                    }

                    // ── Block button ──────────────────────────────────
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PDanger.copy(0.10f))
                            .border(0.7.dp, PDanger.copy(0.25f), CircleShape)
                            .clickable { showBlockDlg = true },
                        contentAlignment = Alignment.Center
                    ) { Text("🚫", fontSize = 16.sp) }

                    Spacer(Modifier.width(6.dp))

                    // ── Report button ──────────────────────────────────
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(amber.copy(0.10f))
                            .border(0.7.dp, amber.copy(0.25f), CircleShape)
                            .clickable { showReport = true },
                        contentAlignment = Alignment.Center
                    ) { Text("⚠️", fontSize = 15.sp) }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(PBorder))
            }
        }

        // ── Moderation warning banner ──────────────────────────────────────────
        AnimatedVisibility(
            visible = moderationWarning != null,
            enter = slideInVertically() + fadeIn(),
            exit  = slideOutVertically() + fadeOut()
        ) {
            moderationWarning?.let { warning ->
                val isRepeated = violationCount >= 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isRepeated) danger.copy(0.12f) else amber.copy(0.10f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        warning,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isRepeated) danger else amber, fontSize = 11.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "OK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PTextMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp
                        ),
                        modifier = Modifier.clickable { moderationWarning = null }.padding(4.dp)
                    )
                }
            }
        }

        // ── Message list ──────────────────────────────────────────────────────
        if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Say the first thing",
                        style = MaterialTheme.typography.titleMedium.copy(color = PText, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("No pressure — just be yourself.", style = MaterialTheme.typography.bodySmall.copy(color = PTextMid))
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(PSageLight)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("🔒", fontSize = 11.sp)
                            Text(
                                "Messages are end-to-end encrypted",
                                style = MaterialTheme.typography.labelSmall.copy(color = PSage, fontSize = 11.sp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.weight(1f),
                state               = listState,
                contentPadding      = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PCardSoft)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Today", style = MaterialTheme.typography.labelSmall.copy(color = PTextMuted, fontSize = 11.sp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(messages, key = { it.id }) { msg ->
                    val mine = msg.uid == myUid
                    val bubbleShape = RoundedCornerShape(
                        topStart    = 18.dp, topEnd    = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp,
                        bottomEnd   = if (mine) 4.dp  else 18.dp
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                        verticalAlignment     = Alignment.Bottom
                    ) {
                        // Partner avatar on left
                        if (!mine) {
                            Box(
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(PCardSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                val p = partner?.photoUrl ?: ""
                                if (p.isNotBlank()) {
                                    coil.compose.AsyncImage(
                                        model = p, contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else { Text(partner?.emoji ?: "?", fontSize = 11.sp) }
                            }
                            Spacer(Modifier.width(6.dp))
                        }

                        // Bubble
                        Box(
                            modifier = Modifier
                                .widthIn(max = 270.dp)
                                .clip(bubbleShape)
                                .background(if (mine) PSage else PCard)
                                .then(
                                    if (!mine) Modifier.border(0.6.dp, PBorder, bubbleShape) else Modifier
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                msg.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color    = if (mine) Color.White else PText,
                                    fontSize = 15.sp, lineHeight = 21.sp
                                )
                            )
                        }

                        // My avatar on right
                        if (mine) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(PSageLight),
                                contentAlignment = Alignment.Center
                            ) {
                                val p = me?.photoUrl ?: ""
                                if (p.isNotBlank()) {
                                    coil.compose.AsyncImage(
                                        model = p, contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else { Text(me?.emoji ?: "?", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }

        // ── Input bar — always visible above keyboard ─────────────────────────
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(PBorder))
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(PCard)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PBg)
                    .border(0.8.dp, PBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value           = text,
                    onValueChange   = { text = it },
                    modifier        = Modifier.fillMaxWidth(),
                    textStyle       = MaterialTheme.typography.bodyMedium.copy(
                        color = PText, fontSize = 15.sp, lineHeight = 21.sp
                    ),
                    cursorBrush     = SolidColor(PSage),
                    decorationBox   = { inner ->
                        if (text.isEmpty()) Text(
                            "Message…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = PTextMuted, fontSize = 15.sp)
                        )
                        inner()
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    singleLine      = false,
                    maxLines        = 5
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) PSage else PBorder)
                    .clickable(enabled = text.isNotBlank()) { send() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "↑",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = if (text.isNotBlank()) Color.White else PTextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp
                    )
                )
            }
        }
    }

    // ── Block confirmation dialog ─────────────────────────────
    if (showBlockDlg) {
        AlertDialog(
            onDismissRequest = { showBlockDlg = false },
            containerColor   = PCard,
            titleContentColor   = PText,
            textContentColor    = PTextMid,
            title = { Text("🚫 Block User", fontWeight = FontWeight.Bold) },
            text  = {
                Text("Blocking ${partner?.name ?: "this user"} will prevent them from sending you messages. Are you sure?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockDlg = false
                    val myUidNow = com.google.firebase.auth.FirebaseAuth
                        .getInstance().currentUser?.uid ?: ""
                    val partnerUidNow = partner?.uid ?: ""
                    if (myUidNow.isNotBlank() && partnerUidNow.isNotBlank()) {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users").document(myUidNow)
                            .update("blockedUsers.$partnerUidNow",
                                com.google.firebase.firestore.FieldValue.serverTimestamp())
                    }
                }) {
                    Text("Block", color = PDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDlg = false }) {
                    Text("Cancel", color = PTextMid)
                }
            }
        )
    }

    // ── Report sheet ─────────────────────────────
    if (showReport) {
        ReportSheet(
            targetUid   = partner?.uid ?: "",
            targetAlias = partner?.name ?: "Partner",
            targetEmoji = partner?.emoji ?: "👤",
            groupId     = "",
            isDay       = true,
            onDismiss   = { showReport = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun F7HRule(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).height(0.5.dp).background(PBorder))
}
@Composable internal fun FHRule(modifier: Modifier = Modifier) = F7HRule(modifier)
@Composable internal fun F7SectionLabel(text: String, color: Color = PSage) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelSmall.copy(
            color = color.copy(0.75f), letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontSize = 10.sp
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Loading / Error states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Getting your island ready…", style = MaterialTheme.typography.bodyMedium.copy(color = PTextMid))
    }
}

@Composable
private fun PError(msg: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            msg,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = PTextMid, textAlign = TextAlign.Center, lineHeight = 22.sp
            )
        )
    }
}
