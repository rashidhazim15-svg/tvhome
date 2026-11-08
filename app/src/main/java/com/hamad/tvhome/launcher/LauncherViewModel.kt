package com.hamad.tvhome.launcher

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamad.tvhome.data.AppEntry
import com.hamad.tvhome.data.AppRepository
import com.hamad.tvhome.data.TmdbClient
import com.hamad.tvhome.data.TmdbRepository
import com.hamad.tvhome.data.TvPrefs
import com.hamad.tvhome.detail.DetailActivity
import com.hamad.tvhome.search.SearchActivity
import com.hamad.tvhome.settings.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = TvPrefs(app)
    private val appRepository = AppRepository(app)
    private val tmdb = TmdbRepository(TmdbClient(com.hamad.tvhome.BuildConfig.TMDB_READ_TOKEN), prefs)

    var state by mutableStateOf(LauncherState())
        private set

    private val loading = HashSet<HomeTab>()
    private var longPressJob: Job? = null
    private var pressedKey: Int = 0
    private var prefetchStarted = false
    private var playedOnce = HashSet<String>()

    init {
        viewModelScope.launch {
            state = state.copy(apps = appRepository.loadLaunchableApps(prefs))
            delay(1500)
            prefetchTabs()
        }
    }

    fun onResume() {
        viewModelScope.launch {
            state = state.copy(apps = appRepository.loadLaunchableApps(prefs))
        }
        if (!prefetchStarted) {
            prefetchStarted = true
            viewModelScope.launch {
                delay(1500)
                prefetchTabs()
            }
        }
    }

    private fun prefetchTabs() {
        loadContent(HomeTab.HOME)
        viewModelScope.launch {
            delay(250)
            loadContent(HomeTab.MOVIES)
            delay(250)
            loadContent(HomeTab.TV_SHOWS)
        }
    }

    fun loadContent(tab: HomeTab, force: Boolean = false) {
        if (tab == HomeTab.APPS) return
        if (!force && (tab in loading || state.content.containsKey(tab) && state.content[tab]?.isEmpty == false)) return
        loading.add(tab)
        viewModelScope.launch {
            val installed = Provider.entries.filter { appRepository.isInstalled(it.packageName) }
                .map { it.packageName }.toSet()
            val content = runCatching {
                when (tab) {
                    HomeTab.HOME -> tmdb.home(installed)
                    HomeTab.MOVIES -> tmdb.movies()
                    HomeTab.TV_SHOWS -> tmdb.tv()
                    HomeTab.APPS -> TabContent()
                }
            }.getOrDefault(TabContent())
            loading.remove(tab)
            val withApps = if (tab == HomeTab.HOME) {
                content.copy(
                    shelves = listOf(
                        Shelf("your_apps", "Your Apps", CardStyle.Wide, emptyList(), isApps = true),
                    ) + content.shelves,
                )
            } else {
                content
            }
            val map = state.content.toMutableMap()
            map[tab] = withApps
            state = state.copy(content = map, heroPage = state.heroPage.coerceIn(0, (withApps.hero.size - 1).coerceAtLeast(0)))
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val supported = code in setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HOME,
        )
        if (!supported) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) return true
            onKeyDown(code)
        } else if (event.action == KeyEvent.ACTION_UP) {
            onKeyUp(code)
        }
        return true
    }

    private fun onKeyDown(code: Int) {
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> move(Dir.Up)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(Dir.Down)
            KeyEvent.KEYCODE_DPAD_LEFT -> move(Dir.Left)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(Dir.Right)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                pressedKey = code
                startLongPress()
            }
            KeyEvent.KEYCODE_BACK -> back()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> menu()
            KeyEvent.KEYCODE_HOME -> homeTab()
        }
    }

    private fun onKeyUp(code: Int) {
        if (code == pressedKey) {
            val shortPress = longPressJob?.isActive == true
            longPressJob?.cancel()
            longPressJob = null
            if (shortPress) select()
            pressedKey = 0
        }
    }

    private fun startLongPress() {
        longPressJob?.cancel()
        longPressJob = viewModelScope.launch {
            delay(650)
            longPress()
            longPressJob = null
        }
    }

    private fun move(dir: Dir) {
        if (state.sheet != null) {
            state = LauncherReducer.move(state, dir)
            return
        }
        val newState = LauncherReducer.move(state, dir)
        state = newState
        if (newState.jiggle) persistOrder(newState.apps)
        if (newState.focus is Focus.ShelfItem && newState.tab != HomeTab.APPS) {
            // Row focus can request a scroll in the UI; state is enough.
        }
    }

    private fun select() {
        val sheet = state.sheet
        if (sheet != null) {
            runAction(sheet.app, sheet.options[sheet.focus].id)
            return
        }
        when (val f = state.focus) {
            is Focus.TabBar -> when (f.index) {
                LauncherReducer.TAB_SEARCH -> openSearch()
                LauncherReducer.TAB_SETTINGS -> openSettings()
                else -> {
                    val tab = HomeTab.entries.firstOrNull { LauncherReducer.tabBarIndex[it] == f.index } ?: return
                    val newState = state.copy(tab = tab, heroPage = 0)
                    state = newState
                    loadContent(tab)
                }
            }
            is Focus.Hero -> {
                val hero = state.currentContent.hero.getOrNull(state.heroPage) ?: return
                if (f.action == 0) play(hero.item) else openDetails(hero.item)
            }
            is Focus.ShelfItem -> {
                val shelf = state.currentContent.shelves.getOrNull(f.shelf) ?: return
                val item = shelf.items.getOrNull(f.item) ?: return
                openDetails(item)
            }
            is Focus.AppGrid -> {
                if (state.jiggle) {
                    state = state.copy(jiggle = false)
                    persistOrder(state.apps)
                } else {
                    val app = state.gridApps.getOrNull(f.index) ?: return
                    launchApp(app)
                }
            }
            is Focus.Sheet -> Unit
        }
    }

    private fun back() {
        if (state.sheet != null) {
            state = state.copy(sheet = null)
            return
        }
        if (state.jiggle) {
            state = state.copy(jiggle = false)
            persistOrder(state.apps)
            return
        }
        when (state.focus) {
            is Focus.TabBar -> {
                if (state.tab != HomeTab.HOME) {
                    state = state.copy(tab = HomeTab.HOME, focus = Focus.TabBar(1))
                }
            }
            else -> state = state.copy(focus = Focus.TabBar(LauncherReducer.tabBarIndex.getValue(state.tab)))
        }
    }

    private fun menu() {
        if (state.jiggle) {
            openSheet()
        }
    }

    private fun longPress() {
        if (state.sheet != null) return
        if (state.tab == HomeTab.APPS && state.focus is Focus.AppGrid) {
            if (state.jiggle) openSheet() else {
                state = state.copy(jiggle = true)
                persistOrder(state.apps)
            }
        }
    }

    private fun openSheet() {
        val f = state.focus as? Focus.AppGrid ?: return
        val app = state.gridApps.getOrNull(f.index) ?: return
        if (app.special != null) return
        val options = buildList {
            add(ActionOption(ActionId.Move, "Move"))
            add(ActionOption(ActionId.HideApp, "Hide App"))
            add(ActionOption(ActionId.AppInfo, "App Info"))
            if (app.removable) add(ActionOption(ActionId.Uninstall, "Uninstall", destructive = true))
            add(ActionOption(ActionId.Cancel, "Cancel"))
        }
        state = state.copy(sheet = ActionSheetState(app, options, 0))
    }

    private fun runAction(app: AppEntry, id: ActionId) {
        val ctx = getApplication<Application>()
        when (id) {
            ActionId.Cancel -> state = state.copy(sheet = null)
            ActionId.Move -> state = state.copy(sheet = null, jiggle = true)
            ActionId.HideApp -> {
                prefs.hiddenApps = prefs.hiddenApps + app.packageName
                state = state.copy(
                    sheet = null,
                    jiggle = false,
                    apps = state.apps.filterNot { it.packageName == app.packageName },
                    focus = Focus.AppGrid(0),
                )
            }
            ActionId.AppInfo -> {
                state = state.copy(sheet = null)
                runCatching {
                    ctx.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${app.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            ActionId.Uninstall -> {
                state = state.copy(sheet = null)
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    private fun persistOrder(apps: List<AppEntry>) {
        prefs.appOrder = apps.filter { it.special == null }.map { it.packageName }
    }

    fun homeTab() {
        state = state.copy(tab = HomeTab.HOME, focus = Focus.TabBar(1), jiggle = false, sheet = null, heroPage = 0)
    }

    fun openSearch(query: String? = null) {
        val intent = Intent(getApplication(), SearchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        query?.let { intent.putExtra("query", it) }
        getApplication<Application>().startActivity(intent)
    }

    fun openSettings() {
        getApplication<Application>().startActivity(
            Intent(getApplication(), SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun openDetails(item: MediaItem) {
        val intent = Intent(getApplication(), DetailActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("tmdb_id", item.tmdbId)
            .putExtra("tmdb_type", if (item.type == MediaType.MOVIE) "movie" else "tv")
            .putExtra("title", item.title)
        getApplication<Application>().startActivity(intent)
    }

    private fun play(item: MediaItem) {
        val ctx = getApplication<Application>()
        val provider = item.provider
        if (provider != null) {
            val intent = appRepository.launchIntent(provider.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(intent) }
                return
            }
        }
        playTrailer(item)
    }

    fun playTrailer(item: MediaItem) {
        val ctx = getApplication<Application>()
        val query = Uri.encode("${item.title} ${item.year ?: ""} trailer")
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=$query"))
                .setPackage("com.google.android.youtube.tv"),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
                .setPackage("com.google.android.youtube.tv"),
        )
        candidates.forEach { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = intent.resolveActivity(ctx.packageManager)
            if (resolved != null) {
                runCatching { ctx.startActivity(intent) }
                return
            }
        }
    }

    private fun launchApp(app: AppEntry) {
        if (app.special == "search") {
            openSearch()
            return
        }
        if (app.special == "settings") {
            openSettings()
            return
        }
        val ctx = getApplication<Application>()
        val intent = appRepository.launchIntent(app.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun openAppInfo(packageName: String) {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun uninstall(packageName: String) {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun hideApp(packageName: String) {
        prefs.hiddenApps = prefs.hiddenApps + packageName
        state = state.copy(apps = state.apps.filterNot { it.packageName == packageName })
    }

    fun nextHeroPage() {
        val count = state.currentContent.hero.size
        if (count <= 1) return
        if (state.focus !is Focus.Hero && state.focus !is Focus.TabBar) return
        state = state.copy(heroPage = (state.heroPage + 1) % count)
        val key = "${state.tab}-${state.heroPage}"
        if (!playedOnce.add(key)) {
            // one-shot: never loop twice
        }
    }

    fun setClock(time: String, date: String) {
        if (state.clock != time || state.date != date) state = state.copy(clock = time, date = date)
    }

    fun setAutoPlayHero(enabled: Boolean) {
        state = state.copy(heroAutoPlay = enabled)
        prefs.autoPlayTopShelf = enabled
    }

    fun currentHero(): HeroSlide? = state.currentContent.hero.getOrNull(state.heroPage)
}
