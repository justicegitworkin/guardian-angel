package com.guardianangel.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class GuardianTextSizes(
    val bodySize: TextUnit = 18.sp,
    val titleSize: TextUnit = 22.sp,
    val headingSize: TextUnit = 26.sp
)

val LocalTextSizes = staticCompositionLocalOf { GuardianTextSizes() }

enum class TextSizePreference { NORMAL, LARGE, EXTRA_LARGE }

fun textSizesFor(pref: TextSizePreference) = when (pref) {
    TextSizePreference.NORMAL -> GuardianTextSizes(18.sp, 22.sp, 26.sp)
    TextSizePreference.LARGE -> GuardianTextSizes(22.sp, 26.sp, 30.sp)
    TextSizePreference.EXTRA_LARGE -> GuardianTextSizes(26.sp, 30.sp, 36.sp)
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
    textSizePreference: TextSizePreference = TextSizePreference.NORMAL,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val textSizes = textSizesFor(textSizePreference)

    CompositionLocalProvider(LocalTextSizes provides textSizes) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GuardianTypography,
            content = content
        )
    }
}
