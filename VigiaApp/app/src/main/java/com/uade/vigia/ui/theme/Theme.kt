package com.uade.vigia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Surface,
    primaryContainer = NavyTint,
    onPrimaryContainer = Navy,
    secondary = OkFg,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    surfaceVariant = Tile,
    outline = Border,
    outlineVariant = Border,
    error = Danger
)

/** La consola se usa en una garita iluminada: la app tiene solo tema claro, como el prototipo. */
@Composable
fun VigiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, content = content)
}
