package com.xern.xrnchips.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xern.xrnchips.data.Settings
import com.xern.xrnchips.data.Store
import com.xern.xrnchips.domain.GameSetup
import com.xern.xrnchips.domain.Phase
import com.xern.xrnchips.domain.SetupPlayer
import com.xern.xrnchips.domain.Street
import com.xern.xrnchips.domain.advance
import com.xern.xrnchips.domain.checkOrCall
import com.xern.xrnchips.domain.newGame
import com.xern.xrnchips.domain.raise
import com.xern.xrnchips.domain.withDealer
import java.nio.file.Files

// Screens rendered without a phone, debug builds only:
// android preview render "?:app" --fqn=com.xern.xrnchips.ui.PreviewsKt.<Name>

@Composable
private fun Frame(content: @Composable () -> Unit) {
    ChipsTheme(Settings()) { Box(Modifier.fillMaxSize().background(Ink.Black)) { content() } }
}

@Preview(name = "Start", widthDp = 393, heightDp = 852)
@Composable
fun StartPreview() = Frame { StartScreen(onContinue = {}, onPlay = {}, onLoad = {}, onSettings = {}, onAbout = {}) }

@Preview(name = "About", widthDp = 393, heightDp = 600)
@Composable
fun AboutPreview() = Frame { AboutScreen(onBack = {}, onLicenses = {}) }

/** Every weight the app uses, with an "@", which the static Bold and Heavy files draw as "&". */
@Preview(name = "Weights", widthDp = 393, heightDp = 380)
@Composable
fun WeightsPreview() = Frame {
    Column(Modifier.padding(20.dp)) {
        listOf(
            "Normal" to FontWeight.Normal,
            "Medium" to FontWeight.Medium,
            "SemiBold" to FontWeight.SemiBold,
            "Bold" to FontWeight.Bold,
            "ExtraBold" to FontWeight.ExtraBold,
            "Black" to FontWeight.Black,
        ).forEach { (name, weight) -> Text("@sam wins 1,250 · $name", style = type(24, weight)) }
    }
}

private val players = listOf(0 to "Alex", 1 to "Sam", 3 to "Jo", 4 to "Kim", 5 to "Lee", 7 to "Max")

/** Six made-up players on the flop: everyone called preflop, then the first to act bets. */
private val onTheFlop = run {
    var game = newGame(GameSetup(players = players.map { (seat, name) -> SetupPlayer(seat, name) })).withDealer(4)
    repeat(players.size) { if (game.phase == Phase.BETTING && game.street == Street.PREFLOP) game = game.checkOrCall() }
    repeat(3) { if (game.phase != Phase.BETTING) game = game.advance() }
    game.raise(40)
}

/** The game screen as GameScreen lays it out, without the model behind it. */
@Preview(name = "Table", widthDp = 393, heightDp = 852)
@Composable
fun TablePreview() = Frame {
    val settings = Settings()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp)) {
            MenuButton(enabled = true, onClick = {}, modifier = Modifier.align(Alignment.CenterStart))
            Text(streetTitle(onTheFlop.street), Modifier.align(Alignment.Center), style = type(28, FontWeight.ExtraBold))
        }
        Table(game = onTheFlop, felt = felt(settings.felt), inBlinds = settings.stacksInBlinds, onClock = {}, modifier = Modifier.weight(1f))
        ActionBar(game = onTheFlop, settings = settings, enabled = true, onCheckOrCall = {}, onRaise = {}, onFold = {})
    }
}

/** A store in a throwaway folder, holding the same made-up players. */
@Composable
private fun previewStore(): Store = remember {
    Store(Files.createTempDirectory("xrnchips-preview").toFile()).apply {
        updateSetup { it.copy(players = players.map { (seat, name) -> SetupPlayer(seat, name) }) }
    }
}

@Preview(name = "Play", widthDp = 393, heightDp = 852)
@Composable
fun SetupPreview() = Frame { SetupScreen(previewStore(), feltIndex = 0, onBack = {}, onStart = {}) }
