package com.twinglish.keyboard.ime

import com.twinglish.keyboard.R

/**
 * Layout definitions mirroring the modern Gboard arrangement:
 * QWERTY rows, a bottom row of (emoji, comma, 123, space, period, enter)
 * and two symbol pages. Layouts are data — the view measures and draws them
 * adaptively.
 */
object KeyboardLayouts {

    private val LETTERS_LOWERCASE = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
    )

    private val LETTERS_UPPERCASE = LETTERS_LOWERCASE.map { row -> row.map { it.uppercase() } }

    private val TOP_HINTS = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
    )

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
        "." to listOf(".com"), "," to listOf("،"),
    )

    private fun letterKey(label: String): Key {
        val lower = label.lowercase()
        return Key(
            id = "c:$label",
            action = KeyAction.CHAR,
            label = label,
            labelTop = TOP_HINTS[lower],
            longPress = LONG_PRESS[lower] ?: emptyList(),
            contentDescription = "letter $label",
        )
    }

    private fun symbolKey(label: String, top: String? = null, weight: Float = 1f): Key = Key(
        id = "c:$label",
        label = label,
        labelTop = top,
        weight = weight,
        longPress = LONG_PRESS[label] ?: emptyList(),
    )

    private fun lettersRow(letters: List<String>, indent: Boolean): List<Key> {
        val keys = letters.map { letterKey(it) }
        return if (indent) {
            // Gboard indents the middle row slightly.
            listOf(Key(id = "spacer", label = "", weight = 0.5f)) + keys +
                listOf(Key(id = "spacer2", label = "", weight = 0.5f))
        } else keys
    }

    fun letters(shifted: Boolean, symbolMode: Boolean): List<List<Key>> {
        val source = if (shifted) LETTERS_UPPERCASE else LETTERS_LOWERCASE
        val rows = mutableListOf<List<Key>>()
        rows.add(lettersRow(source[0], indent = false))
        rows.add(lettersRow(source[1], indent = true))
        rows.add(
            buildList {
                add(Key(id = "shift", action = KeyAction.SHIFT, icon = R.drawable.ic_shift, contentDescription = "Shift"))
                source[2].forEach { add(letterKey(it)) }
                add(Key(id = "backspace", action = KeyAction.BACKSPACE, icon = R.drawable.ic_backspace, contentDescription = "Delete"))
            }
        )
        rows.add(bottomRow(symbolMode))
        return rows
    }

    private fun bottomRow(symbolMode: Boolean): List<Key> = listOf(
        Key(id = "emoji", action = KeyAction.MODE_EMOJI, icon = R.drawable.ic_emoji, contentDescription = "Emoji"),
        Key(id = "comma", action = KeyAction.CHAR, label = ",", weight = 1.3f),
        Key(
            id = "mode", action = KeyAction.MODE_SYMBOLS,
            label = if (symbolMode) "ABC" else "?123", weight = 1.5f,
            contentDescription = "Numbers and symbols",
        ),
        Key(id = "space", action = KeyAction.SPACE, label = "", weight = 5.5f, contentDescription = "Space"),
        Key(id = "period", action = KeyAction.CHAR, label = ".", weight = 1.3f, longPress = listOf(".com", ".net", ".org")),
        Key(id = "enter", action = KeyAction.ENTER, label = "↵", weight = 2.0f, contentDescription = "Enter"),
    )

    fun symbols(page: Int): List<List<Key>> {
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
            add(
                listOf(
                    Key(id = "mode", action = KeyAction.MODE_SYMBOLS, label = "ABC", weight = 1.6f),
                    Key(id = "comma", action = KeyAction.CHAR, label = ",", weight = 1.6f),
                    Key(id = "space", action = KeyAction.SPACE, label = "", weight = 5.5f, contentDescription = "Space"),
                    Key(id = "period", action = KeyAction.CHAR, label = ".", weight = 1.6f),
                    Key(id = "enter", action = KeyAction.ENTER, label = "↵", weight = 2.0f, contentDescription = "Enter"),
                )
            )
        }
    }
}
