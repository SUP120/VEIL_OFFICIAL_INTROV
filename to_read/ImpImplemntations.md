🔴 Critical (App-Breaking)
ID	Issue	Impact
C1	Ghost in queue — Uninstall leaves their UID in matchmaking_queue forever, blocking a batch slot	3 real users stuck at 3/4 forever
C2	No session restore — Reinstalling the app loses the groupId, leaving 3 members with a ghost member in their group	Group of 3 stranded for 3 days
C3	No Leave Group option — Zero way to voluntarily exit once matched	Users feel trapped, immediate uninstall
C4	No inactive user detection — No lastSeen, no inactivity badge in Soul Web, 3 players don't know the 4th is gone	Engaged users lose interest
C5	Queue entry never cleaned up — 

leaveQueue()
 is never called from any UI button	Stale entries pollute all matchmaking
C6	Can be in 2 groups simultaneously — Offline app relaunch can bypass the existingGroupId guard	Complete data corruption
🟠 High (Seriously Damaging)
ID	Issue
H1	No Report/Block — 3 people can't kick a toxic 4th person
H2	Can't switch queue batch / request fresh room
H3	Ceremony gate hangs forever if 1 person never shows (timeout not implemented)
H4	Days never auto-advance — Core 3-day story arc never plays out
H5	No push notifications — Users forget the app after 4 hours
H6	Non-binary users are permanently excluded from matchmaking
🟡 Medium / 🟢 Low
Another 10 medium + 6 low items are documented — WYR results, SlowChat pagination, Answer Card refresh, Party Island soft-lock, dev buttons in production, etc.

The full detailed report with root causes, all 26 items, and a 14-step priority implementation order is in the artifact above.

Reply with the IDs you want me to start implementing — e.g. "Start with C1, C2, C3" or "do all Critical ones first."

