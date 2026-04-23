package com.dgp.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.ui.theme.EditorialMotion
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.LocalIsDarkTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial

@Composable
fun ThemeSwitchPill(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorial = MaterialTheme.editorial
    val isDark = LocalIsDarkTheme.current
    val segments = listOf(
        Triple(ThemeMode.Auto, Icons.Rounded.BrightnessAuto, "Theme auto"),
        Triple(ThemeMode.Light, Icons.Rounded.WbSunny, "Theme light"),
        Triple(ThemeMode.Dark, Icons.Rounded.DarkMode, "Theme dark"),
    )
    val activeIndex = segments.indexOfFirst { it.first == mode }.coerceAtLeast(0)
    val offsetX by animateDpAsState(
        targetValue = (activeIndex * 32).dp,
        animationSpec = spring(
            stiffness = EditorialMotion.springStiffness,
            dampingRatio = EditorialMotion.springDamping,
        ),
        label = "theme-pill-highlight",
    )
    val highlightFill = if (isDark) editorial.paper else editorial.ink.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .height(32.dp)
            .width((segments.size * 32).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(editorial.paperElev)
            .border(1.dp, editorial.rule, RoundedCornerShape(12.dp)),
    ) {
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .padding(4.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(highlightFill),
        )
        Row {
            segments.forEach { (segMode, icon, cd) ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.RadioButton) { onModeChange(segMode) }
                        .semantics {
                            contentDescription = cd
                            selected = (segMode == mode)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (segMode == mode) editorial.ink else editorial.inkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun ThemeSwitchPillLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        Row(Modifier.padding(12.dp)) {
            ThemeSwitchPill(mode = ThemeMode.Auto, onModeChange = {})
            Spacer(Modifier.width(12.dp))
            ThemeSwitchPill(mode = ThemeMode.Light, onModeChange = {})
            Spacer(Modifier.width(12.dp))
            ThemeSwitchPill(mode = ThemeMode.Dark, onModeChange = {})
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF121110,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ThemeSwitchPillDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        Row(Modifier.padding(12.dp)) {
            ThemeSwitchPill(mode = ThemeMode.Auto, onModeChange = {})
            Spacer(Modifier.width(12.dp))
            ThemeSwitchPill(mode = ThemeMode.Light, onModeChange = {})
            Spacer(Modifier.width(12.dp))
            ThemeSwitchPill(mode = ThemeMode.Dark, onModeChange = {})
        }
    }
}
