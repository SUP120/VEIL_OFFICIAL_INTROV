package com.ns.findyourintroverfriend.util

// ─────────────────────────────────────────────────────────────────────────────
//  CloudinaryUtil
//
//  Cloudinary supports on-the-fly image transforms via URL params, so we can
//  derive a lightweight thumbnail URL from any existing full-resolution URL
//  WITHOUT uploading twice or changing Firestore data.
//
//  Full URL:      https://res.cloudinary.com/cloud/image/upload/photo_123.jpg
//  Thumbnail URL: https://res.cloudinary.com/cloud/image/upload/w_400,q_50,f_auto,c_limit/photo_123.jpg
//
//  Benefits:
//  - Zero extra uploads / storage cost
//  - Works on ALL existing posts retroactively
//  - Cloudinary CDN caches generated thumbnails automatically
//  - f_auto picks WebP on supported devices (even smaller)
// ─────────────────────────────────────────────────────────────────────────────

object CloudinaryUtil {

    /**
     * Derive a thumbnail URL for use in feed/grid views.
     * Feed thumbnail: ~400px wide, 50% quality, auto-format (WebP) → ~40–80 KB
     * Falls through to the original URL for non-Cloudinary hosts.
     */
    fun thumbnail(url: String, width: Int = 400, quality: Int = 50): String {
        if (url.isBlank()) return url
        if (!url.contains("res.cloudinary.com")) return url   // non-Cloudinary, use as-is
        return insertTransform(url, "w_$width,h_$width,q_$quality,f_auto,c_fill")
    }

    /**
     * Full-resolution URL suitable for the detail / lightbox viewer.
     * Still applies f_auto (WebP) but at high quality.
     */
    fun fullRes(url: String, maxWidth: Int = 1080): String {
        if (url.isBlank()) return url
        if (!url.contains("res.cloudinary.com")) return url
        return insertTransform(url, "w_$maxWidth,q_85,f_auto,c_limit")
    }

    /**
     * Insert Cloudinary transformation string between `/upload/` and the public_id.
     * Input:  .../image/upload/photo_123.jpg
     * Output: .../image/upload/{transforms}/photo_123.jpg
     * Handles URLs that already have a version (`v1234567/`) prefix gracefully.
     */
    private fun insertTransform(url: String, transform: String): String {
        val marker = "/upload/"
        val idx = url.indexOf(marker)
        if (idx == -1) return url
        val before = url.substring(0, idx + marker.length)
        val after  = url.substring(idx + marker.length)

        // Skip if transforms are already present (avoid double-inserting)
        if (after.startsWith("w_") || after.startsWith("h_") || after.startsWith("f_")) return url

        return "$before$transform/$after"
    }
}
