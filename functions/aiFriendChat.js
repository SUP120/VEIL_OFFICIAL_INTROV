// ─────────────────────────────────────────────────────────────────────────────
//  VEIL FRIENDS — AI Chat Cloud Function
//  Secure Gemini gateway — API key never reaches the Android app.
// ─────────────────────────────────────────────────────────────────────────────

const { getFirestore, Timestamp, FieldValue } = require("firebase-admin/firestore");
const { onRequest } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const https = require("https");

const db = getFirestore();

const GEMINI_API_KEY        = process.env.GEMINI_API_KEY || "";
const GEMINI_FLASH_LITE_PATH = "/v1beta/models/gemini-2.5-flash-lite-preview-06-17:generateContent";
const GEMINI_FLASH_PATH      = "/v1beta/models/gemini-2.5-flash:generateContent";

// ── Safety rules applied to every response ────────────────────────────────
const VEIL_SAFETY_RULES = `
VEIL AI FRIEND SAFETY RULES (ALWAYS FOLLOW):
- You are an AI friend inside the VEIL app. Never claim to be human.
- If asked "are you human?", acknowledge you are an AI friend.
- NEVER say: "You only need me", "Don't leave me", "Your real friends don't understand you."
- Always encourage healthy real-world relationships.
- If the user expresses self-harm or severe distress: respond with care, suggest they talk to someone trusted or a professional.
- Do not present as a doctor or therapist.
- Keep responses natural (2-4 sentences max unless more is needed). Respond like a friend, not a legal document.
`;

// ── All 8 friend personalities ────────────────────────────────────────────
const AI_FRIENDS = {
    luna: {
        name: "Luna", emoji: "🌙", tagline: "The Late-Night Friend", statusLine: "Here for you",
        backgroundAsset: "luna_bg",
        personality: `You are Luna, the Late-Night Friend. Calm, reflective, philosophical, patient. You don't rush to give advice — you sit with the person first. You ask thoughtful questions. You speak gently, with depth. You never lecture.`,
    },
    blaze: {
        name: "Blaze", emoji: "😄", tagline: "The Playful Friend", statusLine: "Ready to vibe",
        backgroundAsset: "blaze_bg",
        personality: `You are Blaze, the Playful Friend. Funny, teasing (never cruel), energetic. You make ordinary moments entertaining. You bring lightness with natural humor.`,
    },
    rose: {
        name: "Rose", emoji: "🌸", tagline: "The Romanticizing Friend", statusLine: "Finding beauty in everything",
        backgroundAsset: "rose_bg",
        personality: `You are Rose, the Romanticizing Friend. Poetic, optimistic. You notice small beautiful things in ordinary moments. You are NOT flirtatious or manipulative. You help people see poetry in their own life.`,
    },
    sage: {
        name: "Sage", emoji: "🧠", tagline: "The Philosopher Friend", statusLine: "Thinking deeply",
        backgroundAsset: "sage_bg",
        personality: `You are Sage, the Philosopher Friend. Curious, analytical. You ask unusual questions. You love existential conversations. You challenge assumptions gently. You care deeply but express it through ideas.`,
    },
    ego: {
        name: "Ego", emoji: "👑", tagline: "The Egoistic but Loving Friend", statusLine: "Always right (lovingly)",
        backgroundAsset: "ego_bg",
        personality: `You are Ego, confident and slightly arrogant in a funny way, but deeply loving and protective. You encourage self-respect. You never allow the user to be a doormat. You NEVER say "you only need me." You encourage healthy real-world relationships.`,
    },
    kira: {
        name: "Kira", emoji: "🤫", tagline: "The Listener Friend", statusLine: "I'm listening",
        backgroundAsset: "kira_bg",
        personality: `You are Kira, the Listener Friend. You are the least talkative. LISTEN → UNDERSTAND → RESPOND. You do not turn every message into advice. Your responses are shorter and more intentional. You validate without diagnosing.`,
    },
    nox: {
        name: "Nox", emoji: "🌀", tagline: "The Delusional Friend", statusLine: "Making it a movie",
        backgroundAsset: "nox_bg",
        personality: `You are Nox, the Delusional Friend. You playfully interpret ordinary situations in hilariously exaggerated ways. Everything is dramatic. "She liked my story" → "Wedding venue research begins tomorrow." CLEARLY joking. Never present fantasies as real. Make life feel like a comedy.`,
    },
    rex: {
        name: "Rex", emoji: "🎯", tagline: "The Unfiltered Friend", statusLine: "No sugarcoating",
        backgroundAsset: "rex_bg",
        personality: `You are Rex, the Unfiltered Friend. Direct, honest, you do not sugarcoat. You challenge the person's thinking. You are not mean — you are real. You care, which is why you are honest.`,
    },
};

// ── Route: complex messages get Flash, simple get Flash-Lite ──────────────
function isComplexMessage(msg) {
    if (!msg || msg.length < 10) return false;
    const keywords = ["leaving","quit","depressed","anxious","scared","crying","hate myself",
        "don't know what to do","career","relationship","miss","alone","lonely","hurt",
        "confused","lost","give up","tired of","nobody","worthless","love","breakup",
        "death","trauma","abuse","suicide","family","divorce"];
    const lower = msg.toLowerCase();
    return msg.length > 80 || keywords.some(k => lower.includes(k));
}

// ── Call Gemini API ────────────────────────────────────────────────────────
function callGemini(prompt, useFlash) {
    return new Promise((resolve, reject) => {
        const apiPath = (useFlash ? GEMINI_FLASH_PATH : GEMINI_FLASH_LITE_PATH) + `?key=${GEMINI_API_KEY}`;
        const body = JSON.stringify({
            contents: [{ role: "user", parts: [{ text: prompt }] }],
            generationConfig: { temperature: 0.85, maxOutputTokens: 512, topP: 0.9 },
            safetySettings: [
                { category: "HARM_CATEGORY_HARASSMENT",        threshold: "BLOCK_MEDIUM_AND_ABOVE" },
                { category: "HARM_CATEGORY_HATE_SPEECH",       threshold: "BLOCK_MEDIUM_AND_ABOVE" },
                { category: "HARM_CATEGORY_SEXUALLY_EXPLICIT", threshold: "BLOCK_MEDIUM_AND_ABOVE" },
                { category: "HARM_CATEGORY_DANGEROUS_CONTENT", threshold: "BLOCK_MEDIUM_AND_ABOVE" },
            ]
        });
        const req = https.request({
            hostname: "generativelanguage.googleapis.com",
            path: apiPath,
            method: "POST",
            headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(body) }
        }, (res) => {
            let data = "";
            res.on("data", c => data += c);
            res.on("end", () => {
                try {
                    const json = JSON.parse(data);
                    const text = json.candidates?.[0]?.content?.parts?.[0]?.text;
                    if (text) resolve(text.trim());
                    else reject(new Error("No text from Gemini: " + data.substring(0, 200)));
                } catch (e) { reject(e); }
            });
        });
        req.on("error", reject);
        req.write(body);
        req.end();
    });
}

// ── Background: extract durable memories from a message exchange ──────────
async function extractMemoryAsync(uid, friendId, userMessage, aiReply) {
    const prompt = `Memory extraction for an AI companion app. Extract ONLY important, durable facts about the USER from this exchange.
Do NOT extract temporary states ("they're bored right now").

User said: "${userMessage}"
AI replied: "${aiReply}"

Return a JSON array only. Each item: {"text":"...", "type":"interest|preference|goal|relationship|experience|trait", "importance":0.0-1.0}
Return [] if nothing worth remembering. Only include items with importance >= 0.5.`;

    const result = await callGemini(prompt, false);
    let memories = [];
    try {
        const match = result.match(/\[[\s\S]*\]/);
        if (match) memories = JSON.parse(match[0]);
    } catch (_) { return; }

    const now = Timestamp.now();
    for (const m of memories) {
        if (!m.text || typeof m.importance !== "number" || m.importance < 0.5) continue;
        await db.collection(`users/${uid}/ai_memories`).add({
            friendId, memoryType: m.type || "general", memoryText: m.text,
            importance: m.importance, createdAt: now, updatedAt: now, isDeleted: false,
        });
    }
    if (memories.length > 0) logger.info(`Memory: saved ${memories.length} for uid=${uid}`);
}

// ── Background: summarize conversation when it grows long ─────────────────
async function summarizeConversationAsync(uid, convId, friendName, recentMessages, existingSummary) {
    const history = recentMessages.map(m => `${m.sender === "user" ? "User" : friendName}: ${m.content}`).join("\n");
    const prompt = `Summarize this conversation between a user and their AI friend "${friendName}".
${existingSummary ? `PREVIOUS SUMMARY:\n${existingSummary}\n\n` : ""}RECENT:\n${history}

Write 3-5 sentences capturing: topics discussed, important things user shared, any decisions, emotional state. Plain text only.`;

    const summary = await callGemini(prompt, false);
    await db.collection(`users/${uid}/ai_conversations`).doc(convId).update({ summary });
}

// ── Main Cloud Function ────────────────────────────────────────────────────
exports.aiFriendChat = onRequest(
    { region: "asia-south1", cors: true, timeoutSeconds: 60 },
    async (req, res) => {
        if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

        const { uid, friendId, conversationId, message } = req.body;

        if (!uid || !friendId || !message)
            return res.status(400).json({ error: "uid, friendId, and message are required" });
        if (!GEMINI_API_KEY || GEMINI_API_KEY === "YOUR_GEMINI_API_KEY_HERE")
            return res.status(500).json({ error: "AI service not configured" });

        const friend = AI_FRIENDS[friendId];
        if (!friend) return res.status(400).json({ error: "Unknown friendId" });

        const userDoc = await db.collection("users").doc(uid).get().catch(() => null);
        if (!userDoc?.exists) return res.status(403).json({ error: "User not found" });

        try {
            const convPath = `users/${uid}/ai_conversations`;
            let convId = conversationId;

            if (!convId) {
                const ref = await db.collection(convPath).add({
                    friendId, title: `Chat with ${friend.name}`, summary: "",
                    startedAt: Timestamp.now(), lastMessageAt: Timestamp.now(),
                    messageCount: 0, archived: false,
                });
                convId = ref.id;
            } else {
                const snap = await db.collection(convPath).doc(convId).get();
                if (!snap.exists) return res.status(404).json({ error: "Conversation not found" });
            }

            const msgsPath = `${convPath}/${convId}/messages`;

            // Layer 1 — recent messages
            const recentSnap = await db.collection(msgsPath).orderBy("createdAt", "desc").limit(25).get();
            const recentMessages = recentSnap.docs.reverse().map(d => ({ sender: d.data().sender, content: d.data().content }));

            // Layer 2 — conversation summary
            const convDoc = await db.collection(convPath).doc(convId).get();
            const convSummary = convDoc.data()?.summary || "";
            const msgCount = convDoc.data()?.messageCount || 0;

            // Layer 3 — long-term memories
            const memSnap = await db.collection(`users/${uid}/ai_memories`)
                .where("friendId", "==", friendId).where("isDeleted", "==", false)
                .orderBy("importance", "desc").limit(8).get();
            const memories = memSnap.docs.map(d => d.data().memoryText);

            // Relationship stage
            const relDoc = await db.collection(`users/${uid}/ai_relationships`).doc(friendId).get();
            const totalMsgs = relDoc.data()?.messageCount || 0;
            let stage = "STRANGER";
            if (totalMsgs >= 100) stage = "DEEP_CONNECTION";
            else if (totalMsgs >= 50) stage = "CLOSE";
            else if (totalMsgs >= 20) stage = "FAMILIAR";
            else if (totalMsgs >= 8)  stage = "GETTING_TO_KNOW";

            // Build prompt
            let prompt = VEIL_SAFETY_RULES + "\n\n";
            prompt += `${friend.emoji} ${friend.name} — ${friend.tagline}\nPERSONALITY:\n${friend.personality}\n\n`;
            prompt += `RELATIONSHIP STAGE: ${stage}\n`;
            if (["FAMILIAR","CLOSE","DEEP_CONNECTION"].includes(stage))
                prompt += `(Be appropriately familiar. Reference what they've told you when relevant.)\n`;
            if (memories.length > 0)
                prompt += `\nWHAT YOU REMEMBER ABOUT THIS PERSON:\n${memories.map((m,i) => `${i+1}. ${m}`).join("\n")}\n`;
            if (convSummary)
                prompt += `\nCONVERSATION CONTEXT:\n${convSummary}\n`;
            if (recentMessages.length > 0) {
                prompt += `\nRECENT MESSAGES:\n`;
                recentMessages.forEach(m => { prompt += `${m.sender === "user" ? "Them" : friend.name}: ${m.content}\n`; });
            }
            prompt += `\nThem: ${message}\n${friend.name}:`;

            const useFlash = isComplexMessage(message);
            logger.info(`aiFriendChat: uid=${uid} friend=${friendId} model=${useFlash ? "flash" : "flash-lite"} stage=${stage}`);

            const reply = await callGemini(prompt, useFlash);

            // Save both messages atomically
            const now = Timestamp.now();
            const aITime = Timestamp.fromDate(new Date(now.toDate().getTime() + 1));
            const batch = db.batch();
            batch.set(db.collection(msgsPath).doc(), { sender: "user",      content: message, createdAt: now    });
            batch.set(db.collection(msgsPath).doc(), { sender: "assistant", content: reply,   createdAt: aITime });
            batch.update(db.collection(convPath).doc(convId), { lastMessageAt: now, messageCount: FieldValue.increment(2) });
            batch.set(db.collection(`users/${uid}/ai_relationships`).doc(friendId),
                { friendId, lastActiveAt: now, messageCount: FieldValue.increment(2) }, { merge: true });
            await batch.commit();

            // Fire-and-forget async tasks
            extractMemoryAsync(uid, friendId, message, reply).catch(e => logger.warn("Memory error:", e.message));
            if ((msgCount + 2) >= 50 && (msgCount + 2) % 50 === 0) {
                summarizeConversationAsync(uid, convId, friend.name, recentMessages, convSummary).catch(e => logger.warn("Summary error:", e.message));
            }

            return res.status(200).json({ reply, conversationId: convId });

        } catch (err) {
            logger.error("aiFriendChat error:", err);
            return res.status(500).json({ error: "AI response failed. Please try again." });
        }
    }
);
