package com.safeharborsecurity.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

data class SafeHarborTextSizes(
    val bodySize: TextUnit = 18.sp,
    val titleSize: TextUnit = 22.sp,
    val headingSize: TextUnit = 26.sp
)

val LocalTextSizes = staticCompositionLocalOf { SafeHarborTextSizes() }

enum class TextSizePreference { NORMAL, LARGE, EXTRA_LARGE }

fun textSizesFor(pref: TextSizePreference) = when (pref) {
    TextSizePreference.NORMAL -> SafeHarborTextSizes(18.sp, 22.sp, 26.sp)
    TextSizePreference.LARGE -> SafeHarborTextSizes(22.sp, 26.sp, 30.sp)
    TextSizePreference.EXTRA_LARGE -> SafeHarborTextSizes(26.sp, 30.sp, 36.sp)
}

// Light theme — clean, readable, professional
private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF00897B),              // Teal — main brand
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF004D40),

    secondary = Color(0xFF1A3A5C),            // Navy blue — secondary
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBBDEFB),
    onSecondaryContainer = Color(0xFF0D47A1),

    tertiary = Color(0xFFE8A833),             // Gold — accent
    onTertiary = Color(0xFF1A1A1A),

    background = Color(0xFFF5F7FA),           // Very light blue-grey
    onBackground = Color(0xFF1A1A1A),         // Near-black text

    surface = Color.White,                    // Cards, dialogs
    onSurface = Color(0xFF1A1A1A),            // Near-black text
    surfaceVariant = Color(0xFFECEFF1),       // Slightly grey cards
    onSurfaceVariant = Color(0xFF546E7A),     // Medium grey secondary text

    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFD32F2F),

    outline = Color(0xFFB0BEC5),
    outlineVariant = Color(0xFFE0E0E0),

    inverseSurface = Color(0xFF2E3440),
    inverseOnSurface = Color.White
)

@Composable
fun SafeHarborTheme(
    darkTheme: Boolean = false,
    textSizePreference: TextSizePreference = TextSizePreference.NORMAL,
    content: @Composable () -> Unit
) {
    val textSizes = textSizesFor(textSizePreference)

    // Light status bar with dark icons
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    CompositionLocalProvider(LocalTextSizes provides textSizes) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography = SafeHarborTypography,
            content = content
        )
    }
}
