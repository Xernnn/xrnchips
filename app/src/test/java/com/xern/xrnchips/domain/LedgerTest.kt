package com.xern.xrnchips.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LedgerTest {
    /** Everyone's result after the payments are made: all zero when the payments settle the night. */
    private fun after(results: List<Pair<String, Int>>, transfers: List<Transfer>): Map<String, Int> {
        val left = results.toMap().toMutableMap()
        for (t in transfers) {
            assertTrue(t.amount > 0)
            left[t.from] = left.getValue(t.from) + t.amount
            left[t.to] = left.getValue(t.to) - t.amount
        }
        return left
    }

    @Test
    fun oneLoserPaysOneWinner() {
        assertEquals(listOf(Transfer("B", "A", 50)), settle(listOf("A" to 50, "B" to -50, "C" to 0)))
        assertTrue(settle(listOf("A" to 0, "B" to 0)).isEmpty())
    }

    @Test
    fun groupsThatAreSquareAmongThemselvesSaveAPayment() {
        // Matching the biggest loser with the biggest winner takes four payments here. Three do:
        // C pays B, and D and E pay A.
        val results = listOf("A" to 9, "B" to 6, "C" to -6, "D" to -5, "E" to -4)
        val transfers = settle(results)
        assertEquals(3, transfers.size)
        assertTrue(Transfer("C", "B", 6) in transfers)
        assertTrue(after(results, transfers).values.all { it == 0 })
    }

    @Test
    fun anySetOfResultsIsSettledInFewerPaymentsThanPlayers() {
        val random = Random(20261106)
        repeat(300) {
            val count = random.nextInt(2, 11)
            val amounts = MutableList(count - 1) { random.nextInt(-40, 41) * 5 }
            amounts += -amounts.sum()
            val results = amounts.mapIndexed { i, amount -> "P$i" to amount }
            val transfers = settle(results)
            assertTrue(after(results, transfers).values.all { it == 0 })
            val open = results.count { it.second != 0 }
            assertTrue(transfers.size <= maxOf(0, open - 1))
        }
    }

    @Test
    fun resultsThatDoNotCancelOutAreNotSettled() {
        assertTrue(settle(listOf("A" to 50, "B" to -40)).isEmpty())
    }

    @Test
    fun chipsBecomeMoneyAtThePriceOfAStack() {
        assertEquals(150, worth(1500, price = 100, stack = 1000))
        assertEquals(-33, worth(-333, price = 100, stack = 1000))
        assertEquals(0, worth(500, price = 0, stack = 1000))
        assertEquals(200_000, worth(10_000, price = 200_000, stack = 10_000))
    }

    @Test
    fun roundedMoneyStillAddsUpToZero() {
        val standings = listOf(
            Standing("A", paidIn = 1000, holds = 1335, seated = true),
            Standing("B", paidIn = 1000, holds = 1335, seated = true),
            Standing("C", paidIn = 1000, holds = 330, seated = true),
        )
        // 33.5, 33.5 and -67 round to 34, 34 and -67: one over. The largest result absorbs it.
        val money = moneyResults(standings, price = 100, stack = 1000)
        assertEquals(listOf("A" to 34, "B" to 34, "C" to -68), money)
        assertEquals(0, money.sumOf { it.second })
        assertEquals(2, settle(money).size)
    }
}
