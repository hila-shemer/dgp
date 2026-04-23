package com.dgp.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.DgpService
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType
import com.dgp.ui.theme.stripFor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ReorderScreen(
    services: List<DgpService>,
    onDone: (List<DgpService>) -> Unit,
    onCancel: () -> Unit,
) {
    var workingOrder by remember(services) { mutableStateOf(services) }
    val listState = rememberLazyListState()
    val view = LocalView.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        workingOrder = workingOrder.toMutableList()
            .apply { add(to.index, removeAt(from.index)) }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    BackHandler(enabled = true) { onCancel() }

    val editorial = MaterialTheme.editorial
    val t = MaterialTheme.editorialType

    Column(Modifier.fillMaxSize().background(editorial.paper)) {
        // Header — 56dp, accent background
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(editorial.accent)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onCancel() }
                    .semantics { contentDescription = "Close Reorder" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = editorial.accentInk,
                    modifier = Modifier.size(24.dp),
                )
            }

            Text(
                text = "reorder · drag to reposition",
                style = t.sectionHeading,
                color = editorial.accentInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            Box(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(editorial.accentInk)
                    .clickable { onDone(workingOrder) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Done Reorder" },
                contentAlignment = Alignment.Center,
            ) {
                Text("done", style = t.button, color = editorial.accent)
            }
        }

        // Footnote row — 28dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(editorial.paper)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "order is persisted as the json array order",
                style = t.caption,
                color = editorial.inkMuted,
            )
        }

        // Reorderable list
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(workingOrder, key = { it.id }) { svc ->
                ReorderableItem(reorderState, key = svc.id) { isDragging ->
                    // Handle only on the drag icon so the list itself can be scrolled.
                    ReorderCard(
                        service = svc,
                        isDragging = isDragging,
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStarted = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderCard(
    service: DgpService,
    isDragging: Boolean,
    dragHandleModifier: Modifier = Modifier,
) {
    val editorial = MaterialTheme.editorial
    val t = MaterialTheme.editorialType

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                rotationZ = if (isDragging) -1f else 0f
            }
            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(editorial.paperElev)
            .border(
                width = if (isDragging) 1.5.dp else 1.dp,
                color = if (isDragging) editorial.accent else editorial.rule,
                shape = RoundedCornerShape(6.dp),
            ),
    ) {
        // 3dp accent strip flush left
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(editorial.stripFor(service.type))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.name,
                    style = t.serviceName,
                    color = editorial.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (service.comment.isNotEmpty()) {
                    Text(
                        text = service.comment,
                        style = t.serviceSubtitle,
                        color = editorial.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Type chip
            Box(
                modifier = Modifier
                    .background(editorial.accentSoft, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = service.type,
                    style = t.chipLabel,
                    color = editorial.ink,
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(dragHandleModifier)
                    .semantics { contentDescription = "Reorder handle" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    tint = editorial.inkMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun ReorderScreenLightPreview() {
    EditorialTheme(mode = ThemeMode.Light) {
        ReorderScreen(
            services = listOf(
                DgpService(name = "github", type = "hexlong", comment = "work@example.com"),
                DgpService(name = "mastodon.social", type = "xkcd", comment = ""),
                DgpService(name = "old-bank", type = "vault", comment = "legacy"),
            ),
            onDone = {},
            onCancel = {},
        )
    }
}
