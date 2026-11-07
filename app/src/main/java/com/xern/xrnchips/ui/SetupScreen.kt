package com.xern.xrnchips.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xern.xrnchips.data.Store
import com.xern.xrnchips.domain.AnteStyle
import com.xern.xrnchips.domain.BlindLevel
import com.xern.xrnchips.domain.DurationUnit
import com.xern.xrnchips.domain.GameSetup
import com.xern.xrnchips.domain.Limit
import com.xern.xrnchips.domain.MAX_LEVELS

/** The Play screen: stacks, blind levels and who sits where. It is kept as it was left. */
@Composable
fun SetupScreen(store: Store, feltIndex: Int, onBack: () -> Unit, onStart: (GameSetup) -> Unit) {
    val setup by store.setup.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val focus = LocalFocusManager.current
    var editingSeat by remember { mutableStateOf<Int?>(null) }
    var resetting by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Header("Play", onBack) {
                IconTap(
                    Icons.Filled.Refresh,
                    "Reset",
                    {
                        focus.clearFocus()
                        resetting = true
                    },
                    enabled = !setup.isDefault,
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(14.dp))
                Text("Starting chips", style = type(21, FontWeight.Bold))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(
                        setup.startingChips,
                        { value -> store.updateSetup { it.withStartingChips(value) } },
                        Modifier.size(width = 150.dp, height = 50.dp),
                        style = type(21, FontWeight.Bold),
                    )
                    Text("×", style = type(20, color = Ink.Dim), modifier = Modifier.padding(horizontal = 10.dp))
                    ChipGlyph(Modifier.size(30.dp), marks = Ink.Felt)
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Ink.Raised, thickness = 2.dp)

                Spacer(Modifier.height(18.dp))
                Text("Blind structure", style = type(21, FontWeight.Bold))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton(
                        "Add level",
                        { store.updateSetup { it.addLevel() } },
                        height = 48.dp,
                        icon = Icons.Filled.Add,
                        enabled = setup.levels.size < MAX_LEVELS,
                    )
                    Spacer(Modifier.weight(1f))
                    Segmented(
                        listOf("Hands", "Minutes"),
                        selected = setup.unit.ordinal,
                        onSelect = { i ->
                            // A length still being typed belongs to the old unit: let it land before the switch.
                            focus.clearFocus()
                            store.updateSetup { it.withUnit(DurationUnit.entries[i]) }
                        },
                        modifier = Modifier.width(188.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp)) {
                    listOf("Big", "Small", "Ante", "Duration").forEach {
                        Text(it, style = type(13, FontWeight.Medium, Ink.Dim), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.width(48.dp))
                }
                // The list grows and shrinks smoothly as levels come and go.
                Column(Modifier.animateContentSize(tween(220))) {
                    setup.levels.forEachIndexed { index, level ->
                        LevelRow(
                            level = level,
                            last = index == setup.levels.lastIndex,
                            removable = setup.levels.size > 1,
                            onBig = { value -> store.updateSetup { it.withBig(index, value) } },
                            onSmall = { value -> store.updateSetup { it.withSmall(index, value) } },
                            onAnte = { value -> store.updateSetup { it.withAnte(index, value) } },
                            onDuration = { value -> store.updateSetup { it.withDuration(index, value) } },
                            onRemove = {
                                focus.clearFocus()
                                store.updateSetup { it.removeLevel(index) }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Rule("Ante paid by") {
                    Segmented(
                        listOf("Everyone", "Big blind"),
                        selected = setup.anteStyle.ordinal,
                        onSelect = { i -> store.updateSetup { it.copy(anteStyle = AnteStyle.entries[i]) } },
                        modifier = Modifier.width(214.dp),
                    )
                }
                Rule("Bet limit") {
                    Segmented(
                        listOf("No limit", "Pot limit"),
                        selected = setup.limit.ordinal,
                        onSelect = { i -> store.updateSetup { it.copy(limit = Limit.entries[i]) } },
                        modifier = Modifier.width(214.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Ink.Raised, thickness = 2.dp)

                Spacer(Modifier.height(18.dp))
                Text("Players", style = type(21, FontWeight.Bold))
                Spacer(Modifier.height(12.dp))
                SeatMap(feltIndex, Modifier.padding(horizontal = 28.dp)) { seat ->
                    SeatButton(setup.playerAt(seat)?.name, onClick = {
                        focus.clearFocus()
                        editingSeat = seat
                    })
                }
                Spacer(Modifier.height(24.dp))
            }
            val seated = setup.players.size
            PillButton(
                "Start",
                {
                    focus.clearFocus()
                    onStart(store.setup.value)
                },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                tone = Tone.ACCENT,
                enabled = seated >= 2,
                height = 60.dp,
                onDisabledClick = { toast(if (seated == 1) "Add one more player." else "Add at least two players.") },
            )
        }

        if (resetting) {
            Confirm(
                "Reset?",
                "The stacks, blinds, rules and players go back to how a new game starts.",
                onConfirm = {
                    store.updateSetup { GameSetup() }
                    resetting = false
                },
                onClose = { resetting = false },
            )
        }

        editingSeat?.let { seat ->
            val current = setup.playerAt(seat)?.name
            NameDialog(
                initial = current,
                taken = { name -> setup.nameTaken(name, exceptSeat = seat) },
                onSave = { name ->
                    store.updateSetup { it.withPlayer(seat, name) }
                    editingSeat = null
                },
                onDelete = {
                    store.updateSetup { it.withoutPlayer(seat) }
                    editingSeat = null
                },
                onClose = { editingSeat = null },
            )
        }
    }
}

/** A rule of the game with its choices beside it. */
@Composable
private fun Rule(name: String, choices: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = type(16, FontWeight.Medium), modifier = Modifier.weight(1f))
        choices()
    }
}

@Composable
private fun LevelRow(
    level: BlindLevel,
    last: Boolean,
    removable: Boolean,
    onBig: (Int?) -> Unit,
    onSmall: (Int?) -> Unit,
    onAnte: (Int?) -> Unit,
    onDuration: (Int?) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell { NumberField(level.big, onBig, Modifier.fillMaxWidth().height(42.dp)) }
        Cell { NumberField(level.small, onSmall, Modifier.fillMaxWidth().height(42.dp)) }
        Cell { NumberField(level.ante, onAnte, Modifier.fillMaxWidth().height(42.dp)) }
        Cell {
            if (last) {
                Icon(Icons.Filled.AllInclusive, contentDescription = "No end", tint = Ink.Faint, modifier = Modifier.size(28.dp))
            } else {
                NumberField(level.duration, onDuration, Modifier.fillMaxWidth().height(42.dp), maxDigits = 3)
            }
        }
        IconTap(Icons.Filled.Cancel, "Remove level", onRemove, enabled = removable, tint = Ink.Red)
    }
}

@Composable
private fun RowScope.Cell(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) { content() }
}

/**
 * Names a seat. A new player gets Add; an existing one gets Update, which waits for the name to
 * change, and Delete. Names are compared without regard to case.
 */
@Composable
fun NameDialog(
    initial: String?,
    taken: (String) -> Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.orEmpty()) }
    val keyboard = remember { FocusRequester() }
    LaunchedEffect(Unit) { keyboard.requestFocus() }
    val clean = name.trim()
    val clash = clean.isNotEmpty() && taken(clean)
    val ready = clean.isNotEmpty() && !clash && clean != initial
    Modal(onDismiss = onClose) {
        ModalBar(right = { CloseIcon(onClose) })
        NameField(name, { name = it }, Modifier.fillMaxWidth(0.82f).focusRequester(keyboard), onDone = { if (ready) onSave(clean) })
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
            if (clash) Text("That name is taken.", style = type(15, color = Ink.Red))
        }
        if (initial == null) {
            PillButton("Add", { onSave(clean) }, Modifier.fillMaxWidth(), tone = Tone.PRIMARY, enabled = ready)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Update", { onSave(clean) }, Modifier.weight(1f), tone = Tone.PRIMARY, enabled = ready)
                PillButton("Delete", onDelete, Modifier.weight(1f), tone = Tone.DANGER)
            }
        }
    }
}
