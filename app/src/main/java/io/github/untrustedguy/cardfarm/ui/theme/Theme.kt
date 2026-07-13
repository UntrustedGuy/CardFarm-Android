package io.github.untrustedguy.cardfarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Steam-inspired palette
val SteamNavy = Color(0xFF171A21)
val SteamPanel = Color(0xFF1B2838)
val SteamPanelLight = Color(0xFF2A475E)
val SteamBlue = Color(0xFF66C0F4)
val SteamGreen = Color(0xFF5BA32B)
val SteamGold = Color(0xFFE5C15A)
val SteamText = Color(0xFFC7D5E0)

private val DarkColors = darkColorScheme(
    primary = SteamBlue,
    onPrimary = SteamNavy,
    secondary = SteamGreen,
    onSecondary = Color.White,
    tertiary = SteamGold,
    background = SteamNavy,
    onBackground = SteamText,
    surface = SteamPanel,
    onSurface = SteamText,
    surfaceVariant = SteamPanelLight,
    onSurfaceVariant = SteamText,
    error = Color(0xFFCF6679),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A6FB0),
    secondary = SteamGreen,
    tertiary = Color(0xFFB8860B),
)

@Composable
fun CardFarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The app is designed dark-first; light mode is a graceful fallback.
    val colors = if (darkTheme) DarkColors else DarkColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
