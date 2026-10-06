# Solitarius for Wear OS

Playing-card games for round Wear OS watches (Kotlin, Compose for Wear OS,
Material 3). English only. No audio: feedback is haptic, through `ui/Haptics`.

## Layout

Every game uses `GameLayout`: a square board (`BOARD_FRACTION` of the
screen) a little above the middle, the time above it, and a status line or
action buttons in the crescent below. Nothing sits beside the board. Swiping
right goes home (the system's back gesture). A short scroll down, by touch
or the crown, brings up Reset (an icon, tapped twice; there's no undoing a reset) and Undo
under the board, and the game's title and rules follow, then its ratings (`Game.rating`, the phone's: each name
beside its bar), its stats (under the chart icon), its picture's credit
(`Game.credit`, the phone's) and a Top button, each line of text shrinking a little and fading as it passes the
top and foot; the view snaps to the board or to
those buttons (`GameScreen`), so a stray drag springs back. A finished game
shows its result over the board: the message, Home and Play Again, then the
score and time, scrolling if there's more than fits. It stays, the game
under it not scrolling, until one is chosen; a finished
game left by swiping away is dealt anew when it's next opened. The game's
own bottom button doesn't change when the game ends. Asleep (ambient), the board shows as grey
outlines with no buttons (`LocalAmbient`), while it's what's in view; on the
home screen, the rules or a result, the logo shows instead. The tap that
wakes the watch goes nowhere (`MainActivity`).
In a game the screen stays on for 10 s after the last touch (`MainActivity`).

The home screen has the last games played under "Continue Playing" (three,
one or none: a setting), then every game, those pinned (by holding one)
first, then Stats and Settings buttons, and a card fact (`ui/CardFacts.kt`) at the foot.
Each button opens its own screen, swiped away like a game: `StatsScreen`
(stats across every game, and each game played) and `SettingsScreen`
(Haptics, Suit Colours, Continue Playing). The app starts
on the logo, then goes straight into the last game played (or home, if
there's none). Each button is the
game's wide picture (`banner`, cut to cover, never stretched) under 85% of the
game's `accent` colour. The tile (`widget/GamesTileService`) offers the last
game played and the two before it by name; a complication
(`widget/ContinueComplicationService`) goes back to the last one, as the
logo or, where a face wants text, "Solitarius". Both open
`MainActivity` with `EXTRA_GAME`.

- Games draw only playing cards, plus short text. No stand-in shapes for cards.
- Each game's picture and its credit are the phone's (`assets/thumbnails` and
  `thumbnailCredit` in its `src/data/games/<id>.ts`): `thumb_<id>` (96 px
  square) and `banner_<id>` (384×128) in `drawable-nodpi`, cut from it.
- **Every card is 25:35** (`CARD_ASPECT`); cards only scale. All cards in a
  game are the same size. Lay them out with `CardLayout`. A card is its rank
  over its suit, both in the middle, in the suit's colour, drawn from the SVGs in `src/main/card-icons`; `cards/CardStyle.kt`
  sets their sizes and spacing. No game draws its cards any other way.
- Boards are a `CardTable`: cards are keyed, so a card that moves glides to
  its new place, a new card (from a deck) slides in from the left, a removed
  card (to a discard) slides off to the right, or vanishes after a move with
  `Removal.Vanish`, and a card turned over flips. An undo runs backwards
  (`LocalUndoing`): cards leave the way they came and come back from where
  they went. A new deal (`LocalDeal`, from `GameSession.deal`) clears the
  table first: every card slides off to the right, and 200 ms after they've
  gone the new deal slides in from the left. A card with a `from` is dealt from that place, turning over.
  A card can lie at an angle (`rotation`, Clock's hours). Piles keep all their cards
  (`pile`), so cards can move under them. Animation timings are in
  `cards/Timing.kt`.
- Selection is a border centred on the card's edge plus a 30% tint (`Selection`): blue
  (#7bd5ff) while neutral, green (#78ebb1) or red (#e0485c) when the
  selection is valid or not, or the colour of the button its action will have. No other card has a border. Status is a 30%
  `CardOverlay` tint, or a `dot` in the card's top right corner (Rogue Run's deadly monsters). A place where several
  cards spread is a `zone`: an outline, with no fill.
- Every colour is in `ui/Theme.kt` (`AppColors`), but for each game's two
  colours, which are in `res/values/colors.xml`: a dark one over its home
  button's picture, and a pale one for its action button and its rules'
  headings (`LocalGameAccent`). The logo's yellow #ffcf87 is for the app's
  own accents; deep blue #262737 for list buttons, card faces and the text
  on game buttons (which are 40% when disabled). Buttons are fully
  rounded (but Fortune's Call's calls, which are card-wide with rounded corners). A button's colour can say what it does (`GameAction.color`).
- Tap areas reach into the gaps round their cards (`spreadTaps`), so small
  cards are easier to hit.
- No swipe gestures for moves: the board takes taps only, as drags scroll or
  go back. Lists (home, rules) scroll with
  touch and the crown.

## Games

The plan, the owner's notes per game and decisions so far are in
`docs/GAMES.md`: read it before adding or changing a game, and keep it up to
date.

A game is a `Game<S>` (see `game/Game.kt`) with an immutable, serializable
state and pure rule functions, registered in `games/Games.kt`. `GameSession`
keeps the undo history; `SaveStore` saves each game after every move.

- Rules follow the phone app (`updownupdown/cards`) unless `docs/GAMES.md`
  says otherwise. No settings or difficulty levels.
- No stock, waste or discard piles on screen: show the count on the button that
  draws ("Deal (27)"), or else as text worded "Stock: 27". Piles use `pile` (the top card up and to the
  right of the shaded card below, when there is one); overlapping columns
  use `fan`.
- Each suit has its own colour, unless the Settings screen's Suit Colours says
  Two (red and white), or Auto and the game's suits don't matter
  (`Game.fourSuitColours`, read through `LocalFourSuitColours`).
- A game that needs more room than the square board, or has nothing under
  it, can change `boardFraction` and `boardLift` (Clock, Fifteen Puzzle, Nestor,
  Memory, Lights Out, Ten-Twenty-Thirty). A board that reaches up into the
  time's room (Ouroboros) has the time drawn over it, on a fade to black.
- A board with more cards than it has room for (Accordion) scrolls them
  inside the board with `LocalBoardScroll`, so the crown turns the cards
  before the screen. `ScrollingCards` is that grid, fades and all (Ten-Twenty-Thirty,
  Royal Marriage, Augury).
- A progress arc on the screen's left edge is a `SideGauge` (Rogue Run, Augury).
- A game whose wins differ gives a `scoreName` and `score`, for the stats'
  best score; every deal is timed while its screen is up (`GameSession`).
- All text is in Outfit, the theme's font.
- The bottom crescent shows `actions` (one as Wear's edge button,
  `BottomButton`, or two side by side) or `status`;
  override `Bottom` for anything else. State that isn't worth an undo step
  (picked cards) is excluded with `isUndoStep`.

## Saved games

Saves are keyed by `Game.id` and hold `S` serialized by its field names and
the card enums' names. Don't rename those without bumping `saveVersion`
(older saves are then discarded; once the app ships, prefer a migration).

## Speed

The baseline profile (`app/src/main/generated/baselineProfiles`) is measured
on a watch by the `baselineprofile` module: `./gradlew
:app:generateBaselineProfile` with a watch connected. It walks the home list
and the first game there, making a few real moves in it.

Judge speed on the `fast` build variant, never on debug. Cards draw through
`drawWithCache` and share one set of icon paths (`CardIcons`): keep per-frame
work out of drawing.

## Checks

`./gradlew testDebugUnitTest`, which includes `RandomPlayTest`: add a case
there for each new game, as it catches crashes in the rules. `CardTableTest`
steps the clock to check how cards move; `GameScrollTest` checks the snap. To see screens without a watch,
`./gradlew recordRoborazziDebug` renders them (small and large round) to
`app/build/outputs/roborazzi`; add a case to `ScreenshotTest` for new screens.
