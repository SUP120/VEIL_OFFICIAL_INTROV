# Activity 1

Goal:
Two matched users can enter a shared room and listen to the same music/ambient audio in sync in real time. The experience should feel emotionally calm, minimal, and intimate — not like Spotify party mode.

- Firebase Firestore realtime sync
- Existing VEIL architecture
- Use YouTube embedded player initially (free + scalable)
- No custom music hosting

Core Requirements:

1. Shared Listening Room
- User can wait for another user to join  the listening room  or the friends/match they have made can be invited  into the “Listening Room”
- Both users see:
  - song title
  - subtle animated background
  - presence indicator (“listening together”)
- minimal chat
- Focus should remain on atmosphere and messaging sharing what they feel

2. Realtime Playback Sync
When one user:
- plays
- pauses
- seeks/skips

The other user syncs automatically.

Use Firestore realtime listeners for synchronization.

Firestore Room Structure Example:
{
  roomId,
  trackId,
  isPlaying,
  currentPosition,
  updatedAt,
  hostUid,
  participantUid
}

3. Smart Sync Logic
- Prevent infinite playback loops
- Use timestamp-based sync correction
- Allow small tolerance (~1–2 sec)
- Sync only when drift becomes noticeable

4. Music Source
Use YouTube embedded player:
- User shares YouTube song link
- App extracts videoId
- Embedded player handles playback

No music files should be stored on our servers.

5. Atmosphere UI
VEIL aesthetic:
- match the theme
- minimal UI
- emotionally calm

6. Listening Presence
Show:
- “Both listening”
- “Paused”
- “Reconnecting…”

No aggressive indicators or gamer-style UI.

7. Fullscreen Experience
Room should feel immersive:
- hide unnecessary controls
- focus on music + emotional presence

8. Backend Safety
- Firestore writes must be throttled/debounced
- Do NOT update playback position every millisecond
- Sync every few seconds or meaningful actions only
- Avoid excessive realtime writes

9. Performance Requirements
- Lightweight
- Low battery usage
- Low Firebase reads/writes
- Smooth on low-end Android devices

10. Future-Proof Architecture
Design room structure so later we can add:
- ambient-only rooms
- anonymous rooms
- group listening rooms
- Spotify support
- voice ambience

11. UX Philosophy
This should feel like:
“Two introverts quietly sharing a moment online.”

Not:
“Party music app.”
