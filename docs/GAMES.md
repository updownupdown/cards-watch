# Game plan

The games chosen for the watch, with the owner's notes from the picker
(2026-09-30). Tick a game off when it ships; keep its notes as a record of
the decisions behind it. Rules follow the phone app (`updownupdown/cards`,
`src/data/games/<id>.ts` and `src/components/Games/<Name>.tsx`) unless noted.

## Rules for every game

- **No settings and no difficulty levels**, in any game, for now.
- **No stock, waste or discard piles on screen.** Show what's left as text
  (e.g. "Ingredients 23") and put drawing or dealing on the bottom button.
- **Empty piles** show as an off-black slot.
- **Stacked piles** show at most two cards: the top card with the edge of
  the one below it, so one card and several cards look different.
- **Cards arrive from off screen on the left and leave off screen to the
  right.** Flipping cards over (front to back) is only worth it if it's cheap
  or a game needs it.
- Cards and short text only: no stand-in shapes for cards.

## Planned

| Game | Status | Notes |
|---|---|---|
| Fifteen Puzzle | Done | 4×4. |
| Memory | | 5×4 grid (10 pairs). No difficulty levels. |
| Lights Out | | Owner asked which size plays best, up to 6×6. Analysis: 4×4 deals solve in ~4 presses on average (trivial); 5×5 needs 5–14, ~10 on average; 6×6 ~12, but cells get too small to tap on a 41 mm watch. **Use 5×5**, and only deal boards needing at least 7 presses. |
| Tower of Hanoi | | Cards overlap in each pile, so show the rank and suit along the **top** of each card: with a small offset the rank must stay visible. |
| Clock Solitaire | | 13 piles on a grid, Kings in the middle. Pure luck. |
| Fortune's Call | | One card and four calls (Red, Black, Higher, Lower). |
| Aces Up | | Four piles as columns filling the full width of the board, cards aligned vertically. Uses the empty-pile slot and two-card stack. Deal on the bottom button. |
| Baroness | | Five piles, top cards only: Kings alone or pairs making 13. Deal on the bottom button. |
| Beehive | | Owner: "let's try to make this work". Leave out the stock and discard piles; there may just be enough room. |
| Triple Stack | | Leave out the stock and discard. Show the stock as "Ingredients N". Serve on the bottom button. |
| Dominion | | 4×3 board. The reserve card can sit on top of the bottom button once it's drawn. |
| Rogue Run | | Room of 4, health and weapon as text. Health arcs maybe later. |
| Seer | | Eight prediction spaces (4×2) plus the revealed card. No difficulty levels, so one set of rules. |
| Lycan | | Owner: "let's try to make this work". |

## Skipped

Everything else, including Black Hole, Chain of Command, Charlatan, Cribbage
Squares, Golf, Herbalist, Monte Carlo, Pagoda, Pocket Poker, Poker Squares,
Stalker and Twelve Trials, which were once on the maybe list.
