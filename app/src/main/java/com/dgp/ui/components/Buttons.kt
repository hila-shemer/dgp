package com.dgp.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(editorial.accent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Box(Modifier.size(20.dp)) { leadingIcon() }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = type.button,
            color = editorial.accentInk,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType
    val shape = RoundedCornerShape(6.dp)
    val borderColor = if (enabled) editorial.rule else editorial.inkFaint
    val textColor = if (enabled) editorial.ink else editorial.inkFaint

    Row(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Box(Modifier.size(20.dp)) { leadingIcon() }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = type.button,
            color = textColor,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun ButtonsLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text = "Generate", onClick = {})
            PrimaryButton(text = "Generate", onClick = {}, enabled = false)
            PrimaryButton(
                text = "Add service",
                onClick = {},
                leadingIcon = {
                    Icon(Icons.Rounded.Add, contentDescription = null,
                        tint = MaterialTheme.editorial.accentInk)
                },
            )
            GhostButton(text = "Cancel", onClick = {})
            GhostButton(text = "Cancel", onClick = {}, enabled = false)
            GhostButton(
                text = "Import",
                onClick = {},
                leadingIcon = {
                    Icon(Icons.Rounded.Add, contentDescription = null,
                        tint = MaterialTheme.editorial.ink)
                },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121110, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ButtonsDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text = "Generate", onClick = {})
            PrimaryButton(text = "Generate", onClick = {}, enabled = false)
            PrimaryButton(
                text = "Add service",
                onClick = {},
                leadingIcon = {
                    Icon(Icons.Rounded.Add, contentDescription = null,
                        tint = MaterialTheme.editorial.accentInk)
                },
            )
            GhostButton(text = "Cancel", onClick = {})
            GhostButton(text = "Cancel", onClick = {}, enabled = false)
            GhostButton(
                text = "Import",
                onClick = {},
                leadingIcon = {
                    Icon(Icons.Rounded.Add, contentDescription = null,
                        tint = MaterialTheme.editorial.ink)
                },
            )
        }
    }
}
