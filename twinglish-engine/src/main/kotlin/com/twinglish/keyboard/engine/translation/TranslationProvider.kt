package com.twinglish.keyboard.engine.translation

/**
 * Abstraction over any English → Telugu → Twinglish translation backend.
 *
 * The keyboard only talks to this interface, so the offline rule-based
 * provider can be swapped for a remote API or a learned model without
 * touching the IME code.
 *
 * Implementations must never block for long periods; callers run them on
 * a background dispatcher.
 */
interface TranslationProvider {

    /** Stable identifier used for settings / diagnostics, e.g. "offline-rules". */
    val id: String

    /** Whether this provider requires a network connection. */
    val isOnline: Boolean

    /**
     * Translate a single English sentence into natural Telugu.
     *
     * Returns null when the provider cannot produce a useful translation
     * (e.g. empty input, unknown language, offline remote provider).
     */
    suspend fun translateEnglishToTelugu(
        text: String,
        style: TranslationStyle,
    ): TranslationResult?

    /**
     * Romanize Telugu script (or mixed text) into Twinglish.
     * Non-Telugu characters pass through unchanged.
     */
    fun romanizeTelugu(teluguText: String, style: RomanizationStyle = RomanizationStyle.CASUAL): String
}
