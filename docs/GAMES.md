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
- **A new deal clears the table first** (owner, from Nestor, for every game):
  all the cards slide off to the right, and 200 ms after they've gone the new
  deal comes in from the left. Every pile's place (and every fixed place for
  a card) shows its grey slot under its cards, so nothing turns black as they go.
- Cards and short text only: no stand-in shapes for cards.

## Planned

| Game | Status | Notes |
|---|---|---|
| Fifteen Puzzle | Built | 4×4. No move count on screen: the board takes that room (76% of the screen), for larger cards. |
| Memory | Built | 5×4 grid (10 pairs). No difficulty levels. No move count on screen: the board is a little larger instead (70%), as is Lights Out's. |
| Lights Out | Built | Owner asked which size plays best. **5 across × 4 down**, for the largest cards that fit, and no move count. Checked by brute force: every 5×4 board has exactly one solution, 10 taps on average (the same as 5×5, which needs 5–14); 4×4 and 5×3 average under 5 taps (trivial). Only deal boards needing at least 7 taps. |
| Tower of Hanoi | Built | A suit for each few ranks, always the same, to tell cards apart: A–3 spades, 4–5 clubs, 6–7 diamonds, 8–9 hearts. Cards overlap in each pile, so show the rank and suit along the **top** of each card: with a small offset the rank must stay visible. Narrow cards (22% of the board) spread half as far again as other fans, so tall piles read. The move count is only the number, outside the board at the screen's far left, level with its middle (owner, 2026-10-05). That leaves the crescent empty, but no room for larger cards: a full pile already reaches the screen's foot, so they keep their size. |
| Clock Solitaire | Built | The one exception to the grid: the hours in a circle, each pile turned so its top points away from the middle, with no offset between a pile's cards. Kings in the middle, each one that has come up showing 20% of its width beside the pile, to count them. No text and no highlight on the next pile: the card just turned up is tinted blue, on a circle that spreads out from under its pile, and the player finds its hour. A tap there sends the card under that pile (and over every other), turning as it goes, and only then is that pile's top card turned up. A progress ring inside the hours (90% of the radius that would just clear them) lies under all the cards, so the Kings may cover it; the cards keep their size (13.9% of the board) and the ring gives way. Its board is larger than the others' (80% of the screen, reaching its foot). Pure luck. |
| Fortune's Call | Built | One card and four calls (Red, Black, Higher, Lower). With nothing under it, the board sits in the middle of the screen. Points and misses are two short arcs at the left (46°, and the misses' 26°), centred on the middle, with no numbers. The calls are card-wide buttons with rounded corners (an owner's exception to round buttons), 15 dp from the cards. Wins at 20 points: "You reached 20 points!" |
| Aces Up | Built | Two piles one over the other in the middle and one at each side, level with the middle of those two; cards as large as two rows allow. Uses the empty-pile slot and two-card stack. Deal on the bottom button. |
| Baroness | Built | Three piles in a row over two centred under them, top cards only: Kings alone or pairs making 13. Deal on the bottom button. |
| Beehive | Built | Owner: "let's try to make this work". Leave out the stock and discard piles; there may just be enough room. Six piles two across and three down in the middle, which set the cards' size; the beehive to their left, reaching out past the board to 40% of a card short of the screen's edge, and to their right, the same gap away, the waste's last three cards in a straight column centred on the grid, each covering 35% of the one above, the top one lowest and all but that one shaded. The waste's three places are fixed, so its first card doesn't move as more come. Its cards come from off screen on the right, rather than the left. |
| Triple Stack | Built | No stock or discard. "Ingredients N" above the hand, then a bar three cards wide in the game's colour, full at 44 and emptying as they're served; the count and bar sit up towards the time, well clear of the cards. **Serve (n)** shows the refill a selection earns (0 when it isn't a sandwich). |
| Dominion | Built | 5×3 board filling a larger board (76% of the screen), with no reserve and no button. Each deal leaves one suit out at random and takes 15 cards from the other three, dealing again (up to 100 times) while any card has no match by rank or suit. No hint of the places the picked card can conquer (owner). Conquests glide one card onto the other; the conquered card stays hidden under it. The owner's note said 18 cards, the phone's 15 plus its reserve of 3; with no reserve, 15 fill the board. |
| Rogue Run | Built | Health is an arc on the screen's left, centred on the screen's middle (`SideGauge`, worked out from the board's size so it scales with any screen). The weapon's place is only its outline and sword, on black, until a weapon is equipped. Top: weapon, then the monsters it has killed in an outlined zone a card high: the last always at the zone's right, each earlier one a little further left under it (at most 30% of a card apart), closing up as they fill the zone. A monster that can kill has a dot in its top right corner: red when both the fist and the weapon would, yellow when only one would. Middle: "HP: 5" at the cards' left edge, larger than "Rm: 7" and "Dk: 28" at the right, which may reach past the board as far as the round screen allows, so all three fit on a small screen; the health shrinks a little only if it must. Bottom: the room's four cards. Buttons are coloured by what they do, each light enough for dark text: orange Run, green Heal, blue Equip, red Fist. With a monster picked, Fist is the bottom button and a purple "Wpn −1" button sits at the left of the killed monsters' zone when the weapon can fight it, wide enough for its label with room either side, so it may reach over the earlier monsters (a black edge sets it apart), but never over the last one, at the zone's right. Tapping the picked card again does its action when there is only one. |
| Lycan | Built | Owner: "let's try to make this work". Plays the phone's Medium deck (Aces to 9s). The draw pile is at the far left of the middle row; when its top card is drawn the one under it turns up 250 ms later, turning over twice as slowly as usual (480 ms). Beside it are the Wolf's score, Fold, and yours; when a round ends what it scored takes Fold's place at once, still, set like a score (the name as large as "Wolf" and "You", the points as the scores); the scores change at once, with no counting up, and the next round starts by itself 2 s later (counted only while the watch is awake, so a round never moves on unseen). The one bottom button names the capture picked, coloured green (Perfect), purple (Sum), pink (Split) or orange (Over), or says "Select". |
| Ebb & Flow | Built | Owner's layout: the four piles in a row at the top (two rising, then two falling), an up or down arrow (mid grey) on each one's empty slot and no labels; the hand in one row under them, as wide as the screen allows; **Draw (45)** on the bottom button, disabled until two cards are down (and once the stock is empty), with no help text. Plays the phone's Normal hand of seven, which sets the card size: seven side by side reach past the board to the screen's edges. Tap a card, then a pile; no hint of which piles the picked card fits (owner). No drawing by itself when nothing fits: Draw is there to tap. |
| Little Windmill | Built | Added by the owner (2026-10-02) as Four Seasons, from the phone's single-card patience; renamed and changed by the owner (2026-10-05): an **Ace taken from the deck** starts the first foundation (not the first card dealt), and every foundation builds **up by suit from Ace to King**, so ranks never wrap. A 3×3 grid: the cross of five piles, built down regardless of suit, a foundation in each corner. The owner's exception to "no waste on screen": the waste is a pile at the middle left, level with the cross's middle row, 30% of a card in from as near the screen's edge as it goes; **Draw (46)** on the bottom button. Tap a card, then a pile; piles the picked card fits are tinted blue. In place of the phone's double tap, tapping the picked card again sends it to its foundation (or unpicks it when it can't go); tapping any corner does the same. No finishing by itself once the stock is empty. Its picture is the phone's windmill (Gabriël's *In the Month of July*). |
| Nestor | Built | Added by the owner (2026-10-02), who gave the choice of two suits with a reserve of two, or Aces to 8s. **Aces to 8s** (32 cards): with two suits each rank has one pair, so there is never a choice to make. Six columns of five, no rank twice in a column, and a reserve of two under them, both free. Checked by playing 3,000 deals each way: 74% of these can be won and random play wins 23% of those; seven columns of four with a reserve of four is too easy (99.9%, random play 43%) and five columns of six too hard (26%). **Only deals that can be won** are dealt (a search of each deal, which is small). Only ranks matter, so columns spread just far enough to show each rank. Nothing under the board: the cards are as large as the round screen holds (12% of its width), the columns across its middle and the reserve at its foot. Tap two free cards of a rank; the owner asked for no hint of which free cards match the picked one. No slots where cards have gone: nothing can go there. The picture is the phone's tapestry, *The Triumph of Time over Fame* (2026-10-06; it was Court's *Achilles Giving Nestor the Prize for Wisdom*). |
| Accordion | Built | Added by the owner (2026-10-02): **five across**, and the board scrolls. All 52 cards dealt face up in one line, read like text (left to right, then the next row down); a pile moves onto the one before it or the one three before it when the top cards share a rank or suit, and the piles after it close up. The usual rules, as the phone's weren't to hand to check against. The board (72% of the screen, in the middle, nothing under it) is a window on the line: three rows and half of the fourth, fading out at the top and foot where there are more. A drag or the crown scrolls the cards first, and the screen (to Reset and Undo) only once they're at their end; a drag above or below the board scrolls the screen at once. Tap a pile, then the pile to move it onto. The piles one and three back from the picked one are marked: green when it can go on them (and the picked pile's border is then green too), faded to 50% (at once, with no fade) when it can't. Only each pile's top card is kept and drawn: what's under it never comes back. A new deal starts from the top. Asleep, it shows the top of the line. |
| Jailbreak | Built | Added by the owner (2026-10-02). Plays the phone's Normal deal: four walls of six and two cards in each corner, leaving a stock of 20. A 3×3 grid of piles, the Joker's cell in the middle: an empty slot (there is no Joker card) showing the Joker's mark in prison orange, drawn on the slot under any cards so it stays put as they come and go, that holds up to three drawn cards. The game's colour is a desaturated orange. Owner's simplifications: no count under each pile; the red and black sums of the picked cards as two numbers at the left, outside the board, level with the middle row (30% when 0); the stock only as a count on the bottom button, **Draw (12)**. Picked cards are blue and go by themselves once the two sums match. |
| Elevens | Built | Added by the owner (2026-10-04), from the phone's. A 3×3 grid and nothing else: no bottom button, the stock only as "Stock: 43" in the crescent. Picked cards go by themselves once they make 11 or a Jack, Queen and King; replacements come in from the left, removed cards leave to the right. As on the phone now, only deals allowing **four moves in a row** are dealt (up to 100 tries). |
| Ten-Twenty-Thirty | Built | Added by the owner (2026-10-04). All 52 cards dealt at the start (owner, 2026-10-05), in a line five across, scrolling as Accordion's does; with no stock there's no bottom button, so the board is Accordion's too (72%, in the middle). Tap the cards of a run one after another, each beside the last (only an end comes off again); it goes once it makes 10, 20 or 30. Lost when no run adds up. |
| Royal Marriage | Built | Added by the owner (2026-10-04). All 52 dealt at once in a scrolling five-wide grid (default board), the Queen and King of Hearts shaded grey. The grid's window stops 8 dp short of the board's foot, for room over the Discard button. **Discard** on the bottom button, enabled only when the picked cards can go; picked cards are green then, blue otherwise. Picks are kept in the state (not an undo step) so the button can see them. Double-tapping a lone card that can go discards it. The picture is the phone's, Rubens's *Wedding by Proxy of Marie de' Medici*. |
| Ozanam's Square | Built | Added by the owner (2026-10-04). The 4×4 square with the tray at its right: a pile for each suit (spades, hearts, diamonds, clubs), one over the other beside the square's rows, set a little further off than the square's own gap; each pile's place shows as an empty slot once its cards are all out. The cards are smaller than Memory's, at the limit of what's usable (owner). Tap a pile to pick its top card; tapping the picked pile again brings up the next card of that suit. Tap a space to place it, a placed card then a space to move or swap it, or a placed card then any pile to send it back. Given cards lie on black (owner, 2026-10-05), clashing cards are tinted red. Plays the phone's Easy deal (seven cards given, one solution); best score is the fewest moves. |
| Augury | Built | Added by the owner (2026-10-04). The path in a scrolling five-wide grid (Accordion's fades), centred while it's one or two rows; under it, fixed, 3.5 card gaps below the grid's window, the hand of four with a card-sized **none fit** button before it (the not-allowed mark), so there's no button in the crescent. Scrolling past the grid's foot scrolls the screen on to Reset and Undo. The run of guesses is an arc on the screen's left like Rogue Run's, with no number. Cards played glide up from the hand; a card turned away lies on black at 50%; a hand declared empty slides off to the right. Plays the phone's Easy laws. No undo: it would make the law too easy to test. |
| Ouroboros | Built | Added by the owner (2026-10-04): "the round card layout is perfect for a watch". The loop of 53 round the whole screen (a board of 100%), the time over it as in every game, with a thin black outline so it reads over the cards (owner, 2026-10-08; it was on a fade to black). As on the phone, the top card is at the bottom (owner, 2026-10-05): the top two either side of 6 o'clock (the top card just right of it, the next just left), tops facing in so both read upright, and the loop runs on clockwise, up the left, across the top and down the right, the last card at 4 o'clock (owner, 2026-10-06). Its one gap is at 5 o'clock, between the last card and the top one, and never at the top: as the loop shortens, it closes up behind the top card and the gap grows. The top two stand apart (1.1 cards), so their taps don't meet; the next ten are 0.85 of a card apart, so each one's rank shows (owner, 2026-10-06: it's needed to decide), the ten after close up to what the rest share, and no two are more than 0.85 of a card apart, so cards always overlap a little. Cards keep their stacking as they move, and glide round the loop (`TableCard.orbit`) rather than across it. In the middle: the three marked paths in an arch under the loop, the middle one highest, each the same way in from it, a map mark on each while empty; under them "Jewels" and "Trinkets", pale and small, each over its count, side by side; as one goes up, a ring grows from behind it and fades, once (red for a jewel, gold for a trinket; owner, 2026-10-06). Collected cards leave to the right. No buttons: tap the top card while face down to look around; tap either top card, face up, to pick it; the card a picked number lands on is purple, pulled in so it barely overlaps the loop (it may cover the paths), less the further round it is: 1 or 2 on fully, tapering to half that at 10, so the far ones don't cover the left path (owner, 2026-10-07), and tapping it explores; with a number picked, tap an empty path to mark it; tap a marked path to put its card back on top (picked). |

## Skipped

Seer and Iris Garden were built and then removed by the owner (2026-10-05).

Everything else, including Black Hole, Chain of Command, Charlatan, Cribbage
Squares, Golf, Herbalist, Monte Carlo, Pagoda, Pocket Poker, Poker Squares,
Stalker and Twelve Trials, which were once on the maybe list.

## Decisions made while building

- **Suit colours** (owner, 2026-10-04): a setting, listed Auto,
  Two, Four (owner, 2026-10-05), Auto by default. Two shows red and white
  only. Auto gives Four where a game's suits matter and Two where only
  colours do (Fortune's Call, Jailbreak) or neither (Lycan, Clock, Nestor,
  Baroness, Beehive, Elevens, Ten-Twenty-Thirty, Ouroboros); when in doubt,
  Four (`Game.fourSuitColours`).
- **Continue Playing** shows the last 1 or 3 games, or Hide (a setting).
- **Stats and Settings** (owner, 2026-10-06) each have their own screen,
  from buttons under the games on the home screen, to declutter it: icons
  only, side by side, with twice the room over them (owner, 2026-10-06). A
  Top button ends the home list. The settings' headings have more room over
  them than the list's; both screens' titles have more room under them.
- **Stats** across every game: games completed (won at least once) out of
  all ("6/24", all in yellow), large, over a bar; Won (green), Lost (red) and Win % side by side over
  a bar split between wins and losses; Played and Best Streak side by side;
  the game played most as its home button (tapping it opens it); then under
  All Games each game played, by title (owner, 2026-10-06), its title in its pale colour, its wins and losses
  and win %, over a thin bar of its win % in that colour. The list's items
  and its groups have twice Wear's room between them, so they stay apart as
  they scroll.
- **Game colours** (owner, 2026-10-06): Dominion (plum), Ozanam's Square
  (teal) and Rogue Run (crimson) changed so they stand apart from the games
  beside them on the home screen; Nestor (green) changed with its picture.
- **Pictures and credits** follow the phone's (2026-10-06): each game's
  whole picture, uncut, is at the foot of its rules under Artwork, with its
  credit, under its stats (owner, 2026-10-06: not over the title), then View
  on Phone to open the picture's page on the phone. `scripts/game-art.py`
  makes the pictures from the phone's.
- **Stats by game** (2026-10-06): each line opens its game, and shows its
  fastest win and best score ("Fastest 2:34 · Moves 41") under its bar.
- **Home colours checked** (2026-10-06): neighbours at least ΔE 15 apart.
  Aces Up (violet grey), Clock (slate blue), Memory (sepia), Royal Marriage
  (gold) and Ten-Twenty-Thirty (indigo) changed for it; Ten-Twenty-Thirty and
  Tower of Hanoi were all but the same colour.
- **The result** is the game's message, in green or red with the buttons
  (Home is an icon), then the time against the best ("NEW BEST!" when it
  is), and the game's wins and losses. It scrolls, by touch or the crown,
  when there's more than fits.
- **Ratings** (difficulty, complexity, luck) are the phone's: each name in
  small capitals beside its bar, with no number, the bars all one width and
  lined up, in a pale grey box with more room over and under it (owner,
  2026-10-06). Headings in the rules have twice the room over them they had.
  The stats under the rules have a Stats heading, each stat its own box, two
  to a row: its number over its name.
- **Opening** (owner, 2026-10-05): the logo (the spade over "Solitarius")
  shows first, then the last game played, or home if there's none. Since
  2026-10-09 (owner): no animation; the spade and name are centred together
  (the name 1/3.4 of the spade's height, 0.75 of the name's size between
  them), white on black, then the spade eases to the colour theme's accent.
- **Colour themes** (owner, 2026-10-09): nine of the phone's (Nocturne by
  default, Folio, Blossom, Verdigris, Ember, Moss, Card Room, Sand,
  Rosewood), picked at the foot of Settings. A theme sets only the app's own
  accent; backgrounds stay black and games keep their colours.
- **Asleep**, a game's board shows only while it's in view; on the home
  screen, a game's rules or its result, the logo shows. The tap that wakes
  the watch (or one just after) never reaches the app, in any game.

- **Stats** count each deal once, the first time it's won or lost; a deal
  given up with Reset doesn't count. They keep the longest winning streak,
  the fastest win, and a best score where wins differ: fewest moves (Fifteen
  Puzzle, Memory, Lights Out, Tower of Hanoi) or highest score (Rogue Run,
  Lycan).
- **Undo** is an undo step per move; picking cards (Triple Stack, Rogue Run,
  Lycan) isn't. Reset (a double tap) deals a new game, which can't be undone.
- **Two-choice moves** use two smaller buttons in the crescent.
- **Fortune's Call** has the card to call face down on the left and the card
  to compare it with on the right. After a call the left card turns over
  and shows for a second; then the right card leaves, the called card takes
  its place and the next card arrives face down. Colour calls are green, on
  the left; rank calls blue, on the right. The called card is tinted green
  or red while it's shown.
- **Memory** flips cards. An open pair stays up for 2.4 s or until the next tap, green
  (then off to the right) for a match, red (then back over) for a miss.
- **Lycan** keeps one card size: each hand is one row that overlaps as it
  grows.
