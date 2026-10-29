package io.github.sh4peshifting.panelawake

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Day = lightColorScheme(
    primary = Color(0xFF65558F), onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF211047),
    secondary = Color(0xFF625B71), secondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF52645A), tertiaryContainer = Color(0xFFD5E9DB),
    background = Color(0xFFFEF7FF), surface = Color(0xFFFEF7FF),
    surfaceContainer = Color(0xFFF3EDF7), surfaceContainerLow = Color(0xFFF7F2FA),
)
private val Night = darkColorScheme(
    primary = Color(0xFFCFBDFE), onPrimary = Color(0xFF36265D),
    primaryContainer = Color(0xFF4D3D75), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), secondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFFB9CCBE), tertiaryContainer = Color(0xFF3B4D42),
    background = Color(0xFF141218), surface = Color(0xFF141218),
    surfaceContainer = Color(0xFF211F26), surfaceContainerLow = Color(0xFF1D1B20),
)

@Composable
fun LucidTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> Night
        else -> Day
    }
    MaterialTheme(colorScheme = colors, content = content)
}
