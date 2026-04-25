package com.dgp.ui.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

fun Modifier.autoFocus(delayMillis: Long = 100): Modifier = composed {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        fr.requestFocus()
    }
    this.focusRequester(fr)
}
