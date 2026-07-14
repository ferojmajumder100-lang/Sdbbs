package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TechDarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    secondary = BrightBlue,
    tertiary = NeonCyan,
    background = SlateDarkBg,
    surface = SlateCardBg,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = OnSlateText,
    onSurface = OnSlateText,
    outline = SlateBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TechDarkColorScheme,
        typography = Typography,
        content = content
    )
}
