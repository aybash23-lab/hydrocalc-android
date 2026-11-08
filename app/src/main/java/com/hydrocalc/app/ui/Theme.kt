package com.hydrocalc.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Светлая инженерная тема: белые карточки, синие акценты (как на исходном сайте).
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A6BB5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E5F8),
    onPrimaryContainer = Color(0xFF0B3B66),
    inversePrimary = Color(0xFF9CCAF9),
    secondary = Color(0xFF51606F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E4F7),
    onSecondaryContainer = Color(0xFF0E1D2A),
    tertiary = Color(0xFF39656C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDEBF3),
    onTertiaryContainer = Color(0xFF001F24),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF191C1F),
    surface = Color.White,
    onSurface = Color(0xFF191C1F),
    surfaceVariant = Color(0xFFE1E6EE),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceTint = Color(0xFF1A6BB5),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF0F7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000)
)

@Composable
fun HydroCalcTheme(content: @Composable () -> Unit) {
    // Приложение рассчитано на светлую инженерную тему;
    // в тёмной системной теме сохраняем светлую палитру для читаемости цифр.
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
