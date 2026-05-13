package com.ns.findyourintroverfriend.util

import android.graphics.BitmapFactory
import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import okio.Buffer

/**
 * Coil Fetcher that handles Base64 data URIs of the form:
 *   data:image/jpeg;base64,/9j/4AAQSkZJRg...
 *
 * These are stored in Firestore as a fallback when Cloudinary is not configured.
 * Coil does NOT handle data: URIs natively, so without this fetcher every
 * profile photo stored as Base64 shows as blank.
 */
class Base64ImageFetcher(
    private val data: String,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Strip the "data:image/...;base64," header
        val commaIndex = data.indexOf(',')
        val base64Str  = if (commaIndex >= 0) data.substring(commaIndex + 1) else data

        val bytes  = Base64.decode(base64Str, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Base64 decoding failed — not a valid image")

        return DrawableResult(
            drawable   = android.graphics.drawable.BitmapDrawable(options.context.resources, bitmap),
            isSampled  = false,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.startsWith("data:image")) return null
            return Base64ImageFetcher(data, options)
        }
    }
}
