package com.guardianangel.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Holds the three scaling dimensions for the active text size mode.
 * Screens read tapTarget from LocalTextSizes; text sizes come through
 * MaterialTheme.typography which is rebuilt for each mode.
 */
data class GuardianTextSizes(
    val bodySize: TextUnit = 20.sp,
    val headingSize: TextUnit = 26.sp,
    val tapTarget: Dp = 56.dp
)

// Default is LARGE so every screen that reads this before the theme is
// applied still gets a usable size.
val LocalTextSizes = staticCompositionLocalOf { GuardianTextSizes() }

enum class TextSizePreference { NORMAL, LARGE, EXTRA_LARGE }

fun textSizesFor(pref: TextSizePreference) = when (pref) {
    TextSizePreference.NORMAL     -> GuardianTextSizes(16.sp, 20.sp, 48.dp)
    TextSizePreference.LARGE      -> GuardianTextSizes(20.sp, 26.sp, 56.dp)
    TextSizePreference.EXTRA_LARGE -> GuardianTextSizes(26.sp, 32.sp, 64.dp)
}

private val LightColorScheme = lightColorScheme(
    primary = NavyBlue,
    onPrimary = TextOnDark,
    primaryContainer = NavyBlueLight,
    onPrimaryContainer = TextOnDark,
    secondary = WarmGold,
    onSecondary = TextOnGold,
    secondaryContainer = WarmGoldLight,
    onSecondaryContainer = NavyBlueDark,
    background = WarmWhite,
    onBackground = TextPrimary,
    surface = WarmWhiteLight,
    onSurface = TextPrimary,
    surfaceVariant = LightSurface,
    onSurfaceVariant = TextSecondary,
    error = ScamRed,
    onError = TextOnDark
)

private val DarkColorScheme = darkColorScheme(
    primary = WarmGold,
    onPrimary = NavyBlueDark,
    primaryContainer = NavyBlueDark,
    onPrimaryContainer = WarmGoldLight,
    secondary = WarmGoldLight,
    onSecondary = NavyBlueDark,
    background = NavyBlueDark,
    onBackground = TextOnDark,
    surface = NavyBlue,
    onSurface = TextOnDark,
    error = ScamRed,
    onError = TextOnDark
)

@Composable
fun GuardianAngelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    textSizePreference: TextSizePreference = TextSizePreference.LARGE,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val textSizes = textSizesFor(textSizePreference)

    CompositionLocalProvider(LocalTextSizes provides textSizes) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildGuardianTypography(
                bodySize = textSizes.bodySize,
                headingSize = textSizes.headingSize
            ),
            content = content
        )
    }
}
