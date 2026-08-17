package com.twinglish.keyboard.engine.personalization

import com.twinglish.keyboard.engine.TwinglishEngine
import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationSanitizer
import com.twinglish.keyboard.engine.translation.TranslationStyle

/** Result of one personalized translation request. */
data class RankedResult(
    val candidates: List<Candidate>,
    val cacheHit: Boolean = false,
    /**
     * Set when every candidate style failed (e.g. offline miss + network
     * down). [candidates] then holds the original English text as a safe
     * fallback, and this explains why, so the UI can show the reason.
     */
    val error: String? = null,
)

/**
 * The personalized translation pipeline the keyboard talks to:
 *
 *   exact user-approved / cached match → instant hit
 *   otherwise: local model generates candidates (base + polite + formal),
 *   learned preferences are applied, candidates are re-ranked, the top
 *   result is cached.
 *
 * Everything is local and offline. Learning events (accept / correct /
 * reject) are recorded through this facade, which owns the [LearningFlags]
 * so the whole system can be switched off from Settings.
 */
class PersonalizationEngine(
    private val engine: TwinglishEngine,
    private val store: KnowledgeStore,
    private val learning: LearningEngine,
    private val ranker: PersonalizedRanker = PersonalizedRanker(),
    private val flagsProvider: () -> LearningFlags = { LearningFlags() },
) {

    /**
     * Translate [input] with cache-first lookup and personalized ranking.
     * Returns null when nothing can be produced (caller falls back to
     * English suggestions).
     */
    suspend fun translateAndRank(
        input: String,
        style: TranslationStyle,
        romanStyle: RomanizationStyle,
    ): RankedResult? = try {
        translateAndRankOrThrow(input, style, romanStyle)
    } catch (t: Throwable) {
        // A translation exception must never crash the IME process — report
        // the failure so the strip can show it instead of going silent.
        RankedResult(candidates = emptyList(), error = "Translation failed")
    }

    private suspend fun translateAndRankOrThrow(
        input: String,
        style: TranslationStyle,
        romanStyle: RomanizationStyle,
    ): RankedResult? {
        val norm = InputNormalizer.normalize(input)
        if (norm.isBlank()) return null
        val flags = flagsProvider()
        val ts = System.currentTimeMillis()

        // 1. Exact cache hit — user-approved or previously generated.
        // Only real translations are ever cached (never failures), so hits
        // can't carry an error.
        store.getCache(norm)?.let { raw ->
            // Defense in depth: never let a polluted entry surface, even if
            // an older build persisted one before the load-time purge.
            val cleaned = TranslationSanitizer.clean(raw.twinglishText)
            if (cleaned.isNotBlank()) {
                store.putCache(raw.copy(twinglishText = cleaned, lastUsedAt = ts, usageCount = raw.usageCount + 1))
                return RankedResult(
                    candidates = listOf(
                        Candidate(
                            text = cleaned,
                            quality = raw.confidence,
                            style = TranslationStyle.fromId(raw.style),
                            approved = raw.userApproved,
                        )
                    ),
                    cacheHit = true,
                )
            }
        }

        // 2. Generate candidates from the local model (never token-by-token).
        // Errored results are kept aside: they hold the original English as a
        // safe fallback and are only surfaced when every style failed.
        val candidates = mutableListOf<Candidate>()
        val fallbacks = mutableListOf<Candidate>()
        var firstError: String? = null
        val styles = linkedSetOf(style, TranslationStyle.POLITE, TranslationStyle.FORMAL)
        for (s in styles) {
            val result = engine.translate(input, s, romanStyle) ?: continue
            val text = if (flags.personalizedSuggestions) {
                learning.applyPreferences(result.twinglish, norm)
            } else {
                result.twinglish
            }
            val cleaned = TranslationSanitizer.clean(text)
            if (cleaned.isBlank()) continue
            val cand = Candidate(text = cleaned, quality = result.confidence, style = s)
            if (result.error != null) {
                if (firstError == null) firstError = result.error
                if (fallbacks.none { it.text == cleaned }) fallbacks += cand
            } else if (candidates.none { it.text == cleaned }) {
                candidates += cand
            }
        }

        // Every style failed → offer the original English and carry the reason.
        val error = if (candidates.isEmpty()) firstError else null
        if (candidates.isEmpty()) candidates += fallbacks
        if (candidates.isEmpty()) {
            return RankedResult(candidates = emptyList(), error = error ?: "Translation failed")
        }

        // 3. Personalized re-ranking (re-orders candidates only).
        val ranked = if (flags.personalizedSuggestions) {
            ranker.rank(candidates, norm, store, learning)
        } else {
            candidates.map { it.copy(score = it.quality) }
        }

        // 4. Persist the top candidate (generated — not user-approved yet).
        // Never cache English fallbacks / failed results — that would poison
        // the cache with non-translations for the session.
        val top = ranked.first()
        if (error == null && top.quality > 0f) {
            store.putCache(
                TranslationCacheEntry(
                    normalizedSource = norm,
                    teluguText = null,
                    twinglishText = top.text,
                    style = top.style.id,
                    createdAt = ts,
                    lastUsedAt = ts,
                    usageCount = 0,
                    confidence = top.quality,
                )
            )
            store.evictCache(DEFAULT_CACHE_LIMIT)
        }

        return RankedResult(candidates = ranked, cacheHit = false, error = error)
    }

    /** Personal phrase autocomplete for the current word prefix. */
    fun phraseCandidates(prefix: String): List<String> = learning.phraseCandidates(prefix)

    /** Apply strong learned preferences to a candidate string (used by the ranker and tests). */
    fun applyPreferences(text: String, sourceNormalized: String): String =
        learning.applyPreferences(text, sourceNormalized)

    val knowledgeStore: KnowledgeStore get() = store
    val learningEngine: LearningEngine get() = learning

    // ------------------------------------------------------------------
    // learning events (gated by flags + secure-field handling upstream)
    // ------------------------------------------------------------------

    fun recordAccepted(source: String, suggestion: String, style: TranslationStyle) {
        learning.recordAccepted(source, suggestion, style.id, flagsProvider())
    }

    fun recordCorrected(source: String, generated: String, userVersion: String) {
        learning.recordCorrected(source, generated, userVersion, flagsProvider())
    }

    fun recordRejected(suggestion: String, style: TranslationStyle) {
        learning.recordRejected(suggestion, style.id, flagsProvider())
    }

    // ------------------------------------------------------------------
    // data management / explainability
    // ------------------------------------------------------------------

    /** For the "What Twinglish learned" settings view. */
    fun learnedInfo(): List<String> = learning.learnedInfo()

    /** Settings → "Clear Learned Data": wipe everything learned. */
    fun clearAllData() {
        store.clearAll()
    }

    /** Settings → "Reset Twinglish Preferences": keep cache, drop learning. */
    fun resetPreferences() {
        store.clearPreferences()
        store.clearCorrections()
        store.clearVocabulary()
        store.clearPhrases()
        store.setStats(com.twinglish.keyboard.engine.personalization.StyleStats())
    }

    companion object {
        const val DEFAULT_CACHE_LIMIT = 5000
    }
}
