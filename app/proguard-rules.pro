# ─────────────────────────────────────────────────────────────────────────────
# Veil App — ProGuard / R8 Rules  (optimised for Google Play DEX score)
#
# ROOT CAUSE of 13% score:
#   The old rule "-keep class com.ns.findyourintroverfriend.** { *; }"
#   blocked R8 from touching ANY of our own code.
#   Removed and replaced with surgical keeps below.
# ─────────────────────────────────────────────────────────────────────────────

# ── Keep crash-friendly stack traces ─────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Annotations / Signatures (needed by Kotlin reflection, Firestore, Gson) ──
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions

# ─────────────────────────────────────────────────────────────────────────────
#  FIRESTORE DATA MODELS  ← THE ONLY THINGS that must keep their field names.
#  Firestore.toObject() uses reflection to map document fields → Kotlin fields.
#  R8 would rename alias→a, uid→b etc. and all objects would come back empty.
#
#  Rule: keep ONLY the data/model classes + their members.
#  Everything else (screens, ViewModels, utils) is free to be shrunk/obfuscated.
# ─────────────────────────────────────────────────────────────────────────────

# Feed models
-keep class com.ns.findyourintroverfriend.feed.VeilPost { *; }
-keep class com.ns.findyourintroverfriend.feed.VeilComment { *; }
-keep class com.ns.findyourintroverfriend.feed.VeilReply { *; }

# Swipe / match models
-keepclassmembers class com.ns.findyourintroverfriend.swipe.** {
    <fields>;
    <init>(...);
}

# Profile / onboarding models
-keepclassmembers class com.ns.findyourintroverfriend.profile.** {
    <fields>;
    <init>(...);
}

# Home / match models
-keepclassmembers class com.ns.findyourintroverfriend.home.** {
    <fields>;
    <init>(...);
}

# Auth models
-keepclassmembers class com.ns.findyourintroverfriend.auth.** {
    <fields>;
    <init>(...);
}

# Any remaining data classes that Firestore deserialises
-keepclassmembers class com.ns.findyourintroverfriend.** {
    # Only keep no-arg constructors and fields — do NOT keep method names
    <init>();
    <fields>;
}

# ── Firebase / GMS ────────────────────────────────────────────────────────────
# Keep the Firebase SDK itself (it uses reflection internally)
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Google Sign-In ────────────────────────────────────────────────────────────
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.android.gms.auth.**

# ── OneSignal ─────────────────────────────────────────────────────────────────
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**

# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.** { *; }
-keep class okio.** { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Jetpack Compose ───────────────────────────────────────────────────────────
# R8 knows how to handle Compose; we only need to suppress warnings.
# Do NOT blanket-keep compose classes — that kills shrinking.
-dontwarn androidx.compose.**

# ── ML Kit ────────────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── UCrop ─────────────────────────────────────────────────────────────────────
-keep class com.yalantis.ucrop.** { *; }
-keepclassmembers class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# ── Coil ──────────────────────────────────────────────────────────────────────
# Only keep the ServiceLoader entry point; let R8 shrink the rest.
-keep class coil.ComponentRegistry { *; }
-dontwarn coil.**

# ── LiveKit / WebRTC ──────────────────────────────────────────────────────────
-keep class io.livekit.** { *; }
-keep class livekit.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn io.livekit.**
-dontwarn org.webrtc.**

# ── Enums — required by Java / Android runtime ───────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Serializable ─────────────────────────────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Parcelable ────────────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── R8 full-mode: allow aggressive optimisation ───────────────────────────────
# Silence warnings for libraries that reference missing optional classes
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.**
-dontwarn sun.misc.Unsafe