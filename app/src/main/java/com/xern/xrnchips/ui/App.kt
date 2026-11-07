package com.xern.xrnchips.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Screen { START, SETUP, GAME, LOAD, SETTINGS, ABOUT, LICENSES }

private val stackSaver = listSaver<SnapshotStateList<Screen>, Int>(
    save = { stack -> stack.map { it.ordinal } },
    restore = { saved -> saved.map { Screen.entries[it] }.toMutableStateList() },
)

/** The whole app: a stack of screens over the start menu. The table sits directly on the menu. */
@Composable
fun ChipsRoot(model: TableModel) {
    val store = model.store
    val settings by store.settings.collectAsStateWithLifecycle()
    val saves by store.saves.collectAsStateWithLifecycle()
    val stack = rememberSaveable(saver = stackSaver) { mutableStateListOf(Screen.START) }

    fun open(screen: Screen) {
        stack += screen
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun toTable() {
        stack.clear()
        stack += Screen.START
        stack += Screen.GAME
    }

    fun home() {
        model.close()
        stack.clear()
        stack += Screen.START
    }

    // A table with no game behind it is what a restarted process leaves: start over from the menu.
    val orphaned = Screen.GAME in stack && model.game == null
    LaunchedEffect(orphaned) { if (orphaned) home() }
    val current = if (orphaned) Screen.START else stack.last()

    ChipsTheme(settings) {
        Box(Modifier.fillMaxSize().background(Ink.Black)) {
            BackHandler(enabled = stack.size > 1 && current != Screen.GAME) { back() }
            Crossfade(targetState = current, animationSpec = tween(220), label = "screen") { screen ->
                when (screen) {
                    Screen.START -> StartScreen(
                        onContinue = saves.firstOrNull { it.auto }?.let { autosave ->
                            {
                                model.resume(autosave)
                                toTable()
                            }
                        },
                        onPlay = { open(Screen.SETUP) },
                        onLoad = { open(Screen.LOAD) },
                        onSettings = { open(Screen.SETTINGS) },
                        onAbout = { open(Screen.ABOUT) },
                    )
                    Screen.SETUP -> SetupScreen(store, settings.felt, onBack = ::back, onStart = { setup ->
                        model.start(setup)
                        toTable()
                    })
                    Screen.GAME -> GameScreen(model, settings, onSettings = { open(Screen.SETTINGS) }, onExit = ::home)
                    Screen.LOAD -> LoadScreen(store, onBack = ::back, onPlay = { saved ->
                        model.resume(saved)
                        toTable()
                    })
                    Screen.SETTINGS -> SettingsScreen(store, onBack = ::back)
                    Screen.ABOUT -> AboutScreen(onBack = ::back, onLicenses = { open(Screen.LICENSES) })
                    Screen.LICENSES -> LicensesScreen(onBack = ::back)
                }
            }
        }
    }
}
