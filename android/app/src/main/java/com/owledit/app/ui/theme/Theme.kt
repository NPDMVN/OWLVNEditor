package com.owledit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OwlRuby = Color(0xFFE53955)
val OwlRubyContainer = Color(0xFF4A1220)
val OwlSapphire = Color(0xFF38BDF8)
val OwlSapphireContainer = Color(0xFF0E344D)
val OwlSoulGold = Color(0xFFF59E0B)
val OwlSoulGoldContainer = Color(0xFF472C05)
val OwlEmerald = Color(0xFF22C55E)
val OwlEmeraldContainer = Color(0xFF0D381E)

val OwlBg = Color(0xFF0D0F14)
val OwlSurface = Color(0xFF151923)
val OwlSurfaceVariant = Color(0xFF1E2433)
val OwlOutline = Color(0xFF333D52)
val OwlTextPrimary = Color(0xFFF1F5F9)
val OwlTextSecondary = Color(0xFF94A3B8)

private val OwlDarkColorScheme = darkColorScheme(
    primary = OwlRuby,
    onPrimary = Color.White,
    primaryContainer = OwlRubyContainer,
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = OwlSapphire,
    onSecondary = Color(0xFF041C2C),
    secondaryContainer = OwlSapphireContainer,
    onSecondaryContainer = Color(0xFFC9ECFF),
    tertiary = OwlSoulGold,
    onTertiary = Color(0xFF2B1800),
    tertiaryContainer = OwlSoulGoldContainer,
    onTertiaryContainer = Color(0xFFFFE4B5),
    background = OwlBg,
    onBackground = OwlTextPrimary,
    surface = OwlSurface,
    onSurface = OwlTextPrimary,
    surfaceVariant = OwlSurfaceVariant,
    onSurfaceVariant = OwlTextSecondary,
    outline = OwlOutline,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3D0008),
    errorContainer = Color(0xFF5C1119),
    onErrorContainer = Color(0xFFFFDADA),
)

@Composable
fun OwlEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OwlDarkColorScheme,
        content = content,
    )
}
