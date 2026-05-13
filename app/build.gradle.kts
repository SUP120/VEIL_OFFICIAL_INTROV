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
        versionCode = 21
        versionName = "1.10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OneSignal — free push notification platform
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"ba0bfa6d-3103-4b13-86dc-b94e971fb798\"")

        // Cloudinary — read from local.properties (NOT gradle.properties)
        // project.findProperty() only reads gradle.properties — wrong!
        // We must load local.properties explicitly, same as keystoreProps above.
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
        val cloudName    = localProps.getProperty("cloudinary.cloud.name", "")
        val uploadPreset = localProps.getProperty("cloudinary.upload.preset", "")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME",    "\"$cloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$uploadPreset\"")

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

    // ── 16 KB page-size alignment (required by Google Play for API 35+) ──────
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
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
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity") // blocks bots
    implementation("com.google.firebase:firebase-perf-ktx")               // performance monitoring
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
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // Google Play In-App Review
    implementation("com.google.android.play:review-ktx:2.0.1")

    // ── Lottie — for animated illustrations ───────────────────────────────────
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}