package com.dgp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.ui.components.TagPill
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType

@Composable
fun SettingsScreen(
    clipboardTimeoutSec: Int,
    onClipboardTimeoutChange: (Int) -> Unit,
    clearOnLock: Boolean,
    onClearOnLockChange: (Boolean) -> Unit,
    compactRows: Boolean,
    onCompactRowsChange: (Boolean) -> Unit,
    seedFingerprint: String,
    onChangeSeed: () -> Unit,
    onRunTestVectors: () -> Unit,
    onExportConfig: () -> Unit,
    onImportEncryptedFile: () -> Unit,
    onImportEncrypted: () -> Unit,
    onImportPlaintext: () -> Unit,
    onClearAll: () -> Unit,
    onLockAndQuit: () -> Unit,
    onBack: () -> Unit,
) {
    val editorial = MaterialTheme.editorial
    val t = MaterialTheme.editorialType

    var showFingerprintFull by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showLockQuitConfirm by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(editorial.paper),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(editorial.paper)
                .border(width = 1.dp, color = editorial.rule,
                    shape = RoundedCornerShape(0.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Close Settings" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = editorial.ink,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = "/dgp/settings",
                style = t.pathCrumb,
                color = editorial.inkMuted,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }

        // Body
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {

            // ## derivation
            SectionHeader("derivation", t, editorial)
            Text(
                text = "fixed by protocol — never editable",
                style = t.caption,
                color = editorial.inkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            SectionCard(editorial) {
                SettingsRow(label = "algorithm", value = "pbkdf2-hmac-sha1")
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(label = "iterations", value = "42000")
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(label = "key length", value = "40 bytes")
            }

            Spacer(Modifier.height(20.dp))

            // ## clipboard
            SectionHeader("clipboard", t, editorial)
            SectionCard(editorial) {
                // auto-clear chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "auto-clear",
                        style = t.body,
                        color = editorial.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Row {
                        val options = listOf(
                            0 to "off",
                            10 to "10s",
                            30 to "30s",
                            60 to "60s",
                            120 to "2m",
                        )
                        options.forEach { (value, label) ->
                            TagPill(
                                flag = label,
                                count = null,
                                active = clipboardTimeoutSec == value,
                                onClick = { onClipboardTimeoutChange(value) },
                            )
                            if (value != 120) Spacer(Modifier.width(4.dp))
                        }
                    }
                }
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(
                    label = "clear on lock",
                    trailing = {
                        Switch(
                            checked = clearOnLock,
                            onCheckedChange = onClearOnLockChange,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = editorial.accent,
                                checkedThumbColor = editorial.accentInk,
                                uncheckedTrackColor = editorial.rule,
                                uncheckedThumbColor = editorial.inkFaint,
                                uncheckedBorderColor = editorial.ruleStrong,
                            ),
                        )
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ## backup
            SectionHeader("backup", t, editorial)
            SectionCard(editorial) {
                // seed fingerprint
                val fingerprintDisplay = if (showFingerprintFull) seedFingerprint
                else seedFingerprint.take(22) + if (seedFingerprint.length > 22) "…" else ""
                SettingsRow(
                    label = "seed fingerprint",
                    value = fingerprintDisplay,
                    onClick = { showFingerprintFull = !showFingerprintFull },
                    valueMonospace = true,
                )
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(label = "change seed", onClick = onChangeSeed)
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(label = "export (encrypted)", onClick = onExportConfig)
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(
                    label = "import (encrypted file)",
                    onClick = onImportEncryptedFile,
                )
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(
                    label = "import (encrypted, from clipboard)",
                    onClick = onImportEncrypted,
                )
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(
                    label = "import (plaintext json file)",
                    onClick = onImportPlaintext,
                )
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(
                    label = "run test vectors",
                    onClick = onRunTestVectors,
                    modifier = Modifier.semantics {
                        contentDescription = "Run Test Vectors"
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ## appearance
            SectionHeader("appearance", t, editorial)
            SectionCard(editorial) {
                SettingsRow(
                    label = "compact rows",
                    trailing = {
                        Switch(
                            checked = compactRows,
                            onCheckedChange = onCompactRowsChange,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = editorial.accent,
                                checkedThumbColor = editorial.accentInk,
                                uncheckedTrackColor = editorial.rule,
                                uncheckedThumbColor = editorial.inkFaint,
                                uncheckedBorderColor = editorial.ruleStrong,
                            ),
                        )
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ## danger zone
            SectionHeader("danger zone", t, editorial)
            SectionCard(editorial) {
                SettingsRow(
                    label = "rm all entries",
                    onClick = { showClearAllConfirm = true },
                    labelColor = editorial.danger,
                )
                Text(
                    text = "vault secrets cannot be recovered after this",
                    style = t.caption,
                    color = editorial.inkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                SettingsRow(
                    label = "lock and quit",
                    onClick = { showLockQuitConfirm = true },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("remove all entries?") },
            text = { Text("vault secrets cannot be recovered after this") },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllConfirm = false
                    onClearAll()
                }) { Text("remove", color = MaterialTheme.editorial.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("cancel") }
            },
        )
    }

    if (showLockQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showLockQuitConfirm = false },
            title = { Text("lock and quit?") },
            confirmButton = {
                TextButton(onClick = {
                    showLockQuitConfirm = false
                    onLockAndQuit()
                }) { Text("confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showLockQuitConfirm = false }) { Text("cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    name: String,
    t: com.dgp.ui.theme.EditorialType,
    editorial: com.dgp.ui.theme.EditorialColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "## ", style = t.chipLabel, color = editorial.accent)
        Text(text = name, style = t.sectionHeading, color = editorial.ink)
    }
}

@Composable
private fun SectionCard(
    editorial: com.dgp.ui.theme.EditorialColors,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, editorial.ruleStrong, RoundedCornerShape(6.dp)),
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    labelColor: Color = Color.Unspecified,
    valueMonospace: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val t = MaterialTheme.editorialType
    val editorial = MaterialTheme.editorial
    val resolvedLabelColor = if (labelColor == Color.Unspecified) editorial.ink else labelColor
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = t.body,
            color = resolvedLabelColor,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = if (valueMonospace) t.chipLabel else t.caption,
                color = editorial.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun SettingsScreenPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        SettingsScreen(
            clipboardTimeoutSec = 30,
            onClipboardTimeoutChange = {},
            clearOnLock = true,
            onClearOnLockChange = {},
            compactRows = false,
            onCompactRowsChange = {},
            seedFingerprint = "SHA-256: 1a2b3c4d5e6f7890",
            onChangeSeed = {},
            onRunTestVectors = {},
            onExportConfig = {},
            onImportEncryptedFile = {},
            onImportEncrypted = {},
            onImportPlaintext = {},
            onClearAll = {},
            onLockAndQuit = {},
            onBack = {},
        )
    }
}
