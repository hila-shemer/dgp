package com.dgp.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.DgpService
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType
import com.dgp.ui.theme.stripFor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceRow(
    service: DgpService,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeLeft: () -> Unit,   // copy
    onSwipeRight: () -> Unit,  // reveal pin/edit/archive row actions
    modifier: Modifier = Modifier,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowBg by animateColorAsState(
        targetValue = if (isPressed) editorial.accentSoft else Color.Transparent,
        animationSpec = tween(120),
        label = "rowBg",
    )

    // Swipe via detectHorizontalDragGestures (fallback — AnchoredDraggableState is experimental
    // in compose-bom 2024.10.01 and complex to wire reliably; pointerInput path is stable)
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var swipeFired by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(rowBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongPress,
                onClick = onTap,
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f; swipeFired = false },
                    onDragEnd = { dragAccum = 0f; swipeFired = false },
                    onDragCancel = { dragAccum = 0f; swipeFired = false },
                    onHorizontalDrag = { _, delta ->
                        if (!swipeFired) {
                            dragAccum += delta
                            val threshold = size.width * 0.33f
                            when {
                                dragAccum < -threshold -> { swipeFired = true; onSwipeLeft() }
                                dragAccum > threshold -> { swipeFired = true; onSwipeRight() }
                            }
                        }
                    },
                )
            },
    ) {
        // 3dp accent strip flush to the left edge, full row height
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(editorial.stripFor(service.type))
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 19.dp, end = 16.dp), // 3dp strip + 16dp gutter
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (service.pinned) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(editorial.pinDot)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = service.name,
                        style = type.serviceName,
                        color = editorial.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (service.comment.isNotBlank()) {
                    Text(
                        text = service.comment,
                        style = type.serviceSubtitle,
                        color = editorial.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Right cluster: type chip + chevron
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(editorial.accentSoft, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = service.type,
                        style = type.chipLabel,
                        color = editorial.ink,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = editorial.inkFaint,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomStart),
            color = editorial.rule,
            thickness = 1.dp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun ServiceRowLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        Column {
            ServiceRow(
                service = DgpService(name = "github", type = "hexlong", comment = "work@ex.com"),
                onTap = {}, onLongPress = {}, onSwipeLeft = {}, onSwipeRight = {},
            )
            ServiceRow(
                service = DgpService(name = "mastodon.social", type = "xkcd", comment = "", pinned = true),
                onTap = {}, onLongPress = {}, onSwipeLeft = {}, onSwipeRight = {},
            )
            ServiceRow(
                service = DgpService(name = "old-bank", type = "vault", comment = "legacy — do not rotate", archived = true),
                onTap = {}, onLongPress = {}, onSwipeLeft = {}, onSwipeRight = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121110, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ServiceRowDarkPreview() {
    EditorialTheme(mode = ThemeMode.Dark) {
        Column {
            ServiceRow(
                service = DgpService(name = "github", type = "hexlong", comment = "work@ex.com"),
                onTap = {}, onLongPress = {}, onSwipeLeft = {}, onSwipeRight = {},
            )
            ServiceRow(
                service = DgpService(name = "mastodon.social", type = "xkcd", comment = "", pinned = true),
                onTap = {}, onLongPress = {}, onSwipeLeft = {}, onSwipeRight = {},
            )
            ServiceRow(
                service = DgpService(name = "old-bank", type = "vault", comment = "legacy — do not rotate", archived = true),
                onTap = {}, onLongPress = {}, onSwipeLeft = {}, onSwipeRight = {},
            )
        }
    }
}
