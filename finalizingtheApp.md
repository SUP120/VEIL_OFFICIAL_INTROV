# 🌑 Veil — Pre-Launch Full App Diagnostic
*Audited: 3 March 2026 · Every screen, every button, every navigation path checked*

---

## 1. Complete App Flow Map

```
Cold Start
    │
    ▼
rememberStartDestination()
    ├── user == null ──────────────────────────→ AUTH
    ├── admin email ───────────────────────────→ ADMIN_DASHBOARD
    ├── profileComplete == false ──────────────→ PROFILE_SETUP
    ├── banned ────────────────────────────────→ AUTH  (ban wall)
    ├── has partySessionId (still active) ─────→ PARTY/{id}
    ├── has groupId  +  ceremonyDone==false ───→ ISLAND_HOME
    ├── has groupId  +  ceremonyDone==true ────→ clears groupId → WAITING_ROOM
    ├── has lobbyRoomId (still open) ──────────→ LOBBY_WAITING/{id}
    ├── leftRoom == true ──────────────────────→ VEIL_HOME
    └── otherwise ─────────────────────────────→ WAITING_ROOM


AUTH
  ├── sign in success, needs profile  →  PROFILE_SETUP  (AUTH cleared)
  ├── sign in success, has profile    →  WAITING_ROOM   (AUTH cleared)
  └── admin email (11-tap easter egg) →  ADMIN_DASHBOARD  ← BUG: AUTH stays in stack

PROFILE_SETUP  →  GAME_INTRO  →  WAITING_ROOM

WAITING_ROOM
  ├── group found        →  ISLAND_HOME  (WR cleared)
  └── leave queue btn    →  VEIL_HOME    (full stack clear)

VEIL_HOME
  └── Find a room btn   →  WAITING_ROOM  (VEIL_HOME cleared)

ISLAND_HOME
  ├── Question Card   →  QUESTION_CARD     →  back → ISLAND_HOME
  ├── Answer Card     →  ANSWER_CARD       →  back → ISLAND_HOME
  ├── Inbox           →  INBOX             →  back → ISLAND_HOME
  ├── My Q Inbox      →  MY_QUESTIONS_INBOX→  back → ISLAND_HOME
  ├── Game Invite     →  GAME_INVITE → TIC_TAC_TOE/{id} → back
  ├── WYR             →  WOULD_YOU_RATHER  →  back → ISLAND_HOME
  ├── True or Myth    →  TRUE_OR_MYTH ↔ TRUE_OR_MYTH_SETUP
  ├── Slow Chat       →  SLOW_CHAT → CHAT_CREDITS (Coming Soon)
  ├── Ceremony (8PM)  →  CEREMONY → PARTY/{id} → WAITING_ROOM
  ├── Error state     →  NoGroupCard → WAITING_ROOM   ← BUG: groupId not cleared
  └── Leave room      →  VEIL_HOME (full stack clear)

PARTY/{id}
  ├── chat rules gate → ChatRulesScreen → ChatScreen → back
  ├── know each other → KnowEachOtherScreen → back
  ├── quit            →  WAITING_ROOM (full stack clear)
  └── partner left    →  PartnerLeftScreen → WAITING_ROOM
```

---

## 2. Screen-by-Screen Audit Table

### AUTH
| Check | Status |
|---|---|
| Google Sign-In picker opens | ✅ |
| `onAuthSuccess(needsProfile=true)` → PROFILE_SETUP | ✅ |
| `onAuthSuccess(needsProfile=false)` → WAITING_ROOM | ✅ |
| Admin email shortcut on login | ✅ |
| Error states displayed (code 12501, 12502, 10…) | ✅ |
| Back press exits app | ✅ Acceptable |
| **BUG B1 — Admin 11-tap keeps AUTH in back stack** | 🔴 Fixed below |
| **BUG B2 — No ban check post fresh sign-in** | 🟡 Documented |

---

### PROFILE_SETUP
| Check | Status |
|---|---|
| 6 steps with progress bar | ✅ |
| DOB → 18+ hard block | ✅ |
| Gender selection | ✅ |
| Alias min 2 chars validated | ✅ |
| Photo → gallery → UCrop circle crop | ✅ |
| Personality chips (max 3) | ✅ |
| Comfort step (optional) | ✅ |
| `onSetupComplete` → GAME_INTRO | ✅ |
| In-step back via `vm.backStep()` | ✅ |

---

### GAME_INTRO
| Check | Status |
|---|---|
| Shown once after profile setup | ✅ |
| `onComplete` → WAITING_ROOM | ✅ |
| **BUG B3 — Back press exits app silently** | 🟡 Documented |
| Cold-restart correctly skips it | ✅ |

---

### WAITING_ROOM
| Check | Status |
|---|---|
| Auto-matching runs | ✅ |
| `onGroupFound` → ISLAND_HOME (WR cleared) | ✅ |
| Leave Queue button → confirmation dialog | ✅ |
| Confirm leave → `leaveQueue()` sets leftRoom=true | ✅ |
| `onLeave` → VEIL_HOME (full stack clear) | ✅ |
| **BUG B4 — Android back press: no BackHandler** | 🔴 Fixed below |

---

### VEIL_HOME
| Check | Status |
|---|---|
| Alias + emoji + stats shown | ✅ |
| "Find a room" clears leftRoom flag + → WAITING_ROOM | ✅ |
| How Veil Works explainer | ✅ |
| Back press exits app (full-stack was cleared) | ✅ Acceptable |

---

### ISLAND_HOME
| Check | Status |
|---|---|
| Day chip (Day 1 / 2 / 3) | ✅ |
| Countdown timer (Day 1 & 2) | ✅ |
| Day peek sheet on chip tap | ✅ |
| All 9 navigation callbacks wired | ✅ |
| Back press → "Exit App?" dialog | ✅ |
| Leave door → confirmation → VEIL_HOME | ✅ |
| Member-left banner (6.5 s auto-dismiss) | ✅ |
| **BUG B5 — NoGroupCard doesn't clear groupId** | 🔴 Fixed below |

---

### GAME_INVITE / TIC_TAC_TOE
| Check | Status |
|---|---|
| Back → ISLAND_HOME | ✅ |
| Incoming invite banner (Accept / Decline) | ✅ |
| `onGameStart(gameId)` → TIC_TAC_TOE/{gameId} | ✅ |
| TicTacToe `onBack` → popBackStack (returns to GAME_INVITE) | ✅ |
| Empty state if no opposite-gender mates | ✅ |

---

### SLOW_CHAT
| Check | Status |
|---|---|
| Back → ISLAND_HOME | ✅ |
| `onUnlock` → CHAT_CREDITS ("Coming Soon") | ✅ |
| Message limit enforced | ✅ |

---

### CHAT_CREDITS / CHAT_UNLOCK
| Check | Status |
|---|---|
| Cashfree fully removed | ✅ |
| "Coming Soon" + Google Play subtext | ✅ |
| Non-interactive (no payment triggers) | ✅ |
| `onBack` → popBackStack | ✅ |

---

### REVEAL_CEREMONY
| Check | Status |
|---|---|
| Bond results shown | ✅ |
| `onGoToParty` → PARTY/{id}, ceremony popped | ✅ |
| `onStartFresh` → WAITING_ROOM (full clear) | ✅ |
| **BUG B6 — Back press returns user to ISLAND_HOME mid-ceremony** | 🔴 Fixed below |

---

### PARTY_ISLAND
| Check | Status |
|---|---|
| Chat Rules gate before chat | ✅ |
| Know Each Other screen | ✅ |
| Quit dialog confirm → WAITING_ROOM | ✅ |
| Partner left → PartnerLeftScreen | ✅ |
| partySessionId cleared when user left | ✅ |

---

### ADMIN_DASHBOARD
| Check | Status |
|---|---|
| Email-gated | ✅ |
| `onBack` → popBackStack | ✅ |

---

## 3. Confirmed Bugs & Fixes

### 🔴 B1 — Admin easter-egg navigation keeps AUTH in back stack
**File:** `VeilNavGraph.kt` line ~67
**Problem:** `popUpTo(VeilRoute.AUTH) { inclusive = false }` → AUTH stays in stack.
Admin presses back from dashboard → returns to AUTH screen.
**Fix:** Change to `inclusive = true` ✅ Applied

---

### 🟡 B2 — Banned users can reach WAITING_ROOM after fresh Google sign-in
**Where:** `NavGraph → AuthScreen.onAuthSuccess`
**Problem:** Cold-start resolver catches bans and redirects to AUTH. But after a
fresh Google sign-in (new device install), `onAuthSuccess(needsProfile=false)`
sends user directly to WAITING_ROOM without a ban check.
**Fix (next sprint):** Add ban check in `WaitingRoomViewModel.init {}` — same
pattern already in SlowChatScreen.

---

### 🟡 B3 — Back press during GAME_INTRO exits app silently
**Where:** `GameIntroScreen` (one-time onboarding after profile setup)
**Problem:** ProfileSetup is already popped before GameIntro opens. Android back
button exits the app with no warning or UI feedback.
**Fix (low priority):** Add a `BackHandler` in GameIntroScreen that shows a
"Going back will exit setup. Continue?" snackbar or navigates to the previous
slide.

---

### 🔴 B4 — Back press on WAITING_ROOM exits app with no confirmation
**Where:** `WaitingRoomScreen`
**Problem:** No `BackHandler` registered. User presses Android back → app exits
silently, losing their queue position.
**Fix:** Add `BackHandler` to trigger the "Leave Queue?" confirmation dialog ✅ Applied

---

### 🔴 B5 — NoGroupCard "Return to Waiting" does not clear stale groupId
**Where:** `IslandHomeScreen` error state
**Problem:** When group doc fails to load and user taps "Return to Waiting",
they are navigated to WAITING_ROOM but their `groupId` in Firestore is NOT
cleared. On cold restart, they'd be routed back to ISLAND_HOME (which fails
again), creating a loop.
**Note:** Cold-start resolver DOES clear a missing groupId, so the loop breaks
on restart. But during an active session, the user bounces back and forth.
**Fix:** Added a Firestore `groupId` delete call inside `IslandHomeViewModel`
before navigating out via `onReturnToWaiting`. ✅ Applied

---

### 🔴 B6 — No BackHandler on CEREMONY — back press returns mid-ceremony
**Where:** `RevealCeremonyScreen`
**Problem:** Android back press during the ceremony returns user to ISLAND_HOME.
The ceremony can be re-opened from there. Could cause duplicate state writes.
**Fix:** Added `BackHandler(enabled = true) {}` to consume back press silently during ceremony. ✅ Applied

---

## 4. Publishing Checklist

| Item | Status |
|---|---|
| Cashfree SDK removed | ✅ |
| No third-party payment API calls | ✅ |
| Payment screens show "Coming Soon" | ✅ |
| Admin dashboard email-gated | ✅ |
| Dev Inspector gated to DEBUG builds | ✅ |
| 18+ enforcement (hard block on underage DOB) | ✅ |
| Content moderation in SlowChat | ✅ |
| Ban system (temp + permanent) | ✅ |
| Report system in chat & Party Island | ✅ |
| Notification permission requested (Android 13+) | ✅ |
| OneSignal user registration on login | ✅ |
| Cold-start routing handles all 7 states | ✅ |
| leftRoom flag correctly set/cleared | ✅ |
| Stale groupId cleared on ceremony complete | ✅ |
| partySessionId cleared when re-entering after leaving | ✅ |
| B1 (admin back-stack) — Fixed | ✅ |
| B4 (WaitingRoom back press) — Fixed | ✅ |
| B5 (NoGroupCard groupId not cleared) — Fixed | ✅ |
| B6 (Ceremony back press) — Fixed | ✅ |
| B2 (ban after fresh sign-in) — Next sprint | 🟡 |
| B3 (GameIntro back press) — Next sprint | 🟡 |

---

## 5. Manual Test Script Before Submitting to Play

Run through this on a physical test device:

```
1. Fresh install
   → Sign in → Profile setup (6 steps) → Game intro → Waiting room ✓

2. Force-close mid-waiting
   → Reopen → Should be on Waiting Room ✓

3. Press Android BACK on Waiting Room
   → Should see "Leave Queue?" dialog ✓

4. Leave queue
   → Should land on Veil Home ✓

5. Force-close from Veil Home
   → Reopen → Should stay on Veil Home ✓

6. Find room → get matched
   → Should land on Island Home ✓

7. Force-close from Island Home
   → Reopen → Should return to Island Home ✓

8. Leave room (door icon)
   → Confirmation dialog → Should land on Veil Home ✓

9. Reach Ceremony → press Android BACK
   → Should NOT navigate away (back blocked) ✓

10. Complete ceremony → land on Party Island ✓
    → Quit Party Island → land on Waiting Room ✓

11. Slow Chat → tap Unlock button
    → Should see "Coming Soon" screen, NOT payment ✓

12. Sign in with admin email
    → Should land directly on Admin Dashboard ✓
    → Press back → Should EXIT app, not see Auth screen ✓
```
