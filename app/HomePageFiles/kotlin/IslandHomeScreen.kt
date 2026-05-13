Makpackage com.ns.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────

private data class CloudConfig(
    val imageRes: Int,
    val startOffsetFraction: Float, // initial X position as fraction of screen width
    val yFraction: Float,           // vertical position as fraction of screen height
    val speedDp: Float,             // drift speed in dp/s
    val alpha: Float,               // max opacity — keep low for incidental feel
    val scale: Float,               // size relative to screen width
    val delayMs: Long               // stagger so clouds never start together
)

private data class AvatarSlot(
    val xFraction: Float,
    val yFraction: Float
)

// ─────────────────────────────────────────────────────────────────────────────
// Palette constants — muted, airy, premium
// ─────────────────────────────────────────────────────────────────────────────

private object Palette {
    // Sky desaturation veil — reduces sky vibrancy so island pops
    val skyVeil        = Color(0xFF1A2535).copy(alpha = 0.28f)

    // Glow — warm amber-ivory, very low intensity
    val glowCore       = Color(0xFFFFF4DC).copy(alpha = 0.22f)
    val glowMid        = Color(0xFFD4EAFF).copy(alpha = 0.10f)

    // Avatar ring — near-invisible, slate-blue
    val avatarRingHigh = Color(0xFFADC8E8).copy(alpha = 0.50f)
    val avatarRingLow  = Color(0xFFADC8E8).copy(alpha = 0.15f)
    val avatarFill     = Color(0x18FFFFFF)
}

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun IslandHomeScreen() {

    var isDaytime by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E18)) // deep off-black so edge bleed looks intentional
    ) {
        val screenWidth  = maxWidth
        val screenHeight = maxHeight

        // ── 1 · Sky ───────────────────────────────────────────────────────────
        SkyLayer(isDaytime = isDaytime)

        // ── 2 · Sky desaturation veil (sits above sky, below everything else) ─
        // Reduces the sky's raw saturation without touching the source image.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.skyVeil)
        )

        // ── 3 · Island (floating) ─────────────────────────────────────────────
        IslandLayer(screenWidth = screenWidth, screenHeight = screenHeight)

        // ── 4 · Clouds ────────────────────────────────────────────────────────
        val clouds = remember {
            listOf(
                // Near cloud — large, very slow, barely visible
                CloudConfig(
                    imageRes            = R.drawable.cloud_1,
                    startOffsetFraction = -0.5f,
                    yFraction           = 0.07f,
                    speedDp             = 9f,   // very slow
                    alpha               = 0.38f, // low opacity — incidental not decorative
                    scale               = 0.80f,
                    delayMs             = 0L
                ),
                // Far cloud — small, slowest, barely perceptible
                CloudConfig(
                    imageRes            = R.drawable.cloud_1,
                    startOffsetFraction = 0.6f,
                    yFraction           = 0.18f,
                    speedDp             = 6f,   // extremely slow
                    alpha               = 0.22f,
                    scale               = 0.42f,
                    delayMs             = 11_000L
                )
            )
        }

        clouds.forEach { cfg ->
            CloudLayer(config = cfg, screenWidth = screenWidth, screenHeight = screenHeight)
        }

        // ── 5 · Glow ──────────────────────────────────────────────────────────
        GlowLayer(screenWidth = screenWidth, screenHeight = screenHeight)


        // ── 7 · Bird ──────────────────────────────────────────────────────────
        BirdLayer(screenWidth = screenWidth, screenHeight = screenHeight)

        // ── 8 · Avatar placeholders — N / S / W / E ───────────────────────────
        val avatarSlots = remember {
            listOf(
                AvatarSlot(xFraction = 0.50f, yFraction = 0.34f), // North
                AvatarSlot(xFraction = 0.50f, yFraction = 0.74f), // South
                AvatarSlot(xFraction = 0.10f, yFraction = 0.54f), // West
                AvatarSlot(xFraction = 0.90f, yFraction = 0.54f)  // East
            )
        }

        avatarSlots.forEachIndexed { index, slot ->
            AvatarPlaceholder(
                slot         = slot,
                screenWidth  = screenWidth,
                screenHeight = screenHeight,
                phaseOffset  = index * 1_300L  // stagger each avatar's entire cycle
            )
        }

        // ── 9 · Day / Night debug toggle ──────────────────────────────────────
        DayNightToggle(
            isDaytime = isDaytime,
            onToggle  = { isDaytime = !isDaytime },
            modifier  = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sky — night base + day cross-fade
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SkyLayer(isDaytime: Boolean) {
    val dayAlpha by animateFloatAsState(
        targetValue   = if (isDaytime) 1f else 0f,
        animationSpec = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
        label         = "skyDayAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter            = painterResource(R.drawable.sky_night),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Image(
            painter            = painterResource(R.drawable.sky_day),
            contentDescription = null,
            modifier           = Modifier
                .fillMaxSize()
                .alpha(dayAlpha),
            contentScale       = ContentScale.Crop
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Island — calm composed float (6.5s cycle, clearly visible but never bouncy)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IslandLayer(screenWidth: Dp, screenHeight: Dp) {

    val floatTransition = rememberInfiniteTransition(label = "islandFloat")
    val floatDp by floatTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = -20f,   // 20 dp — clearly visible, still majestic
        animationSpec = infiniteRepeatable(
            animation  = tween(
                durationMillis = 6_500,
                easing         = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f) // sine-like
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "islandFloatY"
    )

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Image(
            painter            = painterResource(R.drawable.island_base),
            contentDescription = null,
            modifier           = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .offset(y = floatDp.dp),
            contentScale       = ContentScale.FillWidth
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cloud — each instance drifts independently, loops invisibly off-screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CloudLayer(
    config: CloudConfig,
    screenWidth: Dp,
    screenHeight: Dp
) {
    val density       = LocalDensity.current
    val screenWPx     = with(density) { screenWidth.toPx() }
    val cloudWPx      = screenWPx * config.scale * 0.7f
    val travelPx      = screenWPx + cloudWPx
    val speedPxPerMs  = with(density) { config.speedDp.dp.toPx() } / 1_000f
    val durationMs    = (travelPx / speedPxPerMs).toInt()

    val offsetX = remember { Animatable(config.startOffsetFraction * screenWPx) }

    LaunchedEffect(Unit) {
        delay(config.delayMs)
        while (true) {
            offsetX.snapTo(-cloudWPx)
            offsetX.animateTo(
                targetValue   = screenWPx,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter            = painterResource(config.imageRes),
            contentDescription = null,
            modifier           = Modifier
                .offset(
                    x = with(density) { offsetX.value.toDp() },
                    y = screenHeight * config.yFraction
                )
                .width(screenWidth * config.scale * 0.7f)
                .wrapContentHeight()
                // Fade in/out at screen edges for seamless loop feel
                .alpha(config.alpha),
            contentScale = ContentScale.FillWidth
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Glow — very soft warm pulse, long cycle, barely perceptible
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlowLayer(screenWidth: Dp, screenHeight: Dp) {

    val transition = rememberInfiniteTransition(label = "glowPulse")

    // Alpha: 7-second breathe — very slow, low range
    val glowAlpha by transition.animateFloat(
        initialValue  = 0.10f,
        targetValue   = 0.28f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 7_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Size: 9-second breathe — desynced from alpha so it never locks into a rhythm
    val glowScale by transition.animateFloat(
        initialValue  = 0.88f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 9_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    val density  = LocalDensity.current
    val radiusPx = with(density) { (screenWidth * 0.50f * glowScale).toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width  * 0.50f
        val cy = size.height * 0.65f // just above the island crown

        // Outer warm glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Palette.glowCore.copy(alpha = glowAlpha),
                    Palette.glowMid.copy(alpha  = glowAlpha * 0.55f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = radiusPx
            ),
            radius = radiusPx,
            center = Offset(cx, cy)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bird — rare, small, desaturated, single crossing
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BirdLayer(screenWidth: Dp, screenHeight: Dp) {

    val density       = LocalDensity.current
    val screenWPx     = with(density) { screenWidth.toPx() }
    // Smaller bird width reduces visual weight
    val birdWPx       = with(density) { 44.dp.toPx() }

    val offsetX = remember { Animatable(-birdWPx) }
    val visible = remember { mutableStateOf(false) }
    val yOffset = remember { mutableStateOf(screenHeight * 0.25f) }
    // Alpha animates in and out during flight so bird fades rather than pops
    val birdAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            // Rare appearance: 30 – 75 seconds idle between crossings
            delay((30_000L..75_000L).random())

            yOffset.value = screenHeight * ((0.14f..0.35f).random())
            offsetX.snapTo(-birdWPx)
            visible.value = true

            // Fade in
            birdAlpha.animateTo(0.70f, tween(1_800))

            // Slow, dreamy crossing: 55 – 85 seconds
            offsetX.animateTo(
                targetValue   = screenWPx + birdWPx,
                animationSpec = tween(
                    durationMillis = (55_000..85_000).random(),
                    easing         = LinearEasing
                )
            )

            // Fade out
            birdAlpha.animateTo(0f, tween(1_200))
            visible.value = false
        }
    }

    if (visible.value) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter            = painterResource(R.drawable.bird),
                contentDescription = null,
                modifier           = Modifier
                    .offset(
                        x = with(density) { offsetX.value.toDp() },
                        y = yOffset.value
                    )
                    .width(44.dp)   // smaller than before
                    .wrapContentHeight()
                    .alpha(birdAlpha.value),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Avatar placeholder — minimalist, near-invisible ring, very slow breathing
// The bubble/decorative feel is removed; this is purely a positional hint.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AvatarPlaceholder(
    slot: AvatarSlot,
    screenWidth: Dp,
    screenHeight: Dp,
    phaseOffset: Long  // shifts entire animation cycle so no two are in sync
) {
    val transition = rememberInfiniteTransition(label = "avatar_${slot.xFraction}")

    // Scale: very narrow range — 0.97 → 1.03, almost imperceptible
    val scale by transition.animateFloat(
        initialValue  = 0.97f,
        targetValue   = 1.03f,
        animationSpec = infiniteRepeatable(
            animation  = tween(
                durationMillis = (5_400L + phaseOffset).toInt(),
                easing         = FastOutSlowInEasing,
                delayMillis    = phaseOffset.toInt()
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarScale_${slot.xFraction}"
    )

    // Ring alpha: very dim range — 0.12 → 0.42
    val ringAlpha by transition.animateFloat(
        initialValue  = 0.12f,
        targetValue   = 0.42f,
        animationSpec = infiniteRepeatable(
            animation  = tween(
                durationMillis = (6_800L + phaseOffset).toInt(),
                easing         = FastOutSlowInEasing,
                delayMillis    = (phaseOffset + 600L).toInt()
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarRing_${slot.xFraction}"
    )

    val xPos = screenWidth  * slot.xFraction - 26.dp  // centred: half of 52dp
    val yPos = screenHeight * slot.yFraction - 26.dp

    Box(
        modifier = Modifier
            .offset(x = xPos, y = yPos)
            .size(52.dp)
            .scale(scale)
            .clip(CircleShape)
            // Very faint frosted fill — barely there
            .background(Palette.avatarFill)
            // Thin, dim ring — present but not calling attention
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Palette.avatarRingHigh.copy(alpha = ringAlpha),
                        Palette.avatarRingLow.copy(alpha  = ringAlpha * 0.4f)
                    )
                ),
                shape = CircleShape
            )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Day / Night toggle — small, unobtrusive, debug only
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayNightToggle(
    isDaytime: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF1C2B3A).copy(alpha = 0.70f))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = if (isDaytime) "🌙" else "☀️",
            fontSize = TextUnit(15f, TextUnitType.Sp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun ClosedRange<Float>.random(): Float =
    start + Math.random().toFloat() * (endInclusive - start)

private fun ClosedRange<Long>.random(): Long =
    start + (Math.random() * (endInclusive - start)).toLong()

private fun ClosedRange<Int>.random(): Int =
    start + (Math.random() * (endInclusive - start)).toInt()
