package com.ns.findyourintroverfriend.report

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ─────────────────────────────────────────────────────────────────────────────
//  ReportSheet — H1 Report / Block bottom sheet
//  Call this when user long-presses or taps "Report" on a member's profile.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    targetUid   : String,
    targetAlias : String,
    targetEmoji : String,
    groupId     : String,
    isDay       : Boolean = false,
    onDismiss   : () -> Unit,
    vm          : ReportViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    val sheetBg   = if (isDay) Color(0xFFF8FAFC) else Color(0xFF111827)
    val textPrim  = if (isDay) Color(0xFF1E293B) else Color(0xFFF0F4FA)
    val textSub   = if (isDay) Color(0xFF64748B) else Color(0xFF94A3B8)
    val accent    = Color(0xFFEF4444)  // danger red

    // Reset when sheet opens
    LaunchedEffect(targetUid) { vm.reset() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = sheetBg,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 6.dp)
                    .width(44.dp).height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.40f))
            )
        }
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "reportContent"
        ) { s ->
            when (s) {
                is ReportUiState.Success -> ReportSuccessContent(
                    targetAlias = s.targetAlias,
                    flagged     = s.flagged,
                    textPrim    = textPrim,
                    textSub     = textSub,
                    onDismiss   = onDismiss
                )
                is ReportUiState.AlreadyReported -> AlreadyReportedContent(
                    targetAlias = targetAlias,
                    textPrim    = textPrim,
                    textSub     = textSub,
                    onDismiss   = onDismiss
                )
                else -> ReportFormContent(
                    targetAlias   = targetAlias,
                    targetEmoji   = targetEmoji,
                    groupId       = groupId,
                    targetUid     = targetUid,
                    isSubmitting  = s is ReportUiState.Submitting,
                    error         = (s as? ReportUiState.Error)?.msg,
                    sheetBg       = sheetBg,
                    textPrim      = textPrim,
                    textSub       = textSub,
                    accent        = accent,
                    isDay         = isDay,
                    onSubmit      = { reason, note -> vm.submitReport(targetUid, targetAlias, groupId, reason, note) },
                    onDismiss     = onDismiss
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Form content
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ReportFormContent(
    targetAlias  : String,
    targetEmoji  : String,
    groupId      : String,
    targetUid    : String,
    isSubmitting : Boolean,
    error        : String?,
    sheetBg      : Color,
    textPrim     : Color,
    textSub      : Color,
    accent       : Color,
    isDay        : Boolean,
    onSubmit     : (ReportReason, String) -> Unit,
    onDismiss    : () -> Unit
) {
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }
    var extraNote      by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Report Member",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp
                    )
                )
                Text(
                    "$targetEmoji $targetAlias",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color    = textSub,
                        fontSize = 13.sp
                    )
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = textSub)
            }
        }

        HorizontalDivider(color = if (isDay) Color(0xFFE2E8F0) else Color(0xFF1E3050))

        Text(
            "Select a reason",
            style = MaterialTheme.typography.labelMedium.copy(
                color = textSub, fontSize = 12.sp, letterSpacing = 1.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Reason chips
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 260.dp)
        ) {
            items(ReportReason.values()) { reason ->
                val chosen = selectedReason == reason
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (chosen) accent.copy(alpha = 0.12f)
                            else if (isDay) Color(0x08000000) else Color.White.copy(alpha = 0.04f)
                        )
                        .border(
                            1.dp,
                            if (chosen) accent.copy(alpha = 0.60f)
                            else if (isDay) Color(0xFF94A3B8).copy(alpha = 0.20f)
                            else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedReason = reason }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        reason.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color      = if (chosen) accent else textPrim,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize   = 14.sp
                        )
                    )
                    if (chosen) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Optional note
        if (selectedReason != null) {
            OutlinedTextField(
                value            = extraNote,
                onValueChange    = { if (it.length <= 300) extraNote = it },
                modifier         = Modifier.fillMaxWidth(),
                placeholder      = { Text("Add context (optional)...", color = textSub) },
                colors           = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accent,
                    unfocusedBorderColor = if (isDay) Color(0xFFCBD5E1) else Color(0xFF1E3050),
                    focusedTextColor     = textPrim,
                    unfocusedTextColor   = textPrim
                ),
                maxLines         = 3,
                shape            = RoundedCornerShape(12.dp),
                textStyle        = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )
        }

        // Error
        if (error != null) {
            Text(
                error,
                color  = accent,
                style  = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        // Submit button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selectedReason != null) Brush.horizontalGradient(
                        listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                    )
                    else Brush.horizontalGradient(
                        listOf(Color(0xFFEF4444).copy(alpha = 0.3f), Color(0xFFDC2626).copy(alpha = 0.3f))
                    )
                )
                .then(
                    if (selectedReason != null && !isSubmitting)
                        Modifier.clickable { onSubmit(selectedReason!!, extraNote) }
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Flag, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        "Submit Report",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        )
                    )
                }
            }
        }

        Text(
            "Reports are anonymous. Submitting false reports may affect your own standing.",
            style = MaterialTheme.typography.labelSmall.copy(
                color = textSub.copy(alpha = 0.6f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun ReportSuccessContent(
    targetAlias : String,
    flagged     : Boolean,
    textPrim    : Color,
    textSub     : Color,
    onDismiss   : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Flag, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
        }
        Text(
            "Report Submitted",
            style = MaterialTheme.typography.titleMedium.copy(color = textPrim, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        )
        Text(
            if (flagged) "\"$targetAlias\" has been flagged by multiple members and is under review."
            else "Your report against \"$targetAlias\" has been recorded. If enough members report this user, they will be flagged.",
            style = MaterialTheme.typography.bodyMedium.copy(color = textSub, textAlign = TextAlign.Center, fontSize = 13.sp)
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text("Done", style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFFEF4444), fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun AlreadyReportedContent(
    targetAlias : String,
    textPrim    : Color,
    textSub     : Color,
    onDismiss   : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(40.dp))
        Text(
            "Already Reported",
            style = MaterialTheme.typography.titleMedium.copy(color = textPrim, fontWeight = FontWeight.Bold)
        )
        Text(
            "You've already submitted a report against \"$targetAlias\" in this group.",
            style = MaterialTheme.typography.bodyMedium.copy(color = textSub, textAlign = TextAlign.Center, fontSize = 13.sp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text("OK", style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold))
        }
    }
}
