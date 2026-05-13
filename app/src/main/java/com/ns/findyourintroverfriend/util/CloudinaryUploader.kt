package com.ns.findyourintroverfriend.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
//  CloudinaryUploader
//  Free image hosting — no Firebase Storage / Blaze plan needed.
//
//  HOW TO SET UP (one-time, 2 minutes):
//  1. Sign up FREE at https://cloudinary.com  (no credit card)
//  2. Copy your Cloud Name from the dashboard
//  3. Settings → Upload → Upload presets → Add upload preset
//     • Signing mode: Unsigned   • Save → copy the preset name
//  4. Add to local.properties:
//       cloudinary.cloud.name=your_cloud_name
//       cloudinary.upload.preset=your_preset_name
//  5. These are baked into BuildConfig at build time (see build.gradle.kts)
// ─────────────────────────────────────────────────────────────────────────────

object CloudinaryUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)   // longer for image upload
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)        // auto-retry on transient failures
        .build()

    /**
     * Compress then upload an image URI to Cloudinary.
     * Compresses to max 1080px / 80% JPEG quality (~200–400 KB) before upload.
     * Returns the HTTPS CDN URL. Throws on failure.
     */
    suspend fun upload(
        context      : Context,
        uri          : Uri,
        cloudName    : String,
        uploadPreset : String
    ): String = withContext(Dispatchers.IO) {

        android.util.Log.d("CloudinaryUploader", "Starting upload: cloud=$cloudName preset=$uploadPreset")

        // ── Compress before upload ────────────────────────────────────────────
        // Raw phone photos: 4–10 MB → compressed: ~200–400 KB
        val imageBytes = compressImage(context, uri, maxDim = 1080, quality = 80)
        if (imageBytes.isEmpty()) throw Exception("Image is empty after compression — URI: $uri")
        android.util.Log.d("CloudinaryUploader", "Compressed size: ${imageBytes.size / 1024} KB")

        // ── Build multipart upload request ────────────────────────────────────
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name     = "file",
                filename = "photo_${System.currentTimeMillis()}.jpg",
                body     = imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            .addFormDataPart("upload_preset", uploadPreset)
            .build()

        val url = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"
        android.util.Log.d("CloudinaryUploader", "POST → $url")

        val request = Request.Builder().url(url).post(requestBody).build()

        // ── Execute and parse ─────────────────────────────────────────────────
        val response = client.newCall(request).execute()
        val body     = response.body?.string() ?: ""
        android.util.Log.d("CloudinaryUploader", "Response ${response.code}: ${body.take(200)}")

        if (!response.isSuccessful) {
            val errorMsg = try { JSONObject(body).optString("error", body) } catch (e: Exception) { body }
            throw Exception("Cloudinary upload failed (${response.code}): $errorMsg")
        }

        val json      = JSONObject(body)
        val secureUrl = json.optString("secure_url", "")
        if (secureUrl.isBlank()) throw Exception("No secure_url in Cloudinary response: $body")

        android.util.Log.d("CloudinaryUploader", "Upload complete → $secureUrl")
        secureUrl
    }

    // ── Image compression helper ──────────────────────────────────────────────
    // Decodes URI → Bitmap → scales to maxDim → re-encodes as JPEG at quality%
    private fun compressImage(context: Context, uri: Uri, maxDim: Int, quality: Int): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ByteArray(0)
        val original    = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (original == null) return ByteArray(0)

        val w = original.width
        val h = original.height
        val scaled: Bitmap = if (w > maxDim || h > maxDim) {
            val scale = maxDim.toFloat() / maxOf(w, h).toFloat()
            Bitmap.createScaledBitmap(original, (w * scale).toInt(), (h * scale).toInt(), true)
        } else original

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== original) scaled.recycle()
        original.recycle()
        return out.toByteArray()
    }
}
