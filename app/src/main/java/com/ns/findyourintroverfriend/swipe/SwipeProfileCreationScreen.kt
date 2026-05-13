@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ns.findyourintroverfriend.swipe

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ns.findyourintroverfriend.util.VeilSafetyUtil

// Pastel palette (same as SwipeScreen)
private val CBg         = Color(0xFFF7F3ED)
private val CCard       = Color(0xFFFFFFFF)
private val CCardBorder = Color(0xFFEDE8DF)
private val CAccent     = Color(0xFF7C6AE8)
private val CAccentSoft = Color(0xFFEFEBFF)
private val CPrimary    = Color(0xFF1A1523)
private val CSecondary  = Color(0xFF6B5E7E)
private val CMuted      = Color(0xFFAA9DB8)

// ─────────────────────────────────────────────────────────────────────────────
//  SwipeProfileCreationScreen — 3-step full-screen setup (pastel theme)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SwipeProfileCreationScreen(
    vm        : SwipeViewModel = viewModel(),
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    // After save completes, refresh feed then go back to swipe tab
    LaunchedEffect(vm.profileSaved) {
        if (vm.profileSaved) {
            vm.refreshFeed()
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CBg)
            .systemBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).shadow(4.dp, CircleShape, spotColor = Color.Black.copy(0.06f))
                            .clip(CircleShape).background(CCard).border(1.dp, CCardBorder, CircleShape)
                            .clickable { if (step > 0) step-- else onComplete() },
                        Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, null, tint = CSecondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${step + 1} of $totalSteps", fontSize = 12.sp, color = CMuted)
                }

                // Progress bar
                val progress by animateFloatAsState(
                    targetValue   = (step + 1f) / totalSteps,
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    label         = "progress"
                )
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(CCardBorder)
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.horizontalGradient(listOf(CAccent, Color(0xFF9B7BFF))))
                    )
                }
            }

            // ── Step content ──────────────────────────────────────────────────
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (step) {
                    0 -> CStep0Gender(vm)
                    1 -> CStep1(vm)
                    2 -> CStep2(vm)
                    3 -> CStep3(vm)
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── CTA ───────────────────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                vm.saveError?.let {
                    Text(it, fontSize = 12.sp, color = Color(0xFFE05A5A), modifier = Modifier.padding(bottom = 8.dp))
                }
                Button(
                    onClick = {
                        if (step < totalSteps - 1) step++
                        else vm.saveSwipeProfile()
                    },
                    enabled = when (step) {
                        0    -> vm.gender.isNotBlank() && vm.genderPolicyAcknowledged
                        1    -> vm.comfortWhen.trim().length >= 10 &&
                                vm.perfectEvening.trim().length >= 10 &&
                                vm.openUpWhen.trim().length >= 10
                        else -> true
                    } && !vm.isSavingProfile,
                    modifier = Modifier.fillMaxWidth().height(52.dp).shadow(6.dp, RoundedCornerShape(14.dp), spotColor = CAccent.copy(0.20f)),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CAccent, disabledContainerColor = CCardBorder)
                ) {
                    if (vm.isSavingProfile) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (step < totalSteps - 1) Icons.Rounded.ArrowForward else Icons.Rounded.Check,
                            null, tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Step 0 — Gender Identity Verification ───────────────────────────────────
@Composable private fun CStep0Gender(vm: SwipeViewModel) {
    var showPolicy by remember { mutableStateOf(false) }

    if (showPolicy) {
        AlertDialog(
            onDismissRequest = { showPolicy = false },
            confirmButton = {
                TextButton(onClick = { showPolicy = false }) {
                    Text("Understood", fontWeight = FontWeight.SemiBold, color = CAccent)
                }
            },
            title = { Text("Identity Policy", fontWeight = FontWeight.Bold, color = CPrimary) },
            text = {
                Text(VeilSafetyUtil.GENDER_POLICY, fontSize = 13.sp, color = CSecondary, lineHeight = 20.sp)
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    CHeader("Who are you?", "Select your gender identity. This is used to find compatible matches.")

    // Gender chips
    val genders = listOf("Male" to "♂", "Female" to "♀", "Non-binary" to "⚧")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        genders.forEach { (label, emoji) ->
            val selected = vm.gender == label
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) CAccentSoft else CCard)
                    .border(1.5.dp, if (selected) CAccent else CCardBorder, RoundedCornerShape(14.dp))
                    .clickable { vm.gender = label }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(emoji, fontSize = 22.sp)
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) CAccent else CPrimary
                )
                Spacer(Modifier.weight(1f))
                if (selected) Icon(
                    Icons.Rounded.CheckCircle, null,
                    tint = CAccent, modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // ⚠️ Ban warning card
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF3F3))
            .border(1.dp, Color(0xFFE05A5A).copy(0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Color(0xFFE05A5A), modifier = Modifier.size(18.dp))
                Text(
                    "Strict Identity Policy — Instant Ban",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE05A5A)
                )
            }
            Text(
                "Misrepresenting your gender to manipulate matches is a permanent, non-appealable ban. " +
                "This includes catfishing, gender deception, or impersonation of any kind.",
                fontSize = 12.sp, color = Color(0xFFB91C1C), lineHeight = 18.sp
            )
            TextButton(
                onClick = { showPolicy = true },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Read full identity policy →", fontSize = 11.sp, color = Color(0xFFE05A5A))
            }
        }
    }

    // Mandatory acknowledgment checkbox
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CAccentSoft)
            .clickable { vm.genderPolicyAcknowledged = !vm.genderPolicyAcknowledged }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = vm.genderPolicyAcknowledged,
            onCheckedChange = { vm.genderPolicyAcknowledged = it },
            colors = CheckboxDefaults.colors(checkedColor = CAccent, uncheckedColor = CMuted)
        )
        Text(
            "I confirm that my selected gender is my real identity and I have read the identity policy.",
            fontSize = 12.sp, color = CSecondary, lineHeight = 18.sp, modifier = Modifier.weight(1f)
        )
    }
}

// ── Step 1 — Deep introvert questions ─────────────────────────────────────────
@Composable private fun CStep1(vm: SwipeViewModel) {
    CHeader("Who are you, really?", "Introverts connect through depth. Answer honestly — these appear on your card.")

    CDeepField("I feel most comfortable when…", vm.comfortWhen, { vm.comfortWhen = it },
        "e.g. I'm in a cozy corner with music and no pressure to talk")
    CDeepField("A perfect evening looks like…", vm.perfectEvening, { vm.perfectEvening = it },
        "e.g. Hot tea, a good book, rain outside, zero notifications")
    CDeepField("I open up when…", vm.openUpWhen, { vm.openUpWhen = it },
        "e.g. Someone asks about something I'm genuinely passionate about")
    CDeepField("Things I avoid…", vm.thingsIAvoid, { vm.thingsIAvoid = it },
        "e.g. Loud parties, phone calls from unknown numbers", required = false)
}

// ── Step 2 — Energy preferences + hobbies ────────────────────────────────────
@Composable private fun CStep2(vm: SwipeViewModel) {
    CHeader("Your energy style", "Helps find someone whose rhythm matches yours.")

    CTogglePair("I prefer", "Quiet places", "quiet", "Social places", "social", vm.placePreference) { vm.placePreference = it }
    CTogglePair("I prefer to", "Text", "text", "Call", "call", vm.commStyle) { vm.commStyle = it }
    CTogglePair("I am a", "Night owl", "nightowl", "Early bird", "earlybird", vm.sleepStyle) { vm.sleepStyle = it }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("What are you into?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CPrimary)
        Text("Pick up to 5", fontSize = 12.sp, color = CMuted)

        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            HOBBY_OPTIONS.forEach { hobby ->
                val sel = hobby in vm.selectedHobbies
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (sel) CAccentSoft else CCard)
                        .border(0.8.dp, if (sel) CAccent else CCardBorder, RoundedCornerShape(20.dp))
                        .clickable { vm.toggleHobby(hobby) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(hobby, fontSize = 12.sp,
                        color = if (sel) CAccent else CSecondary,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ── Step 3 — Conversation starters ───────────────────────────────────────────
@Composable private fun CStep3(vm: SwipeViewModel) {
    CHeader("Break the ice before the chat", "These appear on your card so matches know how to start.")

    CDeepField("Ask me about…", vm.askMeAbout, { vm.askMeAbout = it },
        "e.g. indie films, lucid dreaming, or why I prefer cats", required = false)
    CDeepField("I can talk for hours about…", vm.talkHoursAbout, { vm.talkHoursAbout = it },
        "e.g. philosophy, music theory, or random history facts", required = false)

    // Info note
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(CAccentSoft).border(0.8.dp, CAccent.copy(0.20f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Info, null, tint = CAccent, modifier = Modifier.size(16.dp))
            Text(
                "Your name, age, and photo are pulled from your existing Veil profile automatically.",
                fontSize = 12.sp, color = CSecondary, lineHeight = 18.sp
            )
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────
@Composable private fun CHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CPrimary)
        Text(subtitle, fontSize = 13.sp, color = CSecondary, lineHeight = 19.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CDeepField(
    label: String, value: String, onValue: (String) -> Unit,
    placeholder: String, required: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CPrimary)
            if (!required) Text("optional", fontSize = 10.sp, color = CMuted)
        }
        OutlinedTextField(
            value         = value,
            onValueChange = { if (it.length <= 200) onValue(it) },
            placeholder   = { Text(placeholder, fontSize = 13.sp, color = CMuted) },
            modifier      = Modifier.fillMaxWidth(),
            minLines      = 2, maxLines = 4,
            shape         = RoundedCornerShape(14.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = CAccent,
                unfocusedBorderColor    = CCardBorder,
                focusedTextColor        = CPrimary,
                unfocusedTextColor      = CPrimary,
                cursorColor             = CAccent,
                focusedContainerColor   = CCard,
                unfocusedContainerColor = CCard
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, lineHeight = 20.sp)
        )
        if (required && value.isNotEmpty() && value.trim().length < 10)
            Text("Too short — add a bit more detail.", fontSize = 11.sp, color = Color(0xFFE05A5A))
    }
}

@Composable private fun CTogglePair(
    label: String, optA: String, valA: String,
    optB: String, valB: String, current: String, onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 12.sp, color = CMuted)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(CCard).border(0.8.dp, CCardBorder, RoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(optA to valA, optB to valB).forEach { (text, value) ->
                val sel = current == value
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) CAccentSoft else Color.Transparent)
                        .clickable { onSelect(value) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) CAccent else CSecondary)
                }
            }
        }
    }
}
