package com.xern.xrnchips.data

import com.xern.xrnchips.domain.GameSetup
import com.xern.xrnchips.domain.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** What the + and - buttons and the slider of the raise dialog move by. */
@Serializable
enum class Step { BB, SB, ONE }

@Serializable
data class Settings(
    val vibration: Boolean = true,
    val doubleTap: Boolean = false,
    val sound: Boolean = true,
    /** Tenths of a second an action button has to be held. Zero fires on touch. */
    val holdTenths: Int = 4,
    val sliderStep: Step = Step.BB,
    val buttonStep: Step = Step.BB,
    val felt: Int = 0,
    /** A soft note each time the turn passes to the next player. */
    val turnSound: Boolean = false,
    /** Stacks shown as a number of big blinds instead of chips. */
    val stacksInBlinds: Boolean = false,
    /** What a starting stack costs, for settling up. Zero while a game is only for chips. */
    val price: Int = 0,
) {
    companion object {
        const val MAX_HOLD_TENTHS = 20
    }
}

/** A game on disk. The history is what Undo can still step back through. */
@Serializable
data class SavedGame(
    val id: String,
    val savedAt: Long,
    val auto: Boolean = false,
    val state: GameState,
    val history: List<GameState> = emptyList(),
)

/**
 * Everything the app keeps, as small JSON files in its private folder: the settings, the Play
 * screen as it was left, one autosave and the games saved by hand. Reads happen once at start;
 * writes go to a single background thread, each to a temporary file that then replaces the old one.
 */
class Store(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val disk = Executors.newSingleThreadExecutor()
    private val saveDir = File(dir, "saves").apply { mkdirs() }

    private val _settings = MutableStateFlow(read(File(dir, SETTINGS), Settings.serializer()) ?: Settings())
    val settings: StateFlow<Settings> = _settings

    private val _setup = MutableStateFlow(read(File(dir, SETUP), GameSetup.serializer()) ?: GameSetup())
    val setup: StateFlow<GameSetup> = _setup

    /** The autosave first, then the others from newest to oldest. */
    private val _saves = MutableStateFlow(
        (saveDir.listFiles().orEmpty().mapNotNull { read(it, SavedGame.serializer()) }).ordered(),
    )
    val saves: StateFlow<List<SavedGame>> = _saves

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.value = change(_settings.value)
        write(File(dir, SETTINGS), Settings.serializer(), _settings.value)
    }

    fun updateSetup(change: (GameSetup) -> GameSetup) {
        val next = change(_setup.value)
        if (next == _setup.value) return
        _setup.value = next
        write(File(dir, SETUP), GameSetup.serializer(), next)
    }

    fun autosave(state: GameState, history: List<GameState>) =
        put(SavedGame(AUTOSAVE, System.currentTimeMillis(), auto = true, state = state, history = history.takeLast(KEPT_HISTORY)))

    fun save(state: GameState, history: List<GameState>) {
        val now = System.currentTimeMillis()
        put(SavedGame("save-$now", now, state = state, history = history.takeLast(KEPT_HISTORY)))
    }

    fun delete(id: String) {
        _saves.value = _saves.value.filter { it.id != id }
        disk.execute { File(saveDir, "$id.json").delete() }
    }

    fun deleteAll() {
        _saves.value = emptyList()
        disk.execute { saveDir.listFiles()?.forEach { it.delete() } }
    }

    private fun put(game: SavedGame) {
        _saves.value = (_saves.value.filter { it.id != game.id } + game).ordered()
        write(File(saveDir, "${game.id}.json"), SavedGame.serializer(), game)
    }

    private fun List<SavedGame>.ordered() = sortedWith(compareByDescending<SavedGame> { it.auto }.thenByDescending { it.savedAt })

    /** A file that is missing or no longer parses reads as nothing, and the caller falls back to defaults. */
    private fun <T> read(file: File, serializer: KSerializer<T>): T? =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()

    private fun <T> write(file: File, serializer: KSerializer<T>, value: T) {
        val text = json.encodeToString(serializer, value)
        disk.execute {
            runCatching {
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writeText(text)
                if (!temp.renameTo(file)) {
                    file.writeText(text)
                    temp.delete()
                }
            }
        }
    }

    private companion object {
        const val SETTINGS = "settings.json"
        const val SETUP = "setup.json"
        const val AUTOSAVE = "autosave"
        const val KEPT_HISTORY = 40
    }
}
