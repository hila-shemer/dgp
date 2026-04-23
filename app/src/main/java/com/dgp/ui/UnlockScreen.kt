package com.dgp.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dgp.ui.components.EditorialInputField
import com.dgp.ui.components.GhostButton
import com.dgp.ui.components.PrimaryButton
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType
import kotlin.math.roundToInt

@Composable
fun UnlockScreen(
    error: Boolean,
    onUnlock: (seed: String) -> Unit,
    onScanQr: (onResult: (String) -> Unit) -> Unit,
    onBiometric: () -> Unit,
    onResetConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType

    var seed by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val shakeX = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error) {
            val amp = 12f
            val dur = 50
            shakeX.animateTo(-amp, tween(dur))
            shakeX.animateTo(amp, tween(dur))
            shakeX.animateTo(-amp, tween(dur))
            shakeX.animateTo(amp, tween(dur))
            shakeX.animateTo(-amp / 2f, tween(dur))
            shakeX.animateTo(0f, tween(dur))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "/dgp/",
                style = type.pathCrumb,
                color = editorial.inkMuted,
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable(onClick = {}),
            ) {
                Text(
                    text = "refresh",
                    style = type.caption,
                    color = editorial.inkMuted,
                )
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = editorial.inkMuted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "reset",
                style = type.caption,
                color = editorial.danger,
                modifier = Modifier.clickable(onClick = onResetConfig),
            )
        }

        Spacer(Modifier.height(140.dp))

        Text(
            text = "$ unlock",
            style = type.caption,
            color = editorial.inkMuted,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "enter seed.",
            style = type.screenTitle,
            color = editorial.ink,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "dgp derives every password from your seed. nothing is stored — losing the seed means losing everything.",
            style = type.body,
            color = editorial.inkMuted,
        )
        Spacer(Modifier.height(24.dp))

        EditorialInputField(
            value = seed,
            onValueChange = { seed = it },
            placeholder = "seed",
            modifier = Modifier
                .offset { IntOffset(shakeX.value.roundToInt(), 0) }
                .semantics { testTag = "seed-input" },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Text(
                    text = "❯",
                    color = editorial.accent,
                    style = type.inputValue,
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = if (visible) "Hide seed" else "Show seed",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { visible = !visible },
                    tint = editorial.inkMuted,
                )
            },
            imeAction = ImeAction.Go,
            onImeAction = { if (seed.isNotEmpty()) onUnlock(seed) },
        )

        if (error) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "wrong seed — could not decrypt config",
                style = type.caption,
                color = editorial.danger,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "scan qr",
                onClick = { onScanQr { scanned -> seed = scanned } },
                modifier = Modifier.weight(1f),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = editorial.ink,
                    )
                },
            )
            GhostButton(
                text = "biometric",
                onClick = onBiometric,
                modifier = Modifier.weight(1f),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = editorial.ink,
                    )
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        PrimaryButton(
            text = "unlock ›",
            enabled = seed.isNotEmpty(),
            onClick = { onUnlock(seed) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "unlock-button" },
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "pbkdf2-hmac-sha1",
                style = type.caption,
                color = editorial.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "42000 iters · 40b key",
                style = type.caption,
                color = editorial.inkFaint,
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun UnlockScreenLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        UnlockScreen(
            error = false,
            onUnlock = {},
            onScanQr = {},
            onBiometric = {},
            onResetConfig = {},
        )
    }
}

@Preview(showSystemUi = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun UnlockScreenDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        UnlockScreen(
            error = false,
            onUnlock = {},
            onScanQr = {},
            onBiometric = {},
            onResetConfig = {},
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun UnlockScreenErrorPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        UnlockScreen(
            error = true,
            onUnlock = {},
            onScanQr = {},
            onBiometric = {},
            onResetConfig = {},
        )
    }
}
