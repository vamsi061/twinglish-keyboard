package com.twinglish.keyboard.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Clipboard panel: shows the current clipboard entry plus user-pinned
 * snippets. Pinning is stored by the caller (service) — this view is purely
 * presentation + interaction.
 */
class ClipboardPanelView(context: Context) : LinearLayout(context) {

    data class Item(val id: String, val text: String, val pinned: Boolean)

    var onPaste: ((Item) -> Unit)? = null
    var onPin: ((Item) -> Unit)? = null
    var onDelete: ((Item) -> Unit)? = null
    var onBackToKeyboard: (() -> Unit)? = null

    var colors: KeyboardColors = KeyboardColors.Blue
        set(value) {
            field = value
            background = android.graphics.drawable.ColorDrawable(value.board)
            rebuild()
        }

    var items: List<Item> = emptyList()
        set(value) {
            field = value
            rebuild()
        }

    private val container = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    init {
        orientation = VERTICAL
        background = android.graphics.drawable.ColorDrawable(colors.board)
        addView(container, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rebuild()
    }

    private fun rebuild() {
        container.removeAllViews()

        val title = TextView(context).apply {
            setText("Clipboard")
            setTextColor(colors.hint)
            textSize = 13f
            setPadding(0, 0, 0, dp(6))
        }
        container.addView(title)

        if (items.isEmpty()) {
            val empty = TextView(context).apply {
                setText("Nothing copied yet")
                setTextColor(colors.hint)
                textSize = 15f
                setPadding(0, dp(16), 0, dp(16))
            }
            container.addView(empty)
        } else {
            items.forEach { item ->
                container.addView(rowFor(item))
            }
        }

        val back = TextView(context).apply {
            setText("Close clipboard")
            setTextColor(colors.accent)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
            setOnClickListener { onBackToKeyboard?.invoke() }
        }
        container.addView(back)
    }

    private fun rowFor(item: Item): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val text = TextView(context).apply {
            setText(item.text)
            setTextColor(colors.suggestionText)
            textSize = 15f
            maxLines = 2
            isClickable = true
            setOnClickListener { onPaste?.invoke(item) }
        }
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val pin = TextView(context).apply {
            setText(if (item.pinned) "📌" else "📌\uFE0E")
            textSize = 14f
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener { onPin?.invoke(item) }
        }
        val del = TextView(context).apply {
            setText("✕")
            textSize = 15f
            setPadding(dp(10), dp(4), dp(2), dp(4))
            setOnClickListener { onDelete?.invoke(item) }
        }
        row.addView(pin)
        row.addView(del)
        return row
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
