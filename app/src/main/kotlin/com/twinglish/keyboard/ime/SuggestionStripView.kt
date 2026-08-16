package com.twinglish.keyboard.ime

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The Gboard-style suggestion strip: plain text suggestions on the blue
 * surface — no cards, no pills. The primary suggestion gets a subtle rounded
 * highlight only. Updates crossfade quickly instead of flashing.
 */
class SuggestionStripView(context: Context) : FrameLayout(context) {

    data class Suggestion(val text: String, val primary: Boolean = false, val source: String = "")

    var onSuggestionClicked: ((Suggestion) -> Unit)? = null

    /** Long-press on a suggestion — used to open the correction editor. */
    var onSuggestionLongClicked: ((Suggestion) -> Unit)? = null

    var colors: KeyboardColors = KeyboardColors.Blue
        set(value) {
            field = value
            background = android.graphics.drawable.ColorDrawable(value.stripBackground)
            adapter?.notifyDataSetChanged()
        }

    var suggestions: List<Suggestion> = emptyList()
        set(value) {
            val old = field
            field = value
            if (old == value) return
            crossfade { adapter?.submitList(value) }
        }

    private val adapter: ChipAdapter? by lazy {
        ChipAdapter().also {
            it.colors = colors
            it.onClick = { s -> onSuggestionClicked?.invoke(s) }
            it.onLongClick = { s -> onSuggestionLongClicked?.invoke(s) }
            recycler.adapter = it
        }
    }

    private val recycler: RecyclerView

    init {
        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            clipToPadding = false
            setPadding(dp(10), 0, dp(10), 0)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        addView(
            recycler,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL,
            )
        )
        background = android.graphics.drawable.ColorDrawable(colors.stripBackground)
    }

    /** Quick alpha dip + swap so the row never flashes. */
    private fun crossfade(swap: () -> Unit) {
        recycler.animate().cancel()
        recycler.alpha = 0.35f
        recycler.animate()
            .alpha(1f)
            .setDuration(130L)
            .setInterpolator(DecelerateInterpolator())
            .withStartAction(swap)
            .start()
    }

    private class ChipAdapter : RecyclerView.Adapter<ChipAdapter.VH>() {
        var colors: KeyboardColors = KeyboardColors.Blue
        var onClick: ((Suggestion) -> Unit)? = null
        var onLongClick: ((Suggestion) -> Unit)? = null
        private var items: List<Suggestion> = emptyList()

        fun submitList(list: List<Suggestion>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                isClickable = true
                isFocusable = true
                textSize = 15f
                maxLines = 1
                includeFontPadding = false
                setTextColor(colors.suggestionText)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                val m = parent.resources.displayMetrics.density
                marginStart = (m * 4).toInt()
                marginEnd = (m * 4).toInt()
            }
            tv.layoutParams = lp
            return VH(tv)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            val tv = holder.tv
            tv.text = s.text
            tv.setTextColor(colors.suggestionText)
            tv.setTypeface(null, Typeface.NORMAL)
            if (s.primary) {
                tv.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = tv.resources.displayMetrics.density * 14f
                    setColor(this@ChipAdapter.colors.suggestionHighlight)
                }
                tv.setPadding(dp(tv, 14), dp(tv, 5), dp(tv, 14), dp(tv, 5))
            } else {
                tv.background = null
                tv.setPadding(dp(tv, 9), dp(tv, 5), dp(tv, 9), dp(tv, 5))
            }
            tv.setOnClickListener { onClick?.invoke(s) }
            tv.setOnLongClickListener {
                // Consume the long-press so the tap is NOT also fired, and
                // give haptic feedback so the edit affordance is discoverable.
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onLongClick?.invoke(s)
                true
            }
        }

        private fun dp(tv: TextView, value: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), tv.resources.displayMetrics).toInt()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = dp(44)
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
        )
    }
}
