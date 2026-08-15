# Twinglish Keyboard

A production-style Android system keyboard (IME) that turns **English into
natural Telugu Twinglish** — the romanized Telugu people actually type in
chat — with a modern Gboard-like UI.

```
You type:      What are you doing?
Suggestion:    Em chestunnav?
Tap it:        the field becomes "Em chestunnav?"
```

The keyboard is a real `InputMethodService`, works in WhatsApp, Instagram,
Telegram, Messages, Gmail, Chrome — anywhere a text field exists — and keeps
working fully offline.

---

## Features

- **Real system keyboard** — registered IME with `BIND_INPUT_METHOD`, works
  in every app, no copy/paste needed.
- **Gboard-style UI** — QWERTY with adaptive proportions, rounded keys,
  key popup preview, long-press alternatives, haptics, light/dark/system
  themes, landscape + tablet layouts, adaptive key height.
- **Suggestion strip** — English word suggestions, or Twinglish sentence
  translation while you type. Tap to apply; your text is never replaced
  without you asking.
- **Twinglish engine** — offline rule-based English → Telugu → romanized
  Twinglish translation behind a clean `TranslationProvider` interface.
  Casual / Polite / Formal styles, Casual / Strict romanization,
  punctuation & emoji preserved, proper nouns kept (`Hyderabad`), and
  code-switching respected (`Nenu office ki velthunna`).
- **Numbers & symbols** pages, **emoji picker**, **clipboard panel**
  (paste / pin / delete).
- **Settings app** — theme, height, haptics, sounds, popups,
  auto-capitalization, Twinglish style, romanization, privacy notes.
- **Privacy first** — no network calls, no analytics, no telemetry, no
  unnecessary permissions, passwords never processed.

---

## Building the APK

Requirements: JDK 17, Android SDK (platform 35, build-tools 35.0.0),
Gradle 8.11.1 (the wrapper is included).

```bash
# point at your SDK (or set ANDROID_HOME)
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Run the tests (Twinglish engine + translation test cases):

```bash
./gradlew :twinglish-engine:test
```

> Note for ARM (aarch64) hosts: the Android build tools ship x86_64-only
> native binaries. This project was built on aarch64 by running `aapt2`
> through `qemu-user` (`android.aapt2FromMavenOverride` in
> `gradle.properties`). On a normal x86_64 machine you can delete that
> property.

---

## Installing & enabling the keyboard

1. Install `app-debug.apk` (side-load; Android will ask to allow unknown
   sources).
2. Open **Settings → System → Languages & input → On-screen keyboard →
   Manage keyboards**, enable **Twinglish Keyboard**.
3. Open any chat app, tap a text field, and when the keyboard appears use
   the **globe button in the toolbar** (or long-press the spacebar) to
   switch to Twinglish Keyboard.
4. Use the launcher app for a quick **Enable** shortcut, a **test screen**
   with every field type, and **settings**.

## Using Twinglish

1. Make sure the Twinglish toggle in the toolbar (the **తె** button) is
   active — tap it to toggle.
2. Type English normally, e.g. `how are you`.
3. The suggestion strip shows `Ela unnav?` (debounced as you type).
4. Tap the suggestion — the English phrase is replaced by the Twinglish
   result.
5. Nothing is ever replaced automatically; tapping the suggestion is always
   your explicit choice.

Style and romanization can be changed in Settings → Twinglish (Casual /
Polite / Formal; Casual / Strict romanization).

## How the Twinglish engine works

```
English input
   ↓  (ContextualTranslator: strips fillers/punctuation/emoji, protects
   ↓   proper nouns and code-switched words)
sentence
   ↓  (OfflineTranslationProvider: phrase-rule grammar → natural Telugu)
Telugu script (e.g. ఏం చేస్తున్నావు)
   ↓  (Romanizer: Telugu script → casual Twinglish)
Twinglish  (e.g. em chestunnav)
```

The keyboard talks to the translation layer **only through**
`TranslationProvider` (in `twinglish-engine`). The built-in
`OfflineTranslationProvider` is a pattern-based grammar with a word
dictionary fallback — not a hard-coded sentence table. A remote provider
(e.g. a machine-translation API) can be plugged in later by implementing
the same interface; no keyboard code changes.

## Adding another language later

- Implement the provider interface for the new language pair, or
- generalize `OfflineTranslationProvider`'s phrase-rule table, and
- swap the provider in `TwinglishEngine` (`app` module) — the keyboard,
  suggestion strip and settings do not need to change.

## Privacy behavior

- All translation runs **offline** on the device. Nothing you type is sent
  anywhere.
- No permissions are requested beyond what an IME requires; no network
  permission, no storage, no location.
- Password, email, URL and number fields never trigger translation.
- Clipboard is only read when you open the clipboard panel; pinned clips
  are stored locally in app preferences.

## Project layout

```
app/                  Android application: IME service, keyboard UI,
                      suggestion pipeline, settings & test screens
twinglish-engine/     Pure-Kotlin translation engine (no Android deps):
                      providers, romanizer, contextual translator, tests
docs/ARCHITECTURE.md  Architecture notes
```

See `docs/ARCHITECTURE.md` for module responsibilities and the
`TranslationProvider` contract.

## License

Open source. Built for Telugu speakers.
