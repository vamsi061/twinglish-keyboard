package com.twinglish.keyboard.ime

import android.graphics.Color
import androidx.core.graphics.toColorInt

/**
 * Flat blue Gboard-family palette. Keys are drawn as text directly on the
 * keyboard surface — no white cards — with a slightly darker toolbar, a
 * subtly different suggestion area, light pressed overlays and lighter
 * action surfaces for the spacebar / ?123 / enter keys.
 */
data class KeyboardColors(
    /** Main keyboard surface (blue). */
    val board: Int,
    /** Divider line under the suggestion strip. */
    val boardTopLine: Int,
    /** Top toolbar surface (slightly darker blue). */
    val toolbarBackground: Int,
    /** Suggestion strip surface (subtly different blue). */
    val stripBackground: Int,
    /** Key label / icon color (white). */
    val text: Int,
    /** Secondary label color (number hints). */
    val hint: Int,
    /** Icon tint. */
    val icon: Int,
    /** Pressed-key overlay (translucent white). */
    val keyPressed: Int,
    /** Lighter surface for ?123 / spacebar pills. */
    val actionKey: Int,
    /** Enter/action button surface. */
    val enterKey: Int,
    /** Key-preview popup bubble. */
    val popupBackground: Int,
    /** Key-preview popup text. */
    val popupText: Int,
    /** Suggestion text color. */
    val suggestionText: Int,
    /** Subtle rounded highlight for the primary suggestion. */
    val suggestionHighlight: Int,
    /** Active-state color (shift active, Twinglish active). */
    val accent: Int,
) {
    companion object {
        private val WHITE = Color.WHITE

        val Blue = KeyboardColors(
            board = "#185ABC".toColorInt(),
            boardTopLine = "#154A9C".toColorInt(),
            toolbarBackground = "#154A9E".toColorInt(),
            stripBackground = "#1753AC".toColorInt(),
            text = WHITE,
            hint = "#A8C6EE".toColorInt(),
            icon = WHITE,
            keyPressed = "#26FFFFFF".toColorInt(),      // white ~15% overlay
            actionKey = "#2E6FC9".toColorInt(),         // lighter blue pill
            enterKey = "#3E7DD6".toColorInt(),          // enter button
            popupBackground = WHITE,
            popupText = "#185ABC".toColorInt(),
            suggestionText = WHITE,
            suggestionHighlight = "#2EFFFFFF".toColorInt(),
            accent = "#8FC1F8".toColorInt(),
        )

        val BlueDark = KeyboardColors(
            board = "#0F2A4C".toColorInt(),
            boardTopLine = "#0C2240".toColorInt(),
            toolbarBackground = "#0D2442".toColorInt(),
            stripBackground = "#0E2646".toColorInt(),
            text = WHITE,
            hint = "#7FA6D6".toColorInt(),
            icon = WHITE,
            keyPressed = "#1FFFFFFF".toColorInt(),
            actionKey = "#1D4376".toColorInt(),
            enterKey = "#2A5A9C".toColorInt(),
            popupBackground = WHITE,
            popupText = "#0F2A4C".toColorInt(),
            suggestionText = WHITE,
            suggestionHighlight = "#26FFFFFF".toColorInt(),
            accent = "#8FC1F8".toColorInt(),
        )
    }
}
