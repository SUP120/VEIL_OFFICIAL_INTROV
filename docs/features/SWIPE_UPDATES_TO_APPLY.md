# Swipe System Updates - Manual Application Guide

## ✅ COMPLETED
- UI updates (4 buttons, Matches button added)
- Match notifications to both users

## 🔧 UPDATES TO APPLY

### 1. Update VeilNotifier.kt - Add message parameter to sendSwipeCompliment

**File**: `app/src/main/java/com/ns/findyourintroverfriend/notifications/VeilNotifier.kt`
**Line**: 427

**FIND**:
```kotlin
    suspend fun sendSwipeCompliment(toUid: String, fromAlias: String) {
        val db    = FirebaseFirestore.getInstance()
        val subId = getSubId(db, toUid)
        if (subId != null) {
            sendToSubscriptionIds(
                ids   = listOf(subId),
                title = "Someone really noticed you on Veil",
                body  = "A special compliment is waiting for you.",
                data  = mapOf("type" to "swipe_compliment")
            )
        } else {
            sendToExternalIds(
                uids  = listOf(toUid),
                title = "Someone really noticed you on Veil",
                body  = "A special compliment is waiting for you.",
                data  = mapOf("type" to "swipe_compliment")
            )
        }
        Log.d(TAG, "sendSwipeCompliment -> $toUid")
    }
```

**REPLACE WITH**:
```kotlin
    suspend fun sendSwipeCompliment(toUid: String, fromAlias: String, message: String = "") {
        val db    = FirebaseFirestore.getInstance()
        val subId = getSubId(db, toUid)
        
        // Show message preview if provided, otherwise generic message
        val body = if (message.isNotBlank()) {
            val preview = message.take(60)
            "\"${preview}${if (message.length > 60) "..." else ""}\""
        } else {
            "A special compliment is waiting for you."
        }
        
        if (subId != null) {
            sendToSubscriptionIds(
                ids   = listOf(subId),
                title = "Someone really noticed you on Veil",
                body  = body,
                data  = mapOf("type" to "swipe_compliment")
            )
        } else {
            sendToExternalIds(
                uids  = listOf(toUid),
                title = "Someone really noticed you on Veil",
                body  = body,
                data  = mapOf("type" to "swipe_compliment")
            )
        }
        Log.d(TAG, "sendSwipeCompliment -> $toUid (message: ${message.take(20)}...)")
    }
```

### 2. Update SwipeViewModel.kt - Add message parameter to sendCompliment

**File**: `app/src/main/java/com/ns/findyourintroverfriend/swipe/SwipeViewModel.kt`
**Line**: 303

**FIND**:
```kotlin
    fun sendCompliment(target: SwipeProfile) {
```

**REPLACE WITH**:
```kotlin
    fun sendCompliment(target: SwipeProfile, message: String = "") {
```

**THEN FIND** (around line 311):
```kotlin
                    .set(mapOf(
                        "fromUid"   to uid,
                        "fromAlias" to myAlias,
                        "at"        to System.currentTimeMillis()
                    )).await()
```

**REPLACE WITH**:
```kotlin
                    .set(mapOf(
                        "fromUid"   to uid,
                        "fromAlias" to myAlias,
                        "message"   to message,
                        "at"        to System.currentTimeMillis()
                    )).await()
```

**THEN FIND** (around line 329):
```kotlin
    private fun sendComplimentPush(target: SwipeProfile) {
```

**REPLACE WITH**:
```kotlin
    private fun sendComplimentPush(target: SwipeProfile, message: String = "") {
```

**THEN FIND** (around line 334):
```kotlin
                com.ns.findyourintroverfriend.notifications.VeilNotifier.sendSwipeCompliment(
                    toUid    = target.uid,
                    fromAlias = _myProfile.value?.alias ?: "Someone"
                )
```

**REPLACE WITH**:
```kotlin
                com.ns.findyourintroverfriend.notifications.VeilNotifier.sendSwipeCompliment(
                    toUid     = target.uid,
                    fromAlias = _myProfile.value?.alias ?: "Someone",
                    message   = message
                )
```

**THEN UPDATE THE CALL** (around line 325):
```kotlin
                sendComplimentPush(target)
```

**TO**:
```kotlin
                sendComplimentPush(target, message)
```

---

## ✅ VERIFICATION

After applying these changes, verify by checking:
1. No compilation errors
2. The sendCompliment function accepts a message parameter
3. The VeilNotifier shows message preview in notifications

Once these are applied, I'll create the ComplimentDialog and MatchesScreen!
