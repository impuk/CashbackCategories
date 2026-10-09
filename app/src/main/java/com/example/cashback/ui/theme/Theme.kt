package com.example.cashback.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF1E7A4F)
private val GreenLight = Color(0xFF8FD8AE)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Color(0xFF4D6357),
    secondaryContainer = Color(0xFFD0E8D9),
    onSecondaryContainer = Color(0xFF0A1F14),
)
private val DarkColors = darkColorScheme(
    primary = GreenLight,
    secondary = Color(0xFFB4CCBC),
    secondaryContainer = Color(0xFF223229),
    onSecondaryContainer = Color(0xFFC6E6D2),
)

/**
 * Цвета плашки лучшего банка. Постоянные «денежные» зелёные, от обоев не зависят,
 * чтобы плашка не сливалась с остальным интерфейсом.
 */
@Immutable
data class BestChipColors(val container: Color, val bankName: Color, val percent: Color)

val BestChipDark = BestChipColors(
    container = Color(0xFF1B3A2A),
    bankName = Color(0xFFD3EEDD),
    percent = Color(0xFF7EE2A8),
)
val BestChipLight = BestChipColors(
    container = Color(0xFFCDEBD8),
    bankName = Color(0xFF0F3A24),
    percent = Color(0xFF0A5F33),
)

val LocalBestChipColors = staticCompositionLocalOf { BestChipLight }

@Composable
fun CashbackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalBestChipColors provides if (darkTheme) BestChipDark else BestChipLight) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
