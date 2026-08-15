package com.twinglish.keyboard.ime

import com.twinglish.keyboard.R

/**
 * Layout definitions mirroring the blue Gboard reference: a number row on
 * top, then QWERTY / home / ZXCV rows with plain centered glyphs (the view
 * draws them directly on the surface — no key cards), and a bottom action
 * row where the spacebar dominates. Layouts are pure data; the view measures
 * and draws them adaptively.
 */
object KeyboardLayouts {

    private val NUMBERS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private val LETTERS_LOWERCASE = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
    )

    private val LETTERS_UPPERCASE = LETTERS_LOWERCASE.map { row -> row.map { it.uppercase() } }

    private val LONG_PRESS = mapOf(
        "q" to listOf("1"), "w" to listOf("2"), "e" to listOf("3", "é", "è", "ê", "ë"),
        "r" to listOf("4"), "t" to listOf("5"), "y" to listOf("6"), "u" to listOf("7", "ü", "ú", "ù"),
        "i" to listOf("8", "í", "ì", "î", "ï"), "o" to listOf("9", "ó", "ò", "ô", "õ"),
        "p" to listOf("0"),
        "a" to listOf("@", "á", "à", "â", "ä", "å"), "s" to listOf("#"),
        "d" to listOf("$", "ð"), "f" to listOf("&"), "g" to listOf("_"),
        "h" to listOf("-"), "j" to listOf("+"), "k" to listOf("("), "l" to listOf(")"),
        "z" to listOf("*"), "x" to listOf("\""), "c" to listOf("'"), "v" to listOf(":"),
        "b" to listOf(";"), "n" to listOf("!"), "m" to listOf("?"),
        "1" to listOf("¹"), "2" to listOf("²"), "3" to listOf("³"),
        "4" to listOf("¼"), "5" to listOf("½"), "6" to listOf("¾"),
        "7" to listOf("⑦"), "8" to listOf("⑧"), "9" to listOf("⑨"), "0" to listOf("°"),
        "." to listOf(".com"), "," to listOf("،"),
    )

    private fun letterKey(label: String): Key {
        val lower = label.lowercase()
        return Key(
            id = "c:$label",
            action = KeyAction.CHAR,
            label = label,
            longPress = LONG_PRESS[lower] ?: emptyList(),
            contentDescription = "letter $label",
        )
    }

    private fun numberKey(label: String): Key = Key(
        id = "c:$label",
        action = KeyAction.CHAR,
        label = label,
        longPress = LONG_PRESS[label] ?: emptyList(),
        contentDescription = "number $label",
    )

    private fun symbolKey(label: String, top: String? = null, weight: Float = 1f): Key = Key(
        id = "c:$label",
        label = label,
        labelTop = top,
        weight = weight,
        longPress = LONG_PRESS[label] ?: emptyList(),
    )

    fun letters(
        shifted: Boolean,
        symbolMode: Boolean,
        enterIcon: Int = R.drawable.ic_enter,
        shiftIcon: Int = R.drawable.ic_shift,
    ): List<List<Key>> {
        val source = if (shifted) LETTERS_UPPERCASE else LETTERS_LOWERCASE
        val rows = mutableListOf<List<Key>>()
        rows.add(NUMBERS.map { numberKey(it) })
        rows.add(source[0].map { letterKey(it) })
        rows.add(
            listOf(Key(id = "spacer", label = "", weight = 0.5f)) +
                source[1].map { letterKey(it) } +
                listOf(Key(id = "spacer2", label = "", weight = 0.5f))
        )
        rows.add(
            buildList {
                add(Key(id = "shift", action = KeyAction.SHIFT, icon = shiftIcon, contentDescription = "Shift"))
                source[2].forEach { add(letterKey(it)) }
                add(Key(id = "backspace", action = KeyAction.BACKSPACE, icon = R.drawable.ic_backspace, contentDescription = "Delete"))
            }
        )
        rows.add(bottomRow(symbolMode, enterIcon))
        return rows
    }

    private fun bottomRow(symbolMode: Boolean, enterIcon: Int): List<Key> = listOf(
        Key(
            id = "mode", action = KeyAction.MODE_SYMBOLS,
            label = if (symbolMode) "ABC" else "?123", weight = 1.6f,
            contentDescription = "Numbers and symbols",
        ),
        Key(id = "comma", action = KeyAction.CHAR, label = ",", weight = 1.1f, longPress = listOf("،")),
        Key(id = "space", action = KeyAction.SPACE, label = "", weight = 5.5f, contentDescription = "Space"),
        Key(id = "period", action = KeyAction.CHAR, label = ".", weight = 1.1f, longPress = listOf(".com", ".net", ".org")),
        Key(id = "enter", action = KeyAction.ENTER, label = "", icon = enterIcon, weight = 1.7f, contentDescription = "Enter"),
    )

    fun symbols(page: Int, enterIcon: Int = R.drawable.ic_enter): List<List<Key>> {
        val rows: List<List<String>> = when (page) {
            0 -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "_", "&", "-", "+", "(", ")"),
                listOf("*", "\"", "'", ":", ";", "!", "?"),
            )
            else -> listOf(
                listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
                listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "%"),
                listOf("©", "®", "™", "✓", "✗", "…", "→", "←"),
            )
        }
        return buildList {
            rows.forEachIndexed { idx, row ->
                val keys = row.map { symbolKey(it) }.toMutableList()
                if (idx == rows.size - 1) {
                    keys += Key(id = "backspace", action = KeyAction.BACKSPACE, icon = R.drawable.ic_backspace, weight = 1.6f, contentDescription = "Delete")
                }
                add(keys)
            }
            add(bottomRow(symbolMode = true, enterIcon = enterIcon))
        }
    }
}
