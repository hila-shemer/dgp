package com.dgp.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import java.time.LocalTime
import kotlinx.coroutines.delay

enum class ThemeMode { Auto, Light, Dark }

val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun EditorialTheme(
    mode: ThemeMode = ThemeMode.Auto,
    content: @Composable () -> Unit,
) {
    val isDark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Auto -> {
            val hour = produceState(initialValue = LocalTime.now().hour) {
                while (true) {
                    kotlinx.coroutines.delay(10 * 60 * 1000L)
                    value = LocalTime.now().hour
                }
            }.value
            hour !in 7..17
        }
    }

    val colors = if (isDark) EditorialColorsDark else EditorialColorsLight
    val type = editorialType()

    val animSpec: AnimationSpec<Color> = tween(
        durationMillis = 180,
        easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f),
    )
    val animated = EditorialColors(
        paper = animateColorAsState(colors.paper, animSpec, label = "paper").value,
        paperElev = animateColorAsState(colors.paperElev, animSpec, label = "paperElev").value,
        ink = animateColorAsState(colors.ink, animSpec, label = "ink").value,
        inkMuted = animateColorAsState(colors.inkMuted, animSpec, label = "inkMuted").value,
        inkFaint = animateColorAsState(colors.inkFaint, animSpec, label = "inkFaint").value,
        rule = animateColorAsState(colors.rule, animSpec, label = "rule").value,
        ruleStrong = animateColorAsState(colors.ruleStrong, animSpec, label = "ruleStrong").value,
        accent = animateColorAsState(colors.accent, animSpec, label = "accent").value,
        accentInk = animateColorAsState(colors.accentInk, animSpec, label = "accentInk").value,
        accentSoft = animateColorAsState(colors.accentSoft, animSpec, label = "accentSoft").value,
        danger = animateColorAsState(colors.danger, animSpec, label = "danger").value,
        pinDot = animateColorAsState(colors.pinDot, animSpec, label = "pinDot").value,
        stripAlnum = animateColorAsState(colors.stripAlnum, animSpec, label = "stripAlnum").value,
        stripAlnumLong = animateColorAsState(colors.stripAlnumLong, animSpec, label = "stripAlnumLong").value,
        stripHex = animateColorAsState(colors.stripHex, animSpec, label = "stripHex").value,
        stripHexLong = animateColorAsState(colors.stripHexLong, animSpec, label = "stripHexLong").value,
        stripBase58 = animateColorAsState(colors.stripBase58, animSpec, label = "stripBase58").value,
        stripBase58Long = animateColorAsState(colors.stripBase58Long, animSpec, label = "stripBase58Long").value,
        stripXkcd = animateColorAsState(colors.stripXkcd, animSpec, label = "stripXkcd").value,
        stripXkcdLong = animateColorAsState(colors.stripXkcdLong, animSpec, label = "stripXkcdLong").value,
        stripVault = animateColorAsState(colors.stripVault, animSpec, label = "stripVault").value,
    )

    val cs: ColorScheme = if (isDark) {
        darkColorScheme(
            background = animated.paper,
            surface = animated.paperElev,
            onBackground = animated.ink,
            onSurface = animated.ink,
            onSurfaceVariant = animated.inkMuted,
            primary = animated.accent,
            onPrimary = animated.accentInk,
            primaryContainer = animated.accentSoft,
            onPrimaryContainer = animated.ink,
            error = animated.danger,
            onError = animated.paper,
            outline = animated.rule,
            outlineVariant = animated.ruleStrong,
            secondary = animated.inkMuted,
            onSecondary = animated.paper,
            secondaryContainer = animated.paperElev,
            onSecondaryContainer = animated.ink,
            tertiary = animated.inkFaint,
            onTertiary = animated.paper,
            tertiaryContainer = animated.paperElev,
            onTertiaryContainer = animated.ink,
        )
    } else {
        lightColorScheme(
            background = animated.paper,
            surface = animated.paperElev,
            onBackground = animated.ink,
            onSurface = animated.ink,
            onSurfaceVariant = animated.inkMuted,
            primary = animated.accent,
            onPrimary = animated.accentInk,
            primaryContainer = animated.accentSoft,
            onPrimaryContainer = animated.ink,
            error = animated.danger,
            onError = animated.paper,
            outline = animated.rule,
            outlineVariant = animated.ruleStrong,
            secondary = animated.inkMuted,
            onSecondary = animated.paper,
            secondaryContainer = animated.paperElev,
            onSecondaryContainer = animated.ink,
            tertiary = animated.inkFaint,
            onTertiary = animated.paper,
            tertiaryContainer = animated.paperElev,
            onTertiaryContainer = animated.ink,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = colors.paper.toArgb()
                window.navigationBarColor = colors.paper.toArgb()
                val insetsController = WindowInsetsControllerCompat(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalEditorialColors provides animated,
        LocalEditorialType provides type,
        LocalIsDarkTheme provides isDark,
    ) {
        MaterialTheme(colorScheme = cs, content = content)
    }
}
