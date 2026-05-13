package com.ns.findyourintroverfriend.themes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ns.findyourintroverfriend.R

// ─────────────────────────────────────────────────────────────────────────────
//  Profile Themes — illustrated backgrounds · dynamic warm bg per theme
// ─────────────────────────────────────────────────────────────────────────────

// Each theme carries its own bg palette for the screen tint
internal data class ThemePalette(
    val bgTop    : Color,   // screen top gradient colour
    val bgMid    : Color,   // screen mid
    val bgBot    : Color,   // screen bottom (usually darkest)
    val orb1     : Color,   // ambient orb 1 colour
    val orb2     : Color,   // ambient orb 2 colour
    val accent   : Color,   // labels, dots, border highlight
    val cardTxt  : Color,   // text on dark bottom of card
    val btnBorder: Color    // buy button border
)

internal val PALETTE_MEADOW = ThemePalette(
    bgTop     = Color(0xFF1A2A12),   // deep forest green
    bgMid     = Color(0xFF141F0E),   // dark moss
    bgBot     = Color(0xFF0F1609),   // near-black green
    orb1      = Color(0xFF6FAB4A),   // soft lime
    orb2      = Color(0xFF3D7A2A),   // leafy green
    accent    = Color(0xFF9FCC72),   // bright soft green
    cardTxt   = Color(0xFFEBF5DC),
    btnBorder = Color(0xFF7BBF55)
)

internal val PALETTE_SAKURA = ThemePalette(
    bgTop     = Color(0xFF2A1520),   // deep dusty rose
    bgMid     = Color(0xFF1F0F18),   // dark plum
    bgBot     = Color(0xFF160B12),   // near-black rose
    orb1      = Color(0xFFD4809A),   // warm pink
    orb2      = Color(0xFF9A5070),   // deep rose
    accent    = Color(0xFFE8A0BC),   // soft sakura
    cardTxt   = Color(0xFFF5E0EA),
    btnBorder = Color(0xFFCE7A9A)
)

internal val PALETTE_CLOUDS = ThemePalette(
    bgTop     = Color(0xFF141E2A),   // deep sky blue
    bgMid     = Color(0xFF0F1720),   // dark slate
    bgBot     = Color(0xFF0A1018),   // near-black blue
    orb1      = Color(0xFF6AA0C8),   // soft sky blue
    orb2      = Color(0xFF3A6E9A),   // deeper blue
    accent    = Color(0xFF90C0E0),   // cloud blue
    cardTxt   = Color(0xFFDCEDF8),
    btnBorder = Color(0xFF6899CC)
)

internal val PALETTE_DUCK = ThemePalette(
    bgTop     = Color(0xFF0F2218),   // deep teal green
    bgMid     = Color(0xFF0A1A12),   // dark teal
    bgBot     = Color(0xFF07120C),   // near-black teal
    orb1      = Color(0xFF5AB09A),   // soft teal
    orb2      = Color(0xFF2A8060),   // deeper teal
    accent    = Color(0xFF7ECFB5),   // cheerful mint
    cardTxt   = Color(0xFFDAF0E8),
    btnBorder = Color(0xFF55B090)
)

internal data class ProfileThemeItem(
    val id         : String,
    val productId  : String,
    val priceInr   : Int,
    val drawableRes: Int,
    val name       : String,
    val mood       : String,
    val palette    : ThemePalette
)

internal val PROFILE_THEMES = listOf(
    ProfileThemeItem(
        id          = "meadow",
        productId   = "profile_bg_meadow",
        priceInr    = 59,
        drawableRes = R.drawable.profile_bg_meadow,
        name        = "Meadow",
        mood        = "calm  ·  gentle",
        palette     = PALETTE_MEADOW
    ),
    ProfileThemeItem(
        id          = "sakura",
        productId   = "profile_bg_sakura",
        priceInr    = 59,
        drawableRes = R.drawable.profile_bg_sakura,
        name        = "Sakura",
        mood        = "soft  ·  dreamy",
        palette     = PALETTE_SAKURA
    ),
    ProfileThemeItem(
        id          = "clouds",
        productId   = "profile_bg_clouds",
        priceInr    = 59,
        drawableRes = R.drawable.profile_bg_clouds,
        name        = "Cloud Stroll",
        mood        = "free  ·  wandering",
        palette     = PALETTE_CLOUDS
    ),
    ProfileThemeItem(
        id          = "duck",
        productId   = "profile_bg_duck",
        priceInr    = 79,
        drawableRes = R.drawable.profile_bg_duck,
        name        = "Flower Hill",
        mood        = "cheerful  ·  fresh",
        palette     = PALETTE_DUCK
    )
)

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileThemesScreen(
    onBack    : () -> Unit,
    onPurchase: (String) -> Unit = {}
) {
    var selectedId by remember { mutableStateOf(PROFILE_THEMES.first().id) }
    val selected   = PROFILE_THEMES.first { it.id == selectedId }
    val pal        = selected.palette

    // ── Animated bg colours ───────────────────────────────────────────────────
    val animSpec  = tween<Color>(durationMillis = 700, easing = FastOutSlowInEasing)
    val bgTop  by animateColorAsState(pal.bgTop,  animSpec, label = "bgTop")
    val bgMid  by animateColorAsState(pal.bgMid,  animSpec, label = "bgMid")
    val bgBot  by animateColorAsState(pal.bgBot,  animSpec, label = "bgBot")
    val orb1   by animateColorAsState(pal.orb1,   animSpec, label = "orb1")
    val orb2   by animateColorAsState(pal.orb2,   animSpec, label = "orb2")
    val accent by animateColorAsState(pal.accent, animSpec, label = "acc")

    // ── Ambient breathing animation ───────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "amb")
    val breathe by inf.animateFloat(
        0.30f, 0.65f,
        infiniteRepeatable(tween(3_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "br"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(bgTop, bgMid, bgBot))
            )
    ) {
        // ── Ambient orb 1 — top-left ──────────────────────────────────────────
        Box(
            Modifier
                .size(380.dp)
                .offset((-90).dp, (-110).dp)
                .background(
                    Brush.radialGradient(
                        listOf(orb1.copy(breathe * 0.55f), Color.Transparent)
                    )
                )
        )
        // ── Ambient orb 2 — bottom-right ─────────────────────────────────────
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(80.dp, 80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(orb2.copy(breathe * 0.40f), Color.Transparent)
                    )
                )
        )
        // ── Small accent orb — centre ─────────────────────────────────────────
        Box(
            Modifier
                .size(180.dp)
                .align(Alignment.Center)
                .offset(60.dp, (-80).dp)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(breathe * 0.18f), Color.Transparent)
                    )
                )
        )

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.08f))
                        .border(0.6.dp, Color.White.copy(0.13f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", fontSize = 16.sp, color = Color.White.copy(0.70f))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "profile themes",
                        fontSize      = 15.sp,
                        fontWeight    = FontWeight.SemiBold,
                        color         = Color.White.copy(0.92f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "swipe to explore  ·  own it forever",
                        fontSize = 10.sp,
                        color    = accent.copy(0.80f),
                        letterSpacing = 0.3.sp
                    )
                }

                Spacer(Modifier.size(38.dp))
            }

            HorizontalDivider(
                color     = Color.White.copy(0.08f),
                thickness = 0.6.dp,
                modifier  = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(22.dp))

            // ── Cards carousel ────────────────────────────────────────────────
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                items(PROFILE_THEMES.size) { idx ->
                    val theme  = PROFILE_THEMES[idx]
                    val active = theme.id == selectedId
                    val scale by animateFloatAsState(
                        targetValue   = if (active) 1f else 0.93f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label         = "sc$idx"
                    )
                    ProfileCard(
                        theme    = theme,
                        isActive = active,
                        accent   = accent,
                        modifier = Modifier.scale(scale),
                        onClick  = { selectedId = theme.id }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Dot indicators ────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                PROFILE_THEMES.forEachIndexed { idx, theme ->
                    val active = theme.id == selectedId
                    val w by animateDpAsState(
                        if (active) 22.dp else 6.dp,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dw$idx"
                    )
                    val dotColor by animateColorAsState(
                        if (active) accent else Color.White.copy(0.20f),
                        tween(400),
                        label = "dc$idx"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clickable { selectedId = theme.id }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Mood label ────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    selected.mood,
                    fontSize      = 13.sp,
                    color         = accent.copy(0.90f),
                    letterSpacing = 1.5.sp,
                    fontWeight    = FontWeight.Light
                )
                Text(
                    "visible to your circle during all 3 days",
                    fontSize  = 11.sp,
                    color     = Color.White.copy(0.40f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            HorizontalDivider(
                color     = Color.White.copy(0.07f),
                thickness = 0.6.dp,
                modifier  = Modifier.padding(horizontal = 28.dp)
            )

            Spacer(Modifier.height(22.dp))

            // ── Benefits ──────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 30.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                BenefitLine("○", "everyone in your circle sees this background", accent)
                BenefitLine("◇", "shown from day 1 — before your name is revealed", accent)
                BenefitLine("△", "one-time unlock  ·  yours across every future circle", accent)
            }

            Spacer(Modifier.height(28.dp))

            // ── CTA ───────────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BuyButton(
                    theme   = selected,
                    accent  = accent,
                    onClick = { onPurchase(selected.productId) }
                )
                Text(
                    "one-time  ·  no subscription  ·  permanent",
                    fontSize      = 10.sp,
                    color         = Color.White.copy(0.30f),
                    textAlign     = TextAlign.Center,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Profile Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(
    theme    : ProfileThemeItem,
    isActive : Boolean,
    accent   : Color,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    val inf    = rememberInfiniteTransition(label = "card_${theme.id}")
    val glow by inf.animateFloat(
        0.45f, 0.95f,
        infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "g"
    )

    val borderColor by animateColorAsState(
        if (isActive) accent.copy(glow * 0.75f) else Color.White.copy(0.10f),
        tween(500),
        label = "bc"
    )
    val borderWidth by animateDpAsState(
        if (isActive) 2.dp else 0.8.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bw"
    )

    Box(
        modifier = modifier
            .width(220.dp)
            .height(300.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(24.dp))
            .clickable { onClick() }
    ) {
        // Illustrated background
        Image(
            painter            = painterResource(theme.drawableRes),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        // Bottom gradient for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.68f to Color(0xFF000000).copy(0.45f),
                        1.00f to Color(0xFF000000).copy(0.82f)
                    )
                )
        )

        // Avatar + name at bottom
        Column(
            modifier            = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Circular avatar ring
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.15f))
                    .border(
                        2.dp,
                        Brush.sweepGradient(
                            listOf(
                                Color.White.copy(0.80f),
                                theme.palette.accent.copy(0.65f),
                                Color.White.copy(0.80f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Person silhouette in white
                Text("◉", fontSize = 22.sp, color = Color.White.copy(0.75f))
            }

            // Name placeholder
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(0.30f))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    "your name",
                    fontSize      = 11.sp,
                    color         = Color.White.copy(0.80f),
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // Price badge — top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(0.55f))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                "₹${theme.priceInr}",
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Bold,
                color         = theme.palette.accent,
                letterSpacing = 0.2.sp
            )
        }

        // Active dot — top left
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }

        // Top shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            theme.palette.accent.copy(if (isActive) glow * 0.9f else 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Buy Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BuyButton(
    theme  : ProfileThemeItem,
    accent : Color,
    onClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "btn")
    val glow by inf.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(1_800), RepeatMode.Reverse),
        label = "bg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(0.22f),
                        accent.copy(0.35f),
                        accent.copy(0.22f)
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(glow * 0.55f),
                        accent.copy(glow * 0.90f),
                        accent.copy(glow * 0.55f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("◈", fontSize = 14.sp, color = accent)
            Text(
                "unlock ${theme.name.lowercase()}  ·  ₹${theme.priceInr}",
                fontSize      = 14.sp,
                fontWeight    = FontWeight.SemiBold,
                color         = Color.White.copy(0.92f),
                letterSpacing = 0.2.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Benefit Line
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BenefitLine(icon: String, title: String, accent: Color) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 11.sp, color = accent.copy(0.75f))
        Text(
            title,
            fontSize   = 12.sp,
            color      = Color.White.copy(0.55f),
            lineHeight = 18.sp
        )
    }
}
