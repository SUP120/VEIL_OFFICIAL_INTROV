# Introvert Feed Feature - Anonymous Posting

## Overview
A professional Instagram-inspired anonymous posting feed where introverts can share their thoughts, feelings, and experiences anonymously. The feed is accessible from the rightmost navigation button in the bottom navigation bar.

## Design Philosophy
- **Instagram-inspired**: Clean, minimal, professional design matching Instagram's aesthetic
- **Pure white backgrounds**: No gradients, no purple colors
- **Professional typography**: Proper font weights and letter spacing
- **Smooth animations**: Spring animations for interactions
- **Anonymous by default**: All posts are anonymous with generated aliases

## Features

### 1. **Anonymous Posting**
- Users can create posts up to 500 characters
- Posts are automatically assigned anonymous aliases like:
  - "Quiet Soul #1234"
  - "Silent Thinker #5678"
  - "Peaceful Heart #9012"
  - And 13 more variations
- No profile pictures shown - only colored avatar circles

### 2. **Feed Display**
- Instagram-style scrollable feed
- Posts show:
  - Anonymous avatar (colored circle with initial)
  - Anonymous alias
  - Time ago (e.g., "2h ago", "3d ago")
  - Post content
  - Like button with count
  - Report button (in menu)

### 3. **Interactions**
- **Like**: Tap the heart icon to like/unlike posts
  - Animated heart with scale effect
  - Like count updates in real-time
  - Red color when liked, gray when not
- **Report**: Report inappropriate content
  - Auto-hides posts after 5 reports
  - One report per user per post

### 4. **Create Post Dialog**
- Clean modal dialog
- Character counter (500 max)
- Minimum 10 characters required
- Anonymous notice badge
- Professional blue accent color

### 5. **Real-time Updates**
- Posts update in real-time using Firestore listeners
- New posts appear automatically
- Like counts update live
- Reported posts disappear automatically

## Technical Implementation

### Files Created
1. **IntrovertFeedScreen.kt** - Main UI screen
2. **IntrovertFeedViewModel.kt** - Business logic and data management

### Firestore Collections

#### `introvertPosts` Collection
```
{
  postId: string (auto-generated)
  authorId: string (Firebase UID)
  authorAlias: string (e.g., "Quiet Soul #1234")
  content: string (post text)
  createdAt: number (timestamp)
  likeCount: number
  reportCount: number
  hidden: boolean (auto-set after 5 reports)
}
```

#### `introvertPosts/{postId}/likes` Subcollection
```
{
  uid: string (user who liked)
  likedAt: number (timestamp)
}
```

#### `introvertPosts/{postId}/reports` Subcollection
```
{
  uid: string (user who reported)
  reportedAt: number (timestamp)
}
```

### Navigation Integration
- Route: `VeilRoute.INTROVERT_FEED`
- Accessible from bottom navigation bar (rightmost button)
- Replaces the "Founders" screen in the navigation flow

## Design Tokens (Instagram-inspired)
```kotlin
FBg         = Color(0xFFFFFFFF)  // Pure white background
FCard       = Color(0xFFFFFFFF)  // White cards
FBorder     = Color(0xFFDBDBDB)  // Subtle gray border
FPrimary    = Color(0xFF262626)  // Near black text
FSecondary  = Color(0xFF8E8E8E)  // Gray secondary text
FMuted      = Color(0xFFC7C7C7)  // Light gray
FAccent     = Color(0xFF0095F6)  // Instagram blue
FAccentSoft = Color(0xFFE7F3FF)  // Very light blue
FRed        = Color(0xFFED4956)  // Instagram red/like color
```

## User Experience

### Empty State
- Clean empty state with icon
- "No posts yet" message
- Encourages first post

### Loading State
- Professional shimmer animation
- Shows 5 skeleton cards
- Smooth fade-in when loaded

### Post Card
- Clean card layout
- Avatar with colored background
- Anonymous alias and timestamp
- Post content with proper line height
- Like button with animation
- Menu button for reporting

### Create Post Flow
1. Tap + button in top bar
2. Modal dialog appears
3. Enter post content (10-500 chars)
4. See character count
5. Tap "Post Anonymously"
6. Post appears in feed immediately

## Moderation
- Auto-moderation: Posts with 5+ reports are automatically hidden
- No manual moderation required initially
- Can be extended with admin review panel

## Future Enhancements
- Comments on posts
- Post categories/tags
- Trending posts
- User's own posts view
- Edit/delete own posts
- Image attachments
- Emoji reactions
- Search functionality

## Testing Checklist
- [ ] Create a post
- [ ] Like a post
- [ ] Unlike a post
- [ ] Report a post
- [ ] Verify real-time updates
- [ ] Test character limit
- [ ] Test minimum character requirement
- [ ] Verify anonymous aliases are generated
- [ ] Test empty state
- [ ] Test loading state
- [ ] Verify navigation works
- [ ] Test on different screen sizes

## Firebase Security Rules (Required)

Add these rules to `firestore.rules`:

```javascript
// Introvert Feed - Anonymous Posting
match /introvertPosts/{postId} {
  // Anyone can read posts (not hidden)
  allow read: if request.auth != null && 
                 (!resource.data.hidden || resource.data.hidden == false);
  
  // Authenticated users can create posts
  allow create: if request.auth != null &&
                   request.resource.data.authorId == request.auth.uid &&
                   request.resource.data.content.size() >= 10 &&
                   request.resource.data.content.size() <= 500;
  
  // Only author can update their own posts
  allow update: if request.auth != null &&
                   resource.data.authorId == request.auth.uid;
  
  // Likes subcollection
  match /likes/{userId} {
    allow read: if request.auth != null;
    allow write: if request.auth != null && userId == request.auth.uid;
  }
  
  // Reports subcollection
  match /reports/{userId} {
    allow read: if request.auth != null;
    allow write: if request.auth != null && userId == request.auth.uid;
  }
}
```

## Performance Considerations
- Posts limited to 100 most recent
- Real-time listener for live updates
- Efficient Firestore queries with indexing
- Lazy loading with LazyColumn
- Optimized animations with remember

## Accessibility
- Proper content descriptions for icons
- Sufficient touch target sizes (48dp minimum)
- High contrast text colors
- Clear visual feedback for interactions

---

**Status**: ✅ Complete and ready for testing
**Design**: Instagram-inspired, professional, production-ready
**Integration**: Fully wired into navigation system
