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
- **Gboard-style UI** — flat blue keyboard matching the Gboard look:
  always-visible number row, flat keys with pressed overlay, space/action
  pills, accent-colored enter key (search/done/send/next/go icons), a
  7-icon toolbar with mic circle, key popup preview, long-press
  alternatives, haptics, light/dark/system themes, landscape + tablet
  layouts, adaptive key height, and spacebar cursor drag.
- **Suggestion strip** — English word suggestions, or Twinglish sentence
  translation while you type. Tap to apply; **long-press** any Twinglish
  suggestion to correct it. Your text is never replaced without you asking.
- **Twinglish engine** — two-stage English → Telugu → romanized Twinglish
  translation behind a clean `TranslationProvider` interface. A curated
  offline phrase grammar answers instantly and works without a network; an
  optional **Google Translate** stage (Settings → Online translation, off
  by default) handles sentences the phrase bank can't. Casual / Polite /
  Formal styles, Casual / Strict romanization, punctuation & emoji
  preserved, proper nouns kept (`Hyderabad`), and code-switching respected
  (`Nenu office ki velthunna`).
- **Self-learning (on-device)** — accepted suggestions, long-press
  corrections and passed-over suggestions teach the engine the user's
  preferred wording (`sinima` over `movie`, `chestunnav` over
  `chestunnavu`). Corrections are remembered with a confidence score that
  re-ranks future candidates, translations are cached for instant repeat
  lookups, and frequently used phrases autocomplete. Everything is stored
  locally and can be cleared from Settings.
- **Numbers & symbols** pages, **emoji picker**, **clipboard panel**
  (paste / pin / delete).
- **Settings app** — theme, height, haptics, sounds, popups,
  auto-capitalization, Twinglish style, romanization, online translation,
  personalization toggles + a "What Twinglish learned" view, privacy
  notes.
- **Privacy first** — no analytics, no telemetry, no unnecessary
  permissions; translation and learning run on-device. Online translation
  is opt-in and sends only the current sentence; passwords are never
  processed.

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
5. **Correct a translation:** long-press the suggestion — an editor
   opens with a blinking cursor so you can see exactly where you're
   typing (tap anywhere in the field to reposition). Edit the
   Twinglish, then tap the **✓ (check)** button to apply or the **✕
   (close)** button to cancel. The field is updated with your version,
   and the engine learns it — the next time the same (or a similar)
   sentence is typed, your corrected wording is shown first.
6. Nothing is ever replaced automatically; tapping the suggestion is
   always your explicit choice.

Style and romanization can be changed in Settings → Twinglish (Casual /
Polite / Formal; Casual / Strict romanization). Personalization can be
tuned in Settings → Personalization (learn preferences / corrections /
vocabulary, personalized suggestions, Clear Learned Data).

## How the Twinglish engine works

```
English input
   ↓  Input Normalizer (case / repeated-whitespace folded)
normalized sentence
   ↓  Personalization lookup
   │   1. exact user-approved translation
   │   2. exact translation cache        ← cache hit returns instantly
   │   3. strong learned phrase
   ↓  cache miss
   ↓  OfflineTranslationProvider (phrase-rule grammar → natural Telugu)
   ↓  + optional Google Translate stage for unseen sentences (opt-in)
Telugu script (e.g. ఏం చేస్తున్నావు)
   ↓  Romanizer (Telugu script → casual Twinglish)
Twinglish  (e.g. em chestunnav)
   ↓  Personalization (learned word preferences + candidate re-ranking)
final suggestion
```

The keyboard talks to the translation layer **only through**
`TranslationProvider` (in `twinglish-engine`). The built-in
`OfflineTranslationProvider` is a pattern-based grammar with a word
dictionary fallback — not a hard-coded sentence table. It always answers
  first (exact, instant, offline); the optional `GoogleTranslationProvider`
  handles only the sentences the phrase bank can't translate.

### Human-chat output, not bot output

Translations are tuned to sound like a person texting, not textbook
Telugu: the offline bank covers everyday conversational sentences
(greetings, plans, food, travel, sleep, work, feelings — e.g. "i am on
my way" → *nenu vastunna*, "did you have lunch" → *lunch chesava?*,
"i am bored" → *naaku bor kodutondi*), the Google path's casual style
shortens formal verb endings (`నేను చేస్తున్నాను` → **chestunna**, not
"chestunnanu"; `ఏమి` → **ఏం**; `మీ` → **నీ**), and the Romanizer uses
common chat spellings (kasepu, khaali ga, nachindi, nidrapoyava,
bagunda). Code-switched English loanwords stay English (`labs`,
`update`, `content`, `video`, `office`, `meeting`, `call` — never
"lyab" / "apdet"), and invisible zero-width characters that Google can
embed in Telugu script are stripped before romanization, so the output
is always clean roman characters.

### Caching & self-learning

Every result is cached locally (bounded, ~5,000 entries, LRU eviction)
with normalized keys — `How are you?`, `how are you?`, `how  are you?`
and `HOW ARE YOU` all resolve to the same entry (case, repeated
whitespace and trailing punctuation are folded), so repeated sentences
return in a few milliseconds without re-translating — and without
hitting Google again. Once a sentence has been translated (even online),
it is always served from the local cache from then on.

The learning engine records **only meaningful events** — tapping a
suggestion, long-pressing to correct it,
or typing over a shown suggestion — never raw keystrokes. Corrections
learn word preferences with a confidence score that grows on every repeat
(`movie → sinima` becomes strong after a couple of confirmations), and
re-ranking is additive: weak or out-of-context preferences are ignored, so
personalization can never make translation worse. All of it lives in local
app storage and can be wiped from Settings (Clear Learned Data / Reset
Twinglish Preferences).

## Adding another language later

- Implement the provider interface for the new language pair, or
- generalize `OfflineTranslationProvider`'s phrase-rule table, and
- swap the provider in `TwinglishEngine` (`app` module) — the keyboard,
  suggestion strip and settings do not need to change.

## Privacy behavior

- Default translation is fully **offline** on the device — nothing you
  type is sent anywhere.
- Optional **online translation** (Settings → Online translation, off by
  default) sends only the *current sentence* to Google Translate when the
  offline phrase bank can't translate it. It can be disabled at any time.
- Personalization data (cached translations, learned preferences, learned
  phrases, corrections) is stored **locally** on the device. It is never
  uploaded, never used for analytics, and can be cleared from Settings
  (Clear Learned Data / Reset Twinglish Preferences).
- Password, PIN and other secure fields never trigger translation — and
  never trigger caching or learning either.
- No permissions are requested beyond what an IME requires; no storage,
  no location.
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
