package com.twinglish.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Shows the last crash stack trace on screen so it can be read, screenshotted
 * or copied straight out of the "app keeps stopping" situation. Plain Views
 * only — must work even when everything else is broken.
 */
class CrashReportActivity : android.app.Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent?.getStringExtra(EXTRA_TRACE)
            ?: "No stack trace captured."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "Twinglish Keyboard crashed"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)

        val hint = TextView(this).apply {
            text = "This screen appears instead of the crash dialog. Copy the error below and send it — it shows the exact line that failed."
            textSize = 13f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(hint)

        val traceView = TextView(this).apply {
            text = trace
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            setBackgroundColor(Color.parseColor("#1C1B1F"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val scroll = ScrollView(this).apply {
            addView(traceView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val copy = Button(this).apply {
            text = "Copy error text"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("twinglish-crash", trace))
                Toast.makeText(this@CrashReportActivity, "Error copied to clipboard — paste it in the chat", Toast.LENGTH_LONG).show()
            }
        }
        root.addView(copy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        val exit = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        root.addView(exit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        val wrap = FrameLayoutLike(this, root)
        setContentView(wrap)
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#263238")))
    }

    /** Simple full-screen frame wrapper. */
    private class FrameLayoutLike(context: Context, child: android.view.View) :
        android.widget.FrameLayout(context) {
        init {
            addView(child, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TRACE = "extra_trace"
    }
}
