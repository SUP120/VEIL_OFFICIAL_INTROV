package com.ns.findyourintroverfriend.util

/**
 * ContentModerator — shared text-safety engine used by chat, questions, and admin.
 *
 * Checks text against:
 *  - Phone number patterns
 *  - Social media handle patterns
 *  - Sexual / explicit keywords (English + Hindi + Hinglish)
 *  - Hate speech / slurs (EN + HI)
 *  - Location / money / dating pressure keywords
 *  - Abusive general slang (Hinglish street slang)
 *  - Age-fishing / minor-related patterns
 *
 * Returns a [ModerationResult] with the verdict and a user-facing warning.
 *
 * UI note:
 * Display only → "Content is automatically reviewed for safety."
 * (No claims of AI, accuracy, or guarantees)
 */
object ContentModerator {

    data class ModerationResult(
        val isSafe: Boolean,
        val category: String? = null,
        val warningMessage: String? = null
    )

    // ── Phone number patterns ────────────────────────────────────────────────
    private val phoneRegex = Regex(
        """(?:(?:\+|00)?91[\s\-]?)?[6-9]\d{9}""" +      // Indian: +91 XXXXXXXXXX or 9XXXXXXXXX
        """|(\+?\d[\d\s\-]{8,14}\d)""" +                 // International long form
        """|(\d{3}[\s\-]?\d{3}[\s\-]?\d{4})""" +         // XXX-XXX-XXXX
        """|(\d{5}[\s]?\d{5})"""                          // XXXXX XXXXX
    )

    /**
     * Replaces any detected phone numbers in [text] with **********
     * Use this to silently sanitize outgoing messages rather than blocking them.
     */
    fun maskPhoneNumbers(text: String): String = phoneRegex.replace(text, "**********")

    // ── Social media patterns ────────────────────────────────────────────────
    private val socialPatterns = listOf(
        Regex("""(?i)\b(instagram|insta|ig)\b"""),
        Regex("""(?i)\b(snapchat|snap|sc)\b"""),
        Regex("""(?i)\b(whatsapp|whats\s?app|wtsapp|wa number)\b"""),
        Regex("""(?i)\b(telegram|tg|tele)\b"""),
        Regex("""(?i)\b(facebook|fb)\b"""),
        Regex("""(?i)\b(twitter|x\.com|xdotcom)\b"""),
        Regex("""(?i)\b(discord)\b"""),
        Regex("""(?i)\b(tiktok|tik\s?tok)\b"""),
        Regex("""(?i)\b(kik|hike|bumble|tinder|hinge)\b"""),
        Regex("""(?i)\b(youtube|yt channel)\b"""),
        Regex("""(?i)@[a-zA-Z0-9_.]{2,30}"""),
        Regex("""(?i)\b(dm\s+me|add\s+me|follow\s+me|text\s+me|ping me)\b"""),
        Regex("""(?i)\b(my id|mera id|apna id|id do|id dena|id loge)\b"""),
        // Phone-sharing intents
        Regex("""(?i)\b(my number|mera number|apna number|number do|call me|call karo|call kr)\b"""),
        Regex("""(?i)\b(whatsapp (pe|par|me)|wa pe|wa par)\b"""),
        Regex("""(?i)\b(contact (me|karo|kare)|bahar baat|bahar milte)\b"""),
    )

    // ── Sexual / explicit keywords ───────────────────────────────────────────

    private val sexualEnglish = setOf(
        "sex","sexy","nude","nudes","naked","porn","pornography",
        "boobs","boob","tits","tit","dick","cock","pussy","vagina",
        "penis","anal","blowjob","handjob","orgasm","cum","cumshot",
        "horny","milf","dildo","vibrator","fetish","bdsm",
        "erotic","xxx","onlyfans","hookup","hook up","fwb",
        "one night stand","smash","bang","wanna fuck","dtf",
        "send pics","send nudes","show me","strip","sext","sexting",
        "topless","nipple","nipples","cleavage",
        "masturbate","masturbation","kinky","threesome",
        "lingerie","underwear pic","body shot",
        "make love","sleep with","get laid","hardcore","escort",
        "call girl","paid sex","prostitute","makeout","french kiss",
        "dirty talk","roleplay","private video","cam sex"
    )

    private val sexualHindi = setOf(
        "chut","choot","lund","lauda","laude","gaand","gand",
        "bhosdike","bhosdi","randi","randwa",
        "chutiya","chutiye","madarchod","behenchod",
        "lodu","jhant","tatte",
        "nangi","nanga","jism","hawas","hawasi",
        "gandi baatein","ashleel","charitraheen",
        "kapde utaro","kapde nikal","nanga hona","mc", "bc", "mkb", 
        "mkc", "tmkc", "gand", "rand", "randi", "lwde", "lode",
        "fck", "lund", "aand", "rass", "chumt", "madarjaat", 
        "mkb"
    )

    private val sexualHinglish = setOf(
        "maal","item","patao","full maza","chipak","ghusna",
        "chod","chodna","thokna","lene do","dene do",
        "photo bhejo","pic bhejo","video bhejo",
        "live aao","camera on",
        "scene set hai","raat ko mil","bed pe aao",
        "touch kar","feel me","alone milte hain",
        "privacy chahiye","video call pe aao"
    )

    private val sexualKeywords =
        sexualEnglish + sexualHindi + sexualHinglish

    // ── Hate speech / slurs ──────────────────────────────────────────────────

    private val slursEnglish = setOf(
        "nigger","nigga","faggot","tranny","retard",
        "chink","kike","gook","coon","dyke","homo"
    )

    private val slursHindi = setOf(
        "chamaar","bhangi","kutta","kutiya",
        "harami","haramzada","kamina",
        "gandu","madarjaat","nalayak",
        "ghatiya","besharam","jahil","gawar"
    )

    private val slursHinglish = setOf(
        "chup kar","saala","saali","bhadwa",
        "apni aukaat","bakwaas band kar",
        "cheap mentality","disgusting",
        "dimag se paidal","tu pagal hai kya"
    )

    private val slurs =
        slursEnglish + slursHindi + slursHinglish

    // ── Location / money ─────────────────────────────────────────────────────
    private val locationMoneyKeywords = setOf(
        "address","location","where do you live","pin code",
        "ghar ka address","kahan rehti",
        "send money","paytm","gpay","phonepe","upi",
        "bank account","ifsc","scan karo","qr code",
        "meet me","hotel","room number","ghar pe aa"
    )

    // ── Dating pressure ──────────────────────────────────────────────────────
    private val datingPressureKeywords = setOf(
        "be my girlfriend","be my boyfriend","date me",
        "i love you","marry me","single?",
        "meri girlfriend ban","shaadi karoge",
        "commitment chahiye","exclusive relationship",
        "future together","tum meri ho"
    )

    // ── Age fishing / minors ─────────────────────────────────────────────────
    private val ageFishingKeywords = setOf(
        "how old are you","age bata","kitne saal",
        "are you 18","under 18","minor",
        "school name","which class",
        "bachi hai","chhoti hai"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Main check
    // ─────────────────────────────────────────────────────────────────────────

    fun check(text: String): ModerationResult {
        val lower = text.lowercase().trim()

        if (phoneRegex.containsMatchIn(lower)) {
            return ModerationResult(false,"phone_number",
                "⚠️ Sharing phone numbers is not allowed for your safety.")
        }

        for (p in socialPatterns) {
            if (p.containsMatchIn(lower)) {
                return ModerationResult(false,"social_media",
                    "⚠️ Sharing social media handles is not allowed.")
            }
        }

        for (k in sexualKeywords) {
            if (containsWord(lower,k)) {
                return ModerationResult(false,"sexual",
                    "⚠️ Sexual or explicit content is not allowed.")
            }
        }

        for (s in slurs) {
            if (containsWord(lower,s)) {
                return ModerationResult(false,"hate_speech",
                    "⚠️ Hate speech or abusive language is not allowed.")
            }
        }

        for (k in locationMoneyKeywords) {
            if (lower.contains(k)) {
                return ModerationResult(false,"location_money",
                    "⚠️ Sharing personal or financial details is not allowed.")
            }
        }

        for (k in datingPressureKeywords) {
            if (lower.contains(k)) {
                return ModerationResult(false,"dating_pressure",
                    "⚠️ This app is for friendship only.")
            }
        }

        for (k in ageFishingKeywords) {
            if (lower.contains(k)) {
                return ModerationResult(false,"age_fishing",
                    "⚠️ Asking about age or minors is not allowed.")
            }
        }

        return ModerationResult(true)
    }

    private fun containsWord(text: String, word: String): Boolean {
        return when {
            word.contains(" ") -> text.contains(word)
            word.length <= 3 ->
                Regex("""\b${Regex.escape(word)}\b""")
                    .containsMatchIn(text)
            else -> text.contains(word)
        }
    }
}