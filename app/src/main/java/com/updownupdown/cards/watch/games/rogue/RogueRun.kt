package com.updownupdown.cards.watch.games.rogue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.BoardText
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.cards.pileEdge
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.BottomActions
import com.updownupdown.cards.watch.ui.GameButton
import com.updownupdown.cards.watch.ui.LocalBoardSize
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Scoundrel, as the phone's Rogue Run plays it.
 *
 * @param room four places; null once resolved.
 * @param weaponUsedOn the monsters the weapon has killed, in order.
 * @param ranFrom the room last run from, which can't be run from again.
 * @param selected the room card picked for the bottom buttons (not an undo step).
 */
@Serializable
data class RogueState(
    val deck: List<Card>,
    val room: List<Card?> = List(ROOM) { null },
    val roomNumber: Int = 0,
    val weapon: Card? = null,
    val weaponUsedOn: List<Card> = emptyList(),
    val health: Int = MAX_HEALTH,
    val ranFrom: Int? = null,
    val running: Boolean = false,
    val roomsSkipped: Int = 0,
    val lastWasPotion: Int? = null,
    val selected: Int? = null,
)

private const val ROOM = 4
private const val MAX_HEALTH = 20

object RogueRun : Game<RogueState> {
    override val id = "rogue-run"
    override val thumbnail = R.drawable.thumb_rogue_run
    override val banner = R.drawable.banner_rogue_run
    override val accent = Color(0xFF33343A)
    override val title = "Rogue Run"
    override val stateSerializer: KSerializer<RogueState> = RogueState.serializer()

    override val rules = listOf(
        "## Objective",
        "- Get through every room before your health runs out.",
        "- Your score is the health left, plus a bonus when you finish on a potion at full health.",
        "## Cards",
        "- **Black cards are monsters.** Fighting one costs health equal to its value (Jacks 11, Queens 12, Kings 13, Aces 14).",
        "- **Diamonds are weapons.** A weapon takes its value off each monster's damage. After it kills a monster it can only fight monsters of **equal or lower** value than the last one it killed.",
        "- **Hearts are potions** and heal you by their value, up to 20.",
        "## Rooms",
        "- Tap a card, then choose what to do with it at the bottom. When there's only one thing to do, tapping the card again does it.",
        "- With a monster picked, **Fist** is at the bottom and **Wpn** (when your weapon can fight it) is at the top right.",
        "- Resolve any three cards of a room to move on; the fourth stays for the next room.",
        "- With no card picked, **Run** puts the whole room at the bottom of the dungeon. You can't run from two rooms in a row.",
    )

    fun value(card: Card) = if (card.rank == Rank.Ace) 14 else card.rank.value

    enum class Kind { Monster, Weapon, Potion }

    fun kind(card: Card) = when (card.suit) {
        Suit.Diamonds -> Kind.Weapon
        Suit.Hearts -> Kind.Potion
        else -> Kind.Monster
    }

    /** Every black card, and the red number cards 2 to 10. */
    val DECK: List<Card> = fullDeck().filter { kind(it) == Kind.Monster || it.rank.value in 2..10 }

    override fun newGame(random: Random): RogueState {
        var deck = DECK.shuffled(random)
        var attempts = 1
        while (falseStart(deck) && attempts < 20) {
            deck = DECK.shuffled(random)
            attempts++
        }
        return advance(RogueState(deck))
    }

    // ---- Dealing ----

    /** The best health after resolving three cards of [room], from full health. */
    private fun bestHealth(room: List<Card>): Int {
        fun solve(cards: List<Card>, resolved: Int, health: Int, weapon: Card?, lastKill: Int?): Int {
            if (health <= 0) return Int.MIN_VALUE
            if (resolved == 3) return health
            var best = Int.MIN_VALUE
            cards.forEachIndexed { i, card ->
                val rest = cards.filterIndexed { j, _ -> j != i }
                best = maxOf(
                    best,
                    when (kind(card)) {
                        Kind.Potion -> solve(rest, resolved + 1, minOf(MAX_HEALTH, health + value(card)), weapon, lastKill)
                        Kind.Weapon -> solve(rest, resolved + 1, health, card, null)
                        Kind.Monster -> maxOf(
                            solve(rest, resolved + 1, health - value(card), weapon, lastKill),
                            if (weapon != null && (lastKill == null || value(card) <= lastKill)) {
                                solve(rest, resolved + 1, health - maxOf(0, value(card) - value(weapon)), weapon, value(card))
                            } else {
                                Int.MIN_VALUE
                            },
                        )
                    },
                )
            }
            return best
        }
        return solve(room, 0, MAX_HEALTH, null, null)
    }

    /** Deals whose first room and the one after it both leave you nearly dead. */
    private fun falseStart(deck: List<Card>) =
        bestHealth(deck.take(4)) <= 2 && bestHealth(deck.drop(4).take(4)) <= 2

    /** Refills the room once a single card is left, and settles a finished game. */
    fun advance(state: RogueState): RogueState {
        val left = state.room.filterNotNull()
        if (state.deck.isEmpty() || left.size > 1) return state
        val keep = left.firstOrNull()
        val needed = if (keep == null) ROOM else ROOM - 1
        val incoming = state.deck.take(needed).toMutableList()
        val room = state.room.map { card -> card?.takeIf { it == keep } ?: incoming.removeFirstOrNull() }
        return state.copy(
            deck = state.deck.drop(needed),
            room = room,
            roomNumber = state.roomNumber + if (state.running) 0 else 1,
            running = false,
        )
    }

    // ---- Actions ----

    fun canRun(state: RogueState) = state.room.count { it != null } == ROOM && state.ranFrom != state.roomNumber

    fun canUseWeapon(state: RogueState, monster: Card): Boolean {
        if (state.weapon == null) return false
        val last = state.weaponUsedOn.lastOrNull() ?: return true
        return value(monster) <= value(last)
    }

    fun run(state: RogueState): RogueState? {
        if (!canRun(state)) return null
        return advance(
            state.copy(
                deck = state.deck + state.room.filterNotNull(),
                room = List(ROOM) { null },
                ranFrom = state.roomNumber,
                running = true,
                roomsSkipped = state.roomsSkipped + 1,
                lastWasPotion = null,
                selected = null,
            ),
        )
    }

    private fun resolve(state: RogueState, index: Int, change: RogueState.() -> RogueState) = advance(
        state.change().copy(
            room = state.room.mapIndexed { i, c -> if (i == index) null else c },
            selected = null,
        ),
    )

    fun drink(state: RogueState, index: Int): RogueState? {
        val card = state.room[index]?.takeIf { kind(it) == Kind.Potion } ?: return null
        return resolve(state, index) { copy(health = minOf(MAX_HEALTH, health + value(card)), lastWasPotion = value(card)) }
    }

    fun equip(state: RogueState, index: Int): RogueState? {
        val card = state.room[index]?.takeIf { kind(it) == Kind.Weapon } ?: return null
        return resolve(state, index) { copy(weapon = card, weaponUsedOn = emptyList(), lastWasPotion = null) }
    }

    fun fight(state: RogueState, index: Int, withWeapon: Boolean): RogueState? {
        val card = state.room[index]?.takeIf { kind(it) == Kind.Monster } ?: return null
        if (withWeapon && !canUseWeapon(state, card)) return null
        val damage = if (withWeapon) maxOf(0, value(card) - value(state.weapon!!)) else value(card)
        return resolve(state, index) {
            copy(
                health = maxOf(0, health - damage),
                weaponUsedOn = if (withWeapon) weaponUsedOn + card else weaponUsedOn,
                lastWasPotion = null,
            )
        }
    }

    fun select(state: RogueState, index: Int): RogueState? {
        if (state.room[index] == null) return null
        return state.copy(selected = if (state.selected == index) null else index)
    }

    override fun isUndoStep(previous: RogueState, next: RogueState) =
        previous.copy(selected = null) != next.copy(selected = null)

    // ---- End ----

    private val totalRooms = 1 + (DECK.size - ROOM + ROOM - 2) / (ROOM - 1)

    override val scoreName = "Score"

    /** The health left, plus the last potion's value when it was drunk at full health. */
    override fun score(state: RogueState) = state.health + if (state.health == MAX_HEALTH) state.lastWasPotion ?: 0 else 0

    override fun outcome(state: RogueState): Outcome? = when {
        state.health <= 0 -> Outcome(false, "Slain in room ${state.roomNumber}")
        state.deck.isEmpty() && state.room.all { it == null } -> Outcome(true, "Escaped!")
        else -> null
    }

    override fun actions(state: RogueState): List<GameAction<RogueState>> {
        val index = state.selected
        val card = index?.let { state.room[it] }
            ?: return listOf(GameAction("Run", enabled = canRun(state)) { run(it) ?: it })
        return when (kind(card)) {
            Kind.Potion -> listOf(
                GameAction("Heal +${minOf(value(card), MAX_HEALTH - state.health)}", color = AppColors.Good) { drink(it, index) ?: it },
            )
            Kind.Weapon -> listOf(GameAction("Equip", color = AppColors.Neutral) { equip(it, index) ?: it })
            // The weapon, when it can be used, has its own button on the board.
            Kind.Monster -> listOf(GameAction("Fist −${value(card)}", color = AppColors.Bad) { fight(it, index, false) ?: it })
        }
    }

    @Composable
    override fun Bottom(state: RogueState, play: (RogueState) -> Unit, feedback: Feedback, size: DpSize) {
        BottomActions(actions(state), null, state, play, feedback, size)
    }

    /** The health bar's row and the bar itself. */
    private val HEALTH_ROW = 16.dp
    /** The weapon button's height. */
    private val WEAPON_BUTTON = 36.dp
    private val HEALTH_BAR = 7.2.dp
    /** The gaps between the three rows, × what would spread them over the whole board. */
    private const val ROW_GAPS = 0.6f

    /**
     * Top: the weapon, the pile of monsters it has killed, then the room
     * number and stock. Middle: health. Bottom: the room's four cards.
     */
    private class Rows(board: Dp) {
        val layout = CardLayout(ROOM, 1, board, board, gap = 0.12f)
        val cardWidth = layout.cardWidth
        val edge = pileEdge(cardWidth)
        val pileHeight = layout.cardHeight + edge
        private val gap = (board - pileHeight - HEALTH_ROW - layout.cardHeight) / 2 * ROW_GAPS
        // Nearer the top than the foot, to stand clear of the buttons.
        val topY = (board - pileHeight - HEALTH_ROW - layout.cardHeight - gap * 2) / 4
        val healthY = topY + pileHeight + gap
        val roomY = healthY + HEALTH_ROW + gap
    }

    @Composable
    override fun Board(state: RogueState, play: (RogueState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val board = LocalBoardSize.current
        CardTable(
            modifier,
            overlay = {
                val rows = Rows(board)
                // The text's left edge, shared by the room, the stock and the health.
                val textX = rows.layout.x(2)
                val picked = state.selected
                val monster = picked?.let { state.room[it] }?.takeIf { kind(it) == Kind.Monster }
                if (picked != null && monster != null && canUseWeapon(state, monster) && outcome(state) == null) {
                    // Fighting with the weapon, in place of the room and stock while a monster is picked.
                    Box(Modifier.offset(x = textX, y = rows.topY).size(board - textX, rows.pileHeight), contentAlignment = Alignment.CenterEnd) {
                        GameButton(
                            label = "Wpn −${maxOf(0, value(monster) - value(state.weapon!!))}",
                            onClick = { feedback.tryMove(fight(state, picked, true), play) },
                            // Clear of the pile beside it.
                            width = board - textX - 6.dp,
                            height = WEAPON_BUTTON,
                            color = AppColors.Purple,
                        )
                    }
                } else {
                    Column(
                        Modifier.offset(x = textX, y = rows.topY).size(board - textX, rows.pileHeight),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                    ) {
                        BoardText("Room: ${state.roomNumber + state.roomsSkipped}")
                        BoardText("Stock: ${state.deck.size}")
                    }
                }
                Row(Modifier.offset(y = rows.healthY).fillMaxWidth().height(HEALTH_ROW), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(textX - 6.dp).height(HEALTH_BAR).clip(CircleShape).background(AppColors.DeepBlue)) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(state.health / MAX_HEALTH.toFloat())
                                .clip(CircleShape)
                                .background(AppColors.Good),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    // On one line, running past the board's edge if it must: the screen is wide here.
                    Text(
                        text = "HP: ${state.health} / $MAX_HEALTH",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            },
        ) {
            val rows = Rows(width)
            val layout = rows.layout
            val weapon = state.weapon
            if (weapon != null) {
                card(weapon, weapon, layout.x(0), rows.topY + rows.edge, rows.cardWidth)
                pile(state.weaponUsedOn, layout.x(1), rows.topY, rows.cardWidth)
            } else {
                slot(layout.x(0), rows.topY + rows.edge, rows.cardWidth)
            }
            state.room.forEachIndexed { i, card ->
                val x = layout.x(i)
                if (card != null) {
                    card(card, card, x, rows.roomY, rows.cardWidth, selection = if (state.selected == i) Selection.Neutral else null)
                } else {
                    slot(x, rows.roomY, rows.cardWidth)
                }
                tap(x, rows.roomY, rows.cardWidth, layout.cardHeight) {
                    // A second tap on the picked card does the one thing it can
                    // (a monster the weapon can fight has two).
                    val choice = card != null && kind(card) == Kind.Monster && canUseWeapon(state, card)
                    val only = actions(state).singleOrNull()?.takeIf { state.selected == i && !choice }
                    when {
                        outcome(state) != null -> feedback.reject()
                        only != null -> {
                            feedback.move()
                            play(only.perform(state))
                        }
                        else -> feedback.tryMove(select(state, i), play)
                    }
                }
            }
        }
    }
}
