package com.ns.findyourintroverfriend.invite

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.google.firebase.auth.FirebaseAuth

// ── Colors matching app theme ──────────────────────────────────────────────────
private val IBg       = Color(0xFFF7F5F0)
private val ICard     = Color(0xFFFFFFFF)
private val IBorder   = Color(0xFFEDE8DF)
private val IPrimary  = Color(0xFF1C1916)
private val ISecondary= Color(0xFF8A8480)
private val IAccent   = Color(0xFF7C6AE8)
private val IAccentBg = Color(0xFFEFEBFF)

@Composable
fun InviteFriendsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uid     = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    // Build deep-link / referral text
    val inviteText = remember(uid) {
        "Hey! I'm on Veil — the app for introverts to connect anonymously. Join me!\n\nhttps://play.google.com/store/apps/details?id=com.ns.findyourintroverfriend"
    }

    // Lottie
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("Invite Friends Page.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = LottieConstants.IterateForever
    )

    // Button pulse animation
    val inf = rememberInfiniteTransition(label = "pulse")
    val btnScale by inf.animateFloat(
        initialValue = 1f, targetValue = 1.03f, label = "btnPulse",
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    var copyDone by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IBg)
            .systemBarsPadding()
    ) {
        // ── Header ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ICard)
                .border(0.5.dp, IBorder)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = IPrimary, modifier = Modifier.size(22.dp))
            }
            Text(
                "Invite Friends",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = IPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Content ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Lottie illustration
            LottieAnimation(
                composition = composition,
                progress    = { progress },
                modifier    = Modifier.size(260.dp)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Bring your people\nto Veil",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = IPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Introvert-friendly. Anonymous. Real connections.",
                fontSize = 14.sp,
                color = ISecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(36.dp))

            // ── Share button ──────────────────────────────────────────────────
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, inviteText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Invite friends to Veil"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .scale(btnScale),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IPrimary,
                    contentColor   = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Share Invite", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(12.dp))

            // ── Copy link button ──────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("Veil Invite", inviteText)
                    )
                    copyDone = true
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape  = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, IBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = ICard,
                    contentColor   = IPrimary
                )
            ) {
                Icon(
                    if (copyDone) Icons.Rounded.ContentCopy else Icons.Rounded.ContentCopy,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = if (copyDone) IAccent else IPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (copyDone) "Copied! ✓" else "Copy Link",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (copyDone) IAccent else IPrimary
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Info pill ─────────────────────────────────────────────────────
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(IAccentBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "🌙  Veil is free. Always.",
                    fontSize = 13.sp,
                    color = IAccent,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
