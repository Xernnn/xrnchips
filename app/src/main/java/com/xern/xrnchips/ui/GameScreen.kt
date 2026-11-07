package com.xern.xrnchips.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xern.xrnchips.data.Settings
import com.xern.xrnchips.domain.Act
import com.xern.xrnchips.domain.BlindLevel
import com.xern.xrnchips.domain.Clock
import com.xern.xrnchips.domain.GameState
import com.xern.xrnchips.domain.Phase
import com.xern.xrnchips.domain.Player
import com.xern.xrnchips.domain.Street
import com.xern.xrnchips.domain.clock
import com.xern.xrnchips.domain.grouped
import com.xern.xrnchips.domain.options
import com.xern.xrnchips.domain.raiseSpec
import kotlinx.coroutines.delay
import java.util.Locale
import kotlinx.coroutines.launch

/** What is open over the table. */
sealed interface Sheet {
    data object Menu : Sheet

    /** The hand rankings, closing back to whatever they were opened from. */
    data class Rankings(val back: Sheet?) : Sheet
    data class Results(val back: Sheet?) : Sheet
    data object Raise : Sheet
    data object Manage : Sheet
    data class Seat(val seat: Int) : Sheet
    data class Ask(val title: String, val text: String, val back: Sheet?, val run: () -> Unit) : Sheet
}

private const val DOUBLE_TAP_MILLIS = 350L

@Composable
fun GameScreen(model: TableModel, settings: Settings, onSettings: () -> Unit, onExit: () -> Unit) {
    val game = model.game ?: return
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val toast = LocalToast.current

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val betting = game.phase == Phase.BETTING && !model.busy
    BackHandler { sheet = if (sheet == null && betting) Sheet.Menu else null }

    fun askUndo(back: Sheet?) {
        sheet = Sheet.Ask("Undo?", "The last move will be taken back.", back) { model.undo() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp)) {
                MenuButton(enabled = betting, onClick = { sheet = Sheet.Menu }, modifier = Modifier.align(Alignment.CenterStart))
                // The street slides in from below as the hand moves on.
                AnimatedContent(
                    targetState = if (game.phase == Phase.SHOWDOWN) "Showdown" else streetTitle(game.street),
                    transitionSpec = { (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 2 }) togetherWith fadeOut(tween(120)) },
                    modifier = Modifier.align(Alignment.Center),
                    label = "street",
                ) { title ->
                    Text(title, style = type(28, FontWeight.ExtraBold))
                }
            }
            Table(
                game = game,
                felt = felt(settings.felt),
                inBlinds = settings.stacksInBlinds,
                onClock = model::togglePause,
                modifier = Modifier.weight(1f),
            )
            ActionBar(
                game = game,
                settings = settings,
                enabled = betting,
                onCheckOrCall = model::checkOrCall,
                onRaise = { sheet = Sheet.Raise },
                onFold = model::fold,
            )
        }

        when (game.phase) {
            Phase.DEALER -> DealerDialog(
                game = game,
                feltIndex = settings.felt,
                onPick = model::dealer,
                onRandom = {
                    val pick = game.players.random()
                    model.dealer(pick.seat)
                    toast("${pick.name} deals first.")
                },
            )
            Phase.SHOWDOWN -> ShowdownDialog(
                game = game,
                canUndo = model.canUndo,
                onUndo = { askUndo(null) },
                onInfo = { sheet = Sheet.Rankings(null) },
                onDone = model::award,
            )
            Phase.REBUY -> RebuyDialog(
                game = game,
                canUndo = model.canUndo,
                onUndo = { askUndo(null) },
                onRebuy = model::rebuy,
                onLeave = model::giveUp,
            )
            Phase.GAME_OVER -> WinnerDialog(
                game = game,
                canUndo = model.canUndo,
                onUndo = { askUndo(null) },
                onResults = { sheet = Sheet.Results(null) },
                onRestart = model::restart,
                onExit = onExit,
            )
            else -> {}
        }

        when (val open = sheet) {
            Sheet.Menu -> MenuDialog(
                canUndo = model.canUndo,
                onInfo = { sheet = Sheet.Rankings(Sheet.Menu) },
                onUndo = { askUndo(Sheet.Menu) },
                onManage = { sheet = Sheet.Manage },
                onResults = { sheet = Sheet.Results(Sheet.Menu) },
                onSave = {
                    if (model.save()) toast("Game saved.")
                    sheet = null
                },
                onSettings = {
                    sheet = null
                    onSettings()
                },
                onRestart = {
                    sheet = Sheet.Ask("Restart?", "The game starts over with the players and chips it began with.", Sheet.Menu) { model.restart() }
                },
                onExit = {
                    sheet = Sheet.Ask("Exit?", "You go back to the start screen. The game stays under Load as the autosave.", Sheet.Menu) { onExit() }
                },
                onClose = { sheet = null },
            )
            is Sheet.Rankings -> RankingsDialog(onClose = { sheet = open.back })
            is Sheet.Results -> ResultsDialog(
                game = game,
                price = settings.price,
                onPrice = { price -> model.store.updateSettings { it.copy(price = price) } },
                onBack = open.back?.let { back -> { sheet = back } },
                onClose = { sheet = null },
            )
            Sheet.Raise -> {
                val spec = if (betting) game.raiseSpec() else null
                if (spec == null) {
                    LaunchedEffect(Unit) { sheet = null }
                } else {
                    key(game.hand, game.street, game.active) {
                        RaiseDialog(
                            spec = spec,
                            settings = settings,
                            onConfirm = { amount ->
                                sheet = null
                                model.raise(amount)
                            },
                            onClose = { sheet = null },
                        )
                    }
                }
            }
            Sheet.Manage -> ManageDialog(
                game = game,
                feltIndex = settings.felt,
                onSeat = { seat ->
                    val player = game.player(seat)
                    if (player?.leaving == true) toast("${player.name} is leaving after this hand.") else sheet = Sheet.Seat(seat)
                },
                onBack = { sheet = Sheet.Menu },
                onClose = { sheet = null },
            )
            is Sheet.Seat -> SeatDialog(
                game = game,
                seat = open.seat,
                model = model,
                onBack = { sheet = Sheet.Manage },
                onClose = { sheet = null },
            )
            is Sheet.Ask -> Confirm(
                title = open.title,
                text = open.text,
                onConfirm = {
                    sheet = null
                    open.run()
                },
                onClose = { sheet = null },
                onBack = open.back?.let { back -> { sheet = back } },
            )
            null -> {}
        }

        // The notice fades out with the level it was showing, after the model has let go of it.
        val raised = model.raisedBlinds
        val shown = remember { arrayOfNulls<BlindLevel>(1) }
        if (raised != null) shown[0] = raised
        AnimatedVisibility(visible = raised != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(320))) {
            shown[0]?.let { BlindsNotice(it) }
        }
    }
}

fun streetTitle(street: Street): String = when (street) {
    Street.PREFLOP -> "Preflop"
    Street.FLOP -> "Flop"
    Street.TURN -> "Turn"
    Street.RIVER -> "River"
}

@Composable
private fun MenuButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val buzz = LocalBuzz.current
    val color = if (enabled) Ink.White else Ink.Faint
    Row(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (enabled) Ink.Field else Ink.Raised)
            .clickable(enabled = enabled) {
                buzz(false)
                onClick()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Menu", style = type(16, FontWeight.Bold, color))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.Menu, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

// ---- The table ----

private val seatRows = listOf(0.19f, 0.5f, 0.81f)
private val seatHeight = 92.dp
private val betHeight = 24.dp
private val dealerSize = 24.dp

@Composable
private fun Table(game: GameState, felt: Felt, inBlinds: Boolean, onClock: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        val w = maxWidth
        val h = maxHeight
        val seatWidth = (w * 0.29f).coerceAtMost(132.dp)
        val density = LocalDensity.current

        TableFelt(felt, Modifier.fillMaxSize().padding(horizontal = w * 0.13f, vertical = h * 0.05f))

        // The middle of the table, top to bottom: the blinds, the board, the pot.
        val middle = (w - seatWidth * 2 - 22.dp).coerceAtMost(164.dp)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Stakes(game, onClock, Modifier.width(middle))
            Spacer(Modifier.height(10.dp))
            Board(game, middle)
            Spacer(Modifier.height(10.dp))
            PotPill(game.pot)
        }

        for (player in game.players) {
            val (x, y) = seatOffset(player.seat, w, h, seatWidth, seatHeight, seatRows)
            // Keyed by seat so a card keeps its own state when a neighbour leaves the table.
            key(player.seat) {
                SeatCard(
                    player = player,
                    active = game.phase == Phase.BETTING && game.active == player.seat,
                    bigBlind = if (inBlinds) game.level.big else 0,
                    modifier = Modifier.offset(x, y).size(seatWidth, seatHeight),
                )
                // The bet sits in front of the player: over the bottom seat, under every other.
                val betY = if (player.seat == 4) y - betHeight - 5.dp else y + seatHeight + 5.dp
                val toPot = with(density) {
                    IntOffset((w / 2 - x - seatWidth / 2).roundToPx(), (h / 2 - betY - betHeight / 2).roundToPx())
                }
                Box(Modifier.offset(x, betY).size(seatWidth, betHeight), contentAlignment = Alignment.Center) {
                    Bet(player.shown, toPot)
                }
            }
        }

        // One button for the whole table, so it travels from dealer to dealer between hands.
        val dealer = game.player(game.dealer)
        if (dealer != null && game.phase != Phase.DEALER) {
            val (x, y) = seatOffset(dealer.seat, w, h, seatWidth, seatHeight, seatRows)
            // It rides the top corner of the seat that faces the middle of the table.
            val cornerX = if (dealer.seat in 1..3) x - 9.dp else x + seatWidth - dealerSize + 9.dp
            val buttonX by animateDpAsState(cornerX, tween(520), label = "dealerX")
            val buttonY by animateDpAsState(y - 9.dp, tween(520), label = "dealerY")
            DealerButton(Modifier.offset(buttonX, buttonY))
        }
    }
}

/**
 * The blinds in play, the level that follows and what is left of this one: `3 HANDS`, a countdown,
 * or `NEXT HAND` once the level is used up. It lies on the felt above the board, where the whole
 * table can read it. Tapping it pauses or resumes a countdown.
 */
@Composable
private fun Stakes(game: GameState, onClock: () -> Unit, modifier: Modifier = Modifier) {
    val level = game.level
    val next = game.nextLevel
    val clock = game.clock()
    val faint = Ink.White.copy(alpha = 0.72f)
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = clock is Clock.Time, onClick = onClock)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BLINDS", style = type(10, FontWeight.Bold, Ink.White.copy(alpha = 0.6f)))
        val blinds = "${level.big.grouped()}/${level.small.grouped()}"
        AnimatedContent(
            targetState = blinds,
            transitionSpec = { (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 1.5f)) togetherWith fadeOut(tween(150)) },
            label = "blinds",
        ) { text ->
            Text(text, style = type(fitted(text), FontWeight.ExtraBold), maxLines = 1)
        }
        if (level.ante > 0) Text("Ante ${level.ante.grouped()}", style = type(12, FontWeight.Medium, faint), maxLines = 1)
        if (next != null) {
            Text("Next ${next.big.grouped()}/${next.small.grouped()}", style = type(12, FontWeight.Medium, faint), maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val text = when (clock) {
                    is Clock.Hands -> if (clock.left == 1) "1 HAND" else "${clock.left} HANDS"
                    is Clock.Time -> "%d:%02d".format(clock.seconds / 60, clock.seconds % 60)
                    else -> "NEXT HAND"
                }
                Text(text, style = type(12, FontWeight.Bold, Ink.Mint), maxLines = 1)
                if (clock is Clock.Time) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (clock.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (clock.paused) "Resume" else "Pause",
                        tint = if (clock.paused) Ink.Mint else Ink.Amber,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

/** The size the blinds are written at: smaller once the numbers grow long. */
private fun fitted(text: String): Int = when {
    text.length <= 9 -> 22
    text.length <= 12 -> 18
    else -> 15
}

/** Five places for the board. The app never sees the cards, so the ones that are out lie face down. */
@Composable
private fun Board(game: GameState, width: Dp) {
    val gap = 5.dp
    val cardWidth = (width - gap * 4) / 5
    val dealt = when {
        game.phase == Phase.DEALER || game.phase == Phase.HAND_END || game.phase == Phase.GAME_OVER -> 0
        game.street == Street.FLOP -> 3
        game.street == Street.TURN -> 4
        game.street == Street.RIVER -> 5
        else -> 0
    }
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(5) { index ->
            BoardCard(dealt = index < dealt, index = index, modifier = Modifier.size(cardWidth, cardWidth * 1.4f))
        }
    }
}

/** A card drops onto its place when it is dealt. The three of the flop land one after another. */
@Composable
private fun BoardCard(dealt: Boolean, index: Int, modifier: Modifier = Modifier) {
    val landed = remember { Animatable(if (dealt) 1f else 0f) }
    LaunchedEffect(dealt) {
        if (dealt) {
            if (index < 3) delay(index * 110L)
            landed.animateTo(1f, tween(280))
        } else {
            landed.snapTo(0f)
        }
    }
    Box(modifier) {
        CardSlot(Modifier.fillMaxSize())
        CardBack(
            Modifier.fillMaxSize().graphicsLayer {
                val t = landed.value
                alpha = t
                scaleX = 1.5f - 0.5f * t
                scaleY = 1.5f - 0.5f * t
                translationY = -(1f - t) * 22.dp.toPx()
            },
        )
    }
}

/** Every chip in the middle. The number counts up as bets come in and runs down as the pot is paid. */
@Composable
private fun PotPill(pot: Int) {
    val counted by animateIntAsState(pot, tween(380), label = "pot")
    val visible by animateFloatAsState(if (pot > 0) 1f else 0f, tween(260), label = "potAlpha")
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.34f)).padding(horizontal = 18.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("POT", style = type(10, FontWeight.Bold, Ink.White.copy(alpha = 0.6f)))
        Text(counted.grouped(), style = type(26, FontWeight.ExtraBold), modifier = Modifier.alpha(visible))
    }
}

/** A slow pulse for the outline of the seat whose turn it is. */
@Composable
private fun rememberPulse(): State<Float> =
    rememberInfiniteTransition(label = "turn").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )

@Composable
private fun SeatCard(player: Player, active: Boolean, bigBlind: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    // Out of the hand fades; waiting for the next one fades less.
    val strength by animateFloatAsState(
        when {
            player.folded -> 0.4f
            !player.inHand -> 0.7f
            else -> 1f
        },
        tween(320),
        label = "strength",
    )
    // The turn marker fades in and then breathes. Only the seat that has it runs the pulse.
    val turn by animateFloatAsState(if (active) 1f else 0f, tween(260), label = "turn")
    val pulse = if (active) rememberPulse() else null
    // Seats arrive one after another around the table.
    val arrived = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(player.seat * 40L)
        arrived.animateTo(1f, tween(280))
    }
    Column(
        modifier
            .graphicsLayer {
                alpha = arrived.value
                scaleX = 0.88f + 0.12f * arrived.value
                scaleY = 0.88f + 0.12f * arrived.value
            }
            .clip(shape)
            // The seat whose turn it is takes on a little of the ring's colour.
            .background(lerp(Ink.Surface, Ink.Mint, 0.14f * turn))
            .border(1.dp, Ink.Line, shape)
            .drawWithContent {
                drawContent()
                if (turn > 0f) {
                    // A glow that fades inwards from the edge (thin strokes piled up, each a little wider), then a thick solid ring on it.
                    val breath = pulse?.value ?: 1f
                    for (step in 1..6) {
                        val halo = (4 + step * 2).dp.toPx()
                        drawRoundRect(
                            color = Ink.Mint.copy(alpha = 0.07f * turn * breath),
                            topLeft = Offset(halo / 2, halo / 2),
                            size = Size(size.width - halo, size.height - halo),
                            cornerRadius = CornerRadius(16.dp.toPx() - halo / 2),
                            style = Stroke(halo),
                        )
                    }
                    val stroke = 4.5f.dp.toPx()
                    drawRoundRect(
                        color = Ink.Mint.copy(alpha = turn * (0.85f + 0.15f * breath)),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(16.dp.toPx() - stroke / 2),
                        style = Stroke(stroke),
                    )
                }
            }
            .padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.alpha(strength), verticalAlignment = Alignment.CenterVertically) {
            Text(
                player.name,
                style = type(17, FontWeight.Bold, lerp(Ink.White, Ink.Mint, turn)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (player.leaving) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = "Leaving", tint = Ink.Sky, modifier = Modifier.size(17.dp))
            }
        }
        // The stack counts to its new value, and swells for a moment when it grows: that is where the pot went.
        val counted by animateIntAsState(player.chips, tween(450), label = "chips")
        val swell = remember { Animatable(1f) }
        var before by remember { mutableIntStateOf(player.chips) }
        LaunchedEffect(player.chips) {
            val grew = player.chips > before
            before = player.chips
            if (grew) {
                swell.snapTo(1.32f)
                swell.animateTo(1f, tween(900))
            }
        }
        // The size goes by where the count will end, so the text does not shrink and grow on the way.
        val settled = stackText(player.chips, bigBlind)
        Text(
            stackText(counted, bigBlind),
            style = type(if (settled.length > 7) 17 else 21, FontWeight.ExtraBold),
            maxLines = 1,
            modifier = Modifier.alpha(strength).scale(swell.value),
        )
        ActTag(player.last)
    }
}

/** A stack as chips, or as big blinds when [bigBlind] is given: one decimal until it reaches a hundred. */
private fun stackText(chips: Int, bigBlind: Int): String {
    if (bigBlind <= 0) return chips.grouped()
    val blinds = chips.toDouble() / bigBlind
    val whole = blinds >= 100 || blinds == Math.floor(blinds)
    return String.format(Locale.US, if (whole) "%,.0f BB" else "%.1f BB", blinds)
}

@Composable
private fun DealerButton(modifier: Modifier = Modifier) {
    Box(
        modifier.size(dealerSize).clip(CircleShape).background(Ink.White).border(2.dp, Ink.Black, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("D", style = type(13, FontWeight.Black, Ink.Black))
    }
}

/** The blind posted or the last move, under the stack. An empty slot keeps the card's height steady. */
@Composable
private fun ActTag(act: Act) {
    val (text, fill, ink) = when (act) {
        Act.NONE -> Triple("", Ink.Black.copy(alpha = 0.35f), Ink.White)
        Act.SB -> Triple("SB", Ink.Field, Ink.Sky)
        Act.BB -> Triple("BB", Ink.Field, Ink.Sky)
        Act.CHECK -> Triple("Check", Ink.Mint.copy(alpha = 0.18f), Ink.Mint)
        Act.CALL -> Triple("Call", Ink.Mint, Ink.Black)
        Act.BET -> Triple("Bet", Ink.Amber, Ink.Black)
        Act.RAISE -> Triple("Raise", Ink.Amber, Ink.Black)
        Act.FOLD -> Triple("Fold", Ink.Field, Ink.Dim)
    }
    val colour by animateColorAsState(fill, tween(220), label = "tag")
    Box(Modifier.fillMaxWidth().height(21.dp).clip(RoundedCornerShape(7.dp)).background(colour), contentAlignment = Alignment.Center) {
        Text(text, style = type(12, FontWeight.Bold, ink), maxLines = 1)
    }
}

/**
 * The chips in front of a player. They pop out when the player puts chips in, count up with a
 * raise, and slide into the pot when the street closes.
 */
@Composable
private fun Bet(amount: Int, toPot: IntOffset) {
    // The amount to show while the pill slides away, after the bet itself is already zero.
    val last = remember { intArrayOf(amount) }
    if (amount > 0) last[0] = amount
    val counted by animateIntAsState(if (amount > 0) amount else last[0], tween(260), label = "bet")
    AnimatedVisibility(
        visible = amount > 0,
        enter = scaleIn(tween(200), initialScale = 0.5f) + fadeIn(tween(200)),
        exit = slideOut(tween(430)) { toPot } + scaleOut(tween(430), targetScale = 0.7f) + fadeOut(tween(430)),
    ) {
        Row(
            Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipGlyph(Modifier.size(16.dp), marks = Ink.Red)
            Spacer(Modifier.width(5.dp))
            Text(counted.grouped(), style = type(13, FontWeight.Bold), maxLines = 1)
        }
    }
}

// ---- Acting ----

@Composable
private fun ActionBar(
    game: GameState,
    settings: Settings,
    enabled: Boolean,
    onCheckOrCall: () -> Unit,
    onRaise: () -> Unit,
    onFold: () -> Unit,
) {
    val options = if (enabled) game.options() else null
    val actor = game.activePlayer
    Box(Modifier.fillMaxWidth().height(142.dp).padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
        if (options != null && actor != null) {
            val hold = settings.holdTenths * 100
            // Fresh buttons for every turn: a press cannot carry over to the next player, and their rising into place marks the change of turn.
            key(game.hand, game.street, game.active) {
                val risen = remember { Animatable(0f) }
                LaunchedEffect(Unit) { risen.animateTo(1f, tween(220)) }
                Column(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = risen.value
                        translationY = (1f - risen.value) * 18.dp.toPx()
                    },
                ) {
                    // Whose buttons these are, for a phone lying where nobody has it in hand: a mint pill that matches the seat's ring.
                    Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "${actor.name}'s turn",
                            style = type(19, FontWeight.ExtraBold, Ink.Black),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Ink.Mint)
                                .padding(horizontal = 22.dp, vertical = 4.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HoldButton(
                            label = when {
                                options.check -> "Check"
                                options.allInCall -> "All in"
                                else -> "Call"
                            },
                            amount = if (options.check) null else options.call.grouped(),
                            fill = Ink.Mint,
                            ink = Ink.Black,
                            holdMillis = hold,
                            doubleTap = settings.doubleTap,
                            onFire = onCheckOrCall,
                            modifier = Modifier.weight(1f),
                        )
                        if (options.canRaise) {
                            TapButton(if (options.firstBet) "Bet" else "Raise", Ink.Amber, Ink.Black, onRaise, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        HoldButton(
                            label = "Fold",
                            amount = null,
                            fill = Ink.Field,
                            ink = Ink.White,
                            holdMillis = hold,
                            doubleTap = settings.doubleTap,
                            onFire = onFold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private val actionShape = RoundedCornerShape(24.dp)

/**
 * A button that has to be held: a shade fills it from the left for [holdMillis] and it fires when
 * full, so a brush of the screen does nothing. Zero fires on touch. With [doubleTap] two quick
 * taps fire it too.
 */
@Composable
private fun HoldButton(
    label: String,
    amount: String?,
    fill: Color,
    ink: Color,
    holdMillis: Int,
    doubleTap: Boolean,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val fire by rememberUpdatedState(onFire)
    val buzz by rememberUpdatedState(LocalBuzz.current)
    Box(
        modifier
            .fillMaxHeight()
            .clip(actionShape)
            .background(fill)
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.26f), size = Size(size.width * progress.value, size.height)) }
            .pointerInput(holdMillis, doubleTap) {
                var lastTap = 0L
                awaitEachGesture {
                    awaitFirstDown()
                    var fired = false
                    val holding = scope.launch {
                        if (holdMillis > 0) {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                        }
                        fired = true
                        buzz(true)
                        fire()
                    }
                    val up = waitForUpOrCancellation()
                    if (!fired) {
                        holding.cancel()
                        if (up != null && doubleTap) {
                            if (up.uptimeMillis - lastTap < DOUBLE_TAP_MILLIS) {
                                lastTap = 0L
                                buzz(true)
                                fire()
                            } else {
                                lastTap = up.uptimeMillis
                            }
                        }
                    }
                    // Let go early and the shade drains back instead of vanishing.
                    scope.launch { if (fired) progress.snapTo(0f) else progress.animateTo(0f, tween(160)) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = type(20, FontWeight.ExtraBold, ink), maxLines = 1)
            if (amount != null) Text(amount, style = type(15, FontWeight.Bold, ink.copy(alpha = 0.7f)), maxLines = 1)
        }
    }
}

@Composable
private fun TapButton(label: String, fill: Color, ink: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val buzz = LocalBuzz.current
    Box(
        modifier
            .fillMaxHeight()
            .clip(actionShape)
            .background(fill)
            .clickable {
                buzz(false)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = type(20, FontWeight.ExtraBold, ink), maxLines = 1)
    }
}
