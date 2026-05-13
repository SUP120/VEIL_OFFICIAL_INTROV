# Swipe Matching & Compliment System - Implementation Plan

## Current Status

### ✅ Already Implemented:
1. **15 swipes per day limit** - Working
2. **Swipe right/left logic** - Working
3. **Match detection** - When both users swipe right, match is created
4. **Compliment sending** - Basic structure exists
5. **Daily compliment limit** - 2 per day

### ❌ Need to Implement:
1. **Match notifications** - Send push notification when matched
2. **Matches button** - Add button next to "Edit Profile"
3. **Matches screen** - Show all matches with swipe to see compliments
4. **Compliment dialog** - Let users type custom compliment message
5. **Compliment notifications** - Send push when someone sends compliment
6. **Undo button** - Restore previous profile

## Implementation Steps

### Step 1: Add Match Notifications
**File:** `SwipeViewModel.kt`
**Function:** `createMatch()`

Add notification call after match is created:
```kotlin
// Send notifications to both users
VeilNotifier.sendSwipeMatch(
    toUid = target.uid,
    matchedWithAlias = myP.alias
)
VeilNotifier.sendSwipeMatch(
    toUid = uid,
    matchedWithAlias = target.alias
)
```

### Step 2: Add Matches Button to Header
**File:** `SwipeScreen.kt`
**Location:** Header Row (next to Edit Profile)

Add a "Matches" button with badge showing unread count:
```kotlin
Row {
    // Matches button
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(SAccentSoft)
            .clickable { onOpenMatches() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row {
            Icon(Icons.Rounded.Favorite, ...)
            Text("Matches")
            if (matchCount > 0) Badge(matchCount)
        }
    }
    
    // Edit Profile button
    Box(...) { ... }
}
```

### Step 3: Create Matches Screen
**New File:** `MatchesScreen.kt`

Features:
- List all matches
- Swipe right to see compliments received
- Click match to open (future: chat)

```kotlin
@Composable
fun MatchesScreen(
    vm: MatchesViewModel,
    onBack: () -> Unit
) {
    // Two tabs: Matches | Compliments
    // Matches tab: Show all matched profiles
    // Compliments tab: Show all compliments received
}
```

### Step 4: Add Compliment Dialog
**File:** `SwipeScreen.kt`

When user clicks star button, show dialog:
```kotlin
@Composable
fun ComplimentDialog(
    profile: SwipeProfile,
    onSend: (message: String) -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Column {
            Text("Send a compliment to ${profile.alias}")
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = "What do you like about them?"
            )
            Button(onClick = { onSend(message) }) {
                Text("Send Compliment")
            }
        }
    }
}
```

### Step 5: Update Compliment System
**File:** `SwipeViewModel.kt`

Update `sendCompliment()` to accept message:
```kotlin
fun sendCompliment(target: SwipeProfile, message: String) {
    if (_complimentsLeft.value <= 0) return
    viewModelScope.launch {
        try {
            val today = LocalDate.now().toString()
            val myAlias = _myProfile.value?.alias ?: "Someone"
            
            db.collection("compliments").document(target.uid)
                .collection("received").add(mapOf(
                    "fromUid"   to uid,
                    "fromAlias" to myAlias,
                    "message"   to message,  // NEW
                    "at"        to System.currentTimeMillis(),
                    "read"      to false     // NEW
                )).await()

            // Update daily limit
            db.collection("swipeProfiles").document(uid).update(
                mapOf(
                    "dailyComplimentsUsed" to FieldValue.increment(1),
                    "dailyComplimentsDate" to today
                )
            ).await()
            
            _complimentsLeft.value = (_complimentsLeft.value - 1).coerceAtLeast(0)

            // Send notification
            VeilNotifier.sendSwipeCompliment(
                toUid = target.uid,
                fromAlias = myAlias,
                message = message  // NEW
            )
        } catch (_: Exception) {}
    }
}
```

### Step 6: Add Notification Functions
**File:** `VeilNotifier.kt`

Add two new notification functions:
```kotlin
suspend fun sendSwipeMatch(toUid: String, matchedWithAlias: String) {
    sendToUser(
        uid = toUid,
        title = "💕 It's a Match!",
        body = "You and $matchedWithAlias liked each other!"
    )
}

suspend fun sendSwipeCompliment(toUid: String, fromAlias: String, message: String) {
    sendToUser(
        uid = toUid,
        title = "⭐ New Compliment!",
        body = "$fromAlias: $message"
    )
}
```

### Step 7: Add Undo Functionality
**File:** `SwipeViewModel.kt`

Track last swiped profile:
```kotlin
private var lastSwipedProfile: SwipeProfile? = null
private var lastSwipeAction: String? = null // "like" or "pass"

fun undoLastSwipe() {
    val profile = lastSwipedProfile ?: return
    val action = lastSwipeAction ?: return
    
    viewModelScope.launch {
        try {
            // Remove from swipeLikes
            db.collection("swipeLikes").document(uid)
                .collection("given").document(profile.uid)
                .delete().await()
            
            // Restore to feed
            _feed.value = listOf(profile) + _feed.value
            
            // Restore swipe count
            _swipesLeft.value = (_swipesLeft.value + 1).coerceAtMost(DAILY_SWIPE_LIMIT)
            
            // Clear undo state
            lastSwipedProfile = null
            lastSwipeAction = null
        } catch (_: Exception) {}
    }
}
```

## Firestore Structure

### Matches Collection:
```
matches/{matchId}
  - userA: string
  - userB: string
  - aliasA: string
  - aliasB: string
  - photoA: string
  - photoB: string
  - matchedAt: timestamp
  - chatUnlocked: boolean
```

### Compliments Collection:
```
compliments/{receiverUid}/received/{complimentId}
  - fromUid: string
  - fromAlias: string
  - message: string
  - at: timestamp
  - read: boolean
```

### SwipeLikes Collection:
```
swipeLikes/{uid}/given/{targetUid}
  - action: "like" | "pass"
  - at: timestamp
```

## UI Flow

### Swipe Screen:
1. User sees profile card
2. Can swipe left (pass) or right (like)
3. Can click star button → Opens compliment dialog
4. Can click undo button → Restores last profile
5. Daily limit: 15 swipes, 2 compliments

### When Match Happens:
1. Both users get push notification
2. Match overlay shows briefly
3. Match is saved to Firestore
4. Badge appears on "Matches" button

### Matches Screen:
1. Shows all matches in a list
2. Can swipe right on match card to see compliments
3. Click match to open (future: chat)

### Compliments:
1. User clicks star button
2. Dialog opens with text field
3. User types compliment message
4. Sends → Receiver gets notification
5. Receiver can see in Matches screen (swipe right)

## Next Steps

1. ✅ Review this plan
2. Implement match notifications
3. Add Matches button to header
4. Create MatchesScreen.kt
5. Add compliment dialog
6. Update compliment system with messages
7. Add undo functionality
8. Test everything

Ready to start implementation?
