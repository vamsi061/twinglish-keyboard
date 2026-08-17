package com.twinglish.keyboard.engine.translation

/**
 * Converts Telugu script into casual "Twinglish" romanization.
 *
 * The converter walks Telugu characters one by one:
 *
 *  - an independent vowel maps to its romanized form,
 *  - a consonant carries an inherent "a" unless it is followed by a virama
 *    (్) or an explicit vowel sign,
 *  - a virama joins consonants: identical consonants double (న్న → nna,
 *    క్క → kka) while different ones form a plain cluster (స్త → sta),
 *  - an anusvara (ం) assimilates to the following consonant (ఇంటికి → intiki),
 *  - non-Telugu characters (English proper nouns, emoji, punctuation)
 *    pass through unchanged.
 *
 * Casual mode applies the shorthand conventions Telugu speakers type in chat
 * (word boundaries are respected so multi-word strings romanize the same as
 * single words):
 *  - final -avu / -uvu → -av / -uv         (చేస్తున్నావు → chestunnav)
 *  - long -aa after a cluster → -a         (వస్తాను → vastanu)
 *  - final -aa → -a                        (వస్తున్నావా → vastunnava)
 *  - -aa before a word-final consonant → -a (ఇవాళ → ivala)
 */
object Romanizer {

    private const val VIRAMA = '\u0C4D'
    private const val ANUSVARA = '\u0C02'
    private const val VISARGA = '\u0C03'

    // Zero-width format characters that Google sometimes embeds in Telugu
    // (ZWNJ \u200c, ZWJ \u200d, ZWSP \u200b, …). They must never reach the
    // romanized output. A non-joiner marks a syllable boundary so it becomes
    // a space (matching the provider's cleanup); the rest are dropped.
    private const val ZWNJ = '\u200C'
    private val zeroWidth = setOf('\u200B', '\u200D', '\u2060', '\uFEFF')

    // Independent vowels → casual form (strict form differs for long vowels).
    private val vowels = mapOf(
        '\u0C05' to "a",   // అ
        '\u0C06' to "aa",  // ఆ
        '\u0C07' to "i",   // ఇ
        '\u0C08' to "ee",  // ఈ
        '\u0C09' to "u",   // ఉ
        '\u0C0A' to "u",   // ఊ (casual) / uu (strict)
        '\u0C0B' to "ru",  // ఋ
        '\u0C0E' to "e",   // ఎ
        '\u0C0F' to "e",   // ఏ (casual) / ee (strict)
        '\u0C10' to "ai",  // ఐ
        '\u0C12' to "o",   // ఒ
        '\u0C13' to "o",   // ఓ (casual) / oo (strict)
        '\u0C14' to "au",  // ఔ
    )

    // Vowel signs (matras).
    private val vowelSigns = mapOf(
        '\u0C3E' to "aa", // ా
        '\u0C3F' to "i",  // ి
        '\u0C40' to "ee", // ీ
        '\u0C41' to "u",  // ు
        '\u0C42' to "u",  // ూ (casual) / uu (strict)
        '\u0C43' to "ru", // ృ
        '\u0C46' to "e",  // ె
        '\u0C47' to "e",  // ే (casual) / ee (strict)
        '\u0C48' to "ai", // ై
        '\u0C4A' to "o",  // ొ
        '\u0C4B' to "o",  // ో (casual) / oo (strict)
        '\u0C4C' to "au", // ౌ
    )

    // Consonants (base form).
    private val consonants = mapOf(
        '\u0C15' to "k",   // క
        '\u0C16' to "kh",  // ఖ
        '\u0C17' to "g",   // గ
        '\u0C18' to "gh",  // ఘ
        '\u0C19' to "ng",  // ఙ
        '\u0C1A' to "ch",  // చ
        '\u0C1B' to "chh", // ఛ
        '\u0C1C' to "j",   // జ
        '\u0C1D' to "jh",  // ఝ
        '\u0C1E' to "ny",  // ఞ
        '\u0C1F' to "t",   // ట
        '\u0C20' to "th",  // ఠ
        '\u0C21' to "d",   // డ
        '\u0C22' to "dh",  // ఢ
        '\u0C23' to "n",   // ణ
        '\u0C24' to "t",   // త
        '\u0C25' to "th",  // థ
        '\u0C26' to "d",   // ద
        '\u0C27' to "dh",  // ధ
        '\u0C28' to "n",   // న
        '\u0C2A' to "p",   // ప
        '\u0C2B' to "ph",  // ఫ
        '\u0C2C' to "b",   // బ
        '\u0C2D' to "bh",  // భ
        '\u0C2E' to "m",   // మ
        '\u0C2F' to "y",   // య
        '\u0C30' to "r",   // ర
        '\u0C31' to "r",   // ఱ
        '\u0C32' to "l",   // ల
        '\u0C33' to "l",   // ళ
        '\u0C35' to "v",   // వ
        '\u0C36' to "sh",  // శ
        '\u0C37' to "sh",  // ష
        '\u0C38' to "s",   // స
        '\u0C39' to "h",   // హ
    )

    // Conjunct romanization overrides for common clusters (else the two
    // consonants are just concatenated, e.g. స్త → st).
    private val clusterOverrides: Map<Pair<Char, Char>, String> = mapOf(
        ('\u0C33' to '\u0C24') to "lth", // ళ్త → lth (వెళ్తున్నాను → velthunnanu)
    )

    // Casual chat spellings that differ from mechanical romanization
    // (కావాలి → "kavali" not "kaavaali"). Keyed on the romanized form
    // (after the final -avu/-uvu shortening, so keys never carry the
    // dropped -u).
    private val casualOverrides = mapOf(
        "kaavaali" to "kavali",
        "kaavaala" to "kavala",
        // చేశాను → chesanu (not cheshaanu), చేశావు → chesav, చేశారు → chesaru.
        "cheshaanu" to "chesanu",
        "cheshaav" to "chesav",
        "cheshaaru" to "chesaru",
        "cheshaam" to "chesam",
        // బాగా family — బాగుంది → bagundi, బాగున్నాను → bagunnanu, …
        "baaga" to "baga",
        "baagundi" to "bagundi",
        "baagunnanu" to "bagunnanu",
        "baagunnav" to "bagunnav",
        "baagunnava" to "bagunnava",
        "baagoledu" to "bagoledu",
        "baagupadu" to "bagupadu",
        "baagunda" to "bagunda",
        "baadhaga" to "badhaga",
        // చాలా → chala
        "chaala" to "chala",
        // common chat verb forms (long -aa after a plain consonant)
        "kontaanu" to "kontanu",
        "pampaanu" to "pampanu",
        "marchipoyaanu" to "marchipoyanu",
        "aaguthaanu" to "aaguthanu",
        "cherukuntaanu" to "cherukuntanu",
        "tinudaam" to "tinudam",
        "tinaalani" to "tinalani",
        "thaagaalani" to "thaagalani",
        "cheshaava" to "chesava",
        "antaaru" to "antaru",
        // వచ్చి/వచ్చా family — చ్చ is mechanical "chch", chat writes one ch:
        // వచ్చాను → vachanu, వచ్చావు → vachav, వచ్చింది → vachindi.
        "vachchanu" to "vachanu",
        "vachchav" to "vachav",
        "vachchindi" to "vachindi",
        "vachchava" to "vachava",
        "vachcharu" to "vacharu",
        // సహాయం → sahayam (not sahaayam)
        "sahaayam" to "sahayam",
        // అయ్యాను → ayanu (not ayyanu)
        "ayyanu" to "ayanu",
        // ఎప్పుడు → epudu (not eppudu)
        "eppudu" to "epudu",
        // నా (my) → naa, not the shortened "na" (నాకు already gives naaku)
        "na" to "naa",
        // దూరం → dooram (not duram)
        "duram" to "dooram",
        // మళ్ళీ → malli (not mallee)
        "mallee" to "malli",
        // మరోసారి → marosari (not marosaari)
        "marosaari" to "marosari",
        // కాసేపు → kasepu (not kaasepu)
        "kaasepu" to "kasepu",
        // ఖాళీగా → khaali ga (not khaaleega)
        "khaaleega" to "khaali ga",
        // దాదాపు → dadapu (not daadaapu)
        "daadaapu" to "dadapu",
        // త్వరలో → tvarala (not tvaralo)
        "tvaralo" to "tvarala",
        // చేశా → chesa (not chesha) — chat drops the h after ch
        "chesha" to "chesa",
        // వచ్చా → vacha (not vachcha)
        "vachcha" to "vacha",
        // చూశా → choosa (not chusha)
        "chusha" to "choosa",
        // ఖరీదు → kharidu (not khareedu)
        "khareedu" to "kharidu",
        // నిద్రపోయావా → nidrapoyava (not nidrapoyaava) — chat question form
        "nidrapoyaava" to "nidrapoyava",
        // నచ్చింది → nachindi (not nachchindi) — chat drops the double ch
        "nachchindi" to "nachindi",
    )

    // Anusvara assimilates to the following consonant's place of articulation
    // (palatals and dentals → n, labials stay m by default): నుంచి → nunchi.
    private val nasalBefore = setOf(
        '\u0C15', '\u0C16', '\u0C17', '\u0C18', '\u0C19', // క ఖ గ ఘ ఙ
        '\u0C1A', '\u0C1B', '\u0C1C', '\u0C1D', '\u0C1E', // చ ఛ జ ఝ ఞ
        '\u0C1F', '\u0C20', '\u0C21', '\u0C22', '\u0C23', // ట ఠ డ ఢ ణ
        '\u0C24', '\u0C25', '\u0C26', '\u0C27', '\u0C28', // త థ ద ధ న
        '\u0C2F', '\u0C30', '\u0C31', '\u0C32', '\u0C33', // య ర ఱ ల ళ
        '\u0C35', '\u0C36', '\u0C37', '\u0C38', '\u0C39', // వ శ ష స హ
    )

    fun isTeluguChar(c: Char): Boolean = c in '\u0C00'..'\u0C7F'

    fun isTeluguConsonant(c: Char): Boolean = c in consonants

    /** True when the character at [index] ends a Telugu word (end, space or non-Telugu). */
    private fun wordEndsAt(input: String, index: Int): Boolean {
        val c = input.getOrNull(index) ?: return true
        return !isTeluguChar(c)
    }

    /**
     * Romanize a string that may contain Telugu, English, numbers, emoji and
     * punctuation. Non-Telugu content is passed through unchanged.
     */
    fun romanize(input: String, style: RomanizationStyle = RomanizationStyle.CASUAL): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        var i = 0
        var clusterPending = false // a virama was seen; the next -aa may shorten in casual mode

        while (i < input.length) {
            val c = input[i]

            when {
                c == VIRAMA -> {
                    clusterPending = true
                    i++
                }

                c == ANUSVARA -> {
                    val next = input.getOrNull(i + 1)
                    if (next != null && next in nasalBefore) sb.append('n') else sb.append('m')
                    i++
                }

                c == VISARGA -> {
                    sb.append('h')
                    i++
                }

                c in vowels -> {
                    if (style == RomanizationStyle.STRICT) {
                        sb.append(
                            when (c) {
                                '\u0C0A', '\u0C42' -> "uu"
                                '\u0C0F', '\u0C47' -> "ee"
                                '\u0C13', '\u0C4B' -> "oo"
                                else -> vowels.getValue(c)
                            }
                        )
                    } else {
                        sb.append(vowels.getValue(c))
                    }
                    clusterPending = false
                    i++
                }

                c in vowelSigns -> {
                    val isLongA = c == '\u0C3E'
                    val nextChar = input.getOrNull(i + 1)
                    val beforeFinalConsonant =
                        isLongA && nextChar != null && nextChar in consonants && wordEndsAt(input, i + 2)
                    // "aa" shortens before a consonant cluster: వస్తాను → vastanu.
                    val beforeCluster =
                        isLongA && nextChar != null && nextChar in consonants &&
                            input.getOrNull(i + 2) == VIRAMA
                    val shorten =
                        style == RomanizationStyle.CASUAL &&
                            isLongA &&
                            (clusterPending || wordEndsAt(input, i + 1) || beforeFinalConsonant || beforeCluster)
                    if (shorten) {
                        sb.append('a')
                    } else if (style == RomanizationStyle.STRICT && (c == '\u0C42' || c == '\u0C47' || c == '\u0C4B')) {
                        sb.append(if (c == '\u0C42') "uu" else if (c == '\u0C47') "ee" else "oo")
                    } else {
                        sb.append(vowelSigns.getValue(c))
                    }
                    clusterPending = false
                    i++
                }

                c in consonants -> {
                    val next = input.getOrNull(i + 1)
                    val after = input.getOrNull(i + 2)
                    val base = consonants.getValue(c)
                    when {
                        next == VIRAMA && after == c -> {
                            // Doubled consonant: క్క → kka, న్న → nna.
                            // The pair takes the vowel that follows: an explicit
                            // vowel sign (న్నా → nna) or the inherent "a" when a
                            // consonant/word-end follows (క్కడి → kkadi).
                            val afterPair = input.getOrNull(i + 3)
                            val explicitVowel = afterPair != null &&
                                (afterPair in vowelSigns || afterPair == ANUSVARA || afterPair == VISARGA)
                            sb.append(base).append(base)
                            if (explicitVowel) {
                                clusterPending = true
                            } else {
                                sb.append('a')
                                clusterPending = false
                            }
                            i += 3 // consume C + virama + C
                        }
                        next == VIRAMA -> {
                            // Plain cluster: స్త → st (no inherent vowel).
                            val c2 = input.getOrNull(i + 2)
                            val override = if (c2 != null) clusterOverrides[c to c2] else null
                            if (override != null) {
                                // Override covers both consonants (ళ్త → lth).
                                sb.append(override)
                                clusterPending = true
                                i += 3 // consume C + virama + C
                            } else {
                                sb.append(base)
                                clusterPending = true
                                i += 2 // consume C + virama; second consonant handled next
                            }
                        }
                        next != null && next in vowelSigns -> {
                            // Explicit vowel sign: no inherent "a". Keep any pending
                            // cluster flag — it applies to this very vowel sign.
                            sb.append(base)
                            i++
                        }
                        else -> {
                            // Inherent "a" (also before anusvara/visarga).
                            sb.append(base).append('a')
                            clusterPending = false
                            i++
                        }
                    }
                }

                else -> {
                    when {
                        c == ZWNJ -> {
                            // Non-joiner marks a syllable boundary → space
                            // (ల్యాబ్\u200cలను → "lyab lanu").
                            sb.append(' ')
                            i++
                        }
                        c in zeroWidth -> {
                            // ZWJ/ZWSP/… carry no sound — skip entirely.
                            i++
                        }
                        else -> {
                            sb.append(c)
                            clusterPending = false
                            i++
                        }
                    }
                }
            }
        }

        var result = sb.toString()
        if (style == RomanizationStyle.CASUAL) {
            // చేస్తున్నావు → chestunnav : drop the final -u in -avu / -uvu
            // endings, per word. Trailing punctuation is peeled off first so
            // "?" / "!" attached to the Telugu (question templates) don't
            // block the shortening (వెళ్తున్నావు? → velthunnav?).
            result = result.split(' ').joinToString(" ") { word ->
                val punct = word.takeLastWhile { it in "?!.,;:" }
                val core = word.dropLast(punct.length)
                val shortened =
                    if (core.endsWith("avu") || core.endsWith("uvu")) core.dropLast(1) else core
                (casualOverrides[shortened] ?: shortened) + punct
            }
        }
        return result
    }
}
