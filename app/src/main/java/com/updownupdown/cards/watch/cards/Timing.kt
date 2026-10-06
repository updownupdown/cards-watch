package com.updownupdown.cards.watch.cards

// Every animation's timing, in milliseconds.

/** How long a card takes to slide in, out or across. */
const val CARD_MOVE_MS = 600
/** How long a card takes to turn over. */
const val CARD_FLIP_MS = 240
/**
 * Cards arriving together (a deal) set off this far apart, the right-most
 * first, so they don't cross. With many cards the gap shrinks so the last
 * sets off within [CARD_DEAL_MAX_MS].
 */
const val CARD_DEAL_STAGGER_MS = 100
const val CARD_DEAL_MAX_MS = 800
/** How long a new deal waits, once the last deal's cards have slid away, before it's dealt. */
const val CARD_CLEAR_PAUSE_MS = 200
/** How long Ouroboros's ring takes to grow and fade from behind a count that goes up. */
const val COLLECT_GLOW_MS = 900
/** How long Memory shows a turned-up pair before turning it back or taking it away. */
const val PAIR_SHOW_MS = 2400L
