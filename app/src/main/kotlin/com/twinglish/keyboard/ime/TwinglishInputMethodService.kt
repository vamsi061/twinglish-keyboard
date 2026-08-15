package com.twinglish.keyboard.ime

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import com.twinglish.keyboard.MainActivity
import com.twinglish.keyboard.R
import com.twinglish.keyboard.TwinglishApplication
import com.twinglish.keyboard.data.HapticMode
import com.twinglish.keyboard.data.KeyboardTheme
import com.twinglish.keyboard.data.Settings
import com.twinglish.keyboard.settings.SettingsActivity
import com.twinglish.keyboard.twinglish.TwinglishController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Twinglish keyboard: a real Android InputMethodService.
 *
 * Handles every editor type, drives the Gboard-style view stack (toolbar,
 * suggestion strip, keys, emoji and clipboard panels) and runs the
 * debounced Twinglish suggestion pipeline. Never activates translation for
 * password or other secure fields.
 */
class TwinglishInputMethodService : android.inputmethodservice.InputMethodService() {

    private lateinit var app: TwinglishApplication
    private lateinit var settingsRepo: com.twinglish.keyboard.data.SettingsRepository

    private val input = InputController()
    private lateinit var controller: TwinglishController
    private var settings: Settings = Settings()

    // Views
    private lateinit var root: FrameLayout
    private lateinit var popupOverlay: FrameLayout
    private lateinit var toolbar: ToolbarView
    private lateinit var strip: SuggestionStripView
    private lateinit var keyboardView: KeyboardView
    private lateinit var contentFrame: FrameLayout
    private lateinit var emojiPanel: EmojiPanelView
    private lateinit var clipboardPanel: ClipboardPanelView
    private lateinit var bottomBar: FrameLayout

    // State
    private var symbolPage = 0
    private var shiftState = ShiftState.LOWERCASE
    private var twinglishActive = false
    private var mode = Mode.LETTERS
    private var editorClass = EditorClass.TEXT
    private var enterActionLabel: String? = null
    private var repeatActive = false
    private var currentSentence = ""

    /** Explicit keyboard shift state machine. */
    private enum class ShiftState { LOWERCASE, SHIFTED, CAPS_LOCK }
    private enum class Mode { LETTERS, SYMBOLS, EMOJI, CLIPBOARD }
    private enum class EditorClass { TEXT, PASSWORD, EMAIL, URL, NUMBER, PHONE, OTHER }

    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private var pinnedClips: List<String> = emptyList()

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        app = TwinglishApplication.from(this)
        settingsRepo = app.settingsRepository
        controller = TwinglishController(
            engine = app.twinglishEngine,
            scope = app.applicationScope,
            styleProvider = { settings.translationStyle },
            romanStyleProvider = { settings.romanizationStyle },
        )

        app.applicationScope.launch {
            settingsRepo.settings.collectLatest { s ->
                // Views must only be touched on the main thread — a stray
                // background write (or touching an not-yet-created view)
                // would take the whole IME process down.
                withContext(Dispatchers.Main) {
                    settings = s
                    applySettingsToViews()
                }
            }
        }

        app.applicationScope.launch {
            controller.state.collectLatest { state ->
                if (translationAllowed && ::strip.isInitialized) {
                    val list = mutableListOf<SuggestionStripView.Suggestion>()
                    state.primary?.let {
                        list += SuggestionStripView.Suggestion(text = it, primary = true, source = "twinglish")
                    }
                    state.alternatives.forEach {
                        list += SuggestionStripView.Suggestion(text = it, primary = false, source = "twinglish")
                    }
                    // Translation failed (no confident rule): fall back to
                    // English word suggestions — never a partial hybrid.
                    if (list.isEmpty() && state.sentence.isNotBlank()) {
                        val word = state.sentence.substringAfterLast(' ').lowercase()
                        list += englishSuggestions(word)
                    }
                    val suggestions = list
                    withContext(Dispatchers.Main) {
                        if (::strip.isInitialized) strip.suggestions = suggestions
                    }
                }
            }
        }

        registerClipboardListener()
    }

    override fun onCreateInputView(): View {
        popupOverlay = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        toolbar = ToolbarView(this).apply {
            onGrid = { openApp() }
            onEmoji = { toggleMode(Mode.EMOJI) }
            onEmojiLongPress = { toggleMode(Mode.CLIPBOARD) }
            onGif = { showVoiceHint("GIF picker coming in a future update") }
            onSettings = { openSettings() }
            onTranslate = { setTwinglishActive(!twinglishActive) }
            onTheme = { cycleTheme() }
            onMic = { showVoiceHint() }
        }
        strip = SuggestionStripView(this).apply {
            onSuggestionClicked = { s -> commitSuggestion(s.text, s.source) }
        }
        keyboardView = KeyboardView(this).apply {
            listener = keyboardListener
            setPopupHost(popupOverlay)
        }
        emojiPanel = EmojiPanelView(this).apply {
            onEmojiPicked = { e -> input.commitText(e) }
        }
        clipboardPanel = ClipboardPanelView(this).apply {
            onPaste = { item -> pasteClip(item) }
            onPin = { item -> togglePin(item) }
            onDelete = { item -> deleteClip(item) }
        }

        contentFrame = FrameLayout(this).apply {
            addView(keyboardView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(emojiPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(clipboardPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        emojiPanel.visibility = View.GONE
        clipboardPanel.visibility = View.GONE

        // Slim bottom bar: keyboard switch (left) + collapse chevron (right).
        // Its bottom padding carries the system navigation inset.
        bottomBar = FrameLayout(this).apply {
            val globe = ImageButton(context).apply {
                setImageResource(R.drawable.ic_globe)
                contentDescription = "Switch keyboard"
                background = null
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(14), dp(4), dp(14), dp(4))
                setOnClickListener { switchToNextIme() }
            }
            val chevron = ImageButton(context).apply {
                setImageResource(R.drawable.ic_chevron_down)
                contentDescription = "Hide keyboard"
                background = null
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(14), dp(4), dp(14), dp(4))
                setOnClickListener { requestHideSelf(0) }
            }
            addView(globe, FrameLayout.LayoutParams(dp(48), dp(32), Gravity.START or Gravity.CENTER_VERTICAL))
            addView(chevron, FrameLayout.LayoutParams(dp(48), dp(32), Gravity.END or Gravity.CENTER_VERTICAL))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)))
            addView(strip, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)))
            addView(contentFrame, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(bottomBar, LinearLayout.LayoutParams(MATCH_PARENT, dp(32)))
        }

        root = FrameLayout(this).apply {
            clipChildren = false
            addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(popupOverlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        // Keep the keyboard above the system navigation bar.
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (::bottomBar.isInitialized) {
                bottomBar.setPadding(0, 0, 0, insets.systemWindowInsetBottom)
            }
            insets
        }

        keyboardView.popupOffsetY = dp(92).toFloat()
        applySettingsToViews()
        refreshLayout()
        return root
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        input.attach(currentInputConnection)
        classifyEditor(editorInfo)
        configureEnterKey(editorInfo)

        mode = Mode.LETTERS
        symbolPage = 0
        currentSentence = ""

        // The keyboard ALWAYS opens in lowercase. Sentence-start
        // capitalization is re-armed while typing (after ".!?" + space and
        // after a newline, when autoCapitalization is enabled) rather than
        // at field open, so the keys never sit stuck in uppercase.
        shiftState = ShiftState.LOWERCASE
        twinglishActive = settings.twinglishEnabled
        if (::toolbar.isInitialized) {
            toolbar.translateActive = twinglishActive
            toolbar.twinglishActive = twinglishActive
        }

        if (editorClass == EditorClass.NUMBER || editorClass == EditorClass.PHONE) {
            mode = Mode.SYMBOLS
            symbolPage = 0
        }

        refreshLayout()
        updateSuggestions("")
        controller.clear()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        input.detach()
        controller.clear()
        // The system can start/finish input BEFORE onCreateInputView has ever
        // run (e.g. right after the IME is enabled), so the input view may
        // not exist yet. Never touch it unconditionally here.
        if (::keyboardView.isInitialized) keyboardView.cancelPendingInput()
    }

    override fun onDestroy() {
        input.detach()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshHeight()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Text may have changed externally — refresh the suggestion sentence.
        if (input.isActive && newSelStart == newSelEnd) {
            refreshSentenceFromCursor()
        }
    }

    override fun onStartInput(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)
        input.attach(currentInputConnection)
    }

    // ------------------------------------------------------------------
    // keyboard height & theming
    // ------------------------------------------------------------------

    override fun onWindowShown() {
        super.onWindowShown()
        refreshHeight()
    }

    private fun computeKeyboardHeight(): Int {
        val dm = resources.displayMetrics
        val portrait = dm.heightPixels > dm.widthPixels
        val baseDp = if (portrait) dm.heightPixels / dm.density * 0.44f else dm.widthPixels / dm.density * 0.48f
        val clamped = baseDp.coerceIn(if (portrait) 260f else 160f, if (portrait) 470f else 300f)
        val percent = (settings.keyboardHeightPercent.coerceIn(50, 150)) / 100f
        return (clamped * percent * dm.density).toInt().coerceAtLeast(dp(140))
    }

    private fun refreshHeight() {
        val height = computeKeyboardHeight()
        if (::root.isInitialized) {
            // root.layoutParams is null until the view is attached to the
            // IME window (onCreateInputView runs before that), so it must be
            // null-checked — an NPE here kills the whole keyboard.
            val lp = root.layoutParams
            if (lp != null) {
                lp.height = height
                root.layoutParams = lp
            }
        }
        // Size the IME window itself so the keyboard height is honored.
        runCatching {
            val window = getWindow().window ?: return@runCatching
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
    }

    private fun currentColors(): KeyboardColors =
        when (settings.theme) {
            KeyboardTheme.LIGHT -> KeyboardColors.Blue
            KeyboardTheme.DARK -> KeyboardColors.BlueDark
            KeyboardTheme.SYSTEM ->
                if (isNightMode()) KeyboardColors.BlueDark else KeyboardColors.Blue
        }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun applySettingsToViews() {
        // Called from onCreateInputView (main thread) and from the settings
        // collector (switched to main thread). Guard every view: the settings
        // emission can arrive while the input view is still being built.
        if (!::keyboardView.isInitialized) return
        val c = currentColors()
        keyboardView.colors = c
        keyboardView.popupEnabled = settings.popupPreview
        if (::strip.isInitialized) strip.colors = c
        if (::toolbar.isInitialized) {
            toolbar.colors = c
            toolbar.twinglishActive = twinglishActive
        }
        if (::emojiPanel.isInitialized) emojiPanel.colors = c
        if (::clipboardPanel.isInitialized) clipboardPanel.colors = c
        if (::bottomBar.isInitialized) {
            bottomBar.background = android.graphics.drawable.ColorDrawable(c.toolbarBackground)
        }
        applyWindowColors(c)
        refreshHeight()
    }

    /**
     * Paint the system navigation/status bars to match the keyboard surface
     * (the nav bar sits directly under the bottom bar, so it must share its
     * color for a seamless look). Icons flip between light/dark by luminance.
     */
    private fun applyWindowColors(c: KeyboardColors) {
        runCatching {
            val w = getWindow().window ?: return@runCatching
            w.navigationBarColor = c.toolbarBackground
            w.statusBarColor = c.board
            val controller = WindowCompat.getInsetsController(w, w.decorView)
            val lightIcons = luminance(c.toolbarBackground) > 0.5f
            controller.isAppearanceLightNavigationBars = lightIcons
            controller.isAppearanceLightStatusBars = lightIcons
        }
    }

    private fun luminance(color: Int): Float {
        val r = android.graphics.Color.red(color) / 255f
        val g = android.graphics.Color.green(color) / 255f
        val b = android.graphics.Color.blue(color) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    // ------------------------------------------------------------------
    // editor classification
    // ------------------------------------------------------------------

    private fun classifyEditor(editorInfo: EditorInfo?) {
        editorClass = when {
            editorInfo == null -> EditorClass.TEXT
            else -> {
                val type = editorInfo.inputType
                val cls = type and InputType.TYPE_MASK_CLASS
                val variation = type and InputType.TYPE_MASK_VARIATION
                when {
                    cls == InputType.TYPE_CLASS_NUMBER -> EditorClass.NUMBER
                    cls == InputType.TYPE_CLASS_PHONE -> EditorClass.PHONE
                    cls == InputType.TYPE_CLASS_DATETIME -> EditorClass.NUMBER
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> EditorClass.PASSWORD
                    variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EditorClass.EMAIL
                    variation == InputType.TYPE_TEXT_VARIATION_URI -> EditorClass.URL
                    else -> EditorClass.TEXT
                }
            }
        }
    }

    private fun configureEnterKey(editorInfo: EditorInfo?) {
        val action = editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        val actionId = action and EditorInfo.IME_MASK_ACTION
        enterActionLabel = when (actionId) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> null
        }
    }

    private val translationAllowed: Boolean
        get() = twinglishActive && settings.autoSuggestTwinglish &&
            (editorClass == EditorClass.TEXT)

    // ------------------------------------------------------------------
    // keyboard events
    // ------------------------------------------------------------------

    private val keyboardListener = object : KeyboardView.Listener {
        override fun onKeyPressed(key: Key) {
            if (!repeatActive) {
                haptic()
                playKeySound()
            }
            handleKey(key)
        }

        override fun onKeyReleased(key: Key) {
            repeatActive = false
        }

        override fun onLongPressStart(key: Key) {
            if (key.action == KeyAction.BACKSPACE) {
                repeatActive = true
            } else if (key.id == "space" || key.id == "mode") {
                switchToNextIme()
            }
        }

        override fun onPopupDismissed(key: Key) {
            repeatActive = false
        }

        override fun onCursorMove(steps: Int) {
            // Spacebar horizontal drag → move the cursor left/right.
            val keyCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            repeat(kotlin.math.abs(steps)) { input.sendKey(keyCode) }
        }
    }

    private fun handleKey(key: Key) {
        when (key.action) {
            KeyAction.CHAR -> commitChar(key.label)
            KeyAction.SPACE -> commitSpace()
            KeyAction.ENTER -> performEnter()
            KeyAction.BACKSPACE -> {
                input.deleteBackward()
                refreshSentenceFromCursor()
                updateSuggestions(currentSentence)
            }
            KeyAction.SHIFT -> toggleShift()
            KeyAction.MODE_SYMBOLS -> toggleSymbolMode()
            KeyAction.MODE_EMOJI -> toggleMode(Mode.EMOJI)
            KeyAction.MODE_CLIPBOARD -> toggleMode(Mode.CLIPBOARD)
            KeyAction.LANGUAGE -> setTwinglishActive(!twinglishActive)
            KeyAction.GLOBE -> switchToNextIme()
            KeyAction.MIC -> showVoiceHint()
            KeyAction.SETTINGS -> openSettings()
            KeyAction.TWINGLISH -> setTwinglishActive(!twinglishActive)
        }
    }

    private fun commitChar(char: String) {
        if (char.isEmpty()) return
        when {
            editorClass == EditorClass.NUMBER || editorClass == EditorClass.PHONE -> {
                // Digits and basic punctuation only.
                if (char.all { it.isDigit() || it in ".,+-*#()/ " }) {
                    input.commitText(char)
                }
                return
            }
            editorClass == EditorClass.PASSWORD -> {
                input.commitText(char)
                return
            }
        }

        // A single SHIFT tap capitalizes exactly one letter, then the
        // keyboard returns to lowercase. CAPS_LOCK keeps capitalizing until
        // the shift key is tapped again.
        if (shiftState == ShiftState.SHIFTED && char.length == 1 && char[0].isLetter()) {
            input.commitText(char.uppercase())
            shiftState = ShiftState.LOWERCASE
            refreshLayout()
        } else {
            input.commitText(char)
        }

        // Auto-capitalize the next word after sentence-ending punctuation.
        if (settings.autoCapitalization && editorClass == EditorClass.TEXT &&
            char in listOf(".", "!", "?", "\n")
        ) {
            shiftState = ShiftState.SHIFTED
            refreshLayout()
        }

        refreshSentenceFromCursor()
        updateSuggestions(currentSentence)
    }

    private fun commitSpace() {
        input.commitText(" ")
        // A space after ".!?" keeps the next sentence capitalized; anywhere
        // else the keyboard returns to lowercase. The text is read a few
        // chars back (the just-committed space sits right before the cursor)
        // so the sentence-ending mark is actually found.
        val before = input.textBeforeCursor(16).toString().trimEnd(' ')
        shiftState = if (settings.autoCapitalization && editorClass == EditorClass.TEXT &&
            before.isNotEmpty() && before.last() in ".!?"
        ) {
            ShiftState.SHIFTED
        } else {
            ShiftState.LOWERCASE
        }
        refreshLayout()
        refreshSentenceFromCursor()
        updateSuggestions(currentSentence)
    }

    private fun performEnter() {
        val action = currentInputEditorInfo?.imeOptions ?: 0
        val actionId = action and EditorInfo.IME_MASK_ACTION
        val flagNoEnter = action and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED && !flagNoEnter) {
            input.performEditorAction(actionId)
            shiftState = ShiftState.LOWERCASE
        } else {
            input.commitText("\n")
            // A newline starts a new sentence in multiline fields.
            shiftState = if (settings.autoCapitalization && editorClass == EditorClass.TEXT) {
                ShiftState.SHIFTED
            } else {
                ShiftState.LOWERCASE
            }
        }
        refreshLayout()
        currentSentence = ""
        updateSuggestions("")
    }

    private fun toggleShift() {
        shiftState = when (shiftState) {
            ShiftState.LOWERCASE -> ShiftState.SHIFTED
            ShiftState.SHIFTED -> ShiftState.CAPS_LOCK
            ShiftState.CAPS_LOCK -> ShiftState.LOWERCASE
        }
        refreshLayout()
    }

    private fun toggleSymbolMode() {
        if (mode == Mode.SYMBOLS) {
            symbolPage = 0
            mode = Mode.LETTERS
        } else {
            mode = Mode.SYMBOLS
        }
        refreshLayout()
    }

    private fun toggleMode(newMode: Mode) {
        mode = if (mode == newMode) Mode.LETTERS else newMode
        refreshLayout()
        if (mode == Mode.CLIPBOARD) refreshClipboardItems()
    }

    private fun setTwinglishActive(active: Boolean) {
        twinglishActive = active
        if (::toolbar.isInitialized) {
            toolbar.translateActive = active
            toolbar.twinglishActive = active
        }
        updateSuggestions(currentSentence)
    }

    private fun refreshLayout() {
        if (!::keyboardView.isInitialized) return
        val enter = enterIcon()
        val shiftIcon = when (shiftState) {
            ShiftState.LOWERCASE -> R.drawable.ic_shift
            ShiftState.SHIFTED -> R.drawable.ic_shift_active
            ShiftState.CAPS_LOCK -> R.drawable.ic_shift_caps
        }
        val rows = when (mode) {
            Mode.LETTERS -> KeyboardLayouts.letters(shiftState != ShiftState.LOWERCASE, symbolMode = false, enterIcon = enter, shiftIcon = shiftIcon)
            Mode.SYMBOLS -> KeyboardLayouts.symbols(symbolPage, enterIcon = enter)
            Mode.EMOJI -> KeyboardLayouts.letters(false, symbolMode = false, enterIcon = enter, shiftIcon = R.drawable.ic_shift)
            Mode.CLIPBOARD -> KeyboardLayouts.letters(false, symbolMode = false, enterIcon = enter, shiftIcon = R.drawable.ic_shift)
        }
        keyboardView.setLayout(rows)
        emojiPanel.isVisible = mode == Mode.EMOJI
        clipboardPanel.isVisible = mode == Mode.CLIPBOARD
        keyboardView.isVisible = mode == Mode.LETTERS || mode == Mode.SYMBOLS
        if (::toolbar.isInitialized) {
            toolbar.translateActive = twinglishActive
            toolbar.twinglishActive = twinglishActive
        }
    }

    // ------------------------------------------------------------------
    // suggestions
    // ------------------------------------------------------------------

    private fun refreshSentenceFromCursor() {
        if (!input.isActive) return
        val before = input.textBeforeCursor(200).toString()
        val segment = sentenceBeforeCursor(before)
        currentSentence = segment
        updateSuggestions(segment)
    }

    private fun sentenceBeforeCursor(before: String): String {
        var text = before
        // Walk back over trailing spaces.
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        // Find the last sentence delimiter before that.
        var start = end
        while (start > 0 && text[start - 1] !in ".!?\n") start--
        return text.substring(start, end).trim()
    }

    private fun updateSuggestions(sentence: String) {
        // May be reached from onUpdateSelection before the input view exists.
        if (!::strip.isInitialized) return
        if (!translationAllowed) {
            // English mode: simple prefix suggestions for the current word.
            if (editorClass != EditorClass.TEXT) {
                strip.suggestions = emptyList()
                return
            }
            val word = sentence.substringAfterLast(' ').lowercase()
            val suggestions = englishSuggestions(word)
            strip.suggestions = suggestions
            return
        }
        // Twinglish mode: debounced translation of the current sentence.
        controller.onSentenceChanged(sentence)
    }

    private fun englishSuggestions(prefix: String): List<SuggestionStripView.Suggestion> {
        if (prefix.isEmpty()) return emptyList()
        val matches = COMMON_WORDS.filter { it.startsWith(prefix) }.take(3)
        return matches.mapIndexed { i, w ->
            SuggestionStripView.Suggestion(text = w, primary = i == 0, source = "english")
        }
    }

    private fun commitSuggestion(text: String, source: String) {
        when (source) {
            "english" -> {
                // Replace the current partial word with the full suggestion.
                val before = input.textBeforeCursor(64).toString()
                val partial = before.substringAfterLast(' ')
                if (partial.isNotEmpty() && partial.all { it.isLetter() }) {
                    input.beginBatch()
                    input.deleteBefore(partial.length)
                    input.commitText(text)
                    input.endBatch()
                } else {
                    input.commitText(text)
                }
            }
            else -> {
                // Twinglish: replace the sentence fragment.
                if (input.replaceComposingOrLastWord(text, currentSentence)) {
                    currentSentence = ""
                    controller.clear()
                    strip.suggestions = emptyList()
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // haptics / sound
    // ------------------------------------------------------------------

    private fun haptic() {
        when (settings.hapticMode) {
            HapticMode.OFF -> {}
            HapticMode.LIGHT -> keyboardView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            HapticMode.MEDIUM -> {
                val v = vibrator()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(25)
                }
            }
        }
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    private fun playKeySound() {
        if (!settings.keyPressSound) return
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audio?.playSoundEffect(AudioManager.FX_KEY_CLICK)
    }

    // ------------------------------------------------------------------
    // clipboard
    // ------------------------------------------------------------------

    private fun registerClipboardListener() {
        runCatching {
            clipboardManager.addPrimaryClipChangedListener {
                refreshClipboardItems()
            }
        }
    }

    @SuppressLint("NewApi")
    private fun refreshClipboardItems() {
        if (mode != Mode.CLIPBOARD) return
        val items = mutableListOf<ClipboardPanelView.Item>()
        runCatching {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString()
                if (text.isNotBlank()) {
                    items += ClipboardPanelView.Item(id = "clip", text = text, pinned = false)
                }
            }
        }
        pinnedClips.forEach { text ->
            items += ClipboardPanelView.Item(id = "pin:$text", text = text, pinned = true)
        }
        clipboardPanel.items = items
    }

    private fun pasteClip(item: ClipboardPanelView.Item) {
        input.commitText(item.text)
        toggleMode(Mode.LETTERS)
    }

    private fun togglePin(item: ClipboardPanelView.Item) {
        pinnedClips = if (item.pinned) {
            pinnedClips - item.text
        } else {
            (listOf(item.text) + pinnedClips).distinct().take(20)
        }
        refreshClipboardItems()
    }

    private fun deleteClip(item: ClipboardPanelView.Item) {
        pinnedClips = pinnedClips - item.text
        refreshClipboardItems()
    }

    // ------------------------------------------------------------------
    // misc actions
    // ------------------------------------------------------------------

    private fun openSettings() {
        // Called from the service context — the NEW_TASK flag is required
        // or Android throws AndroidRuntimeException and kills the IME.
        val intent = Intent(this, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun switchToNextIme() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val window = getWindow().window ?: return
        val token = window.attributes.token ?: return
        imm.switchToNextInputMethod(token, true)
    }

    private fun showVoiceHint(message: String = "Voice input coming in a future update") {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun cycleTheme() {
        val next = when (settings.theme) {
            KeyboardTheme.SYSTEM -> KeyboardTheme.LIGHT
            KeyboardTheme.LIGHT -> KeyboardTheme.DARK
            KeyboardTheme.DARK -> KeyboardTheme.SYSTEM
        }
        settingsRepo.updateAsync { it.copy(theme = next) }
    }

    private fun enterIcon(): Int = when (enterActionLabel) {
        "Go" -> R.drawable.ic_go
        "Search" -> R.drawable.ic_search
        "Send" -> R.drawable.ic_send
        "Next" -> R.drawable.ic_next
        "Done" -> R.drawable.ic_done
        else -> R.drawable.ic_enter
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "TwinglishIME"

        private val COMMON_WORDS = listOf(
            "the", "and", "that", "this", "what", "how", "are", "you", "your",
            "i", "am", "is", "was", "were", "have", "has", "had", "do", "does",
            "did", "will", "would", "can", "could", "should", "shall", "may",
            "might", "must", "not", "no", "yes", "okay", "ok", "please",
            "thank", "thanks", "good", "bad", "great", "nice", "love", "like",
            "want", "need", "go", "going", "gone", "come", "coming", "came",
            "see", "seen", "know", "think", "say", "said", "tell", "make",
            "get", "take", "give", "find", "call", "eat", "drink", "sleep",
            "work", "play", "talk", "walk", "run", "read", "write", "watch",
            "listen", "study", "help", "wait", "meet", "friend", "family",
            "home", "house", "school", "office", "work", "today", "tomorrow",
            "yesterday", "now", "later", "morning", "evening", "night", "week",
            "month", "year", "time", "day", "date", "place", "phone", "number",
            "message", "text", "email", "water", "food", "tea", "coffee",
            "money", "price", "where", "when", "why", "who", "which", "there",
            "here", "hello", "hi", "hey", "bye", "see you", "ok",
        )
    }
}
