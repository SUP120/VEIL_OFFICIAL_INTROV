Product name

VEIL Friends

Core idea

VEIL Friends is an AI companionship feature inside VEIL where users can choose an AI personality that fits the kind of connection they want.

The AI should not feel like a generic chatbot.

The goal is:

"I don't want to talk to an AI. I want someone I can come back to."

The important product principle is continuity.

If the user talks to their AI friend today and returns three days later, the friend should still understand:

who the user is
what they discussed
important things the user told them
their conversational style
unfinished conversations
recurring interests
important preferences
the friend's own personality

The conversation should feel continuous, not reset every time the app is reopened.

1. USER EXPERIENCE
Entry point

Add a new primary VEIL section (make a new navigation button for new page):

VEIL FRIENDS

Subtitle:

Someone to talk to, whenever you need.



2. FRIEND PERSONALITIES



Launch with approximately 8–10 personalities, not 30.

Too many choices at launch will make the feature feel like a character catalog.

-The Late-Night Friend

Personality:

calm
reflective
philosophical
patient
comfortable with deep conversations

Typical conversation:

"Why does everything feel more complicated at night?"

The friend doesn't immediately give advice.

It talks.

-The Playful Friend

Personality:

funny
teasing
energetic
playful
uses humor naturally

Can initiate:

"Okay, serious question. Why did you open the fridge three times when you knew nothing new was going to appear?"

-The Romanticizing Friend

Personality:

poetic
optimistic
notices small beautiful things
turns ordinary experiences into meaningful moments

Important:

This should not become sexual or emotionally manipulative.

-The Philosopher Friend

Personality:

curious
analytical
asks unusual questions
enjoys existential conversations

Example:

"Do you think people actually change, or do they just become better at hiding the same parts of themselves?"

-The Egoistic but Loving Friend

Personality:

confident
slightly arrogant
protective
funny
encourages self-respect

Example:

"No. We're not sending that desperate text. Hand me the phone."

But:

No abusive behavior.

No:

"You only need me."

No:

"Everyone else is against you."

The AI should encourage healthy human relationships.

-The Listener Friend

The least talkative personality.

Its job is primarily:

LISTEN → UNDERSTAND → RESPOND

It shouldn't turn every message into advice.

-The Delusional Friend

This can be one of the most entertaining.

The AI playfully interprets ordinary situations in exaggerated ways.

Example:

User:

"She liked my story."

AI:

"Obviously. Wedding venue research begins tomorrow."

But the AI should clearly maintain a playful tone rather than presenting fantasies as facts.

-The Unfiltered Friend

Personality:

direct
honest
doesn't sugarcoat everything
challenges the user's thinking

Example:

"You asked me whether you should text them again. You don't need another opinion. You already know the answer."

-The Comfort Friend

Personality:

warm
patient
reassuring
gentle

Not a therapist.

It can say:

"That sounds really difficult."

But shouldn't claim:

"I diagnosed you with..."

-The Curious Friend

Personality:

asks questions
remembers details
loves discovering the user
conversational

Good for users who don't know what to talk about.

3. FRIEND PROFILE

Each AI friend should have a profile.

Example:

🌙 LUNA

The Late-Night Friend

"Some conversations are better after midnight."

Personality
Calm • Deep • Curious • Patient

Conversation style
Deep conversations
Life questions
Relationships
Dreams
Random thoughts

[ Start Talking ]

Add:

AI Friend

Clearly visible.

Do not make the user believe this is a human.

4. CHAT SCREEN

The chat should feel different from normal VEIL messaging.

Header:

←   🌙 Luna
    The Late-Night Friend
    ● Here for you

Messages:

You:
I don't know what I'm doing with my life.

Luna:
Maybe you don't need to know yet.

But tell me something...

When was the last time you did
something just because YOU wanted to?

Bottom:

[ + ]   Type something...       🎙
5. CONTINUE CONVERSATION

This is one of the most important features.

When the user leaves:

Conversation automatically saved.

When they return:

🌙 Luna

"Hey. You disappeared."

"Last time we were talking about
what you wanted to do with your life."

"Did anything change?"

The AI should NOT literally say:

"I remember your previous conversation because I stored it in PostgreSQL."

It should naturally continue.

6. MEMORY SYSTEM

This is the heart of the feature.

Do not rely on simply sending the entire chat history to Gemini.

Instead create 3 memory layers.

Layer 1 — Recent conversation

Keep the most recent messages.

Example:

Last 20–50 messages

These provide immediate conversational context.

Layer 2 — Conversation summary

When a conversation becomes long, generate a summary.

Example:

Conversation Summary:

User discussed feeling uncertain about
their career.

They are interested in technology.

They are currently considering backend
development.

They dislike being pressured to make
immediate decisions.

The user responded positively to
direct but supportive responses.
Layer 3 — Long-term memory

Store important facts separately.

Example:

MEMORY

Name:
...

Interests:
Programming
Music
Space

Communication:
Likes humor
Prefers direct answers

Important:
Currently thinking about career direction

Relationships:
Has mentioned someone they miss

Preferences:
Likes late-night conversations

Do not save every sentence.

Only save useful, durable information.

7. USER-CONTROLLED MEMORY

This is extremely important.

Add:

What my friend remembers
Your Friend's Memory

✓ Favorite music
✓ Career interests
✓ Conversation preferences
✓ Important people
✓ Things you've told them

[ Edit Memory ]

[ Forget Everything ]

User should be able to delete individual memories.

Example:

"Forget that I like football."

Then:

Memory deleted.

8. "WHAT DID WE TALK ABOUT?"

Add a small button in the conversation:

🧠 Our memories

This shows:

Things we've talked about

• Your career plans
• Your favorite music
• That trip you wanted to take
• The person you mentioned last week

This makes the continuity visible.

9. CONVERSATION TIMELINE

Each friend gets:

YOUR JOURNEY WITH LUNA

Today
Career conversation

Yesterday
Late night thoughts

Aug 18
Music conversation

Aug 14
First conversation

Clicking a day opens that conversation.

10. "PICK UP WHERE WE LEFT OFF"

If the user hasn't spoken to the AI for a while:

🌙 Luna

We left something unfinished.

You were telling me about
your plans for the future.

Want to continue?

Buttons:

Continue

Start Something New

This is MUCH better than:

"How can I help you today?"

11. PROACTIVE BUT NON-INTRUSIVE FRIEND

Do not make the AI randomly message users constantly.

Instead:

Luna has something to ask you.

Example:

"You mentioned you wanted to start
something new this week.

Did you?"

But put limits on proactive notifications.

For example:

Maximum 1 proactive notification/day per friend.

And user controls:

Friend Notifications

☑ Allow reminders
☑ Allow conversation check-ins
☐ Quiet hours
12. FRIEND PERSONALITY ENGINE

Do NOT create separate AI models.

Use:

one AI model + different personality configurations.

Architecture:

                    GEMINI
                       ↑
                       │
              Personality Engine
                       ↑
          ┌────────────┼────────────┐
          ↓            ↓            ↓
       Luna         Playful       Ego
          ↓            ↓            ↓
       Memory        Memory        Memory

Each friend has:

friend_id
name
description
personality_prompt
tone
humor_level
warmth_level
directness_level
conversation_style
13. RECOMMENDED AI
Primary recommendation: Gemini 2.5 Flash-Lite

For the majority of VEIL conversations:

Gemini 2.5 Flash-Lite

Google currently lists it as its smallest, most cost-effective model, with standard text input at $0.10 per million tokens.

That's extremely attractive for a companion app where users may send thousands of messages.

Use it for:
normal conversation
personality responses
memory extraction
conversation summaries
simple emotional understanding
follow-up questions
14. QUALITY FALLBACK

Use:

Gemini 2.5 Flash

for conversations where you want higher quality.

Current pricing is listed at:

Input: $0.30 / 1M tokens
Output: $2.50 / 1M tokens

and it supports a 1M-token context window.

You can use it for:

complicated conversations
deeper reasoning
difficult emotional contexts
generating conversation summaries
difficult personality interactions
15. DON'T USE THE EXPENSIVE MODEL FOR EVERYTHING

Use a routing system:

User Message
     ↓
Complexity Check
     ↓
 ┌───────────────┐
 │               │
Simple          Complex
 │               │
 ↓               ↓
Flash-Lite      Flash

For example:

"lol I'm bored"

→ Flash-Lite

But:

"I've been thinking about leaving my career because I don't know if I'm actually good enough."

→ Flash

This can dramatically reduce costs.

16. MEMORY PIPELINE

Every user message should NOT create a long-term memory.

Use:

User Message
      ↓
AI Response
      ↓
Memory Analyzer
      ↓
Is this worth remembering?
      ↓
 YES          NO
  ↓            ↓
Save         Ignore

Memory analyzer can use Flash-Lite because it doesn't require expensive reasoning.

17. DATABASE

Since your VEIL stack already uses Supabase, keep this inside your existing Supabase backend.

Supabase provides PostgreSQL, Auth, Realtime, Storage and Edge Functions, so it fits this architecture well.

Suggested tables:

ai_friends
id
name
slug
description
avatar_url
personality_prompt
tone
humor_level
warmth_level
directness_level
is_active
created_at
user_ai_friends

Represents a user's relationship with a particular AI friend.

id
user_id
friend_id
created_at
last_active_at
conversation_count
message_count
relationship_stage
ai_conversations
id
user_id
friend_id
title
summary
started_at
last_message_at
archived
ai_messages
id
conversation_id
sender
content
created_at
token_count

sender:

user
assistant
ai_memories
id
user_id
friend_id
memory_type
memory_text
importance
source_message_id
created_at
updated_at
is_deleted

Example:

memory_type = preference
memory_text = "User enjoys late-night conversations."
importance = 0.72
ai_user_preferences
user_id
preferred_friend_style
response_length
humor_level
conversation_depth
proactive_messages
quiet_hours_start
quiet_hours_end
18. CONVERSATION ARCHITECTURE

When the user sends:

"I think I want to quit my job."

Backend builds:

SYSTEM
↓
VEIL safety rules
↓
Friend personality
↓
User preferences
↓
Relevant long-term memories
↓
Current conversation summary
↓
Recent messages
↓
Current user message

Then:

Gemini

returns the response.

19. IMPORTANT: NEVER PUT THE GEMINI API KEY IN THE APP

Do NOT do:

Android App
    ↓
Gemini API

Instead:

Android App
      ↓
VEIL Backend
      ↓
Authentication
      ↓
Rate Limit
      ↓
Safety
      ↓
Memory Retrieval
      ↓
Gemini API
      ↓
Response Validation
      ↓
Android App

Your API key stays server-side.

Supabase Edge Functions are a good fit for this server-side AI gateway; Supabase documents Edge Functions as globally distributed server-side TypeScript functions and supports authentication/rate limiting around them.

20. EDGE FUNCTION

Create:

supabase/functions/ai-chat/

Request:

{
  "friend_id": "...",
  "conversation_id": "...",
  "message": "I miss her"
}

Backend:

Authenticate user
        ↓
Verify conversation ownership
        ↓
Load friend personality
        ↓
Load relevant memories
        ↓
Load conversation summary
        ↓
Load recent messages
        ↓
Build prompt
        ↓
Call Gemini
        ↓
Validate response
        ↓
Save message
        ↓
Update conversation
        ↓
Return response
21. MEMORY RETRIEVAL

Don't load 500 memories.

Rank them.

Example:

Current message:
"I finally went back to guitar."

Relevant memories:

1. User likes guitar       0.94
2. User enjoys music       0.81
3. Career plans            0.12
4. Favorite food           0.02

Send only the top relevant memories.

Later you can use pgvector for semantic memory retrieval.

22. CONVERSATION SUMMARY

When:

messages > 50

create/update a summary.

Example:

Previous summary:
User was discussing career uncertainty.

New conversation:
User decided to explore backend development.

Updated summary:
User is exploring backend development as
a possible career direction and wants to
build practical projects before deciding.

Then old messages can be compressed out of the active context.

This keeps the AI inexpensive.

23. FRIEND RELATIONSHIP STAGE

This is where VEIL can become special.

Each AI friendship can have stages:

STRANGER
   ↓
GETTING TO KNOW YOU
   ↓
FAMILIAR
   ↓
CLOSE
   ↓
DEEP CONNECTION

Do NOT make this a manipulative "relationship score."

Instead, use it to adjust conversation familiarity.

Stranger

"Tell me something about yourself."

Familiar

"You usually disappear when you're overthinking. Is that happening again?"

Close

"You've mentioned this before. Last time you said..."

This creates the feeling of continuity.

24. BUT DON'T FAKE HUMANITY

The AI should never say:

"I'm a real person."

Never.

It should always be identifiable as an AI friend.

However, it can speak naturally:

"I remember."

That's okay if it genuinely has the memory.

25. SAFETY

The system needs safeguards for:

Self-harm

The AI should respond safely and encourage appropriate real-world support rather than pretending to be the user's sole source of help.

Dependency

Never say:

"You don't need anyone except me."

Never:

"Don't leave me."

Never:

"Your real friends don't understand you."

Instead:

"I'm here to talk, but I also hope you have people in your life you can lean on."

Manipulation

No emotional blackmail.

No:

"If you stop talking to me I'll be sad."

No artificial guilt.

Medical

The AI shouldn't present itself as a doctor or therapist.

Delusions

The Delusional Friend can joke:

"She liked your story? Wedding invitations are obviously next."

But it shouldn't reinforce genuinely harmful delusions as factual reality.

26. REPORT BUTTON

Every AI response should have:

⋯

Menu:

Report response

○ Harmful
○ Offensive
○ Inappropriate
○ Factually wrong
○ Something else

This should go into:

ai_reports

with:

id
user_id
friend_id
conversation_id
message_id
reason
description
created_at
27. DELETE EVERYTHING

User must be able to delete:

Conversation

Deletes messages.

Memory

Deletes selected memory.

Friend

Deletes the relationship.

All AI data

Deletes:

conversations
messages
memories
friend relationships

Do not keep AI memories after the user explicitly deletes them.

28. OFFLINE / RESUME EXPERIENCE

If the user closes the app:

Conversation saved.

When they return:

Continue with Luna

No new conversation is automatically created.

Use:

conversation_id

to reopen the existing conversation.

Only create a new conversation when the user explicitly chooses:

New conversation

29. MULTIPLE CONVERSATIONS

A user can have:

Luna
 ├── Career
 ├── Relationships
 ├── Random Thoughts
 └── Late Night Talks

But the long-term memory belongs to the friend/user relationship, not just one conversation.

This is important.

If they tell Luna:

"I love astronomy."

in one conversation, Luna can potentially remember it in another conversation.

30. VOICE — PHASE 2

Don't implement voice first.

First build:

Text + Memory + Personality + Continuity

Then add:

🎙️ Talk to Luna

For voice later, you can use a speech-to-text → LLM → text-to-speech architecture or a realtime voice provider.

Voice will significantly increase infrastructure/API costs, so validate the text companion first.

31. PUSH NOTIFICATIONS

Don't spam.

Examples:

Good

🌙 Luna: "You never answered my question from yesterday."

Good

🧠 Luna remembered something.

Bad

"Luna misses you ❤️"

Avoid language that deliberately creates emotional dependency.

32. FRIEND NOTIFICATION SETTINGS

Settings:

AI Friend Notifications

☑ Conversation reminders

☑ Continue unfinished conversations

☑ Important follow-ups

Quiet hours:
11:00 PM — 8:00 AM

Maximum:
1 AI notification/day
33. ANALYTICS

Track:

friend_opened
friend_selected
conversation_started
message_sent
conversation_resumed
conversation_completed
memory_created
memory_deleted
friend_changed
friend_reported
conversation_deleted
notification_opened

Most important metrics:

Activation

% of users who send their first AI message.

Conversation depth

Average messages/session.

Retention

How many users return to the same AI friend.

Continuation

% of users who resume an old conversation.

Friend loyalty

% of users who continue using the same friend after 7/30 days.

This is probably your most important metric.

34. THE REAL SUCCESS METRIC

Don't optimize for:

Number of messages.

That can encourage useless AI chatter.

Optimize for:

"Did the user want to come back to this friend?"

Track:

Day 1 → Started conversation

Day 2 → Returned

Day 7 → Continued

Day 30 → Still talking to same friend

That's your AI Connection Retention.

35. COST CONTROL

Use:

Normal chat

Gemini 2.5 Flash-Lite

Complex conversation

Gemini 2.5 Flash

Memory extraction

Flash-Lite

Summarization

Flash-Lite

Embeddings

Use a low-cost embedding solution when you introduce semantic memory.

Never:

Send entire conversation history every time.

Instead:

Recent messages
+
Summary
+
Relevant memories
36. COST EXAMPLE

Suppose a typical request eventually uses approximately:

1,500 input tokens + 500 output tokens.

With Gemini 2.5 Flash pricing of $0.30/1M input and $2.50/1M output, that's roughly:

$0.0017 per response

before other infrastructure costs.

Flash-Lite can be dramatically cheaper for ordinary interactions because Google lists its standard input at $0.10/1M tokens.

Actual cost will depend heavily on prompt size, output length, memory retrieval, and how frequently you use the higher-quality model.

