# Solitarius for Wear OS

Playing-card games for round Wear OS watches (Kotlin, Compose for Wear OS,
Material 3). English only. No audio: feedback is haptic, through `ui/Haptics`.

## Layout

Every game uses `GameLayout`: a square board in the middle
(`BOARD_FRACTION` of the screen), home above, undo left (hold for a new game),
rules right, and a status line or one action button in the crescent below.

- Games draw only playing cards (`PlayingCard`) on a `CardGrid`, plus short
  text. No stand-in shapes for cards.
- A card's border means **selected**, and nothing else. Status is shown with
  an `overlay` colour from `CardOverlay`.
- No swipe gestures on the board: taps only. Lists (home, rules) scroll with
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
- No stock, waste or discard piles on screen: show counts as text and put
  drawing on the bottom button. Piles use `CardPile` (top card plus the edge
  of the one below); overlapping columns use `CardFan`.
- Cards arriving or leaving go through `SlideSlot`: in from the left, out to
  the right.
- The bottom crescent shows `actions` (one or two buttons) or `status`;
  override `Bottom` for anything else. State that isn't worth an undo step
  (picked cards) is excluded with `isUndoStep`.

## Saved games

Saves are keyed by `Game.id` and hold `S` serialized by its field names and
the card enums' names. Don't rename those without bumping `saveVersion`
(older saves are then discarded; once the app ships, prefer a migration).

## Checks

`./gradlew testDebugUnitTest`, which includes `RandomPlayTest`: add a case
there for each new game, as it catches crashes in the rules. To see screens without a watch,
`./gradlew recordRoborazziDebug` renders them (small and large round) to
`app/build/outputs/roborazzi`; add a case to `ScreenshotTest` for new screens.
