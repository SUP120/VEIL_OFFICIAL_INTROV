# Complete List of App Screens/Pages

## 📱 Main Navigation Screens (4 screens)

1. **VeilHomeScreen** - Main home page with circular profile pic, welcome message, and info cards for Anonymous Chat and Slow Chat features
2. **SwipeScreen** - Tinder-style swipe interface with professional card animations, spring physics, LIKE/NOPE stamps, and matches button
3. **IntrovertFeedScreen** - Instagram-style anonymous posting feed with refresh button, create post button, and Instagram-style 3-dot menu
4. **FeedScreen** - Alternative feed screen for posts and interactions

## 🎮 Circle/Room Screens (6 screens)

5. **WaitingRoomScreen** - Queue screen with animations while waiting to be matched with a circle of 4 people
6. **RoomLobbyScreen** - Lobby where 4 matched people wait before entering the room, shows all participants
7. **LobbyWaitingScreen** - Waiting state within the lobby with countdown and participant status
8. **IslandHomeScreen** - Main island interface for active circle/room with day/night cycle and interactive elements
9. **PartyIslandScreen** - Interactive island view for the 4-person circle with activities, chat, and bond tracking
10. **ActiveRoomsScreen** - List of user's active/ongoing circles with status and quick access

## 💬 Chat & Communication (4 screens)

11. **SlowChatScreen** - One message per day slow chat interface for meaningful conversations
12. **ChatScreen** - Real-time chat interface within party/circle with message history
13. **ConnectionRoomScreen** - Private chat room for swipe matches with photo sharing
14. **MatchesScreen** - List of all swipe matches with profile previews and last message

## 🎯 Activities & Games (9 screens)

15. **QuestionCardScreen** - Question card activity where circle members ask/answer questions
16. **AnswerCardScreen** - Screen to answer received questions from circle members
17. **MyQuestionsInboxScreen** - Inbox for received questions with unread indicators
18. **InboxScreen** - General inbox for messages/notifications across all features
19. **WouldYouRatherScreen** - "Would You Rather" game/poll for circle members
20. **TrueOrMythScreen** - True or Myth game where members guess facts about each other
21. **TrueOrMythSetupScreen** - Setup screen for True or Myth game with statement creation
22. **TicTacToeScreen** - Tic Tac Toe game playable with circle members
23. **GameInviteScreen** - Screen to invite circle members to various games

## 🎭 Day-Specific Activities (5 screens)

24. **SecretComplimentScreen** - Day 2 activity for sending anonymous secret compliments to circle members
25. **DareToShareScreen** - Day 2 activity for sharing dares and challenges
26. **VibesScreen** - Activity for sharing current vibes/moods with the circle
27. **RevealCeremonyScreen** - Day 3 reveal ceremony where identities are shown with animations
28. **KnowEachOtherScreen** - Post-reveal screen showing matched partner details

## 👤 Profile & Authentication (5 screens)

29. **AuthScreen** - Login/signup screen with phone authentication
30. **GameIntroScreen** - Onboarding screen explaining the 3-day circle concept
31. **ProfileSetupScreen** - Initial profile creation with name, age, gender, bio, and photo
32. **SwipeProfileCreationScreen** - Create profile specifically for swipe feature with additional photos
33. **ProfileThemesScreen** - Purchase and select profile background themes (monetization)

## 💰 Payment & Monetization (2 screens)

34. **ChatUnlockScreen** - Screen to unlock chat features with payment options
35. **ChatCreditsScreen** - Purchase chat credits/messages with pricing tiers

## ℹ️ Information & Admin (3 screens)

36. **FoundersScreen** - About the founders/team page with social links
37. **DevInspectorScreen** - Developer tools and inspection panel (dev mode only)
38. **AdminDashboardScreen** - Admin panel for moderation and user management

## 🎨 Additional Features (2 screens)

39. **AestheticsFeedScreen** - Alternative aesthetic-focused feed view
40. **ReportSheet** - Bottom sheet for reporting users/content with violation categories

## 🎨 Skeleton/Loading Screens (7 screens)

41. **IslandHomeSkeletonScreen** - Loading skeleton for island home with shimmer effect
42. **SlowChatSkeletonScreen** - Loading skeleton for slow chat messages
43. **QuestionSkeletonScreen** - Loading skeleton for question cards
44. **RevealSkeletonScreen** - Loading skeleton for reveal ceremony
45. **InboxSkeletonScreen** - Loading skeleton for inbox items
46. **AnswerCardSkeletonScreen** - Loading skeleton for answer cards
47. **MyQuestionsSkeletonScreen** - Loading skeleton for questions inbox

---

## 📊 Summary

**Total Screens: 47**

### Categories Breakdown:
- **Main Navigation**: 4 screens (VeilHome, Swipe, IntrovertFeed, Feed)
- **Circle/Room Management**: 6 screens (Waiting, Lobby, Island, Party, Active Rooms)
- **Chat & Messaging**: 4 screens (SlowChat, ChatScreen, ConnectionRoom, Matches)
- **Games & Activities**: 9 screens (Questions, Answers, Inbox, Games, Polls)
- **Day-Specific Activities**: 5 screens (Compliments, Dares, Vibes, Reveal, KnowEachOther)
- **Profile & Auth**: 5 screens (Auth, Intro, Setup, SwipeProfile, Themes)
- **Payment**: 2 screens (Unlock, Credits)
- **Info & Admin**: 3 screens (Founders, DevInspector, AdminDashboard)
- **Additional Features**: 2 screens (Aesthetics, Report)
- **Loading States**: 7 skeleton screens

### Core User Journey:
1. **AuthScreen** → **GameIntroScreen** → **ProfileSetupScreen** (Onboarding)
2. **VeilHomeScreen** (Main hub with navigation)
3. **WaitingRoomScreen** → **RoomLobbyScreen** → **PartyIslandScreen** (Circle matching)
4. **SlowChatScreen** + Activities (Day 1-2 bonding)
5. **RevealCeremonyScreen** → **KnowEachOtherScreen** (Day 3 reveal)
6. **SwipeScreen** → **MatchesScreen** → **ConnectionRoomScreen** (Dating feature)
7. **IntrovertFeedScreen** (Social feed for anonymous posts)

### Recent UI Updates:
- VeilHomeScreen: Circular profile pic at top, removed stats, modern info cards
- SwipeScreen: Professional animations, icon-only matches button, minimal top padding
- IntrovertFeedScreen: Instagram-style 3-dot menu, DM button (planned), 24dp top padding
- Navigation: Smooth slide animations (200ms), swapped home/feed positions

### Planned Features:
- **DMsListScreen** (not yet created) - Instagram-style DMs list for matches and post compliments
