# Feature Specification: Whisper Voice Rooms

## Goal

Add a community voice room feature called **Whisper** inside the Explore tab of the app. The purpose is to let users join real-time voice conversations in a safe, low-pressure environment designed for introverts.

The experience should feel similar to Discord Voice Channels but simplified for a dating/social discovery app.

---

# User Flow

## Step 1 — Explore

Inside the **Explore** screen, display a chip/button named:

**Whisper**

When the user taps this chip, navigate to the Whisper section.

---

## Step 2 — Whisper Home

The Whisper page should display:

* Active Voice Rooms
* Create Room button

The room list should update in real time.

Each room card should show:

* Room Name
* Host Name
* Number of participants
* Maximum capacity
* Locked/Unlocked badge
* Room category (optional)
* Room language (optional)
* Time since created
* Join button

If there are no active rooms, display a friendly empty state encouraging users to create one.

---

# Creating a Room

When creating a room, the host should be able to choose:

* Room Name
* Public (Unlocked)
* Private (Locked)
* Maximum participants
* Optional room description
* Optional language
* Optional topic/category

After creation:

* Host automatically joins the room.
* The room becomes visible in the active room list.

---

# Joining a Room

If the room is unlocked:

* Any user can tap Join.
* They immediately enter the voice room.

If the room is locked:

* User taps Request to Join.
* Host receives a request.
* Host can Accept or Reject.
* Accepted users enter the room.

---

# Inside a Voice Room

The room should display:

* Host
* Speakers
* Audience (if applicable)
* Current participant count
* Room title
* Room topic

Basic controls:

* Mute / Unmute
* Leave Room
* Speaker indicator
* Connection status

---

# Host (Operator) Controls

The room creator acts as the operator.

Operator abilities:

* Mute participants
* Remove participants
* Lock/Unlock room
* End room
* Approve join requests
* Reject join requests
* Transfer host privileges (optional)
* Invite users (optional)

The operator should also have moderation tools similar to community voice apps.

---

# Voice Queue (Waiting List)

If many users want to speak, there should be a **Voice Queue / Waiting List**.

Users can tap:

**Raise Hand**

The operator sees:

Waiting List

Example:

1. Rahul
2. Neha
3. Arjun

The operator can:

* Invite next speaker
* Remove from queue
* Skip
* Return speaker to audience

This keeps conversations organized.

---

# Room Types

## Public Room

Anyone can join.

## Private Room

Requires operator approval before entering.

---

# Room Duration

Each newly created room includes a free voice session lasting a predefined duration.

When the free duration expires:

The host receives a prompt that the room has reached its free limit.

To continue the room, the host can extend it by spending the required number of **Cookies**.

The exact cookie amount and pricing logic should be determined by Antigravity based on the app's economy and monetization strategy.

Only the room host needs to purchase the extension.

---

# Safety Features

Include moderation tools such as:

* Report Room
* Report User
* Block User
* Leave Room
* Automatic removal when disconnected
* Abuse prevention
* Spam protection

---

# Notifications

Users should receive notifications when:

* Someone requests to join a locked room
* They are accepted into a room
* They are removed from a room
* Their speaking request is accepted
* Room time is about to expire
* Room extension is available

---

# Real-Time Behavior

The system should update instantly when:

* Users join
* Users leave
* Users mute
* Users unmute
* Queue changes
* Room locks/unlocks
* Room ends
* Participant count changes

No manual refresh should be required.

---

# Design Direction

The Whisper experience should feel:

* Calm
* Cozy
* Minimal
* Safe
* Modern
* Designed specifically for introverts

Avoid clutter.

Focus on creating a welcoming environment where conversations feel comfortable and natural.

---

# Scalability Requirements

The architecture should support:
9:00 PM

* Multiple voice rooms running simultaneously
* Real-time participant updates
* Reliable low-latency voice communication
* Efficient room discovery
* Future additions such as scheduled rooms, moderators, room themes, achievements, and community events without major architectural changes.

The implementation should prioritize simplicity, reliability, and scalability while integrating cleanly with the existing Android (Kotlin) + Firebase application.



________________________________________________________________


Voice Communication

Use LiveKit Cloud as the real-time voice infrastructure.

Responsibilities:

Voice transmission
Low-latency audio
Joining/leaving rooms
Speaker detection
Mute/Unmute
Connection management
Reconnection
Voice quality optimization

The app should not implement raw WebRTC manually.