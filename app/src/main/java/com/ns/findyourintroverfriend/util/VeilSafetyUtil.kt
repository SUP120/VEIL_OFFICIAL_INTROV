package com.ns.findyourintroverfriend.util

// ─────────────────────────────────────────────────────────────────────────────
//  VeilSafetyUtil — App-wide content moderation & policy text
//  Used by: SwipeProfileCreationScreen, IntrovertFeedScreen,
//           ConnectionRoomScreen, IntrovertFeedViewModel
// ─────────────────────────────────────────────────────────────────────────────

object VeilSafetyUtil {

    // ── Community Guidelines (shown in chat + post create) ────────────────────
    val COMMUNITY_GUIDELINES = """
🛡️ Veil Community Rules

We built Veil for genuine introverts to connect safely. Please read:

1. Be yourself — Misrepresenting your gender, age, or identity is an instant permanent ban with no appeal.

2. No harassment — Any threatening, sexual, or abusive messages will result in an immediate ban.

3. No soliciting — Asking for money, personal contact details, or directing people to external platforms is strictly prohibited.

4. No explicit content — Do not send or request explicit images, videos, or vulgar text.

5. Respect boundaries — If someone is uncomfortable, stop. No means no.

6. No impersonation — Pretending to be someone else (celebrity, other user) is a bannable offence.

7. Report, don't engage — If someone is misbehaving, use the 🚩 Report button. Do not retaliate.

Violations are reviewed by our team. Serious offences result in a permanent ban and may be reported to authorities if illegal activity is involved.

Thank you for keeping Veil safe. 💜
    """.trimIndent()

    // ── Gender Policy Notice ──────────────────────────────────────────────────
    val GENDER_POLICY = """
⚠️ Honest Identity Required

Veil is built on trust. You must represent your actual gender identity honestly.

Misrepresenting your gender — for any reason — violates our Terms of Service and results in a permanent, non-appealable account ban.

This includes: pretending to be a different gender to manipulate matches, catfishing, or using another person's identity.

By continuing, you confirm that the gender you select is your real identity.
    """.trimIndent()

    // ── Chat First-Time Rules ─────────────────────────────────────────────────
    val CHAT_RULES = """
💬 Connection Room Rules

You've unlocked a private conversation. Please keep it respectful:

• No asking for money or payments
• No sending explicit or vulgar content  
• No sharing personal contact info (phone, Instagram, etc.) to bypass Veil
• No threatening or abusive language
• No impersonating someone else

🚨 Violations = Instant permanent ban

Use the 🚩 Report button anytime if you feel uncomfortable. Reports are reviewed by our team within 24 hours.

This is a safe space — help us keep it that way.
    """.trimIndent()

    // ── Profanity Filter ──────────────────────────────────────────────────────
    // Intentionally kept mild — client-side only, not a replacement for server moderation.
    // These are the most commonly reported terms that violate our content policy.
    private val BLOCKED_TERMS = setOf(
        // English
        "fuck", "f*ck", "fck", "shit", "sh1t", "ass", "asshole", "bitch", "b1tch",
        "dick", "d1ck", "cock", "c0ck", "pussy", "cunt", "c*nt", "slut", "sl*t",
        "whore", "wh0re", "nigger", "nigga", "faggot", "retard",
        // Soliciting / scam patterns
        "send money", "transfer money", "cashapp", "venmo", "paytm", "phonepay",
        "gpay me", "pay me", "i need money", "send me ₹", "send me $",
        "my instagram", "my snapchat", "my telegram", "my whatsapp", "add me on",
        // Hindi / Hinglish common vulgar
        "madarchod", "mc", "bhenchod", "bc", "chutiya", "gandu", "randi", "bhosdike",
        "harami", "kutta", "saala", "lauda", "loda"
    )

    /**
     * Returns true if [text] contains any blocked term (case-insensitive).
     * Checks whole words and common leet substitutions.
     */
    fun containsProfanity(text: String): Boolean {
        val lower = text.lowercase().trim()
        return BLOCKED_TERMS.any { term -> lower.contains(term) }
    }

    /**
     * Returns a sanitized version of [text] with blocked terms replaced by ****,
     * for display purposes only (e.g. in admin panel previews).
     */
    fun censor(text: String): String {
        var result = text.lowercase()
        BLOCKED_TERMS.forEach { term ->
            result = result.replace(term, "*".repeat(term.length))
        }
        return result
    }

    // ── Report Reasons (Post) ─────────────────────────────────────────────────
    val POST_REPORT_REASONS = listOf(
        "Vulgar / explicit content",
        "Hate speech or discrimination",
        "Harassment or bullying",
        "Spam or advertisement",
        "Impersonation",
        "Suicide or self-harm content",
        "Other"
    )

    // ── Report Reasons (Chat) ─────────────────────────────────────────────────
    val CHAT_REPORT_REASONS = listOf(
        "Asking for money / financial scam",
        "Sending explicit or sexual content",
        "Harassment, threats, or bullying",
        "Hate speech or discrimination",
        "Pretending to be a different gender or age",
        "Sharing contact info to bypass Veil (Instagram, WhatsApp, etc.)",
        "Impersonating another person",
        "Emotional manipulation or grooming",
        "Spam or bot-like behaviour",
        "Suspicion of being a minor",
        "Racist or casteist language",
        "Suicide or self-harm content",
        "Other (describe below)"
    )
}
