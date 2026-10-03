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

    // The rank sits over the suit, both in the middle of the card's width.
    /** How far the rank's mark is from the card's top edge, and the suit's from its bottom edge, × W. */
    const val FACE_PAD_RANK = 0.15f
    const val FACE_PAD_SUIT = 0.16f

    /**
     * The squares the rank and suit icons are drawn in, × W (the marks fill
     * most of them). A card is 1.4 W high, so the two sizes and the two
     * paddings must come to less than that, or the marks overlap.
     */
    const val RANK_SIZE = 0.56f
    const val SUIT_SIZE = 0.4f

    /** The logo's square on the back, × W, centred. The spade fills about 0.69 of it. */
    const val BACK_LOGO_SIZE = 0.8f

    /**
     * The line around the back: how far its middle sits in from the card's
     * edge, how thick it is and its corner radius, all × W.
     */
    const val BACK_BORDER_INSET = 0.07f
    const val BACK_BORDER_WIDTH = 0.02f
    const val BACK_BORDER_CORNER = 0.06f

    /** How far a pile's top card sits up and right of the card under it, × W. */
    const val PILE_OFFSET = 0.1f

    /** How far down each card of a fan sits from the one above, × H, when there's room. */
    const val FAN_SPREAD = 0.3f

    /**
     * A warning dot in the top right corner: its radius, how far its middle
     * sits in from the top and right edges, and the line round it, all × W.
     */
    const val DOT_RADIUS = 0.085f
    const val DOT_INSET = 0.17f
    const val DOT_EDGE = 0.035f

    /** The edge around a card and the border of a selected one, in dp. */
    const val EDGE_DP = 1.5f
    const val SELECTION_DP = 2f
}
