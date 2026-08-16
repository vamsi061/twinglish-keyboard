package com.twinglish.keyboard.engine.personalization

import java.util.Locale

/** Per-subsystem switches so the user can disable personalization pieces. */
data class LearningFlags(
    val enabled: Boolean = true,
    val corrections: Boolean = true,
    val vocabulary: Boolean = true,
    val personalizedSuggestions: Boolean = true,
)

/**
 * The self-learning core. Learns ONLY from meaningful events — accepted
 * suggestions, edited accepted suggestions, repeated phrase usage, repeated
 * corrections — never from individual keystrokes.
 *
 * Privacy: all learning is local and is fully gated by [LearningFlags]; with
 * flags off, not a single record is written (callers also stop invoking it
 * for password/secure fields).
 */
class LearningEngine(
    private val store: KnowledgeStore,
    private val flagsProvider: () -> LearningFlags = { LearningFlags() },
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Confidence added per confirmation of a word preference. */
    private val CONFIRMATION_DELTA = 0.2f

    /** Below this confidence a learned preference is ignored. */
    private val APPLY_THRESHOLD = 0.4f

    // ------------------------------------------------------------------
    // recording
    // ------------------------------------------------------------------

    /** The user explicitly tapped a suggestion. */
    fun recordAccepted(source: String, suggestion: String, style: String, flags: LearningFlags) {
        if (!flags.enabled) return
        val ts = now()
        store.putCache(
            TranslationCacheEntry(
                normalizedSource = InputNormalizer.normalize(source),
                teluguText = null,
                twinglishText = suggestion,
                style = style,
                createdAt = ts,
                lastUsedAt = ts,
                usageCount = 1,
                confidence = 1f,
                userApproved = true,
            )
        )
        val s = store.stats()
        store.setStats(s.copy(acceptedByStyle = bump(s.acceptedByStyle, style)))
        if (suggestion.endsWith("?")) store.setStats(store.stats().copy(questionMarkCount = store.stats().questionMarkCount + 1))
        if (suggestion.endsWith("!")) store.setStats(store.stats().copy(exclamationCount = store.stats().exclamationCount + 1))

        if (flags.vocabulary) extractVocabulary(suggestion)
        recordPhrase(source, suggestion)
    }

    /**
     * The user edited an accepted suggestion ("E movie kavali?" →
     * "E sinima kavali?"). Learns word-level preferences with confidence and
     * caches the user's version so the exact phrase returns instantly.
     */
    fun recordCorrected(source: String, generated: String, userVersion: String, flags: LearningFlags) {
        if (!flags.enabled || !flags.corrections) return
        if (generated.trim().equals(userVersion.trim(), ignoreCase = true)) return

        val ts = now()
        store.addCorrection(CorrectionEvent(source, generated, userVersion, ts))
        learnWordReplacements(source, generated, userVersion)
        store.putCache(
            TranslationCacheEntry(
                normalizedSource = InputNormalizer.normalize(source),
                teluguText = null,
                twinglishText = userVersion.trim(),
                style = "casual",
                createdAt = ts,
                lastUsedAt = ts,
                usageCount = 1,
                confidence = 0.9f,
                userApproved = true,
                userModified = true,
            )
        )
        if (flags.vocabulary) extractVocabulary(userVersion)
        recordPhrase(source, userVersion)
    }

    /**
     * A displayed suggestion was passed over (the user typed something else
     * or chose a different candidate). De-ranks the exact text in memory and
     * lowers the politeness style's affinity.
     */
    fun recordRejected(suggestion: String, style: String, flags: LearningFlags) {
        if (!flags.enabled) return
        rejectedSuggestions[InputNormalizer.normalize(suggestion)] =
            (rejectedSuggestions[InputNormalizer.normalize(suggestion)] ?: 0) + 1
        val s = store.stats()
        store.setStats(s.copy(rejectedByStyle = bump(s.rejectedByStyle, style)))
    }

    // ------------------------------------------------------------------
    // application
    // ------------------------------------------------------------------

    /**
     * Apply strong learned word preferences to a Twinglish candidate.
     * Only whole-word replacements with confidence ≥ threshold and a
     * matching learning context are applied — weak or out-of-context rules
     * are ignored so personalization can never make translation worse.
     */
    fun applyPreferences(text: String, sourceNormalized: String): String {
        var out = text
        for (p in store.allPreferences()) {
            if (p.confidence < APPLY_THRESHOLD) continue
            if (p.context.isNotEmpty() && !sourceNormalized.startsWith(p.context)) continue
            out = out.replace(
                Regex("\\b" + Regex.escape(p.from) + "\\b", RegexOption.IGNORE_CASE),
                p.to,
            )
        }
        return out
    }

    /** Phrases the user accepted before, matching the given prefix (autocomplete). */
    fun phraseCandidates(prefix: String, limit: Int = 3): List<String> {
        val p = prefix.trim().lowercase(Locale.ROOT)
        if (p.isEmpty()) return emptyList()
        val nowMs = now()
        return store.allPhrases()
            .filter { it.phrase.lowercase(Locale.ROOT).startsWith(p) }
            .sortedByDescending { it.usageCount * recencyWeight(nowMs - it.lastUsedAt) }
            .take(limit)
            .map { it.phrase }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private val rejectedSuggestions = HashMap<String, Int>()

    fun rejectionCount(normalizedSuggestion: String): Int =
        rejectedSuggestions[normalizedSuggestion] ?: 0

    /** Track a phrase the user used (accepted or kept after correction). */
    private fun recordPhrase(source: String, suggestion: String) {
        store.putPhrase(
            LearnedPhrase(
                phrase = suggestion.trim(),
                sourceSentence = source.trim(),
                usageCount = 1,
                lastUsedAt = now(),
            )
        )
    }

    /**
     * Diff the generated vs. user-typed Twinglish and boost a preference for
     * every clean 1:1 word replacement ("movie" → "sinima"). Structural
     * changes (different token counts in the core) are skipped entirely.
     */
    private fun learnWordReplacements(source: String, generated: String, userVersion: String) {
        val g = tokens(generated)
        val u = tokens(userVersion)
        if (g.isEmpty() || u.isEmpty()) return

        var i = 0
        while (i < g.size && i < u.size && g[i] == u[i]) i++
        var gi = g.size - 1
        var ui = u.size - 1
        while (gi > i && ui > i && g[gi] == u[ui]) {
            gi--
            ui--
        }
        val gCore = g.subList(i, gi + 1)
        val uCore = u.subList(i, ui + 1)
        if (gCore.isEmpty() || uCore.isEmpty() || gCore.size != uCore.size) return

        val context = InputNormalizer.normalize(source).split(' ').take(2).joinToString(" ")
        for (k in gCore.indices) {
            val from = gCore[k]
            val to = uCore[k]
            if (from == to || to.isBlank() || from.isBlank()) continue
            if (from in PUNCT || to in PUNCT) continue
            boostPreference(from, to, context)
        }
    }

    private fun boostPreference(from: String, to: String, context: String) {
        val existing = store.getPreference(from, to)
        val confidence = ((existing?.confidence ?: 0f) + CONFIRMATION_DELTA).coerceAtMost(1f)
        store.putPreference(
            LearnedPreference(
                from = from,
                to = to,
                context = context,
                confidence = confidence,
                usageCount = (existing?.usageCount ?: 0) + 1,
                lastUsedAt = now(),
            )
        )
    }

    /** Learn English words the user keeps in their Twinglish (code-switching). */
    private fun extractVocabulary(text: String) {
        val words = text.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9']+"))
            .filter { it.length >= 2 && it.none { c -> c.isDigit() } }
            .filter { it !in FUNCTION_WORDS }
        val distinct = words.distinct().take(10)
        for (w in distinct) {
            val existing = store.allVocabulary().firstOrNull { it.word == w }
            val confidence = ((existing?.confidence ?: 0f) + 0.15f).coerceAtMost(1f)
            store.putVocabulary(
                VocabularyWord(
                    word = w,
                    keepEnglish = true,
                    confidence = confidence,
                    usageCount = (existing?.usageCount ?: 0) + 1,
                )
            )
        }
    }

    /** Human-readable summary for the "What Twinglish learned" view. */
    fun learnedInfo(): List<String> {
        val lines = mutableListOf<String>()
        val strong = store.allPreferences()
            .filter { it.confidence >= 0.3f }
            .sortedByDescending { it.confidence }
            .take(5)
        strong.forEach { p ->
            lines.add("Preferred: \"${p.to}\" instead of \"${p.from}\" (${(p.confidence * 100).toInt()}%)")
        }
        val vocab = store.allVocabulary()
            .filter { it.confidence >= 0.3f }
            .sortedByDescending { it.confidence }
            .take(8)
        if (vocab.isNotEmpty()) {
            lines.add("English words you keep: ${vocab.joinToString { it.word }}")
        }
        val s = store.stats()
        val preferredStyle = s.acceptedByStyle.maxByOrNull { it.value }?.key
        if (preferredStyle != null) {
            lines.add("Preferred style: ${preferredStyle.replaceFirstChar { it.uppercase() }}")
        }
        val phraseCount = store.allPhrases().size
        if (phraseCount > 0) lines.add("Learned phrases: $phraseCount")
        return lines
    }

    private fun bump(map: Map<String, Int>, key: String): Map<String, Int> =
        map + (key to (map[key] ?: 0) + 1)

    // Word tokens only — punctuation is dropped so a "?" → "!" change is
    // never learned as a word replacement.
    private fun tokens(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9']+"))
            .filter { it.isNotEmpty() }

    /** Decay weight: a phrase used 100x six months ago < 10x this week. */
    private fun recencyWeight(ageMs: Long): Double {
        val days = ageMs / 86_400_000.0
        return 1.0 / (1.0 + days / 30.0)
    }

    companion object {
        private val PUNCT = setOf("?", "!", ".", ",", ";", ":")

        // English function words + common Twinglish particles that are never
        // recorded as personal vocabulary.
        private val FUNCTION_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at",
            "for", "with", "from", "by", "is", "am", "are", "was", "were", "be",
            "been", "being", "i", "you", "he", "she", "it", "we", "they", "my",
            "your", "his", "her", "its", "our", "their", "me", "him", "us", "them",
            "this", "that", "these", "those", "do", "does", "did", "will", "would",
            "can", "could", "should", "shall", "may", "might", "must", "not", "no",
            "yes", "so", "as", "if", "then", "than", "now", "when", "where", "what",
            "who", "why", "how", "there", "here", "ok", "okay", "please", "hello",
            "hi", "hey", "bye",
            // Telugu / Twinglish particles
            "em", "ela", "nenu", "nuvvu", "meeru", "manam", "memu", "naa", "nee",
            "naku", "neeku", "ki", "lo", "tho", "nunchi", "kosam", "repu", "ippudu",
            "tarvata", "taravata", "innu", "okati", "rendu", "moodu", "nalugu",
            "ayidu", "aaru", "edu", "enimidi", "tommidi", "padi",
        )
    }
}
