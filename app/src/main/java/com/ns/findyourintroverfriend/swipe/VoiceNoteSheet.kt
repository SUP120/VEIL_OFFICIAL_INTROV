package com.ns.findyourintroverfriend.swipe

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

private val VNPrimary   = Color(0xFF000000)
private val VNSecondary = Color(0xFF737373)
private val VNSurface   = Color(0xFFFFFFFF)
private val VNRed       = Color(0xFFED4956)
private val VNDivider   = Color(0xFFEFEFEF)

// ─────────────────────────────────────────────────────────────────────────────
//  Recording Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VoiceNoteRecorderSheet(
    matchId       : String,
    uid           : String,
    myAlias       : String,
    cookieBalance : Int,
    onSpendCookies: (amount: Int, reason: String, cb: (Boolean) -> Unit) -> Unit,
    onDismiss     : () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val db      = remember { FirebaseFirestore.getInstance() }

    var hasPermission  by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var isRecording    by remember { mutableStateOf(false) }
    var hasRecording   by remember { mutableStateOf(false) }
    var isUploading    by remember { mutableStateOf(false) }
    var uploadError    by remember { mutableStateOf("") }
    var seconds        by remember { mutableIntStateOf(0) }
    var outputFile     by remember { mutableStateOf<File?>(null) }
    var activeRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { activeRecorder?.stop() }
            runCatching { activeRecorder?.release() }
        }
    }

    // Auto-stop at 60 s
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording && seconds < 60) { delay(1000L); seconds++ }
            if (seconds >= 60 && isRecording) {
                runCatching { activeRecorder?.stop() }
                runCatching { activeRecorder?.release() }
                activeRecorder = null; isRecording = false; hasRecording = true
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.55f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(VNSurface)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(VNDivider))
            Spacer(Modifier.height(20.dp))

            Text("Voice Note", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = VNPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    !hasPermission             -> "Microphone permission needed"
                    isUploading                -> "Uploading..."
                    hasRecording               -> "Ready to send · costs 8 cookies"
                    isRecording                -> "Recording"
                    cookieBalance < 8          -> "Need 8 cookies to send voice notes"
                    else                       -> "Tap mic to start · max 60 sec"
                },
                fontSize = 13.sp, color = if (cookieBalance < 8 && !hasRecording && !isRecording) Color(0xFFED4956) else VNSecondary
            )

            if (uploadError.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(uploadError, fontSize = 11.sp, color = Color(0xFFED4956))
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                fontSize = 40.sp, fontWeight = FontWeight.Light, color = VNPrimary
            )

            Spacer(Modifier.height(28.dp))

            // Big record button
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(
                        when {
                            !hasPermission -> VNDivider
                            isRecording    -> VNRed
                            hasRecording   -> VNSecondary
                            else           -> VNPrimary
                        }
                    )
                    .clickable(enabled = !isUploading && !hasRecording) {
                        if (!hasPermission) {
                            permLauncher.launch(Manifest.permission.RECORD_AUDIO); return@clickable
                        }
                        if (!isRecording) {
                            uploadError = ""
                            val file = File(context.cacheDir, "vn_${System.currentTimeMillis()}.m4a")
                            outputFile = file; seconds = 0
                            val rec = MediaRecorder(context)
                            try {
                                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                rec.setAudioEncodingBitRate(128_000)
                                rec.setAudioSamplingRate(44100)
                                rec.setOutputFile(file.absolutePath)
                                rec.prepare(); rec.start()
                                activeRecorder = rec; isRecording = true
                            } catch (_: Exception) { runCatching { rec.release() } }
                        } else {
                            runCatching { activeRecorder?.stop() }
                            runCatching { activeRecorder?.release() }
                            activeRecorder = null; isRecording = false; hasRecording = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        !hasPermission -> Icons.Rounded.MicOff
                        isRecording    -> Icons.Rounded.Stop
                        else           -> Icons.Rounded.Mic
                    },
                    null, tint = Color.White, modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !hasPermission -> "Tap to grant permission"
                    isRecording    -> "Tap to stop"
                    hasRecording   -> "Recorded"
                    else           -> ""
                },
                fontSize = 11.sp, color = VNSecondary
            )

            if (hasRecording && !isUploading) {
                TextButton(onClick = { hasRecording = false; seconds = 0; outputFile = null; uploadError = "" }) {
                    Text("Re-record", fontSize = 12.sp, color = VNSecondary)
                }
            } else { Spacer(Modifier.height(30.dp)) }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Cancel", color = VNSecondary) }

                Button(
                    onClick = {
                        val file = outputFile ?: return@Button
                        if (!file.exists() || file.length() == 0L) return@Button
                        isUploading = true; uploadError = ""
                        // Deduct 8 cookies before uploading
                        onSpendCookies(8, "Voice Note") { ok ->
                            if (!ok) {
                                isUploading = false
                                uploadError = "Not enough cookies (need 8)."
                                return@onSpendCookies
                            }
                            scope.launch {
                                try {
                                    val url = CloudinaryUploader.uploadFile(
                                        file         = file,
                                        resourceType = "video",
                                        mimeType     = "audio/mp4",
                                        folder       = "veil/voiceNotes/$matchId"
                                    )
                                    db.collection("matches").document(matchId)
                                        .collection("messages")
                                        .add(SwipeMessage(
                                            messageId   = db.collection("_").document().id,
                                            senderUid   = uid,
                                            senderAlias = myAlias,
                                            text        = "voice_note::$url::$seconds",
                                            sentAt      = System.currentTimeMillis()
                                        )).await()
                                    onDismiss()
                                } catch (_: Exception) {
                                    uploadError = "Upload failed. Check connection."
                                    isUploading = false
                                }
                            }
                        }
                    },
                    enabled = hasRecording && !isUploading && cookieBalance >= 8,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VNPrimary,
                        disabledContainerColor = Color(0xFFBDBDBD)
                    )
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Send, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (cookieBalance >= 8) "Send · 8" else "Need 8 🍪", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Player Bubble
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VoiceNotePlayerBubble(url: String, durationSec: Int, mine: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    var player    by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) { onDispose { player?.release() } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (mine) Color(0xFF000000) else Color(0xFFF0F0F0))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(if (mine) Color.White.copy(0.18f) else Color(0xFFE0E0E0))
                .clickable {
                    if (isPlaying) {
                        player?.pause(); isPlaying = false
                    } else {
                        try {
                            if (player == null) {
                                player = MediaPlayer().apply {
                                    setDataSource(url)
                                    setOnCompletionListener { isPlaying = false; it.seekTo(0) }
                                    prepare()
                                }
                            }
                            player?.start(); isPlaying = true
                        } catch (_: Exception) {}
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null, tint = if (mine) Color.White else Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text("Voice Note", fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = if (mine) Color.White else Color.Black)
            Text("%d:%02d".format(durationSec / 60, durationSec % 60),
                fontSize = 11.sp, color = if (mine) Color.White.copy(0.65f) else Color(0xFF737373))
        }
    }
}
