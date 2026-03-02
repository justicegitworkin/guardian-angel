package com.guardianangel.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Atkinson Hyperlegible is ideal for low-vision users.
// To enable it: add the .ttf files to res/font/ and replace
// FontFamily.SansSerif below with your FontFamily definition.
val GuardianFontFamily: FontFamily = FontFamily.SansSerif

/**
 * Builds a full Material3 Typography scaled from two anchor points:
 *  - bodySize    → bodyLarge / labelLarge
 *  - headingSize → headlineSmall / titleLarge
 */
fun buildGuardianTypography(
    fontFamily: FontFamily = GuardianFontFamily,
    bodySize: TextUnit = 20.sp,
    headingSize: TextUnit = 26.sp
): Typography {
    val b = bodySize.value        // e.g. 16, 20, 26
    val h = headingSize.value     // e.g. 20, 26, 32

    return Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (h * 1.38f).sp,
            lineHeight = (h * 1.65f).sp,
            letterSpacing = (-0.5).sp
        ),
        displayMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (h * 1.15f).sp,
            lineHeight = (h * 1.40f).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (h * 1.08f).sp,
            lineHeight = (h * 1.35f).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = h.sp,
            lineHeight = (h * 1.30f).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (h * 0.92f).sp,
            lineHeight = (h * 1.25f).sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = h.sp,
            lineHeight = (h * 1.30f).sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (h * 0.85f).sp,
            lineHeight = (h * 1.25f).sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (b * 1.10f).sp,
            lineHeight = (b * 1.45f).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = b.sp,
            lineHeight = (b * 1.55f).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (b * 0.90f).sp,
            lineHeight = (b * 1.50f).sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (b * 0.80f).sp,
            lineHeight = (b * 1.45f).sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = b.sp,
            lineHeight = (b * 1.38f).sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (b * 0.85f).sp,
            lineHeight = (b * 1.43f).sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (b * 0.75f).sp,
            lineHeight = (b * 1.33f).sp
        )
    )
}
