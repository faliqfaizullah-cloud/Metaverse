package com.metaverse.gallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val Ink = Color(0xFF0A0A0A)
val Panel = Color(0xFF111111)
val Cell = Color(0xFF1B1B1B)
val CellStroke = Color(0xFF2A2A2A)
val Muted = Color(0xFF8A8A8A)
val Ember = Color(0xFFFF3D00)
val Polaroid = Color(0xFF3B5BFF)

/** Swap for "Patrick Hand" / "Gochi Hand" in res/font for an even closer match. */
val Hand = FontFamily.Cursive
val Mono = FontFamily.Monospace

@Composable
fun MetaverseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink,
            surface = Panel,
            primary = Ember,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}
