package com.twinglish.keyboard.engine.personalization

/**
 * Normalizes free-form English input into a stable key for cache lookups.
 *
 * Case, repeated whitespace and surrounding spaces are all folded so
 * "How are you?", "how  are YOU?" and " HOW ARE YOU? " resolve to the same
 * key — while meaningful punctuation ("?" / "!" / ".") is preserved.
 */
object InputNormalizer {

    fun normalize(input: String): String =
        input.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
}
