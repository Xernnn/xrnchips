package com.xern.xrnchips.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xern.xrnchips.data.Settings
import com.xern.xrnchips.data.Step
import com.xern.xrnchips.domain.BlindLevel
import com.xern.xrnchips.domain.GameState
import com.xern.xrnchips.domain.MAX_CHIPS
import com.xern.xrnchips.domain.RaiseSpec
import com.xern.xrnchips.domain.canAddChips
import com.xern.xrnchips.domain.canLeave
import com.xern.xrnchips.domain.grouped
import com.xern.xrnchips.domain.moneyResults
import com.xern.xrnchips.domain.nameTaken
import com.xern.xrnchips.domain.settle
import com.xern.xrnchips.domain.standings
import kotlin.math.roundToInt

/** Before the first hand: tap the player who deals, or let chance pick. */
@Composable
fun DealerDialog(game: GameState, feltIndex: Int, onPick: (Int) -> Unit, onRandom: () -> Unit) {
    Modal {
        Text("Dealer", style = type(30, FontWeight.ExtraBold))
        Text("Pick who deals the first hand.", style = type(16, color = Ink.Dim))
        Spacer(Modifier.height(16.dp))
        SeatMap(feltIndex) { seat ->
            game.player(seat)?.let { player -> SeatButton(player.name, onClick = { onPick(seat) }) }
        }
        Spacer(Modifier.height(16.dp))
        PillButton("Randomize", onRandom, Modifier.fillMaxWidth(), tone = Tone.PRIMARY, icon = Icons.Filled.Casino)
    }
}

@Composable
fun MenuDialog(
    canUndo: Boolean,
    onInfo: () -> Unit,
    onUndo: () -> Unit,
    onManage: () -> Unit,
    onResults: () -> Unit,
    onSave: () -> Unit,
    onSettings: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
) {
    Modal(onDismiss = onClose) {
        ModalBar(left = { IconTap(Icons.Outlined.Info, "Hand rankings", onInfo) }, right = { CloseIcon(onClose) })
        val wide = Modifier.fillMaxWidth()
        PillButton("Undo", onUndo, wide, enabled = canUndo, icon = Icons.AutoMirrored.Filled.Undo)
        Spacer(Modifier.height(12.dp))
        PillButton("Manage", onManage, wide, icon = Icons.Filled.ManageAccounts)
        Spacer(Modifier.height(12.dp))
        PillButton("Results", onResults, wide, icon = Icons.Filled.Leaderboard)
        Spacer(Modifier.height(12.dp))
        PillButton("Save", onSave, wide, icon = Icons.Filled.Save)
        Spacer(Modifier.height(12.dp))
        PillButton("Settings", onSettings, wide, icon = Icons.Filled.Settings)
        Spacer(Modifier.height(12.dp))
        PillButton("Restart", onRestart, wide, tone = Tone.DANGER, icon = Icons.Filled.RestartAlt)
        Spacer(Modifier.height(12.dp))
        PillButton("Exit", onExit, wide, tone = Tone.DANGER, icon = Icons.AutoMirrored.Filled.ExitToApp)
    }
}

/**
 * Sizes a bet or raise. The amount is what goes in on top of the call. It can be typed, dragged on
 * the slider, nudged with + and -, or set from the shortcuts: multiples of the big blind for the
 * first raise before the flop, shares of the pot everywhere else.
 */
@Composable
fun RaiseDialog(spec: RaiseSpec, settings: Settings, onConfirm: (Int) -> Unit, onClose: () -> Unit) {
    var amount by remember { mutableIntStateOf(spec.min) }
    var editing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val notch = (if (settings.sliderStep == Step.SB) spec.small else spec.big).coerceAtLeast(1)
    val nudge = when (settings.buttonStep) {
        Step.BB -> spec.big
        Step.SB -> spec.small
        Step.ONE -> 1
    }.coerceAtLeast(1)
    val free = !spec.fixed && !editing
    val span = spec.max - spec.min

    @Composable
    fun RowScope.Shortcut(label: String, value: Int) {
        PillButton(label, { amount = value }, Modifier.weight(1f), enabled = free && value in spec.min..spec.max)
    }

    Modal(onDismiss = onClose, tapOutsideCloses = false) {
        ModalBar(right = { CloseIcon(onClose) })
        val display = Modifier.fillMaxWidth().height(96.dp)
        if (spec.fixed) {
            Box(display.clip(RoundedCornerShape(18.dp)).background(Ink.Black), contentAlignment = Alignment.Center) {
                Text(amount.grouped(), style = type(46, FontWeight.ExtraBold))
            }
        } else {
            NumberField(
                value = amount,
                onCommit = { amount = spec.clamp(it ?: spec.min) },
                modifier = display,
                style = type(46, FontWeight.ExtraBold),
                fill = Ink.Black,
                ink = Ink.White,
                shape = RoundedCornerShape(18.dp),
                onEditing = { editing = it },
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = if (span <= 0) 1f else (amount - spec.min).toFloat() / span,
            onValueChange = { t ->
                amount = if (t >= 0.999f) spec.max else (spec.min + (span * t / notch).roundToInt() * notch).coerceAtMost(spec.max)
            },
            enabled = free,
            colors = SliderDefaults.colors(
                thumbColor = Ink.White,
                activeTrackColor = Ink.Mint,
                inactiveTrackColor = Ink.Field,
                disabledThumbColor = Ink.Faint,
                disabledActiveTrackColor = Ink.Line,
                disabledInactiveTrackColor = Ink.Raised,
            ),
        )
        Spacer(Modifier.height(8.dp))
        val gap = Arrangement.spacedBy(12.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            PillButton(
                "", { amount = spec.clamp(amount - nudge) }, Modifier.weight(1f),
                enabled = free && amount > spec.min, icon = Icons.Filled.Remove, label = "Less",
            )
            PillButton(
                "", { amount = spec.clamp(amount + nudge) }, Modifier.weight(1f),
                enabled = free && amount < spec.max, icon = Icons.Filled.Add, label = "More",
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            PillButton("Min", { amount = spec.min }, Modifier.weight(1f), enabled = free)
            PillButton("Max", { amount = spec.max }, Modifier.weight(1f), enabled = free)
        }
        Spacer(Modifier.height(12.dp))
        if (spec.opening) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                Shortcut("2BB", spec.bigBlinds(20))
                Shortcut("2.5BB", spec.bigBlinds(25))
                Shortcut("3BB", spec.bigBlinds(30))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                Shortcut("1/3", spec.potShare(1, 3))
                Shortcut("1/2", spec.potShare(1, 2))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                Shortcut("3/4", spec.potShare(3, 4))
                Shortcut("Pot", spec.potShare(1, 1))
            }
        }
        Spacer(Modifier.height(18.dp))
        val allIn = amount >= spec.stack
        PillButton(
            text = when {
                allIn -> "All in"
                spec.firstBet -> "Bet"
                else -> "Raise"
            },
            onClick = {
                focus.clearFocus()
                onConfirm(amount)
            },
            modifier = Modifier.fillMaxWidth(),
            tone = if (allIn) Tone.DANGER else Tone.WARM,
            enabled = !editing,
            height = 60.dp,
        )
        if (spec.toCall > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Total: ${(spec.toCall + amount).grouped()} (incl. ${spec.toCall.grouped()} call)",
                style = type(15, color = Ink.Dim),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The seats mid-game: tap a player to top them up or send them home, an empty seat to buy someone in. */
@Composable
fun ManageDialog(game: GameState, feltIndex: Int, onSeat: (Int) -> Unit, onBack: () -> Unit, onClose: () -> Unit) {
    Modal(onDismiss = onClose) {
        ModalBar(left = { BackIcon(onBack) }, right = { CloseIcon(onClose) })
        SeatMap(feltIndex) { seat ->
            val player = game.player(seat)
            SeatButton(player?.name, onClick = { onSeat(seat) }, faded = player?.leaving == true)
        }
    }
}

@Composable
fun SeatDialog(game: GameState, seat: Int, model: TableModel, onBack: () -> Unit, onClose: () -> Unit) {
    val player = game.player(seat)
    val toast = LocalToast.current
    val focus = LocalFocusManager.current
    val buyIn = game.setup.startingChips
    var chips by remember { mutableIntStateOf(buyIn) }
    val panel = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.Black).padding(16.dp)

    @Composable
    fun ChipsRow(enabled: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                value = chips,
                onCommit = { chips = (it ?: buyIn).coerceIn(1, MAX_CHIPS) },
                modifier = Modifier.size(width = 150.dp, height = 50.dp),
                enabled = enabled,
                style = type(21, FontWeight.Bold),
            )
            Text("×", style = type(20, color = Ink.Dim), modifier = Modifier.padding(horizontal = 10.dp))
            ChipGlyph(Modifier.size(30.dp))
        }
    }

    Modal(onDismiss = onClose) {
        ModalBar(left = { BackIcon(onBack) }, right = { CloseIcon(onClose) })
        if (player != null) {
            val canAdd = game.canAddChips(seat)
            Text(player.name, style = type(28, FontWeight.ExtraBold))
            Spacer(Modifier.height(12.dp))
            Column(panel, horizontalAlignment = Alignment.CenterHorizontally) {
                ChipsRow(canAdd)
                Spacer(Modifier.height(14.dp))
                PillButton(
                    "Add chips",
                    {
                        focus.clearFocus()
                        model.addChips(seat, chips)
                        onClose()
                    },
                    Modifier.fillMaxWidth(0.8f),
                    tone = Tone.PRIMARY,
                    enabled = canAdd,
                    onDisabledClick = { toast("${player.name} has to fold before chips can be added.") },
                )
            }
            Spacer(Modifier.height(14.dp))
            PillButton(
                "Leave table",
                {
                    if (game.canLeave(seat)) {
                        model.leave(seat)
                        toast("${player.name} leaves the table after this hand.")
                        onClose()
                    } else {
                        toast("Two players have to stay at the table.")
                    }
                },
                Modifier.fillMaxWidth(),
                tone = Tone.DANGER,
                icon = Icons.Filled.PersonRemove,
            )
        } else {
            var name by remember { mutableStateOf("") }
            val keyboard = remember { FocusRequester() }
            LaunchedEffect(Unit) { keyboard.requestFocus() }
            val clean = name.trim()
            val clash = clean.isNotEmpty() && game.nameTaken(clean)
            Column(panel, horizontalAlignment = Alignment.CenterHorizontally) {
                NameField(name, { name = it }, Modifier.fillMaxWidth(0.82f).focusRequester(keyboard))
                Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                    if (clash) Text("That name is taken.", style = type(15, color = Ink.Red))
                }
                ChipsRow(true)
            }
            Spacer(Modifier.height(14.dp))
            PillButton(
                "Buy-in",
                {
                    focus.clearFocus()
                    model.addPlayer(seat, clean, chips)
                    onClose()
                },
                Modifier.fillMaxWidth(),
                tone = Tone.PRIMARY,
                enabled = clean.isNotEmpty() && !clash,
                icon = Icons.Filled.PersonAdd,
            )
        }
    }
}

/**
 * The app cannot see the cards, so the table says who won. Each pot lists who can win it; more
 * than one winner splits it. A pot only one player can win is already theirs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShowdownDialog(
    game: GameState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onInfo: () -> Unit,
    onDone: (List<List<Int>>) -> Unit,
) {
    val picks = remember(game.hand, game.pots) {
        mutableStateListOf<Set<Int>>().apply {
            addAll(game.pots.map { if (it.eligible.size == 1) it.eligible.toSet() else emptySet() })
        }
    }
    Modal(scroll = false) {
        ModalBar(
            left = { IconTap(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, enabled = canUndo) },
            right = { IconTap(Icons.Outlined.Info, "Hand rankings", onInfo) },
        )
        Text("Showdown", style = type(30, FontWeight.ExtraBold))
        Text(if (game.pots.size > 1) "Choose who wins each pot." else "Choose who wins the pot.", style = type(16, color = Ink.Dim))
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Ink.Black)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            game.pots.forEachIndexed { index, pot ->
                val locked = pot.eligible.size == 1
                val chosen = picks.getOrElse(index) { emptySet() }
                Text(
                    "${if (index == 0) "Main pot" else "Side pot"}: ${pot.amount.grouped()}",
                    style = type(21, FontWeight.Bold, if (locked) Ink.Faint else Ink.White),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pot.eligible.forEach { seat ->
                        val on = seat in chosen
                        WinnerChoice(game.player(seat)?.name.orEmpty(), on, locked) {
                            if (index < picks.size) picks[index] = if (on) chosen - seat else chosen + seat
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${chosen.size}/${pot.eligible.size} selected" + if (chosen.size > 1) " (split pot)" else "",
                    style = type(14, color = Ink.Dim),
                )
                if (index < game.pots.lastIndex) Spacer(Modifier.height(18.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton(
            "Next hand",
            { onDone(picks.map { it.toList() }) },
            Modifier.fillMaxWidth(),
            tone = Tone.ACCENT,
            enabled = picks.all { it.isNotEmpty() },
            height = 60.dp,
        )
    }
}

@Composable
private fun WinnerChoice(name: String, on: Boolean, locked: Boolean, onClick: () -> Unit) {
    val buzz = LocalBuzz.current
    val shape = RoundedCornerShape(14.dp)
    val ink by animateColorAsState(
        when {
            locked -> Ink.Faint
            on -> Ink.Black
            else -> Ink.White
        },
        tween(180),
        label = "winnerInk",
    )
    val fill by animateColorAsState(if (on && !locked) Ink.White else Ink.Raised, tween(180), label = "winnerFill")
    Row(
        Modifier
            .size(width = 134.dp, height = 52.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (on || locked) Color.Transparent else Ink.Line, shape)
            .clickable(enabled = !locked) {
                buzz(false)
                onClick()
            }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            Icon(Icons.Filled.Check, contentDescription = "Winner", tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(name, style = type(18, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One player has all the chips. */
@Composable
fun WinnerDialog(
    game: GameState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onResults: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val winner = game.player(game.winner)
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    // After it lands the cup keeps swaying a little.
    val sway by rememberInfiniteTransition(label = "cup").animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "sway",
    )
    Modal {
        ModalBar(right = { IconTap(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, enabled = canUndo) })
        Text("${winner?.name ?: "Nobody"} wins!", style = type(32, FontWeight.ExtraBold), textAlign = TextAlign.Center)
        Text("${(winner?.chips ?: 0).grouped()} chips", style = type(17, color = Ink.Dim))
        Spacer(Modifier.height(22.dp))
        Box(Modifier.size(150.dp).scale(pop.value).clip(CircleShape).background(Ink.Raised), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Ink.Amber, modifier = Modifier.size(92.dp).rotate(sway))
        }
        Spacer(Modifier.height(26.dp))
        PillButton("Results", onResults, Modifier.fillMaxWidth(), icon = Icons.Filled.Leaderboard)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Restart", onRestart, Modifier.weight(1f), tone = Tone.PRIMARY)
            PillButton("Exit", onExit, Modifier.weight(1f), tone = Tone.DANGER)
        }
    }
}

/**
 * After a hand that left someone without chips: each of them buys back in or leaves before the
 * next deal. Their seat and name wait for the answer.
 */
@Composable
fun RebuyDialog(game: GameState, canUndo: Boolean, onUndo: () -> Unit, onRebuy: (Int, Int) -> Unit, onLeave: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    val buyIn = game.setup.startingChips
    Modal {
        ModalBar(left = { IconTap(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, enabled = canUndo) })
        Text("Out of chips", style = type(30, FontWeight.ExtraBold))
        Text("Buy back in, or leave the table.", style = type(16, color = Ink.Dim))
        game.players.filter { it.broke }.forEach { player ->
            key(player.seat) {
                var chips by remember { mutableIntStateOf(buyIn) }
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.Black).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(player.name, style = type(24, FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(
                            value = chips,
                            onCommit = { chips = (it ?: buyIn).coerceIn(1, MAX_CHIPS) },
                            modifier = Modifier.size(width = 150.dp, height = 50.dp),
                            style = type(21, FontWeight.Bold),
                        )
                        Text("\u00d7", style = type(20, color = Ink.Dim), modifier = Modifier.padding(horizontal = 10.dp))
                        ChipGlyph(Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(
                            "Rebuy",
                            {
                                focus.clearFocus()
                                onRebuy(player.seat, chips)
                            },
                            Modifier.weight(1f),
                            tone = Tone.PRIMARY,
                        )
                        PillButton("Leave", { onLeave(player.seat) }, Modifier.weight(1f), tone = Tone.DANGER)
                    }
                }
            }
        }
    }
}

/**
 * Who is up and who is down, from what each person bought and what they hold. With a price for
 * the starting stack it also says who pays whom, in as few payments as will do.
 */
@Composable
fun ResultsDialog(game: GameState, price: Int, onPrice: (Int) -> Unit, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val standings = game.standings()
    val stack = game.setup.startingChips
    Modal(onDismiss = onClose, scroll = false) {
        ModalBar(left = onBack?.let { { BackIcon(it) } }, right = { CloseIcon(onClose) })
        Text("Results", style = type(30, FontWeight.ExtraBold))
        if (game.pot > 0) {
            Text(
                "The hand in play is not counted: chips in its pot are still their owners'.",
                style = type(14, color = Ink.Dim),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.Black).padding(horizontal = 14.dp, vertical = 10.dp)) {
                ResultRow("", "Paid in", "Has", "Result", Ink.Dim, Ink.Dim, FontWeight.Medium)
                standings.forEach { standing ->
                    ResultRow(
                        name = standing.name,
                        paidIn = standing.paidIn.grouped(),
                        holds = standing.holds.grouped(),
                        net = signed(standing.net),
                        ink = if (standing.seated) Ink.White else Ink.Dim,
                        netInk = netInk(standing.net),
                        weight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Settle up", style = type(21, FontWeight.Bold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${stack.grouped()} chips cost", style = type(16, color = Ink.Dim), modifier = Modifier.weight(1f))
                NumberField(
                    value = price,
                    onCommit = { onPrice(it ?: 0) },
                    modifier = Modifier.size(width = 150.dp, height = 46.dp),
                    maxDigits = 8,
                    style = type(19, FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (price <= 0) {
                Text(
                    "Enter what a starting stack costs to see who pays whom.",
                    style = type(15, color = Ink.Dim),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val money = moneyResults(standings, price, stack)
                val payments = settle(money)
                if (payments.isEmpty()) {
                    Text("Nobody owes anything.", style = type(16, color = Ink.Dim), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                payments.forEach { payment ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Raised).padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(payment.from, style = type(17, FontWeight.Bold), maxLines = 1)
                        Text(" pays ", style = type(16, color = Ink.Dim))
                        Text(payment.to, style = type(17, FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f))
                        Text(payment.amount.grouped(), style = type(18, FontWeight.ExtraBold, Ink.Mint), maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun signed(amount: Int): String = if (amount > 0) "+${amount.grouped()}" else amount.grouped()

private fun netInk(amount: Int): Color = when {
    amount > 0 -> Ink.Mint
    amount < 0 -> Ink.Red
    else -> Ink.Dim
}

@Composable
private fun ResultRow(name: String, paidIn: String, holds: String, net: String, ink: Color, netInk: Color, weight: FontWeight) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = type(16, weight, ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.25f))
        Text(paidIn, style = type(15, color = ink), maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(holds, style = type(15, color = ink), maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(net, style = type(16, weight, netInk), maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
    }
}

/** Shown for a moment when a hand opens a higher blind level. It does not block the table. */
@Composable
fun BlindsNotice(level: BlindLevel) {
    val pop = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier.scale(pop.value).clip(shape).background(Ink.Surface).border(1.dp, Ink.Line, shape).padding(horizontal = 30.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Ink.Amber, modifier = Modifier.size(30.dp))
                Text("Blinds", style = type(26, FontWeight.ExtraBold), modifier = Modifier.padding(horizontal = 18.dp))
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Ink.Amber, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("${level.big.grouped()} / ${level.small.grouped()}", style = type(30, FontWeight.Bold))
            if (level.ante > 0) Text("Ante ${level.ante.grouped()}", style = type(17, color = Ink.Dim))
        }
    }
}

/** A hand of poker by name, with an example. [counted] cards make the hand; the rest are along for the ride. */
private class Ranking(val name: String, val about: String, val cards: String, val counted: Int)

private val rankings = listOf(
    Ranking("Royal flush", "The five highest cards, all of one suit.", "As Ks Qs Js 10s", 5),
    Ranking("Straight flush", "Five cards in a row, all of one suit.", "9h 8h 7h 6h 5h", 5),
    Ranking("Four of a kind", "Four cards of one rank.", "Qs Qh Qd Qc 4s", 4),
    Ranking("Full house", "Three of one rank and two of another.", "Js Jh Jd 8c 8s", 5),
    Ranking("Flush", "Five cards of one suit, in any order.", "Kd 10d 8d 5d 3d", 5),
    Ranking("Straight", "Five cards in a row, suits mixed.", "8c 7d 6s 5h 4s", 5),
    Ranking("Three of a kind", "Three cards of one rank.", "7s 7h 7d Kc 2s", 3),
    Ranking("Two pair", "Two cards of one rank and two of another.", "As Ad 9h 9c 5s", 4),
    Ranking("Pair", "Two cards of one rank.", "10h 10s Ad 8c 3s", 2),
    Ranking("High card", "None of the above: the highest card plays.", "As Jd 8h 6c 2s", 1),
)

/** The ten hands from best to worst, for settling who won. */
@Composable
fun RankingsDialog(onClose: () -> Unit) {
    Modal(onDismiss = onClose, scroll = false) {
        Text("Hand rankings", style = type(26, FontWeight.ExtraBold))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            rankings.forEachIndexed { index, ranking ->
                Text("${index + 1}. ${ranking.name}", style = type(19, FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ranking.cards.split(" ").forEachIndexed { i, card ->
                        CardFace(card.dropLast(1), card.last(), muted = i >= ranking.counted, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(ranking.about, style = type(14, color = Ink.Dim))
                if (index < rankings.lastIndex) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Ink.Line)
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton("Close", onClose, Modifier.fillMaxWidth(), tone = Tone.PRIMARY)
    }
}
