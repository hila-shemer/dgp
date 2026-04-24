package com.dgp.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.DgpService
import com.dgp.engine.DgpEngine
import com.dgp.security.ConfigCrypto
import com.dgp.ui.components.EditorialInputField
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType
import com.dgp.ui.theme.stripFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class TileInfo(val type: String, val label: String, val description: String)

private val TYPE_TILES = listOf(
    TileInfo("alnum",      "alnum",      "8 mixed-case alphanum"),
    TileInfo("alnumlong",  "alnumlong",  "12 mixed-case alphanum"),
    TileInfo("hex",        "hex",        "8 hex chars"),
    TileInfo("hexlong",    "hexlong",    "16 hex chars"),
    TileInfo("base58",     "base58",     "8 base58 chars"),
    TileInfo("base58long", "base58long", "12 base58 chars"),
    TileInfo("xkcd",       "xkcd",       "4 bip-39 words"),
    TileInfo("xkcdlong",   "xkcdlong",   "6 bip-39 words"),
    TileInfo("vault",      "vault",      "store externally-assigned secret"),
)

@Composable
private fun TypeTile(
    type: String,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorial = MaterialTheme.editorial
    val t = MaterialTheme.editorialType
    Box(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) editorial.accentSoft else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) editorial.ink else editorial.rule,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "type-$type"
                testTag = "type-tile-$type"
            },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(6.dp).background(editorial.stripFor(type)))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = t.rmLabel, color = editorial.ink, maxLines = 1)
                Text(description, style = t.caption, color = editorial.inkMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FlagRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    rowTestTag: String,
) {
    val editorial = MaterialTheme.editorial
    val t = MaterialTheme.editorialType
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = t.body, color = editorial.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { testTag = rowTestTag },
            colors = SwitchDefaults.colors(
                checkedThumbColor = editorial.accentInk,
                checkedTrackColor = editorial.accent,
                uncheckedThumbColor = editorial.inkMuted,
                uncheckedTrackColor = editorial.rule,
                uncheckedBorderColor = editorial.rule,
            ),
        )
    }
}

@Composable
fun EditEntryScreen(
    service: DgpService?,
    initialName: String = "",
    seed: String,
    account: String,
    engine: DgpEngine,
    onSave: (DgpService) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val editorial = MaterialTheme.editorial
    val t = MaterialTheme.editorialType

    val originalName = remember { service?.name ?: "" }
    val originalAccount = remember { account }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember(service, initialName) { mutableStateOf(service?.name ?: initialName) }
    var type by remember { mutableStateOf(service?.type ?: "alnum") }
    var comment by remember { mutableStateOf(service?.comment ?: "") }
    var pinned by remember { mutableStateOf(service?.pinned ?: false) }
    var archived by remember { mutableStateOf(service?.archived ?: false) }
    var vaultSecret by remember { mutableStateOf("") }
    var vaultVisible by remember { mutableStateOf(false) }
    var vaultDecryptFailed by remember { mutableStateOf(false) }
    var initialVaultPlaintext by remember { mutableStateOf("") }

    // One-shot decrypt on mount for existing vault entry.
    // Decrypt uses originalName + originalAccount so rename doesn't re-key until save.
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // One-shot decrypt on mount for existing vault entry.
    LaunchedEffect(Unit) {
        if (service != null && service.type == "vault" && service.encryptedSecret != null) {
            val key = withContext(Dispatchers.Default) {
                engine.deriveAesKey(seed, originalName, originalAccount)
            }
            val decrypted = ConfigCrypto.decryptWithRawKey(service.encryptedSecret, key)
            if (decrypted != null) {
                vaultSecret = decrypted
                initialVaultPlaintext = decrypted
            } else {
                vaultDecryptFailed = true
            }
        }
    }

    // Debounced live preview — skip when vault or blank name
    var previewValue by remember { mutableStateOf("") }
    var previewLoading by remember { mutableStateOf(false) }
    LaunchedEffect(seed, name, type, account) {
        if (name.isBlank() || type == "vault") {
            previewValue = ""
            previewLoading = false
            return@LaunchedEffect
        }
        previewLoading = true
        delay(100)
        previewValue = withContext(Dispatchers.Default) {
            engine.generate(seed, name, type, account)
        }
        previewLoading = false
    }

    val hasUnsavedChanges = name != (service?.name ?: "") ||
        type != (service?.type ?: "alnum") ||
        comment != (service?.comment ?: "") ||
        pinned != (service?.pinned ?: false) ||
        archived != (service?.archived ?: false) ||
        (type == "vault" && vaultSecret != initialVaultPlaintext)

    val canSave = name.isNotBlank()

    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }

    fun requestCloseWithUnsavedCheck() {
        if (hasUnsavedChanges) showDiscardConfirm = true else onClose()
    }

    fun attemptSave() {
        if (name.isBlank()) return
        val encryptedSecret = if (type == "vault" && vaultSecret.isNotEmpty()) {
            val key = engine.deriveAesKey(seed, name, account)
            ConfigCrypto.encryptWithRawKey(vaultSecret, key)
        } else null
        val updated = (service ?: DgpService(name = name, type = type)).copy(
            name = name,
            type = type,
            comment = comment,
            pinned = pinned,
            archived = archived,
            encryptedSecret = encryptedSecret,
        )
        onSave(updated)
    }

    BackHandler(enabled = true) { requestCloseWithUnsavedCheck() }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("discard changes?") },
            text = { Text("your edits will be lost.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onClose() }) {
                    Text("discard", color = editorial.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("keep") }
            },
        )
    }

    if (showDeleteConfirm && service != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("remove ${service.name}?") },
            text = { Text("cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("remove", color = editorial.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("cancel") }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(editorial.paper),
    ) {
        // Header: 56dp, paper background, rule bottom border
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { requestCloseWithUnsavedCheck() }
                    .semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = editorial.ink)
            }

            Text(
                text = if (service == null) "/dgp/new" else "/dgp/edit/$originalName",
                style = t.pathCrumb,
                color = editorial.inkMuted,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )

            Box(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canSave) editorial.accent else editorial.rule)
                    .clickable { attemptSave() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Save" },
                contentAlignment = Alignment.Center,
            ) {
                Text("save", style = t.button, color = editorial.accentInk)
            }
        }
        HorizontalDivider(color = editorial.rule, thickness = 1.dp)

        // Body: scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Name field
            Column {
                Text("name", style = t.caption, color = editorial.inkMuted)
                Spacer(Modifier.height(4.dp))
                EditorialInputField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "service-name",
                    leadingIcon = { Text("❯", color = editorial.accent, style = t.inputValue) },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .semantics { testTag = "service-name-input" },
                )
            }

            // Comment field
            Column {
                Row {
                    Text("comment", style = t.caption, color = editorial.inkMuted)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "// optional — shown as subtitle in the list",
                        style = t.caption,
                        color = editorial.inkFaint,
                    )
                }
                Spacer(Modifier.height(4.dp))
                EditorialInputField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = "optional note",
                    singleLine = true,
                    modifier = Modifier.semantics { testTag = "service-comment-input" },
                )
            }

            // Password type — 3×3 grid (no LazyVerticalGrid; avoids nested scroll pain)
            Column {
                Text("password type // 9 formats", style = t.caption, color = editorial.inkMuted)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TYPE_TILES.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { tile ->
                                TypeTile(
                                    type = tile.type,
                                    label = tile.label,
                                    description = tile.description,
                                    selected = type == tile.type,
                                    onClick = { type = tile.type },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // Derive preview — only when not vault and name is non-blank
            if (type != "vault" && name.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // TODO(phase-10): dashed border
                        .background(editorial.paperElev, RoundedCornerShape(6.dp))
                        .border(1.dp, editorial.rule, RoundedCornerShape(6.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        "preview · derives from seed+account+name",
                        style = t.caption,
                        color = editorial.inkMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    val previewText = when {
                        previewLoading -> "…"
                        previewVisible -> previewValue
                        else -> "•".repeat(previewValue.length.coerceAtLeast(1))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = previewText,
                            style = t.passwordDisplay,
                            color = editorial.ink,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { previewVisible = !previewVisible },
                            modifier = Modifier.semantics {
                                contentDescription = if (previewVisible) "Hide preview" else "Show preview"
                            },
                        ) {
                            Icon(
                                imageVector = if (previewVisible) Icons.Filled.VisibilityOff
                                              else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = editorial.inkMuted,
                            )
                        }
                    }
                }
            }

            // Vault secret — only when vault type
            if (type == "vault") {
                Column {
                    Text("vault secret", style = t.caption, color = editorial.inkMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "stores an externally-assigned secret tied to this seed + account + service name.",
                        style = t.caption,
                        color = editorial.inkFaint,
                    )
                    if (vaultDecryptFailed) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "existing secret could not be decrypted — saving will overwrite it.",
                            color = editorial.danger,
                            style = t.caption,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    EditorialInputField(
                        value = vaultSecret,
                        onValueChange = { vaultSecret = it },
                        placeholder = "secret",
                        singleLine = false,
                        visualTransformation = if (vaultVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { vaultVisible = !vaultVisible },
                                modifier = Modifier.semantics {
                                    contentDescription = if (vaultVisible) "Hide secret" else "Show secret"
                                },
                            ) {
                                Icon(
                                    imageVector = if (vaultVisible) Icons.Filled.VisibilityOff
                                                  else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = editorial.inkMuted,
                                )
                            }
                        },
                        modifier = Modifier.semantics { testTag = "vault-secret-input" },
                    )
                }
            }

            // Tags — read-only; hidden when empty
            val tagsText = service?.tags?.joinToString(" ") { "#$it" } ?: ""
            if (tagsText.isNotEmpty()) {
                Column {
                    Text("tags", style = t.caption, color = editorial.inkMuted)
                    Text(tagsText, style = t.rmLabel, color = editorial.ink)
                }
            }

            // Flags (pinned + archive toggle)
            Column {
                Text("flags", style = t.caption, color = editorial.inkMuted)
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, editorial.ruleStrong, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    FlagRow(
                        label = "pinned",
                        checked = pinned,
                        onCheckedChange = { pinned = it },
                        rowTestTag = "pinned-toggle",
                    )
                    HorizontalDivider(color = editorial.rule, thickness = 1.dp)
                    FlagRow(
                        label = if (archived) "archived" else "archive",
                        checked = archived,
                        onCheckedChange = { archived = it },
                        rowTestTag = "archived-toggle",
                    )
                }
            }

            // Delete button — only when editing an existing service
            if (service != null) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .border(1.dp, editorial.danger, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showDeleteConfirm = true }
                            .padding(horizontal = 16.dp)
                            .semantics { contentDescription = "Delete" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("rm $originalName", style = t.button, color = editorial.danger)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "removes this entry and its vault secret (if any). cannot be undone.",
                        style = t.caption,
                        color = editorial.inkFaint,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun EditEntryScreenNewLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        EditEntryScreen(
            service = null,
            seed = "preview-seed",
            account = "user@example.com",
            engine = DgpEngine(emptyList()),
            onSave = {},
            onDelete = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121110, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun EditEntryScreenVaultDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        EditEntryScreen(
            service = DgpService(
                name = "mybank",
                type = "vault",
                comment = "savings account",
                archived = false,
                pinned = true,
                encryptedSecret = null,
            ),
            seed = "preview-seed",
            account = "user@example.com",
            engine = DgpEngine(emptyList()),
            onSave = {},
            onDelete = {},
            onClose = {},
        )
    }
}
