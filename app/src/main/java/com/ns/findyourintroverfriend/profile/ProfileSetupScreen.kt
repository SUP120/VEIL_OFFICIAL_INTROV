package com.ns.findyourintroverfriend.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────────────────
//  Profile Setup — White & Black, Instagram / Twitter aesthetic
// ─────────────────────────────────────────────────────────────────────────────

// ── Palette — strictly white + black + grays ──────────────────────────────────
private val PBg         = Color(0xFFFFFFFF)   // pure white
private val PText       = Color(0xFF0A0A0A)   // near-black
private val PTextSub    = Color(0xFF6B6B6B)   // medium gray
private val PTextHint   = Color(0xFFABABAB)   // light gray
private val PDivider    = Color(0xFFE8E8E8)   // hairline divider
private val PInput      = Color(0xFFF5F5F5)   // input field bg
private val PInputFocus = Color(0xFFEEEEEE)   // focused input bg
private val PBorder     = Color(0xFFD0D0D0)   // subtle border
private val PBorderFocus= Color(0xFF0A0A0A)   // focused border = black
private val PButtonBg   = Color(0xFF0A0A0A)   // CTA = black
private val PButtonText = Color(0xFFFFFFFF)   // CTA text = white
private val PError      = Color(0xFFD32F2F)   // error red
private val PSuccess    = Color(0xFF388E3C)   // success green

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ProfileSetupScreen(
    vm: ProfileSetupViewModel = viewModel(),
    onSetupComplete: () -> Unit
) {
    val saveResult by vm.saveResult.collectAsStateWithLifecycle()

    LaunchedEffect(saveResult) {
        if (saveResult is ProfileSaveResult.Done) onSetupComplete()
    }

    if (vm.isUnderageBlocked) {
        UnderageBlockScreen()
        return
    }

    Surface(color = PBg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            SetupTopBar(
                stepIndex  = vm.stepIndex,
                totalSteps = vm.totalSteps,
                onBack     = { vm.backStep() },
                canGoBack  = vm.stepIndex > 0
            )

            // ── Step content ─────────────────────────────────────────────────
            AnimatedContent(
                targetState    = vm.currentStep,
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut(tween(180)))
                },
                modifier       = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                label          = "stepContent"
            ) { step ->
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(Modifier.height(32.dp))

                    // Step badge
                    Text(
                        text      = "Step ${step.ordinal + 1} of ${vm.totalSteps}",
                        fontSize  = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color     = PTextHint,
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // Title
                    Text(
                        text      = step.title,
                        fontSize  = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color     = PText,
                        lineHeight = 32.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    // Subtitle
                    Text(
                        text      = step.subtitle,
                        fontSize  = 14.sp,
                        color     = PTextSub,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(32.dp))

                    // Step UI
                    when (step) {
                        SetupStep.DOB    -> DobStep(vm)
                        SetupStep.GENDER -> GenderStep(vm)
                        SetupStep.PHOTO  -> PhotoStep(vm)
                        SetupStep.ALIAS  -> AliasStep(vm)
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }

            // ── CTA ──────────────────────────────────────────────────────────
            SetupCta(
                isLastStep = vm.stepIndex == vm.totalSteps - 1,
                canAdvance = vm.canAdvance(),
                isSaving   = saveResult is ProfileSaveResult.Saving,
                saveError  = (saveResult as? ProfileSaveResult.Err)?.msg,
                onNext     = { vm.advanceStep() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar — minimal, Twitter-like
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SetupTopBar(
    stepIndex  : Int,
    totalSteps : Int,
    canGoBack  : Boolean,
    onBack     : () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector        = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint               = PText,
                        modifier           = Modifier.size(22.dp)
                    )
                }
            } else {
                Text(
                    "VEIL",
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = PText,
                    letterSpacing = 4.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${stepIndex + 1} / $totalSteps",
                fontSize  = 13.sp,
                fontWeight = FontWeight.Medium,
                color     = PTextSub
            )
        }

        // Progress bar
        val progress by animateFloatAsState(
            targetValue   = (stepIndex + 1f) / totalSteps,
            animationSpec = tween(400, easing = FastOutSlowInEasing),
            label         = "progress"
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(PDivider)) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(PText)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CTA Button — black pill, Instagram-style
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SetupCta(
    isLastStep : Boolean,
    canAdvance : Boolean,
    isSaving   : Boolean,
    saveError  : String?,
    onNext     : () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PBg)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hairline top divider
        HorizontalDivider(color = PDivider, thickness = 1.dp)
        Spacer(Modifier.height(2.dp))

        saveError?.let { err ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF0F0))
                    .padding(12.dp)
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = PError, modifier = Modifier.size(16.dp))
                Text(err, fontSize = 13.sp, color = PError)
            }
        }

        Button(
            onClick  = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNext()
            },
            enabled  = canAdvance && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor         = PButtonBg,
                disabledContainerColor = PDivider
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = PButtonText,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text  = if (isLastStep) "Create account" else "Continue",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (canAdvance) PButtonText else PTextHint
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Step 1 — Date of Birth
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DobStep(vm: ProfileSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PField(
                value       = vm.dobDay,
                onValue     = { if (it.length <= 2 && it.all(Char::isDigit)) vm.dobDay = it },
                placeholder = "DD",
                modifier    = Modifier.weight(1f),
                keyboardType = KeyboardType.Number
            )
            PField(
                value       = vm.dobMonth,
                onValue     = { if (it.length <= 2 && it.all(Char::isDigit)) vm.dobMonth = it },
                placeholder = "MM",
                modifier    = Modifier.weight(1f),
                keyboardType = KeyboardType.Number
            )
            PField(
                value       = vm.dobYear,
                onValue     = { if (it.length <= 4 && it.all(Char::isDigit)) vm.dobYear = it },
                placeholder = "YYYY",
                modifier    = Modifier.weight(1.8f),
                keyboardType = KeyboardType.Number
            )
        }

        vm.dobError?.let { err ->
            PErrorNote(err)
        }

        PNote("🔒  Your date of birth is never shared with other users.")
        PNote("⚠  You must be 18 or older. Misrepresentation leads to a permanent ban.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Step 2 — Gender
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GenderStep(vm: ProfileSetupViewModel) {
    val options = listOf("Male" to "♂", "Female" to "♀", "Non-binary" to "⚧")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (label, icon) ->
            val selected = vm.gender == label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) PText else PBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { vm.gender = label }
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(icon, fontSize = 22.sp)
                Text(
                    label,
                    fontSize   = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (selected) PText else PTextSub,
                    modifier   = Modifier.weight(1f)
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(PText),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check, null,
                            tint = PButtonText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        PNote("⚈  Groups are balanced 2♂ + 2♀. This stays completely private.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Step 3 — Profile Photo
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PhotoStep(vm: ProfileSetupViewModel) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultUri = com.yalantis.ucrop.UCrop.getOutput(
            result.data ?: return@rememberLauncherForActivityResult
        )
        if (result.resultCode == android.app.Activity.RESULT_OK && resultUri != null) {
            vm.validateAndSetPhoto(resultUri)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { sourceUri: Uri? ->
        if (sourceUri == null) return@rememberLauncherForActivityResult
        val destFile = java.io.File(context.cacheDir, "veil_crop_${System.currentTimeMillis()}.jpg")
        val destUri  = Uri.fromFile(destFile)
        val cropIntent = com.yalantis.ucrop.UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(600, 600)
            .withOptions(com.yalantis.ucrop.UCrop.Options().apply {
                setCircleDimmedLayer(true)
                setShowCropGrid(false)
                setStatusBarColor(android.graphics.Color.BLACK)
                setToolbarColor(android.graphics.Color.BLACK)
                setToolbarWidgetColor(android.graphics.Color.WHITE)
                setActiveControlsWidgetColor(android.graphics.Color.BLACK)
                setToolbarTitle("Crop photo")
                setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
                setCompressionQuality(92)
                setHideBottomControls(false)
                setFreeStyleCropEnabled(false)
            })
            .getIntent(context)
        cropLauncher.launch(cropIntent)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Avatar circle ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(PInput)
                .border(
                    width = when {
                        vm.photoError  != null -> 2.dp
                        vm.photoUri    != null && !vm.isValidating -> 2.dp
                        else -> 1.dp
                    },
                    color = when {
                        vm.photoError  != null -> PError
                        vm.photoUri    != null && !vm.isValidating -> PSuccess
                        else -> PBorder
                    },
                    shape = CircleShape
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    picker.launch("image/*")
                },
            contentAlignment = Alignment.Center
        ) {
            if (vm.photoUri != null) {
                AsyncImage(
                    model              = vm.photoUri,
                    contentDescription = "Profile photo",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                // Scanning overlay
                if (vm.isValidating) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(26.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Verifying…", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.AddAPhoto, null,
                        tint     = PTextHint,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Add photo", fontSize = 12.sp, color = PTextHint)
                }
            }
        }

        // Status row
        when {
            vm.isValidating -> Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(14.dp),
                    color       = PTextSub,
                    strokeWidth = 1.5.dp
                )
                Text("Checking your photo…", fontSize = 13.sp, color = PTextSub)
            }
            vm.photoError != null -> PErrorNote(vm.photoError!!)
            vm.photoUri   != null -> Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = PSuccess, modifier = Modifier.size(16.dp))
                Text("Photo looks good!", fontSize = 13.sp, color = PSuccess, fontWeight = FontWeight.Medium)
            }
        }

        // Change / pick button
        OutlinedButton(
            onClick  = { picker.launch("image/*") },
            shape    = RoundedCornerShape(12.dp),
            border   = BorderStroke(1.dp, PBorder),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = PText),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (vm.photoUri != null) "Change photo" else "Choose from gallery",
                fontSize = 14.sp
            )
        }

        PNote("📸  Crop to a circle — face centered, clearly visible. This is your Veil identity.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Step 4 — Alias
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AliasStep(vm: ProfileSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PField(
            value          = vm.alias,
            onValue        = { if (it.length <= 24) vm.alias = it },
            placeholder    = "e.g. Nova, River, Stargazer…",
            modifier       = Modifier.fillMaxWidth(),
            capitalization = KeyboardCapitalization.Words
        )

        // Live character count
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text("${vm.alias.length} / 24", fontSize = 11.sp, color = PTextHint)
        }

        if (vm.alias.trim().length in 1..1) {
            PErrorNote("Alias must be at least 2 characters.")
        }

        PNote("✨  This is the name others see after the veil lifts. Pick something that feels like you.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Underage block (18+ enforcement)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UnderageBlockScreen() {
    Surface(color = PBg, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(40.dp).systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(PDivider),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Block, null,
                        tint     = PTextSub,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    "You must be 18+",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = PText,
                    textAlign  = TextAlign.Center
                )
                Text(
                    "Veil is only available for adults 18 years and older.",
                    fontSize  = 14.sp,
                    color     = PTextSub,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared field component — Instagram-style input
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PField(
    value          : String,
    onValue        : (String) -> Unit,
    placeholder    : String,
    modifier       : Modifier = Modifier,
    keyboardType   : KeyboardType = KeyboardType.Text,
    capitalization : KeyboardCapitalization = KeyboardCapitalization.None
) {
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value         = value,
        onValueChange = onValue,
        modifier      = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType   = keyboardType,
            capitalization = capitalization
        ),
        singleLine   = true,
        textStyle    = androidx.compose.ui.text.TextStyle(
            fontSize   = 16.sp,
            color      = PText,
            fontWeight = FontWeight.Normal
        ),
        decorationBox = { inner ->
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (focused) PInputFocus else PInput)
                        .border(
                            width = if (focused) 1.5.dp else 1.dp,
                            color = if (focused) PBorderFocus else PBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 16.sp, color = PTextHint)
                    }
                    inner()
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Note chip — subtle info line
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PNote(text: String) {
    Text(
        text      = text,
        fontSize  = 12.sp,
        color     = PTextSub,
        lineHeight = 17.sp
    )
}

@Composable
private fun PErrorNote(text: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = PError, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, color = PError, lineHeight = 17.sp)
    }
}
