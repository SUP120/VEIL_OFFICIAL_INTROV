# 🔥 Firebase Rules Update - Bot Support

## Issue
Bot creation failed with "insufficient permission" because Firebase rules only allowed users to create queue entries with their own UID.

## Solution
Updated Firebase rules to allow dev users to create bot entries.

---

## 📋 What Changed

### 1. Added Dev Check Function
```javascript
function isDev() {
  return request.auth != null 
    && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.get('email', '') 
       in ['supriyamkumar098@gmail.com', 'itspossible4202@gmail.com'];
}
```

### 2. Updated User Profile Rules
```javascript
match /users/{userId} {
  // ... existing rules ...
  
  // NEW: Dev users can create bot profiles
  allow create: if request.auth != null 
    && (request.auth.uid == userId || (userId.matches('bot_.*') && isDev()));
}
```

### 3. Updated Queue Rules
```javascript
match /matchmaking_queue/{userId} {
  allow read: if request.auth != null;
  
  // Regular users can create their own entry
  allow create, update: if request.auth != null && request.auth.uid == userId;
  
  // NEW: Dev users can create bot entries (userId starts with "bot_")
  allow create, update: if request.auth != null 
    && userId.matches('bot_.*') 
    && isDev();
  
  // ANY authenticated user can DELETE a queue entry
  allow delete: if request.auth != null;
}
```

---

## 🚀 How to Update

### Step 1: Copy the Rules
1. Open `firestore.rules` file in your project
2. Copy the entire content

### Step 2: Update Firebase Console
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click **Firestore Database** in left menu
4. Click **Rules** tab at the top
5. **Delete all existing rules**
6. **Paste the new rules** from `firestore.rules`
7. Click **Publish** button

### Step 3: Wait for Deployment
- Rules take 1-2 minutes to propagate
- You'll see "Rules published successfully" message

### Step 4: Test Bot Creation
1. Open your app
2. Go to Dev Panel → Inspector tab
3. Click **+3** button
4. Should now work! ✅

---

## 🔍 How It Works

### Dev User Detection
The rules check if the authenticated user's email is in the dev list:
- `supriyamkumar098@gmail.com`
- `itspossible4202@gmail.com`

### Bot UID Pattern
Bots have UIDs starting with `bot_`:
- `bot_m_001`, `bot_m_002`, etc. (male bots)
- `bot_f_001`, `bot_f_002`, etc. (female bots)

### Permission Logic
```
IF user is authenticated
  AND (
    userId matches their own UID
    OR (
      userId starts with "bot_"
      AND user email is in dev list
    )
  )
THEN allow create
```

---

## ⚠️ Important Notes

### Security
- Only dev emails can create bots
- Bot UIDs must start with `bot_`
- Regular users cannot create bot entries
- Bots cannot authenticate (no real accounts)

### Adding More Dev Emails
To add more dev users, update the `isDev()` function:

```javascript
function isDev() {
  return request.auth != null 
    && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.get('email', '') 
       in [
         'supriyamkumar098@gmail.com',
         'itspossible4202@gmail.com',
         'newdev@gmail.com'  // Add here
       ];
}
```

### Testing Without Dev Access
If you're not a dev user:
1. Add your email to the dev list in rules
2. Publish the updated rules
3. Wait 1-2 minutes
4. Try bot creation again

---

## 🐛 Troubleshooting

### Still Getting Permission Denied?

**1. Check Rules Published**
- Go to Firebase Console → Firestore → Rules
- Verify the new rules are there
- Check "Last published" timestamp

**2. Check Your Email**
- Make sure you're signed in with a dev email
- Check: `FirebaseAuth.getInstance().currentUser?.email`

**3. Wait for Propagation**
- Rules can take 1-2 minutes to apply
- Try again after waiting

**4. Check User Document**
- Your user document must have `email` field
- Go to Firestore → users → [your-uid]
- Verify `email` field exists

**5. Clear App Data**
- Sometimes cached permissions cause issues
- Clear app data and sign in again

### Error: "get() not allowed"
If you see this error, it means the user document doesn't exist or doesn't have email field.

**Fix:**
Update your user profile creation to include email:
```kotlin
db.collection("users").document(uid).set(mapOf(
    "email" to FirebaseAuth.getInstance().currentUser?.email,
    // ... other fields
))
```

---

## ✅ Verification

After updating rules, verify:

### 1. Dev Panel Shows Bot Controls
- Open Dev Panel
- Go to Inspector tab
- See "🤖 Bot Controls" section

### 2. Can Add Bots
- Click +1, +3, +5, or +10
- Should see success message
- Bot count updates

### 3. Bots Appear in Queue
- Check Queue section
- See bots marked with 🤖
- Verify bot UIDs start with `bot_`

### 4. Can Clear Bots
- Click "Clear All Bots"
- Should see success message
- Bot count goes to 0

---

## 📝 Summary

**Before:**
- ❌ Bot creation failed
- ❌ "Insufficient permission" error
- ❌ Could not test with bots

**After:**
- ✅ Dev users can create bots
- ✅ Bot creation works
- ✅ Can test all screens with bots
- ✅ Secure (only dev emails)

---

## 🎯 Next Steps

1. **Update Firebase Rules** (copy from `firestore.rules`)
2. **Publish in Firebase Console**
3. **Wait 1-2 minutes**
4. **Test bot creation**
5. **Start testing your app!**

---

**Rules file ready to copy: `firestore.rules`**

**Just paste it into Firebase Console → Firestore → Rules → Publish!** 🚀
