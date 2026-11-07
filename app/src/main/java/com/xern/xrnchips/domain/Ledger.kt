package com.xern.xrnchips.domain

/*
 * Who is up and who is down. The table only knows chips; the ledger remembers what each person
 * bought and took away, so the night can be settled when it ends.
 */

/** One person's result so far. [holds] is what they have: their stack if seated, plus anything they left with before. */
data class Standing(val name: String, val paidIn: Int, val holds: Int, val seated: Boolean) {
    val net: Int get() = holds - paidIn
}

/**
 * Everyone who has played, best result first. Chips a player has in the pot of a hand still being
 * played count as theirs until the hand is decided, so the results always add up to zero.
 */
fun GameState.standings(): List<Standing> = ledger.map { account ->
    val player = players.firstOrNull { it.name.equals(account.name, ignoreCase = true) }
    Standing(
        name = account.name,
        paidIn = account.paidIn,
        holds = account.cashedOut + (player?.let { it.chips + it.total } ?: 0),
        seated = player != null,
    )
}.sortedWith(compareByDescending<Standing> { it.net }.thenBy { it.name })

/**
 * A game saved before there was a ledger gets one that starts now: everyone is taken to have paid
 * for exactly the chips they hold.
 */
fun GameState.withLedger(): GameState =
    if (ledger.isNotEmpty()) this else copy(ledger = players.map { Account(it.name, paidIn = it.chips + it.total) })

/** [chips] in money when a stack of [stack] chips costs [price], to the nearest whole unit. */
fun worth(chips: Int, price: Int, stack: Int): Int =
    if (stack <= 0) 0 else Math.round(chips.toLong() * price / stack.toDouble()).toInt()

/**
 * Each result in money. Rounding can leave the total a unit or two off zero; that remainder goes
 * to the largest result, where it matters least, so the payments still come out even.
 */
fun moneyResults(standings: List<Standing>, price: Int, stack: Int): List<Pair<String, Int>> {
    val amounts = standings.map { worth(it.net, price, stack) }.toMutableList()
    val off = amounts.sum()
    if (off != 0 && amounts.isNotEmpty()) {
        val largest = amounts.indices.maxBy { kotlin.math.abs(amounts[it]) }
        amounts[largest] -= off
    }
    return standings.mapIndexed { i, standing -> standing.name to amounts[i] }
}

data class Transfer(val from: String, val to: String, val amount: Int)

/** Above this many open results the exact search is too large and the simple pairing is used alone. */
private const val EXACT_LIMIT = 16

/**
 * Who pays whom so that everyone ends up square. [results] add up to zero; a positive one is owed.
 *
 * The fewest payments come from splitting the players into as many groups as possible that are
 * square among themselves: a group of k people needs k - 1 payments, so every extra group saves
 * one. The groups are found by trying every subset, which is fine for a poker table.
 */
fun settle(results: List<Pair<String, Int>>): List<Transfer> {
    val open = results.filter { it.second != 0 }
    if (open.isEmpty() || open.sumOf { it.second } != 0) return emptyList()
    val groups = if (open.size <= EXACT_LIMIT) squareGroups(open) else listOf(open)
    return groups.flatMap(::payOff)
}

/** Within a group the one who owes most pays the one owed most, and so on until all are square. */
private fun payOff(group: List<Pair<String, Int>>): List<Transfer> {
    val owing = group.filter { it.second < 0 }.sortedBy { it.second }.map { it.first to -it.second }.toMutableList()
    val owed = group.filter { it.second > 0 }.sortedByDescending { it.second }.toMutableList()
    val transfers = mutableListOf<Transfer>()
    var i = 0
    var j = 0
    while (i < owing.size && j < owed.size) {
        val amount = minOf(owing[i].second, owed[j].second)
        transfers += Transfer(owing[i].first, owed[j].first, amount)
        owing[i] = owing[i].first to owing[i].second - amount
        owed[j] = owed[j].first to owed[j].second - amount
        if (owing[i].second == 0) i++
        if (owed[j].second == 0) j++
    }
    return transfers
}

/**
 * Splits [open] into the largest number of groups that each add up to zero. For every subset it
 * keeps the most zero-sum subsets that can be nested inside it one element at a time; walking
 * that chain back from the full set peels the groups off.
 */
private fun squareGroups(open: List<Pair<String, Int>>): List<List<Pair<String, Int>>> {
    val size = 1 shl open.size
    val sum = IntArray(size)
    val best = IntArray(size)
    val pick = IntArray(size)
    for (mask in 1 until size) {
        sum[mask] = sum[mask and (mask - 1)] + open[Integer.numberOfTrailingZeros(mask)].second
        var top = -1
        var rest = mask
        while (rest != 0) {
            val i = Integer.numberOfTrailingZeros(rest)
            rest = rest and (rest - 1)
            val without = best[mask xor (1 shl i)]
            if (without > top) {
                top = without
                pick[mask] = i
            }
        }
        best[mask] = top + if (sum[mask] == 0) 1 else 0
    }
    val groups = mutableListOf<List<Pair<String, Int>>>()
    var group = mutableListOf<Pair<String, Int>>()
    var mask = size - 1
    while (mask != 0) {
        val i = pick[mask]
        group += open[i]
        mask = mask xor (1 shl i)
        if (sum[mask] == 0) {
            groups += group
            group = mutableListOf()
        }
    }
    return groups
}
