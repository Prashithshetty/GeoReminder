package com.georeminder.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryCharcoal,
    onPrimary = Color.White,
    secondary = AccentTeal,
    onSecondary = Color.White,
    background = GroundLight,
    surface = SurfaceWhite,
    onBackground = PrimaryCharcoal,
    onSurface = PrimaryCharcoal,
    outline = BorderSubtle,
    surfaceVariant = RaisedGrey
)

@Composable
fun GeoReminderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
