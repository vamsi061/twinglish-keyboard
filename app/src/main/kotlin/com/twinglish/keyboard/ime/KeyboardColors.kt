package com.twinglish.keyboard.ime

import android.graphics.Color
import androidx.core.graphics.toColorInt

/**
 * Gboard-like keyboard palette. Keys are nearly-white on a light grey board
 * in light mode; dark mode uses the classic grey-on-charcoal scheme.
 */
data class KeyboardColors(
    val board: Int,
    val key: Int,
    val keyPressed: Int,
    val text: Int,
    val hint: Int,
    val icon: Int,
    val stripBackground: Int,
    val chipBackground: Int,
    val chipSelectedBackground: Int,
    val suggestionText: Int,
    val accent: Int,
    val boardTopLine: Int,
) {
    companion object {
        val Light = KeyboardColors(
            board = "#F2F3F5".toColorInt(),
            key = "#FFFFFF".toColorInt(),
            keyPressed = "#D3D8DE".toColorInt(),
            text = "#1C1B1F".toColorInt(),
            hint = "#757C85".toColorInt(),
            icon = "#5F6368".toColorInt(),
            stripBackground = "#F2F3F5".toColorInt(),
            chipBackground = Color.TRANSPARENT,
            chipSelectedBackground = "#0B57D01A".toColorInt(), // translucent blue
            suggestionText = "#1C1B1F".toColorInt(),
            accent = "#0B57D0".toColorInt(),
            boardTopLine = "#E3E5E8".toColorInt(),
        )

        val Dark = KeyboardColors(
            board = "#1F2023".toColorInt(),
            key = "#3C4043".toColorInt(),
            keyPressed = "#5F6368".toColorInt(),
            text = "#E8EAED".toColorInt(),
            hint = "#9AA0A6".toColorInt(),
            icon = "#E8EAED".toColorInt(),
            stripBackground = "#1F2023".toColorInt(),
            chipBackground = Color.TRANSPARENT,
            chipSelectedBackground = "#8AB4F826".toColorInt(),
            suggestionText = "#E8EAED".toColorInt(),
            accent = "#8AB4F8".toColorInt(),
            boardTopLine = "#2A2B2E".toColorInt(),
        )
    }
}
