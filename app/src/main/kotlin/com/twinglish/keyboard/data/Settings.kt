package com.twinglish.keyboard.data

import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationStyle

/** Theme preference for the keyboard and settings UI. */
enum class KeyboardTheme(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): KeyboardTheme =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Haptic intensity. */
enum class HapticMode(val id: String) {
    OFF("off"),
    LIGHT("light"),
    MEDIUM("medium");

    companion object {
        fun fromId(id: String?): HapticMode =
            entries.firstOrNull { it.id == id } ?: LIGHT
    }
}

/**
 * All user-facing settings. [SettingsRepository] persists these through
 * DataStore preferences and exposes a StateFlow.
 */
data class Settings(
    val theme: KeyboardTheme = KeyboardTheme.SYSTEM,
    /** Keyboard height as a percentage of the default (50–150). */
    val keyboardHeightPercent: Int = 100,
    /** Draw visible key borders (key gap). */
    val keyBorders: Boolean = true,
    val keyPressSound: Boolean = false,
    val hapticMode: HapticMode = HapticMode.LIGHT,
    val popupPreview: Boolean = true,
    val autoCapitalization: Boolean = true,
    val keyPreviewOn: Boolean = true,

    // Twinglish
    val twinglishEnabled: Boolean = true,
    val autoSuggestTwinglish: Boolean = true,
    val translationStyle: TranslationStyle = TranslationStyle.CASUAL,
    val romanizationStyle: RomanizationStyle = RomanizationStyle.CASUAL,
    val onlineTranslationEnabled: Boolean = false,

    // Privacy
    val networkUsage: Boolean = false,

    // Personalization (privacy-first, local only)
    /** Master switch: learn the user's Twinglish preferences. */
    val personalizationEnabled: Boolean = true,
    /** Learn from edits of accepted suggestions ("movie" → "sinima"). */
    val learnCorrections: Boolean = true,
    /** Re-rank suggestions using learned preferences. */
    val personalizedSuggestions: Boolean = true,
    /** Learn English words the user keeps (code-switching). */
    val learnVocabulary: Boolean = true,
)
