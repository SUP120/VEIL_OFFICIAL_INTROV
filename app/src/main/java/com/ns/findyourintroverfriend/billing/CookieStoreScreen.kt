package com.ns.findyourintroverfriend.billing

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Design tokens ─────────────────────────────────────────────────────────────
private val CSBg      = Color(0xFFFFFFFF)
private val CSBlack   = Color(0xFF1A1A1A)
private val CSGray    = Color(0xFF8E8E8E)
private val CSBorder  = Color(0xFFEFEFEF)
private val CSAccent  = Color(0xFF1A1A1A)   // black pill for price
private val CSGold    = Color(0xFFD97706)   // amber for "popular" badge
private val CSGoldBg  = Color(0xFFFFFBEB)
private val CSGreen   = Color(0xFF16A34A)
private val CSGreenBg = Color(0xFFF0FDF4)
private val CSRed     = Color(0xFFDC2626)
private val CSRedBg   = Color(0xFFFEF2F2)
private val CSLightBg = Color(0xFFF9F9F9)

@Composable
fun CookieStoreScreen(
    cookieVm : CookieViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack   : () -> Unit = {}
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    val cookiePacks     by VeilBillingClient.cookiePacks.collectAsStateWithLifecycle()
    val billingState    by VeilBillingClient.billingState.collectAsStateWithLifecycle()
    val purchaseState   by VeilBillingClient.cookiePurchaseState.collectAsStateWithLifecycle()
    val balance         by cookieVm.cookieBalance.collectAsStateWithLifecycle()

    // Reset to Idle when leaving
    DisposableEffect(Unit) {
        onDispose { VeilBillingClient.resetCookiePurchaseState() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(CSBg)
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBackIosNew, null, tint = CSBlack,
                        modifier = Modifier.size(20.dp))
                }
                Text(
                    "Cookie Store",
                    fontSize      = 20.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = CSBlack,
                    letterSpacing = (-0.3).sp,
                    modifier      = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = CSBorder, thickness = 0.8.dp)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                // ── Balance card ──────────────────────────────────────────────
                BalanceCard(balance)

                Spacer(Modifier.height(28.dp))

                Text(
                    "Choose a pack",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = CSBlack
                )
                Spacer(Modifier.height(12.dp))

                // ── Pack grid ─────────────────────────────────────────────────
                when (billingState) {
                    is BillingState.Connecting -> PackSkeletons()
                    is BillingState.Error      -> BillingErrorCard()
                    is BillingState.Ready      -> {
                        val packs = if (cookiePacks.isNotEmpty()) cookiePacks
                        else defaultPacks()   // fallback if Play hasn't loaded yet

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                packs.getOrNull(0)?.let { pack ->
                                    PackCard(
                                        pack     = pack,
                                        enabled  = purchaseState !is CookiePurchaseState.Pending &&
                                                   purchaseState !is CookiePurchaseState.Verifying,
                                        modifier = Modifier.weight(1f)
                                    ) { activity?.let { a -> VeilBillingClient.buyCookies(a, pack.productId) } }
                                }
                                packs.getOrNull(1)?.let { pack ->
                                    PackCard(
                                        pack     = pack,
                                        enabled  = purchaseState !is CookiePurchaseState.Pending &&
                                                   purchaseState !is CookiePurchaseState.Verifying,
                                        modifier = Modifier.weight(1f)
                                    ) { activity?.let { a -> VeilBillingClient.buyCookies(a, pack.productId) } }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                packs.getOrNull(2)?.let { pack ->
                                    PackCard(
                                        pack     = pack,
                                        enabled  = purchaseState !is CookiePurchaseState.Pending &&
                                                   purchaseState !is CookiePurchaseState.Verifying,
                                        modifier = Modifier.weight(1f)
                                    ) { activity?.let { a -> VeilBillingClient.buyCookies(a, pack.productId) } }
                                }
                                packs.getOrNull(3)?.let { pack ->
                                    PackCard(
                                        pack     = pack,
                                        enabled  = purchaseState !is CookiePurchaseState.Pending &&
                                                   purchaseState !is CookiePurchaseState.Verifying,
                                        modifier = Modifier.weight(1f)
                                    ) { activity?.let { a -> VeilBillingClient.buyCookies(a, pack.productId) } }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── What you can do ───────────────────────────────────────────
                UsageSection()

                // ── Legal note ────────────────────────────────────────────────
                Spacer(Modifier.height(16.dp))
                Text(
                    "Purchases are processed by Google Play and are non-refundable.\n" +
                    "Cookies are tied to your account and do not expire.",
                    fontSize   = 11.sp,
                    color      = CSGray,
                    lineHeight = 16.sp,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(32.dp))
            }
        }

        // ── Pending overlay ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = purchaseState is CookiePurchaseState.Pending ||
                      purchaseState is CookiePurchaseState.Verifying,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape  = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CSBg),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color        = CSBlack,
                            strokeWidth  = 2.dp,
                            modifier     = Modifier.size(36.dp)
                        )
                        Text(
                            if (purchaseState is CookiePurchaseState.Verifying)
                                "Verifying purchase…" else "Processing…",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color      = CSBlack
                        )
                        Text("Please don't close the app", fontSize = 12.sp, color = CSGray)
                    }
                }
            }
        }

        // ── Success sheet ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = purchaseState is CookiePurchaseState.Success,
            enter   = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit    = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val granted = (purchaseState as? CookiePurchaseState.Success)?.cookiesGranted ?: 0
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(16.dp),
                    shape     = RoundedCornerShape(24.dp),
                    colors    = CardDefaults.cardColors(containerColor = CSBg),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(
                        Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(64.dp)
                                .clip(CircleShape)
                                .background(CSGreenBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = CSGreen,
                                modifier = Modifier.size(32.dp))
                        }
                        Text("Purchase Successful!", fontSize = 20.sp,
                            fontWeight = FontWeight.Bold, color = CSBlack)
                        Text(
                            "+$granted 🍪 added to your balance",
                            fontSize = 15.sp, color = CSGray, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                VeilBillingClient.resetCookiePurchaseState()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = CSBlack, contentColor = Color.White
                            )
                        ) {
                            Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ── Failure sheet ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = purchaseState is CookiePurchaseState.Failed,
            enter   = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit    = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val msg = (purchaseState as? CookiePurchaseState.Failed)?.message ?: "Unknown error"
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(16.dp),
                    shape     = RoundedCornerShape(24.dp),
                    colors    = CardDefaults.cardColors(containerColor = CSBg),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(
                        Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(64.dp)
                                .clip(CircleShape)
                                .background(CSRedBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = CSRed,
                                modifier = Modifier.size(32.dp))
                        }
                        Text("Purchase Failed", fontSize = 20.sp,
                            fontWeight = FontWeight.Bold, color = CSBlack)
                        Text(msg, fontSize = 13.sp, color = CSGray,
                            textAlign = TextAlign.Center, lineHeight = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick  = { VeilBillingClient.resetCookiePurchaseState() },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape    = RoundedCornerShape(14.dp),
                                border   = BorderStroke(1.dp, CSBorder)
                            ) { Text("Dismiss", color = CSBlack) }
                            Button(
                                onClick  = { VeilBillingClient.resetCookiePurchaseState() },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = CSBlack)
                            ) { Text("Try Again", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: Int) {
    val inf = rememberInfiniteTransition(label = "bal")
    val borderAlpha by inf.animateFloat(0.25f, 0.9f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "ba")
    val numScale by animateFloatAsState(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "ns")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFAFAFA), Color(0xFFF3F3F3)))
            )
            .border(1.5.dp,
                Brush.sweepGradient(
                    listOf(
                        CSGold.copy(borderAlpha * 0.3f),
                        CSGold.copy(borderAlpha),
                        Color(0xFFD97706).copy(borderAlpha * 0.5f),
                        CSGold.copy(borderAlpha * 0.3f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val cookieScale by inf.animateFloat(0.9f, 1.1f,
                infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), "cs")
            Text("🍪", fontSize = 36.sp, modifier = Modifier.scale(cookieScale))
            Spacer(Modifier.height(4.dp))
            Text(
                "$balance",
                fontSize      = 48.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = CSBlack,
                letterSpacing = (-2).sp,
                modifier      = Modifier.scale(numScale)
            )
            Text(
                "cookies available",
                fontSize = 14.sp,
                color    = CSGray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}


@Composable
private fun PackCard(
    pack     : CookiePack,
    enabled  : Boolean,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "pc")
    val shimX by inf.animateFloat(-180f, 360f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), "px")
    val shimBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(if (pack.isPopular) 0.60f else 0.45f),
            Color.Transparent
        ),
        start = Offset(shimX - 60f, 0f),
        end   = Offset(shimX + 60f, 200f)
    )
    val floatY by inf.animateFloat(-3f, 3f,
        infiniteRepeatable(
            tween((1800 + pack.cookies % 400).toInt(), easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), "py"
    )
    val badgePulse by inf.animateFloat(0.95f, 1.05f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse), "bp")

    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "prs"
    )
    LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(140); pressed = false } }

    // Border brush for popular cards
    val borderBrush = if (pack.isPopular)
        Brush.linearGradient(listOf(CSGold, Color(0xFFFBBF24), CSGold))
    else
        Brush.linearGradient(listOf(CSBorder, CSBorder))
    val borderWidth = if (pack.isPopular) 2.dp else 1.2.dp

    Box(
        modifier
            .offset(y = floatY.dp)
            .scale(pressScale)
    ) {
        Card(
            onClick    = { if (enabled) { pressed = true; onClick() } },
            enabled    = enabled,
            shape      = RoundedCornerShape(18.dp),
            colors     = CardDefaults.cardColors(containerColor = CSBg),
            elevation  = CardDefaults.cardElevation(
                defaultElevation = if (pack.isPopular) 4.dp else 1.dp
            ),
            modifier   = Modifier
                .fillMaxWidth()
                .border(borderWidth, borderBrush, RoundedCornerShape(18.dp))
        ) {
            Box {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cookieSize = when {
                        pack.cookies >= 500 -> 32.sp
                        pack.cookies >= 230 -> 28.sp
                        else                -> 26.sp
                    }
                    val emojis = when {
                        pack.cookies >= 500 -> "🍪🍪🍪🍪"
                        pack.cookies >= 230 -> "🍪🍪🍪"
                        pack.cookies >= 130 -> "🍪🍪"
                        else                -> "🍪"
                    }
                    Text(emojis, fontSize = cookieSize)
                    Text(
                        pack.label,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = CSGray
                    )
                    Text(
                        "${pack.cookies}",
                        fontSize      = 24.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = CSBlack,
                        letterSpacing = (-0.5).sp
                    )
                    Text("cookies", fontSize = 11.sp, color = CSGray)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (pack.isPopular)
                                Brush.linearGradient(listOf(CSGold, Color(0xFFF59E0B)))
                            else
                                Brush.linearGradient(listOf(CSAccent, CSAccent))
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            pack.fallbackPrice,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }
                // Shimmer streak overlay
                Box(Modifier.matchParentSize().background(shimBrush))
            }
        }

        if (pack.isPopular || pack.isBestValue) {
            val badgeLabel = if (pack.isPopular) "POPULAR" else "BEST VALUE"
            val badgeBg    = if (pack.isPopular) CSGold    else CSBlack
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .scale(if (pack.isPopular) badgePulse else 1f)
                    .shadow(4.dp, RoundedCornerShape(50.dp), spotColor = badgeBg.copy(0.3f))
                    .clip(RoundedCornerShape(50.dp))
                    .background(badgeBg)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    badgeLabel,
                    fontSize      = 8.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = Color.White,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}


@Composable
private fun UsageSection() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CSLightBg)
            .border(1.dp, CSBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "What can you do with 🍪?",
            fontSize   = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color      = CSBlack
        )
        UsageItem("Send a compliment", "10 🍪", "First 3 each month are free")
        UsageItem("More coming soon", "—", "Boosting, DMs and more")
    }
}

@Composable
private fun UsageItem(action: String, cost: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(action, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CSBlack)
            Text(subtitle, fontSize = 11.sp, color = CSGray)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(CSBorder)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(cost, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CSBlack)
        }
    }
}

@Composable
private fun PackSkeletons() {
    val inf   = rememberInfiniteTransition(label = "sku")
    val shimX by inf.animateFloat(-300f, 700f,
        infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), "skx")
    val shimBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFEEEEEE), Color(0xFFF6F6F6), Color(0xFFEEEEEE)),
        start  = Offset(shimX - 150f, 0f),
        end    = Offset(shimX + 150f, 0f)
    )
    repeat(2) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Box(
                    Modifier.weight(1f).height(168.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(shimBrush)
                )
            }
        }
    }
}

// ── Billing error state ───────────────────────────────────────────────────────
@Composable
private fun BillingErrorCard() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CSRedBg)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.WifiOff, null, tint = CSRed, modifier = Modifier.size(28.dp))
            Text("Could not load store", fontSize = 14.sp,
                fontWeight = FontWeight.Medium, color = CSBlack)
            Text("Check your connection and try again",
                fontSize = 12.sp, color = CSGray)
        }
    }
}

// ── Fallback packs (shown before Play loads product details) ──────────────────
private fun defaultPacks() = listOf(
    CookiePack(VeilProductId.COOKIES_SNACK,  "Snack",  50,  "₹29"),
    CookiePack(VeilProductId.COOKIES_TIN,    "Tin",    130, "₹59"),
    CookiePack(VeilProductId.COOKIES_JAR,    "Jar",    230, "₹99",  isPopular = true),
    CookiePack(VeilProductId.COOKIES_BAKERY, "Bakery", 500, "₹199", isBestValue = true),
)

@Composable
fun CookieBadge(
    balance         : Int,
    onClick         : () -> Unit,
    modifier        : Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "cb")
    val shimX by inf.animateFloat(-120f, 220f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), "cbx")
    val shimBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.White.copy(0.70f), Color.Transparent),
        start  = Offset(shimX - 30f, 0f),
        end    = Offset(shimX + 30f, 40f)
    )
    val sc by inf.animateFloat(1f, 1.04f,
        infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "cbs")

    Box(
        modifier
            .scale(sc)
            .shadow(4.dp, RoundedCornerShape(50.dp), spotColor = Color(0xFFD97706).copy(0.20f))
            .clip(RoundedCornerShape(50.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFFF7ED), Color(0xFFFEF3C7), Color(0xFFFFF7ED))
                )
            )
            .border(1.dp, Color(0xFFD97706).copy(0.30f), RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("🍪", fontSize = 13.sp)
            Text(
                "$balance",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF92400E)
            )
        }
        Box(Modifier.matchParentSize().background(shimBrush))
    }
}
