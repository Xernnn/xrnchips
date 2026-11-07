package com.xern.xrnchips.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Play screen's field rules, as the reference app applies them when a field loses focus. */
class SetupTest {
    @Test
    fun defaults() {
        val setup = GameSetup()
        assertEquals(1000, setup.startingChips)
        assertEquals(listOf(BlindLevel(10, 5, 0, 15)), setup.levels)
        assertEquals(DurationUnit.MINUTES, setup.unit)
        assertTrue(setup.players.isEmpty())
        assertTrue(setup.isDefault)
    }

    @Test
    fun blindsKeepTheSmallOneUnderTheBigOne() {
        var setup = GameSetup()
        setup = setup.withBig(0, 3)
        assertEquals(BlindLevel(3, 3), setup.levels[0])
        setup = setup.withSmall(0, 50)
        assertEquals(BlindLevel(50, 50), setup.levels[0])
        setup = setup.withSmall(0, 20)
        assertEquals(BlindLevel(50, 20), setup.levels[0])
    }

    @Test
    fun zeroAndEmptyAreRefusedExceptForTheAnte() {
        val setup = GameSetup().withBig(0, 50).withSmall(0, 20).withAnte(0, 99)
        assertEquals(setup, setup.withBig(0, 0))
        assertEquals(setup, setup.withBig(0, null))
        assertEquals(setup, setup.withSmall(0, 0))
        assertEquals(setup, setup.withSmall(0, null))
        assertEquals(setup, setup.withStartingChips(0))
        assertEquals(setup, setup.withStartingChips(null))
        assertEquals(setup, setup.withDuration(0, 0))
        assertEquals(0, setup.withAnte(0, null).levels[0].ante)
        assertEquals(99, setup.levels[0].ante) // an ante above the big blind is allowed
    }

    @Test
    fun amountsStopAtSixDigits() {
        val setup = GameSetup().withBig(0, 123_456_789).withAnte(0, 99_999_999).withStartingChips(1_234_567_890)
        assertEquals(MAX_CHIPS, setup.levels[0].big)
        assertEquals(MAX_CHIPS, setup.levels[0].ante)
        assertEquals(MAX_CHIPS, setup.startingChips)
        assertEquals(MAX_DURATION, setup.withDuration(0, 123_456).levels[0].duration)
    }

    @Test
    fun addedLevelsDoubleAndCopyTheLengthBeforeThem() {
        var setup = GameSetup().addLevel()
        assertEquals(listOf(BlindLevel(10, 5, 0, 15), BlindLevel(20, 10, 0, 15)), setup.levels)
        setup = setup.addLevel()
        assertEquals(BlindLevel(40, 20, 0, 15), setup.levels[2])
        setup = setup.withDuration(1, 7).addLevel()
        assertEquals(listOf(15, 7, 7), setup.levels.take(3).map { it.duration })
        assertEquals(BlindLevel(80, 40, 0, 7), setup.levels[3])
        setup = setup.withAnte(3, 3).addLevel()
        assertEquals(6, setup.levels[4].ante)
    }

    @Test
    fun theLastLevelCannotBeRemoved() {
        val one = GameSetup()
        assertEquals(one, one.removeLevel(0))
        val two = one.addLevel()
        assertEquals(listOf(BlindLevel(20, 10, 0, 15)), two.removeLevel(0).levels)
    }

    @Test
    fun switchingTheUnitSetsTheUsualLengthForIt() {
        val minutes = GameSetup().addLevel().addLevel().withDuration(0, 20)
        assertEquals(listOf(20, 15, 15), minutes.levels.map { it.duration })

        val hands = minutes.withUnit(DurationUnit.HANDS)
        assertEquals(DurationUnit.HANDS, hands.unit)
        assertEquals(listOf(4, 4, 4), hands.levels.map { it.duration })
        assertEquals(minutes.levels.map { it.big }, hands.levels.map { it.big }) // only the lengths change

        assertEquals(listOf(15, 15, 15), hands.withUnit(DurationUnit.MINUTES).levels.map { it.duration })
        assertEquals(minutes, minutes.withUnit(DurationUnit.MINUTES)) // choosing the unit it already has keeps the lengths
    }

    @Test
    fun theFirstAddedLevelUsesTheUnitsUsualLength() {
        // A single level never shows a length, whatever is stored for it.
        val hands = GameSetup(levels = listOf(BlindLevel(10, 5, 0, 30)), unit = DurationUnit.HANDS).addLevel()
        assertEquals(listOf(4, 4), hands.levels.map { it.duration })
        val minutes = GameSetup(levels = listOf(BlindLevel(10, 5, 0, 30))).addLevel()
        assertEquals(listOf(15, 15), minutes.levels.map { it.duration })
    }

    @Test
    fun namesAreUniqueWhateverTheCase() {
        val setup = GameSetup().withPlayer(0, "Sam").withPlayer(3, " Kim ")
        assertEquals(listOf(SetupPlayer(0, "Sam"), SetupPlayer(3, "Kim")), setup.players)
        assertTrue(setup.nameTaken("sam"))
        assertTrue(setup.nameTaken(" KIM"))
        assertFalse(setup.nameTaken("sam", exceptSeat = 0)) // renaming a player to their own name
        assertFalse(setup.nameTaken("Lee"))
        assertEquals(listOf(SetupPlayer(3, "Kim")), setup.withoutPlayer(0).players)
        assertEquals("Sami", setup.withPlayer(0, "Sami").playerAt(0)!!.name)
    }

    @Test
    fun groupedNumbers() {
        assertEquals("0", 0.grouped())
        assertEquals("999", 999.grouped())
        assertEquals("1,000", 1000.grouped())
        assertEquals("1,234,567", 1234567.grouped())
    }
}
