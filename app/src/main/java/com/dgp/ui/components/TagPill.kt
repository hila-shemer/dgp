package com.dgp.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType

@Composable
fun TagPill(
    flag: String,
    count: Int?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType
    val shape = RoundedCornerShape(6.dp)
    val fill = if (active) editorial.accent else editorial.paperElev
    val textColor = if (active) editorial.accentInk else editorial.ink

    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .background(fill, shape)
            .then(if (!active) Modifier.border(1.dp, editorial.rule, shape) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = flag,
            style = type.chipLabel.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$count",
                style = type.chipLabel.copy(fontWeight = FontWeight.Medium),
                color = textColor,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun TagPillLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        Row(Modifier.padding(8.dp)) {
            TagPill(flag = "--all", count = null, active = true, onClick = {})
            Spacer(Modifier.width(6.dp))
            TagPill(flag = "--pinned", count = 3, active = false, onClick = {})
            Spacer(Modifier.width(6.dp))
            TagPill(flag = "--tag work", count = 7, active = false, onClick = {})
            Spacer(Modifier.width(6.dp))
            TagPill(flag = "--archived", count = 1, active = false, onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121110, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TagPillDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        Row(Modifier.padding(8.dp)) {
            TagPill(flag = "--all", count = null, active = true, onClick = {})
            Spacer(Modifier.width(6.dp))
            TagPill(flag = "--pinned", count = 3, active = false, onClick = {})
            Spacer(Modifier.width(6.dp))
            TagPill(flag = "--tag work", count = 7, active = false, onClick = {})
            Spacer(Modifier.width(6.dp))
            TagPill(flag = "--archived", count = 1, active = false, onClick = {})
        }
    }
}
