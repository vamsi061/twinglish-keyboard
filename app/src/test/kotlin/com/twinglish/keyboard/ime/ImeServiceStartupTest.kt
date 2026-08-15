package com.twinglish.keyboard.ime

import android.graphics.Canvas
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduces the IME startup path on the JVM: process onCreate, keyboard
 * view construction, first layout pass, first draw, then onStartInputView /
 * onWindowShown and a simulated key press. Any exception here is exactly
 * what kills the keyboard on a real device ("app keeps stopping").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImeServiceStartupTest {

    private fun textEditorInfo(): EditorInfo =
        EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

    @Test
    fun keyboard_starts_and_draws_without_crashing() {
        val controller = Robolectric.buildService(TwinglishInputMethodService::class.java)
        val service = controller.create().get()

        // onCreateInputView
        val root = service.onCreateInputView()
        assertNotNull("onCreateInputView must return a view", root)

        // First measure + layout pass (what the IME window does on show).
        val w = 1080
        val h = 2200
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, w, h)

        // Draw the whole tree once (first frame).
        val canvas = Canvas()
        root.draw(canvas)

        // onStartInputView (system binding to a text field).
        service.onStartInputView(textEditorInfo(), false)

        // onWindowShown → height sizing.
        service.onWindowShown()

        // Second frame after start.
        root.draw(canvas)
    }

    @Test
    fun keyboard_handles_key_press() {
        val controller = Robolectric.buildService(TwinglishInputMethodService::class.java)
        val service = controller.create().get()
        val root = service.onCreateInputView()

        val w = 1080
        val h = 2200
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, w, h)
        service.onStartInputView(textEditorInfo(), false)
        service.onWindowShown()

        // Find the keyboard view inside the tree and tap a letter key.
        val keyboardView = findKeyboardView(root)
        assertNotNull("keyboard view must exist", keyboardView)

        // Row 2 (indented) letter 'a' area: x at ~16% of width, y at row index 1.
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, w * 0.16f, h * 0.62f, 0)
        keyboardView!!.dispatchTouchEvent(down)
        val up = MotionEvent.obtain(0L, 50L, MotionEvent.ACTION_UP, w * 0.16f, h * 0.62f, 0)
        keyboardView!!.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun findKeyboardView(root: View): KeyboardView? {
        fun walk(v: View): KeyboardView? {
            if (v is KeyboardView) return v
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    walk(v.getChildAt(i))?.let { return it }
                }
            }
            return null
        }
        return walk(root)
    }
}
