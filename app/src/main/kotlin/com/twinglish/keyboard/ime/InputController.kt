package com.twinglish.keyboard.ime

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Thin, null-safe wrapper around the active [InputConnection]. Every method
 * degrades gracefully (returns false / empty) when no editor is attached —
 * the IME must never crash because the connection disappeared.
 */
class InputController {

    private var delegate: InputConnection? = null

    fun attach(connection: InputConnection?) {
        delegate = connection
    }

    fun detach() {
        delegate = null
    }

    val isActive: Boolean get() = delegate != null

    fun commitText(text: CharSequence): Boolean =
        delegate?.commitText(text, 1) ?: false

    fun deleteBackward(): Boolean =
        delegate?.deleteSurroundingText(1, 0) ?: false

    fun deleteBefore(count: Int): Boolean =
        delegate?.deleteSurroundingText(count, 0) ?: false

    fun deleteForward(): Boolean =
        delegate?.deleteSurroundingText(0, 1) ?: false

    fun setComposing(text: CharSequence): Boolean =
        delegate?.setComposingText(text, 1) ?: false

    fun finishComposing(): Boolean =
        delegate?.finishComposingText() ?: false

    fun setComposingRegion(start: Int, end: Int): Boolean =
        delegate?.setComposingRegion(start, end) ?: false

    fun textBeforeCursor(length: Int): CharSequence =
        delegate?.getTextBeforeCursor(length, 0) ?: ""

    fun textAfterCursor(length: Int): CharSequence =
        delegate?.getTextAfterCursor(length, 0) ?: ""

    fun selectedText(): CharSequence =
        delegate?.getSelectedText(0) ?: ""

    fun performEditorAction(actionId: Int): Boolean =
        delegate?.performEditorAction(actionId) ?: false

    fun performPrivateCommand(action: String, data: Bundle?): Boolean =
        delegate?.performPrivateCommand(action, data) ?: false

    fun beginBatch(): Boolean =
        delegate?.beginBatchEdit() ?: false

    fun endBatch(): Boolean =
        delegate?.endBatchEdit() ?: false

    fun sendKey(keyCode: Int): Boolean =
        delegate?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)) ?: false

    /**
     * Replace the [composedText] phrase immediately before the cursor (or, as
     * a fallback, the trailing word) with [replacement]. The keyboard composes
     * plain text, so the phrase itself is located in the surrounding text.
     */
    fun replaceComposingOrLastWord(replacement: String, composedText: String?): Boolean {
        val ic = delegate ?: return false
        val before = ic.getTextBeforeCursor(128, 0)?.toString().orEmpty()
        if (!composedText.isNullOrEmpty()) {
            val index = before.lastIndexOf(composedText)
            if (index >= 0) {
                val toDelete = before.length - index
                return ic.deleteSurroundingText(toDelete, 0) && ic.commitText(replacement, 1)
            }
        }
        // Fall back: delete the trailing word, then commit.
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        val lastWord = trimmed.substring(lastSpace + 1)
        if (lastWord.isNotEmpty()) {
            return ic.deleteSurroundingText(lastWord.length, 0) && ic.commitText(replacement, 1)
        }
        return ic.commitText(replacement, 1)
    }
}
