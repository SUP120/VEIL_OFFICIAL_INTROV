# ─────────────────────────────────────────────────────────────────────────────
# Veil App — ProGuard / R8 Rules
# Required because isMinifyEnabled = true for release builds
# ─────────────────────────────────────────────────────────────────────────────

# ── Keep source file names in stack traces (helpful for crash reports) ───────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Annotations + signatures needed by Firestore/Kotlin/Gson ────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ── Firebase / Firestore ─────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── OneSignal ────────────────────────────────────────────────────────────────
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**

# ── OkHttp (used for OneSignal REST calls) ───────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ── Kotlin coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Kotlin metadata (needed for reflection and serialization) ────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Jetpack Compose ──────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── ML Kit Face Detection ────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── UCrop (photo cropper) ────────────────────────────────────────────────────
-dontwarn com.yalantis.ucrop.**
-keep class com.yalantis.ucrop.** { *; }
-keepclassmembers class com.yalantis.ucrop.** { *; }

# ── Coil (image loading) — comprehensive keep rules ─────────────────────────
-keep class coil.** { *; }
-keep interface coil.** { *; }
-keepclassmembers class coil.** { *; }
-dontwarn coil.**

# ═════════════════════════════════════════════════════════════════════════════
#  ✅ FIRESTORE DATA MODELS — CRITICAL: ALL packages that have Firestore models
#
#  Firestore uses reflection via toObject() to deserialize documents.
#  R8 obfuscates field names (alias→a, uid→b, etc.) in release builds.
#  Without these rules, toObject() returns null/empty objects — profiles
#  invisible, feed empty, profile creation never persists on Play Store.
# ═════════════════════════════════════════════════════════════════════════════

# ── All Veil app model classes — keep everything to be safe ──────────────────
-keep class com.ns.findyourintroverfriend.** { *; }
-keepclassmembers class com.ns.findyourintroverfriend.** { *; }

# ── Google Sign-In / Credential Manager ─────────────────────────────────────
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.android.gms.auth.**

# ── Prevent stripping enum values ────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Serializable classes ──────────────────────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}