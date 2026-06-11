package com.clawd.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ClawdAccent,
    onPrimary = ClawdText,
    background = ClawdBackground,
    onBackground = ClawdText,
    surface = ClawdCard,
    onSurface = ClawdText,
    surfaceVariant = ClawdCard,
    onSurfaceVariant = ClawdMuted,
    outline = ClawdCardBorder,
    outlineVariant = ClawdFaint,
    error = ClawdRed,
    onError = ClawdText
)

@Composable
fun ClawdDarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ClawdTypography,
        content = content
    )
}
