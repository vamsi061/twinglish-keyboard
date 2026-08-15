# Twinglish Keyboard — Architecture

## Modules

```
┌────────────────────────────┐        ┌──────────────────────────┐
│          app               │        │    twinglish-engine      │
│  (Android application)     │        │    (pure Kotlin / JVM)   │
│                            │        │                          │
│  ime/                      │        │  TwinglishEngine         │
│   TwinglishInputMethodSvc  │  uses  │    └ ContextualTranslator│
│   KeyboardView (custom)    │ ─────► │         └ TranslationPr… │
│   SuggestionStripView      │        │              └ Offline   │
│   ToolbarView              │        │                 Provider │
│   EmojiPanelView           │        │  Romanizer               │
│   ClipboardPanelView       │        │  TranslationStyle/Result │
│  twinglish/                │        │  (unit tested)           │
│   TwinglishController      │        └──────────────────────────┘
│  data/  SettingsRepository │
│  settings/ SettingsActivity│
│  test/ KeyboardTestActivity│
└────────────────────────────┘
```

The split keeps the translation intelligence (engine module) completely
free of Android dependencies: it can be unit-tested on the JVM and swapped
out without touching the keyboard.

## Input path

1. `TwinglishInputMethodService` — the Android `InputMethodService`.
   Classifies the editor (text/password/email/url/number/phone) and refuses
   translation for secure fields. Owns the view stack and dispatch.
2. `InputController` — null-safe wrapper over `InputConnection`
   (commitText, deleteSurroundingText, performEditorAction, phrase
   replacement).
3. `KeyboardView` — custom Canvas-drawn keyboard. Key geometry is computed
   once per size change; touch handling is a lightweight state machine
   (press / slide-cancel / long-press options / backspace auto-repeat).
   No per-keystroke recomposition, so key latency stays low even while the
   engine is translating.
4. `SuggestionStripView` — RecyclerView of chips. English mode shows
   prefix-matched common words; Twinglish mode shows the translation with
   the primary chip emphasized.
5. `TwinglishController` — debounced (380 ms) pipeline with sequence
   numbers, request cancellation and an LRU cache. An old response can
   never overwrite a newer sentence.

## Translation path

- `TranslationProvider` (interface): `translateEnglishToTelugu(text, style)`
  + `romanizeTelugu(text, style)`. `isOnline` flags whether a provider
  needs the network.
- `ContextualTranslator`: wraps any provider with sentence-level smarts —
  strips conversational fillers (`Hey ra,`), splits trailing punctuation,
  protects emoji with placeholders, guards proper nouns and detects
  code-switched (already-Twinglish) sentences.
- `OfflineTranslationProvider`: a phrase-rule grammar (regex → natural
  Telugu template, with group capture for proper nouns) plus a word
  dictionary + light verb conjugation fallback. Style transforms
  (casual → polite/formal) are applied on the Telugu output.
- `Romanizer`: Telugu script → Twinglish. Handles inherent vowels, virama
  clusters (doubled consonants, conjunct overrides), anusvara assimilation,
  and the casual shorthand rules (final `-avu → -av`, cluster/final
  long-vowel shortening).

## Sizing & theming

- Keyboard height: ~42 % of screen height portrait / ~48 % of width
  landscape, clamped, scaled by the user's height setting, applied to both
  the root view and the IME window.
- Content width capped at 900 dp and centered on tablets/large screens.
- `KeyboardColors` (light/dark palettes) flow from settings into every
  view; system theme follows `isNightMode()`.

## Privacy

- No INTERNET permission; translation is offline by default.
- Password / visible-password / email / URL / number fields never reach the
  engine.
- Clipboard is read only when the clipboard panel is opened; pins live in
  DataStore preferences.

## Testing

- `twinglish-engine/src/test` covers the spec's translation test cases
  (casual/polite/formal, strict romanization, punctuation, emoji, proper
  nouns, code switching).
- `app` includes `KeyboardTestActivity` to exercise the IME against every
  field type on-device.
