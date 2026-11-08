package com.hamad.tvhome.search

import com.hamad.tvhome.launcher.MediaItem
import com.hamad.tvhome.launcher.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchReducerTest {

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

    private fun results(count: Int) = List(count) { media(it + 1) }

    @Test
    fun `keyboard movement stops at the edges`() {
        var s = SearchReducer.move(SearchState(keyIndex = 0), SearchDir.Left)
        assertEquals(0, s.keyIndex)
        s = SearchReducer.move(SearchState(keyIndex = 0), SearchDir.Up)
        assertEquals(0, s.keyIndex)

        s = SearchReducer.move(SearchState(keyIndex = 5), SearchDir.Right)
        assertEquals(5, s.keyIndex)

        // Last row, down leaves the keyboard for the action row.
        s = SearchReducer.move(SearchState(keyIndex = 35), SearchDir.Down)
        assertEquals(SearchPane.Actions, s.pane)
        assertEquals(0, s.actionIndex)
    }

    @Test
    fun `action row clamps and returns to the last keyboard row`() {
        var s = SearchReducer.move(SearchState(pane = SearchPane.Actions), SearchDir.Left)
        assertEquals(0, s.actionIndex)

        s = SearchReducer.move(SearchState(pane = SearchPane.Actions, actionIndex = 2), SearchDir.Right)
        assertEquals(2, s.actionIndex)

        s = SearchReducer.move(SearchState(pane = SearchPane.Actions, actionIndex = 2), SearchDir.Up)
        assertEquals(SearchPane.Keyboard, s.pane)
        assertEquals(34, s.keyIndex)
    }

    @Test
    fun `action row down needs results`() {
        val empty = SearchReducer.move(SearchState(pane = SearchPane.Actions), SearchDir.Down)
        assertEquals(SearchPane.Actions, empty.pane)

        val withResults = SearchReducer.withResults(
            SearchState(pane = SearchPane.Actions, resultIndex = 9),
            results(3),
        )
        val moved = SearchReducer.move(withResults, SearchDir.Down)
        assertEquals(SearchPane.Results, moved.pane)
        assertEquals(2, moved.resultIndex)
    }

    @Test
    fun `typing lowercases and caps the query`() {
        var s = SearchReducer.append(SearchState(), "AB")
        assertEquals("ab", s.query)
        s = SearchReducer.append(s, "c".repeat(100))
        assertEquals(64, s.query.length)
        assertTrue(s.canSearch)
        assertFalse(SearchState(query = " a ").canSearch)
    }

    @Test
    fun `space delete and clear act on the query`() {
        var s = SearchState(query = "star", pane = SearchPane.Actions)
        s = SearchReducer.activate(s.copy(actionIndex = 0)).first
        assertEquals("star ", s.query)
        s = SearchReducer.activate(s.copy(actionIndex = 1)).first
        assertEquals("star", s.query)
        s = SearchReducer.activate(s.copy(actionIndex = 2)).first
        assertEquals("", s.query)
    }

    @Test
    fun `result grid edges stay inside its last row`() {
        val base = SearchReducer.withResults(
            SearchState(pane = SearchPane.Results),
            results(7),
        )
        var s = SearchReducer.move(base.copy(resultIndex = 0), SearchDir.Left)
        assertEquals(0, s.resultIndex)

        s = SearchReducer.move(base.copy(resultIndex = 3), SearchDir.Right)
        assertEquals(3, s.resultIndex) // column 3 is the last one

        s = SearchReducer.move(base.copy(resultIndex = 2), SearchDir.Right)
        assertEquals(3, s.resultIndex)

        s = SearchReducer.move(base.copy(resultIndex = 0), SearchDir.Down)
        assertEquals(4, s.resultIndex)

        s = SearchReducer.move(base.copy(resultIndex = 5), SearchDir.Down)
        assertEquals(5, s.resultIndex) // incomplete last row

        s = SearchReducer.move(base.copy(resultIndex = 5), SearchDir.Up)
        assertEquals(1, s.resultIndex)

        s = SearchReducer.move(base.copy(resultIndex = 2), SearchDir.Up)
        assertEquals(SearchPane.Actions, s.pane)
    }

    @Test
    fun `results shrinking under focus falls back to the action row`() {
        val focused = SearchState(pane = SearchPane.Results, resultIndex = 5)
        val emptied = SearchReducer.withResults(focused, emptyList())
        assertEquals(SearchPane.Actions, emptied.pane)
        assertEquals(0, emptied.resultIndex)

        val shrunk = SearchReducer.withResults(focused, results(2))
        assertEquals(SearchPane.Results, shrunk.pane)
        assertEquals(1, shrunk.resultIndex)
    }

    @Test
    fun `activating a result returns the item`() {
        val s = SearchReducer.withResults(
            SearchState(pane = SearchPane.Results, resultIndex = 1),
            results(3),
        )
        val (next, item) = SearchReducer.activate(s)
        assertEquals(2, item?.tmdbId)
        assertEquals(s, next)
    }

    @Test
    fun `back leaves the screen only from the keyboard`() {
        val fromKeyboard = SearchReducer.back(SearchState(pane = SearchPane.Keyboard))
        assertTrue(fromKeyboard.second)

        val fromResults = SearchReducer.back(SearchState(pane = SearchPane.Results))
        assertFalse(fromResults.second)
        assertEquals(SearchPane.Keyboard, fromResults.first.pane)

        val fromActions = SearchReducer.back(SearchState(pane = SearchPane.Actions))
        assertFalse(fromActions.second)
        assertEquals(SearchPane.Keyboard, fromActions.first.pane)
    }

    @Test
    fun `activating a keyboard key appends it`() {
        val (s, item) = SearchReducer.activate(SearchState(keyIndex = SearchKeys.indexOf("Q")))
        assertNull(item)
        assertEquals("q", s.query)
    }
}
