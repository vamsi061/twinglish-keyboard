package com.twinglish.keyboard.ime

import android.content.Context
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
 * The compact top toolbar: Twinglish toggle, emoji, clipboard, settings,
 * language switch and (reserved) microphone. Buttons are drawn with the
 * active palette and are big enough to be reliable touch targets.
 */
class ToolbarView(context: Context) : FrameLayout(context) {

    var onTwinglishToggle: ((Boolean) -> Unit)? = null
    var onEmoji: (() -> Unit)? = null
    var onClipboard: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onGlobe: (() -> Unit)? = null
    var onMic: (() -> Unit)? = null

    var colors: KeyboardColors = KeyboardColors.Light
        set(value) {
            field = value
            background = android.graphics.drawable.ColorDrawable(value.stripBackground)
            twinglishButton?.let { b ->
                b.setTextColor(if (twinglishActive) value.accent else value.icon)
            }
            for (b in listOfNotNull(emojiButton, clipboardButton, settingsButton, globeButton, micButton)) {
                b.setColorFilter(value.icon)
            }
        }

    var twinglishActive: Boolean = false
        set(value) {
            field = value
            twinglishButton?.setTextColor(if (value) colors.accent else colors.icon)
            twinglishButton?.alpha = if (value) 1f else 0.55f
        }

    private var twinglishButton: TextView? = null
    private var emojiButton: ImageButton? = null
    private var clipboardButton: ImageButton? = null
    private var settingsButton: ImageButton? = null
    private var globeButton: ImageButton? = null
    private var micButton: ImageButton? = null

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    init {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }

        fun textButton(text: String, contentDesc: String): TextView =
            TextView(context).apply {
                this.text = text
                contentDescription = contentDesc
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                minimumWidth = dp(48)
                minimumHeight = dp(40)
            }

        fun iconButton(resId: Int, contentDesc: String): ImageButton =
            ImageButton(context).apply {
                setImageResource(resId)
                this.contentDescription = contentDesc
                background = null
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(40))
            }

        twinglishButton = textButton("తె", "Twinglish mode").also { tb ->
            tb.setOnClickListener { onTwinglishToggle?.invoke(!twinglishActive) }
            row.addView(tb, LinearLayout.LayoutParams(dp(56), dp(40)))
        }

        emojiButton = iconButton(R.drawable.ic_emoji, "Emoji").also {
            it.setOnClickListener { onEmoji?.invoke() }
            row.addView(it)
        }
        clipboardButton = iconButton(R.drawable.ic_clipboard, "Clipboard").also {
            it.setOnClickListener { onClipboard?.invoke() }
            row.addView(it)
        }
        globeButton = iconButton(R.drawable.ic_globe, "Switch keyboard").also {
            it.setOnClickListener { onGlobe?.invoke() }
            row.addView(it)
        }
        micButton = iconButton(R.drawable.ic_mic, "Voice input (coming soon)").also {
            it.setOnClickListener { onMic?.invoke() }
            row.addView(it)
        }

        // Push settings to the far right.
        row.addView(
            View(context),
            LinearLayout.LayoutParams(0, 0, 1f)
        )
        settingsButton = iconButton(R.drawable.ic_settings, "Settings").also {
            it.setOnClickListener { onSettings?.invoke() }
            row.addView(it)
        }

        addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL,
            )
        )
        background = android.graphics.drawable.ColorDrawable(colors.stripBackground)
        colors = colors
    }
}
