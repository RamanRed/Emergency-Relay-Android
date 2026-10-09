package com.raman.emergencyrelay.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Clean, friendly, HIGH-LEGIBILITY type scale for the emergency UI.
 *
 * Sizes are deliberately larger than Material defaults: under stress
 * and at arm's length, bigger and bolder reads faster. No bundled
 * font file — swap FontFamily.Default later for a rounded face.
 */
private val Sans = FontFamily.Default

val Typography = Typography(

    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),

    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),

    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),

    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp
    ),

    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),

    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp
    ),

    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )
)
