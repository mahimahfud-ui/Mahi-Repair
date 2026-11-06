package com.mahi.repair.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Single swap point for the typeface. To use a bundled font (Inter, Geist, etc.):
 * add the .ttf files to res/font and replace with FontFamily(Font(R.font.inter_regular), ...).
 */
val MahiFontFamily: FontFamily = FontFamily.SansSerif

@Immutable
data class MahiTypography(
    val screenTitle: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    val headline: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    val title: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    val body: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    val bodySmall: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    val sectionLabel: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    val button: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    val caption: TextStyle = TextStyle(fontFamily = MahiFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
