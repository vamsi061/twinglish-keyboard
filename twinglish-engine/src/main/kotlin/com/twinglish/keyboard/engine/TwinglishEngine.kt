package com.twinglish.keyboard.engine

import com.twinglish.keyboard.engine.translation.ContextualTranslator
import com.twinglish.keyboard.engine.translation.OfflineTranslationProvider
import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationProvider
import com.twinglish.keyboard.engine.translation.TranslationResult
import com.twinglish.keyboard.engine.translation.TranslationStyle
import java.util.LinkedHashMap

/**
 * Facade the keyboard talks to. Owns the active [TranslationProvider]
 * (offline by default; a remote provider can be plugged in later through
 * the same interface) plus a small LRU cache so repeated phrases are
 * answered instantly without recomputation.
 */
class TwinglishEngine(
    private val provider: TranslationProvider = OfflineTranslationProvider(),
    cacheSize: Int = 128,
) {

    private val contextual = ContextualTranslator(provider)
    private val cache = object : LinkedHashMap<String, TranslationResult>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslationResult>?): Boolean =
            size > cacheSize
    }

    val activeProvider: TranslationProvider get() = provider

    /**
     * Translate a sentence fragment. Cache key covers input + styles.
     */
    suspend fun translate(
        sentence: String,
        style: TranslationStyle = TranslationStyle.CASUAL,
        romanStyle: RomanizationStyle = RomanizationStyle.CASUAL,
    ): TranslationResult? {
        val trimmed = sentence.trim()
        if (trimmed.isBlank()) return null
        val key = "$style|$romanStyle|$trimmed"
        cache[key]?.let { return it }
        val result = try {
            contextual.translate(trimmed, style, romanStyle)
        } catch (t: Throwable) {
            // A translation bug must never take the keyboard down or leave
            // the strip silently empty — surface a safe English fallback.
            TranslationResult(
                input = trimmed,
                telugu = null,
                twinglish = trimmed,
                confidence = 0f,
                style = style,
                error = "Translation failed",
            )
        }
        if (result != null) cache[key] = result
        return result
    }

    fun clearCache() = cache.clear()
}
