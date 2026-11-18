package com.xern.xrnchips.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xern.xrnchips.data.Settings

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
