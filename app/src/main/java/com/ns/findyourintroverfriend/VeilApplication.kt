package com.ns.findyourintroverfriend

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * VeilApplication — Application entry point.
 *
 * Initialises OneSignal once on process creation.
 * Implements ImageLoaderFactory so Coil uses a properly configured ImageLoader
 * with disk cache, memory cache, and resilient OkHttp client.
 */
class VeilApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // ── Firebase Crashlytics ───────────────────────────────────────────────
        // Automatically logs crashes to Firebase Console.
        // Disabled in debug so crash logs don't pollute the dashboard.
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // ── Firebase App Check (Play Integrity) ────────────────────────────────
        // Verifies requests come from a genuine app install, blocking bots/scrapers
        // that would abuse Firestore reads and inflate your bill.
        try {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            Log.w("VeilApp", "App Check init failed (safe to ignore in debug): ${e.message}")
        }

        // ── Firestore offline persistence (100 MB disk cache) ─────────────────
        // Returning users see data INSTANTLY. Cached reads are FREE (no billing).
        // Critical for viral spikes — reduces live Firestore reads by ~60%.
        try {
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024L * 1024L)
                        .build()
                )
                .build()
        } catch (e: Exception) {
            Log.w("VeilApp", "Firestore persistence setup failed: ${e.message}")
        }

        // ── OneSignal SDK v5 ───────────────────────────────────────────────────
        OneSignal.Debug.logLevel = if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.NONE
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
        if (BuildConfig.DEBUG) Log.d("VeilApp", "OneSignal initialised")
    }

    // ── Coil ImageLoader — called once by Coil's singleton ───────────────────
    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .components {
                // Handle Base64 data URIs (data:image/jpeg;base64,...)
                // These are stored in Firestore when Cloudinary isn't configured.
                // Coil does NOT support data: URIs natively.
                add(com.ns.findyourintroverfriend.util.Base64ImageFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)   // 25% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizePercent(0.05)   // 5% of disk space
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(300)
            .apply {
                if (BuildConfig.DEBUG) logger(DebugLogger())
            }
            .build()
    }
}
