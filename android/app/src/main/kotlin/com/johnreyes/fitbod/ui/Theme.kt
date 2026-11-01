package com.johnreyes.fitbod.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0E0F13)
    val Surface = Color(0xFF17191F)
    val Card = Color(0xFF1F222A)
    val CardHi = Color(0xFF292D37)
    val Line = Color(0xFF2E323C)
    val Accent = Color(0xFFFF5A4E)
    val AccentDim = Color(0x33FF5A4E)
    val Good = Color(0xFF3DDC97)
    val GoodDim = Color(0x333DDC97)
    val Text = Color(0xFFF4F5F7)
    val Muted = Color(0xFF9AA0AC)
    val Faint = Color(0xFF5E6470)

    private val muscle = mapOf(
        "Chest" to Color(0xFFFF7A59), "Back" to Color(0xFF4EA8FF), "Shoulders" to Color(0xFFFFC145),
        "Quads" to Color(0xFF9B7BFF), "Hamstrings" to Color(0xFFB86BFF), "Glutes" to Color(0xFFFF6FB1),
        "Biceps" to Color(0xFF3DDC97), "Triceps" to Color(0xFF2CD3E1), "Abs" to Color(0xFFFFE066),
        "Calves" to Color(0xFF7FD36B),
    )

    fun muscle(name: String): Color = muscle[name] ?: Color(0xFF8C93A1)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.Good,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    error = Color(0xFFFF6B6B),
)

private val type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FitbodTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
