package com.ns.findyourintroverfriend.myth

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

sealed class TomSetupResult {
    object Idle    : TomSetupResult()
    object Saving  : TomSetupResult()
    object Done    : TomSetupResult()
    data class Err(val msg: String) : TomSetupResult()
}

class TrueOrMythSetupViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _result = MutableStateFlow<TomSetupResult>(TomSetupResult.Idle)
    val result: StateFlow<TomSetupResult> = _result.asStateFlow()

    // Check if already submitted
    private val _alreadyDone = MutableStateFlow(false)
    val alreadyDone: StateFlow<Boolean> = _alreadyDone.asStateFlow()

    init { checkExisting() }

    private fun checkExisting() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                val groupId = userDoc.getString("groupId") ?: return@launch
                val memDoc  = db.collection("groups").document(groupId)
                    .collection("members").document(uid).get().await()
                if (memDoc.getString("tomFact") != null) {
                    _alreadyDone.value = true
                }
            } catch (e: Exception) {
                Log.w("TOMSetup", "check failed: ${e.message}")
            }
        }
    }

    fun save(fact: String, isTrue: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (fact.trim().length < 5) return
        _result.value = TomSetupResult.Saving
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                val groupId = userDoc.getString("groupId") ?: run {
                    _result.value = TomSetupResult.Err("No active group.")
                    return@launch
                }
                db.collection("groups").document(groupId)
                    .collection("members").document(uid)
                    .set(mapOf("tomFact" to fact.trim(), "tomIsTrue" to isTrue), com.google.firebase.firestore.SetOptions.merge())
                    .await()
                _alreadyDone.value = true
                _result.value = TomSetupResult.Done
                Log.d("TOMSetup", "fact saved: '$fact' isTrue=$isTrue")
            } catch (e: Exception) {
                Log.e("TOMSetup", "save failed", e)
                _result.value = TomSetupResult.Err(e.localizedMessage ?: "Save failed.")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrueOrMythSetupScreen(
    vm     : TrueOrMythSetupViewModel = viewModel(),
    onDone : () -> Unit
) {
    val result     by vm.result.collectAsState()
    val alreadyDone by vm.alreadyDone.collectAsState()

    // Auto-dismiss when done
    LaunchedEffect(result) {
        if (result is TomSetupResult.Done) onDone()
    }
    // If already submitted, just go back immediately
    LaunchedEffect(alreadyDone) {
        if (alreadyDone && result is TomSetupResult.Idle) onDone()
    }

    var fact    by remember { mutableStateOf("") }
    var isTrue  by remember { mutableStateOf<Boolean?>(null) }
    var touched by remember { mutableStateOf(false) }

    val inf = rememberInfiniteTransition(label = "tomSetup")
    val glow by inf.animateFloat(
        initialValue  = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "g"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(
                    Color(0xFF0A060F), Color(0xFF0E0A18), Color(0xFF0A060F)
                ))
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Emoji orb header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF3A1060).copy(alpha = 0.5f), VeilSurface)
                        )
                    )
                    .border(
                        1.dp,
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF7C5A).copy(alpha = glow * 0.7f),
                                VeilAccentSand.copy(alpha = glow * 0.4f),
                                Color(0xFFFF7C5A).copy(alpha = glow * 0.7f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🎭", fontSize = 30.sp)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Your True or Myth",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color      = VeilTextPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize   = 22.sp
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Write one statement about yourself.\nYour circle will guess if it's True or Myth.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color     = VeilTextTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    fontSize  = 13.sp
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            )

            Spacer(Modifier.height(32.dp))

            // ── Fact text field ────────────────────────────────────────────────
            OutlinedTextField(
                value         = fact,
                onValueChange = { if (it.length <= 120) { fact = it; touched = true } },
                placeholder   = {
                    Text(
                        "e.g. I once stayed up 48 hours coding…",
                        style = MaterialTheme.typography.bodyMedium.copy(color = VeilTextTertiary)
                    )
                },
                textStyle     = MaterialTheme.typography.bodyLarge.copy(
                    color    = VeilTextPrimary,
                    fontSize = 15.sp
                ),
                minLines      = 3,
                maxLines      = 4,
                shape         = RoundedCornerShape(16.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = Color(0xFFFF7C5A).copy(alpha = 0.55f),
                    unfocusedBorderColor    = VeilDivider,
                    cursorColor             = Color(0xFFFF7C5A),
                    focusedContainerColor   = VeilSurface,
                    unfocusedContainerColor = VeilSurface
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "${fact.length}/120",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (fact.length > 100) Color(0xFFFF7C5A).copy(0.6f) else VeilTextDisabled,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── True / Myth toggle ────────────────────────────────────────────
            Text(
                "Is it actually true?",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = VeilTextSecondary, letterSpacing = 0.3.sp
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TomToggleCard(
                    label    = "✅  True",
                    selected = isTrue == true,
                    accent   = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                    onClick  = { isTrue = true }
                )
                TomToggleCard(
                    label    = "🌀  It's a Myth",
                    selected = isTrue == false,
                    accent   = Color(0xFFFF7C5A),
                    modifier = Modifier.weight(1f),
                    onClick  = { isTrue = false }
                )
            }

            // Error hint
            AnimatedVisibility(
                visible = touched && (fact.trim().length < 5 || isTrue == null),
                enter   = fadeIn() + slideInVertically { -it / 2 }
            ) {
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        fact.trim().length < 5 -> "Write at least 5 characters."
                        isTrue == null         -> "Tell us if it's true or a myth."
                        else                   -> ""
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = VeilError, fontSize = 11.sp
                    )
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Save button ───────────────────────────────────────────────────
            val canSave = fact.trim().length >= 5 && isTrue != null
            val isSaving = result is TomSetupResult.Saving

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (canSave)
                            Brush.horizontalGradient(listOf(Color(0xFF6B2A15), Color(0xFFFF7C5A), Color(0xFF6B2A15)))
                        else
                            Brush.horizontalGradient(listOf(VeilSurface, VeilSurface))
                    )
                    .clickable(enabled = canSave && !isSaving) { vm.save(fact, isTrue!!) },
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(22.dp),
                        color       = Color(0xFFFF7C5A),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Set my statement  🎭",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color    = if (canSave) VeilTextPrimary else VeilTextDisabled,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            (result as? TomSetupResult.Err)?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it.msg,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = VeilError, textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Toggle option card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TomToggleCard(
    label    : String,
    selected : Boolean,
    accent   : Color,
    modifier : Modifier,
    onClick  : () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (selected) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "scale"
    )
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else VeilSurface)
            .border(
                width = if (selected) 1.2.dp else 0.6.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (selected) 0.7f else 0.2f),
                        accent.copy(alpha = if (selected) 0.4f else 0.1f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                color      = if (selected) accent else VeilTextSecondary,
                fontSize   = 13.sp,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                             else androidx.compose.ui.text.font.FontWeight.Normal
            )
        )
    }
}
