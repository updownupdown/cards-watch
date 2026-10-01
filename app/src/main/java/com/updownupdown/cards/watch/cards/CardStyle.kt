package com.updownupdown.cards.watch.cards

/** Every card is 25:35, height over width. Cards only ever scale. */
const val CARD_ASPECT = 35f / 25f

/**
 * The sizes and spacing of everything on a card, as fractions of the card's
 * width (W) or height (H). The colours are in `ui/Theme.kt`; the rank, suit
 * and back icons are the SVGs in `src/main/card-icons`, each drawn in a
 * square of the size given here.
 */
object CardStyle {
    /** Corner radius, × W. */
    const val CORNER = 0.102f

    /** The rank's square, × W, and where its centre sits, × H from the top. */
    const val RANK_SIZE = 0.44f
    const val RANK_Y = 0.28f

    /** The suit's square, × W, and where its centre sits, × H from the top. */
    const val SUIT_SIZE = 0.46f
    const val SUIT_Y = 0.72f

    /** The logo's square on the back, × W, centred. The spade fills about 0.69 of it. */
    const val BACK_LOGO_SIZE = 0.82f

    /**
     * The line around the back: how far its middle sits in from the card's
     * edge, how thick it is and its corner radius, all × W.
     */
    const val BACK_BORDER_INSET = 0.08f
    const val BACK_BORDER_WIDTH = 0.03f
    const val BACK_BORDER_CORNER = 0.06f

    /** How far a pile's top card sits up and right of the card under it, × W. */
    const val PILE_OFFSET = 0.1f

    // Overlapping cards (a fan) show the rank and suit side by side in a
    // strip along the top, and a large suit below when there's room.
    /** The strip's rank and suit squares, × the strip's height, and at most × W. */
    const val INDEX_SIZE = 0.72f
    const val INDEX_MAX_SIZE = 0.42f
    /** How far the strip's rank and suit centres sit from the card's middle, × their size. */
    const val INDEX_SPREAD = 0.42f

    /** How far down each card of a fan sits from the one above, × H, when there's room. */
    const val FAN_SPREAD = 0.3f

    /** The edge around a card and the border of a selected one, in dp. */
    const val EDGE_DP = 1.5f
    const val SELECTION_DP = 2f
}
