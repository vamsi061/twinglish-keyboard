package com.twinglish.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.twinglish.keyboard.R
import kotlin.math.abs

/**
 * The keyboard surface. Keys are drawn as plain centered glyphs directly on
 * the blue board (no per-key cards); the only visible shapes are the subtle
 * pressed overlay, the lighter ?123 / spacebar pills and the accent enter
 * key — matching the Gboard-family look of the reference.
 *
 * The full key cell is the touch target; the spacebar additionally supports
 * horizontal drag for cursor movement (and long press for IME switching).
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyPressed(key: Key)
        fun onKeyReleased(key: Key)
        fun onLongPressStart(key: Key)
        fun onPopupDismissed(key: Key)
        /** Horizontal spacebar drag: positive = cursor right, negative = left. */
        fun onCursorMove(steps: Int)
    }

    var listener: Listener? = null

    var colors: KeyboardColors = KeyboardColors.Blue
        set(value) {
            field = value
            textPaint.color = value.text
            hintPaint.color = value.hint
            iconTint = value.icon
            invalidate()
        }

    var popupEnabled: Boolean = true

    /** Vertical offset from this view's origin to the popup host's origin. */
    var popupOffsetY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var rows: List<List<Key>> = emptyList()
    private var geometry: List<List<KeyRect>> = emptyList()
    private var contentWidth = 0f
    private var contentLeft = 0f

    private class KeyRect(val key: Key, val rect: RectF)

    // Paints
    private val boardPaint = Paint()
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private var iconTint: Int = 0
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val handler = Handler(Looper.getMainLooper())
    private var pressedKeyRect: KeyRect? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private var repeatTriggered = false
    private var popupHost: android.widget.FrameLayout? = null
    private var popupView: KeyPopupView? = null

    // Spacebar drag → cursor movement
    private var spaceDragging = false
    private var spaceCursorStep = 0
    private var spaceDown = false
    private var lastCursorX = 0f

    private val longPressRunnable = object : Runnable {
        override fun run() {
            val k = pressedKeyRect?.key ?: return
            if (k.action == KeyAction.BACKSPACE) {
                repeatTriggered = true
                listener?.onLongPressStart(k)
                handler.post(repeatRunnable)
            } else if (k.action == KeyAction.SPACE) {
                // Space long press → IME switch (no repeat, no options).
                longPressTriggered = true
                listener?.onLongPressStart(k)
            } else if (k.longPress.isNotEmpty()) {
                longPressTriggered = true
                listener?.onLongPressStart(k)
            }
        }
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val k = pressedKeyRect?.key ?: return
            listener?.onKeyPressed(k)
            handler.postDelayed(this, 55L)
        }
    }

    fun setLayout(newRows: List<List<Key>>) {
        rows = newRows
        requestLayout()
        invalidate()
    }

    fun setPopupHost(host: android.widget.FrameLayout) {
        popupHost = host
        popupView = KeyPopupView(context).apply {
            colors = this@KeyboardView.colors
        }
    }

    // ---------- measurement / layout ----------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry(w.toFloat(), h.toFloat())
    }

    private fun computeGeometry(w: Float, h: Float) {
        val maxWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 900f, resources.displayMetrics)
        contentWidth = minOf(w, maxWidth)
        contentLeft = (w - contentWidth) / 2f

        val padH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)
        val gap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)
        val padV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)

        val rowCount = rows.size
        if (rowCount == 0) return
        val rowHeight = (h - padV * 2 - gap * (rowCount - 1)) / rowCount

        geometry = rows.mapIndexed { r, rowKeys ->
            val totalWeight = rowKeys.sumOf { it.weight.toDouble() }.toFloat()
            val gaps = gap * (rowKeys.size - 1)
            val available = contentWidth - padH * 2 - gaps
            val unit = available / totalWeight
            var x = contentLeft + padH
            rowKeys.map { key ->
                val kWidth = unit * key.weight
                val rect = RectF(x, padV + r * (rowHeight + gap), x + kWidth, padV + r * (rowHeight + gap) + rowHeight)
                x += kWidth + gap
                KeyRect(key, rect)
            }
        }
    }

    private fun hitTest(x: Float, y: Float): KeyRect? {
        for (row in geometry) {
            for (kr in row) {
                if (kr.rect.contains(x, y) && kr.key.id != "spacer" && kr.key.id != "spacer2") return kr
            }
        }
        return null
    }

    // ---------- touch handling ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                val kr = hitTest(event.x, event.y)
                if (kr != null) {
                    pressedKeyRect = kr
                    announcePressed(kr.key)
                    spaceDown = kr.key.action == KeyAction.SPACE
                    spaceDragging = false
                    spaceCursorStep = 0
                    lastCursorX = event.x
                    if (kr.key.action == KeyAction.SPACE) {
                        // Committed on release (allows swipe for cursor / long press).
                    } else {
                        listener?.onKeyPressed(kr.key)
                        showPopup(kr)
                    }
                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(longPressRunnable, 380L)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val kr = pressedKeyRect ?: return true
                if (longPressTriggered) {
                    if (kr.key.action == KeyAction.SPACE) {
                        // During IME-switch long press, ignore drags.
                    } else {
                        updateLongPressSelection(event.x, event.y)
                    }
                } else if (spaceDown && kr.key.action == KeyAction.SPACE) {
                    // Spacebar drag → cursor movement.
                    val stepPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 26f, resources.displayMetrics)
                    if (abs(event.x - downX) > stepPx && !spaceDragging) {
                        spaceDragging = true
                        handler.removeCallbacks(longPressRunnable)
                        hidePopup()
                    }
                    if (spaceDragging) {
                        val steps = ((event.x - lastCursorX) / stepPx).toInt()
                        if (steps != 0) {
                            listener?.onCursorMove(steps)
                            spaceCursorStep += steps
                            lastCursorX += steps * stepPx
                        }
                    }
                } else {
                    // Cancel press when the finger slides far off the key.
                    val centerX = kr.rect.centerX()
                    val centerY = kr.rect.centerY()
                    val slop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics)
                    if (abs(event.x - centerX) > kr.rect.width() / 2f + slop ||
                        abs(event.y - centerY) > kr.rect.height() / 2f + slop
                    ) {
                        cancelPress()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val kr = pressedKeyRect
                if (kr != null) {
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(repeatRunnable)
                    if (spaceDown && kr.key.action == KeyAction.SPACE) {
                        if (longPressTriggered) {
                            listener?.onKeyReleased(kr.key)
                            listener?.onPopupDismissed(kr.key)
                        } else if (!spaceDragging) {
                            listener?.onKeyPressed(kr.key)
                            listener?.onKeyReleased(kr.key)
                        } else {
                            listener?.onKeyReleased(kr.key)
                        }
                    } else if (longPressTriggered) {
                        val option = selectedOption(kr, event.x)
                        listener?.onKeyReleased(kr.key)
                        listener?.onPopupDismissed(kr.key)
                        if (option != null) {
                            listener?.onKeyPressed(Key(id = "c:$option", action = KeyAction.CHAR, label = option))
                        }
                    } else if (!repeatTriggered) {
                        listener?.onKeyReleased(kr.key)
                    } else {
                        listener?.onPopupDismissed(kr.key)
                    }
                    hidePopup()
                    pressedKeyRect = null
                    repeatTriggered = false
                    longPressTriggered = false
                    spaceDown = false
                    spaceDragging = false
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPress()
                return true
            }
        }
        return true
    }

    private fun cancelPress() {
        val kr = pressedKeyRect
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        if (kr != null) {
            listener?.onPopupDismissed(kr.key)
        }
        hidePopup()
        pressedKeyRect = null
        repeatTriggered = false
        longPressTriggered = false
        spaceDown = false
        spaceDragging = false
        invalidate()
    }

    // ---------- popup ----------

    private var popupShowing = false

    private fun showPopup(kr: KeyRect) {
        if (!popupEnabled) return
        val host = popupHost ?: return
        val pv = popupView ?: return
        if (host.indexOfChild(pv) < 0) host.addView(pv)

        val key = kr.key
        val kRect = kr.rect
        val keyW = kRect.width()
        val keyH = kRect.height()
        val density = resources.displayMetrics.density

        val isOptions = popupEnabled && key.longPress.isNotEmpty()
        val options = if (isOptions) key.longPress else emptyList()

        val maxPopupW = (width - 8f * density).coerceAtLeast(1f)

        // Compact popup: preview scales from the VISUAL key (1.2–1.5x width,
        // 1.3–1.6x height) — never from the touch target.
        val popupW: Float
        val popupH: Float
        if (isOptions) {
            popupH = keyH * 1.5f
            popupW = (options.size * keyH * 1.1f + 12f * density).coerceAtMost(maxPopupW)
        } else {
            popupH = keyH * 1.5f
            popupW = (keyW * 1.35f).coerceAtLeast(keyH * 1.1f).coerceAtMost(maxPopupW)
        }

        // Positioned immediately above the pressed key (small 4dp gap),
        // clamped to stay on screen near edges.
        var left = kRect.centerX() - popupW / 2f
        left = left.coerceIn(4f, (width - popupW - 4f).coerceAtLeast(4f))
        val top = (kRect.top - popupH - 4f * density + popupOffsetY).coerceAtLeast(2f)

        val lp = android.widget.FrameLayout.LayoutParams(popupW.toInt(), popupH.toInt())
        lp.leftMargin = left.toInt()
        lp.topMargin = top.toInt()
        host.updateViewLayout(pv, lp)

        if (isOptions) {
            pv.showOptions(options, 0)
        } else {
            pv.showPreview(key.label)
        }
        pv.visibility = View.VISIBLE
        popupShowing = true
        // Fast, barely-there entrance: 90% → 100% scale with alpha, ~70ms.
        pv.alpha = 0f
        pv.scaleX = 0.9f
        pv.scaleY = 0.9f
        pv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(70L).start()
        pv.invalidate()
    }

    private fun updateLongPressSelection(x: Float, y: Float) {
        val pv = popupView ?: return
        val kr = pressedKeyRect ?: return
        if (pv.visibility != View.VISIBLE) return
        val popupRect = RectF(pv.left.toFloat(), pv.top.toFloat(), pv.right.toFloat(), pv.bottom.toFloat())
        val options = kr.key.longPress
        if (options.isEmpty()) return
        val index = if (popupRect.contains(x, y)) {
            val cell = pv.width / options.size
            ((x - pv.left) / cell).toInt().coerceIn(0, options.size - 1)
        } else {
            -1
        }
        pv.setHighlighted(index)
    }

    private fun selectedOption(kr: KeyRect, x: Float): String? {
        val pv = popupView ?: return null
        val options = kr.key.longPress
        if (options.isEmpty()) return null
        val index = pv.selectedIndex
        return if (index in options.indices && index >= 0) options[index] else options.firstOrNull()
    }

    private fun hidePopup() {
        val pv = popupView ?: return
        if (!popupShowing) return
        popupShowing = false
        pv.animate().cancel()
        // Very short fade-out (~50ms); a re-show during the fade cancels it.
        pv.animate().alpha(0f).setDuration(50L).withEndAction {
            if (!popupShowing) pv.visibility = View.GONE
        }.start()
    }

    // ---------- drawing ----------

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(colors.board)

        val dividerH = dp(1f)
        boardPaint.color = colors.boardTopLine
        canvas.drawRect(0f, 0f, width.toFloat(), dividerH, boardPaint)

        val corner = dp(6f)
        val pillCorner = dp(8f)
        val spaceCorner = dp(10f)
        val inset = dp(1.5f)
        val labelSize = dp(19f)
        val numberSize = dp(17f)
        val smallSize = dp(17f)
        val hintSize = dp(9f)
        val iconSize = dp(22f)

        for (row in geometry) {
            for (kr in row) {
                val key = kr.key
                val rect = kr.rect
                val pressed = kr === pressedKeyRect

                when {
                    key.action == KeyAction.ENTER -> {
                        keyPaint.color = colors.enterKey
                        canvas.drawRoundRect(
                            RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                            pillCorner, pillCorner, keyPaint,
                        )
                        if (pressed) {
                            keyPaint.color = colors.keyPressed
                            canvas.drawRoundRect(
                                RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                                pillCorner, pillCorner, keyPaint,
                            )
                        }
                    }
                    key.id == "mode" -> {
                        keyPaint.color = colors.actionKey
                        canvas.drawRoundRect(
                            RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                            pillCorner, pillCorner, keyPaint,
                        )
                        if (pressed) {
                            keyPaint.color = colors.keyPressed
                            canvas.drawRoundRect(
                                RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                                pillCorner, pillCorner, keyPaint,
                            )
                        }
                    }
                    key.id == "space" -> {
                        keyPaint.color = colors.actionKey
                        canvas.drawRoundRect(
                            RectF(rect.left + inset * 3, rect.top + inset * 2, rect.right - inset * 3, rect.bottom - inset * 2),
                            spaceCorner, spaceCorner, keyPaint,
                        )
                        if (pressed) {
                            keyPaint.color = colors.keyPressed
                            canvas.drawRoundRect(
                                RectF(rect.left + inset * 3, rect.top + inset * 2, rect.right - inset * 3, rect.bottom - inset * 2),
                                spaceCorner, spaceCorner, keyPaint,
                            )
                        }
                    }
                    key.active -> {
                        // Shaded pill inside the key while active (shift on):
                        // lighter for one-shot SHIFT, stronger for CAPS_LOCK.
                        keyPaint.color = if (key.activeStrong) colors.enterKey else colors.actionKey
                        canvas.drawRoundRect(
                            RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                            pillCorner, pillCorner, keyPaint,
                        )
                        if (pressed) {
                            keyPaint.color = colors.keyPressed
                            canvas.drawRoundRect(
                                RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                                pillCorner, pillCorner, keyPaint,
                            )
                        }
                    }
                    pressed -> {
                        keyPaint.color = colors.keyPressed
                        canvas.drawRoundRect(
                            RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                            corner, corner, keyPaint,
                        )
                    }
                }

                val isNumberRow = key.label.isNotEmpty() && key.label[0].isDigit() && key.longPress.none { it.length > 1 }
                if (key.icon != 0) {
                    drawIcon(canvas, key.icon, rect, pressed, iconSize)
                } else if (key.label.isNotEmpty()) {
                    val size = when {
                        key.id == "comma" || key.id == "period" || key.id == "mode" -> smallSize
                        isNumberRow -> numberSize
                        else -> labelSize
                    }
                    textPaint.textSize = size
                    val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(key.label, rect.centerX(), baseline, textPaint)
                }
                if (key.labelTop != null && key.label.isNotEmpty()) {
                    hintPaint.textSize = hintSize
                    val y = rect.top + rect.height() * 0.26f + hintSize * 0.4f
                    canvas.drawText(key.labelTop, rect.centerX(), y, hintPaint)
                }
            }
        }
    }

    private fun drawIcon(canvas: Canvas, resId: Int, rect: RectF, pressed: Boolean, size: Float) {
        val d: Drawable = ContextCompat.getDrawable(context, resId) ?: return
        val tinted = DrawableCompat.wrap(d).mutate()
        DrawableCompat.setTint(tinted, if (pressed) colors.text else iconTint)
        val left = rect.centerX() - size / 2f
        val top = rect.centerY() - size / 2f
        tinted.setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        tinted.draw(canvas)
    }

    private fun announcePressed(key: Key) {
        val desc = key.contentDescription ?: key.label
        if (desc.isNotEmpty()) {
            contentDescription = desc
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
    }

    fun cancelPendingInput() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        hidePopup()
        pressedKeyRect = null
        longPressTriggered = false
        repeatTriggered = false
        spaceDown = false
        spaceDragging = false
        invalidate()
    }
}
