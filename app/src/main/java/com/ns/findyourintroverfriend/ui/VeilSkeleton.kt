package com.ns.findyourintroverfriend.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
//  Core shimmer brush — animates a gradient sweep left→right
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun shimmerBrush(
    baseColor    : Color = Color(0xFF1A2540),
    highlightColor: Color = Color(0xFF2A3A60)
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1200f,
        animationSpec = infiniteRepeatable(
            animation   = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode  = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start  = Offset(translateAnim - 400f, 0f),
        end    = Offset(translateAnim + 400f, 0f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Generic shimmer block
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShimmerBlock(
    modifier      : Modifier = Modifier,
    cornerRadius  : Dp       = 10.dp,
    baseColor     : Color    = Color(0xFF1A2540),
    highlightColor: Color    = Color(0xFF2A3A60)
) {
    val brush = shimmerBrush(baseColor, highlightColor)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun ShimmerCircle(
    size          : Dp,
    baseColor     : Color = Color(0xFF1A2540),
    highlightColor: Color = Color(0xFF2A3A60)
) {
    val brush = shimmerBrush(baseColor, highlightColor)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(brush)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Island Home skeleton — mirrors SoulWeb + activity strip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun IslandHomeSkeletonScreen(isDay: Boolean) {
    val base = if (isDay) Color(0xFFDDE4EE) else Color(0xFF1A2540)
    val hi   = if (isDay) Color(0xFFF0F4FB) else Color(0xFF2A3A60)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar skeleton
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBlock(modifier = Modifier.width(80.dp).height(20.dp), baseColor = base, highlightColor = hi)
                ShimmerBlock(modifier = Modifier.width(100.dp).height(20.dp), baseColor = base, highlightColor = hi)
            }
            Spacer(Modifier.height(16.dp))

            // Centre: soul web placeholder
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                // Central connection web rings
                ShimmerCircle(240.dp, base, hi)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBlock(
                        modifier = Modifier.width(120.dp).height(14.dp),
                        cornerRadius = 8.dp, baseColor = base, highlightColor = hi
                    )
                    ShimmerBlock(
                        modifier = Modifier.width(80.dp).height(10.dp),
                        cornerRadius = 6.dp, baseColor = base, highlightColor = hi
                    )
                }
            }

            // Bottom strip: 4 member avatar skeletons
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(4) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShimmerCircle(48.dp, base, hi)
                        ShimmerBlock(
                            modifier = Modifier.width(36.dp).height(8.dp),
                            cornerRadius = 4.dp, baseColor = base, highlightColor = hi
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Slow Chat skeleton — 5 alternating chat bubbles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SlowChatSkeletonScreen(myBlue: Color, teal: Color) {
    val base = Color(0xFF0D1530)
    val hi   = Color(0xFF1A2550)

    Column(modifier = Modifier.fillMaxSize()) {
        // Message quota bar skeleton
        ShimmerBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(36.dp),
            cornerRadius = 10.dp, baseColor = base, highlightColor = hi
        )

        // Fake message bubbles
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(false, true, false, false, true).forEachIndexed { _, isMe ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!isMe) {
                        ShimmerCircle(34.dp, base, hi)
                        Spacer(Modifier.width(8.dp))
                    }
                    ShimmerBlock(
                        modifier = Modifier
                            .width(if (isMe) 200.dp else 180.dp)
                            .height(52.dp),
                        cornerRadius = 18.dp, baseColor = base, highlightColor = hi
                    )
                    if (isMe) {
                        Spacer(Modifier.width(8.dp))
                        ShimmerCircle(34.dp, base, hi)
                    }
                }
            }
        }

        // Input bar skeleton
        ShimmerBlock(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
            cornerRadius = 16.dp, baseColor = base, highlightColor = hi
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Question Card skeleton — question pill + 4 answer tiles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuestionSkeletonScreen() {
    val base = Color(0xFF0D102A)
    val hi   = Color(0xFF1C2050)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        // Question card
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            cornerRadius = 20.dp, baseColor = base, highlightColor = hi
        )
        Spacer(Modifier.height(8.dp))
        // 4 answer option rows
        repeat(4) {
            ShimmerBlock(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                cornerRadius = 14.dp, baseColor = base, highlightColor = hi
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reveal Ceremony skeleton — 4 member cards stacked
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RevealSkeletonScreen() {
    val base = Color(0xFF0A0C1E)
    val hi   = Color(0xFF181D3A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        // Title
        ShimmerBlock(
            modifier = Modifier.width(180.dp).height(22.dp),
            cornerRadius = 8.dp, baseColor = base, highlightColor = hi
        )
        ShimmerBlock(
            modifier = Modifier.width(240.dp).height(14.dp),
            cornerRadius = 6.dp, baseColor = base, highlightColor = hi
        )
        Spacer(Modifier.height(16.dp))
        // 4 member reveal cards
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF101428))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ShimmerCircle(52.dp, base, hi)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBlock(
                        modifier = Modifier.width(100.dp).height(14.dp),
                        cornerRadius = 6.dp, baseColor = base, highlightColor = hi
                    )
                    ShimmerBlock(
                        modifier = Modifier.width(140.dp).height(10.dp),
                        cornerRadius = 5.dp, baseColor = base, highlightColor = hi
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Inbox skeleton — question + answer cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InboxSkeletonScreen() {
    val base = Color(0xFF0D1530)
    val hi   = Color(0xFF1A2550)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // My question card
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            cornerRadius = 22.dp, baseColor = base, highlightColor = hi
        )
        Spacer(Modifier.height(8.dp))
        // Answer cards
        repeat(3) {
            ShimmerBlock(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                cornerRadius = 20.dp, baseColor = base, highlightColor = hi
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Answer Card skeleton — question list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnswerCardSkeletonScreen() {
    val base = Color(0xFFDDE4EE)
    val hi   = Color(0xFFF0F4FB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress pill
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth().height(20.dp),
            cornerRadius = 10.dp, baseColor = base, highlightColor = hi
        )
        Spacer(Modifier.height(8.dp))
        // Question cards
        repeat(4) {
            ShimmerBlock(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                cornerRadius = 22.dp, baseColor = base, highlightColor = hi
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  My Questions Inbox skeleton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MyQuestionsSkeletonScreen() {
    val base = Color(0xFFEDD9C0)
    val hi   = Color(0xFFFFF9F4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary pill
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            cornerRadius = 12.dp, baseColor = base, highlightColor = hi
        )
        Spacer(Modifier.height(4.dp))
        // Question cards with replies
        repeat(3) {
            ShimmerBlock(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                cornerRadius = 22.dp, baseColor = base, highlightColor = hi
            )
        }
    }
}
