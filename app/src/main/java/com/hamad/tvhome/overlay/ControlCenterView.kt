package com.hamad.tvhome.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.hamad.tvhome.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/**
 * tvOS-style Control Center / App Switcher. Drawn with Canvas to match the rest
 * of the UI (no Material widgets) and driven by the accessibility service's key
 * filter, so focus is trapped while it is open.
 *
 * The scrim is dimmed only: an overlay app cannot capture another app's surface
 * to blur it on this API level, and inventing a blurred screenshot would be fake
 * content. Everything shown underneath is the live system state.
 */
@SuppressLint("ViewConstructor")
class ControlCenterView(context: Context) : View(context) {

    enum class Move { Left, Right, Up, Down }

    private data class Tile(
        val id: String,
        val label: String,
        val status: String,
        val detail: String?,
    )

    var onRequestClose: (() -> Unit)? = null
    var onOpenSystemSettings: ((String) -> Unit)? = null

    val focusLabel: String
        get() = if (focus < tiles.size) {
            tiles[focus].label + " " + tiles[focus].status
        } else {
            snapshot.recents.getOrNull(focus - tiles.size)?.label ?: "none"
        }

    private val snapshot: ControlSnapshot = ControlCenterData.snapshot(context)
    private val tiles: List<Tile> = buildList {
        add(Tile("wifi", "Wi-Fi", snapshot.wifiStatus, snapshot.wifiDetail))
        if (snapshot.bluetoothStatus != null) {
            add(Tile("bluetooth", "Bluetooth", snapshot.bluetoothStatus, null))
        }
    }

    private var focus = 0
    private var recentStart = 0
    private var closed = false

    private val typeface: Typeface? =
        ResourcesCompat.getFont(context, R.font.inter_variable)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        style = Paint.Style.FILL
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = describe()
    }

    fun move(direction: Move) {
        val next = ControlCenterNavigation.move(
            index = focus,
            tileCount = tiles.size,
            recentCount = snapshot.recents.size,
            move = when (direction) {
                Move.Left -> OverlayMove.Left
                Move.Right -> OverlayMove.Right
                Move.Up -> OverlayMove.Up
                Move.Down -> OverlayMove.Down
            },
        )
        if (next != focus) {
            focus = next
            windowStart()
            contentDescription = describe()
            invalidate()
        }
    }

    private fun windowStart() {
        recentStart = ControlCenterNavigation.windowStart(
            index = focus,
            tileCount = tiles.size,
            recentCount = snapshot.recents.size,
            visible = VISIBLE_RECENTS,
            current = recentStart,
        )
    }

    /** Runs the focused item; returns true when the overlay should close. */
    fun activate(): Boolean {
        if (closed) return true
        if (focus < tiles.size) {
            val tile = tiles[focus]
            onOpenSystemSettings?.invoke(tile.id)
            return true
        }
        val recent = snapshot.recents.getOrNull(focus - tiles.size) ?: return false
        val started = ControlCenterData.launchRecent(context, recent.packageName)
        if (started) {
            closed = true
            return true
        }
        return false
    }

    fun close() {
        closed = true
        onRequestClose?.invoke()
    }

    /**
     * The overlay window takes input focus while it is open, so D-pad events are
     * also handled here. On a real remote the accessibility key filter consumes
     * them first; this path covers injected events and keeps the window modal.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                move(Move.Left)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                move(Move.Right)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                move(Move.Up)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                move(Move.Down)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                if (activate()) close()
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_HOME -> {
                close()
                true
            }
            else -> false
        }
        if (handled) Log.d(TAG, "key=$keyCode focus=$focusLabel")
        return handled || super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = height / 1080f
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawColor(Color.argb(158, 0, 0, 0))

        val safeLeft = 80f * s
        val safeRight = w - 80f * s

        text.typeface = typeface
        text.textSize = 44f * s
        text.color = Color.WHITE
        canvas.drawText("Control Center", safeLeft, 104f * s, text)

        text.textSize = 40f * s
        text.color = Color.argb(235, 255, 255, 255)
        val clock = LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm", Locale.US))
        canvas.drawText(clock, safeRight - text.measureText(clock), 104f * s, text)

        drawTiles(canvas, s, safeRight)
        drawRecents(canvas, s, safeLeft, w)

        text.textSize = 22f * s
        text.color = Color.argb(140, 255, 255, 255)
        val hint = if (tiles.size > 1) {
            "Menu closes  \u00b7  Select opens system settings"
        } else {
            "Menu closes"
        }
        canvas.drawText(hint, safeLeft, h - 56f * s, text)
    }

    private fun drawTiles(canvas: Canvas, s: Float, right: Float) {
        val size = TILE_SIZE * s
        val gap = 32f * s
        val total = tiles.size * size + (tiles.size - 1).coerceAtLeast(0) * gap
        var x = right - total
        val y = 170f * s
        tiles.forEachIndexed { index, tile ->
            val focused = focus == index
            val rect = RectF(x, y, x + size, y + size)
            if (focused) {
                val grow = 6f * s
                rect.inset(-grow, -grow)
            }
            fill.color = if (focused) Color.argb(64, 255, 255, 255) else Color.argb(30, 255, 255, 255)
            canvas.drawRoundRect(rect, 44f * s, 44f * s, fill)
            stroke.color = if (focused) Color.argb(235, 255, 255, 255) else Color.argb(56, 255, 255, 255)
            stroke.strokeWidth = if (focused) 3.5f * s else 1.5f * s
            canvas.drawRoundRect(rect, 44f * s, 44f * s, stroke)

            val cx = rect.centerX()
            val iconCy = rect.top + 96f * s
            drawGlyph(canvas, tile.id, cx, iconCy, s)

            text.textSize = 30f * s
            text.color = Color.WHITE
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(tile.label, cx, rect.top + 168f * s, text)
            text.textSize = 25f * s
            text.color = Color.argb(180, 255, 255, 255)
            canvas.drawText(tile.status, cx, rect.top + 204f * s, text)
            if (tile.detail != null) {
                text.textSize = 20f * s
                text.color = Color.argb(120, 255, 255, 255)
                canvas.drawText(tile.detail, cx, rect.top + 230f * s, text)
            }
            text.textAlign = Paint.Align.LEFT
            x += size + gap
        }
    }

    private fun drawGlyph(canvas: Canvas, id: String, cx: Float, cy: Float, s: Float) {
        iconPaint.color = Color.WHITE
        iconPaint.strokeWidth = 6f * s
        if (id == "wifi") {
            for ((radius, alpha) in listOf(48f to 255, 34f to 215, 20f to 175)) {
                val r = radius * s
                iconPaint.alpha = alpha
                canvas.drawArc(
                    RectF(cx - r, cy + 20f * s - r, cx + r, cy + 20f * s + r),
                    225f,
                    90f,
                    false,
                    iconPaint,
                )
            }
            fill.color = Color.WHITE
            canvas.drawCircle(cx, cy + 20f * s, 5f * s, fill)
        } else {
            val path = Path()
            val u = 26f * s
            path.moveTo(cx - u * 0.45f, cy - u)
            path.lineTo(cx + u * 0.45f, cy)
            path.lineTo(cx - u * 0.45f, cy + u)
            path.lineTo(cx - u * 0.45f, cy - u)
            path.moveTo(cx - u * 0.45f, cy + u)
            path.lineTo(cx + u * 0.45f, cy)
            path.lineTo(cx - u * 0.45f, cy - u)
            iconPaint.strokeWidth = 5f * s
            iconPaint.alpha = 255
            canvas.drawPath(path, iconPaint)
        }
    }

    private fun drawRecents(canvas: Canvas, s: Float, left: Float, w: Float) {
        val recents = snapshot.recents
        text.textAlign = Paint.Align.LEFT
        text.textSize = 30f * s
        text.color = Color.argb(190, 255, 255, 255)
        canvas.drawText("Recent", left, 600f * s, text)

        if (!snapshot.usageGranted) {
            text.textSize = 25f * s
            text.color = Color.argb(150, 255, 255, 255)
            canvas.drawText(
                "Usage access is off. Enable it to list recent apps here.",
                left,
                660f * s,
                text,
            )
            return
        }
        if (recents.isEmpty()) {
            text.textSize = 25f * s
            text.color = Color.argb(150, 255, 255, 255)
            canvas.drawText("No apps have been opened recently.", left, 660f * s, text)
            return
        }

        val cardW = 340f * s
        val cardH = 200f * s
        val gap = 40f * s
        val top = 650f * s
        val available = w - left - 80f * s
        val visible = min(VISIBLE_RECENTS, ((available + gap) / (cardW + gap)).toInt()).coerceAtLeast(1)
        var x = left
        for (slot in 0 until visible) {
            val index = recentStart + slot
            val app = recents.getOrNull(index) ?: break
            val focused = focus == tiles.size + index
            val rect = RectF(x, top, x + cardW, top + cardH)
            if (focused) {
                val grow = 6f * s
                rect.inset(-grow, -grow)
            }
            fill.color = if (focused) Color.argb(56, 255, 255, 255) else Color.argb(24, 255, 255, 255)
            canvas.drawRoundRect(rect, 26f * s, 26f * s, fill)
            stroke.color = if (focused) Color.argb(240, 255, 255, 255) else Color.argb(48, 255, 255, 255)
            stroke.strokeWidth = if (focused) 3.5f * s else 1.5f * s
            canvas.drawRoundRect(rect, 26f * s, 26f * s, stroke)

            val icon = app.icon
            if (icon != null) {
                val side = 92f * s
                val iconRect = RectF(rect.left + 28f * s, rect.top + 28f * s, rect.left + 28f * s + side, rect.top + 28f * s + side)
                canvas.drawBitmap(icon, null, iconRect, null)
            }
            text.textSize = 27f * s
            text.color = Color.WHITE
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(app.label, rect.left + 28f * s, rect.bottom - 30f * s, text)
            x += cardW + gap
        }
    }

    private fun describe(): String {
        val builder = StringBuilder("Control Center")
        tiles.forEach { tile ->
            builder.append(". ").append(tile.label).append(' ').append(tile.status)
            if (tile.detail != null) builder.append(' ').append(tile.detail)
        }
        if (snapshot.usageGranted) {
            if (snapshot.recents.isEmpty()) {
                builder.append(". No recent apps")
            } else {
                builder.append(". Recent apps: ")
                builder.append(snapshot.recents.joinToString(", ") { it.label })
            }
        } else {
            builder.append(". Recent apps unavailable: usage access is off")
        }
        builder.append(". Focused: ")
        builder.append(
            if (focus < tiles.size) {
                tiles[focus].label + " " + tiles[focus].status
            } else {
                snapshot.recents.getOrNull(focus - tiles.size)?.label ?: "none"
            },
        )
        return builder.toString()
    }

    private companion object {
        const val TAG = "TvControlCenter"
        const val TILE_SIZE = 250f
        const val VISIBLE_RECENTS = 3
    }
}
