package com.twinglish.keyboard.engine.translation

/**
 * Dialect / politeness level used when generating Twinglish output.
 * The default is [CASUAL] because the primary use case is chat/messaging.
 */
enum class TranslationStyle(val id: String, val label: String) {
    CASUAL("casual", "Casual"),
    POLITE("polite", "Polite"),
    FORMAL("formal", "Formal");

    companion object {
        fun fromId(id: String?): TranslationStyle =
            entries.firstOrNull { it.id == id } ?: CASUAL
    }
}
