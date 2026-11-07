package com.xern.xrnchips.domain

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Seats run clockwise from the top of the table: 0 top, 1 to 3 down the right, 4 bottom, 5 to 7 up the left. */
const val SEATS = 8
const val NAME_LENGTH = 8
const val MAX_CHIPS = 999_999
const val MAX_DURATION = 999
const val MAX_LEVELS = 20

/** What a level's length counts, and the length a level gets until someone sets another. */
@Serializable
enum class DurationUnit(val usual: Int) { HANDS(4), MINUTES(15) }

/** How large a bet or raise may be: anything up to the stack, or no more than the pot. */
@Serializable
enum class Limit { NONE, POT }

/** Who posts the ante: every player, or the big blind alone for the whole table. */
@Serializable
enum class AnteStyle { EVERYONE, BIG_BLIND }

/** One step of the blind structure. The duration counts hands or minutes; the last level never ends. */
@Serializable
data class BlindLevel(val big: Int = 10, val small: Int = 5, val ante: Int = 0, val duration: Int = DurationUnit.MINUTES.usual)

@Serializable
data class SetupPlayer(val seat: Int, val name: String)

/**
 * What the Play screen edits. Fields correct themselves the way the screen needs: a blind of zero is
 * refused, and the small blind never passes the big one.
 */
@Serializable
data class GameSetup(
    val startingChips: Int = 1000,
    val levels: List<BlindLevel> = listOf(BlindLevel()),
    val unit: DurationUnit = DurationUnit.MINUTES,
    val players: List<SetupPlayer> = emptyList(),
    val limit: Limit = Limit.NONE,
    val anteStyle: AnteStyle = AnteStyle.EVERYONE,
) {
    val isDefault: Boolean get() = this == GameSetup()

    fun playerAt(seat: Int): SetupPlayer? = players.firstOrNull { it.seat == seat }

    fun nameTaken(name: String, exceptSeat: Int = -1): Boolean =
        players.any { it.seat != exceptSeat && it.name.equals(name.trim(), ignoreCase = true) }

    fun withStartingChips(value: Int?): GameSetup =
        if (value == null || value < 1) this else copy(startingChips = min(value, MAX_CHIPS))

    /** Lowering the big blind under the small one pulls the small one down with it. */
    fun withBig(index: Int, value: Int?): GameSetup {
        if (value == null || value < 1) return this
        val big = min(value, MAX_CHIPS)
        return level(index) { it.copy(big = big, small = min(it.small, big)) }
    }

    /** Raising the small blind over the big one pushes the big one up with it. */
    fun withSmall(index: Int, value: Int?): GameSetup {
        if (value == null || value < 1) return this
        val small = min(value, MAX_CHIPS)
        return level(index) { it.copy(small = small, big = max(it.big, small)) }
    }

    fun withAnte(index: Int, value: Int?): GameSetup =
        level(index) { it.copy(ante = (value ?: 0).coerceIn(0, MAX_CHIPS)) }

    fun withDuration(index: Int, value: Int?): GameSetup =
        if (value == null || value < 1) this else level(index) { it.copy(duration = min(value, MAX_DURATION)) }

    /**
     * Switching between hands and minutes gives every level the usual length for the new unit:
     * a number of minutes means nothing as a number of hands.
     */
    fun withUnit(unit: DurationUnit): GameSetup =
        if (unit == this.unit) this else copy(unit = unit, levels = levels.map { it.copy(duration = unit.usual) })

    /**
     * The new level doubles the last one. The level it closes takes its length from the one before,
     * or the usual length for the unit when it was the only level and so never had one on show.
     */
    fun addLevel(): GameSetup {
        if (levels.size >= MAX_LEVELS) return this
        val last = levels.last()
        val duration = if (levels.size >= 2) levels[levels.size - 2].duration else unit.usual
        val next = BlindLevel(
            big = min(last.big * 2, MAX_CHIPS),
            small = min(last.small * 2, MAX_CHIPS),
            ante = min(last.ante * 2, MAX_CHIPS),
            duration = duration,
        )
        return copy(levels = levels.dropLast(1) + last.copy(duration = duration) + next)
    }

    fun removeLevel(index: Int): GameSetup =
        if (levels.size <= 1) this else copy(levels = levels.filterIndexed { i, _ -> i != index })

    fun withPlayer(seat: Int, name: String): GameSetup =
        copy(players = (players.filter { it.seat != seat } + SetupPlayer(seat, name.trim())).sortedBy { it.seat })

    fun withoutPlayer(seat: Int): GameSetup = copy(players = players.filter { it.seat != seat })

    private fun level(index: Int, change: (BlindLevel) -> BlindLevel): GameSetup =
        copy(levels = levels.mapIndexed { i, level -> if (i == index) change(level) else level })
}

@Serializable
enum class Street { PREFLOP, FLOP, TURN, RIVER }

/** The label under a player's stack: the blind they posted or the last thing they did this street. */
@Serializable
enum class Act { NONE, SB, BB, CHECK, CALL, BET, RAISE, FOLD }

@Serializable
enum class Phase {
    /** Waiting for the first dealer to be picked. */
    DEALER,

    /** [GameState.active] has to act. */
    BETTING,

    /** The betting round just closed. [advance] deals the next street, opens the showdown or pays a lone survivor. */
    ROUND_END,

    /** Waiting for the winners of each pot. */
    SHOWDOWN,

    /** The pot has been paid. [advance] starts the next hand, asks the broke what they want, or ends the game. */
    HAND_END,

    /** Someone is out of chips: waiting for each of them to buy back in or leave. */
    REBUY,

    GAME_OVER,
}

@Serializable
data class Player(
    val seat: Int,
    val name: String,
    val chips: Int,
    /** Dealt into the current hand. Someone who bought in mid-hand waits for the next one. */
    val inHand: Boolean = false,
    val folded: Boolean = false,
    /** Chips put in on this street, without the ante. */
    val bet: Int = 0,
    /** The ante posted this hand. It sits in front of the player with the bet until the first street closes. */
    val ante: Int = 0,
    /** Everything put in this hand. */
    val total: Int = 0,
    /** The ante as paid this hand. It belongs to the pot as a whole, not to the player's share of a side pot. */
    val dead: Int = 0,
    /** Has acted on this street. */
    val acted: Boolean = false,
    /** The bet that stood when the player last acted, to tell whether they have been raised in full since. */
    val faced: Int = 0,
    val last: Act = Act.NONE,
    val leaving: Boolean = false,
) {
    /** Still contesting the pot. */
    val live: Boolean get() = inHand && !folded

    /** Live and not all in. */
    val canAct: Boolean get() = live && chips > 0

    /** Out of chips and not yet on the way out: has to buy back in or leave before the next deal. */
    val broke: Boolean get() = chips <= 0 && !leaving

    /** The chips shown in front of the player. */
    val shown: Int get() = bet + ante
}

/** A pot and the seats that can win it, shortest contribution first. */
@Serializable
data class Pot(val amount: Int, val eligible: List<Int>)

/** One person's chips in and out over the whole game. It stays when they leave, and is theirs again if they come back. */
@Serializable
data class Account(
    val name: String,
    /** Chips bought: the starting stack, top-ups and rebuys. */
    val paidIn: Int = 0,
    /** Chips carried away from the table on leaving. */
    val cashedOut: Int = 0,
)

@Serializable
data class GameState(
    /** The setup the game began with. Restart goes back to it, and its levels are the blind structure. */
    val setup: GameSetup,
    val players: List<Player>,
    val phase: Phase = Phase.DEALER,
    val hand: Int = 0,
    val street: Street = Street.PREFLOP,
    val dealer: Int = -1,
    val active: Int = -1,
    val levelIndex: Int = 0,
    /** Hands dealt at the current level, counting the one in play. */
    val handsInLevel: Int = 0,
    /** Time left at the current level when levels run on the clock. */
    val secondsLeft: Int = 0,
    val paused: Boolean = false,
    /** The street bet everyone has to match. */
    val currentBet: Int = 0,
    /** The smallest amount a raise may add. */
    val minRaise: Int = 0,
    val pots: List<Pot> = emptyList(),
    /** This hand opened a new blind level. */
    val levelUp: Boolean = false,
    val winner: Int = -1,
    /** Every chip bought and carried off, by name. */
    val ledger: List<Account> = emptyList(),
) {
    val level: BlindLevel get() = setup.levels[levelIndex.coerceIn(0, setup.levels.lastIndex)]
    val nextLevel: BlindLevel? get() = setup.levels.getOrNull(levelIndex + 1)

    /** Every chip in the middle, bets of the running street included. */
    val pot: Int get() = players.sumOf { it.total }

    val activePlayer: Player? get() = player(active)

    fun player(seat: Int): Player? = players.firstOrNull { it.seat == seat }
}

/** 12345 becomes 12,345 whatever the phone's locale. */
fun Int.grouped(): String = String.format(Locale.US, "%,d", this)
