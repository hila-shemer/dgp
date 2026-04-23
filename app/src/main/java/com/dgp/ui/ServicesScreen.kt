package com.dgp.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgp.DgpService
import com.dgp.ui.components.CopyToast
import com.dgp.ui.components.CopyToastState
import com.dgp.ui.components.EditorialInputField
import com.dgp.ui.components.PrimaryButton
import com.dgp.ui.components.ServiceRow
import com.dgp.ui.components.TagPill
import com.dgp.ui.components.ThemeSwitchPill
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType

sealed class ListFilter {
    object All : ListFilter()
    object Pinned : ListFilter()
    object Archived : ListFilter()
    data class Tag(val tag: String) : ListFilter()
}

@Composable
fun ServicesScreen(
    services: List<DgpService>,
    account: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    activeFilter: ListFilter,
    onFilterChange: (ListFilter) -> Unit,
    onTapRow: (DgpService) -> Unit,
    onChevronTap: (DgpService) -> Unit,
    onLongPressRow: (DgpService) -> Unit,
    onAdd: () -> Unit,
    onLock: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    copyToast: CopyToastState,
    flashedServiceId: String? = null,
    onToastDismiss: () -> Unit,
    onToastUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType

    val nonArchivedCount = services.count { !it.archived }
    val pinnedCount = services.count { it.pinned && !it.archived }
    val archivedCount = services.count { it.archived }
    val allTags = services.flatMap { it.tags }.distinct().sorted()

    val q = searchQuery.trim()
    val filtered = services.filter { svc ->
        val matchesFilter = when (activeFilter) {
            ListFilter.All -> !svc.archived
            ListFilter.Pinned -> !svc.archived && svc.pinned
            ListFilter.Archived -> svc.archived
            is ListFilter.Tag -> !svc.archived && activeFilter.tag in svc.tags
        }
        val matchesSearch = q.isEmpty() ||
            svc.name.contains(q, ignoreCase = true) ||
            svc.comment.contains(q, ignoreCase = true) ||
            svc.tags.any { it.contains(q, ignoreCase = true) }
        matchesFilter && matchesSearch
    }

    Column(modifier = modifier.fillMaxSize().background(editorial.paper)) {

        // 1. Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(editorial.paper)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "/dgp/", style = type.pathCrumb, color = editorial.inkMuted)
            Spacer(Modifier.weight(1f))
            ThemeSwitchPill(
                mode = themeMode,
                onModeChange = onThemeModeChange,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = editorial.inkMuted,
                )
            }
            val avatarLetter = account.firstOrNull { !it.isWhitespace() }
                ?.uppercaseChar()?.toString() ?: "•"
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(editorial.accent, CircleShape)
                    .clickable(onClick = onOpenAccount)
                    .semantics {
                        contentDescription =
                            if (account.isEmpty()) "Set Account" else "Clear Account"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = avatarLetter, style = type.chipLabel, color = editorial.accentInk)
            }
        }

        // 2. Search bar
        Spacer(Modifier.height(8.dp))
        EditorialInputField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Search services...",
            modifier = Modifier.padding(horizontal = 20.dp),
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = editorial.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        // 3. Filter rail
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .horizontalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagPill(
                flag = "--all",
                count = nonArchivedCount,
                active = activeFilter is ListFilter.All,
                onClick = { onFilterChange(ListFilter.All) },
            )
            if (pinnedCount > 0) {
                TagPill(
                    flag = "--pinned",
                    count = pinnedCount,
                    active = activeFilter is ListFilter.Pinned,
                    onClick = { onFilterChange(ListFilter.Pinned) },
                )
            }
            TagPill(
                flag = "--archived",
                count = archivedCount,
                active = activeFilter is ListFilter.Archived,
                onClick = { onFilterChange(ListFilter.Archived) },
            )
            allTags.forEach { tag ->
                TagPill(
                    flag = "--tag $tag",
                    count = services.count { !it.archived && tag in it.tags },
                    active = activeFilter is ListFilter.Tag && (activeFilter as ListFilter.Tag).tag == tag,
                    onClick = { onFilterChange(ListFilter.Tag(tag)) },
                )
            }
        }

        // 4. Copy-toast slot
        Spacer(Modifier.height(8.dp))
        CopyToast(
            state = copyToast,
            onUndo = onToastUndo,
            onDismiss = onToastDismiss,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // 5. Service list
        val listState = rememberLazyListState()
        LaunchedEffect(flashedServiceId, filtered) {
            val id = flashedServiceId ?: return@LaunchedEffect
            val index = filtered.indexOfFirst { it.id == id }
            if (index >= 0) listState.animateScrollToItem(index)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (filtered.isEmpty()) {
                item {
                    val emptyText = when {
                        q.isNotEmpty() -> "no matches for \"$q\""
                        activeFilter is ListFilter.Archived -> "nothing archived"
                        activeFilter is ListFilter.Pinned -> "no pinned entries"
                        else -> "no services yet"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emptyText, style = type.body, color = editorial.inkMuted)
                    }
                }
            } else {
                items(filtered, key = { it.id }) { svc ->
                    ServiceRow(
                        service = svc,
                        onTap = { onTapRow(svc) },
                        onChevronTap = { onChevronTap(svc) },
                        onLongPress = { onLongPressRow(svc) },
                        onSwipeLeft = {},
                        onSwipeRight = {},
                        flashed = (svc.id == flashedServiceId),
                    )
                }
            }
        }

        // 6. Bottom action bar
        HorizontalDivider(color = editorial.rule, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (activeFilter !is ListFilter.Archived) {
                PrimaryButton(
                    text = "new service",
                    onClick = onAdd,
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = editorial.accentInk,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Add Service" },
                )
                Spacer(Modifier.width(8.dp))
            }
            IconButton(
                onClick = onLock,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = "Lock", tint = editorial.ink)
            }
        }

        // 7. Footnote
        Text(
            text = "app passwords live here",
            style = type.caption,
            color = editorial.inkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun ServicesScreenLightPreview() {
    val sampleServices = listOf(
        DgpService(name = "github", type = "alnum", comment = "work account", pinned = true),
        DgpService(
            name = "gmail.com",
            type = "hexlong",
            comment = "personal — check regularly",
            tags = listOf("personal", "google"),
        ),
        DgpService(name = "mastodon.social", type = "xkcd", comment = ""),
        DgpService(
            name = "old-bank-legacy",
            type = "vault",
            comment = "legacy — do not rotate",
            archived = true,
        ),
    )
    EditorialTheme(mode = ThemeMode.Light) {
        ServicesScreen(
            services = sampleServices,
            account = "demo@example.com",
            searchQuery = "",
            onSearchChange = {},
            activeFilter = ListFilter.All,
            onFilterChange = {},
            onTapRow = {},
            onChevronTap = {},
            onLongPressRow = {},
            onAdd = {},
            onLock = {},
            onOpenAccount = {},
            onOpenSettings = {},
            themeMode = ThemeMode.Auto,
            onThemeModeChange = {},
            copyToast = CopyToastState.Idle,
            onToastDismiss = {},
            onToastUndo = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121110, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ServicesScreenDarkPreview() {
    val sampleServices = listOf(
        DgpService(name = "github", type = "alnum", comment = "work account", pinned = true),
        DgpService(
            name = "gmail.com",
            type = "hexlong",
            comment = "personal — check regularly",
            tags = listOf("personal", "google"),
        ),
        DgpService(name = "mastodon.social", type = "xkcd", comment = ""),
        DgpService(
            name = "old-bank-legacy",
            type = "vault",
            comment = "legacy — do not rotate",
            archived = true,
        ),
    )
    EditorialTheme(mode = ThemeMode.Dark) {
        ServicesScreen(
            services = sampleServices,
            account = "demo@example.com",
            searchQuery = "",
            onSearchChange = {},
            activeFilter = ListFilter.All,
            onFilterChange = {},
            onTapRow = {},
            onChevronTap = {},
            onLongPressRow = {},
            onAdd = {},
            onLock = {},
            onOpenAccount = {},
            onOpenSettings = {},
            themeMode = ThemeMode.Auto,
            onThemeModeChange = {},
            copyToast = CopyToastState.Idle,
            onToastDismiss = {},
            onToastUndo = {},
        )
    }
}
