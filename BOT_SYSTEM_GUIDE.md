# 🤖 Bot System Implementation Guide

## Overview
Complete bot system for testing VEIL app without needing real users. You can now populate the queue, form groups, and test all screens with fake users.

---

## ✨ Features Implemented

### 1. **Enhanced DevBotManager** (`DevBotManager.kt`)
Expanded bot functionality with 10 diverse bot profiles:

**Male Bots:**
- 🌊 Atlas - Deep thinker, Observer
- ⭐ Orion - Creative, Calm
- 🎭 Kai - Playful, Curious
- 🔥 Zane - Energetic, Bold
- 🌿 River - Peaceful, Wise

**Female Bots:**
- 🌙 Nova - Empath, Loyal
- ✨ Lyra - Observer, Thoughtful
- 🦋 Luna - Dreamer, Kind
- 🎨 Aria - Creative, Warm
- 🌸 Sage - Gentle, Intuitive

### 2. **Bot Control Functions**

#### `addBotsToQueue(count: Int)`
- Adds 1-10 bots to matchmaking queue
- Creates both queue entries and user profiles
- Random selection from bot pool
- Returns number of bots added

#### `clearBotsFromQueue()`
- Removes all bots from queue
- Returns number of bots cleared

#### `getBotCountInQueue()`
- Returns current bot count in queue
- Real-time monitoring

#### `injectBotsAndFormGroup()`
- Original functionality preserved
- Creates instant group (you + 3 bots)
- Maintains 2M + 2F gender balance
- Navigates to Island Home automatically

#### `makeBotsAnswerQuestion(groupId, questionDocId)`
- Bots answer specific questions
- 8 diverse answer templates
- Realistic responses

#### `makeBotsSendChatMessages(groupId, messageCount)`
- Bots send chat messages
- Configurable message count per bot
- 8 message templates

---

## 🎮 How to Use

### Access Dev Panel
1. **Long press** your name on Veil Home Screen
2. Or tap the **🛠 DEV** button (top right)
3. Dev panel opens

### Bot Controls Location
1. Open Dev Panel
2. Switch to **🔧 Inspector** tab
3. Scroll to **🤖 Bot Controls** section

### Add Bots to Queue
**Quick Add Buttons:**
- **+1** - Add 1 bot
- **+3** - Add 3 bots
- **+5** - Add 5 bots
- **+10** - Add 10 bots

**What Happens:**
- Bots appear in matchmaking queue
- Queue count updates automatically
- Bots marked with 🤖 icon
- Gender balance maintained

### Clear All Bots
- Tap **🗑 Clear All Bots** button
- Removes all bots from queue
- Real users remain untouched

### Instant Group Formation
- Tap **⚡ Form Group with 3 Bots** button
- Creates group immediately (you + 3 bots)
- Maintains 2M + 2F balance
- Navigates to Island Home
- Ready to test all screens!

---

## 📱 Testing Workflow

### Test Queue & Waiting Room
```
1. Open Dev Panel → Bot Controls
2. Add 3 bots (+3 button)
3. Navigate to Waiting Room
4. See bots in queue
5. Test matchmaking UI
```

### Test Island Home & Activities
```
1. Open Dev Panel → Bot Controls
2. Tap "Form Group with 3 Bots"
3. Automatically navigates to Island Home
4. Test all Day 1-3 activities:
   - Question Card
   - Answer Card
   - Slow Chat
   - Games
   - Reveal Ceremony
```

### Test Specific Screens
```
1. Form group with bots
2. Use Dev Panel → Screen Teleport tab
3. Jump to any screen directly
4. Test UI with bot data
```

---

## 🔍 Bot Identification

### In Queue Display
- Bots show **🤖** icon
- Format: `🤖 Male bot_m_001…`
- Regular users: `Male user123…`

### In Dev Inspector
- Bot count displayed separately
- "Bots in queue: 🤖 3"
- Clear distinction from real users

---

## 🎯 Use Cases

### 1. **UI Testing**
- Test all screens without real users
- Verify layouts with 4 members
- Check animations and transitions

### 2. **Flow Testing**
- Test complete 3-day journey
- Verify navigation between screens
- Test state management

### 3. **Feature Testing**
- Test question/answer flow
- Test chat functionality
- Test game invites
- Test reveal ceremony

### 4. **Billing Testing**
- Form group with bots
- Navigate to Slow Chat (Day 3)
- Test chat credit purchase flow
- Test unlock screens

### 5. **Performance Testing**
- Add 10 bots to queue
- Test queue performance
- Test group formation logic

---

## 🛠 Technical Details

### Bot Data Structure
```kotlin
BotProfile(
    uid: String,          // e.g., "bot_m_001"
    alias: String,        // e.g., "Atlas"
    gender: String,       // "Male" or "Female"
    emoji: String,        // e.g., "🌊"
    personality: List<String>  // e.g., ["Deep thinker", "Observer"]
)
```

### Firestore Structure

**Queue Entry:**
```
matchmaking_queue/{botUid}
├── uid: "bot_m_001"
├── gender: "Male"
├── status: "waiting"
├── joinedAt: Timestamp
└── isBot: true  ← Identifies as bot
```

**User Profile:**
```
users/{botUid}
├── alias: "Atlas"
├── gender: "Male"
├── emoji: "🌊"
├── personalities: ["Deep thinker", "Observer"]
├── profileComplete: true
├── isBot: true  ← Identifies as bot
└── photoUrl: ""
```

**Group Document:**
```
groups/{groupId}
├── members: [realUid, bot_m_001, bot_f_001, bot_f_002]
├── maleCount: 2
├── femaleCount: 2
├── status: "active"
├── day: 1
└── isDev: true  ← Marks as dev group
```

---

## 🎨 UI Features

### Bot Controls Panel
- **Real-time bot count** - Updates automatically
- **Status messages** - Success/error feedback
- **Loading states** - Disabled during operations
- **Color-coded buttons**:
  - Green = Add bots
  - Red = Clear bots
  - Violet = Form group

### Visual Feedback
- ✅ Success messages
- ❌ Error messages
- 🤖 Bot count display
- Status updates in real-time

---

## 🚀 Quick Start

### First Time Setup
1. Log in with dev account
2. Open Dev Panel (long press name)
3. Go to Inspector tab
4. See Bot Controls section

### Quick Test
```
1. Tap "+3" to add 3 bots
2. Tap "Form Group with 3 Bots"
3. You're now in Island Home with 3 bots!
4. Test any screen you want
```

### Clean Up
```
1. Open Dev Panel
2. Go to Inspector tab
3. Tap "Clear All Bots"
4. Queue is clean
```

---

## 📊 Monitoring

### Check Bot Status
- **Queue section** shows bot count
- **Bot Controls** shows real-time count
- **Queue entries** marked with 🤖

### Verify Bot Data
- Bots have complete profiles
- Bots maintain gender balance
- Bots marked with `isBot: true`

---

## ⚠️ Important Notes

### Bot Limitations
- Bots don't send real-time messages automatically
- Bots don't play games automatically
- Bots are for UI testing only
- Use `makeBotsAnswerQuestion()` for automated responses

### Dev Access Required
- Bot controls only visible to dev emails
- Add your email to `DevConfig.DEV_EMAILS`
- Current dev emails:
  - supriyamkumar098@gmail.com
  - itspossible4202@gmail.com

### Best Practices
1. Clear bots after testing
2. Don't leave bots in production queue
3. Use instant group formation for quick tests
4. Monitor bot count regularly

---

## 🎯 Testing Checklist

### Queue Testing
- [ ] Add 1 bot - verify it appears
- [ ] Add 3 bots - verify count updates
- [ ] Check bot icons in queue display
- [ ] Clear bots - verify removal

### Group Testing
- [ ] Form instant group
- [ ] Verify 4 members (you + 3 bots)
- [ ] Check gender balance (2M + 2F)
- [ ] Navigate to Island Home

### Screen Testing
- [ ] Test Question Card with bots
- [ ] Test Answer Card with bots
- [ ] Test Slow Chat with bots
- [ ] Test Reveal Ceremony with bots

### Billing Testing
- [ ] Form group with bots
- [ ] Navigate to Slow Chat
- [ ] Test chat credit purchase
- [ ] Verify unlock flow

---

## 🎉 Result

You can now:
- ✅ Test all screens without real users
- ✅ Populate queue with 1-10 bots
- ✅ Form instant groups for testing
- ✅ Clear bots when done
- ✅ Monitor bot count in real-time
- ✅ Test complete user flows
- ✅ Verify billing screens
- ✅ Debug UI issues easily

**No more waiting for real users to test your app!** 🚀

---

## 📝 Files Modified

1. ✅ `DevBotManager.kt` - Enhanced with queue controls
2. ✅ `DevInspectorScreen.kt` - Added Bot Controls UI
3. ✅ `DevInspectorViewModel.kt` - Added isBot field support

---

**Ready to test! Open Dev Panel and start adding bots.** 🤖
