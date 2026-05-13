package com.ns.findyourintroverfriend.payment

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.*
import com.ns.findyourintroverfriend.R
import com.ns.findyourintroverfriend.billing.ChatPack
import com.ns.findyourintroverfriend.billing.PurchaseState
import com.ns.findyourintroverfriend.billing.VeilBillingClient
import com.ns.findyourintroverfriend.billing.VeilProductId

// ── Palette — pure white background, clean black text, warm gold accent ────
private val White       = Color(0xFFFFFFFF)
private val Off         = Color(0xFFF7F7F8)     // card surface
private val Line        = Color(0xFFE5E5EA)     // hairlines / borders
private val Ink         = Color(0xFF1C1C1E)     // primary text
private val Muted       = Color(0xFF6C6C70)     // secondary text
private val Faint       = Color(0xFFA8A8B0)     // tertiary / timestamps
private val Gold        = Color(0xFFF0A500)     // popular badge + price highlight
private val GoldSurface = Color(0xFFFFF8EC)     // popular card tint

@Composable
fun ChatCreditsScreen(
    onBack: () -> Unit = {},
) {
    val context       = LocalContext.current
    val activity      = context as? Activity
    val packs         by VeilBillingClient.packs.collectAsStateWithLifecycle()
    val purchaseState by VeilBillingClient.purchaseState.collectAsStateWithLifecycle()

    var showSuccess by remember { mutableStateOf(false) }
    var successMsg  by remember { mutableStateOf("") }
    var showError   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }

    LaunchedEffect(purchaseState) {
        when (val ps = purchaseState) {
            is PurchaseState.Success -> {
                successMsg  = "+${ps.messages} messages added"
                showSuccess = true
                VeilBillingClient.resetPurchaseState()
            }
            is PurchaseState.Failed -> {
                errorMsg  = ps.message
                showError = true
                VeilBillingClient.resetPurchaseState()
            }
            else -> {}
        }
    }

    // Rocket Lottie
    val lottieComp by rememberLottieComposition(
        LottieCompositionSpec.Asset("rocket_success.lottie")
    )
    val lottieProgress by animateLottieCompositionAsState(
        composition = lottieComp,
        iterations  = LottieConstants.IterateForever,
        speed       = 0.9f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Text(
                    "Extend Chat",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Ink, fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Line))

            Spacer(Modifier.height(12.dp))

            // ── Lottie — businessman rocket (smaller, tasteful) ────────────
            LottieAnimation(
                composition = lottieComp,
                progress    = { lottieProgress },
                modifier    = Modifier.size(140.dp),
            )

            Spacer(Modifier.height(4.dp))

            // ── Headline ───────────────────────────────────────────────────
            Text(
                "Keep the conversation going",
                style = MaterialTheme.typography.titleLarge.copy(
                    color      = Ink,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    fontSize   = 20.sp,
                )
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Your 7 free messages are up. One-time purchase — no subscription.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = Muted,
                    textAlign  = TextAlign.Center,
                    lineHeight = 19.sp,
                    fontSize   = 12.sp,
                ),
                modifier = Modifier.padding(horizontal = 44.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── Pack cards ─────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (packs.isEmpty()) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Off)
                        )
                    }
                } else {
                    packs.forEach { pack ->
                        PackRow(
                            pack         = pack,
                            isPurchasing = purchaseState is PurchaseState.Pending,
                            onBuy        = {
                                if (activity != null) VeilBillingClient.buy(activity, pack)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Trust line ─────────────────────────────────────────────────
            Text(
                "🔒  Secured by Google Play Billing · Instant delivery",
                style = MaterialTheme.typography.labelSmall.copy(
                    color     = Faint,
                    textAlign = TextAlign.Center,
                    fontSize  = 10.sp,
                )
            )

            Spacer(Modifier.height(28.dp))
        }
    }

    // ── Success dialog ─────────────────────────────────────────────────────
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false; onBack() },
            containerColor   = White,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Done ✓", fontWeight = FontWeight.Bold, color = Ink) },
            text  = { Text(successMsg, color = Muted, textAlign = TextAlign.Center) },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink)
                        .clickable { showSuccess = false; onBack() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Continue chatting", fontWeight = FontWeight.SemiBold, color = White, fontSize = 14.sp)
                }
            }
        )
    }

    // ── Error dialog ───────────────────────────────────────────────────────
    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            containerColor   = White,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Purchase failed", fontWeight = FontWeight.Bold, color = Ink) },
            text  = { Text(errorMsg, color = Muted) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("OK", color = Ink, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PackRow — clean single-row card: icon · info · price · "Extend" button
//  No gradients. No heavy borders. Just information and a clear action.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PackRow(
    pack         : ChatPack,
    isPurchasing : Boolean,
    onBuy        : () -> Unit,
) {
    val isPopular = pack.productId == VeilProductId.CHAT_EXTEND_50

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPopular) GoldSurface else Off)
            .border(
                width = if (isPopular) 1.dp else 0.6.dp,
                color = if (isPopular) Gold.copy(0.40f) else Line,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Rupee avatar image ───────────────────────────────────────────
        val avatarRes = if (isPopular) R.drawable.avatar_50 else R.drawable.avatar_20
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(
                    width = if (isPopular) 1.5.dp else 1.dp,
                    color = if (isPopular) Gold.copy(0.45f) else Line,
                    shape = CircleShape
                )
        ) {
            Image(
                painter            = painterResource(id = avatarRes),
                contentDescription = "${pack.messages} messages avatar",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
            )
        }

        // ── Label + tag ────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${pack.messages} Messages",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Ink,
                )
                if (isPopular) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Gold)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "POPULAR",
                            fontSize      = 7.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            Text(
                if (isPopular) "Best for longer chats" else "Quick top-up",
                fontSize = 11.sp,
                color    = Muted,
            )
        }

        // ── Price + Extend button ───────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                pack.displayPrice,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = if (isPopular) Gold else Ink,
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPopular) Gold else Ink)
                    .clickable(enabled = !isPurchasing) { onBuy() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        color       = White,
                        strokeWidth = 1.5.dp,
                        modifier    = Modifier.size(14.dp)
                    )
                } else {
                    Text(
                        "Extend",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = White,
                    )
                }
            }
        }
    }
}
