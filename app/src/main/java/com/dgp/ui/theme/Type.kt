@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.dgp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dgp.R

private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular,  FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium,   FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private val RobotoFlex = FontFamily(
    Font(
        R.font.roboto_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.roboto_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.roboto_flex,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

private val Mono = JetBrainsMono
private val Sans = RobotoFlex

@Immutable
data class EditorialType(
    val screenTitle: TextStyle,
    val sectionHeading: TextStyle,
    val serviceName: TextStyle,
    val serviceSubtitle: TextStyle,
    val chipLabel: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val pathCrumb: TextStyle,
    val button: TextStyle,
    val rmLabel: TextStyle,
    val inputValue: TextStyle,
    val passwordDisplay: TextStyle,
)

fun editorialType(): EditorialType = EditorialType(
    screenTitle = TextStyle(
        fontFamily = Sans,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    sectionHeading = TextStyle(
        fontFamily = Sans,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    serviceName = TextStyle(
        fontFamily = Mono,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    serviceSubtitle = TextStyle(
        fontFamily = Sans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    chipLabel = TextStyle(
        fontFamily = Mono,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    body = TextStyle(
        fontFamily = Sans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    caption = TextStyle(
        fontFamily = Sans,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
    pathCrumb = TextStyle(
        fontFamily = Mono,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    button = TextStyle(
        fontFamily = Sans,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    rmLabel = TextStyle(
        fontFamily = Mono,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    inputValue = TextStyle(
        fontFamily = Mono,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    passwordDisplay = TextStyle(
        fontFamily = Mono,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
    ),
)

val LocalEditorialType = staticCompositionLocalOf<EditorialType> {
    error("EditorialType not provided — wrap content in EditorialTheme")
}

val MaterialTheme.editorialType: EditorialType
    @Composable @ReadOnlyComposable
    get() = LocalEditorialType.current
