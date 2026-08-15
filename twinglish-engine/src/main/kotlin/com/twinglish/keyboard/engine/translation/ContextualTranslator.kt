package com.twinglish.keyboard.engine.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps a [TranslationProvider] with the "smart context" behavior from the
 * product spec:
 *
 *  - trailing punctuation and emoji are split off, translated separately
 *    and re-attached ("Where are you!" → "Ekkadunnav!", "I am coming 😂"
 *    → "Nenu vastunna 😂"),
 *  - conversational fillers already in Telugu/Twinglish ("Hey ra,") are kept
 *    as-is,
 *  - sentences that are already mostly Twinglish are left alone (code
 *    switching).
 */
class ContextualTranslator(private val provider: TranslationProvider) {

    /** Conversation fillers kept verbatim when they open a sentence. */
    private val fillers = setOf(
        "hey", "hi", "hello", "ok", "okay", "hmm", "oh", "ya", "yes", "no",
        "ra", "anna", "ayya", "bava", "mama", "yaar", "bro", "dude", "broo",
        "friend", "sir", "madam", "babu", "chudu", "chudandi", "vinara",
        "vinandi", "cheppu", "vinala", "bhai", "bhaiyya",
    )

    /** Words already in Twinglish — if a sentence is mostly these, leave it alone. */
    private val twinglishWords = setOf(
        "nenu", "nuvvu", "meeru", "manam", "memu", "vaadu", "aame", "vallu",
        "ki", "lo", "tho", "nunchi", "kosam", "gurinchi", "valla",
        "chestunna", "chestunnav", "velthunna", "velthunnav", "vastunna",
        "vastunnav", "untunna", "untunnav", "tinnava", "tinnanu",
        "ra", "raa", "ela", "em", "enduku", "ekkada", "ekkadiki", "ela unnav",
        "nijamga", "baga", "bagunnav", "bagunnanu", "okka", "inka", "ippudu",
        "repu", "ninnu", "taravata", "tarvata", "kaluddam", "vellam",
        "baguntundi", "nachindi", "telsu", "telidu", "antu", "kadu", "kaadu",
    )

    /** Emoji/symbol detection — Java regex lacks \p{Emoji_Presentation}. */
    private val emojiRegex = Regex(
        "[" +
            "\\x{1F300}-\\x{1FAFF}" + // misc symbols, emoticons, transport, flags…
            "\\x{2600}-\\x{27BF}" +   // misc symbols, dingbats
            "\\x{2B00}-\\x{2BFF}" +   // arrows / symbols
            "\\x{1F1E6}-\\x{1F1FF}" + // regional indicators (flags)
            "\\x{FE0F}\\x{200D}" +    // variation selector, ZWJ
            "\\x{00A9}\\x{00AE}\\x{203C}\\x{2049}\\x{2122}\\x{2139}" +
            "\\x{2194}-\\x{21AA}\\x{231A}\\x{231B}\\x{23E9}-\\x{23F3}" +
            "\\x{25AA}\\x{25AB}\\x{25B6}\\x{25C0}\\x{25FB}-\\x{25FE}" +
            "\\x{2600}-\\x{26FF}" +
            "\\x{2700}-\\x{27BF}\\x{2934}\\x{2935}\\x{2B05}-\\x{2B07}" +
            "]+"
    )

    /**
     * Translate a complete sentence fragment.
     *
     * @return the final Twinglish text (romanized), or null when there is
     *         nothing worth translating.
     */
    suspend fun translate(
        sentence: String,
        style: TranslationStyle = TranslationStyle.CASUAL,
        romanStyle: RomanizationStyle = RomanizationStyle.CASUAL,
    ): TranslationResult? = withContext(Dispatchers.Default) {
        val input = sentence.trim()
        if (input.isBlank()) return@withContext null

        // 1. Strip leading conversational fillers ("hey ra, ...").
        var cursor = input
        var prefix = ""
        var changed = true
        while (changed) {
            changed = false
            val first = cursor.substringBefore(' ')
            val candidate = first.lowercase().trimEnd(',', '!', ';')
            if (candidate in fillers && cursor != first) {
                val consumed = first.length + 1 // word + following space
                prefix += cursor.substring(0, consumed)
                cursor = cursor.substring(consumed).trimStart()
                changed = true
            } else if (candidate in fillers && cursor == first) {
                prefix += cursor
                cursor = ""
                changed = true
            }
        }
        var core = cursor.trim()

        // 2. Split off trailing punctuation.
        val punct = Regex("[?!.,;:\u2026]+$").find(core)?.value.orEmpty()
        core = core.removeSuffix(punct).trim()

        // 3. Extract trailing emoji so they never get mangled.
        val trailingSymbols = emojiRegex.find(core)?.value.orEmpty()
        core = core.removeSuffix(trailingSymbols).trim()

        // 4. Extract emoji anywhere in the core (e.g. "I am coming 😂").
        val emojiHolder = mutableListOf<String>()
        val protected = emojiRegex.replace(core) { m ->
            emojiHolder += m.value
            "\uE000${emojiHolder.size - 1}\uE001"
        }.trim()

        if (protected.isBlank()) return@withContext null

        // 5. Code switching: mostly-Twinglish input stays as typed.
        //    (at least one known Twinglish token AND at least half the tokens).
        val words = protected.split(Regex("\\s+")).filter { it.isNotBlank() }
        val known = words.count { it.lowercase().trimEnd(',', '.', '!', '?') in twinglishWords }
        if (words.isNotEmpty() && known > 0 && known >= words.size / 2) {
            var restored = protected
            for ((idx, emoji) in emojiHolder.withIndex()) {
                restored = restored.replace("\uE000$idx\uE001", emoji)
            }
            val sep = if (trailingSymbols.isNotEmpty() && trailingSymbols.any { it != ' ' }) " " else ""
            return@withContext TranslationResult(
                input = sentence,
                telugu = null,
                twinglish = mergePunctuation((prefix + restored + sep + trailingSymbols).trim(), punct),
                confidence = 0.7f,
                style = style,
            )
        }

        // 6. Translate.
        val result = provider.translateEnglishToTelugu(protected, style) ?: return@withContext null

        // 7. Restore emoji placeholders.
        var restored = result.telugu ?: result.twinglish
        for ((idx, emoji) in emojiHolder.withIndex()) {
            restored = restored.replace("\uE000$idx\uE001", emoji)
        }

        val twinglish = provider.romanizeTelugu(restored, romanStyle)
        val withNouns = restoreProperNouns(sentence, twinglish)
        // Emoji attaches with a space, punctuation without.
        val sep = if (trailingSymbols.isNotEmpty() && trailingSymbols.any { it != ' ' }) " " else ""
        val finalText = mergePunctuation((prefix + withNouns + sep + trailingSymbols).trim(), punct)

        result.copy(telugu = restored, twinglish = finalText, input = sentence)
    }

    /**
     * Re-attach the user's trailing punctuation to the translation. Question
     * rules carry their own "?" in the template, so a matching "?" is
     * deduplicated and a different mark ("!") replaces it.
     */
    private fun mergePunctuation(base: String, punct: String): String {
        if (punct.isEmpty()) return base
        val last = base.lastOrNull()
        return when {
            punct.length == 1 && last == punct[0] -> base // template already ends with it
            last == '?' && punct == "!" -> base.dropLast(1) + "!"
            else -> base + punct
        }
    }

    /**
     * Restore the original casing of capitalized proper nouns in the output.
     * Title-case words ("Hyderabad") are restored; ALL-CAPS input (accidental
     * keyboard uppercase) is deliberately ignored so it never leaks into the
     * Twinglish output.
     */
    private fun restoreProperNouns(original: String, twinglish: String): String {
        var out = twinglish
        val originalWords = original.split(Regex("\\s+"))
        originalWords.forEachIndexed { idx, w ->
            if (idx > 0 && w.length > 1 && w[0].isUpperCase() && w.all { it.isLetter() } &&
                !w.all { it.isUpperCase() }
            ) {
                val lower = w.lowercase()
                if (out.contains(lower, ignoreCase = true)) {
                    out = out.replaceFirst(lower, w, ignoreCase = true)
                }
            }
        }
        return out
    }
}
