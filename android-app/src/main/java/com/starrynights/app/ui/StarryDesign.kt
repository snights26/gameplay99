package com.starrynights.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Central visual primitives for all story modes. Game state may choose intensity; screens do not invent themes. */
object StarryDesign {
    val black = Color(0xFF090613)
    val charcoal = Color(0xFF171127)
    val nightBlue = Color(0xFF111936)
    val purple = Color(0xFF9A63FF)
    val magenta = Color(0xFFFF4FA3)
    val deepRed = Color(0xFFFF4F6D)
    val softText = Color(0xFFDCD1F7)

    val pagePadding = 20.dp
    val cardPadding = 16.dp
    val compactSpacing = 6.dp
    val sectionSpacing = 16.dp
    val cardShape = RoundedCornerShape(18.dp)
    val chipShape = RoundedCornerShape(50)
    val title = TextStyle(fontWeight = FontWeight.Black, letterSpacing = 4.sp)
    val gradient: Brush = Brush.linearGradient(listOf(nightBlue, purple.copy(alpha = .55f), black))
    fun heatColor(heat: Int): Color = when (heat.coerceIn(0, 100)) {
        in 0..20 -> purple
        in 21..60 -> magenta
        else -> deepRed
    }
    fun emotionColor(emotion: String): Color = when (emotion.lowercase()) {
        "jealousy", "tension", "competition" -> deepRed
        "trust", "attachment" -> purple
        else -> magenta
    }
    val scheme = darkColorScheme(
        primary = magenta, secondary = purple, tertiary = deepRed,
        background = black, surface = charcoal, onPrimary = Color.White,
        onBackground = Color.White, onSurface = Color.White
    )
}
