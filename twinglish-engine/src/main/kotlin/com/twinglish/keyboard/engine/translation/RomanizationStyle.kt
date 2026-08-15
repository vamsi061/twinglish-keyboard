package com.twinglish.keyboard.engine.translation

/**
 * How strictly Telugu script is romanized into Twinglish.
 *
 * [CASUAL] mirrors the way Telugu speakers actually type in chat:
 *   చేస్తున్నావు → chestunnav   (long vowels shortened, final -avu → -av)
 *   నా → naa                     (kept where it matters)
 *
 * [STRICT] is closer to academic transliteration:
 *   చేస్తున్నావు → chestunnavu
 */
enum class RomanizationStyle(val id: String, val label: String) {
    CASUAL("casual", "Casual"),
    STRICT("strict", "Strict");

    companion object {
        fun fromId(id: String?): RomanizationStyle =
            entries.firstOrNull { it.id == id } ?: CASUAL
    }
}
