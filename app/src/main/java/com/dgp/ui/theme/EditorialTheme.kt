package com.dgp.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import java.time.LocalTime
import kotlinx.coroutines.delay

enum class ThemeMode { Auto, Light, Dark }

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

    val cs: ColorScheme = if (isDark) {
        darkColorScheme(
            background = colors.paper,
            surface = colors.paperElev,
            onBackground = colors.ink,
            onSurface = colors.ink,
            onSurfaceVariant = colors.inkMuted,
            primary = colors.accent,
            onPrimary = colors.accentInk,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.ink,
            error = colors.danger,
            onError = colors.paper,
            outline = colors.rule,
            outlineVariant = colors.ruleStrong,
            secondary = colors.inkMuted,
            onSecondary = colors.paper,
            secondaryContainer = colors.paperElev,
            onSecondaryContainer = colors.ink,
            tertiary = colors.inkFaint,
            onTertiary = colors.paper,
            tertiaryContainer = colors.paperElev,
            onTertiaryContainer = colors.ink,
        )
    } else {
        lightColorScheme(
            background = colors.paper,
            surface = colors.paperElev,
            onBackground = colors.ink,
            onSurface = colors.ink,
            onSurfaceVariant = colors.inkMuted,
            primary = colors.accent,
            onPrimary = colors.accentInk,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.ink,
            error = colors.danger,
            onError = colors.paper,
            outline = colors.rule,
            outlineVariant = colors.ruleStrong,
            secondary = colors.inkMuted,
            onSecondary = colors.paper,
            secondaryContainer = colors.paperElev,
            onSecondaryContainer = colors.ink,
            tertiary = colors.inkFaint,
            onTertiary = colors.paper,
            tertiaryContainer = colors.paperElev,
            onTertiaryContainer = colors.ink,
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
        LocalEditorialColors provides colors,
        LocalEditorialType provides type,
    ) {
        MaterialTheme(colorScheme = cs, content = content)
    }
}
