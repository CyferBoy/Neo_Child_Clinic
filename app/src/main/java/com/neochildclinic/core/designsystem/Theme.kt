package com.neochildclinic.core.designsystem

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

@Immutable
data class CustomColors(
    val bgOffWhite: Color,
    val softBlue: Color,
    val softGreen: Color,
    val softOrange: Color,
    val softPurple: Color,
    val softCyan: Color,
    val softGrey: Color,
    val softPink: Color,
    val textBlue: Color,
    val textGreen: Color,
    val textOrange: Color,
    val textPurple: Color,
    val textCyan: Color,
    val textGrey: Color,
    val textPink: Color,
    val iconColor: Color
)

val LightCustomColors = CustomColors(
    bgOffWhite = Color(0xFFFBF8F5),
    softBlue = Color(0xFFD6E4F0),
    softGreen = Color(0xFFDCF0E2),
    softOrange = Color(0xFFFFE8D1),
    softPurple = Color(0xFFF2E4F6),
    softCyan = Color(0xFFD9F2F0),
    softGrey = Color(0xFFEBEBEB),
    softPink = Color(0xFFFCE4E4),
    textBlue = Color(0xFF1E3A5F),
    textGreen = Color(0xFF1B5E20),
    textOrange = Color(0xFF9C4D04),
    textPurple = Color(0xFF4A148C),
    textCyan = Color(0xFF00695C),
    textGrey = Color(0xFF424242),
    textPink = Color(0xFFB71C1C),
    iconColor = Color(0xFF2C2C2C)
)

val DarkCustomColors = CustomColors(
    bgOffWhite = Color(0xFF121212),
    softBlue = DarkBlueContainer,
    softGreen = DarkGreenContainer,
    softOrange = DarkOrangeContainer,
    softPurple = DarkPurpleContainer,
    softCyan = Color(0xFF004D40),
    softGrey = Color(0xFF2C2C2C),
    softPink = DarkRedContainer,
    textBlue = DarkOnBlueContainer,
    textGreen = DarkOnGreenContainer,
    textOrange = DarkOnOrangeContainer,
    textPurple = DarkOnPurpleContainer,
    textCyan = Color(0xFF80CBC4),
    textGrey = Color(0xFFE2E2E6),
    textPink = DarkOnRedContainer,
    iconColor = Color(0xFFE2E2E6)
)

val LocalCustomColors = staticCompositionLocalOf { LightCustomColors }

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    onPrimary = Color(0xFF003355),
    onSecondary = Color(0xFF1B372D),
    onTertiary = Color(0xFF5E1133),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6)
)

private val LightColorScheme = lightColorScheme(
    primary = ClinicBlue,
    onPrimary = Color.White,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun NeoChildTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled by default for more consistent custom color look
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

    val customColors = if (darkTheme) DarkCustomColors else LightCustomColors

    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
