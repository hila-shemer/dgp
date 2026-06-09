package com.dgp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType

/**
 * The flag gallery. Order is LOAD-BEARING: DgpEngine.flagIndexFor maps into
 * this list, and index 0 is the vanity-mining target (DgpEngine.REFERENCE_FLAG_INDEX).
 * Keep the size in sync with DgpEngine.FLAG_COUNT.
 */
object FlagGallery {
    data class Flag(val stripes: List<Long>)

    val flags: List<Flag> = listOf(
        Flag(listOf(0xFF5BCEFA, 0xFFF5A9B8, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA)),
        Flag(listOf(0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF004DFF, 0xFF750787)),
        Flag(listOf(0xFFD60270, 0xFFD60270, 0xFF9B4F96, 0xFF0038A8, 0xFF0038A8)),
        Flag(listOf(0xFFFF218C, 0xFFFFD800, 0xFF21B1FF)),
        Flag(listOf(0xFFD52D00, 0xFFFF9A56, 0xFFFFFFFF, 0xFFD362A4, 0xFFA30262)),
        Flag(listOf(0xFFFCF434, 0xFFFFFFFF, 0xFF9C59D1, 0xFF2C2C2C)),
        Flag(listOf(0xFF000000, 0xFFA3A3A3, 0xFFFFFFFF, 0xFF800080)),
        Flag(listOf(0xFFFF75A2, 0xFFFFFFFF, 0xFFBE18D6, 0xFF000000, 0xFF333EBD)),
        Flag(listOf(0xFF000000, 0xFFBCC4C7, 0xFFFFFFFF, 0xFFB7F684, 0xFFFFFFFF, 0xFFBCC4C7, 0xFF000000)),
        Flag(listOf(0xFFB57EDC, 0xFFFFFFFF, 0xFF4A8123)),
    )
}

/** Equal-height horizontal stripes for one flag. Caller sets size via [modifier]. */
@Composable
fun FlagSwatch(flagIndex: Int, modifier: Modifier = Modifier, cornerDp: Int = 6) {
    val flag = FlagGallery.flags[flagIndex.coerceIn(0, FlagGallery.flags.size - 1)]
    Column(modifier.clip(RoundedCornerShape(cornerDp.dp))) {
        flag.stripes.forEach { c ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(c)),
            )
        }
    }
}

/** Flag swatch + two-word label — the full fingerprint chip used in the account dialog. */
@Composable
fun FlagChip(flagIndex: Int, word: String, modifier: Modifier = Modifier) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FlagSwatch(flagIndex, Modifier.size(width = 40.dp, height = 28.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = word, style = type.chipLabel, color = editorial.ink)
    }
}
