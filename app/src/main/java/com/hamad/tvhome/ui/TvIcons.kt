package com.hamad.tvhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/**
 * tvOS uses a thin, geometric line-icon language. Every glyph here is drawn on a
 * 24x24 grid with round caps so weights stay optically consistent at TV distance.
 */
enum class TvIconKind {
    Search,
    Gear,
    Play,
    Info,
    Home,
    Movies,
    Tv,
    Apps,
    ChevronRight,
    ChevronDown,
    ChevronUp,
    Check,
    Wifi,
    WifiOff,
    Bluetooth,
    Volume,
    VolumeLow,
    Moon,
    Screensaver,
    Clock,
    Star,
    Globe,
    Person,
    Lock,
    Trash,
    Plus,
    Minus,
    ArrowLeft,
    Close,
    Power,
    Download,
    Fajr,
    Sunrise,
    Sun,
    Sunset,
    Maghrib,
    Isha,
    Instagram,
    Calendar,
    Cloud,
    Location,
    PlayCircle,
    Jiggle,
}

@Composable
fun TvIcon(
    kind: TvIconKind,
    modifier: Modifier = Modifier,
    tint: Color = TvColor.label,
    strokeWidth: Float = 1.75f,
) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawTvIcon(kind, tint, strokeWidth)
        }
    }
}

private fun DrawScope.drawTvIcon(kind: TvIconKind, tint: Color, w: Float) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
        drawCircle(tint, r, Offset(cx, cy), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke)

    fun path(build: Path.() -> Unit) = drawPath(Path().apply(build), tint, style = stroke)
    fun filled(build: Path.() -> Unit) = drawPath(Path().apply(build), tint)

    when (kind) {
        TvIconKind.Search -> {
            circle(10.6f, 10.6f, 6.4f)
            line(15.4f, 15.4f, 20f, 20f)
        }

        TvIconKind.Gear -> {
            circle(12f, 12f, 3.1f)
            circle(12f, 12f, 7.7f)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(a).toFloat()
                val s = kotlin.math.sin(a).toFloat()
                line(12f + c * 7.8f, 12f + s * 7.8f, 12f + c * 9.5f, 12f + s * 9.5f)
            }
        }

        TvIconKind.Play -> filled {
            moveTo(8.5f, 5f); lineTo(19.5f, 12f); lineTo(8.5f, 19f); close()
        }

        TvIconKind.Info -> {
            circle(12f, 12f, 9.2f)
            circle(12f, 7.4f, 0.9f, fill = true)
            line(12f, 11f, 12f, 17f)
        }

        TvIconKind.Home -> path {
            moveTo(3.8f, 10.8f); lineTo(12f, 3.6f); lineTo(20.2f, 10.8f)
            lineTo(20.2f, 20.4f); lineTo(14.4f, 20.4f); lineTo(14.4f, 14.2f)
            lineTo(9.6f, 14.2f); lineTo(9.6f, 20.4f); lineTo(3.8f, 20.4f); close()
        }

        TvIconKind.Movies -> {
            drawRoundRect(tint, Offset(3f, 5.2f), Size(18f, 13.6f), style = stroke, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f))
            line(8.2f, 5.4f, 8.2f, 18.6f)
            line(15.8f, 5.4f, 15.8f, 18.6f)
        }

        TvIconKind.Tv -> {
            drawRoundRect(tint, Offset(3f, 4.5f), Size(18f, 13f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.4f), style = stroke)
            line(9f, 21f, 15f, 21f)
        }

        TvIconKind.Apps -> {
            for (x in 0..1) for (y in 0..1) {
                drawRoundRect(
                    tint,
                    Offset(3.2f + x * 9.8f, 3.2f + y * 9.8f),
                    Size(7.6f, 7.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.1f),
                    style = stroke,
                )
            }
        }

        TvIconKind.ChevronRight -> path { moveTo(9f, 4.8f); lineTo(16.4f, 12f); lineTo(9f, 19.2f) }
        TvIconKind.ChevronDown -> path { moveTo(4.8f, 9f); lineTo(12f, 16.4f); lineTo(19.2f, 9f) }
        TvIconKind.ChevronUp -> path { moveTo(4.8f, 15f); lineTo(12f, 7.6f); lineTo(19.2f, 15f) }

        TvIconKind.Check -> path { moveTo(4.2f, 12.4f); lineTo(9.6f, 18f); lineTo(20f, 6.2f) }

        TvIconKind.Wifi -> {
            drawArc(tint, 205f, 130f, false, Offset(2f, 6f), Size(20f, 20f), style = stroke)
            drawArc(tint, 210f, 120f, false, Offset(5.2f, 9.2f), Size(13.6f, 13.6f), style = stroke)
            drawArc(tint, 215f, 110f, false, Offset(8.4f, 12.4f), Size(7.2f, 7.2f), style = stroke)
            drawCircle(tint, 1.15f, Offset(12f, 18.4f))
        }

        TvIconKind.WifiOff -> {
            drawArc(tint, 205f, 130f, false, Offset(2f, 6f), Size(20f, 20f), style = stroke)
            drawArc(tint, 215f, 110f, false, Offset(8.4f, 12.4f), Size(7.2f, 7.2f), style = stroke)
            drawCircle(tint, 1.15f, Offset(12f, 18.4f))
            line(3.5f, 3.5f, 20.5f, 20.5f)
        }

        TvIconKind.Bluetooth -> path {
            moveTo(7f, 7.2f); lineTo(17f, 16.8f); lineTo(11.8f, 21.2f); lineTo(11.8f, 2.8f)
            lineTo(17f, 7.2f); lineTo(7f, 16.8f)
        }

        TvIconKind.Volume, TvIconKind.VolumeLow -> {
            filled {
                moveTo(3.4f, 9.2f); lineTo(7.2f, 9.2f); lineTo(12f, 5.2f); lineTo(12f, 18.8f)
                lineTo(7.2f, 14.8f); lineTo(3.4f, 14.8f); close()
            }
            drawArc(tint, -52f, 104f, false, Offset(9.2f, 8.2f), Size(7.6f, 7.6f), style = stroke)
            if (kind == TvIconKind.Volume) {
                drawArc(tint, -52f, 104f, false, Offset(7f, 4.6f), Size(14.4f, 14.8f), style = stroke)
            }
        }

        TvIconKind.Moon -> filled {
            moveTo(20.4f, 14.6f)
            cubicTo(18.6f, 18.9f, 13.4f, 21.2f, 9f, 19.4f)
            cubicTo(4.6f, 17.6f, 2.6f, 12.3f, 4.6f, 8f)
            cubicTo(6.6f, 3.7f, 11.8f, 1.7f, 16.1f, 3.6f)
            cubicTo(11.9f, 6.6f, 10.8f, 12.7f, 13.6f, 16.3f)
            cubicTo(15.3f, 18.4f, 18f, 19.2f, 20.4f, 18.2f)
            close()
        }

        TvIconKind.Screensaver -> {
            circle(7.6f, 7.9f, 2.7f)
            path {
                moveTo(3f, 18.8f); lineTo(9.4f, 12.4f); lineTo(14f, 17f)
                lineTo(17f, 14f); lineTo(21f, 18.4f)
            }
        }

        TvIconKind.Clock -> {
            circle(12f, 12f, 9f)
            line(12f, 6.6f, 12f, 12.2f)
            line(12f, 12.2f, 15.6f, 14.2f)
        }

        TvIconKind.Star -> filled {
            moveTo(12f, 3.2f); lineTo(14.6f, 9.1f); lineTo(21f, 9.7f); lineTo(16.2f, 14f)
            lineTo(17.6f, 20.3f); lineTo(12f, 17f); lineTo(6.4f, 20.3f); lineTo(7.8f, 14f)
            lineTo(3f, 9.7f); lineTo(9.4f, 9.1f); close()
        }

        TvIconKind.Globe -> {
            circle(12f, 12f, 9f)
            drawOval(tint, Offset(8f, 3f), Size(8f, 18f), style = stroke)
            line(3.2f, 12f, 20.8f, 12f)
        }

        TvIconKind.Person -> {
            circle(12f, 8f, 4f)
            path {
                moveTo(3.8f, 21f)
                cubicTo(4.4f, 15.8f, 8f, 14f, 12f, 14f)
                cubicTo(16f, 14f, 19.6f, 15.8f, 20.2f, 21f)
            }
        }

        TvIconKind.Lock -> {
            drawRoundRect(tint, Offset(4.6f, 10.4f), Size(14.8f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f), style = stroke)
            path {
                moveTo(7.8f, 10f); lineTo(7.8f, 7.6f)
                cubicTo(7.8f, 2.6f, 16.2f, 2.6f, 16.2f, 7.6f); lineTo(16.2f, 10f)
            }
        }

        TvIconKind.Trash -> {
            line(4.4f, 6.6f, 19.6f, 6.6f)
            path { moveTo(9f, 6.4f); lineTo(9.4f, 3.4f); lineTo(14.6f, 3.4f); lineTo(15f, 6.4f) }
            path { moveTo(6.2f, 6.8f); lineTo(7.4f, 21f); lineTo(16.6f, 21f); lineTo(17.8f, 6.8f) }
            line(10.2f, 10.4f, 10.6f, 17.4f)
            line(13.8f, 10.4f, 13.4f, 17.4f)
        }

        TvIconKind.Plus -> { line(12f, 4.6f, 12f, 19.4f); line(4.6f, 12f, 19.4f, 12f) }
        TvIconKind.Minus -> line(4.6f, 12f, 19.4f, 12f)

        TvIconKind.ArrowLeft -> path { moveTo(19.5f, 12f); lineTo(4.5f, 12f); moveTo(10.5f, 5.6f); lineTo(4.2f, 12f); lineTo(10.5f, 18.4f) }

        TvIconKind.Close -> { line(5.4f, 5.4f, 18.6f, 18.6f); line(18.6f, 5.4f, 5.4f, 18.6f) }

        TvIconKind.Power -> {
            drawArc(tint, -60f, 300f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
            line(12f, 2.6f, 12f, 11.4f)
        }

        TvIconKind.Download -> {
            line(12f, 3.4f, 12f, 15f)
            path { moveTo(6.8f, 10f); lineTo(12f, 15.4f); lineTo(17.2f, 10f) }
            path { moveTo(4f, 19f); lineTo(20f, 19f) }
        }

        TvIconKind.Fajr -> {
            drawArc(tint, 180f, 180f, false, Offset(4f, 8f), Size(16f, 16f), style = stroke)
            line(2.6f, 20.4f, 21.4f, 20.4f)
            line(12f, 3.2f, 12f, 6.4f)
            line(5.4f, 6.2f, 7f, 8.2f)
            line(18.6f, 6.2f, 17f, 8.2f)
        }

        TvIconKind.Sunrise -> {
            drawArc(tint, 190f, 160f, false, Offset(5f, 9f), Size(14f, 14f), style = stroke)
            line(2.6f, 20.2f, 21.4f, 20.2f)
            line(12f, 3.4f, 12f, 6.4f)
            line(5.4f, 7.2f, 7f, 9f)
            line(18.6f, 7.2f, 17f, 9f)
            path { moveTo(9f, 16.8f); lineTo(12f, 13.6f); lineTo(15f, 16.8f) }
        }

        TvIconKind.Sun -> {
            circle(12f, 12f, 4.4f)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(a).toFloat()
                val s = kotlin.math.sin(a).toFloat()
                line(12f + c * 6.4f, 12f + s * 6.4f, 12f + c * 8.9f, 12f + s * 8.9f)
            }
        }

        TvIconKind.Sunset -> {
            drawArc(tint, 190f, 160f, false, Offset(5f, 9f), Size(14f, 14f), style = stroke)
            line(2.6f, 20.2f, 21.4f, 20.2f)
            line(12f, 3.4f, 12f, 6.4f)
            line(5.4f, 7.2f, 7f, 9f)
            line(18.6f, 7.2f, 17f, 9f)
            path { moveTo(9f, 13.6f); lineTo(12f, 16.8f); lineTo(15f, 13.6f) }
        }

        TvIconKind.Maghrib -> {
            drawArc(tint, 180f, 180f, false, Offset(4f, 7f), Size(16f, 16f), style = stroke)
            line(2.6f, 20.2f, 21.4f, 20.2f)
            line(12f, 12f, 16.5f, 7.6f)
        }

        TvIconKind.Isha -> {
            filled {
                moveTo(17.4f, 12.2f)
                cubicTo(16.1f, 16.2f, 11.8f, 17.8f, 8.4f, 16.4f)
                cubicTo(4.9f, 15f, 3.4f, 10.6f, 4.9f, 7.2f)
                cubicTo(6.4f, 3.8f, 10.7f, 2.3f, 14.1f, 3.8f)
                cubicTo(10.9f, 6.1f, 10.1f, 10.7f, 12.2f, 13.5f)
                cubicTo(13.5f, 15.2f, 15.6f, 15.8f, 17.4f, 15f)
                close()
            }
            filled {
                moveTo(17.2f, 4.2f); lineTo(17.9f, 6.1f); lineTo(19.8f, 6.8f)
                lineTo(17.9f, 7.5f); lineTo(17.2f, 9.4f); lineTo(16.5f, 7.5f)
                lineTo(14.6f, 6.8f); lineTo(16.5f, 6.1f); close()
            }
        }

        TvIconKind.Instagram -> {
            drawRoundRect(tint, Offset(3.2f, 3.2f), Size(17.6f, 17.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.2f), style = stroke)
            circle(12f, 12f, 4.4f)
            circle(17.1f, 6.9f, 1.0f, fill = true)
        }

        TvIconKind.Calendar -> {
            drawRoundRect(tint, Offset(3.4f, 5f), Size(17.2f, 15.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.6f), style = stroke)
            line(3.6f, 9.8f, 20.4f, 9.8f)
            line(7.6f, 3f, 7.6f, 6.6f)
            line(16.4f, 3f, 16.4f, 6.6f)
        }

        TvIconKind.Cloud -> path {
            moveTo(6.8f, 18.6f)
            cubicTo(3.4f, 18.6f, 2.4f, 13.8f, 5.4f, 12.2f)
            cubicTo(5.2f, 8.2f, 10.2f, 6.2f, 12.8f, 8.8f)
            cubicTo(15.8f, 6.4f, 20.4f, 8.8f, 19.6f, 12.6f)
            cubicTo(22.2f, 13.6f, 21.4f, 18.6f, 17.6f, 18.6f)
            close()
        }

        TvIconKind.Location -> {
            path {
                moveTo(12f, 21f)
                cubicTo(12f, 21f, 19f, 14.8f, 19f, 9.6f)
                cubicTo(19f, 5.6f, 15.8f, 3.2f, 12f, 3.2f)
                cubicTo(8.2f, 3.2f, 5f, 5.6f, 5f, 9.6f)
                cubicTo(5f, 14.8f, 12f, 21f, 12f, 21f)
                close()
            }
            circle(12f, 9.6f, 2.8f)
        }

        TvIconKind.PlayCircle -> {
            circle(12f, 12f, 9.2f)
            filled { moveTo(10f, 7.6f); lineTo(17f, 12f); lineTo(10f, 16.4f); close() }
        }

        TvIconKind.Jiggle -> {
            path { moveTo(3.6f, 8.4f); lineTo(3.6f, 3.6f); lineTo(8.4f, 3.6f) }
            path { moveTo(15.6f, 3.6f); lineTo(20.4f, 3.6f); lineTo(20.4f, 8.4f) }
            path { moveTo(20.4f, 15.6f); lineTo(20.4f, 20.4f); lineTo(15.6f, 20.4f) }
            path { moveTo(8.4f, 20.4f); lineTo(3.6f, 20.4f); lineTo(3.6f, 15.6f) }
        }
    }
}
