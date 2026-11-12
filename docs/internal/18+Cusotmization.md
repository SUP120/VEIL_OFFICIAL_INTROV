🔒 Safety, Verification & Moderation System
(Customized for an 18+ Friendship App)
1️⃣ AGE & IDENTITY VERIFICATION (18+ ONLY)
1.1 Date of Birth (DOB) Verification

Use Android DatePicker to collect Date of Birth during onboarding.

System must calculate age locally before allowing access.

Rule:

If user age is below 18 years:

Block the user permanently from proceeding.

Show a non-bypassable screen:

“This app is strictly for users aged 18 and above. You cannot proceed.”

Do not store any data of blocked users.

1.2 Misrepresentation Warning (Mandatory Text)

Display the following text clearly during onboarding and profile creation:

“Misrepresentation may result in permanent ban.”

This text must be visible on:

Day 1 page

Day 2 page

Day 3 page

Island page

Profile creation / edit screen

Purpose:
To legally and clearly warn users against false age, gender, or identity claims.

2️⃣ COMMUNITY REPORTING SYSTEM
2.1 Reporting Options (User Side)

Provide one-tap reporting with the following options:

“Report fake gender”

“Report impersonation”

2.2 Optional Explanation Field

While reporting, allow users to optionally describe what exactly happened (free text).

This text is stored only for admin review.

Purpose:
This gives users control and provides context for moderation decisions.

3️⃣ ADMIN DASHBOARD (MODERATION PANEL)
3.1 Admin Authentication

Admin access is restricted to one Gmail account only:

supriyamkumar098@gmail.com

No other email should be allowed to access admin features.

3.2 Admin Dashboard Capabilities

Admin must be able to:

View all reported users

View report reasons and optional explanations

View user profiles, chat history (only for moderation)

Take actions:

Warning

Temporary ban

Permanent ban

Dismiss report

All actions should be logged for safety auditing.

4️⃣ CHAT SYSTEM SAFETY & FILTERING
4.1 Automatic Message Filtering (Before Sending)

Every message must be checked before it is sent.

The system must block or flag messages containing:

Phone numbers

Social media handles (Instagram, Snapchat, WhatsApp, etc.)

Sexual keywords

Hate speech

Explicit or abusive slang
(English, Hindi, Hinglish — all included)

4.2 One-Tap Block & Report (Mandatory)

Every chat screen must have:

🚫 Block user

⚠️ Report user

Report reasons must include:

Harassment

Fake profile

Sexual content

Minor suspicion

This must be accessible within one tap, not hidden in menus.

5️⃣ USER-ASKED QUESTIONS – AUTO MODERATION FLOW
5.1 Question Moderation Logic

When a user types a question:

User types question
→ System checks text
→ If SAFE → Question is sent
→ If NOT SAFE → Block question + show warning
5.2 Blocked Question Categories

The system must block questions containing:

Phone numbers

Instagram / Snapchat / WhatsApp words

Sexual or adult words

Dating or relationship pressure

Location sharing (address, city, exact place)

Age-related questions

Money or payment requests

5.3 Warning Message (Mandatory)

If a question is blocked, show this warning:

⚠️ “Personal, sexual, contact or location questions are not allowed. Violations may result in a ban.”

If repeated inappropriate slang is detected:

Show a stronger warning mentioning possible ban.

6️⃣ REPORT QUESTION FEATURE
6.1 Reporting Questions

Every question (even allowed ones) must include:

⚠️ Report Question

6.2 Report Reasons

User can select:

Inappropriate

Harassment

Personal information request

Purpose:
This demonstrates user control and safety awareness, which Google Play strongly expects.

7️⃣ STRIKE & PENALTY SYSTEM (BACKEND LOGIC)
7.1 Strike Rules

Implement a simple, clear escalation system:

1st violation → Warning

2nd violation → Temporary ban

3rd violation → Permanent ban

Strikes are stored server-side.

Admin can override if required.

8️⃣ COMMUNICATION RULES (VERY IMPORTANT)
8.1 What the App MUST NOT Say

Do NOT display or claim the following anywhere in the app or Play Console:

❌ “AI checks everything perfectly”
❌ “100% safe platform”
❌ “Gender verified by AI”

8.2 What the App MAY Say (Safe Language)

You may say:

“Content is automatically reviewed for safety”

“Users can report inappropriate behavior”

“Violations may lead to suspension or ban”

✅ FINAL OUTCOME

By implementing all points above, the app will:

Be 18+ compliant

Be Google Play policy–safe

Reduce abuse, impersonation, and risk

Show clear intentional safety design

Protect both users and the developer legally