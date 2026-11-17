package com.xern.xrnchips.data

import com.xern.xrnchips.domain.GameSetup
import com.xern.xrnchips.domain.SetupPlayer
import com.xern.xrnchips.domain.newGame
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executor

class StoreTest {
    @get:Rule val folder = TemporaryFolder()

    /** Runs what it is given only when told to, as the disk thread would, later. */
    private class Later : Executor {
        val waiting = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            waiting += command
        }
        fun runAll() {
            while (waiting.isNotEmpty()) waiting.removeFirst().run()
        }
    }

    private val game = newGame(GameSetup(players = listOf(SetupPlayer(0, "Ann"), SetupPlayer(4, "Bob"))))

    @Test
    fun savedGamesAreReadOffTheThreadThatOpensTheStore() {
        val disk = Later()
        Store(folder.root, disk).apply {
            save(game, emptyList())
            autosave(game, emptyList())
        }
        disk.runAll()

        val later = Later()
        val store = Store(folder.root, later)
        // Opening the app read every save, with up to 40 earlier states in each, before the first frame.
        assertEquals(emptyList<SavedGame>(), store.saves.value)
        later.runAll()
        assertEquals(listOf(true, false), store.saves.value.map { it.auto })
    }

    @Test
    fun whatHappensBeforeTheSavesAreReadIsKept() {
        val disk = Later()
        Store(folder.root, disk).apply {
            save(game, emptyList())
            autosave(game, emptyList())
        }
        disk.runAll()
        val later = Later()
        val store = Store(folder.root, later)
        store.delete("autosave")
        store.autosave(game.copy(hand = 7), emptyList())
        later.runAll()
        assertEquals(listOf(true to 7, false to 0), store.saves.value.map { it.auto to it.state.hand })
        val again = Later()
        Store(folder.root, again).also { again.runAll() }.saves.value.let { reread ->
            assertEquals(listOf(true to 7, false to 0), reread.map { it.auto to it.state.hand })
        }
    }
}
