package com.ns.findyourintroverfriend.party

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  Star data — pre-computed once, cheap
// ─────────────────────────────────────────────────────────────────────────────

private data class Star(
    val x     : Float,  // 0..1
    val y     : Float,  // 0..1
    val size  : Float,
    val alpha : Float,
    val twinkleOffset: Float  // phase offset for twinkle animation
)

private fun generateStars(count: Int, seed: Long = 42L): List<Star> {
    val rng = Random(seed)
    return List(count) {
        Star(
            x              = rng.nextFloat(),
            y              = rng.nextFloat() * 0.75f,  // top 75% of screen
            size           = 0.8f + rng.nextFloat() * 2.2f,
            alpha          = 0.25f + rng.nextFloat() * 0.55f,
            twinkleOffset  = rng.nextFloat()
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  A. NIGHT SKY — shooting stars, gentle twinkle, deep indigo
// ═════════════════════════════════════════════════════════════════════════════

private val nightBg = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFF02010A),
        0.25f to Color(0xFF070421),
        0.55f to Color(0xFF0D0830),
        0.80f to Color(0xFF130B28),
        1.00f to Color(0xFF0A0618)
    )
)

@Composable
fun NightSkyBackground(modifier: Modifier = Modifier.fillMaxSize()) {
    val stars = remember { generateStars(110) }

    // Global twinkle cycle
    val inf    = rememberInfiniteTransition(label = "night")
    val cycle  by inf.animateFloat(
        initialValue  = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
        label         = "cycle"
    )

    // Aurora strips — two slow diagonal bands
    val aurora by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing)),
        label         = "aurora"
    )

    // Shooting star state
    var showShoot by remember { mutableStateOf(false) }
    var shootProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((8_000L..14_000L).random())
            showShoot = true
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 900) {
                shootProgress = (System.currentTimeMillis() - start) / 900f
                delay(16)
            }
            showShoot = false
            shootProgress = 0f
        }
    }

    Canvas(modifier = modifier) {
        // Background gradient
        drawRect(nightBg)

        // Subtle aurora bands
        val auroraX = size.width * (0.3f + 0.4f * sin(aurora * 2 * PI).toFloat())
        drawRect(
            brush  = Brush.radialGradient(
                colors  = listOf(Color(0x0C9B72CF), Color.Transparent),
                center  = Offset(auroraX, size.height * 0.30f),
                radius  = size.width * 0.7f
            )
        )
        drawRect(
            brush  = Brush.radialGradient(
                colors  = listOf(Color(0x08FF9BB5), Color.Transparent),
                center  = Offset(size.width - auroraX * 0.6f, size.height * 0.15f),
                radius  = size.width * 0.55f
            )
        )

        // Stars with twinkle
        stars.forEach { star ->
            val twinkle = (sin(cycle + star.twinkleOffset * 2 * PI).toFloat() + 1f) / 2f
            val alpha   = star.alpha * (0.55f + 0.45f * twinkle)
            drawCircle(
                color  = Color.White.copy(alpha = alpha),
                radius = star.size * density,
                center = Offset(star.x * size.width, star.y * size.height)
            )
            // Soft glow for brighter stars
            if (star.size > 2f) {
                drawCircle(
                    color  = Color.White.copy(alpha = alpha * 0.18f),
                    radius = star.size * density * 4f,
                    center = Offset(star.x * size.width, star.y * size.height)
                )
            }
        }

        // Shooting star
        if (showShoot) {
            drawShootingStar(shootProgress, size.width, size.height)
        }
    }
}

private fun DrawScope.drawShootingStar(progress: Float, w: Float, h: Float) {
    val ease    = 1f - (1f - progress).pow(3)  // ease-out cubic
    val startX  = w * 0.22f
    val startY  = h * 0.08f
    val endX    = w * 0.65f
    val endY    = h * 0.28f
    val cx      = startX + (endX - startX) * ease
    val cy      = startY + (endY - startY) * ease
    val tailLen = w * 0.12f * (1f - progress).coerceAtLeast(0f)
    val angle   = atan2(endY - startY, endX - startX)
    val tailX   = cx - cos(angle) * tailLen
    val tailY   = cy - sin(angle) * tailLen
    drawLine(
        brush       = Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(0.75f)),
            start   = Offset(tailX, tailY),
            end     = Offset(cx, cy)
        ),
        start       = Offset(tailX, tailY),
        end         = Offset(cx, cy),
        strokeWidth = 1.2f * density,
        cap         = StrokeCap.Round
    )
    // Head glow
    drawCircle(
        color  = Color.White.copy(0.90f),
        radius = 1.5f * density,
        center = Offset(cx, cy)
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  B. NOON / SOFT DAYLIGHT — warmth, clarity, optimism
// ═════════════════════════════════════════════════════════════════════════════

private val noonBg = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFF8BBBD8),
        0.30f to Color(0xFFADD4E9),
        0.60f to Color(0xFFCAE8F5),
        0.85f to Color(0xFFDFF4FC),
        1.00f to Color(0xFFEEF8FD)
    )
)

@Composable
fun NoonSkyBackground(modifier: Modifier = Modifier.fillMaxSize()) {
    val inf   = rememberInfiniteTransition(label = "noon")
    val drift by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
        label         = "drift"
    )
    val warmth by inf.animateFloat(
        initialValue  = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "warmth"
    )

    Canvas(modifier = modifier) {
        drawRect(noonBg)

        // Gentle warm diffusion — upper center
        val warmCx = size.width * 0.5f
        val warmCy = size.height * 0.15f
        drawRect(
            brush  = Brush.radialGradient(
                colors  = listOf(Color(0xFFFFECC8).copy(warmth * 0.22f), Color.Transparent),
                center  = Offset(warmCx, warmCy),
                radius  = size.width * 0.8f
            )
        )

        // Extremely faint horizon warmth
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFFE8C0).copy(0.18f)
                )
            )
        )

        // Very faint, large-radius particles (haze)
        val hazeSeeds = listOf(0.15f, 0.45f, 0.72f, 0.32f, 0.62f)
        hazeSeeds.forEachIndexed { i, baseX ->
            val px = baseX + sin((drift + i * 0.3f) * 2 * PI).toFloat() * 0.06f
            val py = 0.20f + i * 0.08f
            drawCircle(
                color  = Color.White.copy(0.055f + i * 0.01f),
                radius = size.width * (0.25f + i * 0.04f),
                center = Offset(px * size.width, py * size.height)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  C. GOLDEN HOUR — peach, amber, warm evening calm
// ═════════════════════════════════════════════════════════════════════════════

private val goldenBg = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFF1A0C1E),
        0.20f to Color(0xFF39182A),
        0.42f to Color(0xFF7A3420),
        0.62f to Color(0xFFBB6030),
        0.80f to Color(0xFFE08840),
        0.92f to Color(0xFFEFA84A),
        1.00f to Color(0xFFF0B85A)
    )
)

@Composable
fun GoldenHourBackground(modifier: Modifier = Modifier.fillMaxSize()) {
    val inf    = rememberInfiniteTransition(label = "golden")
    val breath by inf.animateFloat(
        initialValue  = 0.80f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "breath"
    )
    val shimmer by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label         = "shimmer"
    )

    Canvas(modifier = modifier) {
        drawRect(goldenBg)

        // Warm glow — horizon center bloom
        drawRect(
            brush  = Brush.radialGradient(
                colors  = listOf(
                    Color(0xFFFFCC80).copy(0.28f * breath),
                    Color(0xFFFF8C40).copy(0.10f),
                    Color.Transparent
                ),
                center  = Offset(size.width * 0.5f, size.height * 0.75f),
                radius  = size.width * 1.0f
            )
        )

        // Shimmering upper atmospheric haze
        drawRect(
            brush  = Brush.radialGradient(
                colors  = listOf(
                    Color(0xFFFF6B50).copy(0.12f * breath),
                    Color.Transparent
                ),
                center  = Offset(
                    size.width * (0.4f + sin(shimmer * 2 * PI).toFloat() * 0.12f),
                    size.height * 0.25f
                ),
                radius  = size.width * 0.7f
            )
        )

        // Very faint stars appearing at top (dusk)
        val duskStarSeeds = listOf(
            Triple(0.12f, 0.05f, 0.40f),
            Triple(0.78f, 0.08f, 0.30f),
            Triple(0.55f, 0.03f, 0.25f),
            Triple(0.35f, 0.10f, 0.20f),
            Triple(0.88f, 0.04f, 0.28f)
        )
        duskStarSeeds.forEach { (x, y, a) ->
            drawCircle(
                color  = Color.White.copy(a * breath * 0.7f),
                radius = 1.2f * density,
                center = Offset(x * size.width, y * size.height)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  FLOATING ISLAND LAYER
//  - accepts an optional imageRes (null = placeholder glow until art is added)
//  - gentle vertical float + barely-perceptible horizontal drift
//  - island placed in center-lower positioning
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun FloatingIslandLayer(
    modifier    : Modifier = Modifier.fillMaxSize(),
    imageRes    : Int?     = null,          // plug in R.drawable.island_xxx here
    islandWidth : Dp      = 280.dp,
    bottomBias  : Float   = 0.72f           // 0=top, 1=bottom; 0.65-0.75 feels natural
) {
    val inf       = rememberInfiniteTransition(label = "island")

    // Primary float — very slow, very small travel
    val floatY by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = -14f,               // px offset at 1x density — tiny
        animationSpec = infiniteRepeatable(
            tween(6_200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "floatY"
    )

    // Secondary subtle horizontal drift (1/3 speed of float)
    val driftX by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 4f,
        animationSpec = infiniteRepeatable(
            tween(9_400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "driftX"
    )

    Box(
        modifier          = modifier,
        contentAlignment  = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(bottomBias)               // positions island in lower region
                .wrapContentSize(Alignment.BottomCenter)
                .offset(
                    x = driftX.dp,
                    y = floatY.dp
                )
        ) {
            if (imageRes != null) {
                // ── Real island art goes here ──
                androidx.compose.foundation.Image(
                    painter          = painterResource(imageRes),
                    contentDescription = null,
                    contentScale     = ContentScale.Fit,
                    modifier         = Modifier.width(islandWidth)
                )
            } else {
                // ── Placeholder glow (remove when art is ready) ──
                IslandPlaceholderGlow(width = islandWidth)
            }
        }
    }
}

@Composable
private fun IslandPlaceholderGlow(width: Dp) {
    val inf     = rememberInfiniteTransition(label = "glow")
    val glowAlpha by inf.animateFloat(
        initialValue  = 0.30f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(3_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "glowA"
    )
    Canvas(
        modifier = Modifier
            .width(width)
            .height(width * 0.45f)           // approx island proportion
    ) {
        // Shadow/base ellipse
        drawOval(
            brush  = Brush.radialGradient(
                listOf(Color(0x33FFCC77), Color(0x11FFAA44), Color.Transparent)
            ),
            size   = this.size.copy(height = this.size.height * 1.3f)
        )
        // Center glowing orb — suggests island mass
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(
                    Color(0xFFFFCC77).copy(glowAlpha),
                    Color(0xFFFF9F43).copy(glowAlpha * 0.5f),
                    Color.Transparent
                ),
                radius = this.size.width * 0.28f
            ),
            radius = this.size.width * 0.28f,
            center = Offset(this.size.width / 2f, this.size.height * 0.40f)
        )
    }
}
