package com.ns.findyourintroverfriend.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlin.math.sin

// ── Palette: pure white + black ───────────────────────────────────────────────
private val ABg        = Color(0xFFFFFFFF)
private val AText      = Color(0xFF0A0A0A)
private val ATextSub   = Color(0xFF6B6B6B)
private val ATextHint  = Color(0xFFABABAB)
private val ADivider   = Color(0xFFEAEAEA)
private val AInput     = Color(0xFFF5F5F5)
private val ABtnBg     = Color(0xFF0A0A0A)
private val ABtnText   = Color(0xFFFFFFFF)
private val AError     = Color(0xFFD32F2F)
private val AAccent    = Color(0xFF0A0A0A)   // brand accent = black

@Composable
fun AuthScreen(
    viewModel    : AuthViewModel = viewModel(),
    onAuthSuccess: (needsProfile: Boolean) -> Unit,
    onOpenDevDash: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success)
            onAuthSuccess((uiState as AuthUiState.Success).needsProfile)
    }

    val gsc = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("202022157666-eeh2pb6u4cbtvibviaptoqmi960t1usg.apps.googleusercontent.com")
            .requestEmail().requestProfile().build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        try {
            viewModel.onGoogleSignInResult(
                GoogleSignIn.getSignedInAccountFromIntent(r.data)
                    .getResult(ApiException::class.java)
            )
        } catch (e: ApiException) { viewModel.onGoogleSignInError(e.statusCode) }
    }

    val isLoading = uiState is AuthUiState.Loading
    val error     = (uiState as? AuthUiState.Error)?.message

    Surface(color = ABg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top half — brand + headline ───────────────────────────────────
            Column(
                modifier            = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated Veil logo mark
                VeilLogoMark()

                Spacer(Modifier.height(28.dp))

                // Wordmark
                Text(
                    "VEIL",
                    fontSize      = 13.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 8.sp,
                    color         = AText
                )

                Spacer(Modifier.height(20.dp))

                // Headline
                Text(
                    "Where introverts\nfind their people.",
                    fontSize   = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = AText,
                    textAlign  = TextAlign.Center,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.5).sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "One question at a time.\nNo pressure. Just real connection.",
                    fontSize   = 14.sp,
                    color      = ATextSub,
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(24.dp))

                // Live whisper strip
                LiveWhisper()
            }

            // ── Divider ───────────────────────────────────────────────────────
            HorizontalDivider(color = ADivider, thickness = 1.dp)

            // ── Bottom section — sign in ──────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                error?.let { err ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF0F0))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(err, fontSize = 13.sp, color = AError, textAlign = TextAlign.Center)
                    }
                }

                GoogleSignInButton(isLoading = isLoading) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.resetState()
                    launcher.launch(gsc.signInIntent)
                }

                LegalText()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Veil animated logo mark — two overlapping circles, minimal B&W
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VeilLogoMark() {
    val inf = rememberInfiniteTransition(label = "logo")
    val pulse by inf.animateFloat(
        0.88f, 1.04f,
        infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "pulse"
    )
    Canvas(
        modifier = Modifier
            .size(72.dp)
            .scale(pulse)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.minDimension * 0.36f

        // Outer ring
        drawCircle(
            color  = AText.copy(alpha = 0.08f),
            radius = r * 1.35f,
            center = Offset(cx, cy),
            style  = Stroke(width = 1f)
        )
        // Left circle
        drawCircle(
            color  = AText.copy(alpha = 0.55f),
            radius = r * 0.44f,
            center = Offset(cx - r * 0.24f, cy),
            style  = Stroke(width = 2f)
        )
        // Right circle (slightly lighter)
        drawCircle(
            color  = AText.copy(alpha = 0.30f),
            radius = r * 0.44f,
            center = Offset(cx + r * 0.24f, cy),
            style  = Stroke(width = 2f)
        )
        // Solid center overlap fill
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(AText.copy(0.15f), Color.Transparent),
                center = Offset(cx, cy), radius = r * 0.30f
            ),
            radius = r * 0.30f,
            center = Offset(cx, cy)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Google Sign-In Button — clean white card, black text
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GoogleSignInButton(isLoading: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (isLoading) 0.97f else 1f,
        spring(stiffness = Spring.StiffnessLow), label = "bs"
    )

    // White bordered Google button
    Surface(
        onClick         = { if (!isLoading) onClick() },
        modifier        = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale),
        shape           = RoundedCornerShape(14.dp),
        color           = ABg,
        shadowElevation = 0.dp,
        border          = ButtonDefaults.outlinedButtonBorder
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = AText,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GoogleGIcon(Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Continue with Google",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = AText
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Legal text
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LegalText() {
    val ctx = LocalContext.current
    val legal = buildAnnotatedString {
        withStyle(SpanStyle(color = ATextHint, fontSize = 11.sp)) {
            append("By continuing you agree to our ")
        }
        pushStringAnnotation("URL", "https://sites.google.com/view/veil-privacy-policy/home")
        withStyle(SpanStyle(
            color          = AText,
            fontSize       = 11.sp,
            textDecoration = TextDecoration.Underline,
            fontWeight     = FontWeight.Medium
        )) { append("Terms & Privacy Policy") }
        pop()
        withStyle(SpanStyle(color = ATextHint, fontSize = 11.sp)) {
            append("  ·  18+ only")
        }
    }
    ClickableText(
        text     = legal,
        style    = TextStyle(textAlign = TextAlign.Center, lineHeight = 16.sp),
        modifier = Modifier.fillMaxWidth(),
        onClick  = { offset ->
            legal.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(it.item)).also { i -> ctx.startActivity(i) }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Google "G" icon (canvas drawn)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GoogleGIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r  = size.minDimension / 2f; val s = r * 0.22f
        drawArc(Color(0xFF4285F4), -200f, 270f, false, Offset(cx-r, cy-r),
            Size(r*2f, r*2f), style = Stroke(s))
        drawArc(Color(0xFFEA4335), -200f, -80f, false, Offset(cx-r, cy-r),
            Size(r*2f, r*2f), style = Stroke(s))
        drawArc(Color(0xFF34A853), 70f, 70f, false, Offset(cx-r, cy-r),
            Size(r*2f, r*2f), style = Stroke(s))
        drawLine(Color(0xFF4285F4), Offset(cx, cy), Offset(cx + r*0.85f, cy), s)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LiveWhisper — rotating social-proof strip
// ─────────────────────────────────────────────────────────────────────────────
private val WHISPERS = listOf(
    "🟢  14 introverts are in queue right now",
    "✦   3 circles formed in the last hour",
    "🌙  Circles fill up fast after midnight",
    "💬  Zero small talk. Just real conversations.",
    "⏳  Your circle might already be waiting",
    "🔒  100% anonymous for the first 3 days",
)

@Composable
private fun LiveWhisper() {
    var idx   by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3_500)
            alpha.animateTo(0f, tween(320))
            idx = (idx + 1) % WHISPERS.size
            alpha.animateTo(1f, tween(320))
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(AInput)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = WHISPERS[idx],
            modifier = Modifier.drawWithCache {
                onDrawWithContent {
                    drawContext.canvas.saveLayer(
                        androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                        Paint().apply { this.alpha = alpha.value }
                    )
                    drawContent()
                    drawContext.canvas.restore()
                }
            },
            fontSize  = 12.sp,
            color     = ATextSub,
            textAlign = TextAlign.Center,
            maxLines  = 1
        )
    }
}
