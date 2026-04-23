package com.dgp.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.DgpService
import com.dgp.ui.components.GhostButton
import com.dgp.ui.components.PrimaryButton
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType

@Composable
private fun RevealSheetContent(
    service: DgpService,
    password: String,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = service.name,
                style = type.serviceName,
                color = editorial.ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .background(editorial.accentSoft, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(service.type, style = type.chipLabel, color = editorial.ink)
            }
        }

        Spacer(Modifier.height(12.dp))

        var revealed by remember { mutableStateOf(false) }
        val masked = "•".repeat(password.length.coerceAtLeast(1))

        Box(
            Modifier
                .fillMaxWidth()
                .background(editorial.paperElev, RoundedCornerShape(6.dp))
                .border(1.dp, editorial.rule, RoundedCornerShape(6.dp))
                .padding(12.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            revealed = true
                            tryAwaitRelease()
                            revealed = false
                        },
                    )
                },
        ) {
            Text(
                text = if (revealed) password else masked,
                style = type.passwordDisplay,
                color = editorial.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "press and hold to reveal",
            style = type.caption,
            color = editorial.inkFaint,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "${service.type} · ${password.length} chars",
            style = type.serviceSubtitle,
            color = editorial.inkMuted,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                text = "copy",
                onClick = onCopy,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Copy" },
                leadingIcon = {
                    Icon(Icons.Rounded.ContentCopy, null, tint = editorial.accentInk)
                },
            )
            GhostButton(
                text = "edit",
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Edit" },
                leadingIcon = {
                    Icon(Icons.Rounded.Edit, null, tint = editorial.ink)
                },
            )
            GhostButton(
                text = if (service.archived) "unarchive" else "archive",
                onClick = onArchive,
                modifier = Modifier.weight(1f),
                leadingIcon = {
                    Icon(Icons.Rounded.Archive, null, tint = editorial.ink)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevealSheet(
    service: DgpService,
    passwordProvider: () -> String,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    val password = remember { passwordProvider() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.editorial.paperElev,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        RevealSheetContent(
            service = service,
            password = password,
            onCopy = onCopy,
            onEdit = onEdit,
            onArchive = onArchive,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun RevealSheetLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        RevealSheetContent(
            service = DgpService(name = "github", type = "hexlong"),
            password = "a1b2c3d4e5f6a7b8",
            onCopy = {},
            onEdit = {},
            onArchive = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121110, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RevealSheetDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        RevealSheetContent(
            service = DgpService(name = "old-bank", type = "vault", archived = true),
            password = "correct-horse-battery-staple",
            onCopy = {},
            onEdit = {},
            onArchive = {},
        )
    }
}
