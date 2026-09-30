package com.updownupdown.cards.watch.games.rogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.cards.BoardText
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardFan
import com.updownupdown.cards.watch.cards.CardGrid
import com.updownupdown.cards.watch.cards.PlayingCard
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.SlideSlot
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.ui.BottomActions
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

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
        "- Tap a card, then choose what to do with it at the bottom.",
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

    override fun outcome(state: RogueState): Outcome? = when {
        state.health <= 0 -> Outcome(false, "Slain in room ${state.roomNumber}")
        state.deck.isEmpty() && state.room.all { it == null } -> {
            val bonus = if (state.health == MAX_HEALTH) state.lastWasPotion ?: 0 else 0
            Outcome(true, "Escaped! Score ${state.health + bonus}")
        }
        else -> null
    }

    override fun actions(state: RogueState): List<GameAction<RogueState>> {
        val index = state.selected
        val card = index?.let { state.room[it] }
            ?: return listOf(GameAction("Run", enabled = canRun(state)) { run(it) ?: it })
        return when (kind(card)) {
            Kind.Potion -> listOf(GameAction("Drink +${minOf(value(card), MAX_HEALTH - state.health)}") { drink(it, index) ?: it })
            Kind.Weapon -> listOf(GameAction("Equip") { equip(it, index) ?: it })
            Kind.Monster -> buildList {
                if (canUseWeapon(state, card)) {
                    add(GameAction("Arm −${maxOf(0, value(card) - value(state.weapon!!))}") { fight(it, index, true) ?: it })
                }
                add(GameAction("Fist −${value(card)}") { fight(it, index, false) ?: it })
            }
        }
    }

    @Composable
    override fun Bottom(state: RogueState, play: (RogueState) -> Unit, feedback: Feedback, size: DpSize) {
        BottomActions(actions(state), null, state, play, feedback, size)
    }

    @Composable
    override fun Board(state: RogueState, play: (RogueState) -> Unit, feedback: Feedback, modifier: Modifier) {
        BoxWithConstraints(modifier) {
            val gap = 6.dp
            val roomHeight = maxHeight * 0.5f
            val lowerHeight = maxHeight - roomHeight - gap
            val cardWidth = (maxWidth - gap * 3) / ROOM
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                CardGrid(
                    columns = ROOM,
                    rows = 1,
                    modifier = Modifier.fillMaxWidth().height(roomHeight),
                    onTap = { index ->
                        val next = if (outcome(state) == null) select(state, index) else null
                        if (next == null) feedback.reject() else {
                            feedback.move()
                            play(next)
                        }
                    },
                ) { index ->
                    SlideSlot(state.room[index], key = { it }) { card ->
                        PlayingCard(card, selected = card != null && state.selected == index)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(lowerHeight),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The weapon, with the last monster it killed over it.
                    Box(Modifier.size(cardWidth, lowerHeight)) {
                        CardFan(
                            cards = listOfNotNull(state.weapon, state.weaponUsedOn.lastOrNull()),
                            modifier = Modifier.size(cardWidth, lowerHeight),
                        )
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        BoardText("Health ${state.health}/$MAX_HEALTH")
                        BoardText("Room ${state.roomNumber + state.roomsSkipped} of ${totalRooms + state.roomsSkipped}")
                        BoardText("${state.deck.size} cards left")
                    }
                }
            }
        }
    }
}
