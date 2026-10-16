package pixiviz.mobile.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pixiviz.mobile.data.remote.AutocompleteTag
import pixiviz.mobile.data.remote.UserPreview
import pixiviz.mobile.data.settings.GridLayout
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.common.CapsuleButton
import pixiviz.mobile.ui.common.EmptyState
import pixiviz.mobile.ui.common.ErrorState
import pixiviz.mobile.ui.common.PagedIllustGrid
import pixiviz.mobile.ui.common.PixivImage
import pixiviz.mobile.ui.common.rememberIllustPaging
import pixiviz.mobile.ui.nav.BottomBarReserve

private enum class SearchTab(val label: String) { WORKS("作品"), USERS("画师") }

private enum class SortOption(val key: String, val label: String) {
    NEW("date_desc", "最新"),
    POPULAR("popular_desc", "热门"),
}

@Composable
fun SearchScreen(
    onOpenIllust: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    initialWord: String = "",
) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val columns = if (settings.gridLayout == GridLayout.DOUBLE) 2 else 1

    var query by remember { mutableStateOf(initialWord) }
    var submitted by remember { mutableStateOf(initialWord) }
    var tab by remember { mutableStateOf(SearchTab.WORKS) }
    var sort by remember { mutableStateOf(SortOption.NEW) }
    var suggestions by remember { mutableStateOf<List<AutocompleteTag>>(emptyList()) }

    var users by remember { mutableStateOf<List<UserPreview>>(emptyList()) }
    var userError by remember { mutableStateOf<String?>(null) }
    var usersLoading by remember { mutableStateOf(false) }

    LaunchedEffect(initialWord) {
        if (initialWord.isNotBlank()) {
            query = initialWord
            submitted = initialWord
        }
    }

    LaunchedEffect(query) {
        suggestions = if (query.trim().length < 1) {
            emptyList()
        } else {
            runCatching { app.repository.autocomplete(query.trim()) }.getOrDefault(emptyList())
        }
    }

    val paging = rememberIllustPaging(key = "search-$submitted-$sort") {
        app.repository.searchLoader(word = submitted, sort = sort.key)
    }

    LaunchedEffect(submitted, tab) {
        if (tab != SearchTab.USERS || submitted.isBlank()) return@LaunchedEffect
        usersLoading = true
        userError = null
        runCatching { app.repository.searchUser(submitted) }
            .onSuccess { users = it }
            .onFailure { userError = it.message }
        usersLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
    ) {
        Text(
            text = "搜索",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("作品名、标签或画师") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { submitted = query.trim() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (suggestions.isNotEmpty() && query.trim() != submitted) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(items = suggestions.take(10), key = { it.name }) { suggestion ->
                    CapsuleButton(
                        text = suggestion.translatedName?.takeIf { it.isNotBlank() } ?: suggestion.name,
                        selected = false,
                        onClick = {
                            query = suggestion.name
                            submitted = suggestion.name
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchTab.entries.forEach { entry ->
                CapsuleButton(
                    text = entry.label,
                    selected = entry == tab,
                    onClick = { tab = entry },
                )
            }
            if (tab == SearchTab.WORKS) {
                SortOption.entries.forEach { option ->
                    CapsuleButton(
                        text = option.label,
                        selected = option == sort,
                        onClick = { sort = option },
                    )
                }
            }
        }

        when (tab) {
            SearchTab.WORKS -> PagedIllustGrid(
                items = paging,
                columns = columns,
                onOpenIllust = onOpenIllust,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = BottomBarReserve,
                ),
                emptyContent = {
                    EmptyState(
                        title = if (submitted.isBlank()) "输入关键词开始搜索" else "没有找到相关作品",
                        subtitle = "可以试试完整标签，或切换「热门」排序",
                    )
                },
            )

            SearchTab.USERS -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = BottomBarReserve,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    userError != null -> item { ErrorState(userError!!) }
                    usersLoading -> item { Text("搜索中…", style = MaterialTheme.typography.bodyMedium) }
                    users.isEmpty() -> item {
                        EmptyState(
                            title = if (submitted.isBlank()) "输入关键词搜索画师" else "没有找到相关画师",
                        )
                    }
                    else -> items(items = users, key = { it.user.id }) { preview ->
                        UserRow(preview = preview, onClick = { onOpenArtist(preview.user.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(preview: UserPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape),
        ) {
            PixivImage(
                rawUrl = preview.user.profileImageUrls.medium,
                contentDescription = preview.user.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(preview.user.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = "@${preview.user.account}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = "${preview.illusts.size} 作品",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
