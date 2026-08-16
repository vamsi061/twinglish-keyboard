package com.twinglish.keyboard.engine.personalization

import com.twinglish.keyboard.engine.translation.TranslationStyle
import java.util.Locale

/** One ranked translation candidate for the suggestion strip. */
data class Candidate(
    val text: String,
    val quality: Float,
    val style: TranslationStyle,
    val score: Float = 0f,
    val approved: Boolean = false,
)

/**
 * Re-ranks translation candidates without touching the translation model:
 *
 *   FinalScore = translationQuality
 *              + userPreference (learned word preferences present in the text)
 *              + styleAffinity  (accepted vs. rejected politeness styles)
 *              + acceptance     (historical use of this exact phrasing)
 *              + frequency × recency
 *              − rejection      (suggestions the user passed over)
 *
 * Personalization is additive: it can only re-order candidates the model
 * already generated, never invent or damage a translation.
 */
class PersonalizedRanker(
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    fun rank(
        candidates: List<Candidate>,
        sourceNormalized: String,
        store: KnowledgeStore,
        learning: LearningEngine,
    ): List<Candidate> {
        if (candidates.size <= 1) return candidates.map { it.copy(score = it.quality) }

        val preferences = store.allPreferences()
        val stats = store.stats()
        val cache = store.allCache()
        val phrases = store.allPhrases()
        val nowMs = now()

        return candidates.map { c ->
            val prefBoost = preferences
                .filter { it.confidence >= 0.4f }
                .filter { it.context.isEmpty() || sourceNormalized.startsWith(it.context) }
                .filter { containsWord(c.text, it.to) }
                .sumOf { (0.1f * it.confidence).toDouble() }
                .toFloat()

            val fromStillPresent = preferences.any {
                it.confidence >= 0.4f && containsWord(c.text, it.from) &&
                    (it.context.isEmpty() || sourceNormalized.startsWith(it.context))
            }

            val styleAffinity = styleAffinity(stats, c.style)

            val acceptance = cache
                .filter { it.twinglishText.equals(c.text, ignoreCase = true) }
                .sumOf { e -> (0.05f * e.usageCount).coerceAtMost(0.15f) * recencyWeight(nowMs - e.lastUsedAt) }
                .toFloat()

            val phraseBonus = if (phrases.any { it.phrase.equals(c.text, ignoreCase = true) }) 0.15f else 0f

            val rejectionPenalty = (0.05f * learning.rejectionCount(InputNormalizer.normalize(c.text))).coerceAtMost(0.2f)

            val score = c.quality + prefBoost + styleAffinity + acceptance + phraseBonus - rejectionPenalty -
                if (fromStillPresent) 0.05f else 0f

            c.copy(score = score)
        }.sortedByDescending { it.score }
    }

    private fun styleAffinity(stats: StyleStats, style: TranslationStyle): Float {
        val accepted = stats.acceptedByStyle[style.id] ?: 0
        val rejected = stats.rejectedByStyle[style.id] ?: 0
        if (accepted == 0 && rejected == 0) return 0f
        return 0.3f * (accepted - rejected) / (accepted + rejected + 2f)
    }

    private fun containsWord(text: String, word: String): Boolean =
        Regex("\\b" + Regex.escape(word) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)

    private fun recencyWeight(ageMs: Long): Double {
        val days = ageMs / 86_400_000.0
        return 1.0 / (1.0 + days / 30.0)
    }
}
