package com.hamad.tvhome.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.hamad.tvhome.data.TmdbImages
import com.hamad.tvhome.launcher.MediaItem
import com.hamad.tvhome.ui.LocalTvMetrics
import com.hamad.tvhome.ui.TvColor
import com.hamad.tvhome.ui.TvIcon
import com.hamad.tvhome.ui.TvIconKind
import com.hamad.tvhome.ui.TvSpring
import com.hamad.tvhome.ui.TvText
import com.hamad.tvhome.ui.tvType

@Composable
fun SearchScreen(state: SearchState) {
    val m = LocalTvMetrics.current
    val type = tvType()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = m.dp(80f), vertical = m.dp(60f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvText("Search", style = type.title2)
            Spacer(Modifier.width(m.dp(40f)))
            Box(Modifier.weight(1f)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvIcon(
                            TvIconKind.Search,
                            Modifier.size(m.dp(30f)),
                            tint = TvColor.labelTertiary,
                            strokeWidth = 1.9f,
                        )
                        Spacer(Modifier.width(m.dp(16f)))
                        TvText(
                            text = state.query.ifEmpty { "Movies, TV shows, people" },
                            style = type.headline,
                            color = if (state.query.isEmpty()) TvColor.labelTertiary else TvColor.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(m.dp(12f)))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(m.dp(1.5f))
                            .background(
                                if (state.pane == SearchPane.Results) {
                                    Color.White.copy(alpha = 0.18f)
                                } else {
                                    Color.White.copy(alpha = 0.55f)
                                },
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.height(m.dp(36f)))

        Row(Modifier.fillMaxSize()) {
            KeyboardPane(
                state = state,
                modifier = Modifier.width(m.dp(560f)).fillMaxHeight(),
            )
            Spacer(Modifier.width(m.dp(64f)))
            ResultsPane(
                state = state,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun KeyboardPane(state: SearchState, modifier: Modifier = Modifier) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val keyW = 85f
    val keyH = 58f
    val gap = 10f

    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        for (row in 0 until SearchKeys.ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(m.dp(gap))) {
                for (col in 0 until SearchKeys.COLUMNS) {
                    val index = row * SearchKeys.COLUMNS + col
                    SearchKey(
                        label = SearchKeys.chars[index],
                        focused = state.pane == SearchPane.Keyboard && state.keyIndex == index,
                        width = keyW,
                        height = keyH,
                    )
                }
            }
            Spacer(Modifier.height(m.dp(gap)))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(m.dp(gap))) {
            SearchKeys.actions.forEachIndexed { index, label ->
                SearchKey(
                    label = label,
                    focused = state.pane == SearchPane.Actions && state.actionIndex == index,
                    width = (560f - gap * 2f) / 3f,
                    height = keyH,
                )
            }
        }

        Spacer(Modifier.height(m.dp(18f)))
        TvText(
            "Type a title, then press Down to browse results.",
            style = type.caption2,
            color = TvColor.labelTertiary,
        )
    }
}

@Composable
private fun SearchKey(label: String, focused: Boolean, width: Float, height: Float) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, TvSpring, label = "keyScale")
    val shape = RoundedCornerShape(m.dp(12f))
    Box(
        Modifier
            .size(width = m.dp(width), height = m.dp(height))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focused) Modifier.shadow(14.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f))
            .then(if (focused) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape)),
        contentAlignment = Alignment.Center,
    ) {
        TvText(
            text = label,
            style = type.callout,
            color = if (focused) Color.Black else TvColor.label,
            maxLines = 1,
        )
    }
}

@Composable
private fun ResultsPane(state: SearchState, modifier: Modifier = Modifier) {
    val m = LocalTvMetrics.current
    val type = tvType()

    Column(modifier) {
        val status = when {
            !state.configured -> "TMDB is not configured"
            state.error != null -> "Search unavailable"
            state.searching -> "Searching\u2026"
            state.results.isNotEmpty() -> "${state.results.size} results"
            state.canSearch -> "No results"
            else -> "Top results"
        }
        TvText(status, style = type.caption1, color = TvColor.labelTertiary)
        Spacer(Modifier.height(m.dp(18f)))

        when {
            !state.configured -> EmptyMessage(
                title = "Add a TMDB read token",
                body = "Put TMDB_READ_TOKEN in local.properties, then rebuild. " +
                    "Search stays hidden instead of showing invented titles.",
            )
            state.error != null -> EmptyMessage(
                title = state.error,
                body = "Check the box\u2019s network connection, then edit the query to retry.",
            )
            state.results.isEmpty() && state.searching -> EmptyMessage(
                title = "Searching\u2026",
                body = "Looking up \u201c${state.query.trim()}\u201d on TMDB.",
            )
            state.results.isEmpty() && !state.canSearch -> EmptyMessage(
                title = "Find something to watch",
                body = "Use the keyboard on the left. Results appear here after two characters.",
            )
            state.results.isEmpty() -> EmptyMessage(
                title = "No matches",
                body = "Nothing on TMDB matches \u201c${state.query.trim()}\u201d.",
            )
            else -> ResultGrid(state)
        }
    }
}

@Composable
private fun EmptyMessage(title: String, body: String) {
    val m = LocalTvMetrics.current
    val type = tvType()
    Column(Modifier.padding(top = m.dp(80f)).width(m.dp(720f))) {
        TvText(title, style = type.headline, color = TvColor.labelSecondary, maxLines = 2)
        Spacer(Modifier.height(m.dp(16f)))
        TvText(body, style = type.body, color = TvColor.labelTertiary, maxLines = 3)
    }
}

@Composable
private fun ResultGrid(state: SearchState) {
    val m = LocalTvMetrics.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(SearchReducer.RESULT_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(m.dp(24f)),
        verticalArrangement = Arrangement.spacedBy(m.dp(24f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = m.dp(24f)),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(state.results, key = { _, item -> "${item.type}-${item.tmdbId}" }) { index, item ->
            ResultCard(
                item = item,
                focused = state.pane == SearchPane.Results && state.resultIndex == index,
            )
        }
    }
}

@Composable
private fun ResultCard(item: MediaItem, focused: Boolean) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, TvSpring, label = "resultScale")
    val shape = RoundedCornerShape(m.dp(10f))
    val poster = TmdbImages.poster(item.posterPath, 342)

    Column(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (focused) 1f else 0f),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(if (focused) Modifier.shadow(28.dp, shape, clip = false) else Modifier)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF232326), Color(0xFF0E0E10)),
                    ),
                )
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.10f),
                    shape = shape,
                ),
        ) {
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TvText(
                    text = item.title,
                    style = type.body,
                    color = TvColor.labelTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(m.dp(20f)),
                )
            }
        }
        Spacer(Modifier.height(m.dp(8f)))
        TvText(item.title, style = type.caption2, color = TvColor.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.year != null) {
            TvText(item.year, style = type.caption2, color = TvColor.labelTertiary, maxLines = 1)
        }
    }
}
