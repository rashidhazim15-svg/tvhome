package com.hamad.tvhome.launcher

import com.hamad.tvhome.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherReducerTest {

    private fun app(id: String, special: String? = null) = AppEntry(
        packageName = id,
        label = id,
        banner = null,
        icon = null,
        removable = false,
        system = false,
        special = special,
    )

    private fun media(id: Int) = MediaItem(
        tmdbId = id,
        type = MediaType.MOVIE,
        title = "Title $id",
        logoPath = null,
        backdropPath = null,
        posterPath = null,
        year = null,
        genres = emptyList(),
        overview = null,
        rating = null,
    )

    private fun content(hero: Int = 2, shelves: List<Int> = listOf(3, 2)): TabContent = TabContent(
        hero = (1..hero).map { HeroSlide(media(it), "Attribution") },
        shelves = shelves.mapIndexed { index, count ->
            Shelf(
                id = "shelf$index",
                title = "Shelf $index",
                style = CardStyle.Poster,
                items = (1..count).map { media(it) },
            )
        },
    )

    private fun state(
        tab: HomeTab = HomeTab.HOME,
        focus: Focus = Focus.TabBar(1),
        apps: List<AppEntry> = List(6) { app("app$it") },
        hero: Int = 2,
        shelves: List<Int> = listOf(3, 2),
        heroPage: Int = 0,
        jiggle: Boolean = false,
        sheet: ActionSheetState? = null,
    ) = LauncherState(
        tab = tab,
        focus = focus,
        apps = apps,
        content = mapOf(tab to content(hero, shelves)),
        heroPage = heroPage,
        jiggle = jiggle,
        sheet = sheet,
    )

    @Test
    fun `tab bar clamps at both ends`() {
        var s = state(focus = Focus.TabBar(1))
        s = LauncherReducer.move(s, Dir.Left)
        assertEquals(Focus.TabBar(0), s.focus)
        s = LauncherReducer.move(s, Dir.Left)
        assertEquals(Focus.TabBar(0), s.focus)

        s = state(focus = Focus.TabBar(4))
        s = LauncherReducer.move(s, Dir.Right)
        assertEquals(Focus.TabBar(5), s.focus)
        s = LauncherReducer.move(s, Dir.Right)
        assertEquals(Focus.TabBar(5), s.focus)
    }

    @Test
    fun `tab bar down enters hero then first shelf`() {
        var s = LauncherReducer.move(state(focus = Focus.TabBar(1)), Dir.Down)
        assertEquals(Focus.Hero(0), s.focus)
        s = LauncherReducer.move(s, Dir.Down)
        assertEquals(Focus.ShelfItem(0, 0), s.focus)
    }

    @Test
    fun `tab bar down falls back to the tab itself when the tab is empty`() {
        val empty = state(focus = Focus.TabBar(1), hero = 0, shelves = emptyList())
        val moved = LauncherReducer.move(empty, Dir.Down)
        assertEquals(Focus.TabBar(1), moved.focus)
    }

    @Test
    fun `apps tab down enters the grid`() {
        val s = state(tab = HomeTab.APPS, focus = Focus.TabBar(4))
        assertEquals(Focus.AppGrid(0), LauncherReducer.move(s, Dir.Down).focus)
    }

    @Test
    fun `hero wraps pages at its left and right ends`() {
        var s = state(focus = Focus.Hero(0), heroPage = 0)
        s = LauncherReducer.move(s, Dir.Left)
        assertEquals(Focus.Hero(1), s.focus)
        assertEquals(1, s.heroPage)

        s = LauncherReducer.move(s, Dir.Right)
        assertEquals(Focus.Hero(0), s.focus)
        assertEquals(0, s.heroPage)
    }

    @Test
    fun `hero up returns to the tab bar`() {
        val s = LauncherReducer.move(state(focus = Focus.Hero(1)), Dir.Up)
        assertEquals(Focus.TabBar(1), s.focus)
    }

    @Test
    fun `shelf edges clamp and row changes preserve the column`() {
        var s = state(focus = Focus.ShelfItem(0, 0))
        s = LauncherReducer.move(s, Dir.Left)
        assertEquals(Focus.ShelfItem(0, 0), s.focus)

        s = state(focus = Focus.ShelfItem(0, 2))
        s = LauncherReducer.move(s, Dir.Right)
        assertEquals(Focus.ShelfItem(0, 2), s.focus)

        s = LauncherReducer.move(s, Dir.Down)
        assertEquals(Focus.ShelfItem(1, 1), s.focus) // second shelf has 2 items
        s = LauncherReducer.move(s, Dir.Up)
        assertEquals(Focus.ShelfItem(0, 1), s.focus)
    }

    @Test
    fun `first shelf up returns to hero`() {
        val s = LauncherReducer.move(state(focus = Focus.ShelfItem(0, 0)), Dir.Up)
        assertEquals(Focus.Hero(0), s.focus)
    }

    @Test
    fun `app grid edges do not cross rows or the special row`() {
        var s = state(tab = HomeTab.APPS, focus = Focus.AppGrid(0))
        s = LauncherReducer.move(s, Dir.Left)
        assertEquals(Focus.AppGrid(0), s.focus) // first column

        s = state(tab = HomeTab.APPS, focus = Focus.AppGrid(4))
        s = LauncherReducer.move(s, Dir.Right)
        assertEquals(Focus.AppGrid(4), s.focus) // last column of the first row

        s = state(tab = HomeTab.APPS, focus = Focus.AppGrid(2))
        s = LauncherReducer.move(s, Dir.Up)
        assertEquals(Focus.TabBar(4), s.focus) // first grid row

        s = state(tab = HomeTab.APPS, focus = Focus.AppGrid(7))
        s = LauncherReducer.move(s, Dir.Down)
        assertEquals(Focus.AppGrid(7), s.focus) // no row below
        s = LauncherReducer.move(s, Dir.Right)
        assertEquals(Focus.AppGrid(7), s.focus) // last item
    }

    @Test
    fun `jiggle moves a real app and leaves special apps alone`() {
        var s = state(
            tab = HomeTab.APPS,
            focus = Focus.AppGrid(2),
            apps = List(5) { app("app$it") },
            jiggle = true,
        )
        assertEquals(7, s.gridApps.size) // 2 synthetic + 5 real

        s = LauncherReducer.move(s, Dir.Right)
        assertEquals(Focus.AppGrid(3), s.focus)
        assertEquals("app1", s.apps.first().packageName)
        assertEquals("app0", s.apps[1].packageName)

        val atSpecial = state(tab = HomeTab.APPS, focus = Focus.AppGrid(0), jiggle = true)
        assertEquals(atSpecial, LauncherReducer.move(atSpecial, Dir.Right))
    }

    @Test
    fun `action sheet focus stops at its first and last option`() {
        val options = listOf(
            ActionOption(ActionId.Move, "Move"),
            ActionOption(ActionId.HideApp, "Hide"),
            ActionOption(ActionId.Cancel, "Cancel"),
        )
        var s = state(
            tab = HomeTab.APPS,
            focus = Focus.AppGrid(2),
            sheet = ActionSheetState(app("app0"), options, focus = 0),
        )
        s = LauncherReducer.move(s, Dir.Up)
        assertEquals(0, s.sheet?.focus)
        s = LauncherReducer.move(s, Dir.Down)
        s = LauncherReducer.move(s, Dir.Down)
        assertEquals(2, s.sheet?.focus)
        s = LauncherReducer.move(s, Dir.Down)
        assertEquals(2, s.sheet?.focus)
        assertTrue(s.sheet != null)
    }
}
