package com.twinglish.keyboard.engine.translation

/**
 * Strips Google's internal model-metadata tokens that the keyless endpoint
 * occasionally embeds inside a translation segment:
 *
 *   సినిమా ఎలా ఉంది + ee29150929b269c38979323546d85c49 + tea_DravidianA_…md
 *
 * These tokens are Google's model id / model file name, NOT part of the
 * translation. If they leak through the parser they get glued onto the
 * romanized output ("sinima ela undiee29150929…c49").
 *
 * This sanitizer is applied at every persistence/display boundary (cache
 * writes, cache reads, generated candidates, persisted data load) so the
 * tokens can never reach a suggestion — even if a corrupt entry was already
 * stored on-device by an older build.
 */
object TranslationSanitizer {

    // The model id is exactly 32 hex characters. No word-boundary guard is
    // used so a hash glued to a word ("undiee29150929…") is still removed;
    // in practice the only 32-hex runs in Twinglish output are these ids.
    private val HASH = Regex("[0-9a-fA-F]{32}")

    // Google model file names, e.g. tea_DravidianA_en2knmlsitate_2021q3.md
    private val MODEL_FILE = Regex("tea_[A-Za-z0-9_.\\-]+\\.md")

    /** True when [text] contains any known Google metadata token. */
    fun hasGarbage(text: String): Boolean =
        HASH.containsMatchIn(text) || MODEL_FILE.containsMatchIn(text)

    /**
     * Remove every metadata token and tidy the leftover whitespace. A hash
     * glued between two words ("elaundiee2915…") leaves the two words joined
     * ("elaundi") — rare and harmless; the common case ("undi" + space +
     * hash) collapses cleanly to "undi".
     */
    fun clean(text: String): String {
        if (text.isBlank()) return text
        var out = text.replace(HASH, "").replace(MODEL_FILE, "")
        out = out.replace(Regex("\\s{2,}"), " ").trim()
        return out
    }
}
