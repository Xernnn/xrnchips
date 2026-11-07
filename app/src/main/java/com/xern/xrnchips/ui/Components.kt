package com.xern.xrnchips.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xern.xrnchips.domain.NAME_LENGTH
import com.xern.xrnchips.domain.SEATS
import com.xern.xrnchips.domain.grouped

enum class Tone { PRIMARY, PLAIN, ACCENT, WARM, DANGER }

/**
 * The app's button: a full pill. A disabled one can still explain itself through [onDisabledClick].
 * With an empty [text] it shows the icon alone, and [label] names it for screen readers.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.PLAIN,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
    onDisabledClick: (() -> Unit)? = null,
    label: String? = null,
) {
    val buzz = LocalBuzz.current
    val (fill, ink) = when (tone) {
        Tone.PRIMARY -> Ink.White to Ink.Black
        Tone.PLAIN -> Ink.Field to Ink.White
        Tone.ACCENT -> Ink.Mint to Ink.Black
        Tone.WARM -> Ink.Amber to Ink.Black
        Tone.DANGER -> Ink.Red to Ink.Black
    }
    val color = if (enabled) ink else Ink.Faint
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(if (enabled) fill else Ink.Raised)
            .clickable(enabled = enabled || onDisabledClick != null) {
                if (enabled) {
                    buzz(false)
                    onClick()
                } else {
                    onDisabledClick?.invoke()
                }
            }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (text.isNotEmpty()) Text(text, style = type(17, FontWeight.Bold, color), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null) {
            if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(if (text.isEmpty()) 24.dp else 20.dp))
        }
    }
}

/** An icon with a full-size touch target. */
@Composable
fun IconTap(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Ink.White,
) {
    val buzz = LocalBuzz.current
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) {
                buzz(false)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else Ink.Faint, modifier = Modifier.size(26.dp))
    }
}

/** The bar on top of a full screen: back on the left, the title, and room for one action on the right. */
@Composable
fun Header(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp)) {
        IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.align(Alignment.CenterStart))
        Text(title, style = type(21, FontWeight.Bold), modifier = Modifier.align(Alignment.Center))
        if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
    }
}

/**
 * A card over a dimmed screen. Back and taps beside it close it when [onDismiss] is given; taps on
 * it never fall through to the screen below. It scrolls when the keyboard leaves it too little
 * room; a modal that scrolls a part of itself turns [scroll] off.
 */
@Composable
fun Modal(
    onDismiss: (() -> Unit)? = null,
    scroll: Boolean = true,
    tapOutsideCloses: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Back closes the modal, or does nothing when it has to be answered. It never reaches the screen below.
    BackHandler { onDismiss?.invoke() }
    // The screen dims and the card settles into place.
    val opened = remember { Animatable(0f) }
    LaunchedEffect(Unit) { opened.animateTo(1f, tween(200)) }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.74f * opened.value)) }
            .pointerInput(onDismiss, tapOutsideCloses) { detectTapGestures { if (tapOutsideCloses) onDismiss?.invoke() } }
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = opened.value
                    scaleX = 0.92f + 0.08f * opened.value
                    scaleY = 0.92f + 0.08f * opened.value
                }
                .clip(shape)
                .background(Ink.Surface)
                .border(1.dp, Ink.Line, shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** The row of small controls on top of a modal. */
@Composable
fun ModalBar(left: (@Composable () -> Unit)? = null, right: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(48.dp)) { left?.invoke() }
        Box(Modifier.size(48.dp)) { right?.invoke() }
    }
}

@Composable
fun BackIcon(onClick: () -> Unit) = IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick)

@Composable
fun CloseIcon(onClick: () -> Unit) = IconTap(Icons.Filled.Close, "Close", onClick)

/** A question with one answer button. Closing it is the other answer. */
@Composable
fun Confirm(title: String, text: String, onConfirm: () -> Unit, onClose: () -> Unit, onBack: (() -> Unit)? = null) {
    Modal(onDismiss = onClose) {
        ModalBar(left = onBack?.let { { BackIcon(it) } }, right = { CloseIcon(onClose) })
        Text(title, style = type(26, FontWeight.ExtraBold))
        Spacer(Modifier.height(6.dp))
        Text(text, style = type(16, color = Ink.Dim), textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        PillButton("OK", onConfirm, Modifier.fillMaxWidth(), tone = Tone.DANGER)
    }
}

/**
 * Enter on a hardware keyboard, taken whole. Left to the text field, the press would finish the
 * edit and the release would then click whatever the focus moved to.
 */
private fun Modifier.onEnterKey(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
        if (event.type == KeyEventType.KeyUp) action()
        true
    } else {
        false
    }
}

/**
 * A whole-number field. Touching it clears it for typing and leaves the old value showing faintly;
 * leaving it without typing keeps that value. [onCommit] gets what was typed when focus leaves,
 * null for an emptied field, and the owner decides what that becomes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumberField(
    value: Int,
    onCommit: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxDigits: Int = 6,
    style: TextStyle = type(17, FontWeight.Bold),
    fill: Color = Ink.White,
    ink: Color = Ink.Black,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    onEditing: (Boolean) -> Unit = {},
) {
    var draft by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val color = if (enabled) ink else Ink.Faint
    val commit by rememberUpdatedState(onCommit)

    // Closing the keyboard ends the edit, the same as Done.
    var keyboardSeen by remember { mutableStateOf(false) }
    val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(keyboardUp, draft != null) {
        if (draft == null) {
            keyboardSeen = false
        } else if (keyboardUp) {
            keyboardSeen = true
        } else if (keyboardSeen) {
            focus.clearFocus()
        }
    }
    // A screen closed mid-edit still keeps what was typed.
    DisposableEffect(Unit) {
        onDispose { if (draft != null && typed) commit(draft?.toIntOrNull()) }
    }
    BasicTextField(
        value = draft ?: "",
        onValueChange = { text ->
            if (draft != null) {
                draft = text.filter(Char::isDigit).take(maxDigits)
                typed = true
            }
        },
        enabled = enabled,
        singleLine = true,
        textStyle = style.copy(color = color, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Ink.Felt),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier
            .clip(shape)
            .background(if (enabled) fill else Ink.Raised)
            .onEnterKey { focus.clearFocus() }
            .onFocusChanged { state ->
                if (state.isFocused && draft == null) {
                    draft = ""
                    typed = false
                    onEditing(true)
                } else if (!state.isFocused && draft != null) {
                    val text = draft
                    draft = null
                    onEditing(false)
                    if (typed) onCommit(text?.toIntOrNull())
                }
            },
        decorationBox = { field ->
            Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                if (draft.isNullOrEmpty()) {
                    Text(
                        value.grouped(),
                        style = style.copy(color = if (draft == null) color else color.copy(alpha = 0.3f)),
                        maxLines = 1,
                    )
                }
                field()
            }
        },
    )
}

/**
 * A player's name: one line, at most [NAME_LENGTH] characters. A name being edited opens with the
 * cursor behind it.
 */
@Composable
fun NameField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val shape = RoundedCornerShape(14.dp)
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        value = field,
        onValueChange = { next ->
            val text = next.text.replace("\n", "").take(NAME_LENGTH)
            val cut = text.length
            field = next.copy(text = text, selection = TextRange(minOf(next.selection.start, cut), minOf(next.selection.end, cut)))
            onChange(text)
        },
        singleLine = true,
        textStyle = type(22, FontWeight.Bold, Ink.Black).copy(textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Ink.Felt),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.height(58.dp).clip(shape).background(Ink.White).onEnterKey(onDone),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text("Name", style = type(22, FontWeight.Bold, Ink.Black.copy(alpha = 0.3f)))
                inner()
            }
        },
    )
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val buzz = LocalBuzz.current
    Switch(
        checked = checked,
        onCheckedChange = {
            buzz(false)
            onChange(it)
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Ink.Black,
            checkedTrackColor = Ink.Mint,
            checkedBorderColor = Ink.Mint,
            uncheckedThumbColor = Ink.Dim,
            uncheckedTrackColor = Ink.Field,
            uncheckedBorderColor = Ink.Line,
        ),
    )
}

/** A choice between a few short options, the chosen one in white. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val buzz = LocalBuzz.current
    Row(modifier.clip(CircleShape).background(Ink.Field).padding(4.dp)) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            val fill by animateColorAsState(if (chosen) Ink.White else Ink.White.copy(alpha = 0f), tween(200), label = "choice")
            val ink by animateColorAsState(if (chosen) Ink.Black else Ink.Dim, tween(200), label = "choiceInk")
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .clickable {
                        buzz(false)
                        onSelect(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(option, style = type(15, FontWeight.Bold, ink), maxLines = 1)
            }
        }
    }
}

@Composable
fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 46.dp) {
    val buzz = LocalBuzz.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) Ink.Field else Ink.Raised)
            .clickable(enabled = enabled) {
                buzz(false)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Ink.White else Ink.Faint, modifier = Modifier.size(22.dp))
    }
}

/** A value with a minus on its left and a plus on its right. */
@Composable
fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean = true, canPlus: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(Icons.Filled.Remove, "Less", onMinus, canMinus)
        Text(text, style = type(26, FontWeight.ExtraBold), textAlign = TextAlign.Center, modifier = Modifier.width(96.dp))
        RoundIcon(Icons.Filled.Add, "More", onPlus, canPlus)
    }
}

/** The chip of the app icon: a disc with six marks around its rim. */
@Composable
fun ChipGlyph(modifier: Modifier = Modifier, body: Color = Ink.White, marks: Color = Ink.Felt) {
    Canvas(modifier) {
        val radius = size.minDimension / 2
        drawCircle(body, radius)
        val ring = radius * 21f / 31f
        val width = radius * 10f / 31f
        for (i in 0 until 6) {
            drawArc(
                color = marks,
                startAngle = -90f - 17f + i * 60f,
                sweepAngle = 34f,
                useCenter = false,
                topLeft = Offset(center.x - ring, center.y - ring),
                size = Size(ring * 2, ring * 2),
                style = Stroke(width),
            )
        }
    }
}

/** A card lying face down. The app never knows the cards; it only shows how many are out. */
@Composable
fun CardBack(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val corner = CornerRadius(size.width * 0.16f)
        drawRoundRect(Ink.White, cornerRadius = corner)
        val inset = size.width * 0.12f
        drawRoundRect(
            Ink.Surface,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(size.width * 0.08f),
        )
        drawCircle(Ink.Mint, radius = size.width * 0.16f, style = Stroke(size.width * 0.07f))
    }
}

/** An empty place for a card on the board. */
@Composable
fun CardSlot(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(Color.Black.copy(alpha = 0.28f), cornerRadius = CornerRadius(size.width * 0.16f))
    }
}

/** A face-up card for the hand rankings. Suits are s, h, d, c. */
@Composable
fun CardFace(rank: String, suit: Char, muted: Boolean, modifier: Modifier = Modifier) {
    val red = suit == 'h' || suit == 'd'
    val color = (if (red) Color(0xFFD92D3A) else Ink.Black).copy(alpha = if (muted) 0.3f else 1f)
    val symbol = when (suit) {
        's' -> "♠"
        'h' -> "♥"
        'd' -> "♦"
        else -> "♣"
    }
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Ink.White.copy(alpha = if (muted) 0.45f else 1f)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(rank, style = type(19, FontWeight.ExtraBold, color), maxLines = 1)
        Text(symbol, style = TextStyle(fontFamily = FontFamily.Default, fontSize = type(18).fontSize, color = color))
    }
}

/** The table seen from above: a rail, the felt and a line marking the betting area. */
@Composable
fun TableFelt(felt: Felt, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val rail = 9.dp.toPx()
        drawRoundRect(Ink.Raised, cornerRadius = CornerRadius(size.minDimension / 2))
        drawRoundRect(Ink.Line, cornerRadius = CornerRadius(size.minDimension / 2), style = Stroke(1.dp.toPx()))
        val cloth = Size(size.width - rail * 2, size.height - rail * 2)
        drawRoundRect(
            brush = Brush.radialGradient(listOf(felt.cloth, felt.shade), center = center, radius = size.maxDimension * 0.6f),
            topLeft = Offset(rail, rail),
            size = cloth,
            cornerRadius = CornerRadius(cloth.minDimension / 2),
        )
        val inset = rail + cloth.width * 0.17f
        val line = Size(size.width - inset * 2, size.height - inset * 2)
        if (line.width > 0 && line.height > 0) {
            drawRoundRect(
                Color.White.copy(alpha = 0.12f),
                topLeft = Offset(inset, inset),
                size = line,
                cornerRadius = CornerRadius(line.minDimension / 2),
                style = Stroke(1.5.dp.toPx()),
            )
        }
    }
}

/** Where a seat sits in a box of [w] by [h], as the top-left corner of something [sw] by [sh]. */
fun seatOffset(seat: Int, w: Dp, h: Dp, sw: Dp, sh: Dp, rows: List<Float>): Pair<Dp, Dp> = when (seat) {
    0 -> (w - sw) / 2 to 0.dp
    1 -> w - sw to h * rows[0] - sh / 2
    2 -> w - sw to h * rows[1] - sh / 2
    3 -> w - sw to h * rows[2] - sh / 2
    4 -> (w - sw) / 2 to h - sh
    5 -> 0.dp to h * rows[2] - sh / 2
    6 -> 0.dp to h * rows[1] - sh / 2
    else -> 0.dp to h * rows[0] - sh / 2
}

/** The eight seats around a small table, for picking one. [slot] draws what sits at each. */
@Composable
fun SeatMap(feltIndex: Int, modifier: Modifier = Modifier, slot: @Composable (seat: Int) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().height(330.dp)) {
        val w = maxWidth
        val h = maxHeight
        val slotWidth = w * 0.4f
        val slotHeight = 46.dp
        TableFelt(felt(feltIndex), Modifier.align(Alignment.Center).width(w * 0.52f).height(h - slotHeight))
        for (seat in 0 until SEATS) {
            val (x, y) = seatOffset(seat, w, h, slotWidth, slotHeight, listOf(0.26f, 0.5f, 0.74f))
            Box(Modifier.offset(x, y).size(slotWidth, slotHeight)) { slot(seat) }
        }
    }
}

/** One seat of a [SeatMap]: a name, or an invitation to sit down when [name] is null. */
@Composable
fun SeatButton(name: String?, onClick: () -> Unit, faded: Boolean = false) {
    val buzz = LocalBuzz.current
    val shape = RoundedCornerShape(14.dp)
    val fill = when {
        faded -> Ink.Raised
        name == null -> Ink.Field
        else -> Ink.White
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(fill)
            .border(1.dp, if (name == null) Ink.Line else Color.Transparent, shape)
            .clickable {
                buzz(false)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (name == null) {
            Icon(Icons.Filled.PersonAdd, contentDescription = "Add player", tint = Ink.Dim, modifier = Modifier.size(22.dp))
        } else {
            Text(
                name,
                style = type(18, FontWeight.Bold, if (faded) Ink.Faint else Ink.Black),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}
