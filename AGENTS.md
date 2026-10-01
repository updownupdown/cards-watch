# Solitarius for Wear OS

Playing-card games for round Wear OS watches (Kotlin, Compose for Wear OS,
Material 3). English only. No audio: feedback is haptic, through `ui/Haptics`.

## Layout

Every game uses `GameLayout`: a square board (`BOARD_FRACTION` of the
screen) a little above the middle, the time above it, and a status line or
action buttons in the crescent below. Nothing sits beside the board. Swiping
right goes home (the system's back gesture). A short scroll down, by touch
or the crown, brings up Reset (an icon, held for 2 s; there's no undoing a reset) and Undo
under the board, and the game's title and rules follow, then its stats and a
Top button, each line of text shrinking a little and fading as it passes the
top and foot; the view snaps to the board or to
those buttons (`GameScreen`), so a stray drag springs back. A finished game
shows its result over the board: the message, Home and Play Again, then the
score and time; a tap elsewhere puts it away. The game's own bottom button
doesn't change when the game ends. Asleep (ambient), the board shows as grey
outlines with no buttons (`LocalAmbient`).
In a game the screen stays on for 10 s after the last touch (`MainActivity`).

The home screen lists the games, each button its wide picture (`banner`,
cut to cover, never stretched) under 85% of the
game's `accent` colour. The tile (`widget/GamesTileService`) offers the last
game played and the two before it by name; a complication
(`widget/ContinueComplicationService`) goes back to the last one. Both open
`MainActivity` with `EXTRA_GAME`.

- Games draw only playing cards, plus short text. No stand-in shapes for cards.
- **Every card is 25:35** (`CARD_ASPECT`); cards only scale. All cards in a
  game are the same size. Lay them out with `CardLayout`. A card is a rank
  icon above a suit icon in the suit's colour, drawn from the SVGs in
  `src/main/card-icons`; `cards/CardStyle.kt` sets their sizes and spacing.
- Boards are a `CardTable`: cards are keyed, so a card that moves glides to
  its new place, a new card (from a deck) slides in from the left, a removed
  card (to a discard) slides off to the right, or vanishes after a move with
  `Removal.Vanish`, and a card turned over flips. An undo runs backwards
  (`LocalUndoing`): cards leave the way they came and come back from where
  they went. A card with a `from` is dealt from that place, turning over
  (Beehive's waste, from its Draw button). Piles keep all their cards
  (`pile`), so cards can move under them. Animation timings are in
  `cards/Timing.kt`.
- Selection is a border centred on the card's edge plus a 30% tint (`Selection`): blue
  (#7bd5ff) while neutral, green (#78ebb1) or red (#e0485c) when the
  selection is valid or not. Nothing else uses a border. Status is a 30%
  `CardOverlay` tint.
- Every colour is in `ui/Theme.kt` (`AppColors`): logo yellow #ffcf87 for
  text accents and buttons in a game (deep blue text, 40% when disabled),
  deep blue #262737 for list buttons and card faces. Buttons are fully
  rounded. A button's colour can say what it does (`GameAction.color`).
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
- Each suit has its own colour.
- A game that needs more room than the square board, or has nothing under
  it, can change `boardFraction` and `boardLift` (Clock, Fifteen Puzzle,
  Memory, Lights Out).
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

Judge speed on the `fast` build variant, never on debug. Cards draw through
`drawWithCache` and share one set of icon paths (`CardIcons`): keep per-frame
work out of drawing.

## Checks

`./gradlew testDebugUnitTest`, which includes `RandomPlayTest`: add a case
there for each new game, as it catches crashes in the rules. `CardTableTest`
steps the clock to check how cards move; `GameScrollTest` checks the snap. To see screens without a watch,
`./gradlew recordRoborazziDebug` renders them (small and large round) to
`app/build/outputs/roborazzi`; add a case to `ScreenshotTest` for new screens.
