package com.xern.xrnchips.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xern.xrnchips.BuildConfig
import com.xern.xrnchips.R
import com.xern.xrnchips.data.SavedGame
import com.xern.xrnchips.data.Settings
import com.xern.xrnchips.data.Step
import com.xern.xrnchips.data.Store
import com.xern.xrnchips.domain.Phase
import com.xern.xrnchips.domain.Street
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The app icon as a tile. The launcher layers keep their art in the middle two thirds, so they are drawn oversize and cropped. */
@Composable
fun AppMark(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.24f)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}

/** [onContinue] is there while a game is waiting in the autosave. It goes straight back to the table. */
@Composable
fun StartScreen(onContinue: (() -> Unit)?, onPlay: () -> Unit, onLoad: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        val pop = remember { Animatable(0.7f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
        AppMark(116.dp, Modifier.scale(pop.value))
        Spacer(Modifier.height(20.dp))
        Text("xrnchips", style = type(40, FontWeight.Black))
        Text("Poker night without the chips", style = type(16, color = Ink.Dim))
        Spacer(Modifier.weight(1f))
        if (onContinue != null) {
            PillButton("Continue", onContinue, Modifier.fillMaxWidth(), tone = Tone.ACCENT, icon = Icons.Filled.Replay, height = 60.dp)
            Spacer(Modifier.height(12.dp))
        }
        PillButton("Play", onPlay, Modifier.fillMaxWidth(), tone = Tone.PRIMARY, icon = Icons.Filled.PlayArrow, height = 60.dp)
        Spacer(Modifier.height(12.dp))
        PillButton("Load", onLoad, Modifier.fillMaxWidth(), icon = Icons.Filled.FolderOpen, height = 60.dp)
        Spacer(Modifier.height(12.dp))
        PillButton("Settings", onSettings, Modifier.fillMaxWidth(), icon = Icons.Filled.Settings, height = 60.dp)
        Spacer(Modifier.height(12.dp))
        PillButton("About", onAbout, Modifier.fillMaxWidth(), icon = Icons.Filled.Info, height = 60.dp)
        Spacer(Modifier.weight(1.2f))
    }
}

// ---- Settings ----

@Composable
fun SettingsScreen(store: Store, onBack: () -> Unit) {
    val settings by store.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Header("Settings", onBack) {
            IconTap(Icons.Filled.Refresh, "Reset settings", { store.updateSettings { Settings() } }, enabled = settings != Settings())
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SwitchRow("Vibration", "A light buzz when a button is pressed.", settings.vibration) { on ->
                store.updateSettings { it.copy(vibration = on) }
            }
            SwitchRow("Double tap", "Check, call and fold also answer to a double tap.", settings.doubleTap) { on ->
                store.updateSettings { it.copy(doubleTap = on) }
            }
            SwitchRow("Sound", "Play the table's sound effects.", settings.sound) { on ->
                store.updateSettings { it.copy(sound = on) }
            }
            SwitchRow("Turn sound", "A soft note each time the turn passes to the next player.", settings.turnSound) { on ->
                store.updateSettings { it.copy(turnSound = on) }
            }
            SwitchRow("Stacks in big blinds", "Show each stack as a number of big blinds instead of chips.", settings.stacksInBlinds) { on ->
                store.updateSettings { it.copy(stacksInBlinds = on) }
            }
            Choice("Long press duration", "Seconds an action button has to be held before it counts.") {
                Stepper(
                    text = String.format(Locale.US, "%.1f", settings.holdTenths / 10f),
                    onMinus = { store.updateSettings { it.copy(holdTenths = it.holdTenths - 1) } },
                    onPlus = { store.updateSettings { it.copy(holdTenths = it.holdTenths + 1) } },
                    canMinus = settings.holdTenths > 0,
                    canPlus = settings.holdTenths < Settings.MAX_HOLD_TENTHS,
                )
            }
            Choice("Raise slider step size", "What one notch of the slider in the raise dialog is worth.") {
                Segmented(
                    listOf("Big blind (BB)", "Small blind (SB)"),
                    selected = if (settings.sliderStep == Step.SB) 1 else 0,
                    onSelect = { i -> store.updateSettings { it.copy(sliderStep = if (i == 1) Step.SB else Step.BB) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Choice("Raise +/− buttons amount", "What the + and − buttons in the raise dialog add or take away.") {
                Segmented(
                    listOf("BB", "SB", "1"),
                    selected = settings.buttonStep.ordinal,
                    onSelect = { i -> store.updateSettings { it.copy(buttonStep = Step.entries[i]) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Choice("Table style", "The colour of the felt.", divider = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Previous", { store.updateSettings { it.copy(felt = (it.felt - 1).mod(felts.size)) } })
                    Spacer(Modifier.width(28.dp))
                    TableFelt(felt(settings.felt), Modifier.size(width = 124.dp, height = 210.dp))
                    Spacer(Modifier.width(28.dp))
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowForward, "Next", { store.updateSettings { it.copy(felt = (it.felt + 1).mod(felts.size)) } })
                }
                Spacer(Modifier.height(12.dp))
                Text(felt(settings.felt).name, style = type(15, FontWeight.Medium, Ink.Dim))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    felts.indices.forEach { i ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == settings.felt.mod(felts.size)) Ink.White else Ink.Line))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, about: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = type(19, FontWeight.Bold))
            Text(about, style = type(15, color = Ink.Dim))
        }
        Toggle(checked, onChange)
    }
    HorizontalDivider(color = Ink.Raised, thickness = 2.dp)
}

@Composable
private fun Choice(title: String, about: String, divider: Boolean = true, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, style = type(19, FontWeight.Bold))
        Text(about, style = type(15, color = Ink.Dim))
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { control() }
    }
    if (divider) HorizontalDivider(color = Ink.Raised, thickness = 2.dp)
}

// ---- About ----

@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Header("About", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AppMark(88.dp)
            Spacer(Modifier.height(14.dp))
            Text("xrnchips", style = type(28, FontWeight.Black))
            Text("Version ${BuildConfig.VERSION_NAME}", style = type(15, color = Ink.Dim))
            Spacer(Modifier.height(22.dp))
            Text(
                "Keeps the stacks, blinds and pots of a home poker game, so the table only needs a deck of cards. " +
                    "One phone is passed around or left in the middle; each player acts on their turn.",
                style = type(16, color = Ink.Dim),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No ads, no accounts and no network access. Games are saved on this phone only.",
                style = type(16, color = Ink.Dim),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            PillButton("Licenses", onLicenses, Modifier.fillMaxWidth(), icon = Icons.Filled.MenuBook, height = 60.dp)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val notices = listOf(
        "MiSans" to "The typeface is MiSans Latin by Xiaomi (hyperos.mi.com/font), included unmodified.",
        "Jetpack Compose and AndroidX" to "Copyright The Android Open Source Project. Licensed under the Apache License, Version 2.0.",
        "Material Icons" to "Copyright Google LLC. Licensed under the Apache License, Version 2.0.",
        "Kotlin, kotlinx.coroutines and kotlinx.serialization" to "Copyright JetBrains s.r.o. and contributors. Licensed under the Apache License, Version 2.0.",
        "Sound effects and artwork" to "Made for this app: the sounds are synthesised by a script in the project, and the table, chips and cards are drawn in code.",
    )
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Header("Licenses", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            notices.forEach { (name, text) ->
                Spacer(Modifier.height(18.dp))
                Text(name, style = type(18, FontWeight.Bold))
                Text(text, style = type(15, color = Ink.Dim))
            }
            Spacer(Modifier.height(14.dp))
            Text("The Apache License 2.0 is at apache.org/licenses/LICENSE-2.0.", style = type(14, color = Ink.Faint))
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ---- Load ----

@Composable
fun LoadScreen(store: Store, onBack: () -> Unit, onPlay: (SavedGame) -> Unit) {
    val saves by store.saves.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<String?>(null) }
    var deletingAll by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Header("Load", onBack) {
                IconTap(Icons.Filled.Delete, "Delete all", { deletingAll = true }, enabled = saves.isNotEmpty())
            }
            if (saves.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No saved games yet.", style = type(17, color = Ink.Dim))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(saves, key = { it.id }) { save ->
                        SaveCard(save, onPlay = { onPlay(save) }, onDelete = { deleting = save.id })
                        HorizontalDivider(color = Ink.Raised, thickness = 2.dp)
                    }
                }
            }
        }
        deleting?.let { id ->
            Confirm(
                "Delete?",
                "This saved game will be lost.",
                onConfirm = {
                    store.delete(id)
                    deleting = null
                },
                onClose = { deleting = null },
            )
        }
        if (deletingAll) {
            Confirm(
                "Delete all?",
                "Every saved game will be deleted.",
                onConfirm = {
                    store.deleteAll()
                    deletingAll = false
                },
                onClose = { deletingAll = false },
            )
        }
    }
}

private fun streetName(street: Street): String = when (street) {
    Street.PREFLOP -> "Preflop"
    Street.FLOP -> "Flop"
    Street.TURN -> "Turn"
    Street.RIVER -> "River"
}

@Composable
private fun SaveCard(save: SavedGame, onPlay: () -> Unit, onDelete: () -> Unit) {
    val state = save.state
    val time = remember(save.savedAt) { SimpleDateFormat("EEE, MMM d  HH:mm", Locale.getDefault()).format(Date(save.savedAt)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (save.auto) {
            Text("Autosave", style = type(23, FontWeight.Bold))
            Text(time, style = type(15, color = Ink.Dim))
        } else {
            Text(time, style = type(23, FontWeight.Bold))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.Surface).padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Fact("Street", if (state.phase == Phase.GAME_OVER) "Finished" else streetName(state.street))
                Fact("Pot", state.pot.toString())
                Fact("BB", state.level.big.toString())
                Fact("SB", state.level.small.toString())
                if (state.level.ante > 0) Fact("Ante", state.level.ante.toString())
                Fact("Hand", state.hand.toString())
            }
            Spacer(Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                state.players.forEach { Fact(it.name, it.chips.toString()) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PillButton("Play", onPlay, Modifier.weight(1f), tone = Tone.PRIMARY, icon = Icons.Filled.PlayArrow)
            PillButton("Delete", onDelete, Modifier.weight(1f), tone = Tone.DANGER)
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text("$label:", style = type(16, FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f))
        Text(value, style = type(16), maxLines = 1)
    }
}
