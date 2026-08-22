import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")   // performance monitoring gradle plugin
}

android {
    namespace = "com.ns.findyourintroverfriend"
    compileSdk = 36

    // ── Release signing — reads from keystore.properties (not committed to git) ──
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties()
    if (keystorePropsFile.exists()) keystoreProps.load(keystorePropsFile.inputStream())

    signingConfigs {
        create("release") {
            storeFile     = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias      = keystoreProps.getProperty("keyAlias")
            keyPassword   = keystoreProps.getProperty("keyPassword")
        }
    }

    defaultConfig {
        applicationId = "com.ns.findyourintroverfriend"
        minSdk = 28
        targetSdk = 36
        versionCode = 32
        versionName = "1.17.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OneSignal — free push notification platform
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"ba0bfa6d-3103-4b13-86dc-b94e971fb798\"")

        // Cloudinary — read from local.properties (NOT gradle.properties)
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
        val cloudName    = localProps.getProperty("cloudinary.cloud.name", "")
        val uploadPreset = localProps.getProperty("cloudinary.upload.preset", "")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME",    "\"$cloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$uploadPreset\"")

        // LiveKit — Voice Rooms (host URL safe to ship in APK)
        val livekitHost     = localProps.getProperty("livekit.host", "")
        val livekitTokenUrl = localProps.getProperty("livekit.token.url", "")
        buildConfigField("String", "LIVEKIT_HOST",      "\"$livekitHost\"")
        buildConfigField("String", "LIVEKIT_TOKEN_URL", "\"$livekitTokenUrl\"")



    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Disable Crashlytics mapping upload during Gradle build.
            // Prevents UnknownHostException on firebasecrashlyticssymbols.googleapis.com.
            // Crashlytics still works at runtime; upload mapping manually if needed.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose      = true
        buildConfig  = true
    }

    // ── 16 KB page-size compliance (REQUIRED for Google Play targeting API 35+) ──────────────
    // .so files MUST be stored uncompressed (useLegacyPackaging = false) so Android
    // can mmap them directly at 16 KB page boundaries.
    // Setting = true (compressed) causes the Play Console 16 KB error — do NOT change.
    // Size impact is kept low because we use voice-sdk (~10 MB) not full-sdk (~28 MB),
    // and bundle ABI splits mean each device downloads only its own ABI.
    packaging {
        jniLibs {
            useLegacyPackaging = false   // ← required for 16 KB page alignment
        }
        // Strip useless metadata from the AAB
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "DebugProbesKt.bin"
            )
        }
    }

    // ── Bundle splits: Play delivers only what each device needs ────────────────
    bundle {
        language { enableSplit = true }   // only install user’s languages
        density  { enableSplit = true }   // only install matching screen density
        abi      { enableSplit = true }   // only install matching ABI
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ── WorkManager — background queue heartbeat ───────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ── Google Fonts (Nunito — the Veil typeface) ─────────────────────────────
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.8")

    // ── Material Icons Extended ───────────────────────────────────────────────
    implementation("androidx.compose.material:material-icons-extended")

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.7")

    // ── ViewModel + Compose integration ───────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // ── Firebase BOM (version-managed) ────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")          // image uploads
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity") // blocks bots
    implementation("com.google.firebase:firebase-perf-ktx")               // performance monitoring
    implementation("com.google.firebase:firebase-messaging-ktx")          // FCM (for notifications)
    implementation("com.google.firebase:firebase-functions-ktx")          // Callable Cloud Functions

    // FCM: OneSignal SDK handles its own FCM registration internally

    // ── Google Sign-In ────────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ── Coil — async image loading for profile photos ─────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // ── OkHttp — for OneSignal REST API calls from admin ──────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ── OneSignal — free push notifications ───────────────────────────────────
    implementation("com.onesignal:OneSignal:5.1.6")

    // ── ML Kit Face Detection — on-device, free, for photo validation ─────────
    implementation("com.google.mlkit:face-detection:16.1.7")

    // ── ML Kit Image Labeling — on-device content moderation for Aesthetics Feed
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // ── ExifInterface — for reading photo orientation metadata ────────────────
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Google Play 16 KB compliant circular image cropper
    implementation("com.github.yalantis:ucrop:2.2.8")

    // ── Google Play Billing ───────────────────────────────────────────────────
    // ⚠ Min required by Google Play: 8.0.0 (enforced Aug 31 2026).
    // Using 8.0.0 — compatible with this project's Kotlin 2.0.21.
    // (9.1.0 requires Kotlin 2.3.0 which causes build failure here)
    implementation("com.android.billingclient:billing-ktx:8.0.0")

    // Google Play In-App Review
    implementation("com.google.android.play:review-ktx:2.0.1")

    // ── Lottie — for animated illustrations ───────────────────────────────────────
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // ── LiveKit — Voice Rooms real-time audio (FREE, no credit card) ──────────
    implementation("io.livekit:livekit-android:2.9.0")
    implementation("io.livekit:livekit-android-compose-components:1.2.0")


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}