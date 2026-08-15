Build a Production-Quality Android Keyboard with Gboard-Style UI and English → Twinglish

You are an expert Android engineer, UI/UX engineer, keyboard/IME engineer, and NLP engineer.

Build a complete Android keyboard application from scratch.

The application is an Android system keyboard (IME) whose primary purpose is:

English → Natural Telugu → Romanized Telugu (Twinglish)

The keyboard must feel extremely close to the modern Google Gboard experience in layout, interaction, proportions, animations, toolbar behavior, suggestion strip, key behavior, and overall usability.

The UI/UX quality is the highest priority.

---

1. PRODUCT CONCEPT

Application name:

Twinglish Keyboard

Core idea:

The user installs the keyboard and enables it from Android Settings.

The keyboard can then be used inside:

- WhatsApp
- Instagram
- Telegram
- Messages
- Gmail
- Chrome
- Notes
- Any normal Android text field

The user types normal English.

Example:

Input:

"What are you doing today?"

The keyboard detects the sentence and provides:

"Em chestunnav ivala?"

The user taps the Twinglish suggestion and the keyboard replaces the English text with the Twinglish result.

Another example:

English:

"I will come tomorrow"

Twinglish:

"Nenu repu vastaanu"

Do NOT transliterate English words.

This is translation followed by Romanized Telugu.

---

2. CRITICAL UI REQUIREMENT

The keyboard UI must closely reproduce the interaction model and visual hierarchy users expect from modern Gboard.

IMPORTANT:

Do not create a generic custom keyboard.

Do not create a simple QWERTY keyboard.

Do not create a keyboard that merely resembles a basic Android keyboard.

The target experience is:

modern Gboard-style keyboard

Study current Gboard behavior and reproduce its general UX patterns:

- QWERTY arrangement
- rounded keys
- key spacing
- key proportions
- bottom-row proportions
- suggestion strip
- toolbar
- microphone/action area
- emoji button
- comma
- spacebar
- period
- enter/action key
- backspace
- shift
- numbers/symbols switch
- long-press behavior
- key press animation
- key pop-up preview
- swipe/gesture behavior where practical
- dark/light theme
- adaptive sizing
- haptic feedback
- keyboard height
- landscape layout
- tablet/foldable adaptation

Do NOT copy Google's proprietary source code, trademarks, logos, proprietary assets, or copyrighted resources.

Create an independent implementation that reproduces the familiar interaction paradigm and visual language.

The app name and branding must remain:

Twinglish Keyboard

---

3. TECHNOLOGY STACK

Use:

- Kotlin
- Android Studio
- Gradle Kotlin DSL
- Jetpack Compose where appropriate
- Android InputMethodService
- Material 3 only where useful
- Kotlin Coroutines
- StateFlow
- DataStore for settings
- Room only if persistent local learning data requires it
- Android HapticFeedback / vibrator APIs
- Android accessibility APIs where appropriate

Minimum Android version:

API 26+

Target the latest stable Android SDK available in the development environment.

Architecture:

Clean Architecture + MVVM

Suggested modules:

app
keyboard
twinglish-engine
translation
settings
data
common

Keep the architecture modular so the translation engine can later be replaced without rewriting the keyboard.

---

4. ANDROID IME

Implement a real Android Input Method Service.

Create:

"TwinglishInputMethodService : InputMethodService"

The keyboard must work as a real system keyboard.

It must correctly implement:

- onCreate()
- onStartInput()
- onStartInputView()
- onFinishInput()
- onDestroy()
- currentInputConnection
- commitText()
- deleteSurroundingText()
- setComposingText()
- finishComposingText()
- getExtractedText() where needed
- editorInfo handling
- inputType detection
- imeOptions detection

Handle:

- normal text fields
- multiline fields
- password fields
- email fields
- URL fields
- number fields
- phone fields
- search fields
- chat fields

Never activate the Twinglish translation system for password fields or other inappropriate secure input contexts.

---

5. KEYBOARD SCREEN

The main keyboard should have this hierarchy:

TOP:

Suggestion / Twinglish strip

Below:

Q W E R T Y U I O P

A S D F G H J K L

SHIFT Z X C V B N M BACKSPACE

Bottom row:

emoji / comma / language-or-mode / SPACE / period / enter

The exact dimensions must be adaptive.

Do not hard-code a single screen resolution.

Use density-independent measurements and responsive layout calculations.

The keyboard must look natural on:

- small phones
- normal phones
- large phones
- tablets
- foldables
- landscape mode

---

6. GBOARD-STYLE KEY DESIGN

Each letter key should have:

- rounded rectangular shape
- subtle elevation/contrast
- centered character
- responsive press state
- ripple/pressed state
- haptic feedback
- optional sound feedback
- long-press handling
- popup preview

The key must visually react immediately when touched.

Touch latency must be extremely low.

Do not use heavy recomposition or expensive operations on every key press.

Keyboard input must remain responsive even when the Twinglish engine is processing.

---

7. KEY POPUP PREVIEW

Implement the familiar keyboard key-preview behavior.

When the user presses:

"A"

show a temporary enlarged "A" above the key.

For long press:

"E"

show alternative characters/options if supported.

The popup must:

- appear immediately
- follow the pressed key
- disappear smoothly
- not interfere with text input
- work correctly near screen edges

---

8. SUGGESTION STRIP

This is one of the most important components.

Create a Gboard-style suggestion strip.

Normal state:

"the    this    that"

Twinglish mode:

"Em chestunnav?"

Possible suggestions:

"Ela unnav?"
"Em chestunnav?"
"Ekkadunnav?"

The strip must be horizontally scrollable when necessary.

Suggestion chips must be compact and clean.

The currently preferred Twinglish suggestion should have stronger visual emphasis.

---

9. TWINGLISH MODE

Add a clearly accessible Twinglish mode.

The user should be able to switch between:

English

and

Twinglish

without leaving the keyboard.

Recommended behavior:

Normal keyboard:

"Q W E R T Y ..."

When Twinglish mode is active, the user still types English characters.

The difference is the suggestion/conversion engine.

Example:

User types:

"how are"

Suggestion strip:

"Ela unnavu?"

User continues:

"how are you"

Suggestion:

"Ela unnavu?"

When the user taps the suggestion:

Replace the current English composing phrase with:

"Ela unnavu?"

---

10. AUTO TWINGLISH MODE

Implement an optional setting:

Automatically suggest Twinglish

ON/OFF

When enabled:

The keyboard observes the current composing sentence.

Do not replace the user's text automatically.

Only provide suggestions.

The user must explicitly tap the suggestion to commit the Twinglish output.

This prevents unexpected text replacement.

---

11. TWINGLISH ENGINE

This is the core intelligence.

Pipeline:

English input
↓
English language understanding
↓
Telugu semantic translation
↓
Natural Telugu generation
↓
Romanization
↓
Twinglish normalization
↓
Suggestion

Example:

"What are you doing?"

Internal Telugu:

"నువ్వు ఏం చేస్తున్నావు?"

Final:

"Nuvvu em chestunnav?"

Another:

"I am going home"

Internal Telugu:

"నేను ఇంటికి వెళ్తున్నాను"

Final:

"Nenu intiki veltunnanu"

Another:

"Did you eat?"

Internal Telugu:

"తిన్నావా?"

Final:

"Tinnava?"

---

12. NATURAL TELUGU REQUIREMENT

Do NOT perform literal word-by-word translation.

Bad:

"What are you doing?"
→ "What nuvvu doing?"

Correct:

"Nuvvu em chestunnav?"

The output must sound like something a Telugu speaker would naturally type in a casual chat.

Prioritize conversational Telugu.

---

13. TWINGLISH ROMANIZATION

Implement consistent Romanization.

Examples:

నువ్వు → nuvvu

ఏం → em

చేస్తున్నావు → chestunnavu

చేస్తున్నావ్ → chestunnav

ఎక్కడ → ekkada

ఎందుకు → enduku

నాకు → naaku

నువ్వు → nuvvu

వస్తున్నావా → vastunnava

తిన్నావా → tinnava

The engine should prefer common everyday Twinglish spelling rather than academically strict transliteration.

For casual chat:

"చేస్తున్నావు"

may become:

"chestunnav"

rather than:

"chestunnavu"

This should be configurable later.

---

14. DIALECT / STYLE

Create settings for:

Casual

"Em chestunnav?"

Polite

"Meeru em chestunnaru?"

Formal

"Meeru emi chestunnaru?"

Default:

Casual

Because the primary use case is messaging.

---

15. TRANSLATION ENGINE IMPLEMENTATION

Do not hard-code hundreds of sentences.

Create an abstraction:

"TranslationProvider"

with:

"translateEnglishToTelugu()"

and:

"romanizeTelugu()"

Implement the application so multiple providers can be plugged in.

Possible providers:

1. Local/offline engine
2. Remote API
3. User-configurable provider

Start with a clean provider interface.

Do not put API keys directly into source code.

---

16. OFFLINE-FIRST DESIGN

The keyboard must remain functional even without network access.

Basic keyboard functionality:

- typing
- deletion
- spaces
- punctuation
- numbers
- symbols
- emoji
- suggestions

must work offline.

Twinglish translation can have:

Offline mode

and

Enhanced online mode

When online translation is unavailable:

Do not freeze the keyboard.

Do not block typing.

Do not show loading spinners inside the main key area.

Continue normal keyboard operation.

---

17. PERFORMANCE

Keyboard performance is extremely important.

Target:

- key response < 16 ms where practical
- no visible keyboard lag
- no blocking network call on UI thread
- no blocking model inference on UI thread
- minimal memory usage
- avoid unnecessary allocations
- debounce translation requests
- cache recent translations
- cancel obsolete translation requests

If the user types:

"What are you doing today"

do not send a translation request for every individual character.

Use intelligent debounce.

---

18. TRANSLATION REQUEST STRATEGY

Use something like:

User typing:

"What"

Wait briefly.

"What are"

Wait briefly.

"What are you"

Wait briefly.

"What are you doing"

Generate suggestion.

When the sentence stabilizes:

Generate final Twinglish suggestion.

Cancel previous request whenever newer text arrives.

Never let an old response overwrite a newer sentence.

---

19. TEXT REPLACEMENT

When the user taps a Twinglish suggestion:

Correctly determine the current composing English phrase.

Replace only the intended phrase.

Example:

Text:

"Hey bro what are you doing"

User taps suggestion for:

"what are you doing"

Result:

"Hey bro em chestunnav"

Do not replace the entire message.

Use InputConnection APIs correctly.

Handle cursor positions.

Handle selections.

Handle punctuation.

Handle emojis around the phrase.

---

20. SMART CONTEXT

Use surrounding text when useful.

Example:

"Hey ra, what are you doing?"

Should produce something natural such as:

"Hey ra, em chestunnav?"

Do not blindly translate proper nouns.

Example:

"I am going to Hyderabad tomorrow"

should preserve:

"Hyderabad"

while translating the rest.

---

21. CODE-SWITCHING

Support mixed English + Twinglish.

Example:

"Nenu office ki going"

could be handled naturally.

Do not force every English word into Telugu.

Allow users to write:

"Nenu meeting ki velthunna"

and continue normally.

---

22. PUNCTUATION

Preserve punctuation.

Examples:

"How are you?"
→ "Ela unnav?"

"Where are you!"
→ "Ekkadunnav!"

"Really?"
→ "Nijamga?"

Do not remove:

- ?
- !
- .
- ,
- :
- ;
- quotes
- parentheses

---

23. EMOJI

Preserve emoji.

Example:

"I am coming 😂"

→

"Nenu vastunna 😂"

Do not translate or remove emoji.

---

24. TOOLBAR

Create a Gboard-style top toolbar.

Include useful controls such as:

- Twinglish toggle
- emoji
- clipboard
- settings
- microphone/voice input if implemented
- language switch
- more options

The toolbar must be compact.

Do not overload it.

Allow toolbar customization from settings.

---

25. LANGUAGE SWITCH

Support:

English

Twinglish

The language/mode button should be easily accessible.

Long press can open language/mode selection.

Do not require the user to open the main app every time.

---

26. NUMBER KEYBOARD

Implement a full numbers/symbol keyboard with Gboard-like organization.

Include:

0–9

punctuation

currency symbols

mathematical symbols

common special characters

Provide a "123" / "ABC" toggle.

---

27. EMOJI KEYBOARD

Implement an emoji entry point.

If a complete emoji browser is too large for the first milestone, create the architecture and a functional initial emoji picker.

It must feel integrated with the keyboard rather than opening an unrelated Activity.

---

28. CLIPBOARD

Implement a clipboard toolbar/panel.

Allow:

- recent copied text
- pin
- delete
- paste

Respect Android privacy restrictions.

Do not collect clipboard contents unnecessarily.

---

29. PRIVACY

This is extremely important because this is a keyboard.

The application must clearly communicate:

- typed text should not be stored unnecessarily
- passwords must not be processed
- sensitive fields must be handled safely
- translation requests must be explicit/configurable
- no advertising SDK
- no analytics SDK unless explicitly added later
- no hidden telemetry
- no unnecessary permissions

Do not transmit all keystrokes to a server.

Only send text to an external translation provider when necessary and permitted by the user's settings.

---

30. SETTINGS APP

Create a polished settings Activity.

Sections:

Keyboard

- Keyboard theme
- Keyboard height
- Key borders
- Key press sound
- Haptic feedback
- Popup preview
- Auto-capitalization

Twinglish

- Enable Twinglish
- Auto suggest
- Translation style
- Casual / Polite / Formal
- Romanization style
- Online translation
- Offline translation

Privacy

- Translation privacy
- Clear translation history
- Clear cached suggestions
- Network usage

About

- App version
- Open-source licenses
- Privacy policy
- GitHub

---

31. THEMES

Provide:

Light

Clean Gboard-like light keyboard.

Dark

Clean dark keyboard.

System

Follow Android system theme.

Use dynamic colors where appropriate but keep keyboard readability and contrast consistent.

Do not use excessive gradients.

---

32. ANIMATIONS

Animations should be subtle.

Implement:

- key press scale/opacity feedback
- popup preview
- suggestion updates
- toolbar transitions
- keyboard mode transitions
- emoji panel transition

Avoid excessive animations.

Keyboard must feel instant.

---

33. HAPTICS

Implement optional haptic feedback.

Settings:

OFF

LIGHT

MEDIUM

Use Android-supported haptic APIs.

Do not create excessive vibration.

---

34. SOUND

Optional keypress sound.

Default:

OFF or system-controlled.

Respect Android sound settings.

---

35. ACCESSIBILITY

Support:

- TalkBack
- content descriptions
- minimum touch targets
- high contrast
- scalable UI where possible
- accessible toolbar controls

Keyboard must remain usable without relying exclusively on color.

---

36. LANDSCAPE

Create a dedicated landscape layout.

Do not simply stretch the portrait keyboard.

Maintain:

- sensible key widths
- correct bottom row
- suggestion strip
- toolbar
- comfortable touch targets

---

37. TABLET / LARGE SCREEN

Support large screens.

Keyboard should not become absurdly wide.

Use a maximum comfortable keyboard content width where appropriate.

Center the keyboard content on large displays.

---

38. SAMSUNG / PIXEL / ONE UI COMPATIBILITY

Test on Android devices with different system UI behavior.

Pay special attention to:

- Samsung One UI
- Pixel Android
- gesture navigation
- 3-button navigation
- edge-to-edge behavior
- IME inset handling
- rotation
- split-screen
- floating keyboard behavior if supported

---

39. APP STRUCTURE

Create a clean project such as:

app/
keyboard/
twinglish-engine/
translation/
data/
settings/
common/

Suggested package structure:

com.twinglish.keyboard

Use clear naming.

Do not create one giant Kotlin file.

---

40. TEST APPLICATION

Create an internal keyboard test screen.

It should contain:

- TextField
- multiline text field
- password field
- email field
- URL field
- search field
- number field

This allows testing the IME behavior without opening external apps.

Also create automated tests for the Twinglish engine.

---

41. TEST CASES

Minimum translation tests:

Input:

"What are you doing?"

Expected:

"Em chestunnav?"

Input:

"How are you?"

Expected:

"Ela unnav?"

Input:

"Where are you going?"

Expected:

"Ekkadiki velthunnav?"

Input:

"Did you eat?"

Expected:

"Tinnava?"

Input:

"I am coming tomorrow"

Expected:

"Nenu repu vastanu"

Input:

"I am going home"

Expected:

"Nenu intiki velthunnanu"

Input:

"Why are you late?"

Expected:

"Enduku late ayyav?"

Input:

"Call me later"

Expected:

"Tarvata naaku call cheyyi"

These are examples, not a hard-coded translation table.

---

42. IMPORTANT UI VALIDATION

Before considering the project complete:

Take screenshots of the keyboard.

Compare the result against modern Gboard screenshots/reference behavior.

Check:

- key spacing
- key width
- row height
- bottom row
- suggestion strip
- toolbar
- icon alignment
- corner radius
- typography
- shadows
- touch feedback
- keyboard height
- overall visual density

Iterate until it looks like a polished production Android keyboard.

The keyboard UI is NOT acceptable if it looks like:

- a basic Compose demo
- a beginner keyboard project
- a calculator keypad
- a generic Material UI keyboard

It must look like a professional mobile keyboard.

---

43. DO NOT MAKE THESE MISTAKES

Do NOT:

- use an Activity as the keyboard
- fake keyboard functionality
- require users to copy/paste into a separate translator
- translate every keystroke synchronously
- block the UI while translating
- send passwords to translation APIs
- hard-code an API key
- create a generic keyboard layout
- ignore landscape mode
- ignore Samsung devices
- create giant buttons
- use excessive colors
- use random fonts
- make the keyboard visually noisy
- automatically replace user text without confirmation
- store every typed character
- collect private user text
- create fake Gboard branding

---

44. DEVELOPMENT PHASES

Build in phases.

Phase 1 — IME foundation

Implement:

- Android project
- InputMethodService
- keyboard registration
- system keyboard selection
- basic QWERTY input
- delete
- space
- enter
- shift

Make it installable.

Phase 2 — Gboard-style UI

Implement:

- key geometry
- suggestion strip
- toolbar
- bottom row
- popup preview
- animations
- haptics
- dark/light theme

Do NOT move to the next phase until the UI is polished.

Phase 3 — Twinglish engine

Implement:

- TranslationProvider
- RomanizationProvider
- suggestion pipeline
- debounce
- caching
- contextual translation

Phase 4 — Advanced keyboard

Implement:

- emoji
- clipboard
- numbers
- symbols
- language switching
- settings
- landscape
- tablet

Phase 5 — Privacy/security

Audit:

- permissions
- clipboard
- network
- password fields
- logging
- API keys
- storage

Phase 6 — Testing

Test on:

- Android emulator
- physical Android phone
- Samsung One UI
- Pixel-like Android
- portrait
- landscape
- WhatsApp
- Chrome
- Messages
- Gmail

---

45. BUILD REQUIREMENTS

The project must compile successfully.

Run:

./gradlew assembleDebug

Fix every compilation error.

Then install the APK on an emulator/device.

Verify that Android Settings shows:

Twinglish Keyboard

under available keyboards.

Enable it.

Open a text application.

Select Twinglish Keyboard.

Type:

"What are you doing?"

Verify that:

"Em chestunnav?"

appears in the suggestion area.

Tap it.

Verify that the text field becomes:

"Em chestunnav?"

---

46. FINAL DELIVERABLE

Deliver:

1. Complete Android Studio project
2. Working APK
3. Source code
4. README
5. Architecture documentation
6. Setup instructions
7. Translation provider interface
8. Twinglish engine
9. Settings screen
10. Test suite

The README must explain:

- how to build
- how to install
- how to enable the keyboard
- how to switch keyboards
- how Twinglish works
- how to configure translation providers
- privacy behavior
- how to add another language later

---

47. IMPORTANT IMPLEMENTATION RULE

Do not stop after generating the project skeleton.

Actually implement the application.

If a dependency/API is unavailable, choose a stable alternative.

If a feature cannot be completed perfectly in the first iteration, implement the best functional version and clearly isolate it behind an interface so it can be improved later.

Do not replace difficult functionality with placeholder buttons.

Every visible button should either work or be clearly marked as a deliberately deferred feature.

---

48. START NOW

First inspect the development environment.

Check:

- Java version
- Android SDK
- Gradle
- Android Studio availability
- connected devices/emulators

Then create the project.

Implement Phase 1.

Build it.

Fix errors.

Implement Phase 2.

Build again.

Then implement the Twinglish engine.

Continue until the complete application is functional.

Do not merely explain what should be done.

Write the actual project files and implementation.

The final result must be a working Android system keyboard with a highly polished Gboard-style user experience and English → natural Telugu Romanized Twinglish conversion.
