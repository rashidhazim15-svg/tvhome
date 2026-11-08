package com.hamad.tvhome.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hamad.tvhome.data.TmdbImages
import com.hamad.tvhome.ui.LocalTvMetrics
import com.hamad.tvhome.ui.TvColor
import com.hamad.tvhome.ui.TvIcon
import com.hamad.tvhome.ui.TvIconKind
import com.hamad.tvhome.ui.TvDpSpring
import com.hamad.tvhome.ui.TvSpring
import com.hamad.tvhome.ui.TvText
import com.hamad.tvhome.ui.liquidGlass
import com.hamad.tvhome.ui.tvType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val state = vm.state

    LaunchedEffect(state.heroPage, state.focus, state.tab, state.heroAutoPlay) {
        if (!state.heroAutoPlay) return@LaunchedEffect
        if (state.focus !is Focus.Hero && state.focus !is Focus.TabBar) return@LaunchedEffect
        if (state.currentContent.hero.size <= 1) return@LaunchedEffect
        delay(8000)
        vm.nextHeroPage()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth

        // Tabs stay composed once shown so switching is only an alpha cross-fade.
        TabLayer(HomeTab.HOME, state, vm, screenHeight, screenWidth)
        TabLayer(HomeTab.MOVIES, state, vm, screenHeight, screenWidth)
        TabLayer(HomeTab.TV_SHOWS, state, vm, screenHeight, screenWidth)
        AppsLayer(state, vm, screenHeight)

        TopChrome(state)

        state.sheet?.let { sheet ->
            ActionSheetOverlay(sheet, state, vm)
        }
    }
}

@Composable
private fun TabLayer(
    tab: HomeTab,
    state: LauncherState,
    vm: LauncherViewModel,
    screenHeight: Dp,
    screenWidth: Dp,
) {
    val m = LocalTvMetrics.current
    val content = state.content[tab] ?: return
    val active = state.tab == tab
    val alpha by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "tabAlpha")

    val listState = rememberLazyListState()
    val heroOffset = if (content.hero.isNotEmpty()) 1 else 0
    var lastScrolled by remember { mutableIntStateOf(-1) }

    val focus = state.focus
    LaunchedEffect(focus, content.hero.size) {
        if (!active) return@LaunchedEffect
        when (focus) {
            is Focus.Hero, is Focus.TabBar -> {
                if (lastScrolled != 0) {
                    listState.animateScrollToItem(0)
                    lastScrolled = 0
                }
            }
            is Focus.ShelfItem -> {
                val target = focus.shelf + heroOffset
                if (lastScrolled != target) {
                    listState.animateScrollToItem(target)
                    lastScrolled = target
                }
            }
            else -> Unit
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        if (content.shelves.isEmpty() && content.hero.isEmpty()) return@Box
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = if (content.hero.isEmpty()) m.dp(176f) else 0.dp,
            ),
        ) {
            if (content.hero.isNotEmpty()) {
                item(key = "hero") {
                    HeroPager(content.hero, state, screenHeight)
                }
            }
            content.shelves.forEachIndexed { shelfIndex, shelf ->
                item(key = shelf.id) {
                    if (shelf.isApps) {
                        AppsShelf(
                            title = shelf.title,
                            apps = state.apps.filter { it.special == null },
                            focusedIndex = (focus as? Focus.ShelfItem)
                                ?.takeIf { it.shelf == shelfIndex }?.item ?: -1,
                        )
                    } else {
                        MediaShelf(
                            shelf = shelf,
                            focusedIndex = (focus as? Focus.ShelfItem)
                                ?.takeIf { it.shelf == shelfIndex }?.item ?: -1,
                        )
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(m.dp(80f))) }
        }
    }
}

@Composable
private fun TopChrome(state: LauncherState) {
    val m = LocalTvMetrics.current
    val focus = state.focus
    val hidden = focus is Focus.ShelfItem || (focus is Focus.AppGrid && focus.index >= LauncherReducer.COLUMNS)
    val barAlpha by animateFloatAsState(if (hidden) 0f else 1f, tween(250), label = "barAlpha")
    val barShift by animateDpAsState(if (hidden) m.dp(-40f) else 0.dp, tween(250), label = "barShift")

    Box(Modifier.fillMaxWidth().height(m.dp(150f))) {
        Clock(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = m.dp(46f), end = m.dp(80f)),
        )
        TabBar(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = m.dp(46f))
                .offset(y = barShift)
                .graphicsLayer { alpha = barAlpha },
        )
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val type = tvType()
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val showSeconds = com.hamad.tvhome.data.TvPrefs(context).showSeconds
    var text by remember { mutableStateOf("") }
    LaunchedEffect(is24, showSeconds) {
        val pattern = when {
            is24 && showSeconds -> "HH:mm:ss"
            is24 -> "HH:mm"
            showSeconds -> "h:mm:ss"
            else -> "h:mm"
        }
        val fmt = SimpleDateFormat(pattern, Locale.ENGLISH)
        while (true) {
            text = fmt.format(Date())
            delay(if (showSeconds) 1000 else 5000)
        }
    }
    TvText(
        text = text,
        modifier = modifier,
        style = type.body,
        color = Color.White.copy(alpha = 0.82f),
    )
}

@Composable
private fun TabBar(
    state: LauncherState,
    modifier: Modifier = Modifier,
) {
    val m = LocalTvMetrics.current
    val type = tvType()
    // Widths in tvOS points: search, Home, Movies, TV Shows, Apps, settings.
    val widths = remember { listOf(76f, 150f, 168f, 220f, 144f, 76f) }
    val gap = 8f
    val padH = 18f
    val total = widths.sum() + gap * (widths.size - 1) + padH * 2
    val height = 68f
    val radius = 34f

    val focusedIndex = (state.focus as? Focus.TabBar)?.index ?: -1
    val selectedIndex = LauncherReducer.tabBarIndex[state.tab] ?: 1

    fun startOf(index: Int): Float {
        var x = padH
        for (i in 0 until index) x += widths[i] + gap
        return x
    }

    val safeFocus = focusedIndex.coerceAtLeast(0)
    val focusX by animateDpAsState(m.dp(startOf(safeFocus)), TvDpSpring, label = "focusX")
    val focusW by animateDpAsState(m.dp(widths[safeFocus]), TvDpSpring, label = "focusW")
    val selectedX by animateDpAsState(m.dp(startOf(selectedIndex)), TvDpSpring, label = "selectedX")
    val selectedW by animateDpAsState(m.dp(widths[selectedIndex]), TvDpSpring, label = "selectedW")

    val shape = RoundedCornerShape(m.dp(radius))
    Box(
        modifier
            .width(m.dp(total))
            .height(m.dp(height))
            .liquidGlass(shape, elevated = true)
            .padding(horizontal = m.dp(padH)),
    ) {
        Box(
            Modifier
                .offset(x = selectedX - m.dp(padH))
                .size(width = selectedW, height = m.dp(height - 14f))
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(m.dp(radius)))
                .background(Color.White.copy(alpha = 0.13f)),
        )
        if (focusedIndex >= 0) {
            Box(
                Modifier
                    .offset(x = focusX - m.dp(padH))
                    .size(width = focusW, height = m.dp(height - 14f))
                    .align(Alignment.CenterStart)
                    .shadow(18.dp, RoundedCornerShape(m.dp(radius)))
                    .clip(RoundedCornerShape(m.dp(radius)))
                    .background(Color.White),
            )
        }
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.dp(gap)),
        ) {
            widths.forEachIndexed { index, w ->
                val focused = index == focusedIndex
                val selected = index == selectedIndex
                val color = when {
                    focused -> Color.Black
                    selected -> Color.White
                    else -> Color.White.copy(alpha = 0.58f)
                }
                Box(
                    Modifier
                        .width(m.dp(w))
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (index) {
                        0 -> TvIcon(TvIconKind.Search, Modifier.size(m.dp(30f)), tint = color, strokeWidth = 1.9f)
                        5 -> TvIcon(TvIconKind.Gear, Modifier.size(m.dp(30f)), tint = color, strokeWidth = 1.9f)
                        else -> TvText(
                            text = HomeTab.entries[index - 1].label,
                            style = type.body.copy(
                                fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = m.sp(29f),
                            ),
                            color = color,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPager(
    slides: List<HeroSlide>,
    state: LauncherState,
    screenHeight: Dp,
) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val slide = slides[state.heroPage.coerceIn(0, slides.lastIndex)]
    val focusedAction = (state.focus as? Focus.Hero)?.action

    Box(Modifier.fillMaxWidth().height(screenHeight)) {
        Crossfade(targetState = slide, animationSpec = tween(800), label = "hero") { current ->
            Box(Modifier.fillMaxSize()) {
                val backdrop = TmdbImages.backdrop(current.item.backdropPath, 1280)
                if (backdrop != null) {
                    AsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF1C1C22), Color(0xFF08080A)))),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.82f),
                                0.45f to Color.Black.copy(alpha = 0.35f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.9f),
                            ),
                        ),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = m.dp(80f), end = m.dp(80f), bottom = m.dp(104f))
                .widthIn(max = m.dp(820f)),
        ) {
            TvText(
                text = slide.attribution.uppercase(Locale.ENGLISH),
                style = type.caption1.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (0.6f * m.dpPerPt).sp,
                ),
                color = TvColor.labelSecondary,
            )
            Spacer(Modifier.height(m.dp(14f)))
            val logo = TmdbImages.logo(slide.item.logoPath, 500)
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = slide.item.title,
                    modifier = Modifier.height(m.dp(78f)).widthIn(max = m.dp(760f)),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                )
            } else {
                TvText(
                    text = slide.item.title,
                    style = type.title1,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(m.dp(12f)))
            val meta = buildMeta(slide.item)
            TvText(
                text = meta,
                style = type.body.copy(fontSize = m.sp(29f)),
                color = TvColor.labelSecondary,
            )
            slide.item.overview?.let { overview ->
                Spacer(Modifier.height(m.dp(10f)))
                TvText(
                    text = overview,
                    style = type.callout.copy(fontWeight = FontWeight.Normal),
                    color = TvColor.labelTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(m.dp(30f)))
            Row(horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
                HeroButton(label = "Play", icon = TvIconKind.Play, focused = focusedAction == 0)
                HeroButton(label = "More Info", icon = TvIconKind.Info, focused = focusedAction == 1)
            }
        }

        if (slides.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = m.dp(40f)),
                horizontalArrangement = Arrangement.spacedBy(m.dp(12f)),
            ) {
                slides.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(m.dp(9f))
                            .clip(CircleShape)
                            .background(
                                if (index == state.heroPage) Color.White
                                else Color.White.copy(alpha = 0.32f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, icon: TvIconKind, focused: Boolean) {
    val m = LocalTvMetrics.current
    val type = tvType()
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, TvSpring, label = "btnScale")
    val color = if (focused) Color.Black else Color.White
    val shape = RoundedCornerShape(m.dp(32f))
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(m.dp(64f))
            .clip(shape)
            .then(if (focused) Modifier.background(Color.White) else Modifier.liquidGlass(shape))
            .padding(horizontal = m.dp(30f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.dp(12f)),
        ) {
            TvIcon(icon, Modifier.size(m.dp(26f)), tint = color, strokeWidth = 2f)
            TvText(
                text = label,
                style = type.body.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
    }
}

fun buildMeta(item: MediaItem): String {
    val parts = ArrayList<String>(4)
    parts += if (item.type == MediaType.MOVIE) "Movie" else "TV Show"
    item.year?.let { parts += it }
    item.genres.firstOrNull()?.let { parts += it }
    item.rating?.let { parts += "★ ${"%.1f".format(Locale.US, it)}" }
    return parts.joinToString(" · ")
}
