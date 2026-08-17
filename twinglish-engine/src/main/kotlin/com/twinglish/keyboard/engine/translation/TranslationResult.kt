package com.twinglish.keyboard.engine.translation

/**
 * Outcome of translating one English phrase.
 *
 * @param input       the English input that was translated
 * @param telugu      the intermediate natural Telugu text (script), when available
 * @param twinglish   the final romanized Twinglish output
 * @param confidence  rough confidence in 0..1
 * @param style       the dialect/style that was used
 */
data class TranslationResult(
    val input: String,
    val telugu: String?,
    val twinglish: String,
    val confidence: Float,
    val style: TranslationStyle,
    /**
     * Set when the provider could NOT produce a confident translation.
     * [twinglish] then holds the original English text (a safe fallback —
     * never a partial hybrid) and [error] explains why, so the UI can show
     * the reason instead of silently failing. Null means success.
     */
    val error: String? = null,
)
