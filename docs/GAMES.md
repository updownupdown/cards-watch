# Game plan

The games chosen for the watch, with the owner's notes from the picker
(2026-09-30). A game moves from Built to Shipped once it's been tested on a
watch and released; keep its notes as a record of the decisions behind it. Rules follow the phone app (`updownupdown/cards`,
`src/data/games/<id>.ts` and `src/components/Games/<Name>.tsx`) unless noted.

## Rules for every game

- **No settings and no difficulty levels**, in any game, for now.
- **No stock, waste or discard piles on screen.** Show what's left as text
  as "Stock: 23" (or the game's own word for it: "Ingredients: 23") and put drawing or dealing on the bottom button.
- **Empty piles** show as an off-black slot.
- **Stacked piles** show at most two cards: the top card, up and to the right
  of the shaded card below it, so one card and several cards look different.
  A card on its own isn't offset.
- **Cards from a (hidden) deck arrive from off screen on the left; cards to a
  (hidden) discard leave off screen to the right.** An undo runs the other
  way: cards go back where they came from. Cards moving on the board
  glide to their new place. Cards turned over flip. Card animations take 0.6 s
  (`cards/Timing.kt`).
- Cards and short text only: no stand-in shapes for cards.

## Planned

| Game | Status | Notes |
|---|---|---|
| Fifteen Puzzle | Built | 4×4. No move count on screen: the board takes that room (76% of the screen), for larger cards. |
| Memory | Built | 5×4 grid (10 pairs). No difficulty levels. No move count on screen: the board is a little larger instead (70%), as is Lights Out's. |
| Lights Out | Built | Owner asked which size plays best. **5 across × 4 down**, for the largest cards that fit, and no move count. Checked by brute force: every 5×4 board has exactly one solution, 10 taps on average (the same as 5×5, which needs 5–14); 4×4 and 5×3 average under 5 taps (trivial). Only deal boards needing at least 7 taps. |
| Tower of Hanoi | Built | A suit for each few ranks, always the same, to tell cards apart: A–3 spades, 4–5 clubs, 6–7 diamonds, 8–9 hearts. Cards overlap in each pile, so show the rank and suit along the **top** of each card: with a small offset the rank must stay visible. Narrow cards (22% of the board) spread half as far again as other fans, so tall piles read. |
| Clock Solitaire | Built | The one exception to the grid: the hours in a circle, Kings in the middle between "Kings" and "2 of 4". Its board is larger than the others' (80% of the screen, reaching its foot), so the hours stand apart; New game sits over the six o'clock pile once the game ends. Pure luck. |
| Fortune's Call | Built | One card and four calls (Red, Black, Higher, Lower). |
| Aces Up | Built | Four piles two by two, for larger cards. Uses the empty-pile slot and two-card stack. Deal on the bottom button. |
| Baroness | Built | Three piles in a row over two centred under them, top cards only: Kings alone or pairs making 13. Deal on the bottom button. |
| Beehive | Built | Owner: "let's try to make this work". Leave out the stock and discard piles; there may just be enough room. Six piles two across and three down on the left; well apart on the right, the beehive at the top and, from the middle row down to the bottom one, the waste's last three cards spread downwards with the top one lowest, all but that one shaded. Its cards come from off screen on the right, rather than the left. |
| Triple Stack | Built | No stock or discard. "Ingredients N" above the hand, which is centred. **Serve (n)** shows the refill a selection earns (0 when it isn't a sandwich). |
| Dominion | Built | 4×3 board. Places the picked card can conquer are tinted blue. Conquests glide one card onto the other; the conquered card stays hidden under it. The grid stops 10% short of the board's foot, clear of the **Draw (n)** button; the drawn reserve card sits over that button, raised 15% of its height, until placed. Checked by exhaustive search: 4×3 wins ~66% of deals with perfect play, the phone's 5×3 ~63%. |
| Rogue Run | Built | Top: weapon, the pile of monsters it has killed, then "Room: 7" over "Stock: 28". Middle: a health bar, then "HP: 5 / 20"; the three texts share a left edge. Bottom: the room's four cards. Buttons are coloured: green Heal, blue Equip, red Fist. With a monster picked, Fist is the bottom button and a purple "Wpn −1" button takes the place of the room and stock text when the weapon can fight it. Tapping the picked card again does its action when there is only one. |
| Seer | Built | Eight prediction spaces (4×2) plus the revealed card. No difficulty levels: plays the phone's Normal rules (a success frees stones directly beside it). |
| Lycan | Built | Owner: "let's try to make this work". Plays the phone's Medium deck (Aces to 9s). The next card sits in the middle, with each side's name over its score either side of it; when a round ends the card fades and "Wolf +14" takes its place. |

## Skipped

Everything else, including Black Hole, Chain of Command, Charlatan, Cribbage
Squares, Golf, Herbalist, Monte Carlo, Pagoda, Pocket Poker, Poker Squares,
Stalker and Twelve Trials, which were once on the maybe list.

## Decisions made while building

- **Stats** count each deal once, the first time it's won or lost; a deal
  given up with Reset doesn't count. They keep the longest winning streak,
  the fastest win, and a best score where wins differ: fewest moves (Fifteen
  Puzzle, Memory, Lights Out, Tower of Hanoi) or highest score (Rogue Run,
  Lycan).
- **Undo** is an undo step per move; picking cards (Triple Stack, Rogue Run,
  Lycan) isn't. Reset (held for 2 s) deals a new game, which can't be undone.
- **Two-choice moves** (Seer's reveal or
  return, Lycan's fold or capture) use two smaller buttons in the crescent.
- **Fortune's Call** shows the last card on the left, green or red for the
  call, instead of pausing on the reveal.
- **Memory** flips cards. An open pair stays up for 3 s or until the next tap, green
  (then off to the right) for a match, red (then back over) for a miss.
- **Lycan** keeps one card size: each hand is one row that overlaps as it
  grows.
- **Seer** shows the last card turned up in the middle of the crescent, with
  Reveal and Return either side when stones are freed.
