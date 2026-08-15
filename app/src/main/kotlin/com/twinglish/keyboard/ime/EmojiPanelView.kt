package com.twinglish.keyboard.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A compact emoji picker that lives inside the keyboard (no separate
 * activity). Category tabs scroll the grid to the relevant section.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    var onEmojiPicked: ((String) -> Unit)? = null
    var onBackToKeyboard: (() -> Unit)? = null

    var colors: KeyboardColors = KeyboardColors.Blue
        set(value) {
            field = value
            background = android.graphics.drawable.ColorDrawable(value.board)
            gridAdapter.notifyDataSetChanged()
            tabs.forEach { it.setTextColor(value.icon) }
        }

    private val categories = listOf(
        "Smileys" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃",
            "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
            "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "🤩", "😏", "😒", "😞", "😔", "😟",
            "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠",
            "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
            "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
            "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧",
            "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻",
            "💀", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀",
            "😿", "😾",
        ),
        "Gestures" to listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘",
            "🤙", "👈", "👉", "👆", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜",
            "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦵",
            "🦶", "👂", "🦻", "👃", "🧠", "🦷", "👀", "👁️", "👅", "👄", "💋", "🫶",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
            "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥️", "💯", "💢", "💥", "💫",
            "💦", "💨", "🕳️", "💣", "💬", "👁️🗨️", "🗨️", "🗯️", "💭", "💤",
        ),
        "Animals" to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮",
            "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
            "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱",
            "🐛", "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🦟", "🦗", "🕷️", "🕸️", "🦂",
            "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠",
            "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🦣",
            "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🦬", "🐃", "🐂", "🐄", "🐎",
            "🐖", "🐏", "🐑", "🦙", "🐐", "🦌", "🐕", "🐩", "🦮", "🐈", "🪶", "🐓",
        ),
        "Food" to listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒",
            "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️",
            "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯", "🍞", "🥖",
            "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴",
            "🌭", "🍔", "🍟", "🍕", "🫓", "🥪", "🥙", "🧆", "🌮", "🌯", "🫔", "🥗",
            "🥘", "🫕", "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤",
            "🍙", "🍚", "🍘", "🍥", "🥠", "🥮", "🍢", "🍡", "🍧", "🍨", "🍦", "🥧",
            "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🥜",
            "🍯", "🥛", "🍼", "🫖", "☕", "🍵", "🧃", "🥤", "🧋", "🍶", "🍺", "🍻",
            "🥂", "🍷", "🥃", "🍸", "🍹", "🧉", "🍾", "🧊", "🥄", "🍴", "🍽️", "🥣",
            "🥡", "🥢", "🧂",
        ),
        "Travel" to listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚",
            "🚛", "🚜", "🛵", "🏍️", "🛺", "🚲", "🛴", "🚨", "🚔", "🚍", "🚘", "🚖",
            "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆",
            "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀", "🛸", "🚁",
            "🛶", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "🪝", "⛽", "🚧", "🚦",
            "🚥", "🚏", "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡", "🎢", "🎠",
            "⛲", "⛱️", "🏖️", "🏝️", "🏜️", "🌋", "⛰️", "🏔️", "🗻", "🏕️", "⛺", "🛖",
            "🏠", "🏡", "🏘️", "🏚️", "🏗️", "🏭", "🏢", "🏬", "🏣", "🏤", "🏥", "🏦",
            "🏨", "🏪", "🏫", "🏩", "💒", "🏛️", "⛪", "🕌", "🕍", "🛕", "🕋", "⛩️",
        ),
        "Objects" to listOf(
            "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️", "🗜️",
            "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥", "📽️", "🎞️", "📞",
            "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️", "🎛️", "🧭", "⏱️", "⏲️", "⏰",
            "🕰️", "⌛", "⏳", "📡", "🔋", "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🗑️",
            "🛢️", "💸", "💵", "💴", "💶", "💷", "🪙", "💰", "💳", "💎", "⚖️", "🪜",
            "🧰", "🪛", "🔧", "🔨", "⚒️", "🛠️", "⛏️", "🪚", "🔩", "⚙️", "🪤", "🧱",
            "⛓️", "🧲", "🔫", "💣", "🧨", "🪓", "🔪", "🗡️", "⚔️", "🛡️", "🚬", "⚰️",
            "🪦", "⚱️", "🏺", "🔮", "📿", "🧿", "💈", "⚗️", "🔭", "🔬", "🕳️", "🩹",
            "🩺", "🩻", "🩼", "💊", "💉", "🩸", "🧬", "🦠", "🧫", "🧪", "🌡️", "🧹",
            "🪠", "🧺", "🧻", "🚽", "🚰", "🚿", "🛁", "🛀", "🧼", "🪥", "🪒", "🧽",
            "🪣", "🧴", "🛎️", "🔑", "🗝️", "🚪", "🪑", "🛋️", "🛏️", "🧸", "🪆", "🖼️",
            "🪞", "🪟", "🛍️", "🛒", "🎁", "🎈", "🎏", "🎀", "🪄", "🪅", "🎊", "🎉",
            "🎎", "🏮", "🎐", "🧧", "✉️", "📩", "📨", "📧", "💌", "📥", "📤", "📦",
            "🏷️", "🪧", "📪", "📫", "📬", "📭", "📮", "📯", "📜", "📃", "📄", "📑",
            "🧾", "📊", "📈", "📉", "🗒️", "🗓️", "📆", "📅", "🗑️", "📇", "🗃️", "🗳️",
            "🗄️", "📋", "📁", "📂", "🗂️", "🗞️", "📰", "📓", "📔", "📒", "📕", "📗",
            "📘", "📙", "📚", "📖", "🔖", "🧷", "🔗", "📎", "🖇️", "📐", "📏", "🧮",
            "📌", "📍", "✂️", "🖊️", "🖋️", "✒️", "🖌️", "🖍️", "📝", "✏️", "🔍", "🔎",
            "🔏", "🔐", "🔒", "🔓",
        ),
        "Symbols" to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
            "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥️", "💯", "💢", "💥", "💫",
            "💦", "💨", "🕳️", "💣", "💬", "👁️🗨️", "🗨️", "🗯️", "💭", "💤", "🔴",
            "🟠", "🟡", "🟢", "🔵", "🟣", "🟤", "⚫", "⚪", "🟥", "🟧", "🟨", "🟩",
            "🟦", "🟪", "🟫", "⬛", "⬜", "◼️", "◻️", "◾", "◽", "▪️", "▫️", "🔶",
            "🔷", "🔸", "🔹", "🔺", "🔻", "💠", "🔘", "🔳", "🔲", "♈", "♉", "♊",
            "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓", "⛎", "🔀", "🔁",
            "🔂", "▶️", "⏩", "⏭️", "⏯️", "◀️", "⏪", "⏮️", "🔼", "⏫", "🔽", "⏬",
            "⏸️", "⏹️", "⏺️", "⏏️", "🎦", "🔅", "🔆", "📶", "📳", "📴", "♾️", "♻️",
            "✅", "❌", "❓", "❔", "❗", "❗", "‼️", "⁉️", "💲", "⚕️", "♟️", "🛐",
        ),
    )

    private val tabs = mutableListOf<TextView>()
    private val gridAdapter = EmojiAdapter()
    private lateinit var grid: GridView

    init {
        orientation = VERTICAL
        background = android.graphics.drawable.ColorDrawable(colors.board)

        // Category tab row.
        val tabRow = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tabContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        categories.forEachIndexed { index, (name, _) ->
            val tab = TextView(context).apply {
                text = name
                textSize = 12f
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setTextColor(colors.icon)
            }
            tab.setOnClickListener {
                grid.setSelection(index * GRID_COLUMNS + 1)
            }
            tabs += tab
            tabContainer.addView(tab)
        }
        tabRow.addView(tabContainer)
        addView(tabRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))

        // Emoji grid.
        grid = GridView(context).apply {
            numColumns = GRID_COLUMNS
            adapter = gridAdapter
            verticalSpacing = 0
            horizontalSpacing = 0
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(2))
        }
        addView(grid, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private inner class EmojiAdapter : BaseAdapter() {
        override fun getCount(): Int = categories.sumOf { it.second.size }
        override fun getItem(position: Int): Any {
            var p = position
            for ((_, list) in categories) {
                if (p < list.size) return list[p]
                p -= list.size
            }
            return ""
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 24f
                setPadding(0, dp(6), 0, dp(6))
            }
            tv.text = getItem(position) as String
            tv.setOnClickListener {
                onEmojiPicked?.invoke(tv.text.toString())
            }
            return tv
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val GRID_COLUMNS = 8
    }
}
