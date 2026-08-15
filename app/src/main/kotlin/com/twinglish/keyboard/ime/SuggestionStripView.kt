package com.twinglish.keyboard.ime

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The Gboard-style suggestion strip. Holds up to [MAX_ITEMS] chips; in
 * Twinglish mode the primary translation chip is emphasized with a tinted
 * pill. Chips are simple TextViews inside a RecyclerView for smooth
 * scrolling.
 */
class SuggestionStripView(context: Context) : FrameLayout(context) {

    data class Suggestion(val text: String, val primary: Boolean = false, val source: String = "")

    var onSuggestionClicked: ((Suggestion) -> Unit)? = null

    var colors: KeyboardColors = KeyboardColors.Light
        set(value) {
            field = value
            background = android.graphics.drawable.ColorDrawable(value.stripBackground)
            adapter?.notifyDataSetChanged()
        }

    var suggestions: List<Suggestion> = emptyList()
        set(value) {
            field = value
            adapter?.submitList(value)
        }

    private val adapter: ChipAdapter? by lazy {
        ChipAdapter().also {
            it.colors = colors
            it.onClick = { s -> onSuggestionClicked?.invoke(s) }
            recycler.adapter = it
        }
    }

    private val recycler: RecyclerView

    init {
        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            clipToPadding = false
            setPadding(dp(8), 0, dp(8), 0)
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

    private class ChipAdapter : RecyclerView.Adapter<ChipAdapter.VH>() {
        var colors: KeyboardColors = KeyboardColors.Light
        var onClick: ((Suggestion) -> Unit)? = null
        private var items: List<Suggestion> = emptyList()

        fun submitList(list: List<Suggestion>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT
                isClickable = true
                isFocusable = true
                textSize = 16f
                maxLines = 1
                includeFontPadding = false
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
            tv.setBackgroundResource(0)
            if (s.primary) {
                val chipColor = colors.chipSelectedBackground
                tv.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = tv.resources.displayMetrics.density * 18f
                    setColor(chipColor)
                }
                tv.setTypeface(null, Typeface.BOLD)
                tv.setTextColor(colors.accent)
                tv.setPadding(dp(tv, 16), dp(tv, 7), dp(tv, 16), dp(tv, 7))
            } else {
                tv.setTypeface(null, Typeface.NORMAL)
                tv.setPadding(dp(tv, 10), dp(tv, 7), dp(tv, 10), dp(tv, 7))
            }
            tv.setOnClickListener { onClick?.invoke(s) }
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
