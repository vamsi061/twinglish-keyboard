package com.twinglish.keyboard.engine.personalization

/**
 * Normalizes free-form English input into a stable key for cache lookups.
 *
 * Case, repeated whitespace and surrounding spaces are all folded so
 * "How are you?", "how  are YOU?" and " HOW ARE YOU? " resolve to the same
 * key. Trailing punctuation is also dropped ("how are you", "how are
 * you?" and "how are you!" share one entry) so a sentence translated once
 * is served from the local cache for every punctuation variant — the
 * network is never called again for it. This is a KEY, not display text:
 * the stored translation keeps the punctuation it was generated with.
 */
object InputNormalizer {

    private val TRAILING_PUNCT = setOf('.', '!', '?', ',', ';', ':', '\u2026')

    fun normalize(input: String): String =
        input.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd(*TRAILING_PUNCT.toCharArray())
            .trim()
}
