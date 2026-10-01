package com.updownupdown.cards.watch.cards

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How cards move on a [CardTable], stepping the clock by hand: cards arrive
 * from the left, leave to the right, glide between places, take turns in a
 * deal, and do it all the other way for an undo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.WearOSSmallRound)
class CardTableTest {
    @get:Rule
    val compose = createComposeRule()

    private val ace = Card(Rank.Ace, Suit.Spades)
    private val two = Card(Rank.Two, Suit.Hearts)
    private val three = Card(Rank.Three, Suit.Clubs)

    private var laid by mutableStateOf(listOf<TableCard>())
    private var undoing by mutableStateOf(false)

    private fun at(card: Card, x: Int, y: Int = 0, removal: Removal = Removal.Discard, from: DpOffset? = null, fromRight: Boolean = false) =
        TableCard(card, card, x.dp, y.dp, 20.dp, removal = removal, from = from, fromRight = fromRight)

    private fun show(vararg first: TableCard) {
        laid = first.toList()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalUndoing provides undoing) {
                CardTable(Modifier.size(150.dp)) { laid.forEach { card(it) } }
            }
        }
        frames()
    }

    /** Lets a change be drawn and its animations start. */
    private fun frames() {
        compose.waitForIdle()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    /** Runs every animation to its end. */
    private fun settle() = compose.mainClock.advanceTimeBy(CARD_MOVE_MS + CARD_DEAL_MAX_MS + 200L)

    private fun change(undo: Boolean = false, next: List<TableCard>) {
        undoing = undo
        laid = next
        frames()
    }

    private fun node(card: Card) = compose.onNodeWithTag(cardTag(card))
    private fun place(card: Card): Offset = node(card).fetchSemanticsNode().positionInRoot
    private fun px(dp: Int) = dp * compose.density.density
    /** The small round screen's width in pixels, which is how far off a card starts or ends. */
    private val screen = 384f

    private fun assertAt(card: Card, x: Int, y: Int = 0) {
        val place = place(card)
        assertEquals(px(x), place.x, 1f)
        assertEquals(px(y), place.y, 1f)
    }

    @Test
    fun theFirstCardsAreInPlaceAtOnce() {
        show(at(ace, 10), at(two, 40, 30))
        assertAt(ace, 10)
        assertAt(two, 40, 30)
    }

    @Test
    fun aNewCardSlidesInFromTheLeft() {
        show(at(ace, 10))
        change(next = listOf(at(ace, 10), at(two, 40)))
        assertTrue("starts off screen to the left", place(two).x < 0)
        settle()
        assertAt(two, 40)
    }

    @Test
    fun aRemovedCardSlidesOffToTheRight() {
        show(at(ace, 10), at(two, 40))
        change(next = listOf(at(ace, 10)))
        compose.mainClock.advanceTimeBy(CARD_MOVE_MS / 2L)
        assertTrue("is on its way right", place(two).x > px(40))
        settle()
        node(two).assertDoesNotExist()
        assertAt(ace, 10)
    }

    @Test
    fun aVanishingCardStaysPutThenGoes() {
        show(at(ace, 10), at(two, 40, removal = Removal.Vanish))
        change(next = listOf(at(ace, 10)))
        compose.mainClock.advanceTimeBy(CARD_MOVE_MS / 2L)
        assertAt(two, 40)
        settle()
        node(two).assertDoesNotExist()
    }

    @Test
    fun aMovedCardGlides() {
        show(at(ace, 10))
        change(next = listOf(at(ace, 60, 20)))
        compose.mainClock.advanceTimeBy(CARD_MOVE_MS / 2L)
        val midway = place(ace)
        assertTrue("is between its places", midway.x > px(10) && midway.x < px(60))
        assertTrue(midway.y > 0 && midway.y < px(20))
        settle()
        assertAt(ace, 60, 20)
    }

    @Test
    fun anUndoSendsANewCardBackToTheLeft() {
        show(at(ace, 10))
        change(next = listOf(at(ace, 10), at(two, 40)))
        settle()
        change(undo = true, next = listOf(at(ace, 10)))
        compose.mainClock.advanceTimeBy(CARD_MOVE_MS / 2L)
        assertTrue("is on its way back left", place(two).x < px(40))
        settle()
        node(two).assertDoesNotExist()
    }

    @Test
    fun anUndoBringsADiscardBackFromTheRight() {
        show(at(ace, 10), at(two, 40))
        change(next = listOf(at(ace, 10)))
        settle()
        change(undo = true, next = listOf(at(ace, 10), at(two, 40)))
        assertTrue("starts off screen to the right", place(two).x > px(40) + screen / 2)
        settle()
        assertAt(two, 40)
    }

    @Test
    fun anUndoPutsAVanishedCardStraightBack() {
        show(at(ace, 10), at(two, 40, removal = Removal.Vanish))
        change(next = listOf(at(ace, 10)))
        settle()
        change(undo = true, next = listOf(at(ace, 10), at(two, 40, removal = Removal.Vanish)))
        assertAt(two, 40)
    }

    @Test
    fun aDealSetsOffFromTheRightOneCardAtATime() {
        show(at(ace, 0))
        change(next = listOf(at(ace, 0), at(two, 40), at(three, 80)))
        val start = place(two).x
        compose.mainClock.advanceTimeBy(CARD_DEAL_STAGGER_MS - 40L)
        assertEquals("the left card waits its turn", start, place(two).x, 1f)
        assertTrue("the right-most card has set off", place(three).x > px(80) - screen + 1f)
        settle()
        assertAt(two, 40)
        assertAt(three, 80)
    }

    @Test
    fun aCardWithAPlaceToComeFromIsDealtFromThere() {
        val stock = DpOffset(50.dp, 120.dp)
        show(at(ace, 0))
        change(next = listOf(at(ace, 0), at(two, 40, from = stock)))
        val start = place(two)
        assertTrue("starts near its stock, not off screen", start.x > 0 && start.y > px(60))
        settle()
        assertAt(two, 40)
        // And goes back there when it leaves.
        change(next = listOf(at(ace, 0)))
        compose.mainClock.advanceTimeBy(CARD_MOVE_MS / 2L)
        assertTrue(place(two).y > 0)
        settle()
        node(two).assertDoesNotExist()
    }

    @Test
    fun aCardFromTheRightArrivesAndIsUndoneThere() {
        show(at(ace, 0))
        change(next = listOf(at(ace, 0), at(two, 40, fromRight = true)))
        assertTrue("starts off screen to the right", place(two).x > px(40) + screen / 2)
        settle()
        assertAt(two, 40)
        change(undo = true, next = listOf(at(ace, 0)))
        compose.mainClock.advanceTimeBy(CARD_MOVE_MS / 2L)
        assertTrue("goes back to the right", place(two).x > px(40))
        settle()
        node(two).assertDoesNotExist()
    }

    @Test
    fun tapAreasGrowIntoTheGapsBetweenThem() {
        val reach = 8.dp
        // Two side by side 4dp apart, and one on its own far below.
        val taps = spreadTaps(
            listOf(
                Tap(0.dp, 0.dp, 20.dp, 28.dp) {},
                Tap(24.dp, 0.dp, 20.dp, 28.dp) {},
                Tap(0.dp, 100.dp, 20.dp, 28.dp) {},
            ),
            reach,
        )
        // Neighbours meet in the middle of their gap, and reach out on their free sides.
        assertEquals(22.dp, taps[0].x + taps[0].width)
        assertEquals(22.dp, taps[1].x)
        assertEquals((-8).dp, taps[0].x)
        assertEquals(52.dp, taps[1].x + taps[1].width)
        // The lone one grows by the full reach all round.
        assertEquals(92.dp, taps[2].y)
        assertEquals(44.dp, taps[2].height)
        // Overlapping areas don't grow into each other.
        val stacked = spreadTaps(listOf(Tap(0.dp, 0.dp, 20.dp, 28.dp) {}, Tap(10.dp, 0.dp, 20.dp, 28.dp) {}), reach)
        assertEquals(20.dp, stacked[0].x + stacked[0].width)
        assertEquals(10.dp, stacked[1].x)
    }
}
