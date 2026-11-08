package com.hamad.tvhome.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterNavigationTest {

    private fun move(index: Int, move: OverlayMove, tiles: Int = 2, recents: Int = 5) =
        ControlCenterNavigation.move(index, tiles, recents, move)

    @Test
    fun `tile row clamps at both ends`() {
        assertEquals(0, move(0, OverlayMove.Left))
        assertEquals(0, move(1, OverlayMove.Left))
        assertEquals(1, move(1, OverlayMove.Right, tiles = 2))
        assertEquals(0, move(0, OverlayMove.Right, tiles = 1))
        assertEquals(0, move(0, OverlayMove.Up))
    }

    @Test
    fun `down enters recents only when there is one`() {
        assertEquals(2, move(0, OverlayMove.Down))
        assertEquals(0, move(0, OverlayMove.Down, recents = 0))
        assertEquals(1, move(1, OverlayMove.Down, recents = 0))
    }

    @Test
    fun `recent row clamps and up returns to the tiles`() {
        assertEquals(2, move(2, OverlayMove.Left))
        assertEquals(6, move(6, OverlayMove.Right))
        assertEquals(6, move(6, OverlayMove.Down))
        assertEquals(0, move(2, OverlayMove.Up, tiles = 2))
        assertEquals(1, move(6, OverlayMove.Up, tiles = 2))
        assertEquals(0, move(2, OverlayMove.Up, tiles = 1))
    }

    @Test
    fun `recents window follows focus and clamps to the list`() {
        // Focus is on a tile: window is untouched.
        assertEquals(0, ControlCenterNavigation.windowStart(1, 2, 5, 3, 0))
        // Moving right past the visible window slides it by one.
        assertEquals(1, ControlCenterNavigation.windowStart(5, 2, 5, 3, 0))
        assertEquals(2, ControlCenterNavigation.windowStart(6, 2, 5, 3, 1))
        // Moving left slides it back.
        assertEquals(0, ControlCenterNavigation.windowStart(2, 2, 5, 3, 2))
        // The end clamps so the last page is full.
        assertEquals(2, ControlCenterNavigation.windowStart(6, 2, 5, 3, 2))
        assertEquals(0, ControlCenterNavigation.windowStart(6, 2, 2, 3, 0))
    }

    @Test
    fun `long press needs the full threshold`() {
        assertFalse(ControlCenterNavigation.isLongPress(0L, 0L))
        assertFalse(ControlCenterNavigation.isLongPress(1_000L, 1_499L))
        assertTrue(ControlCenterNavigation.isLongPress(1_000L, 1_500L))
        assertTrue(ControlCenterNavigation.isLongPress(1_000L, 2_000L))
        assertFalse(ControlCenterNavigation.isLongPress(1_000L, 2_000L, thresholdMs = 1_500L))
    }

    @Test
    fun `an empty overlay keeps focus at zero`() {
        assertEquals(0, ControlCenterNavigation.move(0, 0, 0, OverlayMove.Right))
        assertEquals(0, ControlCenterNavigation.windowStart(0, 0, 0, 3, 0))
    }
}
