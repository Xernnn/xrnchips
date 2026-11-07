package com.xern.xrnchips.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xern.xrnchips.ChipsApp
import com.xern.xrnchips.data.SavedGame
import com.xern.xrnchips.data.Sfx
import com.xern.xrnchips.domain.BlindLevel
import com.xern.xrnchips.domain.GameSetup
import com.xern.xrnchips.domain.GameState
import com.xern.xrnchips.domain.Phase
import com.xern.xrnchips.domain.Street
import com.xern.xrnchips.domain.addChips
import com.xern.xrnchips.domain.addPlayer
import com.xern.xrnchips.domain.advance
import com.xern.xrnchips.domain.award
import com.xern.xrnchips.domain.checkOrCall
import com.xern.xrnchips.domain.fold
import com.xern.xrnchips.domain.giveUp
import com.xern.xrnchips.domain.leave
import com.xern.xrnchips.domain.newGame
import com.xern.xrnchips.domain.options
import com.xern.xrnchips.domain.raise
import com.xern.xrnchips.domain.rebuy
import com.xern.xrnchips.domain.restarted
import com.xern.xrnchips.domain.tick
import com.xern.xrnchips.domain.togglePause
import com.xern.xrnchips.domain.withDealer
import com.xern.xrnchips.domain.withLedger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The game on the table. Players' moves go through the rules in the domain package; this class
 * paces the steps the table takes by itself (dealing a street, paying a pot, starting the next
 * hand), keeps what Undo can go back to, runs the level clock and saves after every move.
 */
class TableModel(app: Application) : AndroidViewModel(app) {
    private val chips = app as ChipsApp
    val store get() = chips.store

    var game by mutableStateOf<GameState?>(null)
        private set

    /** True while the table is playing out a street change or a payout, and takes no input. */
    var busy by mutableStateOf(false)
        private set

    var canUndo by mutableStateOf(false)
        private set

    /** Set for a moment when a hand opens a new blind level. */
    var raisedBlinds by mutableStateOf<BlindLevel?>(null)
        private set

    private val history = ArrayList<GameState>()
    private var stepping: Job? = null
    private var notice: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val now = game ?: continue
                val next = now.tick()
                if (next !== now) game = next
            }
        }
    }

    fun start(setup: GameSetup) = open(newGame(setup), emptyList())

    fun resume(saved: SavedGame) = open(saved.state.withLedger(), saved.history)

    /** Back to the dealer pick with the players and stacks the game began with. */
    fun restart() {
        val now = game ?: return
        open(now.restarted(), emptyList())
    }

    fun close() {
        stepping?.cancel()
        notice?.cancel()
        game = null
        history.clear()
        canUndo = false
        busy = false
        raisedBlinds = null
    }

    fun dealer(seat: Int) = act(record = false) { it.withDealer(seat) }

    fun fold() = act(Sfx.FOLD) { it.fold() }

    fun checkOrCall() {
        val check = game?.options()?.check ?: return
        act(if (check) Sfx.CHECK else Sfx.CHIPS) { it.checkOrCall() }
    }

    fun raise(amount: Int) = act(Sfx.CHIPS) { it.raise(amount) }

    fun award(winners: List<List<Int>>) = act(Sfx.WIN) { it.award(winners) }

    fun addPlayer(seat: Int, name: String, chips: Int) = act { it.addPlayer(seat, name, chips) }

    fun addChips(seat: Int, amount: Int) = act { it.addChips(seat, amount) }

    fun leave(seat: Int) = act { it.leave(seat) }

    fun rebuy(seat: Int, chips: Int) = act { it.rebuy(seat, chips) }

    fun giveUp(seat: Int) = act { it.giveUp(seat) }

    fun togglePause() {
        game = game?.togglePause()
    }

    /** Steps back one move. The level clock keeps the time it has now unless the blinds change with it. */
    fun undo() {
        val now = game ?: return
        if (busy || history.isEmpty()) return
        val back = history.removeAt(history.lastIndex)
        game = if (back.levelIndex == now.levelIndex) back.copy(secondsLeft = now.secondsLeft, paused = now.paused) else back
        canUndo = history.isNotEmpty()
        settle()
    }

    fun save(): Boolean {
        val now = game ?: return false
        store.save(now, history)
        return true
    }

    /** The clock moves without a move being made, so the autosave is refreshed when the app is left. */
    fun flush() = autosave()

    private fun open(state: GameState, past: List<GameState>) {
        stepping?.cancel()
        history.clear()
        history.addAll(past)
        canUndo = history.isNotEmpty()
        raisedBlinds = null
        game = state
        settle()
    }

    private fun act(sound: Sfx? = null, record: Boolean = true, change: (GameState) -> GameState) {
        val before = game ?: return
        if (busy) return
        val after = change(before)
        if (after == before) return
        if (record) {
            history += before
            if (history.size > MAX_HISTORY) history.removeAt(0)
            canUndo = true
        }
        sound?.let(::play)
        game = after
        arrived(before, after)
        settle()
    }

    /**
     * What a new state calls for besides being shown: the notice and sound of a higher blind
     * level, applause at the end, and, if wanted, a note for the player whose turn it now is.
     */
    private fun arrived(before: GameState, after: GameState) {
        if (after.phase == Phase.GAME_OVER && before.phase != Phase.GAME_OVER) play(Sfx.APPLAUSE)
        if (after.hand != before.hand && after.levelUp) {
            announce(after.level)
            play(Sfx.LEVEL)
        }
        val turnMoved = after.phase == Phase.BETTING &&
            (before.phase != Phase.BETTING || before.active != after.active || before.hand != after.hand || before.street != after.street)
        if (turnMoved && store.settings.value.turnSound) {
            // A beat later, so it does not land on top of the chips or the cards.
            viewModelScope.launch {
                delay(TURN_CUE_DELAY)
                play(Sfx.TURN)
            }
        }
    }

    /** Plays out ROUND_END and HAND_END one step at a time so each can be seen, then saves. */
    private fun settle() {
        stepping?.cancel()
        if (game?.phase.let { it != Phase.ROUND_END && it != Phase.HAND_END }) {
            busy = false
            autosave()
            return
        }
        busy = true
        stepping = viewModelScope.launch {
            // The last move stays on screen for a moment; a board run out with nobody left to bet goes quicker.
            var pause = STREET_PAUSE
            while (true) {
                when (game?.phase) {
                    Phase.ROUND_END -> {
                        delay(pause)
                        pause = RUNOUT_PAUSE
                        val now = game ?: break
                        val next = now.advance()
                        when {
                            next.phase == Phase.HAND_END -> play(Sfx.WIN)
                            next.street == Street.FLOP && now.street != Street.FLOP -> play(Sfx.DEAL)
                            next.street != now.street -> play(Sfx.CARD)
                        }
                        game = next
                        arrived(now, next)
                    }
                    Phase.HAND_END -> {
                        delay(PAYOUT_PAUSE)
                        pause = STREET_PAUSE
                        val now = game ?: break
                        val next = now.advance()
                        game = next
                        arrived(now, next)
                    }
                    else -> break
                }
            }
            busy = false
            autosave()
        }
    }

    private fun announce(level: BlindLevel) {
        notice?.cancel()
        raisedBlinds = level
        notice = viewModelScope.launch {
            delay(NOTICE_TIME)
            raisedBlinds = null
        }
    }

    private fun autosave() {
        val now = game ?: return
        if (busy || now.phase == Phase.DEALER) return
        store.autosave(now, history)
    }

    private fun play(sfx: Sfx) {
        if (store.settings.value.sound) chips.sounds.play(sfx)
    }

    private companion object {
        const val MAX_HISTORY = 200
        const val STREET_PAUSE = 650L
        const val RUNOUT_PAUSE = 280L
        const val PAYOUT_PAUSE = 1500L
        const val NOTICE_TIME = 2200L
        const val TURN_CUE_DELAY = 300L
    }
}
