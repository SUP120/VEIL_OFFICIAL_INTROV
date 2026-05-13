🔴 CRITICAL — App Breaks Without These (Fix First)
#	Issue	Impact
1	No automatic Day Progression — Day 1 never becomes Day 2 unless a dev manually runs the inspector. Real users are stuck on Day 1 forever.	💀 App-breaking
2	Non-binary users never match — the 2M + 2F logic ignores them silently. They wait forever with no message.	💀 Discriminatory bug
3	Leave Party Island → soft-lock — groupId isn't cleared, so user gets bounced back to the same group they just left. New cycle is impossible.	💀 Soft lock
4	SlowChat makes 56 Firestore reads every refresh — fetches alias + emoji per message, per listener event. Extremely slow and costly.	🔥 Performance
🟡 UX Confusion & Design Flaws
No onboarding explanation of the 3-day concept — users land on the island with no idea what any of it means
Would You Rather shows no results — you pick, nothing happens. The results only go into a scoring algorithm the user never sees
True or Myth results never shown — submitter never finds out if people guessed correctly
Ceremony has no "All Ready" gate — one person can accidentally enter before others
Soul Web is passive — nothing happens when you tap the connection lines between people
User sees their own question in Answer Card — no "this is yours" badge, no filter
🟤 Weird Things That Need Changing
App is called "Inrovert," branded as "Veil" in UI, package says findyourintroverfriend — pick one name before launch
7 Slow Chat messages feels like pressure for introverts — ironic. Consider 1 message per hour instead (natural pacing = drama)
Waiting Room shows ALL other batches — you can see other random people's aliases. Privacy issue
✨ What Would Make It Viral
Live Bond Meter on Soul Web — fills as you interact, makes users compete to push it higher
Day Transition Animation — cinematic "Day 2 begins..." full-screen moment. People will screenshot it
Share Your Alias Card — generated card after profile setup: "I'm Stargazer on Veil — can you guess who I am?" Free user acquisition
Memory Board on Party Island — matched pair pins moments from the 3 days → their shared story
Push notifications for "Partner answered your question", "Day 2 started", "Ceremony in 1 hour"
Biggest single fix that unlocks everything else: Day auto-progression. Without it, the entire 3-day arc never actually runs. Want me to implement that first? It's low effort and high impact — a simple client-side timestamp check when the app opens.

