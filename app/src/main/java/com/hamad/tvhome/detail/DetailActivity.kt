package com.hamad.tvhome.detail

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.hamad.tvhome.BuildConfig
import com.hamad.tvhome.data.TmdbClient
import com.hamad.tvhome.data.TmdbImages
import com.hamad.tvhome.data.TmdbRepository
import com.hamad.tvhome.data.TvPrefs
import com.hamad.tvhome.launcher.MediaItem
import com.hamad.tvhome.launcher.MediaType
import com.hamad.tvhome.ui.LocalTvMetrics
import com.hamad.tvhome.ui.LocalTvType
import com.hamad.tvhome.ui.TvColor
import com.hamad.tvhome.ui.TvMetrics
import com.hamad.tvhome.ui.TvText
import com.hamad.tvhome.ui.TvType
import com.hamad.tvhome.ui.tvType

class DetailActivity : ComponentActivity() {

    private val repository by lazy {
        TmdbRepository(TmdbClient(BuildConfig.TMDB_READ_TOKEN), TvPrefs(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val uri: Uri? = intent.data
        val id = intent.getIntExtra("tmdb_id", 0).takeIf { it > 0 }
            ?: uri?.getQueryParameter("id")?.toIntOrNull()
            ?: 0
        val typeKey = intent.getStringExtra("tmdb_type")
            ?: uri?.getQueryParameter("type")
            ?: "movie"
        val type = if (typeKey == "tv") MediaType.TV else MediaType.MOVIE
        val seedTitle = intent.getStringExtra("title")
            ?: uri?.getQueryParameter("title").orEmpty()

        setContent {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                val metrics = remember(maxHeight) {
                    TvMetrics((maxHeight.value / 1080f).coerceAtLeast(0.35f))
                }
                val tvType = remember(metrics) { TvType(metrics) }
                CompositionLocalProvider(LocalTvMetrics provides metrics, LocalTvType provides tvType) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        DetailScreen(
                            id = id,
                            type = type,
                            seedTitle = seedTitle,
                            load = { repository.details(MediaItem(id, type, seedTitle, null, null, null, null, emptyList(), null, null)) },
                        )
                    }
                }
            }
        }
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemUi() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun DetailScreen(
    id: Int,
    type: MediaType,
    seedTitle: String,
    load: suspend () -> MediaItem,
) {
    val m = LocalTvMetrics.current
    val tv = tvType()
    var item by remember(id) { mutableStateOf<MediaItem?>(null) }
    var failed by remember(id) { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id <= 0) {
            failed = true
            return@LaunchedEffect
        }
        runCatching { load() }
            .onSuccess { item = it }
            .onFailure { failed = true }
    }

    val title = item?.title?.takeIf { it.isNotBlank() } ?: seedTitle
    val backdrop = TmdbImages.backdrop(item?.backdropPath, 1280)
    val logo = TmdbImages.logo(item?.logoPath, 500)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        0.65f to Color.Transparent,
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = m.dp(80f), end = m.dp(80f), bottom = m.dp(80f))
                .widthIn(max = m.dp(980f)),
        ) {
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.height(m.dp(96f)).width(m.dp(720f)),
                )
                Spacer(Modifier.height(m.dp(14f)))
            } else if (title.isNotBlank()) {
                TvText(title, style = tv.title1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(m.dp(12f)))
            }

            val meta = buildList {
                item?.year?.let(::add)
                item?.genres?.take(3)?.let { add(it.joinToString(" \u00b7 ")) }
                item?.rating?.let { add(String.format(java.util.Locale.US, "\u2605 %.1f", it)) }
            }
            if (meta.isNotEmpty()) {
                TvText(
                    text = meta.joinToString("   \u00b7   "),
                    style = tv.callout,
                    color = TvColor.labelSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(m.dp(18f)))
            }

            when {
                failed -> TvText(
                    "Details aren\u2019t available right now.",
                    style = tv.body,
                    color = TvColor.labelTertiary,
                )
                item == null -> TvText(
                    "Loading details\u2026",
                    style = tv.body,
                    color = TvColor.labelTertiary,
                )
                else -> item?.overview?.let { overview ->
                    TvText(
                        overview,
                        style = tv.body,
                        color = TvColor.labelSecondary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(m.dp(28f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(m.dp(3f))
                        .height(m.dp(20f))
                        .background(Color.White.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(m.dp(10f)))
                TvText(
                    "Press Back to return",
                    style = tv.caption2,
                    color = TvColor.labelTertiary,
                )
            }
            Spacer(Modifier.height(m.dp(6f)))
            TvText(
                "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = tv.caption2,
                color = TvColor.labelTertiary.copy(alpha = 0.7f),
                maxLines = 2,
            )
        }
    }
}
