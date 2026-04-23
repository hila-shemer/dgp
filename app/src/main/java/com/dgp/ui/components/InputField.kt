package com.dgp.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType

@Composable
fun EditorialInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = true,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType
    val shape = RoundedCornerShape(6.dp)

    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = editorial.accent,
            backgroundColor = editorial.accentSoft,
        )
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .heightIn(min = 48.dp)
                .fillMaxWidth()
                .background(editorial.paperElev, shape)
                .border(1.dp, editorial.ruleStrong, shape)
                .padding(horizontal = 12.dp),
            textStyle = type.inputValue.copy(color = editorial.ink),
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(editorial.accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    leadingIcon?.let { it(); Spacer(Modifier.width(8.dp)) }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = type.inputValue,
                                color = editorial.inkFaint,
                            )
                        }
                        innerTextField()
                    }
                    trailingIcon?.let { Spacer(Modifier.width(8.dp)); it() }
                }
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun InputFieldLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        var focused by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            // empty
            EditorialInputField(value = "", onValueChange = {}, placeholder = "service name")
            // filled
            EditorialInputField(value = "nadav@example", onValueChange = {}, placeholder = "email")
            // focused (requests focus on composition; static in non-interactive preview)
            EditorialInputField(
                value = focused,
                onValueChange = { focused = it },
                placeholder = "type here",
                modifier = Modifier.focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            // masked
            EditorialInputField(
                value = "sup3r-secret",
                onValueChange = {},
                placeholder = "password",
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.editorial.inkMuted,
                    )
                },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121110, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun InputFieldDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        var focused by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            EditorialInputField(value = "", onValueChange = {}, placeholder = "service name")
            EditorialInputField(value = "nadav@example", onValueChange = {}, placeholder = "email")
            EditorialInputField(
                value = focused,
                onValueChange = { focused = it },
                placeholder = "type here",
                modifier = Modifier.focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            EditorialInputField(
                value = "sup3r-secret",
                onValueChange = {},
                placeholder = "password",
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.editorial.inkMuted,
                    )
                },
            )
        }
    }
}
