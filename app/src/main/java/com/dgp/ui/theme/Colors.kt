package com.dgp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class EditorialColors(
    val paper: Color, val paperElev: Color,
    val ink: Color, val inkMuted: Color, val inkFaint: Color,
    val rule: Color, val ruleStrong: Color,
    val accent: Color, val accentInk: Color, val accentSoft: Color,
    val danger: Color, val pinDot: Color,
    val stripAlnum: Color, val stripAlnumLong: Color,
    val stripHex: Color, val stripHexLong: Color,
    val stripBase58: Color, val stripBase58Long: Color,
    val stripXkcd: Color, val stripXkcdLong: Color,
    val stripVault: Color,
)

val EditorialColorsLight = EditorialColors(
    paper = Color(0xFFF5F3EE), paperElev = Color(0xFFFFFFFF),
    ink = Color(0xFF1C1A15), inkMuted = Color(0xFF6B6659), inkFaint = Color(0xFFA39D8E),
    rule = Color(0xFFE3DFD4), ruleStrong = Color(0xFFCFC9BC),
    accent = Color(0xFF7FA650), accentInk = Color(0xFF1C1A15), accentSoft = Color(0xFFE8F0D8),
    danger = Color(0xFFB5412A), pinDot = Color(0xFF7FA650),
    stripAlnum = Color(0xFFC4A95A), stripAlnumLong = Color(0xFF9B8A4E),
    stripHex = Color(0xFF4A9C8B), stripHexLong = Color(0xFF3E8477),
    stripBase58 = Color(0xFF8C6CB8), stripBase58Long = Color(0xFF75579C),
    stripXkcd = Color(0xFF5E7CC4), stripXkcdLong = Color(0xFF4A66A8),
    stripVault = Color(0xFFB8603C),
)

val EditorialColorsDark = EditorialColors(
    paper = Color(0xFF121110), paperElev = Color(0xFF1C1A18),
    ink = Color(0xFFE8E4D8), inkMuted = Color(0xFF8F897C), inkFaint = Color(0xFF5C574E),
    rule = Color(0xFF2A2824), ruleStrong = Color(0xFF3A3832),
    accent = Color(0xFFB8D878), accentInk = Color(0xFF121110), accentSoft = Color(0xFF2E3A1F),
    danger = Color(0xFFE06A50), pinDot = Color(0xFFB8D878),
    stripAlnum = Color(0xFFD9BC63), stripAlnumLong = Color(0xFFC2AE63),
    stripHex = Color(0xFF6FB8A8), stripHexLong = Color(0xFF5AA396),
    stripBase58 = Color(0xFFB094D4), stripBase58Long = Color(0xFF9A7DC2),
    stripXkcd = Color(0xFF8098D4), stripXkcdLong = Color(0xFF6D84BE),
    stripVault = Color(0xFFD98060),
)

val LocalEditorialColors = staticCompositionLocalOf<EditorialColors> {
    error("EditorialColors not provided — wrap content in EditorialTheme")
}

val MaterialTheme.editorial: EditorialColors
    @Composable @ReadOnlyComposable
    get() = LocalEditorialColors.current
