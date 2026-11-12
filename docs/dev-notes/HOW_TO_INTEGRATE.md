# 🏝️ Island Home Screen — Integration Guide

## What's in this folder

```
HomePageFiles/
├── kotlin/
│   └── IslandHomeScreen.kt      ← The complete screen composable
└── drawables/
    ├── island_base.png
    ├── sky_day.png
    ├── sky_night.png
    ├── cloud_1.png
    ├── cloud_2.png
    ├── bird.png
    └── glow.jpg
```

---

## Step 1 — Copy the drawable assets

Copy **all files** from the `drawables/` folder into your target project's:

```
app/src/main/res/drawable/
```

---

## Step 2 — Copy the Kotlin file

Copy `kotlin/IslandHomeScreen.kt` into your target project's source folder, e.g.:

```
app/src/main/java/com/yourpackage/
```

Then **update the package declaration** at the top of the file to match your app:

```kotlin
// Change this line:
package com.ns.screen

// To your package, e.g.:
package com.yourcompany.yourapp
```

---

## Step 3 — Required dependencies

Make sure your target project's `build.gradle.kts` has at least:

```kotlin
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.foundation:foundation")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.runtime:runtime")
implementation("androidx.activity:activity-compose")
implementation("androidx.core:core-ktx")
```

> No third-party libraries are needed. The screen uses only Jetpack Compose standard APIs.

---

## Step 4 — Launch the screen

In your `MainActivity.kt` (or any entry point), call:

```kotlin
setContent {
    IslandHomeScreen()
}
```

For best results, also add in MainActivity.onCreate():

```kotlin
enableEdgeToEdge()
WindowCompat.setDecorFitsSystemWindows(window, false)
val controller = WindowInsetsControllerCompat(window, window.decorView)
controller.hide(WindowInsetsCompat.Type.systemBars())
controller.systemBarsBehavior =
    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
```

---

## What the screen contains (layers, bottom → top)

| Layer | Description |
|-------|-------------|
| Sky | Night base + Day cross-fade (togglable) |
| Sky veil | Subtle desaturation overlay |
| Island | Floating with a slow 6.5s/20dp sine-like motion |
| Clouds | 2 clouds drifting at different speeds |
| Glow | Canvas radial gradient pulse (no image) |
| Bird | Rare fly-across (30–75s intervals), fades in/out |
| Avatars | 4 compass-position rings (N/S/E/W) |
| Toggle | 🌙/☀️ day-night debug button (top-right) |

---

## Notes

- The screen is **visual-only** — no logic, no navigation, no authentication.
- Animations are **intentionally async** so nothing loops visibly.
- The `glow.jpg` file is in drawables but **not used** by the screen (replaced with a Canvas gradient). You can omit it.
- Minimum SDK: **API 28** (Android 9)
