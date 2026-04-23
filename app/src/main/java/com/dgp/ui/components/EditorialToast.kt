package com.dgp.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dgp.ui.theme.EditorialMotion
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType
import kotlinx.coroutines.delay

sealed class CopyToastState {
    object Idle : CopyToastState()
    data class Visible(val serviceName: String) : CopyToastState()
}

@Composable
fun CopyToast(
    state: CopyToastState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType
    val shape = RoundedCornerShape(8.dp)

    // Retain last service name so the exit animation still shows the text
    var lastServiceName by remember { mutableStateOf("") }
    if (state is CopyToastState.Visible) {
        lastServiceName = state.serviceName
    }

    LaunchedEffect(state) {
        if (state is CopyToastState.Visible) {
            delay(EditorialMotion.toastDwellMs.toLong())
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = state is CopyToastState.Visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(EditorialMotion.toastEnterMs),
        ) + fadeIn(tween(EditorialMotion.toastEnterMs)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(EditorialMotion.toastExitMs),
        ) + fadeOut(tween(EditorialMotion.toastExitMs)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(editorial.paperElev, shape)
                .border(1.dp, editorial.rule, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = editorial.accent,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "copied · $lastServiceName",
                style = type.body,
                color = editorial.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "undo",
                style = type.button.copy(fontSize = 13.sp),
                color = editorial.accent,
                modifier = Modifier.clickable(onClick = onUndo),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun CopyToastLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        Column(Modifier.padding(16.dp)) {
            CopyToast(
                state = CopyToastState.Visible("github"),
                onUndo = {},
                onDismiss = {},
            )
            CopyToast(
                state = CopyToastState.Idle,
                onUndo = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121110, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CopyToastDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        Column(Modifier.padding(16.dp)) {
            CopyToast(
                state = CopyToastState.Visible("github"),
                onUndo = {},
                onDismiss = {},
            )
            CopyToast(
                state = CopyToastState.Idle,
                onUndo = {},
                onDismiss = {},
            )
        }
    }
}
