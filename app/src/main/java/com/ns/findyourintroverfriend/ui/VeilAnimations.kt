package com.ns.findyourintroverfriend.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

// ─────────────────────────────────────────────────────────────────────────────
//  Reusable Animation Utilities for VEIL App
// ─────────────────────────────────────────────────────────────────────────────

// Removed - use entranceAnimation modifier instead

/**
 * Modifier for entrance animation - slides in from bottom with fade
 */
fun Modifier.entranceAnimation(
    index: Int,
    delayMillis: Int = 50
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * delayMillis).toLong())
        visible = true
    }
    
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "entranceOffsetY"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "entranceAlpha"
    )
    
    this.graphicsLayer {
        translationY = offsetY
        this.alpha = alpha
    }
}

/**
 * Press animation - scales down slightly on press
 */
fun Modifier.pressAnimation(pressed: Boolean = false): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    
    this.scale(scale)
}

/**
 * Bounce animation - bounces in with overshoot
 */
fun Modifier.bounceIn(visible: Boolean): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )
    
    this.scale(scale)
}

/**
 * Pulse animation - continuous subtle pulse
 */
@Composable
fun rememberPulseAnimation(
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    durationMillis: Int = 1200
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    return infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    ).value
}

/**
 * Glow animation - continuous alpha pulse for glowing effects
 */
@Composable
fun rememberGlowAnimation(
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 0.8f,
    durationMillis: Int = 1600
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    return infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    ).value
}

/**
 * Shake animation - for error states or attention
 */
fun Modifier.shake(trigger: Boolean): Modifier = composed {
    var shakeOffset by remember { mutableStateOf(0f) }
    
    LaunchedEffect(trigger) {
        if (trigger) {
            val shakeKeyframes = listOf(0f, -10f, 10f, -10f, 10f, -5f, 5f, 0f)
            shakeKeyframes.forEach { offset ->
                shakeOffset = offset
                kotlinx.coroutines.delay(50)
            }
            shakeOffset = 0f
        }
    }
    
    this.graphicsLayer {
        translationX = shakeOffset
    }
}

/**
 * Success animation - scale up with bounce
 */
fun Modifier.successAnimation(trigger: Boolean): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (trigger) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "successScale"
    )
    
    this.scale(scale)
}

/**
 * Fade in animation with delay
 */
fun Modifier.fadeInWithDelay(
    delayMillis: Long = 0,
    durationMillis: Int = 400
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis)
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "fadeInAlpha"
    )
    
    this.graphicsLayer {
        this.alpha = alpha
    }
}

/**
 * Slide in from side animation
 */
fun Modifier.slideInFromSide(
    fromLeft: Boolean = true,
    delayMillis: Long = 0
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis)
        visible = true
    }
    
    val offsetX by animateFloatAsState(
        targetValue = if (visible) 0f else if (fromLeft) -100f else 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "slideOffsetX"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "slideAlpha"
    )
    
    this.graphicsLayer {
        translationX = offsetX
        this.alpha = alpha
    }
}
