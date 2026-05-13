package com.ns.findyourintroverfriend.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ns.findyourintroverfriend.answer.AnswerCardScreen
import com.ns.findyourintroverfriend.auth.AuthScreen
import com.ns.findyourintroverfriend.auth.GameIntroScreen
import com.ns.findyourintroverfriend.chat.SlowChatScreen
import com.ns.findyourintroverfriend.payment.ChatUnlockScreen   // Coming Soon placeholder
import com.ns.findyourintroverfriend.payment.ChatCreditsScreen  // Coming Soon placeholder
import com.ns.findyourintroverfriend.dev.DevInspectorScreen
import com.ns.findyourintroverfriend.game.GameInviteScreen
import com.ns.findyourintroverfriend.game.TicTacToeScreen
import com.ns.findyourintroverfriend.home.IslandHomeScreen
import com.ns.findyourintroverfriend.home.VeilHomeScreen
import com.ns.findyourintroverfriend.inbox.InboxScreen
import com.ns.findyourintroverfriend.lobby.LobbyWaitingScreen
import com.ns.findyourintroverfriend.lobby.RoomLobbyScreen
import com.ns.findyourintroverfriend.myth.TrueOrMythScreen
import com.ns.findyourintroverfriend.myth.TrueOrMythSetupScreen
import com.ns.findyourintroverfriend.party.PartyIslandScreen
import com.ns.findyourintroverfriend.poll.WouldYouRatherScreen
import com.ns.findyourintroverfriend.profile.ProfileSetupScreen
import com.ns.findyourintroverfriend.question.MyQuestionsInboxScreen
import com.ns.findyourintroverfriend.question.QuestionCardScreen
import com.ns.findyourintroverfriend.reveal.RevealCeremonyScreen
import com.ns.findyourintroverfriend.waiting.WaitingRoomScreen
import com.ns.findyourintroverfriend.admin.AdminDashboardScreen
import com.ns.findyourintroverfriend.aesthetics.AestheticsFeedScreen
import com.ns.findyourintroverfriend.vibes.VibesScreen
import com.ns.findyourintroverfriend.day2.DareToShareScreen
import com.ns.findyourintroverfriend.day2.SecretComplimentScreen
import com.ns.findyourintroverfriend.founders.FoundersScreen
import com.ns.findyourintroverfriend.BuildConfig
import com.ns.findyourintroverfriend.swipe.ConnectionRoomScreen
import com.ns.findyourintroverfriend.swipe.MatchesScreen
import com.ns.findyourintroverfriend.swipe.SwipeProfileCreationScreen
import com.ns.findyourintroverfriend.swipe.SwipeScreen
import com.ns.findyourintroverfriend.feed.FeedUserProfileScreen
import com.ns.findyourintroverfriend.listening.ListeningRoomScreen
import com.ns.findyourintroverfriend.listening.ListeningLobbyScreen
import com.ns.findyourintroverfriend.invite.InviteFriendsScreen


@Composable
fun VeilNavGraph(navController: NavHostController, startDestination: String) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        // ── Global transitions — snappy 200ms feel, no layout jank ───────────────
        enterTransition  = {
            fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 18 }
        },
        exitTransition   = {
            fadeOut(tween(160))
        },
        popEnterTransition = {
            fadeIn(tween(180))
        },
        popExitTransition = {
            fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 18 }
        }
    ) {

        // ── Auth ──────────────────────────────────────────────────────────────
        composable(VeilRoute.AUTH) {
            AuthScreen(
                onAuthSuccess = { needsProfile ->
                    if (needsProfile) {
                        navController.navigate(VeilRoute.PROFILE_SETUP) {
                            popUpTo(VeilRoute.AUTH) { inclusive = true }
                        }
                    } else {
                        // All users (including admin + dev accounts) land on VeilHome first.
                        // Admin dashboard: accessible via long-press on alias (dev panel)
                        // or the existing 5-tap easter egg on IslandHome.
                        navController.navigate(VeilRoute.VEIL_HOME) {
                            popUpTo(VeilRoute.AUTH) { inclusive = true }
                        }
                    }
                },
                onOpenDevDash = {
                    navController.navigate(VeilRoute.ADMIN_DASHBOARD) {
                        popUpTo(VeilRoute.AUTH) { inclusive = true }
                    }
                }
            )
        }

        // ── Profile setup ────────────────────────────────────────────────────────────────
        composable(VeilRoute.PROFILE_SETUP) {
            ProfileSetupScreen(
                onSetupComplete = {
                    // Skip the old 3-Day Game walkthrough — go straight to Veil Home
                    navController.navigate(VeilRoute.VEIL_HOME) {
                        popUpTo(VeilRoute.PROFILE_SETUP) { inclusive = true }
                    }
                }
            )
        }

        // ── Game Intro (one-time onboarding after profile setup) ──────────────────────
        composable(VeilRoute.GAME_INTRO) {
            GameIntroScreen(
                onComplete = {
                    // After intro → VeilHome, not queue. User taps "Find a room" to begin.
                    navController.navigate(VeilRoute.VEIL_HOME) {
                        popUpTo(VeilRoute.GAME_INTRO) { inclusive = true }
                    }
                }
            )
        }

        // ── Waiting Room — auto-assigns 2M + 2F (original flow) ───────────────
        composable(VeilRoute.WAITING_ROOM) {
            WaitingRoomScreen(
                onGroupFound = { groupId ->
                    navController.navigate(VeilRoute.ISLAND_HOME) {
                        popUpTo(VeilRoute.WAITING_ROOM) { inclusive = true }
                    }
                },
                onLeave = {
                    navController.navigate(VeilRoute.VEIL_HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Room Lobby (kept for build compatibility — not used in main flow) ──
        composable(VeilRoute.ROOM_LOBBY) {
            RoomLobbyScreen(
                onEnterRoom = { roomId ->
                    navController.navigate("${VeilRoute.LOBBY_WAITING}/$roomId") {
                        popUpTo(VeilRoute.ROOM_LOBBY) { inclusive = false }
                    }
                }
            )
        }

        // ── Lobby Waiting (kept for build compatibility — not used in main flow) ─
        composable(
            route     = "${VeilRoute.LOBBY_WAITING}/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { back ->
            val roomId = back.arguments?.getString("roomId") ?: ""
            LobbyWaitingScreen(
                roomId       = roomId,
                onGroupReady = { _ ->
                    navController.navigate(VeilRoute.ISLAND_HOME) {
                        popUpTo(VeilRoute.ROOM_LOBBY) { inclusive = true }
                    }
                },
                onLeaveRoom  = {
                    navController.navigate(VeilRoute.WAITING_ROOM) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Island Home ───────────────────────────────────────────────────────
        composable(VeilRoute.ISLAND_HOME) {
            IslandHomeScreen(
                onOpenQuestions      = { navController.navigate(VeilRoute.QUESTION_CARD) },
                onOpenAnswers        = { navController.navigate(VeilRoute.ANSWER_CARD) },
                onOpenInbox          = { navController.navigate(VeilRoute.INBOX) },
                onOpenCeremony       = { navController.navigate(VeilRoute.CEREMONY) },
                onOpenGameInvite     = { navController.navigate("${VeilRoute.GAME_INVITE}/island_game/Partner") },
                onOpenWouldYouRather = { navController.navigate(VeilRoute.WOULD_YOU_RATHER) },
                onOpenTrueOrMyth     = { navController.navigate(VeilRoute.TRUE_OR_MYTH) },
                onOpenTomSetup       = { navController.navigate(VeilRoute.TRUE_OR_MYTH_SETUP) },
                onOpenSlowChat       = { navController.navigate(VeilRoute.SLOW_CHAT) },
                onOpenMyQInbox       = { navController.navigate(VeilRoute.MY_QUESTIONS_INBOX) },
                onOpenAestheticsFeed    = { navController.navigate(VeilRoute.AESTHETICS_FEED) },
                onOpenVibes             = { navController.navigate(VeilRoute.VIBES) },
                onOpenDareToShare       = { navController.navigate(VeilRoute.DARE_TO_SHARE) },
                onOpenSecretCompliment  = { navController.navigate(VeilRoute.SECRET_COMPLIMENT) },
                onReturnToWaiting    = {
                    navController.navigate(VeilRoute.WAITING_ROOM) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLeaveRoom          = {
                    navController.navigate(VeilRoute.VEIL_HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenPartyIsland    = { sessionId ->
                    navController.navigate("${VeilRoute.PARTY}/$sessionId")
                },
                onOpenDevInspector   = {
                    if (BuildConfig.DEBUG) navController.navigate(VeilRoute.DEV_INSPECTOR)
                },
                onOpenAdminDashboard = {
                    val email = com.google.firebase.auth.FirebaseAuth.getInstance()
                        .currentUser?.email?.lowercase()
                    if (email == "supriyamkumar098@gmail.com") {
                        navController.navigate(VeilRoute.ADMIN_DASHBOARD)
                    }
                }
            )
        }

        // ── Veil Home — leave room / find new room ───────────────────────────
        composable(VeilRoute.VEIL_HOME) {
            VeilHomeScreen(
                onFindRoom = {
                    navController.navigate(VeilRoute.WAITING_ROOM) {
                        popUpTo(VeilRoute.VEIL_HOME) { inclusive = true }
                    }
                },
                onOpenSwipe = {},
                onOpenMatch = { matchId ->
                    navController.navigate("${VeilRoute.CONNECTION_ROOM}/$matchId")
                },
                onNavigateToMatches = {
                    navController.navigate(VeilRoute.MATCHES_SCREEN)
                },
                onViewProfile = {
                    navController.navigate(VeilRoute.PROFILE_SETUP)
                },
                onViewRooms = {
                    navController.navigate(VeilRoute.ACTIVE_ROOMS)
                },
                onOpenFounders = {
                    navController.navigate(VeilRoute.INTROVERT_FEED)
                },
                onOpenDevPanel = {
                    navController.navigate(VeilRoute.DEV_INSPECTOR)
                },
                onOpenProfileThemes = {
                    navController.navigate(VeilRoute.PROFILE_THEMES)
                },
                onOpenDms = {
                    navController.navigate(VeilRoute.FEED_DM_INBOX)
                },
                onOpenCookieStore = {
                    navController.navigate(VeilRoute.COOKIE_STORE)
                },
                onOpenUserProfile = { uid ->
                    navController.navigate("user_profile/$uid")
                },
                onOpenListeningRoom = {
                    navController.navigate(VeilRoute.LISTENING_LOBBY)
                },
                onOpenInvite = {
                    navController.navigate(VeilRoute.INVITE_FRIENDS)
                },
                onLogout = {
                    com.ns.findyourintroverfriend.notifications.VeilNotifier.unregisterUser()
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    navController.navigate(VeilRoute.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Founders — early believers page ─────────────────────────────
        composable(VeilRoute.FOUNDERS) {
            FoundersScreen(onBack = { navController.popBackStack() })
        }

        composable(VeilRoute.FEED) {
            com.ns.findyourintroverfriend.feed.FeedScreen(
                onOpenDms = { navController.navigate(VeilRoute.FEED_DM_INBOX) }
            )
        }

        // ── Feed DM Inbox — matches inbox accessible from feed ────────────────
        composable(VeilRoute.FEED_DM_INBOX) {
            com.ns.findyourintroverfriend.feed.FeedDmInboxScreen(
                onBack     = { navController.popBackStack() },
                onOpenChat = { matchId ->
                    navController.navigate("${VeilRoute.CONNECTION_ROOM}/$matchId")
                },
                onOpenProfile = { uid ->
                    navController.navigate("user_profile/$uid")
                }
            )
        }

        // ── Cookie Store ──────────────────────────────────────────────────────
        composable(VeilRoute.COOKIE_STORE) {
            com.ns.findyourintroverfriend.billing.CookieStoreScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── User Profile — public profile viewer ─────────────────────────────
        composable(
            route     = VeilRoute.USER_PROFILE,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { back ->
            val uid = back.arguments?.getString("uid") ?: ""
            FeedUserProfileScreen(
                targetUid        = uid,
                onBack           = { navController.popBackStack() },
                onOpenCookieStore = { navController.navigate(VeilRoute.COOKIE_STORE) }
            )
        }

        // ── Active Rooms — live viewer ─────────────────────────────────────────
        composable(VeilRoute.ACTIVE_ROOMS) {
            com.ns.findyourintroverfriend.home.ActiveRoomsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Question Card ─────────────────────────────────────────────────────
        composable(VeilRoute.QUESTION_CARD) {
            QuestionCardScreen(
                onBack      = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() }
            )
        }

        // ── Answer Card ───────────────────────────────────────────────────────
        composable(VeilRoute.ANSWER_CARD) {
            AnswerCardScreen(onBack = { navController.popBackStack() })
        }

        // ── My Questions Inbox ────────────────────────────────────────────────
        composable(VeilRoute.MY_QUESTIONS_INBOX) {
            MyQuestionsInboxScreen(onBack = { navController.popBackStack() })
        }

        // ── Inbox ─────────────────────────────────────────────────────────────
        composable(VeilRoute.INBOX) {
            InboxScreen(onBack = { navController.popBackStack() })
        }

        // ── Would You Rather ──────────────────────────────────────────────────
        composable(VeilRoute.WOULD_YOU_RATHER) {
            WouldYouRatherScreen(onBack = { navController.popBackStack() })
        }

        // ── True or Myth — guess screen (Day 3) ───────────────────────────────
        composable(VeilRoute.TRUE_OR_MYTH) {
            TrueOrMythScreen(
                onBack      = { navController.popBackStack() },
                onOpenSetup = { navController.navigate(VeilRoute.TRUE_OR_MYTH_SETUP) }
            )
        }

        // ── True or Myth — setup: submit your own fact (Day 3 prompt) ─────────
        composable(VeilRoute.TRUE_OR_MYTH_SETUP) {
            TrueOrMythSetupScreen(onDone = { navController.popBackStack() })
        }

        // ── Sync Game — real-time compatibility game ──────────────────────────
        composable(
            route     = "${VeilRoute.GAME_INVITE}/{matchId}/{partnerAlias}",
            arguments = listOf(
                navArgument("matchId")      { type = NavType.StringType; defaultValue = "default" },
                navArgument("partnerAlias") { type = NavType.StringType; defaultValue = "Partner" }
            )
        ) { back ->
            val matchId      = back.arguments?.getString("matchId")      ?: "default"
            val partnerAlias = back.arguments?.getString("partnerAlias") ?: "Partner"
            com.ns.findyourintroverfriend.game.SyncGameScreen(
                matchId      = matchId,
                partnerAlias = partnerAlias,
                onBack       = { navController.popBackStack() }
            )
        }

        // ── Legacy Tic-Tac-Toe stub (kept for build compat) ──────────────────
        composable(
            route     = "${VeilRoute.TIC_TAC_TOE}/{gameId}",
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId") ?: ""
            TicTacToeScreen(
                gameId = gameId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Reveal Ceremony ───────────────────────────────────────────────────
        composable(VeilRoute.CEREMONY) {
            RevealCeremonyScreen(
                onGoHome = {
                    // Non-winner chose "Return to the Veil" — go home
                    navController.navigate(VeilRoute.VEIL_HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onFindNewRoom = {
                    // Non-winner chose "Join a New Circle" — start matchmaking again
                    navController.navigate(VeilRoute.WAITING_ROOM) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onGoToParty = { sessionId ->
                    // Highest satisfaction moment - request Play Store review
                    (navController.context as? android.app.Activity)?.let { act ->
                        com.ns.findyourintroverfriend.util.InAppReviewHelper.requestReview(act)
                    }
                    // Winner entering the island — pop CEREMONY so back → IslandHome
                    navController.navigate("${VeilRoute.PARTY}/$sessionId") {
                        popUpTo(VeilRoute.CEREMONY) { inclusive = true }
                    }
                }
            )
        }

        // ── Party Island ──────────────────────────────────────────────────────
        composable(
            route     = "${VeilRoute.PARTY}/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            PartyIslandScreen(
                sessionId    = sessionId,
                onStartFresh = {
                    navController.navigate(VeilRoute.VEIL_HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Aesthetics Feed (all days) ────────────────────────────────────
        composable(VeilRoute.AESTHETICS_FEED) {
            AestheticsFeedScreen(onBack = { navController.popBackStack() })
        }

        // ── Circle Vibes — Song / Color / One Word Bio (all days) ───────────
        composable(VeilRoute.VIBES) {
            VibesScreen(onBack = { navController.popBackStack() })
        }

        // ── Dare to Share (Day 2+) ──────────────────────────────────────────
        composable(VeilRoute.DARE_TO_SHARE) {
            DareToShareScreen(onBack = { navController.popBackStack() })
        }

        // ── Secret Compliment (Day 2+) ────────────────────────────────────
        composable(VeilRoute.SECRET_COMPLIMENT) {
            SecretComplimentScreen(onBack = { navController.popBackStack() })
        }

        // ── Dev Inspector ─────────────────────────────────────────────────────
        composable(VeilRoute.DEV_INSPECTOR) {
            DevInspectorScreen(
                onBack     = { navController.popBackStack() },
                onNavigate = { route ->
                    // Handle routes that need arguments — inject a placeholder
                    val resolved = when (route) {
                        "party"      -> "${VeilRoute.PARTY}/dev_test_session"
                        "tic_tac_toe"-> "${VeilRoute.TIC_TAC_TOE}/dev_test_game"
                        else         -> route
                    }
                    navController.navigate(resolved)
                }
            )
        }

        // ── Slow Chat (Day 3) ──────────────────────────────────────────────────
        composable(VeilRoute.SLOW_CHAT) {
            SlowChatScreen(
                onBack   = { navController.popBackStack() },
                onUnlock = { navController.navigate(VeilRoute.CHAT_CREDITS) }
            )
        }

        // ── Chat Credits (new packs + group gifting) ───────────────────────────
        composable(VeilRoute.CHAT_CREDITS) {
            ChatCreditsScreen(onBack = { navController.popBackStack() })
        }

        // ── Chat Unlock paywall (one-time ₹79, legacy) ───────────────────────
        composable(VeilRoute.CHAT_UNLOCK) {
            ChatUnlockScreen(onBack = { navController.popBackStack() })
        }

        // ── Admin Dashboard (restricted) ───────────────────────────────────────
        composable(VeilRoute.ADMIN_DASHBOARD) {
            AdminDashboardScreen(onBack = { navController.popBackStack() })
        }

        // ── Profile Themes — premium avatar frames ────────────────────────────
        composable(VeilRoute.PROFILE_THEMES) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            com.ns.findyourintroverfriend.themes.ProfileThemesScreen(
                onBack     = { navController.popBackStack() },
                onPurchase = { productId ->
                    val activity = ctx as? android.app.Activity
                    activity?.let {
                        com.ns.findyourintroverfriend.billing.VeilBillingClient.buyTheme(it, productId)
                    }
                }
            )
        }

        // ── Swipe feature ───────────────────────────────────────────────────
        composable(VeilRoute.SWIPE_PROFILE_SETUP) {
            SwipeProfileCreationScreen(
                onComplete = {
                    navController.navigate(VeilRoute.SWIPE) {
                        popUpTo(VeilRoute.SWIPE_PROFILE_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(VeilRoute.SWIPE) {
            SwipeScreen(
                onOpenMatch = { matchId ->
                    navController.navigate("${VeilRoute.CONNECTION_ROOM}/$matchId")
                },
                onNeedProfile = {
                    navController.navigate(VeilRoute.SWIPE_PROFILE_SETUP) {
                        popUpTo(VeilRoute.SWIPE) { inclusive = true }
                    }
                },
                onNavigateToMatches = {
                    navController.navigate(VeilRoute.MATCHES_SCREEN)
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Matches Screen — view all matches and compliments ────────────────
        composable(VeilRoute.MATCHES_SCREEN) {
            MatchesScreen(
                onBack = { navController.popBackStack() },
                onOpenMatch = { matchId ->
                    navController.navigate("${VeilRoute.CONNECTION_ROOM}/$matchId")
                }
            )
        }

        composable(
            route     = "${VeilRoute.CONNECTION_ROOM}/{matchId}",
            arguments = listOf(navArgument("matchId") { type = NavType.StringType })
        ) { back ->
            val matchId = back.arguments?.getString("matchId") ?: ""
            ConnectionRoomScreen(
                matchId  = matchId,
                onBack   = { navController.popBackStack() },
                onOpenListeningRoom = { mId, _ ->
                    navController.navigate(VeilRoute.LISTENING_LOBBY)
                }
            )
        }

        // ── Invite Friends ────────────────────────────────────────────────────
        composable(VeilRoute.INVITE_FRIENDS) {
            InviteFriendsScreen(onBack = { navController.popBackStack() })
        }

        // ── Listening Lobby ────────────────────────────────────────────────
        composable(VeilRoute.LISTENING_LOBBY) {
            ListeningLobbyScreen(
                onBack = { navController.popBackStack() },
                onEnterRoom = { roomId, amHost ->
                    navController.navigate(VeilRoute.listeningRoom(roomId, amHost))
                }
            )
        }

        // ── Listening Room ─────────────────────────────────────────────────
        composable(
            route = VeilRoute.LISTENING_ROOM,
            arguments = listOf(
                navArgument("roomId")  { type = NavType.StringType; defaultValue = "" },
                navArgument("amHost")  { type = NavType.StringType; defaultValue = "true" }
            )
        ) { back ->
            val roomId = back.arguments?.getString("roomId")  ?: ""
            val amHost = back.arguments?.getString("amHost") != "false"
            ListeningRoomScreen(
                roomId = roomId,
                amHost = amHost,
                onBack = { navController.popBackStack() }
            )
        }
    }
}


