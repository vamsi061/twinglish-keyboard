package com.twinglish.keyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.twinglish.keyboard.R

/**
 * The compact Gboard-style toolbar: evenly spaced icon buttons (apps grid,
 * emoji, GIF, settings, translate, theme) with a circular microphone button
 * on the right. No visible button containers in the resting state; the
 * translate button tints when Twinglish is active. Long-pressing the emoji
 * button opens the clipboard.
 */
class ToolbarView(context: Context) : FrameLayout(context) {

    var onGrid: (() -> Unit)? = null
    var onEmoji: (() -> Unit)? = null
    var onEmojiLongPress: (() -> Unit)? = null
    var onGif: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onTranslate: (() -> Unit)? = null
    var onTheme: (() -> Unit)? = null
    var onMic: (() -> Unit)? = null

    var colors: KeyboardColors = KeyboardColors.Blue
        set(value) {
            field = value
            background = android.graphics.drawable.ColorDrawable(value.toolbarBackground)
            for (b in listOfNotNull(gridButton, emojiButton, settingsButton, themeButton)) {
                b.setColorFilter(value.icon)
            }
            translateButton?.setColorFilter(if (translateActive) value.accent else value.icon)
            micButton?.setColorFilter(value.icon)
            gifLabel?.setTextColor(if (gifActive) value.accent else value.icon)
            micButton?.background?.let {
                (it as? android.graphics.drawable.GradientDrawable)?.setColor(value.actionKey)
            }
        }

    var translateActive: Boolean = false
        set(value) {
            field = value
            translateButton?.setColorFilter(if (value) colors.accent else colors.icon)
        }

    /** Backwards-compatible alias for the Twinglish state. */
    var twinglishActive: Boolean
        get() = translateActive
        set(value) {
            translateActive = value
        }

    private var gridButton: ImageButton? = null
    private var emojiButton: ImageButton? = null
    private var settingsButton: ImageButton? = null
    private var translateButton: ImageButton? = null
    private var themeButton: ImageButton? = null
    private var micButton: ImageButton? = null
    private var gifLabel: TextView? = null

    private var gifActive: Boolean = false

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun iconButton(resId: Int, contentDesc: String, onTap: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(resId)
            this.contentDescription = contentDesc
            background = null
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isClickable = true
            setOnClickListener { onTap() }
        }

    init {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), 0)
        }

        fun addWeighted(view: View, weight: Float) {
            row.addView(view, LinearLayout.LayoutParams(0, dp(44), weight))
        }

        // Apps grid (opens the Twinglish app).
        gridButton = iconButton(R.drawable.ic_grid, "More apps") { onGrid?.invoke() }.also { addWeighted(it, 1f) }

        // Emoji (long press → clipboard).
        emojiButton = iconButton(R.drawable.ic_emoji, "Emoji") { onEmoji?.invoke() }.also { b ->
            b.setOnLongClickListener {
                onEmojiLongPress?.invoke()
                true
            }
            addWeighted(b, 1f)
        }

        // GIF label.
        gifLabel = TextView(context).apply {
            text = "GIF"
            contentDescription = "GIF"
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.icon)
            isClickable = true
            setOnClickListener { onGif?.invoke() }
        }.also { addWeighted(it, 1f) }

        // Settings.
        settingsButton = iconButton(R.drawable.ic_settings, "Settings") { onSettings?.invoke() }.also { addWeighted(it, 1f) }

        // Translate / Twinglish toggle.
        translateButton = iconButton(R.drawable.ic_translate, "Twinglish translation") { onTranslate?.invoke() }.also { addWeighted(it, 1f) }

        // Theme.
        themeButton = iconButton(R.drawable.ic_theme, "Theme") { onTheme?.invoke() }.also { addWeighted(it, 1f) }

        // Microphone — circular highlighted touch area on the right.
        micButton = iconButton(R.drawable.ic_mic, "Voice input") { onMic?.invoke() }.also { mic ->
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(this@ToolbarView.colors.actionKey)
            }
            mic.background = bg
            mic.setPadding(dp(9), dp(9), dp(9), dp(9))
            val lp = LinearLayout.LayoutParams(dp(34), dp(34), 1f)
            lp.gravity = Gravity.CENTER_VERTICAL
            mic.layoutParams = lp
            row.addView(mic, lp)
        }

        addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL,
            )
        )
        background = android.graphics.drawable.ColorDrawable(colors.toolbarBackground)
        colors = colors
    }
}
