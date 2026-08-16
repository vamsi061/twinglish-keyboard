package com.twinglish.keyboard.twinglish

import com.twinglish.keyboard.engine.personalization.Candidate
import com.twinglish.keyboard.engine.personalization.PersonalizationEngine
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
 * Drives the suggestion strip through the personalized pipeline:
 *
 *   debounce → cache-first translation → candidate generation (base +
 *   polite + formal) → learned preferences → re-ranking → phrase
 *   autocomplete.
 *
 * It also translates meaningful usage into learning events: accepted
 * suggestions, corrections, and suggestions the user typed over
 * (rejections). Old responses can never overwrite newer input because
 * every request carries a monotonically increasing sequence number.
 */
class TwinglishController(
    private val personalization: PersonalizationEngine,
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

    private var lastSentence = ""
    private var lastRanked: List<Candidate> = emptyList()
    private var shownSuggestion: String? = null
    private var shownAt = 0L
    private var lastAccepted: String? = null

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
            val result = personalization.translateAndRank(trimmed, styleProvider(), romanStyleProvider())
            if (seq != sequence) return@launch
            lastSentence = trimmed
            lastRanked = result?.candidates ?: emptyList()
            val primary = lastRanked.firstOrNull()?.text
            val alternatives = (lastRanked.drop(1).map { it.text } + phraseAutocomplete(trimmed))
                .filter { it != primary }
                .distinct()
                .take(3)
            shownSuggestion = primary
            shownAt = System.currentTimeMillis()
            _state.update {
                it.copy(primary = primary, alternatives = alternatives, translating = false)
            }
        }
    }

    /** Personal phrase memory autocomplete for the trailing word. */
    private fun phraseAutocomplete(sentence: String): List<String> {
        val word = sentence.substringAfterLast(' ')
        if (word.length < 3) return emptyList()
        return personalization.phraseCandidates(word)
    }

    // ------------------------------------------------------------------
    // learning events — only called by the IME for non-secure fields
    // ------------------------------------------------------------------

    /** The user tapped a suggestion. */
    fun onSuggestionAccepted(text: String) {
        lastAccepted = text
        val style = styleOf(text)
        if (lastSentence.isNotBlank()) {
            personalization.recordAccepted(lastSentence, text, style)
            // Picking a non-primary candidate also de-ranks what was passed over.
            val primary = _state.value.primary
            if (primary != null && primary != text) {
                personalization.recordRejected(primary, styleOf(primary))
            }
        }
        shownSuggestion = null
    }

    /**
     * The user edited an accepted suggestion. Called with the sentence that
     * was translated, the generated text and the text as the user left it.
     */
    fun onSuggestionCorrected(source: String, generated: String, userVersion: String) {
        personalization.recordCorrected(source, generated, userVersion)
        lastAccepted = null
        shownSuggestion = null
    }

    /**
     * A suggestion was on screen and the user typed something else instead.
     * Only counts after the suggestion has been stably shown (so mid-phrase
     * typing noise is never learned).
     */
    fun onKeyTyped() {
        val shown = shownSuggestion ?: return
        if (shown == lastAccepted) return
        if (System.currentTimeMillis() - shownAt < STABLE_SHOW_MS) {
            shownSuggestion = null
            return
        }
        personalization.recordRejected(shown, styleOf(shown))
        shownSuggestion = null
    }

    /** Clear without firing a new request (used when leaving the field). */
    fun clear() {
        job?.cancel()
        sequence++
        _state.value = State()
        lastRanked = emptyList()
        shownSuggestion = null
        lastAccepted = null
        lastSentence = ""
    }

    private fun styleOf(text: String): TranslationStyle =
        lastRanked.firstOrNull { it.text == text }?.style ?: styleProvider()

    companion object {
        private const val DEBOUNCE_MS = 380L

        /** A suggestion must be shown this long before typing over counts as rejection. */
        private const val STABLE_SHOW_MS = 1500L
    }
}
