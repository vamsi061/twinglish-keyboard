package com.twinglish.keyboard.ime

/**
 * What a key does when tapped.
 */
enum class KeyAction {
    CHAR,           // inserts text (letters, digits, punctuation)
    SPACE,
    ENTER,
    BACKSPACE,
    SHIFT,
    MODE_SYMBOLS,   // 123 / ABC toggle
    MODE_EMOJI,
    MODE_CLIPBOARD,
    LANGUAGE,       // EN / తె toggle + long-press menu
    MIC,
    TWINGLISH,      // toolbar toggle
    SETTINGS,
    GLOBE,          // switch system keyboard
}

/**
 * A single key on a keyboard row. Width is expressed as a [weight] relative
 * to a normal letter key (1.0). [labelTop] is the small secondary label
 * shown above the main label (e.g. "1" over "!" or "ABC" over "123").
 */
data class Key(
    val id: String,
    val action: KeyAction = KeyAction.CHAR,
    val label: String = "",
    val labelTop: String? = null,
    val weight: Float = 1f,
    val longPress: List<String> = emptyList(),
    val icon: Int = 0,          // drawable resource for icon keys
    val contentDescription: String? = null,
)
