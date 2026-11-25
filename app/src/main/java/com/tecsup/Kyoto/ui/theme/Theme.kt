package com.tecsup.Kyoto.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9CD2B2),
    secondary = Color(0xFFB5CDBD),
    tertiary = Color(0xFFE8BBA0),
    background = Color(0xFF111A16),
    surface = Color(0xFF1B2721),
    surfaceVariant = Color(0xFF2A3931),
    onBackground = Color(0xFFE7EEE9),
    onSurface = Color(0xFFE7EEE9)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF28634E),
    secondary = Color(0xFF587565),
    tertiary = Color(0xFF9C6847),
    background = Color(0xFFF5F7F3),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7ECE7),
    onBackground = Color(0xFF18221E),
    onSurface = Color(0xFF18221E)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun KyotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
