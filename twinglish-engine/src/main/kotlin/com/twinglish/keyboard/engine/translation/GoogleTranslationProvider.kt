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
        return TranslationResult(
            input = trimmed,
            telugu = styled,
            twinglish = Romanizer.romanize(styled, RomanizationStyle.CASUAL),
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
         * The first inner array holds the translation segments; the trailing
         * ["en"],["te"]] arrays are source/target language metadata and must
         * NOT be treated as segments. Every real segment starts with
         * "…","…" — the first string of each pair is the translated Telugu.
         * Returns null when nothing useful is found.
         */
        internal fun parseGtxResponse(body: String): String? {
            val start = body.indexOf("[[[")
            if (start < 0) return null

            // Scan to the end of the first segment array: bracket depth
            // starts at 3 ([[[) and the segments array closes when it drops
            // back to 1 (the outermost [). String contents are skipped so
            // brackets inside quoted text never confuse the scan.
            var depth = 0
            var end = start
            var inString = false
            var escaped = false
            while (end < body.length) {
                val c = body[end]
                if (inString) {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') inString = false
                } else {
                    when (c) {
                        '"' -> inString = true
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 1) {
                                end++
                                break
                            }
                        }
                    }
                }
                end++
            }

            val segmentSection = body.substring(start, end)
            val segmentRegex = Regex("\\[\"((?:[^\"\\\\]|\\\\.)*)\"")
            val sb = StringBuilder()
            var matches = 0
            for (m in segmentRegex.findAll(segmentSection)) {
                sb.append(unescape(m.groupValues[1]))
                matches++
            }
            if (matches == 0) return null
            val out = sb.toString().trim()
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
        var out = telugu
        for ((formal, casual) in casualTransforms) {
            out = out.replace(formal, casual)
        }
        return out.trim()
    }
}
