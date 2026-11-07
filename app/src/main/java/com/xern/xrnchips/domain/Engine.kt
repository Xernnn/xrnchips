package com.xern.xrnchips.domain

import kotlin.math.max
import kotlin.math.min

/*
 * The rules of the table as pure functions over GameState. Nothing here waits or draws: after a
 * player acts the state is either BETTING again or ROUND_END, and the caller steps through
 * ROUND_END and HAND_END with advance() at whatever pace it wants to show them.
 */

fun newGame(setup: GameSetup): GameState {
    val seated = setup.players.sortedBy { it.seat }
    return GameState(
        setup = setup,
        players = seated.map { Player(it.seat, it.name, setup.startingChips) },
        secondsLeft = setup.levels[0].duration * 60,
        ledger = seated.map { Account(it.name, paidIn = setup.startingChips) },
    )
}

/** Picks the dealer of the first hand and deals it. */
fun GameState.withDealer(seat: Int): GameState =
    if (phase != Phase.DEALER || player(seat) == null) this else copy(dealer = seat).startHand(first = true)

fun GameState.restarted(): GameState = newGame(setup)

// ---- Acting ----

fun GameState.fold(): GameState {
    val p = actor() ?: return this
    return update(p.seat) { it.copy(folded = true, acted = true, last = Act.FOLD) }.turnAfter(p.seat)
}

/** Checks when there is nothing to call. Otherwise calls, all in if the stack is short. */
fun GameState.checkOrCall(): GameState {
    val p = actor() ?: return this
    val need = max(0, currentBet - p.bet)
    if (need == 0) {
        return update(p.seat) { it.copy(acted = true, faced = currentBet, last = Act.CHECK) }.turnAfter(p.seat)
    }
    val pay = min(need, p.chips)
    return update(p.seat) {
        it.copy(
            chips = it.chips - pay, bet = it.bet + pay, total = it.total + pay,
            acted = true, faced = currentBet, last = Act.CALL,
        )
    }.turnAfter(p.seat)
}

/**
 * Bets or raises. [amount] is what goes in on top of the call. It has to be at least the last full
 * bet or raise of the street unless it is all in. An all-in for less still raises the bet, but
 * it does not move the minimum, and players who have already acted may only call it.
 */
fun GameState.raise(amount: Int): GameState {
    val p = actor() ?: return this
    val spec = raiseSpec() ?: return this
    val add = if (amount >= spec.stack) spec.max else amount.coerceIn(spec.min, spec.max)
    val pay = spec.toCall + add
    val bet = p.bet + pay
    val label = if (currentBet == 0) Act.BET else Act.RAISE
    return update(p.seat) {
        it.copy(chips = it.chips - pay, bet = bet, total = it.total + pay, acted = true, faced = bet, last = label)
    }.copy(currentBet = bet, minRaise = if (add >= minRaise) add else minRaise).turnAfter(p.seat)
}

/** What the acting player may do, for the buttons under the table. */
data class Options(
    val check: Boolean,
    /** Chips a call costs: the whole stack when it cannot cover the bet. */
    val call: Int,
    val allInCall: Boolean,
    val canRaise: Boolean,
    /** Nothing has been bet on this street, so a raise reads "Bet". */
    val firstBet: Boolean,
)

fun GameState.options(): Options? {
    val p = actor() ?: return null
    val need = max(0, currentBet - p.bet)
    return Options(
        check = need == 0,
        call = min(need, p.chips),
        allInCall = need > 0 && p.chips <= need,
        canRaise = mayRaise(p),
        firstBet = currentBet == 0,
    )
}

/** The limits of the raise dialog. Every amount is what goes in on top of the call. */
data class RaiseSpec(
    val toCall: Int,
    val min: Int,
    /** The most that may go in: the stack, or the pot when the game is pot limit. */
    val max: Int,
    /** What is left of the stack after the call. Putting all of it in is all in. */
    val stack: Int,
    val pot: Int,
    val big: Int,
    val small: Int,
    /** Preflop with nobody having raised: the shortcuts are multiples of the big blind. */
    val opening: Boolean,
    val firstBet: Boolean,
    /** There is only one size to choose: the stack cannot cover a full raise, or the limit allows no more. */
    val fixed: Boolean,
) {
    fun clamp(value: Int): Int = value.coerceIn(min, max)

    /** A share of the pot as it stands once the call is in, rounded half up. */
    fun potShare(numerator: Int, denominator: Int): Int =
        ((pot + toCall) * numerator * 2 + denominator) / (denominator * 2)

    /** [tenths] tenths of a big blind, rounded half up: 25 is two and a half big blinds. */
    fun bigBlinds(tenths: Int): Int = (big * tenths + 5) / 10
}

fun GameState.raiseSpec(): RaiseSpec? {
    val p = actor() ?: return null
    if (!mayRaise(p)) return null
    val need = max(0, currentBet - p.bet)
    val stack = p.chips - need
    val most = if (setup.limit == Limit.POT) min(stack, pot + need) else stack
    return RaiseSpec(
        toCall = need,
        min = min(minRaise, most),
        max = most,
        stack = stack,
        pot = pot,
        big = level.big,
        small = level.small,
        opening = street == Street.PREFLOP && currentBet == level.big,
        firstBet = currentBet == 0,
        fixed = most <= minRaise,
    )
}

// ---- Moving the hand along ----

/** One automatic step: out of ROUND_END or HAND_END. Any other phase waits for the table. */
fun GameState.advance(): GameState = when (phase) {
    Phase.ROUND_END -> {
        val live = players.filter { it.live }
        when {
            live.size == 1 -> payAll(live[0].seat)
            street == Street.RIVER -> copy(
                phase = Phase.SHOWDOWN,
                active = -1,
                players = players.map { it.copy(bet = 0, ante = 0) },
                pots = computePots(players),
            )
            else -> nextStreet()
        }
    }
    Phase.HAND_END -> if (players.any { it.broke }) copy(phase = Phase.REBUY, active = -1) else nextHand()
    else -> this
}

/** Runs every automatic step. For tests and for loading a save that stopped between steps. */
fun GameState.settled(): GameState {
    var state = this
    while (state.phase == Phase.ROUND_END || state.phase == Phase.HAND_END) state = state.advance()
    return state
}

/**
 * Pays each pot to its winners. [winners] lines up with [GameState.pots]; a pot left without a
 * winner is shared by everyone who could win it.
 */
fun GameState.award(winners: List<List<Int>>): GameState {
    if (phase != Phase.SHOWDOWN) return this
    val gain = IntArray(SEATS)
    pots.forEachIndexed { i, pot ->
        val chosen = winners.getOrNull(i).orEmpty().filter { it in pot.eligible }.ifEmpty { pot.eligible }
        split(pot.amount, chosen, dealer).forEach { (seat, chips) -> gain[seat] += chips }
    }
    return copy(
        phase = Phase.HAND_END,
        pots = emptyList(),
        players = players.map { it.copy(chips = it.chips + gain[it.seat], total = 0, bet = 0, ante = 0, dead = 0) },
    )
}

/**
 * Side pots from what each player bet. Antes are not bets: they go into the first pot whoever paid
 * them, so a big blind who antes for the table does not get a side pot of their own for it. A
 * folded player's chips stay in the pots without a claim to them. Chips nobody could match come
 * back as a pot with a single name on it.
 */
fun computePots(players: List<Player>): List<Pot> {
    val paid = players.filter { it.inHand && it.total > 0 }
    val live = paid.filter { !it.folded }
    fun bets(p: Player) = p.total - p.dead
    val pots = mutableListOf<Pot>()
    var floor = 0
    var antes = paid.sumOf { it.dead }
    for (ceiling in live.map(::bets).distinct().sorted()) {
        val amount = antes + paid.sumOf { min(bets(it), ceiling) - min(bets(it), floor) }
        antes = 0
        val eligible = live.filter { bets(it) >= ceiling }.sortedWith(compareBy({ bets(it) }, { it.seat })).map { it.seat }
        if (amount > 0) pots += Pot(amount, eligible)
        floor = ceiling
    }
    val above = paid.sumOf { max(0, bets(it) - floor) }
    if (above > 0 && pots.isNotEmpty()) pots[pots.lastIndex] = pots.last().let { it.copy(amount = it.amount + above) }
    return pots
}

/** Equal shares. Chips that do not divide go one each to the winners nearest the dealer's left. */
fun split(amount: Int, winners: List<Int>, dealer: Int): Map<Int, Int> {
    if (winners.isEmpty()) return emptyMap()
    val order = winners.distinct().sortedBy { clockwise(dealer, it) }
    val share = amount / order.size
    val odd = amount % order.size
    return order.mapIndexed { i, seat -> seat to share + if (i < odd) 1 else 0 }.toMap()
}

// ---- Out of chips ----

/** A player who went broke buys back in. Once nobody is left to decide, the next hand is dealt. */
fun GameState.rebuy(seat: Int, chips: Int): GameState {
    val p = player(seat) ?: return this
    if (phase != Phase.REBUY || !p.broke) return this
    val amount = chips.coerceIn(1, MAX_CHIPS)
    return update(seat) { it.copy(chips = amount) }.copy(ledger = ledger.credit(p.name, paidIn = amount)).afterRebuys()
}

/** A player who went broke leaves instead. */
fun GameState.giveUp(seat: Int): GameState {
    val p = player(seat) ?: return this
    if (phase != Phase.REBUY || !p.broke) return this
    return update(seat) { it.copy(leaving = true) }.afterRebuys()
}

private fun GameState.afterRebuys(): GameState = if (players.any { it.broke }) this else nextHand()

// ---- Managing the table mid-game ----

fun GameState.seatFree(seat: Int): Boolean = players.none { it.seat == seat }

fun GameState.nameTaken(name: String): Boolean = players.any { it.name.equals(name.trim(), ignoreCase = true) }

/** Seats a new player. They are dealt in from the next hand. Someone who left earlier keeps their old account. */
fun GameState.addPlayer(seat: Int, name: String, chips: Int): GameState {
    if (!seatFree(seat) || seat !in 0 until SEATS) return this
    val amount = chips.coerceIn(1, MAX_CHIPS)
    val clean = name.trim()
    return copy(
        players = (players + Player(seat, clean, amount)).sortedBy { it.seat },
        ledger = ledger.credit(clean, paidIn = amount),
    )
}

/** Chips can be topped up between hands or once the player is out of the hand. */
fun GameState.canAddChips(seat: Int): Boolean = player(seat)?.let { !it.live || phase == Phase.HAND_END } ?: false

fun GameState.addChips(seat: Int, amount: Int): GameState {
    val p = player(seat) ?: return this
    if (!canAddChips(seat) || amount < 1) return this
    val chips = min(amount, MAX_CHIPS)
    return update(seat) { it.copy(chips = it.chips + chips) }.copy(ledger = ledger.credit(p.name, paidIn = chips))
}

/** A table needs two players who are staying. */
fun GameState.canLeave(seat: Int): Boolean =
    player(seat)?.leaving == false && players.count { !it.leaving && it.seat != seat } >= 2

/** Marks a player as leaving. They finish the hand and their seat empties before the next deal. */
fun GameState.leave(seat: Int): GameState = if (!canLeave(seat)) this else update(seat) { it.copy(leaving = true) }

// ---- Blind levels ----

/** What is left of the current level, for the table. */
sealed interface Clock {
    /** The last level: it never ends. */
    data object Final : Clock

    /** The level is used up and the blinds rise with the next deal. */
    data object NextHand : Clock

    data class Hands(val left: Int) : Clock

    data class Time(val seconds: Int, val paused: Boolean) : Clock
}

fun GameState.clock(): Clock = when {
    nextLevel == null -> Clock.Final
    // Before the first deal the table already counts the hand about to be dealt.
    setup.unit == DurationUnit.HANDS -> (level.duration - max(handsInLevel, 1)).let { if (it <= 0) Clock.NextHand else Clock.Hands(it) }
    secondsLeft <= 0 -> Clock.NextHand
    else -> Clock.Time(secondsLeft, paused)
}

/** One second of the level clock. It only runs while a hand is on the table. */
fun GameState.tick(): GameState {
    val running = setup.unit == DurationUnit.MINUTES && !paused && nextLevel != null && secondsLeft > 0 &&
        phase != Phase.DEALER && phase != Phase.GAME_OVER
    return if (running) copy(secondsLeft = secondsLeft - 1) else this
}

fun GameState.togglePause(): GameState = if (clock() is Clock.Time) copy(paused = !paused) else this

// ---- Internals ----

private fun GameState.actor(): Player? = if (phase == Phase.BETTING) activePlayer else null

/**
 * A raise needs chips beyond the call. A player who has already acted on this street may raise
 * again only if the bet has since gone up by a full raise, in one step or several: short all-ins
 * that add up to less leave them a call or a fold.
 */
private fun GameState.mayRaise(p: Player): Boolean =
    p.chips > max(0, currentBet - p.bet) && (!p.acted || currentBet - p.faced >= minRaise)

private fun GameState.update(seat: Int, change: (Player) -> Player): GameState =
    copy(players = players.map { if (it.seat == seat) change(it) else it })

/** Adds to the account under [name], opening one if this is a new face. */
private fun List<Account>.credit(name: String, paidIn: Int = 0, cashedOut: Int = 0): List<Account> {
    val index = indexOfFirst { it.name.equals(name, ignoreCase = true) }
    if (index < 0) return this + Account(name, paidIn, cashedOut)
    return mapIndexed { i, account ->
        if (i == index) account.copy(paidIn = account.paidIn + paidIn, cashedOut = account.cashedOut + cashedOut) else account
    }
}

/** Steps from [from] to [seat] going clockwise, never zero: a seat is furthest from itself. */
private fun clockwise(from: Int, seat: Int): Int = (seat - from - 1 + SEATS * 2) % SEATS

private fun seatAfter(seats: List<Int>, seat: Int): Int = seats.minBy { clockwise(seat, it) }

/** Hands the turn to the next player who still owes an action, or closes the betting round. */
private fun GameState.turnAfter(seat: Int): GameState {
    if (players.count { it.live } <= 1) return copy(phase = Phase.ROUND_END, active = -1)
    val next = players.filter { it.canAct && (!it.acted || it.bet < currentBet) }.minByOrNull { clockwise(seat, it.seat) }
    return if (next == null) copy(phase = Phase.ROUND_END, active = -1) else copy(phase = Phase.BETTING, active = next.seat)
}

/**
 * Deals the next street. With fewer than two players able to bet there is nothing to decide, so
 * the state stays at ROUND_END and the board runs out one street per step.
 */
private fun GameState.nextStreet(): GameState {
    val runout = players.count { it.canAct } < 2
    val dealt = copy(
        players = players.map { it.copy(bet = 0, ante = 0, acted = false, faced = 0, last = if (runout) it.last else Act.NONE) },
        street = Street.entries[street.ordinal + 1],
        currentBet = 0,
        minRaise = level.big,
        active = -1,
    )
    return if (runout) dealt.copy(phase = Phase.ROUND_END) else dealt.turnAfter(dealer)
}

private fun GameState.payAll(seat: Int): GameState {
    val pot = pot
    return copy(
        phase = Phase.HAND_END,
        active = -1,
        pots = emptyList(),
        players = players.map {
            it.copy(chips = it.chips + if (it.seat == seat) pot else 0, total = 0, bet = 0, ante = 0, dead = 0)
        },
    )
}

private fun GameState.nextHand(): GameState {
    val staying = players.filter { it.chips > 0 && !it.leaving }
    if (staying.size < 2) {
        val winner = staying.firstOrNull() ?: players.maxByOrNull { it.chips }
        return copy(phase = Phase.GAME_OVER, active = -1, winner = winner?.seat ?: -1)
    }
    return startHand(first = false)
}

/**
 * Clears the seats of anyone on the way out and books the chips they take with them, moves the
 * button, raises the blinds when the level is used up, and posts antes and blinds. Heads-up the
 * dealer posts the small blind.
 */
private fun GameState.startHand(first: Boolean): GameState {
    val gone = players.filter { it.chips <= 0 || it.leaving }
    val seated = players - gone.toSet()
    val book = gone.fold(ledger) { accounts, p -> accounts.credit(p.name, cashedOut = p.chips) }
    val seats = seated.map { it.seat }
    val button = if (first) dealer else seatAfter(seats, dealer)

    var index = levelIndex
    var hands = handsInLevel
    var seconds = secondsLeft
    var raised = false
    if (first) {
        index = 0
        hands = 0
        seconds = setup.levels[0].duration * 60
    } else if (index < setup.levels.lastIndex) {
        val used = if (setup.unit == DurationUnit.HANDS) hands >= setup.levels[index].duration else seconds <= 0
        if (used) {
            index++
            hands = 0
            seconds = setup.levels[index].duration * 60
            raised = true
        }
    }
    val blinds = setup.levels[index]

    val smallSeat = if (seats.size == 2) button else seatAfter(seats, button)
    val bigSeat = seatAfter(seats, smallSeat)
    val everyoneAntes = setup.anteStyle == AnteStyle.EVERYONE
    val dealt = seated.map { p ->
        // The ante comes out first, so a stack too short for both is all in on part of its blind.
        val ante = if (everyoneAntes || p.seat == bigSeat) min(blinds.ante, p.chips) else 0
        val left = p.chips - ante
        val (bet, label) = when (p.seat) {
            smallSeat -> min(blinds.small, left) to Act.SB
            bigSeat -> min(blinds.big, left) to Act.BB
            else -> 0 to Act.NONE
        }
        p.copy(
            chips = left - bet, inHand = true, folded = false, bet = bet, ante = ante, dead = ante, total = ante + bet,
            acted = false, faced = 0, last = label,
        )
    }
    return copy(
        players = dealt,
        hand = hand + 1,
        street = Street.PREFLOP,
        dealer = button,
        levelIndex = index,
        handsInLevel = hands + 1,
        secondsLeft = seconds,
        currentBet = blinds.big,
        minRaise = blinds.big,
        pots = emptyList(),
        levelUp = raised,
        winner = -1,
        ledger = book,
    ).turnAfter(bigSeat)
}
