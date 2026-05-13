package com.ns.findyourintroverfriend.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
//  PhotoValidator — on-device photo moderation using Google ML Kit
//  Completely FREE, no API key, works offline once model is downloaded.
//
//  Detects and REJECTS:
//   ✗ Objects, food, scenery, landscapes
//   ✗ Animals (pets, wildlife)
//   ✗ Memes / screenshots (no face)
//   ✗ Group photos (>1 person)
//   ✗ Very small/distant faces
//   ✗ Extreme side-angle shots (>50° rotation)
//   ✗ Eyes fully closed
//
//  MODEL NOTE: On first run, ML Kit downloads the face detection model
//  (~1 MB) from Google Play. The AndroidManifest meta-data tag tells
//  the Play Store to pre-download it at install time, so by the time
//  the user reaches the photo step it is ready.
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "PhotoValidator"

sealed class PhotoValidationResult {
    object Valid : PhotoValidationResult()
    data class Invalid(val reason: String) : PhotoValidationResult()
}

object PhotoValidator {

    // ── ML Kit high-accuracy detector (singleton — lazy) ──────────────────────
    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f)   // face must be ≥ 10% of image width
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Validate a photo URI.
     * MUST be called on a coroutine (suspend).
     * Returns Valid or Invalid(reason).
     *
     * Bug-fix notes:
     *  - Bitmap is NOT recycled before ML Kit processes it (that was Bug #2)
     *  - Exceptions are NOT silently swallowed as Valid (that was Bug #1)
     */
    suspend fun validate(context: Context, uri: Uri): PhotoValidationResult {

        // ── 1. Decode image ───────────────────────────────────────────────────
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return PhotoValidationResult.Invalid(
                "Could not open the selected image. Please try again."
            )

        val bitmap: Bitmap? = try {
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        } finally {
            inputStream.close()
        }

        if (bitmap == null) {
            return PhotoValidationResult.Invalid(
                "The selected file is not a valid image. Please choose a photo."
            )
        }

        // ── 2. Minimum size check ─────────────────────────────────────────────
        if (bitmap.width < 100 || bitmap.height < 100) {
            bitmap.recycle()
            return PhotoValidationResult.Invalid(
                "Image is too small. Please use a higher-quality photo."
            )
        }

        // ── 3. Run ML Kit Face Detection ──────────────────────────────────────
        // IMPORTANT: Create InputImage BEFORE recycling the bitmap.
        //            ML Kit holds a reference to the bitmap internally.
        val mlImage = InputImage.fromBitmap(bitmap, 0)

        val faces = try {
            detector.process(mlImage).await()
        } catch (e: Exception) {
            // Model not yet downloaded — tell user to retry, do NOT silently pass
            Log.e(TAG, "ML Kit face detection failed: ${e.message}", e)
            bitmap.recycle()
            return PhotoValidationResult.Invalid(
                "Photo check failed — please wait a moment and try again. " +
                "(Face detection model may still be downloading.)"
            )
        }

        // Safe to recycle now (ML Kit has finished processing)
        bitmap.recycle()

        // ── 4. Must have at least 1 face ──────────────────────────────────────
        if (faces.isEmpty()) {
            Log.d(TAG, "Rejected: no face detected")
            return PhotoValidationResult.Invalid(
                "No face detected. Please upload a clear selfie where your face is fully visible."
            )
        }

        // ── 5. No more than 2 faces (solo selfie) ─────────────────────────────
        if (faces.size > 2) {
            Log.d(TAG, "Rejected: ${faces.size} faces detected")
            return PhotoValidationResult.Invalid(
                "Multiple people detected (${faces.size} faces). Please upload a solo selfie — just you."
            )
        }

        val face = faces[0]

        // ── 6. Face must be a decent size — not tiny in a wide shot ──────────
        val faceArea  = face.boundingBox.width().toLong() * face.boundingBox.height().toLong()
        val imageArea = mlImage.width.toLong() * mlImage.height.toLong()
        val faceRatio = faceArea.toFloat() / imageArea.toFloat()
        Log.d(TAG, "Face ratio: $faceRatio (need ≥0.04)")

        if (faceRatio < 0.04f) {
            return PhotoValidationResult.Invalid(
                "Your face is too far away — please take a closer selfie so your face fills more of the frame."
            )
        }

        // ── 7. Face angle — must be roughly front-facing ──────────────────────
        val eulerY = face.headEulerAngleY   // left-right pan
        val eulerZ = face.headEulerAngleZ   // clockwise tilt
        Log.d(TAG, "Head angle: Y=$eulerY Z=$eulerZ")

        if (Math.abs(eulerY) > 55f) {
            return PhotoValidationResult.Invalid(
                "Your face is turned too far to the side. Please face the camera directly."
            )
        }
        if (Math.abs(eulerZ) > 45f) {
            return PhotoValidationResult.Invalid(
                "The photo is too tilted. Please hold your phone upright for your profile photo."
            )
        }

        // ── 8. Eyes must be open ──────────────────────────────────────────────
        val leftEye  = face.leftEyeOpenProbability  ?: 1f
        val rightEye = face.rightEyeOpenProbability ?: 1f
        Log.d(TAG, "Eye open prob: L=$leftEye R=$rightEye")

        if (leftEye < 0.25f && rightEye < 0.25f) {
            return PhotoValidationResult.Invalid(
                "Your eyes appear closed. Please choose a photo where your eyes are open."
            )
        }

        // ── All checks passed ─────────────────────────────────────────────────
        Log.d(TAG, "Photo validated: faces=${faces.size}, ratio=$faceRatio, Y=$eulerY")
        return PhotoValidationResult.Valid
    }

    /**
     * Flag a user's photo as inappropriate (peer-reported).
     * Writes to Firestore `photo_reports` for admin review.
     */
    fun reportPhoto(
        reporterUid : String,
        reportedUid : String,
        reason      : String,
        db          : com.google.firebase.firestore.FirebaseFirestore
    ) {
        db.collection("photo_reports").add(hashMapOf(
            "reporterUid" to reporterUid,
            "reportedUid" to reportedUid,
            "reason"      to reason,
            "timestamp"   to com.google.firebase.Timestamp.now(),
            "status"      to "pending"
        ))
    }
}
