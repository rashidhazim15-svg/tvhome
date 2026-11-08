package com.hamad.tvhome.launcher

import androidx.compose.runtime.Immutable
import com.hamad.tvhome.data.AppEntry

enum class HomeTab(val label: String) {
    HOME("Home"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
    APPS("Apps"),
}

enum class MediaType { MOVIE, TV }

@Immutable
data class MediaItem(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val logoPath: String?,
    val backdropPath: String?,
    val posterPath: String?,
    val year: String?,
    val genres: List<String>,
    val overview: String?,
    val rating: Double?,
    val provider: Provider? = null,
    val trending: Boolean = false,
)

@Immutable
enum class Provider(val displayName: String, val tmdbProviderId: Int, val packageName: String) {
    NETFLIX("Netflix", 8, "com.netflix.mediaclient"),
    PRIME("Prime Video", 9, "com.amazon.amazonvideo.livingroom"),
    DISNEY("Disney+", 337, "com.disney.disneyplus"),
    APPLE_TV("Apple TV+", 350, "com.apple.atve.androidtv.appletv"),
}

enum class CardStyle { Poster, Wide }

@Immutable
data class Shelf(
    val id: String,
    val title: String,
    val style: CardStyle,
    val items: List<MediaItem>,
    val isApps: Boolean = false,
) {
    fun count(state: LauncherState): Int = if (isApps) state.apps.size else items.size
}

@Immutable
data class HeroSlide(
    val item: MediaItem,
    val attribution: String,
)

@Immutable
data class TabContent(
    val hero: List<HeroSlide> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
) {
    val isEmpty: Boolean get() = hero.isEmpty() && shelves.isEmpty()
}

sealed interface Focus {
    data class TabBar(val index: Int) : Focus
    data class Hero(val action: Int) : Focus
    data class ShelfItem(val shelf: Int, val item: Int) : Focus
    data class AppGrid(val index: Int) : Focus
    data class Sheet(val index: Int) : Focus
}

@Immutable
data class ActionOption(val id: ActionId, val label: String, val destructive: Boolean = false)

enum class ActionId { Move, HideApp, AppInfo, Uninstall, Cancel }

@Immutable
data class ActionSheetState(
    val app: AppEntry,
    val options: List<ActionOption>,
    val focus: Int = 0,
)

@Immutable
data class LauncherState(
    val tab: HomeTab = HomeTab.HOME,
    val focus: Focus = Focus.TabBar(1),
    val apps: List<AppEntry> = emptyList(),
    val content: Map<HomeTab, TabContent> = emptyMap(),
    val jiggle: Boolean = false,
    val sheet: ActionSheetState? = null,
    val heroPage: Int = 0,
    val heroAutoPlay: Boolean = true,
    val clock: String = "",
    val date: String = "",
) {
    val currentContent: TabContent get() = content[tab] ?: TabContent()
    val currentShelf: Shelf? get() = currentContent.shelves.getOrNull((focus as? Focus.ShelfItem)?.shelf ?: -1)
    val gridApps: List<AppEntry> get() = specialApps + apps

    companion object {
        val specialApps = listOf(
            AppEntry("__search__", "Search", null, null, false, true, "search"),
            AppEntry("__settings__", "Settings", null, null, false, true, "settings"),
        )
    }
}

object LauncherReducer {
    const val TAB_SEARCH = 0
    const val TAB_SETTINGS = 5
    val tabBarIndex = mapOf(
        HomeTab.HOME to 1,
        HomeTab.MOVIES to 2,
        HomeTab.TV_SHOWS to 3,
        HomeTab.APPS to 4,
    )

    fun firstContentFocus(state: LauncherState): Focus {
        if (state.tab == HomeTab.APPS) {
            return Focus.AppGrid(0)
        }
        val content = state.currentContent
        return when {
            content.hero.isNotEmpty() -> Focus.Hero(0)
            content.shelves.isNotEmpty() -> Focus.ShelfItem(0, 0)
            else -> Focus.TabBar(tabBarIndex.getValue(state.tab))
        }
    }

    fun move(state: LauncherState, dir: Dir): LauncherState {
        val sheet = state.sheet
        if (sheet != null) {
            if (dir == Dir.Up) return state.copy(sheet = sheet.copy(focus = (sheet.focus - 1).coerceAtLeast(0)))
            if (dir == Dir.Down) {
                return state.copy(sheet = sheet.copy(focus = (sheet.focus + 1).coerceAtMost(sheet.options.lastIndex)))
            }
            return state
        }
        return when (val f = state.focus) {
            is Focus.TabBar -> moveTabBar(state, f, dir)
            is Focus.Hero -> moveHero(state, dir)
            is Focus.ShelfItem -> moveShelf(state, f, dir)
            is Focus.AppGrid -> moveApps(state, f, dir)
            is Focus.Sheet -> state
        }
    }

    private fun moveTabBar(state: LauncherState, f: Focus.TabBar, dir: Dir): LauncherState = when (dir) {
        Dir.Left -> state.copy(focus = f.copy(index = (f.index - 1).coerceAtLeast(0)))
        Dir.Right -> state.copy(focus = f.copy(index = (f.index + 1).coerceAtMost(TAB_SETTINGS)))
        Dir.Down -> state.copy(focus = firstContentFocus(state))
        Dir.Up -> state
    }

    private fun moveHero(state: LauncherState, dir: Dir): LauncherState {
        val f = state.focus as Focus.Hero
        val pages = state.currentContent.hero.size.coerceAtLeast(1)
        return when (dir) {
            Dir.Left ->
                if (f.action > 0) state.copy(focus = Focus.Hero(f.action - 1))
                else state.copy(
                    heroPage = (state.heroPage - 1).mod(pages),
                    focus = Focus.Hero(1),
                )
            Dir.Right ->
                if (f.action < 1) state.copy(focus = Focus.Hero(f.action + 1))
                else state.copy(
                    heroPage = (state.heroPage + 1).mod(pages),
                    focus = Focus.Hero(0),
                )
            Dir.Up -> state.copy(focus = Focus.TabBar(tabBarIndex.getValue(state.tab)))
            Dir.Down -> {
                val shelves = state.currentContent.shelves
                if (shelves.isNotEmpty()) state.copy(focus = Focus.ShelfItem(0, 0)) else state
            }
        }
    }

    private fun moveShelf(state: LauncherState, f: Focus.ShelfItem, dir: Dir): LauncherState {
        val shelves = state.currentContent.shelves
        val shelf = shelves.getOrNull(f.shelf) ?: return state
        return when (dir) {
            Dir.Left -> state.copy(focus = f.copy(item = (f.item - 1).coerceAtLeast(0)))
            Dir.Right -> state.copy(focus = f.copy(item = (f.item + 1).coerceAtMost((shelf.count(state) - 1).coerceAtLeast(0))))
            Dir.Down -> {
                val next = shelves.getOrNull(f.shelf + 1) ?: return state
                state.copy(focus = Focus.ShelfItem(f.shelf + 1, f.item.coerceAtMost((next.count(state) - 1).coerceAtLeast(0))))
            }
            Dir.Up -> {
                if (f.shelf == 0) {
                    if (state.currentContent.hero.isNotEmpty()) state.copy(focus = Focus.Hero(0))
                    else state.copy(focus = Focus.TabBar(tabBarIndex.getValue(state.tab)))
                } else {
                    val prev = shelves[f.shelf - 1]
                    state.copy(focus = Focus.ShelfItem(f.shelf - 1, f.item.coerceAtMost((prev.count(state) - 1).coerceAtLeast(0))))
                }
            }
        }
    }

    private fun moveApps(state: LauncherState, f: Focus.AppGrid, dir: Dir): LauncherState {
        val apps = state.gridApps
        val count = apps.size
        if (count == 0) return state
        if (state.jiggle) {
            val focusedApp = apps.getOrNull(f.index) ?: return state
            if (focusedApp.special != null) return state
            val target = when (dir) {
                Dir.Left -> f.index - 1
                Dir.Right -> f.index + 1
                Dir.Up -> f.index - COLUMNS
                Dir.Down -> f.index + COLUMNS
            }
            if (target !in LauncherState.specialApps.size until count) return state
            val newGrid = apps.toMutableList()
            val moved = newGrid.removeAt(f.index)
            newGrid.add(target, moved)
            return state.copy(apps = newGrid.filter { it.special == null }, focus = Focus.AppGrid(target))
        }
        val target = when (dir) {
            Dir.Left -> if (f.index % COLUMNS == 0) f.index else f.index - 1
            Dir.Right -> if (f.index % COLUMNS == COLUMNS - 1 || f.index == count - 1) f.index else f.index + 1
            Dir.Up -> if (f.index < COLUMNS) -1 else f.index - COLUMNS
            Dir.Down -> if (f.index + COLUMNS >= count) f.index else f.index + COLUMNS
        }
        if (target == -1) return state.copy(focus = Focus.TabBar(tabBarIndex.getValue(HomeTab.APPS)))
        return state.copy(focus = Focus.AppGrid(target.coerceIn(0, count - 1)))
    }

    const val COLUMNS = 5
}

enum class Dir { Up, Down, Left, Right }
