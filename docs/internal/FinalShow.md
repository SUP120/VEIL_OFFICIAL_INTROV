 Veil — Full Pre-Publishing Diagnosis
I've done a complete audit across all 49 source files. Here's the condensed verdict:

🔴 Critical Blockers (can't ship without fixing)
#	Issue	File
C1	FCM Legacy API is deprecated — notifications will die silently	

VeilNotifier.kt
C2	isMinifyEnabled = false — APK ships unobfuscated	

build.gradle.kts
C3	FCM Server Key embedded in APK — security hole	BuildConfig
C4	Google Client ID hardcoded in AuthScreen	

AuthScreen.kt
C5	No Firestore Security Rules verified	Firestore console
🟠 High Priority
#	Issue
H1	Ceremony 8 PM gate is client-side only — no server trigger
H2	versionCode = 1 — needs proper versioning setup
H3	App label might still show "WhereIsMyIntroverFriend" not "Veil"
H4	firebase-storage-ktx dependency unused — bloats APK
H5	Blank screen while rememberStartDestination() resolves — needs a splash
H6	Banner timer fires even when leftMembers == 0
🟡 UX Polish (world-class apps do these)
No haptic feedback on any button tap
Onboarding has no skip option
No "X of 7 messages remaining" in Slow Chat
Non-binary gender shows ♂/♀ in WaitingRoom slots
No photo crop before upload
Graceful "just you right now" state when activeMembers == 0
🟢 Store Requirements
Privacy Policy URL needed (required by Google Play)
Data Safety form must be filled
512×512 store icon needed
6–8 screenshots required
Check if "Veil" name is taken on Play Store