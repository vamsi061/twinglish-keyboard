package com.twinglish.keyboard.engine.personalization

/**
 * Data model for the persistent, bounded translation cache (one row per
 * normalized English source). A hit returns instantly without consulting the
 * translation provider again.
 */
data class TranslationCacheEntry(
    val normalizedSource: String,
    val teluguText: String?,
    val twinglishText: String,
    val provider: String = "offline-rules",
    val createdAt: Long,
    val lastUsedAt: Long,
    val usageCount: Int = 0,
    val confidence: Float = 0.6f,
    val userApproved: Boolean = false,
    val userModified: Boolean = false,
    val style: String = "casual",
    val locale: String = "te",
)

/**
 * A learned word-level preference ("movie" → "sinima", or a romanization
 * fix like "chestunnavu" → "chestunnav"). [confidence] grows with repeated
 * confirmation and is only acted upon once it clears the apply threshold.
 * [context] is the (normalized) start of the sentence it was learned in, so
 * the replacement is not applied blindly across unrelated topics.
 */
data class LearnedPreference(
    val from: String,
    val to: String,
    val context: String = "",
    val confidence: Float = 0.1f,
    val usageCount: Int = 1,
    val lastUsedAt: Long = 0,
)

/**
 * A complete Twinglish phrase the user accepted more than once. Used for
 * instant retrieval and prefix autocomplete ("Em che" → "Em chestunnav?").
 */
data class LearnedPhrase(
    val phrase: String,
    val sourceSentence: String = "",
    val usageCount: Int = 1,
    val lastUsedAt: Long = 0,
)

/**
 * An English word the user consistently keeps in their Twinglish
 * (code-switching: "office", "call", "meeting", "bro"). Recorded for
 * personalization visibility and to reinforce code-switch-friendly output.
 */
data class VocabularyWord(
    val word: String,
    val keepEnglish: Boolean = true,
    val confidence: Float = 0.1f,
    val usageCount: Int = 1,
)

/** One detected user correction of a generated suggestion. */
data class CorrectionEvent(
    val sourceContext: String,
    val generated: String,
    val userVersion: String,
    val createdAt: Long,
)

/**
 * Aggregate style profile: which politeness levels the user accepts vs.
 * rejects, plus punctuation habits. Drives candidate re-ranking without any
 * model retraining.
 */
data class StyleStats(
    val acceptedByStyle: Map<String, Int> = emptyMap(),
    val rejectedByStyle: Map<String, Int> = emptyMap(),
    val questionMarkCount: Int = 0,
    val exclamationCount: Int = 0,
)
