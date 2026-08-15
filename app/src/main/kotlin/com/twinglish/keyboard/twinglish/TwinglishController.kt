package com.twinglish.keyboard.twinglish

import com.twinglish.keyboard.engine.TwinglishEngine
import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the suggestion strip. Observes the current sentence, waits for it
 * to stabilize (debounce), cancels obsolete requests, and publishes the
 * translation to [state]. Old responses can never overwrite newer input
 * because every request carries a monotonically increasing sequence number.
 */
class TwinglishController(
    private val engine: TwinglishEngine,
    private val scope: CoroutineScope,
    private val styleProvider: () -> TranslationStyle,
    private val romanStyleProvider: () -> RomanizationStyle,
) {

    data class State(
        val sentence: String = "",
        val primary: String? = null,
        val alternatives: List<String> = emptyList(),
        val translating: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var sequence = 0

    fun onSentenceChanged(sentence: String) {
        job?.cancel()
        val trimmed = sentence.trim()
        _state.update { it.copy(sentence = trimmed) }
        if (trimmed.isBlank()) {
            _state.update { it.copy(primary = null, alternatives = emptyList(), translating = false) }
            return
        }
        val seq = ++sequence
        job = scope.launch {
            delay(DEBOUNCE_MS)
            if (seq != sequence) return@launch
            _state.update { it.copy(translating = true) }
            val result = engine.translate(trimmed, styleProvider(), romanStyleProvider())
            if (seq != sequence) return@launch
            val alternatives = buildAlternatives(trimmed, result?.twinglish)
            _state.update {
                it.copy(
                    primary = result?.twinglish,
                    alternatives = alternatives,
                    translating = false,
                )
            }
        }
    }

    /** Clear without firing a new request (used when leaving the field). */
    fun clear() {
        job?.cancel()
        sequence++
        _state.value = State()
    }

    private suspend fun buildAlternatives(input: String, primary: String?): List<String> {
        if (primary == null) return emptyList()
        val list = mutableListOf<String>()
        // Same sentence in another politeness level is a cheap, useful variant.
        val polite = runCatching {
            engine.translate(input, TranslationStyle.POLITE, romanStyleProvider())
        }.getOrNull()?.twinglish
        if (polite != null && polite != primary) list += polite
        return list.distinct().take(3)
    }

    companion object {
        private const val DEBOUNCE_MS = 380L
    }
}
