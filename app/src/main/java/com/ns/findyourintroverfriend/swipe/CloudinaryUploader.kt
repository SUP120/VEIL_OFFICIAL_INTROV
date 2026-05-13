package com.ns.findyourintroverfriend.swipe

import com.ns.findyourintroverfriend.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Lightweight Cloudinary uploader using the unsigned upload preset.
 * Works on the free Spark plan — no Firebase Storage / Blaze needed.
 *
 * Requires in local.properties:
 *   cloudinary.cloud.name=<your_cloud_name>
 *   cloudinary.upload.preset=<your_unsigned_preset>
 */
object CloudinaryUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val cloudName    get() = BuildConfig.CLOUDINARY_CLOUD_NAME
    private val uploadPreset get() = BuildConfig.CLOUDINARY_UPLOAD_PRESET

    /**
     * Upload a [File] to Cloudinary.
     * @param resourceType "image" for drawings, "video" for audio (Cloudinary uses "video" for audio)
     * @param mimeType     e.g. "audio/mp4", "image/png"
     * @return secure download URL
     */
    suspend fun uploadFile(
        file       : File,
        resourceType: String,
        mimeType   : String,
        folder     : String = ""
    ): String = withContext(Dispatchers.IO) {
        val mediaType   = mimeType.toMediaTypeOrNull()
        val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
        if (folder.isNotBlank()) formBuilder.addFormDataPart("folder", folder)

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/$resourceType/upload")
            .post(formBuilder.build())
            .build()

        val response = client.newCall(request).execute()
        val body     = response.body?.string() ?: error("Empty Cloudinary response")
        JSONObject(body).getString("secure_url")
    }

    /**
     * Upload raw [ByteArray] to Cloudinary (e.g. bitmap PNG bytes).
     * @param resourceType "image" for drawings
     * @param mimeType     e.g. "image/png"
     */
    suspend fun uploadBytes(
        bytes       : ByteArray,
        fileName    : String,
        resourceType: String,
        mimeType    : String,
        folder      : String = ""
    ): String = withContext(Dispatchers.IO) {
        val mediaType   = mimeType.toMediaTypeOrNull()
        val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mediaType))
        if (folder.isNotBlank()) formBuilder.addFormDataPart("folder", folder)

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/$resourceType/upload")
            .post(formBuilder.build())
            .build()

        val response = client.newCall(request).execute()
        val body     = response.body?.string() ?: error("Empty Cloudinary response")
        JSONObject(body).getString("secure_url")
    }
}
