package com.xern.xrnchips.ui

import android.widget.Toast
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.xern.xrnchips.R
import com.xern.xrnchips.data.Settings

/** The xrn family's dark surfaces with the felt green of this app's icon. */
object Ink {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF18181B)
    val Raised = Color(0xFF232327)
    val Field = Color(0xFF2E2E33)
    val Line = Color(0xFF3A3A40)
    val White = Color(0xFFFFFFFF)
    val Dim = Color(0xFFA1A1AA)
    val Faint = Color(0xFF71717A)
    val Felt = Color(0xFF146B4A)
    val Mint = Color(0xFF4ADE9C)
    val Amber = Color(0xFFFFB238)
    val Red = Color(0xFFFF5D52)
    val Sky = Color(0xFF7CC4FF)
}

/** A table colour: the felt, and the darker shade it fades to at the rail. */
class Felt(val name: String, val cloth: Color, val shade: Color)

val felts = listOf(
    Felt("Green", Color(0xFF146B4A), Color(0xFF0B3F2C)),
    Felt("Blue", Color(0xFF1F4F8C), Color(0xFF122E55)),
    Felt("Red", Color(0xFF8B2635), Color(0xFF511520)),
    Felt("Slate", Color(0xFF4B505A), Color(0xFF2A2D33)),
)

fun felt(index: Int): Felt = felts[index.mod(felts.size)]

/**
 * MiSans Latin by Xiaomi, one unmodified file per weight. It runs heavier than its names, so Bold
 * draws Semibold and ExtraBold draws Bold.
 */
val MiSans = FontFamily(
    Font(R.font.misans_regular, FontWeight.Normal),
    Font(R.font.misans_medium, FontWeight.Medium),
    Font(R.font.misans_demibold, FontWeight.SemiBold),
    Font(R.font.misans_semibold, FontWeight.Bold),
    Font(R.font.misans_bold, FontWeight.ExtraBold),
    Font(R.font.misans_heavy, FontWeight.Black),
)

/** Text in MiSans with room above and below, which Compose would otherwise trim. */
fun type(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.Unspecified) = TextStyle(
    fontFamily = MiSans,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    lineHeight = 1.3.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** A light buzz for a button press, silent when vibration is switched off. */
val LocalBuzz = staticCompositionLocalOf<(strong: Boolean) -> Unit> { {} }

/** A short message at the bottom of the screen. */
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun ChipsTheme(settings: Settings, content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Ink.Mint,
        onPrimary = Ink.Black,
        secondary = Ink.Mint,
        onSecondary = Ink.Black,
        background = Ink.Black,
        onBackground = Ink.White,
        surface = Ink.Surface,
        onSurface = Ink.White,
        surfaceVariant = Ink.Raised,
        onSurfaceVariant = Ink.Dim,
        outline = Ink.Line,
        error = Ink.Red,
    )
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val vibration = settings.vibration
    val buzz: (Boolean) -> Unit = remember(vibration, haptics) {
        { strong ->
            if (vibration) haptics.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        }
    }
    val toast: (String) -> Unit = remember(context) {
        { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalContentColor provides Ink.White,
            LocalTextStyle provides type(16),
            LocalBuzz provides buzz,
            LocalToast provides toast,
            content = content,
        )
    }
}
