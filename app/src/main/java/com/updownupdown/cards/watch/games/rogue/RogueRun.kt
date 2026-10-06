package com.updownupdown.cards.watch.games.rogue

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardColors
import com.updownupdown.cards.watch.cards.CardIcon
import com.updownupdown.cards.watch.cards.CardIcons
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.RoundScreen
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.BottomActions
import com.updownupdown.cards.watch.ui.GameButton
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalAmbient
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.SideGauge
import com.updownupdown.cards.watch.ui.WearGamePreview
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
    override val thumbnail = R.drawable.thumb_rogue_run
    override val banner = R.drawable.banner_rogue_run
    override val accent = R.color.game_rogue_run
    override val accentPale = R.color.game_rogue_run_pale
    override val title = "Rogue Run"
    override val rating = Rating(difficulty = 70, complexity = 55, luck = 45)
    override val stateSerializer: KSerializer<RogueState> = RogueState.serializer()

    override val rules = listOf(
        "## Objective",
        "Get through every room before your health runs out.",
        "## Cards Types",
        "- **Black cards are monsters.** Fighting one costs health equal to its value (J: 11, Q: 12, K: 13, A: 14).",
        "- **Diamonds are weapons.** A weapon takes its value off each monster's damage. After it kills a monster it can only fight monsters of **equal or lower** value than the last one it killed.",
        "- **Hearts are potions** and heal you by their value, up to 20.",
        "## Rooms",
        "- Tap a room card, and confirm/choose its action. You can double tap to act on non-monster cards.",
        "- With a monster picked, **Fist** fights that monster barehanded, while **Wpn** uses the equipped weapon for lower damage, if available.",
        "- Resolve any three cards of a room to move on; the fourth stays for the next room.",
        "- With no card picked, **Run** allows you to puts the whole room at the bottom of the dungeon. You can't run from two rooms in a row.",
        "## Scoring",
        "Your score is equal to your health left, plus a bonus when you finish with an unused potion while at full health.",
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
        fun solve(
            cards: List<Card>,
            resolved: Int,
            health: Int,
            weapon: Card?,
            lastKill: Int?
        ): Int {
            if (health <= 0) return Int.MIN_VALUE
            if (resolved == 3) return health
            var best = Int.MIN_VALUE
            cards.forEachIndexed { i, card ->
                val rest = cards.filterIndexed { j, _ -> j != i }
                best = maxOf(
                    best,
                    when (kind(card)) {
                        Kind.Potion -> solve(
                            rest,
                            resolved + 1,
                            minOf(MAX_HEALTH, health + value(card)),
                            weapon,
                            lastKill
                        )

                        Kind.Weapon -> solve(rest, resolved + 1, health, card, null)
                        Kind.Monster -> maxOf(
                            solve(rest, resolved + 1, health - value(card), weapon, lastKill),
                            if (weapon != null && (lastKill == null || value(card) <= lastKill)) {
                                solve(
                                    rest,
                                    resolved + 1,
                                    health - maxOf(0, value(card) - value(weapon)),
                                    weapon,
                                    value(card)
                                )
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
        val room =
            state.room.map { card -> card?.takeIf { it == keep } ?: incoming.removeFirstOrNull() }
        return state.copy(
            deck = state.deck.drop(needed),
            room = room,
            roomNumber = state.roomNumber + if (state.running) 0 else 1,
            running = false,
        )
    }

    // ---- Actions ----

    fun canRun(state: RogueState) =
        state.room.count { it != null } == ROOM && state.ranFrom != state.roomNumber

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

    private fun resolve(state: RogueState, index: Int, change: RogueState.() -> RogueState) =
        advance(
            state.change().copy(
                room = state.room.mapIndexed { i, c -> if (i == index) null else c },
                selected = null,
            ),
        )

    fun drink(state: RogueState, index: Int): RogueState? {
        val card = state.room[index]?.takeIf { kind(it) == Kind.Potion } ?: return null
        return resolve(state, index) {
            copy(
                health = minOf(MAX_HEALTH, health + value(card)),
                lastWasPotion = value(card)
            )
        }
    }

    fun equip(state: RogueState, index: Int): RogueState? {
        val card = state.room[index]?.takeIf { kind(it) == Kind.Weapon } ?: return null
        return resolve(state, index) {
            copy(
                weapon = card,
                weaponUsedOn = emptyList(),
                lastWasPotion = null
            )
        }
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
    override val scoreName = "Score"

    /** The health left, plus the last potion's value when it was drunk at full health. */
    override fun score(state: RogueState) =
        state.health + if (state.health == MAX_HEALTH) state.lastWasPotion ?: 0 else 0

    override fun outcome(state: RogueState): Outcome? = when {
        state.health <= 0 -> Outcome(false, "Slain in room ${state.roomNumber}")
        state.deck.isEmpty() && state.room.all { it == null } -> Outcome(true, "Escaped!")
        else -> null
    }

    override fun actions(state: RogueState): List<GameAction<RogueState>> {
        val index = state.selected
        val card = index?.let { state.room[it] }
            ?: return listOf(
                GameAction(
                    "Run",
                    enabled = canRun(state),
                    color = AppColors.ButtonOrange
                ) { run(it) ?: it })
        return when (kind(card)) {
            Kind.Potion -> listOf(
                GameAction(
                    "Heal +${minOf(value(card), MAX_HEALTH - state.health)}",
                    color = AppColors.ButtonGreen
                ) { drink(it, index) ?: it },
            )

            Kind.Weapon -> listOf(GameAction("Equip", color = AppColors.ButtonBlue) {
                equip(
                    it,
                    index
                ) ?: it
            })
            // The weapon, when it can be used, has its own button on the board.
            Kind.Monster -> listOf(
                GameAction(
                    "Fist −${value(card)}",
                    color = AppColors.ButtonRed
                ) { fight(it, index, false) ?: it })
        }
    }

    @Composable
    override fun Bottom(
        state: RogueState,
        play: (RogueState) -> Unit,
        feedback: Feedback,
        size: DpSize
    ) {
        BottomActions(actions(state), null, state, play, feedback, size)
    }

    /** The middle row's height: room for the health, larger than the room and stock beside it. */
    private val HEALTH_ROW = 20.dp

    /** Padding around zone outlines so cards fit inside. */
    private val ZONE_PAD = 3.dp

    /** The weapon button's height. */
    private val WEAPON_BUTTON = 36.dp

    /** How far apart the killed monsters lie, at most, × a card's width. */
    private const val KILLED_STEP = 0.3f

    /**
     * Top: the weapon and the killed monsters' zone, each in an outline.
     * Middle: the health, room and stock on one line. Bottom: the room's four
     * cards. What's left of the board's height goes a fifth above and below
     * and three tenths into each gap between the rows, so the rows sit in the
     * middle whatever the screen.
     */
    private class Rows(board: Dp) {
        val layout = CardLayout(ROOM, 1, board, board, gap = 0.12f)
        val cardWidth = layout.cardWidth
        val cardHeight = layout.cardHeight

        private val remaining =
            max(0.dp, board - (cardHeight + ZONE_PAD * 2) - HEALTH_ROW - cardHeight)
        private val gap = remaining * 0.3f

        val topY = ZONE_PAD + remaining * 0.2f
        val healthY = topY + cardHeight + ZONE_PAD + gap
        val roomY = healthY + HEALTH_ROW + gap

        val zoneX = layout.x(1) + layout.gap
        val zoneWidth = (board - layout.x(0)) - zoneX
    }

    /** The space either side of the weapon button's label, and the black edge round it. */
    private val WEAPON_PAD = 9.dp
    private val WEAPON_EDGE = 2.dp

    /** The space between the health, the room number and the stock's count, and from the stock's to the screen's edge. */
    private val STATS_GAP = 7.dp
    private val ROW_MARGIN = 4.dp

    /** The room number or the stock's count, beside the health: smaller, and on one line. */
    @Composable
    private fun SmallStat(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }

    /** How long a second tap has to follow the first, to be a double tap. */
    private const val DOUBLE_TAP_MS = 400L

    /** Green above 15 health, then yellow above 10, orange above 5, and red. */
    private fun healthColour(health: Int) = when {
        health > 15 -> AppColors.Good
        health > 10 -> AppColors.PaleYellow
        health > 5 -> AppColors.ButtonOrange
        else -> AppColors.Bad
    }

    /** What a card's selection says it will do: heal, equip or fight. */
    private fun selectionFor(card: Card) = when (kind(card)) {
        Kind.Potion -> Selection.Green
        Kind.Weapon -> Selection.Blue
        Kind.Monster -> Selection.Purple
    }

    /**
     * The warning on a monster that can kill: red when every way of fighting
     * it does, yellow when only one of the fist and the weapon does.
     */
    private fun threat(state: RogueState, card: Card): Color? {
        if (kind(card) != Kind.Monster) return null
        val fist = value(card) >= state.health
        val weapon = state.weapon?.takeIf { canUseWeapon(state, card) }
        // Without a weapon it can use, the fist is the only way.
        val armed = if (weapon == null) fist else value(card) - value(weapon) >= state.health
        return when {
            fist && armed -> AppColors.Bad
            fist || armed -> AppColors.PaleYellow
            else -> null
        }
    }

    @Composable
    override fun Board(
        state: RogueState,
        play: (RogueState) -> Unit,
        feedback: Feedback,
        modifier: Modifier
    ) {
        val board = LocalBoardSize.current
        val ambient = LocalAmbient.current
        val context = LocalContext.current
        val icons = remember(context) { CardIcons.get(context.assets) }
        val iconColor = if (ambient) CardColors.Ambient.copy(alpha = 0.3f) else AppColors.SlotMark

        // When the last tap landed, for telling a double tap from a slow second one.
        val lastTap = remember { longArrayOf(0L, -1L) }
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current

        // The weapon button's longest label, in the buttons' text.
        val buttonStyle = MaterialTheme.typography.labelMedium
        val buttonLabelWidth = remember(buttonStyle, density) {
            with(density) {
                measurer.measure(
                    "Wpn −00",
                    buttonStyle,
                    maxLines = 1
                ).size.width.toDp()
            }
        }

        CardTable(
            modifier,
            overlay = {
                val rows = Rows(board)

                // Helper to draw a CardIcon centered and scaled within its Canvas
                fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIconPath(
                    icon: CardIcon,
                    scaleMultiplier: Float = 0.5f
                ) {
                    val baseScale = size.width / icon.viewBox
                    val finalScale = baseScale * scaleMultiplier

                    val iconWidth = icon.bounds.width * finalScale
                    val iconHeight = icon.bounds.height * finalScale
                    val translateX = (size.width - iconWidth) / 2f - (icon.bounds.left * finalScale)
                    val translateY =
                        (size.height - iconHeight) / 2f - (icon.bounds.top * finalScale)

                    withTransform({
                        translate(left = translateX, top = translateY)
                        scale(finalScale, finalScale, pivot = Offset.Zero)
                    }) {
                        drawPath(path = icon.path, color = iconColor)
                    }
                }

                // Health, on the screen's left edge.
                SideGauge(
                    fraction = state.health / MAX_HEALTH.toFloat(),
                    color = healthColour(state.health),
                    board = board,
                    boardFraction = boardFraction,
                    boardLift = boardLift,
                )

                // The middle row: the health at the left, over the cards' left edge, and the room and
                // stock at the right, past the board as far as the round screen leaves room.
                val rowEnd = RoundScreen(board, boardLift).right(
                    rows.healthY,
                    rows.healthY + HEALTH_ROW
                ) - ROW_MARGIN
                Row(
                    Modifier
                        .offset(x = rows.layout.x(0), y = rows.healthY)
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(rowEnd - rows.layout.x(0)),
                    horizontalArrangement = Arrangement.spacedBy(STATS_GAP),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Health matters most, so it's larger, in what the others leave (smaller only if it must be).
                    val health = MaterialTheme.typography.labelLarge
                    BasicText(
                        text = "HP: ${state.health}",
                        style = health.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = MaterialTheme.typography.labelMedium.fontSize,
                            maxFontSize = health.fontSize
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    SmallStat("Rm: ${state.roomNumber + state.roomsSkipped}")
                    SmallStat("Dk: ${state.deck.size}")
                }

                // Sword icon in middle of weapon pile (under cards)
                val swordX = rows.layout.x(0)
                val swordY = rows.topY
                Canvas(
                    Modifier
                        .offset(x = swordX, y = swordY)
                        .size(rows.cardWidth, rows.cardHeight)
                        .zIndex(1f),
                ) {
                    drawIconPath(icons.sword, scaleMultiplier = 0.6f)
                }

                // Dragon icon in middle of monsters zone (under cards), same size as sword icon
                val dragonX = rows.zoneX + (rows.zoneWidth - rows.cardWidth) / 2
                val dragonY = rows.topY
                Canvas(
                    Modifier
                        .offset(x = dragonX, y = dragonY)
                        .size(rows.cardWidth, rows.cardHeight)
                        .zIndex(1f),
                ) {
                    drawIconPath(icons.dragon, scaleMultiplier = 0.85f)
                }

                val picked = state.selected
                val monster = picked?.let { state.room[it] }?.takeIf { kind(it) == Kind.Monster }
                if (picked != null && monster != null && state.weapon != null && outcome(state) == null) {
                    // The weapon button, at the zone's left: over the earlier monsters, never the last, at its right.
                    val buttonWidth = min(
                        buttonLabelWidth + WEAPON_PAD * 2,
                        rows.zoneWidth - rows.cardWidth + WEAPON_PAD
                    )
                    Box(
                        Modifier
                            .offset(
                                x = rows.zoneX - WEAPON_PAD / 2,
                                y = rows.topY
                            )
                            .size(buttonWidth, rows.cardHeight)
                            .zIndex(2000f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        GameButton(
                            label = "Wpn −${maxOf(0, value(monster) - value(state.weapon))}",
                            onClick = { feedback.tryMove(fight(state, picked, true), play) },
                            width = buttonWidth,
                            height = WEAPON_BUTTON,
                            enabled = canUseWeapon(state, monster),
                            color = AppColors.ButtonPurple,
                            modifier = Modifier.background(Color.Black, CircleShape),
                            border = BorderStroke(WEAPON_EDGE, Color.Black),
                        )
                    }
                }
            },
        ) {
            val rows = Rows(width)
            val layout = rows.layout
            val weapon = state.weapon

            // Weapon placeholder pile outline & slot
            val weaponX = layout.x(0)
            val weaponY = rows.topY
            // Only the outline and the sword on black, until a weapon is equipped.
            zone(
                weaponX - ZONE_PAD,
                weaponY - ZONE_PAD,
                rows.cardWidth + ZONE_PAD * 2,
                cardWidth = rows.cardWidth,
                height = rows.cardHeight + ZONE_PAD * 2,
            )

            if (weapon != null) {
                card(
                    weapon,
                    weapon,
                    weaponX,
                    weaponY,
                    rows.cardWidth,
                    false,
                    10.toFloat()
                )
            }

            // Killed monsters zone outline stretching to right edge
            val zoneX = rows.zoneX
            val zoneWidth = rows.zoneWidth
            val zoneY = rows.topY
            zone(
                zoneX - ZONE_PAD,
                zoneY - ZONE_PAD,
                zoneWidth + ZONE_PAD * 2,
                cardWidth = rows.cardWidth,
                height = rows.cardHeight + ZONE_PAD * 2,
            )

            // The monsters the weapon has killed: the last at the zone's right, so the weapon
            // button leaves it showing; each earlier one a little further left, under it.
            val used = state.weaponUsedOn
            val step = if (used.size > 1) {
                min(rows.cardWidth * KILLED_STEP, (zoneWidth - rows.cardWidth) / (used.size - 1))
            } else {
                0.dp
            }
            val lastX = zoneX + zoneWidth - rows.cardWidth
            used.forEachIndexed { i, monsterCard ->
                val isTop = i == used.lastIndex
                val cardX = lastX - step * (used.lastIndex - i)
                card(
                    monsterCard,
                    monsterCard,
                    cardX,
                    zoneY,
                    rows.cardWidth,
                    z = i.toFloat() + 10,
                    under = !isTop,
                )
            }

            // Room cards
            state.room.forEachIndexed { i, card ->
                val x = layout.x(i)
                slot(x, rows.roomY, rows.cardWidth)
                if (card != null) {
                    card(
                        card,
                        card,
                        x,
                        rows.roomY,
                        rows.cardWidth,
                        selection = if (state.selected == i) selectionFor(card) else null,
                        dot = threat(state, card)
                    )
                }
                tap(x, rows.roomY, rows.cardWidth, layout.cardHeight) {
                    val now = SystemClock.uptimeMillis()
                    val quick = lastTap[1] == i.toLong() && now - lastTap[0] <= DOUBLE_TAP_MS
                    lastTap[0] = now
                    lastTap[1] = i.toLong()
                    val choice =
                        card != null && kind(card) == Kind.Monster && canUseWeapon(state, card)
                    val only = actions(state).singleOrNull()
                        ?.takeIf { state.selected == i && quick && !choice }
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

@WearGamePreview
@Composable
private fun RogueRunPreview() = GamePreview(RogueRun)