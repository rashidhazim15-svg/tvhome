package com.hamad.tvhome.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.hamad.tvhome.data.AppEntry
import com.hamad.tvhome.data.TmdbImages
import com.hamad.tvhome.ui.LocalTvMetrics
import com.hamad.tvhome.ui.TvColor
import com.hamad.tvhome.ui.TvIcon
import com.hamad.tvhome.ui.TvIconKind
import com.hamad.tvhome.ui.TvSpring
import com.hamad.tvhome.ui.TvText
import com.hamad.tvhome.ui.liquidGlass
import com.hamad.tvhome.ui.tvType
import kotlin.math.roundToInt

@Composable
fun MediaShelf(shelf: Shelf, focusedIndex: Int) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val isPoster = shelf.style == CardStyle.Poster
    val cardW = if (isPoster) 217f else 480f
    val cardH = if (isPoster) 326f else 270f
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) listState.animateScrollToItem(focusedIndex)
    }

    Column(Modifier.fillMaxWidth().padding(top = m.dp(46f))) {
        TvText(
            text = shelf.title,
            modifier = Modifier.padding(horizontal = m.dp(80f)),
            style = type.callout.copy(fontSize = m.sp(31f)),
            color = Color.White.copy(alpha = 0.82f),
        )
        Spacer(Modifier.height(m.dp(18f)))
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(m.dp(cardH + 74f)),
            contentPadding = PaddingValues(horizontal = m.dp(80f)),
            horizontalArrangement = Arrangement.spacedBy(m.dp(40f)),
        ) {
            itemsIndexed(shelf.items, key = { _, item -> "${item.type}-${item.tmdbId}" }) { index, item ->
                MediaCard(
                    item = item,
                    focused = index == focusedIndex,
                    width = cardW,
                    height = cardH,
                    isPoster = isPoster,
                    modifier = Modifier.zIndex(if (index == focusedIndex) 1f else 0f),
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    item: MediaItem,
    focused: Boolean,
    width: Float,
    height: Float,
    isPoster: Boolean,
    modifier: Modifier = Modifier,
) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, TvSpring, label = "cardScale")
    val shape = RoundedCornerShape(m.dp(if (isPoster) 10f else 12f))
    val path = if (isPoster) item.posterPath else (item.backdropPath ?: item.posterPath)
    val url = if (isPoster) TmdbImages.poster(path, 342) else TmdbImages.backdrop(path, 500)

    Column(
        modifier
            .width(m.dp(width))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(m.dp(height))
                .then(if (focused) Modifier.shadow(26.dp, shape, clip = false) else Modifier)
                .clip(shape)
                .background(Color(0xFF1A1A1E)),
        ) {
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2A2A30), Color(0xFF141416))),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TvIcon(TvIconKind.Movies, Modifier.size(m.dp(48f)), tint = Color.White.copy(alpha = 0.25f))
                }
            }
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), shape),
                )
            }
        }
        Spacer(Modifier.height(m.dp(12f)))
        TvText(
            text = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (focused) 1f else 0f },
            style = type.caption1.copy(fontSize = m.sp(25f), fontWeight = FontWeight.Medium),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AppsShelf(title: String, apps: List<AppEntry>, focusedIndex: Int) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) listState.animateScrollToItem(focusedIndex)
    }
    Column(Modifier.fillMaxWidth().padding(top = m.dp(46f))) {
        TvText(
            text = title,
            modifier = Modifier.padding(horizontal = m.dp(80f)),
            style = type.callout.copy(fontSize = m.sp(31f)),
            color = Color.White.copy(alpha = 0.82f),
        )
        Spacer(Modifier.height(m.dp(18f)))
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(m.dp(266f)),
            contentPadding = PaddingValues(horizontal = m.dp(80f)),
            horizontalArrangement = Arrangement.spacedBy(m.dp(40f)),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppTile(
                    app = app,
                    focused = index == focusedIndex,
                    width = 320f,
                    height = 192f,
                    jiggle = false,
                    modifier = Modifier.zIndex(if (index == focusedIndex) 1f else 0f),
                )
            }
        }
    }
}

@Composable
fun AppsLayer(state: LauncherState, vm: LauncherViewModel, screenHeight: Dp) {
    val m = LocalTvMetrics.current
    val apps = state.gridApps
    val active = state.tab == HomeTab.APPS
    val alpha by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "appsAlpha")

    val listState = rememberLazyListState()
    val focus = state.focus as? Focus.AppGrid
    var lastRow by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focus?.index, active) {
        if (!active) return@LaunchedEffect
        val row = (focus?.index ?: 0) / LauncherReducer.COLUMNS
        if (row != lastRow) {
            listState.animateScrollToItem(row)
            lastRow = row
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = m.dp(80f),
                end = m.dp(80f),
                top = m.dp(200f),
                bottom = m.dp(80f),
            ),
            verticalArrangement = Arrangement.spacedBy(m.dp(60f)),
        ) {
            val rows = (apps.size + LauncherReducer.COLUMNS - 1) / LauncherReducer.COLUMNS
            items(rows) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(m.dp(40f)),
                ) {
                    for (col in 0 until LauncherReducer.COLUMNS) {
                        val index = row * LauncherReducer.COLUMNS + col
                        val app = apps.getOrNull(index)
                        if (app != null) {
                            AppTile(
                                app = app,
                                focused = focus?.index == index,
                                width = 320f,
                                height = 192f,
                                jiggle = state.jiggle,
                                modifier = Modifier.zIndex(if (focus?.index == index) 1f else 0f),
                            )
                        } else {
                            Spacer(Modifier.width(m.dp(320f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    focused: Boolean,
    width: Float,
    height: Float,
    jiggle: Boolean,
    modifier: Modifier = Modifier,
) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, TvSpring, label = "appScale")
    val shape = RoundedCornerShape(m.dp(16f))

    val infinite = rememberInfiniteTransition(label = "jiggle")
    val wiggle by infinite.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(140), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "wiggle",
    )

    Column(
        modifier
            .width(m.dp(width))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = if (jiggle && focused) wiggle else 0f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(m.dp(height))
                .then(if (focused) Modifier.shadow(26.dp, shape, clip = false) else Modifier)
                .clip(shape)
                .background(Color(0xFF161618)),
        ) {
            when {
                app.banner != null -> Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                app.special == "settings" -> GeneratedTile(TvIconKind.Gear, TvColor.systemGray, app.label)
                app.special == "search" -> GeneratedTile(TvIconKind.Search, TvColor.systemBlue, app.label)
                app.icon != null -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF26262B), Color(0xFF101012)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(m.dp(96f)),
                    )
                }
                else -> GeneratedTile(TvIconKind.Tv, Color(0xFF3A3A40), app.label)
            }
            if (focused) {
                Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = 0.9f), shape))
            }
        }
        Spacer(Modifier.height(m.dp(14f)))
        TvText(
            text = app.label,
            modifier = Modifier.fillMaxWidth(),
            style = type.caption1.copy(fontSize = m.sp(25f)),
            color = Color.White.copy(alpha = if (focused) 1f else 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GeneratedTile(icon: TvIconKind, color: Color, label: String) {
    val m = LocalTvMetrics.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.55f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        TvIcon(icon, Modifier.size(m.dp(84f)), tint = Color.White, strokeWidth = 1.6f)
    }
}

@Composable
fun ActionSheetOverlay(sheet: ActionSheetState, state: LauncherState, vm: LauncherViewModel) {
    val m = LocalTvMetrics.current
    val type = tvType()
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(m.dp(600f))
                .liquidGlass(RoundedCornerShape(m.dp(34f)), elevated = true)
                .padding(vertical = m.dp(18f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = m.dp(30f), vertical = m.dp(14f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sheet.app.icon != null) {
                    Image(
                        bitmap = sheet.app.icon!!,
                        contentDescription = null,
                        modifier = Modifier.size(m.dp(54f)),
                    )
                } else {
                    TvIcon(TvIconKind.Tv, Modifier.size(m.dp(54f)), tint = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.width(m.dp(20f)))
                Column {
                    TvText(
                        text = sheet.app.label,
                        style = type.headline.copy(fontSize = m.sp(34f)),
                        color = Color.White,
                        maxLines = 1,
                    )
                    TvText(
                        text = sheet.app.packageName,
                        style = type.caption2.copy(fontSize = m.sp(21f)),
                        color = TvColor.labelTertiary,
                        maxLines = 1,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            sheet.options.forEachIndexed { index, option ->
                val focused = index == sheet.focus
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = m.dp(16f), vertical = m.dp(4f))
                        .height(m.dp(66f))
                        .clip(RoundedCornerShape(m.dp(16f)))
                        .then(if (focused) Modifier.background(Color.White) else Modifier),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TvText(
                        text = option.label,
                        modifier = Modifier.padding(horizontal = m.dp(24f)),
                        style = type.body.copy(fontSize = m.sp(30f)),
                        color = when {
                            focused && option.destructive -> Color(0xFFD70015)
                            focused -> Color.Black
                            option.destructive -> Color(0xFFFF453A)
                            else -> Color.White
                        },
                    )
                }
            }
        }
    }
}
