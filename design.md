TASK: REBUILD THE KEYBOARD UI TO MATCH THE PROVIDED GBOARD REFERENCE

You are modifying an existing Android keyboard project.

The current keyboard UI is NOT acceptable.

The current design looks like a generic custom keyboard. Replace the keyboard UI implementation with a highly faithful recreation of the visual structure, proportions, spacing, interaction model, animations, and behavior of the Gboard keyboard shown in the provided reference image.

The reference image is the PRIMARY UI specification.

Do not redesign it.

Do not "improve" it with your own visual style.

Do not create a generic Material keyboard.

Do not create a modern custom keyboard inspired by Gboard.

The goal is:

«When a user looks at the Twinglish Keyboard next to the reference Gboard screenshot, the overall structure, geometry, spacing, key placement, toolbar, suggestion strip, colors, animations and interaction should feel like the same keyboard family.»

The application remains an independent keyboard called:

Twinglish Keyboard

Do NOT copy Google's proprietary source code, assets, logos, or branding.

Recreate the UI and interaction behavior independently.

---

1. REFERENCE IMAGE IS THE SOURCE OF TRUTH

Use the supplied Gboard screenshot as the visual reference.

Analyze it carefully before modifying the implementation.

Pay attention to:

- overall keyboard height
- keyboard width
- top toolbar height
- suggestion strip height
- row heights
- horizontal spacing
- vertical spacing
- key positioning
- key sizes
- font size
- font weight
- icon size
- icon positioning
- bottom-row proportions
- spacebar width
- enter-key width
- number row
- QWERTY row
- home row
- bottom letter row
- shift position
- backspace position
- background color
- separator colors
- pressed-state colors
- suggestion colors
- toolbar colors
- animation timing
- touch feedback

Do not approximate these casually.

Measure the reference visually and reproduce the relationships between elements.

---

2. IMPORTANT: DO NOT USE THE PREVIOUS KEYBOARD DESIGN

Delete/rework the current keyboard visual implementation.

The previous implementation has problems such as:

- oversized key containers
- excessive rounded rectangular key backgrounds
- incorrect key spacing
- incorrect row proportions
- incorrect bottom row
- incorrect toolbar
- incorrect suggestion strip
- generic Material styling
- incorrect typography
- insufficient visual density
- wrong proportions
- lack of Gboard-style motion
- incorrect icon placement

Do not simply adjust colors.

Rebuild the keyboard layout and component system.

---

3. KEYBOARD SHOULD LOOK LIKE A GBOARD KEYBOARD

The reference keyboard uses a relatively clean blue surface.

The letters are primarily displayed directly on the keyboard surface rather than every key being presented as a large independent white card.

Therefore:

DO NOT create this:

[ Q ] [ W ] [ E ] [ R ] [ T ]

with huge white cards.

Instead, reproduce the reference's flatter keyboard presentation:

Q     W     E     R     T     Y     U     I     O     P

with carefully positioned touch targets.

Touch targets may exist internally, but their visual backgrounds should remain transparent unless the reference shows a visible state.

---

4. GLOBAL LAYOUT

Build the keyboard as a vertically stacked IME view.

Structure:

Keyboard Root
│
├── Toolbar
│
├── Suggestion / conversion strip
│
├── Number row
│
├── QWERTY row
│
├── ASDF row
│
├── ZXCV row
│
├── Bottom action row
│
└── System navigation / IME inset area

Do not create an Activity layout.

This must remain a real Android InputMethodService UI.

---

5. BLUE THEME

The default theme must reproduce the visual character of the blue Gboard reference.

Use a blue keyboard surface.

The keyboard should NOT have:

- gradients
- glassmorphism
- excessive shadows
- neon effects
- cards
- random accent colors
- Material 3 elevated surfaces everywhere

The reference has a clean, relatively flat blue appearance.

Use a small controlled palette:

- main keyboard blue
- slightly darker toolbar blue
- slightly different suggestion area blue
- lighter pressed state
- lighter/white text
- lighter action-button color

Keep contrast high.

Define colors centrally:

KeyboardColors

Do not scatter color literals throughout the UI.

---

6. TOP TOOLBAR

The toolbar is critical.

Reproduce the structure shown in the reference.

The toolbar contains horizontally arranged actions.

Example structure:

[ Apps/Grid ] [ Emoji/Stickers ] [ GIF ] [ Settings ] [ Translate ] [ Theme ] [ Microphone ]

The exact available features may differ for Twinglish, but the visual arrangement must remain similar.

The toolbar should:

- have a fixed compact height
- use evenly spaced icon buttons
- center icons vertically
- use consistent icon sizing
- have generous touch targets
- use subtle pressed states
- avoid visible button containers during the normal state

The microphone/action control on the right should have a circular highlighted touch area similar to the reference.

---

7. TWINGLISH INTEGRATION

The Twinglish feature must be integrated into this existing Gboard-like toolbar/suggestion architecture.

Do NOT create a separate giant "TWINGLISH" button.

Instead:

Add Twinglish naturally into the suggestion/conversion system.

For example:

User types:

"HOW ARE YOU"

Suggestion area:

"ela unnav"
"meeru ela unnaru"
"ela unnaru"

The first suggestion should be the primary Twinglish conversion.

The Twinglish suggestion must look like a natural keyboard suggestion.

---

8. SUGGESTION STRIP

The suggestion strip must visually resemble Gboard's suggestion row.

It must NOT look like a Material Card layout.

Avoid:

- large cards
- giant pills
- excessive borders
- shadows

Use a clean horizontal suggestion layout.

Example:

┌─────────────────────────────────────────────────────┐
│  ela unnav     meeru ela unnaru      ela unnaru     │
└─────────────────────────────────────────────────────┘

The selected/primary suggestion may use a subtle rounded highlight.

The highlight should be restrained.

It should NOT look like a large floating button.

---

9. TWINGLISH SUGGESTION PRIORITY

The first suggestion should normally be the most natural conversational Telugu.

Example:

English:

"How are you?"

Suggestions:

"ela unnav?"
"meeru ela unnaru?"
"ela unnaru?"

Primary:

"ela unnav?"

Another:

English:

"What are you doing?"

Suggestions:

"em chestunnav?"
"meeru em chestunnaru?"
"em chestunnavu?"

Primary:

"em chestunnav?"

Use lowercase when appropriate for conversational suggestions, matching the reference style.

---

10. NUMBER ROW

The number row must match the reference structure.

Display:

1  2  3  4  5  6  7  8  9  0

Do NOT put each number inside a visible rounded key card.

Use transparent touch targets with centered text.

Spacing should be balanced across the entire keyboard width.

---

11. QWERTY ROW

Implement:

q   w   e   r   t   y   u   i   o   p

The letters should be:

- lowercase normally
- clean sans-serif
- centered
- evenly distributed
- visually lightweight
- large enough to read
- not bold

The reference does not use giant heavy key labels.

---

12. HOME ROW

Implement:

a   s   d   f   g   h   j   k   l

Use the same typography and spacing as the QWERTY row.

Center the row appropriately.

Do not make the left/right margins visually unbalanced.

---

13. THIRD LETTER ROW

Implement:

SHIFT   z   x   c   v   b   n   m   BACKSPACE

The shift and backspace controls should occupy larger touch targets.

Their visual icons should remain compact.

Do NOT use large filled rectangular buttons.

---

14. SHIFT BUTTON

Use a clean outlined/up-arrow shift icon similar to the reference.

States:

Lowercase

outlined shift icon

Shift active

filled/highlighted shift state

Caps lock

clearly differentiated caps-lock state

Animate state changes subtly.

Do not rotate the icon unnecessarily.

---

15. BACKSPACE

Backspace should use a clean keyboard-style delete icon.

It must support:

- tap = delete one character
- hold = repeated deletion
- swipe/gesture behavior if implemented
- fast repeat

Implement press/repeat behavior similar to a professional keyboard.

Do not use a huge "X" inside a giant button.

---

16. BOTTOM ROW

The bottom row is extremely important.

Reproduce the reference proportions.

Structure:

[ ?123 ] [ , ] [        SPACE        ] [ . ] [ ENTER ]

The spacebar should occupy the majority of the row.

The number/symbol key should be a compact rounded control.

Comma and period should be narrow.

Enter should be a compact highlighted action button.

Do not make every element equal width.

---

17. SPACEBAR

The spacebar is one of the most visually important components.

It must:

- be very wide
- have a soft rounded shape
- be horizontally centered
- have a subtle lighter blue surface
- respond immediately to touch
- provide haptic feedback
- support long press where appropriate

Do not put the word "SPACE" inside it unless the current Android language/input configuration requires it.

---

18. ENTER KEY

The enter/action button should have a more prominent blue shade.

Its icon must correspond to the editor action:

- Enter
- Done
- Search
- Next
- Send

Use "EditorInfo.imeOptions".

For chat applications, support Send where appropriate.

The shape should be rounded.

---

19. KEYBOARD ICON / LANGUAGE BUTTON

At the bottom-left or bottom area, include the keyboard/language switching affordance according to Android IME conventions.

Long press can expose:

- available keyboards
- language modes
- Twinglish mode

Do not remove the Android keyboard-switch functionality.

---

20. SYSTEM IME INSETS

Handle:

- navigation bar
- gesture navigation
- edge-to-edge
- Android 15+
- Samsung One UI
- Pixel
- different aspect ratios

The keyboard should sit naturally above the navigation area.

Do not allow the keyboard to overlap system navigation controls.

---

21. KEY TOUCH TARGETS

Very important:

The visible key is NOT necessarily the touch target.

Create larger invisible touch targets behind the labels.

For example:

Visual:

"q"

Touch area:

larger rectangular region

This produces the comfortable typing experience of a professional keyboard while maintaining the clean visual appearance.

---

22. TOUCH FEEDBACK

Implement extremely fast touch feedback.

When a key is pressed:

1. Detect touch immediately.
2. Trigger haptic feedback.
3. Show subtle pressed-state feedback.
4. Display key preview.
5. Commit character.
6. Return to normal state.

Do not wait for translation/network operations.

Typing must always be independent of the Twinglish engine.

---

23. KEY PRESS ANIMATION

The animation must be subtle and fast.

Do NOT use:

- large scaling
- bouncing
- springy Material animations
- slow fades
- exaggerated effects

Use a professional keyboard-style response.

Target:

approximately 50–100 ms visual response.

The animation should feel almost instantaneous.

---

24. KEY POPUP PREVIEW

Implement the classic keyboard key preview.

When pressing:

"q"

a temporary enlarged "q" appears above the touch point.

The popup:

- appears immediately
- follows the key
- has rounded geometry
- has appropriate contrast
- disappears quickly
- does not cause layout shifts

When the user long-presses a key, show alternative characters if applicable.

---

25. LONG PRESS

Implement:

- backspace repeat
- character alternatives
- punctuation alternatives
- number alternatives
- special characters

Use a professional long-press threshold.

Do not trigger long press accidentally.

---

26. SWIPE / GESTURE BEHAVIOR

Where practical, implement:

- backspace swipe
- cursor movement
- spacebar cursor control
- glide typing architecture

If full glide typing is too complex for the current milestone, architect the keyboard so it can be added later.

Do not fake glide typing.

---

27. CURSOR CONTROL

Implement spacebar cursor movement if feasible.

The user should be able to move the cursor naturally without leaving the keyboard.

This is an important part of making the keyboard feel like a real production keyboard.

---

28. TOOLBAR ANIMATION

Toolbar interactions must be smooth.

When switching between:

Keyboard

Emoji

Clipboard

GIF

Settings

Twinglish

do not abruptly replace the entire screen.

Use smooth transitions.

Keep the keyboard surface visually stable.

---

29. SUGGESTION ANIMATION

When suggestions change:

Do NOT flash the entire suggestion row.

Use subtle replacement/transition animation.

For example:

"how are"

→

"how are you"

The suggestions should update smoothly.

No flickering.

No loading spinner.

---

30. TWINGLISH TRANSLATION PERFORMANCE

The translation engine must run independently from the keyboard UI.

Architecture:

Keyboard UI
│
▼
Input Controller
│
▼
Debounced Text Stream
│
▼
Twinglish Engine
│
▼
Suggestion State
│
▼
Suggestion Strip

Never block:

- key rendering
- touch processing
- haptics
- InputConnection

while generating Twinglish.

---

31. TEXT INPUT BEHAVIOR

When the user types:

"how are you"

do not immediately replace it.

Show:

"ela unnav?"

as a suggestion.

When tapped:

replace only the intended composing sentence.

Example:

Before:

"Hey bro how are you"

After:

"Hey bro ela unnav?"

Do not replace:

"Hey bro"

---

32. CAPS / INPUT BEHAVIOR

Support:

- lowercase
- uppercase
- automatic capitalization
- sentence capitalization
- caps lock

When the editor indicates sentence capitalization, automatically begin with uppercase.

---

33. DIFFERENT EDITOR TYPES

The keyboard must detect:

- chat
- email
- URL
- password
- number
- phone
- search
- multiline

Twinglish translation should be disabled for:

- password
- PIN
- secure fields

For URL/email fields, use an appropriate keyboard layout.

---

34. RESPONSIVE DIMENSIONS

Do NOT hard-code pixel coordinates from one screenshot.

Instead derive dimensions from:

- available width
- available height
- density
- orientation
- device size

Create a layout specification with relative dimensions.

Example concept:

Toolbar = approximately 10–12% of keyboard height

Suggestion strip = approximately 9–12%

Number row = approximately 12%

Letter rows = approximately 14–16% each

Bottom row = approximately 15–18%

Adjust after visual comparison.

These percentages are starting points, not immutable values.

---

35. TYPOGRAPHY

Use a clean Android system sans-serif font.

Letters should have:

- regular weight
- appropriate optical size
- no unnecessary bold
- consistent baseline
- consistent vertical centering

Numbers should be slightly smaller than letter glyphs where appropriate.

Suggestion text should be smaller than key labels.

Toolbar icons should not overpower text.

---

36. ICONS

Use clean vector icons.

Do not use random icon libraries with inconsistent visual styles.

All icons should have:

- consistent stroke weight
- consistent optical size
- consistent alignment

Icons needed:

- keyboard switch
- shift
- backspace
- enter
- emoji
- clipboard
- GIF
- settings
- translate
- theme
- microphone
- language
- more

Create custom vector drawables if necessary.

Do not use Google's proprietary icon assets.

---

37. GBOARD-LIKE BEHAVIOR WITHOUT GBOARD CODE

The goal is:

same UX pattern

NOT:

copy Google's implementation

Do not import Gboard APK code.

Do not decompile Gboard.

Do not copy proprietary resources.

Do not use Google branding.

Build the components independently.

---

38. REFERENCE-BASED ITERATION

After implementing the UI:

1. Build the APK.
2. Install it on an Android device/emulator.
3. Open a text field.
4. Display the Twinglish keyboard.
5. Take a screenshot.
6. Compare against the supplied Gboard reference.
7. Identify visual differences.
8. Adjust layout.
9. Repeat.

Do at least 3 visual refinement passes.

Do not stop after the first implementation.

---

39. VISUAL ACCEPTANCE CRITERIA

The keyboard fails visual review if:

- keys look like large Material cards
- spacing is obviously different
- keyboard is too tall
- toolbar is too large
- suggestion strip looks like a web UI
- bottom row proportions are wrong
- spacebar is too narrow
- enter button is too large
- typography is too bold
- icons are inconsistent
- animation is slow
- keyboard feels like a demo application

The keyboard passes when:

- the overall silhouette matches the reference
- rows have similar proportions
- spacing feels equivalent
- toolbar feels equivalent
- suggestion strip feels equivalent
- bottom row feels equivalent
- key interaction feels equivalent
- animations are fast and subtle
- typing feels responsive
- the interface looks professionally manufactured

---

40. DO NOT ADD YOUR OWN DESIGN LANGUAGE

This is critical.

Do not add:

- cards
- gradients
- glass effects
- neumorphism
- excessive shadows
- giant rounded buttons
- colorful Material components
- decorative backgrounds
- custom branding inside every component

The keyboard should remain visually restrained.

The Twinglish intelligence is the differentiator.

The keyboard UI should remain familiar.

---

41. TWINGLISH BRANDING

The keyboard itself should NOT constantly display:

"Twinglish Keyboard"

The user already knows which keyboard they selected.

Branding belongs in:

- settings
- onboarding
- about page

The actual keyboard should prioritize typing.

---

42. FINAL MAIN SCREEN STRUCTURE

The final keyboard should approximately follow this hierarchy:

┌───────────────────────────────────────────────┐
│  ▦   😊   GIF   ⚙   Translate   🎨   🎤       │
├───────────────────────────────────────────────┤
│  ela unnav    meeru ela unnaru    ela unnaru │
├───────────────────────────────────────────────┤
│   1   2   3   4   5   6   7   8   9   0     │
│                                               │
│    q   w   e   r   t   y   u   i   o   p     │
│                                               │
│     a   s   d   f   g   h   j   k   l        │
│                                               │
│   ⇧   z   x   c   v   b   n   m        ⌫    │
│                                               │
│ [?123]   ,       [       SPACE       ]  .  ↵ │
├───────────────────────────────────────────────┤
│        keyboard switch                 ˅      │
└───────────────────────────────────────────────┘

This is a structural reference, not literal ASCII UI.

---

43. IMPORTANT: CURRENT PROJECT

Before modifying anything:

Inspect the existing repository.

Identify:

- current UI implementation
- keyboard service
- layouts
- Compose views
- XML layouts
- theme
- colors
- dimensions
- input handling
- suggestion system
- Twinglish engine

Do not blindly create duplicate components.

Refactor the existing implementation where practical.

Remove obsolete UI code after the new implementation is verified.

---

44. BUILD AND VERIFY

After implementation:

Run:

./gradlew clean assembleDebug

Fix all errors.

Then install:

adb install -r <apk>

Enable the keyboard in Android Settings.

Open a text editor.

Test:

"How are you?"

Expected suggestion:

"ela unnav?"

Tap it.

Expected text:

"ela unnav?"

Then test:

"What are you doing?"

Expected:

"em chestunnav?"

Then test normal English typing without Twinglish.

Then test emoji.

Then numbers.

Then symbols.

Then backspace.

Then long press.

Then enter.

Then keyboard switching.

Then landscape.

Then dark/light mode.

---

45. FINAL INSTRUCTION

Do not tell me how to implement this.

Implement it.

Do not stop at a mockup.

Do not stop at a static keyboard screenshot.

Do not create a fake keyboard Activity.

Build a real Android InputMethodService.

The final product must behave like a real Android keyboard and visually follow the supplied Gboard reference as closely as an independently implemented keyboard reasonably can.

The priority order is:

1. Gboard-like UI structure
2. Correct proportions and spacing
3. Smooth keyboard interaction
4. Fast typing
5. Suggestion strip
6. Twinglish conversion
7. Animations
8. Additional features

If there is a conflict between adding a feature and preserving the reference keyboard UI, prioritize the reference UI.

Do not sacrifice keyboard quality for additional features.
