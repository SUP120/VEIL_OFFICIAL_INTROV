package com.ns.findyourintroverfriend

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.android.play.core.review.ReviewManagerFactory
import com.ns.findyourintroverfriend.billing.VeilBillingClient
import com.ns.findyourintroverfriend.data.GroupFields
import com.ns.findyourintroverfriend.navigation.VeilNavGraph
import com.ns.findyourintroverfriend.navigation.VeilRoute
import com.ns.findyourintroverfriend.notifications.VeilNotifier
import com.ns.findyourintroverfriend.ui.theme.VeilTheme
import com.ns.findyourintroverfriend.util.BanStatus
import com.ns.findyourintroverfriend.util.StrikeManager
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // ── Crashlytics — enable crash reporting ──────────────────────────────
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        // ── Google Play Billing — init once on app start ────────────────────
        VeilBillingClient.init(this)

        // ── OneSignal: request notification permission (Android 13+) ──────────
        // OneSignal v5 handles the permission dialog natively
        CoroutineScope(Dispatchers.Main).launch {
            OneSignal.Notifications.requestPermission(false)
        }

        // ── Register logged-in user with OneSignal — persist subId to Firestore ──────
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            VeilNotifier.registerAndPersistUser(uid, FirebaseFirestore.getInstance())
            // Flush any FCM token that refreshed before this login (e.g. token rotated while signed out)
            com.ns.findyourintroverfriend.notifications.VeilMessagingService
                .flushPendingTokenIfNeeded(this, uid)
        }

        // ── Read DM deep-link from notification tap intent ────────────────────
        // When the user taps a "dm_message" notification, VeilMessagingService puts
        // EXTRA_OPEN_DM=true + EXTRA_MATCH_ID into the intent. We capture it here
        // and pass it into setContent so the nav graph can navigate immediately.
        val pendingDmMatchId: String? =
            if (intent?.getBooleanExtra(
                com.ns.findyourintroverfriend.notifications.VeilMessagingService.EXTRA_OPEN_DM, false) == true)
                intent.getStringExtra(
                    com.ns.findyourintroverfriend.notifications.VeilMessagingService.EXTRA_MATCH_ID)
            else null

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            VeilTheme {
                val startDest = rememberStartDestination(pendingDmMatchId)
                if (startDest != null) {
                    val navController = rememberNavController()

                    // ── Double-back-press to exit ──────────────────────────────
                    // On root screens (Home, Auth), first back press shows a toast.
                    // A second press within 2 seconds exits the app.
                    // On any other screen, back navigates normally.
                    val currentEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentEntry?.destination?.route
                    val rootRoutes   = setOf(VeilRoute.VEIL_HOME, VeilRoute.AUTH)

                    var backPressedOnce by remember { mutableStateOf(false) }

                    // Reset the flag after 2 seconds of inactivity
                    LaunchedEffect(backPressedOnce) {
                        if (backPressedOnce) {
                            delay(2000L)
                            backPressedOnce = false
                        }
                    }

                    androidx.activity.compose.BackHandler(enabled = true) {
                        when {
                            // On a non-root screen → just go back normally
                            currentRoute !in rootRoutes && navController.previousBackStackEntry != null -> {
                                navController.popBackStack()
                            }
                            // On root screen — double press to exit
                            backPressedOnce -> {
                                // Second press within 2s → exit
                                this@MainActivity.finish()
                            }
                            else -> {
                                // First press on root → show toast, arm the flag
                                backPressedOnce = true
                                Toast.makeText(
                                    this@MainActivity,
                                    "Press back again to exit Veil",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    VeilNavGraph(
                        navController    = navController,
                        startDestination = startDest
                    )
                } else {
                    // H5: Show branded splash while destination resolves (no more blank white flash)
                    VeilSplashLoader()
                }
            }
        }
    }
}

@Composable
private fun VeilSplashLoader() {
    // Pure-Compose animated splash — no Lottie / asset dependency.
    // Shows a pulsing VEIL wordmark on white, renders immediately.

    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    // Subtle breathing scale on the logo
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue  = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    // Fade the tagline in after a short beat
    var tagVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        tagVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            // Animated wordmark
            Text(
                "VEIL",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color         = Color(0xFF111111),
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 12.sp
                ),
                modifier = Modifier.scale(logoScale)
            )

            Spacer(Modifier.height(10.dp))

            // Tagline fades in
            androidx.compose.animation.AnimatedVisibility(
                visible = tagVisible,
                enter   = androidx.compose.animation.fadeIn(
                    animationSpec = tween(500)
                ) + androidx.compose.animation.slideInVertically(
                    animationSpec = tween(400),
                    initialOffsetY = { it / 3 }
                )
            ) {
                Text(
                    "where introverts connect",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color         = Color(0xFF888888),
                        letterSpacing = 1.5.sp
                    )
                )
            }

            Spacer(Modifier.height(40.dp))

            // Small dot loader
            androidx.compose.animation.AnimatedVisibility(
                visible = tagVisible,
                enter   = androidx.compose.animation.fadeIn(tween(600, delayMillis = 200))
            ) {
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    repeat(3) { i ->
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue  = 1f,
                            animationSpec = infiniteRepeatable(
                                tween(600, delayMillis = i * 180, easing = FastOutSlowInEasing),
                                RepeatMode.Reverse
                            ),
                            label = "dot$i"
                        )
                        Box(
                            Modifier
                                .size(6.dp)
                                .scale(dotAlpha)
                                .background(
                                    Color(0xFF333333).copy(alpha = dotAlpha),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Smart start destination resolver
//  Priority (in order):
//    1. No auth / no profile   → AUTH / PROFILE_SETUP
//    2. Has partySessionId     → PARTY/{id}         (winner already on island)
//    3. Has active groupId     → ISLAND_HOME         (Day 1/2/3 playing area)
//       └─ groupDoc.ceremonyDone=true → clears groupId, falls through to step 5
//    4. In matchmaking queue   → WAITING_ROOM        (was searching when closed)
//    5. In lobby room          → LOBBY_WAITING/{id}  (pre-match room still open)
//    6. Otherwise              → VEIL_HOME           (home, after ceremony, fresh)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun rememberStartDestination(pendingDmMatchId: String? = null): String? {
    val auth = FirebaseAuth.getInstance()
    val db   = FirebaseFirestore.getInstance()

    var startDest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // ── Minimum splash timer ─────────────────────────────────────────────────────────
        // 800ms is enough to show the logo — feels instant on fast networks.
        val minTimer = async { delay(800L) }

        val user = auth.currentUser
        if (user == null) {
            minTimer.await()
            startDest = VeilRoute.AUTH
            return@LaunchedEffect
        }

        // ── DM deep-link: notification tap → go straight to the chat ─────────
        // Must come before any other state check — user tapped a specific chat.
        if (!pendingDmMatchId.isNullOrBlank()) {
            minTimer.await()
            startDest = "${VeilRoute.CONNECTION_ROOM}/$pendingDmMatchId"
            return@LaunchedEffect
        }

        //    All accounts (including admin) go through normal flow.
        //    Admin dashboard is accessible via Dev Panel from VeilHome.

        try {
            val doc = db.collection("users").document(user.uid).get().await()

            // Not profiled yet
            if (!doc.exists() || doc.getBoolean("profileComplete") != true) {
                startDest = VeilRoute.PROFILE_SETUP
                return@LaunchedEffect
            }

            // ── 🔒 Ban enforcement on cold start ──────────────────────────────────
            //  StrikeManager.checkBanStatus() auto-lifts expired temp bans too.
            val banStatus = StrikeManager.checkBanStatus(user.uid)
            if (banStatus is BanStatus.PermaBanned || banStatus is BanStatus.TempBanned) {
                // Redirect to AUTH — user will see Google sign-in, but on screen
                // open the SlowChatScreen/QuestionScreen BanWall will catch them.
                // For now send them back to auth so they can't access anything.
                startDest = VeilRoute.AUTH
                return@LaunchedEffect
            }
            // ─────────────────────────────────────────────────────────────

            // 1. Active private island (party) session?
            val partySession = doc.getString("partySessionId")
            if (!partySession.isNullOrBlank()) {
                val sessionDoc = db.collection("party_sessions").document(partySession).get().await()
                val leftBy = sessionDoc.getString("leftBy") ?: ""
                if (leftBy == user.uid) {
                    db.collection("users").document(user.uid)
                        .update("partySessionId", com.google.firebase.firestore.FieldValue.delete()).await()
                } else {
                    startDest = "${VeilRoute.PARTY}/$partySession"
                    return@LaunchedEffect
                }
            }

            // 1b. Ceremony in progress? (pendingCeremony set but groupId already deleted)
            //     This covers the case where a user updated the app mid-ceremony, or
            //     the app was killed exactly after RevealVM wrote pendingCeremony but
            //     before bond calculation finished.
            val pendingCeremonyGroupId = doc.getString("pendingCeremony")
            if (!pendingCeremonyGroupId.isNullOrBlank()) {
                // Route them back to the ceremony so bond is computed / re-shown.
                startDest = VeilRoute.CEREMONY
                return@LaunchedEffect
            }

            // 2. In an active Day 1–3 group?
            val groupId = doc.getString("groupId")
            if (!groupId.isNullOrBlank()) {
                // C6: verify the group doc actually exists (guard against corrupt state)
                val groupDoc = try {
                    db.collection("groups").document(groupId).get().await()
                } catch (e: Exception) {
                    // C6: Firestore offline — trust the local groupId, go to Island
                    startDest = VeilRoute.ISLAND_HOME
                    return@LaunchedEffect
                }

                if (!groupDoc.exists()) {
                    // C6: Group doc deleted (admin cleanup) — clear stale groupId
                    db.collection("users").document(user.uid)
                        .update("groupId", com.google.firebase.firestore.FieldValue.delete()).await()
                    // Fall through to WAITING_ROOM
                } else {
                    val ceremonyDone = groupDoc.getBoolean(GroupFields.CEREMONY_DONE) ?: false
                    if (ceremonyDone) {
                        // C2: Ceremony complete — user never completed it themselves.
                        // Route them to CEREMONY so they can see the results.
                        // RevealCeremonyViewModel will re-compute from the group data.
                        startDest = VeilRoute.CEREMONY
                        return@LaunchedEffect
                    } else {
                        // C2: Still in active journey — restore to Island Home
                        startDest = VeilRoute.ISLAND_HOME
                        return@LaunchedEffect
                    }
                }
            }

            // 3. Still actively searching in the matchmaking queue?
            //    (WaitingRoomViewModel writes matchmaking_queue/{uid} with status="waiting")
            val queueDoc = try {
                db.collection("matchmaking_queue").document(user.uid).get().await()
            } catch (e: Exception) { null }
            if (queueDoc != null && queueDoc.exists()) {
                val qStatus     = queueDoc.getString("status") ?: ""
                val isBackground = queueDoc.getBoolean("backgroundMode") ?: false
                when {
                    qStatus != "waiting" -> {
                        // Matched / stale — clean up
                        try { db.collection("matchmaking_queue").document(user.uid).delete().await() }
                        catch (_: Exception) {}
                    }
                    isBackground -> {
                        // FIX #1: User tapped "Notify me" — go to VEIL_HOME.
                        // The queue banner on VeilHomeScreen shows their status.
                        // FCM notification will wake them when the circle forms.
                        startDest = VeilRoute.VEIL_HOME
                        return@LaunchedEffect
                    }
                    else -> {
                        // User was mid-search in the waiting room — bring them back
                        startDest = VeilRoute.WAITING_ROOM
                        return@LaunchedEffect
                    }
                }
            }

            // 4. Still waiting inside a lobby room?
            val lobbyRoomId = doc.getString("lobbyRoomId")
            if (!lobbyRoomId.isNullOrBlank()) {
                val roomDoc = db.collection("lobby_rooms").document(lobbyRoomId).get().await()
                val roomStatus = roomDoc.getString("status") ?: "waiting"
                when {
                    roomStatus == "started" -> {
                        // Room already launched — clear stale lobbyRoomId and fall through
                        db.collection("users").document(user.uid)
                            .update("lobbyRoomId", com.google.firebase.firestore.FieldValue.delete()).await()
                    }
                    roomStatus == "abandoned" || !roomDoc.exists() -> {
                        // Room gone — clear it
                        db.collection("users").document(user.uid)
                            .update("lobbyRoomId", com.google.firebase.firestore.FieldValue.delete()).await()
                    }
                    else -> {
                        // Room still open — take user back to lobby waiting screen
                        startDest = "${VeilRoute.LOBBY_WAITING}/$lobbyRoomId"
                        return@LaunchedEffect
                    }
                }
            }

            // 5. Nothing active — VeilHome.
            //    This covers:
            //      • User closed app from Veil Home
            //      • User completed ceremony and chose "Return to Veil" or "Find New Room"
            //        (groupId was cleared by RevealCeremonyViewModel; Find New Room will
            //         start fresh matchmaking from VeilHome → WaitingRoom)
            startDest = VeilRoute.VEIL_HOME

        } catch (e: Exception) {
            startDest = VeilRoute.AUTH
        }

        // Always wait for the minimum splash timer before navigating
        minTimer.await()
    }

    return startDest
}
