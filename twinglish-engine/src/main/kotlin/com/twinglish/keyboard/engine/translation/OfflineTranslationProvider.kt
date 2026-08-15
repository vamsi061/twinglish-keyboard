package com.twinglish.keyboard.engine.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline, rule-based English → Telugu translation provider.
 *
 * The engine is a "sentence grammar": a curated bank of conversational
 * sentence patterns (regular expressions over normalized English) that
 * produce natural Telugu script, followed by a handful of structurally
 * safe verb templates. Translation ALWAYS operates on the complete phrase
 * — there is deliberately NO word-by-word substitution path, because
 * token-by-token output ("which sinima nuvvu want") is unnatural and is
 * treated as worse than no suggestion at all.
 *
 * When no rule matches, the provider returns null and the caller shows the
 * original English (or nothing) instead of a broken partial translation.
 *
 * The keyboard only ever sees [TranslationProvider], so this baseline can
 * be replaced by a remote provider or a learned model later.
 */
class OfflineTranslationProvider : TranslationProvider {

    override val id: String = "offline-rules"
    override val isOnline: Boolean = false

    private data class Rule(val pattern: Regex, val telugu: String, val confidence: Float = 0.92f)

    // A sentence rule: pattern over normalized lowercase input → Telugu
    // template. Templates may reference regex groups ($1, $2 …). Each group
    // is passed through [fillSlot] first, which substitutes known nouns
    // (movie → సినిమా) while leaving proper nouns and code-switched words
    // untouched. Question rules carry their own trailing "?"; the
    // ContextualTranslator dedupes it against user-typed punctuation.
    private val rules: List<Rule> = listOf(
        // ---- what … ----
        Rule(Regex("^what are you doing today$"), "ఏం చేస్తున్నావు ఇవాళ?"),
        Rule(Regex("^what are you doing now$"), "ఏం చేస్తున్నావు ఇప్పుడు?"),
        Rule(Regex("^what are you doing tomorrow$"), "రేపు ఏం చేస్తున్నావు?"),
        Rule(Regex("^what are you doing$"), "ఏం చేస్తున్నావు?"),
        Rule(Regex("^what are you doing (tonight|this weekend|this evening)$"), "\$1 ఏం చేస్తున్నావు?", 0.8f),
        Rule(Regex("^what are you watching$"), "ఏం చూస్తున్నావు?"),
        Rule(Regex("^what are you reading$"), "ఏం చదువుతున్నావు?"),
        Rule(Regex("^what happened$"), "ఏమైంది?"),
        Rule(Regex("^what is your name$"), "నీ పేరు ఏంటి?"),
        Rule(Regex("^what is this$"), "ఇది ఏంటి?"),
        Rule(Regex("^what do you want$"), "నీకు ఏం కావాలి?"),
        Rule(Regex("^what about you$"), "నువ్వు ఎలా ఉన్నావు?"),
        Rule(Regex("^what did you eat$"), "ఏం తిన్నావు?"),

        // ---- how … ----
        Rule(Regex("^how are you$"), "ఎలా ఉన్నావు?"),
        Rule(Regex("^how are you doing$"), "ఎలా ఉన్నావు?"),
        Rule(Regex("^how is it going$"), "ఎలా ఉంది?"),
        Rule(Regex("^how much is this$"), "ఇది ఎంత?"),
        Rule(Regex("^how much$"), "ఎంత?"),

        // ---- where … ----
        Rule(Regex("^where are you going tomorrow$"), "రేపు ఎక్కడికి వెళ్తున్నావు?"),
        Rule(Regex("^where are you going today$"), "ఇవాళ ఎక్కడికి వెళ్తున్నావు?"),
        Rule(Regex("^where are you going$"), "ఎక్కడికి వెళ్తున్నావు?"),
        Rule(Regex("^where are you$"), "ఎక్కడ ఉన్నావు?"),
        Rule(Regex("^where do you live$"), "నువ్వు ఎక్కడ ఉంటున్నావు?"),
        Rule(Regex("^where did you go$"), "ఎక్కడికి వెళ్ళావు?"),
        Rule(Regex("^where is (.+)$"), "\$1 ఎక్కడ ఉంది?", 0.8f),

        // ---- which … ----
        Rule(Regex("^which (.+) you want$"), "ఏ \$1 కావాలి?", 0.8f),
        Rule(Regex("^which (.+) do you want$"), "ఏ \$1 కావాలి?", 0.8f),
        Rule(Regex("^which (.+) are you watching$"), "ఏ \$1 చూస్తున్నావు?", 0.8f),

        // ---- why / when / who ----
        Rule(Regex("^why are you late$"), "ఎందుకు late అయ్యావు?"),
        Rule(Regex("^why did you come$"), "ఎందుకు వచ్చావు?"),
        Rule(Regex("^why$"), "ఎందుకు?"),
        Rule(Regex("^when will you come$"), "ఎప్పుడు వస్తున్నావు?"),
        Rule(Regex("^when did you come$"), "ఎప్పుడు వచ్చావు?"),
        Rule(Regex("^when$"), "ఎప్పుడు?"),
        Rule(Regex("^who are you$"), "నువ్వు ఎవరు?"),

        // ---- yes / no questions ----
        Rule(Regex("^did you eat$"), "తిన్నావా?"),
        Rule(Regex("^did you eat (.+)$"), "\$1 తిన్నావా?", 0.8f),
        Rule(Regex("^are you coming$"), "నువ్వు వస్తున్నావా?"),
        Rule(Regex("^are you coming today$"), "ఇవాళ వస్తున్నావా?"),
        Rule(Regex("^are you coming tomorrow$"), "రేపు వస్తున్నావా?"),
        Rule(Regex("^are you okay$"), "నువ్వు బాగున్నావా?"),
        Rule(Regex("^are you free$"), "నువ్వు ఖాళీగా ఉన్నావా?"),
        Rule(Regex("^are you sure$"), "నీకు ఖచ్చితంగా తెలుసా?"),
        Rule(Regex("^are you at home$"), "నువ్వు ఇంట్లో ఉన్నావా?"),
        Rule(Regex("^are you watching (.+)$"), "\$1 చూస్తున్నావా?", 0.8f),
        Rule(Regex("^do you want (.+)$"), "నీకు \$1 కావాలా?", 0.8f),
        Rule(Regex("^do you like (.+)$"), "నీకు \$1 ఇష్టమా?", 0.8f),
        Rule(Regex("^do you understand$"), "అర్థమైందా?"),
        Rule(Regex("^do you know$"), "నీకు తెలుసా?"),
        Rule(Regex("^will you come tomorrow$"), "రేపు వస్తావా?"),
        Rule(Regex("^can you help me$"), "నాకు సహాయం చేస్తావా?"),

        // ---- i … statements ----
        Rule(Regex("^i am coming tomorrow$"), "నేను రేపు వస్తాను"),
        Rule(Regex("^i will come tomorrow$"), "నేను రేపు వస్తాను"),
        Rule(Regex("^i am going home$"), "నేను ఇంటికి వెళ్తున్నాను"),
        Rule(Regex("^i am going to (.+) tomorrow$"), "నేను రేపు \$1 కి వెళ్తున్నాను", 0.8f),
        Rule(Regex("^i am going to (.+) today$"), "నేను ఇవాళ \$1 కి వెళ్తున్నాను", 0.8f),
        Rule(Regex("^i am going to (.+) tonight$"), "నేను ఈ రాత్రి \$1 కి వెళ్తున్నాను", 0.8f),
        Rule(Regex("^i am going to (.+)$"), "నేను \$1 కి వెళ్తున్నాను", 0.8f),
        Rule(Regex("^i am going$"), "నేను వెళ్తున్నా"),
        Rule(Regex("^i am coming$"), "నేను వస్తున్నా"),
        Rule(Regex("^i am watching (.+)$"), "నేను \$1 చూస్తున్నాను", 0.8f),
        Rule(Regex("^i am at home$"), "నేను ఇంట్లో ఉన్నాను"),
        Rule(Regex("^i am in (.+)$"), "నేను \$1 లో ఉన్నాను", 0.8f),
        Rule(Regex("^i am fine$"), "నేను బాగున్నాను"),
        Rule(Regex("^i am busy$"), "నేను బిజీగా ఉన్నాను"),
        Rule(Regex("^i am tired$"), "నేను అలసిపోయాను"),
        Rule(Regex("^i am hungry$"), "నాకు ఆకలిగా ఉంది"),
        Rule(Regex("^i am thirsty$"), "నాకు దాహంగా ఉంది"),
        Rule(Regex("^i am waiting$"), "నేను ఆగుతున్నాను"),
        Rule(Regex("^i am sorry$"), "నన్ను క్షమించు"),
        Rule(Regex("^i am sure$"), "నాకు ఖచ్చితంగా తెలుసు"),
        Rule(Regex("^i will call you later$"), "తర్వాత నీకు call చేస్తా"),
        Rule(Regex("^i will call you$"), "నేను నీకు call చేస్తాను"),
        Rule(Regex("^i love you$"), "నేను నిన్ను ప్రేమిస్తున్నాను"),
        Rule(Regex("^i miss you$"), "నీ కోసం miss అవుతున్నాను"),
        Rule(Regex("^i know$"), "నాకు తెలుసు"),
        Rule(Regex("^i don't know$"), "నాకు తెలియదు"),
        Rule(Regex("^i don't understand$"), "నాకు అర్థం కాలేదు"),
        Rule(Regex("^i don't want$"), "నాకు వద్దు"),
        Rule(Regex("^i want water$"), "నాకు నీళ్ళు కావాలి"),
        Rule(Regex("^i want (a )?coffee$"), "నాకు coffee కావాలి"),
        Rule(Regex("^i want (a )?tea$"), "నాకు tea కావాలి"),
        Rule(Regex("^i want (.+)$"), "నాకు \$1 కావాలి", 0.7f),
        Rule(Regex("^i like (.+)$"), "నాకు \$1 ఇష్టం", 0.7f),
        Rule(Regex("^my name is (.+)$"), "నా పేరు \$1", 0.85f),

        // ---- requests / small talk ----
        Rule(Regex("^call me later$"), "తర్వాత నాకు call చెయ్యి"),
        Rule(Regex("^call me$"), "నాకు call చెయ్యి"),
        Rule(Regex("^please help me$"), "దయచేసి నాకు సహాయం చెయ్యి"),
        Rule(Regex("^talk to you later$"), "తర్వాత మాట్లాడుదాం"),
        Rule(Regex("^see you later$"), "తర్వాత కలుద్దాం"),
        Rule(Regex("^come here$"), "ఇక్కడికి రా"),
        Rule(Regex("^go away$"), "వెళ్ళిపో"),
        Rule(Regex("^wait$"), "ఆగు"),
        Rule(Regex("^wait (a moment|for me)$"), "కాసేపు ఆగు"),
        Rule(Regex("^let's go$"), "వెళ్దాం"),
        Rule(Regex("^no problem$"), "పర్వాలేదు"),
        Rule(Regex("^it is okay$"), "పర్వాలేదు"),
        Rule(Regex("^never mind$"), "పర్వాలేదు"),
        Rule(Regex("^take care$"), "జాగ్రత్త"),
        Rule(Regex("^good luck$"), "శుభాకాంక్షలు"),
        Rule(Regex("^congratulations$"), "అభినందనలు"),
        Rule(Regex("^happy birthday$"), "పుట్టినరోజు శుభాకాంక్షలు"),
        Rule(Regex("^happy new year$"), "నూతన సంవత్సర శుభాకాంక్షలు"),
        Rule(Regex("^good morning$"), "శుభోదయం"),
        Rule(Regex("^good night$"), "శుభరాత్రి"),
        Rule(Regex("^thank you$"), "ధన్యవాదాలు"),
        Rule(Regex("^really$"), "నిజంగా?"),
        Rule(Regex("^sorry$"), "సారీ"),
        Rule(Regex("^yes$"), "ఔను"),
        Rule(Regex("^no$"), "కాదు"),
    )

    // Known conversational nouns/words substituted inside rule slots so
    // "which movie you want" becomes "ఏ సినిమా కావాలి?" instead of a raw
    // "movie" — while proper nouns (Hyderabad) pass through untouched.
    private val slotWords = mapOf(
        "movie" to "సినిమా", "film" to "సినిమా", "song" to "పాట",
        "money" to "డబ్బు", "time" to "సమయం",
        "place" to "చోటు", "work" to "పని", "home" to "ఇల్లు", "house" to "ఇల్లు",
        "water" to "నీళ్ళు", "food" to "అన్నం",
        "milk" to "పాలు", "book" to "పుస్తకం",
        "city" to "నగరం",
        "family" to "కుటుంబం", "car" to "కారు", "bus" to "బస్సు",
        "train" to "రైలు", "name" to "పేరు",
        "love" to "ప్రేమ", "life" to "జీవితం", "day" to "రోజు",
        "week" to "వారం", "month" to "నెల", "year" to "సంవత్సరం",
        "today" to "ఇవాళ", "tomorrow" to "రేపు", "yesterday" to "నిన్న",
        "now" to "ఇప్పుడు", "later" to "తర్వాత", "tonight" to "ఈ రాత్రి",
        "morning" to "ఉదయం", "evening" to "సాయంత్రం", "night" to "రాత్రి",
        "my" to "నా", "your" to "నీ", "his" to "అతని", "her" to "ఆమె",
        "our" to "మన", "this" to "ఇది", "that" to "అది",
    )

    // Style transforms: convert casual 2nd-person forms into polite/formal.
    private val politeTransforms = listOf(
        "ఉన్నావా" to "ఉన్నారా",
        "చేస్తున్నావా" to "చేస్తున్నారా",
        "వెళ్తున్నావా" to "వెళ్తున్నారా",
        "వస్తున్నావా" to "వస్తున్నారా",
        "చూస్తున్నావా" to "చూస్తున్నారా",
        "తింటున్నావా" to "తింటున్నారా",
        "చదువుతున్నావా" to "చదువుతున్నారా",
        "ఉన్నావు" to "ఉన్నారు",
        "చేస్తున్నావు" to "చేస్తున్నారు",
        "వెళ్తున్నావు" to "వెళ్తున్నారు",
        "వస్తున్నావు" to "వస్తున్నారు",
        "చూస్తున్నావు" to "చూస్తున్నారు",
        "తిన్నావా" to "తిన్నారా",
        "వచ్చావా" to "వచ్చారా",
        "చూశావా" to "చూశారా",
        "వెళ్ళావా" to "వెళ్ళారా",
        "అయ్యావు" to "అయ్యారు",
        "ఉంటున్నావు" to "ఉంటున్నారు",
        "చెయ్యి" to "చేయండి",
        "ఆగు" to "ఆగండి",
        "చూడు" to "చూడండి",
        "విను" to "వినండి",
        "చెప్పు" to "చెప్పండి",
        "రా" to "రండి",
        "నువ్వు" to "మీరు",
        "నీకు" to "మీకు",
        "నీ " to "మీ ",
        "నీ" to "మీ",
    )

    // Verb stems for light conjugation in structured fallback templates.
    private val continuousVerbs = mapOf(
        "go" to "వెళ్తు", "come" to "వస్తు", "eat" to "తింటు", "do" to "చేస్తు",
        "see" to "చూస్తు", "work" to "పని చేస్తు", "play" to "ఆడుతు",
        "sleep" to "నిద్రపోతు", "talk" to "మాట్లాడుతు", "study" to "చదువుతు",
        "wait" to "ఆగుతు", "walk" to "నడుస్తు", "run" to "పరుగెత్తుతు",
        "read" to "చదువుతు", "write" to "రాస్తు", "watch" to "చూస్తు",
        "listen" to "వింటు", "cook" to "వంట చేస్తు", "drink" to "తాగుతు",
        "help" to "సహాయం చేస్తు", "learn" to "నేర్చుకుంటు", "teach" to "చెప్పుతు",
        "sing" to "పాడుతు", "dance" to "డ్యాన్స్ చేస్తు", "drive" to "డ్రైవ్ చేస్తు",
        "travel" to "ప్రయాణం చేస్తు", "meet" to "కలుస్తు", "call" to "call చేస్తు",
        "think" to "అనుకుంటు", "feel" to "ఫీల్ అవుతు", "wear" to "వేసుకుంటు",
        "buy" to "కొంటు", "sell" to "అమ్ముతు", "take" to "తీసుకుంటు",
        "give" to "ఇస్తు", "get" to "తెచ్చుకుంటు", "make" to "చేస్తు",
        "tell" to "చెప్పుతు", "say" to "చెప్పుతు", "ask" to "అడుగుతు",
        "know" to "తెలుసుకుంటు", "find" to "వెతుకుతు", "start" to "మొదలుపెడుతు",
        "finish" to "పూర్తి చేస్తు", "open" to "తెరుస్తు", "close" to "మూస్తు",
        "sit" to "కూర్చుంటు", "stand" to "నిలబడుతు", "fly" to "ఎగురుతు",
        "swim" to "ఈదుతు", "climb" to "ఎక్కుతు", "fall" to "పడుతు",
        "laugh" to "నవ్వుతు", "cry" to "ఏడుస్తు", "smile" to "నవ్వుతు",
        "shout" to "అరుస్తు", "whisper" to "గుసగుసలాడుతు", "pray" to "ప్రార్థిస్తు",
        "try" to "ప్రయత్నిస్తు", "change" to "మారుస్తు", "move" to "కదులుతు",
        "stay" to "ఉంటు", "live" to "ఉంటు", "win" to "గెలుస్తు", "lose" to "ఓడుతు",
    )

    private val futureVerbs = mapOf(
        "come" to "వస్తాను", "go" to "వెళ్తాను", "do" to "చేస్తాను",
        "see" to "చూస్తాను", "call" to "call చేస్తాను", "meet" to "కలుస్తాను",
        "tell" to "చెప్తాను", "give" to "ఇస్తాను", "take" to "తీసుకుంటాను",
        "buy" to "కొంటాను", "eat" to "తింటాను", "sleep" to "నిద్రపోతాను",
        "wait" to "ఆగుతాను", "help" to "సహాయం చేస్తాను", "talk" to "మాట్లాడతాను",
        "send" to "పంపుతాను", "bring" to "తెస్తాను", "make" to "చేస్తాను",
        "watch" to "చూస్తాను", "read" to "చదువుతాను", "write" to "రాస్తాను",
        "play" to "ఆడతాను", "study" to "చదువుతాను", "work" to "పని చేస్తాను",
        "cook" to "వంట చేస్తాను", "drink" to "తాగుతాను", "think" to "అనుకుంటాను",
        "say" to "చెప్తాను", "ask" to "అడుగుతాను", "find" to "వెతుకుతాను",
        "know" to "తెలుసుకుంటాను", "learn" to "నేర్చుకుంటాను", "teach" to "చెప్తాను",
    )

    private val pastYouVerbs = mapOf(
        "eat" to "తిన్నావా", "come" to "వచ్చావా", "go" to "వెళ్ళావా",
        "see" to "చూశావా", "do" to "చేశావా", "call" to "call చేశావా",
        "sleep" to "నిద్రపోయావా", "finish" to "పూర్తి చేశావా", "read" to "చదివావా",
        "write" to "రాశావా", "meet" to "కలిశావా", "talk" to "మాట్లాడావా",
        "watch" to "చూశావా", "listen" to "విన్నావా", "drink" to "తాగావా",
        "buy" to "కొన్నావా", "understand" to "అర్థమైందా", "get" to "వచ్చిందా",
        "play" to "ఆడావా", "study" to "చదివావా", "work" to "పని చేశావా",
    )

    override suspend fun translateEnglishToTelugu(
        text: String,
        style: TranslationStyle,
    ): TranslationResult? = withContext(Dispatchers.Default) {
        val normalized = normalize(text)
        if (normalized.isBlank()) return@withContext null

        val ruleMatch = matchRule(normalized, style)
        val telugu = ruleMatch ?: structuredFallback(normalized, style) ?: return@withContext null

        TranslationResult(
            input = text.trim(),
            telugu = telugu,
            twinglish = romanizeTelugu(telugu),
            confidence = if (ruleMatch != null) 0.92f else 0.7f,
            style = style,
        )
    }

    override fun romanizeTelugu(teluguText: String, style: RomanizationStyle): String =
        Romanizer.romanize(teluguText, style)

    // ---------- internals ----------

    private fun normalize(text: String): String =
        text.lowercase().trim().replace(Regex("[\\s]+"), " ")

    private fun matchRule(normalized: String, style: TranslationStyle): String? {
        for (rule in rules) {
            val m = rule.pattern.matchEntire(normalized) ?: continue
            var out = rule.telugu
            for (g in 1 until m.groupValues.size) {
                out = out.replace("\$$g", fillSlot(m.groupValues[g]))
            }
            return applyStyle(out, style)
        }
        return null
    }

    /**
     * Fill a captured noun/verb slot with natural Telugu: articles are
     * dropped, known conversational words are substituted (movie → సినిమా)
     * and everything else (proper nouns, code-switched words) passes through.
     */
    private fun fillSlot(slot: String): String {
        val words = slot.split(' ').filter { it.isNotBlank() }
        val filled = words.mapNotNull { w ->
            when {
                w in ARTICLES -> null
                slotWords[w] != null -> slotWords.getValue(w)
                else -> w
            }
        }
        return filled.joinToString(" ").ifBlank { slot }
    }

    private val politeVerbForms = listOf(
        "ఉన్నారు", "చేస్తున్నారు", "వెళ్తున్నారు", "వస్తున్నారు", "ఉంటున్నారు",
        "తిన్నారా", "వచ్చారా", "చూశారా", "అయ్యారు", "అర్థమైందా",
    )

    private fun applyStyle(telugu: String, style: TranslationStyle): String {
        if (style == TranslationStyle.CASUAL) return telugu
        var out = telugu
        for ((from, to) in politeTransforms) {
            out = out.replace(from, to)
        }
        if (style == TranslationStyle.FORMAL) {
            out = out.replace("ఏం", "ఏమి")
        }
        // When the sentence addresses a second person with no explicit
        // subject, polite/formal style introduces "మీరు".
        val hasSubject = out.startsWith("మీరు") || out.startsWith("నేను") || out.startsWith("మనం")
        if (!hasSubject && politeVerbForms.any { out.contains(it) }) {
            out = "మీరు $out"
        }
        return out
    }

    /**
     * A small set of structurally safe templates for common sentences that
     * the phrase bank doesn't cover verbatim. Every branch produces a
     * complete natural sentence; anything else returns null (never a
     * token-by-token hybrid).
     */
    private fun structuredFallback(normalized: String, style: TranslationStyle): String? {
        val words = normalized.split(' ')

        if (words.size in 2..5) {
            when {
                // "i will <verb>"
                words.first() == "i" && words[1] == "will" && words.size == 3 ->
                    futureVerbs[words[2]]?.let { return it }

                // "i am <verb>ing"
                words.first() == "i" && words[1] == "am" && words.size >= 3 && words.last().endsWith("ing") -> {
                    val stem = words.last().removeSuffix("ing")
                    continuousVerbs[stem]?.let { return "నేను ${it}న్నాను" }
                }

                // "you are <verb>ing"
                words.first() == "you" && words[1] == "are" && words.size >= 3 && words.last().endsWith("ing") -> {
                    val stem = words.last().removeSuffix("ing")
                    continuousVerbs[stem]?.let { return "నువ్వు ${it}న్నావు" }
                }

                // "are you <verb>ing?"
                words.first() == "are" && words[1] == "you" && words.size >= 3 && words.last().endsWith("ing") -> {
                    val stem = words.last().removeSuffix("ing")
                    continuousVerbs[stem]?.let { return "నువ్వు ${it}న్నావా?" }
                }

                // "did you <verb>?"
                words.first() == "did" && words[1] == "you" && words.size == 3 ->
                    pastYouVerbs[words[2]]?.let { return applyStyle(it, style) }
            }
        }
        return null
    }

    companion object {
        private val ARTICLES = setOf("a", "an", "the")
    }
}
