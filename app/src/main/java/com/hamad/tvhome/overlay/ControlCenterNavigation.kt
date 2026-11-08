package com.hamad.tvhome.overlay

internal enum class OverlayMove { Left, Right, Up, Down }

/**
 * Pure focus and paging rules for the Control Center overlay. Tiles sit in a row
 * at the top, recent apps in a row below; moving down from a tile enters the
 * recents row at the same column, and moving up returns to the tiles. Kept free
 * of Android types so the navigation can be unit tested.
 */
internal object ControlCenterNavigation {

    /** Index 0..tileCount-1 are tiles, the rest are recents. */
    fun move(index: Int, tileCount: Int, recentCount: Int, move: OverlayMove): Int {
        if (tileCount <= 0) return 0
        return if (index < tileCount) {
            when (move) {
                OverlayMove.Left -> (index - 1).coerceAtLeast(0)
                OverlayMove.Right -> (index + 1).coerceAtMost(tileCount - 1)
                OverlayMove.Down -> if (recentCount > 0) tileCount else index
                OverlayMove.Up -> index
            }
        } else {
            val recent = index - tileCount
            when (move) {
                OverlayMove.Left -> tileCount + (recent - 1).coerceAtLeast(0)
                OverlayMove.Right -> tileCount + (recent + 1).coerceAtMost(recentCount - 1)
                OverlayMove.Up -> (recent).coerceAtMost(tileCount - 1)
                OverlayMove.Down -> index
            }
        }
    }

    /**
     * Keeps the focused recent card inside the visible window, sliding the
     * window by one card at a time and clamping it to the list.
     */
    fun windowStart(index: Int, tileCount: Int, recentCount: Int, visible: Int, current: Int): Int {
        if (index < tileCount || visible <= 0) return current
        val recent = index - tileCount
        var start = current
        if (recent < start) start = recent
        if (recent >= start + visible) start = recent - visible + 1
        return start.coerceIn(0, (recentCount - visible).coerceAtLeast(0))
    }

    /** A long press is anything at or past the tvOS-style 0.5 s threshold. */
    fun isLongPress(downAt: Long, upAt: Long, thresholdMs: Long = 500L): Boolean =
        downAt > 0L && upAt - downAt >= thresholdMs
}
