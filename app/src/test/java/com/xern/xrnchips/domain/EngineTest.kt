package com.xern.xrnchips.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session below was played on the reference app with the same seats, stacks and actions, and
 * the numbers asserted were read off its screen. Where the app has since chosen to do something
 * else, the comment at that step says so: a player out of chips is asked whether to buy back in,
 * and an all-in smaller than a full raise no longer reopens the betting.
 */
class EngineTest {
    private val setup = GameSetup(
        startingChips = 1000,
        levels = listOf(
            BlindLevel(10, 5, ante = 2, duration = 2),
            BlindLevel(20, 10, duration = 2),
            BlindLevel(40, 20, duration = 2),
            BlindLevel(80, 40, duration = 2),
        ),
        unit = DurationUnit.HANDS,
        players = listOf(SetupPlayer(0, "Ann"), SetupPlayer(2, "Bob"), SetupPlayer(4, "Cat"), SetupPlayer(7, "Dan")),
    )

    private fun GameState.chips(name: String) = players.first { it.name == name }.chips
    private fun GameState.shown(name: String) = players.first { it.name == name }.shown
    private fun GameState.last(name: String) = players.first { it.name == name }.last
    private fun GameState.turn() = activePlayer?.name
    private fun GameState.toCall() = options()!!.call
    private fun GameState.names(pot: Int) = pots[pot].eligible.map { seat -> player(seat)!!.name }
    private fun GameState.seatOf(name: String) = players.first { it.name == name }.seat

    /** Chips are neither made nor lost: what everyone is up and down always cancels out. */
    private fun assertSquare(state: GameState) = assertEquals(0, state.standings().sumOf { it.net })

    /** The whole session, hand by hand. Each step returns the state the next one starts from. */
    @Test
    fun sessionMatchesTheReference() {
        var s = newGame(setup)
        assertEquals(Phase.DEALER, s.phase)
        s = s.withDealer(0)

        // Hand 1: antes and blinds are posted, the player after the big blind opens.
        assertEquals(998, s.chips("Ann"))
        assertEquals(998, s.chips("Dan"))
        assertEquals(993, s.chips("Bob"))
        assertEquals(988, s.chips("Cat"))
        assertEquals(Act.SB, s.last("Bob"))
        assertEquals(Act.BB, s.last("Cat"))
        assertEquals(7, s.shown("Bob"))
        assertEquals(12, s.shown("Cat"))
        assertEquals(23, s.pot)
        assertEquals("Dan", s.turn())
        assertEquals(10, s.toCall())
        assertEquals(Clock.Hands(1), s.clock())

        // The opening raise dialog: on top of the call, in steps of a big blind.
        s.raiseSpec()!!.let {
            assertEquals(10, it.toCall)
            assertEquals(10, it.min)
            assertEquals(988, it.max)
            assertTrue(it.opening)
            assertEquals(20, it.bigBlinds(20))
            assertEquals(25, it.bigBlinds(25))
            assertEquals(30, it.bigBlinds(30))
        }

        s = s.checkOrCall()
        assertEquals(988, s.chips("Dan"))
        assertEquals(12, s.shown("Dan"))
        assertEquals(Act.CALL, s.last("Dan"))
        assertEquals(33, s.pot)
        assertEquals("Ann", s.turn())

        s = s.raise(30)
        assertEquals(958, s.chips("Ann"))
        assertEquals(42, s.shown("Ann"))
        assertEquals(Act.RAISE, s.last("Ann"))
        assertEquals(73, s.pot)
        assertEquals("Bob", s.turn())
        assertEquals(35, s.toCall())

        // Bob asks to leave and Eve buys in: both take effect with the next deal.
        s = s.leave(s.seatOf("Bob"))
        assertTrue(s.player(2)!!.leaving)
        assertFalse(s.canLeave(2))
        s = s.addPlayer(1, "Eve", 500)
        assertFalse(s.player(1)!!.inHand)
        assertFalse(s.canAddChips(2))

        s = s.fold()
        assertEquals(Act.FOLD, s.last("Bob"))
        assertEquals("Cat", s.turn())
        assertEquals(30, s.toCall())

        // Facing a raise the shortcuts are shares of the pot once the call is in.
        s.raiseSpec()!!.let {
            assertEquals(30, it.min)
            assertEquals(958, it.max)
            assertFalse(it.opening)
            assertEquals(34, it.potShare(1, 3))
            assertEquals(52, it.potShare(1, 2))
            assertEquals(77, it.potShare(3, 4))
            assertEquals(103, it.potShare(1, 1))
        }

        s = s.raise(30)
        assertEquals(928, s.chips("Cat"))
        assertEquals(72, s.shown("Cat"))
        assertEquals(133, s.pot)
        assertEquals("Dan", s.turn())
        assertEquals(60, s.toCall())

        s = s.checkOrCall()
        assertEquals(193, s.pot)
        assertEquals("Ann", s.turn())
        assertEquals(30, s.toCall())
        s = s.checkOrCall()
        assertEquals(Phase.ROUND_END, s.phase)
        assertEquals(223, s.pot)

        s = s.advance()
        assertEquals(Street.FLOP, s.street)
        assertEquals("Cat", s.turn())
        assertEquals(0, s.shown("Cat"))
        assertEquals(Act.NONE, s.last("Bob"))
        assertTrue(s.options()!!.check)
        s.raiseSpec()!!.let {
            assertEquals(10, it.min)
            assertTrue(it.firstBet)
            assertEquals(74, it.potShare(1, 3))
            assertEquals(112, it.potShare(1, 2))
            assertEquals(167, it.potShare(3, 4))
            assertEquals(223, it.potShare(1, 1))
        }
        val beforeBet = s
        s = s.raise(112)
        assertEquals(Act.BET, s.last("Cat"))
        assertEquals(816, s.chips("Cat"))
        assertEquals(335, s.pot)
        assertEquals(112, s.toCall())
        s = beforeBet // undone on the reference app

        repeat(3) { s = s.checkOrCall() }
        s = s.advance()
        assertEquals(Street.TURN, s.street)
        repeat(3) { s = s.checkOrCall() }
        s = s.advance()
        assertEquals(Street.RIVER, s.street)
        repeat(3) { s = s.checkOrCall() }
        assertEquals(Act.CHECK, s.last("Ann"))
        s = s.advance()
        assertEquals(Phase.SHOWDOWN, s.phase)
        assertEquals(listOf(223), s.pots.map { it.amount })
        assertEquals(listOf("Ann", "Cat", "Dan"), s.names(0))

        // A split of 223: the odd chip goes to the winner nearest the dealer's left.
        s = s.award(listOf(listOf(0, 4)))
        assertEquals(1039, s.chips("Ann"))
        assertEquals(1040, s.chips("Cat"))
        assertEquals(928, s.chips("Dan"))

        // Hand 2: Bob is gone, Eve is in, the button moved to her.
        s = s.advance()
        assertEquals(2, s.hand)
        assertEquals(listOf("Ann", "Eve", "Cat", "Dan"), s.players.map { it.name })
        assertEquals(1, s.dealer)
        assertEquals(Act.SB, s.last("Cat"))
        assertEquals(Act.BB, s.last("Dan"))
        assertEquals(1037, s.chips("Ann"))
        assertEquals(498, s.chips("Eve"))
        assertEquals(1033, s.chips("Cat"))
        assertEquals(916, s.chips("Dan"))
        assertEquals(23, s.pot)
        assertEquals("Ann", s.turn())
        assertEquals(Clock.NextHand, s.clock())

        // Ann shoves. Stacks that cannot cover the bet only get "All in" and "Fold".
        s = s.raise(s.raiseSpec()!!.max)
        assertEquals(0, s.chips("Ann"))
        assertEquals(1039, s.shown("Ann"))
        assertEquals(1060, s.pot)
        assertEquals("Eve", s.turn())
        s.options()!!.let {
            assertTrue(it.allInCall)
            assertEquals(498, it.call)
            assertFalse(it.canRaise)
        }
        assertNull(s.raiseSpec())
        s = s.checkOrCall()
        assertEquals(Act.CALL, s.last("Eve"))
        assertEquals(500, s.shown("Eve"))
        assertEquals(1558, s.pot)

        // Cat covers the call with one chip to spare: the only raise left is that chip.
        assertEquals("Cat", s.turn())
        assertEquals(1032, s.toCall())
        s.raiseSpec()!!.let {
            assertTrue(it.fixed)
            assertEquals(1, it.min)
            assertEquals(1, it.max)
        }
        s = s.checkOrCall()
        assertEquals(1, s.chips("Cat"))
        assertEquals(2590, s.pot)
        assertEquals("Dan", s.turn())
        assertEquals(916, s.toCall())
        s = s.checkOrCall()
        assertEquals(3506, s.pot)

        // Nobody is left to bet, so the board runs out and the pots are laid out.
        s = s.settled()
        assertEquals(Phase.SHOWDOWN, s.phase)
        assertEquals(Street.RIVER, s.street)
        assertEquals(listOf(2000, 1284, 222), s.pots.map { it.amount })
        assertEquals(listOf("Eve", "Dan", "Ann", "Cat"), s.names(0))
        assertEquals(listOf("Dan", "Ann", "Cat"), s.names(1))
        assertEquals(listOf("Ann", "Cat"), s.names(2))
        s = s.award(listOf(listOf(1), listOf(0), listOf(0)))
        assertEquals(2000, s.chips("Eve"))
        assertEquals(1506, s.chips("Ann"))
        assertEquals(1, s.chips("Cat"))
        assertEquals(0, s.chips("Dan"))

        // Dan is out of chips. The reference drops him without a word; here the table is asked, and he leaves.
        s = s.advance()
        assertEquals(Phase.REBUY, s.phase)
        assertTrue(s.player(7)!!.broke)
        assertSquare(s)
        s = s.giveUp(7)

        // Hand 3: Dan is gone, and the two hands of the first level are used up.
        assertEquals(listOf("Ann", "Eve", "Cat"), s.players.map { it.name })
        assertTrue(s.levelUp)
        assertEquals(BlindLevel(20, 10, duration = 2), s.level)
        assertEquals(4, s.dealer)
        assertEquals(1496, s.chips("Ann"))
        assertEquals(1980, s.chips("Eve"))
        assertEquals(30, s.pot)
        assertEquals("Cat", s.turn())
        assertEquals(1, s.toCall())
        assertEquals(Clock.Hands(1), s.clock())
        s = s.checkOrCall()
        assertEquals("Ann", s.turn())
        assertEquals(10, s.toCall())
        s.raiseSpec()!!.let {
            assertEquals(20, it.min)
            assertEquals(1486, it.max)
            assertTrue(it.opening)
        }
        s = s.fold()

        // The big blind still gets her option, then the unmatched chips come back as their own pot.
        assertEquals("Eve", s.turn())
        assertTrue(s.options()!!.check)
        s = s.checkOrCall().settled()
        assertEquals(Phase.SHOWDOWN, s.phase)
        assertEquals(listOf(3, 28), s.pots.map { it.amount })
        assertEquals(listOf("Cat", "Eve"), s.names(0))
        assertEquals(listOf("Eve"), s.names(1))
        s = s.award(listOf(listOf(1), listOf(1)))
        assertEquals(2011, s.chips("Eve"))

        // Hand 4, heads-up once Cat has left: the dealer posts the small blind and acts first.
        s = s.advance().giveUp(4)
        assertEquals(listOf("Ann", "Eve"), s.players.map { it.name })
        assertEquals(0, s.dealer)
        assertEquals(Act.SB, s.last("Ann"))
        assertEquals(Act.BB, s.last("Eve"))
        assertEquals(1486, s.chips("Ann"))
        assertEquals(1991, s.chips("Eve"))
        assertEquals("Ann", s.turn())
        assertEquals(10, s.toCall())
        assertEquals(Clock.NextHand, s.clock())

        // A fold with one opponent left pays the pot at once and the next hand is dealt.
        s = s.fold()
        assertEquals(Phase.ROUND_END, s.phase)
        s = s.advance()
        assertEquals(Phase.HAND_END, s.phase)
        assertEquals(2021, s.chips("Eve"))
        s = s.advance()
        assertEquals(5, s.hand)
        assertEquals(BlindLevel(40, 20, duration = 2), s.level)
        assertEquals(1, s.dealer)
        assertEquals(2001, s.chips("Eve"))
        assertEquals(1446, s.chips("Ann"))
        assertEquals("Eve", s.turn())
        assertEquals(20, s.toCall())

        // Two more buy in while the hand is on.
        s = s.addPlayer(4, "Fay", 1000).addPlayer(6, "Gus", 300)
        s = s.fold().settled()
        assertEquals(6, s.hand)
        assertEquals(4, s.dealer)
        assertEquals(Act.SB, s.last("Gus"))
        assertEquals(Act.BB, s.last("Ann"))
        assertEquals(280, s.chips("Gus"))
        assertEquals(1466, s.chips("Ann"))
        assertEquals(2001, s.chips("Eve"))
        assertEquals(1000, s.chips("Fay"))
        assertEquals("Eve", s.turn())
        assertEquals(Clock.NextHand, s.clock())

        // Chips can be added once a player has folded.
        assertFalse(s.canAddChips(1))
        s = s.fold()
        assertTrue(s.canAddChips(1))
        s = s.addChips(1, 250)
        assertEquals(2251, s.chips("Eve"))

        // Gus goes all in for less than a full raise over Fay's. The reference let that reopen the
        // betting and shrink the minimum to 60. By the usual rule the minimum stays at Fay's 200,
        // Ann, who has not acted yet, may still raise, and Fay may only call the extra 60.
        assertEquals("Fay", s.turn())
        s = s.raise(200)
        assertEquals(760, s.chips("Fay"))
        assertEquals(300, s.pot)
        assertEquals("Gus", s.turn())
        assertEquals(220, s.toCall())
        s.raiseSpec()!!.let {
            assertTrue(it.fixed)
            assertEquals(60, it.max)
        }
        s = s.raise(60)
        assertEquals(0, s.chips("Gus"))
        assertEquals(Act.RAISE, s.last("Gus"))
        assertEquals(580, s.pot)
        assertEquals("Ann", s.turn())
        assertEquals(260, s.toCall())
        assertEquals(200, s.raiseSpec()!!.min)
        assertSquare(s)
        s = s.checkOrCall()
        assertEquals(1206, s.chips("Ann"))
        assertEquals(840, s.pot)
        assertEquals("Fay", s.turn())
        assertEquals(60, s.toCall())
        assertFalse(s.options()!!.canRaise)
        assertNull(s.raiseSpec())
        s = s.checkOrCall().advance()
        assertEquals(Street.FLOP, s.street)
        assertEquals(900, s.pot)
        assertEquals(700, s.chips("Fay"))
        assertEquals("Ann", s.turn())

        s = s.raise(s.raiseSpec()!!.max)
        assertEquals(Act.BET, s.last("Ann"))
        assertEquals(2106, s.pot)
        assertEquals("Fay", s.turn())
        assertTrue(s.options()!!.allInCall)
        assertEquals(700, s.toCall())
        s = s.checkOrCall().settled()
        assertEquals(listOf(900, 1400, 506), s.pots.map { it.amount })
        assertEquals(listOf("Gus", "Fay", "Ann"), s.names(0))
        assertEquals(listOf("Fay", "Ann"), s.names(1))
        assertEquals(listOf("Ann"), s.names(2))
        s = s.award(listOf(listOf(4), listOf(4), listOf(0)))
        assertEquals(2300, s.chips("Fay"))
        assertEquals(506, s.chips("Ann"))

        // Hand 7, after Gus has left: the last level. Three-handed the dealer opens.
        s = s.advance().giveUp(6)
        assertEquals(listOf("Ann", "Eve", "Fay"), s.players.map { it.name })
        assertEquals(BlindLevel(80, 40, duration = 2), s.level)
        assertEquals(Clock.Final, s.clock())
        assertEquals(0, s.dealer)
        assertEquals(2211, s.chips("Eve"))
        assertEquals(2220, s.chips("Fay"))
        assertEquals("Ann", s.turn())

        s = s.leave(1)
        assertFalse(s.canLeave(0)) // two have to stay
        s = s.fold()
        assertEquals("Eve", s.turn())
        assertEquals(40, s.toCall())
        s = s.fold().settled()

        // Hand 8, heads-up after Eve left. The button is Fay's, so she posts the small blind.
        assertEquals(listOf("Ann", "Fay"), s.players.map { it.name })
        assertEquals(4, s.dealer)
        assertEquals(Act.SB, s.last("Fay"))
        assertEquals(Act.BB, s.last("Ann"))
        assertEquals(2300, s.chips("Fay"))
        assertEquals(426, s.chips("Ann"))
        assertEquals("Fay", s.turn())

        // Fay shoves, Ann calls and loses: one player left with chips ends the game.
        s = s.raise(s.raiseSpec()!!.max).checkOrCall().settled()
        assertEquals(Phase.SHOWDOWN, s.phase)
        assertEquals(listOf(1012, 1834), s.pots.map { it.amount })
        assertEquals(listOf("Fay"), s.names(1))
        s = s.award(listOf(listOf(4), listOf(4))).advance()
        assertEquals(Phase.REBUY, s.phase)
        s = s.giveUp(0)
        assertEquals(Phase.GAME_OVER, s.phase)
        assertEquals(4, s.winner)
        assertEquals(2846, s.chips("Fay"))
        assertEquals(0, s.chips("Ann"))

        // The ledger remembers everyone who sat down, what they bought and what they left with.
        assertEquals(
            listOf(
                Standing("Fay", paidIn = 1000, holds = 2846, seated = true),
                Standing("Eve", paidIn = 750, holds = 2211, seated = false),
                Standing("Bob", paidIn = 1000, holds = 993, seated = false),
                Standing("Gus", paidIn = 300, holds = 0, seated = false),
                Standing("Ann", paidIn = 1000, holds = 0, seated = true),
                Standing("Cat", paidIn = 1000, holds = 0, seated = false),
                Standing("Dan", paidIn = 1000, holds = 0, seated = false),
            ),
            s.standings(),
        )
        assertEquals(listOf(1846, 1461, -7, -300, -1000, -1000, -1000), s.standings().map { it.net })
        assertSquare(s)

        // Restart goes back to the players and stacks the game began with.
        s = s.restarted()
        assertEquals(Phase.DEALER, s.phase)
        assertEquals(listOf("Ann", "Bob", "Cat", "Dan"), s.players.map { it.name })
        assertTrue(s.players.all { it.chips == 1000 })
    }

    @Test
    fun eightHandedButtonAndBlindsGoClockwise() {
        val full = GameSetup(
            levels = listOf(BlindLevel(20, 10)),
            players = (0 until SEATS).map { SetupPlayer(it, "P$it") },
        )
        var s = newGame(full).withDealer(3)
        assertEquals(Act.SB, s.player(4)!!.last)
        assertEquals(Act.BB, s.player(5)!!.last)
        assertEquals(6, s.active)
        repeat(7) { s = s.fold() }
        s = s.settled()
        assertEquals(4, s.dealer)
        assertEquals(Act.SB, s.player(5)!!.last)
        assertEquals(Act.BB, s.player(6)!!.last)
        assertEquals(7, s.active)
        assertEquals(1010, s.player(5)!!.chips + s.player(5)!!.bet) // the walk paid the big blind 10
    }

    @Test
    fun splitGivesOddChipsLeftOfTheDealer() {
        assertEquals(mapOf(2 to 34, 5 to 33, 7 to 33), split(100, listOf(7, 2, 5), dealer = 0))
        assertEquals(mapOf(7 to 34, 2 to 33, 5 to 33), split(100, listOf(7, 2, 5), dealer = 5))
        assertEquals(mapOf(3 to 50), split(50, listOf(3), dealer = 3))
    }

    @Test
    fun foldedChipsStayInThePotWithoutAClaim() {
        val players = listOf(
            Player(0, "A", 0, inHand = true, total = 50),
            Player(1, "B", 0, inHand = true, folded = true, total = 300),
            Player(2, "C", 100, inHand = true, total = 800),
            Player(3, "D", 500), // bought in mid-hand
        )
        val pots = computePots(players)
        assertEquals(listOf(Pot(150, listOf(0, 2)), Pot(1000, listOf(2))), pots)
    }

    @Test
    fun minutesClockCountsDownAndRaisesBlindsWithTheNextDeal() {
        val timed = setup.copy(unit = DurationUnit.MINUTES, levels = listOf(BlindLevel(10, 5, duration = 1), BlindLevel(20, 10)))
        var s = newGame(timed)
        assertEquals(s, s.tick()) // not before the first deal
        s = s.withDealer(0)
        assertEquals(Clock.Time(60, false), s.clock())
        s = s.togglePause()
        assertEquals(s, s.tick())
        s = s.togglePause()
        repeat(60) { s = s.tick() }
        assertEquals(Clock.NextHand, s.clock())
        assertEquals(BlindLevel(10, 5, duration = 1), s.level)
        repeat(3) { s = s.fold() }
        s = s.settled()
        assertTrue(s.levelUp)
        assertEquals(BlindLevel(20, 10), s.level)
        assertEquals(Clock.Final, s.clock())
    }

    @Test
    fun theClockAlreadyCountsTheFirstHandBeforeItIsDealt() {
        val waiting = newGame(setup)
        assertEquals(Phase.DEALER, waiting.phase)
        assertEquals(Clock.Hands(1), waiting.clock())
        assertEquals(waiting.clock(), waiting.withDealer(0).clock())
        val timed = newGame(setup.copy(unit = DurationUnit.MINUTES))
        assertEquals(Clock.Time(120, false), timed.clock())
    }

    /** Five or six players with 1,000 each at 10/5, except the short stacks given by seat. Seat 0 deals. */
    private fun table(players: Int, short: Map<Int, Int> = emptyMap(), setup: GameSetup = GameSetup()): GameState {
        val game = newGame(setup.copy(players = (0 until players).map { SetupPlayer(it, "P$it") }))
        return game.copy(players = game.players.map { it.copy(chips = short[it.seat] ?: it.chips) }).withDealer(0)
    }

    @Test
    fun anAllInForLessThanARaiseDoesNotReopenTheBetting() {
        var s = table(5, short = mapOf(4 to 60))
        assertEquals(3, s.active) // seat 1 and 2 are the blinds
        s = s.raise(30) // to 40: a full raise, so the next one has to add 30
        assertEquals(30, s.minRaise)
        s.raiseSpec()!!.let {
            assertTrue(it.fixed) // seat 4 has 20 beyond the call
            assertEquals(20, it.max)
        }
        s = s.raise(20) // all in to 60
        assertEquals(60, s.currentBet)
        assertEquals(30, s.minRaise) // a short all-in does not move the minimum

        // Seat 0 and the blinds have not acted yet, so they may still raise, by at least 30.
        assertEquals(0, s.active)
        assertEquals(30, s.raiseSpec()!!.min)
        s = s.checkOrCall().checkOrCall().checkOrCall()

        // Seat 3 raised and has only been raised 20 back: a call or a fold.
        assertEquals(3, s.active)
        assertEquals(20, s.options()!!.call)
        assertFalse(s.options()!!.canRaise)
        assertNull(s.raiseSpec())
        assertEquals(s, s.raise(500)) // and asking for one changes nothing
        assertEquals(Phase.ROUND_END, s.checkOrCall().phase)
    }

    @Test
    fun shortAllInsThatAddUpToARaiseDoReopenIt() {
        var s = table(6, short = mapOf(4 to 60, 5 to 75))
        s = s.raise(30) // seat 3 to 40
        s = s.raise(20) // seat 4 all in to 60
        s = s.raise(15) // seat 5 all in to 75
        assertEquals(75, s.currentBet)
        assertEquals(30, s.minRaise)
        s = s.checkOrCall().checkOrCall().checkOrCall()

        // Seat 3 is now facing 35 more than the 40 they made it: that is a full raise, so they may raise again.
        assertEquals(3, s.active)
        assertTrue(s.options()!!.canRaise)
        assertEquals(30, s.raiseSpec()!!.min)
        s = s.raise(100)
        assertEquals(175, s.currentBet)
        assertEquals(100, s.minRaise)
    }

    @Test
    fun aCheckedPlayerCannotRaiseAShortAllInBet() {
        var s = table(5, short = mapOf(3 to 17))
        repeat(5) { s = s.checkOrCall() } // everyone calls or checks the big blind
        s = s.advance()
        assertEquals(Street.FLOP, s.street)
        assertEquals(1, s.active)
        s = s.checkOrCall().checkOrCall() // seats 1 and 2 check
        assertEquals(3, s.active)
        s = s.raise(7) // seat 3 bets the 7 they have left: less than the big blind
        assertEquals(Act.BET, s.last(3))
        assertEquals(10, s.minRaise)
        assertEquals(10, s.raiseSpec()!!.min) // seat 4 has not acted and may make it 17
        s = s.checkOrCall().checkOrCall()
        assertEquals(1, s.active) // back to those who checked
        assertFalse(s.options()!!.canRaise)
        assertEquals(7, s.options()!!.call)
    }

    private fun GameState.last(seat: Int) = player(seat)!!.last

    @Test
    fun potLimitCapsARaiseAtThePot() {
        val setup = GameSetup(limit = Limit.POT)
        var s = table(3, setup = setup)
        assertEquals(0, s.active)
        s.raiseSpec()!!.let {
            assertEquals(10, it.toCall)
            assertEquals(25, it.max) // the pot of 15 plus the call
            assertEquals(990, it.stack)
            assertEquals(it.max, it.potShare(1, 1))
            assertFalse(it.fixed)
        }
        s = s.raise(990) // asking for everything gets the pot
        assertEquals(35, s.currentBet)
        assertEquals(965, s.player(0)!!.chips)
        assertEquals(80, s.raiseSpec()!!.max) // seat 1: the pot of 50 plus a call of 30

        assertEquals(990, table(3).raiseSpec()!!.max) // no limit: the stack
    }

    @Test
    fun theBigBlindAnteIsPostedByTheBigBlindAlone() {
        val setup = GameSetup(levels = listOf(BlindLevel(20, 10, ante = 20)), anteStyle = AnteStyle.BIG_BLIND)
        val s = table(3, setup = setup)
        assertEquals(listOf(1000, 990, 960), s.players.map { it.chips })
        assertEquals(listOf(0, 10, 40), s.players.map { it.shown })
        assertEquals(50, s.pot)
        assertEquals(20, s.options()!!.call)

        // A big blind too short for both pays the ante first and is all in on what is left. The
        // ante is dead money in the main pot, so the others get back only what they bet beyond it.
        var short = table(3, short = mapOf(2 to 30), setup = setup)
        assertEquals(0, short.player(2)!!.chips)
        assertEquals(10, short.player(2)!!.bet)
        short = short.checkOrCall().checkOrCall() // seat 0 calls 20, seat 1 completes
        short = short.advance()
        repeat(3) { short = short.checkOrCall().checkOrCall().advance() } // the other two check it down
        assertEquals(Phase.SHOWDOWN, short.phase)
        assertEquals(listOf(Pot(50, listOf(2, 0, 1)), Pot(20, listOf(0, 1))), short.pots)
    }

    @Test
    fun aBrokePlayerBuysBackInOrLeaves() {
        var s = table(2)
        assertEquals(0, s.active) // heads-up the dealer acts first
        s = s.raise(990).checkOrCall().settled()
        s = s.award(listOf(listOf(1)))
        assertEquals(s, s.rebuy(0, 500)) // not before the table has been asked
        s = s.advance()
        assertEquals(Phase.REBUY, s.phase)
        assertEquals(-1, s.active)
        assertEquals(s, s.rebuy(1, 500)) // seat 1 is not broke
        assertEquals(s, s.advance()) // and nothing moves until seat 0 decides

        s = s.rebuy(0, 500)
        assertEquals(Phase.BETTING, s.phase)
        assertEquals(2, s.hand)
        assertEquals(listOf(490, 1995), s.players.map { it.chips }) // seat 1 deals and posts the small blind
        assertEquals(1500, s.ledger.first { it.name == "P0" }.paidIn)

        // The second time seat 0 leaves, and one player with chips is a finished game.
        s = s.raise(1000).checkOrCall().settled().award(listOf(listOf(1))).advance()
        assertEquals(Phase.REBUY, s.phase)
        s = s.giveUp(0)
        assertEquals(Phase.GAME_OVER, s.phase)
        assertEquals(1, s.winner)
        assertEquals(listOf(1500, -1500), s.standings().map { it.net })
    }

    @Test
    fun everyBrokePlayerDecidesBeforeTheNextDeal() {
        var s = table(3)
        s = s.raise(990).checkOrCall().checkOrCall().settled().award(listOf(listOf(0)))
        s = s.advance()
        assertEquals(Phase.REBUY, s.phase)
        assertEquals(listOf(1, 2), s.players.filter { it.broke }.map { it.seat })
        s = s.rebuy(1, 1000)
        assertEquals(Phase.REBUY, s.phase) // seat 2 has not decided
        s = s.giveUp(2)
        assertEquals(Phase.BETTING, s.phase)
        assertEquals(listOf(0, 1), s.players.map { it.seat })
        assertEquals(0, s.standings().sumOf { it.net })
    }

    @Test
    fun someoneWhoLeavesAndComesBackKeepsTheirAccount() {
        var s = table(3)
        s = s.leave(2)
        repeat(2) { s = s.fold() } // seats 0 and 1 fold: seat 2 wins the blinds and then leaves
        s = s.settled()
        assertEquals(listOf(0, 1), s.players.map { it.seat })
        assertEquals(Account("P2", paidIn = 1000, cashedOut = 1005), s.ledger.first { it.name == "P2" })

        s = s.addPlayer(5, "p2", 400) // the same person, whatever the capitals
        assertEquals(3, s.ledger.size)
        val back = s.standings().first { it.name == "P2" }
        assertEquals(1400, back.paidIn)
        assertEquals(1405, back.holds)
        assertTrue(back.seated)
        assertEquals(0, s.standings().sumOf { it.net })
    }

    @Test
    fun anOldSaveWithoutALedgerStartsOneFromTheStacks() {
        val old = table(3).copy(ledger = emptyList())
        assertTrue(old.standings().isEmpty())
        val patched = old.withLedger()
        assertEquals(listOf(0, 0, 0), patched.standings().map { it.net })
        assertEquals(patched, patched.withLedger())
    }

    @Test
    fun shortStacksPostWhatTheyHave() {
        val tiny = GameSetup(
            startingChips = 7,
            levels = listOf(BlindLevel(10, 5, ante = 3)),
            players = listOf(SetupPlayer(0, "A"), SetupPlayer(1, "B"), SetupPlayer(2, "C")),
        )
        val s = newGame(tiny).withDealer(0)
        // B posts ante 3 and the 4 left as small blind, C posts ante 3 and 4 of the big blind.
        assertEquals(0, s.player(1)!!.chips)
        assertEquals(0, s.player(2)!!.chips)
        assertEquals(4, s.player(0)!!.chips)
        assertEquals(0, s.active)
        assertTrue(s.options()!!.allInCall)
        val end = s.checkOrCall().settled()
        assertEquals(Phase.SHOWDOWN, end.phase)
        assertEquals(listOf(Pot(21, listOf(0, 1, 2))), end.pots)
    }
}
