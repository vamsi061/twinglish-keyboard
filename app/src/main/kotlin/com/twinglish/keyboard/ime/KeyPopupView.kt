package com.twinglish.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/**
 * The temporary key-preview popup: a white rounded bubble with a blue glyph
 * (like the Gboard preview) or a row of long-press alternatives with a
 * sliding highlight.
 */
class KeyPopupView(context: Context) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var colors: KeyboardColors = KeyboardColors.Blue
        set(value) {
            field = value
            bgPaint.color = value.popupBackground
            borderPaint.color = value.boardTopLine
            highlightPaint.color = value.accent
            textPaint.color = value.popupText
            invalidate()
        }

    private var options: List<String> = emptyList()
    private var highlightedIndex = -1
    private var isOptions = false
    private var singleText = ""

    val selectedIndex: Int get() = highlightedIndex

    /** Show a single-character preview. */
    fun showPreview(char: String) {
        options = emptyList()
        singleText = char
        isOptions = false
        invalidate()
    }

    /** Show long-press alternatives. */
    fun showOptions(alternatives: List<String>, highlight: Int) {
        options = alternatives
        highlightedIndex = highlight
        isOptions = true
        invalidate()
    }

    fun setHighlighted(index: Int) {
        if (highlightedIndex != index) {
            highlightedIndex = index
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics)

        borderPaint.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, bgPaint)
        canvas.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius, radius, borderPaint)

        if (!isOptions) {
            textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28f, resources.displayMetrics)
            val baseline = (h - (textPaint.descent() + textPaint.ascent())) / 2f
            canvas.drawText(singleText, w / 2f, baseline, textPaint)
        } else {
            val cell = w / options.size
            for (i in options.indices) {
                val left = i * cell
                if (i == highlightedIndex) {
                    canvas.drawRoundRect(
                        RectF(left + 2f, 2f, left + cell - 2f, h - 2f),
                        radius, radius, highlightPaint,
                    )
                }
                textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24f, resources.displayMetrics)
                val baseline = (h - (textPaint.descent() + textPaint.ascent())) / 2f
                canvas.drawText(options[i], left + cell / 2f, baseline, textPaint)
            }
        }
    }
}
