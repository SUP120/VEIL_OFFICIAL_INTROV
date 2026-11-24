# Preserve source information for retracing and generic signatures and annotations
# for libraries that read them at runtime.
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,EnclosingMethod,InnerClasses,Exceptions
-renamesourcefileattribute SourceFile

# Firestore's object mapper uses these beans' no-argument constructors, field names,
# accessors and annotations.
-keep,allowoptimization class com.ns.findyourintroverfriend.swipe.SwipeProfile {
    <init>();
    <fields>;
    public *** get*();
    public boolean is*();
}
-keep,allowoptimization class com.ns.findyourintroverfriend.swipe.SwipeMatch {
    <init>();
    <fields>;
    public *** get*();
    public boolean is*();
}
-keep,allowoptimization class com.ns.findyourintroverfriend.swipe.SwipeMessage {
    <init>();
    <fields>;
    public *** get*();
    public boolean is*();
}

# Firebase, OneSignal, ML Kit, Kotlin coroutines, and LiveKit provide consumer
# rules for their own reflective entry points, serialization, and native code.
# The Android Gradle Plugin keeps components declared in the merged manifest.

# Optional JVM TLS providers and annotations referenced by network libraries.
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.**
-dontwarn sun.misc.Unsafe
# Godot's native engine locates Java classes and callbacks by their original names.
-keep class org.godotengine.godot.** { *; }
