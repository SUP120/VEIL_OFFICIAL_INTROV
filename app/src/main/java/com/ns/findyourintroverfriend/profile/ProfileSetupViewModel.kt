package com.ns.findyourintroverfriend.profile

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ns.findyourintroverfriend.notifications.VeilNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.Period

// ─────────────────────────────────────────────────────────────────────────────
//  Step enum — 3 essential steps only (DOB, Gender, Photo)
// ─────────────────────────────────────────────────────────────────────────────
enum class SetupStep(val title: String, val subtitle: String) {
    DOB(
        "When were you born?",
        "We verify your age to keep this space safe for everyone."
    ),
    GENDER(
        "How do you identify?",
        "Groups are balanced 2♂ + 2♀. This stays private."
    ),
    PHOTO(
        "Add your selfie",
        "Your photo verifies you're real and will be shown to your group."
    ),
    ALIAS(
        "Choose your alias",
        "This is the name others will see once the veil lifts. Make it yours."
    ),
    REFERRAL(
        "How did you find Veil?",
        "Takes one second. Helps us reach more introverts like you."
    )
}

val PERSONALITY_OPTIONS = listOf(
    "Thoughtful 🌿", "Reserved 🌙", "Deep-Thinker 🔭",
    "Creative 🎨", "Empathetic 💙", "Calm 🌊",
    "Curious 🔍", "Gentle 🕊", "Honest 📖",
    "Idealistic ✨"
)

val COMFORT_OPTIONS = listOf(
    "Slow texting — no rush",
    "I prefer voice later, not now",
    "I open up gradually",
    "I like thoughtful replies over quick ones",
    "I need space between conversations",
    "I don't like pressure to respond fast"
)

// ─────────────────────────────────────────────────────────────────────────────
//  Save result
// ─────────────────────────────────────────────────────────────────────────────
sealed class ProfileSaveResult {
    object Idle    : ProfileSaveResult()
    object Saving  : ProfileSaveResult()
    object Done    : ProfileSaveResult()
    data class Err(val msg: String) : ProfileSaveResult()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class ProfileSetupViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    // ── Step state ────────────────────────────────────────────────────────────
    var currentStep by mutableStateOf(SetupStep.DOB)
        private set

    val stepIndex get() = SetupStep.values().indexOf(currentStep)
    val totalSteps get() = SetupStep.values().size

    // ── Field values ──────────────────────────────────────────────────────────
    var dobDay   by mutableStateOf("")
    var dobMonth by mutableStateOf("")
    var dobYear  by mutableStateOf("")
    var dobError by mutableStateOf<String?>(null)

    var gender   by mutableStateOf<String?>(null)  // "Male" | "Female" | "Non-binary"

    var alias    by mutableStateOf("")
    var aliasError by mutableStateOf<String?>(null)

    var referralSource by mutableStateOf("")  // e.g. "Instagram", "Friend", etc.

    var photoUri    by mutableStateOf<Uri?>(null)
    var photoError  by mutableStateOf<String?>(null)
    var isValidating by mutableStateOf(false)   // true while ML Kit is running

    // ── Reviewer bypass (hidden code for Google Play reviewers) ──────────────
    // Real users never see this. Activated by tapping the photo circle 7 times.
    // The correct code is set in Play Console → App Access → Instructions.
    private val REVIEWER_CODE     = "VEIL2025REVIEW"  // <-- Change this each submission
    var reviewerBypassCode        by mutableStateOf("")
    var showReviewerBypass        by mutableStateOf(false)
    var isReviewerBypassed        by mutableStateOf(false)
        private set
    var reviewerCodeError         by mutableStateOf<String?>(null)

    fun onPhotoCircleTapped(tapCount: Int) {
        // After 7 rapid taps, reveal the hidden reviewer code field
        if (tapCount >= 7) showReviewerBypass = true
    }

    fun applyReviewerBypass() {
        if (reviewerBypassCode.trim().uppercase() == REVIEWER_CODE) {
            isReviewerBypassed = true
            reviewerCodeError  = null
            photoError         = null
        } else {
            reviewerCodeError = "Incorrect code. Contact support."
        }
    }

    val selectedPersonalities = mutableStateListOf<String>()
    val selectedComforts      = mutableStateListOf<String>()

    // ── Underage block state (18+ enforcement) ──────────────────────────────
    var isUnderageBlocked by mutableStateOf(false)
        private set

    // ── Save state ────────────────────────────────────────────────────────────
    private val _saveResult = MutableStateFlow<ProfileSaveResult>(ProfileSaveResult.Idle)
    val saveResult: StateFlow<ProfileSaveResult> = _saveResult.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    //  Navigation
    // ─────────────────────────────────────────────────────────────────────────

    fun canAdvance(): Boolean = when (currentStep) {
        SetupStep.DOB      -> validateDob()
        SetupStep.GENDER   -> gender != null
        // Photo step: real users must upload a valid face photo.
        // Google Play reviewers can bypass with the secret code (set in Play Console instructions).
        SetupStep.PHOTO    -> isReviewerBypassed || (photoUri != null && photoError == null && !isValidating)
        SetupStep.ALIAS    -> alias.trim().length >= 2
        SetupStep.REFERRAL -> true  // always skippable
    }

    fun advanceStep() {
        if (!canAdvance()) return
        val steps = SetupStep.values()
        val next  = steps.getOrNull(stepIndex + 1)
        if (next != null) currentStep = next else saveProfile()
    }

    fun backStep() {
        val steps = SetupStep.values()
        val prev  = steps.getOrNull(stepIndex - 1)
        if (prev != null) currentStep = prev
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Validation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun validateDob(): Boolean {
        val d = dobDay.toIntOrNull()
        val m = dobMonth.toIntOrNull()
        val y = dobYear.toIntOrNull()

        if (d == null || m == null || y == null || y < 1900) {
            dobError = "Please enter a valid date."
            return false
        }
        val date = try { LocalDate.of(y, m, d) } catch (e: Exception) {
            dobError = "That date doesn't exist."
            return false
        }
        val age = Period.between(date, LocalDate.now()).years
        if (age < 18) {
            // 18+ enforcement — permanently block underage users
            isUnderageBlocked = true
            dobError = "You must be 18 or older to use this app."
            return false
        }
        dobError = null
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Personality / Comfort toggles
    // ─────────────────────────────────────────────────────────────────────────

    fun togglePersonality(p: String) {
        if (p in selectedPersonalities) selectedPersonalities.remove(p)
        else if (selectedPersonalities.size < 3) selectedPersonalities.add(p)
    }

    fun toggleComfort(c: String) {
        if (c in selectedComforts) selectedComforts.remove(c)
        else selectedComforts.add(c)
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Photo — validate with ML Kit before accepting
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Called when user picks a photo from the gallery.
     * Runs ML Kit face detection on-device (free, offline).
     * Sets photoError if rejected, clears it if valid.
     */
    fun validateAndSetPhoto(uri: Uri) {
        photoUri    = uri          // Show preview immediately
        photoError  = null
        isValidating = true
        viewModelScope.launch {
            val ctx = com.google.firebase.FirebaseApp.getInstance().applicationContext
            val result = com.ns.findyourintroverfriend.util.PhotoValidator.validate(ctx, uri)
            isValidating = false
            when (result) {
                is com.ns.findyourintroverfriend.util.PhotoValidationResult.Valid -> {
                    photoError = null
                }
                is com.ns.findyourintroverfriend.util.PhotoValidationResult.Invalid -> {
                    photoError = result.reason
                    // Don't clear photoUri so user can see which photo was rejected
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Firestore save
    // ─────────────────────────────────────────────────────────────────────────

    fun saveProfile() {
        val uid = auth.currentUser?.uid ?: return
        _saveResult.value = ProfileSaveResult.Saving

        viewModelScope.launch {
            try {
                val ctx          = com.google.firebase.FirebaseApp.getInstance().applicationContext
                val cloudName    = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_CLOUD_NAME
                val uploadPreset = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_UPLOAD_PRESET

                android.util.Log.d("ProfileVM", "Uploading photo — cloud=$cloudName preset=$uploadPreset uri=$photoUri")

                // ── 1. Upload photo to Cloudinary ─────────────────────────────
                val resolvedPhotoUrl: String = if (photoUri != null) {
                    try {
                        if (cloudName.isBlank() || uploadPreset.isBlank()) {
                            _saveResult.value = ProfileSaveResult.Err(
                                "Cloudinary not configured. Check local.properties."
                            )
                            return@launch
                        }
                        val url = com.ns.findyourintroverfriend.util.CloudinaryUploader.upload(
                            context      = ctx,
                            uri          = photoUri!!,
                            cloudName    = cloudName,
                            uploadPreset = uploadPreset
                        )
                        android.util.Log.d("ProfileVM", "Upload success → $url")
                        url
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileVM", "Upload failed: ${e.message}", e)
                        _saveResult.value = ProfileSaveResult.Err(
                            "Photo upload failed: ${e.message?.take(80)}"
                        )
                        return@launch
                    }
                } else ""

                // ── 2. Save all profile fields to Firestore ───────────────────
                val data = hashMapOf(
                    "uid"             to uid,
                    "alias"           to alias.trim().ifBlank {
                        // Fallback: use first name from Google if alias is empty
                        auth.currentUser?.displayName?.split(" ")?.firstOrNull()?.take(20) ?: "Introvert"
                    },
                    "gender"          to (gender ?: ""),
                    "dob"             to "$dobYear-${dobMonth.padStart(2,'0')}-${dobDay.padStart(2,'0')}",
                    "personalities"   to emptyList<String>(),
                    "comfortPrefs"    to emptyList<String>(),
                    "photoUrl"        to resolvedPhotoUrl,
                    "profileComplete" to true,
                    "trustScore"      to 0,
                    "unlockStage"     to 0,
                    "referralSource"  to referralSource.trim(),
                    "createdAt"       to com.google.firebase.Timestamp.now(),
                    // Flag reviewer-bypassed accounts so they can be identified & cleaned up
                    "isReviewerAccount" to isReviewerBypassed
                )
                db.collection("users").document(uid)
                    .set(data, com.google.firebase.firestore.SetOptions.merge()).await()

                // ── Register device with OneSignal + send welcome notification ─
                VeilNotifier.registerAndPersistUser(uid, db)
                VeilNotifier.notifyWelcome(uid, db)

                _saveResult.value = ProfileSaveResult.Done
            } catch (e: Exception) {
                _saveResult.value = ProfileSaveResult.Err(e.localizedMessage ?: "Save failed.")
            }
        }
    }
}
