package com.twinglish.keyboard.engine.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Offline-first English → Telugu → Twinglish provider.
 *
 * The pipeline is exactly the two-stage one the product spec requires:
 *
 *   1. English sentence → natural Telugu script
 *   2. Telugu script → conversational Twinglish (romanized)
 *
 * Stage 1 is served by the curated offline phrase bank first — those
 * sentences produce the exact, tested outputs ("how are you" → "ఎలా
 * ఉన్నావు?"). When the phrase bank has no rule for a sentence, stage 1
 * falls back to Google Translate (keyless endpoint, no API key needed)
 * which handles unseen sentences with far better accuracy than any local
 * rule bank. Stage 2 always runs locally through [Romanizer].
 *
 * Privacy: only the *current* sentence is sent to Google, and only when
 * online translation is enabled. The IME already gates translation to
 * plain-text fields, so passwords/PINs never reach this provider.
 */
class GoogleTranslationProvider(
    private val offline: TranslationProvider = OfflineTranslationProvider(),
    private val onlineEnabled: () -> Boolean = { true },
    /** Injectable network fetcher for tests (defaults to the real endpoint). */
    private val fetcher: suspend (String) -> String? = { GoogleTranslationProvider.fetchTelugu(it) },
) : TranslationProvider {

    override val id: String = "google+offline"
    override val isOnline: Boolean = true

    // The IME generates candidates for CASUAL/POLITE/FORMAL in quick
    // succession; Google ignores our style parameter, so cache the raw
    // Telugu fetch per normalized sentence to avoid duplicate network calls.
    private val fetchCache = object : LinkedHashMap<String, String?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean =
            size > 64
    }

    override suspend fun translateEnglishToTelugu(
        text: String,
        style: TranslationStyle,
    ): TranslationResult? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val normalized = normalize(trimmed)

        // 1. Curated phrase bank first — exact, fast, offline.
        val offlineResult = offline.translateEnglishToTelugu(trimmed, style)
        if (offlineResult != null && offlineResult.confidence >= OFFLINE_KEEP_CONFIDENCE) {
            return offlineResult
        }

        // 2. Google Translate for sentences the phrase bank can't handle.
        if (!onlineEnabled()) return offlineResult
        val telugu = withContext(Dispatchers.IO) {
            fetchCache.getOrPut(normalized) { fetcher(normalized) }
        } ?: return offlineResult

        val styled = if (style == TranslationStyle.CASUAL) casualize(telugu) else telugu
        // Interrogative sentences keep their "?" even when the user typed
        // no punctuation ("how is the movie" → "సినిమా ఎలా ఉంది?").
        val withQuestion = ensureQuestionMark(normalized, styled)
        return TranslationResult(
            input = trimmed,
            telugu = withQuestion,
            twinglish = Romanizer.romanize(withQuestion, RomanizationStyle.CASUAL),
            confidence = 0.95f,
            style = style,
        )
    }

    override fun romanizeTelugu(teluguText: String, style: RomanizationStyle): String =
        Romanizer.romanize(teluguText, style)

    // ------------------------------------------------------------------
    // Google Translate (keyless endpoint)
    // ------------------------------------------------------------------

    companion object {
        /**
         * Rules the curated bank must meet to be kept over Google's output.
         * Slot rules (which/where/… with captured nouns) sit at 0.8 and are
         * deliberately kept — they produce the spec's expected phrases.
         * Structural fallback templates (0.7) defer to Google instead.
         */
        private const val OFFLINE_KEEP_CONFIDENCE = 0.8f

        private const val ENDPOINT =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=te&dt=t&q="

        /**
         * GET the keyless Google translate endpoint and return the Telugu
         * script for [text], or null on any failure (network, timeout, or
         * unexpected payload). Never throws — callers treat null as
         * "keep the offline result".
         */
        suspend fun fetchTelugu(text: String): String? = withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(ENDPOINT + URLEncoder.encode(text, "UTF-8"))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 8_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TwinglishKeyboard")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parseGtxResponse(body)
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }

        /**
         * Parse the gtx payload: [[["telugu","english",…],[…]],null,"en",…].
         * The first element holds the translation segments; the trailing
         * ["en"],["te"]] arrays are source/target language metadata and must
         * NOT be treated as segments.
         *
         * Every real segment is an array at bracket depth 3 whose FIRST string
         * is the translated Telugu. Google sometimes embeds extra nested arrays
         * inside a segment carrying its internal model info — e.g.
         * [[["ee29150929…","tea_…md"]]] — and those strings (model ids) must
         * never leak into the translation. Only strings sitting immediately at
         * the top of a depth-3 segment are collected, so nested metadata is
         * skipped. Returns null when nothing useful is found.
         */
        internal fun parseGtxResponse(body: String): String? {
            val start = body.indexOf("[[[")
            if (start < 0) return null

            var depth = 0
            var i = start
            var inString = false
            var escaped = false
            var expectSegmentString = false
            var captureStart = -1
            val fragments = mutableListOf<String>()

            while (i < body.length) {
                val c = body[i]
                if (inString) {
                    if (escaped) {
                        escaped = false
                    } else if (c == '\\') {
                        escaped = true
                    } else if (c == '"') {
                        if (captureStart >= 0) {
                            fragments.add(unescape(body.substring(captureStart, i)))
                            captureStart = -1
                        }
                        inString = false
                    }
                } else {
                    when (c) {
                        '"' -> {
                            if (expectSegmentString) {
                                captureStart = i + 1
                                expectSegmentString = false
                            }
                            inString = true
                        }
                        '[' -> {
                            depth++
                            expectSegmentString = depth == 3
                        }
                        ']' -> {
                            expectSegmentString = false
                            depth--
                            if (depth == 1) {
                                i++
                                break
                            }
                        }
                        ',' -> expectSegmentString = false
                    }
                }
                i++
            }

            if (fragments.isEmpty()) return null
            // Join fragments with a single space; Google sometimes splits mid-
            // sentence with leading/trailing whitespace on the fragments.
            val out = fragments.joinToString(" ").replace(Regex("\\s+"), " ").trim()
            return out.ifBlank { null }
        }

        private fun unescape(s: String): String =
            s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
    }

    // ------------------------------------------------------------------
    // Casual style nudge — Google returns neutral/textbook Telugu; the
    // spec wants conversational chat Telugu by default.
    // ------------------------------------------------------------------

    private fun normalize(text: String): String =
        text.lowercase().trim().replace(Regex("\\s+"), " ")

    /** Question openers — how/what/where/when/why/who/which/do/did/are/… */
    private val questionStarters = listOf(
        "how", "what", "where", "when", "why", "who", "whom", "whose",
        "which", "do", "does", "did", "are", "is", "was", "were",
        "will", "would", "can", "could", "should", "shall", "may", "might",
        "have", "has", "had", "am",
    )

    /** Greeting words that can precede a question ("hi how are you" → "how"). */
    private val greetings = listOf("hi", "hello", "hey", "hlo", "hii", "hai", "yo")

    /**
     * Append "?" to an interrogative translation that has no terminal
     * punctuation. Statements ("i am going home") are left untouched.
     */
    private fun ensureQuestionMark(normalizedInput: String, telugu: String): String {
        if (telugu.lastOrNull()?.let { it in "?!." } == true) return telugu
        // Skip a leading greeting so "hlo how are you" still reads as a question.
        var check = normalizedInput
        val first = check.substringBefore(' ')
        if (first in greetings) check = check.substringAfter(' ', check)
        if (check.substringBefore(' ') in questionStarters) return "$telugu?"
        // "you coming?"-style ellipsis questions
        if (normalizedInput.endsWith("?")) return "$telugu?"
        return telugu
    }

    /**
     * English code-switch substitutions for words Google renders in formal
     * literary Telugu. Those romanize terribly (స్వయంచాలకంగా →
     * "svayamchaalakanga"), so casual style keeps the natural Twinglish forms
     * instead ("automatic ga"). Longer keys MUST come before shorter ones
     * because some contain others ("ఇంగ్లీషును" contains "ఇంగ్లీషు").
     */
    private val casualVocabulary = listOf(
        // The app's own brand name — never transliterated (ట్విగ్లిష్ → tviglish).
        "ట్వింగ్లీష్" to "Twinglish",
        "ట్విగ్లీష్" to "Twinglish",
        "ట్విగ్లిష్" to "Twinglish",
        // సృష్టించాను → తయారు చేశాను (created → made)
        "సృష్టించాను" to "తయారు చేశాను",
        // స్వయంచాలకంగా → automatic గా (automatically)
        "స్వయంచాలకంగా" to "automatic గా",
        "ఇంగ్లీషును" to "English ని",
        "ఆంగ్లాన్ని" to "English ని",
        "ఇంగ్లీషు" to "English",
        "యాప్ని" to "app ని",
        "యాప్" to "app",
    )

    /** Formal → casual chat substitutions (reverse of the offline polite set). */
    private val casualTransforms = listOf(
        "ఉన్నారా" to "ఉన్నావా",
        "చేస్తున్నారా" to "చేస్తున్నావా",
        "వెళ్తున్నారా" to "వెళ్తున్నావా",
        "వస్తున్నారా" to "వస్తున్నావా",
        "చూస్తున్నారా" to "చూస్తున్నావా",
        "తింటున్నారా" to "తింటున్నావా",
        "చదువుతున్నారా" to "చదువుతున్నావా",
        "తిన్నారా" to "తిన్నావా",
        "వచ్చారా" to "వచ్చావా",
        "చూశారా" to "చూశావా",
        "వెళ్ళారా" to "వెళ్ళావా",
        "ఉన్నారు" to "ఉన్నావు",
        "చేస్తున్నారు" to "చేస్తున్నావు",
        "వెళ్తున్నారు" to "వెళ్తున్నావు",
        "వస్తున్నారు" to "వస్తున్నావు",
        "చూస్తున్నారు" to "చూస్తున్నావు",
        "ఉంటున్నారు" to "ఉంటున్నావు",
        "మీరు" to "నువ్వు",
        "మీకు" to "నీకు",
    )

    /**
     * Convert Google's neutral Telugu toward casual chat register when the
     * requested style is CASUAL: second-person verbs become the -వు/-వా
     * forms and "మీరు/మీకు" become "నువ్వు/నీకు". Other styles keep
     * Google's output untouched.
     */
    private fun casualize(telugu: String): String {
        // Google inserts zero-width non-joiners (ట్విగ్లిష్\u200cగా) to stop
        // conjunct formation. They mark a syllable boundary, so they become a
        // space (Twinglish ga, app ni) rather than vanishing. The joiner is
        // simply dropped.
        var out = telugu.replace("\u200C", " ").replace("\u200D", "")
        for ((formal, casual) in casualTransforms) {
            out = out.replace(formal, casual)
        }
        for ((formal, casual) in casualVocabulary) {
            out = out.replace(formal, casual)
        }
        return out.trim().replace(Regex("\\s{2,}"), " ")
    }
}
